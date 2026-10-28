package com.openggf.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Bounded, test-owned source scans; callers keep compiler and scanner state inside each task. */
public final class SourceScanExecutor {
    private SourceScanExecutor() { }

    public static <T> List<T> invokeOrdered(
            List<? extends Callable<T>> tasks, int workers) throws Exception {
        if (workers < 1 || workers > 2) {
            throw new IllegalArgumentException("Source scans support one or two workers");
        }
        if (workers == 1 || tasks.size() < 2) {
            List<T> results = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) results.add(task.call());
            return List.copyOf(results);
        }
        try (var executor = Executors.newFixedThreadPool(workers)) {
            List<Future<T>> futures = new ArrayList<>(tasks.size());
            try {
                for (Callable<T> task : tasks) futures.add(executor.submit(task));
                List<T> results = new ArrayList<>(tasks.size());
                for (Future<T> future : futures) results.add(future.get());
                return List.copyOf(results);
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof Exception exception) throw exception;
                if (failure.getCause() instanceof Error error) throw error;
                throw new AssertionError(failure.getCause());
            } finally {
                // Cancel before close waits for termination, including on caller interruption.
                for (Future<T> future : futures) {
                    if (!future.isDone()) future.cancel(true);
                }
            }
        }
    }
}
