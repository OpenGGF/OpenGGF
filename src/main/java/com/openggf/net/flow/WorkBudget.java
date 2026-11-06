package com.openggf.net.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Atomic admission for retained work across source, destination and process limits. */
public final class WorkBudget {
    public static final long PROCESS_BYTE_LIMIT = 64L * 1024 * 1024;
    public static final long DEFERRED_BYTE_RESERVE = 8L * 1024 * 1024;
    // The two fixed ledgers share one 64 MiB retained-payload envelope. Normal
    // work cannot consume the reserve needed to pause an innocent transport.
    public static final WorkBudget PROCESS = new WorkBudget(8192,
            PROCESS_BYTE_LIMIT - DEFERRED_BYTE_RESERVE);
    public static final WorkBudget DEFERRED = new WorkBudget(8192, DEFERRED_BYTE_RESERVE);

    public enum Refusal { ORIGIN, DESTINATION, PROCESS }

    public record Admission<T>(T value, Refusal refusal) {
        public boolean accepted() { return value != null; }
    }

    public static final class Task implements Runnable, AutoCloseable {
        private final Reservation reservation;
        private final List<Reservation> admissions;
        private final Runnable action;
        private final Runnable discarded;
        private final Runnable released;
        private final AtomicBoolean claimed = new AtomicBoolean();

        private Task(Reservation reservation, List<Reservation> admissions, Runnable action,
                     Runnable discarded, Runnable released) {
            this.reservation = reservation;
            this.admissions = List.copyOf(admissions);
            this.action = Objects.requireNonNull(action);
            this.discarded = Objects.requireNonNull(discarded);
            this.released = Objects.requireNonNull(released);
        }

        public static Task reserved(Reservation reservation, List<Reservation> admissions,
                                    Runnable action, Runnable discarded) {
            return new Task(Objects.requireNonNull(reservation), admissions, action,
                    discarded, () -> { });
        }

        public static Task owned(Runnable action, Runnable released) {
            return new Task(null, List.of(), action, () -> { }, released);
        }

        public Reservation reservation() { return reservation; }

        public boolean transferTo(Account destination) {
            if (claimed.get()) return false;
            for (Reservation admission : admissions) {
                if (!admission.transfer(destination)) return false;
            }
            return true;
        }

        @Override public void run() {
            if (!claimed.compareAndSet(false, true)) return;
            try { action.run(); }
            finally { release(); }
        }

        @Override public void close() {
            if (!claimed.compareAndSet(false, true)) return;
            try { discarded.run(); }
            finally { release(); }
        }

        private void release() {
            try { if (reservation != null) reservation.close(); }
            finally { released.run(); }
        }
    }

    public record Cost(int items, long bytes) {
        public Cost {
            if (items < 1 || bytes < 0) throw new IllegalArgumentException("invalid work cost");
        }
    }

    public static final class Account {
        private final WorkBudget owner;
        private final int maxItems;
        private final long maxBytes;
        private int items;
        private long bytes;
        private Runnable pressureEvictor;

        private Account(WorkBudget owner, int maxItems, long maxBytes) {
            this.owner = owner;
            this.maxItems = maxItems;
            this.maxBytes = maxBytes;
        }

        public int retainedItems() {
            synchronized (owner) { return items; }
        }

        public long retainedBytes() {
            synchronized (owner) { return bytes; }
        }

        /** Registers a nonblocking owner action that releases this account's deferred work. */
        public void onPressure(Runnable evictor) {
            synchronized (owner) { pressureEvictor = evictor; }
        }

    }

    public static final class Reservation implements AutoCloseable {
        private final WorkBudget owner;
        private final Account origin;
        private Account destination;
        private Cost cost;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Reservation(WorkBudget owner, Account origin, Account destination, Cost cost) {
            this.owner = owner;
            this.origin = origin;
            this.destination = destination;
            this.cost = cost;
        }

        /** Releases a conservative byte estimate after the actual payload is encoded. */
        public void shrinkBytes(long actualBytes) {
            synchronized (owner) {
                if (closed.get() || actualBytes < 0 || actualBytes > cost.bytes()) {
                    throw new IllegalStateException("invalid reservation adjustment");
                }
                long released = cost.bytes() - actualBytes;
                owner.bytes -= released;
                origin.bytes -= released;
                if (destination != origin) destination.bytes -= released;
                cost = new Cost(cost.items(), actualBytes);
                owner.notifyAll();
            }
        }

