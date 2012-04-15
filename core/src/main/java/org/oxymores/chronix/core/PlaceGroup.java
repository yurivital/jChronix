package org.oxymores.chronix.core;

import java.util.ArrayList;

public class PlaceGroup extends MetaObject {
	private static final long serialVersionUID = 4569641718657486177L;
	
	protected Application application;
	protected String name, description;
	protected ArrayList<Place> places;
	
	public PlaceGroup()
	{
		super();
		places = new ArrayList<Place>();
	}
	
	public Application getApplication() {
		return application;
	}

	public void setApplication(Application application) {
		this.application = application;
	}
	
	public ArrayList<Place> getPlaces() {
		return places;
	}
	
	public void addPlace(Place p){
		if (!places.contains(p))
		{
			places.add(p);
			p.addToGroup(this);
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
