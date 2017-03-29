package sd1516.webcrawler.agents;

/**
 * DISTRIBUTED, FAULT-TOLERANT WEB CRAWLING WITH RASPI
 * 
 * @page https://apice.unibo.it/xwiki/bin/view/Courses/Sd1516Projects-CrawlingRaspiRamilliBenedetti
 * 
 * @author Riccardo Benedetti & Elisabetta Ramilli
 * @email riccardo.benedetti3@studio.unibo.it
 * @email elisabetta.ramilli@studio.unibo.it
 * 
 * Alma Mater Studiorum - Università di Bologna
 * Laurea Magistrale in Ingegneria e Scienze Informatiche
 * (Corso di Sistemi Distribuiti - Prof. Andrea Omicini & Stefano Mariani)
 * 
 */

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

/*
 * WATCHDOG AGENT
 * This Agent is essential to guarantee all the network fault tolerance and it is 
 * supposed that it will never crash.
 * It keeps track of all the nodes registered in the system, and their own Agents.
 * It periodically checks that each node is effectively working correctly by 
 * exchanging Ping-Pong tuples with their relative Ping Agents.
 * If it doesn't receive no answer from a particular Ping Agent, it triggers
 * a fault tolerance procedure to exclude the crashed node (and its Agents),
 * from the system without compromising the overall system integrity.
 */
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
	
	private HashMap<String,String> pingAgents; // Key = node IP, Value = agent name
	private HashMap<String,List<String>> masterAgents; // Key = node IP, Value = master agent names
	private HashMap<String,List<String>> workerAgents; // Key = node IP, Value = worker agent names
	
	private List<String> crashedMasters;
	private List<String> crashedWorkers;
	
	private boolean complete;
	
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
				
				// Check for new nodes/Agents...
				fsm.registerDefaultTransition("AgentsHandler", "PingHandler");
				// ...then ping all the nodes and wait 2 seconds...
				fsm.registerDefaultTransition("PingHandler", "PongHandler");
				// ...then get all the pong responses...
				fsm.registerDefaultTransition("PongHandler", "RecoveryMastersHandler");
				// ...then recover potential crashed masters...
				fsm.registerDefaultTransition("RecoveryMastersHandler", "RecoveryWorkersHandler");
				// ...then recover potential crashed workers...
				fsm.registerDefaultTransition("RecoveryWorkersHandler", "AgentsHandler");
				// ...finally restart the cycle
			}
		};
		
		this.addBehaviour(watchdogBehaviour);
	}
	
	/*
	 * System registration rules: 
	 * each new Agent must say "Hello!" to the Watchdog Agent specifying
	 * its name and the node it belongs to
	 */
	public class AgentsHandler extends Behaviour {

		private static final long serialVersionUID = 1014719482055275645L;
		
		public AgentsHandler(){
			WatchdogAgent.this.complete = false;
		}
		
		@Override
		public void action() {
			try {
				LogicTuple hello = LogicTuple.parse("hello(from(A),node(N))");
				
				// get and remove all the "Hellos"
				final InAll inAll = new InAll(tcid, hello);
				
				TucsonOpCompletionEvent res = WatchdogAgent.this.bridge.synchronousInvocation(inAll, Long.MAX_VALUE, this);
				
				if(res!=null){
					for(LogicTuple lt : res.getTupleList()){
						String agent = ValidTermFactory.getStringByTerm(lt.getArg(0).getArg(0).toTerm());
						String node = ValidTermFactory.getStringByTerm(lt.getArg(1).getArg(0).toTerm());
						
						// is that a Ping Agent? That means there is a new node in the system.
						// Creating a new key entry for each HashMap structure
						if(agent.contains(SysKb.PING_NAME)){
							pingAgents.put(node, agent); // new node and ping name
							masterAgents.put(node, new ArrayList<String>()); // initially none masters
							workerAgents.put(node, new ArrayList<String>()); // initially none workers
						}
						// is that a Master Agent? That means that its node is already registered.
						// Appending new Master to the corresponding list value.
						else if(agent.contains(SysKb.MASTER_NAME)){
							List<String> m = masterAgents.get(node); 
							m.add(agent);
							masterAgents.put(node,m); // 
						}
						// is that a Worker Agent? That means that its node is already registered.
						// Appending new Worker to the corresponding list value.
						else if(agent.contains(SysKb.WORKER_NAME)){
							List<String> w = workerAgents.get(node);
							w.add(agent);
							workerAgents.put(node,w);
						}
					}
					WatchdogAgent.this.complete = true;
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
			return WatchdogAgent.this.complete;
		}
		
	}
	
	/*
	 * Ping all the nodes and wait 2 seconds for the responses
	 */
	public class PingHandler extends Behaviour {

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
					
					WatchdogAgent.this.complete = false;
						
				} catch (InvalidLogicTupleException | ServiceException e) {
					e.printStackTrace();
					WatchdogAgent.this.doDelete();
				}
			}
		}

		@Override
		public boolean done() {
			try {
				// we have to provide a large time to allows the nodes
				// to handle the pings and elaborate the pongs
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return true;
		}
	}
	
	/*
	 * Get all of Pongs in the tuple space
	 */
	public class PongHandler extends Behaviour {

		private static final long serialVersionUID = -4747959074890049919L;
		
		@Override
		public void action() {
			try {
				LogicTuple pong = LogicTuple.parse("pong(A)");
				
				final InAll inAll = new InAll(tcid, pong);
				
				TucsonOpCompletionEvent res = WatchdogAgent.this.bridge.synchronousInvocation(inAll, Long.MAX_VALUE, this);
				
				if(res != null){
					// preparing the structure for the checked Agents
					List<String> okAgents = new ArrayList<String>();
					
					// preparing the structures for the unreachable Agents
					crashedMasters = new ArrayList<String>();
					crashedWorkers = new ArrayList<String>();
					
					for(LogicTuple lt : res.getTupleList()){
						String agent = ValidTermFactory.getStringByTerm(lt.getArg(0).toTerm());
						okAgents.add(agent);
					}
					
					for(String node : pingAgents.keySet()){
						// A Ping Agent has not answered?
						if(!okAgents.contains(pingAgents.get(node))){
							// Which masters and workers belong to it? Add them to the crashed lists
							crashedMasters.addAll(masterAgents.get(node));
							crashedWorkers.addAll(workerAgents.get(node));
							pingAgents.remove(node);
						}
					}
					
					WatchdogAgent.this.complete = true;
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
			return WatchdogAgent.this.complete;
		}
		
	}
	
	/*
	 * Remove the crashed masters before the workers, to avoid the potentiality
	 * that a worker could see the Waiting Tuple of a crashed master
	 */
	public class RecoveryMastersHandler extends Behaviour {

		private static final long serialVersionUID = 3916695949517290079L;
		
		private int completed; // repeat the behavior for each crashed master
		
		// for each crashed master we have to perform 2 removal:
		// first the Waiting tuples and then the Keyword tuples
		private boolean secondRemoval;

		public RecoveryMastersHandler(){
			this.completed = 0;
			this.secondRemoval = false;
		}
		
		@Override
		public void action() {
			if(!crashedMasters.isEmpty()){
				try { // For each crashed master...
					Term who = ValidTermFactory.getTermByString(crashedMasters.get(completed));
					TucsonOpCompletionEvent res;
					
					if(!secondRemoval){
						// ...remove all the potential Waiting tuples...
						LogicTuple waiting = LogicTuple.parse("waiting(who(" + who + "), keyword(K)" + ")");
						
						final InAll inAllW = new InAll(tcid, waiting);
						
						res = WatchdogAgent.this.bridge.synchronousInvocation(inAllW, Long.MAX_VALUE, this);
					}else{
						// ...and then remove all the potential Keyword tuples...
						LogicTuple keyword = LogicTuple.parse("keyword(value(K), from(" + who + ")" + ")");
						
						final InAll inAllK = new InAll(tcid, keyword);
						
						res = WatchdogAgent.this.bridge.synchronousInvocation(inAllK, Long.MAX_VALUE, this);
					}
					if(res != null){
						WatchdogAgent.this.bridge.clearTucsonOpResult(this);
						if(secondRemoval){
							this.completed++;
						}
						secondRemoval = !secondRemoval;
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
			int c = this.completed;
			if(completed == nMasters){
				this.completed = 0;
			}
			return c == nMasters;
		}
	}
	
	/*
	 * Remove the crashed workers
	 */
	public class RecoveryWorkersHandler extends Behaviour {
		
		private static final long serialVersionUID = 2328352115137198379L;
		
		private int completed; // repeat the behavior for each crashed worker

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
						// ...remove all the potential Waiting tuples...
						
						//...but what if a master is waiting just for these workers?...
						if(!(ValidTermFactory.getStringByTerm(res.getTuple().getArg(1).getArg(0).toTerm()).equals("K") && 
								ValidTermFactory.getStringByTerm(res.getTuple().getArg(2).getArg(0).toTerm()).equals("M"))){
							
							//...first get the master name...
							String master = ValidTermFactory.getStringByTerm(res.getTuple().getArg(2).getArg(0).toTerm());
							
							//...then send him a Done tuple replacing the one that was supposed to be sent by the crashed worker
							if(!crashedMasters.contains(master)){
								WatchdogAgent.this.bridge.clearTucsonOpResult(this);
								
								// the master will detect that this Done tuple has arrived from Watchdog and will handle it differently
								LogicTuple doneWD = LogicTuple.parse("done(who(watchdog)," + "keyword(K)," + "master(" + master + ")" + ")");
								
								final Out out = new Out(tcid, doneWD);
								
								WatchdogAgent.this.bridge.asynchronousInvocation(out);
							}
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
			int c = this.completed;
			if(completed == nWorkers){
				crashedWorkers.clear();
				crashedMasters.clear();
				this.completed = 0;
			}
			return c == nWorkers;
		}
	}
	
	public String getAgentName() {
		return super.getName();
	}
	
	private void log(final String msg) {
		System.out.println("[" + this.getAgentName() + "]: " + msg);
	}
}
