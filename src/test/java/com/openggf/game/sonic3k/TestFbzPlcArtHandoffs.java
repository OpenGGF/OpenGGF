package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.data.RomByteReader;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.resources.CompressionType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TestFbzPlcArtHandoffs {
    @Test void actPlansPublishNativeStartArtButWithholdThePostCapsuleExitConsumers() {
        Set<String> act1 = keys(Sonic3kPlcArtRegistry.getPlan(4, 0));
        Set<String> act2 = keys(Sonic3kPlcArtRegistry.getPlan(4, 1));

        assertTrue(act1.containsAll(Set.of(
                Sonic3kObjectArtKeys.FBZ_MINIBOSS,
                Sonic3kObjectArtKeys.FBZ_EGG_CAPSULE)));
        assertTrue(act2.containsAll(Set.of(
                Sonic3kObjectArtKeys.FBZ2_SUBBOSS,
                Sonic3kObjectArtKeys.FBZ_ROBOTNIK_STAND,
                Sonic3kObjectArtKeys.FBZ_ROBOTNIK_RUN,
                Sonic3kObjectArtKeys.FBZ_EGGROBO_STAND,
                Sonic3kObjectArtKeys.FBZ_EGGROBO_RUN,
                Sonic3kObjectArtKeys.FBZ_CLOUD,
                Sonic3kObjectArtKeys.FBZ_BOSS_PILLAR,
                Sonic3kObjectArtKeys.FBZ_END_BOSS,
                Sonic3kObjectArtKeys.FBZ_ROBOTNIK_HEAD,
                Sonic3kObjectArtKeys.FBZ_END_BOSS_FLAME,
                Sonic3kObjectArtKeys.ROBOTNIK_SHIP,
                ObjectArtKeys.BOSS_EXPLOSION,
                Sonic3kObjectArtKeys.EGG_CAPSULE,
                Sonic3kObjectArtKeys.FBZ_EGG_CAPSULE)));
        assertFalse(act2.contains(Sonic3kObjectArtKeys.FBZ_EXIT_DOOR));
        assertFalse(act2.contains(Sonic3kObjectArtKeys.FBZ_EXIT_HALL_DOOR_SCENERY));
        assertFalse(act2.contains(Sonic3kObjectArtKeys.FBZ_EXIT_HALL));
    }

    @Test void handoffPlcIdsAndOrderMatchLockedOnRom() {
        assertEquals(0x1A, Sonic3kPlcLoader.fbzLevelPlcIds(0)[0]);
        assertEquals(0x1B, Sonic3kPlcLoader.fbzLevelPlcIds(0)[1]);
        assertEquals(0x1C, Sonic3kPlcLoader.fbzLevelPlcIds(1)[0]);
        assertEquals(0x1D, Sonic3kPlcLoader.fbzLevelPlcIds(1)[1]);
        assertEquals(0x6F, Sonic3kPlcLoader.fbzEndBossPlcId());
        assertEquals(java.util.List.of(
                        new Sonic3kPlcLoader.RawPlcEntry(Sonic3kConstants.ARTTILE_MONITORS,
                                Sonic3kConstants.ART_NEM_MONITORS_ADDR)),
                Sonic3kPlcLoader.monitorPlcEntries());
        assertEquals(java.util.List.of(
                        new Sonic3kPlcLoader.RawPlcEntry(Sonic3kConstants.ARTTILE_MONITORS,
                                Sonic3kConstants.ART_NEM_MONITORS_ADDR),
                        new Sonic3kPlcLoader.RawPlcEntry(Sonic3kConstants.ARTTILE_SPIKES_SPRINGS,
                                Sonic3kConstants.ART_NEM_SPIKES_SPRINGS_ADDR)),
                Sonic3kPlcLoader.monitorSpikesSpringsPlcEntries());
    }

    @Test void lockedOnBossDependencySourcesMappingsTilesAndSizesAreExact() {
        var plan = Sonic3kPlcArtRegistry.getPlan(4, 1);
        assertStandalone(plan, Sonic3kObjectArtKeys.FBZ_ROBOTNIK_STAND,
                0x0D7EEC, 0x06847C, 2144);
        assertEquals(0x466, Sonic3kConstants.ART_TILE_FBZ_ROBOTNIK_STAND);
        assertStandalone(plan, Sonic3kObjectArtKeys.FBZ_ROBOTNIK_RUN,
                0x0D8302, 0x06837E, 2784);
        assertEquals(0x4A9, Sonic3kConstants.ART_TILE_FBZ_ROBOTNIK_RUN);
        assertStandalone(plan, Sonic3kObjectArtKeys.FBZ_EGGROBO_STAND,
                0x160340, 0x186BB0, 1984);
        assertStandalone(plan, Sonic3kObjectArtKeys.FBZ_EGGROBO_RUN,
                0x15FFBE, 0x186C20, 2208);
        assertStandalone(plan, Sonic3kObjectArtKeys.FBZ_ROBOTNIK_HEAD,
                0x0D7C7A, 0x068454, 1024);
        assertEquals(0x430, Sonic3kConstants.ART_TILE_FBZ_ROBOTNIK_HEAD);
        assertStandalone(plan, Sonic3kObjectArtKeys.FBZ_END_BOSS_FLAME,
                0x0DDFE6, 0x071090, 2176);
        assertEquals(0x450, Sonic3kConstants.ART_TILE_FBZ_END_BOSS_FLAME);
        assertStandalone(plan, Sonic3kObjectArtKeys.FBZ2_SUBBOSS,
                0x0DBDDE, 0x070440, 2368);
        assertStandalone(plan, Sonic3kObjectArtKeys.FBZ_END_BOSS,
                0x0DC2E0, 0x070FB4, 1536);
        assertStandalone(plan, Sonic3kObjectArtKeys.ROBOTNIK_SHIP,
                0x0D771E, 0x06820C, 2624);
        assertStandalone(plan, ObjectArtKeys.BOSS_EXPLOSION,
                0x0D73CE, 0x083FFC, 1472);
        assertStandalone(plan, Sonic3kObjectArtKeys.EGG_CAPSULE,
                0x0DD990, 0x086BFC, 2240);

        long distinctKeys = plan.standaloneArt().stream().map(e -> e.key()).distinct().count();
        assertEquals(distinctKeys, plan.standaloneArt().size(), "shared PLC dependencies must not be duplicated");
    }

    @Test void sharedPrebossMappingIsSplitByExactRomConsumerFrames() {
        var plan = Sonic3kPlcArtRegistry.getPlan(4, 1);
        var pillar = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_BOSS_PILLAR)).findFirst().orElseThrow();
        var cloud = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_CLOUD)).findFirst().orElseThrow();

        assertEquals(0x3D5, pillar.artTileBase());
        assertArrayEquals(new int[]{0}, pillar.frameFilter());
        assertEquals(0x3A3, cloud.artTileBase());
        assertArrayEquals(new int[]{1, 2, 3}, cloud.frameFilter());
    }

    @Test void threeExitConsumersUseTheirNativeMappingsTileBasesAndFrameCounts() {
        var entries = Sonic3kPlcArtRegistry.fbzExitLevelArtEntries();
        var door = entries.stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_EXIT_DOOR)).findFirst().orElseThrow();
        var doorScenery = entries.stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_EXIT_HALL_DOOR_SCENERY))
                .findFirst().orElseThrow();
        var hall = entries.stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_EXIT_HALL)).findFirst().orElseThrow();

        assertEquals(Sonic3kConstants.MAP_FBZ_EXIT_DOOR_ADDR,door.mappingAddr());
        assertEquals(Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR,door.artTileBase());
        assertEquals(1,door.mappingFrameCount());
        assertEquals(Sonic3kConstants.MAP_FBZ_EXIT_HALL_ADDR,doorScenery.mappingAddr());
        assertEquals(Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR,doorScenery.artTileBase());
        assertEquals(2,doorScenery.mappingFrameCount());
        assertEquals(Sonic3kConstants.MAP_FBZ_EXIT_HALL_ADDR,hall.mappingAddr());
        assertEquals(Sonic3kConstants.ART_TILE_FBZ_EXIT_HALL,hall.artTileBase());
        assertEquals(2,hall.mappingFrameCount());
    }

    @Test void exitMappingPiecesSelectTheExactNativeFramesAndEffectiveTileWindows() {
        List<SpriteMappingFrame> hallMappings = S3kSpriteDataLoader.loadMappingFrames(
                new RomByteReader(bytes(
                        0x00,0x04, 0x00,0x12,
                        0x00,0x02,
                        0xE0,0x07,0x00,0x07,0xFF,0xF8,
                        0x00,0x07,0x10,0x07,0xFF,0xF8,
                        0x00,0x02,
                        0xE8,0x06,0x00,0x00,0xFF,0xF8,
                        0x00,0x06,0x00,0x06,0xFF,0xF8)), 0, 2);
        List<SpriteMappingFrame> doorMappings = S3kSpriteDataLoader.loadMappingFrames(
                new RomByteReader(bytes(
                        0x00,0x02,
                        0x00,0x03,
                        0xE0,0x02,0x08,0x00,0xFF,0xF8,
                        0xF8,0x05,0x08,0x03,0xFF,0xF8,
                        0x08,0x02,0x18,0x00,0xFF,0xF8)), 0, 1);

        assertEquals(List.of(
                new SpriteMappingPiece(-8,-32,2,4,7,false,false,0,false),
                new SpriteMappingPiece(-8,0,2,4,7,false,true,0,false)),
                hallMappings.get(0).pieces(), "$8A subtype $00 selects Map_FBZExitHall frame 0");
        assertEquals(List.of(
                new SpriteMappingPiece(-8,-24,2,3,0,false,false,0,false),
                new SpriteMappingPiece(-8,0,2,3,6,false,false,0,false)),
                hallMappings.get(1).pieces(), "$8A subtype $04 selects Map_FBZExitHall frame 1");
        assertEquals(List.of(
                new SpriteMappingPiece(-8,-32,1,3,0,true,false,0,false),
                new SpriteMappingPiece(-8,-8,2,2,3,true,false,0,false),
                new SpriteMappingPiece(-8,8,1,3,0,true,true,0,false)),
                doorMappings.get(0).pieces(), "$CE selects dedicated Map_FBZExitDoor frame 0");

        assertEquals(Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR + 7,
                effectiveFirstTile(Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR,hallMappings.get(0)));
        assertEquals(Sonic3kConstants.ART_TILE_FBZ_EXIT_HALL,
                effectiveFirstTile(Sonic3kConstants.ART_TILE_FBZ_EXIT_HALL,hallMappings.get(1)));
        assertEquals(Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR,
                effectiveFirstTile(Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR,doorMappings.get(0)));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i=0;i<values.length;i++) result[i]=(byte)values[i];
        return result;
    }

    private static int effectiveFirstTile(int artTileBase, SpriteMappingFrame frame) {
        return artTileBase + frame.pieces().stream().mapToInt(SpriteMappingPiece::tileIndex).min().orElseThrow();
    }

    private static Set<String> keys(Sonic3kPlcArtRegistry.ZoneArtPlan plan) {
        Set<String> keys = plan.standaloneArt().stream()
                .map(Sonic3kPlcArtRegistry.StandaloneArtEntry::key).collect(Collectors.toSet());
        keys.addAll(plan.levelArt().stream().map(Sonic3kPlcArtRegistry.LevelArtEntry::key).toList());
        return keys;
    }

    private static void assertStandalone(Sonic3kPlcArtRegistry.ZoneArtPlan plan, String key,
                                         int art, int mapping, int size) {
        var entry = plan.standaloneArt().stream().filter(e -> key.equals(e.key())).findFirst().orElseThrow();
        assertEquals(art, entry.artAddr());
        assertEquals(mapping, entry.mappingAddr());
        assertEquals(size, entry.artSize());
        assertEquals(CompressionType.NEMESIS, entry.compression());
    }
}
