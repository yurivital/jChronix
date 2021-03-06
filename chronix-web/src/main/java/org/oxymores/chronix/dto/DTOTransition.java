package org.oxymores.chronix.dto;

public class DTOTransition
{
    private String from, to;
    private Integer guard1;
    private String guard2, guard3;
    private String guard4;
    private String id;
    private boolean calendarAware;
    private Integer calendarShift;

    public String getFrom()
    {
        return from;
    }

    public void setFrom(String from)
    {
        this.from = from;
    }

    public String getTo()
    {
        return to;
    }

    public void setTo(String to)
    {
        this.to = to;
    }

    public Integer getGuard1()
    {
        return guard1;
    }

    public void setGuard1(Integer guard1)
    {
        this.guard1 = guard1;
    }

    public String getGuard2()
    {
        return guard2;
    }

    public void setGuard2(String guard2)
    {
        this.guard2 = guard2;
    }

    public String getGuard3()
    {
        return guard3;
    }

    public void setGuard3(String guard3)
    {
        this.guard3 = guard3;
    }

    public String getGuard4()
    {
        return guard4;
    }

    public void setGuard4(String guard4)
    {
        this.guard4 = guard4;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public boolean isCalendarAware()
    {
        return calendarAware;
    }

    public void setCalendarAware(boolean calendarAware)
    {
        this.calendarAware = calendarAware;
    }

    public Integer getCalendarShift()
    {
        return calendarShift;
    }

    public void setCalendarShift(Integer calendarShift)
    {
        this.calendarShift = calendarShift;
    }

}
