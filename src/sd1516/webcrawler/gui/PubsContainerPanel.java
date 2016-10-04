package sd1516.webcrawler.gui;

import javax.swing.JPanel;
import java.awt.GridLayout;

public class PubsContainerPanel extends JPanel{
	
	private static final long serialVersionUID = 911976051439834827L;

	public void addPubsItem(PubItemPanel[] pubItem){
		this.setLayout(new GridLayout(pubItem.length==0?1:pubItem.length, 0, 3, 3));
		for(PubItemPanel p: pubItem){
			this.add(p);
		}
	}
	
	public void remPubsItem(){
		this.setLayout(new GridLayout(1,0,3,3));
	}
}
