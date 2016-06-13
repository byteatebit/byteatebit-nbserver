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

package com.byteatebit.nbserver.simple.client;

import com.byteatebit.common.builder.BaseBuilder;
import com.byteatebit.nbserver.*;
import com.byteatebit.nbserver.simple.ISimpleNbService;
import com.byteatebit.nbserver.simple.NbContext;
import com.byteatebit.nbserver.simple.SimpleNbService;
import com.google.common.base.Preconditions;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleNbClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleNbClient.class);

    protected final ISimpleNbService simpleNbService;
    protected final AtomicBoolean initialized = new AtomicBoolean(false);

    protected SimpleNbClient(ObjectBuilder builder) {
        this.simpleNbService = builder.simpleNbService;
    }

    public SimpleNbClient init() throws IOException {
        if (!initialized.get() && initialized.compareAndSet(false, true))
            simpleNbService.start();
        return this;
    }

    public void tcpConnect(String remoteHost,
                           int remotePort,
                           IClientSocketChannelHandler socketChannelHandler,
                           long timeout) throws IOException {
        if (!initialized.get())
            throw new IllegalStateException("SimpleNbClient must first be initialized via init()");
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        InetSocketAddress address = new InetSocketAddress(remoteHost, remotePort);
        INbContext nbContext = new NbContext(simpleNbService.getSelectorRegistrarBalancer()
                .getSelectorRegistrar(), simpleNbService.getScheduler());
        IOTask connectTask = (selectionKey) -> {
            boolean connectionFinished = false;
            try {
                connectionFinished = socketChannel.finishConnect();
            } catch (IOException e) {
                LOG.error("Could not complete socket connection.", e);
            }
            if (!connectionFinished) {
                LOG.error("Could not complete socket connection.  Closing socket channel");
                selectionKey.cancel();
                IOUtils.closeQuietly(socketChannel);
                socketChannelHandler.connectFailed(remoteHost, remotePort);
                return;
            }
            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_CONNECT);
            socketChannelHandler.accept(nbContext, socketChannel);
        };
        IOTimeoutTask timeoutTask = (selectionKey, ops) -> {
            LOG.error("Connect attempt timed out after " + timeout + " ms");
            selectionKey.cancel();
            IOUtils.closeQuietly(socketChannel);
            socketChannelHandler.connectFailed(remoteHost, remotePort);
        };
        nbContext.register(socketChannel, SelectionKey.OP_CONNECT, connectTask, timeoutTask, timeout);
        socketChannel.connect(address);
    }

    @Override
    public void close() throws IOException {
        simpleNbService.stop();
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

        protected NbServiceConfig nbServiceConfig = NbServiceConfig.Builder.builder().build();

        protected ISimpleNbService simpleNbService;

        public T withNbServiceConfig(NbServiceConfig nbServiceConfig) {
            this.nbServiceConfig = nbServiceConfig;
            return self();
        }

        public SimpleNbClient build() {
            Preconditions.checkNotNull(nbServiceConfig, "nbServiceConfig cannot be null");
            simpleNbService = SimpleNbService.Builder.builder()
                    .withConfig(nbServiceConfig)
                    .build();
            return new SimpleNbClient(this);
        }
    }

}
