package org.swisspush.vertx.cluster;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class ClusterWatchdog extends AbstractVerticle {

    private static final String BROADCAST = "clusterhealthcheck";
    private static final String RESPONSE_ADDRESS_PREFIX = "responseAddress-";
    private static final String RESPONSE_ADDRESS_KEY = "responseAddress";
    private static final String RESULT_ADDRESS_PREFIX = "resultAddress-";

    private static final int WATCHDOG_START_DELAY = 2000;
    private static final int TIME_TO_WAIT_FOR_RESPONSE = 2000;

    private final Logger log = LoggerFactory.getLogger(ClusterWatchdog.class);

    private EventBus eb;
    private String uniqueId;
    private int intervalInMillis;
    private boolean useInjectedClusterMembersCount = true;
    private int clusterMemberCount;
    private Map<String,List<JsonObject>> healthCheckResponses;
    private ClusterWatchdogHttpHandler clusterWatchdogHttpHandler;

    private final AtomicLong atomicClusterMemberCountRequired = new AtomicLong(0);
    private final AtomicLong atomicClusterMemberRespondersCount = new AtomicLong(0);

    @Override
    public void start(Promise<Void> startPromise) {

        eb = vertx.eventBus();
        JsonObject config = config();
        log.info("started with config: \n" +config.encodePrettily());

        // get the interval in seconds to execute the checks
        intervalInMillis = config.getInteger("intervalInSec", 30) * 1000;
        log.info("ClusterWatchdog interval in sec is: " + intervalInMillis / 1000);

        // get the clusterMembers injected over the config, if available
        int clusterMemberCountFromConfig = config.getInteger("clusterMemberCount", -1);
        if(clusterMemberCountFromConfig == -1) {
            useInjectedClusterMembersCount = false;
        } else {
            clusterMemberCount = clusterMemberCountFromConfig;
        }
        atomicClusterMemberRespondersCount.set(0);

        int resultQueueLength = config.getInteger("resultQueueLength", 100);
        log.info("ClusterWatchdog used resultQueueLength: " + resultQueueLength);
        int httport = config.getInteger("http.port", 7878);
        log.info("ClusterWatchdog used http port: " + httport);

        // initalize variables
        healthCheckResponses = new HashMap<>();
        clusterWatchdogHttpHandler = new ClusterWatchdogHttpHandler(vertx, log, resultQueueLength);

        // create a unique ID per verticle to identify it
        uniqueId = UUID.randomUUID().toString();
        log.info("ClusterWatchdog started cluster check verticle: " + uniqueId);

        // the handler for the broadcast event, reads the sender from the event and reply to him
        eb.consumer(BROADCAST, (Handler<Message<JsonObject>>) event -> {
            String responseAddress = event.body().getString(RESPONSE_ADDRESS_KEY);
            String timestamp = event.body().getString("timestamp");
            log.debug("got broadcast, i am: " + uniqueId + ", responseAddress is: " + responseAddress + " timestamp is: " + timestamp);

            // respond to the sender
            JsonObject responsePayload = new JsonObject();
            responsePayload.put("senderId", uniqueId);
            responsePayload.put("timestamp", timestamp);
            eb.send(responseAddress, responsePayload);
        });

        // the handler for the reply of the broadcast handler, adds the result to the healthCheckResponses
        eb.consumer(RESPONSE_ADDRESS_PREFIX + uniqueId, (Handler<Message<JsonObject>>) event -> {
            String senderId = event.body().getString("senderId");
            String timestamp = event.body().getString("timestamp");
            log.debug("ClusterWatchdog got response, i am: " + uniqueId + ", senderId is: " + senderId);
            healthCheckResponses.computeIfAbsent(timestamp, k -> new ArrayList<>());
            JsonObject response = new JsonObject();
            response.put("senderId", senderId);
            healthCheckResponses.get(timestamp).add(response);
        });

        // the handler to add the result from the other members
        eb.consumer(RESULT_ADDRESS_PREFIX + uniqueId, (Handler<Message<JsonObject>>) watchdogResultJsonObj ->
                clusterWatchdogHttpHandler.resultQueue.add(WatchdogResult.fromJson(watchdogResultJsonObj.body())));

        if(intervalInMillis == 0) {
            // wait until all verticles are up and running
            vertx.setTimer(WATCHDOG_START_DELAY, new ClusterCheckHandler());
        }

        if(intervalInMillis > 0) {
            // wait until all verticles are up and running
            vertx.setTimer(WATCHDOG_START_DELAY, event -> vertx.setPeriodic(intervalInMillis, new ClusterCheckHandler()));
        }

        vertx.createHttpServer().requestHandler(clusterWatchdogHttpHandler).listen(httport, result -> {
            if(result.succeeded()){
                startPromise.complete();
            } else {
                startPromise.fail(result.cause());
            }
        });
    }

    public void setMeterRegistry(MeterRegistry meterRegistry) {
        if(meterRegistry != null) {
            Gauge.builder("cluster.watchdog.members", atomicClusterMemberCountRequired, AtomicLong::get)
                    .description("Amount of members visible to the cluster").register(meterRegistry);
            Gauge.builder("cluster.watchdog.members.responded", atomicClusterMemberRespondersCount, AtomicLong::get)
                    .description("Amount of cluster members responded when accessed").register(meterRegistry);
        }
    }

    class ClusterCheckHandler implements Handler<Long> {

        public void handle(Long event) {
            JsonObject testpayload = new JsonObject();
            testpayload.put(RESPONSE_ADDRESS_KEY, RESPONSE_ADDRESS_PREFIX + uniqueId);
            log.debug("ClusterWatchdog send single broadcast healthcheck from: " + uniqueId);
            final String timestamp = String.valueOf(System.currentTimeMillis());
            testpayload.put("timestamp", timestamp);

            // if the cluster
            if(!useInjectedClusterMembersCount) {
                try {
                    clusterMemberCount = ClusterInformation.getMembers(log).size();
                } catch (MoreThanOneHazelcastInstanceException e) {
                    log.error("ClusterWatchdog got more than one hazelcast instance, we can only handle one hazelcast instance, we abort");
                    return;
                }
            }

            if(clusterMemberCount == 0) {
                log.info("ClusterWatchdog no cluster members found, no watchdog will run");
                return;
            }

            atomicClusterMemberCountRequired.set(clusterMemberCount);

            // publish the broadcast event which will us get the response of all the registered handlers
            eb.publish(BROADCAST, testpayload);

            // give the handlers 2sec to respond
            // log an error message in the case if the response counts don't match the cluster member amount
            vertx.setTimer(TIME_TO_WAIT_FOR_RESPONSE, event1 -> {
                List<JsonObject> responses =  healthCheckResponses.remove(timestamp);
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                WatchdogResult watchdogResult = new WatchdogResult();
                watchdogResult.broadcastTimestamp = timestamp;
                watchdogResult.time = time;
                watchdogResult.verticleId = uniqueId;
                watchdogResult.clusterMemberCount = clusterMemberCount;

                atomicClusterMemberRespondersCount.set(responses != null ? responses.size() : 0);

                if(responses == null) {
                    log.error("ClusterWatchdog found no responses for timestamp: " + timestamp);
                    watchdogResult.status = ClusterHealthStatus.INCONSISTENT;
                    watchdogResult.responders = null;
                    clusterWatchdogHttpHandler.resultQueue.add(watchdogResult);
                } else if(clusterMemberCount != responses.size()){
                    watchdogResult.status = ClusterHealthStatus.INCONSISTENT;
                    watchdogResult.setResponders(responses);
                    log.error("ClusterWatchdog known cluster members: " + clusterMemberCount + " responses: " + responses.size());
                    clusterWatchdogHttpHandler.resultQueue.add(watchdogResult);
                    // send the result to the other members to have consistency over the cluster in the results
                    sendResultToOtherMembers(watchdogResult);
                } else {
                    watchdogResult.status = ClusterHealthStatus.CONSISTENT;
                    watchdogResult.setResponders(responses);
                    log.debug("ClusterWatchdog all the cluster members (" + responses.size() + ") answered: " + responses.toString());
                    clusterWatchdogHttpHandler.resultQueue.add(watchdogResult);
                    // send the result to the other members to have consistency over the cluster in the results
                    sendResultToOtherMembers(watchdogResult);
                }
            });
        }

        private void sendResultToOtherMembers(WatchdogResult watchdogResult) {
            for(String member : watchdogResult.responders) {
                // only send it to the other members
                if(uniqueId.equals(member)) {
                    continue;
                }
                eb.send(RESULT_ADDRESS_PREFIX+member, watchdogResult.toJson());
            }
        }
    }
}
