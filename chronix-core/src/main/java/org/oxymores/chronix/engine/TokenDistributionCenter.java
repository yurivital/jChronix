/**
 * By Marc-Antoine Gouillart, 2012
 *
 * See the NOTICE file distributed with this work for
 * information regarding copyright ownership.
 * This file is licensed to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.oxymores.chronix.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;

import org.slf4j.Logger;
import org.joda.time.DateTime;
import org.oxymores.chronix.core.Application;
import org.oxymores.chronix.core.ExecutionNode;
import org.oxymores.chronix.core.Place;
import org.oxymores.chronix.core.Token;
import org.oxymores.chronix.core.transactional.TokenReservation;
import org.oxymores.chronix.engine.data.TokenRequest;
import org.oxymores.chronix.engine.data.TokenRequest.TokenRequestType;
import org.oxymores.chronix.engine.helpers.SenderHelpers;
import org.slf4j.LoggerFactory;
import org.sql2o.Connection;

class TokenDistributionCenter extends BaseListener implements Runnable
{
    private static final Logger log = LoggerFactory.getLogger(TokenDistributionCenter.class);

    private MessageProducer jmsProducer;

    private boolean running = true;
    private Semaphore mainLoop, localResource;
    private List<TokenReservation> shouldRenew;

    void startListening(Broker broker) throws JMSException
    {
        this.init(broker);
        log.debug(String.format("Initializing TokenDistributionCenter"));

        // Sync
        mainLoop = new Semaphore(0);
        localResource = new Semaphore(1);
        this.shouldRenew = new ArrayList<>();

        // Register current object as a listener on ORDER queue
        qName = String.format(Constants.Q_TOKEN, brokerName);
        this.subscribeTo(qName);
        this.jmsProducer = this.jmsSession.createProducer(null);

        // Start thread
        new Thread(this).start();
    }

    @Override
    void stopListening()
    {
        running = false;
        mainLoop.release();
        try
        {
            localResource.acquire();
        }
        catch (InterruptedException e)
        {
            // Don't care
            log.debug("interruption");
        }
        super.stopListening();
    }

    @Override
    public void onMessage(Message msg)
    {
        ObjectMessage omsg = (ObjectMessage) msg;
        TokenRequest request;
        try
        {
            Object o = omsg.getObject();
            if (o instanceof TokenRequest)
            {
                request = (TokenRequest) o;
            }
            else
            {
                log.warn("An object was received on the token queue but was not a token request or token request ack! Ignored.");
                jmsCommit();
                return;
            }
        }
        catch (JMSException e)
        {
            log.error("An error occurred during token reception. BAD. Message will stay in queue and will be analysed later", e);
            jmsRollback();
            return;
        }

        // Check.
        Application a = ctx.getApplication(request.applicationID);
        if (a == null)
        {
            log.warn("A token for an application that does not run locally was received. Ignored.");
            jmsCommit();
            return;
        }

        Token tk = a.getToken(request.tokenID);
        Place p = ctx.getEnvironment().getPlace(request.placeID);
        org.oxymores.chronix.core.State s = a.getState(request.stateID);
        ExecutionNode en = ctx.getEnvironment().getNode(request.requestingNodeID);

        log.debug(String.format("Received a %s token request type %s on %s for state %s (application %s) for node %s. Local: %s", request.type, tk.getName(), p.getName(),
                s.getRepresents().getName(), a.getName(), en.getBrokerName(), request.local));

        // Case 1: TDC proxy: local request.
        if (request.local && request.type == TokenRequestType.REQUEST)
        {
            // Should be stored locally, so as to refresh the request every 5 minutes
            TokenReservation tr = new TokenReservation();
            tr.setApplicationId(request.applicationID);
            tr.setGrantedOn(DateTime.now());
            tr.setLocalRenew(true);
            tr.setPlaceId(request.placeID);
            tr.setRenewedOn(tr.getGrantedOn());
            tr.setRequestedOn(tr.getGrantedOn());
            tr.setStateId(request.stateID);
            tr.setTokenId(request.tokenID);
            tr.setPending(true);
            tr.setRequestedBy(request.requestingNodeID);
            tr.setPipelineJobId(request.pipelineJobID);

            try (Connection conn = this.ctx.getTransacDataSource().beginTransaction())
            {
                tr.insertOrUpdate(conn);
                shouldRenew.add(tr);
            }

            // Request should be sent to the node responsible for the distribution of this token
            request.local = false;
            try
            {
                SenderHelpers.sendTokenRequest(request, ctx, jmsSession, jmsProducer, false);
            }
            catch (JMSException e)
            {
                log.error(e.getMessage(), e);
                jmsRollback();
                return;
            }
            request.local = true;
        }

        // Case 2: TDC proxy: local release
        if (request.local && request.type == TokenRequestType.RELEASE)
        {
            // Delete the local element
            TokenReservation toRemove = null;
            for (TokenReservation tr : shouldRenew)
            {
                if (tr.getStateId().equals(request.stateID) && tr.getPlaceId().equals(request.placeID))
                {
                    toRemove = tr;
                }
            }
            if (toRemove != null)
            {
                shouldRenew.remove(toRemove);
            }

            // Request should be sent to the node responsible for the distribution of this token
            request.local = false;
            try
            {
                SenderHelpers.sendTokenRequest(request, ctx, jmsSession, jmsProducer, false);
            }
            catch (JMSException e)
            {
                log.error(e.getMessage(), e);
                jmsRollback();
                return;
            }
            request.local = true;
        }

        // Case 3: TDC: request
        if (!request.local && request.type == TokenRequestType.REQUEST)
        {
            try (Connection conn = this.ctx.getTransacDataSource().beginTransaction())
            {
                log.debug(String.format("Analysing token request for PJ %s", request.pipelineJobID));
                processRequest(request, conn);
                conn.commit();
            }
        }

        // Case 4: TDC: release
        if (!request.local && request.type == TokenRequestType.RELEASE)
        {
            // Log
            log.info(String.format("A token %s that was granted on %s (application %s) to node %s on state %s is released", tk.getName(), p.getName(), a.getName(),
                    en.getBrokerName(), s.getRepresents().getName()));

            // Find the element
            try (Connection conn = this.ctx.getTransacDataSource().beginTransaction())
            {
                TokenReservation tr = getTR(request, conn);
                tr.delete(conn);
                conn.commit();
            }
        }

        // Case 5: TDC: RENEW
        if (!request.local && request.type == TokenRequestType.RENEW)
        {
            // Log
            log.debug(String.format("A token %s that was granted on %s (application %s) to node %s on state %s is renewed", tk.getName(), p.getName(), a.getName(),
                    en.getBrokerName(), s.getRepresents().getName()));

            try (Connection conn = this.ctx.getTransacDataSource().beginTransaction())
            {
                // Find the element
                TokenReservation tr = getTR(request, conn);

                // Renew it
                tr.setRenewedOn(DateTime.now());
                conn.commit();
            }
        }

        // Case 6: TDC proxy: AGREE
        if (!request.local && request.type == TokenRequestType.AGREE)
        {
            // Just forward it to the pipeline
            ObjectMessage response;
            try
            {
                response = jmsSession.createObjectMessage(request);
                String qNameDest = String.format(Constants.Q_PJ, a.getLocalNode().getComputingNode().getBrokerName());
                Destination d = jmsSession.createQueue(qNameDest);
                log.debug(String.format("A message will be sent to queue %s", qNameDest));
                jmsProducer.send(d, response);
            }
            catch (JMSException e)
            {
                log.error(e.getMessage(), e);
                jmsRollback();
                return;
            }
        }

        // Classic TDC run - purge, then analyse all pending requests
        try (Connection conn = this.ctx.getTransacDataSource().beginTransaction())
        {
            // TDC: Step 1: Purge granted requests that are too old
            if (!request.local)
            {
                // Note: we do not use a simple DELETE statement as we want to log the term of the reservation (should be very rare!)

                List<TokenReservation> q = conn.createQuery("SELECT * from TokenReservation q where q.renewedOn < :notRenewedSince AND q.pending = :pending").
                        addParameter("notRenewedSince", DateTime.now().minusMinutes(Constants.MAX_TOKEN_VALIDITY_MN).toDate()).
                        addParameter("pending", false).executeAndFetch(TokenReservation.class);
                for (TokenReservation tr : q)
                {
                    Application aa = ctx.getApplication(tr.getApplicationId());
                    org.oxymores.chronix.core.State ss = aa.getState(tr.getStateId());
                    Place pp = ctx.getEnvironment().getPlace(tr.getPlaceId());
                    ExecutionNode enn = ctx.getEnvironment().getNode(tr.getRequestedBy());
                    log.warn(String.format(
                            "A token that was granted on %s (application %s) to node %s on state %s will be revoked as the request was not renewed in the last 10 minutes",
                            pp.getName(), aa.getName(), enn.getBrokerName(), ss.getRepresents().getName()));

                    // Remove from database
                    tr.delete(conn);
                }
            }

            // TDC: Step 2: Now that the purge is done, analyse pending requests again - tokens may have freed
            if (!request.local)
            {
                List<TokenReservation> q = conn.createQuery("SELECT * from TokenReservation q where q.pending = :pending AND q.localRenew = :localRenew").
                        addParameter("pending", true).addParameter("localRenew", false).executeAndFetch(TokenReservation.class);
                for (TokenReservation tr : q)
                {
                    log.debug(String.format("Re-analysing token request for PJ %s", tr.getPipelineJobId()));
                    processRequest(tr, conn);
                }
            }
            conn.commit();
        }

        // The end: commit JMS
        jmsCommit();
        log.debug("Commit successful");
    }

    private TokenReservation
            getTR(TokenRequest request, Connection conn)
    {
        return conn.createQuery("SELECT * from TokenReservation q where q.pipelineJobId = :pjId AND q.localRenew = :local")
                .addParameter("pjId", request.pipelineJobID).addParameter("local", false).executeAndFetchFirst(TokenReservation.class);
    }

    private void processRequest(TokenRequest request, Connection conn)
    {
        // Get data
        Application a = ctx.getApplication(request.applicationID);
        Token tk = a.getToken(request.tokenID);
        Place p = ctx.getEnvironment().getPlace(request.placeID);
        org.oxymores.chronix.core.State s = a.getState(request.stateID);

        // Process
        processRequest(conn, a, tk, p, request.requestedAt, s, null, request.pipelineJobID, request.requestingNodeID);
    }

    private void processRequest(TokenReservation tr, Connection conn)
    {
        // Get data
        Application a = ctx.getApplication(tr.getApplicationId());
        Token tk = a.getToken(tr.getTokenId());
        Place p = ctx.getEnvironment().getPlace(tr.getPlaceId());
        org.oxymores.chronix.core.State s = a.getState(tr.getStateId());

        // Process
        processRequest(conn, a, tk, p, new DateTime(tr.getRequestedOn()), s, tr, tr.getPipelineJobId(), tr.getRequestedBy());
    }

    private void processRequest(Connection conn, Application a, Token tk, Place p, DateTime requestedOn, org.oxymores.chronix.core.State s,
            TokenReservation existing, UUID pipelineJobId, UUID requestingNodeId)
    {
        // Locate all the currently allocated tokens on this Token/Place
        int i;
        if (tk.isByPlace())
        {
            i = conn.createQuery("SELECT COUNT(1) from TokenReservation q where q.tokenId = :tokenId AND "
                    + "q.renewedOn > :ro AND q.placeId = :placeId AND q.pending = :pending AND q.localRenew = :localRenew").
                    addParameter("tokenId", tk.getId()).
                    addParameter("ro", DateTime.now().minusMinutes(Constants.MAX_TOKEN_VALIDITY_MN).toDate()).
                    addParameter("placeId", p.getId()).
                    addParameter("pending", false).
                    addParameter("localRenew", false).
                    executeScalar(Integer.class);
        }
        else
        {
            i = conn.createQuery("SELECT COUNT(1) from TokenReservation q where q.tokenId = :tokenId AND "
                    + "q.renewedOn > :ro AND q.pending = :pending AND q.localRenew = :localRenew").
                    addParameter("tokenId", tk.getId()).
                    addParameter("ro", DateTime.now().minusMinutes(Constants.MAX_TOKEN_VALIDITY_MN).toDate()).
                    addParameter("pending", false).
                    addParameter("localRenew", false).
                    executeScalar(Integer.class);
        }

        if (i >= tk.getCount())
        {
            // No available token
            log.warn(String.format("A token was requested but there are none available. (max is %s, allocated is %s)", tk.getCount(), i));

            // Store the request if not done already
            if (existing == null)
            {
                TokenReservation trs = new TokenReservation();
                trs.setGrantedOn(null);
                trs.setLocalRenew(false);
                // PENDING means not given yet
                trs.setPending(true);
                trs.setPlaceId(p.getId());
                trs.setRenewedOn(null);
                trs.setRequestedOn(requestedOn);
                trs.setStateId(s.getId());
                trs.setTokenId(tk.getId());
                trs.setPipelineJobId(pipelineJobId);
                trs.setApplicationId(a.getId());
                trs.setRequestedBy(requestingNodeId);

                trs.insertOrUpdate(conn);
            }
            else
            {
                existing.setRenewedOn(DateTime.now());
                existing.insertOrUpdate(conn);
            }

            // Don't send an answer to the caller.
        }
        else
        {
            // Available token
            log.debug(String.format("A token was requested and one can be issued (%s taken out of %s)", i, tk.getCount()));

            TokenRequest answer;
            if (existing == null)
            {
                TokenReservation trs = new TokenReservation();
                trs.setGrantedOn(DateTime.now());
                trs.setLocalRenew(false);
                trs.setPending(false);
                trs.setPlaceId(p.getId());
                trs.setRenewedOn(trs.getGrantedOn());
                trs.setRequestedOn(requestedOn);
                trs.setStateId(s.getId());
                trs.setTokenId(tk.getId());
                trs.setPipelineJobId(pipelineJobId);
                trs.setApplicationId(a.getId());
                trs.setRequestedBy(requestingNodeId);

                trs.insertOrUpdate(conn);
                answer = trs.getAgreeRequest();
            }
            else
            {
                existing.setPending(false);
                existing.setGrantedOn(DateTime.now());
                existing.setRenewedOn(existing.getGrantedOn());
                existing.insertOrUpdate(conn);
                answer = existing.getAgreeRequest();
            }

            // Send an answer
            ObjectMessage response;
            try
            {
                response = jmsSession.createObjectMessage(answer);
                Destination d = jmsSession.createQueue(String.format(Constants.Q_TOKEN, p.getNode().getComputingNode().getBrokerName()));
                jmsProducer.send(d, response);
            }
            catch (JMSException e)
            {
                log.error(e.getMessage(), e);
                jmsRollback();
            }
        }
    }

    @Override
    public void run()
    {
        do
        {
            DateTime now = DateTime.now();

            // Sync
            try
            {
                localResource.acquire();
            }
            catch (InterruptedException e1)
            {
                // Don't care here, we can loop as we want.
                log.debug("interrupted, no worries");
            }

            // Renew token location for all boring
            for (TokenReservation tr : shouldRenew)
            {
                if (now.minusMinutes(Constants.TOKEN_RENEWAL_MN).compareTo(new DateTime(tr.getRenewedOn())) <= 0)
                {
                    try
                    {
                        log.debug("Sending a renewal request for token state");
                        SenderHelpers.sendTokenRequest(tr.getRenewalRequest(), ctx, jmsSession, jmsProducer, false);
                    }
                    catch (JMSException e)
                    {
                        log.error("Could not send a token renewal request. Will retry soon.", e);
                    }
                }
            }

            jmsCommit();

            // Sync
            localResource.release();

            try
            {
                // Loop every minute or so. No need to be precise here.
                if (mainLoop.tryAcquire(Constants.TOKEN_AUTO_RENEWAL_LOOP_PERIOD_S, TimeUnit.SECONDS))
                {
                    log.trace("TokenDistributionCenter will loop because engine is stopping");
                }
            }
            catch (InterruptedException e)
            {
                // Once again, who cares?
                log.debug("interrupted");
            }
        } while (running);
    }
}
