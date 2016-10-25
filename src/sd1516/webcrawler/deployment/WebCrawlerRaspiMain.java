package sd1516.webcrawler.deployment;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import sd1516.webcrawler.sysconstants.SysKb;

public class WebCrawlerRaspiMain {
	
	public static void main(String[] args) throws InterruptedException{
		int raspiId = Integer.parseInt(args[0].toString());
		String myIp = args[1].toString();
		String tcIp = args[2].toString();
		
		jade.core.Runtime rt = jade.core.Runtime.instance();
		Profile p = new ProfileImpl();
		ContainerController rc;
		
		p.setParameter(Profile.CONTAINER_NAME, SysKb.RASPI_CONT_NAME+raspiId);
		p.setParameter(Profile.MAIN_HOST, tcIp);
		p.setParameter(Profile.MAIN_PORT, ""+SysKb.JADE_PORT);
		p.setParameter(Profile.LOCAL_HOST, myIp);
		p.setParameter(Profile.LOCAL_PORT, ""+SysKb.JADE_PORT);
		p.setParameter("services", SysKb.T4J);
		
		rc = rt.createAgentContainer(p);
		
		try {
			AgentController pingAgent = rc.createNewAgent(SysKb.PING_NAME+raspiId, SysKb.PING_AGENT, new Object[]{myIp, tcIp});
			pingAgent.start();
			Thread.sleep(1000); //Per creare il Tucson Node solo una volta (Evita BindException)
			AgentController managerAgent = rc.createNewAgent(SysKb.MANAGER_NAME+raspiId, SysKb.MANAGER_AGENT, new Object[]{raspiId, myIp, tcIp, rc});
			managerAgent.start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}	
}