package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdSpinningDisc.DiscPlayerField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests FIRST, source-only. Compilation, discovery, RED and every execution are UNRUN.
 * J 64776b7822f4059312988a9856d8a842855fbf68; N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Native Disc/Main:7-223, Object Solid:15-146,188-479, Object Animate:14-95,
 * Functions (Misc):69-101, Object Spawner:385-446, Object Functions:137-196,228-237.
 * Detached literal oracles; real plain Solid, immutable format assets, pool, animator and CPU draw queue.
 * No forged d0/native return, private snapshot, Frame owner, authentication or route witness.
 * The harness's immutable live player pair is a component port, NOT private Runtime/Frame authority.
 * Whole-owner preflight atomicity, exact-once/no-op consumption and failure closure remain Root obligations.
 * No engine/native/media/GL execution or controlled-Body admission is claimed.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdSpinningDisc {
    static final int WHOLE = 40, MAP = 0x812095C6;
    static final String TILES = "002400260028002c002e0030003a0034003800360032003c003e00400042004400460048"
            + "23a023b024092370200024284334032000004000400004090374832800000000049003e40000";
    static final String MAP_BYTES = "00060012001e02f8050000f0f8050800000002f8050000f0f8050800000001f80d0004f0";
    static final String ANI = "000201000102ff00", POSES = "00000001010202020303030404050505";

    @Test void exactFiniteApiAndWidthsAreNotRamOffsetsOrCapabilities() {
        assertEquals(33, CdSpinningDisc.DiscPlayerField.values().length);
        assertEquals(9, CdSpinningDisc.StoreKind.values().length);
        assertEquals(0xA0, CdSpinningDisc.FmSound.FM_A0.nativeId());
        assertEquals(8, CONTROL_ACQUIRE.widthBits()); assertEquals(16, LOGICAL_CONTROLS.widthBits());
        assertEquals(CdSpinningDisc.StoreKind.BSET_BYTE, FLAGS_AIR.kind());
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerStore(FLAGS_AIR, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerStore(CONTROL_CLEAR, 255, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerStore(ROTATION_ADVANCE, 255, 8));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerStore(SCRIPT_INDEX, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerStore(ORBIT_X_BASE, 0, 65536));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.Globals(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.Globals(0, 65536, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.Globals(0, 0, 256, 0));
        assertDoesNotThrow(() -> new CdSpinningDisc.Globals(65535, 65535, 255, 255));
    }

    @Test void initOverwritesSpriteAndOnlyNativeDirtyBytesThenSkipsSolidOnFirstMain() {
        var h = ready(); h.edit(s -> dirty(s).moveByte(ID, 9).moveByte(ROUTINE, 0).moveByte(SUBTYPE2, 0)
                .moveByte(SAVED_FLAGS_ID, 0).moveLong(X_POSITION, 0x0100ABCD).moveLong(Y_POSITION, 0x0080CAFE));
        byte[] expected = h.slot().copyBytes(); b(expected, 0x24, 2); b(expected, 1, 4); b(expected, 0x18, 4);
        l(expected, 4, MAP); w(expected, 2, 0x0409); b(expected, 0x19, 16); b(expected, 0x16, 8);
        var beforePlayer = h.live; var beforeOther = h.pool().state(42); var r = h.run();
        assertArrayEquals(expected, r.state().copyBytes()); assertEquals(beforePlayer, h.live);
        assertEquals(beforeOther, h.pool().state(42)); assertTrue(h.solids.isEmpty()); assertTrue(h.players.isEmpty());
        assertEquals(1, calls(r, CdSpinningDisc.NativeCall.SET_OBJECT_TILE_ID));
        assertEquals(1, calls(r, CdSpinningDisc.NativeCall.DRAW_OBJECT));
    }

    @ParameterizedTest @ValueSource(ints = {1, 127, 255})
    void secondaryTileStopRetainsInitPrefixBeforeWidthAndRadius(int subtype) {
        var h = ready(); h.edit(s -> s.moveByte(ROUTINE, 0).moveByte(SPRITE_FLAGS, 0xFF).moveByte(SUBTYPE2, subtype)
                .moveWord(TILE, 0xBEEF).moveByte(WIDTH, 0xA5).moveByte(Y_RADIUS, 0xB6));
        byte[] expected = h.slot().copyBytes(); b(expected, 0x24, 2); b(expected, 1, 4); b(expected, 0x18, 4); l(expected, 4, MAP);
        var e = assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class, h::run);
        assertEquals(CdSpinningDisc.UnsupportedBoundary.TILE_ENTRY, e.boundary());
        assertArrayEquals(expected, h.slot().copyBytes()); assertTrue(h.draws.isEmpty()); assertTrue(h.solids.isEmpty());
    }

    @Test void malformedConstructedTileOperandFailsOnlyAfterInitPrefix() {
        var a = assets(); var h = new Harness(new Terrain(), new CdSpinningDisc.Assets(CdAssetBytes.copyOf(new byte[0]), a.visual(), MAP, a.playerFrames()));
        h.prepare(); h.edit(s -> s.moveByte(ROUTINE, 0).moveWord(TILE, 0xBEEF));
        assertEquals(CdSpinningDisc.UnsupportedBoundary.TILE_ADDRESS,
                assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class, h::run).boundary());
        assertEquals(2, h.slot().routine()); assertEquals(4, h.slot().spriteFlags()); assertEquals(MAP, h.slot().longBits(MAPPINGS));
        assertEquals(0xBEEF, h.slot().word(TILE)); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 4, 6, 128, 255})
    void rawUnownedRoutineDoesNotRunMainOrWrapper(int routine) {
        var h = ready(); h.edit(s -> s.moveByte(ROUTINE, routine)); var before = h.pool().capture();
        assertEquals(CdSpinningDisc.UnsupportedBoundary.ROUTINE_DISPATCH,
                assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class, h::run).boundary());
        assertEquals(before.slots(), h.pool().capture().slots()); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0, 4, 127})
    void positiveSpriteNeverReadsPlayerOrCallsSolid(int sprite) {
        var h = ready(); h.edit(s -> s.moveByte(SPRITE_FLAGS, sprite)); h.failRead = true;
        h.run(); assertEquals(0, h.reads); assertTrue(h.solids.isEmpty()); assertEquals(1, h.draws.size());
    }

    @Test void realPlainSolidBelowDiscReturnsZeroWithoutAttachmentOrDirectStores() {
        var h = ready(); h.seed.y = 0x009B1234; h.seed.yv = -0x400; h.install();
        h.run(); var r = completed(h); assertEquals(0, r.d0().orElseThrow());
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, r.entry());
        assertEquals(CdSolidObject.GetOffReason.SPINNING_DISC_BELOW, r.getOffReason().orElseThrow());
        assertTrue(h.players.isEmpty()); assertEquals(0, h.live.state().objectLinks().standObject());
        assertEquals(0, h.slot().unsignedByte(SOLID_TYPE));
    }

    @Test void realPlainSolidSideHasItsOwnZeroReturnAndDoesNotForceTopType() {
        var h = ready(); h.seed.x = 0x00E88000; h.seed.y = 0x00801234; h.seed.yv = 0; h.seed.flags = 0; h.install();
        h.edit(s -> s.moveByte(SOLID_TYPE, 0)); h.run(); var r = completed(h);
        assertEquals(CdSolidObject.Path.HALT_ON_X, r.path()); assertEquals(0, r.d0().orElseThrow());
        assertTrue(h.players.isEmpty()); assertEquals(0x00E68000, h.live.state().motion().centreX16_16());
        assertEquals(0, h.slot().unsignedByte(SOLID_TYPE));
    }

    @ParameterizedTest @CsvSource({"240,240,136", "272,271,8", "256,256,8"})
    void realLandingAcquiresWithOldControlBitUnsignedBorrowAndLowByteDistance(int x, int orbitX, int angle) {
        var h = ready(); h.seed.x = (x << 16) | 0xBEEF; h.seed.ctrl = 0xA4; h.seed.rot = 0xD7; h.install();
        h.run(); assertEquals(1, completed(h).d0().orElseThrow());
        assertEquals(0xA5, h.live.state().motion().playerControl()); assertEquals(0x2D, h.live.state().motion().animation());
        assertEquals(angle, h.live.state().rotationAngle());
        assertEquals((orbitX << 16) | 0xBEEF, h.live.state().motion().centreX16_16());
        assertEquals(16 == Math.abs(x - 256) ? 16 : 0, h.live.state().rotationDistance());
        assertEquals(WHOLE, h.live.state().objectLinks().standObject());
        assertEquals(List.of(CONTROL_ACQUIRE, ANIMATION_ACQUIRE, ROTATION_ZERO), h.fields().subList(0, 3));
        if (x < 256) assertTrue(h.fields().contains(ROTATION_LEFT));
        assertEquals(17, h.solids.getFirst().expectedPlayer().state().animation().scriptIndex());
        assertEquals(5, h.live.state().animation().mapFrame()); assertEquals(9, h.live.state().animation().timer());
    }

    @Test void realStandReleasesDirtyIdZeroPriorSlotAndPreservesOtherNinetyFourImages() {
        var h = ready(); h.seed.flags = 8; h.seed.stand = 41; h.install();
        h.pool().write(dirty(h.pool().state(41)).moveByte(ID, 0).moveByte(FLAGS, 0xAD));
        var before = h.pool().capture(); h.run(); var r = completed(h);
        assertEquals(1, r.otherSlots().size()); assertEquals(0xA5, h.pool().state(41).flags());
        for (int i = 32; i < 128; i++) if (i != WHOLE && i != 41) assertEquals(before.slots().get(i - 32), h.pool().state(i));
    }

    @Test void nativeStoppedFloorPublishesGenuinePrefixAndRethrowsIdenticalExceptionWithoutWrapper() {
        var terrain = new Terrain(); terrain.fg[3] = (byte) 0xF9;
        var h = new Harness(terrain, assets()); h.prepare(); h.seed.spr = 0x44; h.install();
        var e = assertThrows(RuntimeException.class, h::run);
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, h.solids.getFirst().outcome());
        assertSame(stopped.stop().originalException(), e); assertEquals(0x00651234, h.live.state().motion().centreY16_16());
        assertTrue(h.players.isEmpty()); assertTrue(h.fm.isEmpty()); assertTrue(h.draws.isEmpty());
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, stopped.prefix().entry());
        assertEquals(CdSolidObject.StopSite.FLOOR_PRIMARY, stopped.stop().site());
        var detail = assertInstanceOf(CdSolidObject.TerrainWord.class, stopped.stop().detail());
        assertEquals(0x21F2E0, detail.address().nativeAddress());
        assertEquals(1, detail.address().layoutOffset()); assertEquals(0xF9, detail.address().layoutByte());
        assertEquals(122, detail.address().effectiveChunkId());
        assertEquals(com.openggf.game.soniccd.level.CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS, detail.reason());
        assertEquals(0, h.slot().unsignedByte(SOLID_TYPE));
    }

    @Test void nativeStoppedPriorReservedLinkRetainsEarlierPlayerAndObjectStores() {
        var h = ready(); h.seed.flags = 8; h.seed.stand = 1; h.install();
        var e = assertThrows(CdSolidObject.UnsupportedSolidObject.class, h::run);
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, h.solids.getFirst().outcome());
        assertSame(stopped.stop().originalException(), e); assertEquals(CdSolidObject.StopSite.STAND_RELEASE, stopped.stop().site());
        assertEquals(8, h.slot().flags() & 8); assertEquals(0, h.live.state().motion().playerJump());
        assertEquals(1, h.live.state().objectLinks().standObject()); assertTrue(h.players.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,271,0", "120,240,3", "248,272,0", "56,256,1", "184,256,4", "80,247,2"})
    void detachedOrbitLiteralsUseCosineLogicalLongShiftAndPreserveFractions(int oldAngle, int expectedX, int script) {
        var h = standing(); h.seed.rot = oldAngle; h.seed.dist = 16; h.install(); h.run();
        assertEquals((expectedX << 16) | 0x8000, h.live.state().motion().centreX16_16());
        assertEquals(script, h.live.state().animation().scriptIndex()); assertEquals(5, h.live.state().animation().mapFrame());
        assertEquals(9, h.live.state().animation().timer()); assertEquals(0x00651234, h.live.state().motion().centreY16_16());
        assertFalse(h.fields().contains(ANIMATION_ACQUIRE));
    }

    @Test void allUnsignedDistanceBytesAtCardinalZeroWrapOnlyTheirOwnByte() {
        for (int distance = 0; distance < 256; distance++) {
            var h = standing(); h.seed.rot = 248; h.seed.dist = distance; h.install(); h.run();
            assertEquals(((256 + distance) << 16) | 0x8000, h.live.state().motion().centreX16_16(), "distance " + distance);
            assertEquals(distance == 255 ? 0 : distance + 1, h.live.state().rotationDistance());
            assertEquals(0, h.live.state().rotationAngle()); assertEquals(0, h.live.state().animation().scriptIndex());
            assertEquals(h.seed.stick, h.live.state().motion().stick());
        }
    }

    @Test void allRawAnglesAndControlBytesStayUnsignedAndRawLockIsNeverReinterpreted() {
        for (int value = 0; value < 256; value++) {
            var h = standing(); h.seed.rot = value; h.seed.ctrl = (value & 0xBF) | 1;
            h.seed.rawHeld = value; h.seed.rawTap = 0; h.seed.held = 0xE1; h.seed.tap = 0x80; h.seed.locked = 0xFF;
            h.install(); h.run(); var controls = h.live.environment().controls();
            assertEquals((value + 8) & 255, h.live.state().rotationAngle());
            assertEquals(value, controls.rawHeld()); assertEquals(value, controls.logicalHeld());
            assertEquals(0, controls.rawTap()); assertEquals(0, controls.logicalTap()); assertEquals(255, controls.controlLocked());
            assertEquals((value & 0xBF) | 1, h.live.state().motion().playerControl());
        }
    }

    @ParameterizedTest @CsvSource({"0,4,15", "0,8,17", "0,12,17", "120,4,18", "120,8,16", "120,12,18", "56,4,16", "56,8,18"})
    void directionsUsePostOrbitUnsignedSideRightTieAndOutwardBothHeldPrecedence(int rotation, int held, int distance) {
        var h = standing(); h.seed.rot = rotation; h.seed.dist = 16; h.seed.rawHeld = held; h.install(); h.run();
        assertEquals(distance, h.live.state().rotationDistance());
    }

    @Test void inwardZeroRecordsWrappedFfThenExplicitClampAndOutwardFfWraps() {
        var inward = standing(); inward.seed.dist = 0; inward.seed.rot = 0; inward.seed.rawHeld = 4; inward.install(); inward.run();
        var stores = inward.stores(); var decrement = stores.stream().filter(s -> s.field() == DISTANCE_SHRINK).findFirst().orElseThrow();
        assertEquals(0, decrement.expectedRaw()); assertEquals(255, decrement.value());
        var clamp = stores.stream().filter(s -> s.field() == DISTANCE_CLAMP).findFirst().orElseThrow();
        assertEquals(255, clamp.expectedRaw()); assertEquals(0, clamp.value());
        var outward = standing(); outward.seed.dist = 255; outward.seed.rawHeld = 8; outward.install(); outward.run();
        assertEquals(0, outward.live.state().rotationDistance());
    }

    @Test void reachedMissingPlayerFrameRetainsAngleAndBothXWordStoresWithoutLogicalCopy() {
        var a = assets(); var h = new Harness(new Terrain(), new CdSpinningDisc.Assets(a.objectTileIds(), a.visual(), MAP, CdAssetBytes.copyOf(new byte[0])));
        h.prepare(); h.seed.ctrl = 1; h.seed.rot = 0; h.seed.dist = 16; h.install();
        var e = assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class, h::run);
        assertEquals(CdSpinningDisc.UnsupportedBoundary.PLAYER_FRAME_ADDRESS, e.boundary()); assertEquals(8, h.live.state().rotationAngle());
        assertEquals(0x010F8000, h.live.state().motion().centreX16_16());
        assertTrue(h.fields().contains(ORBIT_X_BASE)); assertTrue(h.fields().contains(ORBIT_X_OFFSET));
        assertFalse(h.fields().contains(SCRIPT_INDEX)); assertFalse(h.fields().contains(LOGICAL_CONTROLS)); assertTrue(h.draws.isEmpty());
    }

    @Test void constructedFrameBytesAreReadAsUnsignedCursorValuesNotMappingFrames() {
        var a = assets(); byte[] poses = hex(POSES); poses[0] = (byte) 0xFF;
        var h = new Harness(new Terrain(), new CdSpinningDisc.Assets(a.objectTileIds(), a.visual(), MAP, CdAssetBytes.copyOf(poses)));
        h.prepare(); h.seed.ctrl = 1; h.seed.rot = 0; h.install(); h.run();
        assertEquals(255, h.live.state().animation().scriptIndex()); assertEquals(5, h.live.state().animation().mapFrame());
    }

    @ParameterizedTest @CsvSource({"0,96,-1664", "64,32,-896"})
    void jumpUsesObjectWaterAndFmA0BeforeAnyHitboxStore(int objectFlags, int playerFlags, int yVelocity) {
        var h = standing(); h.seed.rot = 0; h.seed.dist = 0x34; h.seed.xv = 0x7FF0; h.seed.yv = 0;
        h.seed.flags = playerFlags | 8; h.seed.stick = 0x12; h.seed.ctrl = 0xA5; h.seed.rawTap = 0x70; h.seed.y = 0x0065CAFE;
        h.install(); h.edit(s -> s.moveByte(FLAGS, objectFlags | 8)); h.run();
        var beforeFm = h.fmPlayers.getFirst().state(); var m = beforeFm.motion();
        assertEquals(0, m.playerControl()); assertEquals(0x7FF0, m.xVelocity()); assertEquals(yVelocity, m.yVelocity());
        assertEquals(0, m.stick()); assertEquals(1, m.playerJump()); assertEquals(0x34, beforeFm.rotationDistance());
        assertEquals(19, m.yRadius()); assertEquals(9, m.xRadius()); assertEquals(WHOLE, beforeFm.objectLinks().standObject());
        assertEquals(CdSpinningDisc.FmSound.FM_A0, h.fm.getFirst().sound());
        var after = h.live.state(); assertEquals(14, after.motion().yRadius()); assertEquals(7, after.motion().xRadius());
        assertEquals(0x006ACAFE, after.motion().centreY16_16()); assertEquals(2, after.motion().animation());
        assertEquals(0x2A, h.seed.charge); assertEquals(0x2A, after.motion().charge());
        assertEquals(0x567, after.motion().groundVelocity()); assertEquals(0, after.motion().angle());
    }

    @Test void miniatureKeepsDuplicateSameValueSizeSitesAndSkipsNormalYShift() {
        var h = standing(); h.seed.mini = 0x80; h.seed.rawTap = 0x10; h.install(); h.run();
        assertEquals(List.of(JUMP_Y_RADIUS_MINI, JUMP_X_RADIUS_MINI, ROLL_Y_RADIUS_MINI, ROLL_X_RADIUS_MINI, FLAGS_ROLL, ANIMATION_ROLL),
                h.fields().subList(h.fields().indexOf(JUMP_Y_RADIUS_MINI), h.fields().size()));
        var secondY = h.stores().stream().filter(s -> s.field() == ROLL_Y_RADIUS_MINI).findFirst().orElseThrow();
        assertEquals(10, secondY.expectedRaw()); assertEquals(10, secondY.value());
        assertEquals(0x00651234, h.live.state().motion().centreY16_16()); assertFalse(h.fields().contains(ROLL_Y_ADVANCE));
    }

    @Test void alreadyRollingSetsRollJumpAfterFirstSizeWithoutSecondSizeOrAnimationChange() {
        var h = standing(); h.seed.flags = 0x2C; h.seed.rawTap = 0x20; h.seed.anim = 0x2D; h.install(); h.run();
        assertTrue(h.fields().contains(FLAGS_ROLL_JUMP)); assertFalse(h.fields().contains(ANIMATION_ROLL));
        assertFalse(h.fields().contains(ROLL_Y_ADVANCE)); assertEquals(0x1E, h.live.state().motion().flags());
        assertEquals(0x2D, h.live.state().motion().animation()); assertEquals(19, h.live.state().motion().yRadius());
    }

    @Test void jumpTapMaskAndSignedVelocityWrapAreLiteralWordOperations() {
        var quiet = standing(); quiet.seed.rawTap = 0x8F; quiet.install(); quiet.run(); assertTrue(quiet.fm.isEmpty());
        var h = standing(); h.seed.rawTap = 0x40; h.seed.xv = 0x7FFF; h.seed.angle = 0x40; h.install(); h.run();
        assertEquals(-31105, h.live.state().motion().xVelocity()); // $7FFF + $0680 = $867F.
        assertEquals(0, h.live.state().motion().yVelocity()); assertEquals(0x40, h.live.state().motion().angle());
    }

    @Test void fmFailureRetainsPreFmPrefixAndSuppressesHitboxAnimatorDrawDespawn() {
        var h = standing(); h.seed.rawTap = 0x10; h.install(); h.failFm = true; h.timeStop = 0;
        assertSame(h.fault, assertThrows(Fault.class, h::run));
        assertEquals(0, h.live.state().motion().playerControl()); assertEquals(1, h.live.state().motion().playerJump());
        assertEquals(19, h.live.state().motion().yRadius()); assertFalse(h.fields().contains(JUMP_Y_RADIUS_NORMAL));
        assertEquals(0, h.slot().unsignedByte(ANIMATION_TIME)); assertTrue(h.draws.isEmpty());
    }

    @Test void failedDirectCommitRetainsEarlierPublishedSpansAndDoesNotAdoptRejectedResult() {
        var h = standing(); h.rejectField = SCRIPT_INDEX; var beforeCursor = h.live.state().animation();
        assertSame(h.fault, assertThrows(Fault.class, h::run));
        assertEquals(8, h.live.state().rotationAngle()); assertEquals(0x01138000, h.live.state().motion().centreX16_16());
        assertEquals(beforeCursor, h.live.state().animation()); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"READ", "PLAYER", "FM"})
    void nonwritingPoolContractRejectsOtherSlotMutationEvenWhenCallbackThrows(String port) {
        var h = standing(); h.seed.rawTap = 0x10; h.install(); h.attackPort = port; h.attackMutation = true; h.throwAfterAttack = true;
        assertThrows(IllegalStateException.class, h::run); assertEquals(0xA6, h.pool().state(42).id()); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"READ", "PLAYER", "SOLID", "FM", "DRAW"})
    void transientGuardRejectsSynchronousReentryAtEveryPort(String port) {
        var h = standing(); h.seed.rawTap = 0x10; h.install(); h.attackPort = port; h.reenter = true;
        assertThrows(IllegalStateException.class, h::run);
    }

    @Test void successfulPlayerPortMustActuallyAdoptWholePrebuiltPairAndFmCannotChangePlayer() {
        var noOp = standing(); noOp.noOpPlayer = true; assertThrows(IllegalStateException.class, noOp::run);
        var h = standing(); h.seed.rawTap = 0x10; h.install(); h.mutateFmPlayer = true;
        assertThrows(IllegalStateException.class, h::run); assertTrue(h.draws.isEmpty());
    }

    @Test void publicValidatorChecksOldRawValuesNonstoresAndCopiesMetadataDefensively() {
        var h = standing(); var expected = h.live; var same = new CdSpinningDisc.PlayerStore(CONTROL_ACQUIRE, 1, 1);
        var list = new ArrayList<CdSpinningDisc.PlayerStore>(); list.add(same);
        var result = new CdSpinningDisc.PlayerResult(expected.state(), expected.environment(), list); list.clear();
        assertEquals(1, result.stores().size()); assertThrows(UnsupportedOperationException.class, () -> result.stores().clear());
        assertDoesNotThrow(() -> CdSpinningDisc.validatePlayerResult(expected, result));
        assertDoesNotThrow(() -> CdSpinningDisc.validatePlayerResult(expected, result)); // Value validation grants NO exactly-once authority.
        var stale = new CdSpinningDisc.PlayerResult(expected.state(), expected.environment(), List.of(new CdSpinningDisc.PlayerStore(CONTROL_ACQUIRE, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> CdSpinningDisc.validatePlayerResult(expected, stale));
        var wrong = h.seed.copy(); wrong.charge = 0x2B;
        var nonstore = new CdSpinningDisc.PlayerResult(wrong.cut().state(), expected.environment(), List.of());
        assertThrows(IllegalArgumentException.class, () -> CdSpinningDisc.validatePlayerResult(expected, nonstore));
        var foreign = new CdObjectPool().capture();
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerCall(foreign, h.slot(), expected, result));
        var altered = CdObjectPool.overlay(h.pool().capture(), List.of(h.slot().moveByte(FLAGS, 0)));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinningDisc.PlayerCall(altered, h.slot(), expected, result));
    }

    @Test void finiteSharedAnimatorRunsOnceAndLoopsWithNativeTimerRatherThanFittedCycle() {
        var h = ready(); h.timeStop = 0; h.edit(s -> s.moveByte(SPRITE_FLAGS, 4));
        int[][] expected = {{0,1,1},{0,1,0},{1,2,1},{1,2,0},{2,3,1},{2,3,0},{0,1,1}};
        for (int[] v : expected) {
            var r = h.run(); assertEquals(v[0], h.slot().unsignedByte(MAP_FRAME)); assertEquals(v[1], h.slot().unsignedByte(ANIMATION_FRAME));
            assertEquals(v[2], h.slot().unsignedByte(ANIMATION_TIME)); assertEquals(1, calls(r, CdSpinningDisc.NativeCall.ANIMATE_OBJECT));
            h.finish(); h.queue.beginPass();
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 256, 32768, 65535})
    void timeStopWordSkipsOnlyAnimatorWhileMainDrawAndDespawnStillRun(int timer) {
        var h = standing(); h.timeStop = timer; var r = h.run();
        assertEquals(1, h.solids.size()); assertFalse(h.players.isEmpty()); assertEquals(1, h.draws.size());
        assertEquals(0, calls(r, CdSpinningDisc.NativeCall.ANIMATE_OBJECT)); assertEquals(1, calls(r, CdSpinningDisc.NativeCall.CHECK_OBJ_DESPAWN));
    }

    @Test void animationDirectoryAndSelectedFrameFaultsRetainReachedPrefixes() {
        var directory = ready(); directory.timeStop = 0; directory.edit(s -> s.moveByte(SPRITE_FLAGS, 4).moveByte(ANIMATION, 0xFF)
                .moveByte(PREVIOUS_ANIMATION, 0).moveByte(ANIMATION_FRAME, 0xA5).moveByte(ANIMATION_TIME, 0xB6));
        assertEquals(CdSpinningDisc.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY,
                assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class, directory::run).boundary());
        assertEquals(255, directory.slot().unsignedByte(PREVIOUS_ANIMATION)); assertEquals(0, directory.slot().unsignedByte(ANIMATION_FRAME));
        assertEquals(255, directory.slot().unsignedByte(ANIMATION_TIME)); assertTrue(directory.draws.isEmpty());
        var selected = ready(); selected.timeStop = 0; selected.edit(s -> s.moveByte(SPRITE_FLAGS, 4).moveByte(ANIMATION_FRAME, 255));
        assertEquals(CdSpinningDisc.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS,
                assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class, selected::run).boundary());
        assertEquals(1, selected.slot().unsignedByte(ANIMATION_TIME)); assertEquals(255, selected.slot().unsignedByte(ANIMATION_FRAME));
    }

    @Test void skippedAnimationDirectoryIsNotEagerlyReadAndCpuDrawSetsVisibilityOnlyAtDrain() {
        var h = ready(); h.timeStop = 0; h.edit(s -> s.moveByte(SPRITE_FLAGS, 4).moveByte(ANIMATION, 255)
                .moveByte(PREVIOUS_ANIMATION, 255).moveByte(ANIMATION_TIME, 1).moveByte(MAP_FRAME, 0));
        h.run(); assertEquals(4, h.slot().spriteFlags()); assertEquals(2, h.finish().spriteCount()); assertEquals(0x84, h.slot().spriteFlags());
    }

    @ParameterizedTest @CsvSource({"640,false", "768,true"})
    void wrapperReloadsActualDrawMutationBeforeUnsignedStrictDespawn(int x, boolean deleted) {
        var h = ready(); h.camera = 0x80; h.edit(s -> s.moveByte(SPRITE_FLAGS, 4));
        h.drawRewrite = s -> s.moveWord(X, x).moveByte(SPRITE_FLAGS, 4);
        assertEquals(deleted, h.run().deleted());
        if (deleted) { assertArrayEquals(new byte[64], h.slot().copyBytes()); assertEquals(0, h.finish().spriteCount()); }
    }

    @ParameterizedTest @CsvSource({"128,1,0", "128,255,1", "129,255,2", "255,128,2", "128,127,0"})
    void rawWarpDirectionUsesSignedWordAdjustmentAndClampOnlyWhenBitSevenIsSet(int time, int dir, int period) {
        var h = ready(); h.rawTime = time; h.direction = dir; h.edit(s -> s.moveByte(SAVED_FLAGS_ID, 1).moveByte(SPRITE_FLAGS, 4));
        h.placement.bsetSavedFlag(1, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        assertTrue(h.run().deleted()); assertEquals(0, h.placement.savedFlag(1, period));
    }

    @Test void rawSavedLinearAliasAndLastByteWorkAndFirstUnownedByteRetainsDrawWithoutDelete() {
        var alias = ready(); alias.rawTime = 127; alias.edit(s -> s.moveByte(SAVED_FLAGS_ID, 1).moveByte(SPRITE_FLAGS, 4));
        alias.placement.bsetSavedFlag(43, 1, CdObjectPlacementRuntime.SavedBit.LOADED); alias.drawRewrite = s -> s.moveWord(X, 0x1000);
        assertTrue(alias.run().deleted()); assertEquals(0, alias.placement.savedFlag(43, 1));
        var last = ready(); last.rawTime = 1; last.edit(s -> s.moveByte(SAVED_FLAGS_ID, 254).moveByte(SPRITE_FLAGS, 4));
        last.placement.bsetSavedFlag(254, 1, CdObjectPlacementRuntime.SavedBit.LOADED); last.drawRewrite = s -> s.moveWord(X, 0x1000);
        assertTrue(last.run().deleted()); assertEquals(0, last.placement.savedFlag(254, 1));
        var bad = ready(); bad.rawTime = 2; bad.edit(s -> s.moveByte(SAVED_FLAGS_ID, 254).moveByte(SPRITE_FLAGS, 4));
        bad.drawRewrite = s -> s.moveWord(X, 0x1000);
        assertEquals(CdSpinningDisc.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,
                assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class, bad::run).boundary());
        assertEquals(9, bad.slot().id()); assertEquals(1, bad.draws.size());
    }

    @Test void deletingDiscNeverAddsGetOffOrControlCleanupAndDrawFailureStopsTail() {
        var h = standing(); h.rawTime = 0x80; h.run(); assertEquals(0, h.slot().id());
        assertEquals(1, h.live.state().motion().playerControl()); assertEquals(WHOLE, h.live.state().objectLinks().standObject());
        var failure = ready(); failure.rawTime = 0x80; failure.failDraw = true;
        assertSame(failure.fault, assertThrows(Fault.class, failure::run)); assertEquals(9, failure.slot().id());
    }

    static long calls(CdSpinningDisc.Result r, CdSpinningDisc.NativeCall call) {
        return r.effects().stream().filter(CdSpinningDisc.CallReturned.class::isInstance).map(CdSpinningDisc.CallReturned.class::cast).filter(e -> e.call() == call).count();
    }
    static CdSolidObject.Result completed(Harness h) { return assertInstanceOf(CdSolidObject.Completed.class, h.solids.getFirst().outcome()).completedResult(); }
    static Harness ready() { var h = new Harness(new Terrain(), assets()); h.prepare(); return h; }
    static Harness standing() {
        var h = ready(); h.seed.flags = 8; h.seed.stand = WHOLE; h.seed.y = 0x00651234; h.seed.yv = 0;
        h.seed.ctrl = 1; h.seed.anim = 0x2D; h.seed.rot = 0; h.seed.dist = 20;
        h.edit(s -> s.moveByte(FLAGS, 8)); h.install(); return h;
    }
    static CdSpinningDisc.Assets assets() {
        return new CdSpinningDisc.Assets(CdAssetBytes.copyOf(hex(TILES)), CdObjectVisualAssets.decode(hex(MAP_BYTES), 3, hex(ANI), 1, 12), MAP, CdAssetBytes.copyOf(hex(POSES)));
    }
    static CdObjectState dirty(CdObjectState s) {
        for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, 0xA5);
        for (var f : CdObjectState.WordField.values()) s = s.moveWord(f, 0xB6C7);
        for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0xD8E9FA0B);
        return s;
    }
    static byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    static void b(byte[] data, int at, int value) { data[at] = (byte) value; }
    static void w(byte[] data, int at, int value) { b(data, at, value >>> 8); b(data, at + 1, value); }
    static void l(byte[] data, int at, int value) { w(data, at, value >>> 16); w(data, at + 2, value); }

    /** Explicit immutable pair ingress; never a physical primary64 RAM shadow or production owner. */
    static final class P {
        int x = 0x01008000, y = 0x00671234, xv = 0x123, yv = 0x400, gv = 0x567, routine = 2, angle, flags = 2;
        int spr = 4, xr = 9, yr = 19, anim, prev, jump = 1, charge = 0x2A, lock, stick = 0x12, ctrl;
        int rot = 0xA7, dist = 0x44, push, stand, mini, rawHeld, rawTap, held = 0x81, tap = 0x82, locked = 0x83;
        P copy() { var p = new P(); p.x=x; p.y=y; p.xv=xv; p.yv=yv; p.gv=gv; p.routine=routine; p.angle=angle; p.flags=flags; p.spr=spr; p.xr=xr; p.yr=yr;
            p.anim=anim; p.prev=prev; p.jump=jump; p.charge=charge; p.lock=lock; p.stick=stick; p.ctrl=ctrl; p.rot=rot; p.dist=dist; p.push=push; p.stand=stand;
            p.mini=mini; p.rawHeld=rawHeld; p.rawTap=rawTap; p.held=held; p.tap=tap; p.locked=locked; return p; }
        CdSpinningDisc.PlayerCut cut() {
            var motion = new CdPlayerMotionState(x,y,xv,yv,gv,routine,angle,flags,spr,xr,yr,anim,prev,jump,charge,lock,stick,ctrl,0x55,0x66);
            var state = new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(17,9,5),new CdPlayerBody.Header(1,0xF0123456,0x780,2,9),
                    new CdPlayerBody.Timers(3,4,5,6),new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0x24),dist,new CdPlayerBody.ObjectLinks(push,stand),rot);
            var movement = new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,12,0x80),new CdPlayerMovement.Bounds(0,0x2000,0x800,0),
                    new CdPlayerMovement.Gates(0,1,mini,0,0,0,0),new CdPlayerMovement.Globals(7,0x60,0x12,0x34,0x56,4,0x78,0x89,0x9A,0xAB));
            var environment = new CdPlayerBody.Environment(movement,new CdPlayerBody.Controls(rawHeld,rawTap,held,tap,locked),
                    new CdPlayerBody.Globals(0x1234,0xABCD,0,0,1,2,1,3),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
            return new CdSpinningDisc.PlayerCut(state,environment);
        }
    }
    static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        Terrain() { fg[0]=41; fg[1]=7; bg[0]=5; bg[1]=7; Arrays.fill(fg,2,fg.length,(byte)2); }
        CdR11aTerrain build() { return CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,new byte[900],new byte[256],new byte[4096],new byte[4096]); }
    }
    static final class Fault extends RuntimeException { private static final long serialVersionUID = 1L; }
    static final class Harness implements CdSpinningDisc.PlayerOwner {
        final CdObjectPlacementRuntime placement = new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(hex("000c0014ffff000000000000ffff000000000000ffff00000000")));
        final P seed = new P(); CdSpinningDisc.PlayerCut live = seed.cut();
        final List<CdSpinningDisc.SolidCall> solids = new ArrayList<>(); final List<CdSpinningDisc.PlayerCall> players = new ArrayList<>();
        final List<CdSpinningDisc.FmCall> fm = new ArrayList<>(); final List<CdSpinningDisc.PlayerCut> fmPlayers = new ArrayList<>();
        final List<CdObjectState> draws = new ArrayList<>(); final Fault fault = new Fault(); final CdSpinningDisc body; final CdObjectDrawQueue queue;
        int timeStop=1,camera=0x100,rawTime=1,direction,reads; boolean failRead,failFm,failDraw,noOpPlayer,mutateFmPlayer,attackMutation,throwAfterAttack,reenter;
        String attackPort=""; CdSpinningDisc.DiscPlayerField rejectField; UnaryOperator<CdObjectState> drawRewrite=UnaryOperator.identity();
        Harness(Terrain terrain, CdSpinningDisc.Assets assets) {
            placement.resetSavedObjectFlags(); var pa=playerAssets();
            queue=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
                @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                    var s=pool().state(ref.wholeIndex()); return new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(X_FRACTION),s.longBits(MAPPINGS),s.unsignedByte(MAP_FRAME),s.word(TILE));
                }
                @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
                    var s=pool().state(ref.wholeIndex()); pool().write(s.moveByte(SPRITE_FLAGS,value?s.spriteFlags()|128:s.spriteFlags()&127));
                }
            },CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(MAP,CdAssetBytes.copyOf(hex(MAP_BYTES)),0,3)),List.of()),new byte[640]);
            queue.beginPass(); body=new CdSpinningDisc(placement,assets,new CdPlayerMath(pa),new CdSolidObject(new CdPlayerMovement(terrain.build(),pa)),this::solid,this,this::fm,this::draw);
        }
        void prepare() { edit(s -> s.moveByte(ID,9).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveLong(MAPPINGS,MAP)
                .moveWord(TILE,0x409).moveLong(X_POSITION,0x0100ABCD).moveLong(Y_POSITION,0x0080CAFE).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveByte(PRIORITY,4)); }
        CdObjectPool pool() { return placement.pool(); } CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); } void install() { live=seed.cut(); }
        CdSpinningDisc.Result run() { return body.run(WHOLE,new CdSpinningDisc.Globals(timeStop,camera,rawTime,direction)); }
        @Override public CdSpinningDisc.PlayerCut read() { reads++; attack("READ"); if(failRead) throw fault; return live; }
        void check(CdObjectPool.Snapshot cut,CdObjectState source,CdSpinningDisc.PlayerCut expected) {
            pool().validateRestore(cut); assertEquals(cut.slots(),pool().capture().slots()); assertEquals(slot(),source); assertEquals(live,expected);
        }
        @Override public void commit(CdSpinningDisc.PlayerCall call) {
            check(call.actualCut(),call.expectedSource(),call.expectedPlayer()); players.add(call); attack("PLAYER");
            if(call.result().stores().stream().anyMatch(s -> s.field()==rejectField)) throw fault;
            CdSpinningDisc.validatePlayerResult(live,call.result()); if(!noOpPlayer) live=new CdSpinningDisc.PlayerCut(call.result().finalState(),call.result().finalEnvironment());
        }
        void solid(CdSpinningDisc.SolidCall call) {
            check(call.actualCut(),call.expectedSource(),call.expectedPlayer()); solids.add(call); attack("SOLID");
            CdSolidObject.Player p; CdSolidObject.Globals g; CdObjectState object; List<CdObjectState> others;
            if(call.outcome() instanceof CdSolidObject.Completed c) { p=c.completedResult().player();g=c.completedResult().globals();object=c.completedResult().object();others=c.completedResult().otherSlots(); }
            else { var prefix=((CdSolidObject.NativeStopped)call.outcome()).prefix();p=prefix.player();g=prefix.globals();object=prefix.object();others=prefix.otherSlots(); }
            // Stage all immutable harness outputs before publication; actual Frame staging is separately Root-owned.
            var s=live.state();var h=s.header();var t=s.timers();var a=p.actor();var e=live.environment();var m=e.movement();var mg=m.globals();var bg=e.globals();
            var nextState=new CdPlayerBody.State(a.motion(),s.animation(),new CdPlayerBody.Header(h.slotId(),h.mappingAddress(),a.tile(),a.priority(),h.width()),
                    new CdPlayerBody.Timers(a.hurt(),t.invincibility(),t.speedShoes(),t.reset()),s.history(),a.rotationDistance(),new CdPlayerBody.ObjectLinks(p.pushObject(),p.standObject()),s.rotationAngle());
            var nextEnvironment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),m.bounds(),m.gates(),
                    new CdPlayerMovement.Globals(mg.lookMode(),mg.cameraYCentre(),g.primaryAngle(),g.secondaryAngle(),mg.boredTimer(),g.scoreChain(),mg.debugAngle(),mg.debugAngleShift(),mg.debugQuadrant(),mg.debugFloorDistance())),e.controls(),
                    new CdPlayerBody.Globals(bg.levelFrames(),bg.cameraXCentre(),bg.timeAttackMode(),bg.debugMode(),bg.debugCheat(),bg.timeWarpTimer(),g.invincible(),bg.bossMusic()),e.dynamicSlots());
            var next=new CdSpinningDisc.PlayerCut(nextState,nextEnvironment);var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);
            var overlay=CdObjectPool.overlay(call.actualCut(),images);pool().restore(overlay);live=next;
        }
        void fm(CdSpinningDisc.FmCall call) {
            check(call.actualCut(),call.expectedSource(),call.expectedPlayer());fm.add(call);fmPlayers.add(live);attack("FM");
            if(mutateFmPlayer) live=seed.copy().cut();if(failFm) throw fault;
        }
        void draw(CdObjectPool actual,CdObjectState.Identity identity) {
            assertSame(pool(),actual);assertEquals(WHOLE,identity.wholeIndex());attack("DRAW");var s=slot();draws.add(s);
            queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(WHOLE),s.spriteFlags(),s.centreX(),s.centreY(),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),s.unsignedByte(PRIORITY)),new CdObjectDrawQueue.CameraWords(camera,0));
            if(failDraw) throw fault;edit(drawRewrite);
        }
        void attack(String port) { if(!attackPort.equals(port)) return;if(reenter)run();if(attackMutation)pool().write(pool().state(42).moveByte(ID,0xA6));if(throwAfterAttack)throw fault; }
        List<CdSpinningDisc.PlayerStore> stores() { return players.stream().flatMap(p -> p.result().stores().stream()).toList(); }
        List<CdSpinningDisc.DiscPlayerField> fields() { return stores().stream().map(CdSpinningDisc.PlayerStore::field).toList(); }
        CdObjectDrawQueue.CpuSat finish() { var c=new CdObjectDrawQueue.CameraWords(camera,0);return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(c,c,c)); }
    }
    static CdR11aPlayerAssets playerAssets() {
        byte[] ani=new byte[578],maps=new byte[398],dplc=new byte[398],atan=new byte[257];
        for(int i=0;i<60;i++)w(ani,2*i,120);ani[122]=(byte)0xFF;
        for(int i=0;i<198;i++){w(maps,2*i,396);w(dplc,2*i,396);}for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);
        return CdR11aPlayerAssets.decode(ani,new byte[60],new byte[]{1,2,3,4,5,(byte)0xFF},new byte[4],hex(SINE),atan,CdSonicVisualAssets.decode(maps,dplc,new byte[69472]));
    }
    /** Literal native SineTable source bytes; not an expected-value generator or production fallback. */
    static final String SINE = "00000006000c00120019001f0025002b00310038003e0044004a00500056005c00610067006d00730078007e00830088008e00930098009d00a200a700ab00b000b500b900bd00c100c500c900cd00d100d400d800db00de00e100e400e700ea00ec00ee00f100f300f400f600f800f900fb00fc00fd00fe00fe00ff00ff00ff010000ff00ff00ff00fe00fe00fd00fc00fb00f900f800f600f400f300f100ee00ec00ea00e700e400e100de00db00d800d400d100cd00c900c500c100bd00b900b500b000ab00a700a2009d00980093008e00880083007e00780073006d00670061005c00560050004a0044003e00380031002b0025001f00190012000c00060000fffafff4ffeeffe7ffe1ffdbffd5ffcfffc8ffc2ffbcffb6ffb0ffaaffa4ff9fff99ff93ff8bff88ff82ff7dff78ff72ff6dff68ff63ff5eff59ff55ff50ff4bff47ff43ff3fff3bff37ff33ff2fff2cff28ff25ff22ff1fff1cff19ff16ff14ff12ff0fff0dff0cff0aff08ff07ff05ff04ff03ff02ff02ff01ff01ff01ff00ff01ff01ff01ff02ff02ff03ff04ff05ff07ff08ff0aff0cff0dff0fff12ff14ff16ff19ff1cff1fff22ff25ff28ff2cff2fff33ff37ff3bff3fff43ff47ff4bff50ff55ff59ff5eff63ff68ff6dff72ff78ff7dff82ff88ff8bff93ff99ff9fffa4ffaaffb0ffb6ffbcffc2ffc8ffcfffd5ffdbffe1ffe7ffeefff4fffa00000006000c00120019001f0025002b00310038003e0044004a00500056005c00610067006d00730078007e00830088008e00930098009d00a200a700ab00b000b500b900bd00c100c500c900cd00d100d400d800db00de00e100e400e700ea00ec00ee00f100f300f400f600f800f900fb00fc00fd00fe00fe00ff00ff00ff";
}
