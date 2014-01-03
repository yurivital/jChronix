package org.oxymores.chronix.wapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.oxymores.chronix.core.ActiveNodeBase;
import org.oxymores.chronix.core.Application;
import org.oxymores.chronix.core.Calendar;
import org.oxymores.chronix.core.CalendarDay;
import org.oxymores.chronix.core.Chain;
import org.oxymores.chronix.core.ConfigurableBase;
import org.oxymores.chronix.core.ExecutionNode;
import org.oxymores.chronix.core.NodeConnectionMethod;
import org.oxymores.chronix.core.NodeLink;
import org.oxymores.chronix.core.Place;
import org.oxymores.chronix.core.PlaceGroup;
import org.oxymores.chronix.core.State;
import org.oxymores.chronix.core.Transition;
import org.oxymores.chronix.core.active.And;
import org.oxymores.chronix.core.active.ChainEnd;
import org.oxymores.chronix.core.active.ChainStart;
import org.oxymores.chronix.core.active.Clock;
import org.oxymores.chronix.core.active.ClockRRule;
import org.oxymores.chronix.core.active.External;
import org.oxymores.chronix.core.active.NextOccurrence;
import org.oxymores.chronix.core.active.Or;
import org.oxymores.chronix.core.active.ShellCommand;
import org.oxymores.chronix.core.timedata.RunLog;
import org.oxymores.chronix.dto.DTOApplication;
import org.oxymores.chronix.dto.DTOCalendar;
import org.oxymores.chronix.dto.DTOCalendarDay;
import org.oxymores.chronix.dto.DTOChain;
import org.oxymores.chronix.dto.DTOClock;
import org.oxymores.chronix.dto.DTOExecutionNode;
import org.oxymores.chronix.dto.DTOExternal;
import org.oxymores.chronix.dto.DTONextOccurrence;
import org.oxymores.chronix.dto.DTOParameter;
import org.oxymores.chronix.dto.DTOPlace;
import org.oxymores.chronix.dto.DTOPlaceGroup;
import org.oxymores.chronix.dto.DTORRule;
import org.oxymores.chronix.dto.DTORunLog;
import org.oxymores.chronix.dto.DTOShellCommand;
import org.oxymores.chronix.dto.DTOState;
import org.oxymores.chronix.dto.DTOTransition;

public class CoreToDto
{

