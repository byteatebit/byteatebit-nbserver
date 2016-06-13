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
import com.byteatebit.nbserver.IComputeTaskScheduler;
import com.byteatebit.nbserver.NbServiceConfig;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleNbService implements ISimpleNbService {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleNbService.class);

    protected final NbServiceConfig config;
    protected final IDistributionStreamFactory<ISelectorTask> distributionStreamFactory;
    protected final ITerminableComputeTaskSchedulerFactory taskSchedulerFactory;

    protected ISelectorRegistrarBalancer selectorRegistrarBalancer;
    protected List<ISelectorTask> selectorTasks;
    protected ITerminableComputeTaskScheduler taskScheduler;
    protected IComputeTaskScheduler scheduler;
    protected ExecutorService ioExecutorService;
    protected boolean started;

    public SimpleNbService(ObjectBuilder builder) {
        this.config = builder.config;
        this.selectorTasks = new ArrayList<>(config.getNumThreads());
        this.distributionStreamFactory = builder.distributionStreamFactory;
        this.taskSchedulerFactory = builder.taskSchedulerFactory;
        this.started = false;
    }

    public synchronized void start() throws IOException {
        if (started)
            throw new IllegalStateException("Service was previously started");
        this.started = true;

        // set the number of io threads
        int numIoThreads = config.getNumIoThreads() == 0
                ? config.getNumThreads()
                : config.getNumIoThreads();
        // remaining threads are general task threads
        int numTaskThreads = config.getNumThreads() - numIoThreads;
        numTaskThreads = numTaskThreads > 0 ? numTaskThreads : config.getNumThreads();
        // created the io and general task schedulers
        ioExecutorService = Executors.newFixedThreadPool(config.getNumThreads());
        taskScheduler = taskSchedulerFactory.create(numTaskThreads);
        // avoids exposing the shutdown() method to tasks
        scheduler = (runnable, exceptionHandler) -> taskScheduler.schedule(runnable, exceptionHandler);

        try {
            // create the selector tasks
            for (int i = 0; i < numIoThreads; i++) {
                ISelectorTask selectorTask = SelectorTask.Builder.builder()
                        .withSelector(Selector.open())
                        .withTaskScheduler(taskScheduler)
                        .withSelectorTimeoutMs(config.getSelectorTimeoutMs())
                        .withDefaultIoTaskTimeoutMs(config.getIoTaskTimeoutMs())
                        .withIoTaskTimeoutCheckPeriodMs(config.getIoTaskTimeoutCheckPeriodMs())
                        .build();
                selectorTasks.add(selectorTask);
            }
            // create the io task distribution stream given the selector to thread affinity
            selectorRegistrarBalancer = new SelectorRegistrarBalancer(distributionStreamFactory.create(selectorTasks));
        } catch (IOException | RuntimeException e) {
            // if any operation after opening the server socketChannel fails, close the
            // socketChannel and select if they have been initialized to avoid
            // throwing an exception from the start() method and leaving hanging resources
            silentlyStop();
            throw e;
        }
        selectorTasks.forEach(selectorTask -> ioExecutorService.execute(selectorTask));
    }

    public ISelectorRegistrarBalancer getSelectorRegistrarBalancer() {
        return selectorRegistrarBalancer;
    }

    public IComputeTaskScheduler getScheduler() {
        return scheduler;
    }

    protected void silentlyStop() {
        try {
            stop();
        } catch (IOException e) {
            LOG.error("Exception stopping the non-blocking IO service", e);
        }
    }

    public synchronized void stop() throws IOException {
        selectorTasks.forEach(selectorTasks ->
                selectorTasks.stop(config.getMaxShutdownWaitMs() - (long) .1 * config.getMaxShutdownWaitMs()));
        boolean tasksStopped = waitForSelectorTasksToComplete(config.getMaxShutdownWaitMs());
        if (!tasksStopped)
            LOG.error("Not all tasks completed within " + config.getMaxShutdownWaitMs() + " ms.  Forcing shutdown");
        // either all tasks have completed or the outstanding tasks need to be force stopped
        if (ioExecutorService != null)
            ioExecutorService.shutdownNow();
        if (taskScheduler != null)
            taskScheduler.shutdown();
    }

    protected boolean waitForSelectorTasksToComplete(long maxWaitTime) {
        long beginTime = System.currentTimeMillis();
        boolean anyTasksExecuting;
        while ((anyTasksExecuting = selectorTasks.stream().anyMatch(task -> !task.isStopped()))
                && System.currentTimeMillis() - beginTime < maxWaitTime) {
            try {
                Thread.sleep(config.getSelectorTimeoutMs());
            } catch (InterruptedException e) {
                LOG.error("Shutdown sleep interrupted", e);
            }
        }
        return !anyTasksExecuting;
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

        protected NbServiceConfig config;
        protected IDistributionStreamFactory distributionStreamFactory = new RandomDistributionStreamFactory<>();
        protected ITerminableComputeTaskSchedulerFactory taskSchedulerFactory = new FixedThreadPoolTaskSchedulerFactory();

        public T withConfig(NbServiceConfig config) {
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

        public SimpleNbService build() {
            Preconditions.checkNotNull(config, "config cannot be null");
            Preconditions.checkNotNull(distributionStreamFactory, "distributionStreamFactory cannot be null");
            Preconditions.checkNotNull(taskSchedulerFactory, "taskSchedulerFactory cannot be null");
            Preconditions.checkNotNull(config.getMaxShutdownWaitMs(), "config.maxShutdownWaitMs cannot be null");
            Preconditions.checkNotNull(config.getNumThreads(), "config.numThreads cannot be null");
            Preconditions.checkArgument(config.getNumThreads() > 0, "config.numThreads must be > 0");
            Preconditions.checkNotNull(config.getIoTaskTimeoutMs(), "config.getIoTaskTimeoutMs cannot be null");
            Preconditions.checkArgument(config.getIoTaskTimeoutMs() > 0, "config.taskTimeoutMs must be > 0");
            Preconditions.checkNotNull(config.getIoTaskTimeoutCheckPeriodMs(), "config.getIoTaskTimeoutCheckPeriodMs cannot be null");
            Preconditions.checkArgument(config.getIoTaskTimeoutCheckPeriodMs() > 0, "config.getIoTaskTimeoutCheckPeriodMs must be > 0");
            Preconditions.checkNotNull(config.getNumIoThreads(), "config.numIoThreads cannot be null");
            Preconditions.checkArgument(config.getNumIoThreads() >= 0, "config.numIoThreads must be >= 0");
            return new SimpleNbService(this);
        }
    }
}
