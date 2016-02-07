/**
 * By Marc-Antoine Gouillart, 2012
 *
 * See the NOTICE file distributed with this work for
 * information regarding copyright ownership.
 * This file is licensed to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.oxymores.chronix.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.commons.io.FilenameUtils;
import org.oxymores.chronix.core.ExecutionNode;
import org.oxymores.chronix.core.ExecutionNodeConnectionAmq;
import org.oxymores.chronix.core.context.ChronixContextMeta;
import org.oxymores.chronix.core.context.ChronixContextTransient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * A Chronix Node. Can be either engine + runner or simply runner.
 *
 */
// @Component(enabled = true)
public class ChronixEngine extends Thread
{
    private static Logger log = LoggerFactory.getLogger(ChronixEngine.class);

    private int feederPort;
    private String feederHost;

    protected ChronixContextMeta ctxMeta;
    protected ChronixContextTransient ctxDb;
    protected String dbPath, brokerPath, logPath;

    protected String localNodeName;

    protected Broker broker;
    protected SelfTriggerAgent stAgent;

    protected Semaphore stop, stopped, engineStops, engineStarts;
    protected boolean run = true;

    // ///////////////////////////////////////////////////////////////
    // Construction

    public ChronixEngine(String dbPath, String nodeName, String logPath)
    {
        this(dbPath, nodeName, FilenameUtils.concat(dbPath, "db_history/db"), FilenameUtils.concat(dbPath, "db_transac/db"),
                FilenameUtils.concat(dbPath, "broker/amq"), logPath);
    }

    public ChronixEngine(String dbPath, String nodeName, String historyDBPath, String transacDbPath, String brokerDataPath, String logPath)
    {
        this.dbPath = dbPath;
        this.brokerPath = brokerDataPath;
        this.localNodeName = nodeName;
        this.logPath = logPath;

        // preContextLoad();

        this.ctxDb = new ChronixContextTransient(historyDBPath, transacDbPath);

        // Force DB init to get errors at once
        this.ctxDb.getHistoryDataSource();
        this.ctxDb.getTransacDataSource();

        this.ctxMeta = new ChronixContextMeta(dbPath);

        // postContextLoad();

        // Putting a token in this will stop the engine
        this.stop = new Semaphore(0);
        // A token is created when the engines stops. (not when it reboots)
        this.stopped = new Semaphore(0);
        // The stop & start sequence is protected with this
        this.engineStops = new Semaphore(0);
        // Will receive one token after each successful or failed start
        this.engineStarts = new Semaphore(0);
    }

    //
    // ///////////////////////////////////////////////////////////////
    protected void startEngine(boolean blocking) throws Exception
    {
        log.info(String.format("Engine %s starting on database %s", this.localNodeName, this.dbPath));

        // Remote nodes must fetch the environment on startup if not already present
        /*
         * if (feederHost != null && !ChronixContext.hasEnvironmentFile(this.dbPath)) { BootstrapListener bl = new BootstrapListener(new
         * File(this.dbPath), localNodeName, feederHost, feederPort); if (!bl.fetchEnvironment()) { throw new
         * ChronixInitializationException(
         * "Could not fetch environment from remote node. Will not be able to start. See errors above for details."); } }
         */

        // Broker with all the consumer threads
        if (this.broker == null)
        {
            this.broker = new Broker(this, this.brokerPath, false, true, true);
        }
        this.broker.registerListeners(this);

        // Active sources agent
        this.stAgent = new SelfTriggerAgent();
        this.stAgent.startAgent(this);

        // Done
        log.info("Engine has finished its boot sequence");

        if (blocking)
        {
            stop.acquire();
        }
    }

