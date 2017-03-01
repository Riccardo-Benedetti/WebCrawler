package sd1516.webcrawler.agents;

import java.util.HashMap;
import alice.logictuple.LogicTuple;
import alice.logictuple.exceptions.InvalidLogicTupleException;
import alice.tucson.api.TucsonTupleCentreId;
import alice.tucson.api.exceptions.TucsonInvalidAgentIdException;
import alice.tucson.api.exceptions.TucsonInvalidTupleCentreIdException;
import alice.tucson.api.exceptions.TucsonOperationNotPossibleException;
import alice.tucson.asynchSupport.actions.ordinary.Rdp;
import alice.tucson.service.TucsonOpCompletionEvent;
import alice.tuprolog.Term;
import it.unibo.tucson.jade.exceptions.CannotAcquireACCException;
import it.unibo.tucson.jade.glue.BridgeToTucson;
import it.unibo.tucson.jade.service.TucsonHelper;
import it.unibo.tucson.jade.service.TucsonService;
import jade.core.ServiceException;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.gui.GuiAgent;
import jade.gui.GuiEvent;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import sd1516.webcrawler.gui.WebCrawlerAgentManagerView;
import sd1516.webcrawler.interfaces.IWebCrawlerGui;
import sd1516.webcrawler.sysconstants.SysKb;
import sd1516.webcrawler.utils.ValidTermFactory;

public class ManagerAgent extends GuiAgent implements IWebCrawlerGui {

	private static final long serialVersionUID = 5311512076350771813L;
	
	private int raspiId;
	private String myIp;
	private String tcIp;
	
	private TucsonHelper helper;
	private BridgeToTucson bridge;
	private TucsonTupleCentreId tcid;
	
	private ContainerController rc;
	private int addedMasters, addedWorkers;
	private HashMap<String,AgentController> masters;
	private HashMap<String,AgentController> workers;
	
	private AddMasterHandler addMasterBehaviour;
	private RemoveMasterHandler removeMasterBehaviour;
	private AddWorkerHandler addWorkerBehaviour;
	private RemoveWorkerHandler removeWorkerBehaviour;
	
	private WebCrawlerAgentManagerView view;
	
	private boolean complete;
	
	@Override
	public void setup(){
		this.raspiId = Integer.parseInt(getArguments()[0].toString());
		this.myIp = this.getArguments()[1].toString();
		this.tcIp = this.getArguments()[2].toString();
		this.rc = (ContainerController) this.getArguments()[3];
		
		this.addedMasters = 0;
		this.addedMasters = 0;
		
		this.masters = new HashMap<String,AgentController>();
		this.workers = new HashMap<String,AgentController>();
		
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
		
		this.view = new WebCrawlerAgentManagerView(this);
		this.view.showGui();
	}

	private class AddMasterHandler extends OneShotBehaviour {
		
		private static final long serialVersionUID = 1646415705381572773L;

		@Override
		public void action() {
			boolean success = false;
			String agentToAdd = "";
			
			try {
				agentToAdd = SysKb.MASTER_NAME + addedMasters + SysKb.RASPI_NAME + raspiId;
				AgentController ac = rc.createNewAgent(agentToAdd, SysKb.MASTER_AGENT, new Object[]{myIp, tcIp});
				addedMasters++;
				ac.start();
				ManagerAgent.this.masters.put(agentToAdd, ac);
				success = true;
			} catch (StaleProxyException e) {
				e.printStackTrace();
			}
			
			ManagerAgent.this.view.updateView(success, agentToAdd, WebCrawlerAgentManagerView.ADDMASTER);
		}
	}

	private class RemoveMasterHandler extends Behaviour {
		private static final long serialVersionUID = -3946208213835102803L;

		private String agentToRemove;
		
		public RemoveMasterHandler(String agentToRemove) {
			this.agentToRemove = agentToRemove;
			ManagerAgent.this.complete = false;
		}

		@Override
		public void action() {
			LogicTuple waiting = null;
			
			try {
				Term who = ValidTermFactory.getTermByString(this.agentToRemove+"@"+SysKb.PLATFORM);
				waiting = LogicTuple.parse("waiting("+ "who(" + who + ")," + "keyword(K)" + ")");				
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				ManagerAgent.this.doDelete();
			}
			
			TucsonOpCompletionEvent rm = null;
			Rdp rdp = new Rdp(ManagerAgent.this.tcid, waiting);
			
			try {
				rm = ManagerAgent.this.bridge.synchronousInvocation(rdp, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				ManagerAgent.this.doDelete();
			}
			
			if(rm != null){
				String keyword = ValidTermFactory.getStringByTerm(rm.getTuple().getArg(1).getArg(0).toTerm());
				if(!keyword.equals("K")){
					ManagerAgent.this.view.updateView(false, this.agentToRemove, WebCrawlerAgentManagerView.REMOVEMASTER);
				}else{
					try {
						ManagerAgent.this.masters.get(agentToRemove).kill();
					} catch (StaleProxyException e) {
						e.printStackTrace();
						ManagerAgent.this.doDelete();
					}
					ManagerAgent.this.masters.remove(agentToRemove);
					ManagerAgent.this.view.updateView(true, this.agentToRemove, WebCrawlerAgentManagerView.REMOVEMASTER);
				}
				ManagerAgent.this.bridge.clearTucsonOpResult(this);
				ManagerAgent.this.complete = true;
			}else{
				this.block();
			}
		}
		
		@Override
		public boolean done(){
			boolean c = ManagerAgent.this.complete;
			ManagerAgent.this.complete = false;
			return c;
		}
	}

