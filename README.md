vertx-cluster-watchdog
======================

[ ![Codeship Status for postit12/vertx-cluster-watchdog](https://codeship.com/projects/05fe16e0-f0cf-0132-7113-460dfda79260/status?branch=master)](https://codeship.com/projects/84716)

Checks if all your hazelcast cluster members are receiveing published messages over the bus.

How the watchdog works
----------------------

1. every verticle gets a unique id
1. The amount of cluster members is read over the hazelcast api `Cluster#getMembers()`
1. a message is published to the broadcast address, the address to reply to is in the payload with a timestamp
1. the receivers of the broadcast message are sending a message back to the sender of the broadcast address, with the timestamp and its unique id
1. the receivers of the point to point message are counting the received messages with the same timestamp
1. the result of each member is sent to the other members to have consensus
1. every member has a `CircularFifoQueue` where he stores the result of himself and the received results, the length can be configured
1. if the watchdog is asked for consistency over `http://host:port/clusterStatus`, the `CircularFifoQueue` will be consulted, if there is one inconsistent entry, the cluster will be considered as **INCONSISTENT** if every entry is consistent, the cluster will be considered as **CONSISTENT** 

Rest API
--------

* The status of the cluster can be get over the following URL `http://host:port/clusterStatus`
* A detailed view of the last watchdog runs can be get over the following URL `http://host:port/clusterWatchdogStats`

Restrictions
------------

* There must be one vertx-cluster-watchdog verticle instance per vertx instance, the watchdog is relying onto the fact that one broadcast message is received by one cluster member
* the verticle can only handle one hazelcast instance. If there are more than one hazelcast instances, the watchdog will not be run.

Configuration
-------------

    {
        "port": 7878              // Port we serve http. Defaults to 7878.
        "intervalInSec": 30,      // In which interval the watchdog will be run. Defaults to 30, if the interval is set to 0 the watchdog only run once after deployment. 	                         
        "clusterMembers": -1,     // The amount of the cluster members, defaults to -1, which lets the mod figure out itself the amount of cluster members. 
        "resultQueueLength: 100"  // The amount of watchdog runs, that should be kept and considered to figure out the cluster state, defaults to 100.
    }
    
Tests
-----

The tests try to simulate the cluster with multiple instances of the verticle. The amount of cluster members is injected over the config.

Use gradle with alternative repositories
----------------------------------------

As standard the default maven repositories are set.
You can overwrite these repositories by setting these properties (`-Pproperty=value`):

* `repository` this is the repository where resources are fetched
* `uploadRepository` the repository used in `uploadArchives`
* `repoUsername` the username for uploading archives
* `repoPassword` the password for uploading archives
    
