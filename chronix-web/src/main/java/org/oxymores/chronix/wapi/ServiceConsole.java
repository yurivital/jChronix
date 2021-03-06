package org.oxymores.chronix.wapi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.FilenameUtils;

import org.slf4j.Logger;
import org.joda.time.DateTime;
import org.oxymores.chronix.core.Application;
import org.oxymores.chronix.core.Place;
import org.oxymores.chronix.core.State;
import org.oxymores.chronix.core.timedata.RunLog;
import org.oxymores.chronix.dto.DTORunLog;
import org.oxymores.chronix.dto.ResOrder;
import org.oxymores.chronix.engine.helpers.SenderHelpers;
import org.oxymores.chronix.exceptions.ChronixException;
import org.slf4j.LoggerFactory;
import org.sql2o.Connection;
import org.sql2o.Query;

@Path("/live")
public class ServiceConsole
{
    private static final Logger log = LoggerFactory.getLogger(ServiceConsole.class);

    private RestApplication restApp;

    private @Context
    HttpServletResponse response;

    ServiceConsole(RestApplication a)
    {
        this.restApp = a;
    }

    @POST
    @Path("log")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public HistoryQuery getLog(HistoryQuery q)
    {
        log.debug("getLog POST was called");

        if (q.getMarkedForRunAfter() == null)
        {
            q.setMarkedForRunAfter(DateTime.now().minusDays(1).toDate());
        }
        if (q.getMarkedForRunBefore() == null)
        {
            q.setMarkedForRunBefore(DateTime.now().plusDays(1).toDate());
        }

        // TODO: direct to DTO attempt!
        try (Connection conn = restApp.getContext().getHistoryDataSource().open())
        {
            String sort = "";
            if (q.getSorts().size() > 0)
            {
                sort = " ORDER BY ";
                for (HistoryQuery.SortSpec s : q.getSorts())
                {
                    sort += " r." + s.col.getCoreLogField() + " " + s.order.name() + ",";
                }
                sort = sort.substring(0, sort.length() - 1);
            }

            String pagination = "";
            if (q.getPageSize() != null)
            {
                pagination += " LIMIT " + q.getPageSize();
            }
            if (q.getStartLine() != null)
            {
                pagination += " OFFSET " + q.getStartLine();
            }

            Query qu = conn.createQuery("SELECT * FROM RunLog r WHERE r.visible = 1 AND r.markedForUnAt >= :markedAfter AND r.markedForUnAt <= :markedBefore "
                    + sort + pagination).addParameter("markedAfter", q.getMarkedForRunAfter()).addParameter("markedBefore", q.getMarkedForRunBefore());

            List<DTORunLog> res = new ArrayList<>();
            for (RunLog rl : qu.executeAndFetch(RunLog.class))
            {
                res.add(CoreToDto.getDTORunLog(rl));
            }
            q.setRes(res);
            q.setTotalLogs((long) conn.createQuery("SELECT COUNT(1) FROM RunLog").executeScalar(Long.class));
        }

        log.debug("End of call to getLog - returning " + q.getRes().size() + " logs out of a total of " + q.getTotalLogs());
        return q;
    }

    @GET
    @Path("/shortlog/{id}")
    @Produces("text/plain")
    public String getShortLog(@PathParam("id") UUID id)
    {
        log.debug("Service getShortLog was called for ID " + id.toString());
        String res;

        try (Connection conn = this.restApp.getContext().getHistoryDataSource().open())
        {
            res = conn.createQuery("SELECT shortLog FROM RunLog WHERE id=:id").addParameter("id", id).executeScalar(String.class);
        }

        if (res == null)
        {
            log.debug("Service getShortLog has ended without finding the log");
            return "notfound";
        }
        else
        {
            log.debug("Service getShortLog has ended - the log was found");
            return res;
        }
    }

    @GET
    @Path("/order/forceok/{launchId}")
    @Produces("application/json")
    public ResOrder orderForceOK(@PathParam("launchId") String launchId)
    {
        log.debug("Service orderForceOK was called");
        try (Connection conn = restApp.getContext().getHistoryDataSource().open())
        {
            RunLog rl = conn.createQuery("SELECT * FROM RunLog WHERE id=:id").addParameter("id", launchId).executeAndFetchFirst(RunLog.class);
            SenderHelpers.sendOrderForceOk(rl.getApplicationId(), rl.getId(), rl.getExecutionNodeId(), restApp.getContext());
        }
        catch (ChronixException e)
        {
            log.debug("End of call to orderForceOK - failure");
            return new ResOrder("ForceOK", false, e.toString());
        }
        log.debug("End of call to orderForceOK - success");
        return new ResOrder("ForceOK", true, "The order was sent successfuly");
    }

    @GET
    @Path("/logfile/{launchId}")
    @Produces("text/plain; charset=utf-8")
    public File getLogFile(@PathParam("launchId") String launchId)
    {
        String path;
        try (Connection conn = restApp.getContext().getHistoryDataSource().open())
        {
            path = conn.createQuery("SELECT logPath FROM RunLog WHERE id=:id").addParameter("id", launchId).executeScalar(String.class);
        }

        log.debug("Log file was required at {}", path);
        response.setHeader("Content-Disposition", "attachment; filename=" + FilenameUtils.getName(path));
        File f = new File(path);
        return f;
    }

    @GET
    @Path("/order/launch/{insidePlan}/{appId}/{stateId}/{placeId}")
    @Produces("application/json")
    public ResOrder orderLaunch(@PathParam("appId") UUID appId, @PathParam("stateId") UUID stateId,
            @PathParam("placeId") UUID placeId, @PathParam("insidePlan") Boolean insidePlan)
    {
        log.info("Calling orderLaunchOutOfPlan - with full params");
        try
        {
            Application a = restApp.getContext().getApplication(appId);
            Place p = this.restApp.getContext().getEnvironment().getPlace(placeId);
            State s = a.getState(stateId);
            if (insidePlan)
            {
                try (Connection o = restApp.getContext().getTransacDataSource().beginTransaction())
                {
                    SenderHelpers.runStateInsidePlan(s, p, restApp.getContext(), o);
                }
            }
            else
            {
                SenderHelpers.runStateAlone(s, p, restApp.getContext());
            }
        }
        catch (Exception e)
        {
            log.warn("could not create an OOPL", e);
            return new ResOrder("LaunchOutOfPlan", false, e.getMessage());
        }
        return new ResOrder("LaunchOutOfPlan", true, "The order was sent successfuly");
    }

    @GET
    @Path("/order/launch/outofplan/duplicatelaunch/{launchId}")
    @Produces("application/json")
    public ResOrder duplicateEndedLaunchOutOfPlan(@PathParam("launchId") UUID launchId)
    {
        log.debug("Service duplicateEndedLaunchOutOfPlan was called");
        RunLog rl;
        try (Connection conn = restApp.getContext().getHistoryDataSource().open())
        {
            rl = conn.createQuery("SELECT * FROM RunLog WHERE id=:id").addParameter("id", launchId).executeAndFetchFirst(RunLog.class);
        }
        return orderLaunch(rl.getApplicationId(), rl.getStateId(), rl.getPlaceId(), false);
    }
}
