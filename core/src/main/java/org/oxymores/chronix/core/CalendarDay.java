package org.oxymores.chronix.core;

import java.util.UUID;

public class CalendarDay extends ApplicationObject {

	private static final long serialVersionUID = -8296932253108182976L;

	protected String seq;

	protected Calendar calendar;

	public CalendarDay(String day, Calendar calendar) {
		super();
		id = UUID.randomUUID();
		this.calendar = calendar;
		calendar.addDay(this);
		this.seq = day;
	}

	public void setCalendar(Calendar c) {
		if (this.calendar == null || !this.calendar.getId().equals(c.getId()))
			c.addDay(this);
		this.calendar = c;
		this.application = c.application;
	}

	public String getValue() {
		return seq;
	}

}