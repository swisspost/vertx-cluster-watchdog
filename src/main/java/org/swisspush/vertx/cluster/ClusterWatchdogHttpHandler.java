package org.swisspush.vertx.cluster;

import io.vertx.core.Vertx;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import org.apache.commons.lang.ArrayUtils;

public class ClusterWatchdogHttpHandler implements Handler<HttpServerRequest> {

    private Router router;
    protected CircularFifoQueue<WatchdogResult> resultQueue;

    public ClusterWatchdogHttpHandler(Vertx vertx, final Logger log, final int resultQueueLength) {
        this.router = Router.router(vertx);
        resultQueue = new CircularFifoQueue<>(resultQueueLength);

        router.getWithRegex(".*clusterWatchdogStats").handler(ctx -> {

            // revers the result, that the newest is showed first
            WatchdogResult[] watchdogResults = resultQueue.toArray(new WatchdogResult[resultQueue.size()]);
            ArrayUtils.reverse(watchdogResults);

            JsonArray results = new JsonArray();
            for(WatchdogResult watchdogResult : watchdogResults) {
                results.add(watchdogResult.toJson());
            }
            JsonObject result = new JsonObject();
            result.put("results", results);

            String body = result.encode();
            ctx.response().headers().add("Content-Length", "" + body.length());
            ctx.response().headers().add("Content-Type", "application/json; charset=utf-8");
            ctx.response().setStatusCode(200);
            ctx.response().end(body);
        });

        router.getWithRegex(".*clusterStatus").handler(ctx -> {
            ClusterHealthStatus status = ClusterHealthStatus.CONSISTENT;
            for(WatchdogResult watchdogResult : resultQueue) {
                if(ClusterHealthStatus.INCONSISTENT.equals(watchdogResult.status)) {
                    status = ClusterHealthStatus.INCONSISTENT;
                }
            }

            if(resultQueue.isEmpty()) {
                status = ClusterHealthStatus.NO_RESULT;
            }

            JsonObject result = new JsonObject();
            result.put("status", status.toString());

            String body = result.encode();
            ctx.response().headers().add("Content-Length", "" + body.length());
            ctx.response().headers().add("Content-Type", "application/json; charset=utf-8");
            ctx.response().setStatusMessage(status.toString());
            ctx.response().setStatusCode(200);
            ctx.response().end(body);
        });

        log.info("ClusterWatchdog created the ClusterWatchdogHttpHandler");
    }

    @Override
    public void handle(HttpServerRequest request) {
        router.accept(request);
    }
}
