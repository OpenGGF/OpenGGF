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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Entire test source frozen before the body/state mirror was authored. ALL JAVA UNRUN.
 * OpenAI Codex GPT-6.1 Sol; N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Literal expectations: Tunnel Door/Main.asm:7-83; Data/Mappings.asm:1-27;
 * Object Spawner.asm:385-446; Object Solid.asm:188-479. No contact-answer injection.
 * Constructed format assets authenticate no media. Solid, terrain probes, pool, saved bank and
 * DrawObject CPU queue are real owners. The three publication/I/O ports are synchronous stand-ins.
 */
class TestCdTunnelDoorVertical {
    private static final int WHOLE = 40;
    private static final int MAP = 0x00CD1000; // Constructed marker, NEVER a claimed native operand.
    /** Two real relative roots, two counted pieces each, and each real zero alignment byte. */
    private static final byte[] MAPS = bytes(0, 4, 0, 16,
            2, 0xE8, 1, 0x18, 4, 0xFC, 0xF8, 3, 0x18, 0, 0xFC, 0,
            2, 0xE8, 0x0C, 0, 6, 0xFC, 0xE8, 4, 0, 0x0A, 0x1C, 0);

    @Test void timerAliasesClearBothBytesButIncrementOnlyTheHighByte() {
        var h = prepared(4);
        assertEquals(0x3A, TUNNEL_DOOR_TIMER.byteOffset());
        assertEquals(0x3A, TUNNEL_DOOR_TIMER_HIGH.byteOffset());
        h.edit(o -> o.moveWord(TUNNEL_DOOR_TIMER, 0x12AB));
        assertEquals(0x12, h.slot().unsignedByte(TUNNEL_DOOR_TIMER_HIGH));
        h.run();
        assertEquals(0x1AAB, h.slot().word(TUNNEL_DOOR_TIMER));
        h.edit(o -> o.moveWord(TUNNEL_DOOR_TIMER, 0));
        assertEquals(0, h.slot().copyBytes()[0x3B]);
    }

    @Test void dirtyInitWritesOnlyItsNativePrefixTileAndTheRealMainSolidStores() {
        var h = prepared(0);
        h.edit(TestCdTunnelDoorVertical::dirtyInit);
        var p = new P(); p.y = 0x02001234; h.player = p.player(); // Main's range branch is not taken.
        var before = h.slot();
        var r = h.run();
        var expected = before.moveByte(ROUTINE, 2).moveLong(MAPPINGS, MAP)
                .moveByte(PRIORITY, 1).moveByte(SPRITE_FLAGS, 0x35)
                .moveByte(WIDTH, 4).moveByte(Y_RADIUS, 24).moveWord(TILE, 0x8328)
                .moveByte(SOLID_TYPE, 0);
        assertEquals(expected, r.state(), "same pool, complete 64-byte comparison");
        assertEquals(expected, h.draws.getFirst());
        assertEquals(List.of("INIT", "MAIN", "SOLID_OBJECT", "DRAW_OBJECT", "CHECK_OBJ_DESPAWN"), trace(r));
        assertEquals(1, h.solids.size());
        assertEquals(CdSolidObject.GetOffReason.PLAYER_DEAD, h.solids.getFirst().result().getOffReason().orElseThrow());
        assertEquals(0x4321, r.state().word(X_FRACTION));
        assertEquals(0x8765, r.state().word(Y_FRACTION));
        assertEquals(0x12AB, r.state().word(TUNNEL_DOOR_TIMER));
        assertEquals(9, r.state().unsignedByte(MAP_FRAME));
        assertEquals(h.player, r.finalPlayer()); assertEquals(h.globals, r.finalGlobals());
        assertTrue(h.sounds.isEmpty());
    }

