package com.openggf.net.host;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestConnectionHygiene {
    @Test
    void concurrentConnectionTurnoverKeepsThePerAddressLimit() throws Exception {
        var counter = new ConnectionHygiene.ConnectionCounter(1);
        var simultaneous = new AtomicInteger();
        var peak = new AtomicInteger();
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(8)) {
            var jobs = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 8; worker++) {
                jobs.add(workers.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (int attempt = 0; attempt < 20_000; attempt++) {
                        if (counter.tryAcquire("127.0.0.1")) {
                            int current = simultaneous.incrementAndGet();
                            peak.accumulateAndGet(current, Math::max);
                            Thread.yield();
                            simultaneous.decrementAndGet();
                            counter.release("127.0.0.1");
                        }
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> job : jobs) job.get(20, TimeUnit.SECONDS);
        }
        assertEquals(1, peak.get(), "one address must never own overlapping connections");
        assertEquals(0, simultaneous.get());
        assertTrue(counter.tryAcquire("127.0.0.1"), "all completed connections release capacity");
        counter.release("127.0.0.1");
    }
}
