package com.openggf.game.soniccd.timeattack;

import java.util.concurrent.SynchronousQueue;

/**
 * Runs a native program's main code as straight-line Java between V-blank waits. The program body runs
 * on its own virtual thread but never concurrently with the host: {@link #step} hands control to it and
 * blocks until it reaches its next {@link #vblank} (or ends), so all program state is touched by one
 * thread at a time and every hand-off is a happens-before edge. This keeps a long native routine with
 * waits inside nested loops (fades, slides, blinking cursors) in its native shape instead of an
 * explicit state machine.
 */
final class CdProgramFiber implements AutoCloseable {
    private static final Object TOKEN = new Object();
    private final SynchronousQueue<Object> toProgram = new SynchronousQueue<>();
    private final SynchronousQueue<Object> toHost = new SynchronousQueue<>();
    private final Runnable body;
    private Thread thread;
    private volatile boolean finished;
    /** Holds whatever the program body threw, for the host to rethrow from {@link #step}. */
    private volatile java.util.concurrent.FutureTask<Void> task;

    CdProgramFiber(Runnable body) { this.body = body; }

    /** Runs the program to its next V-blank wait. False once the program has returned. */
    boolean step() {
        if (finished) return false;
        try {
            if (thread == null) {
                java.util.concurrent.FutureTask<Void> run = new java.util.concurrent.FutureTask<>(body, null);
                task = run;
                thread = Thread.ofVirtual().name("soniccd-program").start(() -> {
                    run.run();                                   // keeps the body's failure for the host
                    finished = true;
                    if (!abandoned(run)) {                       // a closed program has nobody waiting
                        try { toHost.put(TOKEN); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                });
            } else {
                toProgram.put(TOKEN);
            }
            toHost.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running the program", e);
        }
        if (finished) rethrowFailure();
        return !finished;
    }

    /** True when the body ended because the host closed the program. */
    private static boolean abandoned(java.util.concurrent.FutureTask<Void> run) {
        try {
            run.get();
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause() instanceof Abandoned;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    /** Rethrows on the host thread whatever ended the program body. */
    private void rethrowFailure() {
        try {
            task.get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException r) throw r;
            if (t instanceof Error err) throw err;
            throw new IllegalStateException(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading the program's result", e);
        }
    }

    /** Program side: give the frame back to the host until the next V-blank. */
    void vblank() {
        try {
            toHost.put(TOKEN);
            toProgram.take();
        } catch (InterruptedException e) {
            throw new Abandoned();
        }
    }

    boolean finished() { return finished; }

    @Override
    public void close() {
        if (thread != null && !finished) thread.interrupt();
    }

    private static final class Abandoned extends RuntimeException {
        Abandoned() { super(null, null, false, false); }
    }
}
