package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test-first B-factory component vectors. Authored against J3a22085590a7f8d680ac5f59a417e315b3ff4b23;
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. ALL Java UNRUN by this author.
 * Tamabboh/Main.asm:7-282 and Object Tile ID Set.asm:7-17 own the literal writes below.
 * Actual immutable USA assets supply B bytes and terrain; every slot/callback here is a
 * constructed component ingress. These are not the natural7851 image, a production World,
 * current pixels, native execution, player-Frame adoption or authenticated invocation proof.
 * The real-floor case observes the shared primitive and checks its caller's branch/nonwrites;
 * it does not independently certify the primitive's numerical terrain answer.
 */
class TestCdTamabbohPastFactory {
    private static final int SLOT = 40;
    private static final int NORMAL_MAP = 0x20B7C0, DAMAGED_MAP = 0x20B7D6, MISSILE_MAP = 0x20B8C8;
    private static final int NORMAL_ANI = 0x20B79A, DAMAGED_ANI = 0x20B7A6;
    private static CdR11bAssets cachedPast;
    private static CdR11aAssets cachedPresent;

    @Test
    void namedFactoryHasSevenArgumentsAndLegacyNullOrderAndAssets74StayIntact() throws Exception {
        assertNotNull(CdTamabboh.class.getMethod("fromPastAssets", CdObjectPlacementRuntime.class,
                CdR11bAssets.class, CdPlayerTerrain.class, CdTamabboh.PrimaryAngleCommit.class,
                CdTamabboh.PlayerWordRead.class, CdTamabboh.PlayFmSound.class, CdTamabboh.DrawObjectCall.class));
        assertEquals(1, CdTamabboh.class.getConstructors().length);
        assertEquals(7, CdTamabboh.class.getConstructors()[0].getParameterCount());
        assertEquals(9, CdTamabboh.Assets.class.getRecordComponents().length);
        assertEquals("placement", assertThrows(NullPointerException.class,
                () -> new CdTamabboh(null, null, null, null, null, null, null)).getMessage());
        var placement = new CdObjectPlacementRuntime(past().placements());
        assertEquals("assets", assertThrows(NullPointerException.class,
                () -> new CdTamabboh(placement, null, null, null, null, null, null)).getMessage());
        assertThrows(NullPointerException.class,
                () -> CdTamabboh.fromPastAssets(placement, null, null, null, null, null, null));
        var a = CdTamabboh.Assets.fromObjectAssets(present().openingObjects());
        assertEquals(74, a.objectTileIds().length());
        assertThrows(IllegalArgumentException.class, () -> new CdTamabboh.Assets(
                past().view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes(), a.normal(), a.damaged(), a.missile(),
                a.normalMappingsAddress(), a.damagedMappingsAddress(), a.missileMappingsAddress(),
                a.normalAnimationAddress(), a.damagedAnimationAddress()));
    }

    @Test
    void normalInitPublishesActualB2403AndAllLiteral64BytesBeforeFirstTerrainRead() throws Exception {
        var h = new Harness(0, false);
        h.rejectAngle = 1;
        var before = h.state();
        var cut = h.placement.capture();
        byte[] expected = initPrefix(before.copyBytes(), false);
        longword(expected, 12, before.longBits(Y_POSITION) + 0x10000);
        assertSame(h.fault, assertThrows(Fault.class, h::run));
        assertArrayEquals(expected, h.state().copyBytes());
        assertArrayEquals(expected, h.angleCalls.getFirst().expectedSource().copyBytes());
        assertEquals(0, h.angleCalls.getFirst().value());
        assertTrue(h.draws.isEmpty()); assertTrue(h.playerCalls.isEmpty()); assertTrue(h.fmCalls.isEmpty());
        assertOtherSlots(cut, h); assertArrayEquals(cut.savedFlags().copy(), h.placement.capture().savedFlags().copy());
    }

