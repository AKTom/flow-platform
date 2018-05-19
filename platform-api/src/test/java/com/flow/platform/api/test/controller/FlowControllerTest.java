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

package com.flow.platform.api.test.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flow.platform.api.domain.CmdCallbackQueueItem;
import com.flow.platform.api.domain.Flow;
import com.flow.platform.api.domain.job.Job;
import com.flow.platform.api.domain.job.NodeStatus;
import com.flow.platform.api.domain.node.Node;
import com.flow.platform.api.domain.node.NodeTree;
import com.flow.platform.api.domain.response.BooleanValue;
import com.flow.platform.api.service.v1.FlowService;
import com.flow.platform.api.test.FlowHelper;
import com.flow.platform.api.util.CommonUtil;
import com.flow.platform.api.util.PathUtil;
import com.flow.platform.core.exception.NotFoundException;
import com.flow.platform.core.response.ResponseError;
import com.flow.platform.domain.Cmd;
import com.flow.platform.domain.CmdStatus;
import com.flow.platform.domain.CmdType;
import com.flow.platform.util.StringUtil;
import net.bytebuddy.asm.Advice.Unused;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * @author yang
 */
public class FlowControllerTest extends ControllerTestWithoutAuth {

    private final String flowName = "flow_default";

    @Autowired
    private FlowHelper flowHelper;

    @Autowired
    private FlowService flowService;

    @Before
    public void init() throws Throwable {
        stubDemo();
        createEmptyFlow(flowName);
    }

    @Test
    public void should_get_flow_node_detail() throws Throwable {
        MvcResult result = mockMvc.perform(get("/flows/" + flowName + "/show"))
            .andExpect(status().isOk())
            .andReturn();

        Node flowNode = Node.parse(result.getResponse().getContentAsString(), Node.class);
        Assert.assertNotNull(flowNode);
        Assert.assertEquals(flowName, flowNode.getName());
    }

    @Test(expected = NotFoundException.class)
    public void should_delete_flow_success() throws Exception {
        // given:
        String flowName = "flow1";
        setCurrentUser(mockUser);
        Flow flow = flowHelper.createFlowWithYml(flowName, "yml/demo_flow2.yaml");

        // when: perform http delete
        Flow deleted = Flow.parse(mockMvc.perform(delete("/flows/" + flowName))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(), Flow.class);

        // then: flow and flow yml been deleted
        Assert.assertEquals(flow, deleted);
        Assert.assertNull(flowDao.get(flowName));
        Assert.assertNull(ymlDao.get(flowName));
        flowService.find(flowName);
    }

    @Test
    public void should_get_env_value() throws Throwable {
        MockHttpServletRequestBuilder request = get("/flows/" + flowName + "/env")
            .param("key", "FLOW_STATUS")
            .param("editable", "false");

        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        Assert.assertEquals("{\"FLOW_STATUS\":\"PENDING\"}", result.getResponse().getContentAsString());
    }

    @Test
    public void should_return_true_if_flow_name_exist() throws Throwable {
        MockHttpServletRequestBuilder request = get("/flows/" + flowName + "/exist")
            .contentType(MediaType.APPLICATION_JSON);

        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        BooleanValue existed = BooleanValue.parse(body, BooleanValue.class);
        Assert.assertNotNull(existed);
        Assert.assertEquals(true, existed.getValue());
    }

    @Test
    public void should_response_4xx_if_flow_name_format_invalid() throws Throwable {
        String flowName = "hello*gmail";

        MockHttpServletRequestBuilder request = get("/flows/" + flowName + "/exist")
            .contentType(MediaType.APPLICATION_JSON);

        MvcResult result = this.mockMvc.perform(request)
            .andExpect(status().is4xxClientError())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        ResponseError error = ResponseError.parse(body, ResponseError.class);
        Assert.assertNotNull(error);
        Assert.assertEquals(error.getMessage(), "Illegal node name: hello*gmail");
    }

    @Test
    public void should_response_false_if_flow_name_not_exist() throws Throwable {
        // given:
        String flowName = "not-exit";

        // when:
        MockHttpServletRequestBuilder request = get("/flows/" + flowName + "/exist")
            .contentType(MediaType.APPLICATION_JSON);

        MvcResult mvcResult = this.mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn();

        // then:
        String response = mvcResult.getResponse().getContentAsString();
        BooleanValue existed = BooleanValue.parse(response, BooleanValue.class);
        Assert.assertNotNull(existed);
        Assert.assertEquals(false, existed.getValue());
    }

    @Test
    public void should_response_4xx_if_env_not_defined_for_load_file_content() throws Throwable {
        // when: send request to load content
        MvcResult result = this.mockMvc.perform(get("/flows/" + flowName + "/yml/load"))
            .andExpect(status().is4xxClientError())
            .andReturn();

        // then:
        String response = result.getResponse().getContentAsString();
        ResponseError error = ResponseError.parse(response, ResponseError.class);
        Assert.assertTrue(error.getMessage().contains("Missing git settings"));
    }

    @Test
    public void should_get_yml_file_content() throws Throwable {
        // given:
        String yml = getResourceContent("yml/demo_flow.yaml");
        Node flow = nodeService.find(flowName).root();
        setFlowToReady(flow);
        nodeService.updateByYml(PathUtil.build(flowName), yml);

        // when:
        MvcResult result = mockMvc.perform(get("/flows/" + flowName + "/yml"))
            .andExpect(status().isOk())
            .andReturn();
        String content = result.getResponse().getContentAsString();

        // then:
        Assert.assertEquals(yml, content);
    }

    @Test
    public void should_return_empty_string_if_no_yml_content() throws Throwable {
        // when:
        MockHttpServletRequestBuilder request = get("/flows/" + flowName + "/yml");
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        // then:
        Assert.assertEquals(StringUtil.EMPTY, content);
    }

    @Test
    public void should_download_yml_success() throws Exception {
        // given:
        String yml = getResourceContent("yml/demo_flow.yaml");
        performRequestWith200Status(post("/flows/" + flowName + "/yml").content(yml));

        // when: download yml
        MvcResult result = mockMvc.perform(get("/flows/" + flowName + "/yml/download").contentType(MediaType.ALL))
            .andExpect(status().isOk())
            .andReturn();
        Assert.assertNotNull(result.getResponse());

        // then:
        Assert.assertEquals(yml, result.getResponse().getContentAsString());
    }
}
