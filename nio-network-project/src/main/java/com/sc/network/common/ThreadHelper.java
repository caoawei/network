package com.sc.network.common;

public class ThreadHelper {

    public static Thread noneDaemon(Runnable runnable,String threadPrefix) {
        return new Thread(runnable,threadPrefix+"-Thread");
    }

    public static Thread daemon(Runnable runnable,String threadPrefix) {
        Thread thread = new Thread(runnable,threadPrefix+"-Thread");
        thread.setDaemon(true);
        return thread;
    }
}