    @Test void initFallsIntoMainAndSoundObservesPrefixBeforeFrameOneAndSolid() {
        var h = prepared(0);
        h.edit(o -> o.moveByte(MAP_FRAME, 7).moveWord(TUNNEL_DOOR_TIMER, 0xABCD));
        var beforePlayer = h.player;
        var r = h.run();
        assertEquals(List.of("INIT", "MAIN", "FM_A4", "SOLID_OBJECT", "DRAW_OBJECT", "CHECK_OBJ_DESPAWN"), trace(r));
        var atSound = h.soundImages.getFirst();
        assertEquals(4, atSound.routine()); assertEquals(0, atSound.word(TUNNEL_DOOR_TIMER));
        assertEquals(7, atSound.unsignedByte(MAP_FRAME)); assertEquals(0x8328, atSound.word(TILE));
        var call = h.solids.getFirst();
        var atSolid = call.callCut().slots().get(WHOLE - 32);
        assertEquals(1, atSolid.unsignedByte(MAP_FRAME)); assertEquals(4, atSolid.routine());
        assertEquals(beforePlayer, call.expectedPlayer());
        assertEquals(List.of("SOUND", "SOLID", "DRAW"), h.order);
        assertEquals(List.of(CdTunnelDoorVertical.FmSound.FM_A4), h.sounds);
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 255})
    void unsupportedInitSubtypeStopsAtTileReadWithEarlierStoresPublished(int subtype2) {
        var h = prepared(0); h.edit(o -> dirtyInit(o).moveByte(SUBTYPE2, subtype2));
        var before = h.slot();
        var e = assertThrows(CdTunnelDoorVertical.UnsupportedTunnelDoorVertical.class, h::run);
        assertEquals(CdTunnelDoorVertical.UnsupportedBoundary.TILE_ID_SUBTYPE2, e.boundary());
        assertEquals(subtype2, e.value()); assertEquals(WHOLE, e.wholeIndex());
        assertEquals(before.moveByte(ROUTINE, 2).moveLong(MAPPINGS, MAP).moveByte(PRIORITY, 1)
                .moveByte(SPRITE_FLAGS, 0x35).moveByte(WIDTH, 4).moveByte(Y_RADIUS, 24), h.slot());
        assertTrue(h.order.isEmpty()); assertTrue(h.solids.isEmpty());
    }

    @Test void tileServiceFailureRetainsInitPrefixAndNeverRunsMainOrDraw() {
        var tileIds = tiles(); word(tileIds, 24, 0xFFFF); // Selector C's signed root is now outside data.
        var h = new Harness(assets(tileIds)); h.edit(o -> normal(o, 0).moveWord(TILE, 0xCAFE));
        var before = h.slot();
        assertThrows(IllegalArgumentException.class, h::run);
        assertEquals(before.moveByte(ROUTINE, 2).moveLong(MAPPINGS, MAP).moveByte(PRIORITY, 1)
                .moveByte(SPRITE_FLAGS, 0x84).moveByte(WIDTH, 4).moveByte(Y_RADIUS, 24), h.slot());
        assertTrue(h.order.isEmpty());
    }

    @Test void initConsumesTheBoundSelectorWordRatherThanAnEmbeddedTileConstant() {
        var tileIds = tiles(); word(tileIds, 62, 0x1357);
        var h = new Harness(assets(tileIds)); h.edit(o -> normal(o, 0));
        h.run(); assertEquals(0x1357, h.slot().word(TILE));
    }

    @ParameterizedTest
    @CsvSource({"128,128,true", "128,97,true", "128,96,false", "128,159,true", "128,160,false",
            "32768,32767,true", "32767,32768,true", "0,65535,false", "65535,0,false", "32768,0,false"})
    void mainYRangeUsesBorrowNegationAndUnsignedStrictThirtyTwo(int objectY, int playerY, boolean opens) {
        var h = prepared(2); var p = new P(); p.y = (playerY << 16) | 0x1234; h.player = p.player();
        h.edit(o -> o.moveWord(Y, objectY).moveWord(TUNNEL_DOOR_TIMER, 0xABCD).moveByte(MAP_FRAME, 7));
        h.run();
        assertEquals(opens ? 4 : 2, h.slot().routine());
        assertEquals(opens ? 0 : 0xABCD, h.slot().word(TUNNEL_DOOR_TIMER));
        assertEquals(opens ? 1 : 7, h.slot().unsignedByte(MAP_FRAME));
        assertEquals(opens ? 1 : 0, h.sounds.size()); assertEquals(1, h.solids.size());
    }

    @ParameterizedTest
    @CsvSource({"256,256,true", "256,209,true", "256,208,false", "256,257,false",
            "32768,32767,true", "32767,32768,false", "0,65535,false", "65535,0,false", "47,0,true", "48,0,false"})
    void mainXRangeRejectsUnsignedBorrowAndKeepsOnlyZeroThroughFortySeven(int objectX, int playerX, boolean opens) {
        var h = prepared(2); var p = new P(); p.x = (playerX << 16) | 0x8000; h.player = p.player();
        h.edit(o -> o.moveWord(X, objectX));
        h.run(0, 0, (objectX + 128) & 0xFFFF);
        assertEquals(opens ? 4 : 2, h.slot().routine());
        assertEquals(opens ? 1 : 0, h.sounds.size()); assertEquals(1, h.solids.size());
    }

    @ParameterizedTest @CsvSource({"4,0,7", "127,0,7", "128,1,1", "132,1,1", "255,1,1"})
    void onlyIncomingSpriteBitSevenAdmitsSoundAndFrameOne(int spriteFlags, int sounds, int frame) {
        var h = prepared(2);
        h.edit(o -> o.moveByte(SPRITE_FLAGS, spriteFlags).moveByte(MAP_FRAME, 7)
                .moveWord(TUNNEL_DOOR_TIMER, 0xFFFF));
        h.run();
        assertEquals(4, h.slot().routine()); assertEquals(0, h.slot().word(TUNNEL_DOOR_TIMER));
        assertEquals(frame, h.slot().unsignedByte(MAP_FRAME)); assertEquals(sounds, h.sounds.size());
        assertEquals(spriteFlags & 127, h.slot().spriteFlags()); assertEquals(1, h.solids.size());
    }

    @Test void actualPrimaryPlayerRangeRunsBeforeSolidsIndependentGates() {
        var h = prepared(2); var p = new P(); p.routine = 2; h.player = p.player();
        h.gates = new CdSolidObject.Gates(0x0100, 0);
        h.run();
        assertEquals(4, h.slot().routine()); assertEquals(1, h.sounds.size());
        assertEquals(CdSolidObject.GetOffReason.DEBUG_MODE, h.solids.getFirst().result().getOffReason().orElseThrow());
    }

    @Test void openedMainStillRunsPlainSolidAndReturnsTheRealHorizontalPlayerResult() {
        var h = prepared(2); var p = new P(); p.routine = 2; p.x = 0x00FA8000; p.flags = 0; p.yv = 0;
        h.player = p.player(); var expectedInput = h.player;
        var r = h.run(); var call = h.solids.getFirst();
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, call.result().entry());
        assertEquals(CdSolidObject.Path.HALT_ON_X, call.result().path());
        assertEquals(expectedInput, call.expectedPlayer());
        p.x = 0x00F28000; p.xv = 0; p.gv = 0; p.flags = 0x20; p.push = WHOLE;
        assertEquals(p.player(), r.finalPlayer()); assertEquals(h.player, r.finalPlayer());
        assertEquals(call.result().globals(), r.finalGlobals());
        assertEquals(4, call.callCut().slots().get(8).routine());
        assertEquals(1, call.callCut().slots().get(8).unsignedByte(MAP_FRAME));
        assertEquals(0x20, r.state().flags());
        assertEquals(List.of("MAIN", "FM_A4", "SOLID_OBJECT", "DRAW_OBJECT", "CHECK_OBJ_DESPAWN"), trace(r));
        var reset = h.run(); assertEquals(p.player(), reset.finalPlayer()); assertEquals(1, h.solids.size());
    }

    @Test void mainOutsideOpeningRangeStillUsesSolidAndPublishesOtherSlotRelease() {
        var h = prepared(2); var p = new P(); p.routine = 2; p.y = 0x00561234; p.flags = 8; p.stand = 41;
        h.player = p.player();
        h.pool().write(h.pool().state(41).moveByte(ID, 0x12).moveByte(FLAGS, 0xA8));
        var untouched = h.pool().state(42);
        var r = h.run(); var call = h.solids.getFirst();
        assertEquals(CdSolidObject.Path.STAND_ON, call.result().path());
        assertEquals(0xA8, call.callCut().slots().get(9).flags());
        assertEquals(1, call.result().otherSlots().size()); assertEquals(0xA0, h.pool().state(41).flags());
        p.y = 0x00551234; p.yv = 0; p.gv = 0x123; p.angle = 0; p.jump = 0; p.stand = WHOLE;
        assertEquals(p.player(), r.finalPlayer()); assertEquals(untouched, h.pool().state(42));
        assertEquals(0x4321, r.state().word(X_FRACTION)); assertEquals(0x8765, r.state().word(Y_FRACTION));
        assertEquals(2, r.state().routine()); assertTrue(h.sounds.isEmpty());
    }

    @Test void dirtyDescendingVelocityCrushUsesSharedKillAndThreadsItsFinalPlayerAndGlobalsOnce() {
        var h = prepared(2); var p = new P(); p.routine = 2; p.y = 0x00AB1234; p.flags = 0; p.yv = 0; p.gv = 0x123;
        h.player = p.player(); h.globals = new CdSolidObject.Globals(1, 4, 0x12, 0x34);
        h.edit(o -> o.moveWord(Y_VELOCITY, 0x100));
        var r = h.run(); var result = h.solids.getFirst().result();
        assertEquals(CdSolidObject.Path.HIT_BOTTOM, result.path());
        var killed = assertInstanceOf(CdSolidObject.KillPlayerCall.class, result.calls().getFirst()).result();
        assertEquals(CdPlayerTouchResponse.Exit.KILL_PLAYER, killed.exit());
        p.y = 0x00AD1234; p.routine = 6; p.flags = 2; p.yv = 0; p.xv = 0; p.gv = 0;
        p.jump = 0; p.anim = 0x18; p.tile = 0x8780; p.priority = 0; p.rot = 0xAB;
        assertEquals(p.player(), r.finalPlayer());
        assertEquals(new CdSolidObject.Globals(0, 0, 0x12, 0x34), r.finalGlobals());
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_HURT), h.solidSounds);
        assertEquals(List.of("MAIN", "SOLID_OBJECT", "DRAW_OBJECT", "CHECK_OBJ_DESPAWN"), trace(r));
        assertTrue(h.sounds.isEmpty());
        h.run(); assertEquals(1, h.solidSounds.size(), "completed call metadata is never replayed");
    }

    @Test void resetReturnsWithoutSolidForThirtyTwoCallsAndDoesNotRunMainOnCarry() {
        var h = prepared(4); h.edit(o -> o.moveWord(TUNNEL_DOOR_TIMER, 0x00A5).moveByte(MAP_FRAME, 1));
        int[] highBytes = {8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 96, 104, 112, 120, 128,
                136, 144, 152, 160, 168, 176, 184, 192, 200, 208, 216, 224, 232, 240, 248, 0};
        var player = h.player; var globals = h.globals;
        for (int i = 0; i < 32; i++) {
            var r = h.run();
            assertEquals(highBytes[i], r.state().unsignedByte(TUNNEL_DOOR_TIMER_HIGH), "call " + (i + 1));
            assertEquals(0xA5, r.state().copyBytes()[0x3B] & 255);
            assertEquals(i == 31 ? 2 : 4, r.state().routine());
            assertEquals(i == 31 ? 0 : 1, r.state().unsignedByte(MAP_FRAME));
            assertEquals(List.of("RESET", "DRAW_OBJECT", "CHECK_OBJ_DESPAWN"), trace(r));
            assertEquals(player, r.finalPlayer()); assertEquals(globals, r.finalGlobals());
        }
        assertTrue(h.solids.isEmpty()); assertTrue(h.sounds.isEmpty());
        h.run(); assertEquals(1, h.solids.size(), "only the next dispatch enters Main");
    }

    @Test void dirtyTimerLowBitsSurviveAFullThirtyTwoCallHighByteRollover() {
        var h = prepared(4); h.edit(o -> o.moveWord(TUNNEL_DOOR_TIMER, 0x07CC).moveByte(MAP_FRAME, 9));
        for (int i = 0; i < 31; i++) h.run();
        assertEquals(0xFFCC, h.slot().word(TUNNEL_DOOR_TIMER)); assertEquals(4, h.slot().routine());
        h.run();
        assertEquals(0x07CC, h.slot().word(TUNNEL_DOOR_TIMER)); assertEquals(2, h.slot().routine());
        assertEquals(0, h.slot().unsignedByte(MAP_FRAME)); assertTrue(h.solids.isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,8,false", "247,255,false", "248,0,true", "254,6,true", "255,7,true"})
    void resetUsesByteCarryRatherThanWordCarryOrSignedOverflow(int high, int finalHigh, boolean carry) {
        var h = prepared(4); h.edit(o -> o.moveWord(TUNNEL_DOOR_TIMER, (high << 8) | 0x7B).moveByte(MAP_FRAME, 9));
        var before = h.slot(); var r = h.run();
        var expected = before.moveByte(TUNNEL_DOOR_TIMER_HIGH, finalHigh).moveByte(SPRITE_FLAGS, 4);
        if (carry) expected = expected.moveByte(ROUTINE, 2).moveByte(MAP_FRAME, 0);
        assertEquals(expected, r.state()); assertTrue(h.solids.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"640,128,false", "768,128,true", "65535,0,false", "0,128,false", "0,512,true", "128,0,false"})
    void realDrawClearsIncomingBitSevenBeforeWrappedUnsignedDespawnWindow(int x, int camera, boolean deleted) {
        var h = prepared(4); h.edit(o -> o.moveWord(X, x).moveByte(SPRITE_FLAGS, 0x84));
        var r = h.run(0, 0, camera);
        assertEquals(0x84, h.draws.getFirst().spriteFlags());
        assertEquals(deleted, r.deleted());
        if (deleted) assertArrayEquals(new byte[64], h.slot().copyBytes());
        else assertEquals(4, r.state().spriteFlags());
        assertEquals(1, h.drawResults.size());
    }

    @Test void offscreenDeletionClearsOnlyLoadedBitInTheActualBankBeforeFullSlotDelete() {
        var h = prepared(4); h.edit(o -> o.moveWord(X, 0x380).moveByte(SAVED_FLAGS_ID, 7));
        seed(h, 7); var expected = h.runtime.capture().savedFlags().copy(); expected[24] = 0x45;
        var r = h.run(1, 0, 128);
        assertArrayEquals(expected, h.runtime.capture().savedFlags().copy());
        assertArrayEquals(new byte[64], r.state().copyBytes());
        assertEquals(List.of("RESET", "DRAW_OBJECT", "UNLOAD:7:1", "DELETE", "CHECK_OBJ_DESPAWN"), trace(r));
    }

    @ParameterizedTest
    @CsvSource({"129,0,1", "129,1,0", "129,255,2", "128,1,0", "130,255,2", "129,127,0", "129,128,2"})
    void warpDeletionSignExtendsDirectionNegatesAndClampsTheSelectedSavedPeriod(int rawZone, int direction, int period) {
        var h = prepared(4); h.edit(o -> o.moveByte(SAVED_FLAGS_ID, 7)); seed(h, 7);
        var expected = h.runtime.capture().savedFlags().copy(); expected[23 + period] = 0x45;
        var r = h.run(rawZone, direction, 128);
        assertTrue(r.deleted()); assertArrayEquals(expected, h.runtime.capture().savedFlags().copy());
        assertEquals(new CdTunnelDoorVertical.LoadedCleared(7, period), r.effects().get(2));
    }

    @Test void savedIdZeroDeletesWithoutReadingOrClearingAnySavedBankByte() {
        var h = prepared(4); seed(h, 7);
        var bank = h.runtime.capture().savedFlags().copy();
        var r = h.run(0xFF, 0x80, 128);
        assertTrue(r.deleted()); assertArrayEquals(bank, h.runtime.capture().savedFlags().copy());
        assertEquals(List.of("RESET", "DRAW_OBJECT", "DELETE", "CHECK_OBJ_DESPAWN"), trace(r));
    }

    @Test void lastRealSavedBankByteIsUsableWithoutRoundingTheBankUp() {
        var h = prepared(4); h.edit(o -> o.moveWord(X, 0x380).moveByte(SAVED_FLAGS_ID, 254));
        h.runtime.bsetSavedFlag(254, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.runtime.bsetSavedFlag(254, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_3);
        h.run(1, 0, 128);
        assertEquals(766, h.runtime.capture().savedFlags().length());
        assertEquals(4, h.runtime.savedFlag(254, 1)); assertArrayEquals(new byte[64], h.slot().copyBytes());
    }

    @ParameterizedTest @ValueSource(ints = {254, 255})
    void outOfBankClearFailsAfterDrawAndResetPrefixButBeforeDeleteWithoutRollback(int savedId) {
        var h = prepared(4); h.edit(o -> o.moveWord(X, 0x380).moveByte(SAVED_FLAGS_ID, savedId)
                .moveWord(TUNNEL_DOOR_TIMER, 0x12AB));
        var before = h.slot(); var bank = h.runtime.capture().savedFlags().copy();
        assertThrows(IndexOutOfBoundsException.class, () -> h.run(2, 0, 128));
        assertEquals(before.moveByte(TUNNEL_DOOR_TIMER_HIGH, 0x1A).moveByte(SPRITE_FLAGS, 4), h.slot());
        assertArrayEquals(bank, h.runtime.capture().savedFlags().copy()); assertEquals(List.of("DRAW"), h.order);
    }

    @Test void globalsThatAreNeverUsedForABankReadDoNotInventAnEarlierPeriodGate() {
        var h = prepared(4); assertFalse(h.run(0x7F, 0x80, 128).deleted());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 5, 6, 254, 255})
    void unsupportedRoutineStopsBeforeEveryWriteEvenWithSpriteBitSevenClear(int routine) {
        var h = prepared(routine); h.edit(o -> o.moveByte(SPRITE_FLAGS, 4));
        var before = h.slot(); var bank = h.runtime.capture().savedFlags().copy();
        var e = assertThrows(CdTunnelDoorVertical.UnsupportedTunnelDoorVertical.class, h::run);
        assertEquals(CdTunnelDoorVertical.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary());
        assertEquals(routine, e.value()); assertEquals(WHOLE, e.wholeIndex());
        assertEquals(before, h.slot()); assertArrayEquals(bank, h.runtime.capture().savedFlags().copy());
        assertTrue(h.order.isEmpty());
    }

    @Test void soundFailureKeepsRangePrefixButDoesNotWriteFrameOneCallSolidOrDraw() {
        var h = prepared(2); h.edit(o -> o.moveByte(MAP_FRAME, 7).moveWord(TUNNEL_DOOR_TIMER, 0xABCD));
        h.failSound = true; var before = h.slot();
        assertThrows(IllegalStateException.class, h::run);
        assertEquals(before.moveWord(TUNNEL_DOOR_TIMER, 0).moveByte(ROUTINE, 4), h.slot());
        assertEquals(List.of("SOUND"), h.order); assertTrue(h.solids.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void pureSolidFailureKeepsSoundAndFramePrefixWithoutInventingACommitOrDrawing() {
        var h = side(); var p = new P(); p.routine = 2; p.x = 0x00FA8000; p.flags = 0; p.yv = 0; p.push = 7;
        h.player = p.player(); var beforePlayer = h.player;
        var e = assertThrows(CdSolidObject.UnsupportedSolidObject.class, h::run);
        assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS, e.boundary());
        assertEquals(4, h.slot().routine()); assertEquals(1, h.slot().unsignedByte(MAP_FRAME));
        assertEquals(0, h.slot().word(TUNNEL_DOOR_TIMER)); assertEquals(beforePlayer, h.player);
        assertEquals(List.of("SOUND"), h.order); assertTrue(h.solids.isEmpty());
    }

    @Test void failedCommitKeepsTheAlreadyPublishedPrefixAndNeverReplaysSound() {
        var h = side(); h.failCommit = true; var player = h.player;
        assertThrows(IllegalStateException.class, h::run);
        assertEquals(4, h.slot().routine()); assertEquals(1, h.slot().unsignedByte(MAP_FRAME));
        assertEquals(0, h.slot().word(TUNNEL_DOOR_TIMER)); assertEquals(player, h.player);
        assertEquals(List.of("SOUND", "SOLID"), h.order); assertEquals(1, h.sounds.size()); assertTrue(h.draws.isEmpty());
    }

    @Test void missingObjectPublicationIsDetectedBeforeDrawing() {
        var h = side(); h.publishObject = false;
        assertThrows(IllegalStateException.class, h::run);
        assertEquals(0, h.slot().flags()); assertTrue(h.draws.isEmpty());
        assertEquals(0x20, h.solids.getFirst().result().object().flags());
    }

    @Test void missingOtherSlotPublicationIsDetectedAfterTheActualA0CommitBeforeDrawing() {
        var h = prepared(2); var p = new P(); p.routine = 2; p.y = 0x00561234; p.flags = 8; p.stand = 41;
        h.player = p.player(); h.pool().write(h.pool().state(41).moveByte(ID, 0x12).moveByte(FLAGS, 0xA8));
        h.publishOthers = false;
        assertThrows(IllegalStateException.class, h::run);
        assertEquals(8, h.slot().flags()); assertEquals(0xA8, h.pool().state(41).flags()); assertTrue(h.draws.isEmpty());
    }

    @Test void realResultCannotBePublishedIntoAnotherPoolsSameNumberedSlot() {
        var h = side(); h.foreignCommit = true;
        assertThrows(IllegalArgumentException.class, h::run);
        assertEquals(4, h.slot().routine()); assertEquals(1, h.slot().unsignedByte(MAP_FRAME));
        assertEquals(0, h.slot().flags()); assertTrue(h.draws.isEmpty());
    }

    @Test void drawFailureRetainsResetPrefixAndActualBitClearWithoutDespawning() {
        var h = prepared(4); h.edit(o -> o.moveWord(X, 0x380).moveByte(SAVED_FLAGS_ID, 7)); seed(h, 7);
        h.failDraw = true; var before = h.slot(); var bank = h.runtime.capture().savedFlags().copy();
        assertThrows(IllegalStateException.class, h::run);
        assertEquals(before.moveByte(TUNNEL_DOOR_TIMER_HIGH, 8).moveByte(SPRITE_FLAGS, 4), h.slot());
        assertArrayEquals(bank, h.runtime.capture().savedFlags().copy()); assertEquals(List.of("DRAW"), h.order);
    }

    @Test void constructedAssetsRequireTheExactStaticTwoFrameShapeAndBoundedNativePointer() {
        var a = assets(tiles());
        assertEquals(28, a.visual().mappings().length()); assertEquals(0, a.visual().mappingTableOffset());
        assertArrayEquals(MAPS, a.visual().mappings().copy()); assertTrue(a.visual().animations().isEmpty());
        assertEquals(0, a.visual().animationTable().length());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-24, 1, 0x1804, -4),
                new CdObjectVisualAssets.Piece(-8, 3, 0x1800, -4)), a.visual().mappingFrames().getFirst().pieces());
        assertThrows(IllegalArgumentException.class, () -> assets(new byte[73]));
        assertThrows(IllegalArgumentException.class, () -> new CdTunnelDoorVertical.Assets(a.visual(), 0, a.objectTileIds()));
        assertThrows(IllegalArgumentException.class, () -> new CdTunnelDoorVertical.Assets(a.visual(), MAP + 1, a.objectTileIds()));
        assertThrows(IllegalArgumentException.class, () -> new CdTunnelDoorVertical.Assets(a.visual(), 0xFFFFF0, a.objectTileIds()));
        var one = CdObjectVisualAssets.decodeStatic(bytes(0, 2, 0, 0), 1, 12);
        assertThrows(IllegalArgumentException.class, () -> new CdTunnelDoorVertical.Assets(one, MAP, a.objectTileIds()));
    }

    @Test void explicitWidthsWrongIdReservedSlotAndNullPortsRejectWithoutWrites() {
        var h = prepared(2); h.edit(o -> o.moveByte(ID, 0x34)); var before = h.slot();
        assertThrows(IllegalArgumentException.class, h::run); assertEquals(before, h.slot());
        assertThrows(IndexOutOfBoundsException.class, () -> h.body.run(31, new CdTunnelDoorVertical.Globals(1, 0, 128), h.inputs()));
        assertThrows(IllegalArgumentException.class, () -> new CdTunnelDoorVertical.Globals(256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdTunnelDoorVertical.Globals(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdTunnelDoorVertical.Globals(0, 0, 65536));
        assertThrows(NullPointerException.class, () -> new CdTunnelDoorVertical.SolidInputs(null, h.gates, h.globals));
        assertThrows(NullPointerException.class, () -> new CdTunnelDoorVertical(h.runtime, assets(tiles()), h.solid,
                h::commit, h::draw, null));
        assertThrows(NullPointerException.class, () -> h.body.run(WHOLE, null, h.inputs()));
        assertThrows(NullPointerException.class, () -> h.body.run(WHOLE, new CdTunnelDoorVertical.Globals(1, 0, 128), null));
        assertTrue(h.order.isEmpty());
    }

    @Test void orderedEffectsAreImmutableCompletedObservationsWithNoAnimationCall() {
        var h = side(); var r = h.run();
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
        assertEquals(List.of("MAIN", "FM_A4", "SOLID_OBJECT", "DRAW_OBJECT", "CHECK_OBJ_DESPAWN"), trace(r));
        assertEquals(List.of("SOUND", "SOLID", "DRAW"), h.order);
        assertEquals(h.solids.getFirst().result(), assertInstanceOf(CdTunnelDoorVertical.Solid.class, r.effects().get(2)).result());
    }

    private static Harness prepared(int routine) {
        var h = new Harness(assets(tiles())); h.edit(o -> normal(o, routine)); return h;
    }
    private static Harness side() {
        var h = prepared(2); var p = new P(); p.routine = 2; p.x = 0x00FA8000; p.flags = 0; p.yv = 0;
        h.player = p.player(); return h;
    }
    private static CdObjectState normal(CdObjectState o, int routine) {
        return o.moveByte(ID, 0x35).moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, 0x84)
                .moveWord(X, 0x100).moveWord(Y, 0x80).moveWord(X_FRACTION, 0x4321).moveWord(Y_FRACTION, 0x8765)
                .moveByte(WIDTH, 4).moveByte(Y_RADIUS, 24).moveByte(PRIORITY, 1).moveLong(MAPPINGS, MAP)
                .moveWord(TILE, 0x8328).moveByte(MAP_FRAME, 7);
    }
    private static CdObjectState dirtyInit(CdObjectState o) {
        for (var f : CdObjectState.ByteField.values()) o = o.moveByte(f, 0xB9);
        for (var f : CdObjectState.WordField.values()) o = o.moveWord(f, 0xA65C);
        for (var f : CdObjectState.LongField.values()) o = o.moveLong(f, 0x93E7516B);
        return o.moveByte(ID, 0x35).moveByte(ROUTINE, 0).moveByte(SPRITE_FLAGS, 0x31)
                .moveByte(SUBTYPE2, 0).moveByte(SAVED_FLAGS_ID, 0).moveByte(MAP_FRAME, 9)
                .moveWord(X, 0x100).moveWord(Y, 0x80).moveWord(X_FRACTION, 0x4321).moveWord(Y_FRACTION, 0x8765)
                .moveWord(TUNNEL_DOOR_TIMER, 0x12AB);
    }
    private static void seed(Harness h, int id) {
        for (int period = 0; period < 3; period++) {
            h.runtime.bsetSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.LOADED);
            h.runtime.bsetSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            h.runtime.bsetSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.MEMBER_3);
            h.runtime.bsetSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.MEMBER_7);
        }
    }
    private static List<String> trace(CdTunnelDoorVertical.Result r) {
        return r.effects().stream().map(e -> switch (e) {
            case CdTunnelDoorVertical.RoutineEntered x -> x.routine().name();
            case CdTunnelDoorVertical.Solid x -> x.result().entry().name();
            case CdTunnelDoorVertical.PlayedFm x -> x.sound().name();
            case CdTunnelDoorVertical.CallReturned x -> x.call().name();
            case CdTunnelDoorVertical.LoadedCleared x -> "UNLOAD:" + x.savedId() + ":" + x.period();
            case CdTunnelDoorVertical.Deleted x -> "DELETE";
        }).toList();
    }
    private static CdTunnelDoorVertical.Assets assets(byte[] tileIds) {
        return new CdTunnelDoorVertical.Assets(CdObjectVisualAssets.decodeStatic(MAPS, 2, 12), MAP, CdAssetBytes.copyOf(tileIds));
    }
    /** Object Tile IDs (Act 1 Present).asm:8-46: 18 relative roots followed by 19 source words. */
    private static byte[] tiles() {
        int[] words = {36, 38, 40, 44, 46, 48, 58, 52, 56, 54, 50, 60, 62, 64, 66, 68, 70, 72,
                0x23A0, 0x23B0, 0x2409, 0x2370, 0x2000, 0x2428, 0x4334, 0x0320, 0, 0x4000, 0x4000,
                0x0409, 0x0374, 0x8328, 0, 0, 0x0490, 0x03E4, 0};
        var out = new byte[74]; for (int i = 0; i < words.length; i++) word(out, i * 2, words[i]); return out;
    }
    private static CdObjectPlacements layout() {
        var b = ByteBuffer.allocate(34);
        b.putShort((short) 12).putShort((short) 28).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        b.putShort((short) 0x083C).putShort((short) 0x0228).put((byte) 0xB5).put((byte) 0).put((byte) 0xE0).put((byte) 0);
        b.putShort((short) 0xFFFF).putInt(0).putShort((short) 0); b.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout());
        final CdSolidObject solid = terrainSolid();
        final CdTunnelDoorVertical body;
        final List<CdTunnelDoorVertical.SolidCall> solids = new ArrayList<>();
        final List<CdTunnelDoorVertical.FmSound> sounds = new ArrayList<>();
        final List<CdPlayerTouchResponse.FmSound> solidSounds = new ArrayList<>();
        final List<CdObjectState> soundImages = new ArrayList<>(), draws = new ArrayList<>();
        final List<CdObjectDrawQueue.EnqueueResult> drawResults = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        CdSolidObject.Player player = new P().player();
        CdSolidObject.Globals globals = new CdSolidObject.Globals(0, 4, 0x12, 0x34);
        CdSolidObject.Gates gates = new CdSolidObject.Gates(0, 0);
        boolean failSound, failCommit, failDraw, foreignCommit, publishObject = true, publishOthers = true;
        int camera;
        Harness(CdTunnelDoorVertical.Assets a) {
            runtime.resetSavedObjectFlags();
            body = new CdTunnelDoorVertical(runtime, a, solid, this::commit, this::draw, this::sound);
        }
        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); }
        CdTunnelDoorVertical.SolidInputs inputs() { return new CdTunnelDoorVertical.SolidInputs(player, gates, globals); }
        CdTunnelDoorVertical.Result run() { return run(1, 0, 128); }
        CdTunnelDoorVertical.Result run(int rawZone, int rawDirection, int camera) {
            this.camera = camera;
            return body.run(WHOLE, new CdTunnelDoorVertical.Globals(rawZone, rawDirection, camera), inputs());
        }
        void commit(CdTunnelDoorVertical.SolidCall c) {
            order.add("SOLID"); solids.add(c);
            assertEquals(player, c.expectedPlayer()); assertEquals(globals, c.expectedGlobals());
            assertEquals(CdSolidObject.Entry.SOLID_OBJECT, c.result().entry());
            var images = new ArrayList<CdObjectState>(); images.add(c.result().object()); images.addAll(c.result().otherSlots());
            for (var image : images) {
                assertEquals(c.callCut().slots().get(image.identity().dynamicIndex()), pool().state(image.identity().wholeIndex()));
            }
            if (failCommit) throw new IllegalStateException("constructed commit rejection");
            if (foreignCommit) { new CdObjectPool().write(c.result().object()); return; }
            if (publishObject) pool().write(c.result().object());
            if (publishOthers) c.result().otherSlots().forEach(pool()::write);
            player = c.result().player(); globals = c.result().globals();
            solidSounds.addAll(c.result().fmRequests());
        }
        void sound(CdTunnelDoorVertical.FmSound sound) {
            order.add("SOUND"); sounds.add(sound); soundImages.add(slot());
            if (failSound) throw new IllegalStateException("constructed FM failure");
        }
        void draw(CdObjectPool pool, CdObjectState.Identity identity) {
            assertSame(pool(), pool); assertEquals(WHOLE, identity.wholeIndex());
            order.add("DRAW"); var o = pool.state(identity.wholeIndex()); draws.add(o);
            var slots = new CdObjectDrawQueue.CdDrawSlots() {
                public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                    var live = pool.state(ref.wholeIndex());
                    return new CdObjectDrawQueue.LiveDrawSlot(live.id(), live.spriteFlags(), live.centreX(), live.centreY(),
                            live.word(X_FRACTION), live.longBits(MAPPINGS), live.unsignedByte(MAP_FRAME), live.word(TILE));
                }
                public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
                    var live = pool.state(ref.wholeIndex());
                    pool.write(live.moveByte(SPRITE_FLAGS, (live.spriteFlags() & 127) | (value ? 128 : 0)));
                }
            };
            try (var queue = new CdObjectDrawQueue(slots, (address, staticPiece, frame) -> {
                throw new AssertionError("DrawObject must not resolve mappings before DrawObjects");
            }, new byte[640])) {
                queue.beginPass();
                drawResults.add(queue.drawObject(new CdObjectDrawQueue.DrawCallCut(
                        new CdObjectDrawQueue.CdSlotRef(WHOLE), o.spriteFlags(), o.centreX(), o.centreY(),
                        o.unsignedByte(Y_RADIUS), o.unsignedByte(WIDTH), o.unsignedByte(PRIORITY)),
                        new CdObjectDrawQueue.CameraWords(camera, 0)));
            }
            if (failDraw) throw new IllegalStateException("constructed post-DrawObject failure");
        }
    }

    /** Every player field supplied explicitly; default DEAD gates only shared Solid, never Door range work. */
    private static final class P {
        int x = 0x01008000, y = 0x00801234, xv = 0x123, yv = 0x400, gv = 0, routine = 6, angle = 0x10, flags = 2;
        int spr = 4, xr = 9, yr = 19, anim = 0, prev = 0, jump = 1, charge = 0, lock = 0, stick = 0, ctrl = 0;
        int pri = 0x56, sec = 0x78, tile = 0x780, priority = 2, hurt = 0, rot = 0, push = 0, stand = 0;
        CdSolidObject.Player player() {
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x, y, xv, yv, gv,
                    routine, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec),
                    tile, priority, hurt, rot), 1, push, stand);
        }
    }

    /** Existing Solid format-decoder fixture: open-air FG, no injected floor/wall/kill answers. */
    private static CdSolidObject terrainSolid() {
        byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
        ids[1] = 1; ids[2] = 2; Arrays.fill(heights, 16, 32, (byte) 16); Arrays.fill(widths, 32, 48, (byte) 16);
        angles[2] = (byte) 0xC0;
        var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
        byte[] animations = new byte[578];
        for (int i = 0; i < 60; i++) word(animations, i * 2, 120);
        animations[122] = (byte) 0xFF;
        byte[] mappings = new byte[398], dplcs = new byte[398];
        for (int i = 0; i < 198; i++) { word(mappings, i * 2, 396); word(dplcs, i * 2, 396); }
        var visual = CdSonicVisualAssets.decode(mappings, dplcs, new byte[69472]);
        byte[] atan = new byte[257];
        for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8); // atan[0]=0; atan[256]=32, decoder contract.
        var a = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                new byte[4], new byte[640], atan, visual);
        return new CdSolidObject(new CdPlayerMovement(terrain, a));
    }
    private static byte[] bytes(int... values) {
        var out = new byte[values.length]; for (int i = 0; i < values.length; i++) out[i] = (byte) values[i]; return out;
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
}
