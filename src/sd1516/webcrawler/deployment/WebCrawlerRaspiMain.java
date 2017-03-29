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
 * This deployment class initially contains only a Ping Agent and a ManagerAgent.
 * By using the ManagerAgent GUI, the Container can host also one or more
 * Master and Worker Agents.
 * 
 * It needs 3 mandatory arguments:
 * - An unique network identifier (IT IS STRICTLY FORBIDDEN TO HAVE MORE THAN ONE RASPBERRY WITH THE SAME ID NUMBER!!!)
 * - The IP string of the node in which this jar is executed
 * - The IP string of the node in which the Tuple Space is located
 */
public class WebCrawlerRaspiMain {
	
	public static void main(String[] args) throws InterruptedException{
		int raspiId = Integer.parseInt(args[0].toString());
		String myIp = args[1].toString();
		String tcIp = args[2].toString();
		
		jade.core.Runtime rt = jade.core.Runtime.instance();
		Profile p = new ProfileImpl();
		ContainerController rc;
		
		// Rename uniquely the Container
		p.setParameter(Profile.CONTAINER_NAME, SysKb.RASPI_CONT_NAME+raspiId);
		// Set the Main Host reference parameters
		p.setParameter(Profile.MAIN_HOST, tcIp);
		p.setParameter(Profile.MAIN_PORT, ""+SysKb.JADE_PORT);
		p.setParameter(Profile.LOCAL_HOST, myIp);
		p.setParameter(Profile.LOCAL_PORT, ""+SysKb.JADE_PORT);
		// Add T4J as a service
		p.setParameter("services", SysKb.T4J);
		
		// Create the Agent Container
		rc = rt.createAgentContainer(p);
		
		// Create the a Ping and the Manager Agent in the Container
		try {
			// Provide an Agent name, an Agent path and pass the parameters about IP addresses / id number
			AgentController pingAgent = rc.createNewAgent(SysKb.PING_NAME+raspiId, SysKb.PING_AGENT, new Object[]{myIp, tcIp});
			pingAgent.start();
			
			/* This sleep avoids a possible BindException.
			 * It can happen in case of both Agents try to start a new Tucson Node on the same TucsonHelper singleton instance.
			 * During these 1000 ms, the Ping Agent should perform and complete this starting operation,
			 * then the Manager should detect the already existing one and join it.
			 */
			Thread.sleep(1000); 
			
			AgentController managerAgent = rc.createNewAgent(SysKb.MANAGER_NAME+raspiId, SysKb.MANAGER_AGENT, new Object[]{raspiId, myIp, tcIp, rc});
			managerAgent.start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}	
}