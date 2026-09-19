package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kDezFinalArenaControllerInstance {
    @Test
    void cameraThresholdShrinksArenaAndTracksTheFloorWindow() {
        Harness services = new Harness();
        var floor = floor(services);
        services.camera.setX((short) 0x520);
        services.camera.setXCopy((short) 0x520);
        floor.update(0, null);
        assertEquals(0x2C0, services.runtime.act3BackgroundWord(0x00));
        assertEquals(0x110, floor.getX());
        assertTrue(services.objectManager().getActiveObjects().stream()
                .anyMatch(S3kDezFinalArenaControllerInstance.ArenaWall.class::isInstance));
    }

    @Test
    void requestedColumnCreatesAnIndependentFallingSolid() {
        Harness services = new Harness();
        var floor = floor(services);
        services.runtime.setAct3BackgroundWord(0x08, 0x2D0);
        services.runtime.setAct3BackgroundWord(0x16, 0x2C0);
        floor.update(0, null);
        assertEquals(0, services.runtime.act3BackgroundWord(0x08));
        assertEquals(0x2E0, services.runtime.act3BackgroundWord(0x16));
        assertTrue(services.objectManager().getActiveObjects().stream()
                .anyMatch(S3kDezFinalArenaControllerInstance.FallingBlock.class::isInstance));
    }

    private static S3kDezFinalArenaControllerInstance floor(Harness services) {
        var floor = new S3kDezFinalArenaControllerInstance(new ObjectSpawn(
                0x130, 0xF0, 0xA7, 0, 0, false, -1));
        floor.setServices(services);
        services.objectManager().addDynamicObject(floor);
        return floor;
    }

    private static final class Harness extends StubObjectServices {
        private final Camera camera = new Camera();
        private final GameStateManager gameState = new GameStateManager();
        private final S3kDezZoneRuntimeState runtime = new S3kDezZoneRuntimeState(
                Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0, PlayerCharacter.SONIC_ALONE);

        Harness() {
            withIsolatedObjectManager();
            runtime.setAct3BackgroundWord(0x00, 0x6C0);
            runtime.setAct3BackgroundWord(0x16, 0x80);
            zoneRuntimeRegistry().install(runtime);
        }
        @Override public Camera camera() { return camera; }
        @Override public GameStateManager gameState() { return gameState; }
    }
}
