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
package com.flow.platform.api.service.job;

import static com.flow.platform.api.domain.job.NodeStatus.FAILURE;
import static com.flow.platform.api.domain.job.NodeStatus.STOPPED;
import static com.flow.platform.api.domain.job.NodeStatus.SUCCESS;
import static com.flow.platform.api.domain.job.NodeStatus.TIMEOUT;

import com.flow.platform.api.dao.job.JobDao;
import com.flow.platform.api.dao.job.JobNumberDao;
import com.flow.platform.api.domain.CmdCallbackQueueItem;
import com.flow.platform.api.domain.EnvObject;
import com.flow.platform.api.domain.Flow;
import com.flow.platform.api.domain.job.Job;
import com.flow.platform.api.domain.job.JobCategory;
import com.flow.platform.api.domain.job.JobNumber;
import com.flow.platform.api.domain.job.JobStatus;
import com.flow.platform.api.domain.job.NodeResult;
import com.flow.platform.api.domain.job.NodeStatus;
import com.flow.platform.api.domain.job.NodeTag;
import com.flow.platform.api.domain.node.Node;
import com.flow.platform.api.domain.node.NodeTree;
import com.flow.platform.api.domain.node.Yml;
import com.flow.platform.api.domain.user.User;
import com.flow.platform.api.envs.EnvUtil;
import com.flow.platform.api.envs.FlowEnvs;
import com.flow.platform.api.envs.GitEnvs;
import com.flow.platform.api.envs.JobEnvs;
import com.flow.platform.api.events.JobStatusChangeEvent;
import com.flow.platform.api.script.GroovyRunner;
import com.flow.platform.api.service.GitService;
import com.flow.platform.api.service.node.EnvService;
import com.flow.platform.api.service.node.NodeService;
import com.flow.platform.api.util.CommonUtil;
import com.flow.platform.core.domain.Page;
import com.flow.platform.core.domain.Pageable;
import com.flow.platform.core.exception.FlowException;
import com.flow.platform.core.exception.IllegalParameterException;
import com.flow.platform.core.exception.IllegalStatusException;
import com.flow.platform.core.exception.NotFoundException;
import com.flow.platform.core.exception.UnsupportedException;
import com.flow.platform.core.queue.PriorityMessage;
import com.flow.platform.core.service.ApplicationEventService;
import com.flow.platform.domain.Cmd;
import com.flow.platform.domain.CmdInfo;
import com.flow.platform.domain.CmdStatus;
import com.flow.platform.domain.CmdType;
import com.flow.platform.queue.PlatformQueue;
import com.flow.platform.util.DateUtil;
import com.flow.platform.util.ExceptionUtil;
import com.flow.platform.util.http.HttpURL;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import groovy.util.ScriptException;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author yh@firim
 */
@Log4j2
@Service
public class JobServiceImpl extends ApplicationEventService implements JobService {

    private final Integer createSessionRetryTimes = 5;

    @Value("${task.job.toggle.execution_timeout}")
    private Boolean isEnableJobTimeOut;

    @Value("${task.job.toggle.execution_create_session_duration}")
    private Long jobTimeOutOnCreateSession;

    @Value("${task.job.toggle.execution_running_duration}")
    private Long jobTimeOutOnRunning;

    @Value(value = "${domain.api}")
    private String apiDomain;

    @Autowired
    private JobDao jobDao;

    @Autowired
    private JobNumberDao jobNumberDao;

    @Autowired
    private NodeResultService nodeResultService;

    @Autowired
    private JobNodeService jobNodeService;

    @Autowired
    private GitService gitService;

    @Autowired
    private EnvService envService;

    @Autowired
    private CmdService cmdService;

    @Autowired
    private PlatformQueue<PriorityMessage> cmdCallbackQueue;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public Job find(String flowName, Long number) {
        Job job = jobDao.get(flowName, number);
        return find(job);
    }

    @Override
    public Job find(BigInteger jobId) {
        Job job = jobDao.get(jobId);
        return find(job);
    }

