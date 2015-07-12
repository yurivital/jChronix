package org.oxymores.chronix.engine;

import java.io.File;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.oxymores.chronix.core.Application;
import org.oxymores.chronix.core.ChronixContext;
import org.oxymores.chronix.core.Network;
import org.oxymores.chronix.exceptions.ChronixPlanStorageException;
import org.oxymores.chronix.planbuilder.PlanBuilder;

public class TestBase
{
    protected static Logger log = Logger.getLogger(TestBase.class);

    @Rule
    public TestName testName = new TestName();

    protected Map<String, ChronixEngine> engines = new java.util.HashMap<>();
    protected Network n;

    protected final String db1 = "C:\\TEMP\\db1";
    protected final String db2 = "C:\\TEMP\\db2";
    protected final String db3 = "C:\\TEMP\\db3";

    @Before
    public void prepare() throws Exception
    {
        log.debug("****BEGINNING OF TEST " + testName.getMethodName() + "***********************************************************************");
        ChronixEngine.emptyDb(db1);
        ChronixEngine.emptyDb(db2);
        ChronixEngine.emptyDb(db3);
        this.addNetworkToDb(db1);
    }

    @After
    public void cleanup()
    {
        for (ChronixEngine e : engines.values())
        {
            if (e.shouldRun())
            {
                // Stop sequentially so as to avoid connection errors on shutdown
                e.stopEngine();
                //e.waitForStopEnd();
            }
        }
        for (ChronixEngine e : engines.values())
        {
            e.waitForStopEnd();
        }
        log.debug("****END OF TEST " + testName.getMethodName() + "***********************************************************************");
    }

    protected ChronixEngine addRunner(String database_path, String name, String host, int port)
    {
        // Unit names don't really matter here - runners don't use them.
        ChronixEngine e = new ChronixEngine(database_path, name, "TransacUnitXXX", "HistoryUnitXXX", true);
        e.setRunnerPort(port);
        e.setRunnerHost(host);
        engines.put(name, e);
        return e;
    }

    protected ChronixEngine addEngine(String database_path, String name)
    {
        return addEngine(database_path, name, "TransacUnit", "HistoryUnit");
    }

    protected ChronixEngine addEngine(String database_path, String name, String transacUnitName, String histUnitName)
    {
        ChronixEngine e = new ChronixEngine(database_path, name, transacUnitName, histUnitName);
        LogHelpers.clearAllTranscientElements(e.ctx);
        engines.put(name, e);
        return e;
    }

    protected void startEngines()
    {
        for (ChronixEngine e : this.engines.values())
        {
            e.start();
        }
        for (ChronixEngine e : this.engines.values())
        {
            e.waitForInitEnd();
        }
    }

    protected void addNetworkToDb(String database_path)
    {
        n = PlanBuilder.buildLocalhostNetwork();
        storeNetwork(database_path, n);
    }

    protected void storeNetwork(String database_path, Network n)
    {
        try
        {
            ChronixContext.saveNetwork(n, new File(database_path));
        }
        catch (ChronixPlanStorageException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected Application addTestApplicationToDb(String database_path, String name)
    {
        // Create a test application and save it inside context
        Application a = PlanBuilder.buildApplication(name, "test");

        PlanBuilder.buildPlaceGroup(a, "master node", "master node", this.n.getPlace("local"));
        PlanBuilder.buildPlaceGroup(a, "all nodes", "all nodes", this.n.getPlace("local"));

        addApplicationToDb(database_path, a);

        return a;
    }

    protected void addApplicationToDb(String database_path, Application a)
    {
        try
        {
            ChronixContext.saveApplicationAndMakeCurrent(a, new File(database_path));
        }
        catch (ChronixPlanStorageException e)
        {
            throw new RuntimeException(e);
        }
    }

    public ChronixEngine firstEngine()
    {
        if (this.engines.size() != 1)
        {
            throw new RuntimeException("there are multiple engines running - there is not order between them");
        }
        return this.engines.values().iterator().next();
    }

    public void saveAndReloadApp(Application a, ChronixEngine e)
    {
        try
        {
            e.ctx.saveApplication(a);
            e.ctx.setWorkingAsCurrent(a);
            e.queueReloadConfiguration();
            e.waitForInitEnd();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            Assert.fail(ex.getMessage());
        }
    }

    protected void sleep(int s)
    {
        try
        {
            Thread.sleep(s * 1000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}