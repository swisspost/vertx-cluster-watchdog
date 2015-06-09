package li.chee.vertx.cluster;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

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
        result.putString("status", String.valueOf(status));
        result.putString("time", time);
        result.putString("broadcastTimestamp", broadcastTimestamp);
        result.putString("verticleId", verticleId);
        result.putNumber("clusterMemberCount", clusterMemberCount);

        JsonArray respondersAsArr = new JsonArray();
        for(String responder : responders) {
            respondersAsArr.addString(responder);
        }
        result.putArray("responders", respondersAsArr);
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
            watchdogResult.clusterMemberCount = watchdogJsonObj.getNumber("clusterMemberCount").intValue();
        } catch(Exception e) { /* let the clusterMemberCount be null */ }
        List<String> responders = new ArrayList<>();
        try {
            watchdogResult.responders = watchdogJsonObj.getArray("responders").toList();
        } catch(Exception e) { /* let the clusterMemberCount be null */ }
        return watchdogResult;

    }

}
