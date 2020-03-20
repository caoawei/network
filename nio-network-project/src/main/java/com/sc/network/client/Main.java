package com.sc.network.client;

import java.net.InetSocketAddress;

public class Main {

    public static void main(String[] args) throws Exception {

        InetSocketAddress socketAddress = new InetSocketAddress(3900);
        Client client = new Client(socketAddress);

        client.start();


        String dataPattern = "第%s此请求服务当前时间";

        for (int i = 1; i <= 5; i++) {

            String resp = client.request(String.format(dataPattern,(i++)));

            final int t = i;
            new Thread(() -> {
                try {
                    client.request(String.format(resp,t));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },"RequestThread-"+i).start();
        }
    }
}
