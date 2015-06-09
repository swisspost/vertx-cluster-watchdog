package li.chee.vertx.cluster;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

public class ClusterWatchdogHttpHandler implements Handler<HttpServerRequest> {

    Logger log;

    protected CircularFifoQueue<WatchdogResult> resultQueue;

    RouteMatcher routeMatcher = new RouteMatcher();

    public ClusterWatchdogHttpHandler(final Logger log, final int resultQueueLength) {

        this.log = log;
        resultQueue = new CircularFifoQueue<>(resultQueueLength);

        routeMatcher.getWithRegEx(".*clusterWatchdogStats", new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest request) {

                JsonArray results = new JsonArray();
                for(WatchdogResult watchdogResult : resultQueue) {
                    results.add(watchdogResult.toJson());
                }
                JsonObject result = new JsonObject();
                result.putArray("results", results);

                String body = result.encode();
                request.response().headers().add("Content-Length", "" + body.length());
                request.response().headers().add("Content-Type", "application/json; charset=utf-8");
                request.response().setStatusCode(200);
                request.response().end(body);
            }
        });

        routeMatcher.getWithRegEx(".*clusterStatus", new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest request) {

                ClusterHealthStatus status = ClusterHealthStatus.CONSISTENT;
                for(WatchdogResult watchdogResult : resultQueue) {
                    if(ClusterHealthStatus.INCONSISTENT.equals(watchdogResult.status)) {
                        status = ClusterHealthStatus.INCONSISTENT;
                    }
                }
                JsonObject result = new JsonObject();
                result.putString("status", status.toString());

                String body = result.encode();
                request.response().headers().add("Content-Length", "" + body.length());
                request.response().headers().add("Content-Type", "application/json; charset=utf-8");
                request.response().setStatusMessage(status.toString());
                request.response().setStatusCode(200);
                request.response().end(body);
            }
        });

        log.info("created the ClusterWatchdogHttpHandler");
    }

    @Override
    public void handle(HttpServerRequest request) {
        routeMatcher.handle(request);
    }
}
