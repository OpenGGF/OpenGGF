package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.Sonic2PlcArtRegistry;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2OOZBossPath {

    @Test
    void registryCreatesObj55OozBoss() {
        Sonic2ObjectRegistry registry = new Sonic2ObjectRegistry();

        assertEquals(0x55, Sonic2ObjectIds.OOZ_BOSS);
        assertTrue(registry.hasRegisteredFactory(Sonic2ObjectIds.OOZ_BOSS));

        ObjectInstance instance = registry.create(new ObjectSpawn(
                0, 0, Sonic2ObjectIds.OOZ_BOSS, 0, 0, false, 0));

        assertInstanceOf(Sonic2OOZBossInstance.class, instance);
    }

    @Test
    void plcRegistryMapsOozBossArtToObj55Sheet() {
        Sonic2PlcArtRegistry.ArtRegistration registration =
                Sonic2PlcArtRegistry.lookup(Sonic2Constants.ART_NEM_OOZ_BOSS_ADDR);

        assertNotNull(registration);
        assertEquals(Sonic2ObjectArtKeys.OOZ_BOSS, registration.key());
    }

    @Test
    void mainVehicleInitialStateMatchesObj55MainInit() {
        Sonic2OOZBossInstance boss = new Sonic2OOZBossInstance(new ObjectSpawn(
                0, 0, Sonic2ObjectIds.OOZ_BOSS, 0, 0, false, 0));
        boss.setServices(new TestObjectServices());

        assertEquals(0x2940, boss.getState().x);
        assertEquals(0x02D0, boss.getState().y);
        assertEquals(0x02, boss.getBossSubtypeForTesting());
        assertEquals(0x02, boss.getState().routineSecondary);
        assertEquals(-0x80, boss.getState().yVel);
        assertEquals(8, boss.getState().hitCount);
        assertEquals(8, boss.getMainFrameForTesting());
        assertEquals(1, boss.getChildSpriteCountForTesting());
        assertEquals(0, boss.getStatusForTesting());
        assertEquals(0xCF, boss.getCollisionFlags());
    }
}