    @Override
    public void run()
    {
        MDC.put("node", this.localNodeName);

        // Only does one thing: reload configuration and stop on trigger
        while (this.run)
        {
            // First : start!
            try
            {
                startEngine(false);
            }
            catch (Exception e)
            {
                log.error("The engine has failed to start", e);
                this.run = false;
                this.stop.release();
                return;
            }
            finally
            {
                this.engineStarts.release();
            }

            // Then, wait for the end signal
            try
            {
                this.stop.acquire();
            }
            catch (InterruptedException e)
            {
                log.error("big problem here", e);
            }
            log.debug(this.localNodeName + " has actually begun to stop");

            // Properly stop the engine
            if (this.stAgent != null)
            {
                this.stAgent.stopAgent();
            }
            this.broker.stopEngineListeners();

            // TCP port release is not immediate, sad.
            try
            {
                Thread.sleep(Constants.BROKER_PORT_FREEING_MS);
            }
            catch (InterruptedException e)
            {
                log.info("Interruption while waiting for port freeing");
            }

            this.engineStops.release(1);
            // Done. If 'run' is still true, will restart the engine
        }

        // Stop every thread, not only the event engine threads.
        this.broker.stopRunnerAgents();
        this.broker.stopBroker();
        this.ctxDb.close();
        this.stopped.release();
        log.info("The scheduler has stopped");
    }

    public void waitForInitEnd()
    {
        try
        {
            this.engineStarts.acquire();
        }
        catch (InterruptedException e)
        {
            log.warn("Interruption while waiting for engine to start");
        }
    }

    public void waitForStopEnd()
    {
        try
        {
            this.stopped.acquire();
        }
        catch (InterruptedException e)
        {
            log.info("Interruption while waiting for engine to stop");
        }
        this.stop.release();
    }

    public void queueReloadConfiguration()
    {
        log.info(this.localNodeName + " main engine has received a reload request");
        this.run = true;
        this.stop.release();
    }

    public void stopEngine()
    {
        log.info(this.localNodeName + " main engine has received a stop request");
        this.run = false;
        this.stop.release();
    }

    /** Test method only */
    void stopOutgoingJms()
    {
        this.broker.stopAllOutgoingLinks();
    }

    protected void preContextLoad()
    {
        // First start? (only create apps if first start on a console)
        /*
         * if (!ChronixContext.hasEnvironmentFile(this.dbPath)) { try { // Create a default environment Environment n =
         * PlanBuilder.buildLocalDnsNetwork(); ChronixContext.saveEnvironment(n, new File(this.dbPath));
         * 
         * // Create OPERATIONS application Application a = OperationsApplication.getNewApplication(n.getPlace("local"));
         * ChronixContext.saveApplication(a, new File(this.dbPath));
         * 
         * // Create CHRONIX_MAINTENANCE application a = MaintenanceApplication.getNewApplication(n.getPlace("local"));
         * ChronixContext.saveApplication(a, new File(this.dbPath)); } catch (ChronixPlanStorageException e) { throw new
         * ChronixInitializationException("Could not create default applications", e); } }
         */
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helper accessors

    protected void postContextLoad()
    {
        // this.ctxMeta.cleanTransanc();
    }

    boolean shouldRun()
    {
        return this.run;
    }

    public ChronixContextMeta getContextMeta()
    {
        return this.ctxMeta;
    }

    public ChronixContextTransient getContextTransient()
    {
        return this.ctxDb;
    }

    public String getLogPath()
    {
        return this.logPath;
    }

    public void setFeeder(String host, int port)
    {
        this.feederHost = host;
        this.feederPort = port;
    }

    public void setLocalNodeName(String name)
    {
        this.localNodeName = name;
    }

    public void setFeeder(ExecutionNode en)
    {
        ExecutionNodeConnectionAmq conn = en.getComputingNode().getConnectionParameters(ExecutionNodeConnectionAmq.class).get(0);
        this.setFeeder(conn.getDns(), conn.getqPort());
    }

    public ExecutionNode getLocalNode()
    {
        return this.ctxMeta.getEnvironment().getNode(this.localNodeName);
    }

    /**
     * A list of all execution nodes that this engine should connect to (way is always this node -> other node). Takes application usage
     * into account.
     * 
     * @return
     */
    public List<ExecutionNodeConnectionAmq> getDirectNeigbhours()
    {
        List<ExecutionNodeConnectionAmq> res = new ArrayList<>();

        // TODO: filter nodes which are not actually used by locally loaded applications.

        ExecutionNode local = this.getLocalNode();
        return local.getConnectsTo(ExecutionNodeConnectionAmq.class);

    }

    public Broker getBroker()
    {
        return this.broker;
    }

    public boolean isSimulator()
    {
        return false;
    }
}
