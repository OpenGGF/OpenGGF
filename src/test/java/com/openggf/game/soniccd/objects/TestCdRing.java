package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.SavedBit.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Authored before the body. Literal source outcomes; no runtime source reads or world admission. UNRUN. */
class TestCdRing {
    @Test
    void physicalLaterChildrenRunInTheSameAscendingPassAroundOtherParents() {
        var runtime = runtime();
        seed(runtime, 32, 0xF0, 0x720, 0x21, 3);
        seed(runtime, 33, 0x1F0, 0x765, 0x22, 8);
        runtime.pool().write(runtime.pool().state(34).moveByte(ID, 0x3D));
        var ring = new CdRing(runtime, data());
        var ports = new Ports(runtime);
        for (int whole = 32; whole < 128; whole++) {
            if (runtime.pool().state(whole).id() == 0x10) { ring.executeSelected(whole, input(), ports, ports); }
        }
        assertEquals(List.of(32, 33, 35, 36, 37), ports.drawn);
        int[][] expected = {{32, 0xF0, 0x720, 3, 1}, {33, 0x1F0, 0x765, 8, 1},
                {35, 0x110, 0x720, 3, 2}, {36, 0x210, 0x765, 8, 2}, {37, 0x230, 0x765, 8, 3}};
        for (int[] row : expected) {
            var state = runtime.pool().state(row[0]);
            assertEquals(row[1], state.centreX()); assertEquals(row[2], state.centreY());
            assertEquals(row[3], state.savedFlagsId()); assertEquals(row[4], state.unsignedByte(RING_MEMBER));
            assertEquals(2, state.routine()); assertEquals(3, state.unsignedByte(MAP_FRAME));
        }
        assertEquals(0x3D, runtime.pool().state(34).id(), "Other bodies remain caller-owned");
        assertEquals(0, runtime.savedFlag(3, 1)); assertEquals(0, runtime.savedFlag(8, 1));
    }

