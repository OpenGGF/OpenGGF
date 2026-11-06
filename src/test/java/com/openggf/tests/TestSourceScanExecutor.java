package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TestSourceScanExecutor {
    @Test
    void overlappingTasksReturnInInputOrderAndAllWorkersFinish() throws Exception {
        CountDownLatch secondFinished = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicBoolean firstStillRunningWhenSecondFinished = new AtomicBoolean();
        Callable<Integer> first = () -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            firstStarted.countDown();
            try {
                assertTrue(secondFinished.await(5, TimeUnit.SECONDS), "both workers must run");
                return 1;
            } finally {
                active.decrementAndGet();
            }
        };
        Callable<Integer> second = () -> {
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS), "first worker must be running");
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            firstStillRunningWhenSecondFinished.set(active.get() == 2);
            active.decrementAndGet();
            secondFinished.countDown();
            return 2;
        };
        assertEquals(List.of(1, 2), SourceScanExecutor.invokeOrdered(List.of(first, second), 2));
        assertTrue(firstStillRunningWhenSecondFinished.get());
        assertEquals(2, peak.get());
        assertEquals(0, active.get(), "the executor owns completion, not just submission");
    }

    @Test
    void workerFailureIsPreservedAndBlockedSiblingTerminatesBeforeReturn() {
        CountDownLatch siblingStarted = new CountDownLatch(1);
        CountDownLatch siblingStopped = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        IllegalStateException expected = new IllegalStateException("scanner failed");
        Callable<Integer> failing = () -> {
            assertTrue(siblingStarted.await(5, TimeUnit.SECONDS), "sibling must be running");
            throw expected;
        };
        Callable<Integer> blocked = () -> {
            siblingStarted.countDown();
            try {
                neverReleased.await(5, TimeUnit.SECONDS);
                return 0;
            } catch (InterruptedException failure) {
                interrupted.set(true);
                throw failure;
            } finally {
                siblingStopped.countDown();
            }
        };
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> SourceScanExecutor.invokeOrdered(List.of(failing, blocked), 2)));
        assertTrue(interrupted.get(), "failure must cancel unfinished siblings");
        assertEquals(0, siblingStopped.getCount(), "no worker may outlive the invocation");
    }

    @Test
    void queuedInputsBeyondWorkerCountAreVisitedExactlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var expected = IntStream.range(0, 17).boxed().toList();
        var tasks = expected.stream().<Callable<Integer>>map(index -> () -> {
            calls.incrementAndGet();
            return index;
        }).toList();
        assertEquals(expected, SourceScanExecutor.invokeOrdered(tasks, 2));
        assertEquals(expected.size(), calls.get());
    }

    @Test
    void workerAssertionFailureReachesTheCallingTest() {
        AssertionError expected = new AssertionError("worker oracle failed");
        assertSame(expected, assertThrows(AssertionError.class,
                () -> SourceScanExecutor.invokeOrdered(List.<Callable<Integer>>of(
                        () -> { throw expected; }, () -> 2), 2)));
    }

    @Test
    void serialAndEmptyWorkStayBoundedAndUnsupportedCountsNeverStartTasks() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Callable<Integer> task = calls::incrementAndGet;
        assertEquals(List.of(1, 2), SourceScanExecutor.invokeOrdered(List.of(task, task), 1));
        assertEquals(List.of(), SourceScanExecutor.invokeOrdered(List.<Callable<Integer>>of(), 2));
        for (int workers : List.of(0, 3, 16)) {
            assertThrows(IllegalArgumentException.class,
                    () -> SourceScanExecutor.invokeOrdered(List.of(task), workers));
        }
        assertEquals(2, calls.get());
    }
}