    @Test
    void damagedDirtyInitUsesBRootPlus22AndPreservesFractionsAndUnwrittenAliases() throws Exception {
        for (int flip = 0; flip < 4; flip++) {
            var h = new Harness(0xFF, false);
            h.put(dirty(h.state()).moveByte(ID, 0x22).moveByte(ROUTINE, 0).moveByte(SUBTYPE, 0xFF)
                    .moveByte(SUBTYPE2, 0).moveByte(SPRITE_FLAGS, 0xD0 | flip).moveByte(SAVED_FLAGS_ID, 0)
                    .moveLong(X_POSITION, 0x1D601234).moveLong(Y_POSITION, 0x01505678));
            var before = h.state(); byte[] expected = initPrefix(before.copyBytes(), true);
            longword(expected, 12, 0x01515678); h.rejectAngle = 1;
            assertSame(h.fault, assertThrows(Fault.class, h::run));
            assertArrayEquals(expected, h.state().copyBytes());
            assertEquals(0x1234, h.state().word(X_FRACTION)); assertEquals(0x5678, h.state().word(Y_FRACTION));
            assertEquals(0xC7, h.state().unsignedByte(ROUTINE_2));
            assertTrue(h.draws.isEmpty());
        }
    }

    @Test
    void all255SecondaryBytesKeepTypedTileFenceAfterInitPrefixBeforeAnyReadOrFloor() throws Exception {
        for (int secondary = 1; secondary <= 255; secondary++) {
            var h = new Harness(0, false);
            h.put(h.state().moveByte(SUBTYPE2, secondary).moveWord(TILE, 0xBEEF)
                    .moveLong(MAPPINGS, 0x89ABCDEF).moveLong(TAMABBOH_ANIMATION_SCRIPT, 0xFEDCBA98));
            byte[] expected = initHeader(h.state().copyBytes());
            var failure = assertThrows(CdTamabboh.UnsupportedTamabboh.class, h::run);
            assertEquals(CdTamabboh.UnsupportedBoundary.TILE_ENTRY, failure.boundary());
            assertEquals(secondary, failure.value()); assertEquals(SLOT, failure.wholeIndex());
            assertArrayEquals(expected, h.state().copyBytes());
            assertTrue(h.angleCalls.isEmpty()); assertTrue(h.draws.isEmpty());
        }
    }

    @Test
    void positionUsesRealBTerrainPrezeroAndOnlyItsReturnedSignControlsRoutine() throws Exception {
        var h = new Harness(0, false); h.ready(2);
        var before = h.state(); var cut = h.placement.capture();
        var result = h.run();
        var floors = result.effects().stream().filter(CdTamabboh.FloorReturned.class::isInstance)
                .map(CdTamabboh.FloorReturned.class::cast).toList();
        assertEquals(1, floors.size());
        assertFalse(h.angleCalls.isEmpty()); assertEquals(0, h.angleCalls.getFirst().value());
        byte[] expected = before.copyBytes(); longword(expected, 12, before.longBits(Y_POSITION) + 0x10000);
        expected[0x24] = (byte) (floors.getFirst().result().distance() < 0 ? 4 : 2);
        assertArrayEquals(expected, result.state().copyBytes());
        assertEquals(1, h.draws.size()); assertEquals(result.state(), h.draws.getFirst());
        assertFalse(result.effects().stream().anyMatch(e -> e.equals(new CdTamabboh.CallReturned(CdTamabboh.NativeCall.ANIMATE_OBJECT))));
        assertOtherSlots(cut, h); assertTrue(h.playerCalls.isEmpty()); assertTrue(h.fmCalls.isEmpty());
    }

    @Test
    void heldForeignScriptIsUnreadButReachedDirectoryFaultKeepsNativeDecrement() throws Exception {
        var held = new Harness(0, false); held.ready(8);
        held.put(held.state().moveLong(TAMABBOH_ANIMATION_SCRIPT, 0xDEADBEEF)
                .moveByte(ANIMATION_TIME, 1).moveByte(ANIMATION, 0xFE).moveByte(PREVIOUS_ANIMATION, 0xFE)
                .moveByte(ANIMATION_FRAME, 0xE7));
        byte[] expected = held.state().copyBytes(); expected[0x1E] = 0;
        assertArrayEquals(expected, held.run().state().copyBytes());
        assertEquals(1, held.draws.size());
        var reached = new Harness(0, false); reached.ready(8);
        reached.put(reached.state().moveLong(TAMABBOH_ANIMATION_SCRIPT, 0xDEADBEEF).moveByte(ANIMATION_TIME, 0));
        expected = reached.state().copyBytes(); expected[0x1E] = (byte) 0xFF;
        var failure = assertThrows(CdTamabboh.UnsupportedTamabboh.class, reached::run);
        assertEquals(CdTamabboh.UnsupportedBoundary.ANIMATION_POINTER, failure.boundary());
        assertEquals(0xDEADBEEF, failure.value()); assertArrayEquals(expected, reached.state().copyBytes());
        assertTrue(reached.draws.isEmpty());
    }

