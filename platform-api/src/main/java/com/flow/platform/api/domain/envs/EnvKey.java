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

package com.flow.platform.api.domain.envs;

import com.google.common.collect.Sets;
import java.util.Set;

/**
 * @author yang
 */
public interface EnvKey {

    /**
     * The env variable should write to root node result output
     */
    Set<String> FOR_OUTPUTS = Sets.newHashSet(
        GitEnvs.FLOW_GIT_BRANCH.name(),
        GitEnvs.FLOW_GIT_CHANGELOG.name(),
        GitEnvs.FLOW_GIT_COMMIT_ID.name(),
        GitEnvs.FLOW_GIT_COMPARE_ID.name(),
        GitEnvs.FLOW_GIT_COMPARE_URL.name(),
        GitEnvs.FLOW_GIT_AUTHOR.name(),
        GitEnvs.FLOW_GIT_PR_URL.name()
    );

    String name();
}
