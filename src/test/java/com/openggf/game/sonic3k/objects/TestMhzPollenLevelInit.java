package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestMhzPollenLevelInit {

    @Test
    void mhzLevelInitInstallsPersistentPollenSpawner() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 0)
                .startPosition((short) 0x1200, (short) 0x0700)
                .startPositionIsCentre()
                .build();

        List<MhzPollenSpawnerInstance> spawners = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(MhzPollenSpawnerInstance.class::isInstance)
                .map(MhzPollenSpawnerInstance.class::cast)
                .toList();

        assertEquals(1, spawners.size(),
                "S3K Level init installs Obj_MHZ_Pollen_Spawner at Dynamic_object_RAM+object_size for MHZ");
        assertTrue(spawners.get(0).isPersistent(),
                "The pollen spawner is a persistent fixed dynamic object, not a placement-window object");
    }

    @Test
    void placementResetRestoresFixedPollenSpawnerBeforeTitleCardPrelude() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 0)
                .startPosition((short) 0x1200, (short) 0x0700)
                .startPositionIsCentre()
                .build();

        ObjectManager objectManager = GameServices.level().getObjectManager();
        objectManager.reset(GameServices.camera().getX());
        assertEquals(0, pollenSpawnerCount(objectManager),
                "the placement reset clears fixed dynamic objects before the provider restores them");

        GameServices.module().getLevelEventProvider().restoreEventOwnedObjectsAfterPlacementReset();

        assertEquals(1, pollenSpawnerCount(objectManager),
                "Obj_MHZ_Pollen_Spawner must be restored for title-card object prelude execution");
    }

    @Test
    void preludeResetRestoresPollenSpawnerInNativeFixedSlotBeforePlacementObjects() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 0)
                .startPosition((short) 0x1200, (short) 0x0700)
                .startPositionIsCentre()
                .build();

        ObjectManager objectManager = GameServices.level().getObjectManager();
        objectManager.resetForPreludeHydration(GameServices.camera().getX());
        GameServices.module().getLevelEventProvider().restoreEventOwnedObjectsAfterPlacementReset();
        objectManager.preloadInitialSpawnsForHydration();

        MhzPollenSpawnerInstance spawner = objectManager.getActiveObjects().stream()
                .filter(MhzPollenSpawnerInstance.class::isInstance)
                .map(MhzPollenSpawnerInstance.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(4, spawner.getSlotIndex(),
                "sonic3k.asm LevelInit_MHZ writes the pollen controller to Dynamic_object_RAM+object_size");
    }

    private static long pollenSpawnerCount(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(MhzPollenSpawnerInstance.class::isInstance)
                .count();
    }
}
