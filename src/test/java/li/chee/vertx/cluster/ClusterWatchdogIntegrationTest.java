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

public class ClusterWatchdogIntegrationTest extends TestVerticle {

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
        List clusterMembersInfo = getMembersMockAmountOfOne();
        JsonArray clusterMembers = new JsonArray(clusterMembersInfo);
        //config.putArray("clusterMembers", clusterMembers);

        container.deployModule(moduleName, config, 2, new AsyncResultHandler<String>() {
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
    public void testSimple() throws Exception {

            vertx.setTimer(5000, new Handler<Long>() {
                public void handle(Long event) {
                    log.info("answer size is: " + answers.size());
                    if (answers.size() == 2) {
                        VertxAssert.testComplete();
                    }
                }
            });
    }

    private List<String> getMembersMockAmountOfOne() {
        List<String> members = new ArrayList<>();
        members.add("[/192.168.26.35:8981]");
        return members;
    }

    private List<String> getMembersMockAmountOfTwo() {
        List<String> members = new ArrayList<>();
        members.add("[/192.168.26.35:8981]");
        members.add("[/192.168.26.37:8981]");
        return members;
    }
}
