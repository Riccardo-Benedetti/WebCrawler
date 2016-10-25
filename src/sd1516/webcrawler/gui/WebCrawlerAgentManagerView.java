package sd1516.webcrawler.gui;

import javax.swing.JFrame;
import javax.swing.ScrollPaneConstants;
import jade.gui.GuiEvent;
import sd1516.webcrawler.interfaces.IWebCrawlerGui;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Dimension;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;

public class WebCrawlerAgentManagerView extends JFrame {
	
	private static final long serialVersionUID = -7267969757246044299L;
	
	private JButton btnAddMaster;
	private JButton btnRemoveMaster;
	private JButton btnAddWorker;
	private JButton btnRemoveWorker;
	private JScrollPane spnlMasters;
	private JScrollPane spnlWorkers;
	private JList<String> lstMaster;
	private JList<String> lstWorker;
	private DefaultListModel<String> dlstMaster;
	private DefaultListModel<String> dlstWorker;
	
	private IWebCrawlerGui myAgentManager;
	
	public static final int KILL = 0;
	public static final int ADDMASTER = 1;
	public static final int REMOVEMASTER = 2;
	public static final int ADDWORKER = 3;
	public static final int REMOVEWORKER = 4;
	
	public WebCrawlerAgentManagerView(IWebCrawlerGui myAgentManager) {
		this.myAgentManager = myAgentManager;
		initializeComponents();
	}

	private void initializeComponents() {
		setResizable(false);
		setSize(new Dimension(450, 300));
		setTitle("Distributed and Fault-Tolerant Web Crawler");
		getContentPane().setLayout(null);
		
		btnAddMaster = new JButton("Add Master");
		btnAddMaster.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnAddMaster, WebCrawlerAgentManagerView.ADDMASTER);
				WebCrawlerAgentManagerView.this.myAgentManager.postGuiEvent(ge); //accoda gli eventi della GUI
			}
		});
		btnAddMaster.setBounds(29, 30, 180, 23);
		getContentPane().add(btnAddMaster);
		
		btnRemoveMaster = new JButton("Remove Master");
		btnRemoveMaster.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnRemoveMaster, WebCrawlerAgentManagerView.REMOVEMASTER);
				ge.addParameter(WebCrawlerAgentManagerView.this.lstMaster.getSelectedValue().toString());
				WebCrawlerAgentManagerView.this.myAgentManager.postGuiEvent(ge); //accoda gli eventi della GUI
			}
		});
		btnRemoveMaster.setBounds(29, 64, 180, 23);
		getContentPane().add(btnRemoveMaster);
		
		btnAddWorker = new JButton("Add Worker");
		btnAddWorker.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnAddWorker, WebCrawlerAgentManagerView.ADDWORKER);
				WebCrawlerAgentManagerView.this.myAgentManager.postGuiEvent(ge); //accoda gli eventi della GUI
			}
		});
		btnAddWorker.setBounds(242, 30, 177, 23);
		getContentPane().add(btnAddWorker);
		
		btnRemoveWorker = new JButton("Remove Worker");
		btnRemoveWorker.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnRemoveWorker, WebCrawlerAgentManagerView.REMOVEWORKER);
				ge.addParameter(WebCrawlerAgentManagerView.this.lstWorker.getSelectedValue());
				WebCrawlerAgentManagerView.this.myAgentManager.postGuiEvent(ge); //accoda gli eventi della GUI
			}
		});
		btnRemoveWorker.setBounds(242, 64, 177, 23);
		getContentPane().add(btnRemoveWorker);
		
		spnlMasters = new JScrollPane();
		spnlMasters.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		spnlMasters.setBounds(29, 113, 180, 130);
		getContentPane().add(spnlMasters);
		
		spnlWorkers = new JScrollPane();
		spnlWorkers.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		spnlWorkers.setBounds(239, 113, 180, 130);
		getContentPane().add(spnlWorkers);

		dlstMaster = new DefaultListModel<String>();
		dlstWorker = new DefaultListModel<String>();
		
		lstMaster = new JList<String>(dlstMaster);
		lstMaster.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		spnlMasters.setViewportView(lstMaster);
		
		lstWorker = new JList<String>(dlstWorker);
		lstWorker.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		spnlWorkers.setViewportView(lstWorker);
	}
	
	public void updateView(boolean success, String agent, int opType){
		switch(opType){
			case WebCrawlerAgentManagerView.ADDMASTER:
				if(success){
					dlstMaster.addElement(agent);
				}else{
					JOptionPane.showMessageDialog(null, "Cannot add more Masters", "Add Master operation DENIED", JOptionPane.WARNING_MESSAGE);
				}
				break;
			case WebCrawlerAgentManagerView.REMOVEMASTER:
				if(success){
					dlstMaster.remove(dlstMaster.indexOf(agent));
				}else{
					JOptionPane.showMessageDialog(null, "Cannot remove Master "+ agent + " while waiting", "Remove Master operation DENIED", JOptionPane.WARNING_MESSAGE);
				}
				break;
			case WebCrawlerAgentManagerView.ADDWORKER:
				if(success){
					dlstWorker.addElement(agent);
				}else{
					JOptionPane.showMessageDialog(null, "Cannot add more Workers", "Add Worker operation DENIED", JOptionPane.WARNING_MESSAGE);
				}
				break;
			case WebCrawlerAgentManagerView.REMOVEWORKER:
				if(success){
					dlstWorker.remove(dlstWorker.indexOf(agent));
				}else{
					JOptionPane.showMessageDialog(null, "Cannot remove Worker "+ agent + " while working", "Remove Worker operation DENIED", JOptionPane.WARNING_MESSAGE);
				}
				break;
			default:
				break;
		}
	}
	
	/**
	 * Primo avvio della GUI
	 */
	public void showGui() {
		this.pack();
        this.setBounds(100, 100, 450, 300);
		super.setVisible(true);
	}
	
	/**
	 * Abilita gli input components per nuovi user input
	 */
	public void enableInput() {
		btnAddMaster.setEnabled(true);
		btnAddWorker.setEnabled(true);
		btnRemoveMaster.setEnabled(true);
		btnRemoveWorker.setEnabled(true);
	}
	
	/**
	 * Disabilita gli input components durante l'elaborazione dell'agent manager
	 */
	public void disableInput(){
		btnAddMaster.setEnabled(false);
		btnAddWorker.setEnabled(false);
		btnRemoveMaster.setEnabled(false);
		btnRemoveWorker.setEnabled(false);
	}
}