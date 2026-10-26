package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdGameOver;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import com.openggf.graphics.SpritePresentation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed declared source views are format inputs, never authenticated asset owners. */
class TestCdObjectMappings {
    /**
     * Game Over/Data/Mappings.asm:1-58 at scddisasm 1ecc5f3af, hand-assembled: a constructed declared input. GAME's two
     * signed words are relative to MapSpr_GameOver (+0), TIME's to MapSpr_TimeOver (+4); four two-piece bodies follow,
     * each with its real zero pad. Pieces are Y, size, tile word, X.
     */
    private static final byte[] GAME_OVER_MAPPINGS = bytes(
            0x00, 0x08, 0x00, 0x14, 0x00, 0x1C, 0x00, 0x28,
            0x02, 0xF8, 0x0D, 0x00, 0x00, 0xB8, 0xF8, 0x0D, 0x00, 0x08, 0xD8, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x10, 0x08, 0xF8, 0x0D, 0x00, 0x18, 0x28, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x00, 0xBC, 0xF8, 0x09, 0x00, 0x08, 0xDC, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x0E, 0x04, 0xF8, 0x0D, 0x00, 0x16, 0x24, 0x00);
    /** MOVE.L #MapSpr_GameOver / #MapSpr_TimeOver,oMap(a0) (Game Over/Main.asm:36,43): the USA R11A longs. */
    private static final int GAME_MAP = 0x20F03E, TIME_MAP = 0x20F042;
    private static final List<CdObjectDrawQueue.RawPiece> GAME_TEXT = List.of(
            new CdObjectDrawQueue.RawPiece(-8, 0x0D, 0x0000, -72), new CdObjectDrawQueue.RawPiece(-8, 0x0D, 0x0008, -40));
    private static final List<CdObjectDrawQueue.RawPiece> GAME_OVER_TEXT = List.of(
            new CdObjectDrawQueue.RawPiece(-8, 0x0D, 0x0010, 8), new CdObjectDrawQueue.RawPiece(-8, 0x0D, 0x0018, 40));
    private static final List<CdObjectDrawQueue.RawPiece> TIME_TEXT = List.of(
            new CdObjectDrawQueue.RawPiece(-8, 0x0D, 0x0000, -68), new CdObjectDrawQueue.RawPiece(-8, 0x09, 0x0008, -36));
    private static final List<CdObjectDrawQueue.RawPiece> TIME_OVER_TEXT = List.of(
            new CdObjectDrawQueue.RawPiece(-8, 0x0D, 0x000E, 4), new CdObjectDrawQueue.RawPiece(-8, 0x0D, 0x0016, 36));

    /** MapSpr_FloatBlock, Floating Block/Main.asm:42,174-176 at scddisasm 1ecc5f3af; USA oMap long. */
    private static final int FLOAT_BLOCK_MAP = 0x20A3FA;
    /** Literal counted bodies from Floating Block/Data/Mappings.asm:9-41, including the empty frame. */
    private static final List<List<CdObjectDrawQueue.RawPiece>> FLOAT_BLOCK_FRAMES = List.of(
            List.of(),
            List.of(new CdObjectDrawQueue.RawPiece(-12, 9, 0x0000, -12),
                    new CdObjectDrawQueue.RawPiece(4, 0, 0x0000, -4), new CdObjectDrawQueue.RawPiece(4, 0, 0x0000, 4)),
            List.of(new CdObjectDrawQueue.RawPiece(-12, 9, 0x0800, -12),
                    new CdObjectDrawQueue.RawPiece(4, 0, 0x0800, -12), new CdObjectDrawQueue.RawPiece(4, 0, 0x0800, -4)),
            List.of(new CdObjectDrawQueue.RawPiece(-12, 9, 0x1800, -12), new CdObjectDrawQueue.RawPiece(4, 0, 0x1800, -4)),
            List.of(new CdObjectDrawQueue.RawPiece(-12, 9, 0x1000, -12), new CdObjectDrawQueue.RawPiece(4, 0, 0x1000, -4)),
            List.of(new CdObjectDrawQueue.RawPiece(-12, 0x0A, 0x0006, -12)));

