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

package com.byteatebit.nbserver.simple.udp;

import com.byteatebit.nbserver.simple.IConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

public class UdpConnector implements IConnector {

    private static final Logger LOG = LoggerFactory.getLogger(UdpConnector.class);

    protected final DatagramChannel datagramChannel;
    protected final SelectionKey selectionKey;

    public UdpConnector(SelectionKey selectionKey, DatagramChannel datagramChannel) {
        this.selectionKey = selectionKey;
        this.datagramChannel = datagramChannel;
    }

    @Override
    public void stop() {
        selectionKey.cancel();
    }

    @Override
    public void close() throws IOException {
        datagramChannel.close();
    }

}
