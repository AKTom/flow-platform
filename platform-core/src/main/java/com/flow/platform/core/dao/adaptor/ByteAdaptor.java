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

package com.flow.platform.core.dao.adaptor;


import com.flow.platform.util.ObjectUtil;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

/**
 * @author yh@firim
 */
public class ByteAdaptor extends BaseAdaptor {

    @Override
    protected Type getTargetType() {
        return null;
    }

    @Override
    public Class returnedClass() {
        return null;
    }

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.BLOB};
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
        throws HibernateException, SQLException {

        byte[] bytes = rs.getBytes(names[0]);
        return Objects.isNull(bytes) ? null : ObjectUtil.fromBytes(bytes);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
        throws HibernateException, SQLException {

        // set to null
        if (Objects.isNull(value)) {
            st.setBytes(index, null);
            return;
        }

        // value already in string type
        if (value instanceof byte[]) {
            st.setBytes(index, (byte[]) value);
            return;
        }

        byte[] bytes = ObjectUtil.toBytes(value);
        st.setBytes(index, bytes);

    }
}