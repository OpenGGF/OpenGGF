package com.openggf.game.sonic3k;

import com.openggf.GameLoop;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fresh-level fade from black belongs to the S3K {@code Level} routine.
 * {@code Level/loc_64DC} (docs/skdisasm/sonic3k.asm:7906-7910) sets
 * {@code Palette_fade_info = $202F} (lines 1-3), runs {@code Pal_FillBlack} and
 * arms {@code Palette_fade_timer = $16}; {@code Animate_Palette} (5018-5028)
 * spends one {@code Pal_FromBlack} pass per LevelLoop frame. Lines 1-3 hold
 * {@code Level_frame_counter} passes and are complete at 21; line 0 never
 * fades. It runs with or without a presented title card, and a seamless
 * reload never reaches loc_64DC.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLevelPaletteFadeOwner {
    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void passesFollowThePaletteFadeTimer() {
        assertEquals(0, Sonic3kLevelAnimationManager.levelFadePassesAt(0x16), "loc_64DC: Pal_FillBlack");
        assertEquals(1, Sonic3kLevelAnimationManager.levelFadePassesAt(0x15));
        assertEquals(20, Sonic3kLevelAnimationManager.levelFadePassesAt(2));
        assertEquals(-1, Sonic3kLevelAnimationManager.levelFadePassesAt(1), "21 passes: every colour arrived");
        assertEquals(-1, Sonic3kLevelAnimationManager.levelFadePassesAt(0), "Animate_Palette reached AnPal");
        assertEquals(-1, Sonic3kLevelAnimationManager.levelFadePassesAt((short) 0xFF00), "event ST: BMI");
    }

    @Test
    void cardSkippingLoadFadesLinesOneToThreeFromItsFirstFrame() {
        HeadlessTestFixture fixture = knucklesFixture(22, 1);
        Map<Integer, String> observed = new TreeMap<>();
        record(observed);
        for (int frame = 0; frame < 24; frame++) {
            fixture.stepIdleFrames(1);
            record(observed);
        }
        assertNativeLevelFade(observed);
    }

    @Test
    void rewindRestoresTheLevelFadeFromTheCapturedTimer() {
        HeadlessTestFixture fixture = knucklesFixture(22, 1);
        fixture.stepIdleFrames(5);
        var animator = (Sonic3kLevelAnimationManager) GameServices.level().getAnimatedPatternManager();
        var duringFade = animator.capture();
        fixture.stepIdleFrames(20);
        assertEquals("none", presentation(), "the fade completed at counter 21");
        var afterFade = animator.capture();

        animator.restore(duringFade);
        assertEquals(fade(5), presentation(), "restore re-applies the captured counter-5 fade");
        fixture.stepIdleFrames(1);
        assertEquals(fade(6), presentation(), "forward replay continues the fade");

        animator.restore(afterFade);
        assertEquals("none", presentation(), "restoring after the fade releases it");
    }

    @Test
    void seamlessReloadNeverReachesLoc64DC() throws Exception {
        SharedLevel shared = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder().withSharedLevel(shared).build();
            fixture.stepIdleFrames(30);
            GameServices.level().executeActTransition(SeamlessLevelTransitionRequest
                    .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_SAME_LEVEL)
                    .targetZoneAct(0, 0)
                    .preserveMusic(true)
                    .build());
            for (int frame = 0; frame < 24; frame++) {
                assertEquals("none", presentation(), "seamless frame " + frame);
                fixture.stepIdleFrames(1);
            }
        } finally {
            shared.dispose();
        }
    }

    @Test
    void specialStageReturnFadesBehindTheFreshCardHold() throws Exception {
        SharedLevel shared = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            GameServices.level().consumePendingInitialProcessSpritesPass();
            GameLoop loop = new GameLoop(new InputHandler());
            loop.setGameplayMode(TestEnvironment.activeGameplayMode());
            setField(loop, "currentGameMode", GameMode.SPECIAL_STAGE_RESULTS);
            Method exit = GameLoop.class.getDeclaredMethod("doExitResultsScreen");
            exit.setAccessible(true);
            exit.invoke(loop);
            Map<Integer, String> observed = new TreeMap<>();
            for (int frame = 0; frame < 400 && !observed.containsKey(23); frame++) {
                loop.step();
                if (loop.getCurrentGameMode() == GameMode.LEVEL) {
                    record(observed);
                }
            }
            assertNativeLevelFade(observed);
        } finally {
            shared.dispose();
        }
    }

    @Test
    void bonusStageFadesInOnceLevelLoopRunsWithoutACardFade() throws Exception {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(0x00, 0).build();
        GameLoop loop = new GameLoop(new InputHandler());
        BonusStageProvider provider = GameModuleRegistry.getCurrent().getBonusStageProvider();
        Method enter = GameLoop.class.getDeclaredMethod(
                "doEnterBonusStage", BonusStageProvider.class, BonusStageType.class, BonusStageState.class);
        enter.setAccessible(true);
        enter.invoke(loop, provider, BonusStageType.GLOWING_SPHERE, savedState(fixture.sprite(), fixture.camera()));
        Map<Integer, String> observed = new TreeMap<>();
        for (int frame = 0; frame < 400 && !observed.containsKey(23); frame++) {
            loop.step();
            if (loop.getCurrentGameMode() == GameMode.TITLE_CARD) {
                var card = (Sonic3kTitleCardManager) loop.getTitleCardProvider();
                assertTrue(card.drawsOpaqueLevelBackdrop(), "the locked bonus card covers the stage: "
                        + card.getStateName() + "/" + card.getStateTimer());
            }
            if (loop.getCurrentGameMode() == GameMode.BONUS_STAGE) {
                record(observed);
            }
        }
        assertNativeLevelFade(observed);
    }

    @Test
    void deathRestartFadesTheLevelOnceItsLoopRuns() throws Exception {
        knucklesFixture(9, 0);
        GameLoop loop = new GameLoop(new InputHandler());
        Method level = GameLoop.class.getDeclaredMethod("changeGameModeWithoutRewindBoundary", GameMode.class);
        level.setAccessible(true);
        level.invoke(loop, GameMode.LEVEL);
        for (int frame = 0; frame < 40; frame++) {
            loop.step();
        }
        assertEquals("none", presentation(), "the first entry's fade is complete");
        GameServices.level().requestRespawn();
        Map<Integer, String> observed = new TreeMap<>();
        boolean restarted = false;
        for (int frame = 0; frame < 400 && !observed.containsKey(23); frame++) {
            loop.step();
            restarted |= loop.getCurrentGameMode() == GameMode.TITLE_CARD;
            if (restarted && loop.getCurrentGameMode() == GameMode.LEVEL) {
                record(observed);
            }
        }
        assertTrue(restarted, "the restart presented its title card");
        assertNativeLevelFade(observed);
    }

    private static HeadlessTestFixture knucklesFixture(int zone, int act) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        return HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
    }

    private static void record(Map<Integer, String> observed) {
        String value = presentation() + " host=" + GameServices.fade().getState();
        int counter = GameServices.level().getFrameCounter();
        String first = observed.putIfAbsent(counter, value);
        if (first != null && !first.equals(value)) {
            observed.put(counter, first + " | " + value);
        }
    }

    private static void assertNativeLevelFade(Map<Integer, String> observed) {
        assertTrue(observed.containsKey(22), "the level ran past its fade: " + observed);
        for (int counter = 0; counter <= 22; counter++) {
            String fade = counter < PaletteFadePresentation.COLOR_STEPS ? fade(counter) : "none";
            assertEquals(fade + " host=" + FadeManager.FadeState.NONE, observed.get(counter),
                    "Level_frame_counter " + counter + " of " + observed);
        }
    }

    private static String fade(int passes) {
        return PaletteFadePresentation.Mode.FROM_BLACK + "/" + passes + "/" + PaletteFadePresentation.LINES_1_TO_3;
    }

    private static String presentation() {
        var fade = GameServices.graphics().getPaletteFadePresentation();
        return fade.isActive() ? fade.mode() + "/" + fade.steps() + "/" + fade.lineMask() : "none";
    }

    private static BonusStageState savedState(AbstractPlayableSprite player, Camera camera) {
        LevelManager levelManager = GameServices.level();
        int zoneAndAct = (levelManager.getCurrentZone() << 8) | levelManager.getCurrentAct();
        int apparent = (levelManager.getCurrentZone() << 8) | levelManager.getApparentAct();
        return new BonusStageState(zoneAndAct, apparent, 0, 0, 0, 0, 0, 0,
                player.getCentreX(), player.getCentreY(), camera.getX(), camera.getY(),
                player.getTopSolidBit(), player.getLrbSolidBit(), camera.getMaxY(), 0);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
