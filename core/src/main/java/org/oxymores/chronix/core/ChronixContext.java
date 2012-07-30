package org.oxymores.chronix.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.net.InetAddress;
import java.util.Date;
import java.util.Hashtable;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.apache.log4j.Logger;
import org.oxymores.chronix.exceptions.ChronixInconsistentMetadataException;
import org.oxymores.chronix.exceptions.ChronixNoLocalNode;

public class ChronixContext {
	private static Logger log = Logger.getLogger(ChronixContext.class);

	public Date loaded;
	public File configurationDirectory;
	public Hashtable<UUID, Application> applicationsById;
	public Hashtable<String, Application> applicationsByName;
	public String localUrl = "";
	public String dns;
	public int port;

	public static ChronixContext loadContext(String appConfDirectory)
			throws IOException, NumberFormatException, ChronixNoLocalNode {
		log.info(String.format(
				"Creating a new context from configuration database %s",
				appConfDirectory));

		ChronixContext ctx = new ChronixContext();
		ctx.loaded = new Date();
		ctx.configurationDirectory = new File(appConfDirectory);
		ctx.applicationsById = new Hashtable<UUID, Application>();
		ctx.applicationsByName = new Hashtable<String, Application>();

		// List files in directory - and therefore applications
		File[] fileList = ctx.configurationDirectory.listFiles();
		Hashtable<String, File[]> toLoad = new Hashtable<String, File[]>();

		for (int i = 0; i < fileList.length; i++) {
			// Convention is app_(data|network)_UUID_version_.crn. Current
			// version is CURRENT, edited is WORKING, other versions are 1..n
			File f = fileList[i];
			String fileName = f.getName();

			if (fileName.startsWith("app_") && fileName.endsWith(".crn")
					&& fileName.contains("CURRENT")
					&& fileName.contains("data")) {
				// This is a current app DATA file.
				String id = fileName.split("_")[2];
				if (!toLoad.containsKey(id))
					toLoad.put(id, new File[2]);
				toLoad.get(id)[0] = f;
			}

			if (fileName.startsWith("app_") && fileName.endsWith(".crn")
					&& fileName.contains("CURRENT")
					&& fileName.contains("network")) {
				// This is a current app NETWORK file.
				String id = fileName.split("_")[2];
				if (!toLoad.containsKey(id))
					toLoad.put(id, new File[2]);
				toLoad.get(id)[1] = f;
			}

			if (fileName.equals("listener.crn")) {
				// This is the activemq configuration file
				log.debug("A listener configuration file was found");
				BufferedReader fr = new BufferedReader(new FileReader(f));
				ctx.localUrl = fr.readLine();
				ctx.dns = ctx.localUrl.split(":")[0];
				ctx.port = Integer.parseInt(ctx.localUrl.split(":")[1]);
				fr.close();
			}

			// TODO: load version file
		}

		// ///////////////////
		// Local url
		// ///////////////////

		if (ctx.localUrl == "") {
			File f = new File(appConfDirectory + "/listener.crn");
			Writer output = new BufferedWriter(new FileWriter(f));
			String url = InetAddress.getLocalHost().getCanonicalHostName()
					+ ":1789";
			output.write(url);
			output.close();
			ctx.localUrl = url;
			ctx.dns = ctx.localUrl.split(":")[0];
			ctx.port = Integer.parseInt(ctx.localUrl.split(":")[1]);
		}

		// ///////////////////
		// Load apps
		// ///////////////////

		for (String ss : toLoad.keySet()) {
			try {
				ctx.loadApplication(toLoad.get(ss)[0], toLoad.get(ss)[1]);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// ///////////////////
		// Post load checkup & inits
		// ///////////////////

		EntityManagerFactory emf = Persistence
				.createEntityManagerFactory("TransacUnit");
		EntityManager em = emf.createEntityManager();
		EntityTransaction tr = em.getTransaction();
		tr.begin();
		for (Application a : ctx.applicationsById.values()) {
			for (State s : a.getStates())
				s.createPointers(em);
			for (Calendar c : a.calendars.values())
				c.createPointers(em);
		}
		tr.commit();

		// TODO: cleanup event data (elements they reference may have been
		// removed)

		// TODO: validate apps

		// Done!
		return ctx;
	}

	public Application loadApplication(File dataFile, File networkFile)
			throws FileNotFoundException, IOException, ClassNotFoundException,
			NumberFormatException, ChronixNoLocalNode {
		log.info(String.format("(%s) Loading an application from file %s",
				this.configurationDirectory, dataFile.getAbsolutePath()));

		FileInputStream fis = new FileInputStream(dataFile);
		ObjectInputStream ois = new ObjectInputStream(fis);

		Application res = (Application) ois.readObject();
		applicationsById.put(res.getId(), res);
		applicationsByName.put(res.getName(), res);

		res.setLocalNode(this.dns, this.port);

		return res;
	}

	public void saveApplication(String name) throws FileNotFoundException,
			IOException {
		saveApplication(this.applicationsByName.get(name));
	}

	public void saveApplication(UUID id) throws FileNotFoundException,
			IOException {
		saveApplication(this.applicationsById.get(id));
	}

	public void saveApplication(Application a) throws FileNotFoundException,
			IOException {
		log.info(String.format("(%s) Saving application %s to temp file",
				this.configurationDirectory, a.getName()));

		String dataFilePath = configurationDirectory.getAbsolutePath()
				+ "/app_data_" + a.getId() + "_WORKING_.crn";
		FileOutputStream fos = new FileOutputStream(dataFilePath);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(a);
		fos.close();
	}

	// Does NOT refresh caches. Restart engine for that !
	public void setWorkingAsCurrent(Application a) throws Exception {
		log.info(String
				.format("(%s) Promoting temp file for application %s as the active file",
						this.configurationDirectory, a.getName()));
		String workingDataFilePath = configurationDirectory.getAbsolutePath()
				+ "/app_data_" + a.getId() + "_WORKING_.crn";
		String currentDataFilePath = configurationDirectory.getAbsolutePath()
				+ "/app_data_" + a.getId() + "_CURRENT_.crn";

		File workingData = new File(workingDataFilePath);
		if (!workingData.exists())
			throw new Exception(
					"work file does not exist. You sure 'bout that? You seem to have made no changes!");
		File currentData = new File(currentDataFilePath);

		// Get latest version
		File[] fileList = configurationDirectory.listFiles();
		int v = 0;
		for (int i = 0; i < fileList.length; i++) {
			File f = fileList[i];
			String fileName = f.getName();
			if (fileName.startsWith("app_") && fileName.endsWith(".crn")
					&& fileName.contains(a.getId().toString())
					&& !fileName.contains("CURRENT")
					&& !fileName.contains("WORKING")
					&& fileName.contains("data")) {
				Integer tmp;
				try {
					tmp = Integer.parseInt(fileName.split("_")[3]);
				} catch (NumberFormatException e) {
					tmp = 0;
				}
				if (tmp > v)
					v = tmp;
			}
		}
		v++;
		log.info(String
				.format("(%s) Current state of application %s will be saved before switching as version %s",
						this.configurationDirectory, a.getName(), v));

		String nextArchiveDataFilePath = configurationDirectory
				.getAbsolutePath()
				+ "/app_data_"
				+ a.getId()
				+ "_"
				+ v
				+ "_.crn";
		File nextArchiveDataFile = new File(nextArchiveDataFilePath);

		// Move CURRENT to the new archive version
		if (currentData.exists()) {
			currentData.renameTo(nextArchiveDataFile);
		}

		// Move WORKING as the new CURRENT
		log.debug(String.format("(%s) New path will be %s",
				this.configurationDirectory, currentDataFilePath));
		workingData.renameTo(new File(currentDataFilePath));
	}

	public Hashtable<UUID, ExecutionNode> getNetwork() {
		Hashtable<UUID, ExecutionNode> res = new Hashtable<UUID, ExecutionNode>();
		for (Application a : applicationsById.values()) {
			res.putAll(a.nodes);
		}
		return res;
	}

	public ExecutionNode getLocalNode(Application a)
			throws ChronixInconsistentMetadataException {
		for (ExecutionNode n : a.nodes.values()) {
			if (n.getBrokerUrl().equals(this.localUrl))
				return n;
		}
		throw new ChronixInconsistentMetadataException(
				String.format(
						"Application %s has no definition for local node %s but is still trying an operation on that node!",
						a.id, this.localUrl));
	}
}