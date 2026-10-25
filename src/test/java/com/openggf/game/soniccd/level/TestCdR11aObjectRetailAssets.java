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
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_FLOAT_BLOCK;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_SPRINGS;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_TUNNEL_DOOR;
import static com.openggf.game.soniccd.level.CdR11aAsset.ART_MONITOR_TIME_POSTS;
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
    /** Actual USA include digests; each include was also matched against its pinned source (1ecc5f3a). */
    private static final String FLOAT_BLOCK_ANIMATIONS_SHA256 =
            "3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9";
    private static final String FLOAT_BLOCK_MAPPINGS_SHA256 =
            "98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e";

    private static final String SPRING_ANIMATIONS_SHA256 =
            "7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5";
    private static final String SPRING_MAPPINGS_SHA256 =
            "09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c";

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
                verifyMonitorTimeInitiallyLazy(untouchedFrontier);
                verifyMonitorTimeNativeReferences(original, terrainOwner);
                verifyFrontierNativeReferences(original, terrainOwner, objects);
                verifyContactNativeReferences(original, terrainOwner);
                verifyGameOverNativeReferences(original, objects);
                verifyFloatBlockNativeReferences(original, terrainOwner);
                verifySpringNativeReferences(original, terrainOwner);
                verifyPowerupNativeReferences(original, terrainOwner);
                verifyTunnelDoorNativeReferences(original, terrainOwner);
                for (int offset : List.of(0x9C96, 0xA0F2, 0xA0FA, 0xB6CA, 0xB6D8, 0xB6E6, 0xB722,
                        0xCF48, 0x70C6, 0x35626, 0x3702A, 0x30000,
                        0xD1AE, 0xD1F0, 0xD22C, 0xD240, 0xD264, 0xE020, 0xDDC8, 0x1FDBC, 0xE046,
                        0x381C0, 0x3AC7A, 0x3AE02,
                        0x6EDC, 0x3F074, 0x3F088, 0xAC64, 0x35ED8, 0x35EF4,
                        0x3513E, 0x315C4, 0x31628, 0x35F4E,
                        0xEF94, 0xF03E, 0xF075,
                        0xA3DC, 0xA3E5, 0xA3FA, 0xA445, 0xCF78, 0x365D0, 0x366B5, 0x366B6,
                        0x9844, 0x98D6, 0x9B92, 0x9B9F, 0x9BA0, 0x9BA6, 0x9BF0, 0x9C67,
                        0x34918, 0x34B39, 0x34B3A,
                        0x3F00, 0x65D2, 0x65E2, 0x660A, 0x673C, 0x3EE9C, 0x3EEC1, 0x3EFAD,
                        0x3F04, 0xCDD0, 0xCDEA, 0xCE06, 0xCE9A, 0xCEA9, 0xCEB5,
                        0xCF60, 0xCF86, 0xFC02, 0x36AFE, 0x36B96, 0x36B97,
                        0x3E94, 0x3E98, 0xA47E, 0xA4F6, 0xA6AA, 0xA5D6, 0xA756, 0xA830,
                        0xA9C6, 0xAA4D, 0xAA4E, 0xAC0F, 0xFC32, 0x34B3A, 0x3513D, 0x3513E)) {
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
                // Likewise FloatBlock: no regional owner, and both USA table ranges hold other bytes here.
                var regional = source.mmd(CdNativeFile.R11A);
                assertNotEquals(FLOAT_BLOCK_ANIMATIONS_SHA256, CdR11aProfile.sha256(regional.readFile(
                        FLOAT_BLOCK_ANIMATIONS.range().fileOffset(), FLOAT_BLOCK_ANIMATIONS.range().length())));
                assertNotEquals(FLOAT_BLOCK_MAPPINGS_SHA256, CdR11aProfile.sha256(regional.readFile(
                        FLOAT_BLOCK_MAPPINGS.range().fileOffset(), FLOAT_BLOCK_MAPPINGS.range().length())));
                // Spring raw includes and art remain USA-only optional bindings; no regional offset inference.
                assertNotEquals(SPRING_ANIMATIONS_SHA256, CdR11aProfile.sha256(regional.readFile(
                        SPRING_ANIMATIONS.range().fileOffset(), SPRING_ANIMATIONS.range().length())));
                assertNotEquals(SPRING_MAPPINGS_SHA256, CdR11aProfile.sha256(regional.readFile(
                        SPRING_MAPPINGS.range().fileOffset(), SPRING_MAPPINGS.range().length())));
                // Vertical Door also remains USA-only; these supplied regions hold other bytes at its USA range.
                assertFalse(Arrays.equals(TUNNEL_DOOR_VERTICAL_SOURCE_BYTES, regional.readFile(
                        TUNNEL_DOOR_VERTICAL_MAPPINGS.range().fileOffset(), TUNNEL_DOOR_VERTICAL_MAPPINGS.range().length())));
                // USA monitor offsets are not a Japan/Europe fallback, even though some art is shared.
                assertNotEquals(MONITOR_TIME_ANIMATIONS_SHA256, CdR11aProfile.sha256(regional.readFile(
                        MONITOR_TIME_ANIMATIONS.range().fileOffset(), MONITOR_TIME_ANIMATIONS.range().length())));
                assertNotEquals(MONITOR_TIME_MAPPINGS_SHA256, CdR11aProfile.sha256(regional.readFile(
                        MONITOR_TIME_MAPPINGS.range().fileOffset(), MONITOR_TIME_MAPPINGS.range().length())));
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
        assertThrows(UnsupportedOperationException.class, () -> terrainOwner.decoded(ART_FLOAT_BLOCK));
        assertThrows(UnsupportedOperationException.class, () -> terrainOwner.decoded(ART_SPRINGS));
        assertThrows(UnsupportedOperationException.class, () -> terrainOwner.decoded(ART_TUNNEL_DOOR));
        if (variant != CdReleaseVariant.USA) { return; }
        assertNotNull(objects);
        assertNotNull(untouchedFrontier);
        verifyPowerupRetained(untouchedFrontier);
        var postCloseBank = untouchedFrontier.frontierArtBank();
        assertSame(postCloseBank, untouchedFrontier.frontierArtBank());
        assertEquals("a3a40dce6206cd71835f043eadee914a860724ec4361d1275a9535c7b2a1b164",
                CdR11aProfile.sha256(postCloseBank.binding(ART_3D_PLANT).bytes().copy()));
        assertEquals(393, postCloseBank.binding(ART_3D_PLANT).decode().readerInputRange().length());
        // First contact visual/table/art reads occur after close, from the retained authenticated image.
        verifyContact(terrainOwner, untouchedFrontier);
        // So does that owner's first, lazy Game Over view.
        verifyGameOver(untouchedFrontier);
        // And its first FloatBlock views, visual decode, selector-5 tile word and art decode.
        verifyFloatBlock(terrainOwner, untouchedFrontier);
        // That owner's first Spring views and art decode also occur after source closure.
        verifySpring(terrainOwner, untouchedFrontier);
        // This owner has requested neither the vertical mapping/visual nor its bank until after media close.
        verifyTunnelDoor(terrainOwner, untouchedFrontier);
        verifyMonitorTime(terrainOwner, untouchedFrontier);
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
        hashes.putAll(Map.of(
                FLOAT_BLOCK_ANIMATIONS, FLOAT_BLOCK_ANIMATIONS_SHA256,
                FLOAT_BLOCK_MAPPINGS, FLOAT_BLOCK_MAPPINGS_SHA256));
        hashes.putAll(Map.of(SPRING_ANIMATIONS, SPRING_ANIMATIONS_SHA256, SPRING_MAPPINGS, SPRING_MAPPINGS_SHA256));
        hashes.put(SPRING_SPEEDS, "5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621"); // Native Spring/Main.asm:243-244: F000/F600.
        hashes.put(POWERUP_MAPPINGS, "d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc");
        hashes.put(TUNNEL_DOOR_VERTICAL_MAPPINGS,
                "954bf4fd9df8e85b782482c17f65e68d32101f1e43e21fd4a7fe4058a91b5275");
        hashes.put(MONITOR_TIME_ANIMATIONS, MONITOR_TIME_ANIMATIONS_SHA256);
        hashes.put(MONITOR_TIME_MAPPINGS, MONITOR_TIME_MAPPINGS_SHA256);
        hashes.put(TUNNEL_DOOR_ANIMATIONS, SPIN_TUNNEL_DOOR_ANIMATION_SHA256);
        hashes.put(TUNNEL_DOOR_MAPPINGS, SPIN_TUNNEL_DOOR_MAPPING_SHA256);
        // New lazy views still participate in this complete, independently pinned all-table manifest.
        hashes.put(PATA_BATA_ANIMATIONS, "a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3");
        hashes.put(PATA_BATA_NORMAL_MAPPINGS, "9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b");
        hashes.put(PATA_BATA_DAMAGED_MAPPINGS, "96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889");
        hashes.put(BOULDER_MAPPINGS, BOULDER_MAPPING_SHA256);
        assertEquals(CdR11aObjectAssets.Table.values().length, hashes.size(), "every admitted table has a pinned digest");
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
        verifyFloatBlock(terrainOwner, objects);
        verifySpring(terrainOwner, objects);
        assertSame(bank, objects.initialArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), bank.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        for (var added : List.of(ART_ANIMALS, ART_3D_PLANT, ART_3D_RAMP_BOOST,
                ART_EXPLOSIONS, ART_POINTS, ART_FLOWER, ART_FLOAT_BLOCK, ART_SPRINGS)) {
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

    /** Lazy USA FloatBlock views, visual, selector-5 tile word and PLC_Std row-2 art, bound only through the owner. */
    private static void verifyFloatBlock(CdR11aAssets owner, CdR11aObjectAssets objects) throws Exception {
        var animations = objects.view(FLOAT_BLOCK_ANIMATIONS);
        var mappings = objects.view(FLOAT_BLOCK_MAPPINGS);
        assertSame(animations, objects.view(FLOAT_BLOCK_ANIMATIONS));
        assertSame(mappings, objects.view(FLOAT_BLOCK_MAPPINGS));
        assertEquals(FLOAT_BLOCK_ANIMATIONS.range(), animations.range());
        assertEquals(FLOAT_BLOCK_MAPPINGS.range(), mappings.range());
        assertEquals(FLOAT_BLOCK_ANIMATIONS_SHA256, CdR11aProfile.sha256(animations.bytes().copy()));
        assertEquals(FLOAT_BLOCK_MAPPINGS_SHA256, CdR11aProfile.sha256(mappings.bytes().copy()));
        var visual = objects.floatBlock();
        assertSame(visual, objects.floatBlock());
        assertArrayEquals(mappings.bytes().copy(), visual.mappings().copy());
        assertArrayEquals(animations.bytes().copy(), visual.animationTable().copy());
        assertEquals(List.of(12, 14, 30, 46, 58, 70), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(0, 3, 3, 2, 2, 1), visual.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        // Three real even-alignment zeros stay in the include but outside every consumed body.
        assertEquals(List.of(1, 16, 16, 11, 11, 6), visual.mappingFrames().stream().map(f -> f.bytes().length()).toList());
        for (int pad : new int[]{13, 57, 69}) { assertEquals(0, mappings.bytes().unsignedByte(pad)); }
        // Frames 1-4 reuse tile 0 with raw H/V flip words; frame 5 is the 3x3 block from tile 6.
        assertEquals(List.of(0x0000, 0x0800, 0x1800, 0x1000), visual.mappingFrames().subList(1, 5).stream()
                .map(f -> f.pieces().getFirst().rawTileWord()).toList());
        assertTrue(visual.mappingFrames().subList(1, 5).stream().allMatch(f -> f.pieces().stream()
                .allMatch(p -> p.rawTileWord() == f.pieces().getFirst().rawTileWord())));
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-12, 0xA, 0x0006, -12)), visual.mappingFrames().get(5).pieces());
        assertEquals(14, lastUsedTile(visual));
        assertEquals(List.of(6, 10, 20), visual.animations().stream()
                .map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
        assertEquals(List.of(2, 2, 2), visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(List.of(5), List.of(1, 5, 2, 5, 3, 5, 4, 5), List.of(1, 0, 2, 0, 3, 0, 4, 0)),
                visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        assertEquals(List.of(CdObjectVisualAssets.Terminal.LOOP_FF, CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,
                        CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC),
                visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::terminal).toList());
        assertEquals(List.of(4, 10, 10), visual.animations().stream().map(a -> a.bytes().length()).toList());
        assertEquals(0, animations.bytes().unsignedByte(9));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> animations.bytes().buffer().put((byte) 0));
        assertThrows(UnsupportedOperationException.class, visual.mappingFrames()::clear);
        assertThrows(UnsupportedOperationException.class, visual.animations()::clear);
        // SetObjectTileID selector 5 at subtype2 0, the value in every actual R11A FloatBlock record.
        assertEquals(0x4334, objects.tileWord(5, 0));
        assertEquals(0x23A0, objects.tileWord(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(5, 1));
        // Selector 1 is now the authenticated Pata-Bata consumer; the other unowned selectors still reject.
        assertEquals(0x23B0, objects.tileWord(1, 0));
        for (int selector : new int[]{4, 6, 10, 17}) {
            assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(selector, 0));
        }

        var bank = objects.floatBlockArtBank();
        assertSame(bank, objects.floatBlockArtBank());
        assertEquals(List.of(ART_FLOAT_BLOCK), bank.bindings().stream().map(CdR11aObjectAssets.ArtBinding::source).toList());
        var binding = bank.binding(ART_FLOAT_BLOCK);
        assertEquals(1, binding.plcId());
        assertEquals(owner.loadPlan().plcs().get(1).entries().get(2), binding.plc());
        assertEquals(ART_FLOAT_BLOCK, binding.plc().source());
        assertEquals(0x6680, binding.plc().vramAddress());
        assertEquals(0x4334, binding.baseWord());
        assertEquals(objects.tileWord(5, 0), binding.baseWord());
        assertEquals(0x334, binding.firstTileId());
        assertEquals(binding.plc().vramAddress() / 32, binding.baseWord() & 0x7FF);
        var blob = new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x365D0, 230);
        assertEquals(blob, owner.profile().range(ART_FLOAT_BLOCK));
        assertEquals(blob, binding.decode().sourceBlobRange());
        assertEquals(binding.plc().sourceRange(), binding.decode().sourceBlobRange());
        // The finite 8-bit reader also consumes the real first Art_SpringWheel byte; the blob range stays 230 bytes.
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x365D0, 231),
                binding.decode().readerInputRange());
        assertEquals(231, binding.decode().consumedInputBytes());
        assertEquals(ART_FLOAT_BLOCK, binding.decode().asset());
        assertEquals(15, binding.tileCount());
        assertEquals(15 * 32, binding.bytes().length());
        assertEquals("df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362",
                CdR11aProfile.sha256(binding.bytes().copy()));
        assertTrue(lastUsedTile(visual) < binding.tileCount(), "every mapped tile is inside the decoded source");
        int first = binding.firstTileId(), last = first + binding.tileCount() - 1;
        assertEquals(0x342, last);
        for (int tile = first; tile <= last; tile++) {
            assertArrayEquals(binding.bytes().slice((tile - first) * 32, 32).copy(), bank.tile(tile).copy());
        }
        assertThrows(UnsupportedOperationException.class, () -> bank.tile(first - 1));
        assertThrows(UnsupportedOperationException.class, () -> bank.tile(last + 1));
        var initial = objects.initialArtBank();
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        for (var old : List.of(initial.bindings(), frontier.bindings(), contact.bindings()).stream()
                .flatMap(List::stream).toList()) {
            assertTrue(last < old.firstTileId() || first >= old.firstTileId() + old.tileCount());
            assertThrows(UnsupportedOperationException.class, () -> bank.binding(old.source()));
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(old.firstTileId()));
        }
        assertThrows(UnsupportedOperationException.class, () -> initial.binding(ART_FLOAT_BLOCK));
        assertThrows(UnsupportedOperationException.class, () -> frontier.binding(ART_FLOAT_BLOCK));
        assertThrows(UnsupportedOperationException.class, () -> contact.binding(ART_FLOAT_BLOCK));
        assertThrows(UnsupportedOperationException.class, () -> initial.tile(first));
        assertThrows(UnsupportedOperationException.class, () -> frontier.tile(first));
        assertThrows(UnsupportedOperationException.class, () -> contact.tile(last));
        assertThrows(UnsupportedOperationException.class, () -> owner.decoded(ART_FLOAT_BLOCK));
        assertThrows(UnsupportedOperationException.class, () -> bank.bindings().clear());
        assertThrows(IllegalArgumentException.class, () -> bank.tile(-1));
        assertThrows(IllegalArgumentException.class, () -> bank.tile(0x800));
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(first).buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> binding.bytes().buffer().put((byte) 0));
        byte[] altered = bank.tile(first).copy();
        altered[0] ^= 1;
        assertNotEquals(altered[0], bank.tile(first).copy()[0]);
        // The opt-in bank extends none of the existing banks.
        assertSame(initial, objects.initialArtBank());
        assertSame(frontier, objects.frontierArtBank());
        assertSame(contact, objects.contactArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), initial.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(List.of(ART_ANIMALS, ART_3D_PLANT, ART_3D_RAMP_BOOST), frontier.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(List.of(ART_EXPLOSIONS, ART_POINTS, ART_FLOWER), contact.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
    }

    /**
     * ObjFloatBlock (Floating Block/Main.asm:7-176) as assembled into the USA R11A program: dispatch, Init's mapping
     * and SetObjectTileID operands, the three script operands, PLC_Std row 2 and the finite art reader extent.
     */
    private static void verifyFloatBlockNativeReferences(CdMmdImage image, CdR11aAssets owner) throws Exception {
        // ObjectIndex is one-based (Object Index (Act 1 Present).asm: "12 - Floating block").
        assertEquals(0x20A284, ByteBuffer.wrap(image.readFile(0x3E34 + (0x12 - 1) * 4, 4)).getInt());
        // :8-11: MOVE.W .Index(pc,d0.w),d0 and JSR .Index(pc,d0.w) both resolve to the seven-word .Index at $A2B2.
        assertArrayEquals(hexBytes("303B0026" + "4EBB0022"), image.readFile(0xA28A, 8));
        assertEquals(0xA2B2, 0xA28C + 0x26);
        assertEquals(0xA2B2, 0xA290 + 0x22);
        var index = ByteBuffer.wrap(image.readFile(0xA2B2, 14));
        int[] routines = new int[7];
        for (int i = 0; i < routines.length; i++) { routines[i] = 0xA2B2 + index.getShort(); }
        assertArrayEquals(new int[]{0xA2C0, 0xA2F2, 0xA336, 0xA358, 0xA37C, 0xA3AA, 0xA3BE}, routines);
        // Init (:39-48): ADDQ.B #2,oRoutine / ORI.B #4,oSprFlags / MOVE.L #MapSpr_FloatBlock,oMap / MOVEQ #5,d0 /
        // JSR SetObjectTileID / priority 1, width $C, Y radius $C and map frame 5, falling into Main at $A2F2.
        assertArrayEquals(hexBytes("54280024" + "002800040001"), image.readFile(0xA2C0, 10));
        var map = ByteBuffer.wrap(image.readFile(0xA2CA, 8));
        assertEquals((short) 0x217C, map.getShort());
        assertEquals(FLOAT_BLOCK_MAPPINGS.range().nativeAddress(), map.getInt());
        assertEquals(4, map.getShort()); // oMap
        assertArrayEquals(hexBytes("7005" + "4EB90020CF26"), image.readFile(0xA2D2, 8));
        assertArrayEquals(hexBytes("117C00010018" + "117C000C0019" + "117C000C0016" + "117C0005001A"),
                image.readFile(0xA2DA, 24));
        // Object Tile ID Set.asm:7-17 at $20CF26; both PC-relative operands resolve to OBJECT_TILE_IDS.
        assertArrayEquals(hexBytes("43F90020CF48" + "D040" + "383B0018" + "45FB4014" + "7200" + "12280029" + "D241"
                + "3A321000" + "31450002" + "4E75"), image.readFile(0xCF26, 34));
        assertEquals(OBJECT_TILE_IDS.range().fileOffset(), 0xCF30 + 0x18);
        assertEquals(OBJECT_TILE_IDS.range().fileOffset(), 0xCF34 + 0x14);
        var tileIds = ByteBuffer.wrap(image.readFile(OBJECT_TILE_IDS.range().fileOffset(), OBJECT_TILE_IDS.range().length()));
        assertEquals(0x30, tileIds.getShort(5 * 2));
        assertEquals(0x4334, Short.toUnsignedInt(tileIds.getShort(0x30)));
        int next = OBJECT_TILE_IDS.range().length();
        for (int i = 0; i < 18; i++) {
            int offset = tileIds.getShort(i * 2);
            if (offset > 0x30 && offset < next) { next = offset; }
        }
        assertEquals(0x32, next, "selector 5 selects a one-word body; subtype2 1 would read selector 10's word");
        // Appear/Visible/Vanish (:110,129,140): LEA Ani_FloatBlock,a1 inside each routine, then BSR.W AnimateObject.
        int[][] animate = {{0xA36C, 0xA358, 0xA37C}, {0xA39A, 0xA37C, 0xA3AA}, {0xA3AE, 0xA3AA, 0xA3BE}};
        for (int[] reference : animate) {
            assertTrue(reference[0] >= reference[1] && reference[0] + 6 <= reference[2]);
            var instruction = ByteBuffer.wrap(image.readFile(reference[0], 8));
            assertEquals((short) 0x43F9, instruction.getShort());
            assertEquals(FLOAT_BLOCK_ANIMATIONS.range().nativeAddress(), instruction.getInt());
            assertEquals((short) 0x6100, instruction.getShort());
        }
        // ObjFloatBlock_TopSolid (:156-165): LEA objPlayerSlot.w,a1 / BSR.W .Check, then .Check's MOVE.W oX,d3 /
        // MOVE.W oY,d4 / JMP TopSolidObject ends immediately before the two includes.
        assertArrayEquals(hexBytes("43F8D000" + "61000002" + "36280008" + "3828000C" + "4EF9"), image.readFile(0xA3C6, 18));
        assertEquals(0xA3CE, 0xA3CC + 2);
        assertEquals(FLOAT_BLOCK_ANIMATIONS.range().fileOffset(), 0xA3D6 + 6);
        // PLC_Std row 2 (Data (Act 1 Present).asm:60-61).
        int body = 0xFBC6 + ByteBuffer.wrap(image.readFile(0xFBC8, 2)).getShort();
        assertEquals(0xFBFA, body);
        assertEquals(13, ByteBuffer.wrap(image.readFile(body, 2)).getShort());
        var range = owner.profile().range(ART_FLOAT_BLOCK);
        var row = ByteBuffer.wrap(image.readFile(body + 2 + 2 * 6, 6));
        assertEquals(range.nativeAddress(), row.getInt());
        assertEquals(0x6680, Short.toUnsignedInt(row.getShort()));
        assertEquals(0x800F, Short.toUnsignedInt(ByteBuffer.wrap(image.readFile(range.fileOffset(), 2)).getShort()),
                "15 tiles, XOR mode");
        assertEquals("8ccc2959a0cbebd2fc24ed2f4cc08fd1baaafbf971f13df254784cc65b7c5e76",
                CdR11aProfile.sha256(range.read(image).copy()));
        // The complete 230-byte source is one byte short for the Java reader's final 8-bit peek.
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(image.readFile(range.fileOffset(), 230), 480, 480));
        var exact = CdNemesisAsset.decode(image.readFile(range.fileOffset(), 231), 480, 480);
        var extended = CdNemesisAsset.decode(image.readFile(range.fileOffset(), 232), 480, 480);
        assertEquals(231, exact.consumedInputBytes());
        assertEquals(231, extended.consumedInputBytes());
        assertArrayEquals(exact.bytes().copy(), extended.bytes().copy());
        assertEquals("df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362",
                CdR11aProfile.sha256(exact.bytes().copy()));
        var successor = owner.profile().range(CdR11aAsset.ART_SPRING_WHEEL);
        assertEquals(range.endExclusive(), successor.fileOffset());
        assertEquals(0x0008, Short.toUnsignedInt(ByteBuffer.wrap(image.readFile(successor.fileOffset(), 2)).getShort()));
        assertEquals(0x00, Byte.toUnsignedInt(image.readFile(0x366B6, 1)[0]),
                "FloatBlock lookahead is the real first Art_SpringWheel byte, never invented EOF padding");
    }


    /** Raw native includes and the separate normal-art bank; first called on the untouched owner after close. */
    private static void verifySpring(CdR11aAssets owner, CdR11aObjectAssets objects) throws Exception {
        var maps = objects.view(SPRING_MAPPINGS);
        var scripts = objects.view(SPRING_ANIMATIONS);
        assertSame(maps, objects.view(SPRING_MAPPINGS));
        assertSame(scripts, objects.view(SPRING_ANIMATIONS));
        assertEquals(SPRING_MAPPINGS.range(), maps.range());
        assertEquals(SPRING_ANIMATIONS.range(), scripts.range());
        assertEquals(SPRING_MAPPINGS_SHA256, CdR11aProfile.sha256(maps.bytes().copy()));
        assertEquals(SPRING_ANIMATIONS_SHA256, CdR11aProfile.sha256(scripts.bytes().copy()));
        assertEquals(List.of(12, 24, 30), List.of(maps.bytes().unsignedWord(0),
                maps.bytes().unsignedWord(2), maps.bytes().unsignedWord(4)));
        assertEquals(List.of(40, 52, 58), List.of(maps.bytes().unsignedWord(6),
                maps.bytes().unsignedWord(8), maps.bytes().unsignedWord(10)));
        assertEquals(List.of(6, 42, 78), List.of(maps.bytes().unsignedWord(80),
                maps.bytes().unsignedWord(82), maps.bytes().unsignedWord(84)));
        for (int pad : new int[]{23, 57, 199}) { assertEquals(0, maps.bytes().unsignedByte(pad)); }
        // Preserve each native base. Vertical/horizontal cannot be decoded by the existing uniform-index contract.
        assertThrows(IllegalArgumentException.class,
                () -> CdObjectVisualAssets.decode(maps.bytes().copy(), 3, scripts.bytes().copy(), 1, 36));
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(
                maps.bytes().slice(6, 74).copy(), 3, scripts.bytes().copy(), 1, 36));
        // A pure format check of the actual diagonal subtable and shared script; no diagonal art bank is admitted.
        var diagonal = CdObjectVisualAssets.decode(maps.bytes().slice(80, 120).copy(), 3, scripts.bytes().copy(), 1, 46);
        assertEquals(List.of(6, 42, 78), diagonal.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(7, 7, 8), diagonal.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(45, lastUsedTile(diagonal));
        assertEquals(0, diagonal.animations().getFirst().delay());
        assertEquals(List.of(0, 1, 1, 2, 2, 2, 2, 2, 2, 1), diagonal.animations().getFirst().frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, diagonal.animations().getFirst().terminal());
        assertArrayEquals(scripts.bytes().copy(), diagonal.animationTable().copy());
        assertThrows(ReadOnlyBufferException.class, () -> maps.bytes().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> scripts.bytes().buffer().put((byte) 1));
        byte[] changed = maps.bytes().copy();
        changed[1] ^= 1;
        assertEquals(12, maps.bytes().unsignedWord(0));

        var vertical = objects.springVertical(); var horizontal = objects.springHorizontal();
        assertSame(vertical, objects.springVertical()); assertSame(horizontal, objects.springHorizontal());
        assertEquals(0, vertical.mappingTableOffset()); assertEquals(6, horizontal.mappingTableOffset());
        assertArrayEquals(maps.bytes().copy(), vertical.mappings().copy());
        assertArrayEquals(maps.bytes().copy(), horizontal.mappings().copy());
        assertArrayEquals(scripts.bytes().copy(), vertical.animationTable().copy());
        assertArrayEquals(scripts.bytes().copy(), horizontal.animationTable().copy());
        assertEquals(List.of(12, 24, 30), vertical.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(40, 52, 58), horizontal.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        for (var visual : List.of(vertical, horizontal)) {
            assertEquals(List.of(2, 1, 3), visual.mappingFrames().stream().map(f -> f.pieces().size()).toList());
            for (var frame : visual.mappingFrames()) {
                assertArrayEquals(maps.bytes().slice(visual.mappingTableOffset() + frame.relativeOffset(),
                        1 + frame.pieces().size() * 5).copy(), frame.bytes().copy());
            }
            assertEquals(List.of(0, 1, 1, 2, 2, 2, 2, 2, 2, 1), visual.animations().getFirst().frameBytes());
            assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, visual.animations().getFirst().terminal());
        }
        var speeds = objects.view(SPRING_SPEEDS);
        assertSame(speeds, objects.view(SPRING_SPEEDS));
        assertEquals(SPRING_SPEEDS.range(), speeds.range());
        assertArrayEquals(new byte[]{(byte) 0xF0, 0, (byte) 0xF6, 0}, speeds.bytes().copy());
        // All byte subtypes read exactly (subtype & 2), including unrelated high bits.
        for (int subtype = 0; subtype < 256; subtype++) {
            assertEquals((subtype & 2) == 0 ? -0x1000 : -0xA00, objects.springSpeedWord(subtype));
        }
        assertThrows(IllegalArgumentException.class, () -> objects.springSpeedWord(-1));
        assertThrows(IllegalArgumentException.class, () -> objects.springSpeedWord(256));
        assertEquals(17, lastUsedTile(vertical)); assertEquals(35, lastUsedTile(horizontal));

        var bank = objects.springArtBank();
        assertSame(bank, objects.springArtBank());
        assertEquals(List.of(ART_SPRINGS), bank.bindings().stream().map(CdR11aObjectAssets.ArtBinding::source).toList());
        var binding = bank.binding(ART_SPRINGS);
        assertEquals(1, binding.plcId());
        assertEquals(owner.loadPlan().plcs().get(1).entries().get(7), binding.plc());
        assertEquals(ART_SPRINGS, binding.source());
        assertEquals(ART_SPRINGS, binding.plc().source());
        assertEquals(0xA400, binding.plc().vramAddress());
        assertEquals(0x0520, binding.baseWord(), "retain Init's full raw word; do not infer renderer flag semantics");
        assertEquals(0x520, binding.firstTileId());
        assertEquals(binding.baseWord() & 0x7FF, binding.firstTileId());
        var blob = new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x34918, 546);
        assertEquals(blob, owner.profile().range(ART_SPRINGS));
        assertEquals(blob, binding.decode().sourceBlobRange());
        assertEquals(binding.plc().sourceRange(), binding.decode().sourceBlobRange());
        assertEquals(new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x34918, 547), binding.decode().readerInputRange());
        assertEquals(547, binding.decode().consumedInputBytes());
        assertEquals(ART_SPRINGS, binding.decode().asset());
        assertEquals(36, binding.tileCount());
        assertEquals(1152, binding.bytes().length());
        assertEquals("8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",
                CdR11aProfile.sha256(binding.bytes().copy()));
        for (int tile = 0x520; tile <= 0x543; tile++) {
            assertArrayEquals(binding.bytes().slice((tile - 0x520) * 32, 32).copy(), bank.tile(tile).copy());
        }
        assertThrows(UnsupportedOperationException.class, () -> bank.tile(0x51F));
        assertThrows(UnsupportedOperationException.class, () -> bank.tile(0x544), "adjacent Powerup tiles are not Spring art");
        var initial = objects.initialArtBank();
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        var floating = objects.floatBlockArtBank();
        for (var old : List.of(initial.bindings(), frontier.bindings(), contact.bindings(), floating.bindings()).stream()
                .flatMap(List::stream).toList()) {
            assertTrue(0x543 < old.firstTileId() || 0x520 >= old.firstTileId() + old.tileCount());
            assertThrows(UnsupportedOperationException.class, () -> bank.binding(old.source()));
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(old.firstTileId()));
        }
        assertThrows(UnsupportedOperationException.class, () -> bank.binding(CdR11aAsset.ART_DIAGONAL_SPRING));
        assertThrows(UnsupportedOperationException.class, () -> initial.binding(ART_SPRINGS));
        assertThrows(UnsupportedOperationException.class, () -> frontier.binding(ART_SPRINGS));
        assertThrows(UnsupportedOperationException.class, () -> contact.binding(ART_SPRINGS));
        assertThrows(UnsupportedOperationException.class, () -> floating.binding(ART_SPRINGS));
        assertThrows(UnsupportedOperationException.class, () -> initial.tile(0x520));
        assertThrows(UnsupportedOperationException.class, () -> frontier.tile(0x520));
        assertThrows(UnsupportedOperationException.class, () -> contact.tile(0x543));
        assertThrows(UnsupportedOperationException.class, () -> floating.tile(0x543));
        assertThrows(UnsupportedOperationException.class, () -> owner.decoded(ART_SPRINGS));
        assertThrows(UnsupportedOperationException.class, () -> bank.bindings().clear());
        assertThrows(IllegalArgumentException.class, () -> bank.tile(-1));
        assertThrows(IllegalArgumentException.class, () -> bank.tile(0x800));
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(0x520).buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> binding.bytes().buffer().put((byte) 0));
        byte[] changedTile = bank.tile(0x520).copy();
        changedTile[0] ^= 1;
        assertNotEquals(changedTile[0], bank.tile(0x520).copy()[0]);
        assertSame(initial, objects.initialArtBank());
        assertSame(frontier, objects.frontierArtBank());
        assertSame(contact, objects.contactArtBank());
        assertSame(floating, objects.floatBlockArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), initial.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(List.of(ART_ANIMALS, ART_3D_PLANT, ART_3D_RAMP_BOOST), frontier.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(List.of(ART_EXPLOSIONS, ART_POINTS, ART_FLOWER), contact.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(List.of(ART_FLOAT_BLOCK), floating.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(0x23A0, objects.tileWord(0, 0));
        assertEquals(0x4334, objects.tileWord(5, 0));
        for (int subtype2 : new int[]{0, 1, 255}) {
            assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(15, subtype2));
        }
    }

    /** Exact USA instructions, three native mapping bases, all four script consumers and PLC_Std row 7. */
    private static void verifySpringNativeReferences(CdMmdImage image, CdR11aAssets owner) throws Exception {
        assertEquals(0x2097AE, ByteBuffer.wrap(image.readFile(0x3E34 + (0x0A - 1) * 4, 4)).getInt());
        assertArrayEquals(hexBytes("323B0058" + "4EBB1054"), image.readFile(0x97C4, 8));
        assertEquals(0x981E, 0x97C6 + 0x58);
        assertEquals(0x981E, 0x97CA + 0x54);
        var index = ByteBuffer.wrap(image.readFile(0x981E, 26));
        int[] routines = new int[13];
        for (int i = 0; i < routines.length; i++) { routines[i] = 0x981E + index.getShort(); }
        assertArrayEquals(new int[]{0x9838, 0x98FA, 0x993C, 0x9946, 0x996C, 0x99E4, 0x99EE,
                0x9A0E, 0x9A4E, 0x9A58, 0x9A6A, 0x9B0C, 0x9B16}, routines);
        int base = SPRING_MAPPINGS.range().nativeAddress();
        for (var ref : List.of(new int[]{0x9772, base}, new int[]{0x983C, base},
                new int[]{0x9884, base + 6}, new int[]{0x98A6, base + 80})) {
            var instruction = ByteBuffer.wrap(image.readFile(ref[0], 8));
            assertEquals((short) 0x217C, instruction.getShort());
            assertEquals(ref[1], instruction.getInt());
            assertEquals(4, instruction.getShort());
        }
        // Normal Init stores $0520; ObjSpring2_Init stores $8520. Keep both full words at their real instructions.
        for (var ref : List.of(new int[]{0x9844, 0x0520}, new int[]{0x977A, 0x8520})) {
            var instruction = ByteBuffer.wrap(image.readFile(ref[0], 6));
            assertEquals((short) 0x317C, instruction.getShort());
            assertEquals(ref[1], Short.toUnsignedInt(instruction.getShort()));
            assertEquals(2, instruction.getShort());
        }
        // Subtype bit 1 BSETs the HIGH byte of oTile, changing the raw word by $2000, not tile index by $20.
        assertArrayEquals(hexBytes("08E800050002"), image.readFile(0x98D6, 6));
        assertArrayEquals(hexBytes("02400002" + "317B00060030" + "4E75" + "F000F600"), image.readFile(0x98DC, 16));
        // Only the diagonal Init branch calls selector 15. That unbound art/consumer remains unsupported.
        assertArrayEquals(hexBytes("700F4EB90020CF26"), image.readFile(0x98B0, 8));
        var tileIds = ByteBuffer.wrap(image.readFile(0xCF48, 74));
        assertEquals(0x44, tileIds.getShort(15 * 2));
        assertEquals(0x0490, Short.toUnsignedInt(tileIds.getShort(0x44)));
        for (int at : new int[]{0x993C, 0x99E4, 0x9A4E, 0x9B0C}) {
            var instruction = ByteBuffer.wrap(image.readFile(at, 10));
            assertEquals((short) 0x43F9, instruction.getShort());
            assertEquals(SPRING_ANIMATIONS.range().nativeAddress(), instruction.getInt());
            assertEquals((short) 0x6000, instruction.getShort());
            assertEquals(0x5CFA, at + 8 + instruction.getShort());
        }
        assertArrayEquals(hexBytes("70001028001CB028001D67101140"), image.readFile(0x5CFA, 14));
        int body = 0xFBC6 + ByteBuffer.wrap(image.readFile(0xFBC8, 2)).getShort();
        assertEquals(0xFBFA, body);
        assertEquals(13, ByteBuffer.wrap(image.readFile(body, 2)).getShort());
        var row = ByteBuffer.wrap(image.readFile(body + 2 + 7 * 6, 6));
        var range = owner.profile().range(ART_SPRINGS);
        assertEquals(range.nativeAddress(), row.getInt());
        assertEquals(0xA400, Short.toUnsignedInt(row.getShort()));
        assertEquals(0x8024, Short.toUnsignedInt(ByteBuffer.wrap(image.readFile(0x34918, 2)).getShort()));
        assertEquals("19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831",
                CdR11aProfile.sha256(range.read(image).copy()));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(image.readFile(0x34918, 546), 1152, 1152));
        var exact = CdNemesisAsset.decode(image.readFile(0x34918, 547), 1152, 1152);
        var extended = CdNemesisAsset.decode(image.readFile(0x34918, 548), 1152, 1152);
        assertEquals(547, exact.consumedInputBytes());
        assertEquals(547, extended.consumedInputBytes());
        assertArrayEquals(exact.bytes().copy(), extended.bytes().copy());
        assertEquals("8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",
                CdR11aProfile.sha256(exact.bytes().copy()));
        assertEquals(range.endExclusive(), owner.profile().range(CdR11aAsset.ART_MONITOR_TIME_POSTS).fileOffset());
        assertEquals(0x80, Byte.toUnsignedInt(image.readFile(0x34B3A, 1)[0]),
                "real first Art_MonitorTimePosts byte, never synthetic EOF padding");
        // The next genuine unowned Present dispatch: actual placement records 57/58, not fitted route timing.
        for (var ref : List.of(new int[]{57, 0x200}, new int[]{58, 0x2D0})) {
            var record = ByteBuffer.wrap(image.readFile(0x7906 + ref[0] * 8, 8));
            assertEquals(0x6F8, Short.toUnsignedInt(record.getShort()));
            assertEquals(ref[1], Short.toUnsignedInt(record.getShort()));
            assertEquals(0x0A, Byte.toUnsignedInt(record.get()));
            assertEquals(4, Byte.toUnsignedInt(record.get()));
            assertEquals(0x40, Byte.toUnsignedInt(record.get()));
            assertEquals(0, Byte.toUnsignedInt(record.get()));
        }
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

    /** Pinned Powerup/Data/Mappings.asm:1-262 hand-literal directives; independent of actual media and production. */
    private static final byte[] POWERUP_SOURCE_BYTES = sourceBytes(
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

    private static void verifyPowerupNativeReferences(CdMmdImage image, CdR11aAssets owner) throws IOException {
        assertEquals("7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2",
                CdR11aProfile.sha256(image.readFile(0, image.fileBytes())));
        // ObjectIndex ID $34 -> actual ObjSpinTunnel; the two PC-indexed instructions share index $2065F4.
        var index = owner.view(CdR11aAsset.OBJECT_INDEX).bytes();
        assertEquals(0x2065D2, ByteBuffer.wrap(index.copy()).getInt((0x34 - 1) * 4));
        assertArrayEquals(hexBytes("700010280024303B001A4EBB0016"), image.readFile(0x65D2, 14));
        var routines = ByteBuffer.wrap(image.readFile(0x65F4, 4));
        assertEquals(0x65F8, 0x65F4 + routines.getShort());
        assertEquals(0x661A, 0x65F4 + routines.getShort());
        // Exact Init: routine/sprite flags, tile $0544, raw oMap immediate, subtype copy and byte +1.
        assertArrayEquals(hexBytes("54280024002800040001317C05440002217C0023EE9C000411680028001A5228001A"),
                image.readFile(0x65F8, 34));
        var operand = ByteBuffer.wrap(image.readFile(0x6608, 8));
        assertEquals((short) 0x217C, operand.getShort());
        assertEquals(POWERUP_MAPPINGS.range().nativeAddress(), operand.getInt());
        assertEquals(4, operand.getShort());
        // These are TST.B at the actual WORD BASES, not the low-byte addresses +1 (68000 big endian).
        assertArrayEquals(hexBytes("4A3900FF1588"), image.readFile(0x65E0, 6));
        assertArrayEquals(hexBytes("61000106"), image.readFile(0x6632, 4));
        assertEquals(0x673A, 0x6634 + 0x106);
        assertArrayEquals(hexBytes("4A3900FF1906"), image.readFile(0x673A, 6));
        byte[] actual = image.readFile(0, image.fileBytes());
        int matches = 0;
        for (int at = 0; at <= actual.length - POWERUP_SOURCE_BYTES.length; at++) {
            if (Arrays.equals(actual, at, at + POWERUP_SOURCE_BYTES.length,
                    POWERUP_SOURCE_BYTES, 0, POWERUP_SOURCE_BYTES.length)) {
                matches++;
                assertEquals(0x3EE9C, at);
            }
        }
        assertEquals(1, matches, "the complete independently authored include has one actual USA match");
    }

    /** First raw request is after closing media; no extra visual decoder or art source is requested. */
    private static void verifyPowerupRetained(CdR11aObjectAssets objects) {
        var initial = objects.initialArtBank();
        var openingTables = List.of(RING_DELTAS, RING_ANIMATIONS, RING_MAPPINGS, MOSQUI_NORMAL_ANIMATIONS,
                MOSQUI_DAMAGED_ANIMATIONS, MOSQUI_NORMAL_MAPPINGS, MOSQUI_DAMAGED_MAPPINGS,
                OBJECT_TILE_IDS, TOUCH_DIMENSIONS);
        var opening = openingTables.stream().map(objects::view).toList();
        var view = objects.view(POWERUP_MAPPINGS);
        assertSame(view, objects.view(POWERUP_MAPPINGS));
        assertEquals(POWERUP_MAPPINGS, view.table());
        assertEquals(POWERUP_MAPPINGS.range(), view.range());
        assertEquals(0x23EE9C, view.range().nativeAddress());
        assertEquals(274, view.bytes().length());
        assertArrayEquals(POWERUP_SOURCE_BYTES, view.bytes().copy());
        assertEquals("d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc",
                CdR11aProfile.sha256(view.bytes().copy()));
        int[] offsets = {37, 26, 47, 68, 89, 110, 131, 152, 174, 196, 218, 240, 262};
        int[] counts = {0, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 2};
        int[] ends = {38, 47, 68, 89, 110, 131, 152, 173, 195, 217, 239, 261, 273};
        for (int frame = 0; frame < 13; frame++) {
            assertEquals(offsets[frame], (short) view.bytes().unsignedWord(frame * 2));
            assertEquals(counts[frame], view.bytes().unsignedByte(offsets[frame]));
            assertEquals(ends[frame], offsets[frame] + 1 + counts[frame] * 5);
        }
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decodeStatic(view.bytes().copy(), 13, 36));
        assertThrows(ReadOnlyBufferException.class, () -> view.bytes().buffer().put((byte) 1));
        assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().unsignedByte(274));
        byte[] changed = view.bytes().copy(); changed[1] = 0;
        assertEquals(37, view.bytes().unsignedWord(0));
        assertSame(initial, objects.initialArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), initial.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertThrows(UnsupportedOperationException.class, () -> initial.tile(0x544));
        for (int i = 0; i < 9; i++) { assertSame(opening.get(i), objects.view(openingTables.get(i))); }
    }

    /** Tunnel Door/Data/Mappings.asm:1-27 at 1ecc5f3a, independently literalized in full. */
    private static final byte[] TUNNEL_DOOR_VERTICAL_SOURCE_BYTES = hexBytes(
            "0004001002E8011804FCF8031800FC0002E80C0006FCE804000A1C00");

    private static void verifyTunnelDoorNativeReferences(CdMmdImage image, CdR11aAssets owner) throws IOException {
        assertEquals("7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2",
                CdR11aProfile.sha256(image.readFile(0, image.fileBytes())));
        // ObjectIndex ID $35 (Object Index (Act 1 Present).asm:60) -> actual ObjTunnelDoorV.
        assertEquals(0x20CDD0, ByteBuffer.wrap(owner.view(CdR11aAsset.OBJECT_INDEX).bytes().copy())
                .getInt((0x35 - 1) * 4));
        assertArrayEquals(hexBytes("0020CDD0"), image.readFile(0x3F04, 4));
        // Main.asm:7-20: both PC-indexed operations share the same actual index root $20CDEA.
        assertArrayEquals(hexBytes("700010280024303B00124EBB000E"), image.readFile(0xCDD0, 14));
        assertEquals(0xCDEA, 0xCDD8 + 0x12);
        assertEquals(0xCDEA, 0xCDDC + 0xE);
        var index = ByteBuffer.wrap(image.readFile(0xCDEA, 6));
        assertEquals(0xCE02, 0xCDEA + index.getShort());
        assertEquals(0xCE2E, 0xCDEA + index.getShort());
        assertEquals(0xCE7A, 0xCDEA + index.getShort());
        // ObjTunnelDoorV_Init (Main.asm:34-42), including the actual mapping and tile-selector operands.
        assertArrayEquals(hexBytes("54280024217C0020CE9A0004117C00010018002800040001"
                + "117C00040019117C00180016700C4EB90020CF26"), image.readFile(0xCE02, 44));
        var mapOperand = ByteBuffer.wrap(image.readFile(0xCE06, 8));
        assertEquals((short) 0x217C, mapOperand.getShort());
        assertEquals(TUNNEL_DOOR_VERTICAL_MAPPINGS.range().nativeAddress(), mapOperand.getInt());
        assertEquals(4, mapOperand.getShort());
        assertArrayEquals(hexBytes("700C"), image.readFile(0xCE26, 2));
        assertEquals(0x20CF26, absoluteTarget(image, 0xCE28, 0x4EB9));
        // SetObjectTileID (Object Tile ID Set.asm:7-16) uses the real ObjectTileIDs base, not a source label guess.
        assertArrayEquals(hexBytes("43F90020CF48D040383B001845FB4014720012280029D2413A321000314500024E75"),
                image.readFile(0xCF26, 34));
        var tileIds = owner.openingObjects();
        assertEquals(0x8328, tileIds.tileWord(0xC, 0));
        assertEquals(62, ByteBuffer.wrap(image.readFile(0xCF60, 2)).getShort());
        assertArrayEquals(hexBytes("8328"), image.readFile(0xCF86, 2));
        byte[] actual = image.readFile(0, image.fileBytes());
        int matches = 0;
        for (int at = 0; at <= actual.length - TUNNEL_DOOR_VERTICAL_SOURCE_BYTES.length; at++) {
            if (Arrays.equals(actual, at, at + TUNNEL_DOOR_VERTICAL_SOURCE_BYTES.length,
                    TUNNEL_DOOR_VERTICAL_SOURCE_BYTES, 0, TUNNEL_DOOR_VERTICAL_SOURCE_BYTES.length)) {
                matches++;
                assertEquals(0xCE9A, at);
            }
        }
        assertEquals(1, matches, "complete pinned mapping include has one actual USA match");
        // PLCLists -> PLC_Std, then zero-based row 1: Data (Act 1 Present).asm:28,54-59.
        var plcIndex = owner.view(CdR11aAsset.PLC_LISTS).bytes();
        assertEquals(0xFBFA, 0xFBC6 + (short) plcIndex.unsignedWord(2));
        assertEquals(13, ByteBuffer.wrap(image.readFile(0xFBFA, 2)).getShort());
        assertArrayEquals(hexBytes("00236AFE6500"), image.readFile(0xFC02, 6));
        var row = owner.loadPlan().plcs().get(1).entries().get(1);
        assertEquals(ART_TUNNEL_DOOR, row.source());
        assertEquals(owner.profile().range(ART_TUNNEL_DOOR), row.sourceRange());
        assertEquals(0x6500, row.vramAddress());
        assertEquals("cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b",
                CdR11aProfile.sha256(image.readFile(0x36AFE, 154)));
        assertEquals("e798c00206b5889952ecff9e9319db3d5af548096a264f557df36086e238927a",
                CdR11aProfile.sha256(image.readFile(0x36AFE, 153)));
    }

    /** The first visual and bank request on this authenticated owner occurs after source closure. */
    private static void verifyTunnelDoor(CdR11aAssets owner, CdR11aObjectAssets objects) throws Exception {
        var initial = objects.initialArtBank();
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        var floatBlock = objects.floatBlockArtBank();
        var spring = objects.springArtBank();
        var openingTables = List.of(RING_DELTAS, RING_ANIMATIONS, RING_MAPPINGS, MOSQUI_NORMAL_ANIMATIONS,
                MOSQUI_DAMAGED_ANIMATIONS, MOSQUI_NORMAL_MAPPINGS, MOSQUI_DAMAGED_MAPPINGS,
                OBJECT_TILE_IDS, TOUCH_DIMENSIONS);
        var opening = openingTables.stream().map(objects::view).toList();
        var maps = objects.view(TUNNEL_DOOR_VERTICAL_MAPPINGS);
        assertSame(maps, objects.view(TUNNEL_DOOR_VERTICAL_MAPPINGS));
        assertEquals(TUNNEL_DOOR_VERTICAL_MAPPINGS, maps.table());
        assertEquals(TUNNEL_DOOR_VERTICAL_MAPPINGS.range(), maps.range());
        assertArrayEquals(TUNNEL_DOOR_VERTICAL_SOURCE_BYTES, maps.bytes().copy());
        var visual = objects.tunnelDoorVertical();
        assertSame(visual, objects.tunnelDoorVertical());
        assertArrayEquals(TUNNEL_DOOR_VERTICAL_SOURCE_BYTES, visual.mappings().copy());
        assertEquals(List.of(4, 16), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(2, 2), visual.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(List.of(11, 11), visual.mappingFrames().stream().map(f -> f.bytes().length()).toList());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-24, 1, 0x1804, -4),
                new CdObjectVisualAssets.Piece(-8, 3, 0x1800, -4)), visual.mappingFrames().get(0).pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-24, 0xC, 6, -4),
                new CdObjectVisualAssets.Piece(-24, 4, 0xA, 28)), visual.mappingFrames().get(1).pieces());
        for (int frame = 0; frame < 2; frame++) {
            int start = frame == 0 ? 4 : 16, end = frame == 0 ? 15 : 27;
            assertEquals(start, maps.bytes().unsignedWord(frame * 2));
            assertEquals(end, start + 1 + 5 * maps.bytes().unsignedByte(start));
            assertArrayEquals(Arrays.copyOfRange(TUNNEL_DOOR_VERTICAL_SOURCE_BYTES, start, end),
                    visual.mappingFrames().get(frame).bytes().copy());
            assertEquals(0, maps.bytes().unsignedByte(end));
        }
        assertTrue(visual.animations().isEmpty());
        assertEquals(0, visual.animationTable().length());
        assertEquals(11, lastUsedTile(visual));
        assertEquals(0x8328, objects.tileWord(0xC, 0));
        assertEquals(0x23A0, objects.tileWord(0, 0));
        assertEquals(0x4334, objects.tileWord(5, 0));
        for (int subtype = 1; subtype <= 0xFF; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(0xC, unsupported));
        }
        var bank = objects.tunnelDoorArtBank();
        assertSame(bank, objects.tunnelDoorArtBank());
        assertEquals(List.of(ART_TUNNEL_DOOR), bank.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        var binding = bank.binding(ART_TUNNEL_DOOR);
        assertEquals(1, binding.plcId());
        assertEquals(owner.loadPlan().plcs().get(1).entries().get(1), binding.plc());
        assertEquals(0x6500, binding.plc().vramAddress());
        assertEquals(0x8328, binding.baseWord());
        assertEquals(objects.tileWord(0xC, 0), binding.baseWord());
        assertEquals(0x328, binding.firstTileId());
        assertEquals(12, binding.tileCount());
        assertEquals(384, binding.bytes().length());
        assertEquals(ART_TUNNEL_DOOR, binding.decode().asset());
        var source = new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x36AFE, 154);
        var reader = new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x36AFE, 153);
        assertEquals(source, owner.profile().range(ART_TUNNEL_DOOR));
        assertEquals(source, binding.decode().sourceBlobRange());
        assertEquals(source, binding.plc().sourceRange());
        assertEquals(reader, binding.decode().readerInputRange());
        assertEquals(153, binding.decode().consumedInputBytes());
        assertEquals("dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0",
                CdR11aProfile.sha256(binding.bytes().copy()));
        // The 153rd real source byte is lookahead; no adjacent splash byte or synthetic padding is needed.
        var compressed = owner.view(ART_TUNNEL_DOOR).bytes();
        assertEquals(0x800C, compressed.unsignedWord(0));
        assertEquals(0, compressed.unsignedByte(152));
        assertEquals(0, compressed.unsignedByte(153));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(compressed.slice(0, 152).copy(), 384, 384));
        var bounded = CdNemesisAsset.decode(compressed.slice(0, 153).copy(), 384, 384);
        assertEquals(153, bounded.consumedInputBytes());
        assertArrayEquals(binding.bytes().copy(), bounded.bytes().copy());
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(compressed.copy(), 11 * 32, 384));
        for (int tile = 0x328; tile <= 0x333; tile++) {
            assertArrayEquals(binding.bytes().slice((tile - 0x328) * 32, 32).copy(), bank.tile(tile).copy());
        }
        assertEquals("867546aca5f1590ffcf4b95b55ef99f4e370301c62addd69391eb0d481729ba1",
                CdR11aProfile.sha256(bank.tile(0x328).copy()));
        assertEquals("3408ac8644182dd294f69868c3d3c320bf35b5f084d83adc21d91c67166bd261",
                CdR11aProfile.sha256(bank.tile(0x333).copy()));
        for (int absent : new int[]{0, 0x327, 0x334, 0x544, 0x7FF}) {
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(absent));
        }
        for (int invalid : new int[]{-1, 0x800, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> bank.tile(invalid));
        }
        for (var other : CdR11aAsset.values()) {
            if (other == ART_TUNNEL_DOOR) { continue; }
            assertThrows(UnsupportedOperationException.class, () -> bank.binding(other));
        }
        assertThrows(UnsupportedOperationException.class, bank.bindings()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(0x328).buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> binding.bytes().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
        byte[] changed = bank.tile(0x328).copy(); changed[0] ^= 1;
        assertNotEquals(changed[0], bank.tile(0x328).copy()[0]);
        for (var old : List.of(initial.bindings(), frontier.bindings(), contact.bindings(),
                floatBlock.bindings(), spring.bindings()).stream().flatMap(List::stream).toList()) {
            assertThrows(UnsupportedOperationException.class, () -> bank.binding(old.source()));
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(old.firstTileId()));
        }
        assertThrows(UnsupportedOperationException.class, () -> initial.binding(ART_TUNNEL_DOOR));
        assertThrows(UnsupportedOperationException.class, () -> frontier.binding(ART_TUNNEL_DOOR));
        assertThrows(UnsupportedOperationException.class, () -> contact.binding(ART_TUNNEL_DOOR));
        assertThrows(UnsupportedOperationException.class, () -> floatBlock.binding(ART_TUNNEL_DOOR));
        assertThrows(UnsupportedOperationException.class, () -> spring.binding(ART_TUNNEL_DOOR));
        assertThrows(UnsupportedOperationException.class, () -> owner.decoded(ART_TUNNEL_DOOR));
        assertSame(initial, objects.initialArtBank());
        assertSame(frontier, objects.frontierArtBank());
        assertSame(contact, objects.contactArtBank());
        assertSame(floatBlock, objects.floatBlockArtBank());
        assertSame(spring, objects.springArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), initial.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        for (int i = 0; i < 9; i++) { assertSame(opening.get(i), objects.view(openingTables.get(i))); }
    }

    /** Complete independent N source-text includes; the retained USA file is checked against them. */
    private static final byte[] MONITOR_TIME_ANIMATIONS_SOURCE_BYTES = hexBytes("00140020002C003800440050005C00680074007E01100000080000090000FF0001100101080101090101FF00"
                + "01100202080202090202FF0001100303080303090303FF0001100404080404090404FF000110050508050509"
                + "0505FF0001100606080606090606FF0001100707080707090707FF00010A0E0F0E0B0E0F0EFF010C0E0F0E0D"
                + "0E0F0EFF");
    private static final byte[] MONITOR_TIME_MAPPINGS_SOURCE_BYTES = hexBytes("0028003E0054006A0080009600AC00C200D800EE0104011E01380152016C018601A001B001B601BC04F60500"
                + "12F8F0060000F0F006080000080C0006F00004F6050016F8F0060000F0F006080000080C0006F00004F60500"
                + "1AF8F0060000F0F006080000080C0006F00004F605001EF8F0060000F0F006080000080C0006F00004F60500"
                + "22F8F0060000F0F006080000080C0006F00004F6050026F8F0060000F0F006080000080C0006F00004F60500"
                + "2AF8F0060000F0F006080000080C0006F00004F605002EF8F0060000F0F006080000080C0006F00004F60500"
                + "48F8F0060000F0F006080000080C0006F00004F6050848F8F0060000F0F006080000080C0006F00005D80D00"
                + "32F0E803004CF8E803084C0008010050F8080108500005D80D0832F0E803004CF8E803084C0008010050F808"
                + "0108500005D80D003AF0E803004CF8E803084C0008010050F8080108500005D80D083AF0E803004CF8E80308"
                + "4C0008010050F8080108500005D8050042F8E803004CF8E803084C0008010050F8080108500005D8010046FC"
                + "E803004CF8E803084C0008010050F8080108500003F0060000F0F006080000080C0006F001000D000AF001F8"
                + "0D0032F001F80D003AF0");
    private static final String MONITOR_TIME_ANIMATIONS_SHA256 =
            "c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36";
    private static final String MONITOR_TIME_MAPPINGS_SHA256 =
            "00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca";

    private static void verifyMonitorTimeInitiallyLazy(CdR11aObjectAssets objects) throws Exception {
        var views = CdR11aObjectAssets.class.getDeclaredField("views"); views.setAccessible(true);
        assertEquals(9, ((Map<?, ?>) views.get(objects)).size());
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertTrue(((Map<?, ?>) optional.get(objects)).isEmpty());
        for (String name : List.of("monitorTime", "monitorTimeArtBank")) {
            var field = CdR11aObjectAssets.class.getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(objects), "openingObjects must not request Monitor views or art");
        }
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), objects.initialArtBank().bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
    }

    private static void verifyMonitorTimeNativeReferences(CdMmdImage image, CdR11aAssets owner) {
        assertEquals("7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2",
                CdR11aProfile.sha256(image.readFile(0, image.fileBytes())));
        var index = ByteBuffer.wrap(owner.view(CdR11aAsset.OBJECT_INDEX).bytes().copy());
        assertEquals(0x20A662, index.getInt((0x19 - 1) * 4));
        assertEquals(0x20A7DE, index.getInt((0x1A - 1) * 4));
        assertArrayEquals(hexBytes("0020A6620020A7DE"), image.readFile(0x3E94, 8));
        // ObjMonitor (Main.asm:278-305): actual dispatch table $20A68E and Init $20A698.
        assertArrayEquals(hexBytes("0C28000800286400FE2C700010280024323B00064EFB1002"), image.readFile(0xA676, 24));
        var routines = ByteBuffer.wrap(image.readFile(0xA68E, 10));
        assertEquals(0xA698, 0xA68E + routines.getShort());
        assertArrayEquals(hexBytes("54280024117C000E0016117C000E0017217C0020AA4E0004317C05A80002"),
                image.readFile(0xA698, 30));
        // HUD Init, post Init and Monitor Init all use the same complete counted mapping directory.
        for (int at : new int[]{0xA47C, 0xA4F4, 0xA6A8}) {
            var operand = ByteBuffer.wrap(image.readFile(at, 8));
            assertEquals((short) 0x217C, operand.getShort());
            assertEquals(MONITOR_TIME_MAPPINGS.range().nativeAddress(), operand.getInt());
            assertEquals(4, operand.getShort());
        }
        // ObjTimePost_Spin (:182) and ObjMonitor_Animate (:366) use the actual absolute LEA operands.
        for (int at : new int[]{0xA5D4, 0xA754}) {
            var operand = ByteBuffer.wrap(image.readFile(at, 6));
            assertEquals((short) 0x43F9, operand.getShort());
            assertEquals(MONITOR_TIME_ANIMATIONS.range().nativeAddress(), operand.getInt());
        }
        // ObjMonitorItem_Init (:438-459): bit5 static piece, priority base, real root + signed word + 1.
        assertArrayEquals(hexBytes("54280024317C85A800024A28002967060228007F0002"
                + "117C00240001117C00030018117C00080019317CFD00001270001028001C1140001A"
                + "227C0020AA4ED000D2F10000524921490004"), image.readFile(0xA7F6, 74));
        byte[] actual = image.readFile(0, image.fileBytes());
        for (var include : List.of(MONITOR_TIME_ANIMATIONS_SOURCE_BYTES, MONITOR_TIME_MAPPINGS_SOURCE_BYTES)) {
            int expected = include == MONITOR_TIME_ANIMATIONS_SOURCE_BYTES ? 0xA9C6 : 0xAA4E;
            int matches = 0;
            for (int at = 0; at <= actual.length - include.length; at++) {
                if (Arrays.equals(actual, at, at + include.length, include, 0, include.length)) {
                    matches++; assertEquals(expected, at);
                }
            }
            assertEquals(1, matches, "complete independent pinned source include has one actual USA match");
        }
        // PLCLists index 1 -> PLC_Std; DBF word $D admits fourteen entries, row 9 is this source.
        assertEquals(0xFBFA, 0xFBC6 + (short) owner.view(CdR11aAsset.PLC_LISTS).bytes().unsignedWord(2));
        assertEquals(13, ByteBuffer.wrap(image.readFile(0xFBFA, 2)).getShort());
        assertArrayEquals(hexBytes("00234B3AB500"), image.readFile(0xFC32, 6));
        var row = owner.loadPlan().plcs().get(1).entries().get(9);
        assertEquals(ART_MONITOR_TIME_POSTS, row.source());
        assertEquals(owner.profile().range(ART_MONITOR_TIME_POSTS), row.sourceRange());
        assertEquals(0xB500, row.vramAddress());
        assertEquals("ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a",
                CdR11aProfile.sha256(image.readFile(0x34B3A, 1540)));
        assertEquals("a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",
                CdR11aProfile.sha256(image.readFile(0x34B3A, 1541)));
        assertEquals(0x8052, Short.toUnsignedInt(ByteBuffer.wrap(image.readFile(0x34B3A, 2)).getShort()));
        assertEquals(0, image.readFile(0x3513E, 1)[0], "real first Explosion header byte is the reader's lookahead");
    }

    /** First Monitor table, visual and art requests are after CdProgramAssets.close(), on a retained owner. */
    private static void verifyMonitorTime(CdR11aAssets owner, CdR11aObjectAssets objects) throws Exception {
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertFalse(((Map<?, ?>) optional.get(objects)).containsKey(MONITOR_TIME_ANIMATIONS));
        assertFalse(((Map<?, ?>) optional.get(objects)).containsKey(MONITOR_TIME_MAPPINGS));
        for (String name : List.of("monitorTime", "monitorTimeArtBank")) {
            var field = CdR11aObjectAssets.class.getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(objects));
        }
        var initial = objects.initialArtBank();
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        var floatBlock = objects.floatBlockArtBank();
        var spring = objects.springArtBank();
        var door = objects.tunnelDoorArtBank();
        var openingTables = List.of(RING_DELTAS, RING_ANIMATIONS, RING_MAPPINGS, MOSQUI_NORMAL_ANIMATIONS,
                MOSQUI_DAMAGED_ANIMATIONS, MOSQUI_NORMAL_MAPPINGS, MOSQUI_DAMAGED_MAPPINGS, OBJECT_TILE_IDS, TOUCH_DIMENSIONS);
        var opening = openingTables.stream().map(objects::view).toList();
        var scripts = objects.view(MONITOR_TIME_ANIMATIONS);
        var maps = objects.view(MONITOR_TIME_MAPPINGS);
        assertSame(scripts, objects.view(MONITOR_TIME_ANIMATIONS));
        assertSame(maps, objects.view(MONITOR_TIME_MAPPINGS));
        assertArrayEquals(MONITOR_TIME_ANIMATIONS_SOURCE_BYTES, scripts.bytes().copy());
        assertArrayEquals(MONITOR_TIME_MAPPINGS_SOURCE_BYTES, maps.bytes().copy());
        assertEquals(MONITOR_TIME_ANIMATIONS_SHA256, CdR11aProfile.sha256(scripts.bytes().copy()));
        assertEquals(MONITOR_TIME_MAPPINGS_SHA256, CdR11aProfile.sha256(maps.bytes().copy()));
        var bankField = CdR11aObjectAssets.class.getDeclaredField("monitorTimeArtBank"); bankField.setAccessible(true);
        assertNull(bankField.get(objects), "raw views must not decode art");
        var visual = objects.monitorTime();
        assertSame(visual, objects.monitorTime());
        assertNull(bankField.get(objects), "visual-table decode must not decode art");
        var strict = CdObjectVisualAssets.decode(MONITOR_TIME_MAPPINGS_SOURCE_BYTES, 20,
                MONITOR_TIME_ANIMATIONS_SOURCE_BYTES, 10, 82);
        assertArrayEquals(strict.mappings().copy(), visual.mappings().copy());
        assertArrayEquals(strict.animationTable().copy(), visual.animationTable().copy());
        assertEquals(20, visual.mappingFrames().size());
        assertEquals(10, visual.animations().size());
        for (int i = 0; i < 20; i++) {
            assertEquals(strict.mappingFrames().get(i).relativeOffset(), visual.mappingFrames().get(i).relativeOffset());
            assertEquals(strict.mappingFrames().get(i).pieces(), visual.mappingFrames().get(i).pieces());
            assertArrayEquals(strict.mappingFrames().get(i).bytes().copy(), visual.mappingFrames().get(i).bytes().copy());
        }
        for (int i = 0; i < 10; i++) {
            assertEquals(strict.animations().get(i).frameBytes(), visual.animations().get(i).frameBytes());
            assertEquals(strict.animations().get(i).delay(), visual.animations().get(i).delay());
            assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, visual.animations().get(i).terminal());
        }
        int[] pointers = {0x29, 0x3F, 0x55, 0x6B, 0x81, 0x97, 0xAD, 0xC3};
        for (int i = 0; i < 8; i++) {
            assertArrayEquals(new byte[]{(byte) 0xF6, 5, 0, (byte) (0x12 + i * 4), (byte) 0xF8},
                    maps.bytes().slice(pointers[i], 5).copy());
        }
        var bank = objects.monitorTimeArtBank();
        assertSame(bank, objects.monitorTimeArtBank());
        assertEquals(List.of(ART_MONITOR_TIME_POSTS), bank.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        var binding = bank.binding(ART_MONITOR_TIME_POSTS);
        assertEquals(1, binding.plcId());
        assertEquals(owner.loadPlan().plcs().get(1).entries().get(9), binding.plc());
        assertEquals(0xB500, binding.plc().vramAddress());
        assertEquals(0x05A8, binding.baseWord(), "$85A8 item priority belongs to execution, never mutable bank state");
        assertEquals(0x5A8, binding.firstTileId());
        assertEquals(82, binding.tileCount());
        assertEquals(2624, binding.bytes().length());
        assertEquals(ART_MONITOR_TIME_POSTS, binding.decode().asset());
        var source = new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x34B3A, 1540);
        var reader = new CdR11aProfile.FileRange(CdNativeFile.R11A,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x34B3A, 1541);
        assertEquals(source, owner.profile().range(ART_MONITOR_TIME_POSTS));
        assertEquals(source, binding.decode().sourceBlobRange());
        assertEquals(source, binding.plc().sourceRange());
        assertEquals(reader, binding.decode().readerInputRange());
        assertEquals(1541, binding.decode().consumedInputBytes());
        assertEquals("7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",
                CdR11aProfile.sha256(binding.bytes().copy()));
        var compressed = owner.view(ART_MONITOR_TIME_POSTS).bytes();
        assertEquals(0x8052, compressed.unsignedWord(0));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(compressed.copy(), 2624, 2624));
        byte[] finite = new byte[1541]; System.arraycopy(compressed.copy(), 0, finite, 0, 1540);
        finite[1540] = owner.view(ART_EXPLOSIONS).bytes().signedByte(0);
        assertEquals("a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902", CdR11aProfile.sha256(finite));
        var decoded = CdNemesisAsset.decode(finite, 2624, 2624);
        assertEquals(1541, decoded.consumedInputBytes());
        assertArrayEquals(binding.bytes().copy(), decoded.bytes().copy());
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(finite, 81 * 32, 2624));
        assertThrows(IllegalArgumentException.class, () -> CdNemesisAsset.decode(finite, 2624, 2623));
        for (int tile = 0x5A8; tile <= 0x5F9; tile++) {
            assertArrayEquals(binding.bytes().slice((tile - 0x5A8) * 32, 32).copy(), bank.tile(tile).copy());
        }
        // Every raw counted piece resolves into this finite source after native base addition.
        for (var frame : visual.mappingFrames()) {
            for (var piece : frame.pieces()) {
                int first = (binding.baseWord() + piece.rawTileWord()) & 0x7FF;
                for (int i = 0; i < piece.tileCount(); i++) { assertEquals(32, bank.tile(first + i).length()); }
            }
        }
        assertEquals("5fe4b89a35cda62d7cd45d72c752b8700649dc68e327809f9df85ad2a28b1198",
                CdR11aProfile.sha256(bank.tile(0x5A8).copy()));
        assertEquals("0f688166a2c739a962f99073d50bccb56440676ee3753836866f121ec20465a9",
                CdR11aProfile.sha256(bank.tile(0x5F9).copy()));
        for (int absent : new int[]{0, 0x5A7, 0x5FA, 0x680, 0x7FF}) {
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(absent));
        }
        for (int invalid : new int[]{-1, 0x800, 0x85A8, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> bank.tile(invalid));
        }
        for (var other : CdR11aAsset.values()) {
            if (other != ART_MONITOR_TIME_POSTS) { assertThrows(UnsupportedOperationException.class, () -> bank.binding(other)); }
        }
        assertThrows(UnsupportedOperationException.class, bank.bindings()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(0x5A8).buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> binding.bytes().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
        byte[] changed = bank.tile(0x5A8).copy(); changed[0] ^= 1;
        assertNotEquals(changed[0], bank.tile(0x5A8).copy()[0]);
        for (var old : List.of(initial.bindings(), frontier.bindings(), contact.bindings(), floatBlock.bindings(),
                spring.bindings(), door.bindings()).stream().flatMap(List::stream).toList()) {
            assertThrows(UnsupportedOperationException.class, () -> bank.binding(old.source()));
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(old.firstTileId()));
        }
        assertThrows(UnsupportedOperationException.class, () -> initial.binding(ART_MONITOR_TIME_POSTS));
        assertThrows(UnsupportedOperationException.class, () -> frontier.binding(ART_MONITOR_TIME_POSTS));
        assertThrows(UnsupportedOperationException.class, () -> contact.binding(ART_MONITOR_TIME_POSTS));
        assertThrows(UnsupportedOperationException.class, () -> floatBlock.binding(ART_MONITOR_TIME_POSTS));
        assertThrows(UnsupportedOperationException.class, () -> spring.binding(ART_MONITOR_TIME_POSTS));
        assertThrows(UnsupportedOperationException.class, () -> door.binding(ART_MONITOR_TIME_POSTS));
        assertThrows(UnsupportedOperationException.class, () -> initial.tile(0x5A8));
        assertThrows(UnsupportedOperationException.class, () -> frontier.tile(0x5A8));
        assertThrows(UnsupportedOperationException.class, () -> contact.tile(0x5A8));
        assertThrows(UnsupportedOperationException.class, () -> floatBlock.tile(0x5A8));
        assertThrows(UnsupportedOperationException.class, () -> spring.tile(0x5A8));
        assertThrows(UnsupportedOperationException.class, () -> door.tile(0x5A8));
        assertThrows(UnsupportedOperationException.class, () -> owner.decoded(ART_MONITOR_TIME_POSTS));
        assertSame(initial, objects.initialArtBank()); assertSame(frontier, objects.frontierArtBank());
        assertSame(contact, objects.contactArtBank()); assertSame(floatBlock, objects.floatBlockArtBank());
        assertSame(spring, objects.springArtBank()); assertSame(door, objects.tunnelDoorArtBank());
        for (int i = 0; i < 9; i++) { assertSame(opening.get(i), objects.view(openingTables.get(i))); }
        assertEquals(0x23A0, objects.tileWord(0, 0));
        assertEquals(0x4334, objects.tileWord(5, 0));
        assertEquals(0x8328, objects.tileWord(0xC, 0));
        assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(9, 0));
        assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(0, 1));
        assertEquals(0x05A8, binding.baseWord());
    }

    /** Source-literal complete Door animation/mapping includes from Spin Tunnel/Data, N=1ecc5f3a. */
    private static final byte[] SPIN_TUNNEL_DOOR_ANIMATION_BYTES = sourceBytes(
            0, 2, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0xFC);
    private static final byte[] SPIN_TUNNEL_DOOR_MAPPING_BYTES = hexBytes(
            "0010000402D0030000E4F0010004E40002F80C0006E8F804000A0800");
    private static final String SPIN_TUNNEL_DOOR_ANIMATION_SHA256 =
            "398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0";
    private static final String SPIN_TUNNEL_DOOR_MAPPING_SHA256 =
            "7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92";

    @ParameterizedTest(name = "{0} optional horizontal Tunnel Door tables and existing shared art")
    @EnumSource(CdReleaseVariant.class)
    void horizontalTunnelDoorAuthenticatesActualOwnerAndRetainsTheSharedBankAfterMediaClose(CdReleaseVariant variant)
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
        CdR11aAssets owner;
        CdR11aObjectAssets visualFirst = null, bankFirst = null;
        try (var source = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            owner = CdR11aAssetLoader.load(source, variant, CdStageKey.parse("R11A"));
            var image = source.mmd(CdNativeFile.R11A);
            // Both other regions happen to share these source bytes at the USA offsets. Equality grants no owner.
            assertArrayEquals(SPIN_TUNNEL_DOOR_ANIMATION_BYTES, image.readFile(0x3F02A, 14));
            assertArrayEquals(SPIN_TUNNEL_DOOR_MAPPING_BYTES, image.readFile(0x3F038, 28));
            if (variant != CdReleaseVariant.USA) {
                assertEquals("241be9620fbad1229792fedd1604066ae3e7d943cb7cc27bc94a4a837452d9b6",
                        CdR11aProfile.sha256(image.readFile(0, image.fileBytes())));
                assertThrows(UnsupportedOperationException.class, owner::openingObjects);
                assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.load(
                        owner.profile(), owner.identity(), image, owner.loadPlan()));
                var usa = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
                var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, usa.stage(), usa.file(), usa.domain(),
                        usa.expectedMmdSha256(), owner.identity().observedWholeDiscSha256());
                assertThrows(IOException.class, () -> CdR11aObjectAssets.load(usa, asserted, image, owner.loadPlan()));
            } else {
                visualFirst = owner.openingObjects();
                bankFirst = CdR11aObjectAssets.load(owner.profile(), owner.identity(), image, owner.loadPlan());
                verifyHorizontalTunnelDoorCold(visualFirst);
                verifyHorizontalTunnelDoorCold(bankFirst);
                verifyHorizontalTunnelDoorNativeReferences(image, owner);
                for (int offset : new int[]{0x3E64, 0x6280, 0x629A, 0x62F6, 0x6324, 0x633C,
                        0x3F02A, 0x3F02C, 0x3F037, 0x3F038, 0x3F03A, 0x3F03C, 0x3F047, 0x3F048, 0x3F053,
                        0xCF60, 0xCF86, 0xFC02, 0x36AFE, 0x36B96}) {
                    byte[] changed = image.readFile(0, image.fileBytes()); changed[offset] ^= 1;
                    assertThrows(IOException.class, () -> CdR11aObjectAssets.load(owner.profile(), owner.identity(),
                            CdMmdImage.parse(changed), owner.loadPlan()), "changed file offset " + offset);
                }
            }
        }
        assertEquals(variant, owner.identity().variant());
        assertThrows(UnsupportedOperationException.class, () -> owner.decoded(ART_TUNNEL_DOOR));
        if (variant != CdReleaseVariant.USA) { return; }
        assertNotNull(visualFirst); assertNotNull(bankFirst);
        // First new table/visual/art reads on both owners occur only after the supplied-media source closes.
        verifyHorizontalTunnelDoorRetained(owner, visualFirst, bankFirst);
    }

    private static void verifyHorizontalTunnelDoorCold(CdR11aObjectAssets objects) throws Exception {
        var views = CdR11aObjectAssets.class.getDeclaredField("views"); views.setAccessible(true);
        assertEquals(9, ((Map<?, ?>) views.get(objects)).size());
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertTrue(((Map<?, ?>) optional.get(objects)).isEmpty());
        for (String name : List.of("tunnelDoor", "tunnelDoorVertical", "tunnelDoorArtBank", "monitorTimeArtBank")) {
            var field = CdR11aObjectAssets.class.getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(objects), "startup must not read new tables or decode any shared Door art");
        }
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), objects.initialArtBank().bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(84, objects.initialArtBank().bindings().stream().mapToInt(CdR11aObjectAssets.ArtBinding::tileCount).sum());
    }

    private static void verifyHorizontalTunnelDoorNativeReferences(CdMmdImage image, CdR11aAssets owner) {
        assertEquals("7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2",
                CdR11aProfile.sha256(image.readFile(0, image.fileBytes())));
        // ObjectIndex ID $0D -> ObjTunnelDoor, Spin Tunnel/Main (Door).asm:7-20.
        assertEquals(0x206280, owner.view(CdR11aAsset.OBJECT_INDEX).bytes().unsignedLong((0xD - 1) * 4));
        assertArrayEquals(hexBytes("00206280"), image.readFile(0x3E64, 4));
        assertArrayEquals(hexBytes("700010280024303B00124EBB000E"), image.readFile(0x6280, 14));
        var index = ByteBuffer.wrap(image.readFile(0x629A, 8));
        assertEquals(0x62F2, 0x629A + index.getShort());
        assertEquals(0x632C, 0x629A + index.getShort());
        assertEquals(0x633C, 0x629A + index.getShort());
        assertEquals(0x6346, 0x629A + index.getShort());
        // Init's actual MOVE.L operand and Animate's actual LEA disambiguate source labels and identical scripts.
        assertArrayEquals(hexBytes("54280024217C0023F0380004117C00010018002800040001"
                + "117C002C00190C28000200286606117C00180019117C00080016700C4EB90020CF26"), image.readFile(0x62F2, 58));
        var mapOperand = ByteBuffer.wrap(image.readFile(0x62F6, 8));
        assertEquals((short) 0x217C, mapOperand.getShort());
        assertEquals(TUNNEL_DOOR_MAPPINGS.range().nativeAddress(), mapOperand.getInt());
        assertEquals(4, mapOperand.getShort());
        assertArrayEquals(hexBytes("700C"), image.readFile(0x6324, 2));
        assertEquals(0x20CF26, absoluteTarget(image, 0x6326, 0x4EB9));
        assertEquals(TUNNEL_DOOR_ANIMATIONS.range().nativeAddress(), absoluteTarget(image, 0x633C, 0x43F9));
        var animate = ByteBuffer.wrap(image.readFile(0x6342, 4));
        assertEquals((short) 0x6000, animate.getShort());
        assertEquals(0x5CFA, 0x6344 + animate.getShort());
        assertArrayEquals(hexBytes("43F90020CF48D040383B001845FB4014720012280029D2413A321000314500024E75"),
                image.readFile(0xCF26, 34));
        assertEquals(62, ByteBuffer.wrap(image.readFile(0xCF60, 2)).getShort());
        assertArrayEquals(hexBytes("8328"), image.readFile(0xCF86, 2));
        byte[] actual = image.readFile(0, image.fileBytes());
        for (byte[] include : List.of(SPIN_TUNNEL_DOOR_ANIMATION_BYTES, SPIN_TUNNEL_DOOR_MAPPING_BYTES)) {
            var hits = new java.util.ArrayList<Integer>();
            for (int at = 0; at <= actual.length - include.length; at++) {
                if (Arrays.equals(actual, at, at + include.length, include, 0, include.length)) { hits.add(at); }
            }
            assertEquals(include == SPIN_TUNNEL_DOOR_ANIMATION_BYTES ? List.of(0xCE8C, 0x3F02A) : List.of(0x3F038), hits);
        }
        // PLCLists/PLC_Std row 1 is the existing Art_TunnelDoor source, not splash or another decode bank.
        assertEquals(0xFBFA, 0xFBC6 + (short) owner.view(CdR11aAsset.PLC_LISTS).bytes().unsignedWord(2));
        assertEquals(13, ByteBuffer.wrap(image.readFile(0xFBFA, 2)).getShort());
        assertArrayEquals(hexBytes("00236AFE6500"), image.readFile(0xFC02, 6));
        var row = owner.loadPlan().plcs().get(1).entries().get(1);
        assertEquals(ART_TUNNEL_DOOR, row.source());
        assertEquals(owner.profile().range(ART_TUNNEL_DOOR), row.sourceRange());
        assertEquals(0x6500, row.vramAddress());
        assertEquals("cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b",
                CdR11aProfile.sha256(image.readFile(0x36AFE, 154)));
    }

    private static void verifyHorizontalTunnelDoorRetained(CdR11aAssets owner, CdR11aObjectAssets objects,
                                                           CdR11aObjectAssets bankFirst) throws Exception {
        var initial = objects.initialArtBank();
        var oldTables = Arrays.stream(CdR11aObjectAssets.Table.values()).limit(34).toList();
        var oldViews = oldTables.stream().map(objects::view).toList();
        var oldHashes = oldViews.stream().map(v -> CdR11aProfile.sha256(v.bytes().copy())).toList();
        var artCache = CdR11aObjectAssets.class.getDeclaredField("tunnelDoorArtBank"); artCache.setAccessible(true);
        var verticalCache = CdR11aObjectAssets.class.getDeclaredField("tunnelDoorVertical"); verticalCache.setAccessible(true);
        assertNull(artCache.get(objects)); assertNull(verticalCache.get(objects));
        var visual = objects.tunnelDoor();
        assertSame(visual, objects.tunnelDoor());
        assertNull(artCache.get(objects), "decoding visual tables must not decode art");
        assertNull(verticalCache.get(objects), "horizontal accessor must not decode the vertical visual");
        assertArrayEquals(SPIN_TUNNEL_DOOR_MAPPING_BYTES, visual.mappings().copy());
        assertArrayEquals(SPIN_TUNNEL_DOOR_ANIMATION_BYTES, visual.animationTable().copy());
        assertEquals(SPIN_TUNNEL_DOOR_MAPPING_SHA256, CdR11aProfile.sha256(visual.mappings().copy()));
        assertEquals(SPIN_TUNNEL_DOOR_ANIMATION_SHA256, CdR11aProfile.sha256(visual.animationTable().copy()));
        var mapView = objects.view(TUNNEL_DOOR_MAPPINGS);
        var scriptView = objects.view(TUNNEL_DOOR_ANIMATIONS);
        assertSame(mapView, objects.view(TUNNEL_DOOR_MAPPINGS));
        assertSame(scriptView, objects.view(TUNNEL_DOOR_ANIMATIONS));
        assertArrayEquals(SPIN_TUNNEL_DOOR_MAPPING_BYTES, mapView.bytes().copy());
        assertArrayEquals(SPIN_TUNNEL_DOOR_ANIMATION_BYTES, scriptView.bytes().copy());
        assertEquals(List.of(16, 4), visual.mappingFrames().stream()
                .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-8, 12, 6, -24),
                new CdObjectVisualAssets.Piece(-8, 4, 10, 8)), visual.mappingFrames().getFirst().pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-48, 3, 0, -28),
                new CdObjectVisualAssets.Piece(-16, 1, 4, -28)), visual.mappingFrames().getLast().pieces());
        for (int i = 0; i < 2; i++) {
            var frame = visual.mappingFrames().get(i);
            assertEquals(11, frame.bytes().length());
            assertArrayEquals(Arrays.copyOfRange(SPIN_TUNNEL_DOOR_MAPPING_BYTES, frame.relativeOffset(),
                    frame.relativeOffset() + 11), frame.bytes().copy());
            assertEquals(0, visual.mappings().unsignedByte(frame.relativeOffset() + 11));
        }
        assertEquals(1, visual.animations().size());
        var script = visual.animations().getFirst();
        assertEquals(2, script.relativeOffset()); assertEquals(0, script.delay());
        assertEquals(List.of(0, 0, 1, 1, 1, 1, 1, 1, 1, 1), script.frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC, script.terminal());
        assertArrayEquals(Arrays.copyOfRange(SPIN_TUNNEL_DOOR_ANIMATION_BYTES, 2, 14), script.bytes().copy());
        var bank = objects.tunnelDoorArtBank();
        var binding = bank.binding(ART_TUNNEL_DOOR);
        assertSame(bank, objects.tunnelDoorArtBank());
        var vertical = objects.tunnelDoorVertical();
        assertArrayEquals(TUNNEL_DOOR_VERTICAL_SOURCE_BYTES, vertical.mappings().copy());
        assertSame(vertical, objects.tunnelDoorVertical());
        assertTrue(vertical.animations().isEmpty());
        assertSame(bank, objects.tunnelDoorArtBank());
        assertSame(binding, objects.tunnelDoorArtBank().binding(ART_TUNNEL_DOOR));
        assertEquals(List.of(binding), bank.bindings());
        assertEquals(1, binding.plcId()); assertEquals(0x6500, binding.plc().vramAddress());
        assertEquals(0x8328, binding.baseWord()); assertEquals(0x328, binding.firstTileId());
        assertEquals(12, binding.tileCount()); assertEquals(384, binding.bytes().length());
        assertEquals(owner.profile().range(ART_TUNNEL_DOOR), binding.decode().sourceBlobRange());
        assertEquals(153, binding.decode().readerInputRange().length());
        assertEquals(153, binding.decode().consumedInputBytes());
        assertEquals("dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0",
                CdR11aProfile.sha256(binding.bytes().copy()));
        assertEquals("867546aca5f1590ffcf4b95b55ef99f4e370301c62addd69391eb0d481729ba1",
                CdR11aProfile.sha256(bank.tile(0x328).copy()));
        assertEquals("3408ac8644182dd294f69868c3d3c320bf35b5f084d83adc21d91c67166bd261",
                CdR11aProfile.sha256(bank.tile(0x333).copy()));
        for (int i = 0; i < 12; i++) { assertArrayEquals(binding.bytes().slice(i * 32, 32).copy(), bank.tile(0x328 + i).copy()); }
        for (var v : List.of(visual, vertical)) {
            for (var frame : v.mappingFrames()) {
                for (var piece : frame.pieces()) {
                    int first = (binding.baseWord() + piece.rawTileWord()) & 0x7FF;
                    assertTrue(first >= 0x328 && first + piece.tileCount() <= 0x334);
                    for (int i = 0; i < piece.tileCount(); i++) { assertEquals(32, bank.tile(first + i).length()); }
                }
            }
        }
        assertEquals(0x8328, objects.tileWord(0xC, 0));
        for (int subtype = 1; subtype <= 255; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(0xC, unsupported));
        }
        assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(0xD, 0));
        for (var other : CdR11aAsset.values()) {
            if (other != ART_TUNNEL_DOOR) { assertThrows(UnsupportedOperationException.class, () -> bank.binding(other)); }
        }
        for (int absent : new int[]{0, 0x327, 0x334, 0x7FF}) {
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(absent));
        }
        assertThrows(UnsupportedOperationException.class, bank.bindings()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(0x328).buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> scriptView.bytes().buffer().put((byte) 0));
        byte[] changed = mapView.bytes().copy(); changed[4] ^= 1;
        assertNotEquals(changed[4], mapView.bytes().copy()[4]);
        changed = bank.tile(0x328).copy(); changed[0] ^= 1;
        assertNotEquals(changed[0], bank.tile(0x328).copy()[0]);
        for (int i = 0; i < oldTables.size(); i++) {
            assertSame(oldViews.get(i), objects.view(oldTables.get(i)));
            assertEquals(oldHashes.get(i), CdR11aProfile.sha256(objects.view(oldTables.get(i)).bytes().copy()));
        }
        assertSame(initial, objects.initialArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), initial.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        // Reverse access order, on a separately authenticated owner, retains exactly the same per-owner bank.
        var reverseBank = bankFirst.tunnelDoorArtBank();
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertFalse(((Map<?, ?>) optional.get(bankFirst)).containsKey(TUNNEL_DOOR_MAPPINGS));
        assertFalse(((Map<?, ?>) optional.get(bankFirst)).containsKey(TUNNEL_DOOR_ANIMATIONS));
        bankFirst.tunnelDoorVertical();
        var reverseVisual = bankFirst.tunnelDoor();
        assertSame(reverseBank, bankFirst.tunnelDoorArtBank());
        assertArrayEquals(visual.mappings().copy(), reverseVisual.mappings().copy());
        assertArrayEquals(binding.bytes().copy(), reverseBank.binding(ART_TUNNEL_DOOR).bytes().copy());
    }

    /** Complete source-text includes at N=1ecc5f3a. Actual art stays in the authenticated source owner. */
    private static final byte[] PATA_BATA_SCRIPT_BYTES = hexBytes("0004000B070000010201FF030000010201FF");
    private static final byte[] PATA_BATA_NORMAL_BYTES = hexBytes(
            "00060011001C02F0070000F0F00700080002F80D0010F0080000180001F00F0019F0");
    private static final byte[] PATA_BATA_DAMAGED_BYTES = hexBytes(
            "00060011001C02F0070000F0F00700290002F80D0010F00800001800020000003100F00F0019F000");

    @ParameterizedTest(name = "{0} authenticated optional Pata-Bata assets and regional rejection")
    @EnumSource(CdReleaseVariant.class)
    void pataBataAuthenticatesActualUsaOwnerAndRetainsBothSharedScriptsAfterMediaClose(CdReleaseVariant variant)
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
        CdR11aAssets owner;
        CdR11aObjectAssets visualFirst = null, bankFirst = null;
        try (var source = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            owner = CdR11aAssetLoader.load(source, variant, CdStageKey.parse("R11A"));
            var image = source.mmd(CdNativeFile.R11A);
            assertEquals(variant == CdReleaseVariant.USA
                            ? "7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2"
                            : "241be9620fbad1229792fedd1604066ae3e7d943cb7cc27bc94a4a837452d9b6",
                    CdR11aProfile.sha256(image.readFile(0, image.fileBytes())));
            // Independently observed shared art does not authorize a regional opening-object owner.
            assertEquals("e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f",
                    CdR11aProfile.sha256(owner.view(CdR11aAsset.ART_PATA_BATA).bytes().copy()));
            if (variant != CdReleaseVariant.USA) {
                assertEquals("dfaa4722422a49aa4bf0d49eb11119b10ffb308a8fd7ad851074935491a5603e",
                        CdR11aProfile.sha256(image.readFile(0xB51C, 18)));
                assertEquals("1ba7f34eb4d74d7d8e10ec113bc09e1a364dc960c39b5acf44fb4e55c6c28f50",
                        CdR11aProfile.sha256(image.readFile(0xB52E, 34)));
                assertEquals("d35ba3b81691221b683c70a7f0c8febd85a81f9b0e4a1ffa28e85483bbb462ce",
                        CdR11aProfile.sha256(image.readFile(0xB550, 40)));
                assertFalse(Arrays.equals(PATA_BATA_SCRIPT_BYTES, image.readFile(0xB51C, 18)));
                assertFalse(Arrays.equals(PATA_BATA_NORMAL_BYTES, image.readFile(0xB52E, 34)));
                assertFalse(Arrays.equals(PATA_BATA_DAMAGED_BYTES, image.readFile(0xB550, 40)));
                assertThrows(UnsupportedOperationException.class, owner::openingObjects);
                assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.load(
                        owner.profile(), owner.identity(), image, owner.loadPlan()));
                var usa = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
                var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, usa.stage(), usa.file(), usa.domain(),
                        usa.expectedMmdSha256(), owner.identity().observedWholeDiscSha256());
                assertThrows(IOException.class, () -> CdR11aObjectAssets.load(usa, asserted, image, owner.loadPlan()));
            } else {
                visualFirst = owner.openingObjects();
                bankFirst = CdR11aObjectAssets.load(owner.profile(), owner.identity(), image, owner.loadPlan());
                verifyPataBataCold(visualFirst);
                verifyPataBataCold(bankFirst);
                verifyPataBataNativeReferences(image, owner);
                for (int offset : new int[]{0x3F28, 0xB41A, 0xB462, 0xB47E, 0xB480, 0xB492, 0xB494,
                        0xB502, 0xB504, 0xB51C, 0xB526, 0xB52D, 0xB52E, 0xB54F, 0xB550, 0xB576, 0xB577,
                        0xCF4A, 0xCF6E, 0xFC8A, 0x37378, 0x37640, 0x37641}) {
                    byte[] changed = image.readFile(0, image.fileBytes()); changed[offset] ^= 1;
                    assertThrows(IOException.class, () -> CdR11aObjectAssets.load(owner.profile(), owner.identity(),
                            CdMmdImage.parse(changed), owner.loadPlan()), "changed file offset " + offset);
                }
            }
        }
        assertEquals(variant, owner.identity().variant());
        assertThrows(UnsupportedOperationException.class, () -> owner.decoded(CdR11aAsset.ART_PATA_BATA));
        if (variant != CdReleaseVariant.USA) { return; }
        assertNotNull(visualFirst); assertNotNull(bankFirst);
        verifyPataBataRetained(owner, visualFirst, bankFirst);
    }

    private static void verifyPataBataCold(CdR11aObjectAssets objects) throws Exception {
        var views = CdR11aObjectAssets.class.getDeclaredField("views"); views.setAccessible(true);
        assertEquals(9, ((Map<?, ?>) views.get(objects)).size());
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertTrue(((Map<?, ?>) optional.get(objects)).isEmpty());
        for (String name : List.of("pataBataNormal", "pataBataDamaged", "pataBataArtBank")) {
            var field = CdR11aObjectAssets.class.getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(objects), "Pata-Bata assets must remain opt-in");
        }
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), objects.initialArtBank().bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(84, objects.initialArtBank().bindings().stream().mapToInt(CdR11aObjectAssets.ArtBinding::tileCount).sum());
    }

    private static void verifyPataBataNativeReferences(CdMmdImage image, CdR11aAssets owner) throws Exception {
        // ObjectIndex $3E, actual selector/test/branches and native PC-relative LEA operands, not source label numbers.
        assertEquals(0x20B41A, ByteBuffer.wrap(image.readFile(0x3F28, 4)).getInt());
        assertArrayEquals(hexBytes("70014EB90020CF264A2800286614"), image.readFile(0xB462, 14));
        assertArrayEquals(hexBytes("203CFFFF8000323CFE007403760043FA00AE6012"), image.readFile(0xB470, 20));
        assertArrayEquals(hexBytes("203CFFFFC000323CFF007404760143FA00BC"), image.readFile(0xB484, 18));
        assertArrayEquals(hexBytes("2140003031410036314200381143001C21490004"), image.readFile(0xB496, 20));
        for (int[] operand : new int[][]{{0xB47E, 0xB52E}, {0xB492, 0xB550}, {0xB502, 0xB51C}}) {
            var instruction = ByteBuffer.wrap(image.readFile(operand[0], 4));
            assertEquals((short) 0x43FA, instruction.getShort());
            assertEquals(operand[1], operand[0] + 2 + instruction.getShort());
        }
        assertArrayEquals(hexBytes("43FA00184EB900205CFA"), image.readFile(0xB502, 10));
        assertArrayEquals(hexBytes("43F90020CF48D040383B001845FB4014720012280029D2413A321000314500024E75"),
                image.readFile(0xCF26, 34));
        assertEquals(38, ByteBuffer.wrap(image.readFile(0xCF4A, 2)).getShort());
        assertEquals(0x23B0, Short.toUnsignedInt(ByteBuffer.wrap(image.readFile(0xCF6E, 2)).getShort()));
        byte[] actual = image.readFile(0, image.fileBytes());
        for (int i = 0; i < 3; i++) {
            var table = List.of(PATA_BATA_ANIMATIONS, PATA_BATA_NORMAL_MAPPINGS, PATA_BATA_DAMAGED_MAPPINGS).get(i);
            byte[] source = List.of(PATA_BATA_SCRIPT_BYTES, PATA_BATA_NORMAL_BYTES, PATA_BATA_DAMAGED_BYTES).get(i);
            assertArrayEquals(source, image.readFile(table.range().fileOffset(), table.range().length()));
            assertEquals(1, pataBataOccurrences(actual, source));
        }
        var art = owner.profile().range(CdR11aAsset.ART_PATA_BATA);
        assertEquals(0x37378, art.fileOffset()); assertEquals(714, art.length());
        assertEquals(1, pataBataOccurrences(actual, art.read(image).copy()));
        assertEquals(0x8032, art.read(image).unsignedWord(0));
        assertEquals(0, art.read(image).unsignedByte(713));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(image.readFile(0x37378, 713), 1600, 1600));
        var finite = CdNemesisAsset.decode(image.readFile(0x37378, 714), 1600, 1600);
        var extended = CdNemesisAsset.decode(image.readFile(0x37378, 715), 1600, 1600);
        assertEquals(714, finite.consumedInputBytes()); assertEquals(714, extended.consumedInputBytes());
        assertArrayEquals(finite.bytes().copy(), extended.bytes().copy());
        assertEquals("8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943",
                CdR11aProfile.sha256(finite.bytes().copy()));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(image.readFile(0x37378, 714), 49 * 32, 1600));
        assertThrows(IllegalArgumentException.class,
                () -> CdNemesisAsset.decode(image.readFile(0x37378, 714), 1600, 1599));
        // All native full/incremental Pata-Bata rows agree; the optional binding describes only ID 4 / row 2.
        for (int[] row : new int[][]{{4, 0xFC7C, 2, 0xFC8A}, {5, 0xFCA2, 3, 0xFCB6},
                {9, 0xFD4C, 0, 0xFD4E}, {10, 0xFD60, 2, 0xFD6E}}) {
            assertEquals(row[1], 0xFBC6 + ByteBuffer.wrap(image.readFile(0xFBC6 + row[0] * 2, 2)).getShort());
            assertArrayEquals(hexBytes("002373787600"), image.readFile(row[3], 6));
            var entry = owner.loadPlan().plcs().get(row[0]).entries().get(row[2]);
            assertEquals(CdR11aAsset.ART_PATA_BATA, entry.source());
            assertEquals(0x7600, entry.vramAddress()); assertEquals(art, entry.sourceRange());
        }
        var placements = owner.placements().records().stream().filter(p -> p.objectId() == 0x3E).toList();
        assertEquals(List.of(16,45,48,75,78,88,116,141,142,143,160,161,200,205,211,213,214,215,
                268,269,302,304,307,308,310,311,315,361,381,383,406,426,447,473,476),
                placements.stream().map(CdObjectPlacements.Placement::index).toList());
        assertEquals(17, placements.stream().filter(p -> p.subtype() == 0).count());
        assertEquals(18, placements.stream().filter(p -> p.subtype() == 255).count());
        for (var placement : placements) {
            assertEquals(0, placement.subtype2()); assertEquals(0xBE, placement.encodedId());
            assertEquals(0, image.readFile(0x7906 + placement.index() * 8 + 7, 1)[0]);
        }
        assertEquals("c5441a5a61b8c17cbac91d85a830f1560ca1c4e101df72e10326f0c7d9d71791",
                CdR11aProfile.sha256(image.readFile(0x7906, 3984)));
    }

    /** All first new visual/art requests occur after source closure; no reflection constructs an authenticated owner. */
    private static void verifyPataBataRetained(CdR11aAssets owner, CdR11aObjectAssets objects,
                                              CdR11aObjectAssets bankFirst) throws Exception {
        var initial = objects.initialArtBank();
        var oldTables = Arrays.stream(CdR11aObjectAssets.Table.values()).limit(36).toList();
        var oldViews = oldTables.stream().map(objects::view).toList();
        var oldHashes = oldViews.stream().map(v -> CdR11aProfile.sha256(v.bytes().copy())).toList();
        var frontier = objects.frontierArtBank(); var contact = objects.contactArtBank();
        var floating = objects.floatBlockArtBank(); var spring = objects.springArtBank();
        var monitor = objects.monitorTimeArtBank(); var door = objects.tunnelDoorArtBank();
        var cache = CdR11aObjectAssets.class.getDeclaredField("pataBataArtBank"); cache.setAccessible(true);
        assertNull(cache.get(objects));
        var normal = objects.pataBataNormal(); var damaged = objects.pataBataDamaged();
        assertNull(cache.get(objects), "visual requests must not decode optional art");
        assertSame(normal, objects.pataBataNormal()); assertSame(damaged, objects.pataBataDamaged());
        assertArrayEquals(PATA_BATA_NORMAL_BYTES, normal.mappings().copy());
        assertArrayEquals(PATA_BATA_DAMAGED_BYTES, damaged.mappings().copy());
        var scriptView = objects.view(PATA_BATA_ANIMATIONS);
        assertSame(scriptView, objects.view(PATA_BATA_ANIMATIONS));
        assertArrayEquals(PATA_BATA_SCRIPT_BYTES, scriptView.bytes().copy());
        for (var visual : List.of(normal, damaged)) {
            assertArrayEquals(PATA_BATA_SCRIPT_BYTES, visual.animationTable().copy());
            assertEquals(List.of(6,17,28), visual.mappingFrames().stream()
                    .map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
            assertEquals(List.of(7,3), visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
            for (var script : visual.animations()) {
                assertEquals(List.of(0,0,1,2,1), script.frameBytes());
                assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, script.terminal());
                assertEquals(7, script.bytes().length());
            }
            assertThrows(ReadOnlyBufferException.class, () -> visual.mappings().buffer().put((byte) 0));
        }
        assertEquals(List.of(2,2,1), normal.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(List.of(2,2,2), damaged.mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(0x29, damaged.mappingFrames().getFirst().pieces().getLast().rawTileWord());
        assertEquals(0x31, damaged.mappingFrames().getLast().pieces().getFirst().rawTileWord());
        assertEquals(0, damaged.mappings().unsignedByte(39));
        var bank = objects.pataBataArtBank(); var binding = bank.binding(CdR11aAsset.ART_PATA_BATA);
        assertSame(bank, objects.pataBataArtBank()); assertEquals(List.of(binding), bank.bindings());
        assertEquals(4, binding.plcId()); assertEquals(owner.loadPlan().plcs().get(4).entries().get(2), binding.plc());
        assertEquals(0x7600, binding.plc().vramAddress()); assertEquals(0x23B0, binding.baseWord());
        assertEquals(0x3B0, binding.firstTileId()); assertEquals(50, binding.tileCount());
        assertEquals(1600, binding.bytes().length()); assertEquals(714, binding.decode().consumedInputBytes());
        assertEquals(owner.profile().range(CdR11aAsset.ART_PATA_BATA), binding.decode().sourceBlobRange());
        assertEquals(binding.decode().sourceBlobRange(), binding.decode().readerInputRange());
        assertEquals("8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943",
                CdR11aProfile.sha256(binding.bytes().copy()));
        assertEquals("66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925",
                CdR11aProfile.sha256(bank.tile(0x3B0).copy()));
        assertEquals("182b9b7a4ff870e8c37df619f42d6def601ccfee20cd53a284cbaf30aff62f6e",
                CdR11aProfile.sha256(bank.tile(0x3E1).copy()));
        for (int tile = 0x3B0; tile < 0x3E2; tile++) {
            assertArrayEquals(binding.bytes().slice((tile - 0x3B0) * 32, 32).copy(), bank.tile(tile).copy());
        }
        var allUsed = new java.util.TreeSet<Integer>();
        for (var visual : List.of(normal, damaged)) {
            for (var frame : visual.mappingFrames()) {
                for (var piece : frame.pieces()) {
                    int word = binding.baseWord() + piece.rawTileWord(), first = word & 0x7FF;
                    assertEquals(0x2000, word & 0xF800);
                    assertTrue(first >= 0x3B0 && first + piece.tileCount() <= 0x3E2);
                    for (int i = 0; i < piece.tileCount(); i++) {
                        allUsed.add(first + i); assertEquals(32, bank.tile(first + i).length());
                    }
                }
            }
        }
        assertEquals(java.util.stream.IntStream.range(0x3B0, 0x3E2).boxed().toList(), List.copyOf(allUsed));
        assertEquals(0x23B0, objects.tileWord(1, 0));
        assertEquals(0x23A0, objects.tileWord(0, 0)); assertEquals(0x4334, objects.tileWord(5, 0));
        assertEquals(0x8328, objects.tileWord(0xC, 0));
        for (int subtype = 1; subtype <= 255; subtype++) {
            int unsupported = subtype;
            assertThrows(UnsupportedOperationException.class, () -> objects.tileWord(1, unsupported));
        }
        for (var other : CdR11aAsset.values()) {
            if (other != CdR11aAsset.ART_PATA_BATA) { assertThrows(UnsupportedOperationException.class, () -> bank.binding(other)); }
        }
        for (int tile : new int[]{0,0x3AF,0x3E2,0x7FF}) {
            assertThrows(UnsupportedOperationException.class, () -> bank.tile(tile));
        }
        for (int tile : new int[]{-1,0x800,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> bank.tile(tile));
        }
        assertThrows(UnsupportedOperationException.class, bank.bindings()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> bank.tile(0x3B0).buffer().put((byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> scriptView.bytes().buffer().put((byte) 0));
        byte[] changed = bank.tile(0x3B0).copy(); changed[0] ^= 1;
        assertNotEquals(changed[0], bank.tile(0x3B0).copy()[0]);
        for (int i = 0; i < oldTables.size(); i++) {
            assertSame(oldViews.get(i), objects.view(oldTables.get(i)));
            assertEquals(oldHashes.get(i), CdR11aProfile.sha256(objects.view(oldTables.get(i)).bytes().copy()));
        }
        assertSame(initial, objects.initialArtBank()); assertSame(frontier, objects.frontierArtBank());
        assertSame(contact, objects.contactArtBank()); assertSame(floating, objects.floatBlockArtBank());
        assertSame(spring, objects.springArtBank()); assertSame(monitor, objects.monitorTimeArtBank());
        assertSame(door, objects.tunnelDoorArtBank());
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), initial.bindings().stream().map(CdR11aObjectAssets.ArtBinding::source).toList());
        var reverseBank = bankFirst.pataBataArtBank();
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertTrue(((Map<?, ?>) optional.get(bankFirst)).isEmpty(), "art-first access must not read optional visual tables");
        assertArrayEquals(PATA_BATA_DAMAGED_BYTES, bankFirst.pataBataDamaged().mappings().copy());
        assertArrayEquals(PATA_BATA_NORMAL_BYTES, bankFirst.pataBataNormal().mappings().copy());
        assertSame(reverseBank, bankFirst.pataBataArtBank());
        assertArrayEquals(binding.bytes().copy(), reverseBank.binding(CdR11aAsset.ART_PATA_BATA).bytes().copy());
    }

    private static int pataBataOccurrences(byte[] haystack, byte[] needle) {
        int count = 0;
        for (int at = 0; at <= haystack.length - needle.length; at++) {
            boolean same = true;
            for (int i = 0; i < needle.length; i++) {
                if (haystack[at + i] != needle[i]) { same = false; break; }
            }
            if (same) { count++; }
        }
        return count;
    }


    private static final List<CdR11aAsset> SECTION_SOURCES = List.of(CdR11aAsset.ART_PATA_BATA,
            CdR11aAsset.ART_ANTON, CdR11aAsset.ART_TAMABBOH, ART_MOSQUI, ART_3D_PLANT, ART_3D_RAMP_BOOST, CdR11aAsset.ART_BOULDER);
    private static final int[] SECTION_TILES = {50, 22, 59, 70, 28, 63, 19};
    private static final int[] SECTION_JAVA_BYTES = {714, 484, 1142, 846, 393, 677, 270};
    private static final int[] SECTION_NATIVE_BYTES = {715, 485, 1143, 847, 393, 678, 271};
    private static final String[] SECTION_BLOB_SHA256 = {
            "e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f",
            "d51cfb7ef7c2bf4d6fe52da4042f09eae26130838ed503c3e64956607cce3d2a",
            "39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f",
            "b4ab1cf236c3fc6b4a1c44b7d5f2a04a3c4d9d200f50d08ad3f051a408966892",
            "a8fa99c79f800a90652ee82c20d96a0389cc48da733cb10021c91d1bf3adce96",
            "1fbee908474abae3f81f527d437fa6479a2c91902e0f65df78108de39e4a57c0",
            "6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f"};
    private static final String[] SECTION_NATIVE_SHA256 = {
            "a415ffa282fb1e90378a97f458f05e379a6d82d1a006751d1b85c6efcfc736e3",
            "e0e121da920926a86b5a445ba9df766931ee4cac91d4e611a3b30fcf5ebba556",
            "c90255c16ce2a453a6dad66e1b9ff641e12b2fc87e5b639429e1ef1962978b0e",
            "7696cc4d61b4f4607afcafdb693994cd65972efce4949f18a622dd630df8c4b5",
            "f7746c8d049390552d26e0db9617f02370e93aad8a0585cc6049c30969a89b6f",
            "1fbee908474abae3f81f527d437fa6479a2c91902e0f65df78108de39e4a57c0",
            "bf9f0cf2200b515b5f84c8daf70b4d78dcbcd92d432e2f305aebdc7aa0fb2f48"};
    private static final String[] SECTION_DECODED_SHA256 = {
            "8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943",
            "2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101",
            "aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95",
            "230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd",
            "a3a40dce6206cd71835f043eadee914a860724ec4361d1275a9535c7b2a1b164",
            "e52c4daa8995ac4365b3674cabf8bc81fc52db73d729e756dac757d79942b7a7",
            "abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d"};
    private static final String[] SECTION_FIRST_TILE_HEX = {
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000cc00c0cced0c6cce0d0eeee000d00000000d000000024",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000006000000006000000600000000600000000",
            "000000080000000800000008000000080008a0080008a0080008a0080088aa08",
            "99999999966777779677777797777777977777779777777797777777dfffffff",
            "0000000800000008000000080000006800000668000066680000666800007668"};
    private static final String[] SECTION_LAST_TILE_HEX = {
            "ccc888bfcccccaf0cc81c880cb11bfdcc119bfccc99bf00cccff000cccc000cd",
            "4388888028666688261666882611668828199689298888998299999090110000",
            "1111ede01111ede01111e7e01111e7e0e11ede00deedde00dddee000eee00000",
            "0089000000890000006900000069000000690000008900000089000000890000",
            "9999ab00009999a00000099a0000000000000000000000000000000000000000",
            "bbbbbbbbbbbbbbbb999999999999999999999999999999999999999911111111",
            "7777889977778899778888997888889978888899788888907888890000000000"};

    @ParameterizedTest(name = "{0} complete first camera PLC sources and native finite readers")
    @EnumSource(CdReleaseVariant.class)
    void sectionArtSourcesAuthenticateCompletePlc9AndRetainNativeReaderBytesAfterClose(CdReleaseVariant variant)
            throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media checks");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve(directory))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        CdR11aAssets owner;
        CdMmdImage retained;
        CdR11aObjectAssets objects = null, sourceFirst = null;
        try (var program = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            owner = CdR11aAssetLoader.load(program, variant, CdStageKey.parse("R11A"));
            retained = program.mmd(CdNativeFile.R11A);
            assertEquals(variant == CdReleaseVariant.USA
                            ? "7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2"
                            : "241be9620fbad1229792fedd1604066ae3e7d943cb7cc27bc94a4a837452d9b6",
                    CdR11aProfile.sha256(retained.readFile(0, retained.fileBytes())));
            for (int i = 0; i < SECTION_SOURCES.size(); i++) {
                assertEquals(SECTION_BLOB_SHA256[i], CdR11aProfile.sha256(owner.view(SECTION_SOURCES.get(i)).bytes().copy()));
            }
            if (variant != CdReleaseVariant.USA) {
                assertThrows(UnsupportedOperationException.class, owner::openingObjects);
                assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.load(
                        owner.profile(), owner.identity(), retained, owner.loadPlan()));
                var usa = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
                var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, usa.stage(), usa.file(), usa.domain(),
                        usa.expectedMmdSha256(), owner.identity().observedWholeDiscSha256());
                assertThrows(IOException.class, () -> CdR11aObjectAssets.load(usa, asserted, retained, owner.loadPlan()));
            } else {
                objects = owner.openingObjects(); sourceFirst = owner.openingObjects();
                verifySectionSourcesCold(objects); verifySectionSourcesCold(sourceFirst);
                verifySectionNativeRows(retained, owner);
                // Each finite-source tail and native adjacent refill byte is inside the authenticated MMD.
                for (int offset : new int[]{0x36E46,0x37029,0x3702A,0x37377,0x37378,0x37641,0x37642,
                        0x37950,0x37DC5,0x37DC6,0x3AC7A,0x3AE01,0x3AE02,0x3B0A7,0xFD4C,0xFD54,0xFD5A}) {
                    byte[] changed = retained.readFile(0, retained.fileBytes()); changed[offset] ^= 1;
                    assertThrows(IOException.class, () -> CdR11aObjectAssets.load(owner.profile(), owner.identity(),
                            CdMmdImage.parse(changed), owner.loadPlan()), "changed source/refill/PLC byte " + offset);
                }
            }
        }
        assertEquals(variant, owner.identity().variant());
        for (var asset : SECTION_SOURCES) { assertThrows(UnsupportedOperationException.class, () -> owner.decoded(asset)); }
        if (variant != CdReleaseVariant.USA) { return; }
        assertNotNull(objects); assertNotNull(sourceFirst);
        // The first request for either new source bank occurs only after the actual media owner is closed.
        verifySectionSourcesRetained(owner, retained, objects, sourceFirst);
    }

    private static void verifySectionSourcesCold(CdR11aObjectAssets objects) throws Exception {
        var eager = CdR11aObjectAssets.class.getDeclaredField("views"); eager.setAccessible(true);
        assertEquals(9, ((Map<?, ?>) eager.get(objects)).size());
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertTrue(((Map<?, ?>) optional.get(objects)).isEmpty());
        for (String name : List.of("sectionArtSources", "frontierArtBank", "contactArtBank", "floatBlockArtBank",
                "springArtBank", "monitorTimeArtBank", "tunnelDoorArtBank", "pataBataArtBank")) {
            var field = CdR11aObjectAssets.class.getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(objects), "cold optional cache " + name);
        }
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), objects.initialArtBank().bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(84, objects.initialArtBank().bindings().stream().mapToInt(CdR11aObjectAssets.ArtBinding::tileCount).sum());
    }

    private static void verifySectionNativeRows(CdMmdImage image, CdR11aAssets owner) {
        assertArrayEquals(hexBytes("000200237378760000236e468120002379508500"), image.readFile(0xFD4C, 20));
        var plc9 = owner.loadPlan().plcs().get(9);
        assertEquals(0xFD4C, plc9.bodyRange().fileOffset()); assertEquals(20, plc9.bodyRange().length());
        assertEquals(0xFD4C, 0xFBC6 + ByteBuffer.wrap(image.readFile(0xFBC6 + 18, 2)).getShort());
        assertEquals(SECTION_SOURCES.subList(0, 3), plc9.entries().stream().map(CdR11aLoadPlan.PlcEntry::source).toList());
        assertEquals(List.of(0x7600, 0x8120, 0x8500), plc9.entries().stream().map(CdR11aLoadPlan.PlcEntry::vramAddress).toList());
        assertEquals(9, owner.loadPlan().sections().getFirst().updatePlcId() + 1);
        assertEquals(9, owner.loadPlan().sections().get(1).updatePlcId());
        assertEquals(0x680, owner.loadPlan().sections().getFirst().rightThresholdWord());
        assertArrayEquals(hexBytes("00236e466e00"), image.readFile(0xFCDA + 2, 6));
        assertArrayEquals(hexBytes("00236e466e00"), image.readFile(0xFD8C + 2, 6));
    }

    private static void verifySectionSourcesRetained(CdR11aAssets owner, CdMmdImage image,
            CdR11aObjectAssets objects, CdR11aObjectAssets sourceFirst) throws Exception {
        var initial = objects.initialArtBank(); var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank(); var floating = objects.floatBlockArtBank();
        var spring = objects.springArtBank(); var monitor = objects.monitorTimeArtBank();
        var door = objects.tunnelDoorArtBank(); var pata = objects.pataBataArtBank();
        var oldBindings = new java.util.ArrayList<CdR11aObjectAssets.ArtBinding>();
        for (var list : List.of(initial.bindings(), frontier.bindings(), contact.bindings(), floating.bindings(),
                spring.bindings(), monitor.bindings(), door.bindings(), pata.bindings())) { oldBindings.addAll(list); }
        var oldDigests = oldBindings.stream().map(b -> CdR11aProfile.sha256(b.bytes().copy())).toList();
        var bank = objects.sectionArtSources(); assertSame(bank, objects.sectionArtSources());
        assertEquals(SECTION_SOURCES, bank.sources().stream().map(CdR11aObjectAssets.SectionArtSource::source).toList());
        int[] plcIds = {9,9,9,2,2,2,2}, rows = {0,1,2,2,4,5,0};
        int[] vram = {0x7600,0x8120,0x8500,0x7400,0x8480,0x8820,0x6E80};
        int[] headers = {0x8032,0x0016,0x803B,0x0046,0x801C,0x803F,0x8013};
        int[] lastNative = {0x80,0x00,0x00,0x80,0x80,0x00,0x80};
        for (int i = 0; i < SECTION_SOURCES.size(); i++) {
            var asset = SECTION_SOURCES.get(i); var source = bank.source(asset);
            assertSame(source, bank.sources().get(i)); assertEquals(asset, source.source());
            assertEquals(plcIds[i], source.canonicalPlcId()); assertEquals(rows[i], source.canonicalRowIndex());
            assertEquals(owner.loadPlan().plcs().get(plcIds[i]).entries().get(rows[i]), source.canonicalPlc());
            assertEquals(vram[i], source.canonicalPlc().vramAddress()); assertEquals(SECTION_TILES[i], source.tileCount());
            var blob = owner.profile().range(asset); var nativeRange = source.readerInputRange();
            assertEquals(blob, source.canonicalPlc().sourceRange()); assertEquals(blob, source.decode().sourceBlobRange());
            assertEquals(asset, source.decode().asset());
            assertEquals(blob.file(), nativeRange.file()); assertEquals(blob.domain(), nativeRange.domain());
            assertEquals(blob.fileOffset(), nativeRange.fileOffset()); assertEquals(SECTION_NATIVE_BYTES[i], nativeRange.length());
            assertEquals(SECTION_NATIVE_BYTES[i], source.readerBytes().length());
            assertEquals(SECTION_NATIVE_SHA256[i], CdR11aProfile.sha256(source.readerBytes().copy()));
            assertArrayEquals(nativeRange.read(image).copy(), source.readerBytes().copy());
            assertEquals(headers[i], source.readerBytes().unsignedWord(0));
            assertEquals(lastNative[i], source.readerBytes().unsignedByte(SECTION_NATIVE_BYTES[i] - 1));
            assertEquals(SECTION_JAVA_BYTES[i], source.decode().readerInputRange().length());
            assertEquals(SECTION_JAVA_BYTES[i], source.decode().consumedInputBytes());
            assertEquals(SECTION_TILES[i] * 32, source.decode().bytes().length());
            assertEquals(SECTION_DECODED_SHA256[i], CdR11aProfile.sha256(source.decode().bytes().copy()));
            assertArrayEquals(hexBytes(SECTION_FIRST_TILE_HEX[i]), source.decode().bytes().slice(0,32).copy());
            assertArrayEquals(hexBytes(SECTION_LAST_TILE_HEX[i]), source.decode().bytes().slice((SECTION_TILES[i] - 1)*32,32).copy());
            // Independent native prefill/post-symbol refill walk, including the otherwise unused final refill.
            var nativeWalk = walkNativeSectionSource(source.readerBytes().copy());
            assertEquals(SECTION_NATIVE_BYTES[i], nativeWalk.consumedInputBytes());
            assertArrayEquals(source.decode().bytes().copy(), nativeWalk.bytes());
            byte[] oneShort = source.readerBytes().slice(0, SECTION_NATIVE_BYTES[i] - 1).copy();
            assertThrows(IOException.class, () -> walkNativeSectionSource(oneShort));
            // Java has its own smaller sufficient extent; no claim that the native one-short must fail Java.
            byte[] javaInput = source.decode().readerInputRange().read(image).copy();
            var javaWalk = CdNemesisAsset.decode(javaInput, SECTION_TILES[i]*32, SECTION_TILES[i]*32);
            assertEquals(SECTION_JAVA_BYTES[i], javaWalk.consumedInputBytes());
            assertArrayEquals(source.decode().bytes().copy(), javaWalk.bytes().copy());
            int outputBytes = SECTION_TILES[i] * 32;
            assertThrows(IOException.class, () -> CdNemesisAsset.decode(Arrays.copyOf(javaInput, javaInput.length - 1),
                    outputBytes, outputBytes));
            assertThrows(ReadOnlyBufferException.class, () -> source.readerBytes().buffer().put((byte) 0));
            assertThrows(ReadOnlyBufferException.class, () -> source.decode().bytes().buffer().put((byte) 0));
            byte[] changed = source.readerBytes().copy(); changed[0] ^= 1;
            assertNotEquals(changed[0], source.readerBytes().copy()[0]);
            var wrong = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset() + 1, blob.length());
            assertThrows(IllegalArgumentException.class,
                    () -> bank.source(new CdR11aLoadPlan.PlcEntry(asset, wrong, source.canonicalPlc().vramAddress())));
        }
        assertSame(initial.binding(ART_MOSQUI).decode(), bank.source(ART_MOSQUI).decode());
        assertSame(frontier.binding(ART_3D_PLANT).decode(), bank.source(ART_3D_PLANT).decode());
        assertSame(frontier.binding(ART_3D_RAMP_BOOST).decode(), bank.source(ART_3D_RAMP_BOOST).decode());
        assertSame(pata.binding(CdR11aAsset.ART_PATA_BATA).decode(), bank.source(CdR11aAsset.ART_PATA_BATA).decode());
        for (var list : owner.loadPlan().plcs()) {
            for (var entry : list.entries()) {
                if (SECTION_SOURCES.contains(entry.source())) { assertSame(bank.source(entry.source()), bank.source(entry)); }
            }
        }
        // Actual native relocation is admitted solely by source/range; canonical PLC9 metadata does not select runtime destination.
        for (int plc : new int[]{6,11}) {
            var relocated = owner.loadPlan().plcs().get(plc).entries().getFirst();
            assertEquals(CdR11aAsset.ART_ANTON, relocated.source()); assertEquals(0x6E00, relocated.vramAddress());
            assertSame(bank.source(CdR11aAsset.ART_ANTON), bank.source(relocated));
            assertEquals(0x8120, bank.source(relocated).canonicalPlc().vramAddress());
        }
        for (var asset : CdR11aAsset.values()) {
            if (!SECTION_SOURCES.contains(asset)) { assertThrows(UnsupportedOperationException.class, () -> bank.source(asset)); }
        }
        assertThrows(NullPointerException.class, () -> bank.source((CdR11aAsset) null));
        assertThrows(NullPointerException.class, () -> bank.source((CdR11aLoadPlan.PlcEntry) null));
        assertThrows(UnsupportedOperationException.class, bank.sources()::clear);
        assertSame(initial, objects.initialArtBank()); assertSame(frontier, objects.frontierArtBank());
        assertSame(contact, objects.contactArtBank()); assertSame(floating, objects.floatBlockArtBank());
        assertSame(spring, objects.springArtBank()); assertSame(monitor, objects.monitorTimeArtBank());
        assertSame(door, objects.tunnelDoorArtBank()); assertSame(pata, objects.pataBataArtBank());
        assertEquals(oldDigests, oldBindings.stream().map(b -> CdR11aProfile.sha256(b.bytes().copy())).toList());
        assertEquals(List.of(ART_RINGS,ART_MOSQUI), initial.bindings().stream().map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(List.of(ART_ANIMALS,ART_3D_PLANT,ART_3D_RAMP_BOOST), frontier.bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        var reverse = sourceFirst.sectionArtSources(); assertSame(reverse, sourceFirst.sectionArtSources());
        for (var asset : SECTION_SOURCES) {
            assertArrayEquals(bank.source(asset).readerBytes().copy(), reverse.source(asset).readerBytes().copy());
            assertArrayEquals(bank.source(asset).decode().bytes().copy(), reverse.source(asset).decode().bytes().copy());
        }
        assertSame(sourceFirst.initialArtBank().binding(ART_MOSQUI).decode(), reverse.source(ART_MOSQUI).decode());
        assertSame(sourceFirst.pataBataArtBank().binding(CdR11aAsset.ART_PATA_BATA).decode(),
                reverse.source(CdR11aAsset.ART_PATA_BATA).decode());
        for (String name : List.of("pataBataNormal","pataBataDamaged","plant","rampBooster","groundAnimal","flyingAnimal")) {
            var field = CdR11aObjectAssets.class.getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(sourceFirst), "source-only access must not decode visual " + name);
        }
    }

    private record NativeSectionWalk(byte[] bytes, int consumedInputBytes) { }

    /**
     * Test-only independent bit-position walker of N NemDecMain/NemDec_ProcessCompressedData/InlineData
     * (Functions (General).asm:401-490). The 16-bit native prefill and <9-bit refills occur before output,
     * including on the final symbol. It does not call the production Nemesis reader or construct an owner.
     */
    private static NativeSectionWalk walkNativeSectionSource(byte[] input) throws IOException {
        if (input.length < 3) { throw new IOException("short header"); }
        int header = (Byte.toUnsignedInt(input[0]) << 8) | Byte.toUnsignedInt(input[1]);
        int count = (header & 0x7FFF) * 64, cursor = 2, palette = 0;
        int[] lengths = new int[256], values = new int[256];
        while (true) {
            if (cursor >= input.length) { throw new IOException("short table"); }
            int control = Byte.toUnsignedInt(input[cursor++]);
            if (control == 0xFF) { break; }
            if ((control & 0x80) != 0) { palette = control & 15; continue; }
            int length = control & 15;
            if (length < 1 || length > 8 || cursor >= input.length) { throw new IOException("invalid table"); }
            int code = Byte.toUnsignedInt(input[cursor++]);
            for (int i = 0; i < (1 << (8 - length)); i++) {
                int index = ((code << (8 - length)) + i) & 255;
                lengths[index] = length; values[index] = (control & 0x70) | palette;
            }
        }
        int payload = cursor, bits = 0, consumed = payload + 2, at = 0;
        if (consumed > input.length) { throw new IOException("short native prefill"); }
        byte[] output = new byte[count / 2];
        while (at < count) {
            int prefix = sectionSourceBits(input, payload, bits, 8), value;
            if ((prefix & 0xFC) == 0xFC) {
                bits += 6;
                consumed = Math.max(consumed, payload + bits / 8 + 2);
                if (consumed > input.length) { throw new IOException("short inline refill"); }
                value = sectionSourceBits(input, payload, bits, 7); bits += 7;
            } else {
                if (lengths[prefix] == 0) { throw new IOException("invalid prefix"); }
                value = values[prefix]; bits += lengths[prefix];
            }
            consumed = Math.max(consumed, payload + bits / 8 + 2);
            if (consumed > input.length) { throw new IOException("short native post-symbol refill"); }
            int run = (value >>> 4) + 1;
            if (at + run > count) { throw new IOException("run exceeds output"); }
            for (int i = 0; i < run; i++, at++) {
                if ((at & 1) == 0) { output[at / 2] = (byte) ((value & 15) << 4); }
                else { output[at / 2] |= (byte) (value & 15); }
            }
        }
        if ((header & 0x8000) != 0) {
            for (int i = 4; i < output.length; i++) { output[i] ^= output[i - 4]; }
        }
        return new NativeSectionWalk(output, consumed);
    }

    private static int sectionSourceBits(byte[] input, int payload, int bit, int count) throws IOException {
        int value = 0;
        for (int i = 0; i < count; i++) {
            int offset = payload + (bit + i) / 8;
            if (offset >= input.length) { throw new IOException("short payload"); }
            value = (value << 1) | ((input[offset] >>> (7 - (bit + i) % 8)) & 1);
        }
        return value;
    }

    private static final String BOULDER_MAPPING_SHA256 =
            "f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652";
    /** Source-literal mapping plus real EVEN, joined to the actual USA MOVE.L operand. */
    private static final byte[] BOULDER_MAPPING_BYTES = hexBytes("000202f8020000ecf00f0003f400");

    @ParameterizedTest(name = "{0} authenticated lazy Boulder mappings and seventh section-art source")
    @EnumSource(CdReleaseVariant.class)
    void boulderAuthenticatesActualOperandAndFiniteReadersThenLoadsLazilyAfterClose(CdReleaseVariant variant)
            throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for supplied-media checks");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve(directory))) {
            var cues = files.filter(v -> v.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        CdR11aAssets owner;
        CdMmdImage retained;
        CdR11aObjectAssets visualFirst = null, sourceFirst = null;
        try (var program = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            owner = CdR11aAssetLoader.load(program, variant, CdStageKey.parse("R11A"));
            retained = program.mmd(CdNativeFile.R11A);
            assertEquals(262144, retained.fileBytes());
            assertEquals(variant == CdReleaseVariant.USA
                            ? "7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2"
                            : "241be9620fbad1229792fedd1604066ae3e7d943cb7cc27bc94a4a837452d9b6",
                    CdR11aProfile.sha256(retained.readFile(0, retained.fileBytes())));
            // Shared art is observed independently and cannot authorize a regional object owner.
            assertEquals(SECTION_BLOB_SHA256[6], CdR11aProfile.sha256(owner.view(CdR11aAsset.ART_BOULDER).bytes().copy()));
            if (variant != CdReleaseVariant.USA) {
                assertArrayEquals(hexBytes("217c002096620004"), retained.readFile(0x9626, 8));
                assertArrayEquals(BOULDER_MAPPING_BYTES, retained.readFile(0x9662, 14));
                assertFalse(Arrays.equals(BOULDER_MAPPING_BYTES, retained.readFile(0x9654, 14)));
                assertThrows(UnsupportedOperationException.class, owner::openingObjects);
                assertThrows(UnsupportedOperationException.class, () -> CdR11aObjectAssets.load(
                        owner.profile(), owner.identity(), retained, owner.loadPlan()));
                var usa = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
                var asserted = new CdR11aAssets.Identity(CdReleaseVariant.USA, usa.stage(), usa.file(), usa.domain(),
                        usa.expectedMmdSha256(), owner.identity().observedWholeDiscSha256());
                assertThrows(IOException.class, () -> CdR11aObjectAssets.load(usa, asserted, retained, owner.loadPlan()));
            } else {
                visualFirst = owner.openingObjects(); sourceFirst = owner.openingObjects();
                verifyBoulderCold(visualFirst); verifyBoulderCold(sourceFirst);
                verifyBoulderNativeReferences(retained, owner);
                for (int offset : new int[]{0x3E9C,0x961A,0x961D,0x9654,0x9660,0x9661,
                        0xCF5E,0xCF84,0xFC52,0xFC56,0x364C2,0x365CE,0x365CF,0x365D0}) {
                    byte[] changed = retained.readFile(0, retained.fileBytes()); changed[offset] ^= 1;
                    assertThrows(IOException.class, () -> CdR11aObjectAssets.load(owner.profile(), owner.identity(),
                            CdMmdImage.parse(changed), owner.loadPlan()), "changed Boulder authentication byte " + offset);
                }
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> owner.decoded(CdR11aAsset.ART_BOULDER));
        if (variant != CdReleaseVariant.USA) { return; }
        assertNotNull(visualFirst); assertNotNull(sourceFirst);
        // Both first requests occur after media closure; visual-first and source-first orders share one owner cache.
        var visual = visualFirst.boulder(); assertSame(visual, visualFirst.boulder());
        var sourceCache = CdR11aObjectAssets.class.getDeclaredField("sectionArtSources"); sourceCache.setAccessible(true);
        assertNull(sourceCache.get(visualFirst), "mapping access cannot decode section art");
        var bank = visualFirst.sectionArtSources();
        var reverse = sourceFirst.sectionArtSources();
        var visualCache = CdR11aObjectAssets.class.getDeclaredField("boulder"); visualCache.setAccessible(true);
        assertNull(visualCache.get(sourceFirst), "source access cannot decode the visual");
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertFalse(((Map<?, ?>) optional.get(sourceFirst)).containsKey(BOULDER_MAPPINGS));
        assertSame(visual, visualFirst.boulder());
        assertArrayEquals(visual.mappings().copy(), sourceFirst.boulder().mappings().copy());
        assertSame(bank, visualFirst.sectionArtSources()); assertSame(reverse, sourceFirst.sectionArtSources());
        assertEquals(SECTION_SOURCES, bank.sources().stream().map(CdR11aObjectAssets.SectionArtSource::source).toList());
        var source = bank.source(CdR11aAsset.ART_BOULDER);
        assertSame(source, bank.sources().get(6)); assertSame(source.decode(), bank.source(CdR11aAsset.ART_BOULDER).decode());
        assertEquals(2, source.canonicalPlcId()); assertEquals(0, source.canonicalRowIndex());
        assertEquals(owner.loadPlan().plcs().get(2).entries().getFirst(), source.canonicalPlc());
        assertEquals(0x6E80, source.canonicalPlc().vramAddress()); assertEquals(19, source.tileCount());
        assertEquals(0x0374, visualFirst.tileWord(0xB, 0));
        assertArrayEquals(BOULDER_MAPPING_BYTES, visual.mappings().copy());
        assertEquals(0, visual.mappingTableOffset()); assertEquals(1, visual.mappingFrames().size());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-8, 2, 0, -20),
                new CdObjectVisualAssets.Piece(-16, 15, 3, -12)), visual.mappingFrames().getFirst().pieces());
        assertTrue(visual.animations().isEmpty()); assertEquals(0, visual.animationTable().length());
        assertSame(visualFirst.view(BOULDER_MAPPINGS), visualFirst.view(BOULDER_MAPPINGS));
        assertEquals(BOULDER_MAPPING_SHA256, CdR11aProfile.sha256(visualFirst.view(BOULDER_MAPPINGS).bytes().copy()));
        var blob = owner.profile().range(CdR11aAsset.ART_BOULDER);
        assertEquals(blob, source.decode().sourceBlobRange()); assertEquals(blob, source.decode().readerInputRange());
        assertEquals(270, source.decode().consumedInputBytes()); assertEquals(608, source.decode().bytes().length());
        assertEquals(SECTION_BLOB_SHA256[6], CdR11aProfile.sha256(blob.read(retained).copy()));
        assertEquals(SECTION_DECODED_SHA256[6], CdR11aProfile.sha256(source.decode().bytes().copy()));
        assertEquals(new CdR11aProfile.FileRange(blob.file(), blob.domain(), 0x364C2, 271), source.readerInputRange());
        assertEquals(0x8013, source.readerBytes().unsignedWord(0)); assertEquals(0x80, source.readerBytes().unsignedByte(270));
        assertEquals(retained.readFile(0x365D0, 1)[0], source.readerBytes().copy()[270]);
        assertEquals(SECTION_NATIVE_SHA256[6], CdR11aProfile.sha256(source.readerBytes().copy()));
        var javaWalk = CdNemesisAsset.decode(blob.read(retained).copy(), 608, 608);
        assertEquals(270, javaWalk.consumedInputBytes()); assertArrayEquals(source.decode().bytes().copy(), javaWalk.bytes().copy());
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(blob.read(retained).slice(0, 269).copy(), 608, 608));
        var nativeWalk = walkNativeSectionSource(source.readerBytes().copy());
        assertEquals(271, nativeWalk.consumedInputBytes()); assertArrayEquals(source.decode().bytes().copy(), nativeWalk.bytes());
        var shortNative = assertThrows(IOException.class, () -> walkNativeSectionSource(blob.read(retained).copy()));
        assertEquals("short native post-symbol refill", shortNative.getMessage());
        assertArrayEquals(source.readerBytes().copy(), reverse.source(CdR11aAsset.ART_BOULDER).readerBytes().copy());
        assertArrayEquals(source.decode().bytes().copy(), reverse.source(CdR11aAsset.ART_BOULDER).decode().bytes().copy());
        for (int plc : new int[]{2,4,5,8,10,13,14,15,17}) {
            var entry = owner.loadPlan().plcs().get(plc).entries().getFirst();
            assertEquals(CdR11aAsset.ART_BOULDER, entry.source()); assertSame(source, bank.source(entry));
            assertSame(source.decode(), bank.source(entry).decode());
        }
        // Constructed relocation: the source/range selects provenance independently of the canonical destination.
        var relocated = new CdR11aLoadPlan.PlcEntry(CdR11aAsset.ART_BOULDER, blob, 0x6E00);
        assertSame(source, bank.source(relocated)); assertEquals(0x6E80, source.canonicalPlc().vramAddress());
        for (var wrong : List.of(
                new CdR11aProfile.FileRange(blob.file(), blob.domain(), 0x364C3, 270),
                new CdR11aProfile.FileRange(blob.file(), blob.domain(), 0x364C2, 269),
                source.readerInputRange())) {
            assertThrows(IllegalArgumentException.class, () -> bank.source(
                    new CdR11aLoadPlan.PlcEntry(CdR11aAsset.ART_BOULDER, wrong, 0x6E00)));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdR11aProfile.FileRange(
                blob.file(), CdMmdImage.Domain.RELOCATED_COPY, 0x364C2, 270));
        assertThrows(UnsupportedOperationException.class, () -> bank.source(CdR11aAsset.ART_ANIMALS));
        assertSame(visualFirst.initialArtBank().binding(ART_MOSQUI).decode(), bank.source(ART_MOSQUI).decode());
        assertSame(visualFirst.pataBataArtBank().binding(CdR11aAsset.ART_PATA_BATA).decode(),
                bank.source(CdR11aAsset.ART_PATA_BATA).decode());
        assertThrows(UnsupportedOperationException.class, bank.sources()::clear);
        assertThrows(ReadOnlyBufferException.class, () -> source.readerBytes().buffer().put((byte) 1));
        assertThrows(ReadOnlyBufferException.class, () -> source.decode().bytes().buffer().put((byte) 1));
        byte[] changed = source.readerBytes().copy(); changed[270] = 0;
        assertEquals(0x80, source.readerBytes().unsignedByte(270));
    }

    private static void verifyBoulderCold(CdR11aObjectAssets objects) throws Exception {
        var views = CdR11aObjectAssets.class.getDeclaredField("views"); views.setAccessible(true);
        assertEquals(9, ((Map<?, ?>) views.get(objects)).size());
        var optional = CdR11aObjectAssets.class.getDeclaredField("frontierViews"); optional.setAccessible(true);
        assertTrue(((Map<?, ?>) optional.get(objects)).isEmpty());
        for (String name : List.of("boulder", "sectionArtSources", "frontierArtBank", "pataBataArtBank")) {
            var field = CdR11aObjectAssets.class.getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(objects), "cold optional Boulder access " + name);
        }
        assertEquals(List.of(ART_RINGS, ART_MOSQUI), objects.initialArtBank().bindings().stream()
                .map(CdR11aObjectAssets.ArtBinding::source).toList());
        assertEquals(84, objects.initialArtBank().bindings().stream().mapToInt(CdR11aObjectAssets.ArtBinding::tileCount).sum());
    }

    private static void verifyBoulderNativeReferences(CdMmdImage image, CdR11aAssets owner) {
        // ObjectIndex ID1B -> ObjBoulder; Init MOVE.L stores the actual absolute pointer into oMap.
        assertEquals(0x2095EA, ByteBuffer.wrap(image.readFile(0x3E9C, 4)).getInt());
        assertArrayEquals(hexBytes("217c002096540004"), image.readFile(0x9618, 8));
        assertEquals(BOULDER_MAPPINGS.range().nativeAddress(), ByteBuffer.wrap(image.readFile(0x961A, 4)).getInt());
        assertArrayEquals(BOULDER_MAPPING_BYTES, image.readFile(BOULDER_MAPPINGS.range().fileOffset(), 14));
        assertArrayEquals(hexBytes("700b4eb90020cf26"), image.readFile(0x9632, 8));
        assertEquals(60, ByteBuffer.wrap(image.readFile(0xCF5E, 2)).getShort());
        assertEquals(0x0374, Short.toUnsignedInt(ByteBuffer.wrap(image.readFile(0xCF84, 2)).getShort()));
        int[] ids = {2,4,5,8,10,13,14,15,17};
        int[] bodies = {0xFC50,0xFC7C,0xFCA2,0xFD26,0xFD60,0xFC50,0xFC50,0xFC50,0xFC50};
        for (int i = 0; i < ids.length; i++) {
            int target = 0xFBC6 + ByteBuffer.wrap(image.readFile(0xFBC6 + ids[i] * 2, 2)).getShort();
            assertEquals(bodies[i], target);
            assertEquals(target, owner.loadPlan().plcs().get(ids[i]).bodyRange().fileOffset());
            assertArrayEquals(hexBytes("002364c26e80"), image.readFile(target + 2, 6));
            var entry = owner.loadPlan().plcs().get(ids[i]).entries().getFirst();
            assertEquals(CdR11aAsset.ART_BOULDER, entry.source());
            assertEquals(owner.profile().range(CdR11aAsset.ART_BOULDER), entry.sourceRange());
            assertEquals(0x6E80, entry.vramAddress());
        }
        assertTrue(owner.loadPlan().plcs().get(9).entries().stream().noneMatch(v -> v.source() == CdR11aAsset.ART_BOULDER));
    }

    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
}
