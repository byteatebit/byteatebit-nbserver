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

import com.byteatebit.nbserver.*;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

public class NbContext implements INbContext {

    protected final ISelectorRegistrar selectorRegistrar;
    protected final IComputeTaskScheduler taskScheduler;

    public NbContext(ISelectorRegistrar selectorRegistrar,
                     IComputeTaskScheduler taskScheduler) {
        this.selectorRegistrar = selectorRegistrar;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public SelectionKey register(SelectableChannel channel, int ops, IOTask nioTask) {
        return selectorRegistrar.register(channel, ops, nioTask);
    }

    @Override
    public SelectionKey register(SelectableChannel channel,
                                 int ops,
                                 IOTask nioTask,
                                 IOTimeoutTask timeoutTask,
                                 long timeoutMs) {
        return selectorRegistrar.register(channel, ops, nioTask, timeoutTask, timeoutMs);
    }

    @Override
    public SelectionKey register(SelectableChannel channel, int ops, IOTask nioTask, IOTimeoutTask timeoutTask) {
        return selectorRegistrar.register(channel, ops, nioTask, timeoutTask);
    }

    @Override
    public void schedule(Runnable runnable, Consumer<Exception> exceptionHandler) {
        taskScheduler.schedule(runnable, exceptionHandler);
    }

}
