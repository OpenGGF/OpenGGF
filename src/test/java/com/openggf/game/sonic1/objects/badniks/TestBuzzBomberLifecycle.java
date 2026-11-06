package com.openggf.game.sonic1.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the production Buzz Bomber's lifetime rule and its manager integration.
 * No substitute object mirrors the persistence calculation: changing the badnik
 * to use spawn X, sprite visibility or a viewport margin must break these checks.
 */
public class TestBuzzBomberLifecycle {
    private Camera camera;
    private ObjectServices objectServices;

    @BeforeEach
    void setUp() {
        SonicConfigurationService configuration = SonicConfigurationService.createStandalone();
        configuration.resetToDefaults();
        camera = new Camera(configuration);
        camera.resetState();
        objectServices = new StubObjectServices() {
            @Override
            public Camera camera() {
                return camera;
            }
        };
        setCameraX(0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @Test
    void persistenceFollowsCurrentPositionAfterFlyingAwayFromSpawn() {
        setCameraX(400);
        Sonic1BuzzBomberBadnikInstance bomber = createBuzzBomber(1000);

        // One hover tick starts flight; 100 production movement ticks fly 400 px left.
        for (int tick = 1; tick <= 101; tick++) {
            bomber.update(tick, null);
        }
        assertEquals(600, bomber.getX());

        setCameraX(200);

        assertTrue(bomber.isPersistent(),
                "RememberState follows the live X even when the original spawn is out of range");
    }

    @ParameterizedTest(name = "camera X=256, object X={0}: persistent={1}")
    @CsvSource({
            "127, false",
            "128, true",
            "895, true",
            "896, false"
    })
    void persistenceUsesInclusiveChunkAlignedLifetimeWindow(int objectX, boolean expected) {
        setCameraX(256);
        Sonic1BuzzBomberBadnikInstance bomber = createBuzzBomber(objectX);

        // The left chunk begins at 128. The final admitted chunk begins at 768
        // (unsigned distance 640), so its entire 768..895 span remains alive.
        // In particular, 895 is outside a 160-px margin around the viewport.
        assertEquals(expected, bomber.isPersistent());
    }

    @Test
    void destroyedBuzzBomberDoesNotPersistInsideLifetimeWindow() {
        setCameraX(256);
        Sonic1BuzzBomberBadnikInstance bomber = createBuzzBomber(256);
        assertTrue(bomber.isPersistent());

        bomber.setDestroyed(true);

        assertFalse(bomber.isPersistent(), "A destroyed badnik cannot retain its placement");
    }

    @Test
    void managerKeepsSameProductionBuzzBomberWhenCameraRetreats() {
        setCameraX(400);
        BuzzBomberRegistry registry = new BuzzBomberRegistry();
        ObjectManager manager = new ObjectManager(
                List.of(buzzBomberSpawn(1000)), registry, 0, null, null,
                null, camera, objectServices);
        manager.reset(400);

        for (int tick = 1; tick <= 101; tick++) {
            manager.update(400, null, null, tick);
        }
        Sonic1BuzzBomberBadnikInstance bomber = registry.instance;
        assertEquals(600, bomber.getX(), "The production routine must have flown away from its spawn");

        setCameraX(200);
        manager.update(200, null, null, 102);
        manager.update(200, null, null, 103);

        assertEquals(1, manager.getActiveObjects().size());
        assertSame(bomber, manager.getActiveObjects().iterator().next(),
                "Camera retreat must retain the existing object, not recreate its placement");
        assertEquals(1, registry.creationCount);
    }

    private Sonic1BuzzBomberBadnikInstance createBuzzBomber(int x) {
        Sonic1BuzzBomberBadnikInstance bomber =
                new Sonic1BuzzBomberBadnikInstance(buzzBomberSpawn(x));
        bomber.setServices(objectServices);
        return bomber;
    }

    private void setCameraX(int x) {
        camera.setX((short) x);
        AbstractObjectInstance.updateCameraBounds(x, 0, x + 320, 224, 0);
    }

    private static ObjectSpawn buzzBomberSpawn(int x) {
        // Unflipped Buzz Bomber faces left; no player is supplied to trigger firing.
        return new ObjectSpawn(x, 100, 0x22, 0, 0, false, 0);
    }

    private static final class BuzzBomberRegistry implements ObjectRegistry {
        private Sonic1BuzzBomberBadnikInstance instance;
        private int creationCount;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            creationCount++;
            instance = new Sonic1BuzzBomberBadnikInstance(spawn);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {}

        @Override
        public String getPrimaryName(int objectId) {
            return "BuzzBomber";
        }
    }
}
