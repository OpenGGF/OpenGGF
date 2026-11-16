package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class TestLbzLaunchRuntimeState {

    @Test
    void timedShakeCountdownPreparedAndAppliedOffsetsRoundTrip() {
        LbzZoneRuntimeState source = new LbzZoneRuntimeState(
                1, PlayerCharacter.KNUCKLES);
        source.startTimedScreenShake(20);
        source.publishScreenShakeForeground();
        source.setupScreenShake(0, false);
        source.publishScreenShakeForeground();

        LbzZoneRuntimeState restored = new LbzZoneRuntimeState(
                1, PlayerCharacter.KNUCKLES);
        restored.restoreBytes(source.captureBytes());

        assertEquals(19, restored.getTimedShakeCountdown());
        assertEquals(-5, restored.getTimedShakePreparedOffset());
        assertEquals(-5, restored.getTimedShakeAppliedOffset());
        assertEquals(0x05FB, restored.getCameraYCopy(0x0600));
    }

    @Test
    void seamlessActReloadRetainsLevelLoopOscillationTail() {
        LbzZoneRuntimeState state =
                new LbzZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);

        assertTrue(state.advancesOscillationOnSeamlessTransition(),
                "LBZ1BGE_DoTransition returns to LevelLoop's OscillateNumDo tail");
    }

    @Test void rejectsLegacyAndTruncatedStateWithoutMutation() {
        var state = new LbzZoneRuntimeState(1, PlayerCharacter.KNUCKLES);
        state.setLaunchActive(true);
        byte[] before = state.captureBytes();
        for (byte[] bytes : new byte[][] {new byte[0], new byte[3], new byte[12], java.util.Arrays.copyOf(before, before.length - 1)}) {
            assertThrows(IllegalArgumentException.class, () -> state.restoreBytes(bytes));
            assertArrayEquals(before, state.captureBytes());
        }
    }

    @Test void freshEventOwnerRestoresConstructorIdentityAndNumericPublication() {
        var source = new com.openggf.game.sonic3k.events.Sonic3kLBZEvents();
        var state = new LbzZoneRuntimeState(1, PlayerCharacter.KNUCKLES, source);
        state.setFgAccum(0x12345678);
        state.writeScreenShakeFlag(20);
        byte[] before = source.captureLbzState();
        var target = new com.openggf.game.sonic3k.events.Sonic3kLBZEvents();
        target.restoreLbzState(before);
        assertArrayEquals(before, target.captureLbzState());
        assertEquals(0x12345678, target.runtimePublication(1, PlayerCharacter.KNUCKLES).getFgAccum());
        assertArrayEquals(before, target.captureLbzState(), "rebinding preserves the restored numeric owner");
    }

    @Test void malformedConstructorIdentityIsRejectedWithoutMutation() {
        var state = new LbzZoneRuntimeState(1, PlayerCharacter.KNUCKLES);
        byte[] before = state.captureNumericPublication();
        byte[] bad = before.clone();
        ByteBuffer.wrap(bad).putInt(4, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> state.restoreRuntimePublication(bad));
        assertArrayEquals(before, state.captureNumericPublication());
    }

    @Test void malformedNativeRuntimeWordIsRejectedBeforeMutation() {
        var good = new LbzZoneRuntimeState(1, PlayerCharacter.KNUCKLES);
        byte[] before = good.captureBytes();
        var bad = new LbzZoneRuntimeState(1, PlayerCharacter.KNUCKLES);
        bad.setFgLaunchSpeed(0x10000);
        assertThrows(IllegalArgumentException.class, () -> good.restoreBytes(bad.captureBytes()));
        assertArrayEquals(before, good.captureBytes());
    }

    @Test
    void launchFieldsRoundTripThroughRewindBytes() {
        LbzZoneRuntimeState original = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_AND_TAILS);
        original.setLbz1KnucklesCutsceneControlLocked(true);
        original.setLaunchActive(true);
        original.setDeathEggRumble(true);
        original.setFgLaunchSpeed(0x1E00);
        original.setBgLaunchSpeed(-0x18000);
        original.setFgAccum(0x0012_3456);
        original.setBgAccum(0x00AB_CDEF);
        original.setLaunchYDelta(-3);
        original.setPreLaunchDelay(0x3C);
        original.setDetachScroll(0x27);
        original.setDetachWindowCopyTimer(0x1B);
        original.setWaterTargetYForTest(0x0F80);
        original.setPadCollapseActive(true);
        original.setFinalFallActive(true);
        original.setDeathEggTerrainSwapQueued(true);
        original.setDeathEggTerrainSwapApplied(true);
        original.setDeathEggDeformWrapLatch(0x0200);
        original.publishLbz2DeformOutputs(0xFFFC, 0x1234, 0x1200);
        original.setLbz2RideAnimatedTileGateActive(true);
        original.requestLaunchStart();
        original.requestPadCollapseStart();
        original.requestFinalFall();
        original.registerLaunchRiderAnchor(0x1234_5678);
        original.registerFinaleCutsceneAnchor(0x3344_5566);

        LbzZoneRuntimeState restored = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_AND_TAILS);
        restored.restoreBytes(original.captureBytes());

        assertTrue(restored.isLbz1KnucklesCutsceneControlLocked(),
                "LBZ1's _unkFAA9 cutscene input gate must rewind with the zone runtime state.");
        assertTrue(restored.isLaunchActive());
        assertTrue(restored.isDeathEggRumble());
        assertEquals(0x1E00, restored.getFgLaunchSpeed());
        assertEquals(-0x18000, restored.getBgLaunchSpeed());
        assertEquals(0x0012_3456, restored.getFgAccum());
        assertEquals(0x00AB_CDEF, restored.getBgAccum());
        assertEquals(-3, restored.getLaunchYDelta());
        assertEquals(0x3C, restored.getPreLaunchDelay());
        assertEquals(0x27, restored.getDetachScroll());
        assertEquals(0x1B, restored.getDetachWindowCopyTimer());
        assertEquals(0x0F80, restored.getWaterTargetY());
        assertTrue(restored.isPadCollapseActive());
        assertTrue(restored.isFinalFallActive());
        assertTrue(restored.isDeathEggTerrainSwapQueued());
        assertTrue(restored.isDeathEggTerrainSwapApplied());
        assertEquals(0x0200, restored.getDeathEggDeformWrapLatch());
        assertEquals(0xFFFC, restored.lbz2WaterlinePhase());
        assertEquals(0x1234, restored.lbz2ScrollArtPhaseSource());
        assertEquals(0x1200, restored.publishedBgCameraX());
        assertEquals(0x04, restored.lbz2ScrollArtPhase());
        assertTrue(restored.isLbz2RideAnimatedTileGateActive());
        assertEquals(OptionalInt.of(0x1234_5678), restored.getLaunchRiderAnchorId());
        assertEquals(OptionalInt.of(0x3344_5566), restored.getFinaleCutsceneAnchorId());
        assertTrue(restored.consumeLaunchStartRequested());
        assertTrue(restored.consumePadCollapseStartRequested());
        assertTrue(restored.consumeFinalFallRequested());
    }

    @Test
    void launchSignalConsumersAreOneShot() {
        LbzZoneRuntimeState state = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);

        assertFalse(state.consumeLaunchStartRequested());
        assertFalse(state.consumePadCollapseStartRequested());
        assertFalse(state.consumeFinalFallRequested());

        state.requestLaunchStart();
        state.requestPadCollapseStart();
        state.requestFinalFall();

        assertTrue(state.consumeLaunchStartRequested());
        assertFalse(state.consumeLaunchStartRequested());
        assertTrue(state.consumePadCollapseStartRequested());
        assertFalse(state.consumePadCollapseStartRequested());
        assertTrue(state.consumeFinalFallRequested());
        assertFalse(state.consumeFinalFallRequested());
    }

    @Test
    void lbz2RideAnimatedTileGatePersistsUntilExplicitlyCleared() {
        LbzZoneRuntimeState state = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);

        assertFalse(state.isLbz2RideAnimatedTileGateActive());

        state.setLbz2RideAnimatedTileGateActive(true);

        assertTrue(state.isLbz2RideAnimatedTileGateActive());
        assertTrue(state.isLbz2RideAnimatedTileGateActive(),
                "ROM Anim_Counters+$F is a latched flag, not a one-frame consumer");

        state.setLbz2RideAnimatedTileGateActive(false);

        assertFalse(state.isLbz2RideAnimatedTileGateActive());
    }

    @Test
    void launchRiderAnchorCanBeClearedWithoutObjectReferences() {
        LbzZoneRuntimeState state = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);

        assertTrue(state.getLaunchRiderAnchorId().isEmpty());
        state.registerLaunchRiderAnchor(42);
        assertEquals(OptionalInt.of(42), state.getLaunchRiderAnchorId());

        state.clearLaunchRiderAnchor();

        assertTrue(state.getLaunchRiderAnchorId().isEmpty());
    }

    @Test
    void finaleCutsceneAnchorCanBeClearedWithoutObjectReferences() {
        LbzZoneRuntimeState state = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);

        assertTrue(state.getFinaleCutsceneAnchorId().isEmpty());
        state.registerFinaleCutsceneAnchor(84);
        assertEquals(OptionalInt.of(84), state.getFinaleCutsceneAnchorId());

        state.clearFinaleCutsceneAnchor();

        assertTrue(state.getFinaleCutsceneAnchorId().isEmpty());
    }

    private static void dirtyLaunchState(LbzZoneRuntimeState state) {
        state.setLaunchActive(true);
        state.setDeathEggRumble(true);
        state.setFgLaunchSpeed(0x1E00);
        state.setBgLaunchSpeed(-0x18000);
        state.setFgAccum(0x0012_3456);
        state.setBgAccum(0x00AB_CDEF);
        state.setLaunchYDelta(-3);
        state.setPreLaunchDelay(0x3C);
        state.setDetachScroll(0x27);
        state.setDetachWindowCopyTimer(0x1B);
        state.setWaterTargetYForTest(0x0F80);
        state.setPadCollapseActive(true);
        state.setFinalFallActive(true);
        state.setDeathEggTerrainSwapQueued(true);
        state.setDeathEggTerrainSwapApplied(true);
        state.setDeathEggDeformWrapLatch(0x0200);
        state.publishLbz2DeformOutputs(0xFFFC, 0x1234, 0x1200);
        state.setLbz2RideAnimatedTileGateActive(true);
        state.requestLaunchStart();
        state.requestPadCollapseStart();
        state.requestFinalFall();
        state.registerLaunchRiderAnchor(0x1234_5678);
        state.registerFinaleCutsceneAnchor(0x3344_5566);
    }

    private static void assertLaunchStateCleared(LbzZoneRuntimeState state) {
        assertFalse(state.isLaunchActive());
        assertFalse(state.isDeathEggRumble());
        assertEquals(0, state.getFgLaunchSpeed());
        assertEquals(0, state.getBgLaunchSpeed());
        assertEquals(0, state.getFgAccum());
        assertEquals(0, state.getBgAccum());
        assertEquals(0, state.getLaunchYDelta());
        assertEquals(0, state.getPreLaunchDelay());
        assertEquals(0, state.getDetachScroll());
        assertEquals(0, state.getDetachWindowCopyTimer());
        assertEquals(0, state.getWaterTargetY());
        assertFalse(state.isPadCollapseActive());
        assertFalse(state.isFinalFallActive());
        assertFalse(state.isDeathEggTerrainSwapQueued());
        assertFalse(state.isDeathEggTerrainSwapApplied());
        assertEquals(0, state.getDeathEggDeformWrapLatch());
        assertEquals(0, state.lbz2WaterlinePhase());
        assertEquals(0, state.lbz2ScrollArtPhaseSource());
        assertEquals(0, state.publishedBgCameraX());
        assertFalse(state.isLbz2RideAnimatedTileGateActive());
        assertFalse(state.consumeLaunchStartRequested());
        assertFalse(state.consumePadCollapseStartRequested());
        assertFalse(state.consumeFinalFallRequested());
        assertTrue(state.getLaunchRiderAnchorId().isEmpty());
        assertTrue(state.getFinaleCutsceneAnchorId().isEmpty());
    }
}
