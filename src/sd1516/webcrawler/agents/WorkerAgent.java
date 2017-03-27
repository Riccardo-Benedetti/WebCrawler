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

import alice.logictuple.LogicTuple;
import alice.logictuple.exceptions.InvalidLogicTupleException;
import alice.tucson.api.TucsonTupleCentreId;
import alice.tucson.api.exceptions.TucsonInvalidAgentIdException;
import alice.tucson.api.exceptions.TucsonInvalidTupleCentreIdException;
import alice.tucson.api.exceptions.TucsonOperationNotPossibleException;
import alice.tucson.asynchSupport.actions.ordinary.In;
import alice.tucson.asynchSupport.actions.ordinary.Out;
import alice.tucson.asynchSupport.actions.ordinary.Rdp;
import alice.tucson.service.TucsonOpCompletionEvent;
import alice.tuprolog.Struct;
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
import jade.core.behaviours.SimpleBehaviour;
import sd1516.webcrawler.sysconstants.SysKb;
import sd1516.webcrawler.utils.Publication;
import sd1516.webcrawler.utils.PubsCrawler;
import sd1516.webcrawler.utils.ValidTermFactory;

/*
 * WORKER AGENT
 * This Agent starts monitoring the Tuple Space looking for new keywords from 
 * Master Agent(s).
 * Each Worker Agent grabs a keyword at a time and search all of Apice publications
 * that match with it.
 * As soon as the results are ready, it put them back to the Tuple Space and restart
 * looking for others keywords.
 */
public class WorkerAgent extends Agent {

	private static final long serialVersionUID = 6284709727420003881L;
	
	private String myIp;
	private String tcIp;
	
	private TucsonHelper helper;
	private BridgeToTucson bridge;
	private TucsonTupleCentreId tcid;
	
	private HelloHandler helloBehaviour;
	private OneShotBehaviour workerBehaviour;
	private RetrieveKeywordsHandler rkBehaviour;
	private WorkingHandler wBehaviour;
	private SearchPubsHandler spBehaviour;
	private CheckMasterHandler cmBehaviour;
	private ReturnPubsHandler rpBehaviour;
	private RemoveWorkingHandler rwBehaviour;
	
	private String word; // the keyword grabbed from Tuple Space
	private String master; // the Master reference to whom we will return the results
	private Publication[] pubs; // the results of the current worker
	
	// global auxiliary variables to coordinate the Behaviors
	private boolean masterError; 
	private boolean complete;
	//private TucsonOpCompletionEvent pendingOp;
	
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
		
		this.workerBehaviour = new OneShotBehaviour(this){

			private static final long serialVersionUID = 7078910810897323000L;

			@Override
			public void action() {
				final FSMBehaviour fsm = new FSMBehaviour(this.myAgent);
				this.configureFSM(fsm);
				this.myAgent.addBehaviour(fsm);
			}
			
			private void configureFSM(FSMBehaviour fsm) {
				rkBehaviour = new RetrieveKeywordsHandler();
				wBehaviour = new WorkingHandler();
				spBehaviour = new SearchPubsHandler();
				cmBehaviour = new CheckMasterHandler();
				rpBehaviour = new ReturnPubsHandler();
				rwBehaviour = new RemoveWorkingHandler();
				fsm.registerFirstState(rkBehaviour, "RetrieveKeywordsHandler");
				fsm.registerState(wBehaviour, "WorkingHandler");
				fsm.registerState(spBehaviour, "SearchPubsHandler");
				fsm.registerState(cmBehaviour, "CheckMasterHandler");
				fsm.registerState(rpBehaviour, "ReturnPubsHandler");
				fsm.registerState(rwBehaviour, "RemoveWorkingHandler");
				// First of all say "Hello" to Watchdog Agent (see above)...
				// ...then look for a keyword in the Tuple Space and get it...
				fsm.registerDefaultTransition("RetrieveKeywordsHandler", "WorkingHandler");
				// ...then emit the Working tuple to signal you are in busy state...
				fsm.registerDefaultTransition("WorkingHandler", "SearchPubsHandler");
				// ...then perform your research...
				fsm.registerDefaultTransition("SearchPubsHandler", "CheckMasterHandler");
				// ...then, before upload the result to the Tuple Space, check the Master state...
				fsm.registerTransition("CheckMasterHandler", "ReturnPubsHandler", 1);
				// ...the Master still waiting, so upload the results...
				fsm.registerDefaultTransition("ReturnPubsHandler", "RemoveWorkingHandler");
				// ...the Master has been crashed meanwhile the Worker was working, so
				// there's no need to return the results...
				fsm.registerTransition("CheckMasterHandler", "RemoveWorkingHandler", 0);
				// ...Finally, in any case remove the Working tuples...
				fsm.registerDefaultTransition( "RemoveWorkingHandler", "RetrieveKeywordsHandler");
				// ...and look for the next keyword
				
			}
		};
		
