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
import com.google.common.base.Preconditions;

public class ChannelTask {

    protected final INbContext nbContext;

    protected ChannelTask(ObjectBuilder builder) {
        this.nbContext = builder.nbContext;
    }

    public ChannelTask(INbContext nbContext) {
        Preconditions.checkNotNull(nbContext, "nbContext cannot be null");
        this.nbContext = nbContext;
    }

    public static abstract class ObjectBuilder<T extends ObjectBuilder<T>> extends BaseBuilder<T> {

        protected INbContext nbContext;

        public T withNbContext(INbContext nbContext) {
            this.nbContext = nbContext;
            return self();
        }

        protected void validateArguments() {
            Preconditions.checkNotNull(nbContext, "nbContext cannot be null");
        }
    }

}
