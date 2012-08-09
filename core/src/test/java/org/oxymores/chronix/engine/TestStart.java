package org.oxymores.chronix.engine;

import java.io.File;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.oxymores.chronix.core.Application;
import org.oxymores.chronix.core.ChronixContext;
import org.oxymores.chronix.core.transactional.CalendarPointer;
import org.oxymores.chronix.demo.DemoApplication;

public class TestStart {
	private static Logger log = Logger.getLogger(TestStart.class);

	public static String db1;

	@Before
	public void init() throws Exception {
		db1 = "C:\\TEMP\\db1";

	}

	@Test
	public void testNoDb() throws Exception {
		log.info("***** Test: without a db, the scheduler fails with a adequate exception");
		ChronixEngine e = null;
		try {
			e = new ChronixEngine("C:\\WONTEXISTEVER");
			e.start();
			e.waitForInitEnd();
		} catch (Exception ex) {
			return;
		}
		Assert.assertEquals(false, e.run); // engine should not have been able
											// to start
	}

	@Test
	public void testCreateAutoApplications() throws Exception {
		log.info("***** Test: with an empty db, the scheduler creates two auto applications");

		ChronixEngine e = new ChronixEngine("C:\\TEMP\\db1");
		e.emptyDb();
		e.ctx.createNewConfigFile();
		e.start();
		e.waitForInitEnd();
		e.stopEngine();

		Assert.assertEquals(2, e.ctx.applicationsById.values().size());

		Application a1 = e.ctx.applicationsByName.get("Operations");
		Application a2 = e.ctx.applicationsByName.get("Chronix Maintenance");

		Assert.assertEquals(1, a1.getChains().size());
		Assert.assertEquals(1, a2.getChains().size());
		Assert.assertEquals(5, a2.getChains().get(0).getStates().size());
	}

	@Test
	public void testCalendarPointerCreationAtStartup() throws Exception {
		String dbPath = "C:\\TEMP\\db1";
		ChronixEngine e = new ChronixEngine(dbPath);
		e.emptyDb();
		e.ctx.createNewConfigFile();

		Application a = DemoApplication.getNewDemoApplication();
		ChronixContext ctx = new ChronixContext();
		ctx.configurationDirectory = new File(dbPath);
		ctx.saveApplication(a);
		ctx.setWorkingAsCurrent(a);
		e.start();
		e.waitForInitEnd();

		EntityManager em = ctx.getTransacEM();
		TypedQuery<CalendarPointer> q = em.createQuery("SELECT c from CalendarPointer c", CalendarPointer.class);

		Assert.assertEquals(3, q.getResultList().size());
	}
}