    public static DTOApplication getApplication(Application a)
    {
        DTOApplication res = new DTOApplication();

        res.id = a.getId().toString();
        res.name = a.getName();
        res.description = a.getDescription();

        res.chains = new ArrayList<DTOChain>();
        res.shells = new ArrayList<DTOShellCommand>();
        res.places = new ArrayList<DTOPlace>();
        res.groups = new ArrayList<DTOPlaceGroup>();
        res.parameters = new ArrayList<DTOParameter>();
        res.rrules = new ArrayList<DTORRule>();
        res.clocks = new ArrayList<DTOClock>();
        res.externals = new ArrayList<DTOExternal>();
        res.calendars = new ArrayList<DTOCalendar>();
        res.calnexts = new ArrayList<DTONextOccurrence>();

        // Unique elements
        for (ConfigurableBase nb : a.getActiveElements().values())
        {
            if (nb instanceof ChainStart)
            {
                res.setStartId(nb.getId().toString());
                break;
            }
        }
        for (ConfigurableBase nb : a.getActiveElements().values())
        {
            if (nb instanceof ChainEnd)
            {
                res.setEndId(nb.getId().toString());
                break;
            }
        }
        for (ConfigurableBase nb : a.getActiveElements().values())
        {
            if (nb instanceof Or)
            {
                res.setOrId(nb.getId().toString());
                break;
            }
        }
        for (ConfigurableBase nb : a.getActiveElements().values())
        {
            if (nb instanceof And)
            {
                res.setAndId(nb.getId().toString());
                break;
            }
        }

        // Network
        res.nodes = getNetwork(a);

        for (Place p : a.getPlacesList())
            res.places.add(getPlace(p));

        Comparator<PlaceGroup> comparator_pg = new Comparator<PlaceGroup>()
        {
            public int compare(PlaceGroup c1, PlaceGroup c2)
            {
                return c1.getName().compareToIgnoreCase(c2.getName());
            }
        };
        List<PlaceGroup> pgs = a.getGroupsList();
        Collections.sort(pgs, comparator_pg);
        for (PlaceGroup pg : pgs)
            res.groups.add(getPlaceGroup(pg));

        // Calendars
        for (Calendar c : a.getCalendars())
            res.calendars.add(getCalendar(c));

        // Clocks
        for (ClockRRule r : a.getRRulesList())
            res.rrules.add(getRRule(r));

        Comparator<ActiveNodeBase> comparator_act = new Comparator<ActiveNodeBase>()
        {
            public int compare(ActiveNodeBase c1, ActiveNodeBase c2)
            {
                return c1.getName().compareToIgnoreCase(c2.getName());
            }
        };

        // All the active elements!
        List<ActiveNodeBase> active = new ArrayList<ActiveNodeBase>(a.getActiveElements().values());
        Collections.sort(active, comparator_act);
        for (ActiveNodeBase o : active)
        {
            if (o instanceof Chain)
            {
                Chain c = (Chain) o;
                res.chains.add(getChain(c));
            }

            if (o instanceof Clock)
            {
                Clock c = (Clock) o;
                res.clocks.add(getClock(c));
            }

            if (o instanceof ShellCommand)
            {
                ShellCommand s = (ShellCommand) o;
                DTOShellCommand d = new DTOShellCommand();
                d.id = s.getId().toString();
                d.command = s.getCommand();
                d.name = s.getName();
                d.description = s.getDescription();
                res.shells.add(d);
            }

            if (o instanceof External)
            {
                External e = (External) o;
                DTOExternal d = new DTOExternal();
                d.id = e.getId().toString();
                d.accountRestriction = e.getAccountRestriction();
                d.machineRestriction = e.getMachineRestriction();
                d.regularExpression = e.getRegularExpression();
                d.name = e.getName();
                d.description = e.getDescription();
                res.externals.add(d);
            }

            if (o instanceof NextOccurrence)
            {
                NextOccurrence e = (NextOccurrence) o;
                DTONextOccurrence d = new DTONextOccurrence();
                d.id = e.getId().toString();
                d.name = e.getName();
                d.description = e.getDescription();
                d.calendarId = e.getUpdatedCalendar().getId().toString();
                res.calnexts.add(d);
            }
        }

        return res;
    }

    public static DTOChain getChain(Chain c)
    {
        DTOChain res = new DTOChain();
        res.id = c.getId().toString();
        res.name = c.getName();
        res.description = c.getDescription();
        res.states = new ArrayList<DTOState>();
        res.transitions = new ArrayList<DTOTransition>();

        for (State s : c.getStates())
        {
            DTOState t = new DTOState();
            t.setParallel(s.getParallel());
            t.setId(s.getId().toString());
            t.setX(s.getX());
            t.setY(s.getY());
            t.setLabel(s.getRepresents().getName());
            t.setRepresentsId(s.getRepresents().getId().toString());
            if (s.getCalendar() != null)
            {
                t.setCalendarId(s.getCalendar().getId().toString());
                t.setCalendarShift(s.getCalendarShift());
            }
            try
            {
                t.setRunsOnName(s.getRunsOn().getName());
                t.setRunsOnId(s.getRunsOn().getId().toString());
            }
            catch (Exception e)
            {
            }
            if (s.getRepresents() instanceof ChainStart)
            {
                t.setCanReceiveLink(false);
                t.setStart(true);
            }
            if (s.getRepresents() instanceof ChainEnd)
            {
                t.setCanEmitLinks(false);
                t.setEnd(true);
            }
            if (s.getRepresents() instanceof ChainEnd || s.getRepresents() instanceof ChainStart)
                t.setCanBeRemoved(false);
            if (s.getRepresents() instanceof And || s.getRepresents() instanceof Or)
                t.setCanReceiveMultipleLinks(true);
            if (s.getRepresents() instanceof And)
                t.setAnd(true);
            if (s.getRepresents() instanceof Or)
                t.setOr(true);

            res.states.add(t);
        }

        for (Transition o : c.getTransitions())
        {
            DTOTransition d = new DTOTransition();
            d.id = o.getId().toString();
            d.from = o.getStateFrom().getId().toString();
            d.to = o.getStateTo().getId().toString();
            d.guard1 = o.getGuard1();
            d.guard2 = o.getGuard2();
            d.guard3 = o.getGuard3();
            d.guard4 = (o.getGuard4() == null ? "" : o.getGuard4().toString());
            d.calendarAware = o.isCalendarAware();
            d.calendarShift = o.getCalendarShift();

            res.transitions.add(d);
        }

        return res;
    }

