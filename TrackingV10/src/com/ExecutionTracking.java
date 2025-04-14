package com;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Properties;
import javax.sql.rowset.CachedRowSet;
import com.sun.rowset.CachedRowSetImpl;

public class ExecutionTracking {
	private Connection conn;
	private String testExecutionOn;
	
	public static void main(String[] args) throws Exception
	{
		ExecutionTracking  et = new ExecutionTracking();
		et.TrackExecution();
	}
	
	public void TrackExecution() throws FileNotFoundException, SQLException, IOException, InstantiationException, 
	IllegalAccessException, ClassNotFoundException, InterruptedException
	{	
		try
		{
			String pcName = InetAddress.getLocalHost().getHostName();
			System.out.println("TestInfo : Machine name - " + pcName);
		}
		catch (Exception e) 
		{
			System.out.println("TestError: Error in getting pc name!");
		} 
		
		String execIds, execQuery = null, strQuery = null, execRelease = null, execCategory = null, execServerUrl = null, execLapTime = null; CachedRowSet rset; int cnt;
		try {
			execRelease = getSettingsFromConfig("Release");
		} catch (Exception e) {
			System.out.println("TestWarning: Error in getting release value!");
		}
		try {
			execCategory = getSettingsFromConfig("Category");
			if (execCategory.equalsIgnoreCase("${Category}"))
				execCategory = "";
		} catch (Exception e) {
			execCategory = "";
		}
		try {
			execServerUrl = getSettingsFromConfig("ServerUrl");
			if (execServerUrl.equalsIgnoreCase("${ServerUrl}") || execServerUrl.equalsIgnoreCase(""))
				execServerUrl = "";
		} catch (Exception e) {
			execServerUrl = "";
		}
		try {
			execLapTime = getSettingsFromConfig("LapTime");
			if (execLapTime.equalsIgnoreCase("${LapTime}") || execLapTime.equalsIgnoreCase(""))
				execLapTime = "120000";
		} catch (Exception e) {
			execLapTime = "120000";
		}
		
		if ((!execCategory.equals("")) && (!execServerUrl.equals("")))
			execQuery = "SELECT * FROM TEST_EXECUTION_REPORT WHERE STATUS IS NULL AND UPPER(CATEGORY) = '" 
					+ execCategory.toUpperCase() + "' AND URL = '" + execServerUrl + "' AND RELEASE = '" + execRelease + "'";
		else if (!execCategory.equals(""))
			execQuery = "SELECT * FROM TEST_EXECUTION_REPORT WHERE STATUS IS NULL AND UPPER(CATEGORY) = '" 
					+ execCategory.toUpperCase() + "' AND RELEASE = '" + execRelease + "'";
		else
			execQuery = "SELECT * FROM TEST_EXECUTION_REPORT WHERE STATUS IS NULL AND RELEASE = '" + execRelease + "'";
		
		cnt = Integer.parseInt(selectResultFromDataBase("SELECT COUNT(*) FROM ("+ execQuery + ")"));
		while(cnt > 0)
		{
			execIds = "";
			rset = dataBaseOracleConnectionSelect(execQuery);
			while(rset.next())
			{
				String passedCnt, failedCnt,query = "", execId;
				execId = rset.getString("EXECID");
				query = "SELECT COUNT(*) FROM TESTCASE_RESULT WHERE EXEC_ID = '" + execId + "' AND TEST_RESULT = 'PASSED'";
				passedCnt = selectResultFromDataBase(query);
				System.out.println(getDateAndHour() + " : " + query + " : " + passedCnt);
				query = "UPDATE TEST_EXECUTION_REPORT SET PASSED = " + passedCnt + " WHERE EXECID =  '" + execId + "'";
				System.out.println(getDateAndHour() + " : " + query);
				updateResultIntoDataBase(query);
				query = "SELECT COUNT(*) FROM TESTCASE_RESULT WHERE EXEC_ID = '" + execId + "' AND TEST_RESULT = 'FAILED'";
				failedCnt = selectResultFromDataBase(query);
				System.out.println(getDateAndHour() + " : " + query + " : " + failedCnt);
				query = "UPDATE TEST_EXECUTION_REPORT SET FAILED = " + failedCnt + " WHERE EXECID =  '" + execId + "'";
				System.out.println(getDateAndHour() + " : " + query);
				updateResultIntoDataBase(query);
				query = "SELECT TOTAL FROM AMARS.TEST_EXECUTION_REPORT WHERE EXECID =  '" + execId + "'";
				String totalCount = selectResultFromDataBase(query);
				System.out.println(getDateAndHour() + " : " + query);
				int totalCnt = Integer.parseInt(passedCnt) + Integer.parseInt (failedCnt);			
				if (totalCount.contains("(") && totalCount.contains(")"))
					totalCount = totalCount.split("\\(")[1].split("\\)")[0];
				if (totalCnt == Integer.parseInt(totalCount))
				{
					query = "UPDATE TEST_EXECUTION_REPORT SET STATUS = 'Finished' WHERE EXECID =  '" + execId + "'";
					updateResultIntoDataBase(query);
				} 
				execIds =  execIds + "'" + execId + "', ";
			}
			execIds = execIds.substring(0, execIds.length()-2);
			strQuery = "SELECT EXEC_ID, COUNT(*) FROM TESTCASE_RESULT WHERE EXEC_ID IN (" + execIds + ") AND TEST_RESULT = 'PASSED' GROUP BY EXEC_ID";
			rset = dataBaseOracleConnectionSelect(strQuery);
			HashMap<Integer, Integer> execIdcnts1 = getDataKeyValueFromDatabase(rset);
			System.out.println(getDateAndHour() + " : Lap1 Passed Test Cases Count : " + execIdcnts1);
			System.out.println(getDateAndHour() + " : Wait for millseconds : " + execLapTime);
			Thread.sleep(Integer.parseInt(execLapTime));
			rset = dataBaseOracleConnectionSelect(strQuery);
			HashMap<Integer, Integer> execIdcnts2 = getDataKeyValueFromDatabase(rset);
			System.out.println(getDateAndHour() + " : Lap2 Passed Test Cases Count : " + execIdcnts2);
			ArrayList<Integer> keys = new ArrayList<Integer>(execIdcnts1.keySet());
			for (int i = 0; i < execIdcnts1.size(); i++)
			{
				if(!(execIdcnts2.get(keys.get(i)) - execIdcnts1.get(keys.get(i)) > 0))
				{
					String stepsCnt1, stepsCnt2;
					updateRemarks(keys.get(i), "0");
					strQuery = "SELECT COUNT(*) FROM TESTSTEP_RESULT WHERE EXEC_ID = '" + keys.get(i) + "'";
					stepsCnt1 = selectResultFromDataBase(strQuery);
					System.out.println(getDateAndHour() + " : Lap1 Test Steps Count : " + stepsCnt1);
					System.out.println(getDateAndHour() + " : Wait for millseconds : " + Integer.parseInt(execLapTime)/10);
					Thread.sleep(Integer.parseInt(execLapTime)/10);
					stepsCnt2 = selectResultFromDataBase(strQuery);
					System.out.println(getDateAndHour() + " : Lap2 Test Steps Count : " + stepsCnt2);
					if (!(Integer.parseInt(stepsCnt2) - Integer.parseInt(stepsCnt1) > 0))
						updateRemarks(keys.get(i), "1");
					else
						updateRemarks(keys.get(i), "2");
				}
			}	
			cnt = Integer.parseInt(selectResultFromDataBase("SELECT COUNT(*) FROM ("+ execQuery + ")"));
		}
	}
	
