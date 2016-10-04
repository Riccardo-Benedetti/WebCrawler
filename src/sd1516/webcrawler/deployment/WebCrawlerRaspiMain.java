package sd1516.webcrawler.deployment;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public class WebCrawlerRaspiMain {
	
	public static void main(String[] args) throws InterruptedException{
		int raspiId = Integer.parseInt(args[0].toString());
		String myIp = args[1].toString();
		String tcIp = args[2].toString();
		
		jade.core.Runtime rt = jade.core.Runtime.instance();
		Profile p = new ProfileImpl();
		ContainerController rc;
		
		p.setParameter(Profile.CONTAINER_NAME, "RaspiContainer"+raspiId);
		p.setParameter(Profile.MAIN_HOST, tcIp);
		p.setParameter(Profile.MAIN_PORT, "1099");
		p.setParameter(Profile.LOCAL_HOST, myIp);
		p.setParameter(Profile.LOCAL_PORT, "1099");
		p.setParameter("services", "it.unibo.tucson.jade.service.TucsonService");
		
		rc = rt.createAgentContainer(p);
		
		try {
			AgentController pingAgent = rc.createNewAgent("pRB"+raspiId, "sd1516.webcrawler.devices.PingAgent", new Object[]{myIp, tcIp});
			pingAgent.start();
			Thread.sleep(1000); //Per creare il Tucson Node solo una volta (Evita BindException)
			AgentController managerAgent = rc.createNewAgent("mRB"+raspiId, "sd1516.webcrawler.devices.AgentsManager", new Object[]{raspiId, myIp, tcIp, rc});
			managerAgent.start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}	
}