    @Override
    public Job find(String sessionId) {
        return jobDao.get(sessionId);
    }

    @Override
    public String findYml(String path, Long number) {
        Job job = find(path, number);
        return jobNodeService.find(job).getFile();
    }

    @Override
    public List<Job> list(List<String> paths, boolean latestOnly) {
        if (latestOnly) {
            return jobDao.latestByPath(paths);
        }
        return jobDao.listByPath(paths);
    }

    @Override
    public Page<Job> list(List<String> paths, boolean latestOnly, Pageable pageable) {
        if (latestOnly) {
            return jobDao.latestByPath(paths, pageable);
        }
        return jobDao.listByPath(paths, pageable);
    }

    @Override
    @Transactional(noRollbackFor = FlowException.class)
    public Job create(Flow flow, JobCategory eventType, Map<String, String> envs, User creator) {
        Objects.requireNonNull(flow, "Flow must be defined");
        Objects.requireNonNull(eventType, "Eventtype must be defined");
        Objects.requireNonNull(creator, "User must be defined while create job");


        // TODO: Verify yml content

        // verify required envs for create job
        if (!EnvUtil.hasRequired(flow, REQUIRED_ENVS)) {
            throw new IllegalStatusException("Missing required env variables for flow " + flow.getName());
        }

        // increate flow job number
        JobNumber jobNumber = jobNumberDao.get(flow.getName());
        if (Objects.isNull(jobNumber)) {
            throw new IllegalStatusException("Job number not been initialized");
        }

        jobNumber = jobNumberDao.increase(flow.getName());

        // create job
        Job job = new Job(CommonUtil.randomId());
        job.setNodePath(flow.getName());
        job.setNodeName(flow.getName());
        job.setNumber(jobNumber.getNumber());
        job.setCategory(eventType);
        job.setCreatedBy(creator.getEmail());
        job.setCreatedAt(ZonedDateTime.now());
        job.setUpdatedAt(ZonedDateTime.now());

        // setup job env variables
        job.putEnv(FlowEnvs.FLOW_NAME, flow.getName());
        job.putEnv(JobEnvs.FLOW_JOB_BUILD_CATEGORY, eventType.name());
        job.putEnv(JobEnvs.FLOW_JOB_BUILD_NUMBER, job.getNumber().toString());
        job.putEnv(JobEnvs.FLOW_JOB_LOG_PATH, logUrl(job));
        job.putEnv(JobEnvs.FLOW_API_DOMAIN, apiDomain);
        job.putEnv(JobEnvs.FLOW_JOB_ID, job.getId().toString());

        EnvUtil.merge(flow.getEnvs(), job.getEnvs(), true);
        EnvUtil.merge(envs, job.getEnvs(), true);

        //save job
        return jobDao.save(job);
    }

    @Override
    public void callback(CmdCallbackQueueItem cmdQueueItem) {
        BigInteger jobId = cmdQueueItem.getJobId();
        Cmd cmd = cmdQueueItem.getCmd();
        Job job = jobDao.get(jobId);

        // if not found job, re enqueue
        if (job == null) {
            throw new NotFoundException("job");
        }

        if (Job.FINISH_STATUS.contains(job.getStatus())) {
            log.trace("Reject cmd callback since job {} in finish status", job.getId());
            return;
        }

        if (cmd.getType() == CmdType.CREATE_SESSION) {
            onCreateSessionCallback(job, cmd);
            return;
        }

        if (cmd.getType() == CmdType.RUN_SHELL) {
            String path = cmd.getExtra();
            if (Strings.isNullOrEmpty(path)) {
                throw new IllegalParameterException("Node path is required for cmd RUN_SHELL callback");
            }

            onRunShellCallback(path, cmd, job);
            return;
        }

        if (cmd.getType() == CmdType.DELETE_SESSION) {
            log.trace("Session been deleted for job: {}", cmdQueueItem.getJobId());
            return;
        }

        throw new UnsupportedException("Cmd type " + cmd.getType() + " not supported");
    }

