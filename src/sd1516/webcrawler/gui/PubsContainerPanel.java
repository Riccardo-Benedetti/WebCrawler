package sd1516.webcrawler.gui;

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

import javax.swing.JPanel;
import java.awt.GridLayout;

/*
 * Publications viewer component
 */
public class PubsContainerPanel extends JPanel{
	
	private static final long serialVersionUID = 911976051439834827L;

	/*
	 * Fill the container with Publication items
	 */
	public void addPubsItem(PubItemPanel[] pubItem){
		this.setLayout(new GridLayout(pubItem.length==0?1:pubItem.length, 0, 3, 3));
		for(PubItemPanel p: pubItem){
			this.add(p);
		}
	}
	
	/*
	 * Container cleaning
	 */
	public void remPubsItem(){
		this.setLayout(new GridLayout(1,0,3,3));
	}
}
