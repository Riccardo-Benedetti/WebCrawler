package sd1516.webcrawler.master;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import alice.logictuple.LogicTuple;
import alice.logictuple.exceptions.InvalidLogicTupleException;
import alice.tucson.api.TucsonTupleCentreId;
import alice.tucson.api.exceptions.TucsonInvalidAgentIdException;
import alice.tucson.api.exceptions.TucsonInvalidTupleCentreIdException;
import alice.tucson.api.exceptions.TucsonOperationNotPossibleException;
import alice.tucson.asynchSupport.actions.ordinary.In;
import alice.tucson.asynchSupport.actions.ordinary.Out;
import alice.tucson.asynchSupport.actions.ordinary.bulk.InAll;
import alice.tucson.service.TucsonOpCompletionEvent;
import alice.tuprolog.Struct;
import it.unibo.tucson.jade.exceptions.CannotAcquireACCException;
import it.unibo.tucson.jade.glue.BridgeToTucson;
import it.unibo.tucson.jade.service.TucsonHelper;
import it.unibo.tucson.jade.service.TucsonService;
import jade.core.ServiceException;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.gui.GuiAgent;
import jade.gui.GuiEvent;
import sd1516.webcrawler.gui.WebCrawlerMasterView;
import sd1516.webcrawler.interfaces.IWebCrawlerGui;
import sd1516.webcrawler.utils.Publication;
import sd1516.webcrawler.utils.ValidTermFactory;

public class WebCrawlerMaster extends GuiAgent implements IWebCrawlerGui {

	private static final long serialVersionUID = 8507792839728793556L;
	
	private final String myIp = "localhost";//getArguments()[0].toString();
	private final String tcIp = "localhost";//getArguments()[1].toString();
	
	private WebCrawlerMasterView view;
	private String[] keywords;
	private HashMap<Publication, Integer> publOccs; //Occorrenze di ogni Pubblicazione
	private int completed;
	private boolean workerError, waitingsRemoved;
	
	private HelloHandler helloBehaviour;
	private OneShotBehaviour masterBehaviour;
	private KeywordsHandler kBehaviour;
	private DoneHandler dBehaviour;
	private ResultsHandler rBehaviour;
	private RemWaitingsHandler rwBehaviour;
	private UpdateViewHandler uvBehaviour;
	
	private TucsonHelper helper;
	private BridgeToTucson bridge;
	private TucsonTupleCentreId tcid;
	
	private class HelloHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 4533549206646776775L;

