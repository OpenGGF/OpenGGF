package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunSpec;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * A death under {@link GameplayRunPolicy.DeathRestart#FAST_FROM_START} through the production
 * GameLoop death/respawn path: no life is lost, the act reloads at its start without a title
 * card or star-post progress, the host hears a death-restart level start, and a jump press cuts
 * the animation short only after {@link GameplayRunPolicy#FAST_RESTART_SKIP_FRAMES} dead steps.
 * A stock death on the same path still takes a life and keeps its fade.
 */
class TestFastDeathRestart {
    private static final int FRAME_LIMIT = 600;

    @Nested @RequiresRom(SonicGame.SONIC_1)
    class Sonic1 {
        @Test void fastDeathReloadsAtTheStartWithoutALife() throws Exception { fastRestart("s1", 0, 0, false); }
        @Test void jumpSkipsTheRestOfTheDeathAfterHalfASecond() throws Exception { jumpSkip("s1", 0, 0, false); }
        @Test void stockDeathStillTakesALife() throws Exception { stockDeath(0, 0, false); }
    }

    @Nested @RequiresRom(SonicGame.SONIC_2)
    class Sonic2 {
        @Test void fastDeathReloadsAtTheStartWithoutALife() throws Exception { fastRestart("s2", 0, 0, false); }
        @Test void jumpSkipsTheRestOfTheDeathAfterHalfASecond() throws Exception { jumpSkip("s2", 0, 0, false); }
        @Test void stockDeathStillTakesALife() throws Exception { stockDeath(0, 0, false); }
    }

    @Nested @RequiresRom(SonicGame.SONIC_3K)
    class Sonic3k {
        @Test void fastDeathReloadsAtTheStartWithoutALife() throws Exception { fastRestart("s3k", 0, 0, true); }
        @Test void jumpSkipsTheRestOfTheDeathAfterHalfASecond() throws Exception { jumpSkip("s3k", 0, 0, true); }
        @Test void stockDeathStillTakesALife() throws Exception { stockDeath(0, 0, true); }
    }

    private final List<RunLevelStart> starts = new ArrayList<>();

    @AfterEach
    void tearDown() {
        var session = SessionManager.getCurrentGameplayMode();
        if (session != null) {
            session.endGameplayRun();
        }
    }

    private GameLoop fastRun(String game, int zone, int act, boolean skipIntro, InputHandler input) {
        var builder = HeadlessTestFixture.builder().withZoneAndAct(zone, act);
        if (skipIntro) {
            builder.withSkippedZoneIntro();
        }
        builder.build();
        GameplayRunPolicy policy = GameplayRunPolicy.isolatedAct().withFastRestarts();
        SessionManager.getCurrentGameplayMode().beginGameplayRun(policy, zone, act);
        GameLoop loop = new GameLoop(input);
        loop.setGameMode(GameMode.LEVEL);
        loop.beginHostedRun(new RunSpec(game, zone, act, "sonic", policy), new RunHost() {
            @Override public void onLevelReady(RunLevelStart start) { starts.add(start); }
        }, reason -> { });
        return loop;
    }

    private static AbstractPlayableSprite player() {
        return (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
    }

    private void fastRestart(String game, int zone, int act, boolean skipIntro) throws Exception {
        var input = new InputHandler();
        GameLoop loop = fastRun(game, zone, act, skipIntro, input);
        var state = GameServices.gameState();
        int lives = state.getLives();
        int startX = player().getCentreX();
        // Leave the start, then die there: the reload must bring the player back.
        for (int i = 0; i < 4; i++) loop.step();
        player().setCentreX((short) (startX + 0x200));
        loop.step();
        assertTrue(player().applyCrushDeath());

        int deadSteps = 0;
        boolean reloaded = false;
        for (int i = 0; i < FRAME_LIMIT; i++) {
            loop.step();
            assertNotEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode(), "no title card on a fast restart");
            assertFalse(GameServices.level().isTitleCardRequested(), "no title card is requested");
            if (!starts.isEmpty()) {
                reloaded = true;
                break;
            }
            deadSteps++;
        }
        assertTrue(reloaded, "the act reloads after the death animation");
        assertTrue(deadSteps > GameplayRunPolicy.FAST_RESTART_SKIP_FRAMES + 60,
                "without a skip the whole animation plays (fall plus the 60-step restart delay): " + deadSteps);
        assertEquals(1, starts.size());
        assertTrue(starts.get(0).deathRestart(), "the host hears a death restart");
        assertEquals(lives, state.getLives(), "no life is taken");
        assertFalse(player().getDead());
        assertTrue(Math.abs(player().getCentreX() - startX) < 0x40,
                "the reload starts at the act start, not where the player died: " + player().getCentreX());
        assertFalse(GameServices.level().getCheckpointState().isActive(), "star-post progress is cleared");
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
    }

    private void jumpSkip(String game, int zone, int act, boolean skipIntro) throws Exception {
        var input = new InputHandler();
        GameLoop loop = fastRun(game, zone, act, skipIntro, input);
        int lives = GameServices.gameState().getLives();
        int jump = GameServices.configuration().getInt(SonicConfiguration.JUMP);
        for (int i = 0; i < 4; i++) loop.step();
        assertTrue(player().applyCrushDeath());

        // Presses before half a second of death are ignored.
        for (int i = 0; i < GameplayRunPolicy.FAST_RESTART_SKIP_FRAMES - 2; i += 2) {
            input.handleKeyEvent(jump, GLFW_PRESS);
            loop.step();
            input.handleKeyEvent(jump, GLFW_RELEASE);
            loop.step();
        }
        assertTrue(starts.isEmpty(), "a jump inside the first half second does not skip");
        assertTrue(player().getDead());
        while (((com.openggf.sprites.managers.PlayableSpriteMovement) player().getMovementManager())
                .deathElapsedFrames() < GameplayRunPolicy.FAST_RESTART_SKIP_FRAMES) {
            loop.step();
        }
        input.handleKeyEvent(jump, GLFW_PRESS);
        loop.step();
        input.handleKeyEvent(jump, GLFW_RELEASE);
        assertTrue(starts.isEmpty(), "the skip requests the reload for the next boundary");
        loop.step();
        assertEquals(1, starts.size(), "the jump after half a second reloads at once");
        assertTrue(starts.get(0).deathRestart());
        assertEquals(lives, GameServices.gameState().getLives());
        assertFalse(player().getDead());
    }

    private void stockDeath(int zone, int act, boolean skipIntro) throws Exception {
        var builder = HeadlessTestFixture.builder().withZoneAndAct(zone, act);
        if (skipIntro) {
            builder.withSkippedZoneIntro();
        }
        builder.build();
        GameLoop loop = new GameLoop(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);
        var state = GameServices.gameState();
        int lives = state.getLives();
        for (int i = 0; i < 4; i++) loop.step();
        assertTrue(player().applyCrushDeath());
        boolean fading = false;
        for (int i = 0; i < FRAME_LIMIT && !fading; i++) {
            loop.step();
            fading = loop.resolveFadeManager().isActive();
        }
        assertTrue(fading, "a stock death restarts behind the fade to black");
        assertEquals(lives - 1, state.getLives(), "a stock death takes a life");
    }
}