    @Test
    void collectedFirstMemberDeletesParentOnlyAfterCreatingLaterMembers() {
        var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x22, 3);
        runtime.bsetSavedFlag(3, 0, MEMBER_1);
        var ports = new Ports(runtime);
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertArrayEquals(new byte[64], runtime.pool().state(32).copyBytes());
        assertEquals(0x110, runtime.pool().state(33).centreX());
        assertEquals(0x130, runtime.pool().state(34).centreX());
        assertEquals(2, runtime.pool().state(33).unsignedByte(RING_MEMBER));
        assertEquals(3, runtime.pool().state(34).unsignedByte(RING_MEMBER));
        assertEquals(0xF0, runtime.pool().state(34).word(RING_ANCHOR_X));
        assertTrue(ports.drawn.isEmpty()); assertEquals(1, runtime.savedFlag(3, 0));
    }

    @Test
    void collectedMiddleMemberDoesNotAllocateOrShiftTheNextMembersIdentity() {
        var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x23, 3);
        runtime.bsetSavedFlag(3, 1, MEMBER_2);
        var ports = new Ports(runtime);
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertEquals(0x130, runtime.pool().state(33).centreX());
        assertEquals(3, runtime.pool().state(33).unsignedByte(RING_MEMBER));
        assertEquals(0x150, runtime.pool().state(34).centreX());
        assertEquals(4, runtime.pool().state(34).unsignedByte(RING_MEMBER));
        assertEquals(0, runtime.pool().state(35).id());
        assertEquals(2, runtime.savedFlag(3, 1));
    }

    @Test
    void allCollectedLeavesLoadedBitSetBecauseThereWasNoAdmissionBclr() {
        var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x21, 3);
        runtime.bsetSavedFlag(3, 1, MEMBER_1); runtime.bsetSavedFlag(3, 1, MEMBER_2);
        var ports = new Ports(runtime);
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertEquals(0x83, runtime.savedFlag(3, 1));
        assertEquals(0, runtime.pool().state(32).id()); assertEquals(0, runtime.pool().state(33).id());
    }

    @Test
    void fullPoolStopsAfterExistingChildrenAndClearsLoadedBeforeFailedFindNext() {
        var runtime = runtime();
        for (int i = 32; i < 128; i++) { runtime.pool().write(runtime.pool().state(i).moveByte(ID, 1)); }
        seed(runtime, 126, 0xF0, 0x720, 0x26, 3);
        runtime.pool().objNull(127);
        var ports = new Ports(runtime);
        new CdRing(runtime, data()).executeSelected(126, input(), ports, ports);
        assertEquals(0x110, runtime.pool().state(127).centreX());
        assertEquals(2, runtime.pool().state(127).unsignedByte(RING_MEMBER));
        assertEquals(0, runtime.savedFlag(3, 1)); assertEquals(List.of(126), ports.drawn);
        var last = runtime(); seed(last, 127, 0xF0, 0x720, 0x21, 3);
        var lastPorts = new Ports(last);
        new CdRing(last, data()).executeSelected(127, input(), lastPorts, lastPorts);
        assertEquals(0, last.pool().state(32).id(), "No wrap to earlier slots");
        assertEquals(2, last.pool().state(127).routine());
    }

    @Test
    void collectedParentWithNoLaterFreeSlotStillDeletesAndDoesNotRetryOrRollBackBankClear() {
        var runtime = runtime(); seed(runtime, 127, 0xF0, 0x720, 0x21, 3);
        runtime.bsetSavedFlag(3, 1, MEMBER_1);
        var before = runtime.capture(); var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        ring.executeSelected(127, input(), ports, ports);
        assertEquals(1, runtime.savedFlag(3, 1), "Uncollected child clears loaded BEFORE allocator failure");
        assertArrayEquals(new byte[64], runtime.pool().state(127).copyBytes());
        assertEquals(0, runtime.pool().state(32).id()); assertTrue(ports.events.isEmpty());
        runtime.restore(before);
        assertEquals(0x81, runtime.savedFlag(3, 1)); assertEquals(0x10, runtime.pool().state(127).id());
        ring.executeSelected(127, input(), ports, ports);
        assertEquals(1, runtime.savedFlag(3, 1)); assertEquals(0, runtime.pool().state(127).id());
    }

    @Test
    void lowSevenClampsToSevenMembersAndSignedDeltasWrapWordPositionsOnly() {
        var runtime = runtime(); seed(runtime, 32, 0x10, 0xFFF8, 0x97, 3);
        runtime.pool().write(runtime.pool().state(33).moveWord(X_FRACTION, 0xABCD).moveWord(Y_FRACTION, 0xFEDC));
        var ports = new Ports(runtime);
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertEquals(7, runtime.pool().capture().slots().stream().filter(s -> s.id() == 0x10).count());
        assertEquals(0, runtime.pool().state(33).centreX()); assertEquals(8, runtime.pool().state(33).centreY());
        assertEquals(0xFFB0, runtime.pool().state(38).centreX());
        assertEquals(7, runtime.pool().state(38).unsignedByte(RING_MEMBER));
        assertEquals(0xABCD, runtime.pool().state(33).word(X_FRACTION));
        assertEquals(0xFEDC, runtime.pool().state(33).word(Y_FRACTION));
    }

    @Test
    void currentIdZeroIsNotAnExtraBodyGateAndNoInitOrAllocationCleansDirtyBytes() {
        var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x21, 3);
        var parent = dirty(runtime.pool().state(32)).moveByte(ID, 0).moveByte(ROUTINE, 0);
        var child = dirty(runtime.pool().state(33)).moveByte(ID, 0);
        runtime.pool().write(parent); runtime.pool().write(child);
        var ports = new Ports(runtime);
        ports.value = new CdRingGlobals(0xCAFE, 0xFE, 0xD6, 0xA0, 0xB4, 0, 0x81, 3);
        var carriedGlobals = ports.value;
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertEquals(initialized(parent, 0xF0, 0x720, 0xF0, 3, 1).moveByte(MAP_FRAME, 3)
                .moveByte(SPRITE_FLAGS, 4), runtime.pool().state(32));
        assertEquals(initialized(child, 0x110, 0x720, 0xF0, 3, 2), runtime.pool().state(33));
        assertEquals(0xD3, runtime.pool().state(33).unsignedByte(ANIMATION_TIME));
        assertEquals(0xB2, runtime.pool().state(33).unsignedByte(ANIMATION_FRAME));
        assertEquals(0xFFFF, runtime.pool().state(33).word(X_FRACTION));
        assertEquals(carriedGlobals, ports.value, "Init does not reset rewards, update bits or clocks");
        assertEquals(List.of("draw:32"), ports.events);
    }

    @Test
    void warpSelectionPreservesNativeD2PositionClobberAndUsesClampedOldPeriod() {
        for (int[] row : new int[][]{{0x81, 1, 0, 0xFFFF}, {0x81, 0xFF, 2, 1},
                {0x80, 1, 0, 0xFFFF}, {0x82, 0xFF, 2, 1}, {0x81, 0x80, 2, 128}}) {
            var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x21, 3);
            for (int p = 0; p < 3; p++) { runtime.bsetSavedFlag(3, p, LOADED); }
            var ports = new Ports(runtime);
            // Matching wrapped anchor/camera keeps the first body alive; no geometry fitting in production.
            int camera = row[3] < 0x80 ? 0 : row[3];
            new CdRing(runtime, data()).executeSelected(32, input(camera, row[0], row[1]), ports, ports);
            assertEquals(row[3], runtime.pool().state(33).centreX(), "D2 reset before each member");
            assertEquals(row[3], runtime.pool().state(33).word(RING_ANCHOR_X));
            assertEquals(0, runtime.savedFlag(3, row[2]));
            for (int p = 0; p < 3; p++) { if (p != row[2]) { assertEquals(0x80, runtime.savedFlag(3, p)); } }
        }
    }

    @Test
    void earlierPeriodSuppressionStopsAtSelectedPeriodAndDoesNotScanLaterBytes() {
        var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x21, 3);
        runtime.bsetSavedFlag(3, 2, MEMBER_1);
        var ports = new Ports(runtime);
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertEquals(0x10, runtime.pool().state(32).id(), "Future bit cannot suppress Present");
        var past = runtime(); seed(past, 32, 0xF0, 0x720, 0x21, 3);
        past.bsetSavedFlag(3, 1, MEMBER_1);
        var pp = new Ports(past);
        new CdRing(past, data()).executeSelected(32, input(0, 0, 0), pp, pp);
        assertEquals(0x10, past.pool().state(32).id(), "Present bit cannot suppress Past");
    }

    @Test
    void mainUsesGroupAnchorUnsignedWordRangeInclusive280AndPriorOnscreenBypass() {
        var runtime = runtime(); var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        seedMain(runtime, 32, 0xFFFF, 0x200); // camera 0 => left chunk FF80, distance 280.
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(0x10, runtime.pool().state(32).id());
        assertEquals(List.of(32), ports.drawn);
        seedMain(runtime, 33, 0, 0x280); // distance 300, destroy, independent of own X.
        ring.executeSelected(33, input(), ports, ports);
        assertArrayEquals(new byte[64], runtime.pool().state(33).copyBytes());
        seedMain(runtime, 34, 0, 0x4000);
        runtime.pool().write(runtime.pool().state(34).moveByte(SPRITE_FLAGS, 0x84));
        ring.executeSelected(34, input(0, 0x81, 1), ports, ports);
        assertEquals(0x10, runtime.pool().state(34).id(), "Main has no invented warp despawn");
        assertEquals(4, runtime.pool().state(34).spriteFlags(), "Draw port owns bit 7 clear");
    }

    @Test
    void destroyClearsAllBytesWithoutClearingSavedFlagsOrCallingDrawOrReward() {
        var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x21, 3);
        runtime.bsetSavedFlag(3, 1, MEMBER_2);
        runtime.pool().write(dirty(runtime.pool().state(32)).moveByte(ROUTINE, 8));
        var ports = new Ports(runtime); var before = ports.value;
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertArrayEquals(new byte[64], runtime.pool().state(32).copyBytes());
        assertEquals(0x82, runtime.savedFlag(3, 1)); assertEquals(before, ports.value);
        assertTrue(ports.events.isEmpty());
    }

    @Test
    void ringUsesSavedIdZeroAsARealBankAddressAndRangeDeletionIsPlainDelete() {
        var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x21, 0);
        runtime.bsetSavedFlag(0, 0, MEMBER_2);
        var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(0, runtime.savedFlag(0, 1)); assertEquals(2, runtime.savedFlag(0, 0));
        assertEquals(0, runtime.pool().state(33).id(), "Earlier member-2 carry suppresses child");
        runtime.pool().write(runtime.pool().state(32).moveWord(RING_ANCHOR_X, 0x280));
        runtime.bsetSavedFlag(0, 1, LOADED);
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(0, runtime.pool().state(32).id()); assertEquals(0x80, runtime.savedFlag(0, 1));
        assertEquals(2, runtime.savedFlag(0, 0));
    }

    @Test
    void collectCommitsNativeWritesThenSoundThenBankThenSparkleThenDrawExactlyOnce() {
        var runtime = collecting(); var ports = new Ports(runtime);
        var ring = new CdRing(runtime, data());
        ports.onSound = () -> {
            assertEquals(6, runtime.pool().state(32).routine());
            assertEquals(0, runtime.pool().state(32).unsignedByte(COLLISION_TYPE));
            assertEquals(1, runtime.pool().state(32).unsignedByte(PRIORITY));
            assertEquals(1, ports.value.rings()); assertEquals(0x80, runtime.savedFlag(3, 1));
            assertEquals(0, runtime.pool().state(32).unsignedByte(ANIMATION_FRAME));
        };
        ports.onDraw = () -> {
            assertEquals(0x82, runtime.savedFlag(3, 1));
            assertEquals(4, runtime.pool().state(32).unsignedByte(MAP_FRAME));
        };
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(List.of("globals", "FM_RING", "draw:32"), ports.events);
        assertEquals(1, ports.value.updateHudRings());
        ports.onSound = () -> { }; ports.onDraw = () -> { };
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(1, ports.value.rings()); assertEquals(1, ports.sounds.size());
    }

    @Test
    void literalThresholdsUseBsetOldBitUnsignedWordsAndByteIncrementNotReset() {
        int[][] cases = {{98, 0x80, 3, 9, 99, 0x80, 3, 9, 0},
                {99, 0x80, 255, 255, 100, 0x82, 0, 0, 1},
                {199, 2, 3, 9, 200, 6, 4, 10, 1},
                {199, 0, 3, 9, 200, 2, 4, 10, 1},
                {200, 2, 3, 9, 201, 6, 4, 10, 1},
                {200, 6, 3, 9, 201, 6, 3, 9, 0},
                {0xFFFF, 0xFF, 3, 9, 0, 0xFF, 3, 9, 0},
                {0x7FFF, 0, 3, 9, 0x8000, 2, 4, 10, 1}};
        for (int[] c : cases) {
            var runtime = collecting(); var ports = new Ports(runtime);
            ports.value = new CdRingGlobals(c[0], c[2], c[1], 0xA0, c[3], 0xBEEF, 0x80, 0xFE);
            new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
            assertEquals(new CdRingGlobals(c[4], c[6], c[5], 0xA1, c[7], 0xBEEF, 0x80, 0xFE), ports.value);
            assertEquals(List.of(c[8] == 1 ? "SCMD_YESSFX" : "FM_RING"), ports.sounds);
        }
    }

    @Test
    void collectUsesOldWarpPeriodAndNativeMemoryBsetModuloEightIncludingLoadedBit() {
        for (int member : new int[]{0, 1, 7, 8, 9, 255}) {
            var runtime = collecting(); var ports = new Ports(runtime);
            runtime.pool().write(runtime.pool().state(32).moveByte(RING_MEMBER, member));
            new CdRing(runtime, data()).executeSelected(32, input(0, 0x81, 1), ports, ports);
            assertEquals(1 << ((member - 1) & 7), runtime.savedFlag(3, 0));
            assertEquals(0x80, runtime.savedFlag(3, 1));
        }
    }

    @Test
    void sparkleReachesFcAtCall24AndDeletesOnlyOnTheFollowingDispatch() {
        var runtime = collecting(); var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        for (int call = 0; call <= 24; call++) {
            ring.executeSelected(32, input(), ports, ports);
            assertEquals(call == 24 ? 8 : 6, runtime.pool().state(32).routine());
            assertEquals(4 + Math.min(call / 6, 3), runtime.pool().state(32).unsignedByte(MAP_FRAME));
            assertEquals(0x10, runtime.pool().state(32).id());
        }
        assertEquals(25, ports.drawn.size()); assertEquals(1, ports.value.rings());
        assertEquals(4, runtime.pool().state(32).unsignedByte(ANIMATION_FRAME));
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(0, runtime.pool().state(32).id()); assertEquals(25, ports.drawn.size());
        assertEquals(0x82, runtime.savedFlag(3, 1));
    }

    @Test
    void dirtyAnimationWaitsOrFailsAtActualReadAndDoesNotInventASparkleReset() {
        var runtime = collecting(); var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        runtime.pool().write(runtime.pool().state(32).moveByte(ANIMATION_FRAME, 3)
                .moveByte(ANIMATION_TIME, 2).moveByte(MAP_FRAME, 0xDC));
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(1, runtime.pool().state(32).unsignedByte(ANIMATION_TIME));
        assertEquals(3, runtime.pool().state(32).unsignedByte(ANIMATION_FRAME));
        assertEquals(0xDC, runtime.pool().state(32).unsignedByte(MAP_FRAME));
        var broken = collecting(); var bp = new Ports(broken);
        broken.pool().write(broken.pool().state(32).moveByte(ANIMATION, 2));
        assertThrows(UnsupportedOperationException.class,
                () -> new CdRing(broken, data()).executeSelected(32, input(), bp, bp));
        assertEquals(1, bp.value.rings()); assertEquals(0x82, broken.savedFlag(3, 1));
        assertEquals(6, broken.pool().state(32).routine()); assertTrue(bp.drawn.isEmpty());
    }

    @Test
    void soundFailurePropagatesWithoutFalseRollbackOrLaterBankAnimationDraw() {
        var runtime = collecting(); var ports = new Ports(runtime);
        var failure = new IllegalStateException("owned source port failed");
        ports.onSound = () -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new CdRing(runtime, data()).executeSelected(32, input(), ports, ports)));
        assertEquals(6, runtime.pool().state(32).routine()); assertEquals(1, ports.value.rings());
        assertEquals(0x80, runtime.savedFlag(3, 1));
        assertEquals(0, runtime.pool().state(32).unsignedByte(ANIMATION_FRAME)); assertTrue(ports.drawn.isEmpty());
    }

    @Test
    void drawPortReceivesLivePointerAndThereIsNoStaleSlotPublicationAfterIt() {
        var runtime = collecting(); var ports = new Ports(runtime);
        ports.onDraw = () -> {
            assertEquals(1, runtime.pool().state(32).unsignedByte(PRIORITY));
            runtime.pool().deleteObject(32);
            runtime.pool().write(runtime.pool().state(32).moveByte(ID, 0x3D).moveByte(FLAGS, 0xA5));
        };
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertEquals(0x3D, runtime.pool().state(32).id()); assertEquals(0xA5, runtime.pool().state(32).flags());
        assertEquals(0, runtime.pool().state(32).unsignedByte(MAP_FRAME));
        var failure = new IllegalStateException("draw failure");
        seedMain(runtime, 33, 0xF0, 0xF0); ports.onDraw = () -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new CdRing(runtime, data()).executeSelected(33, input(), ports, ports)));
        assertEquals(3, runtime.pool().state(33).unsignedByte(MAP_FRAME));
    }

    @Test
    void ringHandsPriorOnscreenBitToDrawObjectRatherThanClearingItBeforeItsSourceCall() {
        var runtime = runtime(); seedMain(runtime, 32, 0xF0, 0xF0);
        runtime.pool().write(runtime.pool().state(32).moveByte(SPRITE_FLAGS, 0xC7));
        var ports = new Ports(runtime) {
            @Override public void drawObject(CdObjectPool pool, int whole) {
                assertEquals(0xC7, pool.state(whole).spriteFlags());
                assertEquals(3, pool.state(whole).unsignedByte(MAP_FRAME));
                super.drawObject(pool, whole);
            }
        };
        new CdRing(runtime, data()).executeSelected(32, input(), ports, ports);
        assertEquals(0x47, runtime.pool().state(32).spriteFlags());
    }

    @Test
    void globalTimerIsByteNegativeTestForEveryValueAndIgnoresTimeStop() {
        for (int timer = 0; timer < 256; timer++) {
            for (int frame : new int[]{0, 3, 0xFF}) {
                var before = new CdRingGlobals(17, 9, 0xA5, 0x80, 2, 0xFFFF, timer, frame);
                var after = before.advanceGlobalRingAnimation();
                int decremented = (timer - 1) & 255;
                boolean expired = decremented >= 128;
                assertEquals(new CdRingGlobals(17, 9, 0xA5, 0x80, 2, 0xFFFF,
                        expired ? 7 : decremented, expired ? ((frame + 1) & 3) : frame), after);
                assertEquals(timer, before.ringAnimationTimer(), "Immutable old boundary");
            }
        }
    }

    @Test
    void ringMainObservesPriorTailFrameAndTimeStopOnlySuppressesItsMapCopy() {
        var runtime = runtime(); seedMain(runtime, 32, 0xF0, 0xF0);
        var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        ports.value = new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 3);
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(3, runtime.pool().state(32).unsignedByte(MAP_FRAME));
        assertEquals(0, ports.value.ringAnimationTimer(), "Body never advances global clock");
        ports.value = ports.value.advanceGlobalRingAnimation();
        assertEquals(0, ports.value.ringAnimationFrame());
        assertEquals(3, runtime.pool().state(32).unsignedByte(MAP_FRAME), "Tail cannot rewrite queued object");
        ports.value = new CdRingGlobals(0, 3, 0, 0, 0, 1, 0, 1);
        ring.executeSelected(32, input(), ports, ports);
        assertEquals(3, runtime.pool().state(32).unsignedByte(MAP_FRAME));
        ports.value = ports.value.advanceGlobalRingAnimation();
        assertEquals(2, ports.value.ringAnimationFrame());
    }

    @Test
    void immutableUnitBoundaryCaptureReplaysPoolSavedBankGlobalsAndOrderedEffects() {
        var runtime = collecting(); var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        var capture = runtime.capture(); var globals = ports.value;
        for (int i = 0; i < 26; i++) {
            ring.executeSelected(32, input(), ports, ports);
            ports.value = ports.value.advanceGlobalRingAnimation();
        }
        var finalSlots = runtime.pool().capture().slots(); var finalBank = runtime.capture().savedFlags().copy();
        var finalGlobals = ports.value; var effects = List.copyOf(ports.events);
        runtime.validateRestore(capture); runtime.restore(capture); ports.value = globals;
        assertTrue(ports.value == globals); assertEquals(4, runtime.pool().state(32).routine());
        assertEquals(0x80, runtime.savedFlag(3, 1));
        ports.events.clear(); ports.sounds.clear(); ports.drawn.clear();
        for (int i = 0; i < 26; i++) {
            ring.executeSelected(32, input(), ports, ports);
            ports.value = ports.value.advanceGlobalRingAnimation();
        }
        assertEquals(finalSlots, runtime.pool().capture().slots()); assertArrayEquals(finalBank, runtime.capture().savedFlags().copy());
        assertEquals(finalGlobals, ports.value); assertEquals(effects, ports.events);
        assertEquals(4, capture.pool().slots().getFirst().routine());
    }

    @Test
    void restoreAtSparkleCutDoesNotReplayRewardOrAllocateAndForeignRestoreIsRejected() {
        var runtime = collecting(); var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        ring.executeSelected(32, input(), ports, ports);
        var snap = runtime.capture(); var global = ports.value;
        for (int i = 0; i < 7; i++) { ring.executeSelected(32, input(), ports, ports); }
        var later = runtime.pool().state(32); int soundCount = ports.sounds.size();
        runtime.restore(snap); ports.value = global;
        assertEquals(soundCount, ports.sounds.size(), "Restore calls no ports");
        for (int i = 0; i < 7; i++) { ring.executeSelected(32, input(), ports, ports); }
        assertEquals(later, runtime.pool().state(32)); assertEquals(soundCount, ports.sounds.size());
        var foreign = runtime(); var before = foreign.capture();
        assertThrows(IllegalArgumentException.class, () -> foreign.restore(snap));
        assertEquals(before.pool().slots(), foreign.pool().capture().slots());
    }

    @Test
    void explicitDomainAndDispatchGatesFailBeforeWritesRatherThanSkippingSource() {
        var runtime = collecting(); var ports = new Ports(runtime); var ring = new CdRing(runtime, data());
        var before = runtime.capture();
        for (var bad : List.of(new CdRing.Inputs(CdReleaseVariant.JAPAN, CdStageKey.parse("R11A"), 0, 0, 1, 0),
                new CdRing.Inputs(CdReleaseVariant.USA, CdStageKey.parse("R12A"), 0, 0, 1, 0),
                new CdRing.Inputs(CdReleaseVariant.USA, CdStageKey.parse("R11A"), 6, 0, 1, 0), input(0, 3, 0))) {
            assertThrows(UnsupportedOperationException.class, () -> ring.executeSelected(32, bad, ports, ports));
            assertEquals(before.pool().slots(), runtime.pool().capture().slots());
            assertArrayEquals(before.savedFlags().copy(), runtime.capture().savedFlags().copy());
        }
        runtime.pool().write(runtime.pool().state(32).moveByte(ROUTINE, 1));
        assertThrows(UnsupportedOperationException.class, () -> ring.executeSelected(32, input(), ports, ports));
        assertTrue(ports.events.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> input(-1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdRingGlobals(0x10000, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdRingGlobals(0, 256, 0, 0, 0, 0, 0, 0));
    }

    static CdRing.SourceData data() {
        var deltas = CdR11aObjectAssets.decodeRingDeltas(hex("100018002000001000180020101018182020f010e818e02010081810f008e810"));
        var visual = CdObjectVisualAssets.decode(hex("00120018001e0024002a00300036003c0042"
                + "01f8050000f8" + "01f8050004f8" + "01f8010008fc" + "01f8050804f8"
                + "01f805000af8" + "01f805180af8" + "01f805100af8" + "01f805080af8" + "00"),
                9, hex("00020504050607fc"), 1, 14);
        return new CdRing.SourceData(deltas, 0x20A0FA, 0xA7AE, visual);
    }

    static CdObjectPlacementRuntime runtime() {
        // The existing placement owner requires the three real format views, even for an empty common list.
        var bytes = ByteBuffer.allocate(26);
        bytes.putShort((short) 12).putShort((short) 20).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0);
        return new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(bytes.array()));
    }
    static void seed(CdObjectPlacementRuntime runtime, int whole, int x, int y, int subtype, int saved) {
        runtime.pool().write(runtime.pool().state(whole).moveByte(ID, 0x10).moveWord(X, x).moveWord(Y, y)
                .moveByte(SUBTYPE, subtype).moveByte(SAVED_FLAGS_ID, saved));
        runtime.bsetSavedFlag(saved, 1, LOADED);
    }
    static CdRing.Inputs input() { return input(0, 1, 0); }
    static CdRing.Inputs input(int camera, int period, int direction) {
        return new CdRing.Inputs(CdReleaseVariant.USA, CdStageKey.parse("R11A"), 0, camera, period, direction);
    }
    static CdObjectPlacementRuntime collecting() {
        var runtime = runtime(); seed(runtime, 32, 0xF0, 0x720, 0x21, 3);
        runtime.pool().write(initialized(runtime.pool().state(32), 0x110, 0x720, 0xF0, 3, 2).moveByte(ROUTINE, 4));
        return runtime;
    }
    private static void seedMain(CdObjectPlacementRuntime runtime, int whole, int x, int anchor) {
        runtime.pool().write(initialized(runtime.pool().state(whole), x, 0x720, anchor, 3, 1));
    }
    private static CdObjectState dirty(CdObjectState s) {
        return s.moveWord(X_FRACTION, 0xFFFF).moveWord(Y_FRACTION, 0x1357).moveByte(ANIMATION, 0x91)
                .moveByte(PREVIOUS_ANIMATION, 0xA2).moveByte(ANIMATION_FRAME, 0xB2).moveByte(ANIMATION_TIME, 0xD3)
                .moveByte(MAP_FRAME, 0xE4).moveByte(FLAGS, 0xF5).moveByte(ROUTINE_2, 0x81).moveByte(SUBTYPE2, 0x76)
                .moveByte(COLLISION_STATUS, 0xC7).moveWord(X_VELOCITY, 0x1234).moveWord(Y_VELOCITY, 0xFEDC);
    }
    private static CdObjectState initialized(CdObjectState s, int x, int y, int anchor, int saved, int member) {
        return s.moveByte(ID, 0x10).moveByte(ROUTINE, 2).moveWord(X, x).moveWord(RING_ANCHOR_X, anchor).moveWord(Y, y)
                .moveLong(MAPPINGS, 0x20A0FA).moveWord(TILE, 0xA7AE).moveByte(PRIORITY, 2).moveByte(SPRITE_FLAGS, 4)
                .moveByte(COLLISION_TYPE, 0x47).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8)
                .moveByte(SAVED_FLAGS_ID, saved).moveByte(RING_MEMBER, member);
    }
    private static byte[] hex(String s) { return HexFormat.of().parseHex(s); }

    /** Test-owned semantic ports, not another queue or production globals owner. */
    static class Ports implements CdRing.GlobalsPort, CdRing.SourceCalls {
        final CdObjectPlacementRuntime runtime;
        CdRingGlobals value = new CdRingGlobals(0, 3, 0, 0, 0, 0, 7, 3);
        final List<Integer> drawn = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        Runnable onSound = () -> { }; Runnable onDraw = () -> { };
        Ports(CdObjectPlacementRuntime runtime) { this.runtime = runtime; }
        @Override public CdRingGlobals read() { return value; }
        @Override public void write(CdRingGlobals next) { value = next; events.add("globals"); }
        @Override public void playFmSound(CdRing.FmSound sound) {
            sounds.add(sound.name()); events.add(sound.name()); onSound.run();
        }
        @Override public void subCpuCommand(CdRing.SubCpuCommand command) {
            sounds.add(command.name()); events.add(command.name()); onSound.run();
        }
        @Override public void drawObject(CdObjectPool pool, int wholeIndex) {
            assertSame(runtime.pool(), pool);
            // DrawObject entry only. There is deliberately no sprite queue/presentation model in these tests.
            pool.write(pool.state(wholeIndex).moveByte(SPRITE_FLAGS, pool.state(wholeIndex).spriteFlags() & 0x7F));
            drawn.add(wholeIndex); events.add("draw:" + wholeIndex); onDraw.run();
        }
    }
}
