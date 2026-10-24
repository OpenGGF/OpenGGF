package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.level.CdShieldArt;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePresentationRenderer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/** All CPU SATs come from the actual queue; no private constructor, reflection, graphics or native calls. */
class TestCdObjectPresentation {
    /**
     * Game Over/Data/Mappings.asm:1-58 at scddisasm 1ecc5f3af, hand-assembled: a constructed declared input, not
     * media. GAME's two signed words are relative to MapSpr_GameOver (+0), TIME's to MapSpr_TimeOver (+4).
     */
    private static final byte[] GAME_OVER_MAPPINGS = bytes(
            0x00, 0x08, 0x00, 0x14, 0x00, 0x1C, 0x00, 0x28,
            0x02, 0xF8, 0x0D, 0x00, 0x00, 0xB8, 0xF8, 0x0D, 0x00, 0x08, 0xD8, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x10, 0x08, 0xF8, 0x0D, 0x00, 0x18, 0x28, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x00, 0xBC, 0xF8, 0x09, 0x00, 0x08, 0xDC, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x0E, 0x04, 0xF8, 0x0D, 0x00, 0x16, 0x24, 0x00);
    /** The USA R11A oMap longs (Game Over/Main.asm:36,43); the retail mapping test re-proves them from media. */
    private static final int GAME_MAP = 0x20F03E, TIME_MAP = 0x20F042;
    /** MOVE.W #$8544,oTile (:35) selects tile $544: the LVLDMA destination $A880 / 32 (Powerup/Main.asm:173). */
    private static final int WINDOW_TILE = 0x544;
    private static final int LOGICAL = 500;

    @Test void finalAttributeWordIsNotAddedOrPaletteCombinedAgain() {
        var f = new Fixture();
        f.add(32, 2, 0, 128, 128, 0x0800, List.of(new RawPiece(0, 0, 0xF810, 0)));
        var sat = f.build(32); // ADD.W wraps to $0010; converter has no base word argument.
        assertEquals(0x0010, word(sat.bytes(), 4));
        var frame = CdObjectPresentation.convert(sat, id -> new CdObjectPresentation.TilePattern(1000 + id, solid(1)));
        var tile = frame.tiles().getFirst();
        assertEquals(1016, tile.patternId()); assertEquals(0, tile.palette());
        assertFalse(tile.hFlip()); assertFalse(tile.vFlip()); assertFalse(tile.priority());
        assertEquals(0, tile.x()); assertEquals(0, tile.y());
        assertEquals(SpritePresentation.Layer.OBJECT, tile.layer()); assertTrue(tile.priorityShader());
        assertEquals(8, tile.width()); assertEquals(8, tile.height());
        assertFalse(tile.ghost()); assertEquals(0, tile.occlusionMask()); assertFalse(tile.rowClipped());
        assertEquals(List.of(), frame.primitives());
    }

    @Test void fullWordPaletteCarryPriorityAndRawFlipsComeOnlyFromFinalSatBits() {
        int[][] cases = {{0x1FFF, 1, 0x2000}, {0x7FFF, 1, 0x8000}, {0xFFFF, 1, 0},
                {0x6007, 0x1800, 0x7807}, {0xA007, 0x1800, 0xB807}};
        for (int[] row : cases) {
            var f = new Fixture(); f.add(32, 0, 0, 128, 128, row[1], List.of(new RawPiece(0, 0, row[0], 0)));
            var sat = f.build(32); assertEquals(row[2], word(sat.bytes(), 4));
            var tile = CdObjectPresentation.convert(sat, id -> new CdObjectPresentation.TilePattern(id, solid(2))).tiles().getFirst();
            assertEquals(row[2] & 0x7FF, tile.patternId()); assertEquals((row[2] >>> 13) & 3, tile.palette());
            assertEquals((row[2] & 0x8000) != 0, tile.priority());
            assertEquals((row[2] & 0x800) != 0, tile.hFlip()); assertEquals((row[2] & 0x1000) != 0, tile.vFlip());
        }
    }

