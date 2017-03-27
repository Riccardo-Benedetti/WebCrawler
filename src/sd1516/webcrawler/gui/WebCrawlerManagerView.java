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
import javax.swing.ScrollPaneConstants;
import jade.gui.GuiEvent;
import sd1516.webcrawler.interfaces.IWebCrawlerGui;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Dimension;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;

/*
 * ManagerAgent GUI
 */
public class WebCrawlerManagerView extends JFrame {
	
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
	
	/*
	 * GUI Events
	 */
	public static final int KILL = 0;
	public static final int ADDMASTER = 1;
	public static final int REMOVEMASTER = 2;
	public static final int ADDWORKER = 3;
	public static final int REMOVEWORKER = 4;
	
	public WebCrawlerManagerView(IWebCrawlerGui myAgentManager) {
		this.myAgentManager = myAgentManager;
		initializeComponents();
	}

	private void initializeComponents() {
		setResizable(false);
		setSize(new Dimension(450, 300));
		setTitle("Distributed and Fault-Tolerant Web Crawler");
		getContentPane().setLayout(null);
		
		final WebCrawlerManagerView me = this;
		this.addWindowListener(new WindowAdapter(){
			@Override
			public void windowClosing(final WindowEvent ev){
				final GuiEvent ge = new GuiEvent(me, WebCrawlerMasterView.KILL);
				//raise an "Window Closing" event
				WebCrawlerManagerView.this.myAgentManager.postGuiEvent(ge);
			}
		});
		
		btnAddMaster = new JButton("Add Master");
		btnAddMaster.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnAddMaster, WebCrawlerManagerView.ADDMASTER);
				//raise an "Add Master" event
				WebCrawlerManagerView.this.myAgentManager.postGuiEvent(ge); 
			}
		});
		btnAddMaster.setBounds(29, 30, 180, 23);
		getContentPane().add(btnAddMaster);
		
		btnRemoveMaster = new JButton("Remove Master");
		btnRemoveMaster.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnRemoveMaster, WebCrawlerManagerView.REMOVEMASTER);
				// pass the Master to remove
				ge.addParameter(WebCrawlerManagerView.this.lstMaster.getSelectedValue().toString());
				//raise a "Remove Master" event
				WebCrawlerManagerView.this.myAgentManager.postGuiEvent(ge);
			}
		});
		btnRemoveMaster.setBounds(29, 64, 180, 23);
		getContentPane().add(btnRemoveMaster);
		
		btnAddWorker = new JButton("Add Worker");
		btnAddWorker.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnAddWorker, WebCrawlerManagerView.ADDWORKER);
				//raise an "Add Worker" event
				WebCrawlerManagerView.this.myAgentManager.postGuiEvent(ge);
			}
		});
		btnAddWorker.setBounds(242, 30, 177, 23);
		getContentPane().add(btnAddWorker);
		
		btnRemoveWorker = new JButton("Remove Worker");
		btnRemoveWorker.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				final GuiEvent ge = new GuiEvent(btnRemoveWorker, WebCrawlerManagerView.REMOVEWORKER);
				// pass the Worker to remove
				ge.addParameter(WebCrawlerManagerView.this.lstWorker.getSelectedValue());
				//raise a "Remove Worker" event
				WebCrawlerManagerView.this.myAgentManager.postGuiEvent(ge);
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
	
	/*
	 * Update the lists of Masters and Workers according to the outcome of the operation.
	 * In case of the operation has not properly succeed, show a warning message.
	 */
	public void updateView(boolean success, String agent, int opType){
		switch(opType){
			case WebCrawlerManagerView.ADDMASTER:
				if(success){
					dlstMaster.addElement(agent);
				}else{
					JOptionPane.showMessageDialog(null, "Cannot add further Masters", "Add Master operation DENIED", JOptionPane.WARNING_MESSAGE);
				}
				break;
			case WebCrawlerManagerView.REMOVEMASTER:
				if(success){
					dlstMaster.remove(dlstMaster.indexOf(agent));
				}else{
					JOptionPane.showMessageDialog(null, "Cannot remove Master "+ agent + " while waiting", "Remove Master operation DENIED", JOptionPane.WARNING_MESSAGE);
				}
				break;
			case WebCrawlerManagerView.ADDWORKER:
				if(success){
					dlstWorker.addElement(agent);
				}else{
					JOptionPane.showMessageDialog(null, "Cannot add further Workers", "Add Worker operation DENIED", JOptionPane.WARNING_MESSAGE);
				}
				break;
			case WebCrawlerManagerView.REMOVEWORKER:
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
	
	/*
	 * GUI setup
	 */
	public void showGui() {
		this.pack();
        this.setBounds(100, 100, 450, 300);
		super.setVisible(true);
	}
	
	/*
	 * Enable user components to accept new input events
	 */
	public void enableInput() {
		btnAddMaster.setEnabled(true);
		btnAddWorker.setEnabled(true);
		btnRemoveMaster.setEnabled(true);
		btnRemoveWorker.setEnabled(true);
	}
	
	/*
	 * Disable user components while handling a raised event
	 */
	public void disableInput(){
		btnAddMaster.setEnabled(false);
		btnAddWorker.setEnabled(false);
		btnRemoveMaster.setEnabled(false);
		btnRemoveWorker.setEnabled(false);
	}
}