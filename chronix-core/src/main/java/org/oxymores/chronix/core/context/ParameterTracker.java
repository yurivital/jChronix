package org.oxymores.chronix.core.context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.oxymores.chronix.api.prm.Parameter;
import org.oxymores.chronix.api.prm.ParameterProvider;
import org.oxymores.chronix.core.EventSourceWrapper;
import org.oxymores.chronix.core.ParameterHolder;
import org.oxymores.chronix.exceptions.ChronixInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ParameterTracker implements ServiceTrackerCustomizer<ParameterProvider, ParameterProvider>
{
    private static final Logger log = LoggerFactory.getLogger(ParameterTracker.class);
    private ChronixContextMeta ctx;
    private Bundle bd = FrameworkUtil.getBundle(ParameterTracker.class);

    ParameterTracker(ChronixContextMeta ctx)
    {
        this.ctx = ctx;
    }

    @Override
    public ParameterProvider addingService(ServiceReference<ParameterProvider> ref)
    {
        // On add, simply init the plugin.

        // get the service reference - it will stored alongside the parameter (if any)
        ParameterProvider srv = bd.getBundleContext().getService(ref);
        if (srv == null)
        {
            log.warn("Parameter plugin has disappeared before finishing its registration: " + ref.getClass().getCanonicalName());
            return null;
        }

        log.info(
                "Parameter plugin registering: " + srv.getClass().getCanonicalName() + " from bundle " + ref.getBundle().getSymbolicName());

        // Each application may have data created by this plugin - load that data
        Collection<Application> apps = new ArrayList<>();
        apps.addAll(this.ctx.getApplications());
        apps.addAll(this.ctx.getDrafts());
        for (Application app : apps)
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
            Set<? extends Parameter> prms = srv.deserialise(bundleDir);

            // Inflate the parameters inside event sources
            for (EventSourceWrapper esw : app.getEventSourceWrappers().values())
            {
                ph: for (ParameterHolder ph : esw.getParameters())
                {
                    for (Parameter prm : prms)
                    {
                        if (prm.getId().equals(ph.getParameterId()))
                        {
                            ph.setDto(prm);
                            continue ph;
                        }
                    }
                    // throw new ChronixInitializationException(
                    // "A parameter is defined inside the plan but was not found inside the plugin results. Check plugins are all present");
                }
            }
        }

        return srv;
    }

    @Override
    public void modifiedService(ServiceReference<ParameterProvider> reference, ParameterProvider service)
    {
        // Nothing to do
    }

    @Override
    public void removedService(ServiceReference<ParameterProvider> ref, ParameterProvider service)
    {
        log.info("Parameter plugin is going away: " + ref.getClass().getCanonicalName() + ". It was from bundle "
                + ref.getBundle().getSymbolicName());

        // TODO: remove parameters.
    }

}