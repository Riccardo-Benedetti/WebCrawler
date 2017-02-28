package sd1516.webcrawler.agents;

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
import alice.tuprolog.Term;
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
import sd1516.webcrawler.sysconstants.SysKb;
import sd1516.webcrawler.utils.Publication;
import sd1516.webcrawler.utils.ValidTermFactory;

public class MasterAgent extends GuiAgent implements IWebCrawlerGui {

	private static final long serialVersionUID = 8507792839728793556L;
	
	private String myIp;
	private String tcIp;
	
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
	
	@Override
	protected void setup(){
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
		
		this.helloBehaviour = new HelloHandler();
		this.addBehaviour(this.helloBehaviour);
		
		this.view = new WebCrawlerMasterView(this);
		this.view.showGui();
	}

	
	private class HelloHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 4533549206646776775L;

		@Override
		public void action() {
			LogicTuple hello;
			
			try{
				Term me = ValidTermFactory.getTermByString(MasterAgent.this.getAgentName());
				Term host = ValidTermFactory.getTermByString(myIp);
				
				hello = LogicTuple.parse("hello("+ "from("+ me +")," + "node("+ host +")" + ")");
				
				MasterAgent.this.log("Hello from " + me + " (ip: " + myIp + ")");
				
				final Out out = new Out(tcid, hello);
				MasterAgent.this.bridge.asynchronousInvocation(out);
				MasterAgent.this.bridge.clearTucsonOpResult(this);
				
			}catch (InvalidLogicTupleException | ServiceException e) {
	            e.printStackTrace();
	            MasterAgent.this.doDelete();
	        }
		}
	}
	
	private class KeywordsHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 2994251203600300007L;

		@Override
		public void action() {
			completed = keywords.length;
			
			for(String kw : keywords){
				LogicTuple keyword;
				LogicTuple waiting;
				
				try{
					Term k = ValidTermFactory.getTermByString(kw);
					Term me = ValidTermFactory.getTermByString(MasterAgent.this.getAgentName());
					keyword = LogicTuple.parse("keyword("+ "value("+ k +")," + "from("+ me +")" + ")");
					waiting = LogicTuple.parse("waiting("+ "who("+ me +")," + "keyword("+ k +")" + ")");
					MasterAgent.this.log("Sending keyword ("+ k +") to TupleCentre");
					
					final Out outK = new Out(MasterAgent.this.tcid, keyword);
					MasterAgent.this.bridge.asynchronousInvocation(outK);
					MasterAgent.this.bridge.clearTucsonOpResult(this);
					
					final Out outW = new Out(MasterAgent.this.tcid, waiting);
					MasterAgent.this.bridge.asynchronousInvocation(outW);
					MasterAgent.this.bridge.clearTucsonOpResult(this);
					
				}catch (InvalidLogicTupleException | ServiceException e) {
	                e.printStackTrace();
	                MasterAgent.this.doDelete();
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
				Term me = ValidTermFactory.getTermByString(MasterAgent.this.getAgentName());
				done = LogicTuple.parse("done(who(A),keyword(K),master("+ me +"))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				MasterAgent.this.doDelete();
			}	
			
			TucsonOpCompletionEvent dn = null;
			
			final In inD = new In(MasterAgent.this.tcid, done);
			
			try {
				dn = MasterAgent.this.bridge.synchronousInvocation(inD, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				MasterAgent.this.doDelete();
			}
			
			if(dn!=null){
				log("Received done");
				String from = ValidTermFactory.getStringByTerm(dn.getTuple().getArg(0).getArg(0).toTerm());
				if(from.equals(SysKb.WATCHDOG_NAME)){
					workerError = true;
					String k = ValidTermFactory.getStringByTerm(dn.getTuple().getArg(1).getArg(0).toTerm());
					log("ERROR! WORKER WITH KEYWORD: "+ k +" HAS FAILED");
				}
				
				MasterAgent.this.bridge.clearTucsonOpResult(this);
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
				Term me = ValidTermFactory.getTermByString(MasterAgent.this.getAgentName());
				result = LogicTuple.parse("publication(master(" + me + "),pub(P))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				MasterAgent.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			final InAll inP = new InAll(MasterAgent.this.tcid, result);
			
			try {
				res = MasterAgent.this.bridge.synchronousInvocation(inP, null, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				MasterAgent.this.doDelete();
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
					MasterAgent.this.bridge.clearTucsonOpResult(this);
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
				Term me = ValidTermFactory.getTermByString(MasterAgent.this.getAgentName());
				waiting = LogicTuple.parse("waiting(who(" + me + "),keyword(K))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				MasterAgent.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			final InAll inW = new InAll(MasterAgent.this.tcid, waiting);
			
			try {
				res = MasterAgent.this.bridge.synchronousInvocation(inW, null, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				MasterAgent.this.doDelete();
			}
			
			if(res!=null){
				MasterAgent.this.bridge.clearTucsonOpResult(this);
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
			 * la ricerca è di tipo AND: prendiamo solo le pubs che contengono tutte le parole chiave inserite dall'utente, 
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
				MasterAgent.this.view.updateView(resPubs);
			}else{
				MasterAgent.this.view.msgbox("Search failure caused by one or more crashed workers");
			}
			
			keywords = null;
			publOccs = null;
			workerError = false;
			waitingsRemoved = false;
			
			MasterAgent.this.view.enableInput();
		}
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
			this.doDelete();
		}else{
			this.log("Unknown GUI event, terminating...");
			this.view.dispose();
			this.doDelete();
		}
	}

	private void log(final String msg) {
		System.out.println("[" + this.getAgentName() + "]: " + msg);
	}
	
	@Override
	public void doDelete(){
		this.view.dispose();
		super.doDelete();
	}
}