package com.openggf.net.client;

import com.openggf.net.flow.WorkBudget;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.Function;

/** One physical WebSocket write at a time, with bounded retained work and independent stop. */
final class OutboundWriter {
    private enum State { ACTIVE, RETIRING, RETIRED }

    record Action(Supplier<? extends CompletionStage<?>> write, long bytes) {
        Action {
            Objects.requireNonNull(write, "write");
            if (bytes < 0) throw new IllegalArgumentException("negative write bytes");
        }
    }

    final class Prepared {
        private final Pending pending;
        private Prepared(Pending pending) { this.pending = pending; }
        void dispatch() {
            Pending next;
            synchronized (OutboundWriter.this) {
                pending.armed = true;
                next = takeNext();
            }
            if (next != null) write(next);
        }
    }

    private static final class Pending {
        final WorkBudget.Reservation reservation;
        final Supplier<? extends CompletionStage<?>> action;
        boolean armed;
        Pending(WorkBudget.Reservation reservation, Supplier<? extends CompletionStage<?>> action) {
            this.reservation = reservation;
            this.action = action;
        }
    }

    private final WorkBudget budget;
    private final WorkBudget.Account origin;
    private final WorkBudget.Account destination;
    private final Supplier<WebSocket> socket;
    private final Runnable failure;
    private final ArrayDeque<Pending> queued = new ArrayDeque<>();
    private final CompletableFuture<Void> retired = new CompletableFuture<>();
    private State state = State.ACTIVE;
    private boolean drainAccepted;
    private Pending active;
    private boolean closeStarted;

