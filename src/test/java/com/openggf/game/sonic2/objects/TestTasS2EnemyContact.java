package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.objects.badniks.NebulaBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * s2-damage-bounce (600M/1198S, 3339M/5385S) and s2-combo-score (3339M/5385S).
 * A real SCZ Nebula overlaps a rolling Sonic at ($A0,$70); the ROM-backed touch
 * table and production player-slot dispatch must kill it, score and rebound.
 * Synthetic contact entry, not a SCZ route or the KiS2 claimed maximum score.
 * Oracle: Touch_KillEnemy (s2.asm:85514-85565), pin
 * 34fc7f130a96db171e1a1c43e28e0bad81cd6ca7.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2EnemyContact {
    private ObjectManager objects;
    private TestObjectServices services;
    private TestablePlayableSprite player;

    @BeforeEach
    void setUp() throws Exception {
        AbstractObjectInstance.resetCameraBoundsForTests();
        var camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        services = new TestObjectServices().withCamera(camera)
                .withGameState(new GameStateManager())
                .withDebugOverlay(mock(DebugOverlayManager.class));
        var table = GameServices.module().createTouchResponseTable(
                RomByteReader.fromRom(TestEnvironment.currentRom()));
        objects = new ObjectManager(List.of(), null, 0, null, table, null, camera, services);
        services.withDirectObjectManager(objects);
        player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setRolling(true);
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        player.setCentreX((short) 0xA0);
        player.setCentreY((short) 0x70);
        player.setXSpeed((short) 0xA00);
        player.setGSpeed((short) 0xB00);
    }

    @ParameterizedTest
    @CsvSource({"-1,512,-512", "0,512,256", "1,512,256", "-1,-512,-256"})
    void realEnemyContactUsesCentreAndVelocityBranchesWithoutLosingHorizontalMomentum(
            int playerYOffset, int incomingY, int expectedY) {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(16)) {
            player.setAir(true);
            player.setCentreY((short) (0x70 + playerYOffset));
            player.setYSpeed((short) incomingY);
            defeat(1);

            assertEquals(expectedY, player.getYSpeed());
            assertEquals(0xA00, player.getXSpeed());
            assertEquals(0xB00, player.getGSpeed());
            assertTrue(player.getAir());
            assertTrue(player.getRolling());
            assertEquals(100, services.gameState().getScore());
        }
    }

    @Test
    void consecutiveGroundedKillsRetainTheFirstFourNativeComboAwards() {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(16)) {
            int[] awards = {100, 200, 500, 1000};
            int expectedScore = 0;
            for (int i = 0; i < awards.length; i++) {
                player.setYSpeed((short) 0);
                defeat(i + 1);
                expectedScore += awards[i];
                assertEquals(expectedScore, services.gameState().getScore());
                assertFalse(player.getAir(), "Touch_KillEnemy does not set the airborne bit");
                assertTrue(player.getRolling(), "a grounded badnik contact must not unroll the player");
                assertEquals(-0x100, player.getYSpeed());
            }
        }
    }

    @Test
    void sixteenthNormalKillAwardsTenThousandNativePoints() {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(16)) {
            int before = 0;
            for (int i = 1; i <= 16; i++) {
                before = services.gameState().getScore();
                defeat(i);
            }
            assertEquals(10000, services.gameState().getScore() - before,
                    "Touch_KillEnemy compares the incremented Chain_Bonus_counter against $20");
        }
    }

    private void defeat(int pass) {
        var enemy = new NebulaBadnikInstance(new ObjectSpawn(0xA0, 0x70, 0x99, 0x12, 0, false, 0));
        objects.addDynamicObject(enemy);
        objects.snapshotTouchResponseState();
        objects.runTouchResponsesForPlayer(player, pass);
        com.openggf.tests.reel.S2ComponentTestReel.frame(player, objects.getActiveObjects(), 0, 0, "enemy touch slot; later object loop intentionally absent");
        assertTrue(enemy.isDestroyed(), "must reach native enemy destruction on pass " + pass
                + "; anim=" + player.getAnimationId() + ", rolling=" + player.getRolling()
                + ", hurt=" + player.isHurt() + ", score=" + services.gameState().getScore());
        // This fixture runs player touch slots without the later object loop.
        // Retire its destroyed body before the next synthetic contact, as that
        // loop would; otherwise the stale body's old flags stop the next scan.
        objects.removeDynamicObject(enemy);
    }
}
