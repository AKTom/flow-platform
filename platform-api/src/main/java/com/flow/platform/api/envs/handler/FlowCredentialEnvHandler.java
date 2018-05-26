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

package com.flow.platform.api.envs.handler;

import static com.flow.platform.api.envs.GitEnvs.FLOW_GIT_CREDENTIAL;

import com.flow.platform.api.domain.v1.Flow;
import com.flow.platform.api.envs.EnvKey;
import com.flow.platform.api.envs.GitEnvs;
import com.flow.platform.api.service.CredentialService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author yh@firim
 */

@Component
public class FlowCredentialEnvHandler extends EnvHandler {

    @Autowired
    private CredentialService credentialService;

    @Override
    public EnvKey env() {
        return FLOW_GIT_CREDENTIAL;
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    void onHandle(Flow flow, String value) {
        Map<String, String> credentialEnvs = credentialService.findByName(value);
        flow.putAll(credentialEnvs);
    }

    @Override
    void onUnHandle(Flow flow, String value) {
        flow.removeEnv(GitEnvs.FLOW_GIT_SSH_PUBLIC_KEY);
        flow.removeEnv(GitEnvs.FLOW_GIT_SSH_PRIVATE_KEY);
        flow.removeEnv(GitEnvs.FLOW_GIT_HTTP_USER);
        flow.removeEnv(GitEnvs.FLOW_GIT_HTTP_PASS);
    }
}
