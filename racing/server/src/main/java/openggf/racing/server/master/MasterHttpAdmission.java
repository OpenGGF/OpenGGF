package openggf.racing.server.master;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * Server-wide bounds: 32 outstanding jobs and 8 MiB of copied request bodies.
 * One broker drain task serves a removable bounded queue, so connect/close churn
 * cannot leave an unbounded number of canceled tasks in the broker executor.
 */
final class MasterHttpAdmission {
    private static final System.Logger LOG = System.getLogger(MasterHttpAdmission.class.getName());
    static final int MAX_REQUESTS = 32;
    static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private final Executor broker;
    private final ArrayDeque<Ticket> queue = new ArrayDeque<>();
    private int requests;
    private long bodyBytes;
    private boolean drainScheduled;

    MasterHttpAdmission(Executor broker) {
        this.broker = broker;
    }

    final class Ticket {
        private final int bytes;
        private Runnable action;
        private Runnable onRejected;
        private boolean running;
        private boolean finished;

        private Ticket(int bytes) {
            this.bytes = bytes;
        }

        boolean isFinished() {
            synchronized (MasterHttpAdmission.this) {
                return finished;
            }
        }

        void finish() {
            synchronized (MasterHttpAdmission.this) {
                if (finished) return;
                finished = true;
                action = null;
                onRejected = null;
                // A handler already running still owns its body until it returns.
                if (!running) {
                    queue.remove(this);
                    release(this);
                }
            }
        }

        void reject() {
            Runnable callback;
            synchronized (MasterHttpAdmission.this) {
                callback = onRejected;
                finish();
            }
            if (callback != null) callback.run();
        }
    }

    synchronized Ticket reserve(int bytes) {
        if (bytes < 0 || requests >= MAX_REQUESTS || bytes > MAX_BODY_BYTES - bodyBytes) {
            return null;
        }
        requests++;
        bodyBytes += bytes;
        return new Ticket(bytes);
    }

    synchronized void dispatch(Ticket ticket, Runnable action, Runnable onRejected) {
        if (ticket.finished) return;
        ticket.action = action;
        ticket.onRejected = onRejected;
        queue.add(ticket);
        if (drainScheduled) return;
        drainScheduled = true;
        try {
            broker.execute(this::drain);
        } catch (RuntimeException | Error rejected) {
            drainScheduled = false;
            ticket.finish();
            throw rejected;
        }
    }

    private void drain() {
        // Yield to broker heartbeats, authentication and room work even if arrivals
        // replenish this queue continuously while a slice is executing.
        for (int processed = 0; processed < MAX_REQUESTS; processed++) {
            Ticket ticket;
            Runnable action;
            synchronized (this) {
                ticket = queue.poll();
                if (ticket == null) {
                    drainScheduled = false;
                    return;
                }
                ticket.running = true;
                action = ticket.action;
                ticket.action = null;
            }
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                LOG.log(System.Logger.Level.ERROR, "master HTTP broker action failed", failure);
                rejectSafely(ticket);
            } finally {
                synchronized (this) {
                    ticket.running = false;
                    if (ticket.finished) release(ticket);
                }
            }
        }
        synchronized (this) {
            if (queue.isEmpty()) {
                drainScheduled = false;
                return;
            }
            try {
                broker.execute(this::drain);
            } catch (RuntimeException | Error stopped) {
                drainScheduled = false;
                Ticket canceled;
                while ((canceled = queue.poll()) != null) rejectSafely(canceled);
            }
        }
    }

    private void rejectSafely(Ticket ticket) {
        try {
            ticket.reject();
        } catch (RuntimeException | Error failure) {
            // One channel's cleanup must not strand the drain or other reservations.
            LOG.log(System.Logger.Level.ERROR, "master HTTP rejection callback failed", failure);
        }
    }

    private void release(Ticket ticket) {
        requests--;
        bodyBytes -= ticket.bytes;
    }
}
