package com.openggf.game.resources;

import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.rules.DynamicArtDmaServiceModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Failure-boundary regressions for acquisition and owned-iteration retirement. */
class TestPlcOwnedIterationFailureCleanup {
    @Test
    void failedFadePreclaimDiscardsCompletionAndRetainsFadeWithoutReset() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        var blocking = coordinator.beginNativeBlockingFade();
        var failure = new IllegalStateException("preclaim service");
        AtomicInteger completions = new AtomicInteger();
        service.onService = () -> coordinator.afterConsumedIteration(completions::incrementAndGet);
        service.serviceFailure = failure;

        assertSame(failure, assertThrows(IllegalStateException.class,
                coordinator::latchBeforeFadeUpdate));
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(List.of("service:PALETTE_FADE"), service.events);
        assertEquals(0, completions.get());

        service.serviceFailure = null;
        service.onService = null;
        var next = coordinator.latchBeforeFadeUpdate();
        assertTrue(next.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE));
        assertThrows(IllegalStateException.class, coordinator::beginNativeBlockingFade,
                "failed acquisition must not close a retained fade");
        next.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
        next.finish();
        assertEquals(0, completions.get());
        assertEquals(List.of("service:PALETTE_FADE", "service:PALETTE_FADE",
                "prepare:PALETTE_FADE"), service.events);
        blocking.close();
    }

    @Test
    void failedBeforeFadeVblankKeepsPrimaryAndMissingPreparationDiagnostic() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        coordinator.beginNativeBlockingFade();
        var failure = new IllegalArgumentException("game VBlank");
        AtomicReference<PlcLifecycleFrame> failed = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger();
        service.onService = () -> coordinator.afterConsumedIteration(completions::incrementAndGet);

        assertSame(failure, assertThrows(IllegalArgumentException.class, () ->
                coordinator.runLogicalIteration(frame -> {
                    failed.set(frame);
                    frame.dispatchGameVBlank(() -> { throw failure; });
                }, () -> fail("fade must not follow failed game VBlank"), frame -> {
                    fail("body must not follow failed game VBlank");
                    return null;
                })));
        assertMissingPreparation(failure, PlcLifecyclePhase.PALETTE_FADE);
        assertEquals(List.of("service:PALETTE_FADE"), service.events,
                "cleanup must not run the missing preparation");
        assertTerminal(failed.get());
        service.onService = null;
        finishRetainedFade(coordinator);
        assertEquals(0, completions.get());
    }

    @Test
    void failedFadeUpdateKeepsErrorIdentityAndNeverPreparesDuringCleanup() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        coordinator.beginNativeBlockingFade();
        var failure = new AssertionError("fade update");
        AtomicReference<PlcLifecycleFrame> failed = new AtomicReference<>();

        assertSame(failure, assertThrows(AssertionError.class, () ->
                coordinator.runLogicalIteration(failed::set, () -> { throw failure; }, frame -> {
                    fail("body must not follow failed fade");
                    return null;
                })));
        assertMissingPreparation(failure, PlcLifecyclePhase.PALETTE_FADE);
        assertEquals(List.of("service:PALETTE_FADE"), service.events);
        assertTerminal(failed.get());
        finishRetainedFade(coordinator);
    }

    @Test
    void failedPreparationIsNotRetriedAndReleasesOnlyItsOwnedToken() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        coordinator.beginNativeBlockingFade();
        var failure = new IllegalStateException("prepare service");
        service.preparationFailure = failure;
        AtomicReference<PlcLifecycleFrame> failed = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger();
        service.onService = () -> coordinator.afterConsumedIteration(completions::incrementAndGet);

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(failed::set, () -> { }, frame -> {
                    fail("body must not follow failed preparation");
                    return null;
                })));
        assertMissingPreparation(failure, PlcLifecyclePhase.PALETTE_FADE);
        assertEquals(List.of("service:PALETTE_FADE", "prepare:PALETTE_FADE"), service.events);
        assertTerminal(failed.get());
        service.preparationFailure = null;
        service.onService = null;
        finishRetainedFade(coordinator);
        assertEquals(0, completions.get());
    }

    @Test
    void missingPreparationOnSuccessfulBodyIsTerminalOnlyForTheOwnedBoundary() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        AtomicReference<PlcLifecycleFrame> failed = new AtomicReference<>();
        var failure = assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    failed.set(frame);
                    frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
                    return null;
                }));

        assertEquals("missing PLC preparation for ORDINARY_LEVEL", failure.getMessage());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(List.of("service:ORDINARY_LEVEL"), service.events);
        assertTerminal(failed.get());
        finishPause(coordinator);
    }

    @Test
    void failedBodyDoesNotPublishOrRunItsSuccessCompletions() {
        RecordingService service = new RecordingService();
        var dynamicArt = activeDynamicArt();
        var coordinator = new PlcFrameLifecycleCoordinator(service, dynamicArt);
        var failure = new IllegalArgumentException("body");
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<PlcLifecycleFrame> failed = new AtomicReference<>();

        assertSame(failure, assertThrows(IllegalArgumentException.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    failed.set(frame);
                    frame.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                    coordinator.afterConsumedIteration(completions::incrementAndGet);
                    throw failure;
                })));
        verify(dynamicArt, never()).finishProductionIteration(anyBoolean());
        assertTerminal(failed.get());
        finishPause(coordinator);
        verify(dynamicArt, times(1)).finishProductionIteration(false);
        assertEquals(0, completions.get());
    }

    @Test
    void finalPublicationFailureIsNotRetriedAndDoesNotRequireReset() {
        RecordingService service = new RecordingService();
        var dynamicArt = activeDynamicArt();
        var coordinator = new PlcFrameLifecycleCoordinator(service, dynamicArt);
        var failure = new AssertionError("dynamic art publication");
        doThrow(failure).doNothing().when(dynamicArt).finishProductionIteration(false);
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<PlcLifecycleFrame> failed = new AtomicReference<>();

        assertSame(failure, assertThrows(AssertionError.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    failed.set(frame);
                    frame.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                    coordinator.afterConsumedIteration(completions::incrementAndGet);
                    return null;
                })));
        assertEquals(0, failure.getSuppressed().length);
        verify(dynamicArt, times(1)).finishProductionIteration(false);
        assertTerminal(failed.get());
        finishPause(coordinator);
        verify(dynamicArt, times(2)).finishProductionIteration(false);
        assertEquals(0, completions.get());
        assertEquals(List.of("service:NORMAL_PAUSE", "service:NORMAL_PAUSE"), service.events);
    }

    @Test
    void completionFailureDropsRemainingActionsAndDoesNotRepeatPublication() {
        RecordingService service = new RecordingService();
        var dynamicArt = activeDynamicArt();
        var coordinator = new PlcFrameLifecycleCoordinator(service, dynamicArt);
        var failure = new IllegalStateException("completion");
        AtomicInteger first = new AtomicInteger();
        AtomicInteger later = new AtomicInteger();
        AtomicReference<PlcLifecycleFrame> completed = new AtomicReference<>();

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    completed.set(frame);
                    frame.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                    coordinator.afterConsumedIteration(() -> {
                        first.incrementAndGet();
                        throw failure;
                    });
                    coordinator.afterConsumedIteration(later::incrementAndGet);
                    return null;
                })));
        assertEquals(0, failure.getSuppressed().length);
        assertTerminal(completed.get());
        finishPause(coordinator);
        assertEquals(1, first.get());
        assertEquals(0, later.get());
        verify(dynamicArt, times(2)).finishProductionIteration(false);
    }

    @Test
    void throwingCompletionCanReplayAndLeaveAFreshTokenOpen() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        var failure = new IllegalStateException("completion after replay");
        AtomicReference<PlcLifecycleFrame> fresh = new AtomicReference<>();
        AtomicInteger freshCompletion = new AtomicInteger();
        AtomicInteger staleCompletion = new AtomicInteger();

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    frame.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                    coordinator.afterConsumedIteration(() -> {
                        coordinator.runReplayedLogicalIteration(() -> { }, replay -> {
                            replay.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                            return null;
                        });
                        markOneShotDeclarations(coordinator);
                        var next = coordinator.latchBeforeFadeUpdate();
                        fresh.set(next);
                        next.claim(PlcLifecyclePhase.LAG);
                        coordinator.afterConsumedIteration(freshCompletion::incrementAndGet);
                        throw failure;
                    });
                    coordinator.afterConsumedIteration(staleCompletion::incrementAndGet);
                    return null;
                })));

        assertFreshTokenStillOwned(coordinator, fresh.get());
        fresh.get().finish();
        assertEquals(1, freshCompletion.get());
        assertEquals(0, staleCompletion.get());
        finishPause(coordinator);
        assertEquals(1, freshCompletion.get());
    }

    @Test
    void throwingCompletionPreservesDeclarationsForANotYetLatchedNextToken() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        var failure = new IllegalStateException("completion declared next row");

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    frame.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                    coordinator.afterConsumedIteration(() -> {
                        markOneShotDeclarations(coordinator);
                        throw failure;
                    });
                    return null;
                })));

        var next = coordinator.latchBeforeFadeUpdate();
        assertTrue(next.claim(PlcLifecyclePhase.LAG));
        assertTrue(next.hasExplicitDmaQueueService());
        assertTrue(next.defersLoopTailPreparation());
        next.finish();
        finishPause(coordinator);
    }

    @Test
    void failedOuterCallbackCannotDetachATokenCreatedAfterReplayReplacement() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        var failure = new IllegalStateException("outer after replacement");
        AtomicReference<PlcLifecycleFrame> placeholder = new AtomicReference<>();
        AtomicReference<PlcLifecycleFrame> replacement = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger();

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { }, outer -> {
                    placeholder.set(outer);
                    coordinator.runReplayedLogicalIteration(() -> { }, replay -> {
                        replay.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                        return null;
                    });
                    markOneShotDeclarations(coordinator);
                    var next = coordinator.latchBeforeFadeUpdate();
                    replacement.set(next);
                    next.claim(PlcLifecyclePhase.LAG);
                    coordinator.afterConsumedIteration(completions::incrementAndGet);
                    throw failure;
                })));

        assertEquals(0, failure.getSuppressed().length);
        assertTerminal(placeholder.get());
        assertFreshTokenStillOwned(coordinator, replacement.get());
        replacement.get().finish();
        assertEquals(1, completions.get());
        finishPause(coordinator);
    }

    @Test
    void failedUnclaimedIterationConsumesItsDeclarationsWithoutServicingAnything() {
        RecordingService service = new RecordingService();
        var dynamicArt = activeDynamicArt();
        var coordinator = new PlcFrameLifecycleCoordinator(service, dynamicArt,
                DynamicArtDmaServiceModel.SONIC_2_PROCESS_DMA_QUEUE);
        var failure = new IllegalStateException("before claim");
        markOneShotDeclarations(coordinator);

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { throw failure; }, frame -> null)));
        assertEquals(List.of(), service.events);
        verify(dynamicArt, never()).finishProductionIteration(anyBoolean());
        assertNextLagHasNoDeclarations(coordinator, dynamicArt);
    }

    @Test
    void failedClaimConsumesDmaAndDeferralDeclarationsBeforeTheyCanLeak() {
        RecordingService service = new RecordingService();
        var dynamicArt = activeDynamicArt();
        var coordinator = new PlcFrameLifecycleCoordinator(service, dynamicArt,
                DynamicArtDmaServiceModel.SONIC_2_PROCESS_DMA_QUEUE);
        var failure = new IllegalStateException("claim service");
        service.serviceFailure = failure;
        markOneShotDeclarations(coordinator);

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    frame.claim(PlcLifecyclePhase.LAG);
                    return null;
                })));
        service.serviceFailure = null;
        assertNextLagHasNoDeclarations(coordinator, dynamicArt);
    }

    @Test
    void failedPublicationDoesNotArmTheNextRowsOverrunCarry() {
        RecordingService service = new RecordingService();
        var dynamicArt = activeDynamicArt();
        var coordinator = new PlcFrameLifecycleCoordinator(service, dynamicArt);
        var failure = new IllegalStateException("publication after starved row");
        doThrow(failure).doNothing().when(dynamicArt).finishProductionIteration(false);
        coordinator.markRepresentedIterationWithoutVblank();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> finishDeclaredPause(coordinator)));
        finishPause(coordinator);
        verify(dynamicArt, times(2)).finishProductionIteration(false);
        verify(dynamicArt, never()).finishProductionIteration(true);
    }

    @Test
    void failedRowConsumesAPreviousRowsOverrunCarryWithoutPublishing() {
        RecordingService service = new RecordingService();
        var dynamicArt = activeDynamicArt();
        var coordinator = new PlcFrameLifecycleCoordinator(service, dynamicArt);
        coordinator.markRepresentedIterationWithoutVblank();
        finishDeclaredPause(coordinator);
        var failure = new IllegalStateException("successor failed before claim");

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { throw failure; }, frame -> null)));
        finishPause(coordinator);
        verify(dynamicArt, times(2)).finishProductionIteration(false);
        verify(dynamicArt, never()).finishProductionIteration(true);
    }

    @Test
    void failedFadeAcquisitionRetainsHeldWorkAndDoesNotPrepareItDuringCleanup() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        coordinator.markRepresentedIterationDefersLoopTailPreparation();
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
            frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            return null;
        });
        coordinator.beginNativeBlockingFade();
        coordinator.markRepresentedIterationWithoutVblank();
        coordinator.markRepresentedIterationDefersLoopTailPreparation();
        var failure = new IllegalStateException("fade preclaim before retained tail");
        service.serviceFailure = failure;

        assertSame(failure, assertThrows(IllegalStateException.class,
                coordinator::latchBeforeFadeUpdate));
        assertEquals(List.of("service:ORDINARY_LEVEL", "service:PALETTE_FADE"), service.events);
        service.serviceFailure = null;
        var next = coordinator.latchBeforeFadeUpdate();
        assertTrue(next.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE));
        assertFalse(next.defersLoopTailPreparation());
        assertFalse(next.hasExplicitDmaQueueService());
        assertTrue(next.consumedHeldLoopTailPreparation());
        next.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
        next.finish();
        assertEquals(List.of("service:ORDINARY_LEVEL", "service:PALETTE_FADE",
                "service:PALETTE_FADE", "prepare:ORDINARY_LEVEL", "prepare:PALETTE_FADE"), service.events);
    }

    @Test
    void suppressedLagPublicationFailureUsesTheSameOwnedCleanupBoundary() {
        RecordingService service = new RecordingService();
        var dynamicArt = activeDynamicArt();
        var coordinator = new PlcFrameLifecycleCoordinator(service, dynamicArt);
        coordinator.beginNativeBlockingFade();
        var failure = new IllegalStateException("lag publication");
        doThrow(failure).doNothing().when(dynamicArt).finishProductionIteration(true);
        AtomicInteger completions = new AtomicInteger();

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runSuppressedLagIteration(frame -> {
                    frame.claim(PlcLifecyclePhase.LAG);
                    coordinator.afterConsumedIteration(completions::incrementAndGet);
                    return null;
                })));
        coordinator.runSuppressedLagIteration(frame -> {
            frame.claim(PlcLifecyclePhase.LAG);
            return null;
        });
        verify(dynamicArt, times(2)).finishProductionIteration(true);
        assertEquals(0, completions.get());
        finishRetainedFade(coordinator);
    }

    @Test
    void existingSuppressedDiagnosticsRemainBeforeTheNewValidationFailure() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        var failure = new IllegalStateException("body primary");
        var existing = new IllegalArgumentException("earlier owner diagnostic");
        var validation = new AssertionError("preparation validation diagnostic");
        failure.addSuppressed(existing);
        service.validationFailure = validation;

        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    frame.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                    throw failure;
                })));
        assertArrayEquals(new Throwable[] { existing, validation }, failure.getSuppressed());
        service.validationFailure = null;
        finishPause(coordinator);
    }

    @Test
    void identicalPrimaryAndValidationErrorIsNotSelfSuppressedOrReplaced() {
        RecordingService service = new RecordingService();
        var coordinator = new PlcFrameLifecycleCoordinator(service);
        var failure = new AssertionError("same failure from owner and validation");
        service.validationFailure = failure;

        assertSame(failure, assertThrows(AssertionError.class, () ->
                coordinator.runLogicalIteration(() -> { }, frame -> {
                    frame.claim(PlcLifecyclePhase.NORMAL_PAUSE);
                    throw failure;
                })));
        assertEquals(0, failure.getSuppressed().length);
        service.validationFailure = null;
        finishPause(coordinator);
    }

    private static void assertMissingPreparation(Throwable failure, PlcLifecyclePhase phase) {
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(IllegalStateException.class, failure.getSuppressed()[0]);
        assertEquals("missing PLC preparation for " + phase, failure.getSuppressed()[0].getMessage());
    }

    private static void assertTerminal(PlcLifecycleFrame frame) {
        assertNotNull(frame);
        assertThrows(IllegalStateException.class, frame::finish);
        assertThrows(IllegalStateException.class, () -> frame.claim(PlcLifecyclePhase.LAG));
        assertThrows(IllegalStateException.class,
                () -> frame.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE));
        assertThrows(IllegalStateException.class, () -> frame.dispatchGameVBlank(() -> fail("closed token")));
    }

    private static void assertFreshTokenStillOwned(
            PlcFrameLifecycleCoordinator coordinator, PlcLifecycleFrame frame) {
        assertNotNull(frame);
        assertThrows(IllegalStateException.class, coordinator::latchBeforeFadeUpdate,
                "cleanup must not detach the callback's later open token");
        assertTrue(frame.isOwnedBy(PlcLifecyclePhase.LAG));
        assertTrue(frame.hasExplicitDmaQueueService());
        assertTrue(frame.defersLoopTailPreparation());
        assertTrue(frame.dispatchGameVBlank(() -> { }));
    }

    private static void markOneShotDeclarations(PlcFrameLifecycleCoordinator coordinator) {
        coordinator.markRepresentedIterationWithoutVblank();
        coordinator.markNextVblankServicesDmaQueue();
        coordinator.markRepresentedIterationDefersLoopTailPreparation();
    }

    private static void assertNextLagHasNoDeclarations(
            PlcFrameLifecycleCoordinator coordinator, DynamicArtLifecycleService dynamicArt) {
        coordinator.runSuppressedLagIteration(frame -> {
            assertFalse(frame.defersLoopTailPreparation());
            assertTrue(frame.claim(PlcLifecyclePhase.LAG));
            assertFalse(frame.hasExplicitDmaQueueService());
            assertFalse(frame.consumedHeldLoopTailPreparation());
            return null;
        });
        verify(dynamicArt, never()).serviceProductionVBlank();
        verify(dynamicArt, times(1)).finishProductionIteration(true);
        finishPause(coordinator);
        verify(dynamicArt, times(1)).finishProductionIteration(false);
    }

    private static void finishPause(PlcFrameLifecycleCoordinator coordinator) {
        coordinator.runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(PlcLifecyclePhase.NORMAL_PAUSE));
            assertFalse(frame.defersLoopTailPreparation());
            assertFalse(frame.hasExplicitDmaQueueService());
            return null;
        });
    }

    private static void finishDeclaredPause(PlcFrameLifecycleCoordinator coordinator) {
        coordinator.runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(PlcLifecyclePhase.NORMAL_PAUSE));
            assertTrue(frame.hasExplicitDmaQueueService());
            return null;
        });
    }

    private static void finishRetainedFade(PlcFrameLifecycleCoordinator coordinator) {
        coordinator.runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE));
            assertFalse(frame.defersLoopTailPreparation());
            assertFalse(frame.hasExplicitDmaQueueService());
            return null;
        });
    }

    private static DynamicArtLifecycleService activeDynamicArt() {
        var dynamicArt = mock(DynamicArtLifecycleService.class);
        when(dynamicArt.isRunActive()).thenReturn(true);
        return dynamicArt;
    }

    private static final class RecordingService implements PlcLifecycleService {
        final List<String> events = new ArrayList<>();
        Runnable onService;
        RuntimeException serviceFailure;
        RuntimeException preparationFailure;
        Error validationFailure;

        @Override public void serviceVBlank(PlcLifecyclePhase phase) {
            events.add("service:" + phase);
            if (onService != null) onService.run();
            if (serviceFailure != null) throw serviceFailure;
        }
        @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
            if (validationFailure != null) throw validationFailure;
            return phase == PlcLifecyclePhase.PALETTE_FADE || phase == PlcLifecyclePhase.ORDINARY_LEVEL;
        }
        @Override public void prepareAfterLoop(PlcLifecyclePhase phase) {
            events.add("prepare:" + phase);
            if (preparationFailure != null) throw preparationFailure;
        }
    }
}
