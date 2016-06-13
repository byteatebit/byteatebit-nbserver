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

package com.byteatebit.nbserver.simple.udp;

import com.byteatebit.nbserver.IDatagramChannelHandler;
import com.byteatebit.nbserver.INbContext;
import com.byteatebit.nbserver.ISocketChannelHandler;
import com.byteatebit.nbserver.simple.NbContext;
import com.byteatebit.nbserver.task.ReadDatagramTask;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class UdpDatagramChannelHandler implements IDatagramChannelHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UdpDatagramChannelHandler.class);

    protected final IDatagramMessageHandlerFactory datagramMessageHandlerFactory;
    protected final int maxDatagramReceiveSize;

    public UdpDatagramChannelHandler(IDatagramMessageHandlerFactory datagramMessageHandlerFactory, int maxDatagramReceiveSize) {
        this.datagramMessageHandlerFactory = datagramMessageHandlerFactory;
        this.maxDatagramReceiveSize = maxDatagramReceiveSize;
    }

    @Override
    public SelectionKey accept(INbContext nbContext, DatagramChannel datagramChannel) {
        IDatagramMessageHandler messageHandler = datagramMessageHandlerFactory.create(nbContext, datagramChannel);
        ReadDatagramTask readDatagramTask = ReadDatagramTask.Builder.builder()
                .withNbContext(nbContext)
                .withDatagramChannel(datagramChannel)
                .withBufferSize(maxDatagramReceiveSize)
                .build();
        Consumer<Exception> exceptionHandler = (e) -> {
            LOG.error("Message read failed.  Closing datagramChannel");
            IOUtils.closeQuietly(datagramChannel);
        };
        return nbContext.register(datagramChannel, SelectionKey.OP_READ,
                selectionKey -> readDatagramTask.readMessage(selectionKey, messageHandler::accept, exceptionHandler),
                (selectionKey, ops) -> exceptionHandler.accept(new TimeoutException("write timed out")), -1);
    }

}
