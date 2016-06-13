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

import com.byteatebit.common.builder.BaseBuilder;
import com.byteatebit.nbserver.IChannelSelectorRegistrar;
import com.byteatebit.nbserver.ISelectorRegistrar;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class WriteMessageTask {

    private static final Logger LOG = LoggerFactory.getLogger(WriteMessageTask.class);

    protected final ByteBuffer byteBuffer;

    public WriteMessageTask(ObjectBuilder builder) {
        byteBuffer = builder.byteBuffer;
    }

    public void writeMessage(byte[] message,
                             IChannelSelectorRegistrar channelSelectorRegistrar,
                             Runnable callback,
                             Consumer<Exception> exceptionHandler) {
        if (LOG.isInfoEnabled())
            LOG.info("writing message '" + message + "'");
        byteBuffer.clear();
        byteBuffer.put(message);
        byteBuffer.flip();
        channelSelectorRegistrar.register(SelectionKey.OP_WRITE,
                selectionKey -> write(selectionKey, callback, exceptionHandler),
                (selectionKey, ops) -> exceptionHandler.accept(new TimeoutException("write timed out")));
    }

    public void writeMessage(byte[] message,
                             ISelectorRegistrar selectorRegistrar,
                             SelectableChannel channel,
                             Runnable callback,
                             Consumer<Exception> exceptionHandler) {
        writeMessage(message,
                (ops, ioTask, ioTimeoutTask) -> selectorRegistrar.register(channel, ops, ioTask, ioTimeoutTask),
                callback, exceptionHandler);
    }

    protected void write(SelectionKey selectionKey,
                         Runnable callback,
                         Consumer<Exception> exceptionHandler) {
        try {
            if (!selectionKey.isWritable())
                return;
            ((WritableByteChannel) selectionKey.channel()).write(byteBuffer);
        } catch (IOException e) {
            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
            exceptionHandler.accept(e);
            return;
        }
        if (!byteBuffer.hasRemaining()) {
            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
            callback.run();
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

        protected ByteBuffer byteBuffer;

        public T withByteBuffer(ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            return self();
        }

        public WriteMessageTask build() {
            Preconditions.checkNotNull(byteBuffer, "byteBuffer cannot be null");
            Preconditions.checkArgument(byteBuffer.capacity() > 0, "bufferSize.capacity must be > 0");
            return new WriteMessageTask(this);
        }

    }

}