    @Test
    void bNormalFfAndAllRealFcScriptsUseLiteralTimersIndicesAndRoutineWrites() throws Exception {
        int[][] cases = {{0, NORMAL_ANI, 0, 2, 0x1D, 8, 0, 1},
                {0, NORMAL_ANI, 1, 1, 0x3B, 10, 7, 1},
                {0xFF, DAMAGED_ANI, 0, 16, 5, 10, 7, 16},
                {0xFF, DAMAGED_ANI, 1, 1, 0x0B, 10, 7, 1}};
        for (int[] v : cases) {
            var h = new Harness(v[0], false); h.ready(8);
            h.put(h.state().moveLong(TAMABBOH_ANIMATION_SCRIPT, v[1]).moveByte(ANIMATION, v[2])
                    .moveByte(PREVIOUS_ANIMATION, v[2]).moveByte(ANIMATION_FRAME, v[3])
                    .moveByte(ANIMATION_TIME, 0).moveByte(MAP_FRAME, 7));
            byte[] expected = h.state().copyBytes();
            expected[0x1E] = (byte) v[4]; expected[0x24] = (byte) v[5];
            expected[0x1A] = (byte) v[6]; expected[0x1B] = (byte) v[7];
            if (v[5] == 8) expected[1] = (byte) ((expected[1] & 0xFC) | (expected[0x22] & 3));
            assertArrayEquals(expected, h.run().state().copyBytes());
            assertEquals(1, h.draws.size()); assertTrue(h.angleCalls.isEmpty());
        }
    }

    @Test
    void actualFullPointerSelectsDamagedScriptEvenForNormalSubtype() throws Exception {
        var h = new Harness(0, false); h.ready(8);
        h.put(h.state().moveLong(TAMABBOH_ANIMATION_SCRIPT, DAMAGED_ANI).moveByte(ANIMATION_TIME, 0));
        byte[] expected = h.state().copyBytes();
        expected[0x1E] = 5; expected[0x1B] = 1; expected[0x1A] = 0;
        expected[1] = (byte) ((expected[1] & 0xFC) | (expected[0x22] & 3));
        assertArrayEquals(expected, h.run().state().copyBytes());
        assertEquals(NORMAL_MAP, h.state().longBits(MAPPINGS));
        assertEquals(DAMAGED_ANI, h.state().longBits(TAMABBOH_ANIMATION_SCRIPT));
    }

    @Test
    void missileSubtypeOneIsFirstAndDirtyInitPreservesEveryUnwrittenByte() throws Exception {
        for (int direction : new int[]{0, 1, 0xFF}) {
            var h = new Harness(1, false);
            h.put(dirty(h.state()).moveByte(ID, 0x22).moveByte(ROUTINE, 0).moveByte(SUBTYPE, 1)
                    .moveByte(TAMABBOH_MISSILE_DIRECTION, direction).moveByte(SPRITE_FLAGS, 0x81)
                    .moveWord(TILE, 0xCAFE).moveByte(SUBTYPE2, 0xFF));
            h.good = 1; h.time = 0x80;
            byte[] expected = h.state().copyBytes();
            expected[0x24] = 2; expected[1] |= 4; expected[0x20] = (byte) 0xAD;
            expected[0x16] = expected[0x17] = expected[0x19] = 8;
            longword(expected, 4, MISSILE_MAP); longword(expected, 0x32, 0); longword(expected, 0x36, 0x2000);
            longword(expected, 0x2A, direction == 0 ? 0x20000 : -0x20000); longword(expected, 0x2E, -0x40000);
            var result = h.run(); assertArrayEquals(expected, result.state().copyBytes());
            assertEquals(1, h.draws.size()); assertEquals(0xCAFE, h.state().word(TILE));
            assertTrue(h.angleCalls.isEmpty()); assertTrue(h.playerCalls.isEmpty()); assertTrue(h.fmCalls.isEmpty());
            assertFalse(result.effects().stream().anyMatch(CdTamabboh.ConvertedToExplosion.class::isInstance));
        }
    }