    public static ArrayList<DTOExecutionNode> getNetwork(Application a)
    {
        ArrayList<DTOExecutionNode> res = new ArrayList<DTOExecutionNode>();
        for (ExecutionNode en : a.getNodes().values())
            res.add(getExecutionNode(en));

        return res;
    }

    public static DTOExecutionNode getExecutionNode(ExecutionNode en)
    {
        DTOExecutionNode res = new DTOExecutionNode();
        res.id = en.getId().toString();
        res.certFilePath = en.getSshKeyFilePath();
        res.dns = en.getDns();
        res.isConsole = en.isConsole();
        res.jmxPort = en.getJmxPort();
        res.ospassword = en.getOspassword();
        res.osusername = en.getOsusername();
        res.qPort = en.getqPort();
        res.remoteExecPort = en.getRemoteExecPort();
        res.wsPort = en.getWsPort();
        res.x = en.getX();
        res.y = en.getY();

        res.fromRCTRL = new ArrayList<String>();
        res.fromTCP = new ArrayList<String>();
        res.toRCTRL = new ArrayList<String>();
        res.toTCP = new ArrayList<String>();
        res.places = new ArrayList<String>();

        for (NodeLink nl : en.getCanSendTo())
        {
            if (nl.getMethod() == NodeConnectionMethod.RCTRL || nl.getMethod() == NodeConnectionMethod.TCP)
                res.toTCP.add(nl.getNodeTo().getId().toString());
        }
        for (NodeLink nl : en.getCanReceiveFrom())
        {
            if (nl.getMethod() == NodeConnectionMethod.RCTRL || nl.getMethod() == NodeConnectionMethod.TCP)
                res.fromTCP.add(nl.getNodeFrom().getId().toString());
        }
        for (Place p : en.getPlacesHosted())
        {
            res.places.add(p.getId().toString());
        }

        res.setSimpleRunner(en.isHosted());

        return res;
    }

    public static DTOPlace getPlace(Place p)
    {
        DTOPlace res = new DTOPlace();
        res.description = p.getDescription();
        res.id = p.getId().toString();
        res.name = p.getName();
        res.nodeid = p.getNode().getId().toString();
        res.prop1 = p.getProperty1();
        res.prop2 = p.getProperty2();
        res.prop3 = p.getProperty3();
        res.prop4 = p.getProperty4();

        res.memberOf = new ArrayList<String>();
        for (PlaceGroup pg : p.getMemberOfGroups())
        {
            res.memberOf.add(pg.getId().toString());
        }

        return res;
    }

    public static DTOPlaceGroup getPlaceGroup(PlaceGroup g)
    {
        DTOPlaceGroup res = new DTOPlaceGroup();
        res.description = g.getDescription();
        res.id = g.getId().toString();
        res.name = g.getName();

        res.places = new ArrayList<String>();
        for (Place p : g.getPlaces())
        {
            res.places.add(p.getId().toString());
        }

        return res;
    }

    public static DTOClock getClock(Clock c)
    {
        DTOClock res = new DTOClock();
        res.description = c.getDescription();
        res.id = c.getId().toString();
        res.name = c.getName();
        res.nextOccurrences = new ArrayList<Date>();
        res.rulesADD = new ArrayList<String>();
        res.rulesEXC = new ArrayList<String>();

        for (ClockRRule r : c.getRulesADD())
            res.rulesADD.add(r.getId().toString());
        for (ClockRRule r : c.getRulesEXC())
            res.rulesEXC.add(r.getId().toString());

        return res;
    }

