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

import com.byteatebit.nbserver.ISocketChannelHandler;
import com.byteatebit.nbserver.NbServiceConfig;
import com.byteatebit.nbserver.simple.tcp.TcpConnectorFactory;
import com.byteatebit.nbserver.simple.udp.UdpConnectorFactory;
import com.byteatebit.nbserver.task.WriteMessageTask;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TestSimpleNbServer {

    private static final Logger LOG = LoggerFactory.getLogger(TestSimpleNbServer.class);

    @Test
    public void testWriteClose() throws IOException {
        // Create a task to be executed following the acceptance of a new connection to the server socketChannel.
        // This task is the application entry point.
        // This task will write out the message "Hello and Goodbye" and then close the connection.
        ISocketChannelHandler socketChannelHandler = (nbContext, socket) ->
                WriteMessageTask.Builder.builder()
                        .withByteBuffer(ByteBuffer.allocate(2048))
                        .build()
                        .writeMessage("Hello and Goodbye\n".getBytes(StandardCharsets.UTF_8),
                                nbContext, socket, () -> IOUtils.closeQuietly(socket),
                                (e) -> LOG.error("Write failed", e));
        // create the server
        SimpleNbServer simpleNbServer = SimpleNbServer.Builder.builder()
                .withConfig(SimpleNbServerConfig.builder()
                        .withListenAddress("localhost")
                        .withListenPort(1111)
                        .build())
                // Here we configure the provider for the listening socketChannel.  In this case,
                // a TCP server socketChannel will be created using the default TcpAcceptedConnectionHandler
                // to handle incoming connections and pass them off to the socketChannelHandler
                .withConnectorFactory(TcpConnectorFactory.Builder.builder()
                        // supply the application entry point
                        .withConnectedSocketTask(socketChannelHandler)
                        .build())
                .build();
        try {
            simpleNbServer.start();
            Socket socket = new Socket("localhost", 1111);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String message = reader.readLine();
            System.out.println("Received message '" + message + "' from the server");
            Assert.assertEquals("Hello and Goodbye", message);
            socket.close();
        } finally {
            simpleNbServer.shutdown();
        }
    }

    @Test
    public void testEchoServer() throws IOException {
        SimpleNbServer simpleNbServer = SimpleNbServer.Builder.builder()
                .withConfig(SimpleNbServerConfig.builder()
                    .withListenAddress("localhost")
                    .withListenPort(1111)
                    .build())
                .withConnectorFactory(TcpConnectorFactory.Builder.builder()
                        .withConnectedSocketTask(new TcpConnectTestHandler())
                        .build())
                .build();
        simpleNbServer.start();
        String message1 = "this is message1";
        String message2 = "this is message2";
        try {
            Socket socket = new Socket("localhost", 1111);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            // message1
            out.write((message1 + '\n').getBytes(StandardCharsets.UTF_8));
            String messageRead1 = reader.readLine();
            Assert.assertEquals(message1, messageRead1);

            // message2
            out.write((message2 + '\n').getBytes(StandardCharsets.UTF_8));
            String messageRead2 = reader.readLine();
            Assert.assertEquals(message2, messageRead2);

            // quit
            out.write("quit\n".getBytes(StandardCharsets.UTF_8));
            String finalMessage = reader.readLine();
            Assert.assertNull(finalMessage);
        } finally {
            simpleNbServer.shutdown();
        }
    }

    @Test
    public void testMultipleMessages() throws IOException {
        SimpleNbServer simpleNbServer = SimpleNbServer.Builder.builder()
                .withConfig(SimpleNbServerConfig.builder()
                        .withListenAddress("localhost")
                        .withListenPort(1111)
                        .build())
                .withConnectorFactory(TcpConnectorFactory.Builder.builder()
                        .withConnectedSocketTask(new TcpConnectTestHandler())
                        .build())
                .build();
        simpleNbServer.start();
        String message1 = "this is message1";
        String message2 = "this is message2";
        try {
            Socket socket = new Socket("localhost", 1111);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            // message1
            out.write((message1 + '\n' + message2 + '\n').getBytes(StandardCharsets.UTF_8));
            String messageRead1 = reader.readLine();
            Assert.assertEquals(message1, messageRead1);
            String messageRead2 = reader.readLine();
            Assert.assertEquals(message2, messageRead2);

            // quit
            out.write("quit\n".getBytes(StandardCharsets.UTF_8));
            String finalMessage = reader.readLine();
            Assert.assertNull(finalMessage);
        } finally {
            simpleNbServer.shutdown();
        }
    }

    @Test
    public void testReadTimeout() throws IOException, InterruptedException {
        SimpleNbServer simpleNbServer = SimpleNbServer.Builder.builder()
                .withConfig(SimpleNbServerConfig.builder()
                        .withListenAddress("localhost")
                        .withListenPort(1111)
                        .withNbServiceConfig(NbServiceConfig.Builder.builder()
                            .withIoTaskTimeoutMs(10l)
                            .withIoTaskTimeoutCheckPeriodMs(2l)
                            .withSelectorTimeoutMs(2l)
                            .build())
                        .build())
                .withConnectorFactory(TcpConnectorFactory.Builder.builder()
                        .withConnectedSocketTask(new TcpConnectTestHandler())
                        .build())
                .build();
        simpleNbServer.start();
        String message1 = "this is message1";
        try {
            Socket socket = new Socket("localhost", 1111);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            out.write((message1 + '\n').getBytes(StandardCharsets.UTF_8));
            reader.readLine();
            // message1
            Thread.sleep(100);
            String nextLine = null;
            try {
                out.write((message1 + '\n').getBytes(StandardCharsets.UTF_8));
                nextLine = reader.readLine();
            } catch (SocketException e) {}
            Assert.assertNull(nextLine);
        } finally {
            simpleNbServer.shutdown();
        }
    }

    @Test
    public void testBlockingProcessWithSingleThread() throws IOException, ExecutionException, InterruptedException {
        SimpleNbServer simpleNbServer = SimpleNbServer.Builder.builder()
                .withConfig(SimpleNbServerConfig.builder()
                        .withNbServiceConfig(NbServiceConfig.Builder.builder()
                            .withIoTaskTimeoutMs(20000l)
                            .withNumThreads(1)
                            .withNumIoThreads(1)
                            .build())
                        .withListenAddress("localhost")
                        .withListenPort(1111)
                        .build())
                .withConnectorFactory(TcpConnectorFactory.Builder.builder()
                        .withConnectedSocketTask(new TcpConnectTestHandler())
                        .build())
                .build();
        simpleNbServer.start();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        String message1 = "sleep";
        String message2 = "this is message2";
        try {
            long beginTime = System.currentTimeMillis();
            executorService.submit(() -> sendMessage(message1));
            Thread.sleep(1000);
            Future<String> future = executorService.submit(() -> sendMessage(message2));
            String response = future.get();
            long elapsed = System.currentTimeMillis() - beginTime;
            Assert.assertEquals(message2, response);
            // the sleep command in the echo service sleeps for 5 seconds
            Assert.assertTrue(elapsed >= 5000);
        } finally {
            simpleNbServer.shutdown();
        }
    }

    @Test
    public void testBlockingProcessWithSeparateIoThread() throws IOException, ExecutionException, InterruptedException {
        SimpleNbServer simpleNbServer = SimpleNbServer.Builder.builder()
                .withConfig(SimpleNbServerConfig.builder()
                        .withNbServiceConfig(NbServiceConfig.Builder.builder()
                            .withIoTaskTimeoutMs(20000l)
                            .withNumThreads(3)
                            .withNumIoThreads(1)
                            .build())
                        .withListenAddress("localhost")
                        .withListenPort(1111)
                        .build())
                .withConnectorFactory(TcpConnectorFactory.Builder.builder()
                        .withConnectedSocketTask(new TcpConnectTestHandler())
                        .build())
                .build();
        simpleNbServer.start();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        String message1 = "sleep";
        String message2 = "this is message2";
        try {
            long beginTime = System.currentTimeMillis();
            executorService.submit(() -> sendMessage(message1));
            Thread.sleep(1000);
            Future<String> future = executorService.submit(() -> sendMessage(message2));
            String response = future.get();
            long elapsed = System.currentTimeMillis() - beginTime;
            Assert.assertEquals(message2, response);
            // the sleep command in the echo service sleeps for 2 seconds
            Assert.assertTrue(elapsed < 5000);
        } finally {
            simpleNbServer.shutdown();
        }
    }

    protected String sendMessage(String message) throws IOException {
        LOG.info("sending message '" + message + "'");
        Socket socket = new Socket("localhost", 1111);
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        out.write((message + '\n').getBytes(StandardCharsets.UTF_8));
        String response = reader.readLine();
        LOG.info("recieved response '" + response + "'");
        socket.close();
        return response;
    }

    @Test
    public void testUdpEchoServer() throws IOException {
        SimpleNbServer simpleNbServer = SimpleNbServer.Builder.builder()
                .withConfig(SimpleNbServerConfig.builder()
                        .withListenAddress("localhost")
                        .withListenPort(1111)
                        .build())
                .withConnectorFactory(UdpConnectorFactory.Builder.builder()
                        .withDatagramMessageHandlerFactory(new UdpServiceFactory())
                        .build())
                .build();
        simpleNbServer.start();
        String message1 = "this is message1";
        String message2 = "this is message2";
        String message3 = "quit";
        try {
            DatagramSocket socket = new DatagramSocket();
            SocketAddress address = new InetSocketAddress("localhost", 1111);

            // message 1
            byte[] payload1 = message1.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(payload1, payload1.length, address);
            socket.send(packet);
            byte[] response = new byte[50];
            DatagramPacket responsePacket = new DatagramPacket(response, response.length);
            socket.receive(responsePacket);
            String messageRead = new String(responsePacket.getData(), 0, responsePacket.getLength());
            Assert.assertEquals(message1, messageRead);

            // message2
            byte[] payload2 = message2.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet2 = new DatagramPacket(payload2, payload2.length, address);
            socket.send(packet2);
            responsePacket = new DatagramPacket(response, response.length);
            socket.receive(responsePacket);
            messageRead = new String(responsePacket.getData(), 0, responsePacket.getLength());
            Assert.assertEquals(message2, messageRead);

            // message3
            byte[] payload3 = message3.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet3 = new DatagramPacket(payload3, payload3.length, address);
            socket.send(packet3);
            responsePacket = new DatagramPacket(response, response.length);
            socket.receive(responsePacket);
            messageRead = new String(responsePacket.getData(), 0, responsePacket.getLength());
            Assert.assertEquals("goodbye", messageRead);

        } finally {
            simpleNbServer.shutdown();
        }
    }

}
