package org.swisspush.vertx.cluster;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@RunWith(VertxUnitRunner.class)
public class TwoClusterMembersOneVerticleInstancesTests {

    // with this number we simulate the different member count of a cluster
    private final static int SIMULATED_CLUSTER_MEMBERS = 1;
    private Vertx vertx;
    private Logger log = LoggerFactory.getLogger(TwoClusterMembersOneVerticleInstancesTests.class);
    private List<String> answers = new ArrayList<>();

    @Before
    public void before() {
        vertx = Vertx.vertx();

        final String moduleName = "org.swisspush.vertx.cluster.ClusterWatchdog";

        JsonObject config = new JsonObject();
        config.put("intervalInSec", 0);
        config.put("clusterMemberCount", 2);

        vertx.deployVerticle(moduleName, new DeploymentOptions().setConfig(config).setInstances(SIMULATED_CLUSTER_MEMBERS), event ->
                log.info("success of deployment of  module " + moduleName + ": " + event.result()));
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
            httpClient.request(HttpMethod.GET, "/clusterStatus", httpClientReq -> {
                httpClientReq.result().send().onComplete(event1 -> {
                    log.info("response status message: " + event1.result().statusMessage());
                    testContext.assertEquals(ClusterHealthStatus.INCONSISTENT.toString(), event1.result().statusMessage());
                    async.complete();
                });
            });
        });
    }
}
