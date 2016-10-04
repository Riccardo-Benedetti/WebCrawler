package sd1516.webcrawler.gui;

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

public class WebCrawlerMasterView extends JFrame {

	private static final long serialVersionUID = -2498507787790986541L;
	
	private JPanel contentPane;
	private JTextField txtSearch;
	private JLabel lblSearch;
	private JButton btnSearch;
	private JScrollPane spnlResults;
	
	private IWebCrawlerGui myAgentMaster;
	
	public static final int KILL = 0;
	public static final int SEARCH = 1;

	/**
	 * Create the frame.
	 */
	public WebCrawlerMasterView(IWebCrawlerGui myAgentMaster) {
		super(myAgentMaster.getAgentName());
		this.myAgentMaster = myAgentMaster;
		this.initializeComponents();
	}
	
	private void initializeComponents(){
		setResizable(false);
		setTitle("Distributed and Fault-Tolerant Web Crawler - Developed by Riccardo Benedetti & Elisabetta Ramilli");
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
				WebCrawlerMasterView.this.myAgentMaster.postGuiEvent(ge);
			}
		});
		
		txtSearch = new JTextField();
		txtSearch.setBounds(30, 40, 470, 20);
		contentPane.add(txtSearch);
		txtSearch.setColumns(10);
		
		lblSearch = new JLabel("Search for publications with the following keywords:");
		lblSearch.setBounds(30, 15, 600, 14);
		contentPane.add(lblSearch);
		
		btnSearch = new JButton("Search");
		btnSearch.setBounds(510, 39, 120, 22);
		btnSearch.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnSearch, WebCrawlerMasterView.SEARCH);
				ge.addParameter(WebCrawlerMasterView.this.txtSearch.getText());
				PubsContainerPanel pnlPubsCont = new PubsContainerPanel();
				pnlPubsCont.remPubsItem();
				spnlResults.setViewportView(pnlPubsCont);
				WebCrawlerMasterView.this.myAgentMaster.postGuiEvent(ge); //accoda gli eventi della GUI
			}
		});
		contentPane.add(btnSearch);
		
		spnlResults = new JScrollPane();
		spnlResults.setBounds(30, 80, 600, 250);
		spnlResults.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		contentPane.add(spnlResults);
	}

	public void registerListener(ActionListener listener){
		this.btnSearch.addActionListener(listener);
	}
	
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

	/**
	 * Primo avvio della GUI
	 */
	public void showGui() {
		this.pack();
        this.setBounds(100, 100, 670, 381);
		super.setVisible(true);
	}
	
	/**
	 * Abilita gli input components per nuova ricerca
	 */
	public void enableInput() {
		btnSearch.setEnabled(true);
		txtSearch.setEditable(true);
	}
	
	/**
	 * Disabilita gli input components durante l'elaborazione dei Workers
	 */
	public void disableInput(){
		btnSearch.setEnabled(false);
		txtSearch.setEditable(false);
	}
	
	/**
	 * alert di errore
	 */
	public void msgbox(String s){
		JOptionPane.showMessageDialog(null, s, "ERROR IN SEARCH", JOptionPane.ERROR_MESSAGE);
	}
}