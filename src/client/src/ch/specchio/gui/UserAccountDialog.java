package ch.specchio.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import au.ands.org.researchdata.ResearchDataAustralia;

import ch.specchio.client.SPECCHIOClient;
import ch.specchio.client.SPECCHIOClientException;
import ch.specchio.client.SPECCHIOClientFactory;
import ch.specchio.client.SPECCHIOServerDescriptor;
import ch.specchio.constants.UserRoles;
import ch.specchio.gui.MainMenu.OpenUrlAction;
import ch.specchio.types.Institute;
import ch.specchio.types.User;

public class UserAccountDialog extends JDialog implements ActionListener {
	
	/** serialisation version ID */
	private static final long serialVersionUID = 1L;

	/** the client object */
	private SPECCHIOClient specchio_client;
	
	/** server descriptor panel */
	private ServerDescriptorPanel serverPanel;
	
	/** main panel of this dialog */
	private JPanel rootPanel;
	
	/** button for connecting to the server */
	private JButton connectButton;
	
	/** user account panel */
	private UserAccountPanel userAccountPanel;
	
	/** button for submitting user details */
	private JButton submitButton;
	
	/** button for cancelling the dialogue */
	private JButton cancelButton;

	private JCheckBox licenseCheckBox;
	
	/** button text for connecting to the server */
	private static final String CONNECT = "Connect";
	
	/** button text for adding a new institute */
	private static final String ADD_INSTITUTE = "Add new institute...";
	
	/** button text for creating a new user */
	private static final String CREATE = "Create";
	
	/** button text for updating a user's details */
	private static final String UPDATE = "Update";
	
	/** button text for cancelling the dialogue */
	private static final String CANCEL = "Cancel";
	
	/** titles */
	private static final String titles[] = new String [] {
		"Dr.",
		"Ing.",
		"Ph.D.",
		"M.Sc.",
		"B.Sc.",
		"Mr.",
		"Mrs.",
		"Ms."
	};
	
	URI uri;

