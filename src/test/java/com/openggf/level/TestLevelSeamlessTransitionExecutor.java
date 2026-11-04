package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.OscillationManager;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TestLevelSeamlessTransitionExecutor {
    private LevelManager levelManager;
    private LevelTransitionCoordinator transitions;
    private LevelSeamlessTransitionExecutor executor;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        levelManager = mock(LevelManager.class);
        transitions = mock(LevelTransitionCoordinator.class);
        executor = new LevelSeamlessTransitionExecutor(levelManager, transitions);
    }

    @Test
    void nativeWaitGateSurvivesNormalizationAndCarriedOwnerPolicy() {
        var source = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_SAME_LEVEL)
                .showInLevelTitleCard(false)
                .resetLevelGamestateAtInLevelTitleCardDisplay(true)
                .inLevelTitleCardResetAdditionalDispatches(com.openggf.game.TitleCardResetGates.NATIVE_WAIT_GATE)
                .build();
        var normalized = source.retargetedForReload(1, 1);
        assertEquals(com.openggf.game.TitleCardResetGates.NATIVE_WAIT_GATE,
                normalized.inLevelTitleCardResetAdditionalDispatches(),
                "a native child-movement gate is not a zero-dispatch countdown");
        assertEquals(com.openggf.game.TitleCardResetGates.NATIVE_WAIT_GATE,
                CarriedTitlePublicationTiming.from(normalized).resetAdditionalDispatches(),
                "the retained owner must receive the same semantic gate");
    }

    @Test
    void nullRequestHasNoTransitionSideEffects() {
        executor.execute(null);

        verifyNoInteractions(levelManager, transitions);
    }

    @Test
    void mutateOnlyRunsMutationWithoutReloadFrameBridgeAndAlwaysClearsTransitionState()
            throws Exception {
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.MUTATE_ONLY)
                .mutationKey("fire-overlay")
                .build();

        executor.execute(request);

        verify(transitions).setSpecialStageReturnLevelReloadRequested(false);
        verify(levelManager).applySeamlessMutation("fire-overlay");
        verify(levelManager, never()).executeActTransition(any());
        verify(levelManager, never()).advanceGlobalOscillationAtLevelLoopTail();
        verify(transitions).setLevelInactiveForTransition(false);
    }

    @Test
    void reloadSameDispatchesNormalizedRequestAndRunsReloadFrameBridge() throws Exception {
        levelManager.currentZone = 5;
        levelManager.currentAct = 1;
        levelManager.objectManager = mock(ObjectManager.class);
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_SAME_LEVEL)
                .playerOffset(9, -12)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.RESOURCE_HANDOFF_OWNER)
                .resourceHandoff(new SeamlessTransitionResourceHandoffId(73))
                .preserveEndOfLevelActive(true)
                .inLevelTitleCardPreloadedActCameraReleaseDispatches(8)
                .objectSurvivalPolicy(SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.ALL_LIVE_SST)
                .build();

        executor.execute(request);

        ArgumentCaptor<SeamlessLevelTransitionRequest> capture =
                ArgumentCaptor.forClass(SeamlessLevelTransitionRequest.class);
        verify(levelManager).reloadActAtFrameBoundary(capture.capture());
        verify(levelManager, never()).executeActTransition(any());
        verify(levelManager, never()).consumeActTransitionRewindBoundaryDuringFrame();
        verify(levelManager, never()).consumeActTransitionExecutedDuringFrame();
        assertEquals(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL,
                capture.getValue().type());
        assertEquals(5, capture.getValue().targetZone());
        assertEquals(1, capture.getValue().targetAct());
        assertEquals(9, capture.getValue().playerOffsetX());
        assertEquals(-12, capture.getValue().playerOffsetY());
        assertEquals(RuntimeArtAdmissionPolicy.RESOURCE_HANDOFF_OWNER,
                capture.getValue().runtimeArtAdmissionPolicy());
        assertSame(request.resourceHandoffId(), capture.getValue().resourceHandoffId());
        assertTrue(capture.getValue().preserveEndOfLevelActive());
        assertFalse(capture.getValue().preserveEndOfLevelFlag());
        assertEquals(8, capture.getValue().inLevelTitleCardPreloadedActCameraReleaseDispatches());
        assertEquals(request.objectSurvivalPolicy(), capture.getValue().objectSurvivalPolicy());
        verify(levelManager).advanceGlobalOscillationAtLevelLoopTail();
        verify(levelManager.objectManager).advanceVblaCounter();
        verify(levelManager).markSidekickRomVisibleReloadFrameCounterBridge();
        verify(transitions).setLevelInactiveForTransition(false);
    }

    @Test
    void reloadTargetDispatchesOriginalRequestAndRunsReloadFrameBridge() throws Exception {
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(6, 0)
                .build();

        executor.execute(request);

        verify(levelManager).reloadActAtFrameBoundary(request);
        verify(levelManager, never()).consumeActTransitionRewindBoundaryDuringFrame();
        verify(levelManager, never()).consumeActTransitionExecutedDuringFrame();
        verify(levelManager).advanceGlobalOscillationAtLevelLoopTail();
        verify(transitions).setLevelInactiveForTransition(false);
    }

    @Test
    void checkedReloadFailureIsWrappedAndFinallyClearsTransitionState() throws Exception {
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(6, 0)
                .build();
        IOException failure = new IOException("broken load");
        doThrow(failure).when(levelManager).reloadActAtFrameBoundary(request);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executor.execute(request));

        assertEquals("Failed to apply seamless transition", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        verify(levelManager, never()).advanceGlobalOscillationAtLevelLoopTail();
        verify(transitions).setLevelInactiveForTransition(false);
    }

    @Test
    void runtimeMutationFailurePropagatesAndFinallyClearsTransitionState() {
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.MUTATE_ONLY)
                .mutationKey("broken")
                .build();
        IllegalStateException failure = new IllegalStateException("broken mutation");
        doThrow(failure).when(levelManager).applySeamlessMutation("broken");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> executor.execute(request));

        assertSame(failure, thrown);
        verify(transitions).setLevelInactiveForTransition(false);
    }

    @Test
    void reloadFrameBridgeAdvancesStoredLevelSpriteVintAndOscillationClocks() {
        LevelManager realLevelManager = GameServices.level();
        SpriteManager spriteManager = GameServices.sprites();
        realLevelManager.frameCounter = 0x153F;
        spriteManager.setFrameCounter(0x153F);
        ObjectManager objectManager = mock(ObjectManager.class);
        realLevelManager.objectManager = objectManager;
        OscillationManager.reset();
        OscillationManager.update(0x153E);
        int[] oscillationBeforeReload = OscillationManager.valuesForTest();

        new LevelSeamlessTransitionExecutor(
                realLevelManager, realLevelManager.getTransitions())
                .advanceFrameCounterAcrossReload();

        assertEquals(0x1540, realLevelManager.getFrameCounter());
        assertEquals(0x1540, spriteManager.getFrameCounter());
        verify(objectManager).advanceVblaCounter();
        assertTrue(realLevelManager.isSidekickRomVisibleReloadFrameCounterBridgeActive());
        assertFalse(java.util.Arrays.equals(
                oscillationBeforeReload, OscillationManager.valuesForTest()));
    }

    @Test
    void synchronousReloadKeepsClockTailAndRewindBoundaryWithTheScreenEventOwner()
            throws Exception {
        var request = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(6, 0).build();
        levelManager.currentZone = 5;
        levelManager.currentAct = 1;

        executor.executeSynchronousScreenEvent(request);

        InOrder order = inOrder(transitions, levelManager);
        order.verify(transitions).beginSynchronousScreenEventTransition(5, 1, request);
        order.verify(levelManager).executeActTransition(request);
        verify(levelManager, never()).reloadActAtFrameBoundary(any());
        order.verify(transitions).setLevelInactiveForTransition(false);
        order.verify(transitions).endSynchronousScreenEventTransition();
        verify(levelManager, never()).advanceGlobalOscillationAtLevelLoopTail();
        verify(levelManager, never()).consumeActTransitionRewindBoundaryDuringFrame();
        verify(levelManager, never()).revokeNativePlcTargetAdmission();
    }

    @Test
    void reloadTailFailureRevokesAdmissionAfterCleanupWithoutRepeatingTheLoad()
            throws Exception {
        var request = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(6, 0).build();
        var failure = new IllegalStateException("oscillator failed");
        doThrow(failure).when(levelManager).advanceGlobalOscillationAtLevelLoopTail();

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> executor.execute(request)));

        InOrder order = inOrder(levelManager, transitions);
        order.verify(levelManager).reloadActAtFrameBoundary(request);
        order.verify(levelManager).advanceGlobalOscillationAtLevelLoopTail();
        order.verify(transitions).setLevelInactiveForTransition(false);
        order.verify(levelManager).revokeNativePlcTargetAdmission();
    }

    @Test
    void cleanupFailureRevokesOnlyAnActuallySelectedReload() throws Exception {
        var failure = new IllegalStateException("cleanup failed");
        doThrow(failure).when(transitions).setLevelInactiveForTransition(false);
        var mutation = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.MUTATE_ONLY).build();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> executor.execute(mutation)));
        verify(levelManager, never()).revokeNativePlcTargetAdmission();

        var reload = SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL).build();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> executor.execute(reload, LevelSeamlessTransitionExecutor.Host.LEVEL_FRAME)));
        verify(levelManager).revokeNativePlcTargetAdmission();
    }

    @Test
    void frameBridgeClearRetainsPrimingUntilAnActualReset() {
        SpriteManager sprites = GameServices.sprites();
        levelManager.frameCounter = 12;
        sprites.setFrameCounter(13);
        assertFalse(executor.isPostReloadFrameCounterBridgeStillVisible());
        executor.markFrameCounterBridge();
        assertTrue(executor.isFrameCounterBridgeActive());
        assertTrue(executor.isPostReloadFrameCounterBridgeStillVisible());
        executor.clearFrameCounterBridge();
        assertFalse(executor.isFrameCounterBridgeActive());
        assertTrue(executor.isPostReloadFrameCounterBridgeStillVisible());
        sprites.setFrameCounter(14);
        assertFalse(executor.isPostReloadFrameCounterBridgeStillVisible());
        sprites.setFrameCounter(13);
        executor.resetFrameCounterBridge();
        assertFalse(executor.isPostReloadFrameCounterBridgeStillVisible());
    }

}
