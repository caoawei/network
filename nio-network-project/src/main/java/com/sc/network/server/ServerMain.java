package com.sc.network.server;

public class ServerMain {

    public static void main(String[] args) {

        Server server = null;

        try {
            server.start();
        } catch (Exception e) {
            server.shutDown();
        }

    }
}
