package com.openggf.game.rewind;

import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.sonic1.objects.Sonic1BigSpikedBallObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1ElectrocuterObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1HarpoonObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1HiddenBonusObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1MzBrickObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1PoleThatBreaksObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SawObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SceneryObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SmallDoorObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1VanishingPlatformObjectInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestS1SimpleObjectGenericRecreate {

    @ParameterizedTest(name = "{0} round-trips via generic recreate")
    @ValueSource(classes = {
            Sonic1BigSpikedBallObjectInstance.class,
            Sonic1ElectrocuterObjectInstance.class,
            Sonic1HarpoonObjectInstance.class,
            Sonic1HiddenBonusObjectInstance.class,
            Sonic1MzBrickObjectInstance.class,
            Sonic1PoleThatBreaksObjectInstance.class,
            Sonic1SawObjectInstance.class,
            Sonic1SceneryObjectInstance.class,
            Sonic1SmallDoorObjectInstance.class,
            Sonic1VanishingPlatformObjectInstance.class
    })
    void s1SimpleObjectsRoundTripThroughGenericRecreate(Class<?> objectClass) {
        RoundTripSweepResult result = RewindRoundTripHarness.probeClass(objectClass.getName());

        assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                objectClass.getName() + " should round-trip via RewindRecreatable generic recreate");
    }
}
