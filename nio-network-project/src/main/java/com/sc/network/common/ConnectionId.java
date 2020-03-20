package com.sc.network.common;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public class ConnectionId {

    private SocketAddress localAddress;

    private int localPort;

    private SocketAddress remoteAddress;

    private int remotePort;

    public ConnectionId(SocketChannel socketChannel) {
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
