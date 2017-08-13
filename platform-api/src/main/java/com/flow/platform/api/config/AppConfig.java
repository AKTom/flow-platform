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

import com.flow.platform.api.util.SystemUtil;
import com.flow.platform.util.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author yang
 */
@Configuration
@Import({DatabaseConfig.class})
public class AppConfig {

    public final static String DEFAULT_YML_FILE = ".flow.yml";

    private final static Logger LOGGER = new Logger(AppConfig.class);

    private final static int ASYNC_POOL_SIZE = 100;

    @Value("${api.workspace}")
    private String workspace;

    @Bean
    public Path workspace() {
        try {
            Path dir = Files.createDirectories(SystemUtil.replacePathWithEnv(workspace));
            LOGGER.trace("flow.ci working dir been created : %s", dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Fail to create flow.ci api working dir", e);
        }
    }

    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(ASYNC_POOL_SIZE / 3);
        taskExecutor.setMaxPoolSize(ASYNC_POOL_SIZE);
        taskExecutor.setQueueCapacity(100);
        taskExecutor.setThreadNamePrefix("async-task-");
        return taskExecutor;
    }
}