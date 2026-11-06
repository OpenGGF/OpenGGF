package com.openggf.net.flow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestWorkBudget {
    @Test
    void deferredPressureEvictsTheLargestOtherHolderOutsideTheBudgetLock() {
        WorkBudget budget = new WorkBudget(3, 10);
        WorkBudget.Account heavy = budget.account(2, 10);
        WorkBudget.Account light = budget.account(2, 10);
        WorkBudget.Account requester = budget.account(2, 10);
        WorkBudget.Reservation large = budget.tryReserve(heavy, heavy,
                new WorkBudget.Cost(1, 7)).orElseThrow();
        WorkBudget.Reservation small = budget.tryReserve(light, light,
                new WorkBudget.Cost(1, 3)).orElseThrow();
        AtomicInteger evicted = new AtomicInteger();
        heavy.onPressure(() -> { evicted.incrementAndGet(); large.close(); });
        light.onPressure(small::close);
        assertTrue(budget.tryReserve(requester, requester, new WorkBudget.Cost(1, 4)).isEmpty());
        assertTrue(budget.evictHeaviest(requester));
        assertEquals(1, evicted.get());
        try (var admitted = budget.tryReserve(requester, requester,
                new WorkBudget.Cost(1, 4)).orElseThrow()) {
            assertEquals(7, budget.retainedBytes());
        }
        small.close();
        assertEquals(0, budget.retainedBytes());
    }

    @Test
    void deferredPayloadReserveStaysInsideTheGlobal64MiBEnvelope() {
        long normalBefore = WorkBudget.PROCESS.retainedBytes();
        long deferredBefore = WorkBudget.DEFERRED.retainedBytes();
        var normal = WorkBudget.PROCESS.account(1, WorkBudget.PROCESS_BYTE_LIMIT);
        var waiting = WorkBudget.DEFERRED.account(1, WorkBudget.DEFERRED_BYTE_RESERVE);
        var normalOther = WorkBudget.PROCESS.account(1, 1);
        var waitingOther = WorkBudget.DEFERRED.account(1, 1);
        try (var held = WorkBudget.PROCESS.tryReserve(normal, normal,
                new WorkBudget.Cost(1, WorkBudget.PROCESS_BYTE_LIMIT
                        - WorkBudget.DEFERRED_BYTE_RESERVE - normalBefore)).orElseThrow();
             var paused = WorkBudget.DEFERRED.tryReserve(waiting, waiting,
                     new WorkBudget.Cost(1, WorkBudget.DEFERRED_BYTE_RESERVE
                             - deferredBefore)).orElseThrow()) {
            assertEquals(WorkBudget.PROCESS_BYTE_LIMIT, WorkBudget.retainedProcessBytes());
            assertTrue(WorkBudget.PROCESS.tryReserve(normalOther, normalOther,
                    new WorkBudget.Cost(1, 1)).isEmpty());
            assertTrue(WorkBudget.DEFERRED.tryReserve(waitingOther, waitingOther,
                    new WorkBudget.Cost(1, 1)).isEmpty());
        }
        assertEquals(normalBefore + deferredBefore, WorkBudget.retainedProcessBytes());
    }

    @Test
    void refusalNamesTheAccountThatMustYieldWithoutChangingAnyBalance() {
        WorkBudget budget = new WorkBudget(3, 12);
        WorkBudget.Account source = budget.account(2, 12);
        WorkBudget.Account other = budget.account(3, 12);
        WorkBudget.Account mailbox = budget.account(2, 12);
        WorkBudget.Account empty = budget.account(3, 12);
        try (WorkBudget.Reservation first = budget.tryReserve(source, mailbox,
                new WorkBudget.Cost(1, 4)).orElseThrow();
             WorkBudget.Reservation second = budget.tryReserve(source, mailbox,
                     new WorkBudget.Cost(1, 4)).orElseThrow()) {
            assertEquals(WorkBudget.Refusal.ORIGIN, budget.tryReserveDetailed(source, mailbox,
                    new WorkBudget.Cost(1, 1)).refusal());
            assertEquals(WorkBudget.Refusal.DESTINATION, budget.tryReserveDetailed(other,
                    mailbox, new WorkBudget.Cost(1, 1)).refusal());
            try (WorkBudget.Reservation third = budget.tryReserve(other, other,
                    new WorkBudget.Cost(1, 1)).orElseThrow()) {
                assertEquals(WorkBudget.Refusal.PROCESS, budget.tryReserveDetailed(other,
                        empty, new WorkBudget.Cost(1, 1)).refusal());
                assertEquals(3, budget.retainedItems());
            }
        }
        assertEquals(0, budget.retainedItems());
    }

    @Test
    void compoundAdmissionIsAllOrNoneAcrossEveryAccount() {
        WorkBudget budget = new WorkBudget(2, 10);
        WorkBudget.Account source = budget.account(2, 10);
        WorkBudget.Account destination = budget.account(2, 10);
        assertTrue(budget.tryReserveBatch(source, destination,
                List.of(new WorkBudget.Cost(1, 6), new WorkBudget.Cost(1, 5))).isEmpty());
        assertEquals(0, budget.retainedItems());
        assertEquals(0, source.retainedBytes());
        assertEquals(0, destination.retainedBytes());

        List<WorkBudget.Reservation> accepted = budget.tryReserveBatch(source, destination,
                List.of(new WorkBudget.Cost(1, 6), new WorkBudget.Cost(1, 4))).orElseThrow();
        assertEquals(2, budget.retainedItems());
        assertEquals(10, budget.retainedBytes());
        assertTrue(budget.tryReserve(source, destination, new WorkBudget.Cost(1, 1)).isEmpty());
        accepted.getFirst().close();
        accepted.getFirst().close();
        assertEquals(1, budget.retainedItems());
        accepted.getLast().close();
        assertEquals(0, budget.retainedItems());
        assertEquals(0, source.retainedBytes());
        assertEquals(0, destination.retainedBytes());
    }

    @Test
    void simultaneousLastByteAndItemAdmitOnlyOne() throws Exception {
        WorkBudget budget = new WorkBudget(1, 1);
        WorkBudget.Account source = budget.account(1, 1);
        WorkBudget.Account destination = budget.account(1, 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicInteger accepted = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<WorkBudget.Reservation> winner =
                new java.util.concurrent.atomic.AtomicReference<>();
        for (int i = 0; i < 2; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    var reservation = budget.tryReserve(source, destination,
                            new WorkBudget.Cost(1, 1));
                    reservation.ifPresent(value -> {
                        accepted.incrementAndGet();
                        winner.set(value);
                    });
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(finished.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1, accepted.get());
        assertEquals(1, budget.retainedItems());
        winner.get().close();
        assertEquals(0, budget.retainedItems());
    }

    @Test
    void destinationTransferKeepsSourceAndProcessChargedUntilFinalRelease() {
        WorkBudget budget = new WorkBudget(2, 8);
        WorkBudget.Account source = budget.account(2, 8);
        WorkBudget.Account broker = budget.account(2, 8);
        WorkBudget.Account room = budget.account(1, 4);
        WorkBudget.Reservation held = budget.tryReserve(source, broker,
                new WorkBudget.Cost(1, 4)).orElseThrow();
        WorkBudget.Reservation blocker = budget.tryReserve(broker, room,
                new WorkBudget.Cost(1, 4)).orElseThrow();
        assertFalse(held.transfer(room));
        assertEquals(2, broker.retainedItems());
        assertEquals(2, budget.retainedItems());
        blocker.close();
        assertTrue(held.transfer(room));
        assertEquals(1, source.retainedItems());
        assertEquals(0, broker.retainedItems());
        assertEquals(1, room.retainedItems());
        held.close();
        held.close();
        assertEquals(0, budget.retainedItems());
        assertEquals(0, room.retainedBytes());
    }

    @Test
    void conservativeEncodedCostShrinksEveryAccountAndStillReleasesOnce() {
        WorkBudget budget = new WorkBudget(2, 16);
        WorkBudget.Account source = budget.account(2, 16);
        WorkBudget.Account destination = budget.account(2, 16);
        WorkBudget.Reservation held = budget.tryReserve(source, destination,
                new WorkBudget.Cost(1, 12)).orElseThrow();
        held.shrinkBytes(3);
        assertEquals(3, budget.retainedBytes());
        assertEquals(3, source.retainedBytes());
        assertEquals(3, destination.retainedBytes());
        held.close();
        held.close();
        assertEquals(0, budget.retainedBytes());
        assertEquals(0, source.retainedBytes());
        assertEquals(0, destination.retainedBytes());
    }

    @Test
    void discardedTaskNeverRunsAndExecutingTaskStaysChargedUntilItReturns() {
        WorkBudget budget = new WorkBudget(1, 8);
        WorkBudget.Account source = budget.account(1, 8);
        WorkBudget.Account destination = budget.account(1, 8);
        AtomicInteger actions = new AtomicInteger();
        AtomicInteger discards = new AtomicInteger();
        WorkBudget.Reservation abandoned = budget.tryReserve(source, destination,
                new WorkBudget.Cost(1, 8)).orElseThrow();
        WorkBudget.Task discarded = WorkBudget.Task.reserved(abandoned, List.of(abandoned),
                actions::incrementAndGet, discards::incrementAndGet);
        discarded.close();
        discarded.close();
        discarded.run();
        assertEquals(0, actions.get());
        assertEquals(1, discards.get());
        assertEquals(0, budget.retainedItems());

        WorkBudget.Reservation active = budget.tryReserve(source, destination,
                new WorkBudget.Cost(1, 8)).orElseThrow();
        var task = new java.util.concurrent.atomic.AtomicReference<WorkBudget.Task>();
        task.set(WorkBudget.Task.reserved(active, List.of(active), () -> {
            task.get().close();
            assertEquals(1, budget.retainedItems());
            assertEquals(8, destination.retainedBytes());
            actions.incrementAndGet();
        }, discards::incrementAndGet));
        task.get().run();
        task.get().run();
        assertEquals(1, actions.get());
        assertEquals(1, discards.get());
        assertEquals(0, budget.retainedBytes());
    }
}
