/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.oxymores.chronix.wapi;

import java.io.File;
import java.util.UUID;
import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.ApplicationPath;
import org.slf4j.Logger;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.glassfish.jersey.server.ResourceConfig;
import org.joda.time.DateTime;
import org.oxymores.chronix.core.Application;
import org.oxymores.chronix.core.ChronixContext;
import org.oxymores.chronix.core.ExecutionNode;
import org.oxymores.chronix.core.Environment;
import org.oxymores.chronix.core.ExecutionNodeConnectionAmq;
import org.oxymores.chronix.core.Place;
import org.oxymores.chronix.core.timedata.RunLog;
import org.oxymores.chronix.engine.ChronixEngine;
import org.oxymores.chronix.exceptions.ChronixPlanStorageException;
import org.oxymores.chronix.planbuilder.DemoApplication;
import org.oxymores.chronix.planbuilder.PlanBuilder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 *
 * @author Marc-Antoine
 */
@ApplicationPath("ws")
public class RestApplication extends ResourceConfig implements ServletContextListener
{
    private static final Logger log = LoggerFactory.getLogger(RestApplication.class);
    private ChronixContext ctx;
    private ChronixEngine engine;
    private boolean closeOnExit = false;

    public RestApplication(@Context ServletContext context)
    {
        MDC.put("node", "webservice");
        log.info("Creating a new Chronix WS application");

        Object o = context.getAttribute("engine");

        if (o == null)
        {
            // This happens during tests on a standard web server (a chronix engine would set the init params)
            // So create test data inside a test db.
            log.info("Web services are starting in test mode with debug data");
            String dbPath = "C:\\TEMP\\db1";
            closeOnExit = true;
            try
            {
                if (!ChronixContext.hasEnvironmentFile(dbPath))
                {
                    Environment n = new Environment();
                    ExecutionNode en1 = PlanBuilder.buildExecutionNode(n, "e1", "localhost", 1789);
                    en1.setX(100);
                    en1.setY(100);
                    ExecutionNode en2 = PlanBuilder.buildExecutionNode(n, "e2", "localhost", 1400);
                    en2.setX(200);
                    en2.setY(200);
                    ExecutionNode en3 = PlanBuilder.buildExecutionNode(n, "e3", "localhost", 1804);
                    en3.setX(300);
                    en3.setY(300);
                    en3.setComputingNode(en2);
                    n.setConsole(en1);
                    en1.connectTo(en2, ExecutionNodeConnectionAmq.class);
                    en2.connectTo(en3, ExecutionNodeConnectionAmq.class);

                    Place p1 = PlanBuilder.buildPlace(n, "master node", en1);
                    Place p2 = PlanBuilder.buildPlace(n, "second node", en2);
                    Place p3 = PlanBuilder.buildPlace(n, "hosted node by second node", en3);

                    Application a1 = DemoApplication.getNewDemoApplication();

                    a1.getGroup("group all").addPlace(p1);
                    a1.getGroup("group all").addPlace(p2);
                    a1.getGroup("group all").addPlace(p3);
                    a1.getGroup("group 2").addPlace(p1);
                    a1.getGroup("group 3").addPlace(p2);

                    ChronixContext.saveApplication(a1, new File(dbPath));
                    ChronixContext.saveEnvironment(n, new File(dbPath));

                    String localNodeId = en1.getId().toString();

                    ctx = new ChronixContext("simu", dbPath, true, dbPath + "\\hist.db", dbPath + "\\transac.db");
                    ctx.setLocalNode(ctx.getEnvironment().getNode(UUID.fromString(localNodeId)));

                }
                else
                {
                    ctx = new ChronixContext("simu", dbPath, true, dbPath + "\\hist.db", dbPath + "\\transac.db");
                    ctx.setLocalNode(ctx.getEnvironment().getNodesList().get(0));
                }

                try (org.sql2o.Connection conn = ctx.getHistoryDataSource().beginTransaction())
                {
                    for (int i = 0; i < 100; i++)
                    {
                        RunLog l1 = new RunLog();
                        l1.setActiveNodeId(UUID.randomUUID());
                        l1.setApplicationId(UUID.randomUUID());
                        l1.setChainId(UUID.randomUUID());
                        l1.setChainLaunchId(UUID.randomUUID());
                        l1.setExecutionNodeId(UUID.randomUUID());
                        l1.setId(UUID.randomUUID());
                        l1.setPlaceId(UUID.randomUUID());
                        l1.setActiveNodeName("nodename");
                        l1.setApplicationName("appli");
                        l1.setBeganRunningAt(DateTime.now());
                        l1.setChainName("chain");
                        l1.setDns("localhost");
                        l1.setEnteredPipeAt(DateTime.now());
                        l1.setExecutionNodeName("nodename");
                        l1.setLastKnownStatus("OK");
                        l1.setLogPath("/ii/oo");
                        l1.setWhatWasRun("cmd1");
                        l1.setResultCode(0);
                        l1.setMarkedForUnAt(DateTime.now());
                        l1.setStoppedRunningAt(DateTime.now());
                        l1.setPlaceName("place name");
                        l1.setChainLaunchId(UUID.randomUUID());
                        l1.setLogPath(dbPath + "/log.log");

                        l1.insertOrUpdate(conn);
                    }
                    conn.commit();
                }
            }
            catch (ChronixPlanStorageException ex)
            {
                log.error("Failed to create test data", ex);
                return;
            }
        }
        else
        {
            engine = (ChronixEngine) o;
        }

        this.property(MarshallerProperties.JSON_WRAPPER_AS_ARRAY_NAME, true);
        this.property(UnmarshallerProperties.JSON_WRAPPER_AS_ARRAY_NAME, true);

        this.register(new ServiceMeta(this));
        this.register(new ServiceConsole(this));
        this.register(ErrorListener.class);
    }

    @Override
    public void contextInitialized(ServletContextEvent sce)
    {
        log.debug("Servlet context is loading");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce)
    {
        log.info("Servlet context is closing");
        if (closeOnExit && this.ctx != null)
        {
            this.ctx.close();
            this.ctx = null;
        }
    }

    ChronixContext getContext()
    {
        if (this.engine != null)
        {
            return this.engine.getContext();
        }
        else
        {
            return this.ctx;
        }
    }
}