    public static DTORRule getRRule(ClockRRule r)
    {
        DTORRule res = new DTORRule();

        // Identification
        res.id = r.getId().toString();
        res.name = r.getName();
        res.description = r.getDescription();

        // Period
        res.period = r.getPeriod();
        res.interval = r.getINTERVAL();

        // ByDay
        for (String d : r.getBYDAY().split(","))
        {
            if (d.equals("MO"))
                res.bd_01 = true;
            else if (d.equals("TU"))
                res.bd_02 = true;
            else if (d.equals("WE"))
                res.bd_03 = true;
            else if (d.equals("TH"))
                res.bd_04 = true;
            else if (d.equals("FR"))
                res.bd_05 = true;
            else if (d.equals("SA"))
                res.bd_06 = true;
            else if (d.equals("SU"))
                res.bd_07 = true;
        }
        // ByMonthDay
        for (String d : r.getBYMONTHDAY().split(","))
        {
            if (d.equals("01"))
                res.bmd_01 = true;
            else if (d.equals("-01"))
                res.bmdn_01 = true;
            else if (d.equals("02"))
                res.bmd_02 = true;
            else if (d.equals("-02"))
                res.bmdn_02 = true;
            else if (d.equals("03"))
                res.bmd_03 = true;
            else if (d.equals("-03"))
                res.bmdn_03 = true;
            else if (d.equals("04"))
                res.bmd_04 = true;
            else if (d.equals("-04"))
                res.bmdn_04 = true;
            else if (d.equals("05"))
                res.bmd_05 = true;
            else if (d.equals("-05"))
                res.bmdn_05 = true;
            else if (d.equals("06"))
                res.bmd_06 = true;
            else if (d.equals("-06"))
                res.bmdn_06 = true;
            else if (d.equals("07"))
                res.bmd_07 = true;
            else if (d.equals("-07"))
                res.bmdn_07 = true;
            else if (d.equals("08"))
                res.bmd_08 = true;
            else if (d.equals("-08"))
                res.bmdn_08 = true;
            else if (d.equals("09"))
                res.bmd_09 = true;
            else if (d.equals("-09"))
                res.bmdn_09 = true;
            else if (d.equals("10"))
                res.bmd_10 = true;
            else if (d.equals("-10"))
                res.bmdn_10 = true;
            else if (d.equals("11"))
                res.bmd_11 = true;
            else if (d.equals("-11"))
                res.bmdn_11 = true;
            else if (d.equals("12"))
                res.bmd_12 = true;
            else if (d.equals("-12"))
                res.bmdn_12 = true;
            else if (d.equals("13"))
                res.bmd_13 = true;
            else if (d.equals("-13"))
                res.bmdn_13 = true;
            else if (d.equals("14"))
                res.bmd_14 = true;
            else if (d.equals("-14"))
                res.bmdn_14 = true;
            else if (d.equals("15"))
                res.bmd_15 = true;
            else if (d.equals("-15"))
                res.bmdn_15 = true;
            else if (d.equals("16"))
                res.bmd_16 = true;
            else if (d.equals("-16"))
                res.bmdn_16 = true;
            else if (d.equals("17"))
                res.bmd_17 = true;
            else if (d.equals("-17"))
                res.bmdn_17 = true;
            else if (d.equals("18"))
                res.bmd_18 = true;
            else if (d.equals("-18"))
                res.bmdn_18 = true;
            else if (d.equals("19"))
                res.bmd_19 = true;
            else if (d.equals("-19"))
                res.bmdn_19 = true;
            else if (d.equals("20"))
                res.bmd_20 = true;
            else if (d.equals("-20"))
                res.bmdn_20 = true;
            else if (d.equals("21"))
                res.bmd_21 = true;
            else if (d.equals("-21"))
                res.bmdn_21 = true;
            else if (d.equals("22"))
                res.bmd_22 = true;
            else if (d.equals("-22"))
                res.bmdn_22 = true;
            else if (d.equals("23"))
                res.bmd_23 = true;
            else if (d.equals("-23"))
                res.bmdn_23 = true;
            else if (d.equals("24"))
                res.bmd_24 = true;
            else if (d.equals("-24"))
                res.bmdn_24 = true;
            else if (d.equals("25"))
                res.bmd_25 = true;
            else if (d.equals("-25"))
                res.bmdn_25 = true;
            else if (d.equals("26"))
                res.bmd_26 = true;
            else if (d.equals("-26"))
                res.bmdn_26 = true;
            else if (d.equals("27"))
                res.bmd_27 = true;
            else if (d.equals("-27"))
                res.bmdn_27 = true;
            else if (d.equals("28"))
                res.bmd_28 = true;
            else if (d.equals("-28"))
                res.bmdn_29 = true;
            else if (d.equals("29"))
                res.bmd_29 = true;
            else if (d.equals("-29"))
                res.bmdn_29 = true;
            else if (d.equals("30"))
                res.bmd_30 = true;
            else if (d.equals("-30"))
                res.bmdn_30 = true;
            else if (d.equals("31"))
                res.bmd_31 = true;
            else if (d.equals("-31"))
                res.bmdn_31 = true;
        }
        // ByMonth
        for (String d : r.getBYMONTH().split(","))
        {
            if (d.equals("01"))
                res.bm_01 = true;
            else if (d.equals("02"))
                res.bm_02 = true;
            else if (d.equals("03"))
                res.bm_03 = true;
            else if (d.equals("04"))
                res.bm_04 = true;
            else if (d.equals("05"))
                res.bm_05 = true;
            else if (d.equals("06"))
                res.bm_06 = true;
            else if (d.equals("07"))
                res.bm_07 = true;
            else if (d.equals("08"))
                res.bm_08 = true;
            else if (d.equals("09"))
                res.bm_09 = true;
            else if (d.equals("10"))
                res.bm_10 = true;
            else if (d.equals("11"))
                res.bm_11 = true;
            else if (d.equals("12"))
                res.bm_12 = true;
        }
        // ByHour
        for (String d : r.getBYHOUR().split(","))
        {
            if (d.equals("00"))
                res.bh_00 = true;
            else if (d.equals("01"))
                res.bh_01 = true;
            else if (d.equals("02"))
                res.bh_02 = true;
            else if (d.equals("03"))
                res.bh_03 = true;
            else if (d.equals("04"))
                res.bh_04 = true;
            else if (d.equals("05"))
                res.bh_05 = true;
            else if (d.equals("06"))
                res.bh_06 = true;
            else if (d.equals("07"))
                res.bh_07 = true;
            else if (d.equals("08"))
                res.bh_08 = true;
            else if (d.equals("09"))
                res.bh_09 = true;
            else if (d.equals("10"))
                res.bh_10 = true;
            else if (d.equals("11"))
                res.bh_11 = true;
            else if (d.equals("12"))
                res.bh_12 = true;
            else if (d.equals("13"))
                res.bh_13 = true;
            else if (d.equals("14"))
                res.bh_14 = true;
            else if (d.equals("15"))
                res.bh_15 = true;
            else if (d.equals("16"))
                res.bh_16 = true;
            else if (d.equals("17"))
                res.bh_17 = true;
            else if (d.equals("18"))
                res.bh_18 = true;
            else if (d.equals("19"))
                res.bh_19 = true;
            else if (d.equals("20"))
                res.bh_20 = true;
            else if (d.equals("21"))
                res.bh_21 = true;
            else if (d.equals("22"))
                res.bh_22 = true;
            else if (d.equals("23"))
                res.bh_23 = true;
        }
        return res;
    }

