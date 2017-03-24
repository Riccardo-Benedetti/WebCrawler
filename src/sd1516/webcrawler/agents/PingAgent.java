package sd1516.webcrawler.agents;

import alice.logictuple.LogicTuple;
import alice.logictuple.exceptions.InvalidLogicTupleException;
import alice.tucson.api.TucsonTupleCentreId;
import alice.tucson.api.exceptions.TucsonInvalidAgentIdException;
import alice.tucson.api.exceptions.TucsonInvalidTupleCentreIdException;
import alice.tucson.api.exceptions.TucsonOperationNotPossibleException;
import alice.tucson.asynchSupport.actions.ordinary.In;
import alice.tucson.asynchSupport.actions.ordinary.Out;
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
 * PING AGENT
 * This Agent is responsible to guarantee the network fault tolerance.
 * Each node has a Ping Agent that interact with the centralized Watchdog Agent by
 * exchanging with him periodic Ping-Pong tuples.
 */
public class PingAgent extends Agent {
	
	private static final long serialVersionUID = 1669384818587433845L;
	
	private String myIp; 
	private String tcIp;
	
	private TucsonHelper helper;
	private BridgeToTucson bridge;
	private TucsonTupleCentreId tcid;
	
	private OneShotBehaviour pingPongBehaviour;
	private HelloHandler helloBehaviour;
	private PingHandler pingBehaviour;
	private PongHandler pongBehaviour;
	
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
		
		this.pingPongBehaviour = new OneShotBehaviour(this){

			private static final long serialVersionUID = 4170863694975116557L;

			@Override
			public void action() {
				final FSMBehaviour fsm = new FSMBehaviour(this.myAgent);
				this.configureFSM(fsm);
				this.myAgent.addBehaviour(fsm);
			}

			private void configureFSM(FSMBehaviour fsm) {
				helloBehaviour = new HelloHandler();
				pingBehaviour = new PingHandler();
				pongBehaviour = new PongHandler();
				fsm.registerFirstState(helloBehaviour, "HelloHandler");
				fsm.registerState(pingBehaviour, "PingHandler");
				fsm.registerState(pongBehaviour, "PongHandler");
				
				// First of all say "Hello" to Watchdog Agent...
				fsm.registerDefaultTransition("HelloHandler", "PingHandler");
				// ...then get the Ping...
				fsm.registerDefaultTransition("PingHandler", "PongHandler");
				// ...then throw the Pong...
				fsm.registerDefaultTransition("PongHandler", "PingHandler");
				// ...then get next Ping... and so on...
			}
		};
		
		this.addBehaviour(pingPongBehaviour);
	}
	
	/*
	 * System registration rules: 
	 * each new Agent must say "Hello!" to the Watchdog Agent specifying
	 * it name and the node it belongs
	 */
	private class HelloHandler extends OneShotBehaviour{

		private static final long serialVersionUID = -2347267322990525065L;
		
		@Override
		public void action() {
			LogicTuple hello;
			
			try {
				Term me = ValidTermFactory.getTermByString(PingAgent.this.getAgentName());
				Term host = ValidTermFactory.getTermByString(myIp);
				
				hello = LogicTuple.parse("hello("+ "from("+ me +")," + "node("+ host +")" + ")");
				
				PingAgent.this.log("Hello from " + me + " (ip: " + myIp + ")");
				
				final Out out = new Out(tcid, hello);
				PingAgent.this.bridge.asynchronousInvocation(out);
				PingAgent.this.bridge.clearTucsonOpResult(this);
				
			} catch (InvalidLogicTupleException | ServiceException e) {
	            e.printStackTrace();
	            PingAgent.this.doDelete();
	        }
		}
	}
	
	/*
	 * Get the Ping tuple from the Tuple Space
	 */
	private class PingHandler extends Behaviour{

		private static final long serialVersionUID = 5114937424007904657L;
		
		public PingHandler(){
			PingAgent.this.complete = false;
		}
		
		@Override
		public void action() {
			LogicTuple ping = null;
			
			// Get only the Ping tuple addressed to me
			try {
				Term me = ValidTermFactory.getTermByString(PingAgent.this.getAgentName());
				ping = LogicTuple.parse("ping(" + me + ")");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				PingAgent.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			In in = new In(PingAgent.this.tcid, ping);
			
			try {
				res = PingAgent.this.bridge.synchronousInvocation(in, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				PingAgent.this.doDelete();
			}
			
			if(res != null){
				PingAgent.this.complete = true;
				PingAgent.this.bridge.clearTucsonOpResult(this);
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return PingAgent.this.complete;
		}
	}
	
	/*
	 * Throw the Pong tuple to the Tuple Space
	 */
	private class PongHandler extends OneShotBehaviour{

		private static final long serialVersionUID = 5421907147009755279L;
		
		@Override
		public void action() {
			LogicTuple pong;
			
			// it is importa to include my "signature" to the Pong tuple
			try {
				Term me = ValidTermFactory.getTermByString(PingAgent.this.getAgentName());
				pong = LogicTuple.parse("pong(" + me + ")");
				
				final Out out = new Out(PingAgent.this.tcid, pong);
				PingAgent.this.bridge.asynchronousInvocation(out);
				PingAgent.this.bridge.clearTucsonOpResult(this);
				
				PingAgent.this.complete = false;
				
			} catch (InvalidLogicTupleException | ServiceException e) {
				e.printStackTrace();
				PingAgent.this.doDelete();
			}	
		}
	}
	
	public String getAgentName() {
		return super.getName();
	}
	
	private void log(final String msg) {
		System.out.println("[" + this.getAgentName() + "]: " + msg);
	}
}