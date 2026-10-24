package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_RINGS;
import static com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production media ownership; no source checkout, research JSON or extracted payload fixture. */
class TestCdR11aObjectRetailAssets {
    @ParameterizedTest(name = "{0} terrain and optional USA opening-object assets")
    @EnumSource(CdReleaseVariant.class)
    void actualOpenedMediaAuthenticatesBeforeDecodeAndKeepsThreeRegionalTerrainLoads(CdReleaseVariant variant)
            throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media checks");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve(directory))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured regional media must contain exactly one CUE");
            cue = cues.getFirst();
        }
        CdR11aAssets terrainOwner;
        CdR11aObjectAssets objects = null;
        try (var source = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            terrainOwner = CdR11aAssetLoader.load(source, variant, CdStageKey.parse("R11A"));
            if (variant == CdReleaseVariant.USA) {
                objects = terrainOwner.openingObjects();
                var original = source.mmd(CdNativeFile.R11A);
                for (int offset : List.of(0x9C96, 0xA0F2, 0xA0FA, 0xB6CA, 0xB6D8, 0xB6E6, 0xB722,
                        0xCF48, 0x70C6, 0x35626, 0x3702A, 0x30000)) {
                    byte[] altered = original.readFile(0, original.fileBytes());
                    altered[offset] ^= 1;
                    // A plausible/correct caller record cannot authenticate even an unrelated changed byte.
                    assertThrows(IOException.class, () -> CdR11aObjectAssets.load(terrainOwner.profile(),
                            terrainOwner.identity(), CdMmdImage.parse(altered), terrainOwner.loadPlan()));
                }
            } else {
                assertThrows(UnsupportedOperationException.class, terrainOwner::openingObjects);
                var usa = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
                var assertedUsa = new CdR11aAssets.Identity(CdReleaseVariant.USA, usa.stage(), usa.file(), usa.domain(),
                        usa.expectedMmdSha256(), terrainOwner.identity().observedWholeDiscSha256());
                assertThrows(IOException.class, () -> CdR11aObjectAssets.load(usa, assertedUsa,
                        source.mmd(CdNativeFile.R11A), terrainOwner.loadPlan()));
            }
        }
        // The immutable owners remain useful after closing the source, in every region.
        assertEquals(variant, terrainOwner.identity().variant());
        assertEquals(804, terrainOwner.terrain().blockCount());
        assertEquals(800, terrainOwner.terrain().patternCount());
        assertEquals(497, terrainOwner.placements().records().size());
        assertThrows(UnsupportedOperationException.class, () -> terrainOwner.decoded(ART_RINGS));
        if (variant != CdReleaseVariant.USA) { return; }
        assertNotNull(objects);
        verifyUsa(terrainOwner, objects);
    }

    private static void verifyUsa(CdR11aAssets terrainOwner, CdR11aObjectAssets objects) throws Exception {
        assertSame(terrainOwner.identity(), objects.identity());
        Map<CdR11aObjectAssets.Table, String> hashes = Map.of(
                RING_DELTAS, "35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86",
                RING_ANIMATIONS, "4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a",
                RING_MAPPINGS, "cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a",
                MOSQUI_NORMAL_ANIMATIONS, "24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2",
                MOSQUI_DAMAGED_ANIMATIONS, "6718cb74ab9b1ce59a3a122b0dee40b38ece57b7fdacd1f4baf7b76bc38c1a6e",
                MOSQUI_NORMAL_MAPPINGS, "5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e",
                MOSQUI_DAMAGED_MAPPINGS, "23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313",
                OBJECT_TILE_IDS, "82086dba64cbd992cd73628e56bbfba4102783b6a6c63c9f10ada9844687525c",
                TOUCH_DIMENSIONS, "658a42cd58f3c61fc034da9e81c25656b9dc40d923ceed515e14b56672b1581d");
        for (var table : CdR11aObjectAssets.Table.values()) {
            var view = objects.view(table);
            assertEquals(table, view.table());
            assertEquals(table.range(), view.range());
            assertEquals(table.range().length(), view.bytes().length());
            assertEquals(hashes.get(table), CdR11aProfile.sha256(view.bytes().copy()));
        }
        assertEquals(new CdR11aObjectAssets.Delta(-0x20, 0x20), objects.ringDelta(11));
        assertEquals(new CdR11aObjectAssets.TouchSize(6, 6), objects.touchSize(0x47));
        assertEquals(new CdR11aObjectAssets.TouchSize(12, 12), objects.touchSize(0x2B));
        assertThrows(IndexOutOfBoundsException.class, () -> objects.ringDelta(16));
        assertThrows(IllegalArgumentException.class, () -> objects.touchSize(0));
        assertThrows(IllegalArgumentException.class, () -> objects.touchSize(0x40));
        assertThrows(IllegalArgumentException.class, () -> objects.touchSize(256));
        assertEquals(0x23A0, objects.tileWord(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(0, 1));
        assertEquals(9, objects.rings().mappingFrames().size());
        assertEquals(List.of(18, 24, 30, 36, 42, 48, 54, 60, 66), objects.rings().mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertTrue(objects.rings().mappingFrames().getLast().pieces().isEmpty());
        assertEquals(List.of(4, 5, 6, 7), objects.rings().animations().getFirst().frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, objects.rings().animations().getFirst().terminal());
        for (var mosqui : List.of(objects.normalMosqui(), objects.damagedMosqui())) {
            assertEquals(List.of(10, 26, 37, 48, 54), mosqui.mappingFrames().stream()
                    .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
            assertEquals(List.of(0, 1), mosqui.animations().getFirst().frameBytes());
            assertEquals(List.of(2, 3, 4), mosqui.animations().getLast().frameBytes());
            assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, mosqui.animations().getFirst().terminal());
            assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, mosqui.animations().getLast().terminal());
        }
        assertEquals(List.of(1, 6), objects.normalMosqui().animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(4, 14), objects.damagedMosqui().animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(2, objects.damagedMosqui().mappingFrames().getLast().pieces().size());
        var bank = objects.initialArtBank();
        assertEquals(2, bank.bindings().size());
        verifyArt(bank.binding(ART_RINGS), 1, 0xF5C0, 0xA7AE, 244, 14,
                "c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb");
        verifyArt(bank.binding(ART_MOSQUI), 2, 0x7400, 0x23A0, 846, 70,
                "230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd");
        assertEquals(2, terrainOwner.loadPlan().sections().getFirst().initialPlcId());
        assertArrayEquals(bank.binding(ART_RINGS).bytes().slice(0, 32).copy(), bank.tile(0x7AE).copy());
        assertArrayEquals(bank.binding(ART_MOSQUI).bytes().slice(69 * 32, 32).copy(), bank.tile(0x3E5).copy());
        assertThrows(UnsupportedOperationException.class, () -> bank.tile(0x3E6));
        assertThrows(UnsupportedOperationException.class, () -> bank.tile(0x7AD));
        assertThrows(UnsupportedOperationException.class, () -> bank.bindings().clear());
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(0x7AE).buffer().put((byte) 0));
        var ringView = objects.view(RING_MAPPINGS).bytes();
        byte[] altered = ringView.copy();
        altered[0] = 99;
        assertEquals(0, ringView.unsignedByte(0));
    }

    private static void verifyArt(CdR11aObjectAssets.ArtBinding binding, int plcId, int vram, int baseWord,
                                   int compressedBytes, int tiles, String hash) throws Exception {
        assertEquals(plcId, binding.plcId());
        assertEquals(vram, binding.plc().vramAddress());
        assertEquals(binding.source(), binding.plc().source());
        assertEquals(binding.decode().sourceBlobRange(), binding.plc().sourceRange());
        assertEquals(baseWord, binding.baseWord());
        assertEquals(vram / 32, baseWord & 0x7FF);
        assertEquals(tiles, binding.tileCount());
        assertEquals(compressedBytes, binding.decode().sourceBlobRange().length());
        assertEquals(binding.decode().sourceBlobRange(), binding.decode().readerInputRange());
        assertEquals(compressedBytes, binding.decode().consumedInputBytes());
        assertEquals(tiles * 32, binding.bytes().length());
        assertEquals(hash, CdR11aProfile.sha256(binding.bytes().copy()));
    }
}
