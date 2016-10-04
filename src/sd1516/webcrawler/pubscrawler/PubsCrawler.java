package sd1516.webcrawler.pubscrawler;

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
import sd1516.webcrawler.utils.PreParser;
import sd1516.webcrawler.utils.Publication;

public class PubsCrawler {
	
	public static Publication[] getPubsByKeyword(String keyword) {
		System.out.println("PubsCrawler started");
		
		ExecutorService exec = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(30));
		
		List<Publication> results = new ArrayList<Publication>(); //lista pubblicazioni trovate
		List<Future<List<Publication>>> futures = new ArrayList<Future<List<Publication>>>(); //lista di future che tornerà una lista di pubblicazioni
		
		try {
			Document webHome = Jsoup.connect("http://test2.apice.unibo.it/xwiki/bin/view/Publications/WebHome").timeout(Integer.MAX_VALUE).get(); //connessione al sito "Spazio delle Pubblicazioni ApiCE"
			
			int year = 1980; //anno delle prime pubblicazioni
			
			while(!webHome.select("[href$="+year+"]").isEmpty()){ //itera sugli href che finiscono con un anno compreso tra 1980 e l'anno corrente
			
				Document pubsPerYear = Jsoup.connect("http://test2.apice.unibo.it/xwiki/bin/view/Publications/PapersPerYear?year="+year).timeout(Integer.MAX_VALUE).get(); //connessione al sito "Publications in the APICe Space (year)"
				
				Elements pubs = pubsPerYear.select("div [class=title]"); //seleziono i titoli delle pubblicazioni
				
				Future<List<Publication>> subRes = exec.submit(new SubCrawler(pubs, keyword)); //sottopongo all'executor un task callable che gestisce un certo year
				futures.add(subRes); //aggiungo le future alla lista per successivo get
				
				year++; //vado all'anno successivo
			}
		} catch(IOException e){
			if(e instanceof SocketTimeoutException){ //gestione dell'eccezione relativa alla indisponibilità del server ApiCE
				System.out.println("SocketTimeoutException: Apice Server does not respond");
			}else{
				e.printStackTrace();
			}
		}
		
		for(Future<List<Publication>> future : futures){
			try {
				List<Publication> subRes = future.get(); //recupero il risultato della future 
				
				for(Publication pub : subRes){
					results.add(pub); //metto i risultati nella lista di Publication
				}
				
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		exec.shutdown();
		
		Publication[] ret = new Publication[results.size()];
		return results.toArray(ret); //trasformo results da lista a array di pubblicazioni
	}
	
	
	
	private static class SubCrawler implements Callable<List<Publication>>{ //per ogni anno c'è un task che si occupa di trovare la keyword nell'html della pubblicazione
		
		private Elements pubs;
		private String keyword;
		
		public SubCrawler(Elements pubs, String keyword){ //prende in ingresso tutti i titoli e la keyword da cercare
			this.pubs = pubs;
			this.keyword = keyword;
		}
		
		@Override
		public List<Publication> call() {
			List<Publication> subRes = new ArrayList<Publication>(); 
			
			for(Element pub : pubs){ //itero tutti i titoli (che sono di tipo Element)
				if(!pub.absUrl("href").equals("")){
					try {
						Document pubDoc = Jsoup.connect(pub.absUrl("href")).timeout(Integer.MAX_VALUE).get(); //connessione al sito di cui prendo l'url da href della pub
						String pre = pubDoc.select("pre").text().replace("\\", "/"); //seleziono la parte di html con tag pre e sostituisco eventuali \ con / per evitare problemi di parsing
						//in pre ho il blocco @...{..., ..., ...} 
					
						//creo array di stringhe con parole(rimuovo punteggiatura) corrispondenti a autori, keyword e titoli
						String[] titleWords = PreParser.getTagInfo(pre, "title").split("\\W+");
						String[] keyWords = PreParser.getTagInfo(pre, "keyword").split("\\W+");
						String[] authWords = PreParser.getTagInfo(pre, "author").split("\\W+");
						
						//concatenazione degli array di parole
						Stream<String> stream = Stream.concat(Stream.of(titleWords), Stream.of(authWords));
						if(keyWords.length!=0){
							stream = Stream.concat(stream, Stream.of(keyWords));
						}
						String[] allWords = stream.toArray(String[]::new);
					
						//itero sull'array di parole e confronto ognuna di esse con la keyword data in ingresso al task
						for(String s : allWords){
							if(s.toLowerCase().equals(keyword.toLowerCase())){
								//se vi è una corrispondenza, aggiungo alla lista di future un oggetto pubblicazione che contiene titolo e url dell'articolo
								subRes.add(new Publication(PreParser.getTagInfo(pre, "title"), pub.absUrl("href")));
								break;
							}
						}
					} catch(IOException e){
						if(e instanceof SocketTimeoutException){ //gestione dell'eccezione relativa alla indisponibilità del server ApiCE
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
