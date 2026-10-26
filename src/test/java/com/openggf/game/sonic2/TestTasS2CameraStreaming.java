package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 3325M/5373S camera trick: outrun the 16-pixel camera cap to delay object activation. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2CameraStreaming {
    @Test
    void superspeedPassesThePlacementBeforeItLoadsButOrdinarySpeedDoesNot() {
        assertFalse(passPlacement(0x4000));
        assertTrue(passPlacement(0x600));
    }
    private static boolean passPlacement(int speed) {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GroundSensor.setLevelManager(null); // an authored empty airborne approach
        var camera = GameServices.camera();
        camera.setMinX((short) 0); camera.setMaxX((short) 0x2000); camera.setMaxY((short) 0x2000);
        var player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 160); player.setCentreY((short) 700);
        player.setAir(true); player.setXSpeed((short) speed); player.setYSpeed((short) 0x100);
        camera.setFocusedSprite(player); camera.setX((short) 0); camera.setY((short) 604);
        var spawn = new ObjectSpawn(800, 700, 0x26, 3, 0, false, 0);
        var services = new TestObjectServices().withCamera(camera).withGameModule(GameServices.module());
        var objects = new ObjectManager(List.of(spawn), GameServices.module().createObjectRegistry(),
                0, null, null, null, camera, services);
        services.withDirectObjectManager(objects);
        var terrain = new TerrainCollisionManager() {
            @Override public SensorResult[] getSensorResult(Sensor[] ignored) { return new SensorResult[] {null, null}; }
        };
        var movement = new PlayableSpriteMovement(player, new CollisionSystem(terrain), GameServices.gameState());
        int frames = 0;
        boolean seen = false;
        while (player.getCentreX() < 800 && frames++ < 150) {
            movement.handleMovement(false, false, false, false, false, false, false, false);
            int oldCamera = camera.getX();
            camera.updatePosition();
            assertTrue(camera.getX() - oldCamera <= 16, "S2 camera must enforce its actual follow cap");
            objects.update(camera.getX(), player, List.of(), frames);
            seen |= objects.getActiveObjects().stream().anyMatch(o -> o.getSpawn().equals(spawn));
        }
        assertTrue(player.getCentreX() >= 800, "must actually traverse the object coordinate");
        if (!seen) {
            // Freeze the authored approach's player position; camera convergence
            // must eventually admit the very same placement through ObjPosLoad.
            int waits = 0;
            do {
                camera.updatePosition(); objects.update(camera.getX(), player, List.of(), ++frames);
                if (objects.getActiveObjects().stream().anyMatch(o -> o.getSpawn().equals(spawn))) break;
            } while (++waits < 80);
            assertTrue(waits < 80, "the placement must load once the camera catches up");
        }
        return seen;
    }
}
