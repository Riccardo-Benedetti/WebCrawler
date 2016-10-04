package sd1516.webcrawler.devices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import alice.logictuple.LogicTuple;
import alice.logictuple.exceptions.InvalidLogicTupleException;
import alice.tucson.api.TucsonTupleCentreId;
import alice.tucson.api.exceptions.TucsonInvalidAgentIdException;
import alice.tucson.api.exceptions.TucsonInvalidTupleCentreIdException;
import alice.tucson.api.exceptions.TucsonOperationNotPossibleException;
import alice.tucson.asynchSupport.actions.ordinary.Inp;
import alice.tucson.asynchSupport.actions.ordinary.Out;
import alice.tucson.asynchSupport.actions.ordinary.bulk.InAll;
import alice.tucson.service.TucsonOpCompletionEvent;
import it.unibo.tucson.jade.exceptions.CannotAcquireACCException;
import it.unibo.tucson.jade.glue.BridgeToTucson;
import it.unibo.tucson.jade.service.TucsonHelper;
import it.unibo.tucson.jade.service.TucsonService;
import jade.core.Agent;
import jade.core.ServiceException;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;

public class WatchdogAgent extends Agent {

	private static final long serialVersionUID = 6781364993606695807L;

	private TucsonHelper helper;
	private BridgeToTucson bridge;
	private TucsonTupleCentreId tcid;
	
	private OneShotBehaviour watchdogBehaviour;
	private AgentsHandler aBehaviour;
	private PingHandler piBehaviour;
	private PongHandler poBehaviour;
	private RecoveryMastersHandler rmBehaviour;
	private RecoveryWorkersHandler rwBehaviour;
	
	private HashMap<String,List<String>> nodeAgents;
	private List<String> crashedMasters;
	private List<String> crashedWorkers;
	
	@Override
	public void setup(){
		try {
            /*
             * First of all, get the helper for the service you want to exploit
             */
            this.helper = (TucsonHelper) this.getHelper(TucsonService.NAME);
            /*
             * Then, start a TuCSoN Node (if not already up) as the actual
             * executor of the service
             */
            if (!this.helper.isActive("localhost", 20504, 10000)) {
                this.log("Booting local TuCSoN Node on default port...");
                this.helper.startTucsonNode(20504);
            }
            /*
             * Obtain ACC (which is actually given to the bridge, not directly
             * to your agent)
             */
            this.helper.acquireACC(this);
            /*
             * Get the univocal bridge for the agent. Now, mandatory, set-up
             * actions have been carried out and you are ready to coordinate
             */
            this.bridge = this.helper.getBridgeToTucson(this);
            /*
             * build a tuple centre id
             */
            this.tcid = this.helper.buildTucsonTupleCentreId("default",
            		"localhost", 20504);
            
        } catch (final ServiceException e) {
        	e.printStackTrace();
            this.log(">>> No TuCSoN service active, reboot JADE with -services it.unibo.tucson.jade.service.TucsonService option <<<");
            this.doDelete();
        } catch (final TucsonInvalidAgentIdException e) {
        	e.printStackTrace();
            this.log(">>> TuCSoN Agent ids should be compliant with Prolog sytnax (start with lowercase letter, no special symbols), choose another agent id <<<");
            this.doDelete();
        } catch (final TucsonInvalidTupleCentreIdException e) {
            // should not happen
            e.printStackTrace();
            this.doDelete();
        } catch (final CannotAcquireACCException e) {
            // should not happen
            e.printStackTrace();
            this.doDelete();
        } catch (final TucsonOperationNotPossibleException e) {
        	e.printStackTrace();
            this.log(">>> TuCSoN Node cannot be installed, check if given port is already in use <<<");
            this.doDelete();
        }
		
		this.nodeAgents = new HashMap<String,List<String>>();
		this.crashedMasters = new ArrayList<String>();
		this.crashedWorkers = new ArrayList<String>();
		
		this.watchdogBehaviour = new OneShotBehaviour(this){

			private static final long serialVersionUID = 4170863694975116557L;
			
			@Override
			public void action() {
				final FSMBehaviour fsm = new FSMBehaviour(this.myAgent);
				this.configureFSM(fsm);
				this.myAgent.addBehaviour(fsm);
			}

			private void configureFSM(FSMBehaviour fsm) {
				aBehaviour = new AgentsHandler();
				piBehaviour = new PingHandler();
				poBehaviour = new PongHandler();
				rmBehaviour = new RecoveryMastersHandler();
				rwBehaviour = new RecoveryWorkersHandler();
				fsm.registerFirstState(aBehaviour, "AgentsHandler");
				fsm.registerState(piBehaviour, "PingHandler");
				fsm.registerState(poBehaviour, "PongHandler");
				fsm.registerState(rmBehaviour, "RecoveryMastersHandler");
				fsm.registerState(rwBehaviour, "RecoveryWorkersHandler");
				fsm.registerDefaultTransition("AgentsHandler", "PingHandler");
				fsm.registerDefaultTransition("PingHandler", "PongHandler");
				fsm.registerDefaultTransition("PongHandler", "RecoveryMastersHandler");
				fsm.registerDefaultTransition("RecoveryMastersHandler", "RecoveryWorkersHandler");
				fsm.registerDefaultTransition("RecoveryWorkersHandler", "AgentsHandler");
			}
		};
		
		this.addBehaviour(watchdogBehaviour);
	}
	