    @Override
    @Transactional
    public void delete(String path) {
        List<BigInteger> jobIds = jobDao.findJobIdsByPath(path);
        // TODO :  Late optimization and paging jobIds
        if (jobIds.size() > 0) {
            //first clear agent jobs
            stopAllJobs(path);

            jobNodeService.delete(jobIds);
            nodeResultService.delete(jobIds);
            jobDao.deleteJob(path);
        }
    }

    private void stopAllJobs(String path) {
        log.trace("before delete flow, first stop all jobs");
        List<Job> jobs = jobDao.listByPath(Arrays.asList(path));
        List<Job> sessionCreateJobs = new LinkedList<>();
        List<Job> runningJobs = new LinkedList<>();

        for (Job job : jobs) {
            if (job.getStatus() == JobStatus.SESSION_CREATING) {
                sessionCreateJobs.add(job);
            }

            if (job.getStatus() == JobStatus.RUNNING) {
                runningJobs.add(job);
            }
        }

        // first to stop session create job
        for (Job sessionCreateJob : sessionCreateJobs) {
            stop(path, sessionCreateJob.getNumber());
        }

        // last to stop running job
        for (Job runningJob : runningJobs) {
            stop(path, runningJob.getNumber());
        }
        log.trace("before delete flow, finish stop all jobs");
    }

    /**
     * Collect env variables and run node
     *
     * @param node job node's script and record cmdId and sync send http
     */
    private void run(Node node, Job job) {
        if (Objects.isNull(node)) {
            throw new IllegalParameterException("Cannot run node with null value");
        }

        NodeTree tree = jobNodeService.get(job);
        if (!tree.canRun(node.getPath())) {
            // run next node
            Node next = tree.next(node.getPath());
            run(next, job);
            return;
        }

        // build all require env variables
        EnvObject envVars = buildEnvsBeforeStart(node, tree, job);

        // run condition script
        if (!executeConditionScript(job, node, envVars)) {
            Node next = tree.next(node.getPath());
            if (Objects.isNull(next)) {
                stopJob(job);
                return;
            }

            run(next, job);
            return;
        }

        // to run node with customized cmd id
        try {
            NodeResult nodeResult = nodeResultService.find(node.getPath(), job.getId());
            cmdService.runShell(job, node, nodeResult.getCmdId(), envVars);
        } catch (IllegalStatusException e) {
            CmdInfo rawCmd = (CmdInfo) e.getData();
            rawCmd.setStatus(CmdStatus.EXCEPTION);
            nodeResultService.updateStatusByCmd(job, node, Cmd.convert(rawCmd), e.getMessage());
        }
    }

    private EnvObject buildEnvsBeforeStart(Node node, NodeTree tree, Job job) {
        // create env vars instance which will pass to agent
        EnvObject envVars = new EnvObject();
        envVars.putAll(job.getEnvs());

        // pass root node output to current node
        NodeResult rootResult = nodeResultService.find(tree.root().getPath(), job.getId());
        envVars.putAll(rootResult.getOutputs());

        // pass last step node status
        Node prev = tree.prev(node.getPath());
        if (prev != null) {
            NodeResult prevResult = nodeResultService.find(prev.getPath(), job.getId());
            if (prevResult != null) {
                envVars.putEnv(JobEnvs.FLOW_JOB_LAST_STATUS, prevResult.getStatus().toString());
            }
        }

        // pass current node envs
        envVars.putAll(node.getEnvs());

        // format credential variables
        Map<String, String> credentialEnvs = keepNewLineForCredentialEnvs(node);
        envVars.putAll(credentialEnvs);

        return envVars;
    }

