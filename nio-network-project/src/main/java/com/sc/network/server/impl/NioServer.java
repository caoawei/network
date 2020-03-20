package com.sc.network.server.impl;

import com.sc.network.common.*;
import com.sc.network.server.Acceptor;
import com.sc.network.server.Processor;
import com.sc.network.server.RequestChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class NioServer extends AbstractServer {

    private static final Logger logger = LoggerFactory.getLogger(NioServer.class);

    private static final int DEFAULT_PORT = 3900;

    private static final int DEFAULT_RECEIVE_BUFFER_SIZE = -1;

    private ServerSocketChannel serverSocketChannel;

    private Selector selector;

    private ServerConfig serverConfig;

    public NioServer(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    public void start() {

        try {

            // prepare server socket
            handleSocket();

            RequestChannel requestChannel = new RequestChannel();

            // create acceptor
            Acceptor acceptor = new Acceptor(this.selector,this.serverConfig);

            // 创建Processor
            int processorSize = serverConfig.getAsInt(ConfigKeys.NUM_NETWORK_THREADS,3);

            for (int currentProcessorId = 0; currentProcessorId < processorSize; currentProcessorId++) {

                Processor processor = new Processor(currentProcessorId,this.serverConfig,requestChannel);

                acceptor.addProcessor(processor);
            }

            // start processor
            acceptor.startProcessor();

            // start acceptor
            ThreadHelper.noneDaemon(acceptor,"Acceptor").start();

            // wait acceptor start success
            acceptor.awaitStart();

            logger.info("[Server] The server that listen on port {},start success",serverConfig.getAsInt(ConfigKeys.SERVER_PORT,DEFAULT_PORT));

        } catch (Exception e) {
            logger.error(String.format("[Server] The server start failure, error is %s",e.getMessage()),e);
            throw new RuntimeException(e);
        }
    }

    private void handleSocket() throws IOException {

        String host = serverConfig.getAsString(ConfigKeys.SERVER_HOST);

        Integer port = serverConfig.getAsInt(ConfigKeys.SERVER_PORT,DEFAULT_PORT);

        InetSocketAddress socketAddress = createSocketAddress(host,port);

        Integer receiverBufferSize = serverConfig.getAsInt(ConfigKeys.SOCKET_RECEIVER_BUFFER_SIZE,DEFAULT_RECEIVE_BUFFER_SIZE);

        this.selector = Selector.open();

        this.serverSocketChannel = ServerSocketChannel.open();

        this.serverSocketChannel.configureBlocking(false);

        if(receiverBufferSize != DEFAULT_RECEIVE_BUFFER_SIZE) {
            this.serverSocketChannel.socket().setReceiveBufferSize(receiverBufferSize);
        }

        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.serverSocketChannel.bind(socketAddress);

    }

    private InetSocketAddress createSocketAddress(String host,int port) {
        if(StringUtils.isBlank(host)) {
            return new InetSocketAddress(port);
        }

        return new InetSocketAddress(host,port);
    }





}
