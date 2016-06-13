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
import com.google.common.base.Splitter;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class ReadDelimitedMessageTask {

    private static final Logger LOG = LoggerFactory.getLogger(ReadDelimitedMessageTask.class);

    protected final ByteBuffer byteBuffer;
    protected final int maxMessageSize;
    protected final Charset charset;
    protected final Splitter splitter;
    protected final ByteArrayOutputStream messageBuffer;

    public ReadDelimitedMessageTask(ObjectBuilder builder) {
        this.byteBuffer = builder.byteBuffer;
        this.maxMessageSize = builder.maxMessageSize;
        this.charset = builder.charset;
        this.splitter = Splitter.onPattern(builder.delimiter);
        this.messageBuffer = new ByteArrayOutputStream(byteBuffer.capacity());
    }

    public void readMessages(IChannelSelectorRegistrar channelSelectorRegistrar,
                             Consumer<List<String>> callback,
                             Consumer<Exception> exceptionHandler) {
        try {
            channelSelectorRegistrar.register(SelectionKey.OP_READ,
                    (selectionKey -> read(selectionKey, callback, exceptionHandler)),
                    (selectionKey, ops) -> exceptionHandler.accept(new TimeoutException("read timed out")));
        } catch (Exception e) {
            exceptionHandler.accept(e);
        }
    }

    public void readMessages(ISelectorRegistrar selectorRegistrar,
                             SelectableChannel channel,
                             Consumer<List<String>> callback,
                             Consumer<Exception> exceptionHandler) {
        readMessages((ops, ioTask, ioTimeoutTask) -> selectorRegistrar.register(channel, ops, ioTask, ioTimeoutTask),
                callback, exceptionHandler);
    }

    protected void read(SelectionKey selectionKey,
                        Consumer<List<String>> callback,
                        Consumer<Exception> exceptionHandler) {
        try {
            if (!selectionKey.isValid() || !selectionKey.isReadable())
                return;
            byteBuffer.clear();
            int bytesRead = ((ReadableByteChannel) selectionKey.channel()).read(byteBuffer);
            if (bytesRead < 0) {
                LOG.warn("End of stream reached.  Deregistering interest in reads on the selection key invoking exception handler.");
                invokeExceptionHandler(selectionKey, exceptionHandler, new EOFException("End of stream"));
                return;
            }
            byteBuffer.flip();
            if (byteBuffer.remaining() + messageBuffer.size() > maxMessageSize) {
                LOG.error("Max message size of " + maxMessageSize + " bytes exceeded.  Deregistering interest in reads on the selection key invoking exception handler.");
                invokeExceptionHandler(selectionKey, exceptionHandler,
                        new IllegalStateException("Max message size of " + maxMessageSize + " bytes exceeded"));
                return;
            }
            while (byteBuffer.hasRemaining())
                messageBuffer.write(byteBuffer.get());
            String messagesString = messageBuffer.toString(charset);
            messageBuffer.reset();
            List<String> messages = new ArrayList<>();
            for (String message : splitter.split(messagesString))
                messages.add(message);
            messageBuffer.write(messages.remove(messages.size() - 1).getBytes(charset));
            if (!messages.isEmpty()) {
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_READ);
                callback.accept(messages);
            }
        } catch (Exception e) {
            LOG.error("ReadDelimitedMessage task failed", e);
            invokeExceptionHandler(selectionKey, exceptionHandler, e);
        }
    }

    protected void invokeExceptionHandler(SelectionKey selectionKey,
                                          Consumer<Exception> exceptionHandler,
                                          Exception exception) {
        selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_READ);
        try {
            exceptionHandler.accept(exception);
        } catch (Exception e) {
            LOG.error("Read exception handler failed", e);
        }
    }

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends BaseBuilder<T> {

        protected ByteBuffer byteBuffer;
        protected Integer maxMessageSize = 1024 * 1024;
        protected String delimiter;
        protected Charset charset = StandardCharsets.UTF_8;

        public T withByteBuffer(ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            return self();
        }

        public T withMaxMessageSize(Integer maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
            return self();
        }

        public T withDelimiter(String delimiter) {
            this.delimiter = delimiter;
            return self();
        }

        public T withCharset(Charset charset) {
            this.charset = charset;
            return self();
        }

        public ReadDelimitedMessageTask build() {
            Preconditions.checkNotNull(byteBuffer, "byteBuffer cannot be null");
            Preconditions.checkArgument(byteBuffer.capacity() > 0, "bufferSize.capacity must be > 0");
            Preconditions.checkNotNull(maxMessageSize, "maxMessageSize cannot be null");
            Preconditions.checkArgument(maxMessageSize > 0, "maxMessageSize must be > 0");
            Preconditions.checkNotNull(charset, "charset cannot be null");
            Preconditions.checkNotNull(delimiter, "delimiter cannot be null");
            return new ReadDelimitedMessageTask(this);
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