	private boolean license_is_shown = false;
	
	
	/**
	 * Constructor.
	 * 
	 * @param owner				the owner of this dialogue
	 * @param specchio_client	the client object to use (null to use our own client)
	 * @param user				the user object to be edited (null to create a new user)
	 * 
	 * @throws SPECCHIOClientException could not download data from the server
	 */
	public UserAccountDialog(Frame owner, SPECCHIOClient specchio_client, User user) throws SPECCHIOClientException {
		
		super(owner, (user == null)? "Create user account" : "Edit user account", false);
		
		// save the client object for later
		this.specchio_client = specchio_client;
		
		// set up the panel with a box layout
		rootPanel = new JPanel();
		rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));
		getContentPane().add(rootPanel);
		
		if (specchio_client == null) {
			// add a panel for connecting to the server
			JPanel serverConnectionPanel = new JPanel();
			serverConnectionPanel.setBorder(
					BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Server Details")
				);
			rootPanel.add(serverConnectionPanel);
			
			// add a server descriptor panel
			serverPanel = new WebAppDescriptorPanel(null, true);
			serverConnectionPanel.add(serverPanel);
			
			// add a button to connect to the server
			connectButton = new JButton(CONNECT);
			connectButton.setActionCommand(CONNECT);
			connectButton.addActionListener(this);
			serverConnectionPanel.add(connectButton);
		} else {
			serverPanel = null;
		}
		
		// add user account panel
		userAccountPanel = new UserAccountPanel(this, user);
		userAccountPanel.setBorder(
				BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "User Account Details")
			);
		rootPanel.add(userAccountPanel);
		
		
		// add a panel for the buttons
		JPanel buttonPanel = new JPanel();
		rootPanel.add(buttonPanel);
		
		// add the "submit" button
		if (user != null) {
			// the button will update an existing user
			submitButton = new JButton(UPDATE);
			submitButton.setActionCommand(UPDATE);
		} else {
			// the button will create a new user
			submitButton = new JButton(CREATE);
			submitButton.setActionCommand(CREATE);
		}
		submitButton.addActionListener(this);
		buttonPanel.add(submitButton);
		
		// add the "cancel" button
		cancelButton = new JButton(CANCEL);
		cancelButton.setActionCommand(CANCEL);
		cancelButton.addActionListener(this);
		buttonPanel.add(cancelButton);
		
		// initialise panel contents
		if (specchio_client != null) {
			
			// set the institutes list from the server
			userAccountPanel.setInstitutes(specchio_client.getInstitutes());
			if (user != null) {
				userAccountPanel.setInstitute(user.getInstitute());
			}
			
			// set enablement of ANDS features
			userAccountPanel.enableAndsFeatures(specchio_client.getCapability(ResearchDataAustralia.ANDS_SERVER_CAPABILITY) != null);
			
		} else {
			
			// no server connection; disable the user input fields
			userAccountPanel.setEnabled(false);
			submitButton.setEnabled(false);
			
		}
		
		// lay it all out
		pack();
		setResizable(false);
		
	}
	
	
	/**
	 * Button handler.
	 */
	public void actionPerformed(ActionEvent event) {
		
		if (CONNECT.equals(event.getActionCommand())) {
			
			startOperation();
			try {
				// get the server descriptor from the server panel
				SPECCHIOServerDescriptor d = serverPanel.getServerDescriptor();
				
				// create a client connected to this server
				SPECCHIOClientFactory cf = SPECCHIOClientFactory.getInstance();
				specchio_client = cf.createClient(d);
				specchio_client.connect();
				
				// fill the institute selection box
				userAccountPanel.setInstitutes(specchio_client.getInstitutes());
				
				// enable the user input fields
				userAccountPanel.setEnabled(true);
				userAccountPanel.enableAndsFeatures(specchio_client.getCapability(ResearchDataAustralia.ANDS_SERVER_CAPABILITY) != null);
				
				
				// add licensing box if this server holds license information
				String has_license = specchio_client.getCapability("END_USER_LICENSE");
				if(has_license != null && !license_is_shown )
				{
					license_is_shown = true; // prevents that the panel is added again when 'connect' is pressed several times
					String short_license = specchio_client.getCapability("END_USER_LICENSE_SHORT_TEXT");
					
					String license_url = specchio_client.getCapability("END_USER_LICENSE_URL");
					
					JPanel licensePanel = new JPanel();
					rootPanel.add(licensePanel);	
					
					licensePanel.setLayout(new GridBagLayout());
					GridBagConstraints constraints = new GridBagConstraints();
					constraints.gridx = 0;
					constraints.gridy = 0;
					constraints.insets = new Insets(4, 4, 4, 4);
					constraints.anchor = GridBagConstraints.WEST;
					
					// add checkbox and text panel for short license text
					licenseCheckBox = new JCheckBox("");
					licenseCheckBox.setActionCommand("LICENSE_ACCEPTED");
					licenseCheckBox.addActionListener(this);					
					licensePanel.add(licenseCheckBox, constraints);
					
					
					constraints.gridx++;				
					
					JTextArea textArea = new JTextArea(8, 40);
					textArea.setText(short_license);
					textArea.setLineWrap(true);
					textArea.setWrapStyleWord(true);
					
					Font font = textArea.getFont();
					float size = font.getSize() / 1.2f;
					textArea.setFont( font.deriveFont(size) );					
					
					licensePanel.add(textArea, constraints);
					
					constraints.gridwidth = 2;
					constraints.gridy++;
					constraints.gridx = 0;
					
			        JButton button = new JButton();
			        
					uri = new URI(license_url);

			        
			         button.setText("<html>" + 
			        		 " Data License: " +
			        		 "<FONT color=\"#000099\"><U>" + license_url + "</U></FONT>" +
			        		 " </HTML>");
			         button.setHorizontalAlignment(SwingConstants.LEFT);
			         button.setBorderPainted(false);
			         button.setOpaque(false);
			         button.setBackground(Color.WHITE);
			         button.setToolTipText(uri.toString());
			         button.addActionListener(new OpenUrlAction());
			         
			         licensePanel.add(button, constraints);					
				}
				else if (license_is_shown)
				{
					submitButton.setEnabled(false);
				}
				else
				{
					submitButton.setEnabled(true);
				}
				
				// need to re-layout the dialogue since the combo box and panel sizes might have changed
				pack();
			}
			catch (SPECCHIOClientException ex) {
				ErrorDialog error = new ErrorDialog(
						(Frame)getOwner(),
						"Could not connect",
						ex.getUserMessage(),
						ex
					);
				error.setVisible(true);
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			endOperation();
			
		} else if (ADD_INSTITUTE.equals(event.getActionCommand())) {
			
			try {
				// open the institute dialogue
				InstituteDialog id = new InstituteDialog((Frame)getOwner(), specchio_client);
				id.setVisible(true);
				
				Institute inst = id.getInstitute();
				if (inst != null) {
					// add the new institute to the combo box and select it
					userAccountPanel.addInstitute(inst);
					userAccountPanel.setInstitute(inst);
					
					// need to re-layout the dialogue since the combo box size might have changed
					pack();
				}
			}
			catch (SPECCHIOClientException ex) {
				// could not contact the server
				ErrorDialog error = new ErrorDialog(
						(Frame)getOwner(),
						"Could not create an institute",
						ex.getUserMessage(),
						ex
					);
				error.setVisible(true);
			}
			
		} else if (CREATE.equals(event.getActionCommand())) {
			
			startOperation();
			SPECCHIOServerDescriptor d = null;
			User user = null;
			try {
				// build a user object from the input fields
				user = userAccountPanel.getUser();
				
				// ask the server to create the user
				user = specchio_client.createUserAccount(user);
				
				// add a line to the configuration file
				SPECCHIOClientFactory cf = SPECCHIOClientFactory.getInstance();
				d = specchio_client.getServerDescriptor();
				d.setUser(user);
				cf.addAccountConfiguration(d);
				
				// report success
				String message = "User " + user.getUsername() + " created with password " + user.getPassword() + ". " +
						"An entry for this user has been added to your configuration file.";
				JOptionPane.showMessageDialog(
						(Frame)getOwner(),
						message,
						"User created",
						JOptionPane.INFORMATION_MESSAGE
					);
				
				// dismiss the dialogue
				setVisible(false);
			}
			catch (SPECCHIOUserInterfaceException ex) {
				// one of the required fields is missing or invalid
				JOptionPane.showMessageDialog(
						(Frame)getOwner(),
						ex.getMessage(),
						"Invalid configuration",
						JOptionPane.ERROR_MESSAGE
					);
			}
			catch (SPECCHIOClientException ex) {
				// the server failed to create the user
				ErrorDialog error = new ErrorDialog(
						(Frame)getOwner(),
						"User creation failed",
						ex.getUserMessage(),
						ex
					);
				error.setVisible(true);
			}
			catch (IOException ex) {
				// error writing to the configuration file
				StringBuffer message = new StringBuffer();
				message.append("A user account has been created, but I could not update the configuration file. ");
				message.append("Username: " + user.getUsername() + ". Password: " + user.getPassword() + ".");
				if (d != null) {
					message.append("\n");
					message.append("Please add the following line to the file db_config.txt:");
					message.append("\n");
					message.append(d.getAccountConfigurationString());
				}
				ErrorDialog error = new ErrorDialog(
						(Frame)getOwner(),
						"Configuration file not updated",
						message.toString(),
						ex
					);
				error.setVisible(true);
			}
			endOperation();
			
		} else if (UPDATE.equals(event.getActionCommand())) {
			
			startOperation();
			try {
				// build an updated user object from the old the user input fields
				User oldUser = specchio_client.getLoggedInUser();
				User newUser = userAccountPanel.getUser();
				newUser.setUserId(oldUser.getUserId());
				newUser.setUsername(oldUser.getUsername());
				newUser.setPassword(oldUser.getPassword());
				
				// ask the server to update the user
				specchio_client.updateUser(newUser);
				
				// dismiss the dialogue
				setVisible(false);
			}
			catch (SPECCHIOUserInterfaceException ex) {
				// one of the required fields is missing or invalid
				JOptionPane.showMessageDialog(
						(Frame)getOwner(),
						ex.getMessage(),
						"Invalid configuration",
						JOptionPane.ERROR_MESSAGE
					);
			}
			catch (SPECCHIOClientException ex) {
				// the server failed to update the user
				ErrorDialog error = new ErrorDialog(
						(Frame)getOwner(),
						"User update failed",
						ex.getMessage(),
						ex
					);
				error.setVisible(true);
			}
			endOperation();
		
		} else if (CANCEL.equals(event.getActionCommand())) {
			
			setVisible(false);
			
		} else if ("LICENSE_ACCEPTED".equals(event.getActionCommand())) {
			
			if(licenseCheckBox.isSelected())
			{
				submitButton.setEnabled(true);
			}
			else
			{
				submitButton.setEnabled(false);
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
	 * Handler for starting a potentially long-running operation.
	 */
	private void startOperation() {
		
		// change the cursor to its "wait" state
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
	}
	
	
	
	/**
	 * The panel for user details.
	 */
	private class UserAccountPanel extends JPanel {
		
		/** serialisation version identifier */
		private static final long serialVersionUID = 1L;
		
		/** layout constraints descriptor */
		private GridBagConstraints constraints;

		/** label for the user title */
		private JLabel titleLabel;
		
		/** combo box for user title */
		private JComboBox titleField;
		
		/** label for the user first name */
		private JLabel firstNameLabel;
		
		/** text field for user first name */
		private JTextField firstNameField;
		
		/** label for the user last name */
		private JLabel lastNameLabel;
		
		/** text field for user last name */
		private JTextField lastNameField;
		
		/** label for the user institute */
		private JLabel instituteLabel;
		
		/** combo box for user institute */
		private JComboBox instituteField;
		
		/** button for adding a new institute */
		private JButton addInstituteButton;
		
		/** label for the user e-mail address */
		private JLabel emailLabel;
		
		/** text field for user e-mail address */
		private JTextField emailField;
		
		/** label for the the user WWW address */
		private JLabel wwwLabel;
		
		/** text field for user WWW address */
		private JTextField wwwField;
		
		/** ANDS information panel */
		private AndsInformationPanel andsInformationPanel;
		
		/** is the ANDS information panel displayed? */
		private boolean andsInformationDisplayed = true;
		
		
		/**
		 * Constructor.
		 * 
		 * @param owner	the dialogue of which this panel is a part
		 * @param user	the user account with which to initialise the dialogue (may be null)
		 */
		public UserAccountPanel(UserAccountDialog owner, User user) {
			
			// set up the panel with a grid bag layout
			setLayout(new GridBagLayout());
			constraints = new GridBagConstraints();
			constraints.gridx = 0;
			constraints.gridy = 0;
			constraints.insets = new Insets(4, 4, 4, 4);
			constraints.anchor = GridBagConstraints.WEST;
			
			// add title combo box
			constraints.gridx = 0;
			titleLabel = new JLabel("Title:");
			add(titleLabel, constraints);
			constraints.gridx = 1;
			titleField = new JComboBox(titles);
			add(titleField, constraints);
			constraints.gridy++;
			
			// add first name field
			constraints.gridx = 0;
			firstNameLabel = new JLabel("First name:");
			add(firstNameLabel, constraints);
			constraints.gridx = 1;
			firstNameField = new JTextField(30);
			add(firstNameField, constraints);
			constraints.gridy++;
			
			// add last name field
			constraints.gridx = 0;
			lastNameLabel = new JLabel("Last name:");
			add(lastNameLabel, constraints);
			constraints.gridx = 1;
			lastNameField = new JTextField(30);
			add(lastNameField, constraints);
			constraints.gridy++;
			
			// add institutes field and button
			constraints.gridx = 0;
			instituteLabel = new JLabel("Institute:");
			add(instituteLabel, constraints);
			constraints.gridx = 1;
			instituteField = new JComboBox();
			add(instituteField, constraints);
			constraints.gridy++;
			addInstituteButton = new JButton(ADD_INSTITUTE);
			addInstituteButton.setActionCommand(ADD_INSTITUTE);
			addInstituteButton.addActionListener(owner);
			add(addInstituteButton, constraints);
			constraints.gridy++;
			
			// add e-mail field
			constraints.gridx = 0;
			emailLabel = new JLabel("E-mail:");
			add(emailLabel, constraints);
			constraints.gridx = 1;
			emailField = new JTextField(30);
			add(emailField, constraints);
			constraints.gridy++;
			
			// add WWW field
			constraints.gridx = 0;
			wwwLabel = new JLabel("WWW:");
			add(wwwLabel, constraints);
			constraints.gridx = 1;
			wwwField = new JTextField(30);
			add(wwwField, constraints);
			constraints.gridy++;
			
			// create ANDS panel but do not add it yet
			andsInformationPanel = new AndsInformationPanel(
					(user != null)? user.getExternalId() : null
				);
			
			// initialise the dialogue fields
			setUser(user);
			
		}
		
		
		/**
		 * Add an institute to the institutes combo box
		 * 
		 * @param inst	the new institute
		 */
		public void addInstitute(Institute inst) {
			
			instituteField.addItem(inst);
			
		}
		
		
		/**
		 * Enable or disable the ANDS features.
		 * 
		 * @param enabled	true or false
		 */
		public void enableAndsFeatures(boolean enabled) {
			
			if (enabled && !andsInformationDisplayed) {
				
				// create an ANDS information panel
				constraints.gridx = 0;
				constraints.gridwidth = 2;
				add(andsInformationPanel, constraints);
				
			} else if (!enabled && andsInformationDisplayed) {
				
				// remove the ANDS information panel
				remove(andsInformationPanel);
				
			}
			
			// re-draw the panel
			revalidate();
			repaint();
			
		}
		
		
		/**
		 * Build a user object from the contents of the dialogue.
		 * 
		 * @return a user object corresponding to the contents of the dialogue
		 * 
		 * @throws SPECCHIOUserInterfaceException	a required field is not filled
		 */
		public User getUser() throws SPECCHIOUserInterfaceException {
			
			// chec that the required fields have been filled
			if (firstNameField.getText().length() == 0) {
				throw new SPECCHIOUserInterfaceException("You must provide a first name.");
			}
			if (lastNameField.getText().length() == 0) {
				throw new SPECCHIOUserInterfaceException("You must provide a last name.");
			}
			if (emailField.getText().length() == 0) {
				throw new SPECCHIOUserInterfaceException("You must provide an e-mail address.");
			}
			
			// build the user object
			User user = new User();
			user.setTitle((String)titleField.getSelectedItem());
			user.setFirstName(firstNameField.getText());
			user.setLastName(lastNameField.getText());
			user.setInstitute((Institute)instituteField.getSelectedItem());
			user.setEmailAddress(emailField.getText());
			user.setWwwAddress(wwwField.getText());
			user.setRole(UserRoles.USER);
			if (andsInformationPanel != null) {
				user.setExternalId(andsInformationPanel.getPartyId());
			}
		
			return user;
			
		}
		
		/**
		 * Enable or disable the controls on the panel
		 * 
		 * @param enabled	true or false
		 */
		public void setEnabled(boolean enabled) {
			
			// set the enablement of the panel as normal
			super.setEnabled(enabled);
			
			// set the enablement of all child components
			for (Component c : getComponents()) {
				c.setEnabled(enabled);
			}
			
		}
		
		
		/**
		 * Set the selected insititute.
		 * 
		 * @param institute	the  institute to select
		 */
		private void setInstitute(Institute institute) {
			
			instituteField.setSelectedIndex(-1);
			if (institute != null) {
				for (int i = 0; i < instituteField.getItemCount(); i++) {
					Institute item = (Institute)instituteField.getItemAt(i);
					if (item.getInstituteId() == institute.getInstituteId()) {
						instituteField.setSelectedIndex(i);
						break;
					}
				}
			}
			
		}
		
		
		/**
		 * Set the list of institutes from which the user can select.
		 * 
		 * @param institutes	an array of institute objects
		 */
		public void setInstitutes(Institute[] institutes) {
			
			// clear any existing institutes
			instituteField.removeAllItems();
			
			// add the new institutes
			for (Institute institute : institutes) {
				instituteField.addItem(institute);
			}
			
		}
		
		
		/**
		 * Set the user input fields form a user object.
		 * 
		 * @param user	the user object
		 */
		public void setUser(User user) {
			
			if (user != null) {
				titleField.setSelectedItem(user.getTitle());
				firstNameField.setText(user.getFirstName());
				lastNameField.setText(user.getLastName());
				setInstitute(user.getInstitute());
				emailField.setText(user.getEmailAddress());
				wwwField.setText(user.getWwwAddress());
				if (andsInformationPanel != null) {
					andsInformationPanel.setPartyId(user.getExternalId());
				}
			} else {
				titleField.setSelectedIndex(-1);
				firstNameField.setText(null);
				lastNameField.setText(null);
				instituteField.setSelectedIndex(-1);
				emailField.setText(null);
				wwwField.setText(null);
				if (andsInformationPanel != null) {
					andsInformationPanel.setPartyId(null);
				}
			}
			
		}
		
	}
	
	
	/**
	 * ANDS information panel.
	 */
	private class AndsInformationPanel extends JPanel implements ActionListener {
		
		/** serialisation version identifier */
		private static final long serialVersionUID = 1L;

		/** constraints descriptor */
		private GridBagConstraints constraints;
		
		/** check box for a pre-existing ANDS identifier */
		private JCheckBox partyIdCheckBox;
		
		/** label for the party identifier field */
		private JLabel partyIdLabel;
		
		/** text field for the party identifeir */
		private JTextField partyIdField;
		
		/** action for the party identifier check box */
		private static final String HAVE_PARTY_ID = "Have party identifier";
		
		
		/**
		 * Constructor.
		 * 
		 * @param partyId	the ANDS party identifier (may be null)
		 */
		public AndsInformationPanel(String partyId) {
			
			// set up the panel with a grid bag layout
			setLayout(new GridBagLayout());
			constraints = new GridBagConstraints();
			constraints.gridx = 0;
			constraints.gridy = 0;
			constraints.insets = new Insets(4, 4, 4, 4);
			constraints.anchor = GridBagConstraints.WEST;
			
			if (partyId == null) {
				// add the party identifier check box
				constraints.gridx = 1;
				partyIdCheckBox = new JCheckBox("I already have an ANDS party identifier");
				partyIdCheckBox.setActionCommand(HAVE_PARTY_ID);
				partyIdCheckBox.addActionListener(this);
				add(partyIdCheckBox, constraints);
				constraints.gridy++;
			}
			
			// add the party identifier field
			constraints.gridx = 0;
			partyIdLabel = new JLabel("ANDS Party Identifier:");
			add(partyIdLabel, constraints);
			constraints.gridx = 1;
			partyIdField = new JTextField(30);
			add(partyIdField, constraints);
			constraints.gridy++;
			
			if (partyId == null) {
				// disable the party identifier field until the user checks the box
				partyIdLabel.setEnabled(false);
				partyIdField.setEnabled(false);
			} else {
				// the party identifier cannot be edited
				partyIdField.setEditable(false);
			}
			
		}
		
		
		/**
		 * Button handler.
		 */
		public void actionPerformed(ActionEvent event) {
			
			if (HAVE_PARTY_ID.equals(event.getActionCommand())) {
				
				// enable the party identifier box if the check box is ticked
				partyIdLabel.setEnabled(partyIdCheckBox.isSelected());
				partyIdField.setEnabled(partyIdCheckBox.isSelected());
				
			}
			
		}
		
		
		/**
		 * Get the party identifier.
		 * 
		 * @return the contents of the party identifier field
		 */
		public String getPartyId() {
			
			return partyIdField.getText();
		
		}
		
		
		/**
		 * Set the party identifier.
		 * 
		 * @param partyId	the identifier
		 */
		public void setPartyId(String partyId) {
			
			partyIdField.setText(partyId);
			
		}
		
	}
	
	
	   private static void open(URI uri) {
		    if (Desktop.isDesktopSupported()) {
		      try {
		        Desktop.getDesktop().browse(uri);
		      } catch (IOException e) { /* TODO: error handling */ }
		    } else { /* TODO: error handling */ }
		  }   

	   class OpenUrlAction implements ActionListener {
		      @Override public void actionPerformed(ActionEvent e) {
		        open(uri);
		      }
		    }	

}
