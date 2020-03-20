package com.sc.network.common;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractThread implements Runnable {

    protected CountDownLatch startLatch = new CountDownLatch(1);

    protected CountDownLatch shutdownLatch = new CountDownLatch(1);

    protected AtomicBoolean alive = new AtomicBoolean(true);

    public void awaitStart() {
        try {
            startLatch.await();
        } catch (InterruptedException e) {
            // back interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public void awaitShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            // back interrupt status
            Thread.currentThread().interrupt();
        }
    }

}