	public class AgentsHandler extends Behaviour {

		private static final long serialVersionUID = 1014719482055275645L;
		
		private boolean complete;
		
		public AgentsHandler(){
			this.complete = false;
		}
		
		@Override
		public void action() {
			try {
				LogicTuple hello = LogicTuple.parse("hello(from(A),node(N))");
				
				final InAll inAll = new InAll(tcid, hello);
				
				TucsonOpCompletionEvent res = WatchdogAgent.this.bridge.synchronousInvocation(inAll, Long.MAX_VALUE, this);
				
				if(res!=null){
					for(LogicTuple lt : res.getTupleList()){
						String agent = lt.getArg(0).getArg(0).toString();
						String node = lt.getArg(1).getArg(0).toString();
						boolean present = false;
						for(String n : nodeAgents.keySet()){
							if(n.equals(node)){
								List<String> as = nodeAgents.get(node);
								as.add(agent);
								nodeAgents.put(node, as);
								present = true;
							}
						}
						if(!present){
							List<String> as = new ArrayList<String>();
							as.add(agent);
							nodeAgents.put(node, as);
						}
					}
					this.complete = true;
					WatchdogAgent.this.bridge.clearTucsonOpResult(this);
				}else{
					this.block();
				}
			} catch (InvalidLogicTupleException | ServiceException e) {
				e.printStackTrace();
				WatchdogAgent.this.doDelete();
			}
		}

		@Override
		public boolean done() {
			return complete;
		}
		
	}
	
	public class PingHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 8902352611496973464L;

