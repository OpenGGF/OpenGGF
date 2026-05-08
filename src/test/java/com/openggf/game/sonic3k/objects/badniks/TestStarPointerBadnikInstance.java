package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.LevelData;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.ObjectDiscoveryTool.LevelConfig;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestStarPointerBadnikInstance {

    @Test
    void registryCreatesStarPointerForS3klZones() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry() {
            @Override
            protected int currentRomZoneId() {
                return Sonic3kZoneIds.ZONE_ICZ;
            }
        };

        ObjectInstance instance = registry.create(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.STAR_POINTER, 0x06, 0, false, 0));

        assertInstanceOf(StarPointerBadnikInstance.class, instance);
    }

    @Test
    void profileMarksStarPointerImplementedForS3klLevelsOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        LevelConfig icz1 = profile.getLevels().stream()
                .filter(level -> level.levelData() == LevelData.S3K_ICECAP_1)
                .findFirst()
                .orElseThrow();
        LevelConfig mhz1 = profile.getLevels().stream()
                .filter(level -> level.levelData() == LevelData.S3K_MUSHROOM_HILL_1)
                .findFirst()
                .orElseThrow();

        assertTrue(profile.getImplementedIds(icz1).contains(Sonic3kObjectIds.STAR_POINTER));
        assertFalse(profile.getImplementedIds(mhz1).contains(Sonic3kObjectIds.STAR_POINTER));
    }

    @Test
    void subtypeSixTracksLeftAtOnePixelPerFrameAfterInit() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);
        StarPointerBadnikInstance starPointer = new StarPointerBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.STAR_POINTER, 0x06, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 100);
        when(player.getCentreY()).thenReturn((short) 100);
        when(player.getDead()).thenReturn(false);

        starPointer.update(0, player);
        assertEquals(160, starPointer.getX(), "init frame should only set velocity and children");

        starPointer.update(1, player);

        assertEquals(159, starPointer.getX(),
                "subtype bits 1-2 = 3 select ROM speed -$100 toward the player");
    }

    @Test
    void initSpawnsFourOrbitingPoints() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);
        StarPointerBadnikInstance starPointer = new StarPointerBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.STAR_POINTER, 0, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 100);
        when(player.getCentreY()).thenReturn((short) 100);
        ObjectServices services = mock(ObjectServices.class);
        ObjectManager objectManager = mock(ObjectManager.class);
        when(services.objectManager()).thenReturn(objectManager);
        starPointer.setServices(services);

        starPointer.update(0, player);

        verify(objectManager, times(4)).addDynamicObjectAfterCurrent(
                org.mockito.ArgumentMatchers.any(StarPointerBadnikInstance.OrbitingPointInstance.class));
    }
}
