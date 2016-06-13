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

package com.byteatebit.nbserver.simple.tcp;

import com.byteatebit.nbserver.simple.IConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

public class TcpConnector implements IConnector {

    private static final Logger LOG = LoggerFactory.getLogger(TcpConnector.class);

    protected final ServerSocketChannel serverSocketChannel;
    protected final SelectionKey serverSocketSelectionKey;

    public TcpConnector(SelectionKey serverSocketSelectionKey, ServerSocketChannel serverSocketChannel) {
        this.serverSocketSelectionKey = serverSocketSelectionKey;
        this.serverSocketChannel = serverSocketChannel;
    }

    @Override
    public void stop() {
        serverSocketSelectionKey.cancel();
    }

    @Override
    public void close() throws IOException {
        serverSocketChannel.close();
    }
}