    @Test void signedNegativeOffsetFullUnsignedFrameAndAliasesSelectLiteralBodies() {
        byte[] bytes = new byte[520];
        // Root is at offset 8; frame 255's signed -8 points before the table.
        bytes[0] = 1; bytes[1] = -128; bytes[2] = 15;
        bytes[3] = (byte) 0xF8; bytes[4] = 0x23; bytes[5] = 127;
        putWord(bytes, 8, -8); putWord(bytes, 8 + 254 * 2, -8); putWord(bytes, 8 + 255 * 2, -8);
        var resolver = declared(0xF0200000, bytes, 8, 256);
        var expected = new CdObjectDrawQueue.FrameMapping(1,
                List.of(new CdObjectDrawQueue.RawPiece(-128, 15, 0xF823, 127)));
        assertEquals(expected, resolver.resolve(0xF0200008, false, 255));
        assertEquals(expected, resolver.resolve(0xF0200008, false, 254));
        assertEquals(expected, resolver.resolve(0xF0200008, false, 0));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x00200008, false, 255));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0xF0200009, false, 255));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(0xF0200008, false, 256));
    }

    @Test void nonmonotonicOddBodyOffsetsAndRawVisualPieceFieldsSurvive() {
        byte[] bytes = {0, 13, 0, 6, 0, 13, 1, -2, 5, 0x18, 0x23, -3, 0, 0};
        var resolver = declared(0x200000, bytes, 0, 3);
        assertEquals(List.of(), resolver.resolve(0x200000, false, 0).pieces());
        assertEquals(List.of(new CdObjectDrawQueue.RawPiece(-2, 5, 0x1823, -3)),
                resolver.resolve(0x200000, false, 1).pieces());
        assertEquals(resolver.resolve(0x200000, false, 0), resolver.resolve(0x200000, false, 2));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x200000, false, 3));
    }

    @Test void staticModeReadsExactlyOneFiveBytePieceAtItsOwnActualPointerWithoutAHeader() {
        byte[] literal = {-7, 9, (byte) 0xA1, 0x23, -8};
        var table = new CdObjectMappings.DeclaredTable(0x200100, CdAssetBytes.copyOf(new byte[]{0, 2, 0}), 0, 1);
        var resolver = CdObjectMappings.declaredSourceViews(List.of(table), List.of(
                new CdObjectMappings.DeclaredStaticPiece(0x200200, CdAssetBytes.copyOf(literal))));
        assertEquals(new CdObjectDrawQueue.StaticMapping(new CdObjectDrawQueue.RawPiece(-7, 9, 0xA123, -8)),
                resolver.resolve(0x200200, true, 255));
        assertEquals(List.of(), resolver.resolve(0x200100, false, 0).pieces());
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x200100, true, 0));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x200200, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredStaticPiece(
                0x200200, CdAssetBytes.copyOf(new byte[6])));
    }

    @Test void zeroAndOneHundredTwentyEightCountsAreBoundedButNativeNegativeCountsAreNamedUnsupported() {
        var empty = declared(0x1000, new byte[]{0, 2, 0}, 0, 1);
        assertEquals(new CdObjectDrawQueue.FrameMapping(0, List.of()), empty.resolve(0x1000, false, 0));
        byte[] maximum = new byte[3 + 128 * 5]; maximum[1] = 2; maximum[2] = (byte) 128;
        assertEquals(128, declared(0x1000, maximum, 0, 1).resolve(0x1000, false, 0).pieces().size());
        for (int count = 129; count <= 255; count++) {
            byte[] raw = {0, 2, (byte) count};
            var resolver = declared(0x1000, raw, 0, 1);
            var failure = assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x1000, false, 0));
            assertTrue(failure.getMessage().contains("native empty branch"));
        }
    }

    @Test void missingFramesEscapingRelativeWordsTruncationAndHighSizeBitsReject() {
        for (byte[] raw : List.of(new byte[]{(byte) 0xFF, (byte) 0xFF, 0},
                new byte[]{0, 3, 0}, new byte[]{0, 2, 1, 0, 0, 0, 0},
                new byte[]{0, 2, 1, 0, 16, 0, 0, 0})) {
            var resolver = declared(0x1000, raw, 0, 1);
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(0x1000, false, 0));
        }
        assertThrows(UnsupportedOperationException.class,
                () -> declared(0x1000, new byte[]{0, 2, 0}, 0, 1).resolve(0, false, 0));
    }

    @Test void declaredSourceBoundsRegistrationAndImmutableStorageAreExplicit() {
        byte[] input = {0, 2, 1, -1, 0, 0, 7, -2};
        var view = new CdObjectMappings.DeclaredTable(0x1000, CdAssetBytes.copyOf(input), 0, 1);
        var resolver = CdObjectMappings.declaredSourceViews(List.of(view), List.of());
        input[2] = 0;
        assertEquals(1, resolver.resolve(0x1000, false, 0).pieces().size());
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x1000, false, 0).pieces().clear());
        assertThrows(IllegalArgumentException.class, () -> CdObjectMappings.declaredSourceViews(List.of(view, view), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredTable(0xFFFFFFFF,
                CdAssetBytes.copyOf(new byte[]{0, 2, 0}), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredTable(0x1000,
                CdAssetBytes.copyOf(new byte[1]), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredTable(0x1000,
                CdAssetBytes.copyOf(new byte[3]), 0, 257));
    }

    @Test void actualUsaMediaFactoryJoinsAllFourSourceViewsRejectsForeignIdentityAndBindsDevPixels() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media checks");
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        CdR11aAssets assets;
        CdR11aObjectAssets objects;
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            objects = assets.openingObjects();
        }
        var resolver = CdObjectMappings.fromOpeningAssets(assets, objects);
        int sonic = assets.profile().range(CdR11aAsset.MAP_SPR_SONIC).nativeAddress();
        for (int frame = 0; frame < 198; frame++) {
            assertEquals(assets.sonic().mappingFrames().get(frame).pieceCount(),
                    resolver.resolve(sonic, false, frame).pieces().size());
        }
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(sonic, false, 198));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(sonic | 0xFF000000, false, 0));
        for (var table : List.of(CdR11aObjectAssets.Table.RING_MAPPINGS,
                CdR11aObjectAssets.Table.MOSQUI_NORMAL_MAPPINGS, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_MAPPINGS)) {
            var visual = switch (table) {
                case RING_MAPPINGS -> objects.rings();
                case MOSQUI_NORMAL_MAPPINGS -> objects.normalMosqui();
                default -> objects.damagedMosqui();
            };
            int pointer = objects.view(table).range().nativeAddress();
            for (int frame = 0; frame < visual.mappingFrames().size(); frame++) {
                var expected = visual.mappingFrames().get(frame).pieces().stream()
                        .map(p -> new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x())).toList();
                assertEquals(expected, resolver.resolve(pointer, false, frame).pieces());
            }
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, true, 0));
        }
        // Frontier bodies store these exact longs (Obj3DPlant_Init / Obj3DRamp_Init); raw full-word pointers.
        int plantPointer = objects.view(CdR11aObjectAssets.Table.PLANT_MAPPINGS).range().nativeAddress();
        assertEquals(0x20E020, plantPointer);
        assertEquals(4, resolver.resolve(plantPointer, false, 0).pieces().size());
        assertEquals(2, resolver.resolve(plantPointer, false, 1).pieces().size());
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(plantPointer, false, 2));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(plantPointer, true, 0));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(plantPointer | 0xFF000000, false, 0));
        int rampPointer = objects.view(CdR11aObjectAssets.Table.RAMP_BOOSTER_MAPPINGS).range().nativeAddress();
        assertEquals(0x21FDBC, rampPointer);
        assertEquals(8, resolver.resolve(rampPointer, false, 0).pieces().size());
        assertEquals(12, resolver.resolve(rampPointer, false, 8).pieces().size(), "the count byte, not the declared tail");
        assertEquals(12, resolver.resolve(rampPointer, false, 9).pieces().size());
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(rampPointer, false, 10));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(rampPointer, true, 0));
        for (var frontierTable : List.of(CdR11aObjectAssets.Table.PLANT_MAPPINGS, CdR11aObjectAssets.Table.RAMP_BOOSTER_MAPPINGS)) {
            var visual = frontierTable == CdR11aObjectAssets.Table.PLANT_MAPPINGS ? objects.plant() : objects.rampBooster();
            int pointer = objects.view(frontierTable).range().nativeAddress();
            for (int frame = 0; frame < visual.mappingFrames().size(); frame++) {
                var expected = visual.mappingFrames().get(frame).pieces().stream()
                        .map(p -> new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x())).toList();
                assertEquals(expected, resolver.resolve(pointer, false, frame).pieces());
            }
        }
        var id = assets.identity();
        var foreign = new CdR11aAssets.Identity(id.variant(), id.stage(), id.file(), id.domain(), id.mmdSha256(), "foreign");
        // Existing package-private aggregate fixture access; no authenticated test constructor is introduced.
        var mismatch = new CdR11aAssets(assets.profile(), foreign, null, null, null, null, null, null,
                assets.sonic(), null, null);
        assertThrows(IllegalArgumentException.class, () -> CdObjectMappings.fromOpeningAssets(mismatch, objects));
        var state = new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false);
        var bank = CdObjectPresentation.usaR11aOpeningDevPreloadedArt(state, objects, 10000, 11000, 12000);
        assertEquals(10000, bank.resolve(0x780).logicalPatternId());
        assertEquals(10023, bank.resolve(0x797).logicalPatternId());
        for (var binding : objects.initialArtBank().bindings()) {
            int logical = binding.source() == CdR11aAsset.ART_RINGS ? 11000 : 12000;
            for (int tile = 0; tile < binding.tileCount(); tile++) {
                var actual = bank.resolve(binding.firstTileId() + tile);
                assertEquals(logical + tile, actual.logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(binding.bytes().slice(tile * 32, 32)), actual.version());
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> bank.resolve(0x798));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectPresentation.usaR11aOpeningDevPreloadedArt(state, objects, 10000, 10000, 12000));
        // The three-span opening policy stays strict: no frontier tile is admitted there.
        assertThrows(UnsupportedOperationException.class, () -> bank.resolve(0x424));
        assertThrows(UnsupportedOperationException.class, () -> bank.resolve(0x441));
        var frontier = objects.frontierArtBank();
        var full = CdObjectPresentation.usaR11aFrontierDevPreloadedArt(state, objects, frontier, 10000, 11000, 12000, 12070, 12098);
        assertEquals(10000, full.resolve(0x780).logicalPatternId());
        assertEquals(12000, full.resolve(0x3A0).logicalPatternId());
        assertEquals(12069, full.resolve(0x3E5).logicalPatternId());
        assertEquals(12070, full.resolve(0x424).logicalPatternId(), "Art_3DPlant at VRAM $8480");
        assertEquals(12097, full.resolve(0x43F).logicalPatternId());
        assertEquals(12098, full.resolve(0x441).logicalPatternId(), "Art_3DRampBoost at VRAM $8820");
        assertEquals(12160, full.resolve(0x47F).logicalPatternId());
        for (var source : List.of(CdR11aAsset.ART_3D_PLANT, CdR11aAsset.ART_3D_RAMP_BOOST)) {
            var binding = frontier.binding(source);
            int logical = source == CdR11aAsset.ART_3D_PLANT ? 12070 : 12098;
            for (int tile = 0; tile < binding.tileCount(); tile++) {
                var actual = full.resolve(binding.firstTileId() + tile);
                assertEquals(logical + tile, actual.logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(binding.bytes().slice(tile * 32, 32)), actual.version());
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> full.resolve(0x440), "the one-tile gap before the booster");
        assertThrows(UnsupportedOperationException.class, () -> full.resolve(0x423));
        assertThrows(UnsupportedOperationException.class, () -> full.resolve(0x480));
        assertThrows(UnsupportedOperationException.class, () -> full.resolve(0x388), "Animals are never bound: the inert path draws nothing");
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.usaR11aFrontierDevPreloadedArt(
                state, objects, frontier, 10000, 11000, 12000, 12069, 12098), "plant base inside the Mosqui span");
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.usaR11aFrontierDevPreloadedArt(
                state, objects, frontier, 10000, 11000, 12000, 12070, 12097), "booster base inside the plant span");
        assertThrows(NullPointerException.class, () -> CdObjectPresentation.usaR11aFrontierDevPreloadedArt(
                state, objects, null, 10000, 11000, 12000, 12070, 12098));
        // Contact pointers come from the actual Init operands; all counted bodies retain source bytes.
        for (var table : List.of(CdR11aObjectAssets.Table.EXPLOSION_MAPPINGS,
                CdR11aObjectAssets.Table.POINTS_MAPPINGS, CdR11aObjectAssets.Table.FLOWER_MAPPINGS)) {
            var visual = switch (table) {
                case EXPLOSION_MAPPINGS -> objects.explosion();
                case POINTS_MAPPINGS -> objects.points();
                default -> objects.flower();
            };
            int pointer = objects.view(table).range().nativeAddress();
            assertEquals(switch (table) {
                case EXPLOSION_MAPPINGS -> 0x23F088;
                case POINTS_MAPPINGS -> 0x20AC64;
                default -> 0x235EF4;
            }, pointer);
            for (int frame = 0; frame < visual.mappingFrames().size(); frame++) {
                var expected = visual.mappingFrames().get(frame).pieces().stream()
                        .map(p -> new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x())).toList();
                assertEquals(expected, resolver.resolve(pointer, false, frame).pieces());
            }
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, visual.mappingFrames().size()));
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer | 0xFF000000, false, 0));
        }
        var contact = objects.contactArtBank();
        var contactPixels = CdObjectPresentation.usaR11aContactDevPreloadedArt(state, objects, frontier, contact,
                10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236);
        for (var binding : contact.bindings()) {
            int logical = switch (binding.source()) {
                case ART_EXPLOSIONS -> 12161;
                case ART_POINTS -> 12231;
                default -> 12236;
            };
            for (int tile = 0; tile < binding.tileCount(); tile++) {
                var actual = contactPixels.resolve(binding.firstTileId() + tile);
                assertEquals(logical + tile, actual.logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(binding.bytes().slice(tile * 32, 32)), actual.version());
            }
        }
        for (int gap = 0x6CB; gap < 0x6D7; gap++) {
            int unbound = gap;
            assertThrows(UnsupportedOperationException.class, () -> contactPixels.resolve(unbound));
        }
        assertThrows(UnsupportedOperationException.class, () -> full.resolve(0x680), "the old frontier policy stays strict");
        assertThrows(UnsupportedOperationException.class, () -> contactPixels.resolve(0x388), "Animal art remains absent");
        assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.usaR11aContactDevPreloadedArt(
                state, objects, frontier, contact, 10000, 11000, 12000, 12070, 12098, 12160, 12231, 12236));
    }

    @Test void gameOverIncludeRegistersTwoSignedRootsOverOneRawSourceAndFailsClosedOutsideFramesZeroAndOne() throws Exception {
        assertEquals("ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b", sha256(GAME_OVER_MAPPINGS),
                "transcription cross-check against the observed USA include digest; not media authentication");
        var raw = CdAssetBytes.copyOf(GAME_OVER_MAPPINGS);
        var game = new CdObjectMappings.DeclaredTable(GAME_MAP, raw, 0, 2);
        var time = new CdObjectMappings.DeclaredTable(GAME_MAP, raw, 4, 2);
        assertEquals(GAME_MAP, game.tableAddressBits());
        assertEquals(TIME_MAP, time.tableAddressBits(), "one raw source; the second root is its own label");
        assertGameOverRoots(CdObjectMappings.declaredSourceViews(List.of(game, time), List.of()));
        // An invented uniform four-word header at +0 would read TIME's +4-relative words from the wrong base:
        // $1C and $28 land on zero bytes inside GameOverText and TimeText, silently drawing nothing.
        var uniform = declared(GAME_MAP, GAME_OVER_MAPPINGS, 0, 4);
        assertEquals(new CdObjectDrawQueue.FrameMapping(0, List.of()), uniform.resolve(GAME_MAP, false, 2));
        assertEquals(new CdObjectDrawQueue.FrameMapping(0, List.of()), uniform.resolve(GAME_MAP, false, 3));
    }

    @Test void actualUsaGameOverRootsAndNowServicedPowerupWindowDrawOnlyTheirRetailPieces() throws Exception {
        var usa = usaMedia();
        var assets = usa.assets();
        var objects = usa.objects();
        var resolver = CdObjectMappings.fromOpeningAssets(assets, objects);
        var include = CdGameOver.Assets.fromObjectAssets(objects);
        assertEquals(GAME_MAP, include.gameOverMappingsAddress());
        assertEquals(TIME_MAP, include.timeOverMappingsAddress());
        assertArrayEquals(GAME_OVER_MAPPINGS, include.mappings().copy(), "the authenticated include is Mappings.asm:1-58");
        assertGameOverRoots(resolver);
        // Exact SAT raw attributes from the actual queue: base $8544 ADD.W each raw tile word, and clean flags select
        // the absolute mode, so X is oX + piece X and Y is the $0A oYScr word $E0 + piece Y.
        var gameSat = drawGameOver(resolver, GAME_MAP);
        assertSatEntries(gameSat, new int[][]{{0x0D, 0x8544, 0x0D8}, {0x0D, 0x854C, 0x0F8},
                {0x0D, 0x8554, 0x128}, {0x0D, 0x855C, 0x148}});
        var timeSat = drawGameOver(resolver, TIME_MAP);
        assertSatEntries(timeSat, new int[][]{{0x0D, 0x8544, 0x0DC}, {0x09, 0x854C, 0x0FC},
                {0x0D, 0x8552, 0x124}, {0x0D, 0x855A, 0x144}});

        // NOW-serviced windows: the authenticated LoadShieldArt copies over an EXPLICIT constructed RAM ingress
        // (CPU tail $A5, stale resident $CC). That ingress is a test input, not the native cold seed.
        var service = new CdPowerupArtRuntime(assets.shield());
        var shield = service.service(new CdPowerupArtRuntime.State(filled(0x480, 0xA5), filled(0x480, 0xCC)), 0x82).state();
        var game = service.service(shield, 0x88).state();
        var time = service.service(shield, 0x8A).state();
        assertArrayEquals(assets.shield().copy(3).bytes().copy(), game.residentBank().slice(0, 0x400).copy());
        assertArrayEquals(assets.shield().copy(4).bytes().copy(), time.residentBank().slice(0, 0x400).copy());
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        var player = new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false);
        var shieldBank = gameOverBank(player, shield, objects, frontier, contact, 12275);
        var gameBank = gameOverBank(player, game, objects, frontier, contact, 12275);
        var timeBank = gameOverBank(player, time, objects, frontier, contact, 12275);
        assertAllGameOverPatterns(shieldBank, player, shield, objects, frontier, contact);
        assertAllGameOverPatterns(gameBank, player, game, objects, frontier, contact);
        assertAllGameOverPatterns(timeBank, player, time, objects, frontier, contact);

        var shieldPicture = CdObjectPresentation.convert(gameSat, shieldBank);
        var gameRequests = new TreeSet<Integer>();
        var gamePicture = CdObjectPresentation.convert(gameSat, id -> { gameRequests.add(id); return gameBank.resolve(id); });
        assertEquals(tileRange(0x544, 0x564), gameRequests, "GAME OVER reads window tiles 0..31, never the RAM tail");
        assertEquals(32, gamePicture.tiles().size());
        assertEquals(windowVersions(game, 12275, 32), gamePicture.patternVersions());
        assertEquals(windowVersions(shield, 12275, 32), shieldPicture.patternVersions(), "a stale window draws stale pixels");
        assertNotEquals(shieldPicture.patternVersions(), gamePicture.patternVersions(), "servicing $88 refreshes them");
        assertEquals(shieldPicture.tiles(), gamePicture.tiles(), "same logical names and layout; only versions move");
        var timeRequests = new TreeSet<Integer>();
        var timePicture = CdObjectPresentation.convert(timeSat, id -> { timeRequests.add(id); return timeBank.resolve(id); });
        assertEquals(tileRange(0x544, 0x562), timeRequests, "TIME OVER reads 0..29: never the GameOver continuation or tail");
        assertEquals(30, timePicture.tiles().size());
        assertEquals(windowVersions(time, 12275, 30), timePicture.patternVersions());
        assertEquals(windowVersions(shield, 12275, 32), shieldPicture.patternVersions(), "earlier pictures stay immutable");
        assertEquals(shieldPicture, CdObjectPresentation.convert(gameSat, shieldBank));
        // Logical overlaps reject while the bank is built, before any PatternSource can convert or publish.
        for (int powerupBase : new int[]{12274, 12236, 9990}) {
            assertThrows(IllegalArgumentException.class, () -> gameOverBank(player, game, objects, frontier, contact, powerupBase));
        }
        assertThrows(NullPointerException.class, () -> CdObjectPresentation.usaR11aGameOverDevPreloadedArt(player, null,
                objects, frontier, contact, 10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236, 12275));
        // The existing eight-span contact policy stays strict: it never binds the powerup window.
        var contactPixels = CdObjectPresentation.usaR11aContactDevPreloadedArt(player, objects, frontier, contact,
                10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236);
        assertThrows(UnsupportedOperationException.class, () -> contactPixels.resolve(0x544));
        assertThrows(UnsupportedOperationException.class, () -> CdObjectPresentation.convert(gameSat, contactPixels));
    }

    // Source-literal pieces from Spring/Data/Mappings.asm:11-44. Actual media joins the complete include.
    @Test void actualUsaSpringRootsKeepIndependentSignedIndicesAndExactlyThreeFrames() throws Exception {
        var usa = usaMedia(); var objects = usa.objects();
        var view = objects.view(CdR11aObjectAssets.Table.SPRING_MAPPINGS);
        assertEquals(0x209BA0, view.range().nativeAddress());
        assertEquals("09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c", sha256(view.bytes().copy()));
        var resolver = CdObjectMappings.fromOpeningAssets(usa.assets(), objects);
        var expected = List.of(
                List.of(new CdObjectDrawQueue.RawPiece(-8, 12, 0, -16), new CdObjectDrawQueue.RawPiece(0, 12, 4, -16)),
                List.of(new CdObjectDrawQueue.RawPiece(0, 12, 0, -16)),
                List.of(new CdObjectDrawQueue.RawPiece(-32, 12, 0, -16), new CdObjectDrawQueue.RawPiece(-24, 6, 8, -8),
                        new CdObjectDrawQueue.RawPiece(0, 12, 14, -16)),
                List.of(new CdObjectDrawQueue.RawPiece(-16, 3, 18, 0), new CdObjectDrawQueue.RawPiece(-16, 3, 22, -8)),
                List.of(new CdObjectDrawQueue.RawPiece(-16, 3, 18, -8)),
                List.of(new CdObjectDrawQueue.RawPiece(-16, 3, 18, 24), new CdObjectDrawQueue.RawPiece(-8, 9, 26, 0),
                        new CdObjectDrawQueue.RawPiece(-16, 3, 32, -8)));
        int family = 0;
        for (int root : new int[]{0x209BA0, 0x209BA6}) {
            for (int frame = 0; frame < 3; frame++) {
                var pieces = expected.get(family * 3 + frame);
                assertEquals(new CdObjectDrawQueue.FrameMapping(pieces.size(), pieces), resolver.resolve(root, false, frame));
            }
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(root, false, 3));
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(root, false, 255));
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(root, true, 0));
            family++;
        }
        assertEquals(7, resolver.resolve(0x209BF0, false, 0).pieces().size());
        for (int pointer : new int[]{0x209BA2, 0x209BAC, 0xFF209BA6}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
        }
        assertGameOverRoots(resolver);
        assertEquals(FLOAT_BLOCK_FRAMES.get(5), resolver.resolve(FLOAT_BLOCK_MAP, false, 5).pieces());
    }

    @Test void actualUsaFloatBlockRegistersSixLiteralCountedFramesAndKeepsExistingRoots() throws Exception {
        var usa = usaMedia();
        var objects = usa.objects();
        var view = objects.view(CdR11aObjectAssets.Table.FLOAT_BLOCK_MAPPINGS);
        assertEquals(FLOAT_BLOCK_MAP, view.range().nativeAddress());
        assertEquals("98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e", sha256(view.bytes().copy()));
        assertArrayEquals(view.bytes().copy(), objects.floatBlock().mappings().copy());
        var resolver = CdObjectMappings.fromOpeningAssets(usa.assets(), objects);
        for (int frame = 0; frame < 6; frame++) {
            var pieces = FLOAT_BLOCK_FRAMES.get(frame);
            assertEquals(new CdObjectDrawQueue.FrameMapping(pieces.size(), pieces),
                    resolver.resolve(FLOAT_BLOCK_MAP, false, frame), "native counted frame " + frame);
        }
        for (int frame : new int[]{6, 255}) {
            var failure = assertThrows(UnsupportedOperationException.class,
                    () -> resolver.resolve(FLOAT_BLOCK_MAP, false, frame));
            assertTrue(failure.getMessage().contains("frame outside the registered source table"));
        }
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(FLOAT_BLOCK_MAP, true, 0));
        for (int pointer : new int[]{0x20A3F9, 0x20A3FC, 0x20A406, 0xFF20A3FA, 0x8020A3FA}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
        }
        assertGameOverRoots(resolver); // Both prior roots still select their own two frames.
        int sonic = usa.assets().profile().range(CdR11aAsset.MAP_SPR_SONIC).nativeAddress();
        assertEquals(usa.assets().sonic().mappingFrames().get(197).pieceCount(),
                resolver.resolve(sonic, false, 197).pieces().size());
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(sonic, false, 198));
    }

    @Test void actualUsaFloatBlockPolicyBindsOnly364SourcePatternsAndKeepsServicedSnapshots() throws Exception {
        var usa = usaMedia();
        var assets = usa.assets();
        var objects = usa.objects();
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        var floatBlock = objects.floatBlockArtBank();
        var binding = floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK);
        assertEquals(0x334, binding.firstTileId());
        assertEquals(15, binding.tileCount());
        assertEquals("df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362", sha256(binding.bytes().copy()));

        // Explicit constructed ingress, never a cold-entry or residency claim. Real loaded art services refresh it.
        var playerIngress = new CdPlayerArtRuntime.State(filled(0x300, 0xA5), filled(0x300, 0xCC), 0, false);
        var playerService = new CdPlayerArtRuntime(assets.sonic(), playerIngress);
        var powerupService = new CdPowerupArtRuntime(assets.shield());
        var shield = powerupService.service(
                new CdPowerupArtRuntime.State(filled(0x480, 0xA5), filled(0x480, 0xCC)), 0x82).state();
        var before = floatBlockBank(playerIngress, shield, objects, frontier, contact, floatBlock, floatBlockBases());
        assertAllFloatBlockPatterns(before, playerIngress, shield, objects, frontier, contact, floatBlock);
        var resolver = CdObjectMappings.fromOpeningAssets(assets, objects);
        var visibleSat = drawFloatBlock(resolver, 5);
        assertEquals(1, visibleSat.spriteCount());
        assertEquals(0x0A, visibleSat.bytes()[2] & 255);
        assertEquals(0x433A, word(visibleSat.bytes(), 4));
        var requested = new TreeSet<Integer>();
        var blockPicture = CdObjectPresentation.convert(visibleSat, id -> { requested.add(id); return before.resolve(id); });
        assertEquals(tileRange(0x33A, 0x343), requested, "frame 5 uses exactly the last nine of the fifteen source tiles");
        assertEquals(List.of(12317, 12320, 12323, 12318, 12321, 12324, 12319, 12322, 12325),
                blockPicture.tiles().stream().map(SpritePresentation.Tile::patternId).toList());
        for (int index = 0; index < 9; index++) {
            var tile = blockPicture.tiles().get(index);
            assertEquals(244 + (index % 3) * 8, tile.x());
            assertEquals(116 + (index / 3) * 8, tile.y());
            assertEquals(2, tile.palette()); assertFalse(tile.priority());
            assertFalse(tile.hFlip()); assertFalse(tile.vFlip());
            assertEquals(before.resolve(0x33A + (index % 3) * 3 + index / 3).version(),
                    blockPicture.patternVersions().get(tile.patternId()));
        }
        var emptySat = drawFloatBlock(resolver, 0);
        assertEquals(0, emptySat.spriteCount());
        assertEquals(SpritePresentation.Frame.empty(), CdObjectPresentation.convert(emptySat, before));
        var gameSat = drawGameOver(resolver, GAME_MAP);
        var earlierGame = CdObjectPresentation.convert(gameSat, before);

        // Select an actual nonempty native DPLC frame from loaded bytes; no invented cue or frame is supplied.
        int nonemptyFrame = -1;
        for (int frame = 1; frame < assets.sonic().dplcFrames().size(); frame++) {
            if (!assets.sonic().dplcFrames().get(frame).pieces().isEmpty()) { nonemptyFrame = frame; break; }
        }
        assertTrue(nonemptyFrame > 0);
        var dma = playerService.serviceVInt(nonemptyFrame).orElseThrow();
        var servicedPlayer = playerService.capture();
        assertArrayEquals(dma.bytes().copy(), servicedPlayer.residentBank().slice(0, 0x2E0).copy());
        assertArrayEquals(filled(32, 0xCC), servicedPlayer.residentBank().slice(0x2E0, 32).copy());
        var game = powerupService.service(shield, 0x88).state();
        var after = floatBlockBank(servicedPlayer, game, objects, frontier, contact, floatBlock, floatBlockBases());
        assertAllFloatBlockPatterns(after, servicedPlayer, game, objects, frontier, contact, floatBlock);
        assertEquals(CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(filled(32, 0xCC))),
                before.resolve(0x780).version(), "an earlier bank retains its supplied resident pixels");
        assertNotEquals(playerIngress.residentBank(), servicedPlayer.residentBank());
        var refreshedGame = CdObjectPresentation.convert(gameSat, after);
        assertEquals(windowVersions(game, 12275, 32), refreshedGame.patternVersions());
        assertEquals(windowVersions(shield, 12275, 32), earlierGame.patternVersions());
        assertNotEquals(earlierGame.patternVersions(), refreshedGame.patternVersions());
        assertEquals(earlierGame.tiles(), refreshedGame.tiles());
        assertEquals(earlierGame, CdObjectPresentation.convert(gameSat, before));
        assertEquals(blockPicture, CdObjectPresentation.convert(visibleSat, before));
        assertEquals(blockPicture, CdObjectPresentation.convert(visibleSat, after), "static FloatBlock art is unchanged");
        assertThrows(UnsupportedOperationException.class, () -> blockPicture.patternVersions().clear());
        var detached = binding.bytes().copy(); Arrays.fill(detached, (byte) 0xEE);
        assertEquals(blockPicture, CdObjectPresentation.convert(visibleSat, before));

        // The old nine-span policy still rejects both edges of the added source and the actual block SAT.
        var old = gameOverBank(servicedPlayer, game, objects, frontier, contact, 12275);
        assertThrows(UnsupportedOperationException.class, () -> old.resolve(0x334));
        assertThrows(UnsupportedOperationException.class, () -> old.resolve(0x342));
        assertThrows(UnsupportedOperationException.class, () -> CdObjectPresentation.convert(visibleSat, old));
    }

    @Test void actualUsaFloatBlockPolicyRejectsEveryInvalidSpanBeforeEvenAnEmptySatCanConvert() throws Exception {
        var usa = usaMedia();
        var objects = usa.objects();
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        var floatBlock = objects.floatBlockArtBank();
        var player = new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false);
        var powerup = new CdPowerupArtRuntime.State(filled(0x480, 0xA5), filled(0x480, 0xCC));
        var emptySat = drawFloatBlock(CdObjectMappings.fromOpeningAssets(usa.assets(), objects), 0);
        int[] sizes = {24, 14, 70, 28, 63, 70, 5, 39, 36, 15};
        for (int span = 0; span < 10; span++) {
            var negative = floatBlockBases(); negative[span] = -1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(emptySat,
                    floatBlockBank(player, powerup, objects, frontier, contact, floatBlock, negative)));
            var overflow = floatBlockBases(); overflow[span] = Integer.MAX_VALUE - sizes[span] + 2;
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(emptySat,
                    floatBlockBank(player, powerup, objects, frontier, contact, floatBlock, overflow)));
        }
        for (int prior = 0; prior < 9; prior++) {
            var overlap = floatBlockBases(); overlap[9] = overlap[prior] + sizes[prior] - 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectPresentation.convert(emptySat,
                    floatBlockBank(player, powerup, objects, frontier, contact, floatBlock, overlap)));
        }
        for (int base : new int[]{0, Integer.MAX_VALUE - 14}) {
            var bounds = floatBlockBases(); bounds[9] = base;
            var bank = floatBlockBank(player, powerup, objects, frontier, contact, floatBlock, bounds);
            assertEquals(base, bank.resolve(0x334).logicalPatternId());
            assertEquals(base + 14, bank.resolve(0x342).logicalPatternId());
            assertEquals(SpritePresentation.Frame.empty(), CdObjectPresentation.convert(emptySat, bank));
        }
        assertThrows(NullPointerException.class,
                () -> floatBlockBank(null, powerup, objects, frontier, contact, floatBlock, floatBlockBases()));
        assertThrows(NullPointerException.class,
                () -> floatBlockBank(player, null, objects, frontier, contact, floatBlock, floatBlockBases()));
        assertThrows(NullPointerException.class,
                () -> floatBlockBank(player, powerup, null, frontier, contact, floatBlock, floatBlockBases()));
        assertThrows(NullPointerException.class,
                () -> floatBlockBank(player, powerup, objects, null, contact, floatBlock, floatBlockBases()));
        assertThrows(NullPointerException.class,
                () -> floatBlockBank(player, powerup, objects, frontier, null, floatBlock, floatBlockBases()));
        assertThrows(NullPointerException.class,
                () -> floatBlockBank(player, powerup, objects, frontier, contact, null, floatBlockBases()));
    }

    /** The previous nine sources retain their complete byte/version checks; all final 11-bit IDs are audited. */
    private static void assertAllFloatBlockPatterns(CdObjectPresentation.PatternSource bank, CdPlayerArtRuntime.State player,
                                                   CdPowerupArtRuntime.State powerup, CdR11aObjectAssets objects,
                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                   CdR11aObjectAssets.FloatBlockArtBank floatBlock) {
        assertAllGameOverPatterns(bank, player, powerup, objects, frontier, contact);
        var logical = new HashSet<Integer>();
        var binding = floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK);
        assertSpan(bank, logical, 0x334, 12311, binding.bytes());
        assertEquals(15, logical.size());
        assertEquals(12325, bank.resolve(0x342).logicalPatternId());
        logical.clear();
        int count = 0;
        for (int tile = 0; tile <= 0x7FF; tile++) {
            int id = tile;
            // Literal native ranges, independent of binding metadata and production span construction.
            boolean admitted = (id >= 0x334 && id <= 0x342) || (id >= 0x3A0 && id <= 0x3E5)
                    || (id >= 0x424 && id <= 0x43F) || (id >= 0x441 && id <= 0x47F)
                    || (id >= 0x544 && id <= 0x567) || (id >= 0x680 && id <= 0x6C5)
                    || (id >= 0x6C6 && id <= 0x6CA) || (id >= 0x6D7 && id <= 0x6FD)
                    || (id >= 0x780 && id <= 0x797) || (id >= 0x7AE && id <= 0x7BB);
            if (admitted) {
                assertTrue(logical.add(bank.resolve(id).logicalPatternId()), "distinct logical ID for native tile " + id);
                count++;
            } else {
                assertThrows(UnsupportedOperationException.class, () -> bank.resolve(id), "unowned native tile " + id);
            }
        }
        assertEquals(364, count); assertEquals(364, logical.size());
        assertThrows(IllegalArgumentException.class, () -> bank.resolve(-1));
        assertThrows(IllegalArgumentException.class, () -> bank.resolve(0x800));
    }

    private static int[] floatBlockBases() {
        return new int[]{10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236, 12275, 12311};
    }

    private static CdObjectPresentation.PatternSource floatBlockBank(CdPlayerArtRuntime.State player,
                                                                     CdPowerupArtRuntime.State powerup,
                                                                     CdR11aObjectAssets objects,
                                                                     CdR11aObjectAssets.FrontierArtBank frontier,
                                                                     CdR11aObjectAssets.ContactArtBank contact,
                                                                     CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                     int[] bases) {
        return CdObjectPresentation.usaR11aFloatBlockDevPreloadedArt(player, powerup, objects, frontier, contact, floatBlock,
                bases[0], bases[1], bases[2], bases[3], bases[4], bases[5], bases[6], bases[7], bases[8], bases[9]);
    }

    /** Constructed live slot using the actual USA mapping and selector-5 tile word; no object-body/route claim. */
    private static CdObjectDrawQueue.CpuSat drawFloatBlock(CdObjectDrawQueue.CdDrawMappings mappings, int frame) {
        var live = new CdObjectDrawQueue.LiveDrawSlot(0x12, 4, 0x100, 0x80, 0x9999, FLOAT_BLOCK_MAP, frame, 0x4334);
        var slots = new CdObjectDrawQueue.CdDrawSlots() {
            private CdObjectDrawQueue.LiveDrawSlot current = live;
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                assertEquals(32, ref.wholeIndex()); return current;
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean on) {
                assertEquals(32, ref.wholeIndex());
                var s = current;
                current = new CdObjectDrawQueue.LiveDrawSlot(s.idByte(), (s.spriteFlagsByte() & 127) | (on ? 128 : 0),
                        s.xWord(), s.yWord(), s.screenYWord(), s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord());
            }
        };
        var queue = new CdObjectDrawQueue(slots, mappings, new byte[CdObjectDrawQueue.SAT_BYTE_SIZE]);
        var camera = new CdObjectDrawQueue.CameraWords(0, 0);
        queue.beginPass();
        assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(new CdObjectDrawQueue.DrawCallCut(
                new CdObjectDrawQueue.CdSlotRef(32), 4, 0x100, 0x80, 0x0C, 0x0C, 1), camera));
        return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera));
    }

    /** Exact retail pieces for oMapFrame 0/1 at both full 32-bit roots; every other request fails closed. */
    private static void assertGameOverRoots(CdObjectMappings resolver) {
        assertEquals(new CdObjectDrawQueue.FrameMapping(2, GAME_TEXT), resolver.resolve(GAME_MAP, false, 0));
        assertEquals(new CdObjectDrawQueue.FrameMapping(2, GAME_OVER_TEXT), resolver.resolve(GAME_MAP, false, 1));
        assertEquals(new CdObjectDrawQueue.FrameMapping(2, TIME_TEXT), resolver.resolve(TIME_MAP, false, 0));
        assertEquals(new CdObjectDrawQueue.FrameMapping(2, TIME_OVER_TEXT), resolver.resolve(TIME_MAP, false, 1));
        for (int root : new int[]{GAME_MAP, TIME_MAP}) {
            for (int frame : new int[]{2, 3, 255}) { // A dirty oMapFrame never selects another body or table.
                var failure = assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(root, false, frame));
                assertTrue(failure.getMessage().contains("frame outside the registered source table"));
            }
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(root, true, 0), "no static piece");
        }
        // Full raw longs only: no 24-bit masking and no header-word, body or odd-address alias.
        for (int pointer : new int[]{0x20F040, 0x20F044, 0x20F046, 0x20F03F, 0xFF20F03E, 0x8020F042}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
        }
    }

    /** All 349 bound patterns: the eight contact spans' source bytes, then the 36 supplied window tiles. */
    private static void assertAllGameOverPatterns(CdObjectPresentation.PatternSource bank, CdPlayerArtRuntime.State player,
                                                  CdPowerupArtRuntime.State powerup, CdR11aObjectAssets objects,
                                                  CdR11aObjectAssets.FrontierArtBank frontier,
                                                  CdR11aObjectAssets.ContactArtBank contact) {
        var logical = new HashSet<Integer>();
        assertSpan(bank, logical, CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, 10000, player.residentBank());
        for (var span : List.of(Map.entry(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS), 11000),
                Map.entry(objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI), 12000),
                Map.entry(frontier.binding(CdR11aAsset.ART_3D_PLANT), 12070),
                Map.entry(frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST), 12098),
                Map.entry(contact.binding(CdR11aAsset.ART_EXPLOSIONS), 12161),
                Map.entry(contact.binding(CdR11aAsset.ART_POINTS), 12231),
                Map.entry(contact.binding(CdR11aAsset.ART_FLOWER), 12236))) {
            assertSpan(bank, logical, span.getKey().firstTileId(), span.getValue(), span.getKey().bytes());
        }
        assertSpan(bank, logical, 0x544, 12275, powerup.residentBank());
        assertEquals(349, logical.size());
        var tail = CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(filled(32, 0xA5)));
        for (int tile = 0x564; tile < 0x568; tile++) {
            assertEquals(tail, bank.resolve(tile).version(), "the explicit RAM tail is retained, not file or zero bytes");
        }
        for (int unbound : new int[]{0x543, 0x568, 0x6CB, 0x6D6, 0x388}) {
            assertThrows(UnsupportedOperationException.class, () -> bank.resolve(unbound));
        }
    }

    private static void assertSpan(CdObjectPresentation.PatternSource bank, HashSet<Integer> logical,
                                   int nativeFirst, int logicalBase, CdAssetBytes bytes) {
        for (int tile = 0; tile < bytes.length() / 32; tile++) {
            var actual = bank.resolve(nativeFirst + tile);
            assertEquals(logicalBase + tile, actual.logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(bytes.slice(tile * 32, 32)), actual.version());
            assertTrue(logical.add(actual.logicalPatternId()));
        }
    }

    private static CdObjectPresentation.PatternSource gameOverBank(CdPlayerArtRuntime.State player,
                                                                   CdPowerupArtRuntime.State powerup,
                                                                   CdR11aObjectAssets objects,
                                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                                   int powerupBase) {
        return CdObjectPresentation.usaR11aGameOverDevPreloadedArt(player, powerup, objects, frontier, contact,
                10000, 11000, 12000, 12070, 12098, 12161, 12231, 12236, powerupBase);
    }

    /** Parent (frame 0) and OVER child (frame 1) at rest through the actual queue: X $120, oYScr $E0, base $8544. */
    private static CdObjectDrawQueue.CpuSat drawGameOver(CdObjectDrawQueue.CdDrawMappings mappings, int pointer) {
        var live = new HashMap<Integer, CdObjectDrawQueue.LiveDrawSlot>();
        for (int frame = 0; frame < 2; frame++) { // Dirty world Y $9999 never reaches an absolute-mode entry.
            live.put(32 + frame, new CdObjectDrawQueue.LiveDrawSlot(0x3B, 0, 0x120, 0x9999, 0xE0, pointer, frame, 0x8544));
        }
        var slots = new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                return live.get(ref.wholeIndex());
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean on) {
                var s = live.get(ref.wholeIndex());
                live.put(ref.wholeIndex(), new CdObjectDrawQueue.LiveDrawSlot(s.idByte(),
                        (s.spriteFlagsByte() & 127) | (on ? 128 : 0), s.xWord(), s.yWord(), s.screenYWord(),
                        s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord()));
            }
        };
        var queue = new CdObjectDrawQueue(slots, mappings, new byte[CdObjectDrawQueue.SAT_BYTE_SIZE]);
        var camera = new CdObjectDrawQueue.CameraWords(0, 0);
        queue.beginPass();
        for (int slot = 32; slot < 34; slot++) {
            assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(new CdObjectDrawQueue.DrawCallCut(
                    new CdObjectDrawQueue.CdSlotRef(slot), 0, 0x120, 0x9999, 0, 0, 0), camera));
        }
        return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera));
    }

    /** {size, final tile word, X} per entry; every Y word is $D8 ($E0 - 8) and each link is index + 1. */
    private static void assertSatEntries(CdObjectDrawQueue.CpuSat sat, int[][] entries) {
        assertEquals(entries.length, sat.spriteCount());
        byte[] bytes = sat.bytes();
        for (int index = 0; index < entries.length; index++) {
            int at = index * 8;
            assertEquals(0x00D8, word(bytes, at));
            assertEquals(entries[index][0], bytes[at + 2] & 255);
            assertEquals(index + 1, bytes[at + 3] & 255);
            assertEquals(entries[index][1], word(bytes, at + 4));
            assertEquals(entries[index][2], word(bytes, at + 6));
        }
    }

    private static Map<Integer, SpritePresentation.PatternVersion> windowVersions(CdPowerupArtRuntime.State state,
                                                                                  int base, int count) {
        var versions = new HashMap<Integer, SpritePresentation.PatternVersion>();
        for (int tile = 0; tile < count; tile++) {
            versions.put(base + tile, CdObjectPresentation.patternVersion(state.residentBank().slice(tile * 32, 32)));
        }
        return versions;
    }

    private record UsaR11a(CdR11aAssets assets, CdR11aObjectAssets objects) { }

    /** Opt-in supplied media, read in memory through the existing loader; nothing is copied or written. */
    private static UsaR11a usaMedia() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media checks");
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            var assets = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            return new UsaR11a(assets, assets.openingObjects());
        }
    }

    private static CdObjectMappings declared(int start, byte[] bytes, int root, int frames) {
        return CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(
                start, CdAssetBytes.copyOf(bytes), root, frames)), List.of());
    }
    private static void putWord(byte[] bytes, int at, int word) {
        bytes[at] = (byte) (word >>> 8); bytes[at + 1] = (byte) word;
    }
    private static int word(byte[] bytes, int at) { return ((bytes[at] & 255) << 8) | (bytes[at + 1] & 255); }
    private static TreeSet<Integer> tileRange(int from, int to) {
        var range = new TreeSet<Integer>();
        for (int id = from; id < to; id++) { range.add(id); }
        return range;
    }
    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) { bytes[i] = (byte) values[i]; }
        return bytes;
    }
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Pinned Powerup mapping directives, independently transcribed; constructed format input only. */
    private static final byte[] POWERUP_SOURCE = bytes(
                0x00, 0x25, 0x00, 0x1A, 0x00, 0x2F, 0x00, 0x44, 0x00, 0x59, 0x00, 0x6E,
                0x00, 0x83, 0x00, 0x98, 0x00, 0xAE, 0x00, 0xC4, 0x00, 0xDA, 0x00, 0xF0, 0x01, 0x06,
                0x04, 0xE8, 0x0A, 0x00, 0x00, 0xE8, 0xE8, 0x0A, 0x00, 0x09, 0x00, 0x00, 0x0A, 0x10, 0x00, 0xE8, 0x00, 0x0A, 0x10, 0x09, 0x00,
                0x04, 0xE8, 0x0A, 0x00, 0x12, 0xE8, 0xE8, 0x0A, 0x00, 0x1B, 0x00, 0x00, 0x0A, 0x10, 0x12, 0xE8, 0x00, 0x0A, 0x10, 0x1B, 0x00,
                0x04, 0xE8, 0x0A, 0x08, 0x09, 0xE8, 0xE8, 0x0A, 0x08, 0x00, 0x00, 0x00, 0x0A, 0x18, 0x09, 0xE8, 0x00, 0x0A, 0x18, 0x00, 0x00,
                0x04, 0xE8, 0x0A, 0x00, 0x00, 0xE8, 0xE8, 0x0A, 0x00, 0x09, 0x00, 0x00, 0x0A, 0x18, 0x09, 0xE8, 0x00, 0x0A, 0x18, 0x00, 0x00,
                0x04, 0xE8, 0x0A, 0x08, 0x09, 0xE8, 0xE8, 0x0A, 0x08, 0x00, 0x00, 0x00, 0x0A, 0x10, 0x00, 0xE8, 0x00, 0x0A, 0x10, 0x09, 0x00,
                0x04, 0xE8, 0x0A, 0x00, 0x12, 0xE8, 0xE8, 0x0A, 0x00, 0x1B, 0x00, 0x00, 0x0A, 0x18, 0x1B, 0xE8, 0x00, 0x0A, 0x18, 0x12, 0x00,
                0x04, 0xE8, 0x0A, 0x08, 0x1B, 0xE8, 0xE8, 0x0A, 0x08, 0x12, 0x00, 0x00, 0x0A, 0x10, 0x12, 0xE8, 0x00, 0x0A, 0x10, 0x1B, 0x00, 0x00,
                0x04, 0xF0, 0x05, 0x00, 0x00, 0xF0, 0xF0, 0x05, 0x08, 0x00, 0x00, 0x00, 0x05, 0x10, 0x00, 0xF0, 0x00, 0x05, 0x18, 0x00, 0x00, 0x00,
                0x04, 0xF0, 0x05, 0x00, 0x04, 0xF0, 0xF0, 0x05, 0x08, 0x04, 0x00, 0x00, 0x05, 0x10, 0x04, 0xF0, 0x00, 0x05, 0x18, 0x04, 0x00, 0x00,
                0x04, 0xE8, 0x0A, 0x00, 0x08, 0xE8, 0xE8, 0x0A, 0x08, 0x08, 0x00, 0x00, 0x0A, 0x10, 0x08, 0xE8, 0x00, 0x0A, 0x18, 0x08, 0x00, 0x00,
                0x04, 0xF0, 0x05, 0x00, 0x11, 0xF0, 0xF0, 0x05, 0x00, 0x15, 0x00, 0x00, 0x05, 0x18, 0x15, 0xF0, 0x00, 0x05, 0x18, 0x11, 0x00, 0x00,
                0x02, 0xF4, 0x06, 0x00, 0x19, 0xF0, 0xF4, 0x06, 0x08, 0x19, 0x00, 0x00);
    private static final int[][][] POWERUP_FRAMES = {
            {},
            {{-24,10,0x0000,-24},{-24,10,0x0009,0},{0,10,0x1000,-24},{0,10,0x1009,0}},
            {{-24,10,0x0012,-24},{-24,10,0x001B,0},{0,10,0x1012,-24},{0,10,0x101B,0}},
            {{-24,10,0x0809,-24},{-24,10,0x0800,0},{0,10,0x1809,-24},{0,10,0x1800,0}},
            {{-24,10,0x0000,-24},{-24,10,0x0009,0},{0,10,0x1809,-24},{0,10,0x1800,0}},
            {{-24,10,0x0809,-24},{-24,10,0x0800,0},{0,10,0x1000,-24},{0,10,0x1009,0}},
            {{-24,10,0x0012,-24},{-24,10,0x001B,0},{0,10,0x181B,-24},{0,10,0x1812,0}},
            {{-24,10,0x081B,-24},{-24,10,0x0812,0},{0,10,0x1012,-24},{0,10,0x101B,0}},
            {{-16,5,0x0000,-16},{-16,5,0x0800,0},{0,5,0x1000,-16},{0,5,0x1800,0}},
            {{-16,5,0x0004,-16},{-16,5,0x0804,0},{0,5,0x1004,-16},{0,5,0x1804,0}},
            {{-24,10,0x0008,-24},{-24,10,0x0808,0},{0,10,0x1008,-24},{0,10,0x1808,0}},
            {{-16,5,0x0011,-16},{-16,5,0x0015,0},{0,5,0x1815,-16},{0,5,0x1811,0}},
            {{-12,6,0x0019,-16},{-12,6,0x0819,0}}};

    @Test void powerupCountedSourceKeepsEveryRawPieceAndItsInteriorEmptyTarget() {
        var resolver = declared(0x23EE9C, POWERUP_SOURCE, 0, 13);
        assertPowerupFrames(resolver);
        assertEquals(new CdObjectDrawQueue.FrameMapping(0, List.of()), resolver.resolve(0x23EE9C, false, 0));
        assertEquals(4, resolver.resolve(0x23EE9C, false, 1).pieces().size(),
                "frame 0's target at 37 must not truncate frame 1's body at 26..46");
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(POWERUP_SOURCE, 13, 36));
        var truncated = declared(0x23EE9C, Arrays.copyOf(POWERUP_SOURCE, 272), 0, 13);
        assertThrows(IllegalArgumentException.class, () -> truncated.resolve(0x23EE9C, false, 12));
        assertEquals(List.of(), truncated.resolve(0x23EE9C, false, 0).pieces(), "bounds apply to the selected frame");
        byte[] escaping = POWERUP_SOURCE.clone(); putWord(escaping, 0, 274);
        var badRoot = declared(0x23EE9C, escaping, 0, 13);
        assertThrows(IllegalArgumentException.class, () -> badRoot.resolve(0x23EE9C, false, 0));
    }

    @Test void actualUsaPowerupRootJoinsRetainedBytesWithoutChangingPriorMappingsOrArtBanks() throws Exception {
        var usa = usaMedia(); // Source is closed before this owner's first Powerup view request.
        var objects = usa.objects();
        var initial = objects.initialArtBank(); var rings = objects.rings();
        var view = objects.view(CdR11aObjectAssets.Table.POWERUP_MAPPINGS);
        assertEquals(0x23EE9C, view.range().nativeAddress());
        assertArrayEquals(POWERUP_SOURCE, view.bytes().copy());
        var resolver = CdObjectMappings.fromOpeningAssets(usa.assets(), objects);
        assertPowerupFrames(resolver);
        assertSame(initial, objects.initialArtBank()); assertSame(rings, objects.rings());
        assertEquals(List.of(CdR11aAsset.ART_RINGS, CdR11aAsset.ART_MOSQUI), initial.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertThrows(UnsupportedOperationException.class, () -> initial.tile(0x544));
        assertGameOverRoots(resolver);
        assertEquals(FLOAT_BLOCK_FRAMES.get(5), resolver.resolve(FLOAT_BLOCK_MAP, false, 5).pieces());
        assertEquals(3, resolver.resolve(0x209BA0, false, 2).pieces().size());
        assertEquals(3, resolver.resolve(0x209BA6, false, 2).pieces().size());
        assertEquals(9, objects.rings().mappingFrames().size());
        assertEquals(5, objects.normalMosqui().mappingFrames().size());
        assertEquals(5, objects.damagedMosqui().mappingFrames().size());
    }

    private static void assertPowerupFrames(CdObjectMappings resolver) {
        for (int frame = 0; frame < 13; frame++) {
            var expected = Arrays.stream(POWERUP_FRAMES[frame]).map(p ->
                    new CdObjectDrawQueue.RawPiece(p[0], p[1], p[2], p[3])).toList();
            assertEquals(new CdObjectDrawQueue.FrameMapping(expected.size(), expected),
                    resolver.resolve(0x23EE9C, false, frame), "literal native Powerup frame " + frame);
            for (var p : expected) {
                int firstTile = (0x0544 + p.rawTileWord()) & 0x7FF;
                int tiles = (((p.sizeByte() >>> 2) & 3) + 1) * ((p.sizeByte() & 3) + 1);
                assertTrue(firstTile >= 0x544 && firstTile + tiles <= 0x568,
                        "defined frames fit the existing 36-tile Powerup window");
            }
        }
        for (int frame : new int[]{13, 255}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x23EE9C, false, frame));
        }
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x23EE9C, true, 0));
        for (int pointer : new int[]{0x23EE9D, 0x23EEB6, 0x23EEC1, 0xFF23EE9C, 0x8023EE9C}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
        }
    }

    /** Source directives at N: Tunnel Door/Data/Mappings.asm:1-27; no synthetic picture or owner. */
    private static final byte[] TUNNEL_DOOR_VERTICAL_SOURCE = bytes(
            0x00, 0x04, 0x00, 0x10,
            0x02, 0xE8, 0x01, 0x18, 0x04, 0xFC, 0xF8, 0x03, 0x18, 0x00, 0xFC, 0x00,
            0x02, 0xE8, 0x0C, 0x00, 0x06, 0xFC, 0xE8, 0x04, 0x00, 0x0A, 0x1C, 0x00);
    private static final int TUNNEL_DOOR_VERTICAL_MAP = 0x20CE9A;
    private static final List<List<CdObjectDrawQueue.RawPiece>> TUNNEL_DOOR_VERTICAL_FRAMES = List.of(
            List.of(new CdObjectDrawQueue.RawPiece(-24, 1, 0x1804, -4),
                    new CdObjectDrawQueue.RawPiece(-8, 3, 0x1800, -4)),
            List.of(new CdObjectDrawQueue.RawPiece(-24, 0xC, 0x0006, -4),
                    new CdObjectDrawQueue.RawPiece(-24, 4, 0x000A, 28)));

    @Test void declaredVerticalTunnelDoorKeepsBothCountedBodiesRawFlipsAndRealPads() throws Exception {
        assertEquals(28, TUNNEL_DOOR_VERTICAL_SOURCE.length);
        assertEquals("954bf4fd9df8e85b782482c17f65e68d32101f1e43e21fd4a7fe4058a91b5275",
                sha256(TUNNEL_DOOR_VERTICAL_SOURCE), "source transcription, not media authentication");
        assertEquals(4, word(TUNNEL_DOOR_VERTICAL_SOURCE, 0));
        assertEquals(16, word(TUNNEL_DOOR_VERTICAL_SOURCE, 2));
        assertEquals(0, TUNNEL_DOOR_VERTICAL_SOURCE[15]);
        assertEquals(0, TUNNEL_DOOR_VERTICAL_SOURCE[27]);
        var resolver = declared(TUNNEL_DOOR_VERTICAL_MAP, TUNNEL_DOOR_VERTICAL_SOURCE, 0, 2);
        assertVerticalTunnelDoorFrames(resolver);
        byte[] truncated = Arrays.copyOf(TUNNEL_DOOR_VERTICAL_SOURCE, 26);
        var shortSource = declared(TUNNEL_DOOR_VERTICAL_MAP, truncated, 0, 2);
        assertEquals(TUNNEL_DOOR_VERTICAL_FRAMES.getFirst(),
                shortSource.resolve(TUNNEL_DOOR_VERTICAL_MAP, false, 0).pieces());
        assertThrows(IllegalArgumentException.class,
                () -> shortSource.resolve(TUNNEL_DOOR_VERTICAL_MAP, false, 1));
        var visual = CdObjectVisualAssets.decodeStatic(TUNNEL_DOOR_VERTICAL_SOURCE, 2, 12);
        assertEquals(2, visual.mappingFrames().size());
        assertEquals(List.of(), visual.animations()); assertEquals(0, visual.animationTable().length());
        for (int frame = 0; frame < 2; frame++) {
            assertEquals(11, visual.mappingFrames().get(frame).bytes().length());
        }
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decodeStatic(TUNNEL_DOOR_VERTICAL_SOURCE, 2, 11));
        for (int pad : new int[]{15, 27}) {
            byte[] bad = TUNNEL_DOOR_VERTICAL_SOURCE.clone(); bad[pad] = 1;
            assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(bad, 2, 12));
        }
    }

    @Test void actualUsaVerticalTunnelDoorJoinsItsExactSourceAndPreservesEveryPriorMappingRoot() throws Exception {
        var usa = usaMedia(); // Actual media closed before this optional mapping's first access.
        var objects = usa.objects();
        var initial = objects.initialArtBank();
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        var floatBlock = objects.floatBlockArtBank();
        var spring = objects.springArtBank();
        var table = CdR11aObjectAssets.Table.TUNNEL_DOOR_VERTICAL_MAPPINGS;
        var view = objects.view(table);
        assertEquals("MapSpr_FlapDoorV", table.nativeLabel());
        assertEquals(0xCE9A, view.range().fileOffset());
        assertEquals(28, view.range().length());
        assertEquals(TUNNEL_DOOR_VERTICAL_MAP, view.range().nativeAddress());
        assertEquals(table.range(), view.range());
        assertArrayEquals(TUNNEL_DOOR_VERTICAL_SOURCE, view.bytes().copy());
        var visual = objects.tunnelDoorVertical();
        assertEquals(0, visual.mappingTableOffset());
        assertEquals(2, visual.mappingFrames().size());
        assertEquals(List.of(), visual.animations()); assertEquals(0, visual.animationTable().length());
        assertArrayEquals(view.bytes().copy(), visual.mappings().copy());
        assertArrayEquals(Arrays.copyOfRange(TUNNEL_DOOR_VERTICAL_SOURCE, 4, 15),
                visual.mappingFrames().get(0).bytes().copy());
        assertArrayEquals(Arrays.copyOfRange(TUNNEL_DOOR_VERTICAL_SOURCE, 16, 27),
                visual.mappingFrames().get(1).bytes().copy());
        var resolver = CdObjectMappings.fromOpeningAssets(usa.assets(), objects);
        assertVerticalTunnelDoorFrames(resolver);

        // All earlier visual roots, including the two independent Spring bases, retain every raw piece.
        for (var oldVisual : List.of(objects.rings(), objects.normalMosqui(), objects.damagedMosqui(),
                objects.plant(), objects.rampBooster(), objects.explosion(), objects.points(), objects.flower(),
                objects.floatBlock(), objects.springVertical(), objects.springHorizontal())) {
            int root;
            if (oldVisual == objects.rings()) root = 0x20A0FA;
            else if (oldVisual == objects.normalMosqui()) root = 0x20B6E6;
            else if (oldVisual == objects.damagedMosqui()) root = 0x20B722;
            else if (oldVisual == objects.plant()) root = 0x20E020;
            else if (oldVisual == objects.rampBooster()) root = 0x21FDBC;
            else if (oldVisual == objects.explosion()) root = 0x23F088;
            else if (oldVisual == objects.points()) root = 0x20AC64;
            else if (oldVisual == objects.flower()) root = 0x235EF4;
            else if (oldVisual == objects.floatBlock()) root = FLOAT_BLOCK_MAP;
            else if (oldVisual == objects.springVertical()) root = 0x209BA0;
            else root = 0x209BA6;
            for (int frame = 0; frame < oldVisual.mappingFrames().size(); frame++) {
                var expected = oldVisual.mappingFrames().get(frame).pieces().stream()
                        .map(p -> new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x())).toList();
                assertEquals(expected, resolver.resolve(root, false, frame).pieces());
            }
        }
        int sonic = usa.assets().profile().range(CdR11aAsset.MAP_SPR_SONIC).nativeAddress();
        for (int frame = 0; frame < usa.assets().sonic().mappingFrames().size(); frame++) {
            var body = usa.assets().sonic().mappingFrames().get(frame);
            var expected = new java.util.ArrayList<CdObjectDrawQueue.RawPiece>();
            for (int piece = 0; piece < body.pieceCount(); piece++) {
                int at = 1 + piece * 5;
                expected.add(new CdObjectDrawQueue.RawPiece(body.bytes().signedByte(at), body.bytes().unsignedByte(at + 1),
                        body.bytes().unsignedWord(at + 2), body.bytes().signedByte(at + 4)));
            }
            assertEquals(expected, resolver.resolve(sonic, false, frame).pieces());
        }
        assertGameOverRoots(resolver);
        assertPowerupFrames(resolver);
        assertSame(initial, objects.initialArtBank()); assertSame(frontier, objects.frontierArtBank());
        assertSame(contact, objects.contactArtBank()); assertSame(floatBlock, objects.floatBlockArtBank());
        assertSame(spring, objects.springArtBank()); assertSame(visual, objects.tunnelDoorVertical());
        byte[] detached = view.bytes().copy(); Arrays.fill(detached, (byte) 0);
        assertVerticalTunnelDoorFrames(resolver);
        assertThrows(UnsupportedOperationException.class,
                () -> resolver.resolve(TUNNEL_DOOR_VERTICAL_MAP, false, 0).pieces().clear());
    }

    private static void assertVerticalTunnelDoorFrames(CdObjectMappings resolver) {
        for (int frame = 0; frame < 2; frame++) {
            assertEquals(new CdObjectDrawQueue.FrameMapping(2, TUNNEL_DOOR_VERTICAL_FRAMES.get(frame)),
                    resolver.resolve(TUNNEL_DOOR_VERTICAL_MAP, false, frame));
        }
        for (int frame = 2; frame <= 255; frame++) {
            int badFrame = frame;
            assertThrows(UnsupportedOperationException.class,
                    () -> resolver.resolve(TUNNEL_DOOR_VERTICAL_MAP, false, badFrame));
        }
        for (int frame : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class,
                    () -> resolver.resolve(TUNNEL_DOOR_VERTICAL_MAP, false, frame));
        }
        for (int frame : new int[]{0, 1, 255}) {
            assertThrows(UnsupportedOperationException.class,
                    () -> resolver.resolve(TUNNEL_DOOR_VERTICAL_MAP, true, frame));
        }
        for (int pointer : new int[]{0x20CE99, 0x20CE9B, 0x20CE9C, 0x20CE9E, 0x20CEAA, 0x20CEB6,
                0xFF20CE9A, 0x8020CE9A}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
        }
    }

    /** Complete source-text directives from Monitor and Time Post/Data/Mappings.asm:1-176, N 1ecc5f3af.
     * Constructed format bytes, not an authenticated media owner. Native roots are separately literalized. */
    private static final byte[] MONITOR_TIME_SOURCE = HexFormat.of().parseHex("0028003E0054006A0080009600AC00C200D800EE0104011E01380152016C018601A001B001B601BC04F6050012"
            + "F8F0060000F0F006080000080C0006F00004F6050016F8F0060000F0F006080000080C0006F00004F605001AF8"
            + "F0060000F0F006080000080C0006F00004F605001EF8F0060000F0F006080000080C0006F00004F6050022F8F0"
            + "060000F0F006080000080C0006F00004F6050026F8F0060000F0F006080000080C0006F00004F605002AF8F006"
            + "0000F0F006080000080C0006F00004F605002EF8F0060000F0F006080000080C0006F00004F6050048F8F00600"
            + "00F0F006080000080C0006F00004F6050848F8F0060000F0F006080000080C0006F00005D80D0032F0E803004C"
            + "F8E803084C0008010050F8080108500005D80D0832F0E803004CF8E803084C0008010050F8080108500005D80D"
            + "003AF0E803004CF8E803084C0008010050F8080108500005D80D083AF0E803004CF8E803084C0008010050F808"
            + "0108500005D8050042F8E803004CF8E803084C0008010050F8080108500005D8010046FCE803004CF8E803084C"
            + "0008010050F8080108500003F0060000F0F006080000080C0006F001000D000AF001F80D0032F001F80D003AF0");
    private static final int MONITOR_TIME_MAP = 0x20AA4E;
    private static final int[] MONITOR_ITEM_POINTERS = {
            0x20AA77, 0x20AA8D, 0x20AAA3, 0x20AAB9, 0x20AACF, 0x20AAE5, 0x20AAFB, 0x20AB11};
    private static final int[] MONITOR_FRAME_OFFSETS = {
            0x28, 0x3E, 0x54, 0x6A, 0x80, 0x96, 0xAC, 0xC2, 0xD8, 0xEE,
            0x104, 0x11E, 0x138, 0x152, 0x16C, 0x186, 0x1A0, 0x1B0, 0x1B6, 0x1BC};
    /** Independent piece expectations: {signed Y, size, full raw tile word, signed X}. */
    private static final int[][][] MONITOR_TIME_FRAMES = {
            {{-10,5,0x0012,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x0016,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x001A,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x001E,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x0022,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x0026,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x002A,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x002E,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x0048,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-10,5,0x0848,-8},{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{-40,13,0x0032,-16},{-24,3,0x004C,-8},{-24,3,0x084C,0},{8,1,0x0050,-8},{8,1,0x0850,0}},
            {{-40,13,0x0832,-16},{-24,3,0x004C,-8},{-24,3,0x084C,0},{8,1,0x0050,-8},{8,1,0x0850,0}},
            {{-40,13,0x003A,-16},{-24,3,0x004C,-8},{-24,3,0x084C,0},{8,1,0x0050,-8},{8,1,0x0850,0}},
            {{-40,13,0x083A,-16},{-24,3,0x004C,-8},{-24,3,0x084C,0},{8,1,0x0050,-8},{8,1,0x0850,0}},
            {{-40,5,0x0042,-8},{-24,3,0x004C,-8},{-24,3,0x084C,0},{8,1,0x0050,-8},{8,1,0x0850,0}},
            {{-40,1,0x0046,-4},{-24,3,0x004C,-8},{-24,3,0x084C,0},{8,1,0x0050,-8},{8,1,0x0850,0}},
            {{-16,6,0,-16},{-16,6,0x0800,0},{8,12,6,-16}},
            {{0,13,0x000A,-16}}, {{-8,13,0x0032,-16}}, {{-8,13,0x003A,-16}}};

    @Test void declaredMonitorTimeKeepsAllTwentyLiteralCountedBodiesAndSignedPointerDomains() throws Exception {
        assertEquals(450, MONITOR_TIME_SOURCE.length);
        assertEquals("00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca", sha256(MONITOR_TIME_SOURCE));
        var resolver = declaredMonitorTime();
        assertMonitorTimeFrames(resolver);
        for (int frame = 0; frame < 20; frame++) {
            assertEquals(MONITOR_FRAME_OFFSETS[frame], (short) word(MONITOR_TIME_SOURCE, frame * 2));
            if (frame < 10) assertEquals(0, MONITOR_TIME_SOURCE[MONITOR_FRAME_OFFSETS[frame] + 21]);
        }
        for (int frame = 20; frame <= 255; frame++) {
            int absent = frame;
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(MONITOR_TIME_MAP, false, absent));
        }
        for (int frame : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(MONITOR_TIME_MAP, false, frame));
        }
        for (int pointer : new int[]{0x20AA4D, 0x20AA4F, 0x20AA76, 0x20AA8C, 0xFF20AA4E, 0x8020AA4E}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
        }
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(MONITOR_TIME_MAP, true, 0));
        var shortView = declared(MONITOR_TIME_MAP, Arrays.copyOf(MONITOR_TIME_SOURCE, 449), 0, 20);
        assertEquals(resolver.resolve(MONITOR_TIME_MAP, false, 0), shortView.resolve(MONITOR_TIME_MAP, false, 0));
        assertThrows(IllegalArgumentException.class, () -> shortView.resolve(MONITOR_TIME_MAP, false, 19));
        byte[] beforeRoot = MONITOR_TIME_SOURCE.clone(); putWord(beforeRoot, 0, -1);
        var escaped = declared(MONITOR_TIME_MAP, beforeRoot, 0, 20);
        assertThrows(IllegalArgumentException.class, () -> escaped.resolve(MONITOR_TIME_MAP, false, 0));
    }

    @Test void declaredMonitorItemsKeepEightOddHeaderFreeFirstPiecesWithoutAdmittingOtherPointers() {
        var resolver = declaredMonitorTime();
        int[] offsets = {0x29, 0x3F, 0x55, 0x6B, 0x81, 0x97, 0xAD, 0xC3};
        int[] words = {0x12, 0x16, 0x1A, 0x1E, 0x22, 0x26, 0x2A, 0x2E};
        for (int item = 0; item < 8; item++) {
            int pointer = MONITOR_ITEM_POINTERS[item];
            assertEquals(1, pointer & 1);
            assertEquals(pointer, MONITOR_TIME_MAP + (short) word(MONITOR_TIME_SOURCE, item * 2) + 1);
            assertArrayEquals(bytes(0xF6, 5, 0, words[item], 0xF8),
                    Arrays.copyOfRange(MONITOR_TIME_SOURCE, offsets[item], offsets[item] + 5));
            for (int ignoredFrame : new int[]{0, item, 255}) {
                assertEquals(new CdObjectDrawQueue.StaticMapping(new CdObjectDrawQueue.RawPiece(-10, 5, words[item], -8)),
                        resolver.resolve(pointer, true, ignoredFrame));
            }
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
            for (int absent : new int[]{pointer - 1, pointer + 1, pointer | 0xFF000000, pointer | 0x80000000}) {
                assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(absent, true, 0));
            }
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, true, 0).pieces().clear());
        }
        for (int absent : new int[]{0x20AB27, 0x20AB3D, 0x20AC10}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(absent, true, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdObjectMappings.DeclaredStaticPiece(
                MONITOR_ITEM_POINTERS[0], CdAssetBytes.copyOf(new byte[4])));
    }

    @Test void monitorCountedAndStaticPiecesKeepExactQueueSatMirrorsAndFullWordAttributes() {
        var resolver = declaredMonitorTime();
        for (int base : new int[]{0x05A8, 0x85A8, 0xFFA8}) {
            for (int flips = 0; flips < 4; flips++) {
                for (int frame = 0; frame < 20; frame++) {
                    assertMonitorSat(resolver, MONITOR_TIME_MAP, frame, base, 4 | flips, monitorPieces(frame));
                }
                for (int item = 0; item < 8; item++) {
                    assertMonitorSat(resolver, MONITOR_ITEM_POINTERS[item], 255, base, 0x24 | flips,
                            List.of(monitorPieces(item).getFirst()));
                }
            }
        }
        var shoes = drawMonitorMapping(resolver, 0x20AACF, 4, 0x85A8, 0x24);
        assertEquals(1, shoes.spriteCount());
        assertArrayEquals(bytes(0,0xF6,5,1,0x85,0xCA,1,0x78), Arrays.copyOf(shoes.bytes(), 8));
        var mirrored = drawMonitorMapping(resolver, 0x20AACF, 255, 0x85A8, 0x27);
        assertArrayEquals(bytes(0,0xFA,5,1,0x9D,0xCA,1,0x78), Arrays.copyOf(mirrored.bytes(), 8));
        var future = drawMonitorMapping(resolver, MONITOR_TIME_MAP, 9, 0xFFA8, 4);
        assertEquals(0x07F0, word(future.bytes(), 4), "full ADD.W carries before any object flip XOR");
    }

    @Test void actualUsaMonitorTimeJoinsTwentyCountedAndEightStaticRootsAndPreservesAllEarlierRoots() throws Exception {
        var usa = usaMedia(); // Closed media, retained authenticated bytes; no constructed owner bypass.
        var objects = usa.objects();
        var view = objects.view(CdR11aObjectAssets.Table.MONITOR_TIME_MAPPINGS);
        assertEquals(0xAA4E, view.range().fileOffset()); assertEquals(450, view.range().length());
        assertEquals(MONITOR_TIME_MAP, view.range().nativeAddress());
        assertArrayEquals(MONITOR_TIME_SOURCE, view.bytes().copy());
        var visual = objects.monitorTime(); assertEquals(20, visual.mappingFrames().size());
        assertArrayEquals(MONITOR_TIME_SOURCE, visual.mappings().copy());
        var resolver = CdObjectMappings.fromOpeningAssets(usa.assets(), objects);
        assertMonitorTimeFrames(resolver);
        for (int item = 0; item < 8; item++) {
            var expected = new CdObjectDrawQueue.StaticMapping(monitorPieces(item).getFirst());
            assertEquals(expected, resolver.resolve(MONITOR_ITEM_POINTERS[item], true, 255));
            assertMonitorSat(resolver, MONITOR_ITEM_POINTERS[item], item, 0x85A8, 0x24, expected.pieces());
            assertThrows(UnsupportedOperationException.class,
                    () -> resolver.resolve(MONITOR_ITEM_POINTERS[0] | 0xFF000000, true, 0));
        }
        for (int frame = 0; frame < 20; frame++) {
            assertMonitorSat(resolver, MONITOR_TIME_MAP, frame, 0x05A8, 4, monitorPieces(frame));
        }
        assertMonitorPriorRoots(usa, resolver);
        byte[] detached = view.bytes().copy(); Arrays.fill(detached, (byte) 0xA5);
        assertMonitorTimeFrames(resolver); assertSame(visual, objects.monitorTime());
        for (int absent : new int[]{0x20AB27, 0x20AB3D, 0xFF20AA77, 0x8020AA77}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(absent, true, 0));
        }
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(MONITOR_TIME_MAP, false, 20));
    }

    private static List<CdObjectDrawQueue.RawPiece> monitorPieces(int frame) {
        return Arrays.stream(MONITOR_TIME_FRAMES[frame]).map(p ->
                new CdObjectDrawQueue.RawPiece(p[0], p[1], p[2], p[3])).toList();
    }
    private static CdObjectMappings declaredMonitorTime() {
        var staticPieces = new java.util.ArrayList<CdObjectMappings.DeclaredStaticPiece>();
        int[] offsets = {0x29, 0x3F, 0x55, 0x6B, 0x81, 0x97, 0xAD, 0xC3};
        for (int item = 0; item < 8; item++) {
            staticPieces.add(new CdObjectMappings.DeclaredStaticPiece(MONITOR_ITEM_POINTERS[item],
                    CdAssetBytes.copyOf(Arrays.copyOfRange(MONITOR_TIME_SOURCE, offsets[item], offsets[item] + 5))));
        }
        return CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(
                MONITOR_TIME_MAP, CdAssetBytes.copyOf(MONITOR_TIME_SOURCE), 0, 20)), staticPieces);
    }
    private static void assertMonitorTimeFrames(CdObjectMappings resolver) {
        for (int frame = 0; frame < 20; frame++) {
            var expected = monitorPieces(frame);
            assertEquals(new CdObjectDrawQueue.FrameMapping(expected.size(), expected),
                    resolver.resolve(MONITOR_TIME_MAP, false, frame), "literal Monitor/post/HUD frame " + frame);
        }
    }
    private static void assertMonitorPriorRoots(UsaR11a usa, CdObjectMappings resolver) {
        var o = usa.objects();
        var visuals = List.of(o.rings(), o.normalMosqui(), o.damagedMosqui(), o.plant(), o.rampBooster(),
                o.explosion(), o.points(), o.flower(), o.floatBlock(), o.springVertical(), o.springHorizontal(),
                o.tunnelDoorVertical());
        int[] roots = {0x20A0FA,0x20B6E6,0x20B722,0x20E020,0x21FDBC,0x23F088,0x20AC64,0x235EF4,
                0x20A3FA,0x209BA0,0x209BA6,0x20CE9A};
        for (int index = 0; index < roots.length; index++) {
            var visual = visuals.get(index); int root = roots[index];
            for (int frame = 0; frame < visual.mappingFrames().size(); frame++) {
                var expected = visual.mappingFrames().get(frame).pieces().stream().map(p ->
                        new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x())).toList();
                assertEquals(expected, resolver.resolve(root, false, frame).pieces());
            }
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(root, true, 0));
        }
        var sonic = usa.assets().sonic();
        int sonicRoot = usa.assets().profile().range(CdR11aAsset.MAP_SPR_SONIC).nativeAddress();
        for (int frame = 0; frame < sonic.mappingFrames().size(); frame++) {
            var source = sonic.mappingFrames().get(frame); var expected = new java.util.ArrayList<CdObjectDrawQueue.RawPiece>();
            for (int piece = 0; piece < source.pieceCount(); piece++) {
                int at = 1 + piece * 5;
                expected.add(new CdObjectDrawQueue.RawPiece(source.bytes().signedByte(at), source.bytes().unsignedByte(at + 1),
                        source.bytes().unsignedWord(at + 2), source.bytes().signedByte(at + 4)));
            }
            assertEquals(expected, resolver.resolve(sonicRoot, false, frame).pieces());
        }
        assertGameOverRoots(resolver); assertPowerupFrames(resolver); assertVerticalTunnelDoorFrames(resolver);
    }
    private static void assertMonitorSat(CdObjectDrawQueue.CdDrawMappings mappings, int pointer, int frame,
                                         int base, int flags, List<CdObjectDrawQueue.RawPiece> expected) {
        var sat = drawMonitorMapping(mappings, pointer, frame, base, flags);
        assertEquals(expected.size(), sat.spriteCount()); byte[] raw = sat.bytes();
        for (int index = 0; index < expected.size(); index++) {
            var p = expected.get(index); int width = (p.sizeByte() >>> 2) + 1, height = (p.sizeByte() & 3) + 1;
            int x = (flags & 1) == 0 ? p.x() : -p.x() - width * 8;
            int y = (flags & 2) == 0 ? p.y() : -p.y() - height * 8;
            assertEquals(256 + y, word(raw, index * 8)); assertEquals(p.sizeByte(), raw[index * 8 + 2] & 255);
            assertEquals(index + 1, raw[index * 8 + 3] & 255);
            assertEquals(((base + p.rawTileWord()) & 0xFFFF) ^ ((flags & 3) << 11), word(raw, index * 8 + 4));
            assertEquals(384 + x, word(raw, index * 8 + 6));
            assertEquals(new CdObjectDrawQueue.Provenance(3, 0, new CdObjectDrawQueue.CdSlotRef(32), index),
                    sat.provenance().get(index));
        }
        assertArrayEquals(new byte[4], Arrays.copyOfRange(raw, expected.size() * 8, expected.size() * 8 + 4));
    }
    private static CdObjectDrawQueue.CpuSat drawMonitorMapping(CdObjectDrawQueue.CdDrawMappings mappings,
                                                               int pointer, int frame, int base, int flags) {
        var slots = new CdObjectDrawQueue.CdDrawSlots() {
            private CdObjectDrawQueue.LiveDrawSlot current = new CdObjectDrawQueue.LiveDrawSlot(
                    (flags & 0x20) == 0 ? 0x19 : 0x1A, flags, 0x100, 0x80, 0x9999, pointer, frame, base);
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                assertEquals(32, ref.wholeIndex()); return current;
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean on) {
                assertEquals(32, ref.wholeIndex()); var s = current;
                current = new CdObjectDrawQueue.LiveDrawSlot(s.idByte(), (s.spriteFlagsByte() & 127) | (on ? 128 : 0),
                        s.xWord(), s.yWord(), s.screenYWord(), s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord());
            }
        };
        var queue = new CdObjectDrawQueue(slots, mappings, new byte[CdObjectDrawQueue.SAT_BYTE_SIZE]);
        var camera = new CdObjectDrawQueue.CameraWords(0, 0); queue.beginPass();
        assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(new CdObjectDrawQueue.DrawCallCut(
                new CdObjectDrawQueue.CdSlotRef(32), flags, 0x100, 0x80, 0x28, 0x10, 3), camera));
        return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera));
    }

    /**
     * N 1ecc5f3af, Spin Tunnel/Data/Mappings (Door).asm:1-27, complete literal directives.
     * Directory [16,4] selects closed horizontal tiles 6..11 before open vertical tiles 0..5.
     * These declared bytes are format inputs, never an authenticated owner or runtime fallback.
     */
    private static final byte[] TUNNEL_DOOR_SOURCE = bytes(
            0x00, 0x10, 0x00, 0x04,
            0x02, 0xD0, 0x03, 0x00, 0x00, 0xE4, 0xF0, 0x01, 0x00, 0x04, 0xE4, 0x00,
            0x02, 0xF8, 0x0C, 0x00, 0x06, 0xE8, 0xF8, 0x04, 0x00, 0x0A, 0x08, 0x00);
    /** Entire distinct animation include, Animations (Door).asm:1-3; not a counted mapping root. */
    private static final byte[] TUNNEL_DOOR_ANIMATIONS = bytes(
            0x00, 0x02, 0x00, 0x00, 0x00, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0xFC);
    // Actual USA Init MOVE.L at file $62F6 and Animate LEA at $633C select different native longs.
    private static final int TUNNEL_DOOR_MAP = 0x23F038, TUNNEL_DOOR_ANIMATION = 0x23F02A;
    private static final List<List<CdObjectDrawQueue.RawPiece>> TUNNEL_DOOR_FRAMES = List.of(
            List.of(new CdObjectDrawQueue.RawPiece(-8, 0x0C, 0x0006, -24),
                    new CdObjectDrawQueue.RawPiece(-8, 0x04, 0x000A, 8)),
            List.of(new CdObjectDrawQueue.RawPiece(-48, 0x03, 0x0000, -28),
                    new CdObjectDrawQueue.RawPiece(-16, 0x01, 0x0004, -28)));

    @Test void declaredHorizontalTunnelDoorKeepsReversedBodiesSignedGeometryAndRealPads() throws Exception {
        assertEquals(28, TUNNEL_DOOR_SOURCE.length);
        assertEquals("7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92", sha256(TUNNEL_DOOR_SOURCE));
        assertEquals(14, TUNNEL_DOOR_ANIMATIONS.length);
        assertEquals("398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0", sha256(TUNNEL_DOOR_ANIMATIONS));
        assertEquals(16, (short) word(TUNNEL_DOOR_SOURCE, 0));
        assertEquals(4, (short) word(TUNNEL_DOOR_SOURCE, 2));
        assertEquals(0x23F048, TUNNEL_DOOR_MAP + (short) word(TUNNEL_DOOR_SOURCE, 0));
        assertEquals(0x23F03C, TUNNEL_DOOR_MAP + (short) word(TUNNEL_DOOR_SOURCE, 2));
        assertEquals(0, TUNNEL_DOOR_SOURCE[15]); assertEquals(0, TUNNEL_DOOR_SOURCE[27]);
        var resolver = declared(TUNNEL_DOOR_MAP, TUNNEL_DOOR_SOURCE, 0, 2);
        assertHorizontalTunnelDoorFrames(resolver);
        var visual = CdObjectVisualAssets.decode(TUNNEL_DOOR_SOURCE, 2, TUNNEL_DOOR_ANIMATIONS, 1, 12);
        assertArrayEquals(TUNNEL_DOOR_SOURCE, visual.mappings().copy());
        assertArrayEquals(TUNNEL_DOOR_ANIMATIONS, visual.animationTable().copy());
        assertEquals(0, visual.mappingTableOffset()); assertEquals(2, visual.mappingFrames().size());
        assertEquals(16, visual.mappingFrames().get(0).relativeOffset());
        assertEquals(4, visual.mappingFrames().get(1).relativeOffset());
        assertArrayEquals(bytes(2,0xF8,0x0C,0,6,0xE8,0xF8,4,0,0x0A,8),
                visual.mappingFrames().get(0).bytes().copy());
        assertArrayEquals(bytes(2,0xD0,3,0,0,0xE4,0xF0,1,0,4,0xE4),
                visual.mappingFrames().get(1).bytes().copy());
        for (int frame = 0; frame < 2; frame++) {
            var raw = visual.mappingFrames().get(frame).pieces().stream().map(p ->
                    new CdObjectDrawQueue.RawPiece(p.y(), p.sizeByte(), p.rawTileWord(), p.x())).toList();
            assertEquals(TUNNEL_DOOR_FRAMES.get(frame), raw);
            var tiles = new TreeSet<Integer>();
            for (var piece : visual.mappingFrames().get(frame).pieces()) {
                for (int tile = 0; tile < piece.tileCount(); tile++) tiles.add(piece.tileOffset() + tile);
            }
            assertEquals(frame == 0 ? tileRange(6, 12) : tileRange(0, 6), tiles);
        }
        assertEquals(1, visual.animations().size()); var script = visual.animations().getFirst();
        assertEquals(2, script.relativeOffset()); assertEquals(0, script.delay());
        assertEquals(List.of(0,0,1,1,1,1,1,1,1,1), script.frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, script.terminal());
        assertArrayEquals(bytes(0,0,0,1,1,1,1,1,1,1,1,0xFC), script.bytes().copy());
        byte[] detached = visual.mappings().copy(); Arrays.fill(detached, (byte) 0);
        assertArrayEquals(TUNNEL_DOOR_SOURCE, visual.mappings().copy());
        assertHorizontalTunnelDoorFrames(resolver);
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(TUNNEL_DOOR_MAP, false, 0).pieces().clear());
    }

    @Test void horizontalTunnelDoorSignExtendsDeclaredOffsetsAndRejectsMalformedBodies() {
        // A constructed trailing directory checks ADDA.W sign extension against the unchanged literal bodies.
        // It is deliberately NOT a native table/owner claim: only the real root is registered in production.
        byte[] trailingIndex = Arrays.copyOf(TUNNEL_DOOR_SOURCE, 32);
        putWord(trailingIndex, 28, -12); putWord(trailingIndex, 30, -24);
        var backwards = declared(TUNNEL_DOOR_MAP, trailingIndex, 28, 2);
        for (int frame = 0; frame < 2; frame++) {
            assertEquals(new CdObjectDrawQueue.FrameMapping(2, TUNNEL_DOOR_FRAMES.get(frame)),
                    backwards.resolve(0x23F054, false, frame));
        }
        assertThrows(UnsupportedOperationException.class, () -> backwards.resolve(TUNNEL_DOOR_MAP, false, 0));
        var truncated = declared(TUNNEL_DOOR_MAP, Arrays.copyOf(TUNNEL_DOOR_SOURCE, 26), 0, 2);
        assertThrows(IllegalArgumentException.class, () -> truncated.resolve(TUNNEL_DOOR_MAP, false, 0));
        assertEquals(TUNNEL_DOOR_FRAMES.get(1), truncated.resolve(TUNNEL_DOOR_MAP, false, 1).pieces());
        for (int relative : new int[]{-1, 28, 0x8000}) {
            byte[] escaped = TUNNEL_DOOR_SOURCE.clone(); putWord(escaped, 0, relative);
            var resolver = declared(TUNNEL_DOOR_MAP, escaped, 0, 2);
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(TUNNEL_DOOR_MAP, false, 0));
        }
        for (int pad : new int[]{15, 27}) {
            byte[] bad = TUNNEL_DOOR_SOURCE.clone(); bad[pad] = 1;
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectVisualAssets.decode(bad, 2, TUNNEL_DOOR_ANIMATIONS, 1, 12));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(TUNNEL_DOOR_SOURCE, 2, TUNNEL_DOOR_ANIMATIONS, 1, 11));
        byte[] absentFrame = TUNNEL_DOOR_ANIMATIONS.clone(); absentFrame[3] = 2;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(TUNNEL_DOOR_SOURCE, 2, absentFrame, 1, 12));
        byte[] noTerminal = TUNNEL_DOOR_ANIMATIONS.clone(); noTerminal[13] = 0;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(TUNNEL_DOOR_SOURCE, 2, noTerminal, 1, 12));
    }

    @Test void horizontalTunnelDoorQueueKeepsLiteralSatWordsThroughCarriesAndAllFlips() {
        assertHorizontalTunnelDoorSat(declared(TUNNEL_DOOR_MAP, TUNNEL_DOOR_SOURCE, 0, 2));
    }

    @Test void actualUsaHorizontalTunnelDoorJoinsRetainedOwnerWithoutExtendingPatterns() throws Exception {
        var usa = usaMedia(); // Existing supplied-media opt-in; source is closed before the first Door access.
        var objects = usa.objects(); var initial = objects.initialArtBank();
        var frontier = objects.frontierArtBank(); var contact = objects.contactArtBank();
        var floatBlock = objects.floatBlockArtBank(); var spring = objects.springArtBank();
        var door = objects.tunnelDoorArtBank(); var monitor = objects.monitorTimeArtBank();
        var vertical = objects.tunnelDoorVertical(); var monitorVisual = objects.monitorTime();
        var view = objects.view(CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS);
        var animation = objects.view(CdR11aObjectAssets.Table.TUNNEL_DOOR_ANIMATIONS);
        assertEquals(usa.assets().identity(), objects.identity());
        assertEquals(0x3F038, view.range().fileOffset()); assertEquals(28, view.range().length());
        assertEquals(TUNNEL_DOOR_MAP, view.range().nativeAddress());
        assertEquals(CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS.range(), view.range());
        assertEquals(0x3F02A, animation.range().fileOffset()); assertEquals(14, animation.range().length());
        assertEquals(TUNNEL_DOOR_ANIMATION, animation.range().nativeAddress());
        assertArrayEquals(TUNNEL_DOOR_SOURCE, view.bytes().copy());
        assertArrayEquals(TUNNEL_DOOR_ANIMATIONS, animation.bytes().copy());
        var visual = objects.tunnelDoor(); assertSame(visual, objects.tunnelDoor());
        assertEquals(2, visual.mappingFrames().size()); assertEquals(1, visual.animations().size());
        assertArrayEquals(TUNNEL_DOOR_SOURCE, visual.mappings().copy());
        assertArrayEquals(TUNNEL_DOOR_ANIMATIONS, visual.animationTable().copy());
        var resolver = CdObjectMappings.fromOpeningAssets(usa.assets(), objects);
        assertHorizontalTunnelDoorFrames(resolver); assertHorizontalTunnelDoorSat(resolver);
        // All 17 prior counted roots plus all eight raw Monitor icon roots retain their old expectations.
        assertMonitorPriorRoots(usa, resolver); assertMonitorTimeFrames(resolver);
        for (int item = 0; item < 8; item++) {
            assertEquals(new CdObjectDrawQueue.StaticMapping(monitorPieces(item).getFirst()),
                    resolver.resolve(MONITOR_ITEM_POINTERS[item], true, 255));
        }
        var binding = door.binding(CdR11aAsset.ART_TUNNEL_DOOR);
        assertEquals(1, door.bindings().size()); assertEquals(0x328, binding.firstTileId());
        assertEquals(12, binding.tileCount()); assertEquals(384, binding.bytes().length());
        assertEquals("dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0", sha256(binding.bytes().copy()));
        assertEquals(0x8328, objects.tileWord(0xC, 0));
        // Explicit constructed RAM windows exercise the existing pattern binder, not native service/residency.
        var player = new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false);
        var powerup = new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]);
        var bank = CdObjectPresentation.usaR11aMonitorTimeDevPreloadedArt(player, powerup, objects,
                frontier, contact, floatBlock, spring, door, monitor,
                10000,11000,12000,13000,14000,15000,16000,17000,18000,19000,20000,21000,22000);
        int count = 0; var logical = new HashSet<Integer>();
        for (int nativeTile = 0; nativeTile < 0x800; nativeTile++) {
            try {
                var sample = bank.resolve(nativeTile); count++;
                assertTrue(logical.add(sample.logicalPatternId()));
            } catch (UnsupportedOperationException unbound) { /* The existing policy has finite gaps. */ }
        }
        assertEquals(494, count); assertEquals(494, logical.size());
        for (int tile = 0; tile < 12; tile++) {
            assertEquals(21000 + tile, bank.resolve(0x328 + tile).logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(binding.bytes().slice(tile * 32, 32)),
                    bank.resolve(0x328 + tile).version());
        }
        assertThrows(UnsupportedOperationException.class, () -> door.tile(0x327));
        assertThrows(UnsupportedOperationException.class, () -> door.tile(0x334));
        for (int frame = 0; frame < 2; frame++) {
            var requested = new TreeSet<Integer>();
            var sat = drawHorizontalTunnelDoor(resolver, frame, 0x8328, 4);
            var picture = CdObjectPresentation.convert(sat, id -> { requested.add(id); return bank.resolve(id); });
            assertEquals(frame == 0 ? tileRange(0x32E, 0x334) : tileRange(0x328, 0x32E), requested);
            assertEquals(6, picture.tiles().size()); assertEquals(6, picture.patternVersions().size());
            for (var tile : picture.tiles()) {
                assertTrue(tile.priority()); assertEquals(0, tile.palette());
                assertFalse(tile.hFlip()); assertFalse(tile.vFlip());
            }
            var failure = assertThrows(UnsupportedOperationException.class,
                    () -> CdObjectPresentation.convert(sat, id -> {
                        throw new UnsupportedOperationException("Missing required source tile " + id);
                    }));
            assertTrue(failure.getMessage().startsWith("Missing required source tile "));
        }
        assertSame(initial, objects.initialArtBank()); assertSame(frontier, objects.frontierArtBank());
        assertSame(contact, objects.contactArtBank()); assertSame(floatBlock, objects.floatBlockArtBank());
        assertSame(spring, objects.springArtBank()); assertSame(door, objects.tunnelDoorArtBank());
        assertSame(monitor, objects.monitorTimeArtBank()); assertSame(vertical, objects.tunnelDoorVertical());
        assertSame(monitorVisual, objects.monitorTime()); assertSame(visual, objects.tunnelDoor());
        byte[] detached = view.bytes().copy(); Arrays.fill(detached, (byte) 0xA5);
        assertHorizontalTunnelDoorFrames(resolver);
    }

    private static void assertHorizontalTunnelDoorFrames(CdObjectMappings resolver) {
        for (int frame = 0; frame < 2; frame++) {
            assertEquals(new CdObjectDrawQueue.FrameMapping(2, TUNNEL_DOOR_FRAMES.get(frame)),
                    resolver.resolve(TUNNEL_DOOR_MAP, false, frame));
        }
        for (int frame = 2; frame <= 255; frame++) {
            int absent = frame;
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(TUNNEL_DOOR_MAP, false, absent));
        }
        for (int frame : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> resolver.resolve(TUNNEL_DOOR_MAP, false, frame));
        }
        for (int frame : new int[]{0, 1, 255}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(TUNNEL_DOOR_MAP, true, frame));
        }
        for (int pointer : new int[]{TUNNEL_DOOR_ANIMATION, 0x20CE8C, 0x23F037, 0x23F039, 0x23F03A,
                0x23F03C, 0x23F048, 0x23F054, 0xFF23F038, 0x8023F038}) {
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, false, 0));
            assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer, true, 0));
        }
    }

    /** Literal SAT results, independently hand-calculated from the source pieces; no mapper-derived expectations. */
    private static void assertHorizontalTunnelDoorSat(CdObjectDrawQueue.CdDrawMappings mappings) {
        int[] bases = {0x8328, 0x07FC, 0xFFFC, 0xDFFC};
        // Per base, then object flips 0/X/Y/XY: closed piece words, then open piece words.
        int[][][] words = {
                {{0x832E,0x8332,0x8328,0x832C},{0x8B2E,0x8B32,0x8B28,0x8B2C},
                        {0x932E,0x9332,0x9328,0x932C},{0x9B2E,0x9B32,0x9B28,0x9B2C}},
                {{0x0802,0x0806,0x07FC,0x0800},{0x0002,0x0006,0x0FFC,0x0000},
                        {0x1802,0x1806,0x17FC,0x1800},{0x1002,0x1006,0x1FFC,0x1000}},
                {{0x0002,0x0006,0xFFFC,0x0000},{0x0802,0x0806,0xF7FC,0x0800},
                        {0x1002,0x1006,0xEFFC,0x1000},{0x1802,0x1806,0xE7FC,0x1800}},
                {{0xE002,0xE006,0xDFFC,0xE000},{0xE802,0xE806,0xD7FC,0xE800},
                        {0xF002,0xF006,0xCFFC,0xF000},{0xF802,0xF806,0xC7FC,0xF800}}};
        // Per flips: closed Y0/X0/Y1/X1, then open Y0/X0/Y1/X1. Origins are X=$180, Y=$100.
        int[][] positions = {
                {0xF8,0x168,0xF8,0x188,0xD0,0x164,0xF0,0x164},
                {0xF8,0x178,0xF8,0x168,0xD0,0x194,0xF0,0x194},
                {0x100,0x168,0x100,0x188,0x110,0x164,0x100,0x164},
                {0x100,0x178,0x100,0x168,0x110,0x194,0x100,0x194}};
        int[][] sizes = {{0x0C, 4}, {3, 1}};
        for (int base = 0; base < bases.length; base++) {
            for (int flips = 0; flips < 4; flips++) {
                for (int frame = 0; frame < 2; frame++) {
                    var sat = drawHorizontalTunnelDoor(mappings, frame, bases[base], 4 | flips);
                    assertEquals(2, sat.spriteCount()); byte[] raw = sat.bytes();
                    for (int piece = 0; piece < 2; piece++) {
                        assertEquals(positions[flips][frame * 4 + piece * 2], word(raw, piece * 8));
                        assertEquals(sizes[frame][piece], raw[piece * 8 + 2] & 255);
                        assertEquals(piece + 1, raw[piece * 8 + 3] & 255);
                        assertEquals(words[base][flips][frame * 2 + piece], word(raw, piece * 8 + 4));
                        assertEquals(positions[flips][frame * 4 + piece * 2 + 1], word(raw, piece * 8 + 6));
                        assertEquals(new CdObjectDrawQueue.Provenance(1, 0, new CdObjectDrawQueue.CdSlotRef(32), piece),
                                sat.provenance().get(piece));
                    }
                    assertArrayEquals(new byte[4], Arrays.copyOfRange(raw, 16, 20));
                }
            }
        }
    }

    /** CPU queue fixture with native ID $0D and Init dimensions; no runtime object dispatch is introduced. */
    private static CdObjectDrawQueue.CpuSat drawHorizontalTunnelDoor(CdObjectDrawQueue.CdDrawMappings mappings,
                                                                    int frame, int base, int flags) {
        var slots = new CdObjectDrawQueue.CdDrawSlots() {
            private CdObjectDrawQueue.LiveDrawSlot current = new CdObjectDrawQueue.LiveDrawSlot(
                    0x0D, flags, 0x100, 0x80, 0x9999, TUNNEL_DOOR_MAP, frame, base);
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                assertEquals(32, ref.wholeIndex()); return current;
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean on) {
                assertEquals(32, ref.wholeIndex()); var s = current;
                current = new CdObjectDrawQueue.LiveDrawSlot(s.idByte(), (s.spriteFlagsByte() & 127) | (on ? 128 : 0),
                        s.xWord(), s.yWord(), s.screenYWord(), s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord());
            }
        };
        try (var queue = new CdObjectDrawQueue(slots, mappings, new byte[CdObjectDrawQueue.SAT_BYTE_SIZE])) {
            var camera = new CdObjectDrawQueue.CameraWords(0, 0); queue.beginPass();
            assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(new CdObjectDrawQueue.DrawCallCut(
                    new CdObjectDrawQueue.CdSlotRef(32), flags, 0x100, 0x80, 8, 0x2C, 1), camera));
            return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera));
        }
    }

    /** N 1ecc5f3af, Pata-Bata/Data/Mappings (Normal).asm:1-32 and (Damaged).asm:1-38.
     * Literal format inputs only; the supplied-media test independently joins the authenticated owner.
     */
    private static final byte[] PATA_BATA_NORMAL_SOURCE = HexFormat.of().parseHex(
            "00060011001C02F0070000F0F00700080002F80D0010F0080000180001F00F0019F0");
    private static final byte[] PATA_BATA_DAMAGED_SOURCE = HexFormat.of().parseHex(
            "00060011001C02F0070000F0F00700290002F80D0010F00800001800020000003100F00F0019F000");
    private static final byte[] PATA_BATA_ANIMATIONS = HexFormat.of().parseHex(
            "0004000B070000010201FF030000010201FF");
    private static final int PATA_BATA_NORMAL_MAP = 0x20B52E, PATA_BATA_DAMAGED_MAP = 0x20B550;

    @Test void declaredPataBataKeepsBothThreeFrameRootsOddBodiesRealPadAndCompleteSharedScripts() throws Exception {
        assertEquals(34, PATA_BATA_NORMAL_SOURCE.length); assertEquals(40, PATA_BATA_DAMAGED_SOURCE.length);
        assertEquals("9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b",
                sha256(PATA_BATA_NORMAL_SOURCE));
        assertEquals("96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889",
                sha256(PATA_BATA_DAMAGED_SOURCE));
        assertEquals("a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3",
                sha256(PATA_BATA_ANIMATIONS));
        var mappings = declaredPataBata(); assertPataBataFrames(mappings);
        for (int damaged = 0; damaged < 2; damaged++) {
            byte[] source = damaged == 0 ? PATA_BATA_NORMAL_SOURCE : PATA_BATA_DAMAGED_SOURCE;
            var visual = CdObjectVisualAssets.decode(source, 3, PATA_BATA_ANIMATIONS, 2, 50);
            assertPataBataVisual(visual, damaged != 0);
            var bodies = visual.mappingFrames();
            assertEquals(List.of(6,17,28), bodies.stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
            assertEquals(damaged == 0 ? List.of(11,11,6) : List.of(11,11,11),
                    bodies.stream().map(f -> f.bytes().length()).toList());
            assertEquals(1, bodies.get(1).relativeOffset() % 2, "the second real body starts odd");
            assertEquals(damaged == 0 ? 34 : 39,
                    bodies.get(2).relativeOffset() + bodies.get(2).bytes().length());
            if (damaged != 0) assertEquals(0, visual.mappings().unsignedByte(39));
            assertThrows(UnsupportedOperationException.class, () -> bodies.clear());
            assertThrows(UnsupportedOperationException.class, () -> visual.animations().clear());
        }
        var truncated = Arrays.copyOf(PATA_BATA_DAMAGED_SOURCE, 38);
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(truncated, 3, PATA_BATA_ANIMATIONS, 2, 50));
        var badPad = PATA_BATA_DAMAGED_SOURCE.clone(); badPad[39] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(badPad, 3, PATA_BATA_ANIMATIONS, 2, 50));
        var absentScriptFrame = PATA_BATA_ANIMATIONS.clone(); absentScriptFrame[8] = 3;
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(PATA_BATA_NORMAL_SOURCE, 3, absentScriptFrame, 2, 50));
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(PATA_BATA_DAMAGED_SOURCE, 3, PATA_BATA_ANIMATIONS, 2, 49));
    }

    @Test void bothPataBataDirectoriesKeepFullQueueSatWordAdditionAllMirrorsAndLiteralPositions() {
        var mappings = declaredPataBata();
        for (boolean damaged : new boolean[]{false, true}) {
            int root = damaged ? PATA_BATA_DAMAGED_MAP : PATA_BATA_NORMAL_MAP;
            for (int base : new int[]{0x23B0,0xA3B0,0xFFF0}) {
                for (int flips = 0; flips < 4; flips++) {
                    for (int frame = 0; frame < 3; frame++) {
                        assertPataBataSat(mappings, root, frame, base, 4 | flips, pataBataPieces(damaged, frame));
                    }
                }
            }
        }
        assertArrayEquals(bytes(0,0xF0,7,1,0x23,0xB0,1,0x70,0,0xF0,7,2,0x23,0xB8,1,0x80),
                Arrays.copyOf(drawPataBataMapping(mappings, PATA_BATA_NORMAL_MAP, 0, 0x23B0, 4).bytes(),16));
        assertArrayEquals(bytes(1,0,0,1,0x23,0xE1,1,0x80,0,0xF0,15,2,0x23,0xC9,1,0x70),
                Arrays.copyOf(drawPataBataMapping(mappings, PATA_BATA_DAMAGED_MAP, 2, 0x23B0, 4).bytes(),16));
        assertArrayEquals(bytes(0,0xF0,7,1,0xE7,0xF0,1,0x80,0,0xF0,7,2,0x18,0x19,1,0x70),
                Arrays.copyOf(drawPataBataMapping(mappings, PATA_BATA_DAMAGED_MAP, 0, 0xFFF0, 7).bytes(),16));
    }

    @Test void actualUsaPataBataJoinsBothExactOwnerRootsAndRetainsEveryPreviousCountedAndStaticRoot() throws Exception {
        var usa = usaMedia(); var objects = usa.objects();
        var normal = objects.pataBataNormal(); var damaged = objects.pataBataDamaged();
        var normalView = objects.view(CdR11aObjectAssets.Table.PATA_BATA_NORMAL_MAPPINGS);
        var damagedView = objects.view(CdR11aObjectAssets.Table.PATA_BATA_DAMAGED_MAPPINGS);
        var animation = objects.view(CdR11aObjectAssets.Table.PATA_BATA_ANIMATIONS);
        assertEquals(usa.assets().identity(), objects.identity());
        assertEquals(0xB52E, normalView.range().fileOffset()); assertEquals(34, normalView.range().length());
        assertEquals(PATA_BATA_NORMAL_MAP, normalView.range().nativeAddress());
        assertEquals(0xB550, damagedView.range().fileOffset()); assertEquals(40, damagedView.range().length());
        assertEquals(PATA_BATA_DAMAGED_MAP, damagedView.range().nativeAddress());
        assertEquals(0xB51C, animation.range().fileOffset()); assertEquals(18, animation.range().length());
        assertEquals(0x20B51C, animation.range().nativeAddress());
        assertArrayEquals(PATA_BATA_NORMAL_SOURCE, normalView.bytes().copy());
        assertArrayEquals(PATA_BATA_DAMAGED_SOURCE, damagedView.bytes().copy());
        assertArrayEquals(PATA_BATA_ANIMATIONS, animation.bytes().copy());
        assertPataBataVisual(normal, false); assertPataBataVisual(damaged, true);
        var resolver = CdObjectMappings.fromOpeningAssets(usa.assets(), objects);
        assertPataBataFrames(resolver); assertMonitorPriorRoots(usa, resolver); assertMonitorTimeFrames(resolver);
        assertHorizontalTunnelDoorFrames(resolver);
        for (int item = 0; item < 8; item++) {
            assertEquals(new CdObjectDrawQueue.StaticMapping(monitorPieces(item).getFirst()),
                    resolver.resolve(MONITOR_ITEM_POINTERS[item], true, 255));
        }
        assertEquals(0x23B0, objects.tileWord(1, 0));
        for (boolean isDamaged : new boolean[]{false,true}) {
            int pointer = isDamaged ? PATA_BATA_DAMAGED_MAP : PATA_BATA_NORMAL_MAP;
            for (int frame = 0; frame < 3; frame++) for (int flips = 0; flips < 4; flips++) {
                assertPataBataSat(resolver, pointer, frame, 0x23B0, 4 | flips, pataBataPieces(isDamaged, frame));
            }
        }
        for (var view : List.of(normalView, damagedView, animation)) {
            byte[] detached = view.bytes().copy(); Arrays.fill(detached, (byte) 0xA5);
        }
        assertPataBataFrames(resolver); assertSame(normal, objects.pataBataNormal()); assertSame(damaged, objects.pataBataDamaged());
        assertPataBataVisual(normal, false); assertPataBataVisual(damaged, true);
    }

    private static List<CdObjectDrawQueue.RawPiece> pataBataPieces(boolean damaged, int frame) {
        return switch (frame) {
            case 0 -> List.of(new CdObjectDrawQueue.RawPiece(-16,7,0,-16),
                    new CdObjectDrawQueue.RawPiece(-16,7,damaged ? 0x29 : 8,0));
            case 1 -> List.of(new CdObjectDrawQueue.RawPiece(-8,13,0x10,-16),
                    new CdObjectDrawQueue.RawPiece(8,0,0x18,0));
            case 2 -> damaged ? List.of(new CdObjectDrawQueue.RawPiece(0,0,0x31,0),
                    new CdObjectDrawQueue.RawPiece(-16,15,0x19,-16))
                    : List.of(new CdObjectDrawQueue.RawPiece(-16,15,0x19,-16));
            default -> throw new IllegalArgumentException("Pata-Bata has three real frames");
        };
    }
    private static CdObjectMappings declaredPataBata() {
        return CdObjectMappings.declaredSourceViews(List.of(
                new CdObjectMappings.DeclaredTable(PATA_BATA_NORMAL_MAP, CdAssetBytes.copyOf(PATA_BATA_NORMAL_SOURCE),0,3),
                new CdObjectMappings.DeclaredTable(PATA_BATA_DAMAGED_MAP, CdAssetBytes.copyOf(PATA_BATA_DAMAGED_SOURCE),0,3)),
                List.of());
    }
    private static void assertPataBataFrames(CdObjectMappings resolver) {
        for (boolean damaged : new boolean[]{false,true}) {
            int pointer = damaged ? PATA_BATA_DAMAGED_MAP : PATA_BATA_NORMAL_MAP;
            for (int frame = 0; frame < 3; frame++) {
                var expected = pataBataPieces(damaged, frame);
                assertEquals(new CdObjectDrawQueue.FrameMapping(expected.size(), expected), resolver.resolve(pointer,false,frame));
            }
            for (int frame = 3; frame <= 255; frame++) {
                int outside = frame;
                assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer,false,outside));
            }
            for (int invalid : new int[]{-1,256}) {
                assertThrows(IllegalArgumentException.class, () -> resolver.resolve(pointer,false,invalid));
            }
            for (int frame : new int[]{0,255}) {
                assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(pointer,true,frame));
            }
            for (int absent : new int[]{pointer + 6,pointer | 0xFF000000,pointer | 0x80000000}) {
                assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(absent,false,0));
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(0x20B51C,false,0));
    }
    private static void assertPataBataVisual(CdObjectVisualAssets visual, boolean damaged) {
        assertEquals(0, visual.mappingTableOffset()); assertEquals(3, visual.mappingFrames().size());
        assertArrayEquals(damaged ? PATA_BATA_DAMAGED_SOURCE : PATA_BATA_NORMAL_SOURCE, visual.mappings().copy());
        assertArrayEquals(PATA_BATA_ANIMATIONS, visual.animationTable().copy()); assertEquals(2, visual.animations().size());
        for (int script = 0; script < 2; script++) {
            var actual = visual.animations().get(script); assertEquals(script == 0 ? 4 : 11, actual.relativeOffset());
            assertEquals(script == 0 ? 7 : 3, actual.delay()); assertEquals(List.of(0,0,1,2,1), actual.frameBytes());
            assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, actual.terminal());
            assertArrayEquals(Arrays.copyOfRange(PATA_BATA_ANIMATIONS, script == 0 ? 4 : 11, script == 0 ? 11 : 18),
                    actual.bytes().copy());
        }
        for (int frame = 0; frame < 3; frame++) {
            assertEquals(pataBataPieces(damaged,frame), visual.mappingFrames().get(frame).pieces().stream().map(p ->
                    new CdObjectDrawQueue.RawPiece(p.y(),p.sizeByte(),p.rawTileWord(),p.x())).toList());
        }
    }
    private static void assertPataBataSat(CdObjectDrawQueue.CdDrawMappings mappings, int pointer, int frame,
                                          int base, int flags, List<CdObjectDrawQueue.RawPiece> pieces) {
        var sat = drawPataBataMapping(mappings,pointer,frame,base,flags); byte[] raw = sat.bytes();
        assertEquals(pieces.size(),sat.spriteCount());
        for (int index = 0; index < pieces.size(); index++) {
            var p = pieces.get(index); int width = (p.sizeByte() >>> 2) + 1, height = (p.sizeByte() & 3) + 1;
            int x = (flags & 1) == 0 ? p.x() : -p.x() - width * 8;
            int y = (flags & 2) == 0 ? p.y() : -p.y() - height * 8;
            assertEquals(256 + y,word(raw,index * 8)); assertEquals(p.sizeByte(),raw[index * 8 + 2] & 255);
            assertEquals(index + 1,raw[index * 8 + 3] & 255);
            assertEquals(((base + p.rawTileWord()) & 0xFFFF) ^ ((flags & 3) << 11),word(raw,index * 8 + 4));
            assertEquals(384 + x,word(raw,index * 8 + 6));
            assertEquals(new CdObjectDrawQueue.Provenance(3,0,new CdObjectDrawQueue.CdSlotRef(52),index),sat.provenance().get(index));
        }
        assertArrayEquals(new byte[4],Arrays.copyOfRange(raw,pieces.size() * 8,pieces.size() * 8 + 4));
    }
    /** Constructed ID $3E/whole slot 52; no Pata-Bata body, natural contact, route or native-parity claim. */
    private static CdObjectDrawQueue.CpuSat drawPataBataMapping(CdObjectDrawQueue.CdDrawMappings mappings,
                                                               int pointer, int frame, int base, int flags) {
        var slots = new CdObjectDrawQueue.CdDrawSlots() {
            private CdObjectDrawQueue.LiveDrawSlot current = new CdObjectDrawQueue.LiveDrawSlot(0x3E,flags,
                    0x100,0x80,0x9999,pointer,frame,base);
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                assertEquals(52,ref.wholeIndex()); return current;
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean on) {
                assertEquals(52,ref.wholeIndex()); var s = current;
                current = new CdObjectDrawQueue.LiveDrawSlot(s.idByte(),(s.spriteFlagsByte() & 127) | (on ? 128 : 0),
                        s.xWord(),s.yWord(),s.screenYWord(),s.mappingAddressBits(),s.mapFrameByte(),s.baseTileWord());
            }
        };
        var queue = new CdObjectDrawQueue(slots,mappings,new byte[CdObjectDrawQueue.SAT_BYTE_SIZE]);
        var camera = new CdObjectDrawQueue.CameraWords(0,0); queue.beginPass();
        assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED,queue.drawObject(new CdObjectDrawQueue.DrawCallCut(
                new CdObjectDrawQueue.CdSlotRef(52),flags,0x100,0x80,0x10,0x10,3),camera));
        return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera,camera,camera));
    }

    @Test void actualJapanAndEuropeKeepUsaPataBataOwnersAndPointersClosed() throws Exception {
        var usa = usaMedia();
        String configured = System.getProperty("soniccd.media.root");
        for (var variant : List.of(CdReleaseVariant.JAPAN,CdReleaseVariant.EUROPE)) {
            String directory = variant == CdReleaseVariant.JAPAN ? "Japan" : "Europe";
            Path cue;
            try (var files = Files.list(Path.of(configured).resolve(directory))) {
                var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
                assertEquals(1,cues.size()); cue = cues.getFirst();
            }
            try (var source = CdProgramAssets.open(cue,Optional.of(variant),Optional.empty())) {
                var regional = CdR11aAssetLoader.load(source,variant,CdStageKey.parse("R11A"));
                assertEquals(variant,regional.identity().variant());
                assertThrows(UnsupportedOperationException.class,regional::openingObjects);
                assertThrows(IllegalArgumentException.class,
                        () -> CdObjectMappings.fromOpeningAssets(regional,usa.objects()));
            }
        }
    }

    /** Literal N Spikes/Data/Mappings.asm:1-23 plus Main.asm:89 EVEN, not generated from a visual. */
    private static final byte[] SPIKES_SOURCE = HexFormat.of().parseHex(
            "000204f0050000f000050004f0f005000000000500040000");
    private static final int SPIKES_MAP = 0x20C1F0;
    private static final List<CdObjectDrawQueue.RawPiece> SPIKES_PIECES = List.of(
            new CdObjectDrawQueue.RawPiece(-16,5,0,-16), new CdObjectDrawQueue.RawPiece(0,5,4,-16),
            new CdObjectDrawQueue.RawPiece(-16,5,0,0), new CdObjectDrawQueue.RawPiece(0,5,4,0));

    @Test void declaredSpikesRetainsFull24DirectoryPadAndOnlyTheRegisteredCountedPointer() throws Exception {
        assertEquals(24,SPIKES_SOURCE.length); assertEquals(0,SPIKES_SOURCE[23]);
        assertEquals("db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607",sha256(SPIKES_SOURCE));
        var mappings=declared(SPIKES_MAP,SPIKES_SOURCE,0,1);
        assertEquals(new CdObjectDrawQueue.FrameMapping(4,SPIKES_PIECES),mappings.resolve(SPIKES_MAP,false,0));
        for(int frame=1;frame<=255;frame++) {int outside=frame;assertThrows(UnsupportedOperationException.class,()->mappings.resolve(SPIKES_MAP,false,outside));}
        for(int pointer:new int[]{SPIKES_MAP+1,SPIKES_MAP+2,SPIKES_MAP+3,SPIKES_MAP+23,SPIKES_MAP|0xFF000000})
            assertThrows(UnsupportedOperationException.class,()->mappings.resolve(pointer,false,0));
        assertThrows(UnsupportedOperationException.class,()->mappings.resolve(SPIKES_MAP,true,0));
        var visual=CdObjectVisualAssets.decodeStatic(SPIKES_SOURCE,1,8);
        assertEquals(0,visual.mappingTableOffset());assertEquals(1,visual.mappingFrames().size());
        assertEquals(2,visual.mappingFrames().getFirst().relativeOffset());assertEquals(21,visual.mappingFrames().getFirst().bytes().length());
        assertTrue(visual.animations().isEmpty());assertEquals(0,visual.animationTable().length());
        assertArrayEquals(SPIKES_SOURCE,visual.mappings().copy());
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(SPIKES_SOURCE,1,7));
        var patterns=new TreeSet<Integer>();int cells=0;
        for(var p:SPIKES_PIECES)for(int t=0;t<4;t++) {patterns.add(p.rawTileWord()+t);cells++;}
        assertEquals(16,cells);assertEquals(new TreeSet<>(List.of(0,1,2,3,4,5,6,7)),patterns);
    }

    @Test void spikesQueueUsesEveryRawAttributeAndAllFourNativeMirrorsWithoutIdDispatch() {
        // Mutated declared attributes are format inputs, never a claim about authenticated USA pieces.
        byte[] raw=SPIKES_SOURCE.clone();int[] attributes={0xE800,0x1004,0x9800,0x6004};
        for(int p=0;p<4;p++)putWord(raw,5+p*5,attributes[p]);
        var mappings=declared(SPIKES_MAP,raw,0,1);
        int[][] xs={{0x170,0x170,0x180,0x180},{0x180,0x180,0x170,0x170}};
        int[][] ys={{0xF0,0x100,0xF0,0x100},{0x100,0xF0,0x100,0xF0}};
        for(int base:new int[]{0x0320,0xA320,0x07FE,0xFFFE})for(int flip=0;flip<4;flip++) {
            var sat=drawPataBataMapping(mappings,SPIKES_MAP,0,base,4|flip);byte[] bytes=sat.bytes();
            assertEquals(4,sat.spriteCount());
            for(int p=0;p<4;p++) {
                assertEquals(ys[(flip>>>1)&1][p],word(bytes,p*8));assertEquals(xs[flip&1][p],word(bytes,p*8+6));
                assertEquals(5,bytes[p*8+2]&255);assertEquals(p+1,bytes[p*8+3]&255);
                assertEquals(((base+attributes[p])&0xFFFF)^((flip&3)<<11),word(bytes,p*8+4));
            }
            assertArrayEquals(new byte[4],Arrays.copyOfRange(bytes,32,36));
        }
    }

    @Test void actualUsaSpikesJoinsTheRetainedFullSourceAndPreservesEveryPriorRootAndMonitorRawPointer() throws Exception {
        var usa=usaMedia();var objects=usa.objects();var view=objects.view(CdR11aObjectAssets.Table.SPIKES_MAPPINGS);
        assertEquals(0xC1F0,view.range().fileOffset());assertEquals(24,view.range().length());assertEquals(SPIKES_MAP,view.range().nativeAddress());
        assertArrayEquals(SPIKES_SOURCE,view.bytes().copy());var visual=objects.spikes();assertSame(visual,objects.spikes());
        assertArrayEquals(SPIKES_SOURCE,visual.mappings().copy());assertTrue(visual.animations().isEmpty());assertEquals(0,visual.animationTable().length());
        assertEquals(0x0320,objects.tileWord(0xA,0));
        var mappings=CdObjectMappings.fromOpeningAssets(usa.assets(),objects);
        assertEquals(SPIKES_PIECES,mappings.resolve(SPIKES_MAP,false,0).pieces());
        assertThrows(UnsupportedOperationException.class,()->mappings.resolve(SPIKES_MAP,false,1));
        assertThrows(UnsupportedOperationException.class,()->mappings.resolve(SPIKES_MAP,true,0));
        assertThrows(UnsupportedOperationException.class,()->mappings.resolve(SPIKES_MAP+2,false,0));
        assertMonitorPriorRoots(usa,mappings);assertMonitorTimeFrames(mappings);assertHorizontalTunnelDoorFrames(mappings);assertPataBataFrames(mappings);
        for(int frame=0;frame<3;frame++)assertEquals(objects.springDiagonal().mappingFrames().get(frame).pieces().stream()
                .map(p->new CdObjectDrawQueue.RawPiece(p.y(),p.sizeByte(),p.rawTileWord(),p.x())).toList(),mappings.resolve(0x209BF0,false,frame).pieces());
        assertEquals(List.of(new CdObjectDrawQueue.RawPiece(-8,2,0,-20),new CdObjectDrawQueue.RawPiece(-16,15,3,-12)),
                mappings.resolve(0x209654,false,0).pieces());
        for(int item=0;item<8;item++)assertEquals(new CdObjectDrawQueue.StaticMapping(monitorPieces(item).getFirst()),
                mappings.resolve(MONITOR_ITEM_POINTERS[item],true,255));
        byte[] detached=view.bytes().copy();Arrays.fill(detached,(byte)0xA5);
        assertEquals(SPIKES_PIECES,mappings.resolve(SPIKES_MAP,false,0).pieces());assertArrayEquals(SPIKES_SOURCE,visual.mappings().copy());
    }

    /** N Anton/Data/Mappings.asm:1-109; literal full include, never a production asset fallback. */
    private static final int ANTON_MAP = 0x20B3AA;
    private static final byte[] ANTON_SOURCE = HexFormat.of().parseHex(
            "00080022003c005605ed050000f4fd080004f405040007fc04050009f8fc0000110e"
            + "05ec050000f4fc080004f404040007fc0405000df8fb00001112"
            + "05ed050012f4fd080004f405040007fc04050009f8fc0000110e"
            + "05ec050012f4fc080004f404040007fc0405000df8fb00001112");
    private static final byte[] ANTON_SCRIPTS = HexFormat.of().parseHex("00040008030001ff070203ff");
    private static final int[][][] ANTON_PIECES = {
            {{-19,5,0,-12},{-3,8,4,-12},{5,4,7,-4},{4,5,9,-8},{-4,0,17,14}},
            {{-20,5,0,-12},{-4,8,4,-12},{4,4,7,-4},{4,5,13,-8},{-5,0,17,18}},
            {{-19,5,18,-12},{-3,8,4,-12},{5,4,7,-4},{4,5,9,-8},{-4,0,17,14}},
            {{-20,5,18,-12},{-4,8,4,-12},{4,4,7,-4},{4,5,13,-8},{-5,0,17,18}}
    };

    @Test void declaredAntonRetainsAll112LiteralBytesFourFivePieceBodiesAndBothRealScripts() throws Exception {
        assertEquals(112,ANTON_SOURCE.length);
        assertEquals("898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808",sha256(ANTON_SOURCE));
        assertEquals("623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d",sha256(ANTON_SCRIPTS));
        var mappings=declared(ANTON_MAP,ANTON_SOURCE,0,4);
        var visual=CdObjectVisualAssets.decode(ANTON_SOURCE,4,ANTON_SCRIPTS,2,22);
        assertAntonFrames(mappings);
        assertArrayEquals(ANTON_SOURCE,visual.mappings().copy());
        assertArrayEquals(ANTON_SCRIPTS,visual.animationTable().copy());
        assertEquals(0,visual.mappingTableOffset());assertEquals(4,visual.mappingFrames().size());
        var referenced=new TreeSet<Integer>();
        for(int frame=0;frame<4;frame++) {
            int offset=8+frame*26;assertEquals(offset,word(ANTON_SOURCE,frame*2));
            var body=visual.mappingFrames().get(frame);
            assertEquals(offset,body.relativeOffset());assertEquals(26,body.bytes().length());assertEquals(5,body.pieces().size());
            assertArrayEquals(Arrays.copyOfRange(ANTON_SOURCE,offset,offset+26),body.bytes().copy());
            for(var piece:body.pieces())for(int t=0;t<piece.tileCount();t++)referenced.add(piece.tileOffset()+t);
            assertEquals(frame<2?0:18,body.pieces().getFirst().tileOffset());
            assertEquals(4,body.pieces().getFirst().tileCount());
        }
        assertEquals(new TreeSet<>(java.util.stream.IntStream.range(0,22).boxed().toList()),referenced);
        assertEquals(2,visual.animations().size());
        for(int i=0;i<2;i++) {
            var script=visual.animations().get(i);
            assertEquals(4+i*4,script.relativeOffset());assertEquals(i==0?3:7,script.delay());
            assertEquals(i==0?List.of(0,1):List.of(2,3),script.frameBytes());
            assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,script.terminal());
            assertArrayEquals(Arrays.copyOfRange(ANTON_SCRIPTS,4+i*4,8+i*4),script.bytes().copy());
        }
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(ANTON_SOURCE,4,ANTON_SCRIPTS,2,21));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(Arrays.copyOf(ANTON_SOURCE,111),4,ANTON_SCRIPTS,2,22));
        byte[] detached=visual.mappings().copy();Arrays.fill(detached,(byte)0xA5);
        assertArrayEquals(ANTON_SOURCE,visual.mappings().copy());assertAntonFrames(mappings);
        assertThrows(UnsupportedOperationException.class,()->visual.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class,()->visual.animations().clear());
    }

    @Test void actualUsaAntonRegistersOnlyItsAuthenticatedPointerAndPreservesEveryOldRegistration() throws Exception {
        var usa=usaMedia();var o=usa.objects();
        var view=o.view(CdR11aObjectAssets.Table.ANTON_MAPPINGS);
        var scripts=o.view(CdR11aObjectAssets.Table.ANTON_ANIMATIONS);
        assertEquals(0xB3AA,view.range().fileOffset());assertEquals(112,view.range().length());assertEquals(ANTON_MAP,view.range().nativeAddress());
        assertEquals(0xB39E,scripts.range().fileOffset());assertEquals(12,scripts.range().length());assertEquals(0x20B39E,scripts.range().nativeAddress());
        assertArrayEquals(ANTON_SOURCE,view.bytes().copy());assertArrayEquals(ANTON_SCRIPTS,scripts.bytes().copy());
        assertSame(o.anton(),o.anton());assertArrayEquals(ANTON_SOURCE,o.anton().mappings().copy());
        assertArrayEquals(ANTON_SCRIPTS,o.anton().animationTable().copy());
        assertEquals(0x2409,o.tileWord(2,0));assertEquals(0x2370,o.tileWord(2,1));
        var mappings=CdObjectMappings.fromOpeningAssets(usa.assets(),o);assertAntonFrames(mappings);
        // These unchanged helpers cover Sonic, opening/frontier/contact, Game/Time Over,
        // FloatBlock, both old Spring roots, Powerup and both Door roots, Monitor and both Pata roots.
        assertMonitorPriorRoots(usa,mappings);assertMonitorTimeFrames(mappings);
        assertHorizontalTunnelDoorFrames(mappings);assertPataBataFrames(mappings);
        var oldVisuals=List.of(o.springDiagonal(),o.boulder(),o.spikes(),o.checkpoint());
        int[] oldRoots={0x209BF0,0x209654,0x20C1F0,0x23F120};
        for(int i=0;i<oldRoots.length;i++)for(int frame=0;frame<oldVisuals.get(i).mappingFrames().size();frame++) {
            var expected=oldVisuals.get(i).mappingFrames().get(frame).pieces().stream()
                    .map(p->new CdObjectDrawQueue.RawPiece(p.y(),p.sizeByte(),p.rawTileWord(),p.x())).toList();
            assertEquals(expected,mappings.resolve(oldRoots[i],false,frame).pieces());
        }
        for(int item=0;item<8;item++)assertEquals(new CdObjectDrawQueue.StaticMapping(monitorPieces(item).getFirst()),
                mappings.resolve(MONITOR_ITEM_POINTERS[item],true,255));
        for(int frame=0;frame<4;frame++)for(int flips=0;flips<4;flips++)for(int base:new int[]{0x2409,0x2370,0xA409,0xFFF0}) {
            // Generic queue fixture: no actor-ID dispatch or body execution.
            var sat=drawPataBataMapping(mappings,ANTON_MAP,frame,base,4|flips);byte[] raw=sat.bytes();
            assertEquals(5,sat.spriteCount());
            for(int p=0;p<5;p++) {
                int[] piece=ANTON_PIECES[frame][p];int width=((piece[1]>>>2)+1)*8,height=((piece[1]&3)+1)*8;
                assertEquals(256+((flips&2)==0?piece[0]:-piece[0]-height),word(raw,p*8));
                assertEquals(384+((flips&1)==0?piece[3]:-piece[3]-width),word(raw,p*8+6));
                assertEquals(((base+piece[2])&0xFFFF)^((flips&3)<<11),word(raw,p*8+4));
                assertEquals(piece[1],raw[p*8+2]&255);assertEquals(p+1,raw[p*8+3]&255);
            }
        }
    }

    private static void assertAntonFrames(CdObjectMappings mappings) {
        for(int frame=0;frame<4;frame++) {
            var expected=new java.util.ArrayList<CdObjectDrawQueue.RawPiece>();
            for(int[] p:ANTON_PIECES[frame])expected.add(new CdObjectDrawQueue.RawPiece(p[0],p[1],p[2],p[3]));
            assertEquals(new CdObjectDrawQueue.FrameMapping(5,expected),mappings.resolve(ANTON_MAP,false,frame));
        }
        for(int frame=4;frame<=255;frame++) {int outside=frame;assertThrows(UnsupportedOperationException.class,()->mappings.resolve(ANTON_MAP,false,outside));}
        for(int bad:new int[]{-1,256})assertThrows(IllegalArgumentException.class,()->mappings.resolve(ANTON_MAP,false,bad));
        for(int pointer:new int[]{ANTON_MAP+1,ANTON_MAP+8,ANTON_MAP+0x56,ANTON_MAP+112,0x20B39E,ANTON_MAP|0xFF000000,ANTON_MAP|0x80000000})
            assertThrows(UnsupportedOperationException.class,()->mappings.resolve(pointer,false,0));
        assertThrows(UnsupportedOperationException.class,()->mappings.resolve(ANTON_MAP,true,0));
    }

    // BEGIN COLLAPSE PLATFORM PICTURE R1
    private static final int[] CP_ROOT={0x20C520,0x20C5EC,0x20C5AE,0x20C7AC};
    private static final int[] CP_FRAMES={1,6,7,6};
    private static final int[][] CP_DIRECTORY={{2},{12,12,103,204,305,356},{14,20,26,32,38,44,50},{12,28,54,80,106,132}};
    private static final int[][] CP_COUNTS={{23},{18,18,20,20,10,10},{1,1,1,1,1,1,2},{3,5,5,5,5,5}};
    private static final byte[][] CP_RAW={
            HexFormat.of().parseHex("000217d005080dd8d005080de8d005080df8d005080d08d005080d18e0050811d8e0050811e8e0050811f8e005081108e005081118f0050019d8f0050815e8f0050815f8f005081508f00508151800050825d80005001de800050021f800050021080005081d1810050019f810050019081005001918"),
            HexFormat.of().parseHex("000c000c006700cc0131016412e005000dd0e005000de0e005000df0e005000d00e005000d10e005000d20f0050011d0f0050011e0f0050011f0f005001100f005001110f00500112000050015d000050015e000050015f000050015000005001510000500152014d005000de0d005000df0d005000d00d005000d10e0050011e0e0050011f0e005001100e005001110f0050015e0f0050015f0f005001500f00500151000050029e000050033f00005003300000500331010050031e01005002bf01005002b001005002b1014d005000de0d005000df0d005000d00d005000d10e0050011e0e0050011f0e005001100e005001110f0050015e0f0050015f0f005001500f00500151000050033e000050033f0000500330000050033101005002be01005002bf01005002b001005002b100ad005000df0d005000d00e0050011f0e005001100f0050015f0f0050015000005001df0000500210010050019f010050019000ad005000df0d005000d00e0050011f0e005001100f0050015f0f00500150000050021f00005081d0010050019f0100500190000"),
            HexFormat.of().parseHex("000e0014001a00200026002c003201f8050019f801f8050825f801f805001df801f8050021f801f805081df801f8050815f802e805080df8f8050811f800"),
            HexFormat.of().parseHex("000c001c00360050006a008403e005000df8f0050011f800050015f805d005000df8e0050011f8f0050015f800050029f810050031f805d005000df8e0050011f8f0050015f800050033f81005002bf805d005000df8e0050011f8f0050015f80005001df810050019f805d005000df8e0050011f8f0050015f80005081df810050019f805d005000df8e0050011f8f0050015f800050021f810050019f8")
    };

    @Test void declaredCollapseRetainsFourFullRootsRawPiecesAliasesPadsAndSparseFortyTileUnion() throws Exception {
        String[] hashes={"e11e6733195a4700631f68f369278f7335279b756d615bd64b1ead95e6dcca12","b045e60da3748ed69830e29883ed37a49b6a1285fdb4815d62b48ad871e36489","77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c","a39fbf7aa6cb97caa61a6ddcf52e0936979ecf3458c16f8e3aced9d75a005f5b"};
        int[] lengths={118,408,62,158};var union=new TreeSet<Integer>();
        for(int root=0;root<4;root++) {
            byte[] raw=CP_RAW[root];assertEquals(lengths[root],raw.length);assertEquals(hashes[root],sha256(raw));
            var visual=CdObjectVisualAssets.decodeStatic(raw,CP_FRAMES[root],57);
            var resolver=declared(CP_ROOT[root],raw,0,CP_FRAMES[root]);
            assertEquals(0,visual.mappingTableOffset());assertEquals(CP_FRAMES[root],visual.mappingFrames().size());
            assertTrue(visual.animations().isEmpty());assertEquals(0,visual.animationTable().length());
            for(int frame=0;frame<CP_FRAMES[root];frame++) {
                int offset=CP_DIRECTORY[root][frame];assertEquals(offset,word(raw,frame*2));
                var body=visual.mappingFrames().get(frame);assertEquals(offset,body.relativeOffset());
                assertEquals(CP_COUNTS[root][frame],body.pieces().size());
                assertArrayEquals(Arrays.copyOfRange(raw,offset,offset+1+5*CP_COUNTS[root][frame]),body.bytes().copy());
                var expected=new java.util.ArrayList<CdObjectDrawQueue.RawPiece>();
                for(int piece=0;piece<CP_COUNTS[root][frame];piece++) {
                    int at=offset+1+piece*5;assertEquals(5,raw[at+1]);
                    var p=new CdObjectDrawQueue.RawPiece(raw[at],5,word(raw,at+2),raw[at+4]);expected.add(p);
                    var v=body.pieces().get(piece);assertEquals(p,new CdObjectDrawQueue.RawPiece(v.y(),v.sizeByte(),v.rawTileWord(),v.x()));
                    for(int tile=0;tile<4;tile++){int id=(p.rawTileWord()&0x7FF)+tile;assertTrue(id<57);union.add(id);}
                }
                assertEquals(expected,resolver.resolve(CP_ROOT[root],false,frame).pieces());
            }
            byte[] detached=visual.mappings().copy();Arrays.fill(detached,(byte)0x7F);assertArrayEquals(raw,visual.mappings().copy());
            assertThrows(UnsupportedOperationException.class,()->visual.mappingFrames().clear());
        }
        var sparse=new TreeSet<Integer>();for(int tile=13;tile<=46;tile++)sparse.add(tile);for(int tile=49;tile<=54;tile++)sparse.add(tile);
        assertEquals(sparse,union);assertEquals(40,union.size());assertEquals(0,CP_RAW[1][407]);assertEquals(0,CP_RAW[2][61]);
        assertEquals(word(CP_RAW[1],0),word(CP_RAW[1],2));assertEquals(0x080D,word(CP_RAW[0],5));
        assertEquals(-48,CP_RAW[0][3]);assertEquals(-40,CP_RAW[0][7]);
    }

    @Test void collapseRetainsExactPointerFullFrameAndFinite57PieceAdmission() {
        for(int root=0;root<4;root++) {
            int pointer=CP_ROOT[root],frames=CP_FRAMES[root];byte[] raw=CP_RAW[root];var resolver=declared(pointer,raw,0,frames);
            for(int frame=frames;frame<256;frame++){int absent=frame;assertThrows(UnsupportedOperationException.class,()->resolver.resolve(pointer,false,absent));}
            for(int absent:new int[]{pointer+1,pointer+CP_DIRECTORY[root][0],pointer+raw.length,pointer|0x80000000})
                assertThrows(UnsupportedOperationException.class,()->resolver.resolve(absent,false,0));
            assertThrows(UnsupportedOperationException.class,()->resolver.resolve(pointer,true,0));
            for(int frame:new int[]{-1,256})assertThrows(IllegalArgumentException.class,()->resolver.resolve(pointer,false,frame));
            assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(Arrays.copyOf(raw,raw.length-2),frames,57));
            byte[] escaped=raw.clone();int at=CP_DIRECTORY[root][0]+3;escaped[at]=0;escaped[at+1]=54;
            assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(escaped,frames,57));
            escaped[at+1]=53;assertNotNull(CdObjectVisualAssets.decodeStatic(escaped,frames,57));
        }
    }

    @Test void actualUsaCollapseRegistersFourOrdinaryRootsAndPreservesEveryInheritedRegistration() throws Exception {
        var usa=usaMedia();var o=usa.objects();var resolver=CdObjectMappings.fromOpeningAssets(usa.assets(),o);
        var tables=List.of(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_1,CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_2,
                CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_3,CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_4);
        var visuals=List.of(o.collapsePlatform1(),o.collapsePlatform2(),o.collapsePlatform3(),o.collapsePlatform4());
        for(int root=0;root<4;root++) {
            var view=o.view(tables.get(root));var visual=visuals.get(root);
            assertEquals(CP_ROOT[root],view.range().nativeAddress());assertArrayEquals(CP_RAW[root],view.bytes().copy());
            assertArrayEquals(CP_RAW[root],visual.mappings().copy());assertEquals(CP_FRAMES[root],visual.mappingFrames().size());
            assertTrue(visual.animations().isEmpty());assertEquals(0,visual.animationTable().length());
            var literal=declared(CP_ROOT[root],CP_RAW[root],0,CP_FRAMES[root]);
            for(int frame=0;frame<CP_FRAMES[root];frame++)assertEquals(literal.resolve(CP_ROOT[root],false,frame),resolver.resolve(CP_ROOT[root],false,frame));
        }
        assertMonitorPriorRoots(usa,resolver);assertMonitorTimeFrames(resolver);assertHorizontalTunnelDoorFrames(resolver);
        assertPataBataFrames(resolver);assertAntonFrames(resolver);
        var old=List.of(o.springDiagonal(),o.boulder(),o.spikes(),o.checkpoint());int[] pointers={0x209BF0,0x209654,0x20C1F0,0x23F120};
        for(int i=0;i<old.size();i++)for(int frame=0;frame<old.get(i).mappingFrames().size();frame++)
            assertEquals(old.get(i).mappingFrames().get(frame).pieces().stream().map(p->new CdObjectDrawQueue.RawPiece(p.y(),p.sizeByte(),p.rawTileWord(),p.x())).toList(),resolver.resolve(pointers[i],false,frame).pieces());
        for(int item=0;item<8;item++)assertEquals(new CdObjectDrawQueue.StaticMapping(monitorPieces(item).getFirst()),resolver.resolve(MONITOR_ITEM_POINTERS[item],true,255));
        for(var table:List.of(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_SIZES_1,CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_SIZES_2)) {
            int pointer=o.view(table).range().nativeAddress();assertThrows(UnsupportedOperationException.class,()->resolver.resolve(pointer,false,0));
        }
    }
    // END COLLAPSE PLATFORM PICTURE R1
    @Test void actualUsaPlatformRegistersOneCompleteRootWithLiteralPiecesAndSharesTheExistingFiftySevenTiles() throws Exception {
        var usa=usaMedia();var o=usa.objects();var resolver=CdObjectMappings.fromOpeningAssets(usa.assets(),o);
        var raw=o.view(CdR11aObjectAssets.Table.PLATFORM_MAPPINGS).bytes();assertEquals(132,raw.length());
        assertEquals(0x20CCC8,o.view(CdR11aObjectAssets.Table.PLATFORM_MAPPINGS).range().nativeAddress());
        assertEquals("a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7",sha256(raw.copy()));
        assertEquals(List.of(6,28,70),List.of(raw.unsignedWord(0),raw.unsignedWord(2),raw.unsignedWord(4)));
        assertEquals(0,raw.unsignedByte(27));assertEquals(0,raw.unsignedByte(69));assertEquals(0,raw.unsignedByte(131));
        int[][][] literal={
            {{-16,0x001,-16},{-16,0x801,0},{0,0x005,-16},{0,0x805,0}},
            {{-16,1,-32},{-16,1,-16},{-16,1,0},{-16,1,16},{0,5,-32},{0,9,-16},{0,9,0},{0,0x805,16}},
            {{-16,1,-48},{-16,1,-32},{-16,1,-16},{-16,1,0},{-16,1,16},{-16,1,32},
             {0,5,-48},{0,9,-32},{0,9,-16},{0,9,0},{0,9,16},{0,0x805,32}}};
        var union=new TreeSet<Integer>();
        for(int frame=0;frame<3;frame++) {
            var expected=new java.util.ArrayList<CdObjectDrawQueue.RawPiece>();
            for(var p:literal[frame]){expected.add(new CdObjectDrawQueue.RawPiece(p[0],5,p[1],p[2]));
                for(int tile=0;tile<4;tile++)union.add((p[1]&0x7FF)+tile);}
            assertEquals(expected,resolver.resolve(0x20CCC8,false,frame).pieces());
            for(int flips=0;flips<4;flips++)for(int base:new int[]{0x44BE,0xC4BE}) {
                var sat=drawPataBataMapping(resolver,0x20CCC8,frame,base,4|flips);var bytes=sat.bytes();
                assertEquals(literal[frame].length,sat.spriteCount());
                for(int p=0;p<literal[frame].length;p++) {
                    int[] piece=literal[frame][p];assertEquals(5,bytes[p*8+2]&255);assertEquals(p+1,bytes[p*8+3]&255);
                    assertEquals(256+((flips&2)==0?piece[0]:-piece[0]-16),word(bytes,p*8));
                    assertEquals(384+((flips&1)==0?piece[2]:-piece[2]-16),word(bytes,p*8+6));
                    assertEquals(((base+piece[1])&0xFFFF)^(flips<<11),word(bytes,p*8+4));
                }
            }
        }
        assertEquals(new TreeSet<>(List.of(1,2,3,4,5,6,7,8,9,10,11,12)),union);
        for(int frame=3;frame<256;frame++){int absent=frame;assertThrows(UnsupportedOperationException.class,()->resolver.resolve(0x20CCC8,false,absent));}
        for(int pointer:new int[]{0x20CCC9,0x20CCCE,0x20CD4C,0x20C940,0x20C944,0xFF20CCC8})
            assertThrows(UnsupportedOperationException.class,()->resolver.resolve(pointer,false,0));
        assertThrows(UnsupportedOperationException.class,()->resolver.resolve(0x20CCC8,true,0));
        for(int root=0;root<4;root++)for(int frame=0;frame<CP_FRAMES[root];frame++)
            assertEquals(declared(CP_ROOT[root],CP_RAW[root],0,CP_FRAMES[root]).resolve(CP_ROOT[root],false,frame),resolver.resolve(CP_ROOT[root],false,frame));
        assertMonitorPriorRoots(usa,resolver);assertAntonFrames(resolver);
    }

}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
