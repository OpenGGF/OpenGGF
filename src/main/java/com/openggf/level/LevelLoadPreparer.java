package com.openggf.level;

import com.openggf.level.resources.PreparedLevelBuild;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Holds at most one level build running ahead of its install.
 *
 * <p>The build runs on a shared daemon thread and is a pure function of the
 * ROM, so it needs no rewind capture: a rewind that re-runs the transition
 * simply finds the slot empty and loads synchronously. The install side
 * always joins the build, so the frame on which a level is installed is
 * decided by the transition owner, never by host thread timing.
 */
final class LevelLoadPreparer {
    private static final Logger LOGGER = Logger.getLogger(LevelLoadPreparer.class.getName());

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "level-load-preparer");
        thread.setDaemon(true);
        return thread;
    });

    /** Test seam: when false, {@link #prepare} is a no-op and installs load synchronously. */
    private static volatile boolean enabled = true;

    /** What a pending build is for: only a load of the same kind may take it. */
    enum Purpose {
        /** A seamless act transition into the level, after an optional layout mutation. */
        ACT_TRANSITION,
        /** A synchronous reload the game has committed to (death respawn). */
        RELOAD
    }

    private record Pending(Object owner, Purpose purpose, int levelIndex, String mutationKey,
                           Future<PreparedLevelBuild> future) {
    }

    private Pending pending;
    private int installedFromPreparedCount;

    static void setEnabledForTests(boolean value) {
        enabled = value;
    }

    /** Starts (or restarts) preparation of {@code levelIndex}, discarding any other pending build. */
    synchronized void prepare(Object owner, int levelIndex, String mutationKey,
                              Callable<PreparedLevelBuild> task) {
        prepare(owner, Purpose.ACT_TRANSITION, levelIndex, mutationKey, task);
    }

    synchronized void prepare(Object owner, Purpose purpose, int levelIndex, String mutationKey,
                              Callable<PreparedLevelBuild> task) {
        discard();
        if (!enabled) {
            return;
        }
        pending = new Pending(Objects.requireNonNull(owner), Objects.requireNonNull(purpose),
                levelIndex, mutationKey, EXECUTOR.submit(task));
    }

    /**
     * Takes only the owning loader's matching transition build, joining it if still running.
     * Returns {@code null} when nothing was prepared for that index or the
     * build failed; the caller then loads synchronously.
     */
    synchronized PreparedLevelBuild take(Object owner, int levelIndex, String mutationKey) {
        return take(owner, Purpose.ACT_TRANSITION, levelIndex, mutationKey, build -> true);
    }

    /**
     * As {@link #take(Object, int, String)} for a build of {@code purpose}; a finished build
     * that {@code stillValid} rejects is discarded and {@code null} returned.
     */
    synchronized PreparedLevelBuild take(Object owner, Purpose purpose, int levelIndex, String mutationKey,
                                         java.util.function.Predicate<PreparedLevelBuild> stillValid) {
        if (pending == null) {
            return null;
        }
        if (pending.owner() != owner || pending.purpose() != purpose || pending.levelIndex() != levelIndex
                || !Objects.equals(pending.mutationKey(), mutationKey)) {
            discard();
            return null;
        }
        Pending taken = pending;
        pending = null;
        try {
            PreparedLevelBuild prepared = taken.future().get();
            if (!stillValid.test(prepared)) {
                return null;
            }
            installedFromPreparedCount++;
            return prepared;
        } catch (InterruptedException e) {
            awaitCompletion(taken);
            Thread.currentThread().interrupt();
            LOGGER.warning("Prepared level build interrupted; loading synchronously");
            return null;
        } catch (ExecutionException e) {
            LOGGER.warning("Prepared level build failed; loading synchronously: " + e.getCause());
            return null;
        }
    }

    /** Retires pending work of any other purpose than {@code kept}. */
    synchronized void discardUnless(Purpose kept) {
        if (pending != null && pending.purpose() != kept) {
            discard();
        }
    }

    /** Retires pending work before its owner can release the ROM it reads. */
    synchronized void discard() {
        if (pending != null) {
            Pending discarded = pending;
            pending = null;
            awaitCompletion(discarded);
        }
    }

    private static void awaitCompletion(Pending build) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    build.future().get();
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                } catch (ExecutionException ignored) {
                    return;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /** Number of installs served from a prepared build (test observability). */
    synchronized int installedFromPreparedCount() {
        return installedFromPreparedCount;
    }

    /** The purpose of the pending build, or {@code null} when none is pending (test observability). */
    synchronized Purpose pendingPurpose() {
        return pending == null ? null : pending.purpose();
    }
}
