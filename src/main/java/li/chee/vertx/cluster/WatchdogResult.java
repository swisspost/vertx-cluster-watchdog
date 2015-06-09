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
        result.putString("status", status.toString());
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

}
