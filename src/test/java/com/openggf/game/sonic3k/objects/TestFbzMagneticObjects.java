package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.level.objects.ObjectServices;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzMagneticObjects {
    @Test
    void spikeBallSubtypesAndPlatformChainDecodeFromRom() {
        assertEquals(FbzMagneticSpikeBallObjectInstance.Kind.BALL,
                new FbzMagneticSpikeBallObjectInstance(spawn(0x73, 0)).kind());
        assertEquals(FbzMagneticSpikeBallObjectInstance.Kind.STATIC_BALL,
                new FbzMagneticSpikeBallObjectInstance(spawn(0x73, 1)).kind());
        assertEquals(FbzMagneticSpikeBallObjectInstance.Kind.FIELD_WIDE,
                new FbzMagneticSpikeBallObjectInstance(spawn(0x73, 0x80)).kind());
        assertEquals(FbzMagneticSpikeBallObjectInstance.Kind.FIELD_NARROW,
                new FbzMagneticSpikeBallObjectInstance(spawn(0x73, 0x81)).kind());

        FbzMagneticPlatformObjectInstance platform =
                new FbzMagneticPlatformObjectInstance(spawn(0x74, 0x0F));
        assertEquals(0xD0, platform.maximumRise());
        assertEquals(8, platform.maximumVisibleChainPieces());
    }

    @Test
    void bothConsumersObserveTheSharedBitOnEachConsecutiveEdge() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(0, PlayerCharacter.SONIC_AND_TAILS, events);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.zoneRuntimeState()).thenReturn(runtime);
        var field = new FbzMagneticSpikeBallObjectInstance(spawn(0x73, 0x80));
        var platform = new FbzMagneticPlatformObjectInstance(spawn(0x74, 0x0F));
        field.setServices(services);
        platform.setServices(services);

        events.advanceMagneticPhase(0x00FF);
        assertFalse(field.magneticActive());
        assertFalse(platform.magneticActive());
        events.advanceMagneticPhase(0x0100);
        assertTrue(field.magneticActive());
        assertTrue(platform.magneticActive());
        events.advanceMagneticPhase(0x0200);
        assertFalse(field.magneticActive());
        assertFalse(platform.magneticActive());
    }

    @Test
    void platformUsesRadiusFUntilFirstLandingThenRadius10ForFloorAndCeiling() throws Exception {
        FbzMagneticPlatformObjectInstance platform =
                new FbzMagneticPlatformObjectInstance(spawn(0x74, 0x0F));
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        platform.setServices(services);
        setField(platform, "chainAllocationAttempted", true);

        try (MockedStatic<ObjectTerrainUtils> terrain =
                org.mockito.Mockito.mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(0x1000, 0x700, 0x0F))
                    .thenReturn(new TerrainCheckResult(-1, (byte) 0, 1));
            platform.update(0, null);
            assertEquals(0x6FF, platform.getY(), "the initial one-pixel landing seam must use radius $0F");
            assertEquals(0x10, platform.collisionRadius());

            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(0x1000, 0x6FF, 0x10))
                    .thenReturn(TerrainCheckResult.noCollision());
            platform.update(1, null);
            terrain.verify(() -> ObjectTerrainUtils.checkFloorDist(0x1000, 0x6FF, 0x10));

            setField(platform, "rising", true);
            terrain.when(() -> ObjectTerrainUtils.checkCeilingDist(
                            org.mockito.ArgumentMatchers.eq(0x1000),
                            org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.eq(0x10)))
                    .thenReturn(TerrainCheckResult.noCollision());
            platform.update(2, null);
            terrain.verify(() -> ObjectTerrainUtils.checkCeilingDist(
                    org.mockito.ArgumentMatchers.eq(0x1000),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.eq(0x10)));
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x700, id, subtype, 0, false, 1);
    }
}
