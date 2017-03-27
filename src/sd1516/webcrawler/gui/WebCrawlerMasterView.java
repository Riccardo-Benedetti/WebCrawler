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

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import jade.gui.GuiEvent;
import sd1516.webcrawler.interfaces.IWebCrawlerGui;
import sd1516.webcrawler.utils.Publication;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;

/*
 * MasterAgent GUI
 */
public class WebCrawlerMasterView extends JFrame {

	private static final long serialVersionUID = -2498507787790986541L;
	
	private JPanel contentPane;
	private JTextField txtSearch;
	private JLabel lblSearch;
	private JButton btnSearch;
	private JScrollPane spnlResults;
	
	private IWebCrawlerGui myAgentMaster;
	
	/*
	 * GUI Events
	 */
	public static final int KILL = 0;
	public static final int SEARCH = 1;

	public WebCrawlerMasterView(IWebCrawlerGui myAgentMaster) {
		super(myAgentMaster.getAgentName());
		this.myAgentMaster = myAgentMaster;
		this.initializeComponents();
	}
	
	private void initializeComponents(){
		setResizable(false);
		setTitle(myAgentMaster.getAgentName());
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		final WebCrawlerMasterView me = this;
		this.addWindowListener(new WindowAdapter(){
			@Override
			public void windowClosing(final WindowEvent ev){
				final GuiEvent ge = new GuiEvent(me, WebCrawlerMasterView.KILL);
				//raise an "Window Closing" event
				WebCrawlerMasterView.this.myAgentMaster.postGuiEvent(ge);
			}
		});
		
		txtSearch = new JTextField();
		txtSearch.setBounds(30, 40, 470, 20);
		contentPane.add(txtSearch);
		txtSearch.setColumns(10);
		
		lblSearch = new JLabel("Search for publications containing the following keywords:");
		lblSearch.setBounds(30, 15, 600, 14);
		contentPane.add(lblSearch);
		
		btnSearch = new JButton("Search");
		btnSearch.setBounds(510, 39, 120, 22);
		btnSearch.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnSearch, WebCrawlerMasterView.SEARCH);
				// pass the entered Keywords string
				ge.addParameter(WebCrawlerMasterView.this.txtSearch.getText());
				// clean the output panel
				PubsContainerPanel pnlPubsCont = new PubsContainerPanel();
				pnlPubsCont.remPubsItem();
				spnlResults.setViewportView(pnlPubsCont);
				// raise a "New Search" event
				WebCrawlerMasterView.this.myAgentMaster.postGuiEvent(ge); 
			}
		});
		contentPane.add(btnSearch);
		
		spnlResults = new JScrollPane();
		spnlResults.setBounds(30, 80, 600, 250);
		spnlResults.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		contentPane.add(spnlResults);
	}

	/*
	 * Listening for Button events 
	 */
	public void registerListener(ActionListener listener){
		this.btnSearch.addActionListener(listener);
	}
	
	/*
	 * Show in output all the Publications found
	 * (or warn if not found any)
	 */
	public void updateView(Publication[] results){
		if(results.length==0){
			JLabel lbl = new JLabel("No publications found");
			spnlResults.setViewportView(lbl);
		}else{
			PubItemPanel[] pnlResults = new PubItemPanel[results.length];
			for(int i=0; i<results.length; i++){
				pnlResults[i] = new PubItemPanel(results[i]);
			}
			
			PubsContainerPanel pnlPubsCont = new PubsContainerPanel();
			pnlPubsCont.addPubsItem(pnlResults);
			spnlResults.setViewportView(pnlPubsCont);
		}
		
		this.btnSearch.setEnabled(true);
		this.txtSearch.setEditable(true);
	}

	/*
	 * GUI setup
	 */
	public void showGui() {
		this.pack();
        this.setBounds(100, 100, 670, 381);
		super.setVisible(true);
	}
	
	/*
	 * Enable user components to accept new input events
	 */
	public void enableInput() {
		btnSearch.setEnabled(true);
		txtSearch.setEditable(true);
	}
	
	/*
	 * Disable user components while waiting for workers completion
	 */
	public void disableInput(){
		btnSearch.setEnabled(false);
		txtSearch.setEditable(false);
	}
	
	/*
	 * Raise an error message
	 */
	public void msgbox(String s){
		JOptionPane.showMessageDialog(null, s, "SEARCH HAS FAILED", JOptionPane.ERROR_MESSAGE);
	}
}