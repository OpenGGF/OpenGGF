package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.Actor;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.PlayerGlobals;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.WorldGlobals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SOURCE ONLY, UNRUN. Frozen before production exists; Root owns RED, compile, positive checks and runtime joins.
 * J=3626da4f7573b6a5fbb1a8fc207f4219d0a54440; N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Native common Spikes/Main.asm:7-83; Object Solid.asm:15-146,188-607; Object Spawner.asm:329-446;
 * PP Player Object Collision.asm:229-298; Sonic/Main.asm:2295-2330. Shipped FixBugs=0.
 * Declared mapping/table/terrain fixtures authenticate no media. MAP and TILE_MARKER are deliberate markers.
 * Terrain uses real pinned decoders/probes, adapted from J TestCdBoulder.Terrain, not a fake Solid/probe/result.
 * Ports simulate Root's all-96-image CAS, Frame/contact/world checks and ordered publication. They do not test
 * actual Frame, Runtime, World, media, GPU, rewind or the ordinary route. Test observers retain call records only
 * to inspect provenance; the production body must retain none. No scanner/Hazard selection is manufactured.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdSpikes {
    private static final int WHOLE = 61, MAP = 0x00FB2642, TILE_MARKER = 0x5A3C;
    private static final byte[] MAP_BYTES = HexFormat.of().parseHex("000204f0050000f000050004f0f005000000000500040000");
    private static final byte[] TILE_IDS = tileIds();
    private static final OptionalInt NONE = OptionalInt.empty();

    @Test void literalRecord100DecodesAndSpawnsThroughActualPlacementAndPool() {
        var placement = new CdObjectPlacementRuntime(layout(0x0A53, 0x040E, 0x40));
        placement.spawnObjects(0x0800, 1);
        var actual = placement.pool().state(32); // One-record fixture: physical32, not claimed route physical61.
        assertEquals(List.of(2643, 1038, 0x26, 0, 0, 0), List.of(actual.centreX(), actual.centreY(),
                actual.id(), actual.subtype(), actual.subtype2(), actual.savedFlagsId()));
        var h = new H(placement, 32, assets(), new Terrain().owner()); h.p.y = 0; h.sync();
        var before = h.slot(); var r = h.run(new CdSpikes.Globals(1, 0, 0x0800));
        assertArrayEquals(initBytes(before, true), r.state().copyBytes());
        assertEquals(List.of("draw"), h.events); assertTrue(h.solids.isEmpty()); assertFalse(r.deleted());
        assertEquals(List.of(new CdSpikes.RoutineEntered(CdSpikes.NativeRoutine.INIT),
                new CdSpikes.RoutineEntered(CdSpikes.NativeRoutine.MAIN),
                new CdSpikes.CallReturned(CdSpikes.NativeCall.DRAW_OBJECT), new CdSpikes.DespawnChecked(false)), r.effects());
    }

    @Test void suppliedPhysical61ImageInitializesWithoutPretendingToCompleteCall561() throws Exception {
        var h = new H();
        byte[] supplied = HexFormat.of().parseHex("26000000000000000a530000040e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
        h.pool().write(raw(h.slot(), supplied)); h.p.y = 0; h.sync(); var before = h.slot();
        var r = h.run(new CdSpikes.Globals(1, 0, 0x0800));
        assertEquals(0xFFDF40, r.state().identity().nativeAddress24());
        assertArrayEquals(initBytes(before, true), r.state().copyBytes());
        assertEquals(0, r.state().word(X_FRACTION)); assertEquals(0, r.state().word(Y_FRACTION));
        assertEquals(h.live, r.player()); assertEquals(h.pg, r.playerGlobals()); assertEquals(h.wg, r.worldGlobals());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 0x63})
    void dirtyInitOnlyWritesItsNativeBytesAndPrimarySubtypeIsNotASelector(int flip) throws Exception {
        var h = new H(); h.pool().write(dirty(h.slot()).moveByte(ID, 0x26).moveByte(ROUTINE, 0)
                .moveByte(SUBTYPE2, 0).moveByte(SUBTYPE, 0x7F).moveByte(SPRITE_FLAGS, flip)
                .moveByte(SAVED_FLAGS_ID, 0).moveWord(X, 1000).moveWord(Y, 1000));
        h.p.y = 0; h.sync(); var before = h.slot(); var r = h.run(world());
        assertArrayEquals(initBytes(before, true), r.state().copyBytes(), "all 64 bytes, including unnamed tail");
        assertEquals(before.word(X_VELOCITY), r.state().word(X_VELOCITY));
        assertEquals(before.word(Y_VELOCITY), r.state().word(Y_VELOCITY));
        assertEquals(before.unsignedByte(MAP_FRAME), r.state().unsignedByte(MAP_FRAME));
        assertEquals(before.unsignedByte(ROUTINE_2), r.state().unsignedByte(ROUTINE_2));
    }

    @ParameterizedTest @ValueSource(ints = {1, 0x80, 0xFF})
    void subtypeTwoStopsAtTheTileReadAfterOnlyTheEarlierInitPrefix(int subtype) {
        var h = new H(); h.edit(o -> o.moveByte(SUBTYPE2, subtype).moveWord(TILE, 0xBEEF)
                .moveByte(WIDTH, 0x55).moveByte(Y_RADIUS, 0x66).moveByte(COLLISION_TYPE, 0x77));
        var before = h.slot();
        var ex = assertThrows(CdSpikes.UnsupportedSpikes.class, () -> h.run(world()));
        assertEquals(CdSpikes.UnsupportedBoundary.TILE_ID_SUBTYPE2, ex.boundary()); assertEquals(WHOLE, ex.wholeIndex());
        assertArrayEquals(initBytes(before, false), h.slot().copyBytes()); assertTrue(h.events.isEmpty());
    }

    @Test void malformedReachedTileReadPreservesOldTileWidthRadiusAndCollision() {
        byte[] bad = TILE_IDS.clone(); word(bad, 20, 35);
        var h = new H(new CdObjectPlacementRuntime(layout(1000, 1000, 0xE0)), WHOLE,
                new CdSpikes.BodyAssets(CdAssetBytes.copyOf(bad), visual(), MAP), new Terrain().owner());
        h.edit(o -> o.moveWord(TILE, 0xCAFE).moveByte(WIDTH, 0x71).moveByte(Y_RADIUS, 0x22));
        var before = h.slot(); assertThrows(IllegalArgumentException.class, () -> h.run(world()));
        assertArrayEquals(initBytes(before, false), h.slot().copyBytes()); assertTrue(h.events.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 4, 0x80, 0xFE, 0xFF})
    void unsupportedRoutineRejectsBeforeAnyWrites(int routine) {
        var h = new H(); h.edit(o -> o.moveByte(ROUTINE, routine)); var before = h.pool().capture();
        var ex = assertThrows(CdSpikes.UnsupportedSpikes.class, () -> h.run(world()));
        assertEquals(CdSpikes.UnsupportedBoundary.ROUTINE_DISPATCH, ex.boundary());
        assertEquals(before.slots(), h.pool().capture().slots()); assertTrue(h.events.isEmpty());
    }

    @Test void wrongObjectIdRejectsBeforeAnyWrites() {
        var h = new H(); h.edit(o -> o.moveByte(ID, 0)); var before = h.pool().capture();
        assertThrows(IllegalArgumentException.class, () -> h.run(world()));
        assertEquals(before.slots(), h.pool().capture().slots()); assertTrue(h.events.isEmpty());
    }

    @Test void initFallsThroughIntoSolidEvenWhenVisibilityBitIsClearAndKeepsGetOffSoundOnce() {
        var h = new H(); h.edit(o -> o.moveByte(FLAGS, 8).moveByte(ROUTINE_2, 0xA5));
        h.p.flags = 8; h.p.stand = WHOLE; h.p.push = WHOLE; h.p.charge = 7; h.p.stick = 1; h.p.ctrl = 1; h.sync();
        var before = h.slot(); var r = h.run(world()); var s = solid(h).result();
        assertArrayEquals(initBytes(before, true), solid(h).callCut().slots().get(29).copyBytes());
        assertEquals(Optional.of(CdSolidObject.GetOffReason.OBJECT_OFFSCREEN), s.getOffReason());
        assertEquals(0, r.player().standObject()); assertEquals(0, r.player().pushObject());
        assertEquals(7, r.player().actor().motion().charge()); assertEquals(0, r.state().unsignedByte(ROUTINE_2));
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP), h.fm);
        assertEquals(List.of("solid", "draw"), h.events); assertTrue(h.hurts.isEmpty());
    }

    @ParameterizedTest @CsvSource({"1000,969,true", "1000,1031,true", "1000,968,false", "1000,1032,false",
            "65535,0,false", "0,65535,false", "0,32768,false", "32768,0,false", "1000,1000,true"})
    void outerDistanceUsesUnsignedWordBorrowThenNegateRatherThanSignedShortestDistance(int objectY, int playerY, boolean callsSolid) {
        var h = new H(); h.main(); h.edit(o -> o.moveWord(Y, objectY).moveByte(SPRITE_FLAGS, 4));
        h.p.y = (playerY << 16) | 0x4567; h.sync(); var initial = h.live;
        var r = h.run(world()); assertEquals(callsSolid ? 1 : 0, h.solids.size()); assertTrue(h.hurts.isEmpty());
        if (!callsSolid) assertEquals(initial, r.player());
        assertEquals(1, h.drawn.size());
    }

    @ParameterizedTest @ValueSource(ints = {0x82, 0x83, 0xE2, 0xE3})
    void reachedVerticalFlipInitializesTwelveAnd83AndUsesSolidWithoutDirectHurt(int flags) {
        var h = new H(); h.edit(o -> o.moveByte(SPRITE_FLAGS, flags));
        var r = h.run(world()); var s = solid(h).result();
        assertEquals(0x12, r.state().unsignedByte(WIDTH)); assertEquals(0x83, r.state().unsignedByte(COLLISION_TYPE));
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, s.entry()); assertEquals(CdSolidObject.Path.STAND_ON, s.path());
        assertEquals(s.player(), r.player()); assertTrue(h.rewinds.isEmpty()); assertTrue(h.hurts.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0x84, 0x85, 0xC4, 0xC5})
    void uprightAndHorizontalFlipUseTheSameActualLandingAndDirectHurt(int flags) {
        var h = new H(); h.main(); h.edit(o -> o.moveByte(SPRITE_FLAGS, flags));
        var beforePool = h.pool().capture(); var input = h.p.copy(); var r = h.run(world()); var s = solid(h).result();
        assertEquals(CdSolidObject.Path.STAND_ON, s.path()); assertEquals(OptionalInt.of(1), s.d0());
        assertEquals(List.of(new CdSolidObject.FloorDistance(31, NONE, NONE),
                new CdSolidObject.RoutineCall(CdSolidObject.Routine.STAND_ON_OBJECT)), s.calls());
        var expected = input.copy(); expected.y = 0x03CD4567; expected.xv = 0x200; expected.yv = -0x400;
        expected.gv = 0; expected.routine = 4; expected.angle = 0; expected.flags = 0x0A; expected.anim = 0x1A;
        expected.jump = 0; expected.charge = 0; expected.hurt = 0x78; expected.stand = WHOLE;
        assertEquals(expected.player(), r.player(), "complete motion, actor header and link equality");
        assertEquals(new PlayerGlobals(0, 0, 0, 7, 0, 0, 0, 0), r.playerGlobals());
        assertEquals(h.wg, r.worldGlobals()); assertEquals(new CdSolidObject.Globals(0, 0, 0x12, 0x34), r.solidGlobals());
        assertEquals(List.of("solid", "rewind", "hurt", "draw"), h.events); assertTrue(h.fm.isEmpty());
        assertEquals(1, h.rewinds.size()); assertEquals(0x03CD4567, h.rewinds.getFirst().finalYBits());
        assertSame(h.hurts.getFirst().result(), r.effects().stream().filter(CdSpikes.Hurt.class::isInstance)
                .map(CdSpikes.Hurt.class::cast).findFirst().orElseThrow().result());
        for (int whole = 32; whole < 128; whole++) if (whole != WHOLE)
            assertEquals(beforePool.slots().get(whole - 32), h.pool().state(whole));
    }

    @ParameterizedTest @CsvSource({"1000,384,03cbc567", "1000,-256,03ce4567", "27,384,fffec567", "1000,0,03cd4567"})
    void persistentSelfStandingVelocityRewindsOneLongWithBorrowWrapAndSignedVelocity(int objectY, int velocity, String expectedY) {
        var h = new H(); h.main(); h.edit(o -> o.moveWord(Y, objectY).moveByte(FLAGS, 8));
        h.p.y = ((objectY - 26) << 16) | 0x4567; h.p.yv = velocity; h.p.flags = 8; h.p.stand = WHOLE;
        h.p.anim = 0x2B; h.sync(); var r = h.run(world());
        int bits = (int) Long.parseLong(expectedY, 16);
        assertEquals(velocity, solid(h).result().player().actor().motion().yVelocity());
        assertEquals(solid(h).result().player().actor(), h.rewinds.getFirst().expectedActor());
        assertEquals(bits, h.rewinds.getFirst().finalYBits()); assertEquals(bits, hurt(h).expectedActor().motion().centreY16_16());
        assertEquals(bits, r.player().actor().motion().centreY16_16()); assertEquals(WHOLE, r.player().standObject());
    }

    @ParameterizedTest @CsvSource({"1,0", "128,0", "0,1", "0,255"})
    void currentTimeWarpOrInvincibleSuppressesOnlyDirectHurtAfterSolid(int warp, int invincible) {
        var h = new H(); h.main(); h.pg = globals(warp, invincible, 1, 7, 0, 0, 0x1234, 0);
        h.sg = new CdSolidObject.Globals(invincible, 0x1234, 0x12, 0x34);
        var r = h.run(world()); assertEquals(CdSolidObject.Path.STAND_ON, solid(h).result().path());
        assertTrue(h.rewinds.isEmpty()); assertTrue(h.hurts.isEmpty()); assertEquals(solid(h).result().player(), r.player());
    }

    @ParameterizedTest @ValueSource(ints = {1, 0x100, 0x8000, 0xFFFF})
    void entirePostSolidHurtWordSuppressesDirectEntry(int timer) {
        var h = new H(); h.main(); h.p.hurt = timer; h.sync(); var r = h.run(world());
        assertEquals(timer, r.player().actor().hurt()); assertTrue(h.rewinds.isEmpty()); assertTrue(h.hurts.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {4, 5, 6, 0x80, 0xFF})
    void currentRoutineIsComparedUnsignedAfterTheSolidCall(int routine) {
        var h = new H(); h.main(); h.p.routine = routine; h.sync(); var r = h.run(world());
        assertTrue(h.hurts.isEmpty()); assertTrue(h.rewinds.isEmpty());
        if (routine == 4) { assertEquals(2, r.player().actor().motion().routine()); assertEquals(120, r.player().actor().hurt()); }
        if (routine == 5) assertEquals(5, r.player().actor().motion().routine());
        if (routine >= 6) assertEquals(Optional.of(CdSolidObject.GetOffReason.PLAYER_DEAD), solid(h).result().getOffReason());
    }

    @Test void boredPlayerWithoutObjectStandFlagReturnsZeroAndDoesNotHurt() {
        var h = new H(); h.main(); h.p.anim = 0x2B; h.p.flags = 0; h.sync(); h.run(world());
        // Object Solid.asm:215-219 reaches .GetOff before StandOnObject for this actual ingress.
        assertEquals(OptionalInt.of(0), solid(h).result().d0());
        assertEquals(Optional.of(CdSolidObject.GetOffReason.BORED_NOT_STOOD_ON), solid(h).result().getOffReason());
        assertEquals(0, solid(h).result().object().flags() & 8);
        assertTrue(h.rewinds.isEmpty()); assertTrue(h.hurts.isEmpty());
    }

    @ParameterizedTest @CsvSource({"975,1000,HALT_ON_X", "1000,1026,HIT_BOTTOM"})
    void actualSideOrBottomContactDoesNotBecomeAStandingHurt(int x, int y, CdSolidObject.Path path) {
        var h = new H(); h.main(); h.p.x = (x << 16) | 0xA55A; h.p.y = (y << 16) | 0x4567;
        h.p.flags = 0; h.p.xv = 0x600; h.p.yv = 0; h.sync(); var r = h.run(world());
        assertEquals(path, solid(h).result().path()); assertTrue(h.hurts.isEmpty()); assertEquals(0xA55A, r.player().actor().motion().centreX16_16() & 0xFFFF);
        assertEquals(0x4567, r.player().actor().motion().centreY16_16() & 0xFFFF);
        if (path == CdSolidObject.Path.HALT_ON_X) assertEquals(WHOLE, r.player().pushObject());
    }

    @ParameterizedTest @CsvSource({"DROWN,PLAYER_DROWNING", "CTRL,PLAYER_INVISIBLE", "ABSENT,PLAYER_ABSENT",
            "OFFSCREEN,OBJECT_OFFSCREEN", "DEBUG_HIGH,DEBUG_MODE"})
    void allReachedSharedAdmissionGatesBelongToTheActualSolidOwner(String mode, CdSolidObject.GetOffReason reason) {
        var h = new H(); h.main();
        switch (mode) {
            case "DROWN" -> h.p.anim = 0x17;
            case "CTRL" -> h.p.ctrl = 0x40;
            case "ABSENT" -> h.p.id = 0;
            case "OFFSCREEN" -> h.edit(o -> o.moveByte(SPRITE_FLAGS, 4));
            case "DEBUG_HIGH" -> h.debug(0x0100);
            default -> throw new AssertionError(mode);
        }
        h.sync(); h.run(world()); assertEquals(Optional.of(reason), solid(h).result().getOffReason()); assertTrue(h.hurts.isEmpty());
    }

    @Test void lowDebugBytePassesSharedSolidButDirectKillReturnsWithoutWritesAfterTheLongPrefix() {
        var h = new H(); h.persistent(); h.debug(1); h.pg = globals(0, 0, 0, 9, 0, 1, 0x1234, 0);
        h.wg = worldGlobals(0); var r = h.run(world()); var c = hurt(h);
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE, c.result().exit());
        assertEquals(c.expectedActor(), c.result().actor()); assertEquals(c.expectedPlayer(), c.result().player());
        assertEquals(0x03CBC567, r.player().actor().motion().centreY16_16()); assertTrue(h.fm.isEmpty());
        assertEquals(List.of("solid", "rewind", "hurt", "draw"), h.events);
    }

    @Test void postSolidSourceLinksAndBothScratchAnglesReachFreshHurtCutAndFinalResults() {
        var h = new H(); h.main(); h.edit(o -> o.moveWord(X_VELOCITY, 0x180).moveByte(ROUTINE_2, 0xA5));
        h.p.flags = 8; h.p.stand = 32; h.p.yv = 0; h.sync();
        var prior = h.pool().state(32).moveByte(ID, 0x12).moveByte(FLAGS, 0xA9); h.pool().write(prior);
        var r = h.run(world()); var s = solid(h).result(); var c = hurt(h);
        assertEquals(List.of(prior.moveByte(FLAGS, 0xA1)), s.otherSlots());
        assertNotSame(solid(h).callCut(), c.callCut());
        assertEquals(prior.moveByte(FLAGS, 0xA1), c.callCut().slots().getFirst());
        assertEquals(s.object(), c.sourceA2()); assertNotEquals(solid(h).callCut().slots().get(29), c.sourceA2());
        assertEquals(WHOLE, r.player().standObject()); assertEquals(s.player().pushObject(), r.player().pushObject());
        assertEquals(new CdSolidObject.Globals(0, 0, 0xC0, 0xC0), r.solidGlobals());
        assertEquals(0x56, r.player().actor().motion().previousPrimaryAngle());
        assertEquals(0x78, r.player().actor().motion().previousSecondaryAngle());
    }

    @ParameterizedTest @ValueSource(ints = {32, 62, 127, -1})
    void actualFreshHurtAllocationUsesFirstFreeOrFullPoolWithoutChangingRingCount(int firstFree) throws Exception {
        var h = new H(); h.persistent(); h.pg = globals(0, 0, 0, 9, 0, 0, 0x1234, 0); h.wg = worldGlobals(7);
        for (int whole = 32; whole < 128; whole++) if (whole != WHOLE) {
            var o = dirty(h.pool().state(whole)).moveByte(ID, firstFree >= 0 && whole >= firstFree ? 0 : 0x55);
            h.pool().write(o);
        }
        var before = h.pool().capture(); var r = h.run(world()); var c = hurt(h);
        assertEquals(7, r.worldGlobals().rings()); assertEquals(h.wg, r.worldGlobals());
        var find = assertInstanceOf(CdPlayerTouchResponse.FindObjSlot.class, c.result().calls().getFirst());
        assertEquals(firstFree < 0 ? NONE : OptionalInt.of(firstFree), find.wholeIndex());
        assertEquals(firstFree < 0 ? 0 : 1, c.result().changedSlots().size());
        if (firstFree >= 0) {
            var old = before.slots().get(firstFree - 32); byte[] expected = old.copyBytes(); expected[0] = 0x11;
            word(expected, 8, 1000); word(expected, 12, 971);
            assertArrayEquals(expected, h.pool().state(firstFree).copyBytes(), "ID and two integer WORDs only; dirty fractions survive");
            assertEquals(0x26, c.sourceA2().id()); assertNotEquals(WHOLE, firstFree);
        }
        for (int whole = 32; whole < 128; whole++) if (whole != WHOLE && whole != firstFree)
            assertEquals(before.slots().get(whole - 32), h.pool().state(whole));
    }

    @ParameterizedTest @CsvSource({"1,0,7", "2,2,0", "3,2,7"})
    void wholeShieldByteSkipsAllocationButOnlyOldBitZeroControlsCombineRing(int shield, int finalShield, int combine) {
        var h = new H(); h.main(); h.pg = globals(0, 0, shield, 7, 0, 0, 0x1234, 0); h.wg = worldGlobals(0);
        var r = h.run(world()); assertEquals(finalShield, r.playerGlobals().shield()); assertEquals(combine, r.playerGlobals().combineRing());
        assertEquals(CdPlayerTouchResponse.Exit.HURT, hurt(h).result().exit());
        assertTrue(hurt(h).result().changedSlots().isEmpty());
        assertTrue(hurt(h).result().calls().stream().noneMatch(CdPlayerTouchResponse.FindObjSlot.class::isInstance)); assertTrue(h.fm.isEmpty());
    }

    @Test void directHelperUsesSharedHurtBelowHazardGatesWithoutEnemyPointsOrScanner() {
        var pool = new CdObjectPool(); var source = pool.state(WHOLE).moveByte(ID, 0x26).moveWord(X, 1000); pool.write(source);
        var p = new P(); p.hurt = 0x1234; p.routine = 6; var input = globals(7, 9, 2, 7, 0, 0x0100, 0x1234, 0);
        var cut = pool.capture(); var r = CdPlayerTouchResponse.hurtPlayer(p.player().actor(), input, worldGlobals(0), source, cut);
        assertEquals(CdPlayerTouchResponse.Exit.HURT, r.exit()); assertEquals(0x78, r.actor().hurt());
        assertEquals(7, r.player().timeWarp()); assertEquals(9, r.player().invincible()); assertEquals(0, r.player().scoreChain());
        assertEquals(cut.slots(), pool.capture().slots()); assertTrue(r.calls().stream().noneMatch(CdPlayerTouchResponse.PlayFm.class::isInstance));
    }

    @Test void genericDirectHelperStillReadsAnAllocatedIdZeroA2AliasFromItsWorkingMap() {
        var pool = new CdObjectPool(); var source = pool.state(32).moveWord(X, 2000); pool.write(source);
        var p = new P(); var r = CdPlayerTouchResponse.hurtPlayer(p.player().actor(), globals(0, 0, 0, 9, 0, 0, 4, 0),
                worldGlobals(7), source, pool.capture());
        assertEquals(32, r.changedSlots().getFirst().identity().wholeIndex()); assertEquals(1000, r.changedSlots().getFirst().centreX());
        assertEquals(0x200, r.actor().motion().xVelocity()); assertEquals(0, pool.state(32).id());
    }

    @Test void directHelperRejectsSourceImageOrOwnerNotExactlyInItsCallCutBeforeAnyPublication() {
        var pool = new CdObjectPool(); var source = pool.state(WHOLE).moveByte(ID, 0x26); pool.write(source);
        var foreign = new CdObjectPool(); var cut = pool.capture(); var p = new P().player().actor();
        var g = globals(0, 0, 1, 7, 0, 0, 4, 0); var w = worldGlobals(0);
        assertThrows(IllegalArgumentException.class, () -> CdPlayerTouchResponse.hurtPlayer(p, g, w, source.moveByte(FLAGS, 1), cut));
        assertThrows(IllegalArgumentException.class, () -> CdPlayerTouchResponse.hurtPlayer(p, g, w, foreign.state(WHOLE), cut));
        assertEquals(cut.slots(), pool.capture().slots());
    }

    @Test void debugCheatWholeWordReachesNormalHurtWithNoShieldOrRings() {
        var h = new H(); h.main(); h.pg = globals(0, 0, 0, 9, 0x0100, 0, 0x1234, 0); h.wg = worldGlobals(0);
        var r = h.run(world()); assertEquals(CdPlayerTouchResponse.Exit.HURT, hurt(h).result().exit());
        assertEquals(0, r.playerGlobals().combineRing()); assertEquals(4, r.player().actor().motion().routine()); assertTrue(h.fm.isEmpty());
    }

    @Test void realKillOwnsTilePriorityStickWordButPreservesChargeHurtFractionsAndLinks() {
        var h = new H(); h.persistent(); h.p.charge = 7; h.p.priority = 3; h.p.stick = 0x51; h.p.rot = 0xA6; h.sync();
        h.pg = globals(0, 0, 0, 9, 0, 0, 0x1234, 0); h.wg = worldGlobals(0); var r = h.run(world());
        var expected = h.p.copy(); expected.y = 0x03CBC567; expected.routine = 6; expected.flags = 0x0A;
        expected.yv = -0x700; expected.xv = 0; expected.gv = 0; expected.anim = 0x18; expected.jump = 0;
        expected.tile = 0x8780; expected.priority = 0; expected.stick = 3; expected.rot = 0xCB;
        assertEquals(expected.player(), r.player()); assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, hurt(h).result().exit());
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_HURT), h.fm); assertEquals(0, r.playerGlobals().scoreChain());
        assertEquals(WHOLE, r.player().standObject()); assertEquals(8, r.state().flags() & 8);
    }

    @ParameterizedTest @CsvSource({"false,0,968", "false,1,971", "true,0,968", "true,1,971"})
    void sharedResetUnrollsOnlyAtItsActualCutAndOrdersABBefore93(boolean kill, int mini, int expectedY) {
        var h = new H(); h.main(); h.edit(o -> o.moveByte(FLAGS, 8)); h.p.flags = 0x0C; h.p.stand = WHOLE;
        h.p.yv = 0; h.p.charge = 7; h.sync(); h.gates = new CdSolidObject.Gates(0, mini);
        h.pg = globals(0, 0, kill ? 0 : 1, 7, 0, 0, 0x1234, mini); h.wg = worldGlobals(0);
        var r = h.run(world()); var m = r.player().actor().motion();
        assertEquals((expectedY << 16) | 0x4567, m.centreY16_16()); assertEquals(mini == 0 ? 19 : 10, m.yRadius());
        assertEquals(mini == 0 ? 9 : 5, m.xRadius()); assertEquals(0x0A, m.flags()); assertEquals(kill ? 7 : 0, m.charge());
        assertEquals(kill ? List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP, CdPlayerTouchResponse.FmSound.FM_HURT)
                : List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP), h.fm);
    }

    @Test void newLandingAlreadyUnrollsInSolidSoHurtDoesNotResizeOrSoundAgain() {
        var h = new H(); h.main(); h.p.flags = 6; h.sync(); var r = h.run(world());
        assertEquals(0x03C84567, r.player().actor().motion().centreY16_16()); assertTrue(h.fm.isEmpty());
        assertEquals(0, solid(h).result().player().actor().motion().flags() & 4);
        assertEquals(1, hurt(h).result().calls().stream().filter(CdPlayerTouchResponse.ResetOnFloor.class::isInstance).count());
    }

    @ParameterizedTest @ValueSource(ints = {0x40, 0x42, 0x48})
    void waterFlagRetainsNativeHalfKnockbackAndAllUnownedMotionFields(int flags) {
        var h = new H(); h.main(); h.p.flags = flags; h.sync(); var r = h.run(world()); var m = r.player().actor().motion();
        assertEquals(-0x200, m.yVelocity()); assertEquals(0x100, m.xVelocity());
        // Initial stand0 releases the real primary alias when bit3 was set; Hurt preserves post-Solid bit3.
        assertEquals((flags & 8) == 0 ? 0x4A : 0x42, m.flags());
        assertEquals(h.p.prev, m.previousAnimation()); assertEquals(h.p.lock, m.moveLock()); assertEquals(h.p.pri, m.previousPrimaryAngle());
        assertEquals(h.p.sec, m.previousSecondaryAngle()); assertEquals(h.p.rot, r.player().actor().rotationDistance());
    }

    @ParameterizedTest @CsvSource({"65535,0,512", "0,65535,-512", "32768,32767,512", "32767,32768,-512"})
    void directHelperKnockbackComparesCurrentSourceAndPlayerXAsUnsignedWords(int playerX, int sourceX, int velocity) {
        var pool = new CdObjectPool(); var source = pool.state(WHOLE).moveByte(ID, 0x26).moveWord(X, sourceX); pool.write(source);
        var p = new P(); p.x = (playerX << 16) | 0xA55A;
        var r = CdPlayerTouchResponse.hurtPlayer(p.player().actor(), globals(0, 0, 1, 7, 0, 0, 4, 0), worldGlobals(0), source, pool.capture());
        assertEquals(velocity, r.actor().motion().xVelocity()); assertEquals(p.x, r.actor().motion().centreX16_16());
    }

    @Test void dirtyObjectMovementThreadsRealProbeAnglesRatherThanRestoringIngressBuffers() {
        var h = new H(); h.persistent(); h.edit(o -> o.moveWord(X_VELOCITY, 0x180).moveWord(Y_VELOCITY, 0x40));
        var r = h.run(world()); var s = solid(h).result();
        assertInstanceOf(CdSolidObject.RoomInFront.class, s.calls().getFirst());
        assertEquals(0xC0, r.solidGlobals().primaryAngle()); assertEquals(0xC0, r.solidGlobals().secondaryAngle());
        assertEquals(0x180, r.state().word(X_VELOCITY)); assertEquals(0x40, r.state().word(Y_VELOCITY));
        assertEquals(s.player().actor().motion().centreX16_16(), r.player().actor().motion().centreX16_16());
    }

    @ParameterizedTest @CsvSource({"5,RESERVED_SLOT_ADDRESS", "31,RESERVED_SLOT_ADDRESS", "128,OUT_OF_POOL_ADDRESS", "255,OUT_OF_POOL_ADDRESS"})
    void reachedSharedSlotDereferenceFaultPreservesInitButPublishesNoStagedSolidResult(int stand, CdSolidObject.UnsupportedBoundary boundary) {
        var h = new H(); h.edit(o -> o.moveByte(SPRITE_FLAGS, 0x80)); h.p.flags = 8; h.p.stand = stand; h.sync();
        var before = h.slot(); var actor = h.live;
        var ex = assertThrows(CdSolidObject.UnsupportedSolidObject.class, () -> h.run(world()));
        assertEquals(boundary, ex.boundary()); assertArrayEquals(initBytes(before, true), h.slot().copyBytes());
        assertEquals(actor, h.live); assertTrue(h.events.isEmpty()); assertTrue(h.solids.isEmpty());
    }

    @Test void omittedSolidA0PublicationIsDetectedBeforePrefixHurtOrDraw() {
        var h = new H(); h.main(); h.publishSolid = false; var before = h.slot();
        assertThrows(IllegalStateException.class, () -> h.run(world())); assertEquals(before, h.slot());
        assertEquals(1, h.solids.size()); assertTrue(h.hurts.isEmpty()); assertTrue(h.drawn.isEmpty());
    }

    @Test void omittedSolidOtherSlotPublicationIsDetectedWithoutUndoingPublishedA0() {
        var h = new H(); h.main(); h.p.flags = 8; h.p.stand = 32; h.sync(); h.publishOthers = false;
        h.pool().write(h.pool().state(32).moveByte(ID, 0x12).moveByte(FLAGS, 9));
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        assertEquals(8, h.slot().flags() & 8); assertEquals(9, h.pool().state(32).flags()); assertTrue(h.hurts.isEmpty());
    }

    @Test void omittedHurtSlotPublicationIsDetectedAndKeepsTheAlreadyCommittedLongPrefix() {
        var h = new H(); h.persistent(); h.pg = globals(0, 0, 0, 9, 0, 0, 0x1234, 0); h.wg = worldGlobals(7);
        h.publishHurt = false; assertThrows(IllegalStateException.class, () -> h.run(world()));
        assertEquals(0x03CBC567, h.live.actor().motion().centreY16_16()); assertEquals(0, h.pool().state(32).id());
        assertEquals(1, h.hurts.size()); assertTrue(h.drawn.isEmpty()); assertTrue(h.fm.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"solid", "rewind", "hurt", "draw"})
    void eachSynchronousPortFailureKeepsItsEarlierCompletedSourceCutsWithoutRetry(String stage) {
        var h = new H(); h.persistent(); h.fault = stage; var before = h.live;
        assertThrows(Fault.class, () -> h.run(world()));
        assertEquals(1, h.solids.size());
        if (stage.equals("solid")) { assertEquals(before, h.live); assertTrue(h.rewinds.isEmpty()); }
        if (stage.equals("rewind")) { assertEquals(0x03CD4567, h.live.actor().motion().centreY16_16()); assertTrue(h.hurts.isEmpty()); }
        if (stage.equals("hurt")) { assertEquals(0x03CBC567, h.live.actor().motion().centreY16_16()); assertEquals(2, h.live.actor().motion().routine()); }
        if (stage.equals("draw")) { assertEquals(4, h.live.actor().motion().routine()); assertEquals(0x78, h.live.actor().hurt()); }
        assertEquals(stage.equals("draw") ? 1 : 0, h.drawn.size());
    }

    @ParameterizedTest @ValueSource(strings = {"solid", "rewind", "hurt", "draw"})
    void instanceRejectsSynchronousReentryFromEveryPort(String stage) {
        var h = new H(); h.main(); Runnable enter = () -> assertThrows(IllegalStateException.class,
                () -> h.body.run(WHOLE, world(), h.inputs()));
        h.beforeSolid = c -> { if (stage.equals("solid")) enter.run(); };
        h.beforeRewind = c -> { if (stage.equals("rewind")) enter.run(); };
        h.beforeHurt = c -> { if (stage.equals("hurt")) enter.run(); };
        h.afterDraw = (pool, slot) -> { if (stage.equals("draw")) enter.run(); };
        h.run(world()); assertEquals(List.of("solid", "rewind", "hurt", "draw"), h.events);
    }

    @ParameterizedTest @ValueSource(strings = {"solid", "hurt"})
    void wholePoolCasRejectsAnUnrelatedStaleImageAtBothActualCuts(String stage) {
        var h = new H(); h.persistent(); Runnable dirty = () -> h.pool().write(h.pool().state(127).moveByte(ID, 0x55));
        if (stage.equals("solid")) h.beforeSolid = c -> dirty.run(); else h.beforeHurt = c -> dirty.run();
        assertThrows(IllegalStateException.class, () -> h.run(world())); assertEquals(0x55, h.pool().state(127).id());
        assertEquals(stage.equals("solid") ? 0x03CE4567 : 0x03CBC567, h.live.actor().motion().centreY16_16());
        assertTrue(h.drawn.isEmpty()); assertTrue(h.fm.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"actor", "player", "world"})
    void stalePostPrefixFrameContactOrWorldRejectsBeforeAnyHurtResultPublication(String part) {
        var h = new H(); h.persistent(); h.pg = globals(0, 0, 0, 9, 0, 0, 0x1234, 0); h.wg = worldGlobals(7);
        h.beforeHurt = c -> {
            switch (part) {
                case "actor" -> h.live = withActor(h.live, withY(h.live.actor(), h.live.actor().motion().centreY16_16() + 1));
                case "player" -> h.pg = globals(0, 0, 0, 8, 0, 0, 0x1234, 0);
                case "world" -> h.wg = worldGlobals(8);
                default -> throw new AssertionError(part);
            }
        };
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        assertEquals(0, h.pool().state(32).id()); assertEquals(2, h.live.actor().motion().routine());
        assertTrue(h.fm.isEmpty()); assertTrue(h.drawn.isEmpty());
    }

    @Test void staleActorAtPrefixCasPreservesCompletedSolidAndDoesNotEnterHurt() {
        var h = new H(); h.persistent(); h.beforeRewind = c -> h.live = withActor(h.live,
                withY(h.live.actor(), h.live.actor().motion().centreY16_16() + 1));
        assertThrows(IllegalStateException.class, () -> h.run(world()));
        assertEquals(0x03CD4568, h.live.actor().motion().centreY16_16()); assertEquals(8, h.slot().flags() & 8);
        assertTrue(h.hurts.isEmpty()); assertTrue(h.drawn.isEmpty());
    }

    @ParameterizedTest @CsvSource({"2560,false", "2688,true", "1792,true"})
    void nativeTailUsesCurrentDrawOutputXAndUnsignedInclusive280Window(int currentX, boolean deleted) {
        var h = new H(); h.main(); h.p.y = 0; h.sync(); h.edit(o -> o.moveWord(X, 0x7FFF));
        h.afterDraw = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveWord(X, currentX));
        var r = h.run(new CdSpikes.Globals(1, 0, 0x0800)); assertEquals(deleted, r.deleted());
        if (deleted) assertArrayEquals(new byte[64], r.state().copyBytes()); else assertEquals(currentX, r.state().centreX());
    }

    @Test void actualDrawBitSevenIsReloadedBeforeTailAndNeverRestoredFromIngress() {
        var h = new H(); h.main(); h.p.y = 0; h.sync(); h.edit(o -> o.moveWord(X, 0x7FFF));
        h.afterDraw = (pool, slot) -> { var o = pool.state(slot.wholeIndex()); pool.write(o.moveByte(SPRITE_FLAGS, o.spriteFlags() | 0x80)); };
        var r = h.run(world()); assertFalse(r.deleted()); assertEquals(0x84, r.state().spriteFlags());
    }

    @ParameterizedTest @CsvSource({"129,255,2", "129,1,0", "128,1,0", "130,255,2", "129,128,2", "129,127,0"})
    void rawWarpAndSignedDirectionSelectOnlyTheNativeSavedPeriodThenClearAll64Bytes(int zone, int direction, int period) {
        var h = new H(); h.main(); h.p.y = 0; h.sync(); h.edit(o -> o.moveByte(SAVED_FLAGS_ID, 2));
        seedSaved(h.placement, 2); var bank = h.placement.capture().savedFlags().copy();
        var r = h.run(new CdSpikes.Globals(zone, direction, 0x0300));
        bank[2 + 3 * 2 + period] &= 0x7F;
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy()); assertTrue(r.deleted()); assertArrayEquals(new byte[64], r.state().copyBytes());
    }

    @Test void ordinarySavedTailClearsOnlyLoadedBitAtTheUnremappedRawPeriod() {
        var h = new H(); h.main(); h.p.y = 0; h.sync(); h.edit(o -> o.moveWord(X, 0x7FFF).moveByte(SAVED_FLAGS_ID, 2));
        seedSaved(h.placement, 2); var bank = h.placement.capture().savedFlags().copy(); bank[9] &= 0x7F;
        var r = h.run(new CdSpikes.Globals(1, 0xFF, 0x0300));
        assertTrue(r.deleted()); assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
    }

    @Test void zeroSavedIdSkipsBankAndReachedBadSavedAddressRetainsCompletedHurtAndDraw() {
        var zero = new H(); zero.main(); zero.p.y = 0; zero.sync(); var bank = zero.placement.capture().savedFlags().copy();
        assertTrue(zero.run(new CdSpikes.Globals(0x81, 0, 0x0300)).deleted());
        assertArrayEquals(bank, zero.placement.capture().savedFlags().copy());
        var bad = new H(); bad.persistent(); bad.edit(o -> o.moveByte(SAVED_FLAGS_ID, 0xFF));
        assertThrows(IndexOutOfBoundsException.class, () -> bad.run(new CdSpikes.Globals(0x82, 0xFF, 0x0300)));
        assertEquals(4, bad.live.actor().motion().routine()); assertEquals(0x78, bad.live.actor().hurt());
        assertEquals(0x26, bad.slot().id()); assertEquals(4, bad.slot().spriteFlags()); assertEquals(1, bad.drawn.size());
    }

    @Test void drawFailureAfterRealKillKeepsOneActualFm93AndDoesNotRunTail() {
        var h = new H(); h.persistent(); h.pg = globals(0, 0, 0, 9, 0, 0, 0x1234, 0); h.wg = worldGlobals(0); h.fault = "draw";
        assertThrows(Fault.class, () -> h.run(new CdSpikes.Globals(0x81, 0, 0x0300)));
        assertEquals(6, h.live.actor().motion().routine()); assertEquals(0x26, h.slot().id());
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_HURT), h.fm);
    }

    @Test void nativeWidthsAndDuplicateIngressGlobalsMustAgreeWithoutInventedDefaults() {
        assertThrows(IllegalArgumentException.class, () -> new CdSpikes.Globals(3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdSpikes.Globals(1, 256, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdSpikes.Globals(1, 0, 65536));
        var h = new H(); var in = h.inputs();
        assertThrows(IllegalArgumentException.class, () -> new CdSpikes.Inputs(in.player(), in.gates(), in.solidGlobals(),
                globals(0, 1, 1, 7, 0, 0, 0x1234, 0), in.worldGlobals()));
        assertThrows(IllegalArgumentException.class, () -> new CdSpikes.Inputs(in.player(), new CdSolidObject.Gates(1, 0),
                in.solidGlobals(), in.playerGlobals(), in.worldGlobals()));
    }

    private static CdSpikes.Globals world() { return new CdSpikes.Globals(1, 0, 0x0300); }
    private static WorldGlobals worldGlobals(int rings) { return new WorldGlobals(rings, 3, 0xA5, 0x12345678, 0x89ABCDEF, 0x5A); }
    private static PlayerGlobals globals(int warp, int invincible, int shield, int combine, int cheat, int debug, int chain, int mini) {
        return new PlayerGlobals(warp, invincible, shield, combine, cheat, debug, chain, mini);
    }
    private static CdSpikes.SolidCall solid(H h) { assertEquals(1, h.solids.size()); return h.solids.getFirst(); }
    private static CdSpikes.HurtCall hurt(H h) { assertEquals(1, h.hurts.size()); return h.hurts.getFirst(); }
    private static CdObjectVisualAssets visual() { return CdObjectVisualAssets.decodeStatic(MAP_BYTES, 1, 8); }
    private static CdSpikes.BodyAssets assets() { return new CdSpikes.BodyAssets(CdAssetBytes.copyOf(TILE_IDS), visual(), MAP); }
    private static byte[] tileIds() {
        var out = ByteBuffer.allocate(74);
        for (int i = 0; i < 18; i++) out.putShort((short) (36 + 2 * i));
        for (int i = 0; i < 19; i++) out.putShort((short) (i == 10 ? TILE_MARKER : i * 0x101));
        return out.array();
    }
    private static byte[] initBytes(CdObjectState old, boolean complete) {
        var b = old.copyBytes(); b[0x24] += 2;
        b[4] = (byte) (MAP >>> 24); b[5] = (byte) (MAP >>> 16); b[6] = (byte) (MAP >>> 8); b[7] = (byte) MAP;
        b[1] |= 4; b[0x18] = 3;
        if (complete) { word(b, 2, TILE_MARKER); b[0x19] = 0x10; b[0x16] = 8; if ((b[1] & 2) != 0) { b[0x19] = 0x12; b[0x20] = (byte) 0x83; } }
        return b;
    }
    private static CdObjectState raw(CdObjectState base, byte[] bytes) throws Exception {
        var owner = CdObjectState.class.getDeclaredField("owner"); owner.setAccessible(true);
        var ctor = CdObjectState.class.getDeclaredConstructor(Object.class, CdObjectState.Identity.class, byte[].class);
        ctor.setAccessible(true); return ctor.newInstance(owner.get(base), base.identity(), bytes.clone());
    }
    private static CdObjectState dirty(CdObjectState base) throws Exception {
        byte[] b = new byte[64]; for (int i = 0; i < 64; i++) b[i] = (byte) (0x31 + i * 17); return raw(base, b);
    }
    private static CdObjectPlacements layout(int x, int y, int periods) {
        var out = ByteBuffer.allocate(34);
        out.putShort((short) 12).putShort((short) 28).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        out.putShort((short) x).putShort((short) y).put((byte) 0x26).put((byte) 0).put((byte) periods).put((byte) 0);
        out.putShort((short) 0xFFFF).putInt(0).putShort((short) 0); out.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(out.array());
    }
    private static void word(byte[] b, int at, int value) { b[at] = (byte) (value >>> 8); b[at + 1] = (byte) value; }
    private static void seedSaved(CdObjectPlacementRuntime placement, int id) {
        for (int period = 0; period < 3; period++) for (var bit : CdObjectPlacementRuntime.SavedBit.values()) placement.bsetSavedFlag(id, period, bit);
    }
    private static Actor withY(Actor actor, int bits) {
        var m = actor.motion(); return new Actor(new CdPlayerMotionState(m.centreX16_16(), bits, m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle()),
                actor.tile(), actor.priority(), actor.hurt(), actor.rotationDistance());
    }
    private static CdSolidObject.Player withActor(CdSolidObject.Player p, Actor actor) {
        return new CdSolidObject.Player(actor, p.slotId(), p.pushObject(), p.standObject());
    }
    private static final class Fault extends RuntimeException { }

    /** Real pool, source images and shared helpers; only Root's integration publication is simulated. */
    private static final class H {
        final CdObjectPlacementRuntime placement; final int whole; final CdSpikes body;
        final List<CdSpikes.SolidCall> solids = new ArrayList<>(); final List<CdSpikes.YRewindCall> rewinds = new ArrayList<>();
        final List<CdSpikes.HurtCall> hurts = new ArrayList<>(); final List<CdObjectState> drawn = new ArrayList<>();
        final List<CdPlayerTouchResponse.FmSound> fm = new ArrayList<>(); final List<String> events = new ArrayList<>();
        P p = new P(); CdSolidObject.Player live = p.player(); CdSolidObject.Gates gates = new CdSolidObject.Gates(0, 0);
        CdSolidObject.Globals sg = new CdSolidObject.Globals(0, 0x1234, 0x12, 0x34);
        PlayerGlobals pg = globals(0, 0, 1, 7, 0, 0, 0x1234, 0); WorldGlobals wg = worldGlobals(7);
        boolean publishSolid = true, publishOthers = true, publishHurt = true; String fault = "";
        Consumer<CdSpikes.SolidCall> beforeSolid = c -> {}; Consumer<CdSpikes.YRewindCall> beforeRewind = c -> {};
        Consumer<CdSpikes.HurtCall> beforeHurt = c -> {}; CdSpikes.DrawObjectCall afterDraw = (pool, slot) -> {};
        H() { this(new CdObjectPlacementRuntime(layout(1000, 1000, 0xE0)), WHOLE, assets(), new Terrain().owner()); }
        H(CdObjectPlacementRuntime placement, int whole, CdSpikes.BodyAssets assets, CdSolidObject solid) {
            this.placement = placement; this.whole = whole;
            if (slot().id() == 0) edit(o -> o.moveByte(ID, 0x26).moveWord(X, 1000).moveWord(Y, 1000)
                    .moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468));
            body = new CdSpikes(placement, assets, solid, this::solidCommit, this::rewindCommit, this::hurtCommit, this::draw);
        }
        CdObjectPool pool() { return placement.pool(); } CdObjectState slot() { return pool().state(whole); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); }
        void sync() { live = p.player(); }
        void debug(int value) { gates = new CdSolidObject.Gates(value, gates.miniSonic()); pg = globals(pg.timeWarp(), pg.invincible(), pg.shield(),
                pg.combineRing(), pg.debugCheat(), value, pg.scoreChain(), pg.miniSonic()); }
        void main() { edit(o -> o.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 8)
                .moveLong(MAPPINGS, MAP).moveWord(TILE, TILE_MARKER).moveByte(PRIORITY, 3)); }
        void persistent() { main(); edit(o -> o.moveByte(FLAGS, 8)); p.flags = 8; p.stand = whole; p.yv = 0x180; sync(); }
        CdSpikes.Inputs inputs() { return new CdSpikes.Inputs(live, gates, sg, pg, wg); }
        CdSpikes.Result run(CdSpikes.Globals g) { var r = body.run(whole, g, inputs()); assertEquals(live, r.player());
            assertEquals(sg, r.solidGlobals()); assertEquals(pg, r.playerGlobals()); assertEquals(wg, r.worldGlobals()); return r; }
        void fail(String stage) { if (fault.equals(stage)) throw new Fault(); }
        void fullCas(CdObjectPool.Snapshot cut) {
            pool().validateRestore(cut);
            for (var old : cut.slots()) if (!old.equals(pool().state(old.identity().wholeIndex()))) throw new IllegalStateException("stale whole-pool image");
        }
        void solidCommit(CdSpikes.SolidCall c) {
            solids.add(c); beforeSolid.accept(c); fail("solid"); fullCas(c.callCut());
            if (!c.expectedPlayer().equals(live) || !c.expectedGlobals().equals(sg)) throw new IllegalStateException("stale Solid Frame");
            var r = c.result(); pool().validateWrite(r.object()); r.otherSlots().forEach(pool()::validateWrite);
            if (r.entry() != CdSolidObject.Entry.SOLID_OBJECT || r.object().identity().wholeIndex() != whole || slot().id() != 0x26)
                throw new IllegalArgumentException("wrong source");
            if (!publishSolid) return;
            pool().write(r.object()); if (publishOthers) r.otherSlots().forEach(pool()::write);
            live = r.player(); sg = r.globals(); pg = globals(pg.timeWarp(), sg.invincible(), pg.shield(), pg.combineRing(),
                    pg.debugCheat(), pg.debugMode(), sg.scoreChain(), pg.miniSonic()); fm.addAll(r.fmRequests()); events.add("solid");
        }
        void rewindCommit(CdSpikes.YRewindCall c) {
            rewinds.add(c); beforeRewind.accept(c); fail("rewind");
            if (c.sourceIdentity().wholeIndex() != whole || slot().id() != 0x26 || !c.expectedActor().equals(live.actor())) throw new IllegalStateException("stale prefix Frame");
            int expected = live.actor().motion().centreY16_16() - (((short) live.actor().motion().yVelocity()) << 8);
            if (expected != c.finalYBits()) throw new IllegalArgumentException("wrong native LONG subtraction");
            live = withActor(live, withY(live.actor(), c.finalYBits())); events.add("rewind");
        }
        void hurtCommit(CdSpikes.HurtCall c) {
            hurts.add(c); beforeHurt.accept(c); fail("hurt"); fullCas(c.callCut());
            if (!c.sourceA2().equals(slot()) || c.sourceA2().id() != 0x26 || !c.expectedActor().equals(live.actor())
                    || !c.expectedPlayer().equals(pg) || !c.expectedWorld().equals(wg)) throw new IllegalStateException("stale Hurt Frame/contact/world/source");
            var r = c.result(); r.changedSlots().forEach(pool()::validateWrite);
            if (!wg.equals(r.world())) throw new IllegalArgumentException("Hurt changed world");
            if (!publishHurt) return;
            r.changedSlots().forEach(pool()::write); live = withActor(live, r.actor()); pg = r.player(); wg = r.world();
            sg = new CdSolidObject.Globals(pg.invincible(), pg.scoreChain(), sg.primaryAngle(), sg.secondaryAngle());
            for (var call : r.calls()) if (call instanceof CdPlayerTouchResponse.PlayFm request) fm.add(request.sound()); events.add("hurt");
        }
        void draw(CdObjectPool pool, CdObjectState.Identity slot) {
            drawn.add(pool.state(slot.wholeIndex())); fail("draw"); var o = pool.state(slot.wholeIndex());
            pool.write(o.moveByte(SPRITE_FLAGS, o.spriteFlags() & 0x7F)); afterDraw.drawObject(pool, slot); events.add("draw");
        }
    }

    /** All fields are explicit ingress; changing one field does not manufacture zeros for other native aliases. */
    private static final class P {
        int x = 0x03E88000, y = 0x03CE4567, xv = 0x123, yv = 0x180, gv = 0x333, routine = 2, angle = 0x10, flags = 2;
        int spr = 4, xr = 9, yr = 19, anim, prev = 0x31, jump = 1, charge = 7, lock = -123, stick = 0x51, ctrl;
        int pri = 0x56, sec = 0x78, tile = 0x780, priority = 2, hurt, rot = 0xA6, id = 1, push, stand;
        P copy() { var p = new P(); p.x = x; p.y = y; p.xv = xv; p.yv = yv; p.gv = gv; p.routine = routine; p.angle = angle;
            p.flags = flags; p.spr = spr; p.xr = xr; p.yr = yr; p.anim = anim; p.prev = prev; p.jump = jump; p.charge = charge;
            p.lock = lock; p.stick = stick; p.ctrl = ctrl; p.pri = pri; p.sec = sec; p.tile = tile; p.priority = priority;
            p.hurt = hurt; p.rot = rot; p.id = id; p.push = push; p.stand = stand; return p; }
        CdSolidObject.Player player() { return new CdSolidObject.Player(new Actor(new CdPlayerMotionState(x, y, xv, yv, gv, routine, angle,
                flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec), tile, priority, hurt, rot), id, push, stand); }
    }
    /** Real pinned terrain/player format fixtures and probe owner; no source behavior is executed by this author. */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() { fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2; Arrays.fill(heights, 16, 32, (byte) 16); Arrays.fill(widths, 32, 48, (byte) 16); angles[2] = (byte) 0xC0; }
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
