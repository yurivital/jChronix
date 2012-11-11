/**
 * @author Marc-Antoine Gouillart
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

package org.oxymores.chronix.wapi;

import org.apache.cxf.aegis.databinding.AegisDatabinding;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.transport.http_jetty.JettyHTTPDestination;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.apache.cxf.transport.http_jetty.ServerEngine;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.oxymores.chronix.core.ChronixContext;
import org.oxymores.chronix.internalapi.IServer;
import org.oxymores.chronix.internalapi.IServiceClient;
import org.oxymores.chronix.internalapi.IServiceConsole;

public class JettyServer implements IServer
{
	private static Logger log = Logger.getLogger(JettyServer.class);

	protected Server cxfServer, cxfServer2;
	protected org.eclipse.jetty.server.Server jettyServer;
	protected String interfaceToListenOn;
	protected Integer portToListenOn;
	protected ChronixContext ctx;

	public JettyServer(ChronixContext ctx)
	{
		this(ctx, "localhost", 9000);
	}

	public JettyServer(ChronixContext ctx, String hostname, Integer Port)
	{
		this.interfaceToListenOn = hostname;
		this.portToListenOn = Port;
		this.ctx = ctx;
	}

	private String getURL()
	{
		return "http://" + this.interfaceToListenOn + ":" + this.portToListenOn + "/Hello";
	}

	@Override
	public void start()
	{
		log.info("Web service server is starting");
		ServiceClient serviceImpl = new ServiceClient();
		ServerFactoryBean svrFactory = new ServerFactoryBean();

		svrFactory.setServiceClass(IServiceClient.class);
		svrFactory.setAddress(this.getURL());
		svrFactory.setServiceBean(serviceImpl);
		svrFactory.getServiceFactory().setDataBinding(new AegisDatabinding());

		try
		{
			// svrFactory.setStart(false);

			// Start the server (so as to init all jetty objects with CXF
			// parameters)
			cxfServer = svrFactory.create();

			// Get the Jetty server from destination
			JettyHTTPDestination destination = (JettyHTTPDestination) cxfServer.getDestination();
			ServerEngine engine = destination.getEngine();
			JettyHTTPServerEngine jengine = (JettyHTTPServerEngine) engine;
			jettyServer = jengine.getServer();

			// Stop the server so we can add new handlers
			jettyServer.stop();
			jettyServer.join();

			// Get (save) existing CXF handler
			Handler serverHandler = jettyServer.getHandler();

			// Create static resource handler
			ResourceHandler resourceHandler = new ResourceHandler();
			resourceHandler.setDirectoriesListed(true);

			resourceHandler.setWelcomeFiles(new String[] { "index.html" });
			// resourceHandler.setResourceBase(".");
			resourceHandler.setResourceBase("..\\gui\\"); // TODO: for debug only.

			// Add both handlers to server (static first)
			HandlerList handlerList = new HandlerList();
			handlerList.addHandler(resourceHandler);
			handlerList.addHandler(serverHandler);
			jettyServer.setHandler(handlerList);

			// Restart the server and go!
			jettyServer.start();
			// jettyServer.join();
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		ServerFactoryBean svrFactory2 = new ServerFactoryBean();
		svrFactory2.setServiceClass(IServiceConsole.class);
		svrFactory2.setAddress(this.getURL() + "2");
		svrFactory2.setServiceBean(new ServiceConsole(this.ctx));
		svrFactory2.getServiceFactory().setDataBinding(new AegisDatabinding());
		cxfServer2 = svrFactory2.create();

		log.info("Web service server has started");
	}

	@Override
	public void stop()
	{
		cxfServer.stop();
	}

}
