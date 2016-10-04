package sd1516.webcrawler.devices;

import alice.logictuple.LogicTuple;
import alice.logictuple.exceptions.InvalidLogicTupleException;
import alice.tucson.api.TucsonTupleCentreId;
import alice.tucson.api.exceptions.TucsonInvalidAgentIdException;
import alice.tucson.api.exceptions.TucsonInvalidTupleCentreIdException;
import alice.tucson.api.exceptions.TucsonOperationNotPossibleException;
import alice.tucson.asynchSupport.actions.ordinary.In;
import alice.tucson.asynchSupport.actions.ordinary.Out;
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

public class PingAgent extends Agent {
	
	private static final long serialVersionUID = 1669384818587433845L;
	
	private String myIp; 
	private String tcIp;
	
	private TucsonHelper helper;
	private BridgeToTucson bridge;
	private TucsonTupleCentreId tcid;
	
	private OneShotBehaviour pingPongBehaviour;
	private PingHandler pingBehaviour;
	private PongHandler pongBehaviour;
	
	@Override
	public void setup(){
		this.myIp = this.getArguments()[0].toString();
		this.tcIp = this.getArguments()[1].toString();
		
		try {
            /*
             * First of all, get the helper for the service you want to exploit
             */
            this.helper = (TucsonHelper) this.getHelper(TucsonService.NAME);
            /*
             * Then, start a TuCSoN Node (if not already up) as the actual
             * executor of the service
             */
            if (!this.helper.isActive(myIp, 20504, 10000)) {
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
            		tcIp, 20504);
            
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
		
		this.pingPongBehaviour = new OneShotBehaviour(this){

			private static final long serialVersionUID = 4170863694975116557L;

			@Override
			public void action() {
				final FSMBehaviour fsm = new FSMBehaviour(this.myAgent);
				this.configureFSM(fsm);
				this.myAgent.addBehaviour(fsm);
			}

			private void configureFSM(FSMBehaviour fsm) {
				pingBehaviour = new PingHandler();
				pongBehaviour = new PongHandler();
				fsm.registerFirstState(pingBehaviour, "PingHandler");
				fsm.registerLastState(pongBehaviour, "PongHandler");
				fsm.registerDefaultTransition("PingHandler", "PongHandler");
				fsm.registerDefaultTransition("PongHandler", "PingHandler");
			}
		};
		
		this.addBehaviour(pingPongBehaviour);
	}
	
	private class PingHandler extends Behaviour{

		private static final long serialVersionUID = 5114937424007904657L;

		private boolean complete;
		
		public PingHandler(){
			this.complete = false;
		}
		
		@Override
		public void action() {
			LogicTuple ping = null;
			
			try {
				ping = LogicTuple.parse("ping(" + PingAgent.this.getAgentName() + ")");
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
				this.complete = true;
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return this.complete;
		}
	}
	
	private class PongHandler extends OneShotBehaviour{

		private static final long serialVersionUID = 5421907147009755279L;
		
		@Override
		public void action() {
			LogicTuple pong;
			
			try {
				pong = LogicTuple.parse("pong(" + PingAgent.this.getAgentName() + ")");
				
				final Out out = new Out(PingAgent.this.tcid, pong);
				PingAgent.this.bridge.asynchronousInvocation(out);
				PingAgent.this.bridge.clearTucsonOpResult(this);
				
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