    @Test
    void invisibleMissileDeletesThenDrawsActualClearedImageWithoutParentDespawn() throws Exception {
        var h = new Harness(1, false); h.ready(2); h.put(h.state().moveByte(SPRITE_FLAGS, 4));
        h.good = 1; h.time = 0x80;
        var result = h.run();
        assertTrue(result.deleted()); assertArrayEquals(new byte[64], result.state().copyBytes());
        assertEquals(1, h.draws.size()); assertArrayEquals(new byte[64], h.draws.getFirst().copyBytes());
        assertTrue(h.angleCalls.isEmpty()); assertTrue(h.playerCalls.isEmpty()); assertTrue(h.fmCalls.isEmpty());
        assertFalse(result.effects().stream().anyMatch(e -> e.equals(new CdTamabboh.CallReturned(CdTamabboh.NativeCall.CHECK_OBJ_DESPAWN))));
    }

    @Test
    void fireUsesTwoFreshFirstFreeCandidatesAndCopiesOnlyDirtyChildFields() throws Exception {
        var h = new Harness(0, false); h.ready(10);
        for (int whole = 32; whole < 128; whole++) if (whole != SLOT)
            h.pool().write(dirty(h.pool().state(whole)).moveByte(ID, 2));
        h.pool().write(h.pool().state(32).moveByte(ID, 0));
        h.pool().write(h.pool().state(33).moveByte(ID, 0));
        var parent = h.state(); var child32 = h.pool().state(32); var child33 = h.pool().state(33);
        h.beforeFm = () -> {
            assertEquals(0, h.pool().state(32).id()); assertEquals(0, h.pool().state(33).id());
            assertEquals(4, h.state().routine()); assertEquals(0, h.state().unsignedByte(ANIMATION));
            assertEquals(120, h.state().word(TAMABBOH_COOLDOWN));
        };
        var result = h.run();
        byte[] expectedParent = parent.copyBytes(); expectedParent[0x24] = 4; expectedParent[0x1C] = 0;
        word(expectedParent, 0x34, 120);
        assertArrayEquals(expectedParent, result.state().copyBytes());
        assertArrayEquals(child(child32.copyBytes(), parent, 0), h.pool().state(32).copyBytes());
        assertArrayEquals(child(child33.copyBytes(), parent, 0xFF), h.pool().state(33).copyBytes());
        assertEquals(1, h.fmCalls.size()); assertEquals(32, h.fmCalls.getFirst().firstFreeWholeIndex());
        assertEquals(List.of(32, 33), result.effects().stream().filter(CdTamabboh.MissileCreated.class::isInstance)
                .map(CdTamabboh.MissileCreated.class::cast).map(CdTamabboh.MissileCreated::wholeIndex).toList());
        assertEquals(1, h.draws.size());
    }

    @Test
    void damagedFirePublishesParentPrefixWithoutAllocationOrFm() throws Exception {
        var h = new Harness(0xFF, false); h.ready(10); var cut = h.placement.capture();
        byte[] expected = h.state().copyBytes(); expected[0x24] = 4; expected[0x1C] = 0; word(expected, 0x34, 120);
        assertArrayEquals(expected, h.run().state().copyBytes());
        assertOtherSlots(cut, h); assertTrue(h.fmCalls.isEmpty()); assertEquals(1, h.draws.size());
    }