    private Boolean executeConditionScript(Job job, Node node, EnvObject envVars) {
        String conditionScript = node.getConditionScript();
        if (Strings.isNullOrEmpty(conditionScript)) {
            return true;
        }

        GroovyRunner<Boolean> runner = GroovyRunner.create();
        for (Map.Entry<String, String> entry : envVars.getEnvs().entrySet()) {
            runner.putVariable(entry.getKey(), entry.getValue());
        }

        Boolean result = null;
        String errorMessage = null;

        try {
            result = runner.setTimeOut(10)
                .setExecutor(taskExecutor)
                .setScript(node.getConditionScript())
                .runAndReturnBoolean();

            errorMessage = "Step '" + node.getName() + "' condition not match";
        } catch (ScriptException e) {
            result = false;
            errorMessage = "Step '" + node.getName() + "' condition script error: " + e.getMessage();
        }

        // return true when condition is passed
        if (result != null && result) {
            return true;
        }

        // set current node result to STOPPED status
        NodeResult rootResult = nodeResultService.find(job.getNodePath(), job.getId());
        Cmd failureCmd = new Cmd();
        failureCmd.setStatus(CmdStatus.STOPPED);
        nodeResultService.updateStatusByCmd(job, node, failureCmd, errorMessage);
        return false;
    }

    /**
     * keep new line to private key and public key
     * @param node
     * @return Map
     */
    private Map<String, String> keepNewLineForCredentialEnvs(Node node) {
        Map<String, String> map = new HashMap<>(2);

        String privateKey = node.getEnv(GitEnvs.FLOW_GIT_SSH_PRIVATE_KEY);
        String publicKey = node.getEnv(GitEnvs.FLOW_GIT_SSH_PUBLIC_KEY);

        if (!Strings.isNullOrEmpty(privateKey) && !Strings.isNullOrEmpty(publicKey)) {
            map.put(GitEnvs.FLOW_GIT_SSH_PRIVATE_KEY.name(), privateKey);
            map.put(GitEnvs.FLOW_GIT_SSH_PUBLIC_KEY.name(), publicKey);
            EnvUtil.keepNewlineForEnv(map, null);
            return map;
        }

        return Collections.emptyMap();
    }

    /**
     * Create session callback
     */
    private void onCreateSessionCallback(Job job, Cmd cmd) {
        if (cmd.getStatus() != CmdStatus.SENT) {

            if (cmd.getRetry() > 1) {
                log.trace("Create session failure but retrying: {}", cmd.getStatus().getName());
                return;
            }

            final String errMsg = "Create session failure with cmd status: " + cmd.getStatus().getName();
            log.warn(errMsg);

            job.setFailureMessage(errMsg);
            updateJobStatusAndSave(job, JobStatus.FAILURE);
            return;
        }

        // run step
        NodeTree tree = jobNodeService.get(job);
        if (tree == null) {
            throw new NotFoundException("Cannot fond related node tree for job: " + job.getId());
        }

        // set job properties
        job.setSessionId(cmd.getSessionId());
        job.putEnv(JobEnvs.FLOW_JOB_AGENT_INFO, cmd.getAgentPath().toString());
        updateJobStatusAndSave(job, JobStatus.RUNNING);

        // start run flow from fist node
        run(tree.first(), job);
    }

    /**
     * Run shell callback
     */
    private void onRunShellCallback(String path, Cmd cmd, Job job) {
        NodeTree tree = jobNodeService.get(job);
        Node node = tree.find(path);
        Node next = tree.next(path);

        // bottom up recursive update node result
        NodeResult nodeResult = nodeResultService.updateStatusByCmd(job, node, cmd, null);
        log.debug("Run shell callback for node result: {}", nodeResult);

        // no more node to run and status is not running
        if (Objects.isNull(next) && !nodeResult.isRunning()) {
            stopJob(job);
            return;
        }

        // continue to run if on success status
        if (nodeResult.isSuccess()) {
            run(next, job);
            return;
        }

        // continue to run if allow failure on failure status
        if (nodeResult.isFailure() && nodeResult.getNodeTag() == NodeTag.STEP) {
            Node step = node;

            // run next node if allow failure or final node on current step
            if (step.getAllowFailure() || step.getIsFinal()) {
                run(next, job);
                return;
            }

            // run next final node if exist
            next = tree.nextFinal(step.getPath());
            if (!Objects.isNull(next)) {
                run(next, job);
                return;
            }

            stopJob(job);
        }
    }

