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

import com.byteatebit.nbserver.DatagramChannelTask;
import com.byteatebit.nbserver.DatagramMessage;
import com.byteatebit.nbserver.INbContext;
import com.byteatebit.nbserver.simple.udp.IDatagramMessageHandler;
import com.byteatebit.nbserver.task.WriteDatagramTask;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class SimpleNbServerTestUdpService extends DatagramChannelTask implements IDatagramMessageHandler {

    public static final String QUIT_CMD             = "quit";

    private static final Logger LOG = LoggerFactory.getLogger(SimpleNbServerTestUdpService.class);

    protected final WriteDatagramTask writeDatagramTask;

    public SimpleNbServerTestUdpService(INbContext nbContext,
                                        DatagramChannel datagramChannel) {
        super(nbContext, datagramChannel);
        this.writeDatagramTask = WriteDatagramTask.Builder.builder()
                .withNbContext(nbContext)
                .withDatagramChannel(datagramChannel)
                .build();
    }

    @Override
    public void accept(DatagramMessage message) {
        String payload = new String(message.getMessage(), StandardCharsets.UTF_8);
        LOG.info("received datagram message: " + payload);
        if (QUIT_CMD.equals(payload.toLowerCase())) {
            LOG.info("received quit command.  Will close the datagramChannel");
            writeDatagramTask.writeMessage(new DatagramMessage(message.getSocketAddress(), "goodbye".getBytes(StandardCharsets.UTF_8)),
                    () -> { LOG.info("Closing datagramChannel"); IOUtils.closeQuietly(datagramChannel); }, this::handleException);
            return;
        }
        writeDatagramTask.writeMessage(message, this::messageWritten, this::handleException);
    }

    public void handleException(Exception exception) {
        LOG.error("Exception handler invoked.  Closing socketChannel channel", exception);
        IOUtils.closeQuietly(datagramChannel);
    }

    protected void messageWritten() {
        LOG.info("message write callback invoked");
    }
}
