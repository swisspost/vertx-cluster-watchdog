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
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;

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

        VertxOptions options;
        if(null == conf.getBoolean("cluster.noconf") || false == conf.getBoolean("cluster.noconf")) {
            log.info("configure cluster");
            ClusterManager mgr = new HazelcastClusterManager(getClusterConfig(conf));
            options = new VertxOptions().setClusterManager(mgr);
        } else {
            options = new VertxOptions().setClusterManager(new HazelcastClusterManager());
        }

        String defaultAddressNotLoopback = getDefaultAddressNotLoopback();
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        if(null != conf.getString("cluster.host")) {
            log.info("use cluster host from config: " + conf.getString("cluster.host"));
            options.setClusterHost(conf.getString("cluster.host"));
        } else if(null != defaultAddressNotLoopback){
            log.info("use cluster host from address lookup: " + defaultAddressNotLoopback);
            options.setClusterHost(defaultAddressNotLoopback);
        } else if(null != loopbackAddress) {
            log.info("use loopback as cluster host: " + loopbackAddress);
            options.setClusterHost(loopbackAddress.getHostAddress());
        } else {
            throw new IllegalStateException("no address found for cluster host");
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


    private static String getDefaultAddressNotLoopback() {
        Enumeration<NetworkInterface> nets;
        try {
            nets = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            log.error("could not get the network interfaces " + e.getMessage());
            return null;
        }

        NetworkInterface netinf;
        List<InetAddress> usableInetAdresses = new ArrayList<>();
        while (nets.hasMoreElements()) {
            netinf = nets.nextElement();

            Enumeration<InetAddress> addresses = netinf.getInetAddresses();

            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                log.info("found InetAddress: " + address.toString() + " on interface: " + netinf.getName());
                if (!address.isAnyLocalAddress() && !address.isMulticastAddress()
                        && !(address instanceof Inet6Address) &&!address.isLoopbackAddress()) {
                    usableInetAdresses.add(address);
                }
            }
        }

        if(usableInetAdresses.size() > 1) {
            throw new IllegalStateException("don't know which InetAddress to use, there are more than one: " + usableInetAdresses);
        } else if(usableInetAdresses.size() == 1) {
            log.info("found a InetAddress which we can use as default address: " + usableInetAdresses.get(0).toString());
            return usableInetAdresses.get(0).getHostAddress();
        }

        log.info("found no usable inet address");
        return null;
    }

    private static Config getClusterConfig(JsonObject conf) {

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
