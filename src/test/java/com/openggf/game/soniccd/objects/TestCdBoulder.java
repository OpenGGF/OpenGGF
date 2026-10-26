package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests frozen before CdBoulder production authoring. UNRUN by this author: Root owns RED, compilation and positive
 * execution. Native N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/_Objects/Boulder/Main.asm:7-51;
 * Object Solid.asm:15-146,188-607; Object Spawner.asm:385-446. Constructed terrain is the real format/probe fixture
 * from pinned J TestCdSolidObject, extended through its existing row writer, never a mocked Solid/probe. Its data
 * authenticates no media. Literal mapping data is a TEST oracle from N, never a production fallback. Marker tile
 * and mapping values deliberately differ from native. The opt-in retail test's pointer/raw14 are Root-supplied
 * authenticated facts; this author neither read media nor executed that case. Commit is a test stand-in for Root's
 * full-pool/Frame/publication/FM boundary. It does not exercise Frame itself.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdBoulder {
    private static final int WHOLE = 46;
    private static final int MAP = 0x00FB1B42, TILE_WORD = 0x5A3C;
    private static final OptionalInt NONE = OptionalInt.empty();
    private static final byte[] MAPS = HexFormat.of().parseHex("000202f8020000ecf00f0003f400");
    private static final byte[] TILE_IDS = tileIds();
    private static final Set<CdSolidObject.PlayerField> LANDING = EnumSet.of(
            CdSolidObject.PlayerField.CENTRE_X, CdSolidObject.PlayerField.CENTRE_X_FRACTION,
            CdSolidObject.PlayerField.CENTRE_Y, CdSolidObject.PlayerField.CENTRE_Y_FRACTION,
            CdSolidObject.PlayerField.PLAYER_JUMP, CdSolidObject.PlayerField.FLAGS,
            CdSolidObject.PlayerField.STAND_OBJECT, CdSolidObject.PlayerField.ANGLE,
            CdSolidObject.PlayerField.Y_VELOCITY, CdSolidObject.PlayerField.GROUND_VELOCITY);

    @Test void nativeLiteralPlacementSpawnsThroughTheRealPoolThenCleanInitFallsIntoMain() {
        var placement = new CdObjectPlacementRuntime(layout(0x0A0D, 0x04A0, 0x1B, 0x40));
        placement.spawnObjects(0x0800, 1);
        var h = new Harness(placement, 32, assets(), new Terrain().owner());
        var before = h.slot();
        assertEquals(List.of(0x0A0D, 0x04A0, 0x1B, 0, 0),
                List.of(before.centreX(), before.centreY(), before.id(), before.subtype(), before.subtype2()));
        var r = h.run(new CdBoulder.Globals(1, 0, 0x0800));
        assertArrayEquals(initBytes(before, true), r.state().copyBytes());
        assertEquals(List.of(new CdBoulder.RoutineEntered(CdBoulder.NativeRoutine.INIT),
                new CdBoulder.RoutineEntered(CdBoulder.NativeRoutine.MAIN),
                new CdBoulder.CallReturned(CdBoulder.NativeCall.DRAW_OBJECT),
                new CdBoulder.DespawnChecked(false)), r.effects());
        assertEquals(List.of(), h.commit.calls);
        assertEquals(List.of(r.state()), h.draw.seen);
        assertFalse(r.deleted());
    }

    @Test void dirtyCleanSignInitPreservesEveryUnwrittenByteIncludingSpeedsFractionsAndCollisionType() throws Exception {
        var h = new Harness();
        h.pool().write(rawDirty(h.slot()).moveByte(ID, 0x1B).moveByte(ROUTINE, 0).moveByte(SUBTYPE2, 0)
                .moveByte(SPRITE_FLAGS, 0x63).moveByte(SAVED_FLAGS_ID, 0).moveWord(X, 0x1000).moveWord(Y, 0x0200)
                .moveWord(X_FRACTION, 0xA55A).moveWord(Y_FRACTION, 0x5AA5)
                .moveWord(X_VELOCITY, 0x0123).moveWord(Y_VELOCITY, 0xFEDC));
        var before = h.slot();
        var r = h.run(world());
        assertArrayEquals(initBytes(before, true), r.state().copyBytes(), "compare all 64 bytes, including unnamed scratch");
        assertNotEquals(0, r.state().unsignedByte(COLLISION_TYPE));
        assertEquals(0x0123, r.state().word(X_VELOCITY));
        assertEquals(0xFEDC, r.state().word(Y_VELOCITY));
        assertEquals(0xA55A, r.state().word(X_FRACTION));
        assertEquals(0x5AA5, r.state().word(Y_FRACTION));
        assertEquals(0x67, r.state().spriteFlags());
        assertTrue(h.commit.calls.isEmpty());
    }

    @Test void dirtyNegativeInitPublishesItsFullPrefixThenCallsExactlyOneActualSolidInTheSameDispatch() {
        var h = new Harness();
        h.edit(o -> o.moveByte(SPRITE_FLAGS, 0xE3).moveByte(ROUTINE_2, 0xA5).moveByte(COLLISION_TYPE, 0x77)
                .moveWord(X_VELOCITY, 0x123).moveWord(Y_VELOCITY, 0xFEDC));
        h.gates = new CdSolidObject.Gates(0x0100, 0);
        var before = h.slot();
        var r = h.run(world());
        var call = onlyCall(h);
        assertArrayEquals(initBytes(before, true), call.callCut().slots().get(WHOLE - 32).copyBytes());
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, call.result().entry());
        assertEquals(Optional.of(CdSolidObject.GetOffReason.DEBUG_MODE), call.result().getOffReason());
        assertEquals(0xA5, call.callCut().slots().get(WHOLE - 32).unsignedByte(ROUTINE_2));
        assertEquals(0, r.state().unsignedByte(ROUTINE_2));
        assertEquals(0x77, r.state().unsignedByte(COLLISION_TYPE));
        assertEquals(0x123, r.state().word(X_VELOCITY));
        assertEquals(0xFEDC, r.state().word(Y_VELOCITY));
        assertEquals(0x67, r.state().spriteFlags());
        assertSame(call.result(), assertInstanceOf(CdBoulder.Solid.class, r.effects().get(2)).result());
        assertEquals(List.of(call.result().object()), h.draw.seen);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 4, 0x7F})
    void signedNonnegativeOuterGateDoesNotEvenRequireSolidInputs(int spriteFlags) {
        var h = new Harness(); h.main(); h.edit(o -> o.moveByte(SPRITE_FLAGS, spriteFlags).moveByte(ROUTINE_2, 0x91));
        var before = h.slot();
        var r = h.body.run(WHOLE, world(), null);
        assertEquals(before, r.state());
        assertTrue(h.commit.calls.isEmpty());
        assertEquals(List.of(new CdBoulder.RoutineEntered(CdBoulder.NativeRoutine.MAIN),
                new CdBoulder.CallReturned(CdBoulder.NativeCall.DRAW_OBJECT), new CdBoulder.DespawnChecked(false)), r.effects());
    }

    @ParameterizedTest @ValueSource(ints = {0x80, 0x81, 0xFF})
    void everyNegativeSpriteByteEntersTheOnePrimarySolidCall(int spriteFlags) {
        var h = new Harness(); h.main(); h.edit(o -> o.moveByte(SPRITE_FLAGS, spriteFlags));
        var p = new P(); p.anim = 0x17; h.player = p.player();
        var r = h.run(world());
        assertEquals(Optional.of(CdSolidObject.GetOffReason.PLAYER_DROWNING), onlyCall(h).result().getOffReason());
        assertEquals(spriteFlags & 0x7F, r.state().spriteFlags());
        assertEquals(1, h.draw.seen.size());
    }

    @Test void literalSidePushUsesWhole46AndKeepsThePlayerFractionWithoutAnyProbe() {
        var h = new Harness(); h.main();
        var p = new P(); p.x = 0x0FE78000; p.y = 0x02001234; p.flags = 0; p.xv = 0x600; p.yv = 0; p.gv = 0x333;
        h.player = p.player(); var before = h.slot();
        var r = h.run(world()); var s = onlyCall(h).result();
        var e = p.copy(); e.x = 0x0FE68000; e.xv = 0; e.gv = 0; e.push = WHOLE; e.flags = 0x20;
        assertEquals(e.player(), h.player);
        assertEquals(before.moveByte(FLAGS, 0x20), s.object());
        assertEquals(List.of(call(CdSolidObject.Routine.PUSH_OBJECT), call(CdSolidObject.Routine.GET_OFF_OBJECT),
                call(CdSolidObject.Routine.CHECK_WALL_CRUSH)), s.calls());
        assertEquals(EnumSet.of(SOLID_TYPE, FLAGS), s.objectWrites());
        assertEquals(EnumSet.of(CdSolidObject.PlayerField.PUSH_OBJECT, CdSolidObject.PlayerField.CENTRE_X,
                CdSolidObject.PlayerField.FLAGS, CdSolidObject.PlayerField.GROUND_VELOCITY,
                CdSolidObject.PlayerField.X_VELOCITY), s.playerWrites());
        assertEquals(CdSolidObject.Path.HALT_ON_X, s.path()); assertEquals(OptionalInt.of(0), s.d0());
        assertEquals(Optional.empty(), s.getOffReason()); assertEquals(List.of(), s.otherSlots());
        assertEquals(Set.of(), s.globalWrites()); assertEquals(List.of(), s.fmRequests());
        assertArrayEquals(s.object().moveByte(SPRITE_FLAGS, 4).copyBytes(), r.state().copyBytes());
    }

    @Test void literalTopLandingUsesTheRealOpenAirFloorProbeAndCompleteSharedResult() {
        var h = new Harness(); h.main(); var p = new P(); h.player = p.player(); var before = h.slot();
        var r = h.run(world()); var s = onlyCall(h).result(); var e = landed(p);
        assertEquals(e.player(), h.player); assertEquals(e.player(), s.player());
        assertEquals(before.moveByte(FLAGS, 8), s.object());
        // Y $0200 - ($10+$13) = $01DD, feet $01F0: real empty-tile floor distance 31, no angle write.
        assertEquals(List.of(new CdSolidObject.FloorDistance(31, NONE, NONE), call(CdSolidObject.Routine.STAND_ON_OBJECT)), s.calls());
        assertEquals(LANDING, s.playerWrites()); assertEquals(EnumSet.of(SOLID_TYPE, FLAGS), s.objectWrites());
        assertEquals(Set.of(), s.globalWrites()); assertEquals(new CdSolidObject.Globals(0, 4, 0x12, 0x34), s.globals());
        assertEquals(CdSolidObject.Path.STAND_ON, s.path()); assertEquals(OptionalInt.of(1), s.d0());
        assertEquals(Optional.empty(), s.getOffReason()); assertEquals(List.of(), s.otherSlots());
        assertEquals(List.of(), s.fmRequests());
        assertEquals(List.of(new CdBoulder.RoutineEntered(CdBoulder.NativeRoutine.MAIN), new CdBoulder.Solid(s),
                new CdBoulder.CallReturned(CdBoulder.NativeCall.DRAW_OBJECT), new CdBoulder.DespawnChecked(false)), r.effects());
    }

    @Test void dirtyObjectVelocitiesCarryTheCompletePlayerLongsAndReachRealWallAndFloorPorts() {
        var h = new Harness(); h.main(); h.edit(o -> o.moveByte(FLAGS, 8).moveWord(X_VELOCITY, 0x180).moveWord(Y_VELOCITY, 0x40));
        var p = landed(new P()); p.x = 0x1000C000; p.xv = 0; p.gv = 0x55; p.angle = 0x10; p.jump = 1;
        h.player = p.player(); h.run(world()); var s = onlyCall(h).result(); var e = p.copy();
        e.x = 0x10024000; e.y = 0x01DD5234; e.jump = 0;
        assertEquals(e.player(), s.player());
        assertEquals(List.of(new CdSolidObject.RoomInFront(0xC0, 19, OptionalInt.of(0xC0), OptionalInt.of(0xC0)),
                new CdSolidObject.FloorDistance(31, NONE, NONE), call(CdSolidObject.Routine.STAND_ON_OBJECT)), s.calls());
        assertEquals(new CdSolidObject.Globals(0, 4, 0xC0, 0xC0), s.globals());
        assertEquals(EnumSet.of(CdSolidObject.GlobalField.PRIMARY_ANGLE, CdSolidObject.GlobalField.SECONDARY_ANGLE), s.globalWrites());
        assertEquals(0x180, h.slot().word(X_VELOCITY)); assertEquals(0x40, h.slot().word(Y_VELOCITY));
    }

    @Test void getOffReleasesStandingAndPushLinksAndCallerAppendsChargeStopOnce() {
        var h = new Harness(); h.main(); h.edit(o -> o.moveByte(FLAGS, 0x28).moveByte(ROUTINE_2, 0x7F));
        var p = landed(new P()); p.x = 0x101A8000; p.push = WHOLE; p.charge = 0x1E; p.ctrl = 1; p.stick = 1;
        h.player = p.player(); h.run(world()); var s = onlyCall(h).result(); var e = p.copy();
        e.flags = 2; e.stand = 0; e.push = 0; e.ctrl = 0; e.stick = 0;
        assertEquals(e.player(), s.player()); assertEquals(0x20, s.object().flags());
        assertEquals(Optional.of(CdSolidObject.GetOffReason.X_RANGE_RIGHT), s.getOffReason());
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP), h.commit.fm);
        assertEquals(h.commit.fm, s.fmRequests()); assertEquals(0x1E, s.player().actor().motion().charge());
    }

    @Test void landingReleasesTheActualOtherDynamicSlotAndPublishesItBeforeDraw() {
        var h = new Harness(); h.main(); var p = new P(); p.flags = 8; p.stand = 47; p.yv = 0; h.player = p.player();
        var previous = h.pool().state(47).moveByte(ID, 0x12).moveByte(FLAGS, 0xA9).moveWord(X, 0x2222);
        h.pool().write(previous); var poolBefore = h.pool().capture();
        h.draw.after = (pool, slot) -> assertEquals(previous.moveByte(FLAGS, 0xA1), pool.state(47));
        h.run(world()); var s = onlyCall(h).result();
        assertEquals(List.of(previous.moveByte(FLAGS, 0xA1)), s.otherSlots());
        assertEquals(landed(p).player(), s.player());
        for (int i = 0; i < 96; i++) if (i != WHOLE - 32 && i != 47 - 32)
            assertEquals(poolBefore.slots().get(i), h.pool().state(i + 32));
    }

    @Test void standLinkZeroAliasesTheActualPrimaryPlayerInsteadOfAClearedDynamicSlot() {
        var h = new Harness(); h.main(); var p = new P(); p.flags = 8; p.stand = 0; p.yv = 0; h.player = p.player();
        h.run(world()); var s = onlyCall(h).result(); var e = landed(p); e.flags = 0;
        assertEquals(e.player(), s.player()); assertEquals(List.of(), s.otherSlots()); assertEquals(WHOLE, s.player().standObject());
    }

    @Test void dirtyMainSolidTypeOneUsesTheRoutine2AliasAndClearsOnlyThatByteOnRisingExit() {
        var h = new Harness(); h.main(); h.edit(o -> o.moveByte(ROUTINE_2, 1).moveByte(COLLISION_TYPE, 0x91));
        var p = new P(); p.yv = -1; h.player = p.player(); h.run(world()); var s = onlyCall(h).result();
        assertEquals(Optional.of(CdSolidObject.GetOffReason.TOP_SOLID_RISING), s.getOffReason());
        assertEquals(0, s.object().unsignedByte(ROUTINE_2)); assertEquals(0x91, s.object().unsignedByte(COLLISION_TYPE));
        assertEquals(Set.of(), s.playerWrites()); assertEquals(EnumSet.of(SOLID_TYPE), s.objectWrites());
    }

    @ParameterizedTest @CsvSource({"512,SHALLOW_SIDE", "511,NONE"})
    void sideDepthUsesTheLowByteAtHex100RatherThanAWordComparison(int y, String reason) {
        var h = new Harness(); h.main(); h.edit(o -> o.moveByte(Y_RADIUS, 0x7F));
        var p = new P(); p.x = 0x0FE78000; p.y = (y << 16) | 0x1234; p.yr = 0x7F; p.flags = 0; p.yv = 0; p.xv = 0x600;
        h.player = p.player(); h.run(world()); var s = onlyCall(h).result();
        if (reason.equals("NONE")) assertEquals(CdSolidObject.Path.HALT_ON_X, s.path());
        else assertEquals(Optional.of(CdSolidObject.GetOffReason.SHALLOW_SIDE), s.getOffReason());
    }

    @ParameterizedTest @CsvSource({"4069,X_RANGE_LEFT", "4122,X_RANGE_RIGHT"})
    void literalXRangeKeepsSignedLeftAndUnsignedExclusiveRight(int x, CdSolidObject.GetOffReason reason) {
        var h = new Harness(); h.main(); var p = new P(); p.x = (x << 16) | 0x8000; h.player = p.player();
        h.run(world()); assertEquals(Optional.of(reason), onlyCall(h).result().getOffReason());
    }

    @Test void dirtyNegativeWidthIsSignExtendedBySharedSolidWithoutBoulderNormalisation() {
        var h = new Harness(); h.main(); h.edit(o -> o.moveByte(WIDTH, 0xF0));
        var p = new P(); p.x = 0x12008000; h.player = p.player(); h.run(world());
        assertEquals(CdSolidObject.Path.STAND_ON, onlyCall(h).result().path()); assertEquals(0xF0, h.slot().unsignedByte(WIDTH));
    }

    @Test void inheritedDirtyRisingPlatformRunsRealCeilingKillAndNestedFmPublicationOnlyOnce() {
        var terrain = new Terrain(); terrain.row(5, 0x5001);
        var h = new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x1B, 0xE0)), WHOLE, assets(), terrain.owner());
        h.main(); h.edit(o -> o.moveWord(X, 0x100).moveWord(Y, 0x180).moveByte(Y_RADIUS, 0x0C)
                .moveByte(FLAGS, 8).moveWord(Y_VELOCITY, 0xFF00));
        var p = landed(new P()); p.x = 0x01008000; p.y = 0x01611234; p.ctrl = 1; p.charge = 0x1E;
        h.player = p.player(); h.solidGlobals = new CdSolidObject.Globals(1, 4, 0x12, 0x34);
        h.run(new CdBoulder.Globals(1, 0, 0x180)); var s = onlyCall(h).result();
        var e = p.copy(); e.y = 0x01601234; e.routine = 6; e.flags = 2; e.yv = -0x700; e.xv = 0; e.gv = 0;
        e.stick = 0; e.rot = 0x60; e.anim = 0x18; e.tile = 0x8780; e.priority = 0; e.jump = 0; e.ctrl = 0; e.stand = 0;
        assertEquals(e.player(), s.player());
        assertEquals(new CdSolidObject.CeilingDistance(-10, OptionalInt.of(0x80), NONE), s.calls().getFirst());
        assertInstanceOf(CdSolidObject.KillPlayerCall.class, s.calls().get(1));
        assertEquals(List.of(call(CdSolidObject.Routine.STOP_OBJ_PUSH), call(CdSolidObject.Routine.GET_OFF_OBJECT),
                new CdSolidObject.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP)), s.calls().subList(2, 5));
        assertEquals(5, s.calls().size());
        assertEquals(Optional.of(CdSolidObject.GetOffReason.CEILING_CRUSH), s.getOffReason());
        assertEquals(new CdSolidObject.Globals(0, 0, 0x80, 0x34), s.globals());
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_HURT, CdPlayerTouchResponse.FmSound.FM_CHARGESTOP), h.commit.fm);
        assertEquals(h.commit.fm, s.fmRequests()); assertEquals(1, h.commit.calls.size());
    }

    @ParameterizedTest @CsvSource({"5,RESERVED_SLOT_ADDRESS", "31,RESERVED_SLOT_ADDRESS", "128,OUT_OF_POOL_ADDRESS", "255,OUT_OF_POOL_ADDRESS"})
    void reachedUnownedStandLinkRetainsPublishedInitButNoPureHelperWrites(int stand, CdSolidObject.UnsupportedBoundary boundary) {
        var h = new Harness(); h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x83));
        var p = new P(); p.flags = 8; p.stand = stand; p.yv = 0; h.player = p.player(); var before = h.slot();
        var ex = assertThrows(CdSolidObject.UnsupportedSolidObject.class, () -> h.run(world()));
        assertEquals(boundary, ex.boundary()); assertEquals(stand, ex.slotByte());
        assertArrayEquals(initBytes(before, true), h.slot().copyBytes());
        assertEquals(List.of(), h.commit.calls); assertEquals(List.of(), h.draw.seen);
    }

    @Test void missingA0PublicationStopsBeforeDrawAndRetainsTheCompletedInitPrefix() {
        var h = new Harness(); h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x83).moveByte(ROUTINE_2, 0x7F));
        var p = new P(); p.anim = 0x17; h.player = p.player(); h.commit.publish = false; var before = h.slot();
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        assertArrayEquals(initBytes(before, true), h.slot().copyBytes()); assertEquals(1, h.commit.calls.size());
        assertEquals(List.of(), h.draw.seen); assertEquals(List.of(), h.commit.fm);
    }

    @Test void missingOtherSlotPublicationStopsBeforeDrawWithoutRollbackOrASecondSolidCall() {
        var h = new Harness(); h.main(); h.pool().write(h.pool().state(47).moveByte(ID, 0x12).moveByte(FLAGS, 9));
        var p = new P(); p.flags = 8; p.stand = 47; p.yv = 0; h.player = p.player(); h.commit.publishOthers = false;
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        assertEquals(8, h.slot().flags()); assertEquals(9, h.pool().state(47).flags());
        assertEquals(1, h.commit.calls.size()); assertEquals(List.of(), h.draw.seen);
    }

    @Test void fullCallCutCasRejectsAnUnrelatedStaleSlotBeforeAnyPublication() {
        var h = new Harness(); h.main(); var before = h.slot();
        h.commit.before = call -> h.pool().write(h.pool().state(91).moveByte(ID, 0x55));
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        assertEquals(before, h.slot()); assertEquals(0x55, h.pool().state(91).id());
        assertEquals(List.of(), h.draw.seen); assertEquals(List.of(), h.commit.fm);
    }

    @Test void rootCommitStandInRejectsForeignCallCutAndForeignResultImagesBeforeWriting() {
        var h = new Harness(); h.main(); h.run(world()); var c = onlyCall(h); var live = h.pool().capture();
        var foreign = new CdObjectPool();
        assertThrows(IllegalArgumentException.class, () -> h.commit.commit(
                new CdBoulder.SolidCall(foreign.capture(), c.expectedPlayer(), c.expectedGlobals(), c.result())));
        var s = c.result();
        var foreignResult = new CdSolidObject.Result(s.entry(), foreign.state(WHOLE).moveByte(ID, 0x1B), s.player(), s.globals(),
                s.otherSlots(), s.objectWrites(), s.playerWrites(), s.globalWrites(), s.calls(), s.path(), s.getOffReason(), s.d0());
        h.commit.live = c.expectedPlayer(); h.commit.liveGlobals = c.expectedGlobals();
        assertThrows(IllegalArgumentException.class, () -> h.commit.commit(
                new CdBoulder.SolidCall(h.pool().capture(), c.expectedPlayer(), c.expectedGlobals(), foreignResult)));
        assertEquals(live.slots(), h.pool().capture().slots());
    }

    @Test void staleExpectedPlayerIsRejectedByTheSynchronousRootBoundary() {
        var h = new Harness(); h.main(); var before = h.slot();
        h.commit.before = call -> { var p = new P(); p.x++; h.commit.live = p.player(); };
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        assertEquals(before, h.slot()); assertEquals(List.of(), h.draw.seen); assertEquals(List.of(), h.commit.fm);
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 4, 0x80, 0xFE, 0xFF})
    void oddAndOutOfDirectoryRoutineStopsBeforeAnyBodyWrites(int routine) {
        var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, routine)); var before = h.pool().capture();
        var ex = assertThrows(CdBoulder.UnsupportedBoulder.class, () -> h.run(world()));
        assertEquals(CdBoulder.UnsupportedBoundary.ROUTINE_DISPATCH, ex.boundary()); assertEquals(WHOLE, ex.wholeIndex());
        assertEquals(before.slots(), h.pool().capture().slots()); assertEquals(List.of(), h.draw.seen);
    }

    @ParameterizedTest @ValueSource(ints = {1, 0x80, 0xFF})
    void reachedSubtypeTwoFailureRetainsInitPrefixButDoesNotStoreATileOrCallMain(int subtype2) {
        var h = new Harness(); h.edit(o -> o.moveByte(SUBTYPE2, subtype2).moveWord(TILE, 0xBEEF)); var before = h.slot();
        var ex = assertThrows(CdBoulder.UnsupportedBoulder.class, () -> h.run(world()));
        assertEquals(CdBoulder.UnsupportedBoundary.TILE_ID_SUBTYPE2, ex.boundary());
        assertArrayEquals(initBytes(before, false), h.slot().copyBytes()); assertEquals(0xBEEF, h.slot().word(TILE));
        assertEquals(List.of(), h.commit.calls); assertEquals(List.of(), h.draw.seen);
    }

    @Test void malformedReachedTileTableKeepsInitPrefixAndOldTile() {
        byte[] bad = TILE_IDS.clone(); word(bad, 2 * 11, 35);
        var h = new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x1B, 0xE0)), WHOLE,
                new CdBoulder.Assets(CdAssetBytes.copyOf(bad), visual(), MAP), new Terrain().owner());
        h.edit(o -> o.moveWord(TILE, 0x1234)); var before = h.slot();
        assertThrows(IllegalArgumentException.class, () -> h.run(world()));
        assertArrayEquals(initBytes(before, false), h.slot().copyBytes()); assertEquals(List.of(), h.draw.seen);
    }

    @Test void drawFailureRetainsItsOwnLiveMutationsAndTheAlreadyPublishedSolidAndFmPrefix() {
        var h = new Harness(); h.main(); h.edit(o -> o.moveByte(FLAGS, 8));
        var p = landed(new P()); p.x = 0x101A8000; p.charge = 1; h.player = p.player();
        var failure = new IllegalStateException("test draw stop");
        h.draw.after = (pool, slot) -> { pool.write(pool.state(slot.wholeIndex()).moveByte(COLLISION_STATUS, 0x71)); throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> h.run(world())));
        var s = onlyCall(h).result();
        assertEquals(s.object().moveByte(SPRITE_FLAGS, 4).moveByte(COLLISION_STATUS, 0x71), h.slot());
        assertEquals(s.player(), h.commit.live); assertEquals(s.fmRequests(), h.commit.fm);
        assertEquals(1, h.commit.calls.size()); assertEquals(0, h.slot().flags());
    }

    @Test void drawActualOutputIsReloadedForBothTheDespawnXAndAllOtherBytes() {
        var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4));
        h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveWord(X, 0x0A0D).moveByte(COLLISION_STATUS, 0x71));
        var r = h.run(new CdBoulder.Globals(1, 0, 0x0800));
        assertFalse(r.deleted()); assertEquals(0x0A0D, r.state().centreX()); assertEquals(0x71, r.state().unsignedByte(COLLISION_STATUS));
    }

    @ParameterizedTest @CsvSource({"2573,false", "2688,true", "1792,true"})
    void despawnUsesUnsignedWordChunksAndRetainsExactHex280Equality(int x, boolean deleted) {
        var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveWord(X, x));
        var before = h.slot(); var r = h.run(new CdBoulder.Globals(1, 0, 0x0800));
        assertEquals(deleted, r.deleted()); assertEquals(new CdBoulder.DespawnChecked(deleted), r.effects().getLast());
        assertArrayEquals(deleted ? new byte[64] : before.copyBytes(), r.state().copyBytes());
    }

    @Test void drawClearingOldNegativeFlagMakesTheWindowRunAndNewNegativeOutputBypassesIt() {
        var h = new Harness(); h.main(); h.edit(o -> o.moveWord(X, 0x0A80));
        var p = new P(); p.anim = 0x17; h.player = p.player();
        assertTrue(h.run(new CdBoulder.Globals(1, 0, 0x0800)).deleted()); assertEquals(1, h.commit.calls.size());
        var other = new Harness(); other.edit(o -> o.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveWord(X, 0x0A80));
        other.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(SPRITE_FLAGS, 0x84));
        assertFalse(other.run(new CdBoulder.Globals(1, 0, 0x0800)).deleted());
    }

    @ParameterizedTest @CsvSource({"129,0,1", "129,1,0", "129,255,2", "129,128,2", "129,127,0", "128,1,0", "130,255,2"})
    void rawWarpByteDeletesEvenNegativeDrawOutputAndRemapsSavedPeriodWithSignedDirection(int timeZone, int direction, int period) {
        var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveByte(SAVED_FLAGS_ID, 7));
        for (int i = 0; i < 3; i++) {
            h.placement.bsetSavedFlag(7, i, CdObjectPlacementRuntime.SavedBit.LOADED);
            h.placement.bsetSavedFlag(7, i, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
        }
        h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(SPRITE_FLAGS, 0x84));
        var r = h.run(new CdBoulder.Globals(timeZone, direction, 0x1080));
        assertTrue(r.deleted()); assertArrayEquals(new byte[64], r.state().copyBytes());
        for (int i = 0; i < 3; i++) assertEquals(i == period ? 2 : 0x82, h.placement.savedFlag(7, i));
    }

    @Test void ordinaryOutsideDespawnClearsOnlyCurrentLoadedBitAndDeletesTheWholeSlot() throws Exception {
        var h = new Harness(); h.pool().write(rawDirty(h.slot()).moveByte(ID, 0x1B).moveByte(ROUTINE, 2)
                .moveByte(SPRITE_FLAGS, 4).moveByte(SAVED_FLAGS_ID, 7).moveWord(X, 0x0A80));
        for (int i = 0; i < 3; i++) h.placement.bsetSavedFlag(7, i, CdObjectPlacementRuntime.SavedBit.LOADED);
        assertTrue(h.run(new CdBoulder.Globals(1, 0xFF, 0x0800)).deleted());
        assertArrayEquals(new byte[64], h.slot().copyBytes());
        assertEquals(0x80, h.placement.savedFlag(7, 0)); assertEquals(0, h.placement.savedFlag(7, 1));
        assertEquals(0x80, h.placement.savedFlag(7, 2));
    }

    @Test void reachedSavedBankBoundsFailurePreservesThePostDrawImageAndEarlierWrites() {
        var h = new Harness(); h.edit(o -> o.moveByte(SAVED_FLAGS_ID, 0xFF));
        var before = h.slot(); var saved = h.placement.capture().savedFlags();
        h.draw.after = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(COLLISION_STATUS, 0x71));
        assertThrows(IndexOutOfBoundsException.class, () -> h.run(new CdBoulder.Globals(0x81, 0, 0x1080)));
        byte[] expected = initBytes(before, true); expected[0x21] = 0x71;
        assertArrayEquals(expected, h.slot().copyBytes()); assertArrayEquals(saved.copy(), h.placement.capture().savedFlags().copy());
        assertEquals(1, h.draw.seen.size());
    }

    @Test void synchronousDrawCannotReenterThisBodyAndGuardDoesNotEraseThePrefix() {
        var h = new Harness();
        h.draw.after = (pool, slot) -> assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world(), h.inputs()));
        var r = h.run(world()); assertEquals(2, r.state().routine()); assertEquals(1, h.draw.seen.size());
    }

    @Test void synchronousSolidCommitCannotReenterThisBody() {
        var h = new Harness(); h.main();
        h.commit.before = call -> assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world(), h.inputs()));
        h.run(world()); assertEquals(1, h.commit.calls.size()); assertEquals(1, h.draw.seen.size());
    }

    @Test void ingressAndRecordsRejectWrongWidthsNullsWrongIdAndForeignPoolImages() {
        assertThrows(IllegalArgumentException.class, () -> new CdBoulder.Globals(3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdBoulder.Globals(0x100, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdBoulder.Globals(1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdBoulder.Globals(1, 0, 0x10000));
        assertThrows(NullPointerException.class, () -> new CdBoulder.SolidInputs(null, new CdSolidObject.Gates(0, 0), new CdSolidObject.Globals(0, 0, 0, 0)));
        assertThrows(NullPointerException.class, () -> new CdBoulder(null, assets(), new Terrain().owner(), call -> {}, (pool, slot) -> {}));
        var h = new Harness(); h.edit(o -> o.moveByte(ID, 0x12)); var before = h.slot();
        assertThrows(IllegalArgumentException.class, () -> h.run(world())); assertEquals(before, h.slot());
        assertThrows(IllegalArgumentException.class, () -> h.pool().write(new CdObjectPool().state(WHOLE)));
        assertThrows(NullPointerException.class, () -> new CdBoulder.Result(null, List.of()));
        assertThrows(NullPointerException.class, () -> new CdBoulder.SolidCall(null, h.player, h.solidGlobals, null));
    }

    @Test void assetShapeRequiresStaticSingleFrameAndAtMostNineteenTilesWithoutAnEmbeddedFallback() {
        assertEquals(1, assets().visual().mappingFrames().size()); assertTrue(assets().visual().animations().isEmpty());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-8, 2, 0, -20), new CdObjectVisualAssets.Piece(-16, 15, 3, -12)),
                visual().mappingFrames().getFirst().pieces());
        assertThrows(IllegalArgumentException.class, () -> new CdBoulder.Assets(CdAssetBytes.copyOf(new byte[73]), visual(), MAP));
        var twoFrames = CdObjectVisualAssets.decodeStatic(new byte[]{0, 4, 0, 4, 0, 0}, 2, 19);
        assertThrows(IllegalArgumentException.class, () -> new CdBoulder.Assets(CdAssetBytes.copyOf(TILE_IDS), twoFrames, MAP));
        var beyond = CdObjectVisualAssets.decodeStatic(new byte[]{0, 2, 1, 0, 0, 0, 19, 0}, 1, 20);
        assertThrows(IllegalArgumentException.class, () -> new CdBoulder.Assets(CdAssetBytes.copyOf(TILE_IDS), beyond, MAP));
        assertThrows(NullPointerException.class, () -> CdBoulder.Assets.fromObjectAssets(null));
    }

    @Test void completedMetadataIsImmutableAndReadingItNeverReplaysTheSolidOrFmCommit() {
        var h = new Harness(); h.main(); var r = h.run(world()); var c = onlyCall(h);
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
        for (int i = 0; i < 3; i++) assertSame(c.result(), assertInstanceOf(CdBoulder.Solid.class, r.effects().get(1)).result());
        assertEquals(1, h.commit.calls.size()); assertEquals(1, h.draw.seen.size()); assertEquals(List.of(), h.commit.fm);
    }

    @Test void optionalActualUsaAssetBindingUsesAuthenticatedPointerMappingAndTileWord() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "UNRUN opt-in: set soniccd.media.root");
        java.nio.file.Path cue;
        try (var files = Files.list(java.nio.file.Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA media must contain exactly one CUE"); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            var usa = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            var bound = CdBoulder.Assets.fromObjectAssets(usa.openingObjects());
            assertEquals(0x209654, bound.mappingsAddress()); assertArrayEquals(MAPS, bound.visual().mappings().copy());
            assertEquals(0x374, CdR11aObjectAssets.decodeTileWord(bound.objectTileIds().copy(), 0xB, 0));
            assertArrayEquals(usa.openingObjects().view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes().copy(), bound.objectTileIds().copy());
            var h = new Harness(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x1B, 0xE0)), WHOLE, bound, CdSolidObject.fromAssets(usa));
            var r = h.run(world()); assertEquals(0x209654, r.state().longBits(MAPPINGS)); assertEquals(0x374, r.state().word(TILE));
            assertTrue(h.commit.calls.isEmpty());
        }
    }

    private static CdBoulder.Globals world() { return new CdBoulder.Globals(1, 0, 0x1080); }
    private static CdSolidObject.RoutineCall call(CdSolidObject.Routine r) { return new CdSolidObject.RoutineCall(r); }
    private static CdBoulder.SolidCall onlyCall(Harness h) { assertEquals(1, h.commit.calls.size()); return h.commit.calls.getFirst(); }
    private static P landed(P p) {
        var e = p.copy(); e.y = 0x01DD1234; e.yv = 0; e.gv = p.xv; e.angle = 0; e.flags = 8; e.jump = 0; e.stand = WHOLE;
        return e;
    }
    private static CdObjectVisualAssets visual() { return CdObjectVisualAssets.decodeStatic(MAPS, 1, 19); }
    private static CdBoulder.Assets assets() { return new CdBoulder.Assets(CdAssetBytes.copyOf(TILE_IDS), visual(), MAP); }
    private static byte[] tileIds() {
        var out = ByteBuffer.allocate(74);
        for (int i = 0; i < 18; i++) out.putShort((short) (36 + 2 * i));
        for (int i = 0; i < 19; i++) out.putShort((short) (i == 11 ? TILE_WORD : i * 0x101));
        return out.array();
    }
    /** Expected raw instruction writes, independently addressed; no production method computes this oracle. */
    private static byte[] initBytes(CdObjectState old, boolean tileStored) {
        var b = old.copyBytes(); b[0x24] += 2; b[1] |= 4; b[0x18] = 4;
        b[4] = (byte) (MAP >>> 24); b[5] = (byte) (MAP >>> 16); b[6] = (byte) (MAP >>> 8); b[7] = (byte) MAP;
        b[0x19] = 0x10; b[0x16] = 0x10; b[0x1A] = 0;
        if (tileStored) word(b, 2, TILE_WORD);
        return b;
    }
    /** Storage-only fixture: exercise all 64 bytes without adding production aliases or a raw import API. */
    private static CdObjectState rawDirty(CdObjectState base) throws Exception {
        var owner = CdObjectState.class.getDeclaredField("owner"); owner.setAccessible(true);
        var ctor = CdObjectState.class.getDeclaredConstructor(Object.class, CdObjectState.Identity.class, byte[].class);
        ctor.setAccessible(true); byte[] b = new byte[64];
        for (int i = 0; i < 64; i++) b[i] = (byte) (0x31 + i * 17);
        return ctor.newInstance(owner.get(base), base.identity(), b);
    }
    private static CdObjectPlacements layout(int x, int y, int id, int periods) {
        var out = ByteBuffer.allocate(34);
        out.putShort((short) 12).putShort((short) 28).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        out.putShort((short) x).putShort((short) y).put((byte) id).put((byte) 0).put((byte) periods).put((byte) 0);
        out.putShort((short) 0xFFFF).putInt(0).putShort((short) 0); out.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(out.array());
    }
    private static void word(byte[] b, int at, int v) { b[at] = (byte) (v >>> 8); b[at + 1] = (byte) v; }

    private static final class Harness {
        final CdObjectPlacementRuntime placement; final int whole; final CdSolidObject solid;
        final Commit commit; final Draw draw = new Draw(); final CdBoulder body;
        CdSolidObject.Player player = new P().player();
        CdSolidObject.Gates gates = new CdSolidObject.Gates(0, 0);
        CdSolidObject.Globals solidGlobals = new CdSolidObject.Globals(0, 4, 0x12, 0x34);
        Harness() { this(new CdObjectPlacementRuntime(layout(0x1000, 0x200, 0x1B, 0xE0)), WHOLE, assets(), new Terrain().owner()); }
        Harness(CdObjectPlacementRuntime placement, int whole, CdBoulder.Assets assets, CdSolidObject solid) {
            this.placement = placement; this.whole = whole; this.solid = solid; commit = new Commit(pool());
            if (slot().id() == 0) edit(o -> o.moveByte(ID, 0x1B).moveWord(X, 0x1000).moveWord(Y, 0x200)
                    .moveWord(X_FRACTION, 0xA55A).moveWord(Y_FRACTION, 0x5AA5).moveByte(SPRITE_FLAGS, 3));
            body = new CdBoulder(placement, assets, solid, commit, draw);
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(whole); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); }
        void main() { edit(o -> o.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84).moveByte(WIDTH, 0x10)
                .moveByte(Y_RADIUS, 0x10).moveLong(MAPPINGS, MAP).moveWord(TILE, TILE_WORD).moveByte(PRIORITY, 4)); }
        CdBoulder.SolidInputs inputs() { return new CdBoulder.SolidInputs(player, gates, solidGlobals); }
        CdBoulder.Result run(CdBoulder.Globals world) {
            commit.live = player; commit.liveGlobals = solidGlobals;
            var r = body.run(whole, world, inputs()); player = commit.live; solidGlobals = commit.liveGlobals; return r;
        }
    }
    /** Tests the contract handed to Root, including ALL 96 call-cut slots; production Frame is outside this unit. */
    private static final class Commit implements CdBoulder.SolidCommit {
        final CdObjectPool pool; final List<CdBoulder.SolidCall> calls = new ArrayList<>();
        final List<CdPlayerTouchResponse.FmSound> fm = new ArrayList<>();
        CdSolidObject.Player live; CdSolidObject.Globals liveGlobals;
        boolean publish = true, publishOthers = true;
        Consumer<CdBoulder.SolidCall> before = call -> {};
        Commit(CdObjectPool pool) { this.pool = pool; }
        @Override public void commit(CdBoulder.SolidCall call) {
            calls.add(call); before.accept(call); pool.validateRestore(call.callCut());
            if (!call.expectedPlayer().equals(live) || !call.expectedGlobals().equals(liveGlobals)) throw new IllegalStateException("stale Frame inputs");
            for (var cut : call.callCut().slots()) if (!cut.equals(pool.state(cut.identity().wholeIndex())))
                throw new IllegalStateException("stale full call cut");
            var r = call.result(); pool.validateWrite(r.object()); r.otherSlots().forEach(pool::validateWrite);
            if (r.entry() != CdSolidObject.Entry.SOLID_OBJECT || pool.state(r.object().identity().wholeIndex()).id() != 0x1B)
                throw new IllegalArgumentException("wrong dispatch source");
            if (!publish) return;
            pool.write(r.object()); if (publishOthers) r.otherSlots().forEach(pool::write);
            live = r.player(); liveGlobals = r.globals(); fm.addAll(r.fmRequests());
        }
    }
    private static final class Draw implements CdBoulder.DrawObjectCall {
        final List<CdObjectState> seen = new ArrayList<>();
        CdBoulder.DrawObjectCall after = (pool, slot) -> {};
        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var state = pool.state(slot.wholeIndex()); seen.add(state);
            pool.write(state.moveByte(SPRITE_FLAGS, state.spriteFlags() & 0x7F)); // actual DrawObject BCLR only
            after.drawObject(pool, slot);
        }
    }
    /** Exact explicit projection fields, adapted from J TestCdSolidObject.P without changing that owner. */
    private static final class P {
        int x = 0x10008000, y = 0x01DD1234, xv = 0x123, yv = 0x100, gv, routine = 2, angle = 0x10, flags = 2;
        int spr = 4, xr = 9, yr = 0x13, anim, prev, jump = 1, charge, lock, stick, ctrl, pri = 0x56, sec = 0x78;
        int tile = 0x780, priority = 2, hurt, rot, id = 1, push, stand;
        P copy() {
            var p = new P(); p.x = x; p.y = y; p.xv = xv; p.yv = yv; p.gv = gv; p.routine = routine; p.angle = angle;
            p.flags = flags; p.spr = spr; p.xr = xr; p.yr = yr; p.anim = anim; p.prev = prev; p.jump = jump;
            p.charge = charge; p.lock = lock; p.stick = stick; p.ctrl = ctrl; p.pri = pri; p.sec = sec;
            p.tile = tile; p.priority = priority; p.hurt = hurt; p.rot = rot; p.id = id; p.push = push; p.stand = stand; return p;
        }
        CdSolidObject.Player player() {
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x, y, xv, yv, gv,
                    routine, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec), tile,
                    priority, hurt, rot), id, push, stand);
        }
    }
    /** Real format decoders and real shared collision probes, all FG cells chunk 2, with explicit floor/ceiling rows. */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2; Arrays.fill(heights, 16, 32, (byte) 16); Arrays.fill(widths, 32, 48, (byte) 16); angles[2] = (byte) 0xC0;
        }
        void row(int y, int value) { for (int x = 0; x < 16; x++) word(chunks, 512 + (y * 16 + x) * 2, value); }
        CdSolidObject owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578]; for (int i = 0; i < 60; i++) word(animations, i * 2, 120); animations[122] = (byte) 0xFF;
            byte[] maps = new byte[398], dplcs = new byte[398];
            for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]); byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[4], new byte[640], atan, visual);
            return new CdSolidObject(new CdPlayerMovement(terrain, assets));
        }
    }
}
