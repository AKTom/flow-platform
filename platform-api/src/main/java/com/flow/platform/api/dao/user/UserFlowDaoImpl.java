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
package com.flow.platform.api.dao.user;

import com.flow.platform.api.domain.user.UserFlow;
import com.flow.platform.api.domain.user.UserFlowKey;
import com.flow.platform.core.dao.AbstractBaseDao;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * @author lhl
 */

@Repository
public class UserFlowDaoImpl extends AbstractBaseDao<UserFlowKey, UserFlow> implements UserFlowDao {

    @Override
    protected Class<UserFlow> getEntityClass() {
        return UserFlow.class;
    }

    @Override
    protected String getKeyName() {
        return "key";
    }

    @Override
    public List<Long> listByEmail(String email) {
        return execute(session -> session
            .createQuery("select key.flowId from UserFlow where key.email = ?", Long.class)
            .setParameter(0, email)
            .list());
    }

    @Override
    public List<String> listByFlow(Long flowId) {
        return execute(session -> session
            .createQuery("select key.email from UserFlow where key.flowId = ?", String.class)
            .setParameter(0, flowId)
            .list());
    }

    @Override
    public Long numOfUserByFlow(Long flowId) {
        return execute(session -> session
            .createQuery("select count(key.email) from UserFlow where key.flowId = ?", Long.class)
            .setParameter(0, flowId)
            .uniqueResult());
    }

    @Override
    public int deleteByEmail(String email) {
        return execute(session -> session
            .createQuery("delete from UserFlow where key.email = ?")
            .setParameter(0, email)
            .executeUpdate());
    }

    @Override
    public int deleteByFlow(Long flowId) {
        return execute(session -> session
            .createQuery("delete from UserFlow where key.flowId = ?")
            .setParameter(0, flowId)
            .executeUpdate());
    }
}
