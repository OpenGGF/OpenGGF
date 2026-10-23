package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunInput;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.run.RunStep;
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

/**
 * The hosted-run pieces a fast restart's lead-in and a Time Attack act start use, through the
 * production GameLoop: a host can run steps with the player's input held (recorded as no
 * buttons), {@link RunInput#levelFadingIn()} reports the reloaded level's own palette fade
 * (S3K) and nothing for S1, and a {@code withoutZoneIntros()} policy starts S3K AIZ1 where its
 * plane intro hands Sonic control, on every reload.
 */
class TestHostedRunLeadIn {
    private static final int FRAME_LIMIT = 600;

    @Nested @RequiresRom(SonicGame.SONIC_1)
    class Sonic1 {
        @Test void aMidActRetryKeepsNativeS1GameplayRunningUnderTheReloadFade() {
            assertHostedS1RetryFade(false);
        }

        @Test void aCompletedActRetryStillHoldsNativeS1GameplayUnderTheReloadFade() {
            assertHostedS1RetryFade(true);
        }

        @Test void aReloadedS1LevelNeverReportsItsOwnFade() throws Exception {
            Recorder host = new Recorder();
            GameLoop loop = run("s1", false, GameplayRunPolicy.isolatedAct().withFastRestarts(), host);
            dieAndReload(loop, host);
            for (int i = 0; i < 30; i++) loop.step();
            assertFalse(host.fading.contains(true), "S1 has no level-owned palette fade");
        }
    }

    @Nested @RequiresRom(SonicGame.SONIC_3K)
    class Sonic3k {
        @Test void heldInputStepsAdvanceTheWorldButNotThePlayer() throws Exception {
            Recorder host = new Recorder();
            host.holdInput = true;
            var input = new InputHandler();
            GameLoop loop = run("s3k", true, GameplayRunPolicy.isolatedAct().withFastRestarts(), host, input);
            for (int i = 0; i < 30; i++) loop.step();
            int x = player().getCentreX();
            input.handleKeyEvent(GameServices.configuration().getInt(SonicConfiguration.RIGHT), GLFW_PRESS);
            host.steps.clear();
            for (int i = 0; i < 30; i++) loop.step();
            assertEquals(x, player().getCentreX(), "held input never reaches the player");
            assertEquals(30, host.steps.size(), "held-input steps still execute");
            assertTrue(host.steps.stream().allMatch(step -> step.heldMask() == 0),
                    "held-input steps record no buttons");

            host.holdInput = false;
            host.steps.clear();
            for (int i = 0; i < 30; i++) loop.step();
            assertTrue(player().getCentreX() > x, "released input moves the player again");
            assertTrue(host.steps.stream().allMatch(step -> step.heldMask() != 0));
        }

        @Test void aReloadedS3kLevelReportsItsFadeUntilItIsVisible() throws Exception {
            Recorder host = new Recorder();
            GameLoop loop = run("s3k", true, GameplayRunPolicy.isolatedAct().withFastRestarts(), host);
            dieAndReload(loop, host);
            host.fading.clear();
            for (int i = 0; i < 40; i++) loop.step();
            assertTrue(host.fading.get(0), "the reload starts dark: Level/loc_64DC arms the fade");
            int fadingFrames = host.fading.indexOf(false);
            assertTrue(fadingFrames > 15 && fadingFrames < 30,
                    "the $16-step Palette_fade_timer fade ends within its steps: " + fadingFrames);
            assertFalse(host.fading.subList(fadingFrames, host.fading.size()).contains(true));
        }

