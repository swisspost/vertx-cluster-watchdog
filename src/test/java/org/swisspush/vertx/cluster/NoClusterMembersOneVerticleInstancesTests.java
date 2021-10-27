package org.swisspush.vertx.cluster;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(VertxUnitRunner.class)
public class NoClusterMembersOneVerticleInstancesTests {

    // with this number we simulate the different member count of a cluster
    private final static int SIMULATED_CLUSTER_MEMBERS = 1;

    private Vertx vertx;
    private Logger log = LoggerFactory.getLogger(NoClusterMembersOneVerticleInstancesTests.class);

    private List<String> answers = new ArrayList<>();

    @Before
    public void before() {
        vertx = Vertx.vertx();
        vertx.eventBus().consumer("clusterhealthcheck", (Handler<Message<JsonObject>>) event -> {
            final JsonObject body = event.body();
            answers.add(body.getString("responseAddress"));
            log.info("got message in test: " + body.toString());
        });

        JsonObject config = new JsonObject();
        config.put("intervalInSec", 0);
        config.put("clusterMemberCount", 0);

        final String moduleName = "org.swisspush.vertx.cluster.ClusterWatchdog";

        vertx.deployVerticle(moduleName, new DeploymentOptions().setConfig(config).setInstances(SIMULATED_CLUSTER_MEMBERS), event ->
                log.info("success of deployment of module " + moduleName + ": " + event.result()));
    }

    @After
    public void after(TestContext testContext) {
        vertx.close(testContext.asyncAssertSuccess());
    }

    @Test
    /**
     * To simulate two cluster members, we create two instances of the verticle
     */
    public void test2ClusterMembers(TestContext testContext) throws Exception {
        Async async = testContext.async();
        vertx.setTimer(5000, event -> {
            HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(7878));
            httpClient.getNow("/clusterStatus", httpClientResponse -> {
                log.info("response status message: " + httpClientResponse.statusMessage());
                testContext.assertEquals(ClusterHealthStatus.NO_RESULT.toString(), httpClientResponse.statusMessage());
                httpClient.close();
                async.complete();
            });
        });
    }
}
