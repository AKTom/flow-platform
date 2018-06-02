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

package com.flow.platform.agent;

import com.flow.platform.agent.config.AgentConfig;
import com.flow.platform.cmd.Log;
import com.flow.platform.cmd.LogListener;
import com.flow.platform.domain.AgentPath;
import com.flow.platform.domain.v1.Cmd;
import com.flow.platform.util.CommandUtil.Unix;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import lombok.extern.log4j.Log4j2;
import org.glassfish.tyrus.client.ClientManager;

/**
 * Record log to $HOME/agent-log/{cmd id}.out.zip
 * Send log via web socket if real time log enabled and ws url provided
 * <p>
 *
 * @author gy@fir.im
 */
@Log4j2
public class LogEventHandler implements LogListener {

    private final static String REALTIME_LOG_SPLITTER = "#";

    private final static Path DEFAULT_LOG_PATH = AgentConfig.getInstance().getLogDir();

    private final Cmd cmd;

    private Path stdoutLogPath;

    private FileOutputStream stdoutLogStream;

    private ZipOutputStream stdoutLogZipStream;

    private Session wsSession;

    public LogEventHandler(Cmd cmd) {
        this.cmd = cmd;

        // init zip log path
        try {
            initZipLogFile(this.cmd);
        } catch (IOException e) {
            log.error("Fail to init cmd log file", e);
        }

        AgentConfig config = AgentConfig.getInstance();

        if (!config.getUrl().hasWebsocket()) {
            return;
        }

        // init rabbit queue
        try {
            initWebSocketSession(config.getUrl().getWebsocket(), 10);
        } catch (Throwable warn) {
            wsSession = null;
            log.warn("Fail to web socket: " + config.getUrl().getWebsocket() + ": " + warn.getMessage());
        }
    }

    @Override
    public void onLog(Log log) {
        LogEventHandler.log.debug(log.toString());

        sendRealTimeLog(log);

        // write stdout & stderr
        writeZipStream(stdoutLogZipStream, log.getContent());
    }

    private void sendRealTimeLog(Log log) {
        if (Objects.isNull(wsSession)) {
            return;
        }

        try {
            String format = websocketLogFormat(log);
            wsSession.getBasicRemote().sendText(format);
            LogEventHandler.log.debug("Log sent: {}", format);
        } catch (Throwable e) {
            LogEventHandler.log.warn("Fail to send real time log to queue");
        }
    }

    @Override
    public void onFinish() {
        // close socket io
        closeWebSocket();

        if (closeZipAndFileStream(stdoutLogZipStream, stdoutLogStream)) {
            renameAndUpload(stdoutLogPath, Log.Type.STDOUT);
        }
    }

    public String websocketLogFormat(Log log) {
        AgentPath agentPath = AgentConfig.getInstance().getPath();

        return new StringBuilder(100)
            .append(cmd.getType()).append(REALTIME_LOG_SPLITTER)
            .append(log.getNumber()).append(REALTIME_LOG_SPLITTER)
            .append(agentPath.getZone()).append(REALTIME_LOG_SPLITTER)
            .append(agentPath.getName()).append(REALTIME_LOG_SPLITTER)
            .append(cmd.getId()).append(REALTIME_LOG_SPLITTER)
            .append(log.getContent())
            .toString();
    }

    private void initWebSocketSession(String url, int wsConnectionTimeout) throws Exception {
        CountDownLatch wsLatch = new CountDownLatch(1);
        ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        ClientManager client = ClientManager.createClient();

        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig endpointConfig) {
                wsSession = session;
                wsLatch.countDown();
            }
        }, cec, new URI(url));

        if (!wsLatch.await(wsConnectionTimeout, TimeUnit.SECONDS)) {
            throw new TimeoutException("Web socket connection timeout");
        }
    }


    private void renameAndUpload(Path logPath, Log.Type logType) {
        // rename xxx.out.tmp to xxx.out.zip and renameAndUpload to server
        if (Files.exists(logPath)) {
            try {
                Path target = Paths.get(DEFAULT_LOG_PATH.toString(), getLogFileName(cmd, logType, false));
                Files.move(logPath, target);

                // delete if uploaded
                ReportManager reportManager = ReportManager.getInstance();

                if (reportManager.cmdLogUploadSync(cmd.getId(), target)) {
                    Files.deleteIfExists(target);
                }
            } catch (IOException warn) {
                log.warn("Exception while move update log name from temp: {}", warn.getMessage());
            }
        }
    }

    private boolean closeZipAndFileStream(final ZipOutputStream zipStream, final FileOutputStream fileStream) {
        try {
            if (zipStream != null) {
                zipStream.flush();
                zipStream.closeEntry();
                zipStream.close();
                return true;
            }
        } catch (IOException e) {
            log.error("Exception while close zip stream", e);
        } finally {
            try {
                if (fileStream != null) {
                    fileStream.close();
                }
            } catch (IOException e) {
                log.error("Exception while close log file", e);
            }
        }
        return false;
    }

    private void closeWebSocket() {
        if (wsSession != null) {
            try {
                wsSession.close();
            } catch (IOException e) {
                log.warn("Exception while close web socket session");
            }
        }
    }

    private void writeZipStream(final ZipOutputStream stream, final String log) {
        if (stream == null) {
            return;
        }

        // write to zip output stream
        try {
            stream.write(log.getBytes());
            stream.write(Unix.LINE_SEPARATOR.getBytes());
        } catch (IOException e) {
            LogEventHandler.log.warn("Log cannot write: " + log);
        }
    }

    private void initZipLogFile(final Cmd cmd) throws IOException {
        // init log directory
        try {
            Files.createDirectories(DEFAULT_LOG_PATH);
        } catch (FileAlreadyExistsException ignore) {
            log.warn("Log path {} already exist", DEFAULT_LOG_PATH);
        }

        // init zipped log file for tmp
        Path stdoutPath = Paths.get(DEFAULT_LOG_PATH.toString(), getLogFileName(cmd, Log.Type.STDOUT, true));
        Files.deleteIfExists(stdoutPath);

        stdoutLogPath = Files.createFile(stdoutPath);

        // init zip stream for stdout log
        stdoutLogStream = new FileOutputStream(stdoutLogPath.toFile());
        stdoutLogZipStream = new ZipOutputStream(stdoutLogStream);
        ZipEntry outEntry = new ZipEntry(cmd.getId() + ".out");
        stdoutLogZipStream.putNextEntry(outEntry);
    }

    private String getLogFileName(Cmd cmd, Log.Type logType, boolean isTemp) {
        String logTypeSuffix = logType == Log.Type.STDERR ? ".err" : ".out";
        String tempSuffix = isTemp ? ".tmp" : ".zip";

        // replace / with - since cmd id may includes slash which the same as dir path
        return cmd.getId().replace(Unix.PATH_SEPARATOR.charAt(0), '-') + logTypeSuffix + tempSuffix;
    }
}