    @Test
    void normalRangeReadsActualYThenOnlyReachedXAndCutsStayFresh() throws Exception {
        var farY = new Harness(0, false); farY.ready(4); farY.debug = 0;
        farY.py = (farY.state().centreY() + 0x100) & 0xFFFF; farY.rejectAngle = 1;
        assertSame(farY.fault, assertThrows(Fault.class, farY::run));
        assertEquals(List.of(CdTamabboh.PlayerField.Y_WORD), farY.playerCalls.stream().map(CdTamabboh.PlayerReadCall::field).toList());
        var nearY = new Harness(0, false); nearY.ready(4); nearY.debug = 0;
        nearY.py = nearY.state().centreY(); nearY.px = nearY.state().centreX(); nearY.rejectPlayer = 2;
        assertSame(nearY.fault, assertThrows(Fault.class, nearY::run));
        assertEquals(List.of(CdTamabboh.PlayerField.Y_WORD, CdTamabboh.PlayerField.X_WORD),
                nearY.playerCalls.stream().map(CdTamabboh.PlayerReadCall::field).toList());
        assertNotSame(nearY.playerCalls.get(0).callCut(), nearY.playerCalls.get(1).callCut());
        assertEquals(nearY.playerCalls.get(0).callCut().slots(), nearY.playerCalls.get(1).callCut().slots());
        assertTrue(nearY.draws.isEmpty());
    }

    @Test
    void goodFutureUsesRawPeriodPredicateAndConversionSkipsTileAndDraw() throws Exception {
        for (int[] vector : new int[][]{{0, 0}, {0x81, 0}, {1, 0xFF}}) {
            var h = new Harness(vector[1], false);
            h.put(dirty(h.state()).moveByte(ID, 0x22).moveByte(SUBTYPE, vector[1])
                    .moveByte(SUBTYPE2, 0xFF).moveLong(X_POSITION, 0x12345678).moveLong(Y_POSITION, 0x9ABCDEF0));
            h.good = 1; h.time = vector[0];
            byte[] expected = new byte[64]; expected[0] = 0x18;
            word(expected, 8, 0x1234); word(expected, 12, 0x9ABC);
            assertArrayEquals(expected, h.run().state().copyBytes());
            assertTrue(h.draws.isEmpty()); assertTrue(h.angleCalls.isEmpty()); assertTrue(h.fmCalls.isEmpty());
        }
        var allowed = new Harness(0, false); allowed.good = 1; allowed.time = 1; allowed.rejectAngle = 1;
        assertSame(allowed.fault, assertThrows(Fault.class, allowed::run));
        assertEquals(0x2403, allowed.state().word(TILE));
    }

    @Test
    void rawSavedAddressAndWarpClampsUseExistingLinearBankAtActualTail() throws Exception {
        int[][] cases = {{3, 0, 2, 0, 8}, {0x80, 1, 1, 0, 5}, {0xFE, 0, 1, 2, 7}};
        for (int[] v : cases) {
            var h = new Harness(0, false); h.ready(8); h.time = v[0]; h.direction = v[1];
            h.put(h.state().moveByte(SAVED_FLAGS_ID, 1).moveByte(SPRITE_FLAGS, 4));
            h.placement.bsetSavedFlag(v[2], v[3], CdObjectPlacementRuntime.SavedBit.LOADED);
            h.afterDraw = () -> h.put(h.state().moveWord(TAMABBOH_ANCHOR_X, 0x1000));
            var result = h.run(); assertTrue(result.deleted());
            assertArrayEquals(new byte[64], h.state().copyBytes());
            assertEquals(0, h.placement.savedFlag(v[2], v[3]));
            var change = result.effects().stream().filter(CdTamabboh.SavedFlagChanged.class::isInstance)
                    .map(CdTamabboh.SavedFlagChanged.class::cast).findFirst().orElseThrow();
            assertEquals(v[4], change.byteIndex()); assertEquals(0x80, change.previousValue()); assertEquals(0, change.value());
        }
        var a = new Harness(0, true); a.rejectAngle = 1;
        assertSame(a.fault, assertThrows(Fault.class, a::run));
        assertEquals(0x2428, a.state().word(TILE));
        assertEquals(0x20B968, a.state().longBits(MAPPINGS));
        assertEquals(0x20B942, a.state().longBits(TAMABBOH_ANIMATION_SCRIPT));
    }

