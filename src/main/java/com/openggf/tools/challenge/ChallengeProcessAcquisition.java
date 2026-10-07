package com.openggf.tools.challenge;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Acquisition rollback before a spawned JVM can join its host/hook ownership list. */
final class ChallengeProcessAcquisition {
    private ChallengeProcessAcquisition() {}

    static void unwind(Throwable failure, Process process, Path directory, Thread hook,
            ExecutorService executor, Closeable... streams) {
        ChallengeCleanup.closeAll(failure,
                () -> {
                    if (hook != null) {
                        try { Runtime.getRuntime().removeShutdownHook(hook); }
                        catch (IllegalStateException shutdownInProgress) { }
                    }
                },
                () -> reap(process),
                () -> { if (executor != null) executor.shutdownNow(); });
        for (Closeable stream : streams)
            if (stream != null) ChallengeCleanup.closeAll(failure, stream::close);
        ChallengeCleanup.closeAll(failure, () -> {
            if (process.isAlive())
                throw new IOException("Acquired worker teardown pending: " + process.pid());
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        });
    }

    private static void reap(Process process) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            process.destroyForcibly();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
            while (process.isAlive() && System.nanoTime() < deadline) {
                try { process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
                catch (InterruptedException failure) { interrupted = true; }
            }
            if (process.isAlive()) throw new IOException("Acquired worker did not terminate: " + process.pid());
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
