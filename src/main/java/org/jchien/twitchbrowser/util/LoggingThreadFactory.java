package org.jchien.twitchbrowser.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jchien
 */
public class LoggingThreadFactory implements ThreadFactory {
    private AtomicInteger counter = new AtomicInteger(0);

    private final String threadNamePrefix;

    public LoggingThreadFactory(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        int threadNum = counter.getAndIncrement();
        String threadName = threadNamePrefix + "-" + threadNum;
        Thread t = new Thread(r, threadName);

        final Logger log = LoggerFactory.getLogger(t.getName());
        t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.error(e.getMessage(), e);
            }
        });

        return t;
    }
}
