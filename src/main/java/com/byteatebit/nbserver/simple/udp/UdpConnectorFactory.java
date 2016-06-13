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

import com.byteatebit.common.builder.BaseBuilder;
import com.byteatebit.nbserver.IComputeTaskScheduler;
import com.byteatebit.nbserver.IDatagramChannelHandler;
import com.byteatebit.nbserver.simple.*;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

public class UdpConnectorFactory implements IConnectorFactory {

    protected final List<SocketOptionValue> socketOptionValues;
    protected final IDatagramChannelHandler datagramChannelHandler;

    protected UdpConnectorFactory(ObjectBuilder builder) {
        this.socketOptionValues = builder.socketOptionValues;
        this.datagramChannelHandler = builder.datagramChannelHandler;
    }

    @Override
    public IConnector create(ISelectorRegistrarBalancer selectorRegistrarBalancer,
                             IComputeTaskScheduler taskScheduler,
                             SocketAddress listenAddress) throws IOException {
        Preconditions.checkNotNull(selectorRegistrarBalancer, "selectorRegistrarBalancer cannot be null");
        Preconditions.checkNotNull(taskScheduler, "taskScheduler cannot be null");
        Preconditions.checkNotNull(listenAddress, "listenAddress cannot be null");
        DatagramChannel datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        for (SocketOptionValue socketOptionValue : socketOptionValues)
            datagramChannel.setOption(socketOptionValue.getOption(), socketOptionValue.getValue());
        datagramChannel.bind(listenAddress);
        SelectionKey datagramChannelSelectionKey =
                datagramChannelHandler.accept(new NbContext(selectorRegistrarBalancer.getSelectorRegistrar(), taskScheduler), datagramChannel);
        return new UdpConnector(datagramChannelSelectionKey, datagramChannel);
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

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends BaseBuilder<T> {

        protected List<SocketOptionValue> socketOptionValues = new ArrayList<>();

        protected IDatagramChannelHandler datagramChannelHandler;
        protected IDatagramMessageHandlerFactory datagramMessageHandlerFactory;
        protected Integer maxDatagramReceiveSize = 2048;

        public <V> T addSocketOptionValue(SocketOption<V> option, V value) {
            socketOptionValues.add(new SocketOptionValue(option, value));
            return self();
        }

        public T withDatagramChannelHandler(IDatagramChannelHandler datagramChannelHandler) {
            this.datagramChannelHandler = datagramChannelHandler;
            return self();
        }

        public T withDatagramMessageHandlerFactory(IDatagramMessageHandlerFactory datagramMessageHandlerFactory) {
            this.datagramMessageHandlerFactory = datagramMessageHandlerFactory;
            return self();
        }

        /**
         * This value is only used with the default datagramChannelHandler
         * @param maxDatagramReceiveSize
         * @return
         */
        public T withMaxDatagramReceiveSize(Integer maxDatagramReceiveSize) {
            this.maxDatagramReceiveSize = maxDatagramReceiveSize;
            return self();
        }

        public UdpConnectorFactory build() {
            if (datagramChannelHandler == null) {
                Preconditions.checkNotNull(datagramMessageHandlerFactory, "datagramMessageHandlerFactory cannot be null if datagramChannelHandler is not supplied");
                Preconditions.checkNotNull(maxDatagramReceiveSize, "maxDatagramReceiveSize cannot be null");
                Preconditions.checkArgument(maxDatagramReceiveSize > 0, "maxDatagramReceiveSize must be > 0");
                datagramChannelHandler = new UdpDatagramChannelHandler(datagramMessageHandlerFactory, maxDatagramReceiveSize);
            } else {
                Preconditions.checkArgument(datagramMessageHandlerFactory != null,
                        "if a datagramMessageHandler is supplied, the datagramMessageHandlerFactory will not be used");
            }
            return new UdpConnectorFactory(this);
        }


    }
}
