package sd1516.webcrawler.utils;

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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import sd1516.webcrawler.sysconstants.SysKb;

/*
 * Pubs Crawler is the most computationally expensive part, it has been
 * optimized as much as possible assigning an independent task for every year
 * of publications.
 * 
 * To perform the crawling operation it has been used the Jsoup library
 */
public class PubsCrawler {
	
	public static Publication[] getPubsByKeyword(String keyword) {
		ExecutorService exec = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(30));
		
		List<Publication> results = new ArrayList<Publication>(); // list of publications found
		List<Future<List<Publication>>> futures = new ArrayList<Future<List<Publication>>>(); // list of futures that each return a publications list
		
		try {
			// connecting to the page named "Spazio delle Pubblicazioni ApiCE"
			Document webHome = Jsoup.connect(SysKb.APICE_HOME).timeout(Integer.MAX_VALUE).get();
			
			 // starting from the first year...
			int year = SysKb.APICE_STARTING_YEAR;
			
			while(!webHome.select("[href$="+year+"]").isEmpty()){ // iterate the list of all the years from 1980 till today
			
				//connecting to the page named "Publications in the APICe Space (year)"
				Document pubsPerYear = Jsoup.connect(SysKb.APICE_HOME+"PapersPerYear?year="+year).timeout(Integer.MAX_VALUE).get(); 
				
				Elements pubs = pubsPerYear.select("div [class=title]"); // select all the publication titles
				
				Future<List<Publication>> subRes = exec.submit(new SubCrawler(pubs, keyword)); // submit the executor to a callable task to handle a specific year
				futures.add(subRes); // add futures to future list to get them next
				
				year++; // next year
			}
		} catch(IOException e){
			if(e instanceof SocketTimeoutException){ // handle an eventual server ApiCE unavailability
				System.out.println("SocketTimeoutException: Apice Server does not respond");
			}else{
				e.printStackTrace();
			}
		}
		
		// waiting for each future completions
		for(Future<List<Publication>> future : futures){
			try {
				// get future results
				List<Publication> subRes = future.get();
				
				// add the future results to the main publication structure
				for(Publication pub : subRes){
					results.add(pub);
				}
				
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		exec.shutdown();
		
		Publication[] ret = new Publication[results.size()];
		return results.toArray(ret);
	}
	
	
	// for each year there is a task that looks for all keyword occurrences in the publication "pre"
	private static class SubCrawler implements Callable<List<Publication>>{
		
		private Elements pubs;
		private String keyword;
		
		// look for keyword in pubs
		public SubCrawler(Elements pubs, String keyword){
			this.pubs = pubs;
			this.keyword = keyword;
		}
		
		@Override
		public List<Publication> call() {
			List<Publication> subRes = new ArrayList<Publication>(); 
			
			for(Element pub : pubs){ // iterating all the publications
				if(!pub.absUrl("href").equals("")){
					try {
						// connecting to the publication page
						Document pubDoc = Jsoup.connect(pub.absUrl("href")).timeout(Integer.MAX_VALUE).get();
						// select the "pre" part from the HTML source and replace all the \ with / to avoid parsing issues
						String pre = pubDoc.select("pre").text().replace("\\", "/"); 
						// "pre" format  --->   @...{..., ..., ...} 
					
						// arrays of words (removing the punctuation) corresponding to authors, keywords and titles
						String[] titleWords = PreParser.getTagInfo(pre, "title").split("\\W+");
						String[] keyWords = PreParser.getTagInfo(pre, "keyword").split("\\W+");
						String[] authWords = PreParser.getTagInfo(pre, "author").split("\\W+");
						
						// concatenating the previous 3 arrays into one
						Stream<String> stream = Stream.concat(Stream.of(titleWords), Stream.of(authWords));
						if(keyWords.length!=0){
							stream = Stream.concat(stream, Stream.of(keyWords));
						}
						String[] allWords = stream.toArray(String[]::new);
					
						// looking for keyword occurrences
						for(String s : allWords){
							if(s.toLowerCase().equals(keyword.toLowerCase())){
								// as soon as get a match, build the publication object and add it to the results
								subRes.add(new Publication(PreParser.getTagInfo(pre, "title"), pub.absUrl("href")));
								break;
							}
						}
					} catch(IOException e){
						if(e instanceof SocketTimeoutException){ // handle an eventual server ApiCE unavailability
							System.out.println("SocketTimeoutException: Apice Server does not respond");
						}else{
							e.printStackTrace();
						}
					}
				}
			}
			return subRes; 
		}
	}
}
