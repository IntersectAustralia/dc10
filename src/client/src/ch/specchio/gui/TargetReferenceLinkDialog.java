package ch.specchio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import ch.specchio.client.SPECCHIOClient;
import ch.specchio.client.SPECCHIOClientException;
import ch.specchio.constants.UserRoles;
import ch.specchio.queries.EAVQueryConditionObject;
import ch.specchio.queries.Query;
import ch.specchio.queries.QueryConditionObject;
import ch.specchio.types.MetaParameter;
import ch.specchio.types.Metadata;
import ch.specchio.types.Spectrum;
import ch.specchio.types.SpectrumDataLink;

/**
 * Target-reference lin dialogue.
 */
public class TargetReferenceLinkDialog extends JDialog implements ActionListener, TreeSelectionListener {
	
	/** serialisation version identifier */
	private static final long serialVersionUID = 1L;
	
	/** client object for contacting the server */
	private SPECCHIOClient specchioClient;
	
	/** target browser panel */
	private TargetReferenceBrowserPanel targetPanel;
	
	/** reference browser panel */
	private TargetReferenceBrowserPanel referencePanel;
	
	/** panel for listing existing links */
	private TargetReferenceLinkListPanel existingLinkPanel;
	
	/** panel for creating new links */
	private TargetReferenceLinkCreationPanel newLinkPanel;
	
	/** "dismiss" button */
	private JButton dismissButton;
	
	/** text for the "target" panel */
	private static final String TARGETS = "Targets";
	
	/** text for the "reference" panel */
	private static final String REFERENCES = "References";
	
	/** text for the "dismiss" button */
	private static final String DISMISS  = "Close";
	
	
	/**
	 * Constructor.
	 * 
	 * @param owner	the owner of this dialogue
	 * @param modal	is the dialogue modal?
	 */
	public TargetReferenceLinkDialog(Frame owner, boolean modal) throws SPECCHIOClientException {
		
		super(owner, "Target-Reference Links", modal);

		// get a reference to the application's client object
		specchioClient = SPECCHIOApplication.getInstance().getClient();
		
		// set up the root pane with a border layout
		JPanel rootPanel = new JPanel();
		rootPanel.setLayout(new BorderLayout());
		getContentPane().add(rootPanel);
		
		// add the target selection panel
		targetPanel = new TargetReferenceBrowserPanel(TARGETS);
		targetPanel.addSelectionListener(this);
		rootPanel.add(targetPanel, BorderLayout.WEST);
		
		// add the reference selection panel
		referencePanel = new TargetReferenceBrowserPanel(REFERENCES);
		referencePanel.addSelectionListener(this);
		rootPanel.add(referencePanel, BorderLayout.EAST);
		
		// add a panel for the tabs
		JTabbedPane tabs = new JTabbedPane();
		rootPanel.add(tabs, BorderLayout.CENTER);
		
		// add a tab for the existing links
		existingLinkPanel = new TargetReferenceLinkListPanel();
		tabs.addTab("Show existing links", new JScrollPane(existingLinkPanel));
		
		// add a tab for creatign new links
		newLinkPanel = new TargetReferenceLinkCreationPanel();
		tabs.addTab("Create new links", new JScrollPane(newLinkPanel));
		
		// create a panel for the buttons
		JPanel buttonPanel = new JPanel();
		rootPanel.add(buttonPanel, BorderLayout.SOUTH);
		
		// add the "dismiss" button
		dismissButton = new JButton(DISMISS);
		dismissButton.setActionCommand(DISMISS);
		dismissButton.addActionListener(this);
		buttonPanel.add(dismissButton);
		
		// lay it out
		pack();
		
	}
	
	
	/**
	 * Button handler.
	 * 
	 * @param event	the event to be handled
	 */
	public void actionPerformed(ActionEvent event) {
		
		if (DISMISS.equals(event.getActionCommand())) {
			
			// close the dialogue
			setVisible(false);
			
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
	 * Handler for starting a potentially long-running operation.
	 */
	private void startOperation() {
		
		// change the cursor to its "wait" state
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
	}
	
	
	/**
	 * Handler for selection changes in the browser panel.
	 * 
	 * @param event	the event to be handled
	 */
	public void valueChanged(TreeSelectionEvent event) {
		
		if (event.getSource() instanceof TargetReferenceBrowserPanel) {
			
			startOperation();
			try {
				TargetReferenceBrowserPanel source = (TargetReferenceBrowserPanel)event.getSource();
				
				if (source == targetPanel) {
					
					// update target selection
					ArrayList<Integer> ids = targetPanel.getSelectedIds();
					existingLinkPanel.setTargetIds(ids);
					newLinkPanel.setTargetIds(ids);
					
				} else if (source == referencePanel) {
					
					// update reference selection
					ArrayList<Integer> ids = referencePanel.getSelectedIds();
					existingLinkPanel.setReferenceIds(ids);
					newLinkPanel.setReferenceIds(ids);
					
				}
				
			}
			catch (SPECCHIOClientException ex) {
				// error contacting the server
				ErrorDialog error = new ErrorDialog(TargetReferenceLinkDialog.this, "Error", ex.getUserMessage(), ex);
				error.setVisible(true);
			}
			endOperation();
		}
		
	}
	
	
	/**
	 * Panel for selecting a set of spectra.
	 */
	private class TargetReferenceBrowserPanel extends JPanel implements DocumentListener, TreeSelectionListener {
		
		/** serialisation version identifier */
		private static final long serialVersionUID = 1L;

		/** spectral data browser */
		private SpectralDataBrowser sdb;
		
		/** filename restriction label */
		private JLabel filenamePatternLabel;
		
		/** filename restriction field */
		private JTextField filenamePatternField;
		
		/** listeners */
		private List<TreeSelectionListener> listeners;
		
		
		/**
		 * Constructor.
		 * 
		 * @param title	the title for this panel
		 */
		public TargetReferenceBrowserPanel(String title) throws SPECCHIOClientException {
			
			super();
			
			// initialise member variables
			listeners = new ArrayList<TreeSelectionListener>();
			
			// set up a vertical box layout
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			
			// add a titled border
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), title));
			
			// add a spectral data browser
			sdb = new SpectralDataBrowser(specchioClient, !specchioClient.isLoggedInWithRole(UserRoles.ADMIN));
			sdb.build_tree();
			sdb.tree.addTreeSelectionListener(this);
			add(sdb);
			
			// add the filename pattern control
			JPanel filenamePanel = new JPanel();
			add(filenamePanel);
			filenamePatternLabel = new JLabel("Filename restriction:");
			filenamePanel.add(filenamePatternLabel);
			filenamePatternField = new JTextField(20);
			filenamePatternField.getDocument().addDocumentListener(this);
			filenamePanel.add(filenamePatternField);
			
		}
		
		
		/**
		 * Register for notification when a new selection is made.
		 * 
		 * @param listener	the object to be registered
		 */
		public void addSelectionListener(TreeSelectionListener listener) {
			
			listeners.add(listener);
			
		}


