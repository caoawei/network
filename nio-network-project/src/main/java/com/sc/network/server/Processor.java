package com.sc.network.server;

import com.sc.network.common.AbstractThread;
import com.sc.network.common.ConnectionId;
import com.sc.network.common.ServerChannel;
import com.sc.network.common.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/**
 *
 *  Processor 为IO线程, 负责接受自 {@link Acceptor} 分发的新连接, 网络数据的读写. 并把请求发送给业务处理线程
 *
 *  @author caoawei
 */
public class Processor extends AbstractThread implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(Processor.class);

    /**
     * 单个Processor 可以处理的最大连接数
     */
    private static final int maxConnections = 20;

    int id;

    private ArrayBlockingQueue<SocketChannel> connectionQueue;

    private Map<String, ServerChannel> serverChannelMap = new LinkedHashMap<>();

    private List<SocketChannel> connectedChannel = new ArrayList<>();

    private RequestChannel requestChannel;

    private ServerConfig serverConfig;

    private Selector selector;

    public Processor(
            int processorId,
            ServerConfig serverConfig,
            RequestChannel requestChannel
    ) {
        this.serverConfig = serverConfig;
        this.requestChannel = requestChannel;
        this.id = processorId;
        this.connectionQueue = new ArrayBlockingQueue<>(maxConnections);

        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 接收新连接
     * @param socketChannel socket channel
     * @param maybeBlock 是否阻塞
     * @return 是否接受
     */
    boolean accept(SocketChannel socketChannel,boolean maybeBlock) {

        boolean accept = this.connectionQueue.offer(socketChannel);

        if(!accept && maybeBlock) {
            try {
                connectionQueue.put(socketChannel);
                accept = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                accept = false;
            }
        }

        return accept;
    }

    @Override
    public void run() {

        this.startLatch.countDown();

        while (this.alive.get()) {

            processNewConnection();

            processRequest();

        }
    }

    @Override
    public void close() throws IOException {

    }

    private ServerChannel buildServerChannel(String connectionId,SelectionKey key) {
        ServerChannel serverChannel = new ServerChannel(connectionId,this.id);
        key.attach(serverChannel);
        serverChannelMap.put(connectionId,serverChannel);
        return serverChannel;
    }

    private void processNewConnection() {

        logger.info("[Server] Processor prepare handle new connection");

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

                    serverChannelMap.put(connectionId,serverChannel);
                }
            }

        } catch (IOException e) {

        }

    }

    private void processRequest() {

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
                    iter.remove();

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
}
