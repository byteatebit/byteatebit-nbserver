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

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public interface ISelectorRegistrar {

    /**
     * Register the supplied channel with a selector for the events
     * indicated by ops upon which the supplied consumer function should be invoked.
     * The implementation specific default task timeout will be set.
     * @param channel The channel to watch for the ops events
     * @param ops The operations for which the channel should be registered on the selector
     * @param nioTask The function to be invoked
     * @return the SelectionKey registered with the channel
     * @throws SelectorException if the underlying Selector throws a {@link java.nio.channels.ClosedChannelException}.
     * The checked exception is mapped to this RuntimeException to simplify the use of lambda functions.
     */
    SelectionKey register(SelectableChannel channel,
                  int ops,
                  IOTask nioTask);

    /**
     * Register the supplied channel with a selector for the events
     * indicated by ops upon which the supplied consumer function should be invoked.
     * @param channel The channel to watch for the ops events
     * @param ops The operations for which the channel should be registered on the selector
     * @param nioTask The function to be invoked
     * @param timeoutMs The max time in milliseconds to wait for the IO event identified by ops to be
     *                  triggered for the channel
     * @return the SelectionKey registered with the channel
     * @throws SelectorException if the underlying Selector throws a {@link java.nio.channels.ClosedChannelException}.
     * The checked exception is mapped to this RuntimeException to simplify the use of lambda functions.
     */
    SelectionKey register(SelectableChannel channel,
                          int ops,
                          IOTask nioTask,
                          IOTimeoutTask timeoutTask,
                          long timeoutMs);


    SelectionKey register(SelectableChannel channel,
                          int ops,
                          IOTask nioTask,
                          IOTimeoutTask timeoutTask);

}