		@Override
		public void action() {
			LogicTuple hello;
			
			try{
				hello = LogicTuple.parse("hello("+ "from('"+ WebCrawlerMaster.this.getAgentName() +"')," + "node('"+ myIp +"')" + ")");
				
				WebCrawlerMaster.this.log("Hello from " + WebCrawlerMaster.this.getAgentName() + " (ip: " + myIp + ")");
				
				final Out out = new Out(tcid, hello);
				WebCrawlerMaster.this.bridge.asynchronousInvocation(out);
				WebCrawlerMaster.this.bridge.clearTucsonOpResult(this);
				
			}catch (final InvalidLogicTupleException e) {
	            e.printStackTrace();
	            WebCrawlerMaster.this.doDelete();
	        } catch (final ServiceException e) {
	        	WebCrawlerMaster.this
	                    .log(">>> No TuCSoN service active, reboot JADE with -services it.unibo.tucson.jade.service.TucsonService option <<<");
	        	WebCrawlerMaster.this.doDelete();
	        }
		}
		
	}
	
	private class KeywordsHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 2994251203600300007L;

		@Override
		public void action() {
			completed = keywords.length;
			
			for(String k : keywords){
				LogicTuple keyword;
				LogicTuple waiting;
				
				try{
					keyword = LogicTuple.parse("keyword("+ "value('"+ k +"')," + "from('"+
							WebCrawlerMaster.this.getAgentName() +"')" + ")");
					waiting = LogicTuple.parse("waiting("+ "who('"+ WebCrawlerMaster.this.getAgentName() +"')," + "keyword('"+
							k +"')" + ")");
					WebCrawlerMaster.this.log("Sending keyword ("+ k +") to TupleCentre");
					
					final Out outK = new Out(WebCrawlerMaster.this.tcid, keyword);
					WebCrawlerMaster.this.bridge.asynchronousInvocation(outK);
					WebCrawlerMaster.this.bridge.clearTucsonOpResult(this);
					
					final Out outW = new Out(WebCrawlerMaster.this.tcid, waiting);
					WebCrawlerMaster.this.bridge.asynchronousInvocation(outW);
					WebCrawlerMaster.this.bridge.clearTucsonOpResult(this);
					
				}catch (final InvalidLogicTupleException e) {
	                e.printStackTrace();
	                WebCrawlerMaster.this.doDelete();
	            } catch (final ServiceException e) {
	            	WebCrawlerMaster.this
	                        .log(">>> No TuCSoN service active, reboot JADE with -services it.unibo.tucson.jade.service.TucsonService option <<<");
	            	WebCrawlerMaster.this.doDelete();
	            }
			}
		}
	}
	
	private class DoneHandler extends Behaviour {

		private static final long serialVersionUID = 2994251203600300007L;

		@Override
		public void action() {
			
			LogicTuple done = null;
				
			try {
				done = LogicTuple.parse("done(who(A),keyword(K),master('"+ WebCrawlerMaster.this.getAgentName() +"'))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WebCrawlerMaster.this.doDelete();
			}	
			
			TucsonOpCompletionEvent dn = null;
			
			final In inD = new In(WebCrawlerMaster.this.tcid, done);
			
			try {
				dn = WebCrawlerMaster.this.bridge.synchronousInvocation(inD, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				WebCrawlerMaster.this.doDelete();
			}
			
			if(dn!=null){
				log("Received done");
				if(dn.getTuple().getArg(0).getArg(0).toString().equals("watchdog")){
					workerError = true;
					log("ERROR! WORKER WITH KEYWORD: "+dn.getTuple().getArg(1).getArg(0).toString()+" HAS FAILED");
				}
				
				WebCrawlerMaster.this.bridge.clearTucsonOpResult(this);
				completed--;
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return completed==0;
		}
	}
	
	private class ResultsHandler extends SimpleBehaviour {

		private static final long serialVersionUID = 7624827275295163632L;

		@Override
		public void action() {
			LogicTuple result = null;
			
			try {
				result = LogicTuple.parse("publication(master('" + WebCrawlerMaster.this.getAgentName() + "'),pub(P))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WebCrawlerMaster.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			final InAll inP = new InAll(WebCrawlerMaster.this.tcid, result);
			
			try {
				res = WebCrawlerMaster.this.bridge.synchronousInvocation(inP, null, this);
			} catch (ServiceException e) {
				WebCrawlerMaster.this.view.msgbox("Master could not retrieve results");
				e.printStackTrace();
				WebCrawlerMaster.this.doDelete();
			}
			
			if(res!=null){
				publOccs = new HashMap<Publication,Integer>();
				for(LogicTuple lt : res.getTupleList()){
					Struct pubStr = (Struct) lt.getArg(1).getArg(0).toTerm(); //getArg(1) è pub all'interno della tupla, getArg(0) è la struct P
					String title = ValidTermFactory.getStringByTerm(pubStr.getTerm(0)); //getTerm(0) è il title nella struct
					String url = ValidTermFactory.getStringByTerm(pubStr.getTerm(1)); //getTerm(1) è l'url nella struct
					Publication pub = new Publication(title, url);
					log("Received result: "+title);
					boolean present = false;
					for(Publication p : publOccs.keySet()){
						if(pub.getUrl().equals(p.getUrl())){ //se l'url c'è già
							publOccs.put(p, publOccs.get(p)+1); //incremento le occorrenze di quella pubblicazione
							present = true;
						}
					}
					if(!present){ //se non è già presente
						publOccs.put(pub, 1); //metto nell'hashmap la pubblicazione e metto l'occorrenza di quella pub
					}
					WebCrawlerMaster.this.bridge.clearTucsonOpResult(this);
				}
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return publOccs!=null;
		}
	}
	
	private class RemWaitingsHandler extends SimpleBehaviour {

		private static final long serialVersionUID = 6936753012927760015L;

		@Override
		public void action() {
			LogicTuple waiting = null;
			
			try {
				waiting = LogicTuple.parse("waiting(who('" + WebCrawlerMaster.this.getAgentName() + "'),keyword(K))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WebCrawlerMaster.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			final InAll inW = new InAll(WebCrawlerMaster.this.tcid, waiting);
			
			try {
				res = WebCrawlerMaster.this.bridge.synchronousInvocation(inW, null, this);
			} catch (ServiceException e) {
				WebCrawlerMaster.this.view.msgbox("Master could not retrieve waitings");
				e.printStackTrace();
				WebCrawlerMaster.this.doDelete();
			}
			
			if(res!=null){
				WebCrawlerMaster.this.bridge.clearTucsonOpResult(this);
				waitingsRemoved = true;
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return waitingsRemoved;
		}
	}
	
	private class UpdateViewHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 4073088775775699286L;

		@Override
		public void action() {
			
			/**
			 * la ricerca è a AND: prendiamo solo le pubs che contengono tutte le parole chiave inserite dall'utente, 
			 * cioè le entry dell'hashmap che hanno value = keywords.length. 
			 * Se una pubblicazione contiene solo una parte delle parole chiave e non tutte viene rimossa dalla hashtable
			 * */
			
			//itero la hashmap finchè c'è un elemento successivo
			for(Iterator<Map.Entry<Publication, Integer>> it = publOccs.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Publication, Integer> entry = it.next(); //entry è il prossimo elemento
				if(entry.getValue()<keywords.length) { //Se il valore della entry è minore del numero totale di keywords
					it.remove(); //la rimuovo
				}
			}
			Publication[] resPubs = publOccs.keySet().toArray(new Publication[publOccs.size()]);
			
			if(!workerError){
				WebCrawlerMaster.this.view.updateView(resPubs);
			}else{
				WebCrawlerMaster.this.view.msgbox("Search failure caused by one or more crashed workers");
			}
			
			keywords = null;
			publOccs = null;
			workerError = false;
			waitingsRemoved = false;
			
			WebCrawlerMaster.this.view.enableInput();
		}
	}
	
	@Override
	protected void setup(){
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
		
		this.helloBehaviour = new HelloHandler();
		this.addBehaviour(this.helloBehaviour);
		
		this.view = new WebCrawlerMasterView(this);
		this.view.showGui();
	}

	@Override
	public String getAgentName() {
		return super.getName();
	}

	@Override
	protected void onGuiEvent(GuiEvent ev) {
		
		if(ev.getType() == WebCrawlerMasterView.SEARCH){
			this.workerError = false;
			this.waitingsRemoved = false;
			String input = ev.getParameter(0).toString();
			this.keywords = input.split("\\W+");
			this.view.disableInput();
			
			this.masterBehaviour = new OneShotBehaviour(this){

				private static final long serialVersionUID = 7078910810897323000L;

				@Override
				public void action() {
					final FSMBehaviour fsm = new FSMBehaviour(this.myAgent);
					this.configureFSM(fsm);
					this.myAgent.addBehaviour(fsm);
				}
				
				private void configureFSM(FSMBehaviour fsm) {
					kBehaviour = new KeywordsHandler();
					dBehaviour = new DoneHandler();
					rBehaviour = new ResultsHandler();
					rwBehaviour = new RemWaitingsHandler();
					uvBehaviour = new UpdateViewHandler();
					fsm.registerFirstState(kBehaviour, "KeywordsHandler");
					fsm.registerState(dBehaviour, "DoneHandler");
					fsm.registerState(rBehaviour, "ResultsHandler");
					fsm.registerState(rwBehaviour, "RemWaitingsHandler");
					fsm.registerLastState(uvBehaviour, "UpdateViewHandler");
					fsm.registerDefaultTransition("KeywordsHandler", "DoneHandler");
					fsm.registerDefaultTransition("DoneHandler", "ResultsHandler");
					fsm.registerDefaultTransition("ResultsHandler", "RemWaitingsHandler");
					fsm.registerDefaultTransition("RemWaitingsHandler", "UpdateViewHandler");
				}
			};
			
			this.addBehaviour(this.masterBehaviour);
			
		}else if(ev.getType() == WebCrawlerMasterView.KILL){
			this.doDelete(); //Termina l'agente alla chiusura della GUI (distruggendo il Thread)
		}else{
			this.log("Unknown GUI event, terminating...");
			this.view.dispose();
			this.doDelete();
		}
	}

	private void log(final String msg) {
		System.out.println("[" + this.getAgentName() + "]: " + msg);
	}
}