	private Connection getDataSourceOracle() throws SQLException	//Added Throws SQLException
	, InstantiationException, IllegalAccessException, ClassNotFoundException, FileNotFoundException, IOException
		{		
			try
			{
				if (conn != null && !conn.isClosed())
					return conn;
			}
			catch(Exception ex)
			{
				System.out.println(ex.getLocalizedMessage());
			}
			testExecutionOn = getSettingsFromConfig("TestExecutionOn");
			//Connection jdbcConnection = null;	
			try
			{
				String strDbUrl, strDbUser, strDbPassword; 
				String[] UrlAry = testExecutionOn.split("-");
				try {
					strDbUrl = getSettingsFromConfig("DbUrl" + "-" + UrlAry[1]);
				}
				catch (Exception e) {
					System.out.println("Database-Warning: Error in getting AMARS TNS!");
					strDbUrl = getSettingsFromConfig("DbUrl");
				}
				strDbUser = getSettingsFromConfig("DbUser");
				strDbPassword = getSettingsFromConfig("DbPassword");
				
				Class.forName("oracle.jdbc.driver.OracleDriver"); 
				conn=DriverManager.getConnection(strDbUrl, strDbUser, strDbPassword);	
				conn.setAutoCommit(true);	
				//jdbcConnection = DriverManager.getConnection(strDbUrl, strDbUser, strDbPassword);	
				//jdbcConnection.setAutoCommit(true);	
			
			}
			catch(SQLException e) 
			{
				String connectMsg = "Could not connect to the database: " + e.getMessage() + " "  + e.getLocalizedMessage();
				System.out.println(connectMsg);
				throw (e);
			}
			//return jdbcConnection;	
			return conn;		
		}

	
	//private ResultSet dataBaseOracleConnectionSelect(String StrQuery) throws Exception	
	private CachedRowSet dataBaseOracleConnectionSelect(String StrQuery) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, FileNotFoundException, IOException	
	{
		
		Connection connection = getDataSourceOracle();
		CachedRowSet crset =  new CachedRowSetImpl();	
		crset.setCommand(StrQuery);	
		crset.execute(connection);
		return crset;		
		
		/*	
		Statement stmtSelect = null;
		ResultSet rsetSelect = null;
		try{			
			stmtSelect = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
			rsetSelect = stmtSelect.executeQuery(StrQuery);
		}
		catch (Exception e) 
		{
			logger.info(e.getMessage());
		}
		return rsetSelect;	
		*/
	}

