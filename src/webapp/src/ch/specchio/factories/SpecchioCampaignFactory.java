package ch.specchio.factories;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ch.specchio.db_import_export.CampaignExport;
import ch.specchio.db_import_export.CampaignImport;
import ch.specchio.eav_db.SQL_StatementBuilder;
import ch.specchio.eav_db.id_and_op_struct;
import ch.specchio.types.Campaign;
import ch.specchio.types.ResearchGroup;
import ch.specchio.types.SpecchioCampaign;
import ch.specchio.types.User;

public class SpecchioCampaignFactory extends CampaignFactory {
	
	
	/**
	 * Constructor.
	 * 
	 * @param db_user		database account user name
	 * @param db_password	database account password
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public SpecchioCampaignFactory(String db_user, String db_password, String ds_name) throws SPECCHIOFactoryException {
		
		super(db_user, db_password, ds_name);
		
	}
	
	
	/**
	 * Copy constructor. Construct a new factory that uses the same database connection
	 * as an existing factory.
	 * 
	 * @param factory	the existing factory
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public SpecchioCampaignFactory(SPECCHIOFactory factory) throws SPECCHIOFactoryException {
		
		super(factory);
		
	}
	
	
	/**
	 * Create a new research group.
	 * 
	 * @param name	the name of the new research group
	 * 
	 * @return	a ResearchGroup object representing the new group
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	private ResearchGroup createResearchGroup(String name) throws SPECCHIOFactoryException {
		
		ResearchGroup group = new ResearchGroup(name);
		
		try {
			// create SQL-building objects
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String query;
			
			// create an entry in the research_group table
			query = "insert into research_group_view(name) values(" + SQL.quote_string(name) + ")";
			stmt.executeUpdate(query);
			
			// get the identifier for the new group
			query = "select last_insert_id()";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				group.setId(rs.getInt(1));
			}
			rs.close();
			
			// make the current user into a member of the group
			UserFactory uf = new UserFactory(this);
			User user = uf.getUser(getDatabaseUserName());
			query = "insert into research_group_members_view(research_group_id,member_id) " +
					"values(" + Integer.toString(group.getId()) + "," + Integer.toString(user.getUserId()) + ")";
			stmt.executeUpdate(query);
			
			// clean up
			stmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return group;
		
	}
	
	
	/**
	 * Export a campaign to an output stream.
	 * 
	 * @param campaign_id	the identifier of the campaign to be exported
	 * @param os			the output stream to export to
	 * 
	 * @throws SPECCHIOFactoryException	no such campaign
	 * @throws IOException				could not write to the output stream
	 */
	public void exportCampaign(int campaign_id, OutputStream os) throws SPECCHIOFactoryException, IOException {
		
		CampaignExport cex = new CampaignExport(getStatementBuilder(), getDatabaseName(), campaign_id);
		cex.export(os);
		
	}
	
	
	/**
	 * Get the campaign object for a given identifier.
	 * 
	 * @param int campaign_id	the identifier of the desired campaign
	 * 
	 * @return a reference to a campaign object with the given identifier
	 */
	@Override
	public Campaign getCampaign(int campaign_id) throws SPECCHIOFactoryException {
		
		try {
			
			// create a campaign object
			Campaign campaign = new SpecchioCampaign();
			campaign.setId(campaign_id);
			
			// create SQL-building objects
			Statement stmt = getConnection().createStatement();
			String query;
			ResultSet rs;
			
			// load campaign data from the database
			int userId = 0;
			int researchGroupId = 0;
			query = "SELECT name,description,path,user_id,research_group_id FROM campaign_view WHERE campaign_id=" + campaign_id; 
			rs = stmt.executeQuery(query);
			while (rs.next()) {
				campaign.setName(rs.getString(1));
				campaign.setDescription(rs.getString(2));
				campaign.setPath(rs.getString(3));
				userId = rs.getInt(4);
				researchGroupId = rs.getInt(5);
			}
			rs.close();
			
			// set the campaign paths
			query = "SELECT path FROM campaign_path_view WHERE campaign_id=" + campaign_id;
			rs = stmt.executeQuery(query);
			while (rs.next()) {
				campaign.addKnownPath(rs.getString(1));
			}
			rs.close();
			
			// set the campaign investigator
			if (userId != 0) {
				UserFactory uf = new UserFactory(this);
				User investigator = uf.getUser(userId);
				if (investigator != null) {
					campaign.setInvestigator(investigator.toString());
				}
				uf.dispose();
			}
			
			// set the research group
			if (researchGroupId != 0) {
				ResearchGroup rg = getResearchGroup(researchGroupId);
				if (rg != null) {
					campaign.setResearchGroup(rg);
				}
			}
			
			// clean up
			stmt.close();
			
			return campaign;
			
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Get the list of campaigns in the database.
	 * 
	 * @throws SPECCHIOFactoryException the list could not be generated
	 */
	@Override
	public Campaign[] getCampaigns() throws SPECCHIOFactoryException {
		
		try {
			
			// create an SQL statement
			Statement stmt = getConnection().createStatement();
			
			// get the list of campaign names from the database
			ResultSet rs = stmt.executeQuery("SELECT campaign_id,name,path FROM campaign_view");
			
			// put the results into a list
			List<Campaign> results = new LinkedList<Campaign>();
			while (rs.next()) {
				
				// create a new campaign object
				Campaign c = new SpecchioCampaign();
				c.setId(rs.getInt(1));
				c.setName(rs.getString(2));
				c.setPath(rs.getString(3));
				
				// add the object to the list
				results.add(c);
				
			}
			
			// convert the list into an array
			return results.toArray(new Campaign[0]);
			
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	

	/**
	 * Get the identifier of a node in a campaign's hierarchy.
	 * 
	 * @param campaign_id	the identifier of the campaign to be tested
	 * @param name			the name of the node to test
	 * @param parent_id		the identifier of the node's parent
	 * 
	 * @returns the node's identifier, or -1 if the node does not exist
	 * 
	 * @throws SPECCHIOFactoryException	the database could not accessed
	 */
	public int getHierarchyNodeId(int campaign_id, String name, int parent_id) throws SPECCHIOFactoryException {
		
		try {
			
			// create an SQL statement
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			
			// build a statement that will find any existing hierarchy nodes
			id_and_op_struct p_id_and_op = new id_and_op_struct(parent_id);
			String query = "SELECT hierarchy_level_id from hierarchy_level where "
					+ "campaign_id = " + campaign_id
					+ " and parent_level_id " + p_id_and_op.op + " " + p_id_and_op.id
					+ " and name = " + SQL.quote_string(name);
			
			// execute the statement
			int id = -1;
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				id = rs.getInt(1);
			}
			
			rs.close();
			
			return id;
			
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Import a campaign from an input stream.
	 * 
	 * @param userId	the identifier of the user who will own the campaign
	 * @param is		the input stream
	 *
	 * @throws IOException				the stream could not be read
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void importCampaign(int userId, InputStream is) throws IOException, SPECCHIOFactoryException {
		
		try {
			CampaignImport cim = new CampaignImport(getStatementBuilder(), getDatabaseName(), userId);
			cim.read_input_stream(is);
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Get a research group.
	 * 
	 * @param research_group_id	the researh group identifier
	 * 
	 * @return a ResearchGroup object, or null if the group does not exist
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	private ResearchGroup getResearchGroup(int research_group_id) throws SPECCHIOFactoryException {
		
		ResearchGroup group = null;
		
		try {
			// create SQL-building objects
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String query;
			ResultSet rs;
			
			// get the research group definition
			query = "select name from research_group_view where research_group_id=" + Integer.toString(research_group_id);
			rs = stmt.executeQuery(query);
			if (rs.next()) {
				group = new ResearchGroup(research_group_id, rs.getString(1));
			}
			rs.close();
			
			if (group != null) {
				// add the members to the group
				UserFactory uf = new UserFactory(this);
				query = "select member_id from research_group_members_view where research_group_id=" + Integer.toString(research_group_id);
				rs = stmt.executeQuery(query);
				while (rs.next()) {
					int user_id = rs.getInt(1);
					group.addMember(uf.getUser(user_id));
				}
				rs.close();
				uf.dispose();
			}
			
			// clean up
			stmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return group;
	
	}
	
	
	/**
	 * Insert a new campaign into the database.
	 * 
	 * @param c	the campaign to insert
	 * 
	 * @throws SPECCHIOFactoryException	the campaign could not be inserted
	 */
	@Override
	public void insertCampaign(Campaign c) throws SPECCHIOFactoryException {
		
		try {
			// create a research group for the campaign
			ResearchGroup rg = createResearchGroup(c.getName());
			
			// create SQL-building objects
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
		
			// insert the campaign into the database
			String query = "INSERT INTO campaign_view(name, path, research_group_id) VALUES (" +
					SQL.quote_string(c.getName()) + "," +
					SQL.quote_string(c.getPath()) + "," +
					Integer.toString(rg.getId()) +
					")";
			stmt.executeUpdate(query);
		
			// get the campaign id
			ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()");
			while (rs.next())
				c.setId(rs.getInt(1));
			stmt.close();
			
			// update the campaign path table
			query = "insert into campaign_path_view values(" + Integer.toString(c.getId()) + ",?)";
			PreparedStatement pstmt = SQL.prepareStatement(query);
			for (String path : c.getKnownPaths()) {
				pstmt.setString(1, path);
				pstmt.executeUpdate();
			}
			pstmt.close();
			
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
	
	}


	/**
	 * Insert a node into a campaign's hierarchy.
	 * 
	 * @param campaign_id	the identifier of the campaign to which the node is to be added
	 * @param name			the name of the new node
	 * @param parent_id		the identifier of the new node's parent
	 * 
	 * @returns the identifier of the new node
	 * 
	 * @throws SPECCHIOFactoryException	the node could not be inserted
	 */
	public int insertHierarchyNode(int campaign_id, String name, int parent_id) throws SPECCHIOFactoryException {
		
		try {
			
			// initialise id to a nonsense value
			int id = 0;
			
			// create an SQL statement
			Statement stmt = getConnection().createStatement();
			
			// insert the node
			id_and_op_struct p_id_and_op = new id_and_op_struct(parent_id);
			String query = "INSERT INTO hierarchy_level_view (parent_level_id, campaign_id, name) "
					+ "VALUES (" + p_id_and_op.id + ", " + campaign_id + ", '" + name + "')";
			stmt.executeUpdate(query);
			
			// find out what it id was
			ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()");
			while (rs.next())
				id = rs.getInt(1);

			rs.close();
			
			return id;
			
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Remove a campaign from the database.
	 * 
	 * @param id		the identifier of the campaign to be removed
	 * @param is_admin	is the requesting user an administator?
	 * 
	 * @throws SPECCHIOFactoryException	the campaign could not be removed
	 */
	public void removeCampaign(int campaign_id, boolean is_admin) throws SPECCHIOFactoryException
	{
		
		try {
			
			Statement stmt = getStatementBuilder().createStatement();
			String campaign_cond = "campaign_id = " + Integer.toString(campaign_id);
			
			// remove all hierarchies
			String query = "select hierarchy_level_id, name from hierarchy_level where " + campaign_cond;
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				int hierarchy_id = rs.getInt(1);		
				String name = rs.getString(2);
				removeHierarchyNode(hierarchy_id, name, is_admin);
			}
			rs.close();
			
			// remove campaign paths
			query = "delete from campaign_path_view where " + campaign_cond;
			stmt.executeUpdate(query);
		
			// get the approprate view name
			String table_name = (is_admin)? "campaign" : "campaign_view";
			
			// save a reference to the research group associated with this campaign
			int research_group_id = 0;
			query = "select research_group_id from " + table_name + " where " + campaign_cond;
			rs = stmt.executeQuery(query);
			while (rs.next()) {
				research_group_id = rs.getInt(1);
			}
		
			// remove campaign itself
			String cmd = "delete from " + table_name + " where " + campaign_cond;
			stmt.executeUpdate(cmd);
			
			// remove the research group
			if (research_group_id != 0) {
				removeResearchGroup(research_group_id);
			}
			
			stmt.close();
			
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		
		}
		
	}
	
	
	/**
	 * Remove a whole sub-heirarchy from the database.
	 * 
	 * @param hierarchy_id	the identifier of the node at the root of the sub-hierarchy
	 * @param name			the name of the node
	 * @param is_admin		is the requesting user an administator?
	 * 
	 * @throws SPECCHIOFactoryException	the sub-hierarchy could not be remove
	 */
	public void removeHierarchyNode(int hierarchy_id, String name, boolean is_admin) throws SPECCHIOFactoryException {
		
		try {
			
			Statement stmt = getStatementBuilder().createStatement();
			String table_name;
			String cmd;
			
			// remove all spectrum nodes under this hierarchy
			SpectrumFactory sf = new SpectrumFactory(this);
			String query = "select spectrum_id from spectrum where hierarchy_level_id = " + Integer.toString(hierarchy_id);
			ResultSet rs = stmt.executeQuery(query);	
			while (rs.next()) {	
				int spectrum_id = rs.getInt(1);
				sf.removeSpectrum(spectrum_id, is_admin);
			}
			rs.close();
			
			// remove all sub-hierarchies
			query = "select hierarchy_level_id, name from hierarchy_level where parent_level_id = " + Integer.toString(hierarchy_id);
			rs = stmt.executeQuery(query);
			while (rs.next()) {
				int hierarchy_level_id = rs.getInt(1);		
				String sub_name = rs.getString(2);
				removeHierarchyNode(hierarchy_level_id, name+"/"+sub_name, is_admin);
			}	
			rs.close();		
			
			table_name = (is_admin)? "hierarchy_level" : "hierarchy_level_view";
	
			// remove hierarchy itself
			cmd = "delete from "+table_name+" where hierarchy_level_id = " + Integer.toString(hierarchy_id);
			stmt.executeUpdate(cmd); 
			
			stmt.close();
			
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Remove a research group.
	 * 
	 * @param research_group_id	the research group identifier
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void removeResearchGroup(int research_group_id) throws SPECCHIOFactoryException {
		
		try {
			// create SQL-building objects
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String research_group_cond = "research_group_id=" + Integer.toString(research_group_id);
			String query;
			
			// need to disable foreign key checks because research_group_view and research_group_members_view refer to each other
			query = "set @@foreign_key_checks = 0";
			stmt.executeQuery(query);
			
			// delete entry from the research group table
			query = "delete from research_group_view where " + research_group_cond;
			stmt.executeUpdate(query);
			
			// restore foreign key checks
			query = "set @@foreign_key_checks = 1";
			stmt.executeQuery(query);
			
			// delete members from the research group member table
			query = "delete from research_group_members_view where " + research_group_cond;
			stmt.executeUpdate(query);
			
			// clean up
			stmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Update campaign information.
	 * 
	 * @param campaign	the new campaign information
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void updateCampaign(Campaign campaign) throws SPECCHIOFactoryException {
		
		try {
			// create SQL-building objects
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			PreparedStatement pstmt;
			String query;
			
			// set up attributes and values to set
			ArrayList<String> attr_and_vals = new ArrayList<String>(6);
			attr_and_vals.add("name"); attr_and_vals.add(campaign.getName());
			attr_and_vals.add("description"); attr_and_vals.add(campaign.getDescription());
			attr_and_vals.add("path"); attr_and_vals.add(campaign.getPath());
			
			// update the campaign table
			query = SQL.assemble_sql_update_query(
					SQL.conc_attr_and_vals("campaign_view", attr_and_vals.toArray(new String[attr_and_vals.size()])),
					"campaign_view",
					"campaign_id=" + Integer.toString(campaign.getId())
				);
			stmt.executeUpdate(query);
			
			// update the campaign path table
			query = "delete from campaign_path_view where campaign_id=" + Integer.toString(campaign.getId());
			stmt.executeUpdate(query);
			query = "insert into campaign_path_view values(" + Integer.toString(campaign.getId()) + ",?)";
			pstmt = SQL.prepareStatement(query);
			for (String path : campaign.getKnownPaths()) {
				pstmt.setString(1, path);
				pstmt.executeUpdate();
			}
			
			// clean up
			stmt.close();
			pstmt.close();
			
			ResearchGroup rg = campaign.getResearchGroup();
			if (rg != null) {
				if (rg.getId() == 0) {
					// the campaign was created without a research group; we need to create a new research group
					ResearchGroup rgNew = createResearchGroup(campaign.getName());
					rg.setId(rgNew.getId());
					rg.setName(rgNew.getName());
					
					// insert the research group identifier into the campaign table
					stmt = SQL.createStatement();
					query = "update campaign_view" +
							" set research_group_id=" + Integer.toString(rgNew.getId()) +
							" where campaign_id=" + Integer.toString(campaign.getId());
					stmt.executeUpdate(query);
					stmt.close();
				}
				
				// update research group tables
				updateResearchGroup(campaign.getResearchGroup());
			}
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Update research group membership
	 * 
	 * @param group	the research group
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	private void updateResearchGroup(ResearchGroup group) throws SPECCHIOFactoryException {
		
		try {
			// create SQL-building objects
			SQL_StatementBuilder SQL = getStatementBuilder();
			String query;
			
			// delete the existing research group members
			query = "delete from research_group_members_view where research_group_id=" + Integer.toString(group.getId());
			Statement stmt = SQL.createStatement();
			stmt.executeUpdate(query);
			stmt.close();
			
			// add the new research group members
			query = "insert into research_group_members_view(research_group_id, member_id) values(?, ?)";
			PreparedStatement pstmt = SQL.prepareStatement(query);
			for (User member : group.getMembers()) {
				pstmt.setInt(1, group.getId());
				pstmt.setInt(2, member.getUserId());
				pstmt.executeUpdate();
			}
			pstmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
			
			
		
}
