package sd1516.webcrawler.gui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import sd1516.webcrawler.utils.Publication;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.LineBorder;
import java.awt.Color;

public class PubItemPanel extends JPanel implements MouseListener{

	private static final long serialVersionUID = 2353532880026830322L;

    private JLabel lblTitle;
    private JLabel lblUrl;
    private GroupLayout groupLayout;
    
    public PubItemPanel(Publication pub) {
    	this.lblTitle = new JLabel(pub.getTitle());
    	this.lblUrl = new JLabel(pub.getUrl());
    	configureComponents();
    }

	private void configureComponents() {
		this.setBorder(new LineBorder(new Color(0, 0, 0), 1, true));
    	
		this.lblUrl.setForeground(Color.BLUE);
    	this.lblUrl.setCursor(new Cursor(Cursor.HAND_CURSOR));
    	this.lblUrl.addMouseListener(this);
        
    	this.groupLayout = new GroupLayout(this);
        
        groupLayout.setHorizontalGroup(
        	groupLayout.createParallelGroup(Alignment.LEADING)
        		.addGroup(groupLayout.createSequentialGroup()
        			.addContainerGap()
        			.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
        				.addComponent(lblTitle, GroupLayout.DEFAULT_SIZE, 426, Short.MAX_VALUE)
        				.addComponent(lblUrl, GroupLayout.DEFAULT_SIZE, 426, Short.MAX_VALUE))
        			.addContainerGap())
        );
        groupLayout.setVerticalGroup(
        	groupLayout.createParallelGroup(Alignment.LEADING)
        		.addGroup(groupLayout.createSequentialGroup()
        			.addGap(6)
        			.addComponent(lblTitle)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(lblUrl)
        			.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        setLayout(groupLayout);
	}

	@Override
	public void mouseClicked(MouseEvent ev) {
		if (Desktop.isDesktopSupported()) {
		      try {
		        Desktop.getDesktop().browse(new URI(lblUrl.getText()));
		        this.lblUrl.setForeground(Color.RED);
		      } catch (IOException | URISyntaxException e) { /* TODO: error handling */ }   
		}
	}

	@Override
	public void mousePressed(MouseEvent ev) {
		// Auto-generated method stub
	}

	@Override
	public void mouseReleased(MouseEvent ev) {
		// Auto-generated method stub
		
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// Auto-generated method stub
		
	}
}