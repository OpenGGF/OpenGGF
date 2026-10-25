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
        for (int pointer : new int[]{0x209BF0, 0x209BA2, 0x209BAC, 0xFF209BA6}) {
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
}
