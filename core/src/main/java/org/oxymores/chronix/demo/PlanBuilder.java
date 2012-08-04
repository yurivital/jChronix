package org.oxymores.chronix.demo;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.oxymores.chronix.core.ActiveNodeBase;
import org.oxymores.chronix.core.Application;
import org.oxymores.chronix.core.Chain;
import org.oxymores.chronix.core.ConfigurableBase;
import org.oxymores.chronix.core.ExecutionNode;
import org.oxymores.chronix.core.Parameter;
import org.oxymores.chronix.core.Place;
import org.oxymores.chronix.core.PlaceGroup;
import org.oxymores.chronix.core.State;
import org.oxymores.chronix.core.active.ChainEnd;
import org.oxymores.chronix.core.active.ChainStart;
import org.oxymores.chronix.core.active.ShellCommand;

public class PlanBuilder {

	public static Application buildApplication(String name, String description) {
		Application a = new Application();
		a.setname(name);
		a.setDescription("test application auto created");

		return a;
	}

	public static Chain buildChain(Application a, String name,
			String description, PlaceGroup targets) {
		Chain c1 = new Chain();
		c1.setDescription(description);
		c1.setName(name);
		a.addActiveElement(c1);

		// Start & end retrieval
		ChainStart cs = null;
		for (ConfigurableBase nb : a.getActiveElements().values()) {
			if (nb instanceof ChainStart)
				cs = (ChainStart) nb;
		}
		ChainEnd ce = null;
		for (ConfigurableBase nb : a.getActiveElements().values()) {
			if (nb instanceof ChainEnd)
				ce = (ChainEnd) nb;
		}

		// Start
		State s1 = new State();
		s1.setChain(c1);
		s1.setRunsOn(targets);
		s1.setRepresents(cs);
		s1.setX(100);
		s1.setY(100);

		// End
		State s2 = new State();
		s2.setChain(c1);
		s2.setRunsOn(targets);
		s2.setRepresents(ce);
		s2.setX(300);
		s2.setY(200);

		return c1;
	}

	public static PlaceGroup buildDefaultLocalNetwork(Application a) {
		// Execution node (the local sever)
		String hostname;
		try {
			hostname = InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException e) {
			hostname = "localhost";
		}
		ExecutionNode n1 = new ExecutionNode();
		n1.setDns(hostname);
		n1.setOspassword("");
		n1.setqPort(1789);
		n1.setX(100);
		n1.setY(100);
		a.addNode(n1);

		// Place
		Place p1 = new Place();
		p1.setDescription("the local server");
		p1.setName(hostname);
		p1.setNode(n1);
		a.addPlace(p1);

		// Group with only this place
		PlaceGroup pg1 = new PlaceGroup();
		pg1.setDescription("the local server");
		pg1.setName(hostname);
		a.addGroup(pg1);
		pg1.addPlace(p1);

		return pg1;
	}

	public static ShellCommand buildNewActiveShell(Application a,
			String command, String name, String description,
			String... prmsandvalues) {
		ShellCommand sc1 = new ShellCommand();
		sc1.setCommand(command);
		sc1.setDescription(description);
		sc1.setName(name);
		a.addActiveElement(sc1);

		for (int i = 0; i < prmsandvalues.length / 2; i++) {
			Parameter pa1 = new Parameter();
			pa1.setDescription("param " + i + " of command " + name);
			pa1.setKey(prmsandvalues[i * 2]);
			pa1.setValue(prmsandvalues[i * 2 + 1]);
			a.addParameter(pa1);
			sc1.addParameter(pa1);
		}
		return sc1;
	}

	public static State buildNewState(Chain c1, PlaceGroup pg1,
			ActiveNodeBase target) {
		State s1 = new State();
		s1.setChain(c1);
		s1.setRunsOn(pg1);
		s1.setRepresents(target);
		s1.setX(100);
		s1.setY(100);

		return s1;
	}
}