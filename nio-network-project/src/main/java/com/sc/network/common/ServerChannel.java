package com.sc.network.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class ServerChannel {

    private static final Logger logger = LoggerFactory.getLogger(ServerChannel.class);

    /**
     * 连接ID
     */
    private String connectionId;

    /**
     * 处理器ID
     */
    private int processorId;

    /**
     * 请求数据大小
     */
    private ByteBuffer byteSize;

    /**
     * 请求数据内容
     */
    private ByteBuffer dataBuffer;

    public ServerChannel(String connectionId) {
        this.connectionId = connectionId;
        this.byteSize = ByteBuffer.allocate(4);
    }

    public Request readFrom(SocketChannel socketChannel) {

        Request request = null;

        try {

            // 请求长度数据未读完
            if(byteSize.hasRemaining()) {
                socketChannel.read(byteSize);
            }

            if(!byteSize.hasRemaining()) {

                if(dataBuffer == null) {
                    allocateMemory(byteSize.getInt());
                }

                if(dataBuffer.hasRemaining()) {
                    socketChannel.read(dataBuffer);
                }

                if(!dataBuffer.hasRemaining()) {
                    request = decode();
                }
            }


        } catch (IOException e) {
            logger.error(String.format("[Server] socket read failure, connectionId is %s, error info is %s",this.connectionId,e.getMessage()),e);
        }

        return request;
    }

    private void allocateMemory(int size) {
        dataBuffer = ByteBuffer.allocate(size);
    }

    /**
     * 解码
     */
    private Request decode() {
        Request request = new Request();
        byteSize.flip();
        dataBuffer.flip();
        request.setRequestLength(byteSize.getInt());
        request.setData(new String(dataBuffer.array()));
        request.setConnectionId(this.connectionId);
        return request;
    }

}
