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

package com.flow.platform.cc.consumer;

import com.flow.platform.cc.config.QueueCCConfig;
import com.flow.platform.agent.manager.exception.AgentErr;
import com.flow.platform.cc.service.CmdDispatchService;
import com.flow.platform.cc.service.CmdCCService;
import com.flow.platform.core.exception.IllegalParameterException;
import com.flow.platform.core.exception.IllegalStatusException;
import com.flow.platform.core.queue.PriorityMessage;
import com.flow.platform.core.util.ThreadUtil;
import com.flow.platform.domain.Cmd;
import com.flow.platform.domain.CmdStatus;
import com.flow.platform.queue.PlatformQueue;
import com.flow.platform.queue.QueueListener;
import com.flow.platform.util.zk.ZkException;
import javax.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cmd queue consumer to handle cmd from RabbitMQ
 *
 * @author gy@fir.im
 */
@Log4j2
@Component
public class CmdQueueConsumer implements QueueListener<PriorityMessage> {

    private final static long RETRY_WAIT_TIME = 1000; // in millis

    @Value("${queue.cmd.idle_agent.period}")
    private Integer idleAgentPeriod; // period for check idle agent in seconds

    @Value("${queue.cmd.idle_agent.timeout}")
    private Integer idleAgentTimeout; // timeout if no idle agent in seconds

    @Autowired
    private CmdCCService cmdService;

    @Autowired
    private CmdDispatchService cmdDispatchService;

    @Autowired
    private PlatformQueue<PriorityMessage> cmdQueue;

    @PostConstruct
    public void init() {
        cmdQueue.register(this);
    }

    @Override
    public void onQueueItem(PriorityMessage message) {
        String cmdId = new String(message.getBody());
        log.trace("Receive a cmd queue item: {}", cmdId);

        Cmd cmd = cmdService.find(cmdId);

        try {
            cmdDispatchService.dispatch(cmd);
        } catch (IllegalParameterException e) {
            log.warn("Illegal cmd id: {}", e.getMessage());
        } catch (IllegalStatusException e) {
            log.warn("Illegal cmd status: {}", e.getMessage());
        } catch (AgentErr.NotAvailableException | AgentErr.NotFoundException | ZkException.NotExitException e) {
            if (cmd.getRetry() <= 0) {
                return;
            }

            cmd = cmdService.find(cmdId);

            // do not re-enqueue if cmd been stopped or killed
            if (cmd.getStatus() == CmdStatus.STOPPED || cmd.getStatus() == CmdStatus.KILLED) {
                return;
            }

            // reset cmd status to pending, record num of retry
            int retry = cmd.getRetry() - 1;
            cmd.setStatus(CmdStatus.PENDING);
            cmd.setRetry(retry);
            cmdService.save(cmd);

            // do not retry
            if (retry <= 0) {
                return;
            }

            // retry the message
            retry(message);

        } catch (Throwable e) {
            log.error("Unexpected exception", e);
        }
    }

    private void retry(final PriorityMessage message) {
        message.setPriority(QueueCCConfig.MAX_PRIORITY);
        cmdQueue.enqueue(message);
        ThreadUtil.sleep(RETRY_WAIT_TIME);
    }
}