    @Override
    public void enqueue(CmdCallbackQueueItem cmdQueueItem, long priority) {
        cmdCallbackQueue.enqueue(PriorityMessage.create(cmdQueueItem.toBytes(), priority));
    }

    @Override
    public Job stop(String path, Long buildNumber) {
        Job runningJob = find(path, buildNumber);
        NodeResult result = runningJob.getRootResult();

        if (result == null) {
            throw new NotFoundException("running job not found node result - " + path);
        }

        if (!result.isRunning()) {
            return runningJob;
        }

        // do not handle job since it is not in running status
        try {
            final Set<NodeStatus> skipStatus = ImmutableSet.of(SUCCESS, FAILURE, TIMEOUT);
            nodeResultService.updateStatus(runningJob, STOPPED, skipStatus);

            stopJob(runningJob);
        } catch (Throwable throwable) {
            String message = "Stop job error: " + ExceptionUtil.findRootCause(throwable);
            throw new IllegalParameterException(message);
        }

        return runningJob;
    }

    @Override
    public Job update(Job job) {
        jobDao.update(job);
        return job;
    }

    /**
     * Update job instance with new job status and boardcast JobStatusChangeEvent
     */
    @Override
    public void updateJobStatusAndSave(Job job, JobStatus newStatus) {
        JobStatus originStatus = job.getStatus();

        if (originStatus == newStatus) {
            jobDao.update(job);
            return;
        }

        //if job has finish not to update status
        if (Job.FINISH_STATUS.contains(originStatus)) {
            return;
        }

        log.debug("Job '{}' status is changed to: {}", job.getId(), newStatus);
        job.setStatus(newStatus);
        jobDao.update(job);

        this.dispatchEvent(new JobStatusChangeEvent(this, job, originStatus, newStatus));
    }

    @Transactional(noRollbackFor = FlowException.class)
    public void createJobNodesAndCreateSession(Job job, String yml) {
        //create yml snapshot for job
        jobNodeService.save(job, yml);

        // set root node env from yml to job env
        Node root = jobNodeService.get(job).root();
        EnvUtil.merge(root.getEnvs(), job.getEnvs(), true);

        // init for node result and set to job object
        List<NodeResult> resultList = nodeResultService.create(job);
        NodeResult rootResult = resultList.remove(resultList.size() - 1);
        job.setRootResult(rootResult);
        job.setChildrenResult(resultList);

        // to create agent session for job
        try {
            String sessionId = cmdService.createSession(job, createSessionRetryTimes);
            job.setSessionId(sessionId);
            updateJobStatusAndSave(job, JobStatus.SESSION_CREATING);
        } catch (IllegalStatusException e) {
            job.setFailureMessage(e.getMessage());
            updateJobStatusAndSave(job, JobStatus.FAILURE);
        }
    }

    private class OnYmlSuccess implements Consumer<Yml> {

        private final Job job;

        private final String path;

        private final Consumer<Job> onJobCreated;

        public OnYmlSuccess(Job job, Consumer<Job> onJobCreated) {
            this.job = job;
            this.path = job.getNodePath();
            this.onJobCreated = onJobCreated;
        }

        @Override
        public void accept(Yml yml) {
//            log.trace("Yml content has been loaded for path: " + path);
//            Node root = nodeService.find(PathUtil.rootPath(path)).root();
//
//            // set git commit info to job env
//            if (job.getCategory() == JobCategory.MANUAL
//                || job.getCategory() == JobCategory.SCHEDULER
//                || job.getCategory() == JobCategory.API) {
//
//                try {
//                    GitCommit gitCommit = gitService.latestCommit(root);
//                    Map<String, String> envFromCommit = GitEventEnvConverter.convert(gitCommit);
//                    EnvUtil.merge(envFromCommit, job.getEnvs(), true);
//                    jobDao.update(job);
//                } catch (IllegalStatusException exceptionFromGit) {
//                    log.warn(exceptionFromGit.getMessage());
//                }
//            }
//
//            createJobNodesAndCreateSession(job, yml.getFile());
//
//            try {
//                if (onJobCreated != null) {
//                    onJobCreated.accept(job);
//                }
//            } catch (Throwable e) {
//                log.warn("Fail to create job for path {}: {} ", path, ExceptionUtil.findRootCause(e).getMessage());
//            }
        }
    }

