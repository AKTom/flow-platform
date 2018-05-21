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

package com.flow.platform.agent.manager.event;

import org.springframework.context.ApplicationEvent;

/**
 * No available resources in resource pool
 *
 * Should be handled in ZoneService to adjust resource pool in zone
 *
 * @author yang
 */
public class AgentResourceEvent extends ApplicationEvent {

    public enum Category {
        RELEASED,

        /**
         * All agent resource occupied
         */
        FULL
    }

    private final String zone;

    private final Category category;

    public AgentResourceEvent(Object source, String zone, Category category) {
        super(source);
        this.zone = zone;
        this.category = category;
    }

    public String getZone() {
        return zone;
    }

    public Category getCategory() {
        return category;
    }
}