        @Test void withoutZoneIntrosStartsAiz1WhereTheIntroHandsOver() throws Exception {
            var config = GameServices.configuration();
            Object previous = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            try {
                Recorder withIntro = new Recorder();
                GameLoop loop = run("s3k", false, GameplayRunPolicy.isolatedAct().withFastRestarts(),
                        withIntro);
                dieAndReload(loop, withIntro);
                assertFalse(GameServices.camera().isLevelStarted(), "a stock policy replays the plane intro");
                int introX = player().getCentreX();
                for (int i = 0; i < 40; i++) loop.step(); // let the reload's fade from black finish
                SessionManager.getCurrentGameplayMode().endGameplayRun();

                Recorder skipping = new Recorder();
                loop = run("s3k", false,
                        GameplayRunPolicy.isolatedAct().withFastRestarts().withoutZoneIntros(), skipping);
                dieAndReload(loop, skipping);
                assertTrue(GameServices.camera().isLevelStarted(), "the reload starts after the intro");
                assertNotEquals(introX, player().getCentreX(), "the reload uses the intro-skip start");
                assertFalse(player().getDead());
            } finally {
                config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, previous != null ? previous : false);
            }
        }
    }

    @AfterEach
    void tearDown() {
        var session = SessionManager.getCurrentGameplayMode();
        if (session != null) {
            session.endGameplayRun();
        }
    }

    /** Records what the engine shows the host; optionally holds the player's input. */
    private static final class Recorder implements RunHost {
        final List<RunLevelStart> starts = new ArrayList<>();
        final List<Boolean> fading = new ArrayList<>();
        final List<RunStep> steps = new ArrayList<>();
        boolean holdInput;
        boolean retryAtCompletion;
        RunHandle handle;

        @Override public boolean retryOnActCompletion() { return retryAtCompletion; }
        @Override public void onLevelReady(RunLevelStart start) { starts.add(start); }
        @Override public boolean admitStep(RunInput input) {
            fading.add(input.levelFadingIn());
            return true;
        }
        @Override public boolean holdsPlayerInput() { return holdInput; }
        @Override public void afterStep(RunStep step) { steps.add(step); }
    }

    private GameLoop run(String game, boolean skipIntro, GameplayRunPolicy policy, Recorder host) {
        return run(game, skipIntro, policy, host, new InputHandler());
    }

    private GameLoop run(String game, boolean skipIntro, GameplayRunPolicy policy, Recorder host,
                         InputHandler input) {
        var builder = HeadlessTestFixture.builder().withZoneAndAct(0, 0);
        if (skipIntro) {
            builder.withSkippedZoneIntro();
        }
        builder.build();
        SessionManager.getCurrentGameplayMode().beginGameplayRun(policy, 0, 0);
        GameLoop loop = new GameLoop(input);
        loop.setGameMode(GameMode.LEVEL);
        host.handle = loop.beginHostedRun(new RunSpec(game, 0, 0, "sonic", policy), host, reason -> { });
        return loop;
    }

    private void assertHostedS1RetryFade(boolean actCompleted) {
        Recorder host = new Recorder();
        GameLoop loop = run("s1", false, GameplayRunPolicy.isolatedAct(), host);
        for (int i = 0; i < 4; i++) loop.step();
        var level = GameServices.level();
        assertTrue(GameServices.module().getLevelInitProfile()
                instanceof com.openggf.game.internal.BlockingLevelEntryFade);
        player().setAir(true);
        player().setYSpeed((short) -0x200);
        if (actCompleted) {
            host.retryAtCompletion = true;
            level.getTransitions().requestHostReturn();
        } else {
            host.handle.retry();
        }
        loop.step(); // host retry publishes its selected deactivation policy
        assertEquals(actCompleted, level.isLevelInactiveForTransition());
        loop.step(); // ordinary request consumer starts the reload fade
        assertTrue(SessionManager.getCurrentGameplayMode().getFadeManager().hasPendingCompletion());
        assertEquals(actCompleted, level.isLevelInactiveForTransition(),
                "an active host owns the request's blocking policy");
        int objects = level.getObjectManager().getFrameCounter();
        int afterSteps = host.steps.size();
        int y = player().getCentreY();
        int fade = SessionManager.getCurrentGameplayMode().getFadeManager().getFrameCount();
        for (int i = 0; i < 4; i++) loop.step();
        assertEquals(objects + (actCompleted ? 0 : 4), level.getObjectManager().getFrameCounter());
        assertEquals(afterSteps + (actCompleted ? 0 : 4), host.steps.size());
        if (actCompleted) assertEquals(y, player().getCentreY());
        else assertNotEquals(y, player().getCentreY(), "the real airborne player keeps moving");
        assertTrue(SessionManager.getCurrentGameplayMode().getFadeManager().getFrameCount() > fade);
        assertEquals(actCompleted, level.isLevelInactiveForTransition());
        assertTrue(host.starts.isEmpty(), "the four observed passes precede the reload callback");
    }

    private static void dieAndReload(GameLoop loop, Recorder host) {
        for (int i = 0; i < 4; i++) loop.step();
        assertTrue(player().applyCrushDeath());
        for (int i = 0; i < FRAME_LIMIT && host.starts.isEmpty(); i++) loop.step();
        assertEquals(1, host.starts.size(), "the death reloads the act");
        assertTrue(host.starts.get(0).deathRestart());
    }

    private static AbstractPlayableSprite player() {
        return (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
    }
}