	private String getSettingsFromConfig(String strKey) throws FileNotFoundException, IOException 
	{
		Properties prop = new Properties();
		prop.load(new FileInputStream("Tracking.properties"));
		String strData = prop.getProperty(strKey);
		strData = strData.trim();
		return strData;
	}
	
	private String selectResultFromDataBase(String strQuery) throws InstantiationException, IllegalAccessException, ClassNotFoundException, FileNotFoundException, SQLException, IOException
	{
		String queryResult = "";
		try
		{
			Connection connection = getDataSourceOracle();	
			ResultSet execRset = null;
			Statement execStmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);	
			execRset = execStmt.executeQuery(strQuery);
			execRset.first();
			queryResult = execRset.getString(1);
			execStmt.close();
			execRset.close();
			connection.close();
		}
		catch (Exception e) 
		{
			System.out.println(e.getLocalizedMessage());
		}
		return queryResult;
	}
	
	private boolean updateResultIntoDataBase(String strQuery) throws InstantiationException, IllegalAccessException, ClassNotFoundException, FileNotFoundException, SQLException, IOException
	{
		boolean isSuccessful = true;
		try
		{
			Connection connection = getDataSourceOracle();	
			Statement execStmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);	
			execStmt.executeUpdate(strQuery);
			connection.setAutoCommit(true);
			execStmt.close();
			connection.close();
		}
		catch (Exception e) 
		{
			isSuccessful = false;
			System.out.println(e.getLocalizedMessage());
		}
		return isSuccessful;
	}
	
	private String getDateAndHour() { 
		String today; 
		DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss"); 
		Calendar calendar = Calendar.getInstance(); 
		today = dateFormat.format(calendar.getTime()); 
		return today; 
	}
	
	private HashMap<Integer, Integer> getDataKeyValueFromDatabase(CachedRowSet rset) throws InstantiationException, IllegalAccessException, ClassNotFoundException, FileNotFoundException, SQLException, IOException 
	{
		HashMap<Integer, Integer> execIdcnts = new HashMap<Integer, Integer>();
		while(rset.next())
			execIdcnts.put(rset.getInt(1), rset.getInt(2));
		rset.close();
		return execIdcnts;
	}
	
	private void updateRemarks(int execId, String remark) throws InstantiationException, IllegalAccessException, ClassNotFoundException, FileNotFoundException, SQLException, IOException
	{
		String strQuery = "SELECT REMARKS FROM TEST_EXECUTION_REPORT WHERE EXECID = '" + execId + "'";
		String remarks = selectResultFromDataBase(strQuery);
		System.out.println(getDateAndHour() + " : " + strQuery + " : " + remarks);
		if (remarks != null)
			strQuery = "UPDATE TEST_EXECUTION_REPORT SET REMARKS = '" + remarks + remark + "' WHERE EXECID = '" + execId + "'";
		else
			strQuery = "UPDATE TEST_EXECUTION_REPORT SET REMARKS = '" + remark + "' WHERE EXECID = '" + execId + "'";
		updateResultIntoDataBase(strQuery);
		System.out.println(getDateAndHour() + " : " + strQuery);
	}
	
}
