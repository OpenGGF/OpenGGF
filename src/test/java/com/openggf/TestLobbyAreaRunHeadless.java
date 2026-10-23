package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.recording.RecordingMainPlayerResolver;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHpzInstance;
import com.openggf.mods.run.RunArea;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import openggf.timeattack.mp.LobbyAreas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Time Attack lobby levels through the real level loop: a death reload places the
 * character at the fence's spawn, the camera stays inside the fence while the act's own level
 * events run, held controls keep the character still, and the released character cannot leave
 * the fence in either direction. In Hidden Palace the Knuckles fight never starts.
 */
class TestLobbyAreaRunHeadless {
    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;

    @BeforeEach
    void setup() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) saved.put(key, config.getConfigValue(key));
        }
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
    }

    @AfterEach
    void cleanup() {
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Nested
    @RequiresRom(SonicGame.SONIC_3K)
    class HiddenPalace {
        @Test
        void theArenaFenceHoldsAndTheKnucklesFightNeverStarts() {
            playFenced("s3k", 0x128, true);
            assertFalse(GameServices.level().getObjectManager().getActiveObjects().stream()
                            .filter(CutsceneKnucklesHpzInstance.class::isInstance)
                            .map(CutsceneKnucklesHpzInstance.class::cast)
                            .anyMatch(CutsceneKnucklesHpzInstance::initializedForTest),
                    "the camera never entered word_63CEC");
        }
    }

    @Nested
    @RequiresRom(SonicGame.SONIC_1)
    class GreenHill {
        @Test
        void theStartFenceHolds() {
            // Outside GameLoop the S1 death reload leaves its PLC decoder mid-job (the loop's
            // respawn fade owns that lifecycle), so this act boots at the fence's spawn instead;
            // the respawn hand-off is covered by the S2 and S3K cases.
            playFenced("s1", 0x168, false);
        }
    }

    @Nested
    @RequiresRom(SonicGame.SONIC_2)
    class EmeraldHill {
        @Test
        void theStartFenceHolds() {
            playFenced("s2", 0x168, true);
        }
    }

    private void playFenced(String gameId, int rightWallOffset, boolean throughRespawn) {
        LobbyAreas.LobbyArea lobby = LobbyAreas.forGame(gameId);
        RunArea area = lobby.area();
        var builder = HeadlessTestFixture.builder().withZoneAndAct(lobby.zone(), lobby.act());
        if (!throughRespawn) {
            builder.startPosition((short) area.spawnX(), (short) area.spawnY()).startPositionIsCentre();
        }
        HeadlessTestFixture fixture = builder.build();
        HostedRunController controller = new HostedRunController(config);
        RunHandle handle = controller.begin(LobbyAreas.runSpec(lobby, "sonic"), new RunHost() { });
        try {
            // A death reload (the act's own respawn) uses the fence's spawn, as launch and retry do.
            if (throughRespawn) {
                controller.armAreaSpawn(GameServices.level());
                GameServices.level().respawnPlayer();
            }
            controller.onLevelReady(false);
            AbstractPlayableSprite player = RecordingMainPlayerResolver.resolve(config, GameServices.sprites());
            assertEquals(area.spawnX(), player.getCentreX() & 0xFFFF, "spawn X");
            assertEquals(area.spawnY(), player.getCentreY() & 0xFFFF, "spawn Y");
            assertFence(area);

            InputHandler input = new InputHandler();
            handle.holdControls(true);
            handle.setLevelCovered(true);
            long timerBefore = GameServices.level().getLevelGamestate().getTimerFrames();
            for (int frame = 0; frame < 40; frame++) step(controller, fixture, input, false, true, false);
            assertTrue(com.openggf.level.LevelHudCoverAccess.covered(GameServices.level()), "the menu hides the stock HUD");
            assertTrue(GameServices.level().getLevelGamestate().getTimerFrames() > timerBefore,
                    "covering is presentation only: the level timer keeps running");
            handle.setLevelCovered(false);
            controller.admitStep(input);
            assertFalse(com.openggf.level.LevelHudCoverAccess.covered(GameServices.level()));
            int heldX = player.getCentreX() & 0xFFFF;
            assertTrue(Math.abs(heldX - area.spawnX()) <= 2, "held controls keep the character still: " + heldX);

            handle.holdControls(false);
            int furthestRight = 0;
            for (int frame = 0; frame < 900; frame++) {
                step(controller, fixture, input, false, true, frame % 50 == 0);
                assertFence(area);
                furthestRight = Math.max(furthestRight, player.getCentreX() & 0xFFFF);
            }
            assertTrue(furthestRight > heldX + 0x80, "released, the character runs: " + furthestRight);
            assertTrue(furthestRight <= area.maxX() + rightWallOffset, "right wall: " + furthestRight);

            int furthestLeft = Integer.MAX_VALUE;
            for (int frame = 0; frame < 1200; frame++) {
                step(controller, fixture, input, true, false, frame % 50 == 0);
                assertFence(area);
                furthestLeft = Math.min(furthestLeft, player.getCentreX() & 0xFFFF);
            }
            assertTrue(furthestLeft >= area.minX() + 0x10, "left wall: " + furthestLeft);
            assertTrue(furthestLeft < furthestRight - 0x100, "the character came back left: " + furthestLeft);
        } finally {
            controller.end(RunEndReason.LEFT);
        }
    }

    private static void step(HostedRunController controller, HeadlessTestFixture fixture, InputHandler input,
                             boolean left, boolean right, boolean jump) {
        controller.admitStep(input);
        fixture.stepFrame(false, false, left, right, jump);
        controller.afterStep();
    }

    private static void assertFence(RunArea area) {
        var camera = GameServices.camera();
        int x = camera.getX() & 0xFFFF;
        int y = camera.getY() & 0xFFFF;
        assertTrue(x >= area.minX() && x <= area.maxX(), "camera X " + Integer.toHexString(x));
        assertTrue(y >= area.minY() && y <= area.maxY(), "camera Y " + Integer.toHexString(y));
        assertEquals(area.minX(), camera.getMinX() & 0xFFFF);
        assertEquals(area.maxX(), camera.getMaxX() & 0xFFFF);
        assertEquals(area.minY(), camera.getMinY() & 0xFFFF);
        assertEquals(area.maxY(), camera.getMaxY() & 0xFFFF);
    }
}