    @Test
    void bFactoryRetainsReadOnlyPortFullPoolAndReentryGuardsIncludingKnownFinallyLimit() throws Exception {
        for (String port : List.of("ANGLE", "PLAYER", "FM")) {
            var mutation = portHarness(port); mutation.attackPort = port; mutation.mutateSource = true;
            assertThrows(IllegalStateException.class, mutation::run);
            assertEquals(0xA5, mutation.state().flags()); assertTrue(mutation.draws.isEmpty());
            var reentry = portHarness(port); reentry.attackPort = port; reentry.reenter = true;
            assertThrows(IllegalStateException.class, reentry::run); assertTrue(reentry.draws.isEmpty());
            var thrownMutation = portHarness(port); thrownMutation.attackPort = port;
            thrownMutation.mutateSource = true; thrownMutation.throwAfterAttack = true;
            // Existing shared finally verification replaces a simultaneous malicious callback fault.
            // This is a named inherited limitation, not an identical-exception guarantee.
            var verification = assertThrows(IllegalStateException.class, thrownMutation::run);
            assertNotSame(thrownMutation.fault, verification);
        }
    }

    private static Harness portHarness(String port) throws Exception {
        var h = new Harness(0, false); h.ready(port.equals("ANGLE") ? 2 : port.equals("PLAYER") ? 4 : 10);
        if (port.equals("PLAYER")) { h.debug = 0; h.py = h.state().centreY(); h.px = h.state().centreX(); }
        return h;
    }

    private static byte[] initHeader(byte[] bytes) {
        bytes[0x24] = (byte) ((bytes[0x24] + 2) & 255); bytes[1] |= 4;
        bytes[0x18] = 4; bytes[0x20] = 0x2C; bytes[0x17] = bytes[0x19] = 16; bytes[0x16] = 15;
        word(bytes, 0x2A, ((bytes[8] & 255) << 8) | (bytes[9] & 255)); return bytes;
    }
    private static byte[] initPrefix(byte[] bytes, boolean damaged) {
        initHeader(bytes); word(bytes, 2, 0x2403); longword(bytes, 4, damaged ? DAMAGED_MAP : NORMAL_MAP);
        longword(bytes, 0x30, damaged ? DAMAGED_ANI : NORMAL_ANI);
        longword(bytes, 0x2C, damaged ? -0x5000 : -0xA000); return bytes;
    }
    private static byte[] child(byte[] bytes, CdObjectState parent, int direction) {
        bytes[0] = 0x22; bytes[0x28] = 1; word(bytes, 2, parent.word(TILE));
        bytes[0x18] = (byte) (parent.unsignedByte(PRIORITY) + 1);
        word(bytes, 8, parent.centreX()); word(bytes, 12, parent.centreY() - 10); bytes[0x3F] = (byte) direction;
        return bytes;
    }
    private static CdObjectState dirty(CdObjectState state) {
        for (var field : CdObjectState.ByteField.values()) state = state.moveByte(field, 0xA5);
        for (var field : CdObjectState.WordField.values()) state = state.moveWord(field, 0xB6C7);
        for (var field : CdObjectState.LongField.values()) state = state.moveLong(field, 0xD8E9FA0B);
        return state;
    }
    private static void assertOtherSlots(CdObjectPlacementRuntime.Snapshot cut, Harness h) {
        for (int whole = 32; whole < 128; whole++) if (whole != SLOT)
            assertArrayEquals(cut.pool().slots().get(whole - 32).copyBytes(), h.pool().state(whole).copyBytes());
    }

