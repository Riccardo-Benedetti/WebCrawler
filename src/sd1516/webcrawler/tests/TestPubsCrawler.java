package sd1516.webcrawler.tests;

import sd1516.webcrawler.pubscrawler.PubsCrawler;
import sd1516.webcrawler.utils.Publication;

public class TestPubsCrawler {
	
	public static void main(String[] args) {
		Publication[] results = PubsCrawler.getPubsByKeyword("omicini");
		
		for(Publication p : results){
			System.out.println("PubTitle: "+p.getTitle());
			System.out.println("PubUrl: "+p.getUrl()+"\n");
		}
	}
}