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
import com.byteatebit.nbserver.IOTask;
import com.byteatebit.nbserver.IOTimeoutTask;
import com.byteatebit.nbserver.SelectorException;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SelectorTask implements ISelectorTask {

    private static final Logger LOG = LoggerFactory.getLogger(SelectorTask.class);

    protected final Selector selector;
    protected final Long selectorTimeoutMs;
    protected final Long defaultIoTaskTimeoutMs;
    protected final Long ioTaskTimeoutCheckPeriodMs;
    protected final IComputeTaskScheduler taskScheduler;
    protected final IOTimeoutTask defaultIoTimeoutTask;
    protected volatile boolean running;
    protected volatile boolean stopped;
    protected volatile Supplier<Boolean> shouldRun;
    // an array list allocated to hold a copy of the selector keys
    // to avoid Iterator contention
    protected List<SelectionKey> workingSelectorKeys;

    public SelectorTask(ObjectBuilder builder) {
        this.selector = builder.selector;
        this.taskScheduler = builder.taskScheduler;
        this.selectorTimeoutMs = builder.selectorTimeoutMs;
        this.defaultIoTaskTimeoutMs = builder.defaultIoTaskTimeoutMs;
        this.ioTaskTimeoutCheckPeriodMs = builder.ioTaskTimeoutCheckPeriodMs;
        this.running = true;
        this.stopped = false;
        this.shouldRun = () -> true;
        this.defaultIoTimeoutTask = (selectionKey, task) -> {
            LOG.error("Executing default IO timeout handler.  Cancelling selection key and closing channel");
            selectionKey.cancel();
            selectionKey.channel().close();
        };
        this.workingSelectorKeys = new ArrayList<>(1000);
    }

    @Override
    public SelectionKey register(SelectableChannel channel, int ops, IOTask nioTask) {
        return register(channel, ops, nioTask, null);
    }

    @Override
    public SelectionKey register(SelectableChannel channel, int ops, IOTask nioTask, IOTimeoutTask timeoutTask) {
        return register(channel, ops, nioTask, timeoutTask, defaultIoTaskTimeoutMs);
    }

    @Override
    public SelectionKey register(SelectableChannel channel,
                                 int ops,
                                 IOTask nioTask,
                                 IOTimeoutTask timeoutTask,
                                 long timeoutMs) {
        Preconditions.checkNotNull(channel, "channel cannot be null");
        Preconditions.checkNotNull(nioTask, "nioTask cannot be null");
        IOTimeoutTask ioTimeoutTask = timeoutTask == null ? defaultIoTimeoutTask : timeoutTask;
        if (LOG.isDebugEnabled())
            LOG.debug("registering channel " + channel + "for ops " + ops + " and task " + nioTask);
        SelectionKey key = null;
        NioSelectionKeyEntry selectionKeyEntry = null;
        synchronized (this) {
            try {
                key = channel.keyFor(selector);
                if (key != null) {
                    key.interestOps(key.interestOps() | ops);
                    selectionKeyEntry = (NioSelectionKeyEntry) key.attachment();
                } else {
                    selectionKeyEntry = new NioSelectionKeyEntry();
                    key = channel.register(selector, ops, selectionKeyEntry);
                }
            } catch (ClosedChannelException e) {
                throw new SelectorException(e);
            }
            selectionKeyEntry.setTask(nioTask, ioTimeoutTask, ops, timeoutMs);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Total number of selection keys: " + selector.keys().size());
        return key;
    }

    @Override
    public void run() {
        long lastTaskTimeoutCheck = System.currentTimeMillis();
        while (shouldRun.get()) {
            // check for tasks without any activity in the
            // max time allowed
            if (System.currentTimeMillis() - lastTaskTimeoutCheck >= ioTaskTimeoutCheckPeriodMs) {
                handleTimedOutTasks();
                lastTaskTimeoutCheck = System.currentTimeMillis();
            }
            // select tasks for which the underlying channel is
            // ready for the appropriate IO operations
            try {
                if (selectorTimeoutMs < 1) {
                    int numReadyKeys = selector.selectNow();
                    if (numReadyKeys == 0)
                        Thread.yield();
                } else {
                    selector.select(selectorTimeoutMs);
                }
            } catch (ClosedSelectorException e) {
                LOG.error("selector closed, shutting down selector task", e);
                running = false;
            } catch (Exception e) {
                LOG.error("Exception thrown while on selector.select call", e);
            }
            // execute selected tasks
            executeIoTasksOnSelectedKeys();
        }
        try {
            selector.close();
        } catch (Exception e) {
            LOG.error("Selector threw exception on close", e);
        }
        this.stopped = true;
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    protected void handleTimedOutTasks() {
        synchronized (this) {
            workingSelectorKeys.clear();
            workingSelectorKeys.addAll(selector.keys());
        }
        workingSelectorKeys.stream()
                .forEach(selectionKey -> {
                    try {
                        ((NioSelectionKeyEntry) selectionKey.attachment()).executeTimedOutTasks(selectionKey);
                    } catch (CancelledKeyException e) {
                        LOG.info("Selection key was cancelled during the task timeout check");
                    } catch (Exception e) {
                        LOG.error("Execution of timeout tasks for selection key failed.  Cancelling selection key and closing channel", e);
                        selectionKey.cancel();
                        IOUtils.closeQuietly(selectionKey.channel());
                    }
                });
    }

    protected void executeIoTasksOnSelectedKeys() {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey selectionKey = iterator.next();
            iterator.remove();
            try {
                NioSelectionKeyEntry nioTaskEntry = (NioSelectionKeyEntry) selectionKey.attachment();
                if (nioTaskEntry == null) {
                    LOG.error("The nio task is unexpectedly null for selectionKey " + selectionKey);
                    return;
                }
                nioTaskEntry.executeTasks(selectionKey);
            } catch (Exception e) {
                LOG.error("Failed to execute the consumer attached to selectionKey "
                        + selectionKey + ".  Cancelling the key and closing the underlying channel", e);
                try {
                    selectionKey.channel().close();
                } catch (IOException e1) {
                    LOG.error("selection key channel error on close", e);
                }
                selectionKey.cancel();
            }
        }
    }

    @Override
    public synchronized void stop(long maxWaitMs) {
        if (!running)
            return;
        running = false;
        long beginTime = System.currentTimeMillis();
        shouldRun = () -> {
            long elaspsed = System.currentTimeMillis() - beginTime;
            return !selector.keys().isEmpty() && elaspsed < maxWaitMs;
        };
    }

    protected static class NioSelectionKeyEntry {

        private volatile NioOperationTask readTask;
        private volatile NioOperationTask writeTask;
        private volatile NioOperationTask connectTask;
        private volatile NioOperationTask acceptTask;

        private NioOperationTask selectorThreadReadTask;
        private NioOperationTask selectorThreadWriteTask;
        private NioOperationTask selectorThreadConnectTask;
        private NioOperationTask selectorThreadAcceptTask;

        /**
         * This method may be invoked from different threads via the SelectorTask
         * @param task
         * @param timeoutTask
         * @param ops
         * @param timeoutMs
         */
        private void setTask(IOTask task, IOTimeoutTask timeoutTask, int ops, long timeoutMs) {
            if ((SelectionKey.OP_READ & ops) != 0)
                readTask = new NioOperationTask(task, timeoutTask, timeoutMs);
            if ((SelectionKey.OP_WRITE & ops) != 0)
                writeTask = new NioOperationTask(task, timeoutTask, timeoutMs);
            if ((SelectionKey.OP_CONNECT & ops) != 0)
                connectTask = new NioOperationTask(task, timeoutTask, timeoutMs);
            if ((SelectionKey.OP_ACCEPT & ops) != 0)
                acceptTask = new NioOperationTask(task, timeoutTask, timeoutMs);
        }

        /**
         * This method can only be called by the SelectorTask thread
         */
        private void setSelectorThreadTasks() {
            selectorThreadReadTask = readTask;
            selectorThreadWriteTask = writeTask;
            selectorThreadConnectTask = connectTask;
            selectorThreadAcceptTask = acceptTask;
        }

        /**
         * This method can only be called by the SelectorTask thread
         * @param selectionKey
         * @throws IOException
         */
        private void executeTasks(SelectionKey selectionKey) throws IOException {
            setSelectorThreadTasks();
            if (selectionKey.isValid() && selectionKey.isAcceptable() && selectorThreadAcceptTask != null) {
                selectorThreadAcceptTask.getNioTask().accept(selectionKey);
                selectorThreadAcceptTask.touch();
            }
            if (selectionKey.isValid() && selectionKey.isConnectable() && selectorThreadConnectTask != null) {
                selectorThreadConnectTask.getNioTask().accept(selectionKey);
                selectorThreadConnectTask.touch();
            }
            if (selectionKey.isValid() && selectionKey.isReadable() && selectorThreadReadTask != null) {
                selectorThreadReadTask.getNioTask().accept(selectionKey);
                selectorThreadReadTask.touch();
            }
            if (selectionKey.isValid() && selectionKey.isWritable() && selectorThreadWriteTask != null) {
                selectorThreadWriteTask.getNioTask().accept(selectionKey);
                selectorThreadWriteTask.touch();
            }
        }

        /**
         * This method can only be called by the SelectorTask thread.  If another thread
         * cancels the selection key while the timeout evaluation or task execution is in progress,
         * this method will throw a CancelledKeyException.
         * @param selectionKey
         */
        private void executeTimedOutTasks(SelectionKey selectionKey) throws IOException {
            int interestOps = selectionKey.interestOps();
            setSelectorThreadTasks();
            if (selectionKey.isValid() && (SelectionKey.OP_READ & interestOps) != 0 && selectorThreadReadTask != null && selectorThreadReadTask.hasTimedOut())
                selectorThreadReadTask.getTimeoutTask().accept(selectionKey, SelectionKey.OP_READ);
            if (selectionKey.isValid() && (SelectionKey.OP_WRITE & interestOps) != 0 && selectorThreadWriteTask != null && selectorThreadWriteTask.hasTimedOut())
                selectorThreadWriteTask.getTimeoutTask().accept(selectionKey, SelectionKey.OP_READ);
            if (selectionKey.isValid() && (SelectionKey.OP_CONNECT & interestOps) != 0 && selectorThreadConnectTask != null && selectorThreadConnectTask.hasTimedOut())
                selectorThreadConnectTask.getTimeoutTask().accept(selectionKey, SelectionKey.OP_READ);
            if (selectionKey.isValid() && (SelectionKey.OP_ACCEPT & interestOps) != 0 && selectorThreadAcceptTask != null && selectorThreadAcceptTask.hasTimedOut())
                selectorThreadAcceptTask.getTimeoutTask().accept(selectionKey, SelectionKey.OP_READ);
        }
    }

    protected static class NioOperationTask {

        protected final IOTask nioTask;
        protected final IOTimeoutTask timeoutTask;
        protected final long timeoutMs;
        protected long lastUpdatedTimestamp;

        private NioOperationTask(IOTask nioTask, IOTimeoutTask timeoutTask, long timeoutMs) {
            this.nioTask = nioTask;
            this.timeoutTask = timeoutTask;
            this.timeoutMs = timeoutMs;
            this.lastUpdatedTimestamp = System.currentTimeMillis();
        }

        private IOTask getNioTask() {
            return nioTask;
        }

        private IOTimeoutTask getTimeoutTask() {
            return timeoutTask;
        }

        private void touch() {
            lastUpdatedTimestamp = System.currentTimeMillis();
        }

        private boolean hasTimedOut() {
            return timeoutMs > 0 && System.currentTimeMillis() - lastUpdatedTimestamp >= timeoutMs;
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

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends BaseBuilder<T> {

        protected Selector selector;
        protected Long selectorTimeoutMs;
        protected Long defaultIoTaskTimeoutMs;
        protected Long ioTaskTimeoutCheckPeriodMs;
        protected IComputeTaskScheduler taskScheduler;

        public T withSelector(Selector selector) {
            this.selector = selector;
            return self();
        }

        public T withTaskScheduler(IComputeTaskScheduler taskScheduler) {
            this.taskScheduler = taskScheduler;
            return self();
        }

        public T withSelectorTimeoutMs(Long selectorTimeoutMs) {
            this.selectorTimeoutMs = selectorTimeoutMs;
            return self();
        }

        public T withDefaultIoTaskTimeoutMs(Long defaultIoTaskTimeoutMs) {
            this.defaultIoTaskTimeoutMs = defaultIoTaskTimeoutMs;
            return self();
        }

        public T withIoTaskTimeoutCheckPeriodMs(Long ioTaskTimeoutCheckPeriodMs) {
            this.ioTaskTimeoutCheckPeriodMs = ioTaskTimeoutCheckPeriodMs;
            return self();
        }

        public SelectorTask build() {
            Preconditions.checkNotNull(selector, "selector cannot be null");
            Preconditions.checkNotNull(taskScheduler, "taskScheduler cannot be null");
            Preconditions.checkNotNull(selectorTimeoutMs, "selectorTimeoutMs cannot be null");
            Preconditions.checkArgument(selectorTimeoutMs >= 0, "selectorTimeoutMs must be >= 0");
            Preconditions.checkNotNull(defaultIoTaskTimeoutMs, "defaultIoTaskTimeoutMs cannot be null");
            Preconditions.checkArgument(defaultIoTaskTimeoutMs >= 0, "defaultIoTaskTimeoutMs must be >= 0");
            Preconditions.checkNotNull(ioTaskTimeoutCheckPeriodMs, "ioTaskTimeoutCheckPeriodMs cannot be null");
            Preconditions.checkArgument(ioTaskTimeoutCheckPeriodMs >= 0, "ioTaskTimeoutCheckPeriodMs must be >= 0");
            return new SelectorTask(this);
        }
    }
}
