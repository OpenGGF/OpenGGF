package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed declared source views are format inputs, never authenticated asset owners. */
class TestCdObjectMappings {
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

    private static CdObjectMappings declared(int start, byte[] bytes, int root, int frames) {
        return CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(
                start, CdAssetBytes.copyOf(bytes), root, frames)), List.of());
    }
    private static void putWord(byte[] bytes, int at, int word) {
        bytes[at] = (byte) (word >>> 8); bytes[at + 1] = (byte) word;
    }
}
