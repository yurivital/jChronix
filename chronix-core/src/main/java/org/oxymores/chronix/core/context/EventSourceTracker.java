package org.oxymores.chronix.core.context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.io.FilenameUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.oxymores.chronix.core.source.api.EventSource;
import org.oxymores.chronix.core.source.api.EventSourceProvider;
import org.oxymores.chronix.exceptions.ChronixInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class EventSourceTracker implements ServiceTrackerCustomizer<EventSourceProvider, EventSourceProvider>
{
    private static final Logger log = LoggerFactory.getLogger(EventSourceTracker.class);
    private ChronixContextMeta ctx;
    private Bundle bd = FrameworkUtil.getBundle(EventSourceTracker.class);

    EventSourceTracker(ChronixContextMeta ctx)
    {
        this.ctx = ctx;
    }

    @Override
    public EventSourceProvider addingService(ServiceReference<EventSourceProvider> ref)
    {
        // On add, simply init the plugin.

        // get the service reference - it will stored alongside the event sources (if any)
        EventSourceProvider srv = bd.getBundleContext().getService(ref);
        if (srv == null)
        {
            log.warn("Event source plugin has disappeared before finishing its registration: " + ref.getClass().getCanonicalName());
            return null;
        }

        log.info("Event source plugin registering: " + srv.getClass().getCanonicalName() + " from bundle "
                + ref.getBundle().getSymbolicName());

        // Each application may have data created by this plugin - load that data
        Collection<Application2> apps = new ArrayList<>();
        apps.addAll(this.ctx.getApplications());
        apps.addAll(this.ctx.getDrafts());
        for (Application2 app : apps)
        {
            File appDir = this.ctx.getRootApplication(app.getId());
            if (!appDir.isDirectory())
            {
                throw new ChronixInitializationException("Configuration directory " + appDir.getAbsolutePath() + " cannot be opened");
            }

            File bundleDir = new File(FilenameUtils.concat(appDir.getAbsolutePath(), ref.getBundle().getSymbolicName()));
            if (!bundleDir.isDirectory() && !bundleDir.mkdir())
            {
                throw new ChronixInitializationException(
                        "Configuration directory " + bundleDir.getAbsolutePath() + " does not exist and could not be created");
            }

            log.trace("Asking plugin " + ref.getBundle().getSymbolicName() + " to read directory " + bundleDir.getAbsolutePath());
            srv.deserialise(bundleDir, new EngineCb(app, srv, ref.getBundle().getSymbolicName()));
        }

        return srv;
    }

    @Override
    public void modifiedService(ServiceReference<EventSourceProvider> reference, EventSourceProvider service)
    {
        // Nothing to do
    }

    @Override
    public void removedService(ServiceReference<EventSourceProvider> ref, EventSourceProvider service)
    {
        log.info("Source event plugin is going away: " + ref.getClass().getCanonicalName() + ". It was from bundle "
                + ref.getBundle().getSymbolicName());

        for (Application2 app : this.ctx.getApplications())
        {
            for (EventSource o : app.getEventSources(service))
            {
                app.unregisterSource(o);
            }
        }
    }

}
