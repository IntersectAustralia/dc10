package ch.specchio.factories;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import ch.specchio.eav_db.SQL_StatementBuilder;
import ch.specchio.eav_db.id_and_op_struct;
import ch.specchio.spaces.Space;
import ch.specchio.spaces.SpectralSpace;
import ch.specchio.types.Calibration;
import ch.specchio.types.CalibrationMetadata;
import ch.specchio.types.CalibrationPlotsMetadata;
import ch.specchio.types.Institute;
import ch.specchio.types.Instrument;
import ch.specchio.types.InstrumentDescriptor;
import ch.specchio.types.Picture;
import ch.specchio.types.PictureTable;
import ch.specchio.types.Reference;
import ch.specchio.types.ReferenceBrand;
import ch.specchio.types.ReferenceDescriptor;
import ch.specchio.types.Sensor;
import ch.specchio.types.SpectralFile;



/**
 * Class for manipulating instruments, references and sensors in the database.
 */
public class InstrumentationFactory extends SPECCHIOFactory {
	
	/** the prefix of instrument table and column names */
	private static final String INSTRUMENT = "instrument";
	
	/** the prefix of reference table and column names */
	private static final String REFERENCE = "reference";
	
	/**
	 * Constructor. 
	 * 
	 * @param db_user		database account user name
	 * @param db_password	database account password
	 * 
	 * @throws SPECCHIOFactoryException	could not establish initial context
	 */
	public InstrumentationFactory(String db_user, String db_password) throws SPECCHIOFactoryException {

		super(db_user, db_password);
		
	}
	
	
	public InstrumentationFactory() throws SPECCHIOFactoryException {
		super();
	}


