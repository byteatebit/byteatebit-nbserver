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

import com.google.common.base.Preconditions;

import java.nio.channels.SocketChannel;

public class SocketChannelTask extends ChannelTask implements IChannelSelectorRegistrar {

    protected final SocketChannel socketChannel;

    public SocketChannelTask(ObjectBuilder builder) {
        super(builder);
        this.socketChannel = builder.socketChannel;
    }

    public SocketChannelTask(INbContext nbContext, SocketChannel socketChannel) {
        super(nbContext);
        Preconditions.checkNotNull(socketChannel, "socketChannel cannot be null");
        this.socketChannel = socketChannel;
    }

    @Override
    public void register(int ops, IOTask ioTask, IOTimeoutTask ioTimeoutTask) {
        nbContext.register(socketChannel, ops, ioTask, ioTimeoutTask);
    }

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends ChannelTask.ObjectBuilder<T> {

        protected SocketChannel socketChannel;

        public T withSocketChannel(SocketChannel socketChannel) {
            this.socketChannel = socketChannel;
            return self();
        }

        protected void validateArguments() {
            super.validateArguments();
            Preconditions.checkNotNull(nbContext, "nbContext cannot be null");
            Preconditions.checkNotNull(socketChannel, "socketChannel cannot be null");
        }
    }
}
