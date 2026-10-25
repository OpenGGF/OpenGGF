package com.openggf.control;

import java.util.Objects;

/**
 * Engine-internal ownership of recorded controller input during a host step.
 * The lease changes only who owns a logical Start edge; it supplies no input,
 * mode, timing or gameplay state and is separate from foreign frame drivers.
 */
public final class RecordedInputOwnership {
    private RecordedInputOwnership() { }

    public static Lease acquire(InputHandler handler) {
        Objects.requireNonNull(handler, "handler");
        synchronized (handler) {
            if (handler.recordedInputOwner != null) {
                throw new IllegalStateException("recorded input already has an owner");
            }
            Lease lease = new Lease(handler, Thread.currentThread());
            handler.recordedInputOwner = lease;
            return lease;
        }
    }

    public static boolean active(InputHandler handler) {
        Objects.requireNonNull(handler, "handler");
        synchronized (handler) {
            Lease lease = handler.recordedInputOwner;
            if (lease == null) return false;
            lease.requireOwnerThread();
            return true;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final InputHandler handler;
        private final Thread owner;
        private boolean closed;

        private Lease(InputHandler handler, Thread owner) {
            this.handler = handler;
            this.owner = owner;
        }

        private void requireOwnerThread() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("recorded input belongs to another thread");
            }
        }

        @Override
        public void close() {
            requireOwnerThread();
            synchronized (handler) {
                if (closed) return;
                if (handler.recordedInputOwner != this) {
                    throw new IllegalStateException("recorded input owner was replaced");
                }
                handler.recordedInputOwner = null;
                closed = true;
            }
        }
    }
}
