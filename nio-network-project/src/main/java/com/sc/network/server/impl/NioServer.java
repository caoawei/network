package com.sc.network.server.impl;

import com.sc.network.common.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class NioServer extends AbstractServer {

    private static final Logger logger = LoggerFactory.getLogger(NioServer.class);

    private static final int DEFAULT_PORT = 3900;

    private static final int DEFAULT_RECEIVE_BUFFER_SIZE = -1;

    private ServerSocketChannel serverSocketChannel;

    private Selector selector;

    private ServerConfig serverConfig;

    private Map<Integer,Processor> processorMap = new ConcurrentHashMap<>();

    public NioServer(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    public void start() {

        try {

            Integer receiverBufferSize = serverConfig.getAsInt(ConfigKeys.SOCKET_RECEIVER_SEND_SIZE,DEFAULT_RECEIVE_BUFFER_SIZE);
            Integer port = serverConfig.getAsInt(ConfigKeys.SERVER_PORT,DEFAULT_PORT);
            String host = serverConfig.getAsString(ConfigKeys.SERVER_HOST);

            selector = Selector.open();

            InetSocketAddress socketAddress = createSocketAddress(host,port);
            serverSocketChannel = createServerSocketChannel(receiverBufferSize);

            try {
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                serverSocketChannel.bind(socketAddress);
            } catch (IOException e) {
                logger.error("[Server] The server bind port {} failure",port);
            }

            // create acceptor

            RequestChannel requestChannel = new RequestChannel();
            Acceptor acceptor = createAcceptor();

            int processorSize = serverConfig.getAsInt(ConfigKeys.NUM_NETWORK_THREADS,3);

            for (int currentProcessorId = 0; currentProcessorId < processorSize; currentProcessorId++) {
                Processor processor = new Processor(currentProcessorId,requestChannel);
                processorMap.put(currentProcessorId,processor);
                acceptor.addProcessor(processor);
            }

            acceptor.startProcessor();

            ThreadHelper.noneDaemon(acceptor,"Acceptor").start();

            acceptor.awaitStart();

        } catch (Exception e) {
            logger.error(String.format("[Server] The server start failure, error is %s",e.getMessage()),e);
            throw new RuntimeException(e);
        }
    }


    private Acceptor createAcceptor() {
        return new Acceptor();
    }

    private ServerSocketChannel createServerSocketChannel(int receiveBufferSize) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        if(receiveBufferSize != DEFAULT_RECEIVE_BUFFER_SIZE) {
            serverSocketChannel.socket().setReceiveBufferSize(receiveBufferSize);
        }

        return serverSocketChannel;
    }

    private InetSocketAddress createSocketAddress(String host,int port) {
        if(StringUtils.isBlank(host)) {
            return new InetSocketAddress(port);
        }

        return new InetSocketAddress(host,port);
    }


    /**
     *  Acceptor
     */
    private class Acceptor extends AbstractThread implements Closeable {

        List<Processor> processors;

        void addProcessor(Processor processor) {
            processors.add(processor);
        }

        void startProcessor() {
            for (Processor processor : processors) {
                ThreadHelper.noneDaemon(processor,"Processor-"+processor.id).start();
            }
        }

        @Override
        public void run() {

            this.startLatch.countDown();

            int currentProcessorIndex = this.processors.size();

            int processorCount = this.processors.size();

            while (this.alive.get()) {

                try {

                    int ready = selector.select(500);

                    if(ready > 0) {

                        Set<SelectionKey> keySet = selector.selectedKeys();

                        Iterator<SelectionKey> iter = keySet.iterator();

                        while (iter.hasNext()) {

                            SelectionKey selectionKey = iter.next();
                            iter.remove();

                            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

                            Processor processor;
                            do {

                                currentProcessorIndex--;

                                processor = processors.get(currentProcessorIndex % processorCount);

                            } while (processor.accept(socketChannel,currentProcessorIndex == 0));
                        }
                    }

                } catch (IOException e) {
                    // handle exception
                }
            }
        }

        @Override
        public void close() throws IOException {
            for (Processor processor : processors) {
                processor.close();
            }
        }
    }


    /**
     *  Processor
     */
    private class Processor extends AbstractThread implements Closeable {

        static final int maxConnections = 20;

        int id;

        ArrayBlockingQueue<SocketChannel> connectionQueue = new ArrayBlockingQueue<>(maxConnections);

        Map<String, ServerChannel> serverChannelMap = new LinkedHashMap<>();

        List<SocketChannel> connectedChannel = new ArrayList<>();

        RequestChannel requestChannel;

        private ByteBuffer bufferSize = ByteBuffer.allocate(4);

        private Selector selector;

        Processor(int processorId,RequestChannel requestChannel) {
            this.requestChannel = requestChannel;
            this.id = processorId;
            connectionQueue = new ArrayBlockingQueue<>(maxConnections);

            try {
                selector = Selector.open();
            } catch (IOException e) {
                // ignore
            }
        }

        boolean accept(SocketChannel socketChannel,boolean maybeBlock) {

            int currConnectionIndex = 0;

            boolean accept = false;

            while (currConnectionIndex < maxConnections && !connectionQueue.isEmpty()) {

                accept = this.connectionQueue.offer(socketChannel);

                currConnectionIndex++;
            }

            if(!accept && maybeBlock) {
                try {
                    connectionQueue.put(socketChannel);
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            return accept;
        }

        @Override
        public void run() {

            this.startLatch.countDown();

            while (this.alive.get()) {

                processConfigNewConnection();

                processRequest();

            }
        }

        void processConfigNewConnection() {

            SocketChannel socketChannel = this.connectionQueue.poll();

            if(socketChannel == null) {
                return;
            }

            try {

                if(socketChannel.isConnected()) {
                    if(socketChannel.finishConnect()) {
                        SelectionKey key = socketChannel.register(selector,SelectionKey.OP_READ);
                        connectedChannel.add(socketChannel);

                        String connectionId = new ConnectionId(socketChannel).toString();
                        ServerChannel serverChannel = buildServerChannel(connectionId,key);

                    }
                }

            } catch (IOException e) {

            }

        }

        void processRequest() {

            int timeout = this.connectionQueue.isEmpty() ? 300 : 0;

            int ready;

            try {

                if(timeout <= 0) {
                    ready = selector.selectNow();
                } else {
                    ready = selector.select(timeout);
                }

                if(ready > 0) {

                    Set<SelectionKey> keySet = selector.selectedKeys();
                    Iterator<SelectionKey> iter = keySet.iterator();

                    while (iter.hasNext()) {

                        SelectionKey selectionKey = iter.next();

                        if(!selectionKey.isValid()) {
                            selectionKey.cancel();
                        }

                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

                        if(selectionKey.isReadable()) {

                        }
                    }
                }

            } catch (IOException e) {

            }

        }

        @Override
        public void close() throws IOException {

            // 清理操作
            requestChannel.destroy();
        }

        private ServerChannel buildServerChannel(String connectionId,SelectionKey key) {
            ServerChannel serverChannel = new ServerChannel(connectionId);
            key.attach(serverChannel);
            serverChannelMap.put(connectionId,serverChannel);
            return serverChannel;
        }
    }


    /**
     *  RequestChannel
     */
    private class RequestChannel {

        RequestChannel() {

        }

        void destroy() {

        }
    }

    private class ConnectionId {

        SocketAddress localAddress;

        int localPort;

        SocketAddress remoteAddress;

        int remotePort;

        ConnectionId(SocketChannel socketChannel) {

            localAddress = socketChannel.socket().getLocalSocketAddress();
            localPort = socketChannel.socket().getLocalPort();

            remoteAddress = socketChannel.socket().getRemoteSocketAddress();
            remotePort = socketChannel.socket().getPort();
        }

        @Override
        public String toString() {
            return String.format("%s-%s-%s-%s",remoteAddress.toString(),remotePort,localAddress.toString(),localPort);
        }
    }
}
