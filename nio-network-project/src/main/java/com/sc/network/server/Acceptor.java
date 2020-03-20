package com.sc.network.server;

import com.sc.network.common.AbstractThread;
import com.sc.network.common.ConfigKeys;
import com.sc.network.common.ServerConfig;
import com.sc.network.common.ThreadHelper;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 * Acceptor 担当了分发器的角色. 把新建连接以轮询的方式分发给 {@link Processor}
 *
 * @see Processor
 * @author caoawei
 */
public class Acceptor extends AbstractThread implements Closeable {

    private static final int DEFAULT_SEND_BUFFER_SIZE = -1;

    /**
     * 处理器
     */
    private List<Processor> processors;

    private Selector selector;

    /**
     * 服务器配置
     */
    private ServerConfig serverConfig;

    public Acceptor(Selector selector, ServerConfig serverConfig) {
        this.selector = selector;
        this.serverConfig = serverConfig;
        processors = new ArrayList<>();
    }

    public void addProcessor(Processor processor) {
        processors.add(processor);
    }

    public void startProcessor() {
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

                        SocketChannel socketChannel = accept(selectionKey);

                        Processor processor;

                        do {

                            currentProcessorIndex--;

                            processor = processors.get(currentProcessorIndex % processorCount);

                        } while (!processor.accept(socketChannel,currentProcessorIndex == 0));
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

    private SocketChannel accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.socket().setTcpNoDelay(true);
        socketChannel.socket().setKeepAlive(true);
        Integer sendBuffer = serverConfig.getAsInt(ConfigKeys.SOCKET_SEND_BUFFER_SIZE,DEFAULT_SEND_BUFFER_SIZE);
        if( sendBuffer != DEFAULT_SEND_BUFFER_SIZE) {
            socketChannel.socket().setSendBufferSize(sendBuffer);
        }

        return socketChannel;
    }
}
