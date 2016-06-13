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

import java.nio.channels.DatagramChannel;

public class DatagramChannelTask extends ChannelTask {

    protected final DatagramChannel datagramChannel;

    public DatagramChannelTask(ObjectBuilder builder) {
        super(builder);
        this.datagramChannel = builder.datagramChannel;
    }

    public DatagramChannelTask(INbContext nbContext, DatagramChannel datagramChannel) {
        super(nbContext);
        Preconditions.checkNotNull(datagramChannel, "datagramChannel cannot be null");
        this.datagramChannel = datagramChannel;
    }

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends ChannelTask.ObjectBuilder<T> {

        protected DatagramChannel datagramChannel;

        public T withDatagramChannel(DatagramChannel datagramChannel) {
            this.datagramChannel = datagramChannel;
            return self();
        }

        protected void validateArguments() {
            super.validateArguments();
            Preconditions.checkNotNull(nbContext, "nbContext cannot be null");
            Preconditions.checkNotNull(datagramChannel, "datagramChannel cannot be null");
        }
    }
}