    OutboundWriter(WorkBudget budget, int maxItems, long maxBytes,
                   Supplier<WebSocket> socket, Runnable failure) {
        this.budget = Objects.requireNonNull(budget, "budget");
        this.origin = budget.account(maxItems, maxBytes);
        this.destination = budget.account(maxItems, maxBytes);
        this.socket = Objects.requireNonNull(socket, "socket");
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    boolean offerEncoded(Supplier<String> encoder, long ceiling) {
        Optional<Prepared> send = prepareEncoded(encoder, ceiling,
                text -> () -> socket.get().sendText(text, true));
        if (send.isEmpty()) { overflow(); return false; }
        send.get().dispatch();
        return true;
    }

    Optional<Prepared> prepareEncoded(Supplier<String> encoder, long ceiling,
            Function<String, Supplier<? extends CompletionStage<?>>> sender) {
        return prepareEncodedBatch(List.of(encoder), ceiling, sender).map(List::getFirst);
    }

    Optional<List<Prepared>> prepareEncodedBatch(List<Supplier<String>> encoders, long ceiling,
            Function<String, Supplier<? extends CompletionStage<?>>> sender) {
        Objects.requireNonNull(encoders, "encoders");
        Objects.requireNonNull(sender, "sender");
        if (ceiling < 1 || encoders.isEmpty()) throw new IllegalArgumentException("invalid encoded batch");
        List<WorkBudget.Cost> costs = encoders.stream()
                .map(ignored -> new WorkBudget.Cost(1, ceiling)).toList();
        Optional<List<WorkBudget.Reservation>> admitted =
                budget.tryReserveBatch(origin, destination, costs);
        if (admitted.isEmpty()) return Optional.empty();
        try {
            List<Pending> prepared = new ArrayList<>(encoders.size());
            for (int i = 0; i < encoders.size(); i++) {
                String encoded = Objects.requireNonNull(encoders.get(i).get(), "encoded text");
                long bytes = encoded.getBytes(StandardCharsets.UTF_8).length;
                if (bytes > ceiling) throw new IllegalArgumentException("encoded message exceeds frame limit");
                admitted.get().get(i).shrinkBytes(bytes);
                prepared.add(new Pending(admitted.get().get(i), sender.apply(encoded)));
            }
            List<Prepared> result = new ArrayList<>(prepared.size());
            for (Pending pending : prepared) result.add(new Prepared(pending));
            List<Prepared> accepted = List.copyOf(result);
            synchronized (this) {
                if (state != State.ACTIVE) {
                    admitted.get().forEach(WorkBudget.Reservation::close);
                    return Optional.empty();
                }
                prepared.forEach(queued::addLast);
                return Optional.of(accepted);
            }
        } catch (Throwable failure) {
            admitted.get().forEach(WorkBudget.Reservation::close);
            throw failure;
        }
    }

    boolean offerBinary(byte[] data) {
        Optional<Prepared> send = prepareBinary(data);
        if (send.isEmpty()) { overflow(); return false; }
        send.ifPresent(Prepared::dispatch);
        return true;
    }

    Optional<Prepared> prepareBinary(byte[] data) {
        Objects.requireNonNull(data, "data");
        var cost = new WorkBudget.Cost(1, data.length);
        Optional<WorkBudget.Reservation> reservation = budget.tryReserve(origin, destination, cost);
        if (reservation.isEmpty()) return Optional.empty();
        byte[] copy;
        try { copy = data.clone(); }
        catch (RuntimeException | Error failedCopy) { reservation.get().close(); throw failedCopy; }
        Optional<Prepared> send = add(reservation.get(),
                () -> socket.get().sendBinary(ByteBuffer.wrap(copy), true));
        return send;
    }

    Optional<Prepared> prepare(Action action) {
        Optional<WorkBudget.Reservation> reservation = budget.tryReserve(origin, destination,
                new WorkBudget.Cost(1, action.bytes()));
        if (reservation.isEmpty()) return Optional.empty();
        return add(reservation.get(), action.write());
    }

    private Optional<Prepared> add(WorkBudget.Reservation reservation,
                                   Supplier<? extends CompletionStage<?>> action) {
        synchronized (this) {
            if (state != State.ACTIVE) {
                reservation.close();
                return Optional.empty();
            }
            Pending pending = new Pending(reservation, action);
            queued.addLast(pending);
            return Optional.of(new Prepared(pending));
        }
    }

    private Pending takeNext() {
        if (state != State.ACTIVE && !(state == State.RETIRING && drainAccepted)
                || active != null || queued.isEmpty()
                || !queued.getFirst().armed) return null;
        active = queued.removeFirst();
        return active;
    }

    private void write(Pending pending) {
        try {
            CompletionStage<?> stage = Objects.requireNonNull(pending.action.get(), "write stage");
            stage.whenComplete((ignored, error) -> completed(pending, error));
        } catch (RuntimeException | Error error) {
            completed(pending, error);
            if (error instanceof Error fatal) throw fatal;
        }
    }

    private void completed(Pending pending, Throwable error) {
        Pending next = null;
        boolean close = false;
        synchronized (this) {
            pending.reservation.close();
            if (active != pending || state == State.RETIRED) return;
            active = null;
            if (error == null && (state == State.ACTIVE || drainAccepted)) next = takeNext();
            if (error == null && next == null && state == State.RETIRING && queued.isEmpty()) {
                close = true;
            }
        }
        if (error != null) { overflow(); return; }
        if (next != null) write(next);
        else if (close) closeTransport();
    }

    CompletionStage<Void> stop(Duration deadline) {
        return stop(deadline, false);
    }

    CompletionStage<Void> stop(Duration deadline, boolean drainAccepted) {
        Objects.requireNonNull(deadline, "deadline");
        boolean close;
        Pending next = null;
        synchronized (this) {
            if (state == State.ACTIVE) {
                state = State.RETIRING;
                this.drainAccepted = drainAccepted;
                if (!drainAccepted) {
                    while (!queued.isEmpty()) queued.removeFirst().reservation.close();
                } else {
                    next = takeNext();
                }
                long timeoutMillis = Math.max(1, deadline.toMillis());
                CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS)
                        .execute(this::abort);
            }
            close = state == State.RETIRING && active == null && queued.isEmpty();
        }
        if (next != null) write(next);
        if (close) closeTransport();
        return retired.copy();
    }

    private void closeTransport() {
        synchronized (this) {
            if (state != State.RETIRING || closeStarted) return;
            closeStarted = true;
        }
        try {
            WebSocket ws = socket.get();
            if (ws == null) { finish(); return; }
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                    .whenComplete((ignored, error) -> {
                        if (error == null) finish();
                        else abort();
                    });
        } catch (RuntimeException | Error error) {
            abort();
            if (error instanceof Error fatal) throw fatal;
        }
    }

    private void overflow() {
        abort();
        failure.run();
    }

    void abort() {
        WebSocket ws;
        synchronized (this) {
            if (state == State.RETIRED) return;
            state = State.RETIRED;
            while (!queued.isEmpty()) queued.removeFirst().reservation.close();
            if (active != null) active.reservation.close();
            active = null;
            ws = socket.get();
            retired.complete(null);
        }
        if (ws != null) ws.abort();
    }

    private void finish() {
        synchronized (this) {
            if (state != State.RETIRING) return;
            state = State.RETIRED;
            retired.complete(null);
        }
    }

    synchronized int retainedItems() { return origin.retainedItems(); }
    synchronized long retainedBytes() { return origin.retainedBytes(); }
}