    public static DTORunLog getDTORunLog(RunLog rl)
    {
        DTORunLog res = new DTORunLog();
        res.setId(rl.getId());
        res.setActiveNodeName(rl.getActiveNodeName());
        res.setApplicationName(rl.getApplicationName());
        res.setBeganRunningAt(rl.getBeganRunningAt());
        res.setCalendarName(rl.getCalendarName());
        res.setCalendarOccurrence(rl.getCalendarOccurrence());
        res.setChainLev1Name(rl.getChainLev1Name());
        res.setChainName(rl.getChainName());
        res.setDataIn(rl.getDataIn());
        res.setDataOut(rl.getDataOut());
        res.setDns(rl.getDns());
        res.setEnteredPipeAt(rl.getEnteredPipeAt());
        res.setExecutionNodeName(rl.getExecutionNodeName());
        res.setLastKnownStatus(rl.getLastKnownStatus());
        res.setMarkedForUnAt(rl.getMarkedForUnAt());
        res.setOsAccount(rl.getOsAccount());
        res.setPlaceName(rl.getPlaceName());
        res.setResultCode(rl.getResultCode());
        res.setSequence(rl.getSequence());
        res.setStoppedRunningAt(rl.getStoppedRunningAt());
        res.setWhatWasRun(rl.getWhatWasRun());

        return res;
    }

    public static DTOCalendar getCalendar(Calendar c)
    {
        DTOCalendar res = new DTOCalendar();
        res.setAlertThreshold(c.getAlertThreshold());
        res.setDescription(c.getDescription());
        res.setId(c.getId().toString());
        res.setName(c.getName());
        res.setDays(new ArrayList<DTOCalendarDay>());

        for (CalendarDay day : c.getDays())
        {
            DTOCalendarDay cd = new DTOCalendarDay();
            cd.setId(day.getId().toString());
            cd.setSeq(day.getValue());
            res.getDays().add(cd);
        }

        return res;
    }
}
