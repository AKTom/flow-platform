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

package com.flow.platform.api.domain.sync;

import com.flow.platform.domain.AgentPath;
import com.google.gson.annotations.Expose;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * @author yang
 */
@RequiredArgsConstructor
@EqualsAndHashCode(of = {"path"})
public class Sync {

    /**
     * Agent path
     */
    @Expose
    @Getter
    private final AgentPath path;

    /**
     * Synced repo list for agent
     */
    @Expose
    @Getter
    private Set<SyncRepo> repos = new LinkedHashSet<>();

    /**
     * Latest sync time
     */
    @Expose
    @Setter
    @Getter
    private ZonedDateTime syncTime = ZonedDateTime.now();
}
