package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzZoneRuntimeState {

    @Test
    void act2UsesNormalBackgroundWindowUntilItsEventOwnsAnExactRetainedPayload() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        assertFalse(state.usesPersistentBackgroundVdpPlane());
        assertEquals(0, events.captureRetainedPlaneSnapshot().length);

        byte[] retained = new byte[64 * 32 * 4];
        retained[17] = 0x5A;
        events.restoreRetainedPlaneSnapshot(retained);
        byte[] captured = state.captureBytes();
        events.restoreRetainedPlaneSnapshot(new byte[0]);
        state.restoreBytes(captured);

        assertArrayEquals(retained, events.captureRetainedPlaneSnapshot());
    }

    @Test
    void exactRetainedPlanePayloadRoundTripsDefensively() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        byte[] retained = new byte[64 * 32 * 4];
        for (int i = 0; i < retained.length; i++) retained[i] = (byte) (i * 31);
        events.restoreRetainedPlaneSnapshot(retained);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE, events);
        byte[] snapshot = state.captureBytes();

        retained[0] ^= 0x7F;
        events.restoreRetainedPlaneSnapshot(new byte[0]);
        state.restoreBytes(snapshot);

        byte[] restored = events.captureRetainedPlaneSnapshot();
        assertEquals(64 * 32 * 4, restored.length);
        assertEquals((byte) 0, restored[0]);
        assertEquals((byte) 31, restored[1]);
        restored[1] = 0;
        assertEquals((byte) 31, events.captureRetainedPlaneSnapshot()[1]);
    }

    @Test
    void captureRestoreCaptureRoundTripsEveryAuthoritativeField() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        events.setForegroundLayoutRegion(4);
        events.setForegroundOutdoor(true);
        events.setBackgroundOutdoor(true);
        events.setBackgroundRedraw(12, Sonic3kFBZEvents.RedrawDirection.LEFT_TO_RIGHT);
        events.setOutdoorBobOffset(-7);
        assertEquals(0, events.sampleOutdoorHScrollAccumulator(0x1234));
        events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 0x5A);
        events.setAct2ForegroundStage(8);
        events.setBossBackgroundState(16, 0x1234, -0x234);
        events.setBossLoadPositionAdjustmentPending(true);
        events.setCloudRewindId(0, ObjectRefId.dynamic(3, 4, 5));
        events.setCloudRewindId(9, ObjectRefId.child(7, 8, 9, 10));
        events.setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED);
        events.setCollisionMode(Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, 0x20, -0x10);
        events.restoreScreenShakePipelineState(true, -3, 5, 0x22, 0x404);
        events.setEventsFg5(true);
        events.setS1DonationSqueezeAssistState(
                FbzZoneRuntimeState.S1DonationSqueezeAssistState.CONSUMED);

        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.KNUCKLES, events);
        byte[] expected = state.captureBytes();

        events.init(1);
        state.restoreBytes(expected);

        assertArrayEquals(expected, state.captureBytes());
        assertEquals(4, state.foregroundLayoutRegion());
        assertTrue(state.foregroundOutdoor());
        assertTrue(state.backgroundOutdoor());
        assertEquals(Sonic3kFBZEvents.RedrawDirection.LEFT_TO_RIGHT, state.backgroundRedrawDirection());
        assertEquals(-7, state.outdoorBobOffset());
        assertEquals(0xE00, state.hScrollAccumulator());
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, state.magneticPolarity());
        assertEquals(0x5A, state.magneticTimerPhase());
        assertEquals(8, state.act2ForegroundStage());
        assertEquals(16, state.bossBackgroundStage());
        assertEquals(0x1234, state.bossBackgroundOffsetX());
        assertEquals(-0x234, state.bossBackgroundOffsetY());
        assertTrue(state.bossLoadPositionAdjustmentPending());
        assertEquals(ObjectRefId.dynamic(3, 4, 5), state.cloudRewindId(0));
        assertEquals(ObjectRefId.child(7, 8, 9, 10), state.cloudRewindId(9));
        assertFalse(state.cloudCleanupTerminal());
        assertEquals(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED, state.planeAssignmentMode());
        assertEquals(Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, state.collisionMode());
        assertEquals(new BackgroundPlaneCollisionProvider.State(true, 0x20, -0x10),
                state.backgroundPlaneCollisionStateOrNull());
        assertTrue(state.screenShakeActive());
        assertEquals(-3, state.screenShakeOffset());
        assertEquals(5, state.screenShakeLastOffset());
        assertEquals(0x22, state.screenShakePhase());
        assertEquals(0x404, state.bossForegroundVScroll());
        assertTrue(state.isActTransitionFlagActive());
        assertEquals(FbzZoneRuntimeState.S1DonationSqueezeAssistState.CONSUMED,
                state.s1DonationSqueezeAssistState());
    }

    @Test
    void accumulatorAndMagneticEdgeRestoreRemainIdempotentAtTheCapturedFrame() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);

        assertEquals(0, events.sampleOutdoorHScrollAccumulator(0x40));
        events.advanceMagneticPhase(0x100);
        byte[] captured = state.captureBytes();

        assertEquals(0xE00 >> 3, events.sampleBossHScrollAccumulator(0x41));
        events.advanceMagneticPhase(0x200);
        state.restoreBytes(captured);

        assertEquals(0, events.sampleOutdoorHScrollAccumulator(0x40),
                "recomputing a captured frame must return its original read-old sample");
        assertEquals(0xE00, state.hScrollAccumulator(), "duplicate recompute must not advance twice");
        events.advanceMagneticPhase(0x100);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, state.magneticPolarity(),
                "restored edge metadata must suppress a duplicate toggle");

        assertEquals(0xE00 >> 3, events.sampleBossHScrollAccumulator(0x41));
        assertEquals(0x8E00, state.hScrollAccumulator());
        events.advanceMagneticPhase(0x200);
        assertEquals(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, state.magneticPolarity());
    }

    @Test
    void setupLatchAndControllerCollisionIntentRoundTripAsAuthoritativeEventWords() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        events.setAct2ForegroundStage(4);
        events.setBossApproachMotionState(0x120, 0x230, true);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events);
        byte[] captured = state.captureBytes();

        events.init(1);
        state.restoreBytes(captured);

        assertTrue(events.isBossEventSetupAttempted());
        assertTrue(events.isBossCollisionIntentActive());
        assertArrayEquals(captured, state.captureBytes());
    }

    @Test
    void donationLowerLoopAssistStateResetsAndRoundTripsThroughTheTypedRuntimeOwner() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events);
        assertEquals(FbzZoneRuntimeState.S1DonationLowerLoopAssistState.ARMED,
                state.s1DonationLowerLoopAssistState());

        state.setS1DonationLowerLoopAssistState(
                FbzZoneRuntimeState.S1DonationLowerLoopAssistState.CONSUMED);
        byte[] consumed = state.captureBytes();
        events.init(1);
        assertEquals(FbzZoneRuntimeState.S1DonationLowerLoopAssistState.ARMED,
                state.s1DonationLowerLoopAssistState(),
                "fresh FBZ runtime initialization must rearm the compatibility impulse");

        state.restoreBytes(consumed);
        assertEquals(FbzZoneRuntimeState.S1DonationLowerLoopAssistState.CONSUMED,
                state.s1DonationLowerLoopAssistState());
        assertArrayEquals(consumed, state.captureBytes());
    }

    @Test
    void donationUpperLoopAssistStateIsIndependentAndRoundTripsThroughTheTypedRuntimeOwner() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, events);
        assertEquals(FbzZoneRuntimeState.S1DonationLowerLoopAssistState.ARMED,
                state.s1DonationLowerLoopAssistState());
        assertEquals(FbzZoneRuntimeState.S1DonationUpperLoopAssistState.ARMED,
                state.s1DonationUpperLoopAssistState());

        state.setS1DonationUpperLoopAssistState(
                FbzZoneRuntimeState.S1DonationUpperLoopAssistState.CONSUMED);
        byte[] consumed = state.captureBytes();
        assertEquals(FbzZoneRuntimeState.S1DonationLowerLoopAssistState.ARMED,
                state.s1DonationLowerLoopAssistState(),
                "upper-loop consumption must not consume the later lower-loop assist");

        events.init(1);
        assertEquals(FbzZoneRuntimeState.S1DonationUpperLoopAssistState.ARMED,
                state.s1DonationUpperLoopAssistState());
        state.restoreBytes(consumed);
        assertEquals(FbzZoneRuntimeState.S1DonationUpperLoopAssistState.CONSUMED,
                state.s1DonationUpperLoopAssistState());
        assertEquals(FbzZoneRuntimeState.S1DonationLowerLoopAssistState.ARMED,
                state.s1DonationLowerLoopAssistState());
        assertArrayEquals(consumed, state.captureBytes());
    }

    @Test
    void invalidStagesAndModesAreRejectedWithoutPartiallyMutatingHandler() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        byte[] before = state.captureBytes();

        assertThrows(IllegalArgumentException.class, () -> events.setForegroundLayoutRegion(8));
        assertThrows(IllegalArgumentException.class, () -> events.setAct2ForegroundStage(6));
        assertThrows(IllegalArgumentException.class, () -> events.setBossBackgroundState(20, 0, 0));

        events.setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED);
        events.setCollisionMode(Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, 0, 0);
        byte[] changedPlane = state.captureBytes();
        int planeOrdinalOffset = firstChangedIntOffset(before, changedPlane);
        byte[] corruptMode = before.clone();
        putInt(corruptMode, planeOrdinalOffset, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> state.restoreBytes(corruptMode));
        events.setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode.NORMAL);
        events.setCollisionMode(Sonic3kFBZEvents.CollisionMode.FOREGROUND_ONLY, 0, 0);
        assertArrayEquals(before, state.captureBytes(), "malformed restore must be transactional");
    }

    @Test
    void cloudIdentityTableHasExactlyTenStableNullableSlots() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.TAILS_ALONE, events);

        assertEquals(10, state.cloudRewindIds().size());
        assertThrows(IndexOutOfBoundsException.class, () -> events.setCloudRewindId(10, null));
        assertThrows(UnsupportedOperationException.class,
                () -> state.cloudRewindIds().set(0, ObjectRefId.dynamic(0, 0, 0)));
    }

    @Test
    void dynamicResizeIsNotAnFbzAuthority() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        byte[] before = state.captureBytes();

        events.setDynamicResizeRoutine(12);

        assertEquals(0, events.getDynamicResizeRoutine());
        assertEquals(0, state.getDynamicResizeRoutine());
        assertArrayEquals(before, state.captureBytes());
    }

    @Test
    void act2OnlyWritesAndImpossiblePlaneCombinationsAreRejected() {
        Sonic3kFBZEvents act1 = new Sonic3kFBZEvents();
        act1.init(0);
        assertThrows(IllegalArgumentException.class, () -> act1.setBossBackgroundOffsets(1, 2));
        assertThrows(IllegalArgumentException.class, () -> act1.setBossLoadPositionAdjustmentPending(true));
        assertThrows(IllegalArgumentException.class,
                () -> act1.setCloudRewindId(0, ObjectRefId.dynamic(1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> act1.setCloudCleanupTerminal(true));
        assertThrows(IllegalArgumentException.class, () -> act1.setScreenShakeState(true, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> act1.setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED));
        assertThrows(IllegalArgumentException.class,
                () -> act1.setCollisionMode(Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, 0, 0));

        Sonic3kFBZEvents act2 = new Sonic3kFBZEvents();
        act2.init(1);
        act2.setCollisionMode(Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, 3, 4);
        assertEquals(Sonic3kFBZEvents.PlaneAssignmentMode.NORMAL, act2.getPlaneAssignmentMode());
        act2.setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED);
        assertEquals(Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, act2.getCollisionMode());
        act2.setCollisionMode(Sonic3kFBZEvents.CollisionMode.FOREGROUND_ONLY, 0, 0);
        assertEquals(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED, act2.getPlaneAssignmentMode());
        assertThrows(IllegalArgumentException.class,
                () -> act2.setCollisionMode(Sonic3kFBZEvents.CollisionMode.FOREGROUND_ONLY, 1, 0));
    }

    @Test
    void terminalSnapshotClearsIdsAndRejectsPresentCloudPayloads() {
        Sonic3kFBZEvents clean = new Sonic3kFBZEvents();
        clean.init(1);
        FbzZoneRuntimeState cleanState = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, clean);
        byte[] nonTerminal = cleanState.captureBytes();
        clean.setCloudCleanupTerminal(true);
        byte[] terminal = cleanState.captureBytes();
        int terminalOffset = firstChangedByte(nonTerminal, terminal);
        assertTrue(clean.getCloudRewindIds().stream().allMatch(java.util.Objects::isNull));

        Sonic3kFBZEvents withId = new Sonic3kFBZEvents();
        withId.init(1);
        withId.setCloudRewindId(0, ObjectRefId.dynamic(1, 2, 3));
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, withId);
        byte[] before = state.captureBytes();
        byte[] malformed = before.clone();
        malformed[terminalOffset + (5 * Integer.BYTES)] = 1;
        assertThrows(IllegalArgumentException.class, () -> state.restoreBytes(malformed));
        assertArrayEquals(before, state.captureBytes());
    }

    @Test
    void rewindCanRestorePreterminalCloudIdsOverTerminalHandlerState() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        ObjectRefId stable = ObjectRefId.dynamic(7, 8, 9);
        events.setCloudRewindId(4, stable);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        byte[] preterminal = state.captureBytes();
        events.setCloudCleanupTerminal(true);

        state.restoreBytes(preterminal);

        assertFalse(state.cloudCleanupTerminal());
        assertEquals(stable, state.cloudRewindId(4));
    }

    @Test
    void pendulumRespawnOrientationBitsRoundTripWithoutAliasingPlacements() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(1);
        FbzZoneRuntimeState state = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
        state.setPendulumOrientationBit(137, true);
        byte[] captured = state.captureBytes();
        state.setPendulumOrientationBit(137, false);
        state.setPendulumOrientationBit(138, true);

        state.restoreBytes(captured);

        assertTrue(state.pendulumOrientationBit(137));
        assertFalse(state.pendulumOrientationBit(138));
    }

    private static int firstChangedByte(byte[] before, byte[] after) {
        for (int i = 0; i < before.length; i++) if (before[i] != after[i]) return i;
        fail("probe must change a byte");
        return -1;
    }

    private static int firstChangedIntOffset(byte[] before, byte[] after) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) {
                // Enum ordinals are big-endian ints and this 0 -> 1 probe changes
                // their final byte first; rewind fields are intentionally packed.
                return i - (Integer.BYTES - 1);
            }
        }
        fail("enum probe must change the snapshot");
        return -1;
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
