package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Locked-on oracle for Obj_FBZMiniboss (sonic3k.asm:146766-148026). */
class TestFbzAct1Miniboss {
    @Test
    void objectOwnedGateAndWaitDurationsMatchTheRom() {
        assertArrayEquals(new int[] {0x240, 0x600, 0x2D20, 0x2F20},
                FbzMinibossInstance.activationBounds());
        assertArrayEquals(new int[] {0x540, 0x540, 0x2E20, 0x2EA0},
                FbzMinibossInstance.lockBounds());
        assertEquals(121, FbzMinibossInstance.musicWaitUpdates());
        assertArrayEquals(new int[] {33, 33, 65}, FbzMinibossCoverChild.waitUpdates());
        assertEquals(32, FbzMinibossChainLink.phaseWaitUpdates());
    }

    @Test
    void onlySixScriptedTerminalImpactsDefeatTheBoss() {
        RecordingServices services = new RecordingServices();
        FbzMinibossInstance boss = boss(services);
        PlayableEntity player = mock(PlayableEntity.class);
        boss.update(-1, player); // setup callback is intentionally setup-only

        for (int i = 0; i < 20; i++) boss.onPlayerAttack(player);
        assertEquals(6, boss.remainingHits());
        assertFalse(boss.isDefeated());

        for (int i = 0; i < 5; i++) {
            assertTrue(boss.publishScriptedTerminalImpact());
            boss.update(i, player);
            assertEquals(5 - i, boss.remainingHits());
            assertEquals(31, boss.hitFlashUpdatesRemaining(),
                    "the hit update consumes the first of the 32 flash writes");
            for (int frame = 0; frame < 31; frame++) boss.update(frame, player);
        }
        assertEquals(5, services.bossHits);
        assertTrue(boss.publishScriptedTerminalImpact());
        boss.update(100, player);
        assertTrue(boss.isDefeated());
        assertEquals(5, services.bossHits, "the sixth impact starts defeat without BossHit");
        assertEquals(0, services.scoreAwards, "the ROM awards no invented boss-hit score");
    }

    @Test
    void terminalHazardRetainsOrdinaryCollision86WithoutBecomingAttackable() {
        FbzMinibossChainLink terminal = FbzMinibossChainLink.terminalForTest(boss(new RecordingServices()), 0);
        assertEquals(0x86, terminal.getCollisionFlags());
        assertFalse(terminal.acceptsPlayerAttack());
    }

    @Test
    void aimedPauseIsTheRomOneUpdateFallthroughAndAimerUsesIntegerOctants() {
        assertEquals(1, FbzMinibossChainLink.aimedPauseUpdates());
        assertEquals(0, FbzMinibossAimerChild.findSonicTails8Way(0, -20));
        assertEquals(1, FbzMinibossAimerChild.findSonicTails8Way(20, -20));
        assertEquals(2, FbzMinibossAimerChild.findSonicTails8Way(20, 0));
        assertEquals(3, FbzMinibossAimerChild.findSonicTails8Way(20, 20));
        assertEquals(4, FbzMinibossAimerChild.findSonicTails8Way(0, 20));
        assertEquals(5, FbzMinibossAimerChild.findSonicTails8Way(-20, 20));
        assertEquals(6, FbzMinibossAimerChild.findSonicTails8Way(-20, 0));
        assertEquals(7, FbzMinibossAimerChild.findSonicTails8Way(-20, -20));
        assertEquals(7, FbzMinibossAimerChild.findSonicTails8Way(0, 0));
    }

    @Test
    void setupReturnsBeforeCameraWorkAndLockWaitsForVerticalConvergence() {
        Camera camera = new Camera();
        camera.setX((short) 0x2D40);
        camera.setY((short) 0x300);
        camera.setMinX((short) 0x100);
        camera.setMinY((short) 0x240);
        camera.setMaxX((short) 0x2F80);
        camera.setMaxY((short) 0x600);
        CameraServices services = new CameraServices(camera);
        FbzMinibossInstance boss = boss(services);

        boss.update(0, null);
        assertEquals(0x100, Short.toUnsignedInt(camera.getMinX()),
                "initialization installs the next callback and returns");
        assertEquals(0x600, boss.storedCameraMaxY());

        boss.update(1, null);
        assertEquals(0x2D40, Short.toUnsignedInt(camera.getMinX()));
        assertEquals(0x240, Short.toUnsignedInt(camera.getMinY()),
                "loc_85C7E must wait while current maxY is below-screen of $540");
        assertEquals(0x2F80, Short.toUnsignedInt(camera.getMaxX()));

        camera.setX((short) 0x2E20);
        camera.setMaxX((short) 0x2EC0);
        camera.setMaxY((short) 0x540);
        boss.update(2, null);
        assertEquals(0x540, Short.toUnsignedInt(camera.getMinY()));
        assertEquals(0x2E20, Short.toUnsignedInt(camera.getMinX()));
        assertEquals(0x2EA0, Short.toUnsignedInt(camera.getMaxX()));
        assertEquals(0x2EC0, boss.storedCameraMaxX(),
                "the horizontal lock snapshots the live maxX, not only the init value");
    }

    private static FbzMinibossInstance boss(ObjectServices services) {
        FbzMinibossInstance boss = new FbzMinibossInstance(
                new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3));
        boss.setServices(services);
        return boss;
    }

    private static final class RecordingServices extends TestObjectServices {
        int bossHits;
        int scoreAwards;
        private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> null, List::of);
        @Override public ObjectPlayerQuery playerQuery() { return query; }
        @Override public void playSfx(int id) { if (id == Sonic3kSfx.BOSS_HIT.id) bossHits++; }
    }

    private static final class CameraServices extends TestObjectServices {
        private final Camera camera;
        private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> null, List::of);
        private CameraServices(Camera camera) { this.camera = camera; }
        @Override public Camera camera() { return camera; }
        @Override public ObjectPlayerQuery playerQuery() { return query; }
    }
}
