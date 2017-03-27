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

/*
 * MASTER AGENT
 * This Agent provides a GUI in which the user can enter one or more keywords
 * and then starts the crawling process.
 * As soon as the Agent receives the result, it shows them in the output panel.
 * After that, it enables the input field again to allow the user
 * to perform other eventual researches. 
 */
public class MasterAgent extends GuiAgent implements IWebCrawlerGui {

	private static final long serialVersionUID = 8507792839728793556L;
	
	private String myIp;
	private String tcIp;
	
	private WebCrawlerMasterView view;
	private String[] keywords; // array of user input keywords
	private HashMap<Publication, Integer> publOccs; // Key: publication, Value: occurrences
	
	// global auxiliary variables to coordinate the Behaviors
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

	/*
	 * System registration rules: 
	 * each new Agent must say "Hello!" to the Watchdog Agent specifying
	 * it name and the node it belongs
	 */
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
	
	/*
	 * Put the Keyword and Waiting tuples into the Tuple Space
	 */
	private class KeywordsHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 2994251203600300007L;

		@Override
		public void action() {
			MasterAgent.this.completed = keywords.length;
			
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
	
	/*
	 * Looking for completed workers
	 */
	private class DoneHandler extends Behaviour {

		private static final long serialVersionUID = 2994251203600300007L;

		@Override
		public void action() {
			
			LogicTuple done = null;
				
			// get and remove only Done tuple addressed to me
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
				
				// What if the Done tuple come from Watchdog? A Worker has been crashed
				if(from.equals(SysKb.WATCHDOG_NAME)){
					workerError = true; // signal error
					String k = ValidTermFactory.getStringByTerm(dn.getTuple().getArg(1).getArg(0).toTerm());
					log("ERROR! WORKER WITH KEYWORD: "+ k +" HAS FAILED");
				}
				
				MasterAgent.this.bridge.clearTucsonOpResult(this);
				MasterAgent.this.completed--;
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return MasterAgent.this.completed==0;
		}
	}
	
	/*
	 * Collecting the results
	 */
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
			
			// get all publications addressed to me
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
					// getArg(1) is pub(P), getArg(1).getArg(0) is the Struct P
					Struct pubStr = (Struct) lt.getArg(1).getArg(0).toTerm();
					// getTerm(0) is the title of the Publication represented by Struct P
					String title = ValidTermFactory.getStringByTerm(pubStr.getTerm(0));
					// getTerm(1) is the url of the Publication represented by Struct P
					String url = ValidTermFactory.getStringByTerm(pubStr.getTerm(1));
					// building the Publication object
					Publication pub = new Publication(title, url);
					log("Received result: "+title);
					boolean present = false;
					for(Publication p : publOccs.keySet()){
						// Had already seen this Publication previously?
						if(pub.getUrl().equals(p.getUrl())){
							// Increase it occurrence value
							publOccs.put(p, publOccs.get(p)+1);
							present = true;
						}
					}
					// Hadn't already seen this Publication previously?
					if(!present){
						// Create new key for it and initialize it occurrences value to 1
						publOccs.put(pub, 1);
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
	
	/*
	 * Remove the Waiting tuples previously added
	 */
	private class RemWaitingsHandler extends SimpleBehaviour {

		private static final long serialVersionUID = 6936753012927760015L;

		@Override
		public void action() {
			LogicTuple waiting = null;
			
			// remove only the Waiting tuples belonging to me
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
	
	/*
	 * Elaborate and show results (only if all workers have been terminated correctly)
	 * 
	 * NB: Why, in case of worker error, we get anyway the Publications? Why
	 *     get Publications while we already know they won't be showed?
	 *     To keep the Tuple Space as clean as possible
	 * 
	 */
	private class UpdateViewHandler extends OneShotBehaviour {

		private static final long serialVersionUID = 4073088775775699286L;

		@Override
		public void action() {
			
			/**
			 * We have chosen to implement an AND-crawler: the Publication returned are only
			 * those that contain ALL the input Keywords. 
			 * That means we remove the Publication which have occurrences less than the number of keywords. 
			 * */
			
			// Iterating the results...
			for(Iterator<Map.Entry<Publication, Integer>> it = publOccs.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Publication, Integer> entry = it.next();
				// remove it if its occurrences value doesn't match the number of keywords
				if(entry.getValue()<keywords.length) {
					it.remove();
				}
			}
			Publication[] resPubs = publOccs.keySet().toArray(new Publication[publOccs.size()]);
			
			if(!workerError){
				// If everything is fine, then show the results
				MasterAgent.this.view.updateView(resPubs);
			}else{
				// In case of worker failure, the results are incomplete and so incorrect...
				// ...then throw a MsgBox error instead of showing them.
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
					// First of all say "Hello" to Watchdog Agent (see above at setup method) and wait for user input...
					// ...then put the Keyword tuples to the Tuple space and wait...
					fsm.registerDefaultTransition("KeywordsHandler", "DoneHandler");
					// ...then get the Done tuples from the completed Workers...
					fsm.registerDefaultTransition("DoneHandler", "ResultsHandler");
					// ...then get all the results(publications) from the Tuple spaces...
					fsm.registerDefaultTransition("ResultsHandler", "RemWaitingsHandler");
					// ...then remove the Waiting tuples...
					fsm.registerDefaultTransition("RemWaitingsHandler", "UpdateViewHandler");
					// ...and finally show the results trough the GUI and wait for next research
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