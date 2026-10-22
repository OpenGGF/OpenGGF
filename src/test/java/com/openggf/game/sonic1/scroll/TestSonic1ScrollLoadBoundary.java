package com.openggf.game.sonic1.scroll;

import com.openggf.game.CheckpointBackgroundScrollAccess;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.internal.CheckpointBackgroundScroll;
import com.openggf.game.sonic1.objects.Sonic1LamppostObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1ScrollLoadBoundary {
    private SharedLevel shared;
    @AfterEach void cleanup() { if (shared != null) shared.dispose(); }

    @Test
    void sameActProductionReloadDiscardsThePreviousBackgroundSeed() throws Exception {
        shared = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
        var level = GameServices.level();
        var camera = GameServices.camera();
        camera.setX((short) 0x1363);
        camera.setY((short) 0x400);
        GameServices.parallax().update(0, 0, camera, 53);
        assertNotEquals(0, com.openggf.level.ParallaxCheckpointAccess.capture(GameServices.parallax(), 0).bg3X());
        level.loadZoneAndAct(0, 0);
        level.recomputeParallaxAfterRewindRestore();
        assertEquals(0, camera.getX());
        assertEquals(0, com.openggf.level.ParallaxCheckpointAccess.capture(GameServices.parallax(), 0).bg2X());
        assertEquals(0, com.openggf.level.ParallaxCheckpointAccess.capture(GameServices.parallax(), 0).bg3X());
        assertEquals(0, (short) GameServices.parallax().getHScroll()[80]);
    }

    @Test
    void realLamppostBanksBackgroundWordsRatherThanReseedingFromRespawnCamera() throws Exception {
        shared = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
        var level = GameServices.level();
        var camera = GameServices.camera();
        camera.setX((short) 0x200);
        camera.setY((short) 0x400);
        GameServices.parallax().update(0, 0, camera, 19);
        var sonic = GameServices.sprites().getMainPlayable();
        // The lamppost/player position produces a different return camera from
        // the scroll bank (which was accumulated at 1/2 and 3/8 FG speed).
        sonic.setCentreX((short) 0x400);
        sonic.setCentreY((short) 0x300);
        var lamp = new Sonic1LamppostObjectInstance(new ObjectSpawn(0x400, 0x300, 0x79, 1, 0, false, 0));
        lamp.setServices(level.getObjectManager().getObjectServices());
        lamp.update(0, sonic);
        CheckpointState checkpoint = (CheckpointState) level.getCheckpointState();
        assertTrue(checkpoint.isActive(), "must actually activate the production lamppost");
        CheckpointBackgroundScroll bank = CheckpointBackgroundScrollAccess.saved(checkpoint);
        assertEquals(0x100, bank.bg2X());
        assertEquals(0xC0, bank.bg3X());
        var registry = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var bankSnapshot = registry.capture();
        assertTrue(bankSnapshot.containsKey("s1-checkpoint-background"), "production adapter must be registered");
        CheckpointBackgroundScrollAccess.save(checkpoint, new CheckpointBackgroundScroll(1, 2, 3, 4, 5, 6));
        registry.restore(bankSnapshot);
        assertEquals(bank, CheckpointBackgroundScrollAccess.saved(checkpoint));

        level.respawnPlayer();
        int returnCameraX = camera.getX();
        assertEquals(0x360, returnCameraX, "LevSz_InitCameraPositions uses restored player centre");
        level.recomputeParallaxAfterRewindRestore();
        assertEquals(returnCameraX, camera.getX(), "background restore must preserve the foreground camera");
        assertEquals(0x100, com.openggf.level.ParallaxCheckpointAccess.capture(GameServices.parallax(), 0).bg2X());
        assertEquals(0xC0, com.openggf.level.ParallaxCheckpointAccess.capture(GameServices.parallax(), 0).bg3X());
        assertEquals(bank, CheckpointBackgroundScrollAccess.saved((CheckpointState) level.getCheckpointState()));
        assertEquals(-0xC0, (short) GameServices.parallax().getHScroll()[80]);

        var rewind = GameServices.parallax().capture();
        camera.setX((short) (returnCameraX + 3));
        GameServices.parallax().update(0, 0, camera, 1);
        int[] expected = GameServices.parallax().getHScroll().clone();
        camera.setX((short) 0x500);
        GameServices.parallax().update(0, 0, camera, 33);
        GameServices.parallax().restore(rewind);
        camera.setX((short) (returnCameraX + 3));
        GameServices.parallax().update(0, 0, camera, 1);
        assertArrayEquals(expected, GameServices.parallax().getHScroll(), "checkpoint-derived fractional motion replays");
    }

}
