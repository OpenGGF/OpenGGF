package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.ENEMY_POINTS;
import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.TOUCH_DIMENSIONS;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual configured USA B table binding plus pure component scan/response witnesses.
 * Constructed dynamic slots are component inputs, never an authenticated B World or object pass.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Player_ObjCollide (Level/Palmtree Panic/Player Object Collision.asm:7-98),
 * Player_TouchEnemy.KillEnemy (:155-197), EnemyPoints (:200-204), ObjColSizes (:398-461).
 * All Java discovery, RED, compilation and positive execution are initially UNRUN.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bTouchTables {
    // Complete independent native declaration expansion, selector 1 first. Half sizes are unsigned bytes.
    private static final int[][] NATIVE_DIMENSIONS = {
            {0x14, 0x14}, {0x12, 0x0C}, {0x0C, 0x10}, {0x04, 0x10}, {0x0C, 0x12}, {0x10, 0x10},
            {0x06, 0x06}, {0x18, 0x0C}, {0x0C, 0x10}, {0x10, 0x0C}, {0x08, 0x08}, {0x14, 0x10},
            {0x14, 0x08}, {0x0E, 0x0E}, {0x18, 0x18}, {0x28, 0x10}, {0x10, 0x18}, {0x08, 0x10},
            {0x20, 0x70}, {0x40, 0x20}, {0x80, 0x20}, {0x20, 0x20}, {0x08, 0x08}, {0x04, 0x04},
            {0x20, 0x08}, {0x0C, 0x0C}, {0x08, 0x04}, {0x18, 0x04}, {0x28, 0x04}, {0x04, 0x08},
            {0x04, 0x18}, {0x04, 0x28}, {0x04, 0x20}, {0x18, 0x18}, {0x0C, 0x18}, {0x48, 0x08},
            {0x08, 0x0C}, {0x10, 0x08}, {0x20, 0x10}, {0x20, 0x10}, {0x08, 0x10}, {0x10, 0x10},
            {0x0C, 0x0C}, {0x10, 0x10}, {0x04, 0x04}, {0x10, 0x10}, {0x16, 0x1A}, {0, 0},
            {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0},
            {0x28, 0x24}, {0x12, 0x11}, {0x20, 0x18}, {0x0C, 0x14}, {0x20, 0x0C}, {0x0C, 0x10}
    };
    private static final byte[] NATIVE_POINTS = HexFormat.of().parseHex("000a001400320064");
    private static final String DIMENSIONS_SHA = "658a42cd58f3c61fc034da9e81c25656b9dc40d923ceed515e14b56672b1581d";
    private static final String POINTS_SHA = "75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5";

    @Test void appendedViewsAreCompletePastLocalRawTablesSelectedByNativeOperands() throws Exception {
        var f = retail(); var a = f.assets();
        assertEquals(84, CdR11bAssets.Asset.values().length);
        assertEquals(List.of(TOUCH_DIMENSIONS, ENEMY_POINTS), Arrays.asList(CdR11bAssets.Asset.values()).subList(70, 72));
        var dimensions = a.view(TOUCH_DIMENSIONS); var points = a.view(ENEMY_POINTS);
        assertEquals(70, TOUCH_DIMENSIONS.ordinal()); assertEquals(71, ENEMY_POINTS.ordinal());
        assertEquals("ObjColSizes", TOUCH_DIMENSIONS.nativeLabel()); assertEquals("EnemyPoints", ENEMY_POINTS.nativeLabel());
        assertSame(TOUCH_DIMENSIONS, dimensions.asset()); assertSame(ENEMY_POINTS, points.asset());
        assertEquals(TOUCH_DIMENSIONS.range(), dimensions.range()); assertEquals(ENEMY_POINTS.range(), points.range());
        assertEquals(CdNativeFile.R11B, dimensions.range().file()); assertEquals(CdNativeFile.R11B, points.range().file());
        assertEquals(com.openggf.game.soniccd.media.CdMmdImage.Domain.ORIGINAL_WORD_RAM, dimensions.range().domain());
        assertEquals(com.openggf.game.soniccd.media.CdMmdImage.Domain.ORIGINAL_WORD_RAM, points.range().domain());
        assertEquals(0x6ECA, dimensions.range().fileOffset()); assertEquals(0x206ECA, dimensions.range().nativeAddress());
        assertEquals(126, dimensions.range().length()); assertEquals(0x206F48, dimensions.range().endExclusive());
        assertEquals(0x6CE0, points.range().fileOffset()); assertEquals(0x206CE0, points.range().nativeAddress());
        assertEquals(8, points.range().length()); assertEquals(0x206CE8, points.range().endExclusive());
        assertEquals(DIMENSIONS_SHA, sha(dimensions.bytes().copy())); assertEquals(POINTS_SHA, sha(points.bytes().copy()));
        assertArrayEquals(nativeDimensionBytes(), dimensions.bytes().copy()); assertArrayEquals(NATIVE_POINTS, points.bytes().copy());
        // Actual B operands authenticated independently of A addresses or matching table contents.
        assertArrayEquals(HexFormat.of().parseHex("0240003fd04045f900206eca45f200fe7200121a"),
                f.programs().r11b().readFile(0x6B64, 20));
        assertArrayEquals(HexFormat.of().parseHex("303b005a"), f.programs().r11b().readFile(0x6C84, 4));
    }

    @Test void allSixtyThreePairsAndFourWordsBindExistingPureReaders() throws Exception {
        var a = retail().assets(); var bytes = a.view(TOUCH_DIMENSIONS).bytes();
        var pairs = CdR11aObjectAssets.decodeTouchDimensions(bytes.copy());
        assertEquals(63, NATIVE_DIMENSIONS.length); assertEquals(63, pairs.size());
        for (int i = 0; i < NATIVE_DIMENSIONS.length; i++) {
            assertEquals(NATIVE_DIMENSIONS[i][0], pairs.get(i).halfWidth(), "selector " + (i + 1));
            assertEquals(NATIVE_DIMENSIONS[i][1], pairs.get(i).halfHeight(), "selector " + (i + 1));
        }
        assertEquals(0x80, pairs.get(20).halfWidth()); assertEquals((byte) 0x80, bytes.signedByte(40));
        for (int selector = 48; selector <= 57; selector++) {
            assertEquals(new CdR11aObjectAssets.TouchSize(0, 0), pairs.get(selector - 1));
        }
        var points = a.view(ENEMY_POINTS).bytes(); int[] words = {10, 20, 50, 100};
        for (int i = 0; i < words.length; i++) { assertEquals(words[i], points.unsignedWord(2 * i)); }
        assertDoesNotThrow(() -> new CdPlayerObjectTouch(pairs));
        assertDoesNotThrow(() -> new CdPlayerTouchResponse(points));
        assertThrows(UnsupportedOperationException.class, () -> pairs.clear());
        byte[] copy = bytes.copy(); copy[40] = 0; assertEquals(0x80, a.view(TOUCH_DIMENSIONS).bytes().unsignedByte(40));
        assertThrows(ReadOnlyBufferException.class, () -> points.buffer().put(0, (byte) 1));
    }

    @Test void allNativeSelectorsUseTheirLiteralUnsignedDimensionsAtInclusiveEdges() throws Exception {
        var scanner = scanner(retail().assets()); var player = playerCut();
        for (int selector = 1; selector <= 63; selector++) {
            int halfWidth = NATIVE_DIMENSIONS[selector - 1][0], halfHeight = NATIVE_DIMENSIONS[selector - 1][1];
            var pool = new CdObjectPool();
            place(pool, 32, selector, 0x2008 + halfWidth, 0x2000);
            var rightEdge = pool.capture(); var selected = scanner.scan(player, rightEdge);
            assertEquals(CdPlayerObjectTouch.Kind.ENEMY, selected.kind(), "right edge selector " + selector);
            assertSame(rightEdge.slots().getFirst(), selected.selectedSlot().orElseThrow());
            place(pool, 32, selector, 0x2009 + halfWidth, 0x2000);
            assertSame(CdPlayerObjectTouch.NoTouch.INSTANCE, scanner.scan(player, pool.capture()), "outside right selector " + selector);
            place(pool, 32, selector, 0x2000, 0x2010 + halfHeight);
            assertEquals(CdPlayerObjectTouch.Kind.ENEMY, scanner.scan(player, pool.capture()).kind(), "bottom edge selector " + selector);
            place(pool, 32, selector, 0x2000, 0x2011 + halfHeight);
            assertSame(CdPlayerObjectTouch.NoTouch.INSTANCE, scanner.scan(player, pool.capture()), "outside bottom selector " + selector);
        }
    }

    @Test void zeroTypeAndOffscreenSkipBeforeUnboundedSelectorZeroIsRejected() throws Exception {
        var scanner = scanner(retail().assets()); var pool = new CdObjectPool();
        place(pool, 32, 0, 0x2000, 0x2000);
        assertSame(CdPlayerObjectTouch.NoTouch.INSTANCE, scanner.scan(playerCut(), pool.capture()));
        for (int collisionType : new int[]{0x40, 0x80, 0xC0}) {
            place(pool, 32, collisionType, 0x7000, 0x7000); // Reached selector rejection precedes geometry.
            var cut = pool.capture();
            assertThrows(IllegalArgumentException.class, () -> scanner.scan(playerCut(), cut));
            assertEquals(cut.slots(), pool.capture().slots());
            pool.write(pool.state(32).moveByte(SPRITE_FLAGS, 4));
            assertSame(CdPlayerObjectTouch.NoTouch.INSTANCE, scanner.scan(playerCut(), pool.capture()));
        }
    }

    @Test void rawTableViewsAndExactReadersKeepTheirFiniteBoundaries() throws Exception {
        var a = retail().assets();
        for (var asset : List.of(TOUCH_DIMENSIONS, ENEMY_POINTS)) {
            assertFalse(asset.isPlcSource()); assertTrue(asset.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values()) {
                assertThrows(IllegalArgumentException.class, () -> a.readerView(asset, kind));
            }
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
        var dimensions = a.view(TOUCH_DIMENSIONS).bytes(); var points = a.view(ENEMY_POINTS).bytes();
        assertThrows(IndexOutOfBoundsException.class, () -> dimensions.unsignedByte(-2));
        assertThrows(IndexOutOfBoundsException.class, () -> dimensions.unsignedByte(126));
        assertThrows(IndexOutOfBoundsException.class, () -> dimensions.unsignedWord(125));
        assertThrows(IndexOutOfBoundsException.class, () -> dimensions.slice(-2, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> dimensions.slice(126, 1));
        assertEquals(0x10, dimensions.unsignedByte(125));
        for (int length : new int[]{0, 124, 125, 127, 128}) {
            assertThrows(IllegalArgumentException.class, () -> CdR11aObjectAssets.decodeTouchDimensions(Arrays.copyOf(dimensions.copy(), length)));
        }
        assertThrows(NullPointerException.class, () -> CdR11aObjectAssets.decodeTouchDimensions(null));
        var pairs = CdR11aObjectAssets.decodeTouchDimensions(dimensions.copy());
        assertThrows(NullPointerException.class, () -> new CdPlayerObjectTouch(null));
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerObjectTouch(pairs.subList(0, 62)));
        var extra = new ArrayList<>(pairs); extra.add(pairs.getFirst());
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerObjectTouch(extra));
        for (var bad : List.of(new CdR11aObjectAssets.TouchSize(-1, 0), new CdR11aObjectAssets.TouchSize(0, 256))) {
            var invalid = new ArrayList<>(pairs); invalid.set(0, bad);
            assertThrows(IllegalArgumentException.class, () -> new CdPlayerObjectTouch(invalid));
        }
        assertThrows(NullPointerException.class, () -> new CdPlayerTouchResponse(null));
        for (int length : new int[]{0, 6, 7, 9, 10}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPlayerTouchResponse(CdAssetBytes.copyOf(Arrays.copyOf(points.copy(), length))));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> points.unsignedWord(-2));
        assertThrows(IndexOutOfBoundsException.class, () -> points.unsignedWord(7));
        assertThrows(IndexOutOfBoundsException.class, () -> points.unsignedWord(8));
        // The raw byte container permits odd offsets. Native response admission owns the odd-address stop.
        assertEquals(0x0A00, points.unsignedWord(1)); assertEquals(100, points.unsignedWord(6));
        assertEquals(19, a.loadPlan().plcs().size()); assertEquals(4, a.loadPlan().sections().size());
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(x -> x.readerProfile().isPresent()).count());
    }

    @Test void actualPastPointsDriveTheFourBigEndianWordsCapThresholdAndWrap() throws Exception {
        var a = retail().assets(); var scanner = scanner(a); var response = new CdPlayerTouchResponse(a.view(ENEMY_POINTS).bytes());
        // old chain, points, new wrapped chain, final points-frame; literals follow native :155-170.
        int[][] vectors = {{0, 10, 2, 0}, {2, 20, 4, 2}, {4, 50, 6, 4}, {6, 100, 8, 6},
                {0x1C, 100, 0x1E, 6}, {0x1E, 1000, 0x20, 0xA}, {0xFFFE, 100, 0, 6}, {0xFFFF, 100, 1, 6}};
        for (int[] row : vectors) {
            var pool = new CdObjectPool(); place(pool, 32, 1, 0x2000, 0x2000); var cut = pool.capture();
            var selected = assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class, scanner.scan(playerCut(), cut));
            var result = response.respond(selected, actor(), globals(row[0]), world(), cut);
            assertEquals(CdPlayerTouchResponse.Exit.KILL_ENEMY, result.exit());
            assertEquals(row[1], result.world().score()); assertEquals(row[2], result.player().scoreChain());
            assertEquals(List.of(new CdPlayerTouchResponse.AddPoints(row[1], false),
                    new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_DESTROY)), result.calls());
            assertEquals(1, result.changedSlots().size());
            assertEquals(row[3], result.changedSlots().getFirst().word(EXPLOSION_POINTS_FRAME));
            assertEquals(cut.slots(), pool.capture().slots(), "component response never commits a pool");
        }
    }

    @Test void oddNativePointsIndicesStopWithoutReturningOrPublishingChanges() throws Exception {
        var a = retail().assets(); var scanner = scanner(a); var response = new CdPlayerTouchResponse(a.view(ENEMY_POINTS).bytes());
        for (int odd : new int[]{1, 3, 5}) {
            var pool = new CdObjectPool(); place(pool, 32, 1, 0x2000, 0x2000); var cut = pool.capture();
            var selected = assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class, scanner.scan(playerCut(), cut));
            var failure = assertThrows(CdPlayerTouchResponse.UnsupportedResponse.class,
                    () -> response.respond(selected, actor(), globals(odd), world(), cut));
            assertEquals(CdPlayerTouchResponse.UnsupportedBoundary.ODD_ENEMY_POINTS_ADDRESS, failure.boundary());
            assertEquals(cut.slots(), pool.capture().slots());
            assertEquals(0x3D, pool.state(32).id()); assertEquals(0, pool.state(32).word(EXPLOSION_POINTS_FRAME));
        }
    }

    private static byte[] nativeDimensionBytes() {
        byte[] bytes = new byte[126];
        for (int i = 0; i < NATIVE_DIMENSIONS.length; i++) {
            bytes[2 * i] = (byte) NATIVE_DIMENSIONS[i][0]; bytes[2 * i + 1] = (byte) NATIVE_DIMENSIONS[i][1];
        }
        return bytes; // Serialization of independent source literals, never values produced by a gameplay helper.
    }
    private static CdPlayerObjectTouch scanner(CdR11bAssets a) {
        return new CdPlayerObjectTouch(CdR11aObjectAssets.decodeTouchDimensions(a.view(TOUCH_DIMENSIONS).bytes().copy()));
    }
    private static CdPlayerObjectTouch.PlayerCut playerCut() { return new CdPlayerObjectTouch.PlayerCut(0x2000, 0x2000, 0x13, 0, 0); }
    private static void place(CdObjectPool pool, int whole, int collisionType, int x, int y) {
        pool.write(pool.state(whole).moveByte(ID, 0x3D).moveByte(SPRITE_FLAGS, 0x84).moveByte(COLLISION_TYPE, collisionType)
                .moveWord(X, x).moveWord(Y, y));
    }
    private static CdPlayerTouchResponse.Actor actor() {
        return new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(0x20001234, 0x20005678, 0, 0, 0,
                2, 0, 0, 4, 9, 0x13, 2, 2, 0, 0, 0, 0, 0, 0, 0), 0x780, 2, 0, 0);
    }
    private static CdPlayerTouchResponse.PlayerGlobals globals(int chain) {
        return new CdPlayerTouchResponse.PlayerGlobals(0, 0, 0, 0, 0, 0, chain, 0);
    }
    private static CdPlayerTouchResponse.WorldGlobals world() { return new CdPlayerTouchResponse.WorldGlobals(0, 3, 0, 0, 5000, 0); }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets) { }
    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Actual USA media requires soniccd.media.root");
        if (retained != null && root.equals(retainedRoot)) { return retained; }
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must name exactly one existing CUE");
            cue = cues.getFirst();
        }
        CdTimeWarpPrograms programs;
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            programs = CdTimeWarpPrograms.fromPrograms(owner);
        }
        retained = new Fixture(programs, CdR11bAssets.fromPrograms(programs)); retainedRoot = root;
        return retained;
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
