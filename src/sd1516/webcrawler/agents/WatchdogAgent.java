package sd1516.webcrawler.agents;

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
import alice.tuprolog.Term;
import it.unibo.tucson.jade.exceptions.CannotAcquireACCException;
import it.unibo.tucson.jade.glue.BridgeToTucson;
import it.unibo.tucson.jade.service.TucsonHelper;
import it.unibo.tucson.jade.service.TucsonService;
import jade.core.Agent;
import jade.core.ServiceException;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import sd1516.webcrawler.sysconstants.SysKb;
import sd1516.webcrawler.utils.ValidTermFactory;

public class WatchdogAgent extends Agent {

	private static final long serialVersionUID = 6781364993606695807L;

	private String myIp;
	private String tcIp;
	
	private TucsonHelper helper;
	private BridgeToTucson bridge;
	private TucsonTupleCentreId tcid;
	
	private OneShotBehaviour watchdogBehaviour;
	private AgentsHandler aBehaviour;
	private PingHandler piBehaviour;
	private PongHandler poBehaviour;
	private RecoveryMastersHandler rmBehaviour;
	private RecoveryWorkersHandler rwBehaviour;
	
	private HashMap<String,String> pingAgents;
	private HashMap<String,List<String>> masterAgents;
	private HashMap<String,List<String>> workerAgents;
	private List<String> crashedMasters;
	private List<String> crashedWorkers;
	
	@Override
	public void setup(){
		this.myIp = this.getArguments()[0].toString();
		this.tcIp = this.getArguments()[1].toString();
		
		try {
            this.helper = (TucsonHelper) this.getHelper(TucsonService.NAME);
           
            if (!this.helper.isActive(myIp, SysKb.TUCSON_PORT, SysKb.TUCSON_TIMEOUT)) {
                this.log("Booting local TuCSoN Node on default port...");
                this.helper.startTucsonNode(SysKb.TUCSON_PORT);
            }
            
            this.helper.acquireACC(this);
            
            this.bridge = this.helper.getBridgeToTucson(this);
            
            this.tcid = this.helper.buildTucsonTupleCentreId(SysKb.TUCSON_TC_NAME, tcIp, SysKb.TUCSON_PORT);
            
        } catch (final ServiceException e) {
        	e.printStackTrace();
            this.log(SysKb.TUCSON_SERVICE_EXCEPTION_MSG);
            this.doDelete();
        } catch (final TucsonInvalidAgentIdException e) {
        	e.printStackTrace();
            this.log(SysKb.TUCSON_INVALID_AGENT_ID_EXCEPTION_MSG);
            this.doDelete();
        } catch (final TucsonInvalidTupleCentreIdException e) {
            e.printStackTrace();
            this.doDelete();
        } catch (final CannotAcquireACCException e) {
            e.printStackTrace();
            this.doDelete();
        } catch (final TucsonOperationNotPossibleException e) {
        	e.printStackTrace();
            this.log(SysKb.TUCSON_OPERATION_NOT_POSSIBLE_EXCEPTION_MSG);
            this.doDelete();
        }
		
		this.pingAgents = new HashMap<String,String>();
		this.masterAgents = new HashMap<String,List<String>>();
		this.workerAgents = new HashMap<String,List<String>>();
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
						String agent = ValidTermFactory.getStringByTerm(lt.getArg(0).getArg(0).toTerm());
						String node = ValidTermFactory.getStringByTerm(lt.getArg(1).getArg(0).toTerm());
						if(agent.contains(SysKb.PING_NAME)){
							pingAgents.put(node, agent);
						}else if(agent.contains(SysKb.MASTER_NAME)){
							if(!masterAgents.containsKey(node)){
								masterAgents.put(pingAgents.get(node), new ArrayList<String>());
							}
							masterAgents.get(pingAgents.get(node)).add(agent);
						}else if(agent.contains(SysKb.WORKER_NAME)){
							if(!workerAgents.containsKey(node)){
								workerAgents.put(pingAgents.get(node), new ArrayList<String>());
							}
							workerAgents.get(pingAgents.get(node)).add(agent);
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
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return complete;
		}
		
	}
	
	public class PingHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 8902352611496973464L;

		@Override
		public void action() {
			for(String as : pingAgents.values()){
				try {
					Term who = ValidTermFactory.getTermByString(as);
					LogicTuple ping = LogicTuple.parse("ping("+ who + ")");
						
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
					crashedMasters = new ArrayList<String>();
					crashedWorkers = new ArrayList<String>();
					
					for(LogicTuple lt : res.getTupleList()){
						String agent = ValidTermFactory.getStringByTerm(lt.getArg(0).toTerm());
						okAgents.add(agent);
					}
					
					for(String ag : pingAgents.values()){
						if(!okAgents.contains(ag)){
							crashedMasters.addAll(masterAgents.get(ag));
							crashedWorkers.addAll(workerAgents.get(ag));
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
					Term who = ValidTermFactory.getTermByString(crashedMasters.get(completed));
					LogicTuple waiting = LogicTuple.parse("waiting(who(" + who + "), keyword(K)" + ")");
					
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
					Term who = ValidTermFactory.getTermByString(crashedWorkers.get(completed));
					LogicTuple working = LogicTuple.parse("working(who(" + who + "), keyword(K)," + "for(M)" + ")");
					
					final Inp inp = new Inp(tcid, working);
					
					TucsonOpCompletionEvent res = WatchdogAgent.this.bridge.synchronousInvocation(inp, Long.MAX_VALUE, this);
					
					if(res != null){
						if(!(ValidTermFactory.getStringByTerm(res.getTuple().getArg(1).getArg(0).toTerm()).equals("K") && 
								ValidTermFactory.getStringByTerm(res.getTuple().getArg(2).getArg(0).toTerm()).equals("M"))){
							
							String master = ValidTermFactory.getStringByTerm(res.getTuple().getArg(2).getArg(0).toTerm());
							
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
