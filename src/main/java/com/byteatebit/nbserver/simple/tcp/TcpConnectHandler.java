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

import com.byteatebit.nbserver.INbContext;
import com.byteatebit.nbserver.ISocketChannelHandler;
import com.byteatebit.nbserver.ITcpConnectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TcpConnectHandler implements ITcpConnectHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TcpConnectHandler.class);

    protected final ISocketChannelHandler socketChannelHandler;

    public TcpConnectHandler(ISocketChannelHandler socketChannelHandler) {
        this.socketChannelHandler = socketChannelHandler;
    }

    @Override
    public void accept(INbContext nbContext,
                       SelectionKey serverSocketSelectionKey,
                       ServerSocketChannel serverSocketChannel) {
        SocketChannel socketChannel;
        try {
            socketChannel = serverSocketChannel.accept();
            if (socketChannel == null)
                return;
            socketChannel.configureBlocking(false);
        } catch (IOException e) {
            LOG.error("Failed to accept connection on server socketChannel channel", e);
            return;
        }
        socketChannelHandler.accept(nbContext, socketChannel);
    }

}
