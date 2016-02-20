package org.oxymores.chronix;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

public class Launcher
{
    public static void main(String[] args) throws Exception
    {
        System.out.println("Hello World!");

        FrameworkFactory frameworkFactory = ServiceLoader.load(FrameworkFactory.class).iterator().next();
        Map<String, String> config = new HashMap<String, String>();
        config.put("felix.log.level", "4");
        config.put(org.osgi.framework.Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "sun.misc,sun.reflect,javax.annotation");
        Framework framework = frameworkFactory.newFramework(config);
        framework.start();

        BundleContext context = framework.getBundleContext();

        // Enable shell (for debug for now)
        // InputStream is = new FileInputStream(getFile("ext", "org.apache.felix.gogo.shell"));
        // Bundle b2 = context.installBundle("org.apache.felix.gogo.shell", is);
        // b2.start();

        // Enable config admin (before scan as it can use it)
        Bundle b2 = context.installBundle("file:" + getFile("core", "org.apache.felix.configadmin").getAbsolutePath());
        b2.start();

        // Install bundles
        List<Bundle> bundles = new ArrayList<>();
        for (File f : getJars("ext"))
        {
            bundles.add(context.installBundle("file:" + f.getAbsolutePath()));
        }
        for (File f : getJars("core"))
        {
            bundles.add(context.installBundle("file:" + f.getAbsolutePath()));
        }
        for (Bundle b : bundles)
        {
            if (b == null)
            {
                System.out.println("OOOOOOPS ");
                continue;
            }
            System.out.println("Starting " + b.getSymbolicName());
            if (b.getSymbolicName() != null && b.getSymbolicName().startsWith("org.apache.activemq"))
            {
                continue;
            }

            try
            {
                b.start();
            }
            catch (Exception e)
            {
                System.out.println("Could not start " + b.getSymbolicName());
                e.printStackTrace(System.err);
            }
        }

        // Enable the directory scanning plugin
        System.setProperty("felix.fileinstall.dir", "./plugins/ext, ./plugins/core");
        System.setProperty("felix.fileinstall.bundles.new.start", "true");
        System.setProperty("felix.fileinstall.noInitialDelay", "false");
        System.setProperty("felix.fileinstall.poll", "1000");
        System.setProperty("felix.fileinstall.log.level", "4");
        Bundle b1 = context.installBundle("file:" + getFile("bootstrap", "org.apache.felix.fileinstall").getAbsolutePath());

        // System.out.println("Starting directory scanner");
        // b1.start();
        // System.out.println("Directory scanner started");

        Thread.sleep(5000);
        for (Bundle b : framework.getBundleContext().getBundles())
        {
            System.out.println(getState(b.getState()) + " - " + b.getBundleId() + " - " + b.getSymbolicName());
        }

        for (Bundle b : context.getBundles())
        {
            if (b != null && b.getSymbolicName() != null && (b.getSymbolicName().startsWith("org.apache.felix.gogo")
                    || b.getSymbolicName().startsWith("org.apache.felix.configadmin")))
            {
                b.start();
            }
        }

        System.out.println("waiting for framework death");
        // framework.waitForStop(0);
    }

    private static String getState(int i)
    {
        switch (i)
        {
        case Bundle.ACTIVE:
            return "active";
        case Bundle.INSTALLED:
            return "installed";
        case Bundle.RESOLVED:
            return "resolved";
        case Bundle.STARTING:
            return "starting";
        case Bundle.STOPPING:
            return "stopping";
        case Bundle.UNINSTALLED:
            return "uninstalled";
        default:
            return "unknown";
        }
    }

    private static File[] getJars(String directory)
    {
        File f = new File("./plugins/" + directory);
        return f.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.endsWith("jar");
            }
        });
    }

    private static File getFile(String directory, final String jarStart)
    {
        File f = new File("./plugins/" + directory);
        File[] matchingFiles = f.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.startsWith(jarStart) && name.endsWith("jar");
            }
        });
        if (matchingFiles.length != 1)
        {
            throw new RuntimeException("missing plugin in " + directory + " directory: " + jarStart + ". Cannot start the scheduler.");
        }
        return matchingFiles[0];
    }
}
