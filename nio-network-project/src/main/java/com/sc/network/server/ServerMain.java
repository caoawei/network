package com.sc.network.server;

import com.sc.network.common.ServerConfig;
import com.sc.network.server.impl.NioServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class ServerMain {

    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {

        // 属性配置
        Properties props = new Properties();

        ServerConfig serverConfig = new ServerConfig(props);

        Server server = new NioServer(serverConfig);

        try {

            server.start();

        } catch (Exception e) {

            logger.error(String.format("server start failure,error is %s",e.getMessage()),e);

            System.exit(0);
        }

    }
}
