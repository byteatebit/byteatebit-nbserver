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

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ExecutorComputeTaskScheduler implements ITerminableComputeTaskScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutorComputeTaskScheduler.class);

    protected final ExecutorService executorService;

    public ExecutorComputeTaskScheduler(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void schedule(Runnable runnable, Consumer<Exception> exceptionHandler) {
        Preconditions.checkNotNull(runnable, "runnable cannot be null");
        Preconditions.checkNotNull(exceptionHandler, "exceptionHandler cannot be null");
        Runnable task = () -> {
          try {
              runnable.run();
          } catch (Exception e) {
              LOG.error("Scheduled task failed.  Invoking the exception handler", e);
              exceptionHandler.accept(e);
          }
        };
        executorService.execute(task);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }
}
