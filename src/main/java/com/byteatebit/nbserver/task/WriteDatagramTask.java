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

package com.byteatebit.nbserver.task;

import com.byteatebit.nbserver.DatagramChannelTask;
import com.byteatebit.nbserver.DatagramMessage;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class WriteDatagramTask extends DatagramChannelTask {

    private static final Logger LOG = LoggerFactory.getLogger(WriteDatagramTask.class);

    protected final ByteBuffer byteBuffer;
    protected final int maxMessageQueueSize;
    protected final Queue<DatagramMessage> messages;

    public WriteDatagramTask(ObjectBuilder builder) {
        super(builder);
        byteBuffer = ByteBuffer.allocate(builder.bufferSize);
        maxMessageQueueSize = builder.maxMessageQueueSize;
        messages = new LinkedList<>();
    }

    public synchronized boolean writeMessageBlocking(DatagramMessage message,
                                                     Runnable callback,
                                                     Consumer<Exception> exceptionHandler,
                                                     long timeout) {
        while (messages.size() >= maxMessageQueueSize) {
            try {
                wait(timeout);
                return false;
            } catch (InterruptedException e) {
                LOG.error("Thread interrupted", e);
                return false;
            }
        }
        messages.offer(message);
        queueMessage(message, callback, exceptionHandler);
        return true;
    }

    public synchronized boolean writeMessage(DatagramMessage message,
                                             Runnable callback,
                                             Consumer<Exception> exceptionHandler) {
        if (messages.size() >= maxMessageQueueSize)
            return false;
        queueMessage(message, callback, exceptionHandler);
        return true;
    }

    protected void queueMessage(DatagramMessage message,
                                Runnable callback,
                                Consumer<Exception> exceptionHandler) {
        messages.offer(message);
        if (LOG.isDebugEnabled())
            LOG.debug("Queueing message for write '" + new String(message.getMessage()) + "'");
        nbContext.register(datagramChannel, SelectionKey.OP_WRITE,
                selectionKey -> write(selectionKey, callback, exceptionHandler),
                (selectionKey1, ops) -> exceptionHandler.accept(new TimeoutException("write timed out")));
    }

    protected void write(SelectionKey selectionKey,
                         Runnable callback,
                         Consumer<Exception> exceptionHandler) {
        synchronized (this) {
            try {
                if (!selectionKey.isWritable() || messages.isEmpty())
                    return;
                DatagramMessage message = messages.poll();
                byteBuffer.clear();
                byteBuffer.put(message.getMessage());
                byteBuffer.flip();
                datagramChannel.send(byteBuffer, message.getSocketAddress());
            } catch (IOException e) {
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                exceptionHandler.accept(e);
                return;
            }
            if (messages.isEmpty())
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
            notify();
        }
        callback.run();
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

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends DatagramChannelTask.ObjectBuilder<T> {

        protected Integer bufferSize = 2048;
        protected Integer maxMessageQueueSize = 100;
        protected Boolean blocking;

        public T withBufferSize(Integer bufferSize) {
            this.bufferSize = bufferSize;
            return self();
        }

        public T withBlocking(Boolean blocking) {
            this.blocking = blocking;
            return self();
        }

        public T withMaxMessageQueueSize(Integer maxMessageQueueSize) {
            this.maxMessageQueueSize = maxMessageQueueSize;
            return self();
        }

        @Override
        protected void validateArguments() {
            super.validateArguments();
            Preconditions.checkNotNull(bufferSize, "bufferSize cannot be null");
            Preconditions.checkArgument(bufferSize > 0, "bufferSize must be > 0");
            Preconditions.checkNotNull(maxMessageQueueSize, "maxMessageQueueSize cannot be null");
            Preconditions.checkArgument(maxMessageQueueSize > 0, "maxMessageQueueSize must be > 0");
        }

        public WriteDatagramTask build() {
            validateArguments();
            return new WriteDatagramTask(this);
        }

    }

}
