package sd1516.webcrawler.worker;

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
import sd1516.webcrawler.pubscrawler.PubsCrawler;
import sd1516.webcrawler.utils.Publication;
import sd1516.webcrawler.utils.ValidTermFactory;

public class WebCrawlerWorker extends Agent {

	private static final long serialVersionUID = 6284709727420003881L;
	
	private final String myIp = "localhost";//getArguments()[0].toString();
	private final String tcIp = "localhost";//getArguments()[1].toString();
	
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
	
	private String word;
	private String master;
	private Publication[] pubs;
	private boolean masterError;
	private boolean workingsRemoved;
	
	private class HelloHandler extends OneShotBehaviour {

		private static final long serialVersionUID = -9142113698067729455L;

		@Override
		public void action() {
			LogicTuple hello;
			
			try{
				hello = LogicTuple.parse("hello("+ "from('"+ WebCrawlerWorker.this.getAgentName() +"')," + "node('"+ myIp +"')" + ")");
				
				WebCrawlerWorker.this.log("Hello from " + WebCrawlerWorker.this.getAgentName() + " (ip: " + myIp + ")");
				
				final Out out = new Out(tcid, hello);
				WebCrawlerWorker.this.bridge.asynchronousInvocation(out);
				WebCrawlerWorker.this.bridge.clearTucsonOpResult(this);
				
			}catch (final InvalidLogicTupleException e) {
	            e.printStackTrace();
	            WebCrawlerWorker.this.doDelete();
	        } catch (final ServiceException e) {
	        	WebCrawlerWorker.this
	                    .log(">>> No TuCSoN service active, reboot JADE with -services it.unibo.tucson.jade.service.TucsonService option <<<");
	        	WebCrawlerWorker.this.doDelete();
	        }
		}
		
	}
	
	private class RetrieveKeywordsHandler extends Behaviour{

		private static final long serialVersionUID = -1150194599564407339L;

