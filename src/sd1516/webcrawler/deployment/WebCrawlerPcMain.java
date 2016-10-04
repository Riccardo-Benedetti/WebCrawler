package sd1516.webcrawler.deployment;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public class WebCrawlerPcMain {

	public static void main(String[] args) {
		jade.core.Runtime rt = jade.core.Runtime.instance();
		Profile p = new ProfileImpl();
		
		p.setParameter(Profile.MAIN_HOST, "localhost");
		p.setParameter(Profile.PLATFORM_ID, "WebCrawlerPlatform");
		p.setParameter(Profile.GUI, "true");
		p.setParameter("services", "it.unibo.tucson.jade.service.TucsonService");
		ContainerController mc = rt.createMainContainer(p);
		
		try {
			AgentController wd = mc.createNewAgent("watchdog", "sd1516.webcrawler.devices.WatchdogAgent", null);
			wd.start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}

}
