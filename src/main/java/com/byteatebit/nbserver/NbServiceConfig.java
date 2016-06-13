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

package com.byteatebit.nbserver;

import com.byteatebit.common.builder.BaseBuilder;
import com.google.common.base.MoreObjects;

public class NbServiceConfig {

    protected Integer numThreads;
    protected Integer numIoThreads;
    protected Long ioTaskTimeoutCheckPeriodMs;
    protected Long ioTaskTimeoutMs;
    protected Long maxShutdownWaitMs;
    protected Long selectorTimeoutMs;

    protected NbServiceConfig(ObjectBuilder builder) {
        this.numThreads = builder.numThreads;
        this.numIoThreads = builder.numIoThreads;
        this.ioTaskTimeoutMs = builder.ioTaskTimeoutMs;
        this.ioTaskTimeoutCheckPeriodMs = builder.ioTaskTimeoutCheckPeriodMs;
        this.maxShutdownWaitMs = builder.maxShutdownWaitMs;
        this.selectorTimeoutMs = builder.selectorTimeoutMs;
    }

    /**
     * Returns the maximum amount of time the non-blocking IO service should wait for the
     * IO thread(s) to complete during shutdown.  The default value is 1000.
     * @return maximum thread shutdown wait time in milliseconds
     */
    public Long getMaxShutdownWaitMs() {
        return maxShutdownWaitMs;
    }

    /**
     * Returns the amount of time a thread should block on the
     * {@link java.nio.channels.Selector#select(long)} call.
     * The default value is 100.
     * @return selector select() timeout in milliseconds
     */
    public Long getSelectorTimeoutMs() {
        return selectorTimeoutMs;
    }

    /**
     * Returns the number of threads to use in the server.  This value should
     * not exceed the the number of cores on the machine.  The default is 1.
     * @return the number of server threads
     */
    public Integer getNumThreads() {
        return numThreads;
    }

    /**
     * Returns the period between checks for IO tasks
     * older than {@link #getIoTaskTimeoutMs()}
     * @return the timeout check period in milliseconds
     */
    public Long getIoTaskTimeoutCheckPeriodMs() {
        return ioTaskTimeoutCheckPeriodMs;
    }

    /**
     * Returns the max idle time allowed for an IO task before
     * the task is cancelled and the underlying channel closed.
     * @return IO task timeout in milliseconds
     */
    public Long getIoTaskTimeoutMs() {
        return ioTaskTimeoutMs;
    }

    /**
     * The number of threads to dedicate to IO operations.
     * numIoThreads cannot exceed numThreads.
     * If numIoThreads == numThreads or numIoThreads == 0, then both IO and non-IO
     * tasks will be serviced by the same threads.
     * @return
     */
    public Integer getNumIoThreads() {
        return numIoThreads;
    }

    protected MoreObjects.ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("numThreads", numThreads)
                .add("numIoThreads", numIoThreads)
                .add("ioTaskTimeoutMs", ioTaskTimeoutMs)
                .add("ioTaskTimeoutCheckPeriodMs", ioTaskTimeoutCheckPeriodMs)
                .add("maxShutdownWaitMs", maxShutdownWaitMs)
                .add("selectorTimeoutMs", selectorTimeoutMs);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
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

    public abstract static class ObjectBuilder<T extends ObjectBuilder<T>> extends BaseBuilder<T> {

        protected Long ioTaskTimeoutMs = 30000L;
        protected Long ioTaskTimeoutCheckPeriodMs = 500L;
        protected Integer numIoThreads = 1;
        protected Long maxShutdownWaitMs = 2000L;
        protected Long selectorTimeoutMs = 1L;
        protected Integer numThreads = 2;

        public T withNumIoThreads(Integer numIoThreads) {
            this.numIoThreads = numIoThreads;
            return self();
        }

        public T withIoTaskTimeoutMs(Long ioTaskTimeoutMs) {
            this.ioTaskTimeoutMs = ioTaskTimeoutMs;
            return self();
        }

        public T withIoTaskTimeoutCheckPeriodMs(Long ioTaskTimeoutCheckPeriodMs) {
            this.ioTaskTimeoutCheckPeriodMs = ioTaskTimeoutCheckPeriodMs;
            return self();
        }

        public T withNumThreads(Integer numThreads) {
            this.numThreads = numThreads;
            return self();
        }

        public T withMaxServerShutdownWaitMs(Long maxServerShutdownWaitMs) {
            this.maxShutdownWaitMs = maxServerShutdownWaitMs;
            return self();
        }

        public T withSelectorTimeoutMs(Long selectorTimeoutMs) {
            this.selectorTimeoutMs = selectorTimeoutMs;
            return self();
        }

        public NbServiceConfig build() {
            return new NbServiceConfig(this);
        }
    }
}
