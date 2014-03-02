package ch.specchio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import au.ands.org.researchdata.RDACollectionDescriptor;
import au.ands.org.researchdata.ResearchDataAustralia;

import ch.specchio.client.SPECCHIOClient;
import ch.specchio.client.SPECCHIOClientException;
import ch.specchio.client.SPECCHIOWebClientException;
import ch.specchio.constants.UserRoles;
import ch.specchio.proc_modules.FileOutputManager;
import ch.specchio.proc_modules.ModuleException;
import ch.specchio.proc_modules.RadianceToReflectance;
import ch.specchio.proc_modules.SpaceProcessingChainComponent;
import ch.specchio.proc_modules.VisualisationModule;
import ch.specchio.proc_modules.VisualisationSelectionDialog;
import ch.specchio.processing_plane.ProcessingPlane;
import ch.specchio.processors.DataProcessor;
import ch.specchio.queries.MatlabQueryBuilder;
import ch.specchio.queries.Query;
import ch.specchio.queries.QueryCondition;
import ch.specchio.queries.QueryConditionChangeInterface;
import ch.specchio.queries.QueryConditionObject;
import ch.specchio.queries.RQueryBuilder;
import ch.specchio.query_builder.QueryController;
import ch.specchio.spaces.Space;
import ch.specchio.types.Campaign;
import ch.specchio.types.Spectrum;

public class QueryBuilder extends JFrame  implements ActionListener, TreeSelectionListener, ChangeListener, ClipboardOwner, QueryConditionChangeInterface 
{

	private static final long serialVersionUID = 1L;

	
	boolean hierarchy_browser = false;
	boolean mds_restrictions = false;
	boolean is_admin;

	JTabbedPane data_selection_tabs;
	JTextArea SQL_query;
	JTextField resulting_rows;
	public SpectralDataBrowser sdb;
	public JCheckBox show_only_my_data;
	
	private JButton show_report;
	private JButton file_export;
	private JButton process;
	private JButton spectral_plot;
	private JButton refl;
	private JButton publish_collection;
	
	JRadioButton split_spaces_by_sensor_and_unit = new JRadioButton("Split spaces by sensor and unit");
	JRadioButton split_spaces_by_sensor = new JRadioButton("Split spaces by sensor");
	JRadioButton split_spaces_by_sensor_and_unit_and_instrument_and_cal = new JRadioButton("Split spaces by sensor, instrument, calibration_no and unit");
	
	ButtonGroup split_group = new ButtonGroup();

	SPECCHIOClient specchio_client;
	
	public Query query;
	
	private ArrayList<Integer> ids_matching_query;


	private JMenuBar menuBar;


	private JMenu menu;
	private JMenu test_menu;
	
	
	// new class for a JTextArea to include a popup listener
	class SQLQueryArea extends JTextArea implements ActionListener
	{
		
		QueryBuilder qb;
		
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		JPopupMenu popup;

