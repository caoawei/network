package com.sc.network.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class Client implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    private Selector selector;

    private InetSocketAddress socketAddress;

    private SelectionKey selectionKey;

    private volatile boolean isStart = false;

    private final LinkedList<Packet> outGoingQueue = new LinkedList<>();

    private final LinkedList<Packet> pendingQueue = new LinkedList<>();

    private CountDownLatch startLatch = new CountDownLatch(1);

    private AtomicLong atomicLong = new AtomicLong(1);

    private Thread thread;

    public Client(InetSocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }

    public void start() {

        if(isStart) {
            return;
        }

        isStart = true;

        try {
            thread = new Thread(this,"Client-Thread");
            thread.start();
            startLatch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {


        try {

            selector = Selector.open();

            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);

            selectionKey = sc.register(selector,SelectionKey.OP_CONNECT);

            boolean hasConnected = sc.connect(socketAddress);

            startLatch.countDown();

            if(hasConnected) {
                enableReadAndWrite();
            }

            while (isStart) {

                selector.select(500);

                Set<SelectionKey> selectedKeys = selector.selectedKeys();

                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    iter.next();
                    iter.remove();

                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

                    if(selectionKey.isConnectable()) {

                        if(socketChannel.finishConnect()) {
                            enableReadAndWrite();
                        }
                    }

                    doIo(selectionKey);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String request(String request) throws Exception {

        Packet packet = new Packet();
        packet.setData(request);
        packet.setXid(atomicLong.getAndIncrement());

        try {

            synchronized (outGoingQueue) {
                outGoingQueue.add(packet);
            }

            enableReadAndWrite();

            synchronized (packet) {
                while (!packet.isDone())
                    packet.wait();
            }

            return packet.getResponse();

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }

    private void doIo(SelectionKey selectionKey) throws IOException {

        if(selectionKey.isReadable()) {
            doRead(selectionKey);
        }

        if(selectionKey.isWritable()) {
            doWrite(selectionKey);
        }
    }

    private void doWrite(SelectionKey key) throws IOException {

        Packet packet;

        synchronized (outGoingQueue) {

            if(outGoingQueue.isEmpty()) {
                disableWrite();
                return;
            }

            packet = outGoingQueue.getFirst();
            outGoingQueue.removeFirstOccurrence(packet);
            pendingQueue.add(packet);
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData().getBytes());
        SocketChannel socketChannel = (SocketChannel) key.channel();

        socketChannel.write(byteBuffer);

        logger.info("[Client] 发送请求 >>>> data:{}",packet.getData());

        enableReadAndWrite();

        synchronized (outGoingQueue) {
            if(outGoingQueue.isEmpty()) {
                disableWrite();
            }
        }

    }

    void doRead(SelectionKey key) throws IOException {

        SocketChannel socketChannel = (SocketChannel) key.channel();

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024*16);
        socketChannel.read(byteBuffer);

        int pos = byteBuffer.position();
        byteBuffer.flip();

        String data = new String(byteBuffer.array(),0,pos);
        logger.info("[Client] 接受到服务器响应 >>>> data:{}",data);

        synchronized (pendingQueue) {

            Packet packet = pendingQueue.remove();
            packet.setResponse(data);
            packet.setDone(true);

            try {

                if(pendingQueue.isEmpty()) {
                    disableRead();
                }

                synchronized (outGoingQueue) {
                    if(!outGoingQueue.isEmpty()) {
                        enableReadAndWrite();
                    }
                }

                synchronized (packet) {
                    packet.notifyAll();
                }

            } catch (Exception e) {
                packet.setThrowable(e);
            }
        }
    }

    private synchronized void enableReadAndWrite() {
        selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private synchronized void disableWrite() {
        int ops = selectionKey.interestOps();
        if((ops & SelectionKey.OP_WRITE) != 0) {
            selectionKey.interestOps(ops & (~SelectionKey.OP_WRITE));
        }
    }

    private synchronized void disableRead() {
        int ops = selectionKey.interestOps();
        if((ops & SelectionKey.OP_READ) != 0) {
            selectionKey.interestOps(ops & (~SelectionKey.OP_READ));
        }
    }

    private class Packet {

        private long xid;

        private String data;

        private String response;

        private boolean done;

        private Throwable throwable;

        public String getData() {
            return data + ":" +xid;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public boolean isDone() {
            return done;
        }

        public void setDone(boolean done) {
            this.done = done;
        }

        public long getXid() {
            return xid;
        }

        public void setXid(long xid) {
            this.xid = xid;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }
    }
}
