package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdGameOver;
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
import java.util.Arrays;
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
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_EXPLOSIONS;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_POINTS;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_FLOWER;
import static com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production media ownership; no source checkout, research JSON or extracted payload fixture. */
class TestCdR11aObjectRetailAssets {
    /**
     * Game Over/Data/Mappings.asm:1-58 hand-assembled from the pinned source (1ecc5f3a), independent of media:
     * MapSpr_GameOver's words are relative to +0 (8, $14), MapSpr_TimeOver's to +4 ($1C, $28).
     */
    private static final byte[] GAME_OVER_SOURCE_BYTES = sourceBytes(
            0x00, 0x08, 0x00, 0x14, 0x00, 0x1C, 0x00, 0x28,
            0x02, 0xF8, 0x0D, 0x00, 0x00, 0xB8, 0xF8, 0x0D, 0x00, 0x08, 0xD8, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x10, 0x08, 0xF8, 0x0D, 0x00, 0x18, 0x28, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x00, 0xBC, 0xF8, 0x09, 0x00, 0x08, 0xDC, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x0E, 0x04, 0xF8, 0x0D, 0x00, 0x16, 0x24, 0x00);

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
                // A separate owner is never asked for frontier or contact data before media closure.
                untouchedFrontier = CdR11aObjectAssets.load(terrainOwner.profile(), terrainOwner.identity(),
                        original, terrainOwner.loadPlan());
                verifyFrontierNativeReferences(original, terrainOwner, objects);
                verifyContactNativeReferences(original, terrainOwner);
                verifyGameOverNativeReferences(original, objects);
                for (int offset : List.of(0x9C96, 0xA0F2, 0xA0FA, 0xB6CA, 0xB6D8, 0xB6E6, 0xB722,
                        0xCF48, 0x70C6, 0x35626, 0x3702A, 0x30000,
                        0xD1AE, 0xD1F0, 0xD22C, 0xD240, 0xD264, 0xE020, 0xDDC8, 0x1FDBC, 0xE046,
                        0x381C0, 0x3AC7A, 0x3AE02,
                        0x6EDC, 0x3F074, 0x3F088, 0xAC64, 0x35ED8, 0x35EF4,
                        0x3513E, 0x315C4, 0x31628, 0x35F4E,
                        0xEF94, 0xF03E, 0xF075)) {
                    byte[] altered = original.readFile(0, original.fileBytes());
                    altered[offset] ^= 1;
                    // A plausible/correct caller record cannot authenticate even an unrelated changed byte.
                    assertThrows(IOException.class, () -> CdR11aObjectAssets.load(terrainOwner.profile(),
                            terrainOwner.identity(), CdMmdImage.parse(altered), terrainOwner.loadPlan()));
                }
            } else {
                assertThrows(UnsupportedOperationException.class, terrainOwner::openingObjects);
                // The Game Over view and its body assets are USA-only: no regional owner exists to bind them, and
                // the USA range does not hold the source blob in this region's R11A (no offset fallback is inferred).
                assertThrows(UnsupportedOperationException.class,
                        () -> CdGameOver.Assets.fromObjectAssets(terrainOwner.openingObjects()));
                assertFalse(Arrays.equals(GAME_OVER_SOURCE_BYTES, source.mmd(CdNativeFile.R11A)
                        .readFile(GAME_OVER_MAPPINGS.range().fileOffset(), GAME_OVER_MAPPINGS.range().length())));
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
        // First contact visual/table/art reads occur after close, from the retained authenticated image.
        verifyContact(terrainOwner, untouchedFrontier);
        // So does that owner's first, lazy Game Over view.
        verifyGameOver(untouchedFrontier);
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
        hashes.putAll(Map.of(
                ENEMY_POINTS, "75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5",
                EXPLOSION_ANIMATIONS, "9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649",
                EXPLOSION_MAPPINGS, "0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44",
                POINTS_MAPPINGS, "24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e",
                FLOWER_ANIMATIONS, "74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9",
                FLOWER_MAPPINGS, "48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41",
                GAME_OVER_MAPPINGS, "ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b"));
        for (var table : CdR11aObjectAssets.Table.values()) {
            var view = objects.view(table);
            assertEquals(table, view.table());
            assertEquals(table.range(), view.range());
            assertEquals(table.range().length(), view.bytes().length());
            assertEquals(hashes.get(table), CdR11aProfile.sha256(view.bytes().copy()));
        }
        verifyGameOver(objects);
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
        verifyContact(terrainOwner, objects);
        assertSame(bank, objects.initialArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), bank.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        for (var added : List.of(ART_ANIMALS, ART_3D_PLANT, ART_3D_RAMP_BOOST,
                ART_EXPLOSIONS, ART_POINTS, ART_FLOWER)) {
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

    private static void verifyContact(CdR11aAssets owner, CdR11aObjectAssets objects) throws Exception {
        var explosion = objects.explosion();
        var points = objects.points();
        var flower = objects.flower();
        assertSame(explosion, objects.explosion());
        assertSame(points, objects.points());
        assertSame(flower, objects.flower());
        assertEquals(List.of(14, 26, 42, 64, 86, 108, 124), explosion.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(2, 3, 4, 4, 4, 3, 4), explosion.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(List.of(List.of(0, 5, 6, 3, 4), List.of(0, 1, 2, 3, 4)), explosion.animations().stream()
                .map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        assertEquals(List.of(3, 3), explosion.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertTrue(explosion.animations().stream().allMatch(a -> a.terminal() == CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC));
        assertEquals(List.of(12, 24, 36, 48, 60, 66), points.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(2, 2, 2, 2, 1, 3), points.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(0, points.animationTable().length());
        assertTrue(points.animations().isEmpty(), "ObjPoints selects oMapFrame directly; no invented script");
        assertEquals(List.of(16, 22, 28, 34, 40, 52, 58, 74), flower.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(1, 1, 1, 1, 2, 1, 3, 3), flower.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(List.of(List.of(0, 1), List.of(2, 3, 2, 3), List.of(5, 5, 4, 6), List.of(6, 7)),
                flower.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        assertEquals(List.of(3, 3, 1, 19), flower.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(CdObjectVisualAssets.Terminal.LOOP_FF, CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,
                        CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, CdObjectVisualAssets.Terminal.LOOP_FF),
                flower.animations().stream().map(CdObjectVisualAssets.AnimationScript::terminal).toList());
        assertEquals(List.of(69, 4, 38), List.of(lastUsedTile(explosion), lastUsedTile(points), lastUsedTile(flower)));
        var visuals = List.of(explosion, points, flower);
        var mappings = List.of(EXPLOSION_MAPPINGS, POINTS_MAPPINGS, FLOWER_MAPPINGS);
        for (int i = 0; i < visuals.size(); i++) {
            var visual = visuals.get(i);
            assertArrayEquals(objects.view(mappings.get(i)).bytes().copy(), visual.mappings().copy());
            byte[] changed = visual.mappings().copy();
            changed[0] ^= 1;
            assertNotEquals(changed[0], visual.mappings().copy()[0]);
            assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
            assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        }
        assertArrayEquals(objects.view(EXPLOSION_ANIMATIONS).bytes().copy(), explosion.animationTable().copy());
        assertArrayEquals(objects.view(FLOWER_ANIMATIONS).bytes().copy(), flower.animationTable().copy());
        var rewards = objects.view(ENEMY_POINTS).bytes();
        assertEquals(List.of(10, 20, 50, 100), List.of(rewards.unsignedWord(0), rewards.unsignedWord(2),
                rewards.unsignedWord(4), rewards.unsignedWord(6)));
        assertThrows(IndexOutOfBoundsException.class, () -> rewards.unsignedWord(8));
        assertThrows(ReadOnlyBufferException.class, () -> rewards.buffer().put((byte) 0));
        byte[] changedRewards = rewards.copy();
        changedRewards[1] = 0;
        assertEquals(10, rewards.unsignedWord(0));

        var initial = objects.initialArtBank();
        var frontier = objects.frontierArtBank();
        var bank = objects.contactArtBank();
        assertSame(bank, objects.contactArtBank());
        var sources = List.of(ART_EXPLOSIONS, ART_POINTS, ART_FLOWER);
        assertEquals(sources, bank.bindings().stream().map(CdR11aObjectAssets.ArtBinding::source).toList());
        int[] vrams = {0xD000, 0xD8C0, 0xDAE0}, bases = {0x8680, 0x06C6, 0xA6D7};
        int[] blobs = {1256, 100, 646}, readers = {1256, 101, 645}, tiles = {70, 5, 39};
        String[] hashes = {"434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",
                "2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",
                "db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793"};
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            var binding = bank.binding(source);
            assertEquals(1, binding.plcId());
            assertEquals(owner.loadPlan().plcs().get(1).entries().get(10 + i), binding.plc());
            assertEquals(source, binding.plc().source());
            assertEquals(vrams[i], binding.plc().vramAddress());
            assertEquals(bases[i], binding.baseWord());
            assertEquals(bases[i] & 0x7FF, binding.firstTileId());
            assertEquals(owner.profile().range(source), binding.decode().sourceBlobRange());
            assertEquals(binding.plc().sourceRange(), binding.decode().sourceBlobRange());
            assertEquals(blobs[i], binding.decode().sourceBlobRange().length());
            assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                    binding.decode().sourceBlobRange().fileOffset(), readers[i]), binding.decode().readerInputRange());
            assertEquals(readers[i], binding.decode().consumedInputBytes());
            assertEquals(tiles[i], binding.tileCount());
            assertEquals(tiles[i] * 32, binding.bytes().length());
            assertEquals(hashes[i], CdR11aProfile.sha256(binding.bytes().copy()));
            int first = binding.firstTileId(), last = first + tiles[i] - 1;
            assertArrayEquals(binding.bytes().slice(0, 32).copy(), bank.tile(first).copy());
            assertArrayEquals(binding.bytes().slice((tiles[i] - 1) * 32, 32).copy(), bank.tile(last).copy());
            for (var previous : List.of(initial.bindings(), frontier.bindings())) {
                for (var old : previous) {
                    assertTrue(last < old.firstTileId() || first >= old.firstTileId() + old.tileCount());
                    assertThrows(UnsupportedOperationException.class, () -> bank.binding(old.source()));
                    assertThrows(UnsupportedOperationException.class, () -> bank.tile(old.firstTileId()));
                }
            }
            assertThrows(UnsupportedOperationException.class, () -> initial.binding(source));
            assertThrows(UnsupportedOperationException.class, () -> frontier.binding(source));
            assertThrows(UnsupportedOperationException.class, () -> initial.tile(first));
            assertThrows(UnsupportedOperationException.class, () -> frontier.tile(first));
            assertThrows(UnsupportedOperationException.class, () -> owner.decoded(source));
            byte[] altered = bank.tile(first).copy();
            altered[0] ^= 1;
            assertNotEquals(altered[0], bank.tile(first).copy()[0]);
        }
        for (int tile = 0x6CB; tile <= 0x6D6; tile++) {
            int gap = tile;
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(gap), "checkpoint gap must remain absent");
        }
        for (int tile : new int[]{0x67F, 0x6FE, 0x780, 0x797}) {
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(tile));
        }
        assertThrows(UnsupportedOperationException.class, () -> bank.binding(CdR11aAsset.ART_CHECKPOINT));
        assertThrows(UnsupportedOperationException.class, () -> bank.bindings().clear());
        assertThrows(IllegalArgumentException.class, () -> bank.tile(-1));
        assertThrows(IllegalArgumentException.class, () -> bank.tile(0x800));
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(0x680).buffer().put((byte) 0));
        assertSame(initial, objects.initialArtBank());
        assertSame(frontier, objects.frontierArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), initial.bindings().stream().map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(List.of(ART_ANIMALS, ART_3D_PLANT, ART_3D_RAMP_BOOST), frontier.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
    }

    private static void verifyContactNativeReferences(CdMmdImage image, CdR11aAssets owner) throws Exception {
        for (var reference : List.of(new int[]{0x609C, 0x23F088}, new int[]{0xAC38, 0x20AC64}, new int[]{0x6136, 0x235EF4})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 8));
            assertEquals((short) 0x217C, instruction.getShort());
            assertEquals(reference[1], instruction.getInt());
            assertEquals(4, instruction.getShort());
        }
        for (var reference : List.of(new int[]{0x60CA, 0x23F074}, new int[]{0x61BE, 0x235ED8})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 6));
            assertEquals((short) 0x43F9, instruction.getShort());
            assertEquals(reference[1], instruction.getInt());
        }
        var enemyPoints = ByteBuffer.wrap(image.readFile(0x6E80, 4));
        assertEquals((short) 0x303B, enemyPoints.getShort());
        assertEquals(0, enemyPoints.get()); // PC + signed byte displacement + D0.w
        assertEquals(ENEMY_POINTS.range().fileOffset(), 0x6E82 + enemyPoints.get());
        for (var reference : List.of(new int[]{0x608A, 0x8680}, new int[]{0xAC32, 0x06C6},
                new int[]{0x6124, 0xA6D7}, new int[]{0x6168, 0x06D7}, new int[]{0x620E, 0x26D7})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 6));
            assertEquals((short) 0x317C, instruction.getShort());
            assertEquals(reference[1], Short.toUnsignedInt(instruction.getShort()));
            assertEquals(2, instruction.getShort());
        }
        int body = 0xFBC6 + ByteBuffer.wrap(image.readFile(0xFBC8, 2)).getShort();
        assertEquals(0xFBFA, body);
        assertEquals(13, ByteBuffer.wrap(image.readFile(body, 2)).getShort());
        var sources = List.of(ART_EXPLOSIONS, ART_POINTS, ART_FLOWER);
        int[] vrams = {0xD000, 0xD8C0, 0xDAE0}, headers = {0x0046, 0x0005, 0x8027}, readers = {1256, 101, 645};
        String[] sourceHashes = {"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1",
                "6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363",
                "baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1"};
        for (int i = 0; i < sources.size(); i++) {
            var range = owner.profile().range(sources.get(i));
            var row = ByteBuffer.wrap(image.readFile(body + 2 + (10 + i) * 6, 6));
            assertEquals(range.nativeAddress(), row.getInt());
            assertEquals(vrams[i], Short.toUnsignedInt(row.getShort()));
            assertEquals(headers[i], Short.toUnsignedInt(ByteBuffer.wrap(image.readFile(range.fileOffset(), 2)).getShort()));
            assertEquals(sourceHashes[i], CdR11aProfile.sha256(range.read(image).copy()));
            int outputBytes = (headers[i] & 0x7FFF) * 32, truncated = readers[i] - 1;
            assertThrows(IOException.class, () -> CdNemesisAsset.decode(image.readFile(range.fileOffset(), truncated), outputBytes, outputBytes));
            var exact = CdNemesisAsset.decode(image.readFile(range.fileOffset(), readers[i]), outputBytes, outputBytes);
            var extended = CdNemesisAsset.decode(image.readFile(range.fileOffset(), readers[i] + 1), outputBytes, outputBytes);
            assertEquals(readers[i], exact.consumedInputBytes());
            assertEquals(readers[i], extended.consumedInputBytes());
            assertArrayEquals(exact.bytes().copy(), extended.bytes().copy());
        }
        assertEquals(0x80, Byte.toUnsignedInt(image.readFile(0x31628, 1)[0]),
                "points lookahead is the first real FlowerCapsule byte, not invented EOF padding");
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

    /**
     * ObjGameOver (Game Over/Main.asm:11-118) as assembled into the USA R11A program. Each operand Init and Main use is
     * checked at its source instruction, and each helper call lands on the plain helper of the R43_LEGACY_DATA_PREFIX 0
     * include (Palmtree Panic/Act 1 Present.asm:9; Level End/Main.asm:36-47), hand-assembled here from its source.
     */
    private static void verifyGameOverNativeReferences(CdMmdImage image, CdR11aObjectAssets objects) {
        var range = objects.view(GAME_OVER_MAPPINGS).range();
        assertEquals(0x20F03E, range.nativeAddress());
        // .Index (:19-21): Init directly follows the table and Main's JMP DrawObject (:111) ends at the blob (:118).
        var index = ByteBuffer.wrap(image.readFile(0xEF68, 4));
        assertEquals(0xEF6C, 0xEF68 + index.getShort());
        assertEquals(0xF024, 0xEF68 + index.getShort());
        assertEquals(0xF038 + 6, range.fileOffset());
        // MOVE.L #MapSpr_GameOver,oMap(a0) (:36) and MOVE.L #MapSpr_TimeOver,oMap(a0) (:43): one blob, two bases.
        for (var reference : List.of(new int[]{0xEF92, range.nativeAddress()}, new int[]{0xEFB4, range.nativeAddress() + 4})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 8));
            assertEquals((short) 0x217C, instruction.getShort());
            assertEquals(reference[1], instruction.getInt());
            assertEquals(4, instruction.getShort()); // oMap
        }
        var tile = ByteBuffer.wrap(image.readFile(0xEF8C, 6)); // MOVE.W #$8544,oTile(a0) (:35)
        assertEquals((short) 0x317C, tile.getShort());
        assertEquals(CdGameOver.NATIVE_TILE_WORD, Short.toUnsignedInt(tile.getShort()));
        assertEquals(2, tile.getShort()); // oTile
        // MOVE.W #SCMD_FADEPCM,d0 (:28) and MOVE.W #SCMD_GMOVERMUS,d0 (:87).
        for (var reference : List.of(new int[]{0xEF6C, CdGameOver.SubCpuCommand.SCMD_FADEPCM.commandWord()},
                new int[]{0xF01A, CdGameOver.SubCpuCommand.SCMD_GMOVERMUS.commandWord()})) {
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 4));
            assertEquals((short) 0x303C, instruction.getShort());
            assertEquals(reference[1], Short.toUnsignedInt(instruction.getShort()));
        }
        // MOVE.B #$3B,oID(a1): .SpawnOverText (:76) and ObjSonic_DeadChkGone (Sonic/Main.asm:2437).
        for (int at : new int[]{0xEFE2, 0x54D2}) {
            assertArrayEquals(hexBytes("137C" + "003B" + "0000"), image.readFile(at, 6));
        }
        int subCpuCmd = absoluteTarget(image, 0xEF70, 0x4EB9);                    // :29 JSR SubCPUCmd
        assertEquals(subCpuCmd, absoluteTarget(image, 0xF01E, 0x4EF9));            // :88 JMP SubCPUCmd
        // Sub CPU.asm:21-22: CMPI.W #SCMD_BOSSMUS,d0, $67 when System.inc's unnamed :183 entry is counted.
        assertArrayEquals(hexBytes("0C400067"), image.readFile(subCpuCmd - 0x200000, 4));
        int findObjSlot = absoluteTarget(image, 0xEFD4, 0x4EB9);                  // :60 JSR FindObjSlot
        assertEquals(findObjSlot, absoluteTarget(image, 0x54CC, 0x4EB9), "the routine DeadChkGone allocates with");
        // Object Spawner.asm:329-340: LEA dynObjects.w ($FFD800),a1 / MOVE.W #95,d0 / TST.B (a1) / BEQ.S / LEA $40 / DBF.
        assertArrayEquals(hexBytes("43F8D800" + "303C005F" + "4A116708" + "43E90040" + "51C8FFF6" + "4E75"),
                image.readFile(findObjSlot - 0x200000, 22));
        int deleteObject = absoluteTarget(image, 0xEFDC, 0x4EF9);                 // :72 JMP R43LegacyDeleteObject
        // Object Functions.asm:228-237: MOVEA.L a0,a1 / MOVEQ #0,d1 / MOVEQ #15,d0 / MOVE.L d1,(a1)+ / DBF / RTS.
        assertArrayEquals(hexBytes("2248" + "7200" + "700F" + "22C1" + "51C8FFFC" + "4E75"),
                image.readFile(deleteObject - 0x200000, 14));
        int drawObject = absoluteTarget(image, 0xF038, 0x4EF9);                   // :111 JMP R43LegacyDrawObject
        // Object Functions.asm:137-138: DrawObject begins BCLR #7,oSprFlags(a0).
        assertArrayEquals(hexBytes("08A800070001"), image.readFile(drawObject - 0x200000, 6));
        assertArrayEquals(GAME_OVER_SOURCE_BYTES, image.readFile(range.fileOffset(), range.length()));
    }

    /** Lazy USA view plus the $3B body's assets, bound only through the authenticated owner. */
    private static void verifyGameOver(CdR11aObjectAssets objects) {
        var view = objects.view(GAME_OVER_MAPPINGS);
        assertSame(view, objects.view(GAME_OVER_MAPPINGS));
        assertEquals(GAME_OVER_MAPPINGS.range(), view.range());
        assertArrayEquals(GAME_OVER_SOURCE_BYTES, view.bytes().copy(), "the authenticated view is the pinned include");
        assertEquals("ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b",
                CdR11aProfile.sha256(view.bytes().copy()));
        var bound = CdGameOver.Assets.fromObjectAssets(objects);
        assertEquals(0x20F03E, bound.gameOverMappingsAddress());
        assertEquals(0x20F042, bound.timeOverMappingsAddress());
        assertArrayEquals(view.bytes().copy(), bound.mappings().copy());
        assertThrows(ReadOnlyBufferException.class, () -> bound.mappings().buffer().put((byte) 0));
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), objects.initialArtBank().bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList(), "the Game Over view decodes no art");
    }

    private static int absoluteTarget(CdMmdImage image, int fileOffset, int opcode) {
        var instruction = ByteBuffer.wrap(image.readFile(fileOffset, 6));
        assertEquals((short) opcode, instruction.getShort(), "JSR/JMP (xxx).L");
        int target = instruction.getInt();
        assertTrue(target >= 0x200000 && target < 0x240000, "target inside the original Word RAM program");
        return target;
    }

    private static byte[] hexBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) { out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16); }
        return out;
    }

    private static byte[] sourceBytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }
}
