package li.chee.vertx.cluster;

import org.junit.Assert;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

import java.util.ArrayList;
import java.util.List;

public class TwoClusterMembersTwoVerticleInstancesTests extends TestVerticle {

    // with this number we simulate the different member count of a cluster
    private final static int SIMULATED_CLUSTER_MEMBERS = 2;

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
        config.putNumber("clusterMemberCount", 2);

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
                HttpClient httpClient = vertx.createHttpClient().setHost("localhost").setPort(7878);
                httpClient.getNow("clusterStatus", new Handler<HttpClientResponse>() {

                    @Override
                    public void handle(HttpClientResponse httpClientResponse) {
                        log.info("response status message: " + httpClientResponse.statusMessage());
                        Assert.assertEquals(ClusterHealthStatus.CONSISTENT.toString(), httpClientResponse.statusMessage());
                        VertxAssert.testComplete();
                    }
                });
            }
        });
    }
}