	/**
	 * Delete an instrument calibration.
	 * 
	 * @param calibration_id	the identifier of the calibration to be deleted
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void deleteCalibration(int calibration_id) throws SPECCHIOFactoryException {
		
		try {
			Statement stmt = getStatementBuilder().createStatement();
			String query = "delete from calibration where calibration_id = " + Integer.toString(calibration_id);
			stmt.executeUpdate(query);
			stmt.close();
		} catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		} 
		
	}
	
	
	/**
	 * Delete an instrument from the database.
	 * 
	 * @param instrument_id	the identifier of the instrument to be deleted
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void deleteInstrument(int instrument_id) throws SPECCHIOFactoryException {
		
		try {
			Statement stmt = getStatementBuilder().createStatement();
			String query;
			
			// delete the instrument's calibration data
			query = "delete from calibration where instrument_id = " + Integer.toString(instrument_id);
			stmt.executeUpdate(query);
			
			// delete the instrument from the database
			query = "delete from instrument where instrument_id = " + Integer.toString(instrument_id);
			stmt.executeUpdate(query);
			
			// clean up
			stmt.close();
			
			// delete the instrument from the cache
			getDataCache().delete_instrument(instrument_id);
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Delete a picture from the database.
	 * 
	 * @param picture_id	the identifier of the picture to be deleted
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void deletePicture(int picture_id) throws SPECCHIOFactoryException {
		
		try {
			String query;
			Statement stmt = getStatementBuilder().createStatement();
			
			// delete the picture from the join tables
			for (String table : new String[] {INSTRUMENT, REFERENCE}) {
				query = "delete from " + table + "_x_picture where instrumentation_picture_id=" + Integer.toString(picture_id);
				stmt.executeUpdate(query);
			}
			
			// delete the picture from the instrumentation_picture table
			query = "delete from instrumentation_picture where instrumentation_picture_id=" + Integer.toString(picture_id);
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
	 * Delete a reference from the database.
	 * 
	 * @param reference_id	the identifier of the reference to be deleted
	 * 
	 * @throws SPECCHIOFactoryException	the reference does not exist
	 */
	public void deleteReference(int reference_id) throws SPECCHIOFactoryException {
		
		try {	
			// get SQL-building objects
			Statement stmt = getStatementBuilder().createStatement();
			String query;
			
			// delete the references's calibration data
			query = "delete from calibration where reference_id = " + Integer.toString(reference_id);
			stmt.executeUpdate(query);
			
			// delete the reference itself
			query = "delete from reference where reference_id = " + Integer.toString(reference_id);
			stmt.executeUpdate(query);
			stmt.close();
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Get the calibration metadata for an instrument or reference.
	 * 
	 * @param object_type	"instrument" or "reference"
	 * @param object_id		the instrument or reference identifier
	 * 
	 * @return an array of calibration metatdata objects associated with the instrument
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	private CalibrationMetadata[] getCalibrationMetadata(String object_type, int object_id) throws SPECCHIOFactoryException {
		
		List<CalibrationMetadata> cmlist = new ArrayList<CalibrationMetadata>();
		
		// work out column name
		String id_column = object_type + "_id";
		
		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String query = "select calibration_id,calibration_no,calibration_date,comments, cal_factors, uncertainty from calibration " +
					" where " + id_column + "=" + Integer.toString(object_id);
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				CalibrationMetadata cm = new CalibrationMetadata(rs.getInt(1));
				cm.setCalibrationNumber(rs.getInt(2));
				cm.setCalibrationDate(rs.getDate(3));
				cm.setComments(rs.getString(4));
				cm.setCalFactorsId(rs.getInt(5));
				cm.setCalibrationFactorsPlot(getCalibrationPlotsMetadata(cm.getCalFactorsId(), "cal_factors"));
				cm.setUncertainty_id(rs.getInt(6));
				cm.setCalibrationUncertaintyPlot(getCalibrationPlotsMetadata(cm.getUncertainty_id(), "uncertainty"));
				cmlist.add(cm);
			}
			rs.close();
			stmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return cmlist.toArray(new CalibrationMetadata[cmlist.size()]);
		
	}


	/**
	 * Get calibration plot metadata.
	 * 
	 * @param calibration_id	the calibration identifier
	 * @param plot_type			"cal_factors" or "uncertainty"
	 * 
	 * @return a new calibration plots metadata object
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	private CalibrationPlotsMetadata getCalibrationPlotsMetadata(Integer calibration_id, String plot_type)
			throws SPECCHIOFactoryException {
		
		// get spaces for this calibration identifier
		SpaceFactory sf = new SpaceFactory(this);
		ArrayList<Integer> calibration_id_list = new ArrayList<Integer>();
		calibration_id_list.add(calibration_id);
		ArrayList<Space> spaces = sf.getSpacesForInstrumentationFactors(calibration_id_list);
		
		// create the calibration plots metadata object
		CalibrationPlotsMetadata cpm = new CalibrationPlotsMetadata();
		if (spaces.size() > 0) {
			SpectralSpace ss = (SpectralSpace)spaces.get(0);
			sf.loadSpace(ss);
			cpm.setSpace(ss);
		}
		
		// clean up
		sf.dispose();
		
		return cpm;
		
	}
	
	
	/**
	 * Get an Instrument object from the database.
	 * 
	 * @param instrument_id	the identifier of the desired instrument
	 * 
	 * @return an Instrument object corresponding to instrument_id
	 * 
	 * @trhows SPECCHIOFactoryException	the instrument does not exist
	 */
	public Instrument getInstrument(int instrument_id) throws SPECCHIOFactoryException {
		
		Instrument instrument = new Instrument(instrument_id);

		try {
			
			// set default values
			int institute_id = 0;
			
			// read information from database
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();			String[] tables = new String[]{"instrument"};
			String[] attr = new String[]{"name", "institute_id", "serial_number", "sensor_id"};
			String query = SQL.assemble_sql_select_query(
						SQL.conc_attributes(attr),
						SQL.conc_tables(tables),
						"instrument.instrument_id = " + Integer.toString(instrument_id));
			
						
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				instrument.getInstrumentName().value = rs.getString(1);
				institute_id = rs.getInt(2);
				instrument.getInstrumentNumber().value = rs.getString(3);
				instrument.setSensorId(rs.getInt(4));
			}
			rs.close();		
			
			// load sensor
			instrument.setSensor(getDataCache().get_sensor(instrument.getSensorId()));
			
			Institute inst = getDataCache().get_institute_by_id(institute_id);
			if (inst != null) {
				instrument.getInstrumentOwner().value = inst.toString();
			}
			
			stmt.close();
			
		} catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
		instrument.setValid(true);
		
		return instrument;
		
	}
	
	
	/**
	 * Get the calibration metadata for an instrument.
	 * 
	 * @param instrument_id	the instrument identifier
	 * 
	 * @return an array of calibration metadata objects for the instrument
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public CalibrationMetadata[] getInstrumentCalibrationMetadata(int instrument_id) throws SPECCHIOFactoryException {
		
		return getCalibrationMetadata(INSTRUMENT, instrument_id);
		
	}
	
	
	/**
	 * Get the descriptors of every instrument in the database.
	 * 
	 * @param an array of InstrumentDescriptors identifying every instrument in the database
	 * 
	 * @throws SPECCHIOFactoryException	could not access the database
	 */
	public InstrumentDescriptor[] getInstrumentDescriptors() throws SPECCHIOFactoryException {
		
		List<InstrumentDescriptor> instruments = new ArrayList<InstrumentDescriptor>();
		
		try {
			Statement stmt = getStatementBuilder().createStatement();
			String query = "select instrument_id, name from instrument";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				InstrumentDescriptor instrument = new InstrumentDescriptor(rs.getInt(1), rs.getString(2));
				instruments.add(instrument);
			}
			rs.close();
			stmt.close();
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
		return instruments.toArray(new InstrumentDescriptor[instruments.size()]);
	
	}
	
	
	/**
	 * Get the picture associated with an instrument.
	 * 
	 * @param instrument_id	the instrument identifier
	 * 
	 * @return a table of pictures asscoiated with the instrument
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public PictureTable getInstrumentPictures(int instrument_id) throws SPECCHIOFactoryException {
		
		return getPictures(INSTRUMENT, instrument_id);
		
	}
	
	
	/**
	 * Get the pictures associated with an instrument or reference.
	 * 
	 * @param object_type	"instrument" or "reference"
	 * @param object_id		the instrument or reference identifier
	 * 
	 * @return a table of pictures associated with the instrument or reference
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */

