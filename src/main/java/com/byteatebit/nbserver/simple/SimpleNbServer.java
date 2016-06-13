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

import com.byteatebit.common.builder.BaseBuilder;
import com.byteatebit.common.server.IServer;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

public class SimpleNbServer implements IServer {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleNbServer.class);

    protected final SimpleNbServerConfig config;
    protected final IConnectorFactory connectorFactory;
    protected final ISimpleNbService simpleNbService;

    protected boolean started;
    protected IConnector connector;

    public SimpleNbServer(ObjectBuilder builder) {
        this.config = builder.config;
        this.connectorFactory = builder.connectorFactory;
        this.simpleNbService = builder.simpleNbService;
    }

    public synchronized void start() throws IOException {
        LOG.info("Starting SimpleNbServer");
        if (started)
            throw new IllegalStateException("Service was previously started");
        this.started = true;
        simpleNbService.start();
        try {
            InetSocketAddress address = config.getListenAddress() == null
                    ? new InetSocketAddress(config.getListenPort())
                    : new InetSocketAddress(config.getListenAddress(), config.getListenPort());
            connector = connectorFactory.create(simpleNbService.getSelectorRegistrarBalancer(),
                    simpleNbService.getScheduler(), address);
        } catch (IOException | RuntimeException e) {
            try {
                simpleNbService.stop();
            } catch (Exception stopException) {
                LOG.error("Failed to stop the simpleNbService", stopException);
            }
            throw e;
        }
        LOG.info("SimpleNbServer started");
    }

    public synchronized void shutdown() throws IOException {
        LOG.info("Shutting down SimpleNbServer");
        // close the server socketChannel to reject any further connections
        if (connector != null)
            connector.stop();

        // stop the non-blocking IO service
        simpleNbService.stop();

        // finally close the serverSocketChannel
        try {
            if (connector != null)
                connector.close();
        } catch (IOException e) {
            LOG.error("Server socketChannel channel did not shutdown cleanly", e);
        }
        LOG.info("SimpleNbServer stopped");
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

        protected SimpleNbServerConfig config;
        protected IConnectorFactory connectorFactory;
        protected ISimpleNbService simpleNbService;

        // passed through to the SimpleNbService
        protected IDistributionStreamFactory distributionStreamFactory = new RandomDistributionStreamFactory<>();
        protected ITerminableComputeTaskSchedulerFactory taskSchedulerFactory = new FixedThreadPoolTaskSchedulerFactory();

        public T withConfig(SimpleNbServerConfig config) {
            this.config = config;
            return self();
        }

        public T withDistributionStreamFactory(IDistributionStreamFactory distributionStreamFactory) {
            this.distributionStreamFactory = distributionStreamFactory;
            return self();
        }

        public T withTaskSchedulerFactory(ITerminableComputeTaskSchedulerFactory taskSchedulerFactory) {
            this.taskSchedulerFactory = taskSchedulerFactory;
            return self();
        }

        public T withConnectorFactory(IConnectorFactory connectorFactory) {
            this.connectorFactory = connectorFactory;
            return self();
        }

        public SimpleNbServer build() {
            Preconditions.checkNotNull(config, "config cannot be null");
            Preconditions.checkNotNull(connectorFactory, "connectorFactory cannot be null");
            this.simpleNbService = SimpleNbService.Builder.builder()
                    .withConfig(config.getNbServiceConfig())
                    .withDistributionStreamFactory(distributionStreamFactory)
                    .withTaskSchedulerFactory(taskSchedulerFactory)
                    .build();
            return new SimpleNbServer(this);
        }
    }
}
