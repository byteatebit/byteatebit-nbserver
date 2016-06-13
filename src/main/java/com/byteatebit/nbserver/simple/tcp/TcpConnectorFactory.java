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

package com.byteatebit.nbserver.simple.tcp;

import com.byteatebit.common.builder.BaseBuilder;
import com.byteatebit.nbserver.IComputeTaskScheduler;
import com.byteatebit.nbserver.IOTimeoutTask;
import com.byteatebit.nbserver.ISocketChannelHandler;
import com.byteatebit.nbserver.ITcpConnectHandler;
import com.byteatebit.nbserver.simple.*;
import com.google.common.base.Preconditions;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;

public class TcpConnectorFactory implements IConnectorFactory {

    private static final Logger LOG = LoggerFactory.getLogger(TcpConnectorFactory.class);

    protected final List<SocketOptionValue> socketOptionValues;
    protected final int maxServerSocketBacklog;
    protected final ITcpConnectHandler connectHandler;

    protected TcpConnectorFactory(ObjectBuilder builder) {
        this.socketOptionValues = new ArrayList<>(builder.socketOptionValues);
        this.maxServerSocketBacklog = builder.maxServerSocketBacklog;
        this.connectHandler = builder.connectHandler;
    }

    @Override
    public IConnector create(ISelectorRegistrarBalancer selectorRegistrarBalancer,
                             IComputeTaskScheduler taskScheduler,
                             SocketAddress listenAddress) throws IOException {
        Preconditions.checkNotNull(selectorRegistrarBalancer, "selectorRegistrarBalancer cannot be null");
        Preconditions.checkNotNull(taskScheduler, "taskScheduler cannot be null");
        Preconditions.checkNotNull(listenAddress, "listenAddress cannot be null");
        // open the server socketChannel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        IOTimeoutTask timeoutTask = (selectionKey, ops) -> {
            LOG.error("selectionKey timeout");
            selectionKey.cancel();
            IOUtils.closeQuietly(selectionKey.channel());
        };
        for (SocketOptionValue socketOptionValue : socketOptionValues)
            serverSocketChannel.setOption(socketOptionValue.getOption(), socketOptionValue.getValue());
        SelectionKey serverSocketSelectionKey = selectorRegistrarBalancer.getSelectorRegistrar().register(serverSocketChannel,
                SelectionKey.OP_ACCEPT,
                selectionKey -> connectHandler.accept(new NbContext(selectorRegistrarBalancer.getSelectorRegistrar(), taskScheduler),
                        selectionKey, serverSocketChannel), timeoutTask, -1);
        serverSocketChannel.socket().bind(listenAddress, maxServerSocketBacklog);
        return new TcpConnector(serverSocketSelectionKey, serverSocketChannel);
    }

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends BaseBuilder<T> {

        protected List<SocketOptionValue> socketOptionValues = new ArrayList<>();
        protected Integer maxServerSocketBacklog = 0;
        protected ITcpConnectHandler connectHandler;
        protected ISocketChannelHandler socketChannelHandler;

        public <V> T addSocketOptionValue(SocketOption<V> option, V value) {
            socketOptionValues.add(new SocketOptionValue(option, value));
            return self();
        }

        public T withMaxServerSocketBacklog(Integer maxServerSocketBacklog) {
            this.maxServerSocketBacklog = maxServerSocketBacklog;
            return self();
        }

        public T withConnectHandler(ITcpConnectHandler connectTask) {
            this.connectHandler = connectTask;
            return self();
        }

        public T withConnectedSocketTask(ISocketChannelHandler connectedSocketTask) {
            this.socketChannelHandler = connectedSocketTask;
            return self();
        }

        public TcpConnectorFactory build() {
            if (connectHandler == null) {
                Preconditions.checkNotNull(socketChannelHandler, "socketChannelHandler cannot be null if connectHandler is not supplied");
                connectHandler = new TcpConnectHandler(socketChannelHandler);
            } else {
                Preconditions.checkArgument(socketChannelHandler != null, "if the connectHandler is supplied, the socketChannelHandler will not be used");
            }
            Preconditions.checkNotNull(maxServerSocketBacklog, "maxServerSocketBacklog cannot be null");
            return new TcpConnectorFactory(this);
        }

    }

    public static class Builder extends ObjectBuilder<Builder> {
        @Override
        public Builder self() {
            return this;
        }

        public static Builder builder() {
            return new Builder();
        }

    }
}