	private PictureTable getPictures(String object_type, int object_id) throws SPECCHIOFactoryException {
		
		PictureTable pictures = new PictureTable();
		
		// work out table and column names
		String join_table = object_type + "_x_picture";
		String id_column = object_type + "_id";
		
		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String query = "select instrumentation_picture.instrumentation_picture_id,instrumentation_picture.caption,instrumentation_picture.image_data" +
					" from instrumentation_picture," + join_table + 
					" where instrumentation_picture.instrumentation_picture_id=" + SQL.prefix(join_table, "instrumentation_picture_id") +
					" and " + SQL.prefix(join_table, id_column) + "=" + Integer.toString(object_id);
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				Integer picture_id = rs.getInt(1);
				String caption = rs.getString(2);
				Blob image_data = rs.getBlob(3);
				Picture picture = new Picture(picture_id, object_id, caption, image_data.getBytes(1, (int)image_data.length()));
				pictures.put(picture_id, picture);
			}
			rs.close();
			stmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return pictures;
		
	}
	
	
	/**
	 * Get the calibration metadata for an reference.
	 * 
	 * @param reference_id	the reference identifier
	 * 
	 * @return an array of calibration metadata objects for the reference
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public CalibrationMetadata[] getReferenceCalibrationMetadata(int reference_id) throws SPECCHIOFactoryException {
		
		return getCalibrationMetadata(REFERENCE, reference_id);
		
	}
	
	
	/**
	 * Get the picture associated with a reference.
	 * 
	 * @param reference_id	the reference identifier
	 * 
	 * @return a table of pictures asscoiated with the reference
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public PictureTable getReferencePictures(int reference_id) throws SPECCHIOFactoryException {
		
		return getPictures(REFERENCE, reference_id);
		
	}
	

	/**
	 * Get a Reference object from the database.
	 * 
	 * @param reference_id	the identifier of the reference to get
	 * 
	 * @return a new Reference object corresponding to reference_id
	 * 
	 * @throws SPECCHIOFactoryException	the reference does not exist
	 */
	public Reference getReference(int reference_id) throws SPECCHIOFactoryException {
		
		Reference ref = new Reference(reference_id);
		
		try {
			Statement stmt = getStatementBuilder().createStatement();
						
			// read information from database

			String query = "select r.name, r.serial_number, r.comments, rb.name, rt.name, r.institute_id, m.name " +
					"from reference r, reference_brand rb, manufacturer m, reference_type rt " +
					"where r.reference_brand_id = rb.reference_brand_id " +
					"and rb.manufacturer_id = m.manufacturer_id and rb.reference_type_id = rt.reference_type_id " +
					"and r.reference_id = " + Integer.toString(reference_id);
			
						
			int pos = 1;
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				ref.getReferenceName().value = rs.getString(pos++);
				ref.getSerialNumber().value = rs.getString(pos++);
				ref.getComments().value = rs.getString(pos++);
				ref.getBrandName().value = rs.getString(pos++);
				ref.getTypeName().value = rs.getString(pos++);
				ref.getReferenceOwner().value = getDataCache().get_institute_by_id(rs.getInt(pos++)).toString();
				ref.getManufacturer().value = rs.getString(pos++);
			}
			rs.close();		
			
			stmt.close();		
			
		} catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
		return ref;
	
	}
	
	
	/**
	 * Get all of the reference brands in the database.
	 * 
	 * @return an array of ReferenceBrands objects describing every brand in the database
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public ReferenceBrand[] getReferenceBrands() throws SPECCHIOFactoryException {
		
		List<ReferenceBrand> brands = new ArrayList<ReferenceBrand>();
		
		try {
			Statement stmt = getStatementBuilder().createStatement();
			String query = "select reference_brand_id, reference_type_id, manufacturer_id, name from reference_brand";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				ReferenceBrand reference = new ReferenceBrand(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4));
				brands.add(reference);
			}
			rs.close();
			stmt.close();
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
		return brands.toArray(new ReferenceBrand[brands.size()]);
	
	}
	
	
	/**
	 * Get the descriptors of every reference in the database.
	 * 
	 * @param an array of ReferenceDescriptors identifying every reference in the database
	 * 
	 * @throws SPECCHIOFactoryException	could not access the database
	 */
	public ReferenceDescriptor[] getReferenceDescriptors() throws SPECCHIOFactoryException {
		
		List<ReferenceDescriptor> references = new ArrayList<ReferenceDescriptor>();
		
		try {
			Statement stmt = getStatementBuilder().createStatement();
			String query = "select reference_id, name from reference";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				ReferenceDescriptor reference = new ReferenceDescriptor(rs.getInt(1), rs.getString(2));
				references.add(reference);
			}
			rs.close();
			stmt.close();
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
		return references.toArray(new ReferenceDescriptor[references.size()]);
	
	}
	
	
	/**
	 * Get a Sensor object from the database.
	 * 
	 * @param sensor_id	the identifier of the desired sensor
	 * 
	 * @return a Sensor object corresponding to sensor_id
	 * 
	 * @throws SPECCHIOFactoryException	the sensor does not exist
	 */
	public Sensor getSensor(int sensor_id) throws SPECCHIOFactoryException {
		
		return getDataCache().get_sensor(sensor_id);
		
	}
	
	
	/**
	 * Get descriptors of all of the sensors in the database. Note that this method
	 * does not load the full sensor definition; just enough information to
	 * identify the sensor. Use getSensor() to load the full definition.
	 * 
	 * @return an array of Sensor objects representing every sensor in the database
	 * 
	 * @throws SPECCHIOFactoryException	could not access the database
	 */
	public Sensor[] getSensorDescriptors() throws SPECCHIOFactoryException {
		
		List<Sensor> sensors = getDataCache().get_sensors();
		
		return sensors.toArray(new Sensor[sensors.size()]);
		
	}
	
	

	/**
	 * Insert an instrument or reference calibration into the database.
	 * 
	 * @param object_type	the type of object with which this calibration is associated ("instrument" or "reference")
	 * @param cal			the calibration object to be inserted
	 * 
	 * @return the identifier of the new calibration, or zero
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void insertCalibration(String object_type, Calibration cal) throws SPECCHIOFactoryException {
		
		String id_column = object_type + "_id";
		
		try {
			
			// insert the spectral file, if any, into the database
			Integer cal_factor_id = 0;
			Integer uncert_id = 0;
			SpectralFile spec_file = cal.getSpectralFile();
			if (spec_file != null) {
				cal_factor_id = insertInstrumentationFactors(spec_file, 0);
				
				if (cal.getIncludesUncertainty() && spec_file.getNumberOfSpectra() == 2)
				{
					uncert_id = insertInstrumentationFactors(spec_file, 1);
				}			
			}

			// update the calibration table
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			id_and_op_struct cal_factor = SQL.is_null_key_get_val_and_op(cal_factor_id);
			id_and_op_struct cal_uncert = SQL.is_null_key_get_val_and_op(uncert_id);
			String query = "insert into calibration (" + id_column + ", cal_factors, uncertainty) " +
					"values (" + Integer.toString(cal.getReferenceId()) + "," + cal_factor.id + "," + cal_uncert.id + ")";
			stmt.executeUpdate(query);
			
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Insert an instrument into the database.
	 * 
	 * @param instrument_name	the name of the new instrument
	 * 
	 * @throws SPECCHIOFactoryException could not insert the new instrument
	 */
	public void insertInstrument(String instrument_name) throws SPECCHIOFactoryException {
		
		try {	
			Statement stmt = getStatementBuilder().createStatement();
			String query = "insert into instrument (name, sensor_id) " +
					"values ('" + instrument_name + "', (select sensor_id from sensor limit 1))";
			stmt.executeUpdate(query);
			stmt.close();
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	/**
	 * Insert an instrument into the database.
	 * 
	 * @param instrument	instrument data structure
	 * 
	 * @throws SPECCHIOFactoryException could not insert the new instrument
	 */	
	public void insertInstrument(Instrument instr) throws SPECCHIOFactoryException {
		
		try {	
			Statement stmt = getStatementBuilder().createStatement();
			
			// update the instrument table
			String query = "insert into instrument (name, sensor_id, institute_id, serial_number) " +
					"values ('" + instr.getInstrumentName().get_value() + "', " + instr.getSensorId() + "," 
					+ getStatementBuilder().is_null_key_get_val_and_op(instr.getInstrumentId()).id + "," + instr.getInstrumentNumber().get_value() + ")";
			stmt.executeUpdate(query);
			
			// get the identifier of the new instrument
			query = "select last_insert_id()";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				instr.setInstrumentId(rs.getInt(1));
			}
			rs.close();
			
			// update the cache
			getDataCache().add_instrument(instr);
			
			// clean up
			stmt.close();
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}				
		
	}
	
	
	/**
	 * Insert an instrument calibration into the database.
	 * 
	 * @param cal			the calibration
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void insertInstrumentCalibration(Calibration cal) throws SPECCHIOFactoryException {
		
		insertCalibration(INSTRUMENT, cal);
		
	}
	
	
	/**
	 * Insert an instrument picture into the database.
	 * 
	 * @param instrument_id	the instrument identifier
	 * @param image_data	the image data
	 * @param caption		the image's caption
	 * 
	 * @return the identifier of the new picture
	 *
	 * @throws SPECCHIOFactoryException	database error
	 */
	public int insertInstrumentPicture(int instrument_id, byte[] image_data, String caption) throws SPECCHIOFactoryException {
		
		return insertPicture(INSTRUMENT, instrument_id, image_data, caption);
		
	}
	
	
	/**
	 * Insert a spectral file into the instrumentation factors table.
	 * 
	 * @param spec_file	the file to be inserted
	 * @param spec_no	the index of the spectrum to be inserted
	 * 
	 * @return the identifier of the instrumentation factors that were inserted, or 0 if the sensor was not recognised
	 * 
	 * @throws SPECCHIOFactoryException database error
	 */
	public int insertInstrumentationFactors(SpectralFile spec_file, int spec_no) throws SPECCHIOFactoryException {
		
		Integer id = 0;
		
		try {
			// get the sensor id
			int sensor_id = getDataCache().get_sensor_id_for_file(spec_file, spec_no);
			if (sensor_id == 0) {
				return 0;
			}
			
			Statement stmt = getStatementBuilder().createStatement();
			String query = "INSERT INTO instrumentation_factors "
				+ "(loading_date, sensor_id, measurement_unit_id) "
				+ "VALUES (" + "current_timestamp, " + Integer.toString(sensor_id) + ", "
				+ getDataCache().get_measurement_unit_id(spec_file.getMeasurementUnits(spec_no)) + ")";
			stmt.executeUpdate(query);
			ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()");
			while (rs.next())
				id = rs.getInt(1);
			rs.close();
			stmt.close();

			// insert the measurement blob
			query = "UPDATE instrumentation_factors set measurement = ? where instrumentation_factors_id = " + id.toString();
			PreparedStatement pstmt = getStatementBuilder().prepareStatement(query);

			InputStream refl = spec_file.getInputStream(spec_no);
			pstmt.setBinaryStream(1, refl, spec_file.getNumberOfChannels(spec_no) * 4);
			pstmt.executeUpdate();
			refl.close();
			pstmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		catch (IOException ex) {
			// don't know why this would happen
			throw new SPECCHIOFactoryException(ex);
		}

		return id;
		
	}
	
	
	/**
	 * Insert a reference into the database.
	 * 
	 * @param reference_name	the name of the new reference
	 * 
	 * @return an empty string
	 * 
	 * @throws SPECCHIOFactoryException could not insert the new reference
	 */
	public void insertReference(String reference_name) throws SPECCHIOFactoryException {
		
		try {	
			Statement stmt = getStatementBuilder().createStatement();
			String query = "insert into reference (name)  values ('" + reference_name + "')";
			stmt.executeUpdate(query);
			stmt.close();
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	

	/**
	 * Insert an reference calibration into the database.
	 * 
	 * @param cal			the calibratino
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void insertReferenceCalibration(Calibration cal) throws SPECCHIOFactoryException {
		
		insertCalibration(REFERENCE, cal);
		
	}
	
	
	/**
	 * Insert an reference picture into the database.
	 * 
	 * @param reference_id	the reference identifier
	 * @param image_data	the image data
	 * @param caption		the image's caption
	 * 
	 * @return the identifier of the new picture
	 *
	 * @throws SPECCHIOFactoryException	database error
	 */
	public int insertReferencePicture(int reference_id, byte[] image_data, String caption) throws SPECCHIOFactoryException {
		
		return insertPicture(REFERENCE, reference_id, image_data, caption);
		
	}
	
	
	/**
	 * Insert a picture into the database.
	 * 
	 * @param object_type	the type of object with which this picture is associated ("instrument" or "reference")
	 * @param object_id		the identifier of the object with which this picture is associated
	 * @param image_data	the image data
	 * @param caption		the image caption
	 * 
	 * @return the identifier of the new picture
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	private int insertPicture(String object_type, int object_id, byte[] image_data, String caption) throws SPECCHIOFactoryException {
		
		int picture_id = 0;
		
		// work out table and column names
		String join_table = object_type + "_x_picture";
		String id_column = object_type + "_id";
		
		try {
			String query;
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			
			// add the picture to the instrumentation_pictures table
			Blob image_blob = SQL.createBlob();
			image_blob.setBytes(1, image_data);
			query = "insert into instrumentation_picture(caption, image_data) values(?, ?)";
			PreparedStatement pstmt = SQL.prepareStatement(query);
			pstmt.setString(1, caption);
			pstmt.setBlob(2, image_blob);
			pstmt.executeUpdate();
			pstmt.close();
			
			// get the identifier assigned to the new picture
			query = "select last_insert_id()";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				picture_id = rs.getInt(1);
			}
			rs.close();
			
			// add the picture to the join table
			query = "insert into " + join_table + "(instrumentation_picture_id," + id_column + ") " +
					"values(" + SQL.conc_ids(picture_id, object_id) + ")";
			stmt.executeUpdate(query);
			
			// clean up
			stmt.close();
			
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return picture_id;
		
	}
	
	
	/**
	 * Load a sensor definition from an input stream.
	 * 
	 * @param is	the input stream
	 * 
	 * @return the identifier of the new sensor
	 *
	 * @throws SPECCHIOFactoryException	the input stream cannot be parsed
	 * @throws IOException				the input stream cannot be read
	 */
	public int loadSensorDefinition(InputStream is) throws SPECCHIOFactoryException, IOException {
		
		String sensor_name, sensor_desc, company;
		int type_no, no_of_channels;
		int sensor_id = 0;
		
		String line;
		double[] wavelengths;
		
		try {
			// use buffered stream to read lines
			DataInputStream data_in = new DataInputStream(is);
			BufferedReader d = new BufferedReader(new InputStreamReader(data_in));
			
			// read header
			d.readLine(); // skip first line
			line=d.readLine();
			
			// tokenise
			String[] hdr = line.split("\t");
			int cnt = 0;
			
			sensor_name = hdr[cnt++];
			sensor_desc = hdr[cnt++];
			company = hdr[cnt++];
			type_no = Integer.valueOf(hdr[cnt++]);
			no_of_channels = Integer.valueOf(hdr[cnt++]);
			
			wavelengths = new double[no_of_channels];
			
			d.readLine(); // skip the body header
			
			cnt = 0;
			// read line by line
			while((line=d.readLine()) != null)
			{
				String[]tokens = line.split("\t");
				
				// second token is the wavelength				
				wavelengths[cnt++] = Double.valueOf(tokens[1]).doubleValue();			
			}
			
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			
			
			// check if manufacturer exists
			int manufacturer_id = 0;
			String query = "select manufacturer_id from manufacturer where name = '" + company + "' OR short_name = '" + company + "'";
			ResultSet rs = stmt.executeQuery(query);
			
			while (rs.next()) {
				manufacturer_id = rs.getInt(1);
			}

			if(manufacturer_id == 0) // insert manufacturer
			{
				String cmd = "insert into manufacturer (name, short_name) " +
						"values (" + SQL.quote_string(company) + "," + SQL.quote_string(company) + ")";
				stmt.executeUpdate(cmd);
				 
				rs = stmt.executeQuery("SELECT LAST_INSERT_ID()");
				 
				 while (rs.next())
					 manufacturer_id = rs.getInt(1);	
			}
			
			hdr[2] = Integer.toString(manufacturer_id); // replace had information
			
			// insert sensor			
			 String cmd = "insert into sensor (name, description, manufacturer_id, sensor_type_no, no_of_channels) " +
			 		"values (" + getStatementBuilder().conc_values(hdr) +
			 ")";
			 stmt.executeUpdate(cmd);
			 rs = stmt.executeQuery("SELECT LAST_INSERT_ID()");
			 while (rs.next())
				 sensor_id = rs.getInt(1);
			 rs.close();
			 
			 // insert all elements
			 short sensor_element_type_id = 0;
			 short sensor_element_types[] = new short[no_of_channels];
			 query = "select sensor_element_type_id from sensor_element_type where code = 0";
			 rs = stmt.executeQuery(query);
			 while (rs.next()) {
				 sensor_element_type_id = rs.getShort(1);
			 }
			 for(int i = 0; i < no_of_channels;i++)
			 {
				 // by default we assume narrow bands. Broadband channels must be defined manually in the database
				 cmd = "insert into sensor_element (avg_wavelength, sensor_id, sensor_element_type_id) values (" + 
				 Double.toString(wavelengths[i]) + ", " + Integer.toString(sensor_id) + ", " + Short.toString(sensor_element_type_id) + ")";
				 stmt.executeUpdate(cmd);
				 sensor_element_types[i] = sensor_element_type_id;
			 }

			 // clean up
			 stmt.close();
			 
			 // add the new sensor to the cache
			 Sensor s = new Sensor(sensor_id);
			 s.setName(sensor_name);
			 s.setDescription(sensor_desc);
			 if (manufacturer_id != 0) {
				 s.setManufacturerId(manufacturer_id);
			 }
			 s.setManufacturerName(company);
			 s.setSensorTypeNumber(type_no);
			 s.setNumberOfChannels(no_of_channels);
			 s.setAverageWavelengths(wavelengths);
			 s.setElementTypes(sensor_element_types);
			 getDataCache().add_sensor(s);
			 
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
		return sensor_id;
	
	}
	
	
	/**
	 * Update calibration metadata.
	 * 
	 * @param cm		the new calibration metadata
	 *
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void updateCalibrationMetadata(CalibrationMetadata cm) throws SPECCHIOFactoryException {
		
		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			
			// build a list of columns and values
			ArrayList<String> attr_and_vals = new ArrayList<String>(8);
			attr_and_vals.add("calibration_no"); attr_and_vals.add(Integer.toString(cm.getCalibrationNumber()));
			if (cm.getComments() != null) {
				attr_and_vals.add("comments");
				attr_and_vals.add(cm.getComments());
			}
			if (cm.getCalibrationDate() != null) {
				attr_and_vals.add("calibration_date");
				attr_and_vals.add(SQL.DateAsString(cm.getCalibrationDate()));
			}
			
			// update database
			Statement stmt = SQL.createStatement();
			String query =SQL.assemble_sql_update_query(
					SQL.conc_attr_and_vals("calibration", attr_and_vals.toArray(new String[attr_and_vals.size()])),
					"calibration",
					"calibration_id=" + cm.getCalibrationId()
				);
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
	 * Update a picture associated with an instrument.
	 * 
	 * @param picture	the new picture
	 * 
	 * @return an emptu string
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void updateInstrumentPicture(Picture picture) throws SPECCHIOFactoryException {
		
		updatePicture(INSTRUMENT, picture);
		
	}
	
	
	/**
	 * Update an existing instrument.
	 * 
	 * @param instrument	the instrument
	 * 
	 * @throws SPECCHIOFactoryException database error
	 */
	public void updateInstrument(Instrument instrument) throws SPECCHIOFactoryException {
		
		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			
			// build a list of columns and values
			ArrayList<String> attr_and_vals = new ArrayList<String>(8);
			attr_and_vals.add("sensor_id"); attr_and_vals.add(Integer.toString(instrument.getSensorId()));
			attr_and_vals.add("serial_number"); attr_and_vals.add(instrument.getInstrumentNumber().toString());
			attr_and_vals.add("name"); attr_and_vals.add(instrument.getInstrumentName().toString());
			
			// get the identifier of the instrument's owner
			attr_and_vals.add("institute_id");
			Institute institute = getDataCache().get_institute_by_name(instrument.getInstrumentOwner().toString());
			if (institute != null) {
				attr_and_vals.add(Integer.toString(institute.getInstituteId()));
			} else {
				attr_and_vals.add("null");
			}
			
			// update database
			Statement stmt = SQL.createStatement();
			String query = SQL.assemble_sql_update_query(
					SQL.conc_attr_and_vals("instrument", attr_and_vals.toArray(new String[attr_and_vals.size()])),
					"instrument",
					"instrument_id=" + instrument.getInstrumentId()
				);
			stmt.executeUpdate(query);
			stmt.close();
		}
		catch (SQLException ex) {
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Update a picture associated with a picture or a reference.
	 * 
	 * @param object_type	"instrument" or "reference"
	 * @param picture		the new picture
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	private void updatePicture(String object_type, Picture picture) throws SPECCHIOFactoryException {
		
		try {
			String query;
			SQL_StatementBuilder SQL = getStatementBuilder();
			
			// add the picture to the instrumentation_pictures table
			Blob image_blob = SQL.createBlob();
			image_blob.setBytes(1, picture.getImageData());
			query = "update instrumentation_picture set caption=?, image_data=? where instrumentation_picture_id=" + picture.getPictureId();
			PreparedStatement pstmt = SQL.prepareStatement(query);
			pstmt.setString(1, picture.getCaption());
			pstmt.setBlob(2, image_blob);
			pstmt.executeUpdate();
			pstmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Update a reference.
	 * 
	 * @param reference	the reference
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void updateReference(Reference reference) throws SPECCHIOFactoryException {
		
		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			
			// build a list of columns and values
			ArrayList<String> attr_and_vals = new ArrayList<String>(10);
			attr_and_vals.add("serial_number"); attr_and_vals.add(reference.getSerialNumber().toString());
			attr_and_vals.add("name"); attr_and_vals.add(reference.getReferenceName().toString());
			attr_and_vals.add("comments");
			if (reference.getComments() != null) {
				attr_and_vals.add(reference.getComments().toString());
			} else {
				attr_and_vals.add("");
			}
			
			// get the identifier of the reference's owner
			Institute institute = getDataCache().get_institute_by_name(reference.getReferenceOwner().toString());
			if (institute != null) {
				attr_and_vals.add("institute_id");
				attr_and_vals.add(Integer.toString(institute.getInstituteId()));
			}
			
			// get the identifier of the reference's brand name
			ReferenceBrand brand = getDataCache().get_reference_brand_by_name(reference.getBrandName().toString());
			if (brand != null) {
				attr_and_vals.add("reference_brand_id");
				attr_and_vals.add(Integer.toString(brand.getReferenceBrandId()));
			}
			
			Statement stmt = SQL.createStatement();
			String query = SQL.assemble_sql_update_query(
					SQL.conc_attr_and_vals("reference", attr_and_vals.toArray(new String[attr_and_vals.size()])),
					"reference",
					"reference_id=" + reference.getReferenceId()
				);
			stmt.executeUpdate(query);
			stmt.close();
		}
		catch (SQLException ex) {
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	/**
	 * Update a picture associated with a reference.
	 * 
	 * @param picture	the new picture
	 * 
	 * @return an emptu string
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public void updateReferencePicture(Picture picture) throws SPECCHIOFactoryException {
		
		updatePicture(REFERENCE, picture);
		
	}



	
}