    /**
     *
     */
    private class OnYmlError implements Consumer<Throwable> {

        private final Job job;

        public OnYmlError(Job job) {
            this.job = job;
        }

        @Override
        public void accept(Throwable throwable) {
            job.setFailureMessage(throwable.getMessage());
            updateJobStatusAndSave(job, JobStatus.FAILURE);
        }
    }

    /**
     * Update job status by root node result
     */
    private NodeResult setJobStatusByRootResult(Job job) {
        NodeResult rootResult = nodeResultService.find(job.getNodePath(), job.getId());
        JobStatus newStatus = job.getStatus();

        if (rootResult.isFailure()) {
            newStatus = JobStatus.FAILURE;
        }

        if (rootResult.isSuccess()) {
            newStatus = JobStatus.SUCCESS;
        }

        if (rootResult.isStop()) {
            newStatus = JobStatus.STOPPED;
        }

        updateJobStatusAndSave(job, newStatus);
        return rootResult;
    }

    private Job find(Job job) {
        if (Objects.isNull(job)) {
            throw new NotFoundException("Job is not found");
        }

        List<NodeResult> childrenResult = nodeResultService.list(job, true);
        job.setChildrenResult(childrenResult);
        return job;
    }

    /**
     * Update job status and delete agent session
     */
    private void stopJob(Job job) {
        setJobStatusByRootResult(job);
        cmdService.deleteSession(job);
    }

    @Override
    public void checkTimeOut(Job job) {
        if (Job.FINISH_STATUS.contains(job.getStatus())) {
            return;
        }

        // check job is timeout when create session
        if (job.getStatus() == JobStatus.SESSION_CREATING) {
            boolean isTimeOut = DateUtil.isTimeOut(job.getCreatedAt(), ZonedDateTime.now(), jobTimeOutOnCreateSession);
            if (isTimeOut) {
                updateJobAndNodeResultTimeout(job);
            }
            return;
        }

        // check job is timeout when running
        if (job.RUNNING_STATUS.contains(job.getStatus())) {
            boolean isTimeOut = DateUtil.isTimeOut(job.getCreatedAt(), ZonedDateTime.now(), jobTimeOutOnRunning);
            if (isTimeOut) {
                updateJobAndNodeResultTimeout(job);
            }
        }
    }

    @Override
    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 60 * 1000)
    public void checkTimeOutTask() {
        if (!isEnableJobTimeOut) {
            return;
        }

        List<Job> jobs = jobDao.listByStatus(Job.RUNNING_STATUS);
        for (Job job : jobs) {
            checkTimeOut(job);
        }
    }

    private void updateJobAndNodeResultTimeout(Job job) {
        // if job is running , please delete session first
        if (job.getStatus() == JobStatus.RUNNING) {
            try {
                cmdService.deleteSession(job);
            } catch (Throwable e) {
                log.warn("Error on delete session for job {}: {}", job.getId(),
                    ExceptionUtil.findRootCause(e).getMessage());
            }
        }

        updateJobStatusAndSave(job, JobStatus.TIMEOUT);
        nodeResultService.updateStatus(job, NodeStatus.TIMEOUT, NodeResult.FINISH_STATUS);
    }

    private String logUrl(final Job job) {
        return HttpURL.build(apiDomain)
            .append("/jobs/")
            .append(job.getNodeName())
            .append(job.getNumber().toString())
            .append("/log/download").toString();
    }
}
