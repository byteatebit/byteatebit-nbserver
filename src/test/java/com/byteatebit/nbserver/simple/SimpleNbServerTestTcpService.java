/*
 * Copyright (c) 2016 byteatebit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.byteatebit.nbserver.simple;

import com.byteatebit.common.util.CollectionsUtil;
import com.byteatebit.nbserver.INbContext;
import com.byteatebit.nbserver.SocketChannelTask;
import com.byteatebit.nbserver.task.ReadDelimitedMessageTask;
import com.byteatebit.nbserver.task.WriteMessageTask;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public class SimpleNbServerTestTcpService extends SocketChannelTask {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleNbServerTestTcpService.class);

    public static final String QUIT_CMD             = "quit";
    public static final String SLEEP_CMD            = "sleep";
    public static final String MESSAGE_DELIMITER    = "\r?\n";

    protected final ReadDelimitedMessageTask readDelimitedMessageTask;
    protected final WriteMessageTask writeMessageTask;


    public SimpleNbServerTestTcpService(INbContext nbContext, SocketChannel socket) {
        super(nbContext, socket);
        this.readDelimitedMessageTask = ReadDelimitedMessageTask.Builder.builder()
                .withDelimiter(MESSAGE_DELIMITER)
                .withByteBuffer(ByteBuffer.allocate(2048))
                .build();
        this.writeMessageTask = WriteMessageTask.Builder.builder()
                .withByteBuffer(ByteBuffer.allocate(2048))
                .build();
    }

    public void readMessage() {
        Consumer<List<String>> messageCallback = (messages) -> nbContext.schedule(() -> echo(messages), this::handleException);
        readDelimitedMessageTask.readMessages(this::register, messageCallback, this::handleException);
    }

    public void handleException(Exception exception) {
        LOG.error("Exception handler invoked.  Closing socketChannel channel", exception);
        IOUtils.closeQuietly(socketChannel);
    }

    public void echo(List<String> commands) {
        if (commands.isEmpty()) {
            readMessage();
            return;
        }
        echo(commands.get(0), () -> nbContext.schedule(() -> echo(CollectionsUtil.cdr(commands)), this::handleException));
    }

    public void echo(String command, Runnable callback) {
        LOG.info("EchoService recieved command '" + command + "'");
        if (command.equals(QUIT_CMD)) {
            IOUtils.closeQuietly(socketChannel);
            LOG.info("EchoService shutting down due to receipt of " + QUIT_CMD + " command");
        } else if (command.equals(SLEEP_CMD)) {
            try {
                LOG.info("Sleeping for 5 seconds");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOG.error("sleep interrupted", e);
            }
            writeMessageTask.writeMessage("Slept for 5 seconds\n".getBytes(StandardCharsets.UTF_8),
                    this::register, callback, this::handleException);
        } else
            writeMessageTask.writeMessage((command + "\n").getBytes(StandardCharsets.UTF_8),
                    this::register, callback, this::handleException);
    }

}
