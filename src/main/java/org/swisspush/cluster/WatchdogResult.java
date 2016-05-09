package org.swisspush.cluster;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kammermannf on 09.06.2015.
 */
public class WatchdogResult {

    ClusterHealthStatus status;
    String time;
    String broadcastTimestamp;
    String verticleId;
    int clusterMemberCount;
    List<String> responders = new ArrayList<>();

    public void setResponders(List<JsonObject> responses) {
        for(JsonObject response : responses) {
            responders.add(response.getString("senderId"));
        }
    }

    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        result.put("status", String.valueOf(status));
        result.put("time", time);
        result.put("broadcastTimestamp", broadcastTimestamp);
        result.put("verticleId", verticleId);
        result.put("clusterMemberCount", clusterMemberCount);

        JsonArray respondersAsArr = new JsonArray();
        for(String responder : responders) {
            respondersAsArr.add(responder);
        }
        result.put("responders", respondersAsArr);
        return result;
    }

    public static WatchdogResult fromJson(JsonObject watchdogJsonObj) {
        WatchdogResult watchdogResult = new WatchdogResult();
        try {
            watchdogResult.status = ClusterHealthStatus.valueOf(watchdogJsonObj.getString("status"));
        } catch(Exception e) { /* let the status be null */ }

        watchdogResult.time = watchdogJsonObj.getString("time");
        watchdogResult.broadcastTimestamp = watchdogJsonObj.getString("broadcastTimestamp");
        watchdogResult.verticleId = watchdogJsonObj.getString("verticleId");
        try {
            watchdogResult.clusterMemberCount = watchdogJsonObj.getInteger("clusterMemberCount").intValue();
        } catch(Exception e) { /* let the clusterMemberCount be null */ }

        try {
            watchdogResult.responders = watchdogJsonObj.getJsonArray("responders").getList();
        } catch(Exception e) { /* let the clusterMemberCount be null */ }
        return watchdogResult;

    }

}
