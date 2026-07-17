package com.openggf.physics;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestFbzCalcRoomInFrontBackgroundCollision {
    @Test
    void calcRoomInFrontUsesTranslatedBackgroundWallProbe() throws Exception {
        TestFbzBackgroundPlaneCollision fixture = new TestFbzBackgroundPlaneCollision();
        fixture.setUp();
        try {
            CollisionSystem collision = new CollisionSystem(new TerrainCollisionManager());
            CollisionSystem.CalcRoomInFrontProbe probe =
                    CollisionSystem.describeCalcRoomInFrontProbe(0, (short) 0x400);
            Method calcRoom = CollisionSystem.class.getDeclaredMethod(
                    "scanCalcRoomInFront", com.openggf.sprites.playable.AbstractPlayableSprite.class,
                    CollisionSystem.CalcRoomInFrontProbe.class, short.class, short.class);
            calcRoom.setAccessible(true);

            SensorResult result = (SensorResult) calcRoom.invoke(
                    collision, fixture.sprite, probe, (short) 0, (short) 0);

            assertNotNull(result);
            assertEquals(7, result.tileId(),
                    "CalcRoomInFront must select the translated layer-1 result");
        } finally {
            fixture.tearDown();
        }
    }
}
