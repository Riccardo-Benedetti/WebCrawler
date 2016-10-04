package sd1516.webcrawler.devices;

import java.util.HashMap;
import alice.logictuple.LogicTuple;
import alice.logictuple.exceptions.InvalidLogicTupleException;
import alice.tucson.api.TucsonTupleCentreId;
import alice.tucson.api.exceptions.TucsonInvalidAgentIdException;
import alice.tucson.api.exceptions.TucsonInvalidTupleCentreIdException;
import alice.tucson.api.exceptions.TucsonOperationNotPossibleException;
import alice.tucson.asynchSupport.actions.ordinary.Rdp;
import alice.tucson.service.TucsonOpCompletionEvent;
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

public class AgentsManager extends GuiAgent implements IWebCrawlerGui {

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
		
		this.view = new WebCrawlerAgentManagerView(this);
		this.view.showGui();
	}

	private class AddMasterHandler extends OneShotBehaviour {
		
		private static final long serialVersionUID = 1646415705381572773L;

		@Override
		public void action() {
			boolean success = false;
			String agentName = "";
			
			try {
				agentName = "m"+addedMasters+"r"+raspiId;
				AgentController ac = rc.createNewAgent(agentName, "sd1516.webcrawler.master.WebCrawlerMaster", new Object[]{myIp, tcIp});
				addedMasters++;
				ac.start();
				AgentsManager.this.masters.put(agentName, ac);
				success = true;
			} catch (StaleProxyException e) {
				e.printStackTrace();
			}
			
			AgentsManager.this.view.updateView(success, agentName, WebCrawlerAgentManagerView.ADDMASTER);
		}
	}

	private class RemoveMasterHandler extends Behaviour {
		private static final long serialVersionUID = -3946208213835102803L;

		private String agentName;
		private boolean complete;
		
		public RemoveMasterHandler(String agentName) {
			this.agentName = agentName;
			this.complete = false;
		}

		@Override
		public void action() {
			LogicTuple waiting = null;
			
			try {
				waiting = LogicTuple.parse("waiting("+ "who('" + this.agentName+"@WebCrawlerPlatform" + "')," + "keyword(K)" + ")");				
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				AgentsManager.this.doDelete();
			}
			
			TucsonOpCompletionEvent rm = null;
			Rdp rdp = new Rdp(AgentsManager.this.tcid, waiting);
			
			try {
				rm = AgentsManager.this.bridge.synchronousInvocation(rdp, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				AgentsManager.this.doDelete();
			}
			
			if(rm != null){
				String keyword = rm.getTuple().getArg(1).getArg(0).toString();
				if(!keyword.equals("K")){
					AgentsManager.this.view.updateView(false, this.agentName, WebCrawlerAgentManagerView.REMOVEMASTER);
				}else{
					try {
						AgentsManager.this.masters.get(agentName).kill();
					} catch (StaleProxyException e) {
						e.printStackTrace();
						AgentsManager.this.doDelete();
					}
					AgentsManager.this.masters.remove(agentName);
					AgentsManager.this.view.updateView(true, this.agentName, WebCrawlerAgentManagerView.REMOVEMASTER);
				}
				AgentsManager.this.bridge.clearTucsonOpResult(this);
				this.complete = true;
			}else{
				this.block();
			}
		}
		
		@Override
		public boolean done(){
			return this.complete;
		}
	}

	private class AddWorkerHandler extends OneShotBehaviour {
		
		private static final long serialVersionUID = -1065051638657132090L;

		@Override
		public void action() {
			boolean success = false;
			String agentName = "";
			
			try {
				agentName = "w"+addedWorkers+"r"+raspiId;
				AgentController ac = rc.createNewAgent(agentName, "sd1516.webcrawler.worker.WebCrawlerWorker", new Object[]{myIp, tcIp});
				addedWorkers++;
				ac.start();
				AgentsManager.this.workers.put(agentName, ac);
				success = true;
			} catch (StaleProxyException e) {
				e.printStackTrace();
			}
			
			AgentsManager.this.view.updateView(success, agentName, WebCrawlerAgentManagerView.ADDWORKER);
		}
	}

	private class RemoveWorkerHandler extends Behaviour {
		
		private static final long serialVersionUID = 3494054411702657298L;

		private String agentName;
		private boolean complete;
		
		public RemoveWorkerHandler(String agentName) {
			this.complete = false;
			this.agentName = agentName;
		}

		@Override
		public void action() {
			LogicTuple working = null;
			
			try {
				working = LogicTuple.parse("working("+ "who('" + this.agentName + "')," + "keyword(K)," + "for(M)" + ")");				
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				AgentsManager.this.doDelete();
			}
			
			TucsonOpCompletionEvent rw = null;
			Rdp rdp = new Rdp(AgentsManager.this.tcid, working);
			
			try {
				rw = AgentsManager.this.bridge.synchronousInvocation(rdp, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				AgentsManager.this.doDelete();
			}
			
			if(rw != null){
				String keyword = rw.getTuple().getArg(1).getArg(0).toString();
				if(!keyword.equals("K")){
					AgentsManager.this.view.updateView(false, this.agentName, WebCrawlerAgentManagerView.REMOVEWORKER);
				}else{
					try {
						AgentsManager.this.workers.get(agentName).kill();
					} catch (StaleProxyException e) {
						e.printStackTrace();
						AgentsManager.this.doDelete();
					}
					AgentsManager.this.workers.remove(agentName);
					AgentsManager.this.view.updateView(true, this.agentName, WebCrawlerAgentManagerView.REMOVEWORKER);
				}
				AgentsManager.this.bridge.clearTucsonOpResult(this);
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
			this.doDelete(); //Termina l'agente alla chiusura della GUI (distruggendo il Thread)
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