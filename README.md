byteatebit-nbserver
=================
byteatebit-nbserver is a non-blocking IO server written in Java.  

This project depends upon the library byteatebit-common available [here](https://github.com/byteatebit/byteatebit-common "byteatebit-common").
First, run `./gradlew build install` in the byteatebit-common project directory and then the same in the byteatebit-nbserver
project directory to build the project, execute the unit tests, and install the library in your local maven repository.

There are a number of options available to configure an a SimpleNbServer instance, but for unit testing purposes it is relatively simple to start up a server.
The example below performs the following:

1. Creates a TCP server configured to listen on address ‘localhost’ on port 1111
+  Creates a handler for an accepted connection that sends the message “Hello and Goodbye” to the remote client and register it with the server
+ Starts the server
+ Connects a client socket to the server
+ Reads a line from the client socket
+ Closes the socket
+ Shutdowns the server

```
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

```

