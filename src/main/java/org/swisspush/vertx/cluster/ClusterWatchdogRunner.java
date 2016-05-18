package org.swisspush.vertx.cluster;

import com.hazelcast.config.*;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.Args;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Created by florian kammermann on 18.05.2016.
 */
public class ClusterWatchdogRunner {

    private static final Logger log = LoggerFactory.getLogger(ClusterWatchdogRunner.class);

    public static void main(String[] sargs) {
        Args args = new Args(sargs);
        String confArg = args.map.get("-conf");
        JsonObject conf;

        if (confArg != null) {
            try (Scanner scanner = new Scanner(new File(confArg)).useDelimiter("\\A")){
                String sconf = scanner.next();
                try {
                    conf = new JsonObject(sconf);
                } catch (DecodeException e) {
                    log.error("Configuration file " + sconf + " does not contain a valid JSON object");
                    return;
                }
            } catch (FileNotFoundException e) {
                try {
                    conf = new JsonObject(confArg);
                } catch (DecodeException e2) {
                    log.error("-conf option does not point to a file and is not valid JSON: " + confArg);
                    return;
                }
            }
        } else {
            conf = new JsonObject();
        }

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setConfig(conf);

        VertxOptions options = new VertxOptions();
        if(null == conf.getBoolean("cluster.noconf") || false == conf.getBoolean("cluster.noconf")) {
            log.info("configure cluster");
            ClusterManager mgr = new HazelcastClusterManager(getClusterConfig(conf));
            options = new VertxOptions().setClusterManager(mgr);
        } else {
            options = new VertxOptions().setClusterManager(new HazelcastClusterManager());
        }

        Vertx.clusteredVertx(options, res -> {
            if (res.succeeded()) {
                Vertx vertx = res.result();
                vertx.deployVerticle("org.swisspush.vertx.cluster.ClusterWatchdog", deploymentOptions, result -> {
                    if (result.succeeded()) {
                        String deploymentID = result.result();
                        log.debug("Module has been deployed with deploymentID {}", deploymentID);
                    } else {
                        throw new RuntimeException("Module not started: {}", result.cause());
                    }
                });
            } else {
                log.error("Cannot create vert.x instance : " + res.cause());
            }
        });
    }

    public static Config getClusterConfig(JsonObject conf) {

        Config config = new Config();
        config.setProperty("hazelcast.logging.type", "slf4j");
        if(conf.getString("hazelcast.group.name") != null || conf.getString("hazelcast.group.password") != null) {
            GroupConfig groupConfig = new GroupConfig();
            if(conf.getString("hazelcast.group.name") != null) {
                groupConfig.setName(conf.getString("hazelcast.group.name"));
            }
            if(conf.getString("hazelcast.group.password") != null) {
                groupConfig.setPassword(conf.getString("hazelcast.group.password"));
            }
            config.setGroupConfig(groupConfig);
        }
        NetworkConfig netConfig = new NetworkConfig();
        if(conf.getString("hazelcast.net.port") != null) {
            netConfig.setPort(conf.getInteger("hazelcast.net.port"));
        }

        if(conf.getString("hazelcast.multicast.group") != null || conf.getString("hazelcast.multicast.port") != null) {
            JoinConfig joinConfig = new JoinConfig();
            MulticastConfig multicastConfig = new MulticastConfig();
            if(conf.getString("hazelcast.multicast.group") != null) {
                multicastConfig.setMulticastGroup(conf.getString("hazelcast.multicast.group"));
            }
            if(conf.getString("hazelcast.multicast.port") != null) {
                multicastConfig.setMulticastPort(conf.getInteger("hazelcast.multicast.port"));
            }
            joinConfig.setMulticastConfig(multicastConfig);
            netConfig.setJoin(joinConfig);
        }
        config.setNetworkConfig(netConfig);

        return config;
    }


}
