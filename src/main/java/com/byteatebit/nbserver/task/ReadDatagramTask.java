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

import java.io.EOFException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

public class ReadDatagramTask extends DatagramChannelTask {

    private static final Logger LOG = LoggerFactory.getLogger(ReadDatagramTask.class);

    protected final ByteBuffer byteBuffer;

    public ReadDatagramTask(ObjectBuilder builder) {
        super(builder);
        this.byteBuffer = ByteBuffer.allocate(builder.bufferSize);
    }

    public void readMessage(SelectionKey selectionKey,
                            Consumer<DatagramMessage> callback,
                            Consumer<Exception> exceptionHandler) {
        try {
            if (!selectionKey.isReadable())
                return;
            byteBuffer.clear();
            SocketAddress remoteAddress = datagramChannel.receive(byteBuffer);
            if (remoteAddress == null) {
                LOG.error("No data available to be read.  Deregistering interest in reads on the selection key invoking exception handler.");
                invokeExceptionHandler(selectionKey, exceptionHandler, new EOFException("End of stream"));
                return;
            }
            byteBuffer.flip();
            byte[] message = new byte[byteBuffer.remaining()];
            byteBuffer.get(message);
            callback.accept(new DatagramMessage(remoteAddress, message));
        } catch (Exception e) {
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

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends DatagramChannelTask.ObjectBuilder<T> {

        protected Integer bufferSize = 2048;

        public T withBufferSize(Integer bufferSize) {
            this.bufferSize = bufferSize;
            return self();
        }

        @Override
        protected void validateArguments() {
            super.validateArguments();
            Preconditions.checkNotNull(bufferSize, "bufferSize cannot be null");
            Preconditions.checkArgument(bufferSize > 0, "bufferSize must be > 0");
        }

        public ReadDatagramTask build() {
            validateArguments();
            return new ReadDatagramTask(this);
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
