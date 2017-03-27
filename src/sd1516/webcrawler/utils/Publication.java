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

/*
 * Publication item
 */
public class Publication {

	private String title;
	private String url;
	
	public Publication(String title, String url){
		this.title = title;
		this.url = url;
	}
	
	public String getTitle(){
		return this.title;
	}
	
	public String getUrl(){
		return this.url;
	}
}
