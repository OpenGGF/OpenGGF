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
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

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

    @Test void declaredFifteenTileFloatBlockSpanKeepsBothEdgesAndRejectsPhysicalOrLogicalOverlapBeforeDrawing() {
        // Constructed pixel input for the generic bank contract, NOT file art or an authenticated object owner.
        byte[] raw = new byte[15 * 32];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) (i * 13 + 7);
        var span = new CdObjectPresentation.PixelSpan(0x334, 12311, CdAssetBytes.copyOf(raw));
        var alone = CdObjectPresentation.declaredPixelBank(List.of(span));
        for (int tile = 0; tile < 15; tile++) {
            var decoded = new Pattern(); decoded.fromSegaFormat(Arrays.copyOfRange(raw, tile * 32, (tile + 1) * 32));
            assertEquals(12311 + tile, alone.resolve(0x334 + tile).logicalPatternId());
            assertEquals(SpritePresentationRenderer.version(decoded), alone.resolve(0x334 + tile).version());
        }
        assertThrows(UnsupportedOperationException.class, () -> alone.resolve(0x333));
        assertThrows(UnsupportedOperationException.class, () -> alone.resolve(0x343));
        var one = CdAssetBytes.copyOf(new byte[32]);
        var adjacent = CdObjectPresentation.declaredPixelBank(List.of(
                new CdObjectPresentation.PixelSpan(0x333, 12310, one), span,
                new CdObjectPresentation.PixelSpan(0x343, 12326, one)));
        assertEquals(12310, adjacent.resolve(0x333).logicalPatternId());
        assertEquals(12325, adjacent.resolve(0x342).logicalPatternId());
        assertEquals(12326, adjacent.resolve(0x343).logicalPatternId());
        for (var conflict : List.of(new CdObjectPresentation.PixelSpan(0x334, 13000, one),
                new CdObjectPresentation.PixelSpan(0x342, 13000, one),
                new CdObjectPresentation.PixelSpan(0x333, 13000, CdAssetBytes.copyOf(new byte[64])),
                new CdObjectPresentation.PixelSpan(0x500, 12311, one),
                new CdObjectPresentation.PixelSpan(0x500, 12325, one))) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(span, conflict)));
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(conflict, span)));
        }
        var maximum = new CdObjectPresentation.PixelSpan(2033, Integer.MAX_VALUE - 14, span.bytes());
        var boundary = CdObjectPresentation.declaredPixelBank(List.of(maximum));
        assertEquals(Integer.MAX_VALUE, boundary.resolve(0x7FF).logicalPatternId());
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(2034, 0, span.bytes()));
        assertThrows(IllegalArgumentException.class,
                () -> new CdObjectPresentation.PixelSpan(0x334, Integer.MAX_VALUE - 13, span.bytes()));
    }

    @Test void declaredNormalSpringSpanHasExactlyThirtySixTilesAndRejectsBothKindsOfOverlapBeforeDrawing() {
        // Constructed PixelSpan input only, never authenticated Spring art or executed PLC residency.
        byte[] raw = new byte[36 * 32];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) (i * 11 + 3);
        var spring = new CdObjectPresentation.PixelSpan(0x520, 12326, CdAssetBytes.copyOf(raw));
        var alone = CdObjectPresentation.declaredPixelBank(List.of(spring));
        for (int tile = 0; tile < 36; tile++) {
            assertEquals(12326 + tile, alone.resolve(0x520 + tile).logicalPatternId());
            assertEquals(engineVersion(spring.bytes(), tile), alone.resolve(0x520 + tile).version());
        }
        assertThrows(UnsupportedOperationException.class, () -> alone.resolve(0x51F));
        assertThrows(UnsupportedOperationException.class, () -> alone.resolve(0x544));
        var one = CdAssetBytes.copyOf(new byte[32]);
        var adjacent = CdObjectPresentation.declaredPixelBank(List.of(
                new CdObjectPresentation.PixelSpan(0x51F, 12325, one), spring,
                new CdObjectPresentation.PixelSpan(0x544, 12362, one)));
        assertEquals(12325, adjacent.resolve(0x51F).logicalPatternId());
        assertEquals(12361, adjacent.resolve(0x543).logicalPatternId());
        assertEquals(12362, adjacent.resolve(0x544).logicalPatternId());
        for (var conflict : List.of(new CdObjectPresentation.PixelSpan(0x520, 15000, one),
                new CdObjectPresentation.PixelSpan(0x543, 15000, one),
                new CdObjectPresentation.PixelSpan(0x51F, 15000, CdAssetBytes.copyOf(new byte[64])),
                new CdObjectPresentation.PixelSpan(0x600, 12326, one),
                new CdObjectPresentation.PixelSpan(0x600, 12361, one))) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(spring, conflict)));
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(conflict, spring)));
        }
        var maximum = CdObjectPresentation.declaredPixelBank(List.of(
                new CdObjectPresentation.PixelSpan(0x7DC, Integer.MAX_VALUE - 35, spring.bytes())));
        assertEquals(Integer.MAX_VALUE, maximum.resolve(0x7FF).logicalPatternId());
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(0x7DD, 0, spring.bytes()));
        assertThrows(IllegalArgumentException.class,
                () -> new CdObjectPresentation.PixelSpan(0x520, Integer.MAX_VALUE - 34, spring.bytes()));
    }

    @Test void actualUsaSpringPolicyBindsAll400PixelsKeepsOld364AndRetainsBothServicedSnapshots() throws Exception {
        var a = springMedia();
        var binding = a.spring().binding(CdR11aAsset.ART_SPRINGS);
        assertEquals(0x520, binding.firstTileId()); assertEquals(36, binding.tileCount());
        assertEquals(0x0520, binding.baseWord());
        assertEquals("8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binding.bytes().copy())));
        var before = springBank(a, a.player(), a.powerup(), springBases());
        assertAllSpringPixels(a, a.player(), a.powerup(), before);
        var old = oldFloatBlockBank(a, a.player(), a.powerup());
        var allIds = springNativeIds();
        int unchanged = 0;
        for (int id = 0; id <= 0x7FF; id++) {
            int nativeId = id;
            if (id >= 0x520 && id <= 0x543) {
                assertThrows(UnsupportedOperationException.class, () -> old.resolve(nativeId));
            } else if (allIds.contains(id)) {
                assertEquals(old.resolve(id), before.resolve(id)); unchanged++;
            } else {
                assertThrows(UnsupportedOperationException.class, () -> old.resolve(nativeId));
            }
        }
        assertEquals(364, unchanged);
        assertThrows(UnsupportedOperationException.class, () -> a.spring().tile(0x51F));
        assertThrows(UnsupportedOperationException.class, () -> a.spring().tile(0x544));
        assertEquals(engineVersion(a.powerup().residentBank(), 0), before.resolve(0x544).version());

        var playerSlots = new Fixture();
        playerSlots.add(32, 0, 0, 128, 128, 0x780,
                List.of(new RawPiece(0, 14, 0, 0), new RawPiece(0, 14, 12, 32)));
        var playerSat = playerSlots.build(32);
        var gameSat = new GameOverSlots(GAME_MAP, 0, 1).draw();
        var earlierPlayer = CdObjectPresentation.convert(playerSat, before);
        var earlierGame = CdObjectPresentation.convert(gameSat, before);
        assertEquals(24, earlierPlayer.patternVersions().size()); assertEquals(32, earlierGame.patternVersions().size());
        var playerService = new CdPlayerArtRuntime(a.assets().sonic(), a.player());
        CdPlayerArtRuntime.State nextPlayer = null;
        for (int frame = 1; frame < a.assets().sonic().dplcFrames().size(); frame++) {
            if (a.assets().sonic().dplcFrames().get(frame).pieces().isEmpty()) continue;
            playerService.serviceVInt(frame);
            if (!Arrays.equals(a.player().residentBank().copy(), playerService.capture().residentBank().copy())) {
                nextPlayer = playerService.capture(); break;
            }
        }
        assertNotNull(nextPlayer, "actual DPLC frames must distinguish the two supplied resident snapshots");
        var game = new CdPowerupArtRuntime(a.assets().shield()).service(a.powerup(), 0x88).state();
        var after = springBank(a, nextPlayer, game, springBases());
        assertAllSpringPixels(a, nextPlayer, game, after);
        var laterPlayer = CdObjectPresentation.convert(playerSat, after);
        var laterGame = CdObjectPresentation.convert(gameSat, after);
        assertNotEquals(earlierPlayer.patternVersions(), laterPlayer.patternVersions());
        assertNotEquals(earlierGame.patternVersions(), laterGame.patternVersions());
        assertEquals(earlierPlayer.tiles(), laterPlayer.tiles()); assertEquals(earlierGame.tiles(), laterGame.tiles());
        assertEquals(earlierPlayer, CdObjectPresentation.convert(playerSat, before));
        assertEquals(earlierGame, CdObjectPresentation.convert(gameSat, before));
        assertAllSpringPixels(a, a.player(), a.powerup(), before);
        for (int tile = 0; tile < 36; tile++) assertEquals(before.resolve(0x520 + tile), after.resolve(0x520 + tile));
        var detached = binding.bytes().copy(); Arrays.fill(detached, (byte) 0xEE);
        assertEquals(engineVersion(binding.bytes(), 35), before.resolve(0x543).version());
        assertThrows(UnsupportedOperationException.class, () -> earlierPlayer.patternVersions().clear());
        assertThrows(UnsupportedOperationException.class, () -> earlierGame.patternVersions().clear());
    }

    @Test void actualUsaSpringPolicyValidatesEveryLogicalSpanBeforeEvenAnEmptySat() throws Exception {
        var a = springMedia();
        var empty = new Fixture().build(); assertEquals(0, empty.spriteCount());
        int[] sizes = springSizes();
        int[] nativeFirst = springFirstTiles();
        for (int span = 0; span < 11; span++) {
            var negative = springBases(); negative[span] = -1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(empty,
                    springBank(a, a.player(), a.powerup(), negative)));
            var overflow = springBases(); overflow[span] = Integer.MAX_VALUE - sizes[span] + 2;
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(empty,
                    springBank(a, a.player(), a.powerup(), overflow)));
            for (int base : new int[]{0, Integer.MAX_VALUE - sizes[span] + 1}) {
                var bounds = springBases(); bounds[span] = base;
                var bank = springBank(a, a.player(), a.powerup(), bounds);
                assertEquals(base, bank.resolve(nativeFirst[span]).logicalPatternId());
                assertEquals(base + sizes[span] - 1, bank.resolve(nativeFirst[span] + sizes[span] - 1).logicalPatternId());
                assertEquals(SpritePresentation.Frame.empty(), CdObjectPresentation.convert(empty, bank));
            }
        }
        // Every pair, including all ten inherited spans; both boundary-overlap directions fail at construction.
        for (int first = 0; first < 11; first++) {
            for (int second = first + 1; second < 11; second++) {
                var overlapEnd = springBases(); overlapEnd[second] = overlapEnd[first] + sizes[first] - 1;
                assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(empty,
                        springBank(a, a.player(), a.powerup(), overlapEnd)));
                var overlapStart = springBases(); overlapStart[first] = overlapStart[second] + sizes[second] - 1;
                assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(empty,
                        springBank(a, a.player(), a.powerup(), overlapStart)));
            }
        }
        assertThrows(NullPointerException.class, () -> springBank(a, null, a.powerup(), springBases()));
        assertThrows(NullPointerException.class, () -> springBank(a, a.player(), null, springBases()));
        int[] b = springBases();
        assertThrows(NullPointerException.class, () -> CdObjectPresentation.usaR11aSpringDevPreloadedArt(
                a.player(), a.powerup(), a.objects(), a.frontier(), a.contact(), a.floatBlock(), null,
                b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10]));
    }

    @Test void actualUsaNormalSpringFamiliesKeepSixFramesRawAttributesAndExactly36TilesThroughTheQueue() throws Exception {
        var a = springMedia();
        var resolver = CdObjectMappings.fromOpeningAssets(a.assets(), a.objects());
        var bank = springBank(a, a.player(), a.powerup(), springBases());
        var old = oldFloatBlockBank(a, a.player(), a.powerup());
        // Source literals, not a second decode of the resolver's result: Spring/Data/Mappings.asm:11-44, pin1ecc5f3af.
        var pieces = List.of(
                List.of(new RawPiece(-8, 12, 0, -16), new RawPiece(0, 12, 4, -16)),
                List.of(new RawPiece(0, 12, 0, -16)),
                List.of(new RawPiece(-32, 12, 0, -16), new RawPiece(-24, 6, 8, -8), new RawPiece(0, 12, 14, -16)),
                List.of(new RawPiece(-16, 3, 18, 0), new RawPiece(-16, 3, 22, -8)),
                List.of(new RawPiece(-16, 3, 18, -8)),
                List.of(new RawPiece(-16, 3, 18, 24), new RawPiece(-8, 9, 26, 0), new RawPiece(-16, 3, 32, -8)));
        var offsets = List.of(List.of(4, 5, 6, 7, 0, 1, 2, 3), List.of(0, 1, 2, 3),
                List.of(14, 15, 16, 17, 8, 11, 9, 12, 10, 13, 0, 1, 2, 3),
                List.of(22, 23, 24, 25, 18, 19, 20, 21), List.of(18, 19, 20, 21),
                List.of(32, 33, 34, 35, 26, 28, 30, 27, 29, 31, 18, 19, 20, 21));
        // $0520 and palette bit $2000: ObjSpring_Init:188-234. Priority $8520: ObjSpring2_Init:110-113.
        // The combined $A520 and all flip states are explicit raw-attribute component inputs, no body admission.
        int[] bases = {0x0520, 0x2520, 0x8520, 0xA520};
        var allRequested = new TreeSet<Integer>();
        for (int family = 0; family < 2; family++) {
            int root = family == 0 ? 0x209BA0 : 0x209BA6;
            for (int frame = 0; frame < 3; frame++) {
                int index = family * 3 + frame;
                assertEquals(new FrameMapping(pieces.get(index).size(), pieces.get(index)), resolver.resolve(root, false, frame));
                for (int flips = 0; flips < 4; flips++) {
                    var sat = drawNormalSpring(resolver, root, frame, bases[flips], 4 | flips);
                    var requested = new TreeSet<Integer>();
                    var picture = CdObjectPresentation.convert(sat, id -> { requested.add(id); return bank.resolve(id); });
                    assertSpringSatAndPicture(sat, picture, pieces.get(index), offsets.get(index), bases[flips], flips,
                            a.spring().binding(CdR11aAsset.ART_SPRINGS).bytes());
                    var expectedIds = new TreeSet<Integer>();
                    for (int offset : offsets.get(index)) expectedIds.add(0x520 + offset);
                    assertEquals(expectedIds, requested); allRequested.addAll(requested);
                    assertThrows(UnsupportedOperationException.class, () -> CdObjectPresentation.convert(sat, old));
                }
            }
            for (int badFrame : new int[]{3, 255}) {
                assertThrows(UnsupportedOperationException.class, () -> drawNormalSpring(resolver, root, badFrame, 0x0520, 4));
            }
        }
        assertEquals(nativeRange(0x520, 0x544), allRequested);
        for (int pointer : new int[]{0x209BF0, 0x209BA2, 0xFF209BA6}) {
            assertThrows(UnsupportedOperationException.class, () -> drawNormalSpring(resolver, pointer, 0, 0x0520, 4));
        }
        for (int id = 0x490; id < 0x4BE; id++) { // PLC_Std's separate diagonal art is never bound.
            int diagonalTile = id;
            assertThrows(UnsupportedOperationException.class, () -> bank.resolve(diagonalTile));
        }
    }

    private static int[] springBases() {
        return new int[]{10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236, 12275, 12311, 12326};
    }
    private static int[] springFirstTiles() {
        return new int[]{0x780, 0x7AE, 0x3A0, 0x424, 0x441, 0x680, 0x6C6, 0x6D7, 0x544, 0x334, 0x520};
    }
    private static int[] springSizes() { return new int[]{24, 14, 70, 28, 63, 70, 5, 39, 36, 15, 36}; }
    private static TreeSet<Integer> springNativeIds() {
        var ids = new TreeSet<Integer>();
        int[] first = springFirstTiles(), count = springSizes();
        for (int span = 0; span < 11; span++) ids.addAll(nativeRange(first[span], first[span] + count[span]));
        assertEquals(400, ids.size()); return ids;
    }

    /** Engine Pattern conversion is the independent pixel oracle, rather than the factory's nibble packer. */
    private static SpritePresentation.PatternVersion engineVersion(CdAssetBytes bytes, int tile) {
        var pattern = new Pattern(); pattern.fromSegaFormat(bytes.slice(tile * 32, 32).copy());
        return SpritePresentationRenderer.version(pattern);
    }

    private static void assertAllSpringPixels(UsaSpring a, CdPlayerArtRuntime.State player,
                                              CdPowerupArtRuntime.State powerup, CdObjectPresentation.PatternSource bank) {
        var pixels = List.of(player.residentBank(), a.objects().initialArtBank().binding(CdR11aAsset.ART_RINGS).bytes(),
                a.objects().initialArtBank().binding(CdR11aAsset.ART_MOSQUI).bytes(),
                a.frontier().binding(CdR11aAsset.ART_3D_PLANT).bytes(), a.frontier().binding(CdR11aAsset.ART_3D_RAMP_BOOST).bytes(),
                a.contact().binding(CdR11aAsset.ART_EXPLOSIONS).bytes(), a.contact().binding(CdR11aAsset.ART_POINTS).bytes(),
                a.contact().binding(CdR11aAsset.ART_FLOWER).bytes(), powerup.residentBank(),
                a.floatBlock().binding(CdR11aAsset.ART_FLOAT_BLOCK).bytes(), a.spring().binding(CdR11aAsset.ART_SPRINGS).bytes());
        int[] first = springFirstTiles(), count = springSizes(), bases = springBases();
        var logical = new HashSet<Integer>();
        for (int span = 0; span < 11; span++) {
            assertEquals(count[span] * 32, pixels.get(span).length());
            for (int tile = 0; tile < count[span]; tile++) {
                var actual = bank.resolve(first[span] + tile);
                assertEquals(bases[span] + tile, actual.logicalPatternId());
                assertEquals(engineVersion(pixels.get(span), tile), actual.version());
                assertTrue(logical.add(actual.logicalPatternId()));
            }
        }
        assertEquals(400, logical.size());
        var nativeIds = springNativeIds();
        for (int tile = 0; tile <= 0x7FF; tile++) {
            int id = tile;
            if (!nativeIds.contains(tile)) assertThrows(UnsupportedOperationException.class, () -> bank.resolve(id));
        }
        assertThrows(IllegalArgumentException.class, () -> bank.resolve(-1));
        assertThrows(IllegalArgumentException.class, () -> bank.resolve(0x800));
    }

    private static CdObjectPresentation.PatternSource springBank(UsaSpring a, CdPlayerArtRuntime.State player,
                                                                 CdPowerupArtRuntime.State powerup, int[] b) {
        return CdObjectPresentation.usaR11aSpringDevPreloadedArt(player, powerup, a.objects(), a.frontier(), a.contact(),
                a.floatBlock(), a.spring(), b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10]);
    }
    private static CdObjectPresentation.PatternSource oldFloatBlockBank(UsaSpring a, CdPlayerArtRuntime.State player,
                                                                        CdPowerupArtRuntime.State powerup) {
        int[] b = springBases();
        return CdObjectPresentation.usaR11aFloatBlockDevPreloadedArt(player, powerup, a.objects(), a.frontier(), a.contact(),
                a.floatBlock(), b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9]);
    }

    private static void assertSpringSatAndPicture(CpuSat sat, SpritePresentation.Frame picture, List<RawPiece> pieces,
                                                   List<Integer> offsets, int base, int flips, CdAssetBytes art) {
        assertEquals(pieces.size(), sat.spriteCount());
        byte[] raw = sat.bytes();
        for (int index = 0; index < pieces.size(); index++) {
            var p = pieces.get(index);
            int width = (p.sizeByte() >>> 2) + 1, height = (p.sizeByte() & 3) + 1;
            int x = (flips & 1) == 0 ? p.x() : -p.x() - width * 8;
            int y = (flips & 2) == 0 ? p.y() : -p.y() - height * 8;
            assertEquals(256 + y, word(raw, index * 8));
            assertEquals(p.sizeByte(), raw[index * 8 + 2] & 255); assertEquals(index + 1, raw[index * 8 + 3] & 255);
            assertEquals((base + p.rawTileWord()) ^ (flips << 11), word(raw, index * 8 + 4));
            assertEquals(384 + x, word(raw, index * 8 + 6));
        }
        assertEquals(offsets.size(), picture.tiles().size()); assertEquals(offsets.size(), picture.patternVersions().size());
        assertEquals(offsets.stream().map(offset -> 12326 + offset).toList(),
                picture.tiles().stream().map(SpritePresentation.Tile::patternId).toList());
        int index = 0;
        for (int piece = pieces.size() - 1; piece >= 0; piece--) {
            var p = pieces.get(piece);
            int width = (p.sizeByte() >>> 2) + 1, height = (p.sizeByte() & 3) + 1;
            int x = (flips & 1) == 0 ? p.x() : -p.x() - width * 8;
            int y = (flips & 2) == 0 ? p.y() : -p.y() - height * 8;
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    var tile = picture.tiles().get(index);
                    assertEquals(256 + x + ((flips & 1) == 0 ? col : width - 1 - col) * 8, tile.x());
                    assertEquals(128 + y + ((flips & 2) == 0 ? row : height - 1 - row) * 8, tile.y());
                    assertEquals((base >>> 13) & 3, tile.palette()); assertEquals((base & 0x8000) != 0, tile.priority());
                    assertEquals((flips & 1) != 0, tile.hFlip()); assertEquals((flips & 2) != 0, tile.vFlip());
                    assertEquals(engineVersion(art, offsets.get(index)), picture.patternVersions().get(tile.patternId()));
                    index++;
                }
            }
        }
    }

    /** Constructed normal $0A live slot; authenticated mapping bytes and the real queue own every emitted SAT piece. */
    private static CpuSat drawNormalSpring(CdDrawMappings mappings, int pointer, int frame, int base, int flags) {
        var slots = new CdDrawSlots() {
            private LiveDrawSlot current = new LiveDrawSlot(0x0A, flags, 0x100, 0x80, 0x9999, pointer, frame, base);
            @Override public LiveDrawSlot read(CdSlotRef ref) { assertEquals(32, ref.wholeIndex()); return current; }
            @Override public void writeOnScreenBit(CdSlotRef ref, boolean on) {
                assertEquals(32, ref.wholeIndex()); var s = current;
                current = new LiveDrawSlot(s.idByte(), (s.spriteFlagsByte() & 127) | (on ? 128 : 0),
                        s.xWord(), s.yWord(), s.screenYWord(), s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord());
            }
        };
        var queue = new CdObjectDrawQueue(slots, mappings, new byte[SAT_BYTE_SIZE]);
        var camera = new CameraWords(0, 0);
        queue.beginPass();
        assertEquals(EnqueueResult.QUEUED, queue.drawObject(new DrawCallCut(new CdSlotRef(32), flags,
                0x100, 0x80, 0x10, 0x10, 4), camera));
        return queue.finishAfterCamera(new DrawCameras(camera, camera, camera));
    }

    private record UsaSpring(CdR11aAssets assets, CdR11aObjectAssets objects,
                             CdR11aObjectAssets.FrontierArtBank frontier, CdR11aObjectAssets.ContactArtBank contact,
                             CdR11aObjectAssets.FloatBlockArtBank floatBlock, CdR11aObjectAssets.SpringArtBank spring,
                             CdPlayerArtRuntime.State player, CdPowerupArtRuntime.State powerup) { }

    /** Existing actual-media fixture pattern from TestCdObjectMappings:769-780; no generated media or public seams. */
    private static UsaSpring springMedia() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media checks");
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        CdR11aAssets assets;
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
        var objects = assets.openingObjects(); // First optional object/bank access is after source closure.
        var service = new CdPlayerArtRuntime(assets.sonic(), new CdPlayerArtRuntime.State(
                filled(0x300, 0xA5), filled(0x300, 0xCC), 0, false));
        int frame = 1;
        while (frame < assets.sonic().dplcFrames().size() && assets.sonic().dplcFrames().get(frame).pieces().isEmpty()) frame++;
        assertTrue(frame < assets.sonic().dplcFrames().size());
        service.serviceVInt(frame).orElseThrow();
        var player = service.capture();
        assertFalse(player.dirty()); assertEquals(frame, player.lastFrame());
        assertArrayEquals(filled(32, 0xCC), player.residentBank().slice(0x2E0, 32).copy());
        var shield = new CdPowerupArtRuntime(assets.shield()).service(new CdPowerupArtRuntime.State(
                filled(0x480, 0xA5), filled(0x480, 0xCC)), 0x82).state();
        assertArrayEquals(assets.shield().copy(0).bytes().copy(), shield.residentBank().slice(0, 0x400).copy());
        assertArrayEquals(filled(0x80, 0xA5), shield.residentBank().slice(0x400, 0x80).copy());
        return new UsaSpring(assets, objects, objects.frontierArtBank(), objects.contactArtBank(),
                objects.floatBlockArtBank(), objects.springArtBank(), player, shield);
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

    @Test void actualUsaVerticalTunnelDoorPolicyAddsOnlyTwelveDecodedTilesToTheUnchanged400Patterns() throws Exception {
        var a = springMedia();
        var door = a.objects().tunnelDoorArtBank();
        var binding = door.binding(CdR11aAsset.ART_TUNNEL_DOOR);
        assertEquals(List.of(CdR11aAsset.ART_TUNNEL_DOOR),
                door.bindings().stream().map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(1, binding.plcId());
        assertEquals(a.assets().loadPlan().plcs().get(1).entries().get(1), binding.plc());
        assertEquals(CdR11aAsset.ART_TUNNEL_DOOR, binding.plc().source());
        assertEquals(0x6500, binding.plc().vramAddress());
        assertEquals(0x8328, binding.baseWord());
        assertEquals(0x328, binding.firstTileId()); assertEquals(12, binding.tileCount());
        assertEquals(0x36AFE, binding.decode().sourceBlobRange().fileOffset());
        assertEquals(154, binding.decode().sourceBlobRange().length());
        assertEquals(binding.decode().sourceBlobRange(), binding.plc().sourceRange());
        assertEquals(0x36AFE, binding.decode().readerInputRange().fileOffset());
        assertEquals(153, binding.decode().readerInputRange().length());
        assertEquals(153, binding.decode().consumedInputBytes());
        assertEquals(384, binding.bytes().length());
        assertEquals("dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binding.bytes().copy())));
        assertEquals("867546aca5f1590ffcf4b95b55ef99f4e370301c62addd69391eb0d481729ba1",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(door.tile(0x328).copy())));
        assertEquals("3408ac8644182dd294f69868c3d3c320bf35b5f084d83adc21d91c67166bd261",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(door.tile(0x333).copy())));
        var old = springBank(a, a.player(), a.powerup(), springBases());
        assertAllSpringPixels(a, a.player(), a.powerup(), old);
        var bank = tunnelDoorBank(a, door, a.player(), a.powerup(), tunnelDoorBases());
        var oldIds = springNativeIds();
        var logical = new HashSet<Integer>();
        int unchanged = 0, added = 0;
        for (int id = 0; id <= 0x7FF; id++) {
            int nativeId = id;
            if (id >= 0x328 && id <= 0x333) {
                var tile = bank.resolve(id);
                assertEquals(12362 + id - 0x328, tile.logicalPatternId());
                assertEquals(engineVersion(binding.bytes(), id - 0x328), tile.version());
                assertArrayEquals(binding.bytes().slice((id - 0x328) * 32, 32).copy(), door.tile(id).copy());
                assertTrue(logical.add(tile.logicalPatternId())); added++;
                assertThrows(UnsupportedOperationException.class, () -> old.resolve(nativeId));
            } else if (oldIds.contains(id)) {
                assertEquals(old.resolve(id), bank.resolve(id));
                assertTrue(logical.add(bank.resolve(id).logicalPatternId())); unchanged++;
                assertThrows(UnsupportedOperationException.class, () -> door.tile(nativeId));
            } else {
                assertThrows(UnsupportedOperationException.class, () -> bank.resolve(nativeId));
                assertThrows(UnsupportedOperationException.class, () -> door.tile(nativeId));
            }
        }
        assertEquals(400, unchanged); assertEquals(12, added); assertEquals(412, logical.size());
        assertEquals(12362, bank.resolve(0x328).logicalPatternId());
        assertEquals(12373, bank.resolve(0x333).logicalPatternId());
        assertEquals(12311, bank.resolve(0x334).logicalPatternId(), "the adjacent FloatBlock source retains its own base");
        assertThrows(UnsupportedOperationException.class, () -> door.binding(CdR11aAsset.ART_FLOAT_BLOCK));
        assertThrows(UnsupportedOperationException.class, () -> door.tile(0x327));
        assertThrows(UnsupportedOperationException.class, () -> door.tile(0x334));
        assertThrows(IllegalArgumentException.class, () -> door.tile(-1));
        assertThrows(IllegalArgumentException.class, () -> door.tile(0x800));
        assertThrows(IllegalArgumentException.class, () -> bank.resolve(-1));
        assertThrows(IllegalArgumentException.class, () -> bank.resolve(0x800));
        byte[] detached = binding.bytes().copy(); Arrays.fill(detached, (byte) 0xEE);
        assertEquals(engineVersion(binding.bytes(), 11), bank.resolve(0x333).version());
        assertSame(door, a.objects().tunnelDoorArtBank());
        assertThrows(UnsupportedOperationException.class, () -> door.bindings().clear());
    }

    @Test void actualUsaVerticalTunnelDoorPolicyValidatesAllTwelveSpansAndOwnersBeforeAnEmptySat() throws Exception {
        var a = springMedia(); var door = a.objects().tunnelDoorArtBank();
        var empty = new Fixture().build(); assertEquals(0, empty.spriteCount());
        int[] sizes = {24, 14, 70, 28, 63, 70, 5, 39, 36, 15, 36, 12};
        int[] firstTiles = {0x780, 0x7AE, 0x3A0, 0x424, 0x441, 0x680, 0x6C6, 0x6D7, 0x544, 0x334, 0x520, 0x328};
        for (int span = 0; span < 12; span++) {
            var negative = tunnelDoorBases(); negative[span] = -1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(empty,
                    tunnelDoorBank(a, door, a.player(), a.powerup(), negative)));
            var overflow = tunnelDoorBases(); overflow[span] = Integer.MAX_VALUE - sizes[span] + 2;
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(empty,
                    tunnelDoorBank(a, door, a.player(), a.powerup(), overflow)));
            for (int base : new int[]{0, Integer.MAX_VALUE - sizes[span] + 1}) {
                var bounds = tunnelDoorBases(); bounds[span] = base;
                var bank = tunnelDoorBank(a, door, a.player(), a.powerup(), bounds);
                assertEquals(base, bank.resolve(firstTiles[span]).logicalPatternId());
                assertEquals(base + sizes[span] - 1, bank.resolve(firstTiles[span] + sizes[span] - 1).logicalPatternId());
                assertEquals(SpritePresentation.Frame.empty(), CdObjectPresentation.convert(empty, bank));
            }
        }
        for (int first = 0; first < 12; first++) {
            for (int second = first + 1; second < 12; second++) {
                var overlapEnd = tunnelDoorBases(); overlapEnd[second] = overlapEnd[first] + sizes[first] - 1;
                assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(empty,
                        tunnelDoorBank(a, door, a.player(), a.powerup(), overlapEnd)));
                var overlapStart = tunnelDoorBases(); overlapStart[first] = overlapStart[second] + sizes[second] - 1;
                assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(empty,
                        tunnelDoorBank(a, door, a.player(), a.powerup(), overlapStart)));
            }
        }
        for (int missing = 0; missing < 8; missing++) {
            int absent = missing;
            int[] b = tunnelDoorBases();
            assertThrows(NullPointerException.class, () -> CdObjectPresentation.convert(empty,
                    CdObjectPresentation.usaR11aTunnelDoorVerticalDevPreloadedArt(
                            absent == 0 ? null : a.player(), absent == 1 ? null : a.powerup(),
                            absent == 2 ? null : a.objects(), absent == 3 ? null : a.frontier(),
                            absent == 4 ? null : a.contact(), absent == 5 ? null : a.floatBlock(),
                            absent == 6 ? null : a.spring(), absent == 7 ? null : door,
                            b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11])));
        }
    }

    @Test void actualDoorBytesKeepTwelveTileDeclaredSpanBoundsAndRejectNativeOrLogicalOverlapInEitherOrder() throws Exception {
        var a = springMedia();
        var bytes = a.objects().tunnelDoorArtBank().binding(CdR11aAsset.ART_TUNNEL_DOOR).bytes();
        // Generic declared format checks use real decoded pixels; this is no additional owner/residency claim.
        var span = new CdObjectPresentation.PixelSpan(0x328, 12362, bytes);
        var alone = CdObjectPresentation.declaredPixelBank(List.of(span));
        for (int tile = 0; tile < 12; tile++) {
            assertEquals(12362 + tile, alone.resolve(0x328 + tile).logicalPatternId());
            assertEquals(engineVersion(bytes, tile), alone.resolve(0x328 + tile).version());
        }
        assertThrows(UnsupportedOperationException.class, () -> alone.resolve(0x327));
        assertThrows(UnsupportedOperationException.class, () -> alone.resolve(0x334));
        for (var conflict : List.of(
                new CdObjectPresentation.PixelSpan(0x328, 15000, bytes.slice(0, 32)),
                new CdObjectPresentation.PixelSpan(0x333, 15000, bytes.slice(352, 32)),
                new CdObjectPresentation.PixelSpan(0x327, 15000, bytes.slice(0, 64)),
                new CdObjectPresentation.PixelSpan(0x600, 12362, bytes.slice(0, 32)),
                new CdObjectPresentation.PixelSpan(0x600, 12373, bytes.slice(352, 32)))) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(span, conflict)));
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.declaredPixelBank(List.of(conflict, span)));
        }
        var boundary = CdObjectPresentation.declaredPixelBank(List.of(
                new CdObjectPresentation.PixelSpan(0x7F4, Integer.MAX_VALUE - 11, bytes)));
        assertEquals(Integer.MAX_VALUE, boundary.resolve(0x7FF).logicalPatternId());
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(0x7F5, 0, bytes));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(-1, 0, bytes));
        assertThrows(IllegalArgumentException.class,
                () -> new CdObjectPresentation.PixelSpan(0x328, Integer.MAX_VALUE - 10, bytes));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(0x328, -1, bytes));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectPresentation.PixelSpan(0x328, 0, bytes.slice(0, 383)));
    }

    @Test void actualVerticalDoorClosedAndOpenFramesKeepLiteralSatCoordinatesFlipsPriorityAndWholeWordAdd() throws Exception {
        var a = springMedia(); var door = a.objects().tunnelDoorArtBank();
        var mappings = CdObjectMappings.fromOpeningAssets(a.assets(), a.objects());
        var bank = tunnelDoorBank(a, door, a.player(), a.powerup(), tunnelDoorBases());
        var art = door.binding(CdR11aAsset.ART_TUNNEL_DOOR).bytes();
        // Literal SAT rows: {Y, size, final full tile word, X}. Picture rows: {source tile offset, screen X, screen Y}.
        // Closed raw $1804/$1800 flips remain independent of the object's four flip states.
        assertTunnelDoorPicture(mappings, bank, art, 0, 0x8328, 4,
                new int[][]{{0xE8,1,0x9B2C,0x17C},{0xF8,3,0x9B28,0x17C}},
                new int[][]{{0,252,144},{1,252,136},{2,252,128},{3,252,120},{4,252,112},{5,252,104}},
                true, true, 0, true);
        assertTunnelDoorPicture(mappings, bank, art, 0, 0x8328, 5,
                new int[][]{{0xE8,1,0x932C,0x17C},{0xF8,3,0x9328,0x17C}},
                new int[][]{{0,252,144},{1,252,136},{2,252,128},{3,252,120},{4,252,112},{5,252,104}},
                false, true, 0, true);
        assertTunnelDoorPicture(mappings, bank, art, 0, 0x8328, 6,
                new int[][]{{0x108,1,0x8B2C,0x17C},{0xE8,3,0x8B28,0x17C}},
                new int[][]{{0,252,104},{1,252,112},{2,252,120},{3,252,128},{4,252,136},{5,252,144}},
                true, false, 0, true);
        assertTunnelDoorPicture(mappings, bank, art, 0, 0x8328, 7,
                new int[][]{{0x108,1,0x832C,0x17C},{0xE8,3,0x8328,0x17C}},
                new int[][]{{0,252,104},{1,252,112},{2,252,120},{3,252,128},{4,252,136},{5,252,144}},
                false, false, 0, true);
        assertTunnelDoorPicture(mappings, bank, art, 1, 0x8328, 4,
                new int[][]{{0xE8,0xC,0x832E,0x17C},{0xE8,4,0x8332,0x19C}},
                new int[][]{{10,284,104},{11,292,104},{6,252,104},{7,260,104},{8,268,104},{9,276,104}},
                false, false, 0, true);
        assertTunnelDoorPicture(mappings, bank, art, 1, 0x8328, 5,
                new int[][]{{0xE8,0xC,0x8B2E,0x164},{0xE8,4,0x8B32,0x154}},
                new int[][]{{10,220,104},{11,212,104},{6,252,104},{7,244,104},{8,236,104},{9,228,104}},
                true, false, 0, true);
        assertTunnelDoorPicture(mappings, bank, art, 1, 0x8328, 6,
                new int[][]{{0x110,0xC,0x932E,0x17C},{0x110,4,0x9332,0x19C}},
                new int[][]{{10,284,144},{11,292,144},{6,252,144},{7,260,144},{8,268,144},{9,276,144}},
                false, true, 0, true);
        assertTunnelDoorPicture(mappings, bank, art, 1, 0x8328, 7,
                new int[][]{{0x110,0xC,0x9B2E,0x164},{0x110,4,0x9B32,0x154}},
                new int[][]{{10,220,144},{11,212,144},{6,252,144},{7,244,144},{8,236,144},{9,228,144}},
                true, true, 0, true);
        // Deliberate raw-attribute component inputs, NOT another admitted body: $6B28 + $1800 = $8328.
        // OR/XOR or separately combined palette/priority would fail these exact final words and pixels.
        assertTunnelDoorPicture(mappings, bank, art, 0, 0x6B28, 4,
                new int[][]{{0xE8,1,0x832C,0x17C},{0xF8,3,0x8328,0x17C}},
                new int[][]{{0,252,120},{1,252,128},{2,252,136},{3,252,144},{4,252,104},{5,252,112}},
                false, false, 0, true);
        assertTunnelDoorPicture(mappings, bank, art, 1, 0x6B28, 4,
                new int[][]{{0xE8,0xC,0x6B2E,0x17C},{0xE8,4,0x6B32,0x19C}},
                new int[][]{{10,292,104},{11,284,104},{6,276,104},{7,268,104},{8,260,104},{9,252,104}},
                true, false, 3, false);
        var old = springBank(a, a.player(), a.powerup(), springBases());
        for (int frame = 0; frame < 2; frame++) {
            var sat = drawVerticalTunnelDoor(mappings, 0x20CE9A, frame, 0x8328, 4);
            assertThrows(UnsupportedOperationException.class, () -> CdObjectPresentation.convert(sat, old));
        }
        for (int frame = 2; frame <= 255; frame++) {
            int badFrame = frame;
            assertThrows(UnsupportedOperationException.class,
                    () -> drawVerticalTunnelDoor(mappings, 0x20CE9A, badFrame, 0x8328, 4));
        }
        for (int pointer : new int[]{0x20CE9C, 0x20CE9E, 0x20CEAA, 0x20CE9B, 0xFF20CE9A}) {
            assertThrows(UnsupportedOperationException.class,
                    () -> drawVerticalTunnelDoor(mappings, pointer, 0, 0x8328, 4));
        }
        assertThrows(UnsupportedOperationException.class,
                () -> drawVerticalTunnelDoor(mappings, 0x20CE9A, 0, 0x8328, 0x24));
    }

    @Test void verticalDoorPolicySnapshotsBothNowServicedWindowsWithoutChangingEarlierPicturesOrStaticDoorPixels() throws Exception {
        var a = springMedia(); var door = a.objects().tunnelDoorArtBank();
        var before = tunnelDoorBank(a, door, a.player(), a.powerup(), tunnelDoorBases());
        var mappings = CdObjectMappings.fromOpeningAssets(a.assets(), a.objects());
        var doorSat = drawVerticalTunnelDoor(mappings, 0x20CE9A, 1, 0x8328, 4);
        var earlierDoor = CdObjectPresentation.convert(doorSat, before);
        var playerSlots = new Fixture();
        playerSlots.add(32, 0, 0, 128, 128, 0x780,
                List.of(new RawPiece(0, 14, 0, 0), new RawPiece(0, 14, 12, 32)));
        var playerSat = playerSlots.build(32);
        var gameSat = new GameOverSlots(GAME_MAP, 0, 1).draw();
        var earlierPlayer = CdObjectPresentation.convert(playerSat, before);
        var earlierGame = CdObjectPresentation.convert(gameSat, before);
        var playerService = new CdPlayerArtRuntime(a.assets().sonic(), a.player());
        CdPlayerArtRuntime.State nextPlayer = null;
        for (int frame = 1; frame < a.assets().sonic().dplcFrames().size(); frame++) {
            if (a.assets().sonic().dplcFrames().get(frame).pieces().isEmpty()) continue;
            playerService.serviceVInt(frame);
            if (!Arrays.equals(a.player().residentBank().copy(), playerService.capture().residentBank().copy())) {
                nextPlayer = playerService.capture(); break;
            }
        }
        assertNotNull(nextPlayer);
        var powerup = new CdPowerupArtRuntime(a.assets().shield()).service(a.powerup(), 0x88).state();
        var after = tunnelDoorBank(a, door, nextPlayer, powerup, tunnelDoorBases());
        for (int tile = 0; tile < 24; tile++) {
            assertEquals(engineVersion(nextPlayer.residentBank(), tile), after.resolve(0x780 + tile).version());
        }
        for (int tile = 0; tile < 36; tile++) {
            assertEquals(engineVersion(powerup.residentBank(), tile), after.resolve(0x544 + tile).version());
        }
        var laterPlayer = CdObjectPresentation.convert(playerSat, after);
        var laterGame = CdObjectPresentation.convert(gameSat, after);
        assertNotEquals(earlierPlayer.patternVersions(), laterPlayer.patternVersions());
        assertNotEquals(earlierGame.patternVersions(), laterGame.patternVersions());
        assertEquals(earlierPlayer.tiles(), laterPlayer.tiles()); assertEquals(earlierGame.tiles(), laterGame.tiles());
        assertEquals(earlierPlayer, CdObjectPresentation.convert(playerSat, before));
        assertEquals(earlierGame, CdObjectPresentation.convert(gameSat, before));
        assertEquals(earlierDoor, CdObjectPresentation.convert(doorSat, before));
        assertEquals(earlierDoor, CdObjectPresentation.convert(doorSat, after));
        for (int tile = 0; tile < 12; tile++) {
            assertEquals(before.resolve(0x328 + tile), after.resolve(0x328 + tile));
        }
        assertThrows(UnsupportedOperationException.class, () -> earlierDoor.patternVersions().clear());
    }

    private static int[] tunnelDoorBases() {
        return new int[]{10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236, 12275, 12311, 12326, 12362};
    }

    private static CdObjectPresentation.PatternSource tunnelDoorBank(UsaSpring a, CdR11aObjectAssets.TunnelDoorArtBank door,
                                                                     CdPlayerArtRuntime.State player,
                                                                     CdPowerupArtRuntime.State powerup, int[] b) {
        return CdObjectPresentation.usaR11aTunnelDoorVerticalDevPreloadedArt(player, powerup, a.objects(), a.frontier(),
                a.contact(), a.floatBlock(), a.spring(), door,
                b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11]);
    }

    private static void assertTunnelDoorPicture(CdDrawMappings mappings, CdObjectPresentation.PatternSource bank,
                                                 CdAssetBytes art, int frame, int base, int flags,
                                                 int[][] expectedSat, int[][] expectedTiles,
                                                 boolean hFlip, boolean vFlip, int palette, boolean priority) {
        var sat = drawVerticalTunnelDoor(mappings, 0x20CE9A, frame, base, flags);
        assertEquals(2, sat.spriteCount());
        byte[] raw = sat.bytes();
        for (int entry = 0; entry < 2; entry++) {
            assertEquals(expectedSat[entry][0], word(raw, entry * 8));
            assertEquals(expectedSat[entry][1], raw[entry * 8 + 2] & 255);
            assertEquals(entry + 1, raw[entry * 8 + 3] & 255);
            assertEquals(expectedSat[entry][2], word(raw, entry * 8 + 4));
            assertEquals(expectedSat[entry][3], word(raw, entry * 8 + 6));
            assertEquals(32, sat.provenance().get(entry).slotRef().wholeIndex());
            assertEquals(entry, sat.provenance().get(entry).mappingPieceOrdinal());
            assertEquals(1, sat.provenance().get(entry).queueBucket());
        }
        var requested = new TreeSet<Integer>();
        var picture = CdObjectPresentation.convert(sat, id -> { requested.add(id); return bank.resolve(id); });
        assertEquals(6, picture.tiles().size()); assertEquals(6, picture.patternVersions().size());
        assertEquals(frame == 0 ? nativeRange(0x328, 0x32E) : nativeRange(0x32E, 0x334), requested);
        for (int index = 0; index < expectedTiles.length; index++) {
            int[] want = expectedTiles[index]; var tile = picture.tiles().get(index);
            assertTile(tile, 12362 + want[0], want[1], want[2]);
            assertEquals(hFlip, tile.hFlip()); assertEquals(vFlip, tile.vFlip());
            assertEquals(palette, tile.palette()); assertEquals(priority, tile.priority());
            assertEquals(SpritePresentation.Layer.OBJECT, tile.layer()); assertTrue(tile.priorityShader());
            assertEquals(8, tile.width()); assertEquals(8, tile.height());
            assertEquals(engineVersion(art, want[0]), picture.patternVersions().get(tile.patternId()));
        }
        assertEquals(List.of(), picture.primitives());
        assertThrows(UnsupportedOperationException.class, () -> picture.tiles().clear());
        assertThrows(UnsupportedOperationException.class, () -> picture.patternVersions().clear());
    }

    /** Constructed live $35 slot only: authenticated bytes -> actual queue -> convert, without a Door body or route. */
    private static CpuSat drawVerticalTunnelDoor(CdDrawMappings mappings, int pointer, int frame, int base, int flags) {
        var slots = new CdDrawSlots() {
            private LiveDrawSlot current = new LiveDrawSlot(0x35, flags, 0x100, 0x80, 0x9999, pointer, frame, base);
            @Override public LiveDrawSlot read(CdSlotRef ref) { assertEquals(32, ref.wholeIndex()); return current; }
            @Override public void writeOnScreenBit(CdSlotRef ref, boolean on) {
                assertEquals(32, ref.wholeIndex()); var s = current;
                current = new LiveDrawSlot(s.idByte(), (s.spriteFlagsByte() & 127) | (on ? 128 : 0),
                        s.xWord(), s.yWord(), s.screenYWord(), s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord());
            }
        };
        var queue = new CdObjectDrawQueue(slots, mappings, new byte[SAT_BYTE_SIZE]);
        var camera = new CameraWords(0, 0);
        queue.beginPass();
        assertEquals(EnqueueResult.QUEUED, queue.drawObject(new DrawCallCut(new CdSlotRef(32), flags,
                0x100, 0x80, 0x18, 4, 1), camera));
        return queue.finishAfterCamera(new DrawCameras(camera, camera, camera));
    }
}
