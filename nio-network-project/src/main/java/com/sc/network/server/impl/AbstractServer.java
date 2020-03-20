package com.sc.network.server.impl;

import com.sc.network.server.Server;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractServer implements Server {

    AtomicBoolean alive = new AtomicBoolean(true);

}