        /** Changes the charged owner mailbox without dropping source or process admission. */
        public boolean transfer(Account next) {
            Objects.requireNonNull(next, "next");
            synchronized (owner) {
                if (next.owner != owner) throw new IllegalArgumentException("foreign account");
                if (closed.get()) return false;
                if (destination == next) return true;
                if (next != origin && !fits(next.items, next.bytes, next.maxItems,
                        next.maxBytes, cost.items(), cost.bytes())) return false;
                if (destination != origin) {
                    destination.items -= cost.items();
                    destination.bytes -= cost.bytes();
                }
                if (next != origin) {
                    next.items += cost.items();
                    next.bytes += cost.bytes();
                }
                destination = next;
                owner.notifyAll();
                return true;
            }
        }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            synchronized (owner) {
                owner.items -= cost.items();
                owner.bytes -= cost.bytes();
                origin.items -= cost.items();
                origin.bytes -= cost.bytes();
                if (destination != origin) {
                    destination.items -= cost.items();
                    destination.bytes -= cost.bytes();
                }
                owner.notifyAll();
            }
        }
    }

    private final int maxItems;
    private final long maxBytes;
    private int items;
    private long bytes;
    private final WeakHashMap<Account, Boolean> accounts = new WeakHashMap<>();

    public WorkBudget(int maxItems, long maxBytes) {
        if (maxItems < 1 || maxBytes < 1) throw new IllegalArgumentException("invalid budget");
        this.maxItems = maxItems;
        this.maxBytes = maxBytes;
    }

    public Account account(int maxItems, long maxBytes) {
        if (maxItems < 1 || maxBytes < 1) throw new IllegalArgumentException("invalid account");
        Account account = new Account(this, maxItems, maxBytes);
        synchronized (this) { accounts.put(account, Boolean.TRUE); }
        return account;
    }

    /** Evicts the largest other deferred holder; the callback runs outside the budget lock. */
    public boolean evictHeaviest(Account requester) {
        Runnable evictor;
        synchronized (this) {
            Account heaviest = null;
            for (Account account : accounts.keySet()) {
                if (account == requester || account.pressureEvictor == null || account.items == 0)
                    continue;
                if (heaviest == null || account.bytes > heaviest.bytes
                        || account.bytes == heaviest.bytes && account.items > heaviest.items)
                    heaviest = account;
            }
            if (heaviest == null) return false;
            evictor = heaviest.pressureEvictor;
            heaviest.pressureEvictor = null;
        }
        evictor.run();
        return true;
    }

    public Optional<Reservation> tryReserve(Account origin, Account destination, Cost cost) {
        return Optional.ofNullable(tryReserveDetailed(origin, destination, cost).value());
    }

    public Admission<Reservation> tryReserveDetailed(Account origin, Account destination,
                                                       Cost cost) {
        Admission<List<Reservation>> batch = tryReserveBatchDetailed(origin, destination,
                List.of(cost));
        return new Admission<>(batch.accepted() ? batch.value().getFirst() : null,
                batch.refusal());
    }

    public Optional<List<Reservation>> tryReserveBatch(
            Account origin, Account destination, List<Cost> costs) {
        return Optional.ofNullable(tryReserveBatchDetailed(origin, destination, costs).value());
    }

    public synchronized Admission<List<Reservation>> tryReserveBatchDetailed(
            Account origin, Account destination, List<Cost> costs) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(costs, "costs");
        if (origin.owner != this || destination.owner != this || costs.isEmpty()) {
            throw new IllegalArgumentException("accounts and costs must belong to this budget");
        }
        int itemTotal = 0;
        long byteTotal = 0;
        for (Cost cost : costs) {
            Objects.requireNonNull(cost, "cost");
            itemTotal = Math.addExact(itemTotal, cost.items());
            byteTotal = Math.addExact(byteTotal, cost.bytes());
        }
        if (!fits(origin.items, origin.bytes, origin.maxItems, origin.maxBytes,
                itemTotal, byteTotal)) return new Admission<>(null, Refusal.ORIGIN);
        if (!fits(destination.items, destination.bytes,
                destination.maxItems, destination.maxBytes, itemTotal, byteTotal))
            return new Admission<>(null, Refusal.DESTINATION);
        if (!fits(items, bytes, maxItems, maxBytes, itemTotal, byteTotal))
            return new Admission<>(null, Refusal.PROCESS);
        List<Reservation> reservations = new ArrayList<>(costs.size());
        for (Cost cost : costs) reservations.add(new Reservation(this, origin, destination, cost));
        List<Reservation> accepted = List.copyOf(reservations);
        items += itemTotal;
        bytes += byteTotal;
        origin.items += itemTotal;
        origin.bytes += byteTotal;
        if (destination != origin) {
            destination.items += itemTotal;
            destination.bytes += byteTotal;
        }
        return new Admission<>(accepted, null);
    }

    public synchronized int retainedItems() { return items; }
    public synchronized long retainedBytes() { return bytes; }

    public static long retainedProcessBytes() {
        return PROCESS.retainedBytes() + DEFERRED.retainedBytes();
    }

    /** Waits for retained work to leave an account; callers retry admission afterward. */
    public synchronized void awaitChange(long millis) throws InterruptedException {
        wait(millis);
    }

    private static boolean fits(int items, long bytes, int maxItems, long maxBytes,
                                int moreItems, long moreBytes) {
        return moreItems <= maxItems - items && moreBytes <= maxBytes - bytes;
    }
}
