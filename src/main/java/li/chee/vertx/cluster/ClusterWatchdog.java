package li.chee.vertx.cluster;

import li.chee.vertx.cluster.jmx.ClusterInformation;
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
    private static final long CHECK_INTERVAL = 1000;
    private static final String RESPONSE_ADDRESS_PREFIX = "responseAddress-";
    private static final String RESPONSE_ADDRESS_KEY = "responseAddress";

    private String uniqueId;
    private int intervalInSec;
    private int clusterMemberCount;
    private Map<String,List<JsonObject>> healthCheckResponses;

    @Override
    public void start() {

        // initalize variables
        healthCheckResponses = new HashMap<>();

        final Logger log = container.logger();
        log.info("container environment: " + container.env().toString());
        final EventBus eb = vertx.eventBus();


        JsonObject config = container.config();
        intervalInSec = config.getInteger("intervalInSec", 0) * 1000;
        JsonArray clusterMembers = config.getArray("clusterMembers", new JsonArray());
        clusterMemberCount = clusterMembers.size();

        uniqueId = UUID.randomUUID().toString();

        log.info("started cluster check verticle: " + uniqueId);

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
            vertx.setTimer(2000, new Handler<Long>() {
                public void handle(Long event) {
                    JsonObject testpayload = new JsonObject();
                    testpayload.putString(RESPONSE_ADDRESS_KEY, RESPONSE_ADDRESS_PREFIX + uniqueId);
                    log.info("send single broadcast healthcheck from: " + uniqueId);
                    final String timestamp = String.valueOf(System.currentTimeMillis());
                    testpayload.putString("timestamp", timestamp);
                    if(clusterMemberCount == 0) {
                        ClusterInformation clusterInformation = new ClusterInformation();
                        try {
                            clusterMemberCount = clusterInformation.getMembers(log).size();
                        } catch (Exception e) {
                           log.error("could not read cluster member information");
                        }
                    }
                    eb.publish(BROADCAST, testpayload);
                    vertx.setTimer(2000, new Handler<Long>() {
                        public void handle(Long event) {
                            List<JsonObject> responses =  healthCheckResponses.get(timestamp);
                            if(responses == null) {
                                log.error("found no responses for timestamp: " + timestamp);
                            } else if(clusterMemberCount != responses.size()){
                                log.error("known cluster members: " + clusterMemberCount + " responses: " + responses.size());
                            } else {
                                log.info("all the cluster members answered: " + responses.size());
                            }

                        }
                    });
                }
            });
        }

        if(intervalInSec > 0) {
            // wait until all verticles are up and running
            vertx.setTimer(2000, new Handler<Long>() {
                public void handle(Long event) {
                    vertx.setPeriodic(CHECK_INTERVAL, new Handler<Long>() {
                        public void handle(Long event) {
                            JsonObject testpayload = new JsonObject();
                            testpayload.putString(RESPONSE_ADDRESS_KEY, RESPONSE_ADDRESS_PREFIX + uniqueId);
                            final String timestamp = String.valueOf(System.currentTimeMillis());
                            testpayload.putString("timestamp", timestamp);
                            log.info("send interval broadcast healthcheck from: " + uniqueId + " with timestamp: " + timestamp);
                            eb.publish(BROADCAST, testpayload);

                        }
                    });
                }
            });
        }
    }

}
