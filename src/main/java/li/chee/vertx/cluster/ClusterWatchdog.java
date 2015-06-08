package li.chee.vertx.cluster;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import java.util.*;

public class ClusterWatchdog extends Verticle {

    private static final String BROADCAST = "clusterhealthcheck";
    private static final String RESPONSE_ADDRESS_PREFIX = "responseAddress-";
    private static final String RESPONSE_ADDRESS_KEY = "responseAddress";

    private Logger log;
    private EventBus eb;
    private String uniqueId;
    private int intervalInSec;
    private boolean useInjectedClusterMembersCount = true;
    private int clusterMemberCount;
    protected Map<String,List<JsonObject>> healthCheckResponses;

    @Override
    public void start() {

        // initalize variables
        healthCheckResponses = new HashMap<>();

        log = container.logger();
        eb = vertx.eventBus();

        JsonObject config = container.config();

        // get the interval in seconds to execute the checks
        intervalInSec = config.getInteger("intervalInSec", 0) * 1000;

        // get the clusterMembers injected over the config, if available
        JsonArray clusterMembers = config.getArray("clusterMembers", null);
        if(clusterMembers == null) {
            useInjectedClusterMembersCount = false;
        } else {
            clusterMemberCount = clusterMembers.size();
        }

        // create a unique ID per verticle to identify it
        uniqueId = UUID.randomUUID().toString();
        log.info("started cluster check verticle: " + uniqueId);

        // the handler for the broadcast event, reads the sender from the event and reply to him
        eb.registerHandler(BROADCAST, new Handler<Message<JsonObject>>() {
            public void handle(Message<JsonObject> event) {
                String responseAddress = event.body().getString(RESPONSE_ADDRESS_KEY);
                String timestamp = event.body().getString("timestamp");
                log.info("got broadcast, i am: " + uniqueId + ", responseAddress is: " + responseAddress + " timestamp is: " + timestamp);

                // respond to the sender
                JsonObject responsePayload = new JsonObject();
                responsePayload.putString("senderId", uniqueId);
                responsePayload.putString("timestamp", timestamp);
                eb.send(responseAddress, responsePayload);
            }
        });

        // the handler for the reply of the broadcast handler, adds the result to the healthCheckResponses
        eb.registerHandler(RESPONSE_ADDRESS_PREFIX + uniqueId, new Handler<Message<JsonObject>>() {
            public void handle(Message<JsonObject> event) {
                String senderId = event.body().getString("senderId");
                String timestamp = event.body().getString("timestamp");
                log.info("got response, i am: " + uniqueId + ", senderId is: " + senderId);
                if(healthCheckResponses.get(timestamp) == null) {
                    healthCheckResponses.put(timestamp, new ArrayList<JsonObject>());
                }
                JsonObject response = new JsonObject();
                response.putString("senderId", senderId);
                healthCheckResponses.get(timestamp).add(response);
            }
        });

        if(intervalInSec == 0) {
            // wait until all verticles are up and running
            vertx.setTimer(2000, new ClusterCheckHandler());
        }

        if(intervalInSec > 0) {
            // wait until all verticles are up and running
            vertx.setTimer(2000, new Handler<Long>() {
                @Override public void handle(Long event) {
                    vertx.setPeriodic(intervalInSec, new ClusterCheckHandler());
                }
            });
        }
    }

    class ClusterCheckHandler implements Handler<Long> {

        public void handle(Long event) {
            JsonObject testpayload = new JsonObject();
            testpayload.putString(RESPONSE_ADDRESS_KEY, RESPONSE_ADDRESS_PREFIX + uniqueId);
            log.info("send single broadcast healthcheck from: " + uniqueId);
            final String timestamp = String.valueOf(System.currentTimeMillis());
            testpayload.putString("timestamp", timestamp);

            // if the cluster
            if(! useInjectedClusterMembersCount) {
                ClusterInformation clusterInformation = new ClusterInformation();
                try {
                    clusterMemberCount = clusterInformation.getMembers(log).size();
                } catch (MoreThanOneHazelcastInstanceException e) {
                    log.error("got more than one hazelcast instance, we can only handle one hazelcast instance");
                }
            }

            // publish the broadcast event which will us get the response of all the registered handlers
            eb.publish(BROADCAST, testpayload);

            // give the handlers 2sec to respond
            // log an error message in the case if the response counts don't match the cluster member amount
            vertx.setTimer(2000, new Handler<Long>() {
                public void handle(Long event) {
                    List<JsonObject> responses =  healthCheckResponses.get(timestamp);
                    if(responses == null) {
                        log.error("found no responses for timestamp: " + timestamp);
                    } else if(clusterMemberCount != responses.size()){
                        log.error("known cluster members: " + clusterMemberCount + " responses: " + responses.size());
                    } else {
                        log.info("all the cluster members ("+ responses.size() +") answered: " + responses.toString());
                    }
                }
            });
        }
    }

}
