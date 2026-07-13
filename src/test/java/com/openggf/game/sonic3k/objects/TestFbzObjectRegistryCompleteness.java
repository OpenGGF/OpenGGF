package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.LevelData;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.tools.ObjectDiscoveryTool.LevelConfig;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestFbzObjectRegistryCompleteness {
    private static final Set<Integer> CONCRETE_FBZ_IDS = Set.of(
            0x00, 0x01, 0x02, 0x07, 0x08, 0x0F, 0x26, 0x28, 0x2A,
            0x2F, 0x33, 0x34, 0x3D, 0x6A, 0x6B,
            0x6F, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
            0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F, 0xE0, 0xE1,
            0xE3, 0xE4, 0xE5, 0xFF,
            0x80, 0x85, 0xA8, 0xA9, 0xAA);

    @Test
    void fbzProfileAllowlistMatchesTheCheckedConcreteFactoryInventory() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        LevelConfig fbz1 = profile.getLevels().stream()
                .filter(level -> level.levelData() == LevelData.S3K_FLYING_BATTERY_1)
                .findFirst().orElseThrow();

        assertEquals(CONCRETE_FBZ_IDS, profile.getImplementedIds(fbz1));
        assertEquals(true, profile.getImplementedIds(fbz1).containsAll(Set.of(0xA8, 0xA9)));
    }

    @Test
    void checkedLayoutsContainExactly37CurrentPlaceholderPlacements() throws IOException {
        Sonic3kObjectRegistry registry = new FbzTestRegistry();
        List<ObjectSpawn> placements = new java.util.ArrayList<>();
        placements.addAll(TestFbzObjectInventory.load("1.bin"));
        placements.addAll(TestFbzObjectInventory.load("2.bin"));

        long placeholders = placements.stream()
                .map(registry::create)
                .filter(PlaceholderObjectInstance.class::isInstance)
                .count();
        assertEquals(37, placeholders);

        for (ObjectSpawn spawn : placements) {
            ObjectInstance instance = registry.create(spawn);
            assertEquals(CONCRETE_FBZ_IDS.contains(spawn.objectId()),
                    !(instance instanceof PlaceholderObjectInstance),
                    "factory classification for S3KL ID $" + Integer.toHexString(spawn.objectId()));
        }
    }

    @Test
    void mhzZoneSetRetainsTheCutsceneRemapsForA8AndA9() {
        Sonic3kObjectRegistry registry = new MhzTestRegistry();
        assertInstanceOf(Mhz1CutsceneKnucklesInstance.class,
                registry.create(new ObjectSpawn(0, 0, 0xA8, 0, 0, false, 0)));
        assertInstanceOf(Mhz1CutsceneButtonInstance.class,
                registry.create(new ObjectSpawn(0, 0, 0xA9, 0, 0, false, 0)));
    }

    private static final class FbzTestRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_FBZ;
        }
    }

    private static final class MhzTestRegistry extends Sonic3kObjectRegistry {
        @Override protected int currentRomZoneId() { return Sonic3kZoneIds.ZONE_MHZ; }
    }
}
