package sd1516.webcrawler.deployment;

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

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import sd1516.webcrawler.sysconstants.SysKb;

/*
 * This deployment must be EXECUTED AS THE FIRST.
 * It contains the Watchdog Agent and represent the
 * centralized point of all the network fault tolerance.
 * 
 * It need 2 mandatory arguments:
 * - The IP string of the node in which this jar is executed
 * - The IP string of the node in which the Tuple Space is located
 */
public class WebCrawlerPcMain {

	public static void main(String[] args) {
		String myIp = args[0].toString();
		String tcIp = args[1].toString();
		
		jade.core.Runtime rt = jade.core.Runtime.instance();
		Profile p = new ProfileImpl();
		
		// Set this as the jade Main Host
		p.setParameter(Profile.MAIN_HOST, myIp);
		// Set Platform name
		p.setParameter(Profile.PLATFORM_ID, SysKb.PLATFORM);
		// Enable Jade GUI
		p.setParameter(Profile.GUI, "true");
		// Add T4J as a service
		p.setParameter("services", SysKb.T4J);
		
		// Create the Main Agent Container
		ContainerController mc = rt.createMainContainer(p);
		
		// Create the a Watchdog Agent in the Container
		try {
			// Provide an Agent name, an Agent path and pass the parameters about IP addresses
			AgentController wd = mc.createNewAgent(SysKb.WATCHDOG_NAME, SysKb.WATCHDOG_AGENT, new Object[]{myIp, tcIp});
			// Raise the Watchdog Agent
			wd.start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}

}