    private static CdR11bAssets past() throws Exception {
        if (cachedPast == null) try (var source = openUsa()) {
            cachedPast = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        return cachedPast;
    }
    private static CdR11aAssets present() throws Exception {
        if (cachedPresent == null) try (var source = openUsa()) {
            cachedPresent = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
        return cachedPresent;
    }
    private static CdProgramAssets openUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "explicit execution of these actual-USA vectors requires soniccd.media.root");
        assertFalse(root.isBlank(), "soniccd.media.root is not an optional/skip prerequisite here");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "exactly one existing original USA CUE is required"); cue = cues.getFirst();
        }
        return CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty());
    }

    private static final class Fault extends RuntimeException { private static final long serialVersionUID = 1L; }

    /** Public-format component ports; they confer no actual Frame/private Visit authority. */
    private static final class Harness {
        final CdObjectPlacementRuntime placement;
        final CdTamabboh body;
        final Fault fault = new Fault();
        final List<CdTamabboh.PrimaryAngleCall> angleCalls = new ArrayList<>();
        final List<CdTamabboh.PlayerReadCall> playerCalls = new ArrayList<>();
        final List<CdTamabboh.FmCall> fmCalls = new ArrayList<>();
        final List<CdObjectState> draws = new ArrayList<>();
        int good, time, direction, debug = 1, camera = 0x1B00, angle = 0x55, px, py;
        int rejectAngle, rejectPlayer;
        String attackPort = "";
        boolean mutateSource, reenter, throwAfterAttack;
        Runnable beforeFm = () -> { }, afterDraw = () -> { };
        Harness(int subtype, boolean present) throws Exception {
            var b = past(); placement = new CdObjectPlacementRuntime(b.placements());
            put(pool().state(SLOT).moveByte(ID, 0x22).moveByte(SUBTYPE, subtype).moveByte(SPRITE_FLAGS, 0x80)
                    .moveLong(X_POSITION, 0x1D601234).moveLong(Y_POSITION, 0x01505678));
            if (present) body = new CdTamabboh(placement, CdTamabboh.Assets.fromObjectAssets(present().openingObjects()),
                    CdPlayerTerrain.fromStageTerrain(b.terrain()), this::angle, this::player, this::fm, this::draw);
            else body = CdTamabboh.fromPastAssets(placement, b, CdPlayerTerrain.fromStageTerrain(b.terrain()),
                    this::angle, this::player, this::fm, this::draw);
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState state() { return pool().state(SLOT); }
        void put(CdObjectState state) { pool().write(state); }
        void ready(int routine) {
            put(state().moveByte(ROUTINE, routine).moveByte(SPRITE_FLAGS, 0x84).moveWord(TILE, 0x2403)
                    .moveLong(MAPPINGS, state().subtype() == 0 ? NORMAL_MAP : DAMAGED_MAP)
                    .moveLong(TAMABBOH_ANIMATION_SCRIPT, state().subtype() == 0 ? NORMAL_ANI : DAMAGED_ANI)
                    .moveWord(TAMABBOH_ANCHOR_X, state().centreX()).moveLong(TAMABBOH_X_DELTA, -0xA000)
                    .moveByte(X_RADIUS, 16).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 15)
                    .moveByte(PRIORITY, 4).moveByte(COLLISION_TYPE, 0x2C).moveByte(ANIMATION_TIME, 1));
        }
        CdTamabboh.Result run() { return body.run(SLOT, new CdTamabboh.Globals(good, time, direction, debug, camera, angle)); }
        void check(CdObjectPool.Snapshot cut, CdObjectState expected) {
            pool().validateRestore(cut); assertEquals(pool().capture().slots(), cut.slots()); assertEquals(state(), expected);
        }
        void attack(String port) {
            if (!attackPort.equals(port)) return;
            if (reenter) run();
            if (mutateSource) put(state().moveByte(FLAGS, 0xA5));
            if (throwAfterAttack) throw fault;
        }
        void angle(CdTamabboh.PrimaryAngleCall call) {
            check(call.callCut(), call.expectedSource()); angleCalls.add(call);
            assertEquals(angle, call.expectedPrimaryAngle());
            if (angleCalls.size() == rejectAngle) throw fault;
            attack("ANGLE"); angle = call.value();
        }
        int player(CdTamabboh.PlayerReadCall call) {
            check(call.callCut(), call.expectedSource()); playerCalls.add(call);
            if (playerCalls.size() == rejectPlayer) throw fault;
            attack("PLAYER"); return call.field() == CdTamabboh.PlayerField.Y_WORD ? py : px;
        }
        void fm(CdTamabboh.FmCall call) {
            check(call.callCut(), call.expectedSource()); fmCalls.add(call);
            assertEquals(pool().findObjSlot().orElseThrow(), call.firstFreeWholeIndex());
            beforeFm.run(); attack("FM");
        }
        void draw(CdObjectPool actual, CdObjectState.Identity identity) {
            assertSame(pool(), actual); assertEquals(SLOT, identity.wholeIndex());
            draws.add(state()); afterDraw.run();
        }
    }

    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void longword(byte[] bytes, int at, int value) { word(bytes, at, value >>> 16); word(bytes, at + 2, value); }
}