		/**
		 * Handle a change to a document
		 * 
		 * @param event	the event to be handled
		 */
		@Override
		public void changedUpdate(DocumentEvent event) {
			
			documentChanged(event);
			
		}


		/**
		 * Handle a change in the contents of a text field
		 * 
		 * @param event	the event to be handled
		 */
		private void documentChanged(DocumentEvent event) {
			
			// manufacture a tree selection event from the current selection in the tree
			TreePath paths[] = sdb.tree.getSelectionPaths();
			boolean areNew[] = new boolean[(paths != null)? paths.length : 0];
			for (int i = 0; i < areNew.length; i++) {
				areNew[i] = false;
			}
			fireValueChanged(
				new TreeSelectionEvent(
					this,
					paths,
					areNew,
					sdb.tree.getSelectionPath(),
					sdb.tree.getSelectionPath()
				)
			);
			
		}
		
		
		/**
		 * Notify all listeners of a selection change.
		 * 
		 * @param event	the event of which the listeners are to be notified
		 */
		private void fireValueChanged(TreeSelectionEvent event) {
			
			for (TreeSelectionListener listener : listeners) {
				listener.valueChanged(event);
			}
			
		}
		
		
		/**
		 * Get the selected identifiers
		 * 
		 * @return a new list of the spectrum identifiers selected by the panel
		 * 
		 * @throws SPECCHIOClientException	error contacting the server
		 */
		public ArrayList<Integer> getSelectedIds() throws SPECCHIOClientException {

			// get the spectrum identifiers selected in the browser
			ArrayList<Integer> spectrumIds = sdb.get_selected_spectrum_ids();
			
			String filenamePattern = filenamePatternField.getText();
			if (filenamePattern.length() > 0) {
				
				// create a query object for the spectrum table
				Query query = new Query("spectrum");
				query.setQueryType(Query.SELECT_QUERY);
				query.addColumn("spectrum_id");
				
				if (spectrumIds.size() > 0) {
					// restrict to the spectrum identifiers selected in the browser
					QueryConditionObject spectrumIdsCond = new QueryConditionObject("spectrum", "spectrum_id");
					spectrumIdsCond.setOperator("in");
					spectrumIdsCond.setValue(spectrumIds);
					query.add_condition(spectrumIdsCond);
				}
				
				// restrict to the spectrum filenames that match the input pattern
				EAVQueryConditionObject filenameCond = new EAVQueryConditionObject("spectrum", null, "File Name", "string_val");
				filenameCond.setOperator("like");
				filenameCond.setValue(filenamePattern);
				query.add_condition(filenameCond);
				
				// return the spectrum identifiers that match the query
				return specchioClient.getSpectrumIdsMatchingQuery(query);
			
			} else {
				
				// no filename restriction; just return the list we got from the browser
				return spectrumIds;
				
			}
			
		}


