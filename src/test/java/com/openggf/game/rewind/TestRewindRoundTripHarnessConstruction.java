package com.openggf.game.rewind;

import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.sonic1.objects.Sonic1PointsObjectInstance;
import com.openggf.game.sonic2.objects.PointsObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestRewindRoundTripHarnessConstruction {

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(classes = {
            Sonic1PointsObjectInstance.class,
            PointsObjectInstance.class,
            Sonic3kPointsObjectInstance.class
    })
    void pointsObjectsWithServicesAndScoreConstructorRoundTrip(Class<?> objectClass) {
        RoundTripSweepResult result = RewindRoundTripHarness.probeClass(objectClass.getName());

        assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                objectClass.getName() + " must be probeable before its points codec can be deleted");
    }
}
