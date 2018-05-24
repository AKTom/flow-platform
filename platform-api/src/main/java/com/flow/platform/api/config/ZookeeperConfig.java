/*
 * Copyright 2017 flow.ci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flow.platform.api.config;

/**
 * @author yh@fir.im
 */
/*
 * Copyright 2017 flow.ci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.flow.platform.util.zk.ZKClient;
import com.flow.platform.util.zk.ZKServer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import javax.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author yang
 */
@Log4j2
@Configurable
public class ZookeeperConfig {

    /**
     * Zone dynamic property naming rule
     * - First %s is zone name
     * - Seconds %s is property name xx_yy_zz from Zone.getXxYyZz
     */
    private final static String ZONE_PROPERTY_NAMING = "zone.%s.%s";

    private final static Properties EMBEDDED_ZOOKEEPER_PROP = new Properties();

    static {
        File zkDataPath = Paths.get(System.getProperty("java.io.tmpdir", "/tmp/flow-zookeeper"), "zookeeper", "data")
            .toFile();
        EMBEDDED_ZOOKEEPER_PROP.setProperty("dataDir", zkDataPath.getAbsolutePath());
        EMBEDDED_ZOOKEEPER_PROP.setProperty("clientPort", "2181");
        EMBEDDED_ZOOKEEPER_PROP.setProperty("clientPortAddress", "0.0.0.0");
        EMBEDDED_ZOOKEEPER_PROP.setProperty("tickTime", "1500");
        EMBEDDED_ZOOKEEPER_PROP.setProperty("maxClientCnxns", "50");
    }

    @Value("${zk.server.embedded}")
    private Boolean enableEmbeddedServer;

    @Value("${zk.host}")
    private String host;

    @Value("${zk.timeout}")
    private Integer clientTimeout; // zk client connection timeout

    @Value("${zk.node.root}")
    private String rootNodeName;

    @Value("${zk.node.zone}")
    private String zonesDefinition;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    private ZKServer zkServer = null;

    @PostConstruct
    public void init() {
        log.trace("Host: {}", host);
        log.trace("Root node: {}", rootNodeName);
        log.trace("Zones: {}", zonesDefinition);
        log.trace("Embedded enabled: {}", enableEmbeddedServer);
    }

    @Bean
    public ZKClient zkClient() {

        if (enableEmbeddedServer) {
            if (startEmbeddedServer()) {
                final String localZkHost = "127.0.0.1:2181";
                ZKClient zkClient = new ZKClient(localZkHost, clientTimeout);

                if (zkClient.start()) {
                    log.info("Client been connected at: {}", localZkHost);
                    return zkClient;
                }

                throw new RuntimeException("Fail to connect embedded zookeeper server: " + localZkHost);
            }

            throw new RuntimeException("Fail to start embedded zookeeper server");
        }

        ZKClient zkClient = new ZKClient(host, clientTimeout);
        if (zkClient.start()) {
            log.info("Zookeeper been connected at: {}", host);
            return zkClient;
        }

        throw new RuntimeException("Fail to connect zookeeper server: " + host);
    }

    @Bean
    public ZKServer zkServer() {
        return zkServer;
    }

    private boolean startEmbeddedServer() {
        try {
            zkServer = new ZKServer();
            QuorumPeerConfig quorumPeerConfig = new QuorumPeerConfig();
            quorumPeerConfig.parseProperties(EMBEDDED_ZOOKEEPER_PROP);

            ServerConfig configuration = new ServerConfig();
            configuration.readFrom(quorumPeerConfig);

            log.info("Starting internal zookeeper server.......");

            taskExecutor.execute(() -> {
                try {
                    zkServer.runFromConfig(configuration);
                } catch (IOException e) {
                    log.warn("Start internal zookeeper error: {}", e.getMessage());
                }
            });

            return true;
        } catch (Exception e) {
            log.warn("Start internal zookeeper error: {}", e.getMessage());
            return false;
        }
    }
}
