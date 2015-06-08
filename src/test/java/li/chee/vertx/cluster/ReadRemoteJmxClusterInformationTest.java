package li.chee.vertx.cluster;


import li.chee.vertx.cluster.jmx.ClusterInformation;
import org.hamcrest.core.Is;
import org.junit.Ignore;
import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.JULLogDelegateFactory;
import org.vertx.java.core.logging.impl.LogDelegate;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by kammermannf on 02.06.2015.
 */
public class ReadRemoteJmxClusterInformationTest {

    @Test
    @Ignore
    /**
     * Use this test in conjunction with a running external vert.x, which is not clustered.
     * You should be able to query the remote jmx beans
     */
    public void readHazelcastInstanceInformation() throws Exception {
        LogDelegate logDelegate = new JULLogDelegateFactory().createDelegate(ReadRemoteJmxClusterInformationTest.class.toString());
        Logger logger = new Logger(logDelegate);
        ClusterInformation clusterInformation = new ClusterInformation("service:jmx:rmi://localhost:9999/jndi/rmi://localhost:9999/jmxrmi");
        // members ~ [/192.168.26.35:8981]
        assertThat(clusterInformation.getMembers(logger).size(), Is.is(1));
    }

}
