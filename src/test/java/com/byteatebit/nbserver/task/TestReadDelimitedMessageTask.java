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

import com.byteatebit.nbserver.INbContext;
import com.byteatebit.nbserver.ISelectorRegistrar;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

public class TestReadDelimitedMessageTask {

    @Test
    public void testReadCompleteMessage() throws IOException {
        SocketChannel socket = mock(SocketChannel.class);
        String message = "hi\n";
        when(socket.read(any(ByteBuffer.class))).then(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocationOnMock.getArguments()[0];
                buffer.put(message.getBytes(StandardCharsets.UTF_8));
                return buffer.position();
            }
        });
        ISelectorRegistrar selectorRegistrar = mock(ISelectorRegistrar.class);
        SelectionKey selectionKey = mock(SelectionKey.class);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.channel()).thenReturn(socket);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_READ);

        ReadDelimitedMessageTask readTask = ReadDelimitedMessageTask.Builder.builder()
                .withDelimiter("\n")
                .withByteBuffer(ByteBuffer.allocate(10))
                .withMaxMessageSize(10)
                .build();
        List<String> messagesRead = new ArrayList<>();
        Consumer<List<String>> callback = messagesRead::addAll;
        Consumer<Exception> exceptionHandler = e -> Assert.fail(e.getMessage());
        readTask.readMessages(selectorRegistrar, socket, callback, exceptionHandler);
        readTask.read(selectionKey, callback, exceptionHandler);

        List<String> expectedMessages = Collections.singletonList("hi");
        Assert.assertEquals(expectedMessages, messagesRead);
        verify(selectorRegistrar, times(1)).register(any(SocketChannel.class), eq(SelectionKey.OP_READ), any(), any());
        verify(selectionKey, times(1)).interestOps(0);
    }

    @Test
    public void testReadMessageInParts() throws IOException {
        SocketChannel socket = mock(SocketChannel.class);
        String messagePart1 = "messagePart1 ";
        String messagePart2 = "messagePart2\n";
        when(socket.read(any(ByteBuffer.class)))
                .then(new Answer<Integer>() {
                    @Override
                    public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ByteBuffer buffer = (ByteBuffer) invocationOnMock.getArguments()[0];
                        buffer.put(messagePart1.getBytes(StandardCharsets.UTF_8));
                        return buffer.position();
                    }
                })
                .then(new Answer<Object>() {
                    @Override
                    public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ByteBuffer buffer = (ByteBuffer) invocationOnMock.getArguments()[0];
                        buffer.put(messagePart2.getBytes(StandardCharsets.UTF_8));
                        return buffer.position();
                    }
                });
        ISelectorRegistrar selectorRegistrar = mock(ISelectorRegistrar.class);
        SelectionKey selectionKey = mock(SelectionKey.class);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.channel()).thenReturn(socket);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_READ);

        ReadDelimitedMessageTask readTask = ReadDelimitedMessageTask.Builder.builder()
                .withDelimiter("\n")
                .withByteBuffer(ByteBuffer.allocate(50))
                .withMaxMessageSize(50)
                .build();
        List<String> messagesRead = new ArrayList<>();
        Consumer<List<String>> callback = messagesRead::addAll;
        Consumer<Exception> exceptionHandler = e -> Assert.fail(e.getMessage());
        readTask.readMessages(selectorRegistrar, socket, callback, exceptionHandler);
        verify(selectorRegistrar, times(1)).register(any(SocketChannel.class), eq(SelectionKey.OP_READ), any(), any());
        // simulate first read
        readTask.read(selectionKey, callback, exceptionHandler);
        verify(selectionKey, times(0)).interestOps(0);
        // simulate second read
        readTask.read(selectionKey, callback, exceptionHandler);
        verify(selectionKey, times(1)).interestOps(0);

        List<String> expectedMessages = Collections.singletonList("messagePart1 messagePart2");
        Assert.assertEquals(expectedMessages, messagesRead);
    }

    @Test
    public void testMessageTooLarge() throws IOException {
        SocketChannel socket = mock(SocketChannel.class);
        String message = "This message is too large\n";
        when(socket.read(any(ByteBuffer.class))).then(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocationOnMock.getArguments()[0];
                buffer.put(message.getBytes(StandardCharsets.UTF_8));
                return buffer.position();
            }
        });
        ISelectorRegistrar selectorRegistrar = mock(ISelectorRegistrar.class);
        SelectionKey selectionKey = mock(SelectionKey.class);
        when(selectionKey.channel()).thenReturn(socket);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_READ);

        ReadDelimitedMessageTask readTask = ReadDelimitedMessageTask.Builder.builder()
                .withDelimiter("\n")
                .withByteBuffer(ByteBuffer.allocate(100))
                .withMaxMessageSize(5)
                .build();
        List<String> messagesRead = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        Consumer<List<String>> callback = messagesRead::addAll;
        Consumer<Exception> exceptionHandler = exceptions::add;
        readTask.readMessages(selectorRegistrar, socket, callback, exceptionHandler);
        readTask.read(selectionKey, callback, exceptionHandler);

        Assert.assertTrue(messagesRead.isEmpty());
        Assert.assertEquals(1, exceptions.size());
        verify(selectorRegistrar, times(1)).register(any(SocketChannel.class), eq(SelectionKey.OP_READ), any(), any());
        verify(selectionKey, times(1)).interestOps(0);
    }

}
