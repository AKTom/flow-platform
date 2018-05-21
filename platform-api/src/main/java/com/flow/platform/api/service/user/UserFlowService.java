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
package com.flow.platform.api.service.user;

import com.flow.platform.api.domain.Flow;
import com.flow.platform.api.domain.user.User;
import java.util.List;

/**
 * @author lhl
 */
public interface UserFlowService {

    /**
     * List flows for user
     */
    List<Flow> list(User user);

    /**
     * List users for flow
     */
    List<User> list(String flowName);

    /**
     * Assign user to flow
     */
    void assign(User user, Flow flow);

    /**
     * Un-assign a user for all flows
     */
    void unAssign(User user);

    /**
     * Un-assign a flow for all users
     */
    void unAssign(Flow flow);

    /**
     * Un-assign a user form flow
     */
    void unAssign(User user, Flow flow);

}