		/**
		 * Handle an insert to a document.
		 * 
		 * @param event	the event to be handled
		 */
		@Override
		public void insertUpdate(DocumentEvent event) {
			
			documentChanged(event);
			
		}


		/**
		 * Handle a deletion from a document.
		 * 
		 * @param event	the event to be handled
		 */
		@Override
		public void removeUpdate(DocumentEvent event) {
			
			documentChanged(event);
			
		}
		
		
		/**
		 * Handle a change of selection in the browser.
		 *
		 * @param event the event to be handled
		 */
		@Override
		public void valueChanged(TreeSelectionEvent event) {
			
			fireValueChanged((TreeSelectionEvent)event.cloneWithSource(this));
			
		}
		
	}
	
	
	/**
	 * Panel for listing existing links.
	 */
	private class TargetReferenceLinkListPanel extends JPanel {
		
		/** serialisation version identifier */
		private static final long serialVersionUID = 1L;
		
		/** the list of linked references */
		private TargetReferenceList referenceList;
		
		/** the list of linked targets */
		private TargetReferenceList targetList;
		
		/** title for the matching references list */
		private static final String REFERENCES = "Linked references";
		
		/** title for the matchign targets list */
		private static final String TARGETS = "Linked targets";
		
		
		/**
		 * Constructor.
		 */
		public TargetReferenceLinkListPanel() {
			
			super();
			
			// set up border layout
			setLayout(new BorderLayout());
			
			// add the list of linked references
			referenceList = new TargetReferenceList(REFERENCES);
			add(referenceList, BorderLayout.WEST);
			
			// add the list of linked targets
			targetList = new TargetReferenceList(TARGETS);
			add(targetList, BorderLayout.EAST);
			
		}
		
		
		/**
		 * Set the reference identifiers displayed by the panel.
		 * 
		 * @param ids	the identifiers
		 * 
		 * @throws SPECCHIOClientException	error contacting server
		 */
		public void setReferenceIds(List<Integer> ids) throws SPECCHIOClientException {
			
			// refresh the list of linked targets
			targetList.clear();
			for (Integer id : ids) {
				SpectrumDataLink datalinks[] = specchioClient.getTargetReferenceLinks(0, id);
				for (SpectrumDataLink datalink : datalinks) {
					targetList.addId(datalink.getReferencingId());
				}
			}
			
		}
		
		
		/**
		 * Set the target identifiers displayed by the panel.
		 * 
		 * @param ids	the identifiers
		 * 
		 * @throws SPECCHIOClientException	error contacting server
		 */
		public void setTargetIds(List<Integer> ids) throws SPECCHIOClientException {
			
			// refresh the list of linked references
			referenceList.clear();
			for (Integer id : ids) {
				SpectrumDataLink datalinks[] = specchioClient.getTargetReferenceLinks(id, 0);
				for (SpectrumDataLink datalink : datalinks) {
					referenceList.addId(datalink.getReferencedId());
				}
			}
			
		}
		
	}
	
	
	/**
	 * Panel for creating new links
	 */
	private class TargetReferenceLinkCreationPanel extends JPanel implements ActionListener {
		
		/** serialisation version identifier */
		private static final long serialVersionUID = 1L;
		
		/** list of target filenames */
		private TargetReferenceList targetList;
		
		/** list of reference filenames */
		private TargetReferenceList referenceList;
		
		/** "link" button */
		private JButton linkButton;
		
		/** title for the target list */
		private static final String TARGETS = "Target spectra";
		
		/** title for the reference list */
		private static final String REFERENCES = "Reference spectra";
		
		/** text for the "link" button */
		private static final String LINK = "Link";
		
		/**
		 * Constructor.
		 */
		public TargetReferenceLinkCreationPanel() {
			
			super();
			
			// set up a border layout
			setLayout(new BorderLayout());
			
			// add the list of target file names
			targetList = new TargetReferenceList(TARGETS);
			add(targetList, BorderLayout.WEST);
			
			// add the list of reference file names
			referenceList = new TargetReferenceList(REFERENCES);
			add(referenceList, BorderLayout.EAST);
			
			// add a panel for the buttons
			JPanel buttonPanel = new JPanel();
			add(buttonPanel, BorderLayout.SOUTH);
			
			// add the "link" button but don't enable it yet
			linkButton = new JButton(LINK);
			linkButton.setActionCommand(LINK);
			linkButton.addActionListener(this);
			linkButton.setEnabled(false);
			buttonPanel.add(linkButton);
			
		}
		
		
		/**
		 * Button handler.
		 * 
		 * @param event	the event to be handled
		 */
		public void actionPerformed(ActionEvent event) {
			
			if (LINK.equals(event.getActionCommand())) {
				
				// launch a thread to do the actual work
				TargetReferenceLinkCreationThread thread = new TargetReferenceLinkCreationThread(targetList.getIds(), referenceList.getIds());
				thread.run();
				
			}
			
		}
		
		
		/**
		 * Set the reference identifiers.
		 * 
		 * @param ids	the identifiers
		 * 
		 * @throws SPECCHIOClientException	error contacting server
		 */
		public void setReferenceIds(List<Integer> ids) throws SPECCHIOClientException {
			
			// update the list contents
			referenceList.setIds(ids);
			
			// enable the "link" button if there are spectra in both lists
			linkButton.setEnabled(targetList.getLength() > 0 && referenceList.getLength() > 0);
			
		}
		
		
		/**
		 * Set the target identifiers.
		 * 
		 * @param ids	the identifiers
		 * 
		 * @throws SPECCHIOClientException	error contacting server
		 */
		public void setTargetIds(List<Integer> ids) throws SPECCHIOClientException {
			
			// update the list contents
			targetList.setIds(ids);
			
			// enable the "link" button if there are spectra in both lists
			linkButton.setEnabled(targetList.getLength() > 0 && referenceList.getLength() > 0);
			
		}
			
		
	}
	
	
	/**
	 * A list of spectra.
	 */
	private class TargetReferenceList extends JPanel {
		
		/** serialisation version identifier */
		private static final long serialVersionUID = 1L;
		
		/** the list model */
		private DefaultListModel model;
		
		/** the list control */
		private JList list;
		
		/**
		 * Constructor.
		 * 
		 * @param title	the title for the list
		 */
		public TargetReferenceList(String title) {
			
			super();
			
			// add a titled border
			setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), title));
			
			// create the list model and control
			model = new DefaultListModel();
			list = new JList(model);
			
			// put the list inside a scroll pane
			JScrollPane scroller = new JScrollPane(list);
			add(scroller);
			
		}
		
		
		/**
		 * Add an identifier to the list.
		 * 
		 * @param id	the identifier
		 * 
		 * @throws SPECCHIOClientException	error contacting the server
		 */
		public void addId(int id) throws SPECCHIOClientException {
			
			// first check that we haven't got this identifier already
			for (int i = 0; i < model.getSize(); i++) {
				if (getEntryAt(i).getId() == id) {
					return;
				}
			}
			
			// okay, add the new item
			model.addElement(new TargetReferenceListEntry(id));
			
		}
		
		
		/**
		 * Clear the list.
		 */
		public void clear() {
			
			model.clear();
			
		}
		
		
		/**
		 * Get the entry at a given location in the list.
		 * 
		 * @param index	the desired position
		 * 
		 * @return a reference to the entry at the given index
		 */
		private TargetReferenceListEntry getEntryAt(int index) {
			
			return (TargetReferenceListEntry)model.getElementAt(index);
			
		}
		
		
		/**
		 * Get the number of identifiers in the list.
		 * 
		 * @return the number of spectrum identifiers currently in the list
		 */
		public int getLength() {
			
			return model.getSize();
			
		}
		
		
		/**
		 * Get the list of spectrum identifiers that appear in this list
		 * 
		 * @return a new list of identifiers
		 */
		public ArrayList<Integer> getIds() {
			
			ArrayList<Integer> ids = new ArrayList<Integer>(model.getSize());
			for (int i = 0; i < model.getSize(); i++) {
				ids.add(getEntryAt(i).getId());
			}
			
			return ids;
			
		}
		
		
		/**
		 * Set the identifiers of the spectra to be displayed in the list
		 * 
		 * @param ids	the identifiers
		 * 
		 * @throws SPECCHIOClientException	error contacting server
		 */
		public void setIds(List<Integer> ids) throws SPECCHIOClientException {
			
			// clear the list
			model.clear();
			
			// fill the list with information from the server
			for (Integer id : ids) {
				model.addElement(new TargetReferenceListEntry(id));
			}
			
		}
		
		
		/**
		 * Structure for describing list items.
		 */
		private class TargetReferenceListEntry {
			
			/** spectrum object */
			private Spectrum spectrum;
			
			/** the string to appear in the list */
			private String string;
			
			/**
			 * Constructor.
			 * 
			 * @param id	the identifier of the spectrum in this entry
			 * 
			 * @throws SPECCHIOClientException	error contacting the server
			 */
			public TargetReferenceListEntry(Integer id) throws SPECCHIOClientException {
				
				// download the spectrum from the server
				spectrum = specchioClient.getSpectrum(id, true);
				Metadata md = spectrum.getEavMetadata();
				
				// build a string representing this spectrum
				StringBuffer sbuf = new StringBuffer();
				MetaParameter mp = md.get_entry("File Name");
				if (mp != null) {
					sbuf.append(mp.getValue().toString());
				}
				string = sbuf.toString();
				
			}
			
			
			/**
			 * Get the spectrum identifier associated with this entry.
			 * 
			 * @param the identifier
			 */
			public Integer getId() {
				
				return spectrum.getSpectrumId();
				
			}
			
			
			/**
			 * Get a string representation of the entry.
			 * 
			 * @return a string
			 */
			public String toString() {
				
				return string;
				
			}
			
		}
			
		
	}
	
	
	/**
	 * Thread for creating new target-reference links.
	 */
	private class TargetReferenceLinkCreationThread extends Thread {
		
		/** target spectrum identifiers */
		private ArrayList<Integer> targetIds;
		
		/** reference spectrum identifiers */
		private ArrayList<Integer> referenceIds;
		
		
		/**
		 * Constructor.
		 * 
		 * @param targetIdsIn		the target spectrum identifiers
		 * @param referenceIdsIn	the reference spectrum identifiers
		 */
		public TargetReferenceLinkCreationThread(ArrayList<Integer> targetIdsIn, ArrayList<Integer> referenceIdsIn) {
			
			targetIds = targetIdsIn;
			referenceIds = referenceIdsIn;
			
		}
		
		
		/**
		 * Thread entry point.
		 */
		public void run() {
			
			// move through all spectra in the selected target hierarchy
			// and link them to the corresponding reference spectrum in the 
			// reference hierarchy
			try {
							
				ProgressReport pr = new ProgressReport("Linking data ...", false);
				pr.set_operation("Creating TGT-REF links. ");
				pr.setVisible(true);
				
				int cnt = 0;
				int num = 0;
				
				for (Integer targetId : targetIds)
				{
					// create a link from the target to all of the references
					num += specchioClient.insertTargetReferenceLinks(targetId, referenceIds);
					
					// update progress
					cnt++;
					pr.set_progress(cnt * 100.0 / targetIds.size());
					
					
				}
				
				pr.setVisible(false);
				
				JOptionPane.showMessageDialog(
						null,
						"Datalinks for " + Integer.toString(num) + " out of " + targetIds.size() + " spectra successfully created."
					);
			
			} catch (SPECCHIOClientException ex) {
				ErrorDialog error = new ErrorDialog(
						(Frame)TargetReferenceLinkDialog.this.getOwner(),
						"Could not insert links",
						ex.getMessage(),
						ex
					);
				error.setVisible(true);
			}
			
		}
		
	}
			
	
}