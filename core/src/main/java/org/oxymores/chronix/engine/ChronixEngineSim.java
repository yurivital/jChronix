package org.oxymores.chronix.engine;

import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.oxymores.chronix.core.Application;
import org.oxymores.chronix.core.ChronixContext;
import org.oxymores.chronix.core.ExecutionNode;
import org.oxymores.chronix.core.Place;
import org.oxymores.chronix.core.timedata.RunLog;

public class ChronixEngineSim extends ChronixEngine
{
	private static Logger log = Logger.getLogger(ChronixEngineSim.class);

	private UUID appToSimulateId;
	private DateTime start, end;

	public static List<RunLog> simulate(String configurationDirectoryPath, UUID appID, DateTime start, DateTime end)
	{
		ChronixEngineSim es = new ChronixEngineSim(configurationDirectoryPath, appID, start, end);
		es.startEngine(false, false);
		return es.waitForSimEnd();
	}
	
	public ChronixEngineSim(String configurationDirectoryPath, UUID appID, DateTime start, DateTime end)
	{
		super(configurationDirectoryPath, "raccoon:9999", "TransacUnitSim", "HistoryUnitSim", false, 0);
		this.appToSimulateId = appID;
		this.ctx.simulateExternalPayloads = true;
		this.start = start;
		this.end = end;
	}

	// params are ignored!
	@Override
	protected void startEngine(boolean blocking, boolean purgeQueues)
	{
		log.info(String.format("(%s) simulation engine starting (%s)", this.dbPath, this));
		try
		{
			this.startCritical.acquire();
			this.threadInit.release(1);

			// Context
			this.ctx = ChronixContext.loadContext(this.dbPath, this.transacUnitName, this.historyUnitName, this.brokerInterface, true);
			this.ctx.simulateExternalPayloads = true;

			// This is a simulation: we are only interested in a single application
			Application a = this.ctx.applicationsById.get(this.appToSimulateId);
			for (UUID i : this.ctx.applicationsById.keySet())
			{
				if (!i.equals(appToSimulateId))
					this.ctx.removeApplicationFromCache(appToSimulateId);
			}

			// This is a simulation: there is no network, only one simulation node.
			ExecutionNode simulationNode = new ExecutionNode();
			simulationNode.setDns("raccoon");
			simulationNode.setqPort(9999);
			simulationNode.setConsole(true);
			a.addNode(simulationNode);
			a.setLocalNode(simulationNode.getDns(), simulationNode.getqPort());

			for (Place p : a.getPlaces().values())
			{
				p.setNode(simulationNode);
			}

			// Broker with some of the consumer threads. Not started: meta, runner agent, order
			this.broker = new Broker(this.ctx, false, false, false); // In memory, no networking
			this.broker.setNbRunners(this.nbRunner);
			this.broker.registerListeners(this, false, false, true, true, true, true, true, false, true);

			// Active sources agent
			if (broker.getEmf() != null)
			{
				this.stAgent = new SelfTriggerAgentSim();
				((SelfTriggerAgentSim) this.stAgent).setBeginTime(start);
				((SelfTriggerAgentSim) this.stAgent).setEndTime(end);
				this.stAgent.startAgent(broker.getEmf(), ctx, broker.getConnection());
			}

			// Done
			this.startCritical.release();

		} catch (Exception e)
		{
			log.error("The simulation engine has failed to start", e);
			this.run = false;
		}
	}

	public List<RunLog> waitForSimEnd()
	{
		this.waitForInitEnd();
		try
		{
			this.stAgent.join();
		} catch (InterruptedException e)
		{
		}
		
		EntityManager em = this.ctx.getHistoryEM();
		return em.createQuery("SELECT h from RunLog h", RunLog.class).getResultList();
	}
}
