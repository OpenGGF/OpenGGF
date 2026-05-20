package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLauncherBallObjectInstance {

    @Test
    void launcherProcessesUniqueEngineParticipantsOncePerFrame() {
        LauncherBallObjectInstance launcher = newLauncher();
        TestablePlayableSprite main = playerAtLauncher("sonic");
        TestablePlayableSprite tails = playerAtLauncher("tails");
        TestablePlayableSprite knuckles = playerAtLauncher("knuckles");
        Camera camera = new Camera();
        camera.setFocusedSprite(main);
        launcher.setServices(new TestObjectServices()
                .withCamera(camera)
                .withSidekicks(List.of(tails, knuckles, tails)));

        launcher.update(0, main);

        assertTrue(main.isObjectControlled(), "Main player should be captured");
        assertTrue(tails.isObjectControlled(), "First sidekick should be captured");
        assertTrue(knuckles.isObjectControlled(), "Additional engine sidekick should be captured");
        assertEquals(0, mappingFrame(launcher),
                "Duplicate sidekick entries must not advance shared launcher animation in the capture frame");
    }

    private static LauncherBallObjectInstance newLauncher() {
        return new LauncherBallObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x48, 0, 0, false, 0),
                "LauncherBall");
    }

    private static TestablePlayableSprite playerAtLauncher(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x1000, (short) 0x1000);
        player.setCentreX((short) 0x1000);
        player.setCentreY((short) 0x1000);
        player.setAir(false);
        return player;
    }

    private static int mappingFrame(LauncherBallObjectInstance launcher) {
        try {
            Field field = LauncherBallObjectInstance.class.getDeclaredField("mappingFrame");
            field.setAccessible(true);
            return field.getInt(launcher);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read launcher mappingFrame", e);
        }
    }
}
