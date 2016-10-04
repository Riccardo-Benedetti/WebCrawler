package sd1516.webcrawler.interfaces;

import jade.gui.GuiEvent;

public interface IWebCrawlerGui {
	void postGuiEvent(GuiEvent ge);
	String getAgentName();
}
