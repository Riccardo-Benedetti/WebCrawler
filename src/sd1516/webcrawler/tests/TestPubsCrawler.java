package sd1516.webcrawler.tests;

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

import sd1516.webcrawler.utils.Publication;
import sd1516.webcrawler.utils.PubsCrawler;

/*
 * Crawling Test (it may take a few minutes)
 * Get all publication containing the keyword "omicini" and
 * print title + url
 */
public class TestPubsCrawler {
	
	public static void main(String[] args) {
		Publication[] results = PubsCrawler.getPubsByKeyword("omicini");
		
		for(Publication p : results){
			System.out.println("PubTitle: "+p.getTitle());
			System.out.println("PubUrl: "+p.getUrl()+"\n");
		}
	}
}