    @Test void twoByThreePieceUsesColumnMajorPatternOffsetsAndBothFlipsThroughEngineHelper() {
        for (int flips = 0; flips < 4; flips++) {
            var f = new Fixture();
            f.add(32, 0, 0, 148, 158, 0, List.of(new RawPiece(0, 6, 0x600A | (flips << 11), 0)));
            var frame = CdObjectPresentation.convert(f.build(32), id -> new CdObjectPresentation.TilePattern(id, solid(3)));
            // Helper visits screen row then column; native source offsets remain col*3+row.
            assertEquals(List.of(10, 13, 11, 14, 12, 15), frame.tiles().stream().map(SpritePresentation.Tile::patternId).toList());
            for (int index = 0; index < 6; index++) {
                int row = index / 2, col = index % 2;
                var tile = frame.tiles().get(index);
                assertEquals(20 + (((flips & 1) == 0 ? col : 1 - col) * 8), tile.x());
                assertEquals(30 + (((flips & 2) == 0 ? row : 2 - row) * 8), tile.y());
                assertEquals((flips & 1) != 0, tile.hFlip()); assertEquals((flips & 2) != 0, tile.vFlip());
                assertEquals(3, tile.palette());
            }
        }
    }

    @Test void oneFourByFourNativeEntryExpandsSixteenTilesAndMasksEveryPhysicalTileBeforeLookup() {
        var f = new Fixture(); f.add(32, 0, 0, 128, 128, 0, List.of(new RawPiece(0, 15, 0x7FF, 0)));
        var sat = f.build(32); assertEquals(1, sat.spriteCount());
        var requested = new ArrayList<Integer>();
        var frame = CdObjectPresentation.convert(sat, id -> {
            assertTrue(id >= 0 && id <= 0x7FF); requested.add(id);
            return new CdObjectPresentation.TilePattern(10000 + id, solid(4));
        });
        assertEquals(16, frame.tiles().size());
        assertEquals(List.of(0x7FF, 3, 7, 11, 0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14), requested);
        assertEquals(8, frame.tiles().get(4).y()); assertEquals(0, frame.tiles().get(4).x());
    }

    @Test void reverseNativeSatOrderMakesEarlierOpaqueEntryWinWithoutSortingPlayerOrDynamicProvenance() {
        var f = new Fixture();
        f.add(127, 2, 0, 128, 128, 0, List.of(new RawPiece(0, 0, 1, 0), new RawPiece(0, 0, 2, 0)));
        f.add(0, 2, 0, 128, 128, 0, List.of(new RawPiece(0, 0, 3, 0)));
        f.add(32, 2, 0, 128, 128, 0, List.of(new RawPiece(0, 0, 4, 0)));
        var sat = f.build(127, 0, 32);
        assertEquals(List.of(127, 127, 0, 32), sat.provenance().stream().map(p -> p.slotRef().wholeIndex()).toList());
        byte[] before = sat.bytes(); int reads = f.reads, writes = f.writes;
        var frame = CdObjectPresentation.convert(sat, id -> new CdObjectPresentation.TilePattern(id, solid(id)));
        assertEquals(List.of(4, 3, 2, 1), frame.tiles().stream().map(SpritePresentation.Tile::patternId).toList());
        assertEquals(1, paintedPixel(frame, 0, 0), "last opaque painter tile is earliest native SAT entry");
        assertEquals(reads, f.reads); assertEquals(writes, f.writes); assertArrayEquals(before, sat.bytes());
        assertEquals(frame, CdObjectPresentation.convert(sat, id -> new CdObjectPresentation.TilePattern(id, solid(id))));
    }

    @Test void emptyCountIgnoresRetainedRawTailAndNeverReadsAnArtSource() {
        var f = new Fixture(); f.add(32, 0, 0, 128, 128, 0, List.of());
        var sat = f.build(32); assertEquals(0, sat.spriteCount());
        var frame = CdObjectPresentation.convert(sat, id -> { throw new AssertionError("no active tile"); });
        assertEquals(SpritePresentation.Frame.empty(), frame);
        assertEquals((byte) 0xA5, sat.bytes()[4]);
    }

