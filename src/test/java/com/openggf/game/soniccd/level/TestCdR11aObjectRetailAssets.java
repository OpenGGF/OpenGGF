package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_RINGS;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST;
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
        CdR11aObjectAssets untouchedFrontier = null;
        try (var source = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            terrainOwner = CdR11aAssetLoader.load(source, variant, CdStageKey.parse("R11A"));
            if (variant == CdReleaseVariant.USA) {
                objects = terrainOwner.openingObjects();
                var original = source.mmd(CdNativeFile.R11A);
                // A separate owner is never asked for frontier data before media closure.
                untouchedFrontier = CdR11aObjectAssets.load(terrainOwner.profile(), terrainOwner.identity(),
                        original, terrainOwner.loadPlan());
                verifyFrontierNativeReferences(original, terrainOwner, objects);
                for (int offset : List.of(0x9C96, 0xA0F2, 0xA0FA, 0xB6CA, 0xB6D8, 0xB6E6, 0xB722,
                        0xCF48, 0x70C6, 0x35626, 0x3702A, 0x30000,
                        0xD1AE, 0xD1F0, 0xD22C, 0xD240, 0xD264, 0xE020, 0xDDC8, 0x1FDBC, 0xE046,
                        0x381C0, 0x3AC7A, 0x3AE02)) {
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
        assertNotNull(untouchedFrontier);
        var postCloseBank = untouchedFrontier.frontierArtBank();
        assertSame(postCloseBank, untouchedFrontier.frontierArtBank());
        assertEquals("a3a40dce6206cd71835f043eadee914a860724ec4361d1275a9535c7b2a1b164",
                CdR11aProfile.sha256(postCloseBank.binding(ART_3D_PLANT).bytes().copy()));
        assertEquals(393, postCloseBank.binding(ART_3D_PLANT).decode().readerInputRange().length());
        verifyUsa(terrainOwner, objects);
    }

    private static void verifyUsa(CdR11aAssets terrainOwner, CdR11aObjectAssets objects) throws Exception {
        assertSame(terrainOwner.identity(), objects.identity());
        Map<CdR11aObjectAssets.Table, String> hashes = new HashMap<>(Map.of(
                RING_DELTAS, "35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86",
                RING_ANIMATIONS, "4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a",
                RING_MAPPINGS, "cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a",
                MOSQUI_NORMAL_ANIMATIONS, "24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2",
                MOSQUI_DAMAGED_ANIMATIONS, "6718cb74ab9b1ce59a3a122b0dee40b38ece57b7fdacd1f4baf7b76bc38c1a6e",
                MOSQUI_NORMAL_MAPPINGS, "5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e",
                MOSQUI_DAMAGED_MAPPINGS, "23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313",
                OBJECT_TILE_IDS, "82086dba64cbd992cd73628e56bbfba4102783b6a6c63c9f10ada9844687525c",
                TOUCH_DIMENSIONS, "658a42cd58f3c61fc034da9e81c25656b9dc40d923ceed515e14b56672b1581d"));
        hashes.putAll(Map.of(
                FLYING_ANIMAL_ANIMATIONS, "5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3",
                GROUND_ANIMAL_ANIMATIONS, "b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4",
                FLYING_ANIMAL_MAPPINGS, "00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39",
                GROUND_ANIMAL_MAPPINGS, "029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2",
                ANIMAL_BASE_TILES, "74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec",
                PLANT_MAPPINGS, "669ff9b5467e3aa4711cc28d8433db3fbd8d54e63c63a2478884651c8299c920",
                PLANT_OFFSETS, "f51ae30277622886f2ae6d035f28c562a0246850cc8a4821cabddebb2ec12bba",
                RAMP_BOOSTER_MAPPINGS, "a05ddcca02858f2bff236a02279726ef8170995325365f7b63b45645f95dbf94",
                RAMP_BOOSTER_ANIMATIONS, "95f05b61076aa3b77cef9fbeea466b7a03f5d2e77f62346cbcfe7fe9630871fe"));
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
        var pool = new CdObjectPool();
        var ring = pool.state(32)
                .moveByte(CdObjectState.ByteField.ID, 0x10)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x80)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0x47)
                .moveByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveWord(CdObjectState.WordField.X, 0xF0)
                .moveWord(CdObjectState.WordField.Y, 0x720);
        pool.write(ring);
        var touch = CdPlayerObjectTouch.fromAssets(objects).scan(
                new CdPlayerObjectTouch.PlayerCut(0xF0, 0x720, 19, 0, 0), pool.capture());
        assertEquals(CdPlayerObjectTouch.Kind.ITEM, touch.kind());
        assertSame(ring, touch.selectedSlot().orElseThrow());
        assertEquals(4, touch.changedSlot().orElseThrow().routine());
        assertSame(ring, pool.state(32), "the authenticated table consumer stages, rather than commits, its item write");
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
        verifyFrontier(terrainOwner, objects);
        assertSame(bank, objects.initialArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), bank.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        for (var added : List.of(ART_ANIMALS, ART_3D_PLANT, ART_3D_RAMP_BOOST)) {
            assertThrows(UnsupportedOperationException.class, () -> bank.binding(added));
            assertThrows(UnsupportedOperationException.class, () -> terrainOwner.decoded(added));
        }
    }

    private static void verifyFrontier(CdR11aAssets terrainOwner, CdR11aObjectAssets objects) throws Exception {
        var plant = objects.plant();
        assertEquals(List.of(4, 26), plant.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(4, 2), plant.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(0, plant.animationTable().length(), "the static plant has no native script table");
        assertTrue(plant.animations().isEmpty());
        assertEquals(0x1B, lastUsedTile(plant));
        assertArrayEquals(objects.view(PLANT_MAPPINGS).bytes().copy(), plant.mappings().copy());
        assertEquals(List.of(0x40, 0x80, -0x40, -0x80), objects.plantOffsets().main());
        assertEquals(List.of(0, 0x60, -0x60), objects.plantOffsets().small());
        assertThrows(UnsupportedOperationException.class, () -> objects.plantOffsets().main().clear());
        assertThrows(UnsupportedOperationException.class, () -> objects.plantOffsets().small().clear());

        var flying = objects.flyingAnimal();
        var ground = objects.groundAnimal();
        assertEquals(List.of(6, 12, 18), flying.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(10, 16, 22, 23, 29), ground.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertTrue(flying.mappingFrames().get(2).pieces().isEmpty());
        assertTrue(ground.mappingFrames().get(2).pieces().isEmpty());
        assertEquals(7, lastUsedTile(flying));
        assertEquals(0x13, lastUsedTile(ground));
        assertEquals(List.of(19, 0), flying.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(2, 56), flying.animations().stream().map(a -> a.frameBytes().size()).toList());
        assertEquals(List.of(0), ground.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(56, ground.animations().getFirst().frameBytes().size());
        int[][] tiles = {{0x4F7, 0x388, 0x463}, {0x4F7, 0x38F, 0x461}, {0, 0, 0x3CF}};
        for (int act = 0; act < 3; act++) {
            for (int timeZone = 0; timeZone < 3; timeZone++) {
                assertEquals(tiles[act][timeZone], objects.animalBaseTile(act, timeZone));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> objects.animalBaseTile(2, 3));
        assertThrows(IllegalArgumentException.class, () -> objects.animalBaseTile(3, 0));

        var ramp = objects.rampBooster();
        assertEquals(List.of(20, 62, 108, 154, 200, 236, 268, 350, 426, 498), ramp.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(8, 9, 9, 9, 7, 6, 16, 15, 12, 12), ramp.mappingFrames().stream()
                .map(f -> f.pieces().size()).toList());
        // The native count stops before 11/21 real tail bytes in the final two frames.
        // The complete authenticated include is preserved, and those bytes are not extra pieces.
        assertEquals(61, ramp.mappingFrames().get(8).bytes().length());
        assertEquals(61, ramp.mappingFrames().get(9).bytes().length());
        assertEquals(580, ramp.mappings().length());
        assertArrayEquals(objects.view(RAMP_BOOSTER_MAPPINGS).bytes().copy(), ramp.mappings().copy());
        assertEquals(0x3E, lastUsedTile(ramp));
        assertTrue(ramp.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .anyMatch(p -> (p.rawTileWord() & 0x1800) == 0x1800));
        assertEquals(List.of(0, 2, 2), ramp.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(4, 7, 5, 6), ramp.animations().get(1).frameBytes());
        assertEquals(List.of(8, 0, 9, 0), ramp.animations().get(2).frameBytes());
        for (var visual : List.of(flying, ground, ramp)) {
            assertTrue(visual.animations().stream().allMatch(a -> a.terminal() == CdObjectVisualAssets.Terminal.LOOP_FF));
        }
        var bank = objects.frontierArtBank();
        assertEquals(List.of(ART_ANIMALS, ART_3D_PLANT, ART_3D_RAMP_BOOST), bank.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        verifyFrontierArt(terrainOwner, bank.binding(ART_ANIMALS), 1, 0x7100, 0x388, 446, 445, 445, 20,
                "ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1");
        verifyFrontierArt(terrainOwner, bank.binding(ART_3D_PLANT), 4, 0x8480, 0x4424, 392, 393, 393, 28,
                "a3a40dce6206cd71835f043eadee914a860724ec4361d1275a9535c7b2a1b164");
        verifyFrontierArt(terrainOwner, bank.binding(ART_3D_RAMP_BOOST), 5, 0x8820, 0x441, 678, 677, 677, 63,
                "e52c4daa8995ac4365b3674cabf8bc81fc52db73d729e756dac757d79942b7a7");
        for (var binding : bank.bindings()) {
            int first = binding.firstTileId();
            int last = first + binding.tileCount() - 1;
            assertArrayEquals(binding.bytes().slice(0, 32).copy(), bank.tile(first).copy());
            assertArrayEquals(binding.bytes().slice((binding.tileCount() - 1) * 32, 32).copy(), bank.tile(last).copy());
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(first - 1));
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(last + 1));
            assertThrows(UnsupportedOperationException.class, () -> objects.initialArtBank().tile(first));
            for (var previous : objects.initialArtBank().bindings()) {
                assertTrue(last < previous.firstTileId() || first >= previous.firstTileId() + previous.tileCount());
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> bank.binding(ART_RINGS));
        assertThrows(UnsupportedOperationException.class, () -> bank.bindings().clear());
        assertThrows(IllegalArgumentException.class, () -> bank.tile(-1));
        assertThrows(IllegalArgumentException.class, () -> bank.tile(0x800));
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(0x424).buffer().put((byte) 0));
    }

    private static int lastUsedTile(CdObjectVisualAssets visual) {
        return visual.mappingFrames().stream().flatMap(f -> f.pieces().stream())
                .mapToInt(p -> p.tileOffset() + p.tileCount() - 1).max().orElseThrow();
    }

    private static void verifyFrontierArt(CdR11aAssets owner, CdR11aObjectAssets.ArtBinding binding,
                                         int row, int vram, int baseWord, int sourceBytes, int readerBytes,
                                         int consumedBytes, int tiles, String decodedHash) {
        assertEquals(2, binding.plcId());
        assertEquals(owner.loadPlan().plcs().get(2).entries().get(row), binding.plc());
        assertEquals(binding.source(), binding.plc().source());
        assertEquals(vram, binding.plc().vramAddress());
        assertEquals(baseWord, binding.baseWord());
        assertEquals(baseWord & 0x7FF, binding.firstTileId());
        assertEquals(owner.profile().range(binding.source()), binding.decode().sourceBlobRange());
        assertEquals(binding.plc().sourceRange(), binding.decode().sourceBlobRange());
        assertEquals(sourceBytes, binding.decode().sourceBlobRange().length());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                binding.decode().sourceBlobRange().fileOffset(), readerBytes), binding.decode().readerInputRange());
        assertEquals(consumedBytes, binding.decode().consumedInputBytes());
        assertEquals(tiles, binding.tileCount());
        assertEquals(tiles * 32, binding.bytes().length());
        assertEquals(decodedHash, CdR11aProfile.sha256(binding.bytes().copy()));
    }

    private static void verifyFrontierNativeReferences(CdMmdImage image, CdR11aAssets owner,
                                                       CdR11aObjectAssets objects) throws Exception {
        for (var reference : List.of(new int[]{0xCFCA, 0x20D22C}, new int[]{0xD0DA, 0x20D240},
                new int[]{0xDCE6, 0x20E020}, new int[]{0xDEF6, 0x21FDBC})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 8));
            assertEquals((short) 0x217C, instruction.getShort());
            assertEquals(reference[1], instruction.getInt());
            assertEquals(4, instruction.getShort()); // oMap
        }
        for (var reference : List.of(new int[]{0xD03C, 0xD1AE}, new int[]{0xD084, 0xD1AE},
                new int[]{0xD16C, 0xD1F0}, new int[]{0xD190, 0xD264})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 4));
            assertEquals((short) 0x43FA, instruction.getShort());
            assertEquals(reference[1], reference[0] + 2 + instruction.getShort());
        }
        for (var reference : List.of(new int[]{0xDD52, 0xDDC8}, new int[]{0xDDB8, 0xDDD0})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 4));
            assertEquals((short) 0x323B, instruction.getShort());
            assertEquals(0x20, instruction.get()); // PC + signed byte + D2.w
            assertEquals(reference[1], reference[0] + 2 + instruction.get());
        }
        for (var reference : List.of(new int[]{0xDCEE, 0x4424}, new int[]{0xDEFE, 0x441})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 6));
            assertEquals((short) 0x317C, instruction.getShort());
            assertEquals(reference[1], Short.toUnsignedInt(instruction.getShort()));
            assertEquals(2, instruction.getShort()); // oTile
        }
        var animation = ByteBuffer.wrap(image.readFile(0xDF40, 6));
        assertEquals((short) 0x43F9, animation.getShort());
        assertEquals(RAMP_BOOSTER_ANIMATIONS.range().nativeAddress(), animation.getInt());
        int plcBody = 0xFBC6 + ByteBuffer.wrap(image.readFile(0xFBC6 + 4, 2)).getShort();
        assertEquals(0xFC50, plcBody);
        assertEquals(6, ByteBuffer.wrap(image.readFile(plcBody, 2)).getShort());
        var sources = List.of(ART_ANIMALS, ART_3D_PLANT, ART_3D_RAMP_BOOST);
        int[] rows = {1, 4, 5};
        int[] vrams = {0x7100, 0x8480, 0x8820};
        int[] headers = {0x0014, 0x801C, 0x803F};
        int[] consumed = {445, 393, 677};
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            var range = owner.profile().range(source);
            var row = ByteBuffer.wrap(image.readFile(plcBody + 2 + rows[i] * 6, 6));
            assertEquals(range.nativeAddress(), row.getInt());
            assertEquals(vrams[i], Short.toUnsignedInt(row.getShort()));
            assertEquals(headers[i], Short.toUnsignedInt(ByteBuffer.wrap(image.readFile(range.fileOffset(), 2)).getShort()));
            int outputBytes = (headers[i] & 0x7FFF) * 32;
            int truncatedLength = consumed[i] - 1;
            assertThrows(IOException.class, () -> CdNemesisAsset.decode(
                    image.readFile(range.fileOffset(), truncatedLength), outputBytes, outputBytes));
            var exact = CdNemesisAsset.decode(image.readFile(range.fileOffset(), consumed[i]), outputBytes, outputBytes);
            var extended = CdNemesisAsset.decode(image.readFile(range.fileOffset(), consumed[i] + 1), outputBytes, outputBytes);
            assertEquals(consumed[i], exact.consumedInputBytes());
            assertEquals(consumed[i], extended.consumedInputBytes());
            assertArrayEquals(exact.bytes().copy(), extended.bytes().copy());
            assertArrayEquals(exact.bytes().copy(), objects.frontierArtBank().binding(source).bytes().copy());
        }
        assertEquals(0x80, Byte.toUnsignedInt(image.readFile(0x3AE02, 1)[0]),
                "plant read-ahead is the real first byte of the adjacent ramp blob, never synthetic EOF padding");
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
