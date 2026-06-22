package com.openggf.game.rewind;

import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.sonic1.objects.Sonic1BigSpikedBallObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1ConveyorBeltObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1ElectrocuterObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1HarpoonObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1HiddenBonusObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1LavaBallObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1LavaTagObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1MzBrickObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1PoleThatBreaksObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SawObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SceneryObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SmallDoorObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SpikedPoleHelixObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SpikeObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1VanishingPlatformObjectInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS1SimpleObjectGenericRecreate {

    @ParameterizedTest(name = "{0} round-trips via generic recreate")
    @ValueSource(classes = {
            Sonic1BigSpikedBallObjectInstance.class,
            Sonic1ConveyorBeltObjectInstance.class,
            Sonic1ElectrocuterObjectInstance.class,
            Sonic1HarpoonObjectInstance.class,
            Sonic1HiddenBonusObjectInstance.class,
            Sonic1LavaBallObjectInstance.class,
            Sonic1LavaTagObjectInstance.class,
            Sonic1MzBrickObjectInstance.class,
            Sonic1PoleThatBreaksObjectInstance.class,
            Sonic1SawObjectInstance.class,
            Sonic1SceneryObjectInstance.class,
            Sonic1SmallDoorObjectInstance.class,
            Sonic1SpikedPoleHelixObjectInstance.class,
            Sonic1SpikeObjectInstance.class,
            Sonic1VanishingPlatformObjectInstance.class
    })
    void s1SimpleObjectsRoundTripThroughGenericRecreate(Class<?> objectClass) {
        RoundTripSweepResult result = RewindRoundTripHarness.probeClass(objectClass.getName());

        assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                objectClass.getName() + " should round-trip via RewindRecreatable generic recreate");
    }

    @Test
    void spawnConstructibleS1ObjectsUseSharedRecreateMarker() {
        Class<?> marker = assertDoesNotThrow(
                () -> Class.forName("com.openggf.level.objects.SpawnRewindRecreatable"));
        Class<?>[] objectClasses = {
                Sonic1BigSpikedBallObjectInstance.class,
                Sonic1ConveyorBeltObjectInstance.class,
                Sonic1ElectrocuterObjectInstance.class,
                Sonic1HarpoonObjectInstance.class,
                Sonic1HiddenBonusObjectInstance.class,
                Sonic1LavaBallObjectInstance.class,
                Sonic1LavaTagObjectInstance.class,
                Sonic1MzBrickObjectInstance.class,
                Sonic1PoleThatBreaksObjectInstance.class,
                Sonic1SawObjectInstance.class,
                Sonic1SceneryObjectInstance.class,
                Sonic1SmallDoorObjectInstance.class,
                Sonic1SpikedPoleHelixObjectInstance.class,
                Sonic1SpikeObjectInstance.class,
                Sonic1VanishingPlatformObjectInstance.class
        };

        for (Class<?> objectClass : objectClasses) {
            assertTrue(marker.isAssignableFrom(objectClass),
                    objectClass.getName() + " should opt into shared spawn-based recreate");
            assertTrue(hasNoDeclaredRecreateForRewind(objectClass),
                    objectClass.getName() + " should inherit the shared recreate implementation");
        }
    }

    private static boolean hasNoDeclaredRecreateForRewind(Class<?> objectClass) {
        for (var method : objectClass.getDeclaredMethods()) {
            if (method.getName().equals("recreateForRewind")) {
                return false;
            }
        }
        return true;
    }
}
