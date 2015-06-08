package li.chee.vertx.cluster.jmx;

import org.vertx.java.core.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by kammermannf on 02.06.2015.
 */
public class ClusterInformation {

    private MBeanServerConnection mbeanServerConnection;

    public ClusterInformation(String jmxServiceURL) {
        JMXServiceURL url = null;
        try {
            url = new JMXServiceURL(jmxServiceURL);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        JMXConnector mConnector = null;
        try {
            mConnector = JMXConnectorFactory.connect(url);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            mbeanServerConnection = mConnector.getMBeanServerConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ClusterInformation() {
        mbeanServerConnection = ManagementFactory.getPlatformMBeanServer();
    }


    public List<String> getMembers(Logger log) throws Exception {
        log.info("query for MBeans: com.hazelcast:*");
        ObjectName hcDomainQuery = new ObjectName("com.hazelcast:*");
        Set<ObjectInstance> objectInstances = mbeanServerConnection.queryMBeans(hcDomainQuery, null);
        ObjectInstance hazelcastInstance = null;
        for (Iterator<ObjectInstance> iterator = objectInstances.iterator(); iterator.hasNext(); ) {
            ObjectInstance next = iterator.next();
            log.info("found object: " + next.getObjectName().toString());
            if(next.getObjectName().toString().contains(",type=HazelcastInstance,name=")) {
                hazelcastInstance = next;
            }
        }

        log.info("found hazelcast instance: " + hazelcastInstance.getObjectName().toString());

        try {
            List<String> members = (List<String>) mbeanServerConnection.getAttribute(hazelcastInstance.getObjectName(), "Members");
            log.info("found the following members registered to hazelcast: " + members.toString());
            return members;
        } catch(Exception e) {
            log.error("got an exception, when accessing the members of the hazelcastInstance: " + e.toString());
            return new ArrayList<>();
        }
    }

}