		@Override
		public void action() {
			for(String n : nodeAgents.keySet()){
				for(String ag : nodeAgents.get(n)){
					try {
						LogicTuple ping = LogicTuple.parse("ping('"+ ag + "')");
						
						final Out out = new Out(tcid, ping);
						
						WatchdogAgent.this.bridge.asynchronousInvocation(out);
						WatchdogAgent.this.bridge.clearTucsonOpResult(this);
						
					} catch (InvalidLogicTupleException | ServiceException e) {
						e.printStackTrace();
						WatchdogAgent.this.doDelete();
					}
				}
			}
		}
		
	}
	
	public class PongHandler extends Behaviour {

		private static final long serialVersionUID = -4747959074890049919L;

		private boolean complete;
		
		public PongHandler(){
			this.complete = false;
		}
		
		@Override
		public void action() {
			try {
				LogicTuple pong = LogicTuple.parse("pong(A)");
				
				final InAll inAll = new InAll(tcid, pong);
				
				TucsonOpCompletionEvent res = WatchdogAgent.this.bridge.synchronousInvocation(inAll, Long.MAX_VALUE, this);
				
				if(res != null){
					List<String> okAgents = new ArrayList<String>();
					
					for(LogicTuple lt : res.getTupleList()){
						String agent = lt.getArg(0).toString();
						okAgents.add(agent);
					}
					
					for(String n : nodeAgents.keySet()){
						for(String ag : nodeAgents.get(n)){
							if(!okAgents.contains(ag)){
								if(ag.indexOf(0) == 'm'){
									crashedMasters.add(ag);
								}else if(ag.indexOf(0) == 'w'){
									crashedWorkers.add(ag);
								}
							}
						}
					}
					
					this.complete = true;
					WatchdogAgent.this.bridge.clearTucsonOpResult(this);
				}else{
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					this.block();
				}
			} catch (InvalidLogicTupleException | ServiceException e) {
				e.printStackTrace();
				WatchdogAgent.this.doDelete();
			}
		}

		@Override
		public boolean done() {
			return complete;
		}
		
	}
	
	public class RecoveryMastersHandler extends Behaviour {

		private static final long serialVersionUID = 3916695949517290079L;
		
		private int completed;

		public RecoveryMastersHandler(){
			this.completed = 0;
		}
		
		@Override
		public void action() {
			if(!crashedMasters.isEmpty()){
				try {
					LogicTuple waiting = LogicTuple.parse("waiting(who('" + crashedMasters.get(completed) + "'), keyword(K)" + ")");
					
					final InAll inAll = new InAll(tcid, waiting);
					
					TucsonOpCompletionEvent res = WatchdogAgent.this.bridge.synchronousInvocation(inAll, Long.MAX_VALUE, this);
					
					if(res != null){
						this.completed++;
						WatchdogAgent.this.bridge.clearTucsonOpResult(this);
					}else{
						this.block();
					}
				} catch (InvalidLogicTupleException | ServiceException e) {
					e.printStackTrace();
					WatchdogAgent.this.doDelete();
				}
			}
		}

		@Override
		public boolean done() {
			int nMasters = crashedMasters.size();
			if(completed == nMasters){
				crashedMasters.clear();
			}
			return this.completed == nMasters;
		}
	}
	
	public class RecoveryWorkersHandler extends Behaviour {
		
		private static final long serialVersionUID = 2328352115137198379L;
		
		private int completed;

		public RecoveryWorkersHandler(){
			this.completed = 0;
		}
		
		@Override
		public void action() {
			if(!crashedWorkers.isEmpty()){
				try {
					LogicTuple working = LogicTuple.parse("working(who('" + crashedWorkers.get(completed) + "'), keyword(K)," + "for(M)" + ")");
					
					final Inp inp = new Inp(tcid, working);
					
					TucsonOpCompletionEvent res = WatchdogAgent.this.bridge.synchronousInvocation(inp, Long.MAX_VALUE, this);
					
					if(res != null){
						if(!(res.getTuple().getArg(1).getArg(0).toString().equals("K") && 
								res.getTuple().getArg(2).getArg(0).toString().equals("M"))){ //Se è un TupleTemplate ha fallito
							
							String master = res.getTuple().getArg(2).getArg(0).toString();
							
							WatchdogAgent.this.bridge.clearTucsonOpResult(this);
							
							LogicTuple doneWD = LogicTuple.parse("done(who(watchdog)," + "keyword(K)," + "master(" + master + ")" + ")");
							
							final Out out = new Out(tcid, doneWD);
							
							WatchdogAgent.this.bridge.asynchronousInvocation(out);
						}
						
						completed++;
						WatchdogAgent.this.bridge.clearTucsonOpResult(this);
					}else{
						this.block();
					}
				} catch (InvalidLogicTupleException | ServiceException e) {
					e.printStackTrace();
					WatchdogAgent.this.doDelete();
				}
			}
		}

		@Override
		public boolean done() {
			int nWorkers = crashedWorkers.size();
			if(completed == nWorkers){
				crashedWorkers.clear();
			}
			return this.completed == nWorkers;
		}
	}
	
	public String getAgentName() {
		return super.getName();
	}
	
	private void log(final String msg) {
		System.out.println("[" + this.getAgentName() + "]: " + msg);
	}
}