		this.addBehaviour(workerBehaviour);
	}
	
	/*
	 * System registration rules: 
	 * each new Agent must say "Hello!" to the Watchdog Agent specifying
	 * it name and the node it belongs
	 */
	private class HelloHandler extends OneShotBehaviour {

		private static final long serialVersionUID = -9142113698067729455L;

		@Override
		public void action() {
			LogicTuple hello;
			
			try{
				Term me = ValidTermFactory.getTermByString(WorkerAgent.this.getAgentName());
				Term host = ValidTermFactory.getTermByString(myIp);
				hello = LogicTuple.parse("hello("+ "from("+ me +")," + "node("+ host +")" + ")");
				
				WorkerAgent.this.log("Hello from " + me + " (ip: " + myIp + ")");
				
				final Out out = new Out(tcid, hello);
				WorkerAgent.this.bridge.asynchronousInvocation(out);
				WorkerAgent.this.bridge.clearTucsonOpResult(this);
				
			}catch (InvalidLogicTupleException | ServiceException e) {
	            e.printStackTrace();
	            WorkerAgent.this.doDelete();
	        }
		}
	}
	
	/*
	 * Looking for new keyword...
	 */
	private class RetrieveKeywordsHandler extends Behaviour{

		private static final long serialVersionUID = -1150194599564407339L;

		@Override
		public void action() {
			// reset all the structures
			WorkerAgent.this.word = "";
			WorkerAgent.this.master = "";
			WorkerAgent.this.pubs = null;
			WorkerAgent.this.masterError = false;
			WorkerAgent.this.complete = false;
			
			LogicTuple keyword = null;
			
			try {
				keyword = LogicTuple.parse("keyword(value(V),from(M))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WorkerAgent.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			
			final In in = new In(WorkerAgent.this.tcid, keyword);
			
			try {
				res = WorkerAgent.this.bridge.synchronousInvocation(in, Long.MAX_VALUE, this);
				//WorkerAgent.this.pendingOp = WorkerAgent.this.bridge.GETPENDINGOP!!!!;
			} catch (ServiceException e) {
				e.printStackTrace();
				WorkerAgent.this.doDelete();
			}
			
			if(res != null){
				// Save the keyword and the master reference
				word = ValidTermFactory.getStringByTerm(res.getTuple().getArg(0).getArg(0).toTerm());
				master = ValidTermFactory.getStringByTerm(res.getTuple().getArg(1).getArg(0).toTerm());
				
				WorkerAgent.this.log("Retrieving keyword ("+ word + ") from " + master);
				
				WorkerAgent.this.bridge.clearTucsonOpResult(this);
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return (!word.equals(""));
		}
	}
	
	/*
	 * Signal Working state
	 */
	private class WorkingHandler extends OneShotBehaviour{

		private static final long serialVersionUID = 2211911919984358284L;

		@Override
		public void action() {
			//WorkerAgent.this.pendingOp = null; TODO
			LogicTuple working = null;
			
			// Signal that "I, the Worker W, have taken the word K and I'm working for Master M"
			try {
				Term me = ValidTermFactory.getTermByString(WorkerAgent.this.getAgentName());
				Term k = ValidTermFactory.getTermByString(word);
				Term to = ValidTermFactory.getTermByString(master);
				working = LogicTuple.parse("working(" + "who(" + me + ")," + "keyword(" + k + ")," + "for(" + to + ")" + ")");
				final Out out = new Out(WorkerAgent.this.tcid, working);
				WorkerAgent.this.bridge.asynchronousInvocation(out);
				WorkerAgent.this.bridge.clearTucsonOpResult(this);
				
			} catch (InvalidLogicTupleException | ServiceException e) {
				e.printStackTrace();
				WorkerAgent.this.doDelete();
			}
		}
	}
	
	/*
	 * Main Worker behavior (IT MIGHT TAKE A SIGNIFICATIVE AMOUNT OF TIME!!!)
	 */
	private class SearchPubsHandler extends OneShotBehaviour{

		private static final long serialVersionUID = -1645830099144688951L;

		@Override
		public void action() {
			pubs = PubsCrawler.getPubsByKeyword(word);
		}
		
	}
	
	/*
	 * Check if my Master is fine and still waiting
	 */
	private class CheckMasterHandler extends OneShotBehaviour{

		private static final long serialVersionUID = 1197354630339942978L;

		@Override
		public void action() {
			LogicTuple waiting = null;
			
			// Is still there the Master M waiting for keyword K?
			try {
				Term to = ValidTermFactory.getTermByString(master);
				waiting = LogicTuple.parse("waiting(who(" + to + ")," + "keyword(K)"+ ")");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WorkerAgent.this.doDelete();
			}
			
			// Checking for a tuple means NO REMOVING it, so use Read not suspensive primitive instead of In
			TucsonOpCompletionEvent res = null;
			final Rdp rdp = new Rdp(WorkerAgent.this.tcid, waiting);
			
			try {
				res = WorkerAgent.this.bridge.synchronousInvocation(rdp, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				WorkerAgent.this.doDelete();
			}
			
			if(res != null){
				String keyword = ValidTermFactory.getStringByTerm(res.getTuple().getArg(1).getArg(0).toTerm());
				// If I got the tuple templates, so I didn't find the tuple I was searching for
				if(keyword.equals("K")){
					masterError = true; // Signal Master error
				}
				WorkerAgent.this.bridge.clearTucsonOpResult(this);
			}else{
				this.block();
			}
		}
		
		@Override
		public int onEnd(){
			// Select next Behavior referring to the presence or not of a Master error
			if(masterError){
				return 0;
			}
			return 1;
		}
		
	}
	
	/*
	 * No master error, so return the result as agreed
	 */
	private class ReturnPubsHandler extends OneShotBehaviour{

		private static final long serialVersionUID = 1144167084871453507L;

		@Override
		public void action() {
			try {
				LogicTuple result = null;
				
				// Build a tuple for each Publication result and send it to the tuple space
				for(Publication pub : pubs){
					Term title = ValidTermFactory.getTermByString(pub.getTitle());
					Term url = ValidTermFactory.getTermByString(pub.getUrl());
					Term to = ValidTermFactory.getTermByString(master);
					result = LogicTuple.parse("publication(" + "master(" + to + ")," + "pub(" + new Struct(new Term[]{title,url}) + ")" + ")");
					final Out out = new Out(WorkerAgent.this.tcid, result);
					WorkerAgent.this.bridge.asynchronousInvocation(out);
					WorkerAgent.this.bridge.clearTucsonOpResult(this);
				}
				
				LogicTuple done = null;
				
				// Finalize sending also a Done tuple to signal my successful completion
				Term me = ValidTermFactory.getTermByString(WorkerAgent.this.getAgentName());
				Term to = ValidTermFactory.getTermByString(master);
				done = LogicTuple.parse("done(who(" + me + ")," + "keyword(K)," + "master("+ to +")" + ")");
				final Out out = new Out(WorkerAgent.this.tcid, done);
				WorkerAgent.this.bridge.asynchronousInvocation(out);
				WorkerAgent.this.bridge.clearTucsonOpResult(this);
				
			} catch (InvalidLogicTupleException | ServiceException e) {
				e.printStackTrace();
				WorkerAgent.this.doDelete();
			}
		}
	}
	
	/*
	 * Finally, clean the Tuple Space removing the Working tuple
	 */
	private class RemoveWorkingHandler extends SimpleBehaviour {

		private static final long serialVersionUID = 8173896219779085865L;

		@Override
		public void action() {
			LogicTuple working = null;
			
			try {
				Term me = ValidTermFactory.getTermByString(WorkerAgent.this.getAgentName());
				working = LogicTuple.parse("working(who(" + me + "),keyword(K),for(M))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WorkerAgent.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			final In in = new In(WorkerAgent.this.tcid, working);
			
			try {
				res = WorkerAgent.this.bridge.synchronousInvocation(in, null, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				WorkerAgent.this.doDelete();
			}
			
			if(res!=null){
				WorkerAgent.this.bridge.clearTucsonOpResult(this);
				WorkerAgent.this.complete = true;
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return WorkerAgent.this.complete;
		}
	}
	
	public String getAgentName() {
		return super.getName();
	}
	
	private void log(final String msg) {
		System.out.println("[" + this.getName() + "]: " + msg);
	}
	
	@Override
	public void doDelete(){
		//TODO
		
		/*if(this.pendingOp != null){
			this.bridge.removePendingOp(this.pendingOp.getOpId().getId());
		}
		super.doDelete();*/
	}
}