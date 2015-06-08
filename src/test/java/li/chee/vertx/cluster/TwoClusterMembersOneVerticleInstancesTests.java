package li.chee.vertx.cluster;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

import java.util.ArrayList;
import java.util.List;

public class TwoClusterMembersOneVerticleInstancesTests extends TestVerticle {

    // with this number we simulate the different member count of a cluster
    private final static int SIMULATED_CLUSTER_MEMBERS = 1;

    private EventBus eb;
    private Logger log;

    private List<String> answers = new ArrayList<>();

    @Override
    public void start() {

        log = container.logger();
        eb = vertx.eventBus();

        initialize();

        eb.registerHandler("clusterhealthcheck", new Handler<Message<JsonObject>>() {
            public void handle(Message<JsonObject> event) {
                final JsonObject body = event.body();
                answers.add(body.getString("responseAddress"));
                log.info("got message in test: " + body.toString());
            }
        });

        final String moduleName = System.getProperty("vertx.modulename");

        JsonObject config = new JsonObject();
        config.putNumber("intervalInSec", 0);
        JsonArray members = new JsonArray();
        members.addString("[/192.168.26.35:8981]");
        members.addString("[/192.168.26.37:8981]");
        config.putArray("clusterMembers", members);

        container.deployModule(moduleName, config, SIMULATED_CLUSTER_MEMBERS, new AsyncResultHandler<String>() {
            public void handle(AsyncResult<String> event) {
                log.info("success of deployment of  module " + moduleName + ": " + event.result());
                startTests();
            }
        });
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Test
    /**
     * To simulate two cluster members, we create two instances of the verticle
     */
    public void test2ClusterMembers() throws Exception {
        vertx.setTimer(5000, new Handler<Long>() {
            public void handle(Long event) {
                log.info("answer size is: " + answers.size());
                if (answers.size() == 2) {
                    VertxAssert.testComplete();
                }
            }
        });
    }
}
