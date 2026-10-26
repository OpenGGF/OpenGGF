package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdPlatform.Site.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-only R1; every test UNRUN at freeze. N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Platform/Main.asm:7-555, Object Functions.asm:91-196, Collision Check.asm:318-338.
 * Real shared owners consume constructed public-format fixtures. No forged Result/Prefix, probe,
 * private State constructor, reflection, raw RAM setter or authenticated-USA assertion is used.
 * Full 64-byte comparisons cover typed-accessible dirty bytes; opaque gaps remain their public zero.
 * Harness publication is not private Runtime/Frame/phase/CAS/FM-ledger authentication.
 * Root owns RED, import, compile, positive runs, review, R2, World/runtime/route/native evidence.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdPlatform {
    private static final int WHOLE = 40, X0 = 3248, Y0 = 784, MAP = 0x20CCC8;
    private static final byte[] WIDTHS = {0x10, 0x20, 0x30, 0};
    private static final byte[] RANGES = {2, 3, 4, 6};
    private static final Data DEFAULT_DATA = new Terrain().data();

    @Test void eightAliasesAddressOnlyExistingStorageAndKeepCollapseAliases() {
        assertEquals(0x2A, PLATFORM_PHASE.byteOffset()); assertEquals(0x2B, PLATFORM_SUBSTATE.byteOffset());
        assertEquals(0x2C, PLATFORM_SAG.byteOffset()); assertEquals(0x2D, PLATFORM_RANGE.byteOffset());
        assertEquals(0x2E, PLATFORM_TIMER.byteOffset()); assertEquals(0x36, PLATFORM_INITIAL_Y.byteOffset());
        assertEquals(0x38, PLATFORM_ANCHOR_X.byteOffset()); assertEquals(0x3A, PLATFORM_ANCHOR_Y.byteOffset());
        var pool = new CdObjectPool(); var before = pool.state(32);
        var changed = before.moveByte(PLATFORM_PHASE, 0xA1).moveByte(PLATFORM_SUBSTATE, 0xB2)
                .moveByte(PLATFORM_SAG, 0xC3).moveByte(PLATFORM_RANGE, 0xD4).moveByte(PLATFORM_TIMER, 0xE5)
                .moveWord(PLATFORM_INITIAL_Y, 0x1020).moveWord(PLATFORM_ANCHOR_X, 0x3040).moveWord(PLATFORM_ANCHOR_Y, 0x5060);
        byte[] e = new byte[64]; e[0x2A] = (byte) 0xA1; e[0x2B] = (byte) 0xB2; e[0x2C] = (byte) 0xC3;
        e[0x2D] = (byte) 0xD4; e[0x2E] = (byte) 0xE5; word(e, 0x36, 0x1020); word(e, 0x38, 0x3040); word(e, 0x3A, 0x5060);
        assertArrayEquals(e, changed.copyBytes()); assertArrayEquals(new byte[64], before.copyBytes());
        assertEquals(0xA1B2, changed.word(COLLAPSE_DELAY)); assertEquals(0xC3D4E500, changed.longBits(COLLAPSE_FALL_DELTA));
        assertEquals(0xC3, changed.unsignedByte(RAW_CONTROL_2C)); assertEquals(0x3040, changed.word(PATA_BATA_SHIFT));
    }

    @Test void initAllFramesRangesAndZoneBranchesPreservesEveryOtherByte() {
        for (int subtype = 0; subtype < 16; subtype++) for (int zone : new int[]{0, 1, 2, 0x0100, 0xFFFF}) {
            var h = new Harness(); final int st = subtype;
            h.edit(o -> dirty(o).moveByte(ID, 0x21).moveByte(ROUTINE, 0).moveByte(SUBTYPE, st).moveByte(SUBTYPE2, 0));
            var before = h.slot(); var r = h.run(world(zone, 0, X0 - 128, Y0 - 128, 0xFF));
            byte[] e = initExpected(before, subtype); assertArrayEquals(e, h.drawSeen.getFirst().copyBytes());
            e[1] &= 0x7F; assertArrayEquals(e, r.state().copyBytes());
            assertTrue(h.calls.isEmpty()); assertTrue(h.angles.isEmpty()); assertEquals(1, h.drawSeen.size());
        }
    }

    @Test void initNeverPrefiltersHighNibbleAndReturnsWithoutMainOrAnchorTail() {
        for (int high = 0; high < 16; high++) {
            var h = new Harness(); final int st = high << 4; h.edit(o -> o.moveByte(SUBTYPE, st));
            h.body.run(WHOLE, world(0, 0, 0x7000, 0, 0xFF), null);
            assertEquals(2, h.slot().routine()); assertEquals(0x21, h.slot().id());
            assertEquals(0, h.slot().unsignedByte(PLATFORM_PHASE)); assertTrue(h.calls.isEmpty());
            assertEquals(1, h.drawSeen.size());
        }
    }

    @Test void frameThreeStopsOnlyAtLaterActualFiniteDrawReader() {
        var h = new Harness(); h.edit(o -> o.moveByte(SUBTYPE, 3)); var r = h.run();
        assertEquals(3, r.state().unsignedByte(MAP_FRAME)); assertEquals(0, r.state().unsignedByte(WIDTH));
        assertEquals(2, r.state().routine()); assertTrue(h.calls.isEmpty());
        assertThrows(UnsupportedOperationException.class, h::finishDraw);
        assertEquals(CdObjectDrawQueue.Phase.FAILED, h.queue.phase());
    }

    @Test void widthFailureRetainsExactEarlierInitPrefixBeforeRadiusAndRange() {
        var h = new Harness(assets(new byte[0], RANGES), DEFAULT_DATA);
        h.edit(o -> dirty(o).moveByte(ID, 0x21).moveByte(ROUTINE, 0).moveByte(SUBTYPE, 2).moveByte(SUBTYPE2, 0));
        var before = h.slot(); var ex = assertThrows(CdPlatform.UnsupportedPlatform.class, h::run);
        assertEquals(CdPlatform.UnsupportedBoundary.WIDTH_READ, ex.boundary()); assertEquals(2, ex.byteOffset());
        byte[] e = initHeader(before); e[0x1A] = 2; assertArrayEquals(e, h.slot().copyBytes());
        assertTrue(h.calls.isEmpty()); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void rangeFailureRetainsWidthAndRadiusButNotRoutineIncrement() {
        var h = new Harness(assets(WIDTHS, new byte[]{2}), DEFAULT_DATA);
        h.edit(o -> dirty(o).moveByte(ID, 0x21).moveByte(ROUTINE, 0).moveByte(SUBTYPE, 0x0D).moveByte(SUBTYPE2, 0));
        var before = h.slot(); var ex = assertThrows(CdPlatform.UnsupportedPlatform.class, h::run);
        assertEquals(CdPlatform.UnsupportedBoundary.RANGE_READ, ex.boundary()); assertEquals(3, ex.byteOffset());
        byte[] e = initHeader(before); e[0x1A] = 1; e[0x19] = 0x20; e[0x16] = 8;
        assertArrayEquals(e, h.slot().copyBytes()); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void reachedZoneReadFailureKeepsHeaderAndAnchorsBeforeMappingStore() {
        var h = new Harness(); var before = h.slot();
        assertThrows(NullPointerException.class, () -> h.body.run(WHOLE, null, null));
        byte[] e = before.copyBytes(); e[1] |= 4; word(e, 2, 0x44BE); e[0x18] = 2;
        word(e, 0x38, X0); word(e, 0x3A, Y0); word(e, 0x36, Y0);
        assertArrayEquals(e, h.slot().copyBytes()); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void allNonzeroSecondaryBytesPartiallyInitializeActualFirstFreeDirtySpring() {
        for (int secondary = 1; secondary < 256; secondary++) {
            var h = new Harness(); final int sec = secondary;
            h.pool().write(dirty(h.pool().state(32)).moveByte(ID, 0)); var before = h.pool().state(32);
            h.edit(o -> o.moveByte(SUBTYPE2, sec)); h.run();
            byte[] e = before.copyBytes(); e[0] = 0x0A; word(e, 8, X0 + (secondary & 0xF8)); word(e, 0x0C, Y0 - 16);
            e[0x39] = (byte) 0xF0; word(e, 0x34, 0xD000 + WHOLE * 64); e[0x28] = (byte) (secondary & 2); e[0x38] = (byte) (secondary & 0xF8);
            assertArrayEquals(e, h.pool().state(32).copyBytes(), "secondary " + secondary);
            assertEquals(2, h.slot().routine()); assertTrue(h.calls.isEmpty());
        }
    }

    @Test void dirtyFreeSlotIsIdOnlyAndInitialOffsetIsZeroExtendedDespiteLaterSignedAlias() {
        var h = new Harness(); h.pool().write(dirty(h.pool().state(32)).moveByte(ID, 0));
        h.edit(o -> o.moveByte(SUBTYPE2, 0xF8)); h.run(); var child = h.pool().state(32);
        assertEquals(X0 + 248, child.centreX()); assertEquals(-8, child.signedByte(SPRING_PARENT_X_OFFSET));
        assertNotEquals(X0 - 8, child.centreX()); assertEquals(0xD000 + WHOLE * 64, child.word(SPRING_PARENT));
        assertNotEquals(0, child.routine()); assertNotEquals(0, child.word(X_FRACTION));
    }

    @Test void allocationExhaustionDestroysBeforeRoutineIncrementAndStillDrawsClearedSlot() {
        var h = new Harness(); h.fillPool(); h.edit(o -> o.moveByte(SUBTYPE2, 1).moveByte(SAVED_FLAGS_ID, 1));
        h.placement.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.placement.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
        var r = h.run(); assertTrue(r.deleted()); assertArrayEquals(new byte[64], h.drawSeen.getFirst().copyBytes());
        assertEquals(2, h.placement.savedFlag(1, 1)); assertTrue(h.calls.isEmpty()); assertEquals(1, h.drawSeen.size());
    }

    @Test void earlierChildWaitsButLaterChildIsVisibleToAscendingPhysicalWalk() {
        for (int free : new int[]{32, 41}) {
            var h = new Harness(); h.fillPool(); h.pool().objNull(free); h.edit(o -> o.moveByte(SUBTYPE2, 1));
            var encountered = new ArrayList<Integer>();
            for (int whole = 32; whole < 128; whole++) {
                int id = h.pool().state(whole).id();
                if (id == 0x21) h.body.run(whole, world(), h.inputs());
                else if (id == 0x0A) encountered.add(whole); // Observe dispatch eligibility; do NOT pretend Spring ran.
            }
            assertEquals(free == 32 ? List.of() : List.of(41), encountered);
            assertEquals(0x0A, h.pool().state(free).id()); assertEquals(0, h.pool().state(free).routine());
        }
    }

    @Test void allTenMainFamiliesReachTheirExactNativeTopSitesWithoutOuterVisibilityGate() {
        var sites = List.of(SUBTYPE_0_TOP, SUBTYPE_1_TOP, SUBTYPE_2_TOP, SUBTYPE_3_TOP, SUBTYPE_4_TOP,
                SUBTYPE_5_STATIONARY_TOP, SUBTYPE_6_STATIONARY_1_TOP, SUBTYPE_7_STATIONARY_1_TOP,
                SUBTYPE_8_STATIONARY_1_TOP, SUBTYPE_9_STATIONARY_1_TOP);
        for (int variant = 0; variant < 10; variant++) {
            var h = new Harness(); h.main(variant << 4); h.run();
            assertEquals(List.of(sites.get(variant)), h.sites()); assertEquals(1, h.drawSeen.size());
            assertEquals(96, h.calls.getFirst().actualCut().slots().size()); assertEquals(0x21, h.slot().id());
        }
    }

    @Test void rawRoutineAndSubtypeDirectoryStopsDoNotNormalizeDispatchOrDraw() {
        for (int raw : new int[]{1, 3, 4, 0x80, 0xFF}) {
            var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, raw)); var before = h.pool().capture();
            var ex = assertThrows(CdPlatform.UnsupportedPlatform.class, h::run);
            assertEquals(CdPlatform.UnsupportedBoundary.ROUTINE_DISPATCH, ex.boundary()); assertEquals(raw, ex.byteOffset());
            assertPool(before, h.pool()); assertTrue(h.calls.isEmpty()); assertTrue(h.drawSeen.isEmpty());
        }
        for (int high = 10; high < 16; high++) {
            var h = new Harness(); h.main(high << 4); var before = h.pool().capture();
            var ex = assertThrows(CdPlatform.UnsupportedPlatform.class, h::run);
            assertEquals(CdPlatform.UnsupportedBoundary.SUBTYPE_DISPATCH, ex.boundary()); assertEquals(high * 2, ex.byteOffset());
            assertPool(before, h.pool()); assertTrue(h.drawSeen.isEmpty());
        }
    }

    @Test void allFourSubstateDirectoriesUseRawByteOffsetsAndStopBeforeNonwrites() {
        for (int high = 6; high <= 9; high++) for (int raw : new int[]{1, 3, 5, 6, 0x80, 0xFF}) {
            var h = new Harness(); h.main(high << 4); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, raw)); var before = h.pool().capture();
            var ex = assertThrows(CdPlatform.UnsupportedPlatform.class, h::run);
            assertEquals(CdPlatform.UnsupportedBoundary.SUBSTATE_DISPATCH, ex.boundary()); assertEquals(raw, ex.byteOffset());
            assertPool(before, h.pool()); assertTrue(h.calls.isEmpty()); assertTrue(h.drawSeen.isEmpty());
        }
    }

    @Test void timeStopIsFullWordAndDirectTopSkipsInvalidSubtypeAndAnchorTail() {
        for (int timer : new int[]{1, 0x100, 0x8000, 0xFFFF}) {
            var h = new Harness(); h.main(0xFF); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 0xFF).moveWord(PLATFORM_ANCHOR_X, 0x7000));
            var before = h.slot(); h.run(world(0, timer, 0x80, 0, 0xFF));
            byte[] e = before.copyBytes(); e[0x25] = 0; e[1] &= 0x7F; assertArrayEquals(e, h.slot().copyBytes());
            assertEquals(List.of(TIME_STOP_TOP), h.sites()); assertEquals(1, h.drawSeen.size());
        }
    }

    @Test void byteWidthSagShiftIsUnsignedWordAdditionForFFAnd80AndContactDoesNotClamp() {
        for (int sag : new int[]{0, 1, 8, 0x80, 0xFF}) for (boolean contact : new boolean[]{false, true}) {
            var h = new Harness(); h.main(0x40); h.edit(o -> o.moveByte(PLATFORM_SAG, sag));
            int displacement = ((byte) sag >> 1) & 0xFF;
            if (contact) { h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x84)); h.contactAt(Y0 + displacement); }
            var before = h.slot(); h.run(); var cut = h.calls.getFirst().actualCut().slots().get(WHOLE - 32);
            byte[] e = before.copyBytes(); word(e, 0x0C, Y0 + displacement); assertArrayEquals(e, cut.copyBytes());
            assertEquals(contact ? (sag >= 8 ? sag : sag + 1) : Math.max(0, sag - 1), h.slot().unsignedByte(PLATFORM_SAG));
            assertEquals(before.word(X_VELOCITY), h.slot().word(X_VELOCITY)); assertEquals(before.word(Y_VELOCITY), h.slot().word(Y_VELOCITY));
        }
    }

    @Test void oscillatorShiftsLowProductWordAndPhaseByteWraps() {
        var h = new Harness(); h.main(0); h.edit(o -> o.moveByte(PLATFORM_PHASE, 0x3F).moveByte(PLATFORM_RANGE, 0xFF)); h.run();
        assertEquals(Y0 - 16, h.slot().centreY()); assertEquals(0x40, h.slot().unsignedByte(PLATFORM_PHASE));
        var wrap = new Harness(); wrap.main(0); wrap.edit(o -> o.moveByte(PLATFORM_PHASE, 0xFF)); wrap.run();
        assertEquals(0, wrap.slot().unsignedByte(PLATFORM_PHASE)); assertEquals(Y0, wrap.slot().centreY());
    }

    @Test void subtypeOneUsesOldPhaseThenFullLongDifferenceAndKeepsFractionsAndYVelocity() {
        var h = new Harness(); h.main(0x10); h.edit(o -> o.moveByte(PLATFORM_PHASE, 1).moveByte(PLATFORM_SAG, 3)
                .moveWord(X, X0 - 1).moveWord(X_FRACTION, 0xFFFF).moveWord(Y_VELOCITY, 0xABCD)); h.run();
        assertEquals(X0 + 2, h.slot().centreX()); assertEquals(Y0 + 1, h.slot().centreY());
        assertEquals(0x300, h.slot().word(X_VELOCITY)); assertEquals(0xABCD, h.slot().word(Y_VELOCITY));
        assertEquals(0xFFFF, h.slot().word(X_FRACTION)); assertEquals(0x5678, h.slot().word(Y_FRACTION));
        assertEquals(2, h.slot().unsignedByte(PLATFORM_PHASE)); assertEquals(2, h.slot().unsignedByte(PLATFORM_SAG));
    }

    @Test void subtypeTwoAndThreeUseSamePhaseSineWithWordNegationAndLongWrap() {
        for (int high : new int[]{2, 3}) {
            var h = new Harness(); h.main(high << 4); h.edit(o -> o.moveByte(PLATFORM_PHASE, 1).moveWord(X, 0xFFFF)
                    .moveWord(PLATFORM_ANCHOR_X, 0).moveWord(X_FRACTION, 0xFFFF));
            h.run(world(0, 0, 0x80, 0, 1)); int x = high == 2 ? 4 : 0xFFFC;
            assertEquals(x, h.slot().centreX()); assertEquals(Y0 + 4, h.slot().centreY());
            assertEquals(high == 2 ? 0x500 : 0xFD00, h.slot().word(X_VELOCITY)); assertEquals(0xFFFF, h.slot().word(X_FRACTION));
        }
    }

    @Test void firstFiveContactCallsTopTwiceThreadsActualOutputsAndLeavesTimer29() {
        var h = new Harness(); h.main(0x50); h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x84)); h.contactAt(Y0); h.run();
        assertEquals(List.of(SUBTYPE_5_STATIONARY_TOP, SUBTYPE_5_TIMER_TOP), h.sites());
        assertEquals(29, h.slot().unsignedByte(PLATFORM_TIMER)); assertEquals(2, h.slot().unsignedByte(PLATFORM_SUBSTATE));
        assertEquals(2, h.slot().unsignedByte(PLATFORM_SAG));
        var first = completed(h.calls.getFirst()); var second = h.calls.get(1);
        assertEquals(first.player(), second.expectedPlayer()); assertEquals(first.globals(), second.expectedGlobals());
        assertEquals(29, second.actualCut().slots().get(WHOLE - 32).unsignedByte(PLATFORM_TIMER));
        assertNotSame(h.calls.getFirst().actualCut(), second.actualCut());
    }

    @Test void fiveAcceptsAnyNonzeroSubstateAndTimerOneVisitStillUsesSagPath() {
        for (int raw : new int[]{1, 2, 3, 0x80, 0xFF}) {
            var h = new Harness(); h.main(0x50); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, raw).moveByte(PLATFORM_TIMER, 1)
                    .moveWord(Y_VELOCITY, 0x123)); h.run();
            assertEquals(List.of(SUBTYPE_5_TIMER_TOP), h.sites()); assertEquals(0, h.slot().unsignedByte(PLATFORM_TIMER));
            assertEquals(raw, h.slot().unsignedByte(PLATFORM_SUBSTATE)); assertEquals(Y0, h.slot().centreY());
            assertEquals(0x123, h.slot().word(Y_VELOCITY));
        }
    }

    @Test void fiveDropIntegratesOldSignedYVelocityButAcceleratesOnlyUnsignedBelow0400() {
        for (int old : new int[]{0, 0x3FF, 0x400, 0x7FFF, 0x8000, 0xFFFF}) {
            var h = new Harness(); h.main(0x50); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(PLATFORM_TIMER, 0)
                    .moveWord(Y_VELOCITY, old).moveWord(X_VELOCITY, 0x789A)); var before = h.slot();
            h.run(world(0, 0, X0 - 128, 0xFEFF, 1));
            assertEquals(before.longBits(Y_POSITION) + ((short) old << 8), h.slot().longBits(Y_POSITION));
            assertEquals(old < 0x400 ? old + 0x40 : old, h.slot().word(Y_VELOCITY));
            assertEquals(before.longBits(X_POSITION), h.slot().longBits(X_POSITION)); assertEquals(0x789A, h.slot().word(X_VELOCITY));
            assertEquals(List.of(SUBTYPE_5_DROP_TOP), h.sites());
        }
    }

    @Test void fiveVerticalDeleteDoesNotClearSavedMembershipAndClearedAnchorTailStillRuns() {
        for (int cameraX : new int[]{0x80, X0 - 128}) {
            var h = new Harness(); h.main(0x50); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(SAVED_FLAGS_ID, 1));
            h.placement.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            var r = h.run(world(0, 0, cameraX, 0, 1));
            assertTrue(r.deleted()); assertEquals(0x80, h.placement.savedFlag(1, 1)); assertArrayEquals(new byte[64], h.drawSeen.getFirst().copyBytes());
            assertEquals(cameraX == 0x80 ? List.of(SUBTYPE_5_DROP_TOP, SUBTYPE_5_DELETE_GET_OFF)
                    : List.of(SUBTYPE_5_DROP_TOP, SUBTYPE_5_DELETE_GET_OFF, ANCHOR_GET_OFF), h.sites());
        }
    }

    @Test void fiveVerticalThresholdEqualitySurvivesAndStrictlyBeyondDeletes() {
        for (int y : new int[]{0x100, 0x101}) {
            var h = new Harness(); h.main(0x50); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveWord(Y, y));
            var r = h.run(world(0, 0, X0 + 0x80, 0, 1)); assertEquals(y > 0x100, r.deleted());
        }
    }

    @Test void anchorTailUsesUnsignedChunkBhiNotCurrentXVisibilityOrAbsoluteDistance() {
        for (int anchor : new int[]{0xA00, 0xA80, 0}) {
            var h = new Harness(); h.main(0x40); h.edit(o -> o.moveWord(X, 0x7000).moveWord(PLATFORM_ANCHOR_X, anchor)
                    .moveByte(SAVED_FLAGS_ID, 1)); h.placement.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            var r = h.run(world(0, 0, 0x800, 0, 1)); boolean deleted = anchor != 0xA00;
            assertEquals(deleted, r.deleted()); assertEquals(deleted ? 0 : 0x80, h.placement.savedFlag(1, 1));
            assertEquals(deleted ? List.of(SUBTYPE_4_TOP, ANCHOR_GET_OFF) : List.of(SUBTYPE_4_TOP), h.sites());
            if (deleted) assertArrayEquals(new byte[64], h.drawSeen.getFirst().copyBytes());
        }
    }

    @Test void destroyRawTimeZoneAliasesExactBankAddressWithoutWarpOrClamp() {
        var h = new Harness(); h.main(0x40); h.edit(o -> o.moveWord(PLATFORM_ANCHOR_X, 0x7000).moveByte(SAVED_FLAGS_ID, 1));
        h.placement.bsetSavedFlag(86, 0, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.placement.bsetSavedFlag(86, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
        h.placement.bsetSavedFlag(1, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.run(world(0, 0, 0x80, 0, 0xFF)); // native 2 + 3*1 + 255 = 260 = 2 + 3*86 + 0
        assertEquals(2, h.placement.savedFlag(86, 0)); assertEquals(0x80, h.placement.savedFlag(1, 1));
    }

    @Test void destroyOutsideBankStopsAfterGetOffBeforeDeleteOrDrawAndSavedZeroSkipsBank() {
        var h = new Harness(); h.main(0x40); h.edit(o -> o.moveWord(PLATFORM_ANCHOR_X, 0x7000).moveByte(SAVED_FLAGS_ID, 255));
        var ex = assertThrows(CdPlatform.UnsupportedPlatform.class, () -> h.run(world(0, 0, 0x80, 0, 0)));
        assertEquals(CdPlatform.UnsupportedBoundary.SAVED_FLAGS_ADDRESS, ex.boundary()); assertEquals(767, ex.byteOffset());
        assertEquals(0x21, h.slot().id()); assertEquals(List.of(SUBTYPE_4_TOP, ANCHOR_GET_OFF), h.sites()); assertTrue(h.drawSeen.isEmpty());
        var zero = new Harness(); zero.fillPool(); zero.edit(o -> o.moveByte(SUBTYPE2, 1).moveByte(SAVED_FLAGS_ID, 0));
        var bank = zero.placement.capture().savedFlags().copy(); zero.run(world(0, 0, 0, 0, 0xFF));
        assertArrayEquals(bank, zero.placement.capture().savedFlags().copy()); assertArrayEquals(new byte[64], zero.drawSeen.getFirst().copyBytes());
    }

    @Test void sixPhase3EVisitMovesThenFollowing40VisitStopsAndFallsThroughStationaryTwo() {
        var h = new Harness(); h.main(0x60); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(PLATFORM_PHASE, 0x3E));
        h.run(); assertEquals(Y0 - 31, h.slot().centreY()); assertEquals(0x40, h.slot().unsignedByte(PLATFORM_PHASE));
        assertEquals(List.of(SUBTYPE_6_MOVE_DOWN_TOP), h.sites()); h.calls.clear();
        h.run(); assertEquals(Y0 - 31, h.slot().word(PLATFORM_ANCHOR_Y)); assertEquals(4, h.slot().unsignedByte(PLATFORM_SUBSTATE));
        assertEquals(List.of(SUBTYPE_6_STATIONARY_2_TOP), h.sites());
    }

    @Test void sevenEightNineFirstContactStartsSixtyAndFallsThroughToSecondTopAt59() {
        for (int high : new int[]{7, 8, 9}) {
            var h = new Harness(); h.main(high << 4); h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x84)); h.contactAt(Y0); h.run();
            assertEquals(59, h.slot().unsignedByte(PLATFORM_TIMER)); assertEquals(2, h.slot().unsignedByte(PLATFORM_SUBSTATE));
            assertEquals(2, h.calls.size()); assertEquals(2, h.slot().unsignedByte(PLATFORM_SAG));
            assertEquals(completed(h.calls.getFirst()).player(), h.calls.get(1).expectedPlayer());
        }
    }

    @Test void sevenTimerOneDoesNotMoveOrProbeAndEightNineStopRetainInheritedXVelocity() {
        var seven = new Harness(); seven.main(0x70); seven.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(PLATFORM_TIMER, 1)
                .moveWord(X_VELOCITY, 0x123).moveWord(Y_VELOCITY, 0x456)); var before = seven.slot(); seven.run();
        assertEquals(before.longBits(X_POSITION), seven.slot().longBits(X_POSITION)); assertEquals(0x456, seven.slot().word(Y_VELOCITY));
        assertEquals(List.of(SUBTYPE_7_TIMER_TOP), seven.sites()); assertTrue(seven.angles.isEmpty());
        for (int high : new int[]{8, 9}) {
            var h = new Harness(); h.main(high << 4); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(PLATFORM_PHASE, 0x40)
                    .moveWord(X, X0 + 32).moveWord(X_VELOCITY, 0x9876)); h.run();
            assertEquals(X0 + 32, h.slot().word(PLATFORM_ANCHOR_X)); assertEquals(4, h.slot().unsignedByte(PLATFORM_SUBSTATE));
            assertEquals(0x9876, h.slot().word(X_VELOCITY)); assertEquals(high == 8 ? SUBTYPE_8_STATIONARY_2_TOP : SUBTYPE_9_STATIONARY_2_TOP, h.sites().getFirst());
        }
    }

    @Test void eightNineMoveAtOldPhase3FThenIncrementAndPreserveYVelocityAndFractions() {
        for (int high : new int[]{8, 9}) {
            var h = new Harness(); h.main(high << 4); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(PLATFORM_PHASE, 0x3F)
                    .moveWord(Y_VELOCITY, 0xABCD)); h.run(); int delta = high == 8 ? 32 : -32;
            assertEquals(X0 + delta, h.slot().centreX()); assertEquals((delta << 8) & 0xFFFF, h.slot().word(X_VELOCITY));
            assertEquals(0x40, h.slot().unsignedByte(PLATFORM_PHASE)); assertEquals(0xABCD, h.slot().word(Y_VELOCITY));
            assertEquals(0x1234, h.slot().word(X_FRACTION)); assertEquals(0x5678, h.slot().word(Y_FRACTION));
        }
    }

    @Test void sevenZeroTimerUsesActualObjMoveFullFractionsBeforeYVelocitySubtractAndCeiling() {
        var h = new Harness(); h.main(0x70); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveWord(X_FRACTION, 0xFFFF)
                .moveWord(X_VELOCITY, 1).moveWord(Y_VELOCITY, 0xFEFF)); var before = h.slot(); h.run();
        assertEquals(before.longBits(X_POSITION) + 0x100, h.slot().longBits(X_POSITION));
        assertEquals(before.longBits(Y_POSITION) - 0x10100, h.slot().longBits(Y_POSITION));
        assertEquals(0xFEF7, h.slot().word(Y_VELOCITY)); assertEquals(1, h.slot().word(X_VELOCITY));
        assertTrue(h.angles.isEmpty()); assertEquals(List.of(SUBTYPE_7_RISING_TOP), h.sites());
    }

    @Test void sevenObjMoveReadsActualDynamicAndPrimarySupportAndNeverFabricatesIdZero() {
        for (boolean primary : new boolean[]{false, true}) for (int flags : new int[]{0, 1}) {
            var h = new Harness(); h.main(0x70); int link = primary ? 0 : 91;
            h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(FLAGS, 8).moveByte(STAND_LINK_3D, link));
            if (primary) { var p = new P(); p.id = 0x1E; p.flags = flags; h.player = p.player(); }
            else h.pool().write(h.pool().state(link).moveByte(ID, 0x1E).moveByte(FLAGS, flags));
            int old = h.slot().longBits(X_POSITION); h.run();
            assertEquals(old + (flags == 0 ? -0x10000 : 0x10000), h.slot().longBits(X_POSITION));
            assertEquals(0, h.slot().word(X_VELOCITY));
        }
    }

    @Test void sevenReservedAndOutsideSupportStopsAtExistingMovementReadBeforePositionWrites() {
        for (int link : new int[]{1, 31, 128, 255}) {
            var h = new Harness(); h.main(0x70); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(FLAGS, 8)
                    .moveByte(STAND_LINK_3D, link).moveWord(Y_VELOCITY, 0x4321)); var before = h.slot();
            assertThrows(CdPlayerMovement.UnsupportedMotion.class, h::run); assertEquals(before, h.slot());
            assertTrue(h.calls.isEmpty()); assertTrue(h.angles.isEmpty()); assertTrue(h.drawSeen.isEmpty());
        }
    }

    @Test void sevenNegativeCeilingUsesRawAngleAndCorrectsWordThenAnchorSubstateAndFourX() {
        var t = new Terrain(); t.row(0, 0x4001); t.angles[1] = 3; Arrays.fill(t.heights, 16, 32, (byte) -9);
        var h = new Harness(assets(), t.data()); h.main(0x70); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2)); h.run();
        var call = h.angles.getFirst(); assertEquals(0x12, call.expectedPrimaryAngle()); assertEquals(3, call.value());
        assertEquals(0xFFF8, call.expectedSource().word(Y_VELOCITY)); assertEquals(Y0, call.expectedSource().centreY());
        assertEquals(Y0 + 1, h.slot().centreY()); assertEquals(Y0 + 1, h.slot().word(PLATFORM_ANCHOR_Y));
        assertEquals(4, h.slot().unsignedByte(PLATFORM_SUBSTATE)); assertEquals(3, h.solidGlobals.primaryAngle());
        var returned = h.last.effects().stream().filter(CdPlatform.CeilingReturned.class::isInstance).map(CdPlatform.CeilingReturned.class::cast).findFirst().orElseThrow().result();
        assertEquals(-1, returned.distance()); assertEquals(3, returned.primaryAngle()); assertEquals(0x80, returned.angleResult());
        assertEquals(List.of(SUBTYPE_7_STATIONARY_2_TOP), h.sites());
    }

    @Test void sevenNoCeilingAngleWritePreservesOddInitialBufferAndOnlyNormalizesReturnRegister() {
        var h = new Harness(); h.main(0x70); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2));
        h.solidGlobals = new CdSolidObject.Globals(0, 4, 0x13, 0x34); h.run(); assertTrue(h.angles.isEmpty());
        var returned = h.last.effects().stream().filter(CdPlatform.CeilingReturned.class::isInstance).map(CdPlatform.CeilingReturned.class::cast).findFirst().orElseThrow().result();
        assertEquals(0x13, returned.primaryAngle()); assertEquals(0x80, returned.angleResult()); assertEquals(0x13, h.solidGlobals.primaryAngle());
    }

    @Test void ceilingLaterUnavailableReadKeepsMovedPrefixAndRawAngleWithoutTopOrDraw() {
        var t = new Terrain(); t.angles[1] = 3;
        // Layout admits valid chunk2; bit6 selects actual alternate chunk3 only at the probe.
        Arrays.fill(t.fg, 2, t.fg.length, (byte) 0x82);
        for (int x = 0; x < 16; x++) {
            word(t.chunks, 1024 + x * 2, 0x4001); // $307 writes angle3; metric16 visits $317 (negative a3 is negated).
            word(t.chunks, 1024 + (1 * 16 + x) * 2, 0x4400); // Reached collision index1024 is unowned.
        }
        Arrays.fill(t.heights, 16, 32, (byte) -16);
        var h = new Harness(assets(), t.data()); h.main(0x70);
        h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2).moveByte(SPRITE_FLAGS, 0x44));
        var failure = assertThrows(IllegalStateException.class, h::run);
        assertEquals("Collision ID requires unavailable resident bytes: 1024", failure.getMessage());
        assertEquals(3, h.solidGlobals.primaryAngle());
        assertEquals(0xFFF8, h.slot().word(Y_VELOCITY)); assertEquals(2, h.slot().unsignedByte(PLATFORM_SUBSTATE));
        assertEquals(Y0, h.slot().centreY()); assertEquals(1, h.angles.size()); assertTrue(h.calls.isEmpty()); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void angleHostFailureRethrowsIdenticalExceptionAndPoolPerturbationIsNotOverwritten() {
        var t = new Terrain(); t.row(0, 0x4001); Arrays.fill(t.heights, 16, 32, (byte) -9);
        var h = new Harness(assets(), t.data()); h.main(0x70); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2));
        var stop = new IllegalStateException("host angle"); h.afterAngle = call -> { throw stop; };
        assertSame(stop, assertThrows(IllegalStateException.class, h::run)); assertEquals(0xFFF8, h.slot().word(Y_VELOCITY));
        assertEquals(Y0, h.slot().centreY()); assertTrue(h.calls.isEmpty()); assertTrue(h.drawSeen.isEmpty());
        var changed = new Harness(assets(), t.data()); changed.main(0x70); changed.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2));
        changed.afterAngle = call -> changed.pool().write(changed.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID, 0x51));
        assertThrows(IllegalStateException.class, changed::run); assertEquals(0x51, changed.pool().state(91).unsignedByte(COLLAPSE_SAVED_SOURCE_ID));
        assertTrue(changed.calls.isEmpty()); assertTrue(changed.drawSeen.isEmpty());
    }

    @Test void angleCallsCarryActualFreshCutsAndNoopIsCaughtAtNextExpectedGlobalsJoin() {
        var t = new Terrain(); t.row(0, 0x4801); t.angles[1] = 3; Arrays.fill(t.heights, 16, 32, (byte) -9);
        var h = new Harness(assets(), t.data()); h.main(0x70); h.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2)); h.run();
        assertEquals(List.of(3, 0xFD), h.angles.stream().map(CdPlatform.PrimaryAngleCall::value).toList());
        assertEquals(3, h.angles.get(1).expectedPrimaryAngle()); assertNotSame(h.angles.getFirst().actualCut(), h.angles.get(1).actualCut());
        for (var call : h.angles) assertEquals(call.expectedSource(), call.actualCut().slots().get(WHOLE - 32));
        var noop = new Harness(assets(), t.data()); noop.main(0x70); noop.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2)); noop.publishAngle = false;
        assertThrows(IllegalStateException.class, noop::run); assertTrue(noop.drawSeen.isEmpty());
        var source = h.angles.getFirst();
        assertThrows(IllegalArgumentException.class, () -> new CdPlatform.PrimaryAngleCall(source.actualCut(), source.expectedSource().moveByte(ID, 0), source.expectedPrimaryAngle(), source.value()));
    }

    @Test void stoppedSolidStandReleaseCommitsActualPrefixThenThrowsSameOriginalBeforeSagOrTimer() {
        for (int address : new int[]{1, 31, 128, 255}) {
            var h = new Harness(); h.main(0x50); h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x84));
            var p = h.contact(Y0); p.flags = 8; p.stand = address; p.yv = 0; h.player = p.player();
            var ex = assertThrows(RuntimeException.class, h::run); var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, h.calls.getFirst().outcome());
            assertSame(stopped.stop().originalException(), ex); assertEquals(stopped.prefix().object(), h.slot());
            assertEquals(stopped.prefix().player(), h.player); assertEquals(stopped.prefix().globals(), h.solidGlobals);
            assertEquals(0, h.slot().unsignedByte(PLATFORM_SAG)); assertEquals(0, h.slot().unsignedByte(PLATFORM_TIMER));
            assertEquals(0, h.slot().unsignedByte(PLATFORM_SUBSTATE)); assertEquals(1, h.calls.size()); assertTrue(h.drawSeen.isEmpty());
        }
    }

    @Test void realSolidSecondFloorFailurePublishesFirstAnglePrefixAndStopsBeforeSagDrawTail() {
        var t = new Terrain(); t.fg[2 + 2 * 42 + 15] = (byte) 0xF9; t.row(15, 0x2001); t.angles[1] = 3;
        Arrays.fill(t.heights, 16, 32, (byte) 1);
        var h = new Harness(assets(), t.data()); h.main(0x40); h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x84).moveByte(Y_RADIUS, 34).moveWord(X, 0x1000));
        var p = h.contact(Y0); p.x = 0x10008000; p.spr = 0x44; h.player = p.player();
        var ex = assertThrows(RuntimeException.class, h::run); var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, h.calls.getFirst().outcome());
        assertSame(stopped.stop().originalException(), ex); assertEquals(CdSolidObject.StopSite.FLOOR_SECONDARY, stopped.stop().site());
        assertEquals(3, stopped.prefix().globals().primaryAngle()); assertEquals(0x34, stopped.prefix().globals().secondaryAngle());
        assertEquals(Set.of(CdSolidObject.GlobalField.PRIMARY_ANGLE), stopped.prefix().globalWrites());
        assertEquals(stopped.prefix().object(), h.slot()); assertEquals(0, h.slot().unsignedByte(PLATFORM_SAG)); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void realTopCarriesFullPlayerFractionAndDynamicStandReleasePreservesFullOtherImage() {
        var h = new Harness(); h.main(0x40); h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x84).moveWord(X_VELOCITY, 1));
        var other = dirty(h.pool().state(91)).moveByte(ID, 0).moveByte(FLAGS, 0xA9); h.pool().write(other);
        var p = h.contact(Y0); p.x = (X0 << 16) | 0xFFFF; p.flags = 8; p.stand = 91; p.yv = 0; h.player = p.player(); h.run();
        var result = completed(h.calls.getFirst()); assertEquals(p.x + 0x100, result.player().actor().motion().centreX16_16());
        assertEquals(List.of(other.moveByte(FLAGS, 0xA1)), result.otherSlots()); assertEquals(other.moveByte(FLAGS, 0xA1), h.pool().state(91));
        assertEquals(WHOLE, h.player.standObject());
    }

    @Test void genuineGetOffDefinesNoD0AndOnlySharedOutcomeRequestsFm() {
        var h = new Harness(); h.main(0x40); h.edit(o -> o.moveByte(FLAGS, 8).moveWord(PLATFORM_ANCHOR_X, 0x7000));
        var p = new P(); p.flags = 8; p.stand = WHOLE; p.charge = 5; h.player = p.player(); h.run();
        assertEquals(List.of(SUBTYPE_4_TOP, ANCHOR_GET_OFF), h.sites());
        assertTrue(completed(h.calls.get(1)).d0().isEmpty()); assertEquals(1, h.sharedFm.size());
        assertEquals(5, h.player.actor().motion().charge());
    }

    @Test void omittedPublicationAndUnexpectedOtherPoolWriteFailBeforeLaterSagOrDraw() {
        var missing = new Harness(); missing.main(0x40); missing.edit(o -> o.moveByte(SOLID_TYPE, 0x7F).moveByte(PLATFORM_SAG, 3));
        missing.publishSolid = false; assertThrows(IllegalStateException.class, missing::run);
        assertEquals(0x7F, missing.slot().unsignedByte(SOLID_TYPE)); assertEquals(3, missing.slot().unsignedByte(PLATFORM_SAG)); assertTrue(missing.drawSeen.isEmpty());
        var dirtyTail = new Harness(); dirtyTail.main(0x40);
        dirtyTail.afterSolid = call -> dirtyTail.pool().write(dirtyTail.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID, 0xA5));
        assertThrows(IllegalStateException.class, dirtyTail::run); assertEquals(0xA5, dirtyTail.pool().state(91).unsignedByte(COLLAPSE_SAVED_SOURCE_ID));
        assertTrue(dirtyTail.drawSeen.isEmpty());
    }

    @Test void contractHarnessRejectsForeignCutStaleExpectedGlobalsAndWrongSiteEntry() {
        var h = new Harness(); h.main(0x40); h.run(); var call = h.calls.getFirst();
        var foreign = new CdObjectPool();
        var forgedCut = new CdPlatform.SolidCall(call.site(), call.entry(), foreign.capture(), call.expectedPlayer(), call.expectedGlobals(), call.outcome());
        assertThrows(IllegalArgumentException.class, () -> h.commit(forgedCut));
        var stale = new CdPlatform.SolidCall(call.site(), call.entry(), call.actualCut(), call.expectedPlayer(), new CdSolidObject.Globals(0, 0, 0, 0), call.outcome());
        assertThrows(IllegalStateException.class, () -> h.commit(stale));
        assertThrows(IllegalArgumentException.class, () -> new CdPlatform.SolidCall(ANCHOR_GET_OFF, CdSolidObject.Entry.TOP_SOLID_OBJECT,
                call.actualCut(), call.expectedPlayer(), call.expectedGlobals(), call.outcome()));
    }

    @Test void sourceCutCheckIncludesUntouchedIdZeroDirtyTailAndHostExceptionIsNotNativeStopped() {
        var h = new Harness(); h.main(0x40); var stop = new IllegalArgumentException("host commit");
        h.beforeSolid = call -> { throw stop; };
        assertSame(stop, assertThrows(IllegalArgumentException.class, h::run)); assertTrue(h.calls.isEmpty()); assertTrue(h.drawSeen.isEmpty());
        var stale = new Harness(); stale.main(0x40);
        stale.beforeSolid = call -> stale.pool().write(stale.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID, 0x5A));
        assertThrows(IllegalStateException.class, stale::run); assertTrue(stale.calls.isEmpty()); assertTrue(stale.drawSeen.isEmpty());
    }

    @Test void actualDrawClearsVisibilityAndResultReloadsCallbackWritesIncludingAfterDeletion() {
        var h = new Harness(); h.afterDraw = (pool, identity) -> pool.write(pool.state(identity.wholeIndex()).moveByte(COLLISION_STATUS, 0x71));
        var r = h.run(); assertEquals(0x71, r.state().unsignedByte(COLLISION_STATUS)); assertEquals(0, r.state().spriteFlags() & 0x80);
        var dead = new Harness(); dead.fillPool(); dead.edit(o -> o.moveByte(SUBTYPE2, 1));
        dead.afterDraw = (pool, identity) -> pool.write(pool.state(identity.wholeIndex()).moveByte(COLLISION_STATUS, 0x72));
        var deleted = dead.run(); assertTrue(deleted.deleted()); assertEquals(0, deleted.state().id()); assertEquals(0x72, deleted.state().unsignedByte(COLLISION_STATUS));
        assertArrayEquals(new byte[64], dead.drawSeen.getFirst().copyBytes());
    }

    @Test void drawHostFailureRetainsOnlyCompletedBodyAndActualDrawPrefix() {
        var h = new Harness(); var stop = new IllegalStateException("host draw");
        h.afterDraw = (pool, identity) -> { pool.write(pool.state(identity.wholeIndex()).moveByte(COLLISION_STATUS, 0x73)); throw stop; };
        assertSame(stop, assertThrows(IllegalStateException.class, h::run)); assertEquals(2, h.slot().routine()); assertEquals(0x73, h.slot().unsignedByte(COLLISION_STATUS));
        assertTrue(h.calls.isEmpty());
    }

    @Test void reachedInputFailureDoesNotRollBackPhaseAndYBeforeTop() {
        var h = new Harness(); h.main(0); var before = h.slot();
        assertThrows(NullPointerException.class, () -> h.body.run(WHOLE, world(), null));
        byte[] e = before.copyBytes(); e[0x2A] = 1; word(e, 0x0C, Y0 + 2); assertArrayEquals(e, h.slot().copyBytes());
        assertTrue(h.drawSeen.isEmpty());
    }

    @Test void transientGuardRejectsSolidAngleAndDrawReentryAndEffectsCannotReplay() {
        var h = new Harness(); h.main(0x40);
        h.beforeSolid = call -> assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world(), h.inputs()));
        h.afterDraw = (pool, identity) -> assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world(), h.inputs()));
        var r = h.run(); assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
        assertEquals(1, h.calls.size()); assertEquals(1, h.drawSeen.size());
        var t = new Terrain(); t.row(0, 0x4001); Arrays.fill(t.heights, 16, 32, (byte) -9);
        var angle = new Harness(assets(), t.data()); angle.main(0x70); angle.edit(o -> o.moveByte(PLATFORM_SUBSTATE, 2));
        angle.beforeAngle = call -> assertThrows(IllegalStateException.class, () -> angle.body.run(WHOLE, world(), angle.inputs()));
        angle.run(); assertEquals(1, angle.angles.size());
    }

    @Test void globalsValidateOnlyWidthsWrongIdIsRejectedAndConstructedAssetsGrantNoAuthority() {
        assertDoesNotThrow(() -> world(0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFF));
        assertThrows(IllegalArgumentException.class, () -> world(0x10000, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> world(0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> world(0, 0, 0x10000, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> world(0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> world(0, 0, 0, 0, 256));
        assertThrows(NullPointerException.class, () -> CdPlatform.Assets.fromObjectAssets(null));
        var h = new Harness(); h.edit(o -> o.moveByte(ID, 0x20)); var before = h.pool().capture();
        assertThrows(IllegalArgumentException.class, h::run); assertPool(before, h.pool());
    }

    private static CdPlatform.Globals world() { return world(0, 0, X0 - 128, Y0 - 128, 1); }
    private static CdPlatform.Globals world(int z, int stop, int x, int y, int tz) { return new CdPlatform.Globals(z, stop, x, y, tz); }
    private static byte[] initHeader(CdObjectState before) {
        byte[] e = before.copyBytes(); e[1] |= 4; word(e, 2, 0x44BE); e[0x18] = 2;
        word(e, 0x38, before.centreX()); word(e, 0x3A, before.centreY()); word(e, 0x36, before.centreY()); putLong(e, 4, MAP); return e;
    }
    private static byte[] initExpected(CdObjectState before, int subtype) {
        byte[] e = initHeader(before); e[0x1A] = (byte) (subtype & 3); e[0x19] = WIDTHS[subtype & 3]; e[0x16] = 8;
        e[0x2D] = RANGES[(subtype >>> 2) & 3]; e[0x24] += 2; return e;
    }
    private static CdSolidObject.Result completed(CdPlatform.SolidCall call) { return assertInstanceOf(CdSolidObject.Completed.class, call.outcome()).completedResult(); }
    private static CdPlatform.Assets assets() { return assets(WIDTHS, RANGES); }
    private static CdPlatform.Assets assets(byte[] widths, byte[] ranges) { return new CdPlatform.Assets(visual(), MAP, CdAssetBytes.copyOf(widths), CdAssetBytes.copyOf(ranges)); }
    private static CdObjectVisualAssets visual() {
        byte[] bytes = {0,6,0,6,0,6,0,0}; return CdObjectVisualAssets.decodeStatic(bytes, 3, 57);
    }
    private static CdObjectState dirty(CdObjectState s) {
        for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, 0x53 + 7 * f.byteOffset());
        for (var f : CdObjectState.WordField.values()) s = s.moveWord(f, 0xA153 + 0x103 * f.byteOffset());
        for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0xA1536729 + 0x01030507 * f.byteOffset());
        return s;
    }
    private static void assertPool(CdObjectPool.Snapshot cut, CdObjectPool pool) { for (var s : cut.slots()) assertEquals(s, pool.state(s.identity().wholeIndex())); }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void putLong(byte[] bytes, int at, int value) { word(bytes, at, value >>> 16); word(bytes, at + 2, value); }
    private static CdObjectPlacements layout() {
        var b = ByteBuffer.allocate(26); b.putShort((short) 12).putShort((short) 20).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        b.putShort((short) 0xFFFF).putInt(0).putShort((short) 0); b.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }

    private static final class Harness {
        final CdObjectPlacementRuntime placement = new CdObjectPlacementRuntime(layout());
        final List<CdPlatform.SolidCall> calls = new ArrayList<>(); final List<CdPlatform.PrimaryAngleCall> angles = new ArrayList<>();
        final List<CdObjectState> drawSeen = new ArrayList<>(); final List<CdPlayerTouchResponse.FmSound> sharedFm = new ArrayList<>();
        final CdPlatform body; final CdObjectDrawQueue queue; CdPlatform.Result last;
        CdSolidObject.Player player = new P().player(); CdSolidObject.Gates gates = new CdSolidObject.Gates(0, 0);
        CdSolidObject.Globals solidGlobals = new CdSolidObject.Globals(0, 4, 0x12, 0x34);
        Consumer<CdPlatform.SolidCall> beforeSolid = call -> {}, afterSolid = call -> {};
        Consumer<CdPlatform.PrimaryAngleCall> beforeAngle = call -> {}, afterAngle = call -> {};
        CdPlatform.DrawObjectCall afterDraw = (pool, identity) -> {};
        boolean publishSolid = true, publishAngle = true;
        Harness() { this(assets(), DEFAULT_DATA); }
        Harness(CdPlatform.Assets assets, Data data) {
            edit(o -> o.moveByte(ID, 0x21).moveWord(X, X0).moveWord(Y, Y0).moveWord(X_FRACTION, 0x1234).moveWord(Y_FRACTION, 0x5678));
            var mappings = CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(MAP, assets.visual().mappings(), 0, 3)), List.of());
            queue = new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
                public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                    var s = pool().state(ref.wholeIndex()); return new CdObjectDrawQueue.LiveDrawSlot(s.id(), s.spriteFlags(), s.centreX(), s.centreY(), s.word(X_FRACTION), s.longBits(MAPPINGS), s.unsignedByte(MAP_FRAME), s.word(TILE));
                }
                public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
                    var s = pool().state(ref.wholeIndex()); pool().write(s.moveByte(SPRITE_FLAGS, value ? s.spriteFlags() | 0x80 : s.spriteFlags() & 0x7F));
                }
            }, mappings, new byte[640]); queue.beginPass();
            body = new CdPlatform(placement, assets, data.math(), data.terrain(), data.solid(), this::commit, this::angle, (pool, identity) -> {
                var s = pool.state(identity.wholeIndex()); drawSeen.add(s);
                queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(identity.wholeIndex()), s.spriteFlags(), s.centreX(), s.centreY(), s.unsignedByte(Y_RADIUS), s.unsignedByte(WIDTH), s.unsignedByte(PRIORITY)), camera());
                afterDraw.drawObject(pool, identity);
            });
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> change) { pool().write(change.apply(slot())); }
        void main(int subtype) { edit(o -> o.moveByte(ROUTINE, 2).moveByte(SUBTYPE, subtype).moveByte(SPRITE_FLAGS, 4)
                .moveWord(TILE, 0x44BE).moveLong(MAPPINGS, MAP).moveByte(PRIORITY, 2).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 8)
                .moveWord(PLATFORM_ANCHOR_X, X0).moveWord(PLATFORM_ANCHOR_Y, Y0).moveByte(PLATFORM_RANGE, 2)); }
        void fillPool() { for (int whole = 32; whole < 128; whole++) if (whole != WHOLE) pool().write(pool().state(whole).moveByte(ID, 0x7F)); }
        P contact(int objectY) { var p = new P(); p.y = ((objectY - slot().signedByte(Y_RADIUS) - 19) << 16) | 0x1234; return p; }
        void contactAt(int objectY) { player = contact(objectY).player(); }
        CdPlatform.Inputs inputs() { return new CdPlatform.Inputs(player, gates, solidGlobals); }
        CdPlatform.Result run() { return run(world()); }
        CdPlatform.Result run(CdPlatform.Globals globals) { last = body.run(WHOLE, globals, inputs()); return last; }
        List<CdPlatform.Site> sites() { return calls.stream().map(CdPlatform.SolidCall::site).toList(); }
        CdObjectDrawQueue.CameraWords camera() { return new CdObjectDrawQueue.CameraWords(X0 - 128, Y0 - 128); }
        CdObjectDrawQueue.CpuSat finishDraw() { return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera(), camera(), camera())); }
        void commit(CdPlatform.SolidCall call) {
            beforeSolid.accept(call); pool().validateRestore(call.actualCut());
            if (!player.equals(call.expectedPlayer()) || !solidGlobals.equals(call.expectedGlobals())) throw new IllegalStateException("stale expected Frame values");
            assertLive(call.actualCut());
            CdObjectState object; List<CdObjectState> others; CdSolidObject.Player next; CdSolidObject.Globals nextGlobals; List<CdPlayerTouchResponse.FmSound> sounds;
            if (call.outcome() instanceof CdSolidObject.Completed done) {
                var r = done.completedResult(); object = r.object(); others = r.otherSlots(); next = r.player(); nextGlobals = r.globals(); sounds = r.fmRequests();
            } else {
                var p = ((CdSolidObject.NativeStopped) call.outcome()).prefix(); object = p.object(); others = p.otherSlots(); next = p.player(); nextGlobals = p.globals(); sounds = List.of();
            }
            pool().validateWrite(object); others.forEach(pool()::validateWrite); calls.add(call);
            if (publishSolid) { pool().write(object); others.forEach(pool()::write); player = next; solidGlobals = nextGlobals; sharedFm.addAll(sounds); }
            afterSolid.accept(call);
        }
        void angle(CdPlatform.PrimaryAngleCall call) {
            beforeAngle.accept(call); pool().validateRestore(call.actualCut()); assertLive(call.actualCut());
            if (!slot().equals(call.expectedSource()) || solidGlobals.primaryAngle() != call.expectedPrimaryAngle()) throw new IllegalStateException("stale angle source/expected global");
            angles.add(call);
            if (publishAngle) solidGlobals = new CdSolidObject.Globals(solidGlobals.invincible(), solidGlobals.scoreChain(), call.value(), solidGlobals.secondaryAngle());
            afterAngle.accept(call);
        }
        void assertLive(CdObjectPool.Snapshot cut) { for (var s : cut.slots()) if (!s.equals(pool().state(s.identity().wholeIndex()))) throw new IllegalStateException("stale full pool"); }
    }

    private static final class P {
        int x = (X0 << 16) | 0x8000, y = 0x01001234, xv, yv = 0x100, gv, routine = 2, angle = 0x10, flags = 2;
        int spr = 4, xr = 9, yr = 19, anim, prev, jump = 1, charge, lock, stick, ctrl, pri = 0x56, sec = 0x78;
        int tile = 0x780, priority = 2, hurt, rot, id = 1, push, stand;
        CdSolidObject.Player player() { return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x, y, xv, yv, gv,
                routine, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec), tile, priority, hurt, rot), id, push, stand); }
    }
    private record Data(CdPlayerMath math, CdPlayerTerrain terrain, CdSolidObject solid) { }
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() { fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2); ids[1] = 1; ids[2] = 2; }
        void row(int y, int value) { for (int x = 0; x < 16; x++) word(chunks, 512 + (y * 16 + x) * 2, value); }
        Data data() {
            var raw = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578]; for (int i = 0; i < 60; i++) word(animations, i * 2, 120); animations[122] = (byte) 0xFF;
            byte[] maps = new byte[398], dplcs = new byte[398]; for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]); byte[] atan = new byte[257], sine = new byte[640];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            // Constructed scalar data, not a claim of retail sine-table authentication.
            for (int i = 0; i < 320; i++) word(sine, 2 * i, switch (i & 255) { case 1 -> 16; case 2 -> 32; case 0x3E -> 255; case 0x3F, 0x40 -> 256; default -> 0; });
            var player = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6,7,8,0x49,0x4C,(byte) 0xFF}, new byte[4], sine, atan, visual);
            return new Data(new CdPlayerMath(player), new CdPlayerTerrain(raw), new CdSolidObject(new CdPlayerMovement(raw, player)));
        }
    }
}