    @Test void normalCoordinateDomainKeepsAllEntriesWithoutASecondViewportCullAndRejectsUnownedYWrap() {
        for (int[] xy : List.of(new int[]{1, 0}, new int[]{511, 511}, new int[]{448, 352})) {
            var f = new Fixture(); f.add(32, 0, 0, xy[0], xy[1], 0, List.of(new RawPiece(0, 0, 0, 0)));
            var frame = CdObjectPresentation.convert(f.build(32), id -> new CdObjectPresentation.TilePattern(id, solid(1)));
            assertEquals(1, frame.tiles().size()); assertEquals(xy[0] - 128, frame.tiles().getFirst().x());
            assertEquals(xy[1] - 128, frame.tiles().getFirst().y());
        }
        for (int y : List.of(512, 0x8000, 0xFFFF)) {
            var f = new Fixture(); f.add(32, 0, 0, 128, y, 0, List.of(new RawPiece(0, 0, 0, 0)));
            assertThrows(UnsupportedOperationException.class, () -> CdObjectPresentation.convert(f.build(32),
                    id -> new CdObjectPresentation.TilePattern(id, solid(1))));
        }
    }

    @Test void rawNibblePackingAgreesWithEnginePatternVersionsAndPixelOrientation() {
        byte[] sega = new byte[32];
        for (int i = 0; i < sega.length; i++) sega[i] = (byte) (i * 7 + 0x12);
        var version = CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(sega));
        var pattern = new Pattern(); pattern.fromSegaFormat(sega);
        assertEquals(SpritePresentationRenderer.version(pattern), version);
        assertEquals(1, SpritePresentationRenderer.pattern(version).getPixel(0, 0));
        assertEquals(2, SpritePresentationRenderer.pattern(version).getPixel(1, 0));
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(new byte[31])));
    }

    @Test void immutablePixelBankAndCompletedFrameKeepOldPixelsAcrossBankChangesAndRebinding() {
        byte[] raw = new byte[32]; Arrays.fill(raw, (byte) 0x12);
        var original = CdObjectPresentation.declaredPixelBank(List.of(new CdObjectPresentation.PixelSpan(7, 100,
                CdAssetBytes.copyOf(raw))));
        var f = new Fixture(); f.add(32, 0, 0, 128, 128, 0, List.of(new RawPiece(0, 0, 7, 0)));
        var sat = f.build(32);
        var frame = CdObjectPresentation.convert(sat, original);
        Arrays.fill(raw, (byte) 0x34);
        var rebound = CdObjectPresentation.declaredPixelBank(List.of(new CdObjectPresentation.PixelSpan(7, 100,
                CdAssetBytes.copyOf(raw))));
        var replacement = CdObjectPresentation.convert(sat, rebound);
        assertNotEquals(frame.patternVersions(), replacement.patternVersions());
        assertEquals(1, paintedPixel(frame, 0, 0)); assertEquals(3, paintedPixel(replacement, 0, 0));
        assertEquals(frame, CdObjectPresentation.convert(sat, original));
        assertThrows(UnsupportedOperationException.class, () -> frame.patternVersions().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.tiles().clear());
    }

    @Test void liveSemanticSourceChangesCannotMutateCompletedVersionsAndConflictingLogicalIdsReject() {
        var f = new Fixture(); f.add(32, 0, 0, 128, 128, 0,
                List.of(new RawPiece(0, 0, 1, 0), new RawPiece(0, 0, 2, 8)));
        var sat = f.build(32); var source = new HashMap<Integer, CdObjectPresentation.TilePattern>();
        source.put(1, new CdObjectPresentation.TilePattern(101, solid(1)));
        source.put(2, new CdObjectPresentation.TilePattern(102, solid(2)));
        var frame = CdObjectPresentation.convert(sat, source::get);
        source.put(1, new CdObjectPresentation.TilePattern(101, solid(9)));
        assertEquals(solid(1), frame.patternVersions().get(101));
        assertEquals(solid(2), frame.patternVersions().get(102));
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(sat,
                id -> new CdObjectPresentation.TilePattern(500, solid(id))));
        var shared = CdObjectPresentation.convert(sat, id -> new CdObjectPresentation.TilePattern(500, solid(1)));
        assertEquals(1, shared.patternVersions().size(), "identical versions may share a logical name");
    }

    @Test void unknownTilesNullSamplesAndOverlappingOrInvalidBankClaimsRejectWithoutZeroFill() {
        var f = new Fixture(); f.add(32, 0, 0, 128, 128, 0, List.of(new RawPiece(0, 0, 7, 0)));
        var sat = f.build(32);
        var bank = CdObjectPresentation.declaredPixelBank(List.of(new CdObjectPresentation.PixelSpan(8, 100,
                CdAssetBytes.copyOf(new byte[32]))));
        assertThrows(UnsupportedOperationException.class, () -> CdObjectPresentation.convert(sat, bank));
        assertThrows(UnsupportedOperationException.class, () -> CdObjectPresentation.convert(sat, id -> null));
        assertThrows(IllegalArgumentException.class, () -> bank.resolve(2048));
        var a = new CdObjectPresentation.PixelSpan(7, 100, CdAssetBytes.copyOf(new byte[64]));
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(a, a)));
        var physicalOverlap = new CdObjectPresentation.PixelSpan(8, 200, CdAssetBytes.copyOf(new byte[32]));
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(a, physicalOverlap)));
        var logicalOverlap = new CdObjectPresentation.PixelSpan(20, 101, CdAssetBytes.copyOf(new byte[32]));
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(a, logicalOverlap)));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(2047, 0, CdAssetBytes.copyOf(new byte[64])));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(0, -1, CdAssetBytes.copyOf(new byte[32])));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(0, 0, CdAssetBytes.copyOf(new byte[33])));
    }

    @Test void nowServicedPowerupWindowRefreshesGameOverPixelsWhileEarlierPicturesKeepTheirVersions() throws Exception {
        var art = componentShieldArt();
        var service = new CdPowerupArtRuntime(art);
        var ingress = new CdPowerupArtRuntime.State(filled(0x480, 0xA5), filled(0x480, 0xCC));
        var shield = service.service(ingress, 0x82).state();
        var game = service.service(shield, 0x88).state();
        var sat = new GameOverSlots(GAME_MAP, 0, 1).draw();
        var stale = CdObjectPresentation.convert(sat, powerupBank(ingress));
        var shieldPicture = CdObjectPresentation.convert(sat, powerupBank(shield));
        var gamePicture = CdObjectPresentation.convert(sat, powerupBank(game));
        for (int tile = 0; tile < 32; tile++) {
            assertEquals(solid(0xC), stale.patternVersions().get(LOGICAL + tile), "an unserviced window is not file art");
            assertEquals(version(art.copy(0).bytes(), tile), shieldPicture.patternVersions().get(LOGICAL + tile));
            assertEquals(version(art.copy(3).bytes(), tile), gamePicture.patternVersions().get(LOGICAL + tile));
            assertNotEquals(shieldPicture.patternVersions().get(LOGICAL + tile), gamePicture.patternVersions().get(LOGICAL + tile));
        }
        assertEquals(32, gamePicture.patternVersions().size(), "no window tail pattern is part of the picture");
        assertEquals(shieldPicture.tiles(), gamePicture.tiles(), "only the versions move with the serviced state");
        // A later service and its bank cannot reach either earlier immutable picture.
        var time = service.service(game, 0x8A).state();
        assertEquals(version(art.copy(4).bytes(), 0), powerupBank(time).resolve(WINDOW_TILE).version());
        for (int tile = 0; tile < 32; tile++) {
            assertEquals(version(art.copy(0).bytes(), tile), shieldPicture.patternVersions().get(LOGICAL + tile));
            assertEquals(version(art.copy(3).bytes(), tile), gamePicture.patternVersions().get(LOGICAL + tile));
        }
        assertEquals(gamePicture, CdObjectPresentation.convert(sat, powerupBank(game)));
        assertThrows(UnsupportedOperationException.class, () -> gamePicture.patternVersions().clear());
    }

    @Test void gameAndTimeRootsDrawOnlyTheirOwnWindowTilesAndADirtyFrameFailsClosedBeforePublication() throws Exception {
        var art = componentShieldArt();
        var service = new CdPowerupArtRuntime(art);
        var ingress = new CdPowerupArtRuntime.State(filled(0x480, 0xA5), filled(0x480, 0xCC));
        var gameBank = powerupBank(service.service(ingress, 0x88).state());
        var timeBank = powerupBank(service.service(ingress, 0x8A).state());
        var gameRequests = new TreeSet<Integer>();
        var game = CdObjectPresentation.convert(new GameOverSlots(GAME_MAP, 0, 1).draw(),
                id -> { gameRequests.add(id); return gameBank.resolve(id); });
        assertEquals(nativeRange(0x544, 0x564), gameRequests, "GAME OVER pieces reach window tiles 0..31 only");
        assertEquals(32, game.tiles().size());
        // Reverse SAT order: OVER's second piece first. A piece visits rows, then columns (offset col*height+row).
        assertTile(game.tiles().get(0), LOGICAL + 24, 200, 88);
        assertTile(game.tiles().get(4), LOGICAL + 25, 200, 96);
        assertTile(game.tiles().get(8), LOGICAL + 16, 168, 88);
        assertTile(game.tiles().get(16), LOGICAL + 8, 120, 88);
        assertTile(game.tiles().get(24), LOGICAL, 88, 88);
        var timeRequests = new TreeSet<Integer>();
        var time = CdObjectPresentation.convert(new GameOverSlots(TIME_MAP, 0, 1).draw(),
                id -> { timeRequests.add(id); return timeBank.resolve(id); });
        assertEquals(nativeRange(0x544, 0x562), timeRequests, "TIME OVER pieces reach window tiles 0..29 only");
        assertEquals(30, time.tiles().size());
        assertTile(time.tiles().get(0), LOGICAL + 22, 196, 88);
        assertTile(time.tiles().get(8), LOGICAL + 14, 164, 88);
        assertTile(time.tiles().get(16), LOGICAL + 8, 124, 88);
        assertTile(time.tiles().get(19), LOGICAL + 9, 124, 96); // TimeText's 3x2 piece has three columns.
        assertTile(time.tiles().get(22), LOGICAL, 92, 88);
        for (var picture : List.of(game, time)) {
            for (var tile : picture.tiles()) {
                assertTrue(tile.priority()); assertEquals(0, tile.palette());
                assertFalse(tile.hFlip()); assertFalse(tile.vFlip());
            }
        }
        // Window tiles 30..35 stay bound but undrawn: Art_GameOver's first two tiles, then the RAM tail.
        assertEquals(version(art.copy(3).bytes(), 0), timeBank.resolve(WINDOW_TILE + 30).version());
        assertEquals(version(art.copy(3).bytes(), 1), timeBank.resolve(WINDOW_TILE + 31).version());
        for (int tile = 32; tile < 36; tile++) {
            assertEquals(version(CdAssetBytes.copyOf(filled(32, 0xA5)), 0), timeBank.resolve(WINDOW_TILE + tile).version());
        }
        // A dirty oMapFrame 2 resolves no invented body: the drain fails and publishes no SAT.
        var dirty = new GameOverSlots(GAME_MAP, 2, 1);
        var failure = assertThrows(UnsupportedOperationException.class, dirty::draw);
        assertTrue(failure.getCause().getMessage().contains("frame outside the registered source table"));
        assertEquals(Phase.FAILED, dirty.queue.phase());
        assertThrows(IllegalStateException.class, dirty.queue::cpuSat);
    }

    @Test void completeResidentWindowBindsThirtySixTilesAndOverlapsRejectWhileTheBankIsBuilt() throws Exception {
        var game = new CdPowerupArtRuntime(componentShieldArt())
                .service(new CdPowerupArtRuntime.State(filled(0x480, 0xA5), filled(0x480, 0xCC)), 0x88).state();
        var span = CdObjectPresentation.powerupResidentSpan(game, LOGICAL);
        assertEquals(CdPowerupArtRuntime.VRAM_BYTE_ADDRESS / 32, span.nativeFirstTileId());
        assertEquals(WINDOW_TILE, span.nativeFirstTileId());
        assertEquals(LOGICAL, span.logicalFirstPatternId());
        assertArrayEquals(game.residentBank().copy(), span.bytes().copy(), "the whole $480-byte DMA window, RAM tail included");
        byte[] one = new byte[32];
        var bank = CdObjectPresentation.declaredPixelBank(List.of(
                new CdObjectPresentation.PixelSpan(0x543, LOGICAL - 1, CdAssetBytes.copyOf(one)), span,
                new CdObjectPresentation.PixelSpan(0x568, LOGICAL + 36, CdAssetBytes.copyOf(one))));
        assertEquals(LOGICAL - 1, bank.resolve(0x543).logicalPatternId());
        assertEquals(LOGICAL + 35, bank.resolve(0x567).logicalPatternId());
        assertEquals(LOGICAL + 36, bank.resolve(0x568).logicalPatternId());
        // Physical claims on the first window tile or the RAM tail, and a logical claim on the tail, reject while
        // the bank is built, in either order: no PatternSource exists for any conversion to publish.
        for (var conflict : List.of(new CdObjectPresentation.PixelSpan(0x567, 900, CdAssetBytes.copyOf(one)),
                new CdObjectPresentation.PixelSpan(0x520, 900, CdAssetBytes.copyOf(new byte[37 * 32])),
                new CdObjectPresentation.PixelSpan(0x600, LOGICAL + 35, CdAssetBytes.copyOf(one)))) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(span, conflict)));
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(conflict, span)));
        }
        assertThrows(NullPointerException.class, () -> CdObjectPresentation.powerupResidentSpan(null, LOGICAL));
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.powerupResidentSpan(game, -1));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectPresentation.powerupResidentSpan(game, Integer.MAX_VALUE - 34));
    }

    private static CdObjectPresentation.PatternSource powerupBank(CdPowerupArtRuntime.State state) {
        return CdObjectPresentation.declaredPixelBank(List.of(CdObjectPresentation.powerupResidentSpan(state, LOGICAL)));
    }
    private static SpritePresentation.PatternVersion version(CdAssetBytes bytes, int tile) {
        return CdObjectPresentation.patternVersion(bytes.slice(tile * 32, 32));
    }
    private static void assertTile(SpritePresentation.Tile tile, int logical, int x, int y) {
        assertEquals(logical, tile.patternId()); assertEquals(x, tile.x()); assertEquals(y, tile.y());
    }
    private static TreeSet<Integer> nativeRange(int from, int to) {
        var range = new TreeSet<Integer>();
        for (int id = from; id < to; id++) range.add(id);
        return range;
    }
    private static byte[] filled(int length, int value) { byte[] b = new byte[length]; Arrays.fill(b, (byte) value); return b; }
    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return b;
    }

    /**
     * Component-only LoadShieldArt input for the existing decoder, NOT an authenticated program (precedent:
     * TestCdPowerupArtRuntime): five distinct byte ramps at the profile's raw-copy ranges plus the five pointer longs.
     */
    private static CdShieldArt componentShieldArt() throws Exception {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        byte[] file = new byte[0x40000];
        var data = ByteBuffer.wrap(file);
        int table = profile.range(CdR11aAsset.SHIELD_ART_INDEX).fileOffset();
        CdR11aAsset[] order = {CdR11aAsset.ART_SHIELD, CdR11aAsset.ART_INV_STARS, CdR11aAsset.ART_TIME_STARS,
                CdR11aAsset.ART_GAME_OVER, CdR11aAsset.ART_TIME_OVER};
        for (int slot = 0; slot < order.length; slot++) {
            var range = profile.range(order[slot]);
            for (int i = 0; i < range.length(); i++) file[range.fileOffset() + i] = (byte) (17 * slot + i);
            data.putInt(table + 4 * slot, range.nativeAddress());
        }
        return CdShieldArt.decode(profile, CdMmdImage.parse(file));
    }

    /** Two counted roots over one raw declared include, exactly as the production factory registers them. */
    private static CdObjectMappings gameOverResolver() {
        var raw = CdAssetBytes.copyOf(GAME_OVER_MAPPINGS);
        return CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(GAME_MAP, raw, 0, 2),
                new CdObjectMappings.DeclaredTable(GAME_MAP, raw, 4, 2)), List.of());
    }

    private static SpritePresentation.PatternVersion solid(int value) {
        long word = 0; for (int pixel = 0; pixel < 16; pixel++) word |= (long) (value & 15) << (pixel * 4);
        return new SpritePresentation.PatternVersion(word, word, word, word);
    }
    /** Small CPU painter for opaque overlap assertions; this does not emulate VDP scanline admission. */
    private static int paintedPixel(SpritePresentation.Frame frame, int x, int y) {
        int result = 0;
        for (var tile : frame.tiles()) {
            int px = x - (int) tile.x(), py = y - (int) tile.y();
            if (px < 0 || py < 0 || px >= 8 || py >= 8) continue;
            if (tile.hFlip()) px = 7 - px; if (tile.vFlip()) py = 7 - py;
            int pixel = SpritePresentationRenderer.pattern(frame.patternVersions().get(tile.patternId())).getPixel(px, py);
            if (pixel != 0) result = pixel;
        }
        return result;
    }
    private static int word(byte[] bytes, int at) { return ((bytes[at] & 255) << 8) | (bytes[at + 1] & 255); }

    private static final class Fixture implements CdDrawSlots {
        final Map<Integer, LiveDrawSlot> slots = new HashMap<>();
        final Map<Integer, MappingBody> mappings = new HashMap<>();
        final Map<Integer, Integer> priorities = new HashMap<>();
        int reads, writes;
        void add(int slot, int priority, int flags, int x, int y, int base, List<RawPiece> pieces) {
            int pointer = 0x200000 + slot * 1024;
            slots.put(slot, new LiveDrawSlot(slot == 0 ? 1 : 16, flags, x, 0x9999, y, pointer, 0, base));
            mappings.put(pointer, new FrameMapping(pieces.size(), pieces)); priorities.put(slot, priority);
        }
        CpuSat build(int... order) {
            byte[] seed = new byte[SAT_BYTE_SIZE]; Arrays.fill(seed, (byte) 0xA5);
            var queue = new CdObjectDrawQueue(this, (address, staticPiece, frame) -> mappings.get(address), seed);
            queue.beginPass();
            for (int slot : order) {
                var live = slots.get(slot);
                assertEquals(EnqueueResult.QUEUED, queue.drawObject(new DrawCallCut(new CdSlotRef(slot),
                        live.spriteFlagsByte(), live.xWord(), live.yWord(), 0, 0, priorities.get(slot)), new CameraWords(0, 0)));
            }
            var camera = new CameraWords(0, 0);
            return queue.finishAfterCamera(new DrawCameras(camera, camera, camera));
        }
        @Override public LiveDrawSlot read(CdSlotRef ref) { reads++; return slots.get(ref.wholeIndex()); }
        @Override public void writeOnScreenBit(CdSlotRef ref, boolean on) {
            writes++; var s = slots.get(ref.wholeIndex());
            slots.put(ref.wholeIndex(), new LiveDrawSlot(s.idByte(), (s.spriteFlagsByte() & 127) | (on ? 128 : 0),
                    s.xWord(), s.yWord(), s.screenYWord(), s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord()));
        }
    }

    /** Live $3B parent (slot 32) and OVER child (slot 33) at rest: X $120, oYScr $E0, base $8544, clean flags. */
    private static final class GameOverSlots implements CdDrawSlots {
        final Map<Integer, LiveDrawSlot> live = new HashMap<>();
        final CdObjectDrawQueue queue = new CdObjectDrawQueue(this, gameOverResolver(), new byte[SAT_BYTE_SIZE]);
        GameOverSlots(int pointer, int parentFrame, int childFrame) {
            // Dirty world Y $9999 never reaches an absolute-mode SAT entry; the $0A word is oYScr.
            live.put(32, new LiveDrawSlot(0x3B, 0, 0x120, 0x9999, 0xE0, pointer, parentFrame, 0x8544));
            live.put(33, new LiveDrawSlot(0x3B, 0, 0x120, 0x9999, 0xE0, pointer, childFrame, 0x8544));
        }
        CpuSat draw() {
            var camera = new CameraWords(0, 0);
            queue.beginPass();
            for (int slot = 32; slot < 34; slot++) {
                assertEquals(EnqueueResult.QUEUED, queue.drawObject(
                        new DrawCallCut(new CdSlotRef(slot), 0, 0x120, 0x9999, 0, 0, 0), camera));
            }
            return queue.finishAfterCamera(new DrawCameras(camera, camera, camera));
        }
        @Override public LiveDrawSlot read(CdSlotRef ref) { return live.get(ref.wholeIndex()); }
        @Override public void writeOnScreenBit(CdSlotRef ref, boolean on) {
            var s = live.get(ref.wholeIndex());
            live.put(ref.wholeIndex(), new LiveDrawSlot(s.idByte(), (s.spriteFlagsByte() & 127) | (on ? 128 : 0),
                    s.xWord(), s.yWord(), s.screenYWord(), s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord()));
        }
    }
}