		@Override
		public void action() {
			WebCrawlerWorker.this.word = "";
			WebCrawlerWorker.this.master = "";
			WebCrawlerWorker.this.pubs = null;
			WebCrawlerWorker.this.masterError = false;
			WebCrawlerWorker.this.workingsRemoved = false;
			
			LogicTuple keyword = null;
			
			try {
				keyword = LogicTuple.parse("keyword(value(V),from(M))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WebCrawlerWorker.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			
			final In in = new In(WebCrawlerWorker.this.tcid, keyword);
			
			try {
				res = WebCrawlerWorker.this.bridge.synchronousInvocation(in, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				WebCrawlerWorker.this.doDelete();
			}
			
			if(res != null){
				WebCrawlerWorker.this.log("Retrieving keyword ("+ res.getTuple().getArg(0).getArg(0) +") from TupleCentre");
				WebCrawlerWorker.this.word = res.getTuple().getArg(0).getArg(0).toString();
				WebCrawlerWorker.this.master = res.getTuple().getArg(1).getArg(0).toString();
				System.out.println("MASTER: "+WebCrawlerWorker.this.master);
				WebCrawlerWorker.this.bridge.clearTucsonOpResult(this); //Pulisce la struttura per non prendere più volte lo stesso risultato
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return (!word.equals(""));
		}
	}
	
	private class WorkingHandler extends OneShotBehaviour{

		private static final long serialVersionUID = 2211911919984358284L;

		@Override
		public void action() {
			try {
				LogicTuple working = null;
				
				working = LogicTuple.parse("working(" + "who('" + WebCrawlerWorker.this.getAgentName() + "')," + "keyword(" + word + ")," + "for(" + master + ")" + ")");
				final Out out = new Out(WebCrawlerWorker.this.tcid, working);
				WebCrawlerWorker.this.bridge.asynchronousInvocation(out);
				WebCrawlerWorker.this.bridge.clearTucsonOpResult(this);
				
			} catch (InvalidLogicTupleException | ServiceException e) {
				e.printStackTrace();
				WebCrawlerWorker.this.doDelete();
			}
		}
	}
	
	private class SearchPubsHandler extends OneShotBehaviour{

		private static final long serialVersionUID = -1645830099144688951L;

		@Override
		public void action() {
			pubs = PubsCrawler.getPubsByKeyword(word);
		}
		
	}
	
	private class CheckMasterHandler extends OneShotBehaviour{

		private static final long serialVersionUID = 1197354630339942978L;

		@Override
		public void action() {
			LogicTuple waiting = null;
			
			try {
				waiting = LogicTuple.parse("waiting(who(" + master + ")," + "keyword(K)"+ ")");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WebCrawlerWorker.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			
			final Rdp rdp = new Rdp(WebCrawlerWorker.this.tcid, waiting);
			
			try {
				res = WebCrawlerWorker.this.bridge.synchronousInvocation(rdp, Long.MAX_VALUE, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				WebCrawlerWorker.this.doDelete();
			}
			
			if(res != null){
				String keyword = res.getTuple().getArg(1).getArg(0).toString();
				if(keyword.equals("K")){
					masterError = true;
				}
				WebCrawlerWorker.this.bridge.clearTucsonOpResult(this); //Pulisce la struttura per non prendere più volte lo stesso risultato
			}else{
				this.block();
			}
		}
		
		@Override
		public int onEnd(){
			if(masterError){
				return 0;
			}
			return 1;
		}
		
	}
	
	private class ReturnPubsHandler extends OneShotBehaviour{

		private static final long serialVersionUID = 1144167084871453507L;

		@Override
		public void action() {
			try {
				LogicTuple result = null;
				for(Publication pub : pubs){
					Term title = ValidTermFactory.getTermByString(pub.getTitle());
					Term url = ValidTermFactory.getTermByString(pub.getUrl());
					result = LogicTuple.parse("publication(" + "master(" + master + ")," + "pub(" + new Struct(new Term[]{title,url}) + ")" + ")");
					final Out out = new Out(WebCrawlerWorker.this.tcid, result);
					WebCrawlerWorker.this.bridge.asynchronousInvocation(out);
					WebCrawlerWorker.this.bridge.clearTucsonOpResult(this);
				}
				
				LogicTuple done = null;
				done = LogicTuple.parse("done(who(" + WebCrawlerWorker.this.getAgentName() + ")," + "keyword(K)," + "master("+ master +")" + ")");
				final Out out = new Out(WebCrawlerWorker.this.tcid, done);
				WebCrawlerWorker.this.bridge.asynchronousInvocation(out);
				WebCrawlerWorker.this.bridge.clearTucsonOpResult(this);
				
			} catch (InvalidLogicTupleException | ServiceException e) {
				e.printStackTrace();
				WebCrawlerWorker.this.doDelete();
			}
		}
	}
	
	private class RemoveWorkingHandler extends SimpleBehaviour {

		private static final long serialVersionUID = 8173896219779085865L;

		@Override
		public void action() {
			LogicTuple working = null;
			
			try {
				working = LogicTuple.parse("working(who('" + WebCrawlerWorker.this.getAgentName() + "'),keyword(K),for(M))");
			} catch (InvalidLogicTupleException e) {
				e.printStackTrace();
				WebCrawlerWorker.this.doDelete();
			}
			
			TucsonOpCompletionEvent res = null;
			final In in = new In(WebCrawlerWorker.this.tcid, working);
			
			try {
				res = WebCrawlerWorker.this.bridge.synchronousInvocation(in, null, this);
			} catch (ServiceException e) {
				e.printStackTrace();
				WebCrawlerWorker.this.doDelete();
			}
			
			if(res!=null){
				WebCrawlerWorker.this.bridge.clearTucsonOpResult(this);
				workingsRemoved = true;
			}else{
				this.block();
			}
		}

		@Override
		public boolean done() {
			return workingsRemoved;
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
				fsm.registerDefaultTransition("RetrieveKeywordsHandler", "WorkingHandler");
				fsm.registerDefaultTransition("WorkingHandler", "SearchPubsHandler");
				fsm.registerDefaultTransition("SearchPubsHandler", "CheckMasterHandler");
				fsm.registerTransition("CheckMasterHandler", "ReturnPubsHandler", 1);
				fsm.registerDefaultTransition("ReturnPubsHandler", "RemoveWorkingHandler");
				fsm.registerTransition("CheckMasterHandler", "RemoveWorkingHandler", 0);
				fsm.registerDefaultTransition( "RemoveWorkingHandler", "RetrieveKeywordsHandler");
				
			}
		};
		
		this.addBehaviour(workerBehaviour);
	}
	
	public String getAgentName() {
		return super.getName();
	}
	
	private void log(final String msg) {
		System.out.println("[" + this.getName() + "]: " + msg);
	}
}