	private class AddWorkerHandler extends OneShotBehaviour {
		
		private static final long serialVersionUID = -1065051638657132090L;

		@Override
		public void action() {
			boolean success = false;
			String agentToAdd = "";
			
			try {
				agentToAdd = SysKb.WORKER_NAME + addedWorkers + SysKb.RASPI_NAME + raspiId;
				AgentController ac = rc.createNewAgent(agentToAdd, SysKb.WORKER_AGENT, new Object[]{myIp, tcIp});
				addedWorkers++;
				ac.start();
				ManagerAgent.this.workers.put(agentToAdd, ac);
				success = true;
			} catch (StaleProxyException e) {
				e.printStackTrace();
			}
			
			ManagerAgent.this.view.updateView(success, agentToAdd, WebCrawlerAgentManagerView.ADDWORKER);
		}
	}

	private class RemoveWorkerHandler extends Behaviour {
		
		private static final long serialVersionUID = 3494054411702657298L;

		private String agentToRemove;
		
		public RemoveWorkerHandler(String agentToRemove) {
			ManagerAgent.this.complete = false;
			this.agentToRemove = agentToRemove;
		}

		@Override
		public void action() {
			LogicTuple working = null;
			
			try {
				Term who = ValidTermFactory.getTermByString(this.agentToRemove+"@"+SysKb.PLATFORM);
				working = LogicTuple.parse("working("+ "who(" + who + ")," + "keyword(K)," + "for(M)" + ")");				
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				ManagerAgent.this.doDelete();
			}
			
			TucsonOpCompletionEvent rw = null;
			Rdp rdp = new Rdp(ManagerAgent.this.tcid, working);
			
			try {
				rw = ManagerAgent.this.bridge.synchronousInvocation(rdp, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				ManagerAgent.this.doDelete();
			}
			
			if(rw != null){
				String keyword = ValidTermFactory.getStringByTerm(rw.getTuple().getArg(1).getArg(0).toTerm());
				if(!keyword.equals("K")){
					ManagerAgent.this.view.updateView(false, this.agentToRemove, WebCrawlerAgentManagerView.REMOVEWORKER);
				}else{
					try {
						ManagerAgent.this.workers.get(agentToRemove).kill();
					} catch (StaleProxyException e) {
						e.printStackTrace();
						ManagerAgent.this.doDelete();
					}
					ManagerAgent.this.workers.remove(agentToRemove);
					ManagerAgent.this.view.updateView(true, this.agentToRemove, WebCrawlerAgentManagerView.REMOVEWORKER);
				}
				ManagerAgent.this.bridge.clearTucsonOpResult(this);
				ManagerAgent.this.complete = true;
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			boolean c = ManagerAgent.this.complete;
			ManagerAgent.this.complete = false;
			return c;
		}
	}
	
	@Override
	public String getAgentName() {
		return super.getName();
	}
	
	@Override
	protected void onGuiEvent(GuiEvent ev) {
		this.view.disableInput();
		
		if(ev.getType() == WebCrawlerAgentManagerView.ADDMASTER){
			this.addMasterBehaviour = new AddMasterHandler();
			this.addBehaviour(this.addMasterBehaviour);
		}else if(ev.getType() == WebCrawlerAgentManagerView.REMOVEMASTER){
			this.removeMasterBehaviour = new RemoveMasterHandler(ev.getParameter(0).toString());
			this.addBehaviour(this.removeMasterBehaviour);
		}else if(ev.getType() == WebCrawlerAgentManagerView.ADDWORKER){
			this.addWorkerBehaviour = new AddWorkerHandler();
			this.addBehaviour(this.addWorkerBehaviour);
		}else if(ev.getType() == WebCrawlerAgentManagerView.REMOVEWORKER){
			this.removeWorkerBehaviour = new RemoveWorkerHandler(ev.getParameter(0).toString());
			this.addBehaviour(this.removeWorkerBehaviour);
		}else if(ev.getType() == WebCrawlerAgentManagerView.KILL){
			this.doDelete();
		}else{
			this.log("Unknown GUI event, terminating...");
			this.view.dispose();
			this.doDelete();
		}

		this.view.enableInput();
	}
	
	private void log(final String msg) {
		System.out.println("[" + this.getAgentName() + "]: " + msg);
	}
}