		class PopupListener extends MouseAdapter {
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e);
			}
			
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e);
			}
			
			private void maybeShowPopup(MouseEvent e) {
				if (e.isPopupTrigger()) {
					popup.show(e.getComponent(),
							e.getX(), e.getY());
					
				}
			}
		}	
		
		
		public SQLQueryArea(QueryBuilder qb, int x, int y)
		{
			super(x,y);
			
			this.qb = qb;

			popup = new JPopupMenu();
			JMenuItem menuItem = new JMenuItem("Copy Matlab-ready query to clipboard");
		    menuItem.addActionListener(this);
		    popup.add(menuItem);	    
		    menuItem = new JMenuItem("Copy R-ready query to clipboard");
		    menuItem.addActionListener(this);
		    popup.add(menuItem);	    		    
		    
			PopupListener popupListener = new PopupListener();
			addMouseListener(popupListener);				    
			
		}


		public void actionPerformed(ActionEvent e) {
			
			if("Copy Matlab-ready query to clipboard".equals(e.getActionCommand()))
			{
				try {
					qb.copy_matlab_query_to_clipboard();
				}
				catch (IOException ex) {
					// write error; probably never happens
					JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not copy to the clipboard", JOptionPane.ERROR_MESSAGE);
				}
				
			}
			
			if("Copy R-ready query to clipboard".equals(e.getActionCommand()))
			{
				try {
					qb.copy_r_query_to_clipboard();
				}
				catch (IOException ex) {
					// write error; probably never happens
					JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not copy to the clipboard", JOptionPane.ERROR_MESSAGE);
				}
				
			}
			

			
		}
		
	}
	
	public QueryBuilder() throws SPECCHIOClientException
	{
		this("Query Builder (V3)");
	}
	

	public QueryBuilder(String title) throws SPECCHIOClientException
	{
		this(title, "combined");		
	}
	

	
	public QueryBuilder(String title, String type) throws SPECCHIOClientException
	{
		super(title);

		// get a reference to the application's client object
		this.specchio_client = SPECCHIOApplication.getInstance().getClient();
		
		// create a query object and initalise the matching ids to an empty list
		query = new Query("spectrum");
		query.addColumn("spectrum_id");
		ids_matching_query = new ArrayList<Integer>();
		
		if (type.equals("combined"))
		{
			hierarchy_browser = true;
			mds_restrictions = true;
		}
		
		if (type.equals("hierarchy_browser"))
		{
			hierarchy_browser = true;
		}	
		
		if (type.equals("mds_restrictions"))
		{
			mds_restrictions = true;
		}				
		
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		// create GUI
		
		// set border layout for this dialog
		this.setLayout(new BorderLayout());
		
		// create query panel
		JPanel query_panel = new JPanel();
		GridbagLayouter query_panel_l = new GridbagLayouter(query_panel);
		
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(4, 4, 4, 4);
		constraints.gridheight = 1;
		constraints.anchor = GridBagConstraints.WEST;
		constraints.fill = GridBagConstraints.HORIZONTAL;

		// add query results panel
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.gridwidth = GridBagConstraints.REMAINDER;
		JPanel SQL_query_panel = new JPanel();
		Border blackline = BorderFactory.createLineBorder(Color.black);
		TitledBorder tb = BorderFactory.createTitledBorder(blackline, "Matching Spectra");
		SQL_query_panel.setBorder(tb);
		SQL_query = new SQLQueryArea(this, 8, 50);
		SQL_query.setLineWrap(true);
		SQL_query.setWrapStyleWord(true);
		SQL_query.setEditable(false);
		SQL_query_panel.add(new JScrollPane(SQL_query));
		query_panel_l.insertComponent(SQL_query_panel, constraints);
		
		// add resulting rows panel
		constraints.gridx = 0;
		constraints.gridy++;
		constraints.gridwidth = GridBagConstraints.REMAINDER;
		JPanel resulting_rows_panel = new JPanel();
		tb = BorderFactory.createTitledBorder(blackline, "Number of results");
		resulting_rows_panel.setBorder(tb);
		resulting_rows = new JTextField(10);
		resulting_rows.setEditable(false);
		resulting_rows_panel.add(resulting_rows);
		query_panel_l.insertComponent(resulting_rows_panel, constraints);
		
		show_report = new JButton("Show report");
		show_report.setActionCommand("show_report");
		show_report.addActionListener(this);		

		constraints.gridx = 0;
		constraints.gridy++;	
		constraints.fill = GridBagConstraints.NONE;
		constraints.gridwidth = 1;
		query_panel_l.insertComponent(show_report, constraints);
		
		file_export = new JButton("File export");
		file_export.setActionCommand("file_export");
		file_export.addActionListener(this);		

		constraints.gridx++;	
		query_panel_l.insertComponent(file_export, constraints);
		
		
		process = new JButton("Process");
		process.setActionCommand("process");
		process.addActionListener(this);		

		constraints.gridx++;	
		query_panel_l.insertComponent(process, constraints);
		
		spectral_plot = new JButton("Spectral Plot");
		spectral_plot.setActionCommand(VisualisationSelectionDialog.spectral_multiplot);
		spectral_plot.addActionListener(this);		

		constraints.gridx++;	
		query_panel_l.insertComponent(spectral_plot, constraints);		
		
		refl = new JButton("Refl.Calc");
		refl.setActionCommand("refl");
		refl.addActionListener(this);		

		constraints.gridx++;	
		query_panel_l.insertComponent(refl, constraints);
		
		if (specchio_client.getCapability(ResearchDataAustralia.ANDS_SERVER_CAPABILITY) != null) {
			publish_collection = new JButton("Publish Collection");
			publish_collection.setActionCommand("publish_collection");
			publish_collection.addActionListener(this);
			constraints.gridx++;
			query_panel_l.insertComponent(publish_collection, constraints);
		} else {
			publish_collection = null;
		}
		
		// all remaining elements should span whole panel
		constraints.gridwidth = GridBagConstraints.REMAINDER;
		
		split_group.add(this.split_spaces_by_sensor);
		split_group.add(this.split_spaces_by_sensor_and_unit);
		split_spaces_by_sensor_and_unit.setSelected(true);
		split_group.add(this.split_spaces_by_sensor_and_unit_and_instrument_and_cal);		
		
		constraints.gridx = 0;	
		constraints.gridy = 3;
		
		query_panel_l.insertComponent(new JLabel("Splitting rules for file export and plotting:"), constraints);	
		constraints.gridy++;
		
		query_panel_l.insertComponent(split_spaces_by_sensor, constraints);	
		constraints.gridy++;
		query_panel_l.insertComponent(split_spaces_by_sensor_and_unit, constraints);	
		constraints.gridy++;
		query_panel_l.insertComponent(split_spaces_by_sensor_and_unit_and_instrument_and_cal, constraints);		
		
		// create visualisation menu
		menuBar = new JMenuBar();
		menu = new JMenu("Visualisations");
	    JMenuItem menuItem = new JMenuItem(VisualisationSelectionDialog.spectral_multiplot);
	    menuItem.addActionListener(this);
	    menu.add(menuItem);	    
	    
	    menuItem = new JMenuItem(VisualisationSelectionDialog.spectral_scatter_multiplot);
	    menuItem.addActionListener(this);
	    menu.add(menuItem);	   
	    
	    menuItem = new JMenuItem(VisualisationSelectionDialog.time_line_plot);
	    menuItem.addActionListener(this);
	    menu.add(menuItem);		    
	    
	    menuItem = new JMenuItem(VisualisationSelectionDialog.time_line_expl);
	    menuItem.addActionListener(this);
	    menu.add(menuItem);		
	    
	    menuItem = new JMenuItem(VisualisationSelectionDialog.sampling_points_plot);
	    menuItem.addActionListener(this);
	    menu.add(menuItem);	
	    
	    menuItem = new JMenuItem(VisualisationSelectionDialog.gonio_hem_expl);
	    menuItem.addActionListener(this);
	    menu.add(menuItem);		    
	    
	    menuBar.add(menu);
	    
	    // test menu for developing and debugging purposes
	    // TODO the Test menu is here ....
	    test_menu = new JMenu("Test");
	    menuItem = new JMenuItem("Test");
	    menuItem.addActionListener(this);
	    test_menu.add(menuItem);	    
//	    menuBar.add(test_menu);
	    
	    
	    this.setJMenuBar(menuBar);		

		
		// create tabbed pane
		data_selection_tabs = new JTabbedPane();
		
		sdb = new SpectralDataBrowser(specchio_client, false); // trick to get the order by field set
		
		if(hierarchy_browser){

			// create browser and add to control panel
			sdb = new SpectralDataBrowser(specchio_client, false);
				
			// load all campaigns
			sdb.build_tree();
							
			// add tree listener
			sdb.tree.addTreeSelectionListener(this);
			sdb.order_by_box.addActionListener(this);
				
			show_only_my_data = new JCheckBox("Show only my data.");
			show_only_my_data.addChangeListener(this);
				
			JPanel sdb_panel = new JPanel();
			sdb_panel.setLayout(new BorderLayout());
			sdb_panel.add("North", show_only_my_data);
			sdb_panel.add("Center", sdb);
				
			data_selection_tabs.addTab("Browser", sdb_panel);
	
		}
		
		if (mds_restrictions)
		{
			QueryController qc = new QueryController(this.specchio_client, "Standard");
			qc.addChangeListener(this);
			SpectrumQueryPanel query_condition_panel = new SpectrumQueryPanel(this, qc);
			JScrollPane scroll_pane = new JScrollPane(query_condition_panel);
			scroll_pane.getVerticalScrollBar().setUnitIncrement(10);
			data_selection_tabs.addTab("Query conditions", scroll_pane);
		}
		
		is_admin = specchio_client.isLoggedInWithRole(UserRoles.ADMIN);
		
		

	    
		
		add("East", query_panel);
		add("Center", data_selection_tabs);
		pack();
		
		// disable the action buttons until a selection is made
		setButtonsEnabled(false);
		
		
		query.setOrderBy(sdb.get_order_by_field());
		
	}
	
	public void copy_matlab_query_to_clipboard() throws IOException
	{
		// write the Matlab code into a string
		MatlabQueryBuilder mqb = new MatlabQueryBuilder();
		StringWriter writer = new StringWriter();
		mqb.writeMatlabCode(writer, query);
		
		// copy the string to the clipboard
		StringSelection stringSelection = new StringSelection(writer.toString());
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	    clipboard.setContents(stringSelection , this);
	}
	
	
	public void copy_r_query_to_clipboard()  throws IOException {
		// write the R code into a string
		RQueryBuilder rqb = new RQueryBuilder();
		StringWriter writer = new StringWriter();
		rqb.writeMatlabCode(writer, query);
		
		// copy the string to the clipboard
		StringSelection stringSelection = new StringSelection(writer.toString());
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	    clipboard.setContents(stringSelection , this);
		
	}
	
	
	
	public void changed(boolean changed)
	{
		if(changed == true)
		{
			try {
				// get all of the spectrum identifiers that match the current query
				query.setQueryType(Query.SELECT_QUERY);
				ids_matching_query = specchio_client.getSpectrumIdsMatchingQuery(query);
				
				// clear the old list of matching ids
				//ids_matching_query.clear();
				SQL_query.setText(null);
				
				// add the new list of ids
				for (Integer id : ids_matching_query) {
					//ids_matching_query.add(id);
					if (SQL_query.getText().length() > 0) {
						SQL_query.append(", ");
					}
					SQL_query.append(Integer.toString(id));
				}
				resulting_rows.setText(Integer.toString(ids_matching_query.size()));
				
				// enable the action buttons if there were any matches
				setButtonsEnabled(ids_matching_query.size() > 0);
			}
			catch (SPECCHIOClientException ex) {
				// something went wrong; display the error instead of the ids
				SQL_query.setText(ex.getUserMessage());
				resulting_rows.setText("Error!");
				setButtonsEnabled(false);
			}
		}
		
		
	}
	
	
	private void setButtonsEnabled(boolean enabled) {
		
		show_report.setEnabled(enabled);
		file_export.setEnabled(enabled);
		process.setEnabled(enabled);
		spectral_plot.setEnabled(enabled);
		refl.setEnabled(enabled);
		if (publish_collection != null) {
			publish_collection.setEnabled(
					enabled &&
					(mds_restrictions || show_only_my_data.isSelected()) &&
					!is_admin
				);
		}
		this.menu.setEnabled(enabled);
	}
	
	
	public void actionPerformed(ActionEvent e) 
	{
		
		// SDB combobox
		if("comboBoxChanged".equals(e.getActionCommand()))
		{
			
			String order_by = ((combo_table_data) this.sdb.order_by_box.getSelectedItem()).getValue();
			
			query.setOrderBy(order_by);
			
			return;
		}
		
		
	      if("show_report".equals(e.getActionCommand()))
	      {
	    	  startOperation();
	    	  ReportThread thread = new ReportThread(
    				  ids_matching_query,
    				  split_spaces_by_sensor.isSelected(),
    				  split_spaces_by_sensor_and_unit.isSelected(),
    				  sdb.get_order_by_field()
	    			  );
	    	  thread.start();
	    	  endOperation();
	      }
	      
	      if("file_export".equals(e.getActionCommand()))
	      {
	    	  startOperation();
	    	  try {
	    		  Space spaces[] = specchio_client.getSpaces(
	    				  ids_matching_query,
	    				  split_spaces_by_sensor.isSelected(),
	    				  split_spaces_by_sensor_and_unit.isSelected(),
	    				  sdb.get_order_by_field()
	    			);
	    		  ArrayList<SpaceProcessingChainComponent> spaces_li = new ArrayList<SpaceProcessingChainComponent>(spaces.length);
	    		  for (Space space : spaces) {
	    			  spaces_li.add(new SpaceProcessingChainComponent(this, space));
	    		  }
		    	  
		    	  FileOutputManager fom = new FileOutputManager(specchio_client, spaces_li);
		    	  fom.get_unit_from_spectrum = true; // otherwise it will be taken from the containing space, leading to wrong results if spaces are forced together
		    	  fom.start();  
	    	  }
	  		catch (SPECCHIOClientException ex) {
		  		ErrorDialog error = new ErrorDialog(
				    	this,
			    		"Error",
			    		ex.getUserMessage(),
			    		ex
				    );
			  		error.setVisible(true);
		    }
	    	  endOperation();
	      }
		
	      
	      if("process".equals(e.getActionCommand()))
	      {	
	    	  	try {
		        	Space[] spaces = specchio_client.getSpaces(
		        			ids_matching_query,
		        			this.split_spaces_by_sensor.isSelected(),
		        			this.split_spaces_by_sensor_and_unit.isSelected(),
		        			sdb.get_order_by_field()
		        		);
	    	  		
	    	  		
	    	  		DataProcessor d = new DataProcessor(specchio_client);
	    	  		d.set_input_spaces(spaces);
	    	  		d.setVisible(true);
	    	  	}
		  		catch (SPECCHIOClientException ex) {
			  		ErrorDialog error = new ErrorDialog(
					    	this,
				    		"Error",
				    		ex.getUserMessage(),
				    		ex
					    );
				  		error.setVisible(true);
			    }
	      }
	      
	      
	      if(VisualisationSelectionDialog.gonio_hem_expl.equals(e.getActionCommand()) 
	    		  || VisualisationSelectionDialog.sampling_points_plot.equals(e.getActionCommand())
	    		  || VisualisationSelectionDialog.spectral_multiplot.equals(e.getActionCommand())
	    	  || VisualisationSelectionDialog.spectral_scatter_multiplot.equals(e.getActionCommand())
	    	  || VisualisationSelectionDialog.time_line_plot.equals(e.getActionCommand())
	    	  || VisualisationSelectionDialog.time_line_expl.equals(e.getActionCommand()))
	      {	 
	    	  startOperation();
	    	  VisualisationThread thread = new VisualisationThread(
	    			  e.getActionCommand(),
    				  ids_matching_query,
    				  split_spaces_by_sensor.isSelected(),
    				  split_spaces_by_sensor_and_unit.isSelected(),
    				  sdb.get_order_by_field()
    			);
	    	  thread.start();
	    	  endOperation();	 			  
	      }	      
	      
	      
	      if("refl".equals(e.getActionCommand()))
	      {        	   
	    	  startOperation();
	    	  try {
		    	  DataProcessor d = new DataProcessor(specchio_client);
	
		    	  d.set_ids(ids_matching_query);
		    	  d.setVisible(true);
		    	  ProcessingPlane pp = d.getProcessingPlane();
		    	  ArrayList<SpaceProcessingChainComponent> spaces = pp.get_spaces();
		    	  SpaceProcessingChainComponent L = spaces.get(0);
	
		    	  // create processing module, add to processing plane and connect to input space in pp
		    	  RadianceToReflectance L2R = new RadianceToReflectance(d, specchio_client);
	
		    	  pp.add_module(L2R, 150, 150);
	
		    	  L2R.add_and_connect_input_space(L);
	
		    	  L2R.create_output_spaces_and_add_to_processing_plane(300,300);
	    	  }
		  	catch (SPECCHIOClientException ex) {
		  		ErrorDialog error = new ErrorDialog(
				    	this,
			    		"Error",
			    		ex.getUserMessage(),
			    		ex
				    );
			  		error.setVisible(true);
			   }
	    	  endOperation();
	    	  
	      }
	      
	      if ("publish_collection".equals(e.getActionCommand()))
	      {
	    	  startOperation();
	    	  try {
	    		  
	    		  if (publicationAllowed()) {
	    			  
	    			  PublishCollectionDialog d = new PublishCollectionDialog(this, ids_matching_query, true);
				   	  d.setVisible(true);
				   	  RDACollectionDescriptor collection_d = d.getCollectionDescriptor();
				   	  if (collection_d != null) {
				   		  String collectionId = specchio_client.submitRDACollection(collection_d);
				   		  if (collectionId != null && collectionId.length() > 0) {
					   		  JOptionPane.showMessageDialog(
					   				  this,
					   				  "Published " + ids_matching_query.size() + " spectra with collection identifier " + collectionId + ".",
					   				  "Publication successful",
					   				  JOptionPane.INFORMATION_MESSAGE
					   				);
				   		  } else {
				   			  JOptionPane.showMessageDialog(
				   					  this,
				   					  "The server could not publish the collection, but did not specify a reason.",
				   					  "Publication failed",
				   					  JOptionPane.ERROR_MESSAGE
				   					);
				   		  }
				   	  }
			    	  
	    		  } else {
	    			  
	    			  String message = "The search results contain data for which you are not a member of the research group.\n" +
	    					  "You can only publish data from campaigns for which you are a member of the research group.";
	    			  JOptionPane.showMessageDialog(
	    					  this,
	    					  message,
	    					  "Publication not allowed",
	    					  JOptionPane.ERROR_MESSAGE
	    					 );
	    			  
	    		  }
	    	  }
			  catch (SPECCHIOClientException ex) {
				  ErrorDialog error = new ErrorDialog(
						  this,
						  "Error",
						  ex.getUserMessage(),
						  ex
					);
				  error.setVisible(true);
			  }
	    	  endOperation();		  
    					  
	      }
	      
	      // TODO The Sandbox is here ...
	      if ("Test".equals(e.getActionCommand()))
	      {
	    	  try {
				// ArrayList<Integer> ids = specchio_client.getInstrumentIds(ids_matching_query);
	    		  Spectrum s = specchio_client.getSpectrum(ids_matching_query.get(0), false);
	    		  int current_hierarchy_id = s.getHierarchyLevelId();
	    		  int first_parent_id = specchio_client.getHierarchyParentId(current_hierarchy_id);
	    		  
	    		
				Campaign campaign = specchio_client.getCampaign(s.getCampaignId());
				
				specchio_client.getSubHierarchyId(campaign, "Reflectance", first_parent_id);
				
			} catch (SPECCHIOWebClientException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (SPECCHIOClientException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}	    	  
	    	  
	      }
	      
		
	}
	
	
	/**
	 * Handler for ending a potentially long-running operation.
	 */
	private void endOperation() {
		
		// change the cursor to its default start
		setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		
	}
	
	
	/**
	 * Test whether or not the current user is permitted to publish the current
	 * selection as an ANDS collection.
	 * 
	 * @return true or false
	 * 
	 * @throws SPECCHIOClientException	error contacting the server
	 */
	private boolean publicationAllowed() throws SPECCHIOClientException {
		
		// in browsing mode, publication is allowed if and only if the user is browsing his or her own data
		if (hierarchy_browser) {
			return show_only_my_data.isSelected();
		}
		
		// build a list of all of the campaigns to which the spectra belong
		Set<Integer> campaignIds = new HashSet<Integer>();
		for (Integer spectrumId : ids_matching_query) {
			Spectrum s = specchio_client.getSpectrum(spectrumId, false);
			campaignIds.add(s.getCampaignId());
		}
		
		// check that the user is a member of the research group of each campaign
		for (Integer campaignId : campaignIds) {
			Campaign c = specchio_client.getCampaign(campaignId);
			if (c == null || c.getResearchGroup() == null || c.getResearchGroup().getMembers() == null) {
				return false;
			}
			if (!c.getResearchGroup().getMembers().contains(specchio_client.getLoggedInUser())) {
				return false;
			}
		}
		
		return true;
		
	}
	
	
	/**
	 * Handler for starting a potentially long-running operation.
	 */
	private void startOperation() {
		
		// change the cursor to its "wait" state
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
	}

	public void valueChanged(TreeSelectionEvent arg0) 
	{

		try {
			ArrayList<Integer> spectrum_ids = sdb.get_selected_spectrum_ids();
		
			
			if(spectrum_ids != null && spectrum_ids.size() > 0)
			{
				query.remove_all_conditions();
					
				QueryConditionObject condition = new QueryConditionObject("spectrum", "spectrum_id");
				condition.setOperator("in");
				condition.setValue(spectrum_ids);
				query.add_condition(condition);
				query.add_join("spectrum", condition);
				setButtonsEnabled(true);
				changed(true);
			}
			else
			{
				ids_matching_query.clear();
				resulting_rows.setText("");
				this.SQL_query.setText("");
				setButtonsEnabled(false);
			}
		}
  		catch (SPECCHIOClientException ex) {
	  		ErrorDialog error = new ErrorDialog(
			    	SPECCHIOApplication.getInstance().get_frame(),
		    		"Error",
		    		ex.getUserMessage(),
		    		ex
			    );
		  		error.setVisible(true);
	    }
		
	}

	public void stateChanged(ChangeEvent e) {
		try {
			sdb.set_view_restriction(show_only_my_data.isSelected());
			
			// changing the view restriction may remove the selection, so update the button state
			setButtonsEnabled(sdb.get_selected_node() != null);
		}
  		catch (SPECCHIOClientException ex) {
	  		ErrorDialog error = new ErrorDialog(
			    	SPECCHIOApplication.getInstance().get_frame(),
		    		"Error",
		    		ex.getUserMessage(),
		    		ex
			    );
		  		error.setVisible(true);
	    }
		
	}

	public void lostOwnership(Clipboard arg0, Transferable arg1) {
		// ignore
		
	}



	public void changed(Object source) {
		
		QueryController qc = (QueryController) source;
		
		ArrayList<QueryCondition> conds = qc.getListOfConditions();
		
		ListIterator<QueryCondition> li = conds.listIterator();
		
		query.remove_all_conditions();
		
		while(li.hasNext())
		{
			QueryCondition cond = li.next();
			
			query.add_condition(cond);
		}

		
		changed(true);
		
	}
	
	
	/**
	 * Thread for builing reports.
	 */
	private class ReportThread extends Thread {
		
		/** spectrum identifiers on which to report */
		private ArrayList<Integer> ids;
		
		/** split spaces by sensor */
		private boolean bySensor;
		
		/** split spaces by sensor and unit */
		private boolean bySensorAndUnit;
		
		/** field to order by */
		private String orderBy;
		
		/**
		 * Constructor.
		 */
		public ReportThread(ArrayList<Integer> idsIn, boolean bySensorIn, boolean bySensorAndUnitIn, String orderByIn)
		{
			// save parameters for later
			ids = idsIn;
			bySensor = bySensorIn;
			bySensorAndUnit = bySensorAndUnitIn;
			orderBy = orderByIn;
		}
		
		/**
		 * Thread entry point.
		 */
		public void run()
		{
	  	    // create a progress report
			ProgressReportDialog pr = new ProgressReportDialog(QueryBuilder.this, "Spectrum Report", false, 20);
			pr.set_operation("Opening report");
			pr.set_progress(0);
			pr.set_indeterminate(true);
			pr.setVisible(true);
			
	    	try {
	    		
	    		pr.set_operation("Identifying spaces");
	    		Space spaces[] = specchio_client.getSpaces(
	    				ids,
	    				bySensor,
	    				bySensorAndUnit,
	    				orderBy
	    			);
	   
	    		ArrayList<Space> spaces_li = new ArrayList<Space>(spaces.length);
	    		for (Space space : spaces) {
	    			spaces_li.add(space);
	    		}
	  
	    		pr.set_indeterminate(false);
			    SpectrumReportDialog  d = new SpectrumReportDialog(specchio_client, spaces_li, pr);
			    pr.setVisible(false);
			    d.setVisible(true);
	    	}
	    	catch (SPECCHIOClientException ex) {
		  		ErrorDialog error = new ErrorDialog(
		  				QueryBuilder.this,
			    		"Error",
			    		ex.getUserMessage(),
			    		ex
				    );
			  	error.setVisible(true);
		    }
	    	
	    	pr.setVisible(false);
		}
		
	}
	
	
	/**
	 * Thread for building visualisations.
	 */
	private class VisualisationThread extends Thread {
		
		/** the plot type */
		private String plotType;
		
		/** spectrum identifiers on which to report */
		private ArrayList<Integer> ids;
		
		/** split spaces by sensor */
		private boolean bySensor;
		
		/** split spaces by sensor and unit */
		private boolean bySensorAndUnit;
		
		/** field to order by */
		private String orderBy;
		
		
		/**
		 * Constructor.
		 * 
		 * @param plotTypeIn		the plot type
		 * @param idsIn				the spectrum identifiers to be visualised
		 * @param bySensor			split spaces by sensor
		 * @param bySensorAndUnit	split spaces by sensor and unit
		 * @param orderByIn			field to order by
		 */
		public VisualisationThread(String plotTypeIn, ArrayList<Integer> idsIn, boolean bySensorIn, boolean bySensorAndUnitIn, String orderByIn)
		{
			// save parameters for later
			plotType = plotTypeIn;
			ids = idsIn;
			bySensor = bySensorIn;
			bySensorAndUnit = bySensorAndUnitIn;
			orderBy = orderByIn;
		}
		
		
		/**
		 * Thread entry point.
		 */
		public void run()
		{
	  	    // create a progress report
			ProgressReportDialog pr = new ProgressReportDialog(QueryBuilder.this, plotType, false, 20);
			pr.set_operation("Opening " + plotType);
			pr.set_progress(0);
			pr.setVisible(true);
			
		  	try {
		      	VisualisationModule VM;
		      	
		      	pr.set_operation("Identifying spaces");
		      	Space[] spaces = specchio_client.getSpaces(
		      			ids,
		      			bySensor,
		      			bySensorAndUnit,
		      			orderBy
		      		);
		      	pr.set_progress(100);
					
		      	Integer i = 0;
				for (Space space : spaces)
				{
					pr.set_operation("Loading space " + i);
					pr.set_progress(0);
					Space s = specchio_client.loadSpace(space);
					pr.set_progress(50);
					
					pr.set_operation("Building plot");
					VM = new VisualisationModule(QueryBuilder.this, specchio_client);
					SpaceProcessingChainComponent c = new SpaceProcessingChainComponent(QueryBuilder.this, s);
					c.setNumber(i);
					VM.add_input_space(c, -1);
					VM.set_vis_module_type(plotType);
					VM.transform();
					pr.set_progress(100);
					
					i++;
				}
		  	}
			catch (SPECCHIOClientException ex) {
		  		ErrorDialog error = new ErrorDialog(
				    	QueryBuilder.this,
			    		"Server error",
			    		ex.getUserMessage(),
			    		ex
				    );
			  		error.setVisible(true);
		    }
		  	catch (ModuleException ex) {
		  		ErrorDialog error = new ErrorDialog(
		  				QueryBuilder.this,
		  				"Module error",
		  				ex.getMessage(),
		  				ex
		  			);
		  		error.setVisible(true);
		  	}
		  	
		  	pr.setVisible(false);
			
		}
		
	}
	

}
