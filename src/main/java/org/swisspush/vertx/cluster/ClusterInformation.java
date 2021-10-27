package org.swisspush.vertx.cluster;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import io.vertx.core.logging.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * @author https://github.com/floriankammermann [Florian Kammermann] on 02.06.2015
 */
public class ClusterInformation {

    public static Set<Member> getMembers(Logger log) throws MoreThanOneHazelcastInstanceException {

        // get the hazelcast instances
        Set<HazelcastInstance> allHazelcastInstances = Hazelcast.getAllHazelcastInstances();
        if(allHazelcastInstances.isEmpty()) {
            log.error("ClusterWatchdog no hazelcast instances found");
            return new HashSet<>();
        }

        if(allHazelcastInstances.size() > 1) {
            log.error("ClusterWatchdog more than one hazelcast instances found: " + allHazelcastInstances.size());
            throw new MoreThanOneHazelcastInstanceException();
        }

        log.debug("ClusterWatchdog found exactly one hazelcast instance, we can go on");
        Set<Member> members = allHazelcastInstances.iterator().next().getCluster().getMembers();
        log.debug("ClusterWatchdog found the following members in the hazelcast instance: " + members);

        return members;
    }

}
