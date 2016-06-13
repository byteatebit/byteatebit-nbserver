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
import org.apache.commons.io.output.ByteArrayOutputStream;
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
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TestWriteMessageTask {

    @Test
    public void testWriteCompleteMessage() throws IOException {
        SocketChannel socket = mock(SocketChannel.class);
        ByteArrayOutputStream messageStream = new ByteArrayOutputStream();
        String message = "hi\n";
        when(socket.write(any(ByteBuffer.class))).then(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                ByteBuffer buffer = (ByteBuffer) invocationOnMock.getArguments()[0];
                while (buffer.hasRemaining())
                    messageStream.write(buffer.get());
                return buffer.position();
            }
        });
        INbContext nbContext = mock(INbContext.class);
        SelectionKey selectionKey = mock(SelectionKey.class);
        when(selectionKey.channel()).thenReturn(socket);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);

        WriteMessageTask writeTask = WriteMessageTask.Builder.builder()
                .withByteBuffer(ByteBuffer.allocate(100))
                .build();
        List<String> callbackInvoked = new ArrayList<>();
        Runnable callback = () -> callbackInvoked.add("");
        Consumer<Exception> exceptionHandler = e -> Assert.fail(e.getMessage());
        writeTask.writeMessage(message.getBytes(StandardCharsets.UTF_8), nbContext, socket, callback, exceptionHandler);
        verify(nbContext, times(1)).register(any(SocketChannel.class), eq(SelectionKey.OP_WRITE), any(), any());
        writeTask.write(selectionKey, callback, exceptionHandler);
        verify(selectionKey, times(1)).interestOps(0);

        Assert.assertEquals(message, messageStream.toString(StandardCharsets.UTF_8));
        Assert.assertEquals(1, callbackInvoked.size());
    }


    @Test
    public void testWriteMessageInParts() throws IOException {
        SocketChannel socket = mock(SocketChannel.class);
        ByteArrayOutputStream messageStream = new ByteArrayOutputStream();
        String messagePart1 = "messagePart1 ";
        String messagePart2 = "messagePart2";
        String message = messagePart1 + messagePart2;
        when(socket.write(any(ByteBuffer.class)))
                .then(new Answer<Integer>() {
                    @Override
                    public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ByteBuffer buffer = (ByteBuffer) invocationOnMock.getArguments()[0];
                        for (int i = 0; i < messagePart1.length(); i++)
                            messageStream.write(buffer.get());
                        return messagePart1.length();
                    }
                })
                .then(new Answer<Integer>() {
                    @Override
                    public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ByteBuffer buffer = (ByteBuffer) invocationOnMock.getArguments()[0];
                        for (int i = 0; i < messagePart2.length(); i++)
                            messageStream.write(buffer.get());
                        return messagePart2.length();
                    }
                });
        INbContext nbContext = mock(INbContext.class);
        SelectionKey selectionKey = mock(SelectionKey.class);
        when(selectionKey.channel()).thenReturn(socket);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);

        WriteMessageTask writeTask = WriteMessageTask.Builder.builder()
                .withByteBuffer(ByteBuffer.allocate(100))
                .build();
        List<String> callbackInvoked = new ArrayList<>();
        Runnable callback = () -> callbackInvoked.add("");
        Consumer<Exception> exceptionHandler = e -> Assert.fail(e.getMessage());
        writeTask.writeMessage(message.getBytes(StandardCharsets.UTF_8), nbContext, socket, callback, exceptionHandler);
        verify(nbContext, times(1)).register(any(SocketChannel.class), eq(SelectionKey.OP_WRITE), any(), any());
        // simulate first write
        writeTask.write(selectionKey, callback, exceptionHandler);
        verify(selectionKey, times(0)).interestOps(0);
        // simulate second write
        writeTask.write(selectionKey, callback, exceptionHandler);
        verify(selectionKey, times(1)).interestOps(0);

        Assert.assertEquals(message, messageStream.toString(StandardCharsets.UTF_8));
        Assert.assertEquals(1, callbackInvoked.size());
    }

    @Test
    public void testWriteFailed() throws IOException {
        SocketChannel socket = mock(SocketChannel.class);
        ByteArrayOutputStream messageStream = new ByteArrayOutputStream();
        String message = "hi\n";
        when(socket.write(any(ByteBuffer.class))).thenThrow(new IOException("write failed"));
        INbContext nbContext = mock(INbContext.class);
        SelectionKey selectionKey = mock(SelectionKey.class);
        when(selectionKey.channel()).thenReturn(socket);
        when(selectionKey.isValid()).thenReturn(true);
        when(selectionKey.readyOps()).thenReturn(SelectionKey.OP_WRITE);

        WriteMessageTask writeTask = WriteMessageTask.Builder.builder()
                .withByteBuffer(ByteBuffer.allocate(100))
                .build();
        List<String> callbackInvoked = new ArrayList<>();
        List<Exception> exceptionHandlerInvoked = new ArrayList<>();
        Runnable callback = () -> callbackInvoked.add("");
        Consumer<Exception> exceptionHandler = exceptionHandlerInvoked::add;
        writeTask.writeMessage(message.getBytes(StandardCharsets.UTF_8), nbContext, socket, callback, exceptionHandler);
        verify(nbContext, times(1)).register(any(SocketChannel.class), eq(SelectionKey.OP_WRITE), any(), any());
        writeTask.write(selectionKey, callback, exceptionHandler);
        verify(selectionKey, times(1)).interestOps(0);

        Assert.assertEquals(0, messageStream.size());
        Assert.assertEquals(0, callbackInvoked.size());
        Assert.assertEquals(1, exceptionHandlerInvoked.size());
    }
}
