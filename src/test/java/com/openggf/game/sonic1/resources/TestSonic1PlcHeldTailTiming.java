package com.openggf.game.sonic1.resources;

import com.openggf.data.Rom;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Exercises the real S1 arm owner through the shared held-iteration coordinator.
 * Retail FixBugs=0 RunPLC (sonic.asm:1379-1415) exposes its pattern count before
 * code-table preparation; the fixed branch moves that write after preparation.
 * A held gameplay counter cannot locate that interrupt. Only matching readiness
 * may override the coordinator's coarse untimed fallback.
 */
class TestSonic1PlcHeldTailTiming {
    @Test
    void matchingReadinessExposesThePreparedHeadDespiteAHeldIteration() {
        Sonic1PlcService service = recordedHeldIteration(true);
        assertNotNull(service.capture().activeEntry(),
                "matching readiness must bypass the coarse held-counter hint");
        assertEquals(24, service.capture().activeEntry().remainingPatterns(),
                "arming consumes no VBlank pattern budget");
        assertEquals(0, service.capture().queuedEntries().size());
    }

    @Test
    void absentReadinessKeepsTheSameSubmittedHeadUnprepared() {
        Sonic1PlcService service = recordedHeldIteration(false);
        assertNull(service.capture().activeEntry());
        assertEquals(1, service.capture().queuedEntries().size());
        assertEquals(24, service.capture().queuedEntries().getFirst().remainingPatterns());
    }

    private static Sonic1PlcService recordedHeldIteration(boolean admit) {
        HardwareTimingService timing = new HardwareTimingService();
        var authority = timing.beginRecordedAdmission();
        Sonic1PlcService service = queuedService();
        service.bindArmTiming(new Sonic1PlcArmTiming(timing));
        PlcFrameLifecycleCoordinator coordinator = new PlcFrameLifecycleCoordinator(service);
        coordinator.markRepresentedIterationDefersLoopTailPreparation();
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
            service.submitArmableHead();
            timing.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            assertEquals(1, timing.pendingHandles().size());
            if (admit) {
                var submitted = timing.pendingHandles().getFirst();
                authority.admitRecordedCompletion(HardwareServiceBoundary.PRE_MAIN_LOOP,
                        HardwareWorkKind.NEMESIS_PLC_QUEUE, submitted.ordinal(),
                        submitted.submissionFingerprint());
            }
            frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            return null;
        });
        assertEquals(admit ? 0 : 1, timing.pendingHandles().size());
        return service;
    }

    @Test
    void untimedFallbackArmsOnTheLagClosureWithoutSpendingItsBudget() {
        Sonic1PlcService service = queuedService();
        PlcFrameLifecycleCoordinator coordinator = new PlcFrameLifecycleCoordinator(service);
        assertFalse(service.ownsTimedLoopTailArm());
        coordinator.markRepresentedIterationDefersLoopTailPreparation();
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
            frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            return null;
        });
        assertNull(service.capture().activeEntry());
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.LAG);
            return null;
        });
        assertNotNull(service.capture().activeEntry());
        assertEquals(24, service.capture().activeEntry().remainingPatterns());
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
            frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            return null;
        });
        assertEquals(21, service.capture().activeEntry().remainingPatterns());
    }

    private static Sonic1PlcService queuedService() {
        Sonic1PlcService service = new Sonic1PlcService(mock(Rom.class));
        // Synthetic descriptor, as in TestSonic1PlcArmTiming: no fixture or recorder input.
        service.restore(new NemesisPlcQueueSnapshot(null,
                List.of(new NemesisPlcQueueSnapshot.Entry(
                        0x2E9A0, 0x6B4, 24, 24))));
        return service;
    }
}
