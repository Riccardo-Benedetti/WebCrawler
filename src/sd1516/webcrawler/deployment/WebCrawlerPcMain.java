package sd1516.webcrawler.deployment;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import sd1516.webcrawler.sysconstants.SysKb;

public class WebCrawlerPcMain {

	public static void main(String[] args) {
		String myIp = args[0].toString();
		String tcIp = args[1].toString();
		
		jade.core.Runtime rt = jade.core.Runtime.instance();
		Profile p = new ProfileImpl();
		
		p.setParameter(Profile.MAIN_HOST, myIp);
		p.setParameter(Profile.PLATFORM_ID, SysKb.PLATFORM);
		p.setParameter(Profile.GUI, "true");
		p.setParameter("services", SysKb.T4J);
		ContainerController mc = rt.createMainContainer(p);
		
		try {
			AgentController wd = mc.createNewAgent(SysKb.WATCHDOG_NAME, SysKb.WATCHDOG_AGENT, new Object[]{myIp, tcIp});
			wd.start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}

}
