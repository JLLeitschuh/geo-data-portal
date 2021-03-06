package gov.usgs.cida.gdp.dataaccess.helper;

import java.io.FileNotFoundException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import java.net.MalformedURLException;
import thredds.catalog.InvService;
import ucar.nc2.units.DateType;
import thredds.catalog.DataFormatType;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import opendap.dap.DAP2Exception;
import opendap.dap.parsers.ParseException;
import org.junit.Test;
import thredds.catalog.InvAccess;
import thredds.catalog.InvCatalog;
import thredds.catalog.InvCatalogFactory;
import thredds.catalog.InvDataset;
import thredds.catalog.ServiceType;
import static org.junit.Assert.*;

public class NetCDFUtilityTest {

	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NetCDFUtilityTest.class);

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		log.debug("Started testing class.");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		log.debug("Ended testing class.");
	}

	@Test
	public void testGetDatasetHandles() throws Exception {
		URI catalogURI = NetCDFUtilityTest.class.getResource("multi_catalog_all.xml").toURI();
		InvCatalogFactory factory = new InvCatalogFactory("default", true);
		InvCatalog catalog = factory.readXML(catalogURI);

		StringBuilder errorBuilder = new StringBuilder();
		if (!catalog.check(errorBuilder)) {
			throw new IOException(errorBuilder.toString());
		}

		List<InvAccess> handlesFromCatalog = NetCDFUtility.getDatasetHandles(catalog, ServiceType.OPENDAP);
		assertEquals(handlesFromCatalog.size(), 2);
		assertEquals(handlesFromCatalog.get(0).getDataset().getName(), "NCEP WaveWatch III:  Atlantic (4 min grid)");
		assertEquals(handlesFromCatalog.get(1).getDataset().getName(), "NCEP WaveWatch III:  Atlantic (10 min grid)");

		List<InvDataset> topLevelDatasets = catalog.getDatasets();
		assertEquals(topLevelDatasets.size(), 1);
		assertEquals(topLevelDatasets.get(0).getName(), "NOAA-WW3");
		List<InvAccess> handlesFromTopLevelDataset =
				NetCDFUtility.getDatasetHandles(topLevelDatasets.get(0), ServiceType.OPENDAP);

		// Assert that the handles obtained from the catalog and from the top-level dataset are the same.
		assertEquals(handlesFromCatalog, handlesFromTopLevelDataset);
	}

	@Test
	public void testCIDAThredds() {
		URI catalogURI = null;
		try {
			catalogURI = NetCDFUtilityTest.class.getResource("internal_cida.xml").toURI();
		} catch (URISyntaxException ex) {
			fail(ex.getMessage());
		}
		InvCatalogFactory factory = new InvCatalogFactory("default", true);
		InvCatalog catalog = factory.readXML(catalogURI);

		StringBuilder errorBuilder = new StringBuilder();
		if (!catalog.check(errorBuilder)) {
			fail(errorBuilder.toString());
		}

		List<InvAccess> openDAPHandlesFromCatalog = NetCDFUtility.getDatasetHandles(catalog, ServiceType.OPENDAP);
		assertEquals(12, openDAPHandlesFromCatalog.size());

		assertEquals("CIDA Development and Testing USGS Internal THREDDS Server.", catalog.getName());
		assertEquals("1.0.1", catalog.getVersion());
		assertTrue(catalog.getProperties().isEmpty());
		List<InvService> invServiceList = catalog.getServices();
		assertFalse(invServiceList.isEmpty());

		InvDataset invDataSet = catalog.findDatasetByID("gmo/GMO_w_meta.ncml");
		DataFormatType dft = invDataSet.getDataFormatType();
		assertEquals("NetCDF-Grid", dft.toString());

		String catalogURL = invDataSet.getCatalogUrl();
		assertTrue(catalogURL.contains("file:/"));
		assertTrue(catalogURL.contains("gdp-data-access/target/test-classes/gov/usgs/cida/gdp/dataaccess/helper/internal_cida.xml#gmo/GMO_w_meta.ncml"));

		String auth = invDataSet.getAuthority();
		assertNull(auth);

		assertFalse(invDataSet.hasNestedDatasets());

		List<DateType> dateList = invDataSet.getDates();
		assertTrue(dateList.isEmpty());

		assertTrue(invDataSet.hasAccess());

		InvAccess invAccess = invDataSet.getAccess(ServiceType.DODS);
		assertNull(invAccess);

		invAccess = invDataSet.getAccess(ServiceType.HTTPServer);
		assertNotNull(invAccess);
		assertTrue(Double.isNaN(invAccess.getDataSize()));
		assertEquals("gmo/GMO_w_meta.ncml", invAccess.getUrlPath());
		assertFalse(invAccess.hasDataSize());
		assertTrue(invAccess.getUnresolvedUrlName().contains("/thredds/"));
		assertTrue(invAccess.getUnresolvedUrlName().contains("/gmo/GMO_w_meta.ncml"));

		List<InvAccess> invList = invDataSet.getAccess();
		assertFalse(invList.isEmpty());
		assertEquals(invList.size(), 4);
	}

	@Test
	public void testDateRangeWithoutUnitsAttribute() throws MalformedURLException, URISyntaxException, FileNotFoundException, IOException, ParseException, DAP2Exception {
		// Attained from http://cida-eros-thredds1.er.usgs.gov:8081/qa/thredds/dodsC/temp/monthlyETa/modis_monthly_ET.ncml.html
		URI modisMonthlyEtURI = this.getClass().getResource("modis_monthly_ET.ncml").toURI();
		List<String> result = OpendapServerHelper.getOPeNDAPTimeRange(modisMonthlyEtURI.toASCIIString(), "et");
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(result.get(0), "2000-01-01T00:00:00Z");
		assertEquals(result.get(1), "2011-12-01T00:00:00Z");
	}

	@Test
	public void testDateRangeWithCalendarMonthsUnitsAttribute() throws MalformedURLException, URISyntaxException, FileNotFoundException, IOException, ParseException, DAP2Exception {
		// Attained from http://cida-eros-thredds1.er.usgs.gov:8081/qa/thredds/dodsC/temp/monthlyETa/modis_monthly_ET_2.ncml.html
		URI modisMonthlyEtURI = this.getClass().getResource("modis_monthly_ET_2.ncml").toURI();
		List<String> result = OpendapServerHelper.getOPeNDAPTimeRange(modisMonthlyEtURI.toASCIIString(), "et");
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(result.get(0), "2000-01-01T00:00:00Z");
		assertEquals(result.get(1), "2011-12-01T00:00:00Z");
	}
	
        // this test case was using both the CalendarDateUnit and the DateUnit (it executes both try blocks in getTimeDim) 
	@Test
	public void testDateRangeWithoutCalendarMonthsUnitsAttribute() throws MalformedURLException, URISyntaxException, FileNotFoundException, IOException, ParseException, DAP2Exception {
		// Attained from http://cida-eros-thredds1.er.usgs.gov:8081/qa/thredds/dodsC/temp/monthlyETa/modis_monthly_ET_3.ncml.html
		URI modisMonthlyEtURI = this.getClass().getResource("modis_monthly_ET_3.ncml").toURI();
		List<String> result = OpendapServerHelper.getOPeNDAPTimeRange(modisMonthlyEtURI.toASCIIString(), "et");
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(result.get(0), "2000-01-01T00:00:00Z");
		assertEquals(result.get(1), "2011-12-01T11:16:07.865Z"); //added the .865 after eliminating the DateUnit use
	}
        
        // created to resolve Jira bug GDP-1027
        @Test
	public void testDateRangeWithMonthNoLeap() throws MalformedURLException, URISyntaxException, FileNotFoundException, IOException, ParseException, DAP2Exception {
                // String dataSet = "http://cida-eros-netcdfdev.er.usgs.gov:8080/thredds/dodsC/thredds/temp/haj/wrfc36km_d01_T2_monthly_2000-09_2050-12_NOHALO.nc";
		URI dataSetURI = this.getClass().getResource("d01_T2_monthly_NOHALO.ncml").toURI();
                String gridSelection = "T2MAX";

                List<String> result = OpendapServerHelper.getOPeNDAPTimeRange(dataSetURI.toASCIIString(), gridSelection);
		assertNotNull(result); 
		assertEquals(2, result.size());
                assertEquals(result.get(0), "2000-10-01T00:00:00Z");
                assertEquals(result.get(1), "2051-01-01T00:00:00Z"); // correct according to the toolsui tool; java -Xmx1g -jar toolsUI.jar
	}
        
}
