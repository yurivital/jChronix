package org.chronix.integration.tests;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.oxymore.chronix.source.chain.dto.Plan;
import org.oxymores.chronix.core.engine.api.ChronixEngine;
import org.oxymores.chronix.core.engine.api.DTOApplication;
import org.oxymores.chronix.core.engine.api.HistoryService;
import org.oxymores.chronix.core.engine.api.OrderService;
import org.oxymores.chronix.core.engine.api.PlanAccessService;
import org.oxymores.chronix.dto.DTOEnvironment;
import org.oxymores.chronix.dto.HistoryQuery;
import org.oxymores.chronix.source.basic.dto.And;
import org.oxymores.chronix.source.basic.dto.Failure;
import org.oxymores.chronix.source.basic.dto.Noop;
import org.oxymores.chronix.source.basic.dto.Or;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class BaseIT
{
    @Inject
    protected BundleContext bc;

    @Inject
    protected ConfigurationAdmin conf;

    @Inject
    protected PlanAccessService meta;

    @Inject
    protected HistoryService history;

    @Inject
    protected OrderService order;

    protected DTOEnvironment envt;
    protected DTOApplication app;
    protected Plan plan1;
    protected Noop noop;
    protected And and;
    protected Or or;
    protected Failure failure;
    protected Map<String, ChronixEngine> engines;

    protected static String configPath = Paths.get("./target/felix-config").toAbsolutePath().normalize().toString();

    @Configuration
    public Option[] config() throws IOException
    {
        try
        {
            FileUtils.cleanDirectory(new File(configPath));
        }
        catch (Exception e)
        {
        }

        return options(junitBundles(), systemPackage("sun.misc"),
                systemProperty("logback.configurationFile")
                        .value("file:" + Paths.get("./target/test-classes/logback.xml").toAbsolutePath().normalize().toString()),
                systemProperty("felix.cm.dir").value(configPath), mavenBundle("org.apache.felix", "org.apache.felix.configadmin", "1.8.8"),
                mavenBundle("org.slf4j", "slf4j-api", "1.7.14"), mavenBundle("org.slf4j", "log4j-over-slf4j", "1.7.14"),
                mavenBundle("ch.qos.logback", "logback-core", "1.1.3"), mavenBundle("ch.qos.logback", "logback-classic", "1.1.3"),
                mavenBundle("org.apache.felix", "org.apache.felix.scr", "2.0.2"),
                mavenBundle("org.oxymores.chronix", "chronix-source-chain", "0.9.2-SNAPSHOT"),
                mavenBundle("org.oxymores.chronix", "chronix-source-basic", "0.9.2-SNAPSHOT"),
                mavenBundle("org.oxymores.chronix", "chronix-agent-command", "0.9.2-SNAPSHOT"),
                mavenBundle("org.oxymores.chronix", "chronix-agent-command-shell", "0.9.2-SNAPSHOT"),
                mavenBundle("org.oxymores.chronix", "chronix-core", "0.9.2-SNAPSHOT"),
                mavenBundle("org.oxymores.chronix", "chronix-nonosgilibs", "0.9.2-SNAPSHOT"),
                mavenBundle("org.oxymores.chronix", "chronix-plugin-api", "0.9.2-SNAPSHOT"), mavenBundle("org.hsqldb", "hsqldb", "2.3.3"),
                mavenBundle("com.thoughtworks.xstream", "xstream", "1.4.8"), mavenBundle("joda-time", "joda-time", "2.8.1"),
                mavenBundle("com.fasterxml", "classmate", "1.1.0"), mavenBundle("commons-codec", "commons-codec", "1.8"),
                mavenBundle("commons-io", "commons-io", "2.4"), mavenBundle("commons-lang", "commons-lang", "2.6"),
                mavenBundle("commons-logging", "commons-logging", "1.1.3"),
                mavenBundle("org.hibernate", "hibernate-validator", "5.2.2.Final"), mavenBundle("org.mnode.ical4j", "ical4j", "1.0.6"),
                mavenBundle("org.jboss.logging", "jboss-logging", "3.3.0.Final"),
                mavenBundle("javax.validation", "validation-api", "1.1.0.Final"));
    }

    @Before
    public void before() throws Exception
    {
        // Sanity check
        Assert.assertNotNull(meta);
        Assert.assertNotNull(order);
        Assert.assertNotNull(conf);

        // Clear directories
        FileUtils.cleanDirectory(new File("C:/TEMP/db1"));

        // Maps
        engines = new HashMap<>();

        // base elements
        noop = new Noop();
        and = new And();
        or = new Or();
        failure = new Failure();

        // Environment
        envt = meta.createMinimalEnvironment(); // node is called "local"
        meta.saveEnvironmentDraft(envt);

        // Application
        app = meta.createMinimalApplication(); // also creates placegroup "local"
        app.setName("test app");
        app.setDescription("This app was created by integration tests");
        app.addEventSource(noop);
        plan1 = new Plan("plan", "integration test plan");
        app.addEventSource(plan1);

        // Deploy
        envt.getPlace("local").addMemberOfGroup(app.getGroup("local").getId());

        // App is not saved here - it will be by the test

        // Allow the broker to start (and clear remaining messages if any)
        try
        {
            org.osgi.service.cm.Configuration cfg = conf.getConfiguration("ServiceHost", null);
            Dictionary<String, Object> props = (cfg.getProperties() == null ? new Hashtable<String, Object>() : cfg.getProperties());
            props.put("org.oxymores.chronix.network.dbpath", "C:/TEMP/amq");
            props.put("org.oxymores.chronix.network.clear", "true");
            props.put("org.oxymores.chronix.network.nodeid", "3654654");
            cfg.update(props);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @After
    public void after() throws Exception
    {
        // Stop all services after each test (including the host)
        for (org.osgi.service.cm.Configuration cfg : conf.listConfigurations(null))
        {
            if (cfg.getFactoryPid() != null && cfg.getFactoryPid().contains("agent"))
            {
                // These elements self-destruct. No need to remove them.
                continue;
            }
            cfg.delete();
        }

        // Wait for end of all engines
        ServiceTracker<ChronixEngine, ChronixEngine> tracker = new ServiceTracker<>(bc,
                bc.createFilter("(objectClass=" + ChronixEngine.class.getCanonicalName() + ")"), null);
        tracker.open();
        while (tracker.getServices() != null && tracker.getServices().length > 0)
        {
            Thread.sleep(100);
        }
        tracker.close();
    }

    protected void save()
    {
        meta.saveEnvironmentDraft(envt);
        meta.promoteEnvironmentDraft("test commit");

        meta.saveApplicationDraft(app);
        meta.promoteApplicationDraft(app.getId(), "test commit");
    }

    protected void addAndStartEngine(String name)
    {
        try
        {
            org.osgi.service.cm.Configuration cfg = conf.getConfiguration("scheduler", null);
            Dictionary<String, Object> props = (cfg.getProperties() == null ? new Hashtable<String, Object>() : cfg.getProperties());
            props.put("chronix.repository.path", "C:/TEMP/db1");
            props.put("chronix.cluster.node.name", "local");
            cfg.update(props);

            ServiceTracker<ChronixEngine, ChronixEngine> tracker = new ServiceTracker<>(bc,
                    bc.createFilter(
                            "(&(objectClass=" + ChronixEngine.class.getCanonicalName() + ")(chronix.cluster.node.name=" + name + "))"),
                    null);
            tracker.open();
            tracker.waitForService(20 * 1000);
            ChronixEngine e = tracker.getService();
            tracker.close();
            if (e == null)
            {
                throw new RuntimeException("could not start engine");
            }
            e.waitOperational();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    protected void waitForOk(int count, int maxSeconds)
    {
        waitForOk(count, maxSeconds, 0);
    }

    protected void waitForOk(int count, int maxSeconds, int waitForExcessMs)
    {
        int waitedMs = 0;
        HistoryQuery q = new HistoryQuery();
        q.setResultCode(0);
        history.query(q);
        while (q.getRes().size() < count && waitedMs < maxSeconds * 1000)
        {
            try
            {
                Thread.sleep(50);
                waitedMs += 50;
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            history.query(q);
        }
        if (q.getRes().size() == count)
        {
            try
            {
                Thread.sleep(waitForExcessMs);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    protected void waitForEnded(int count, int maxSeconds, int waitForExcessMs)
    {
        int waitedMs = 0;
        HistoryQuery q = new HistoryQuery();
        q.hasEnded(true);
        history.query(q);
        while (q.getRes().size() < count && waitedMs < maxSeconds * 1000)
        {
            try
            {
                Thread.sleep(50);
                waitedMs += 50;
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            history.query(q);
        }
        if (q.getRes().size() == count)
        {
            try
            {
                Thread.sleep(waitForExcessMs);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    protected void checkHistory(int nbOk, int nbKo)
    {
        HistoryQuery q = new HistoryQuery();

        q.setResultCode(0);
        history.query(q);
        int ok = q.getRes().size();
        Assert.assertEquals(nbOk, ok);

        q.setResultCode(null);
        history.query(q);
        int all = q.getRes().size();
        Assert.assertEquals(nbKo, all - ok);
    }
}