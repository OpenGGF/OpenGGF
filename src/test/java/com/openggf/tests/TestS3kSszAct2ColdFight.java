package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Controller-only cold arrival, eight real hits, transformation, and replay into Super Mecha. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszAct2ColdFight {
    private static final Path INPUT = Path.of("src/test/resources/routes/s3k/ssz2-first-defeat.bk2");
    // Authored input milestones, not native timings or runtime behavior gates.
    private static final int FIRST_DEFEAT = 3294;
    private static final int SUPER_ENTRY = 4016;

    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void eightRealHitsReachSuperMechaAndTheTransformationReplays(int width) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1)
                .withFreshLevelStartLifecycle().build();
        // Declared entry progress/clock. Positions, velocities, collisions and boss health
        // come only from production gameplay; no physics trace is opened by this test.
        GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7, 3), true);
        GameServices.level().getObjectManager().initVblaCounter(410766);
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(INPUT);
        assertEquals(SUPER_ENTRY + 1, movie.getFrameCount());
        fixture.runner().primeInputState(movie.getFrame(0));
        int hitsSeen = 0, previousHealth = 8;
        for (int frame = 1; frame <= FIRST_DEFEAT; frame++) {
            step(fixture, movie.getFrame(frame));
            assertFalse(fixture.sprite().getDead(), "cold route died at " + frame);
            var bosses = GameServices.level().getObjectManager().activeObjectsOfType(SszMechaSonicObjectInstance.class);
            if (!bosses.isEmpty()) {
                int health = bosses.getFirst().getCollisionProperty();
                if (health < previousHealth) {
                    assertEquals(previousHealth - 1, health, "each collision deals one hit");
                    hitsSeen++; previousHealth = health;
                }
            }
        }
        assertEquals(8, hitsSeen);
        assertTrue(boss().defeatedForTest());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var defeated = registry.capture();
        int[] expected = null;
        byte[] expectedRuntime = null;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(defeated);
                fixture.runner().primeInputState(movie.getFrame(FIRST_DEFEAT));
            }
            for (int frame = FIRST_DEFEAT + 1; frame <= SUPER_ENTRY; frame++) step(fixture, movie.getFrame(frame));
            var player = GameServices.sprites().getMainPlayable();
            assertFalse(player.getDead());
            assertFalse(boss().defeatedForTest(), "loc_7BBE0 returns with eight Super-phase hits");
            assertEquals(8, boss().getCollisionProperty());
            assertEquals(2, boss().routineForTest());
            assertFalse(player.isObjectControlled(), "forced run must return control");
            int[] observed = {boss().getX(), boss().getY(), boss().routineForTest(), boss().timerForTest(),
                    boss().xVelForTest(), boss().yVelForTest(), player.getCentreX(), player.getCentreY(),
                    GameServices.camera().getX(), GameServices.camera().getY()};
            byte[] runtime = ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).captureBytes();
            if (replay == 0) { expected = observed; expectedRuntime = runtime; }
            else { assertArrayEquals(expected, observed); assertArrayEquals(expectedRuntime, runtime); }
        }
    }

    private static SszMechaSonicObjectInstance boss() {
        return GameServices.level().getObjectManager().activeObjectsOfType(SszMechaSonicObjectInstance.class).getFirst();
    }

    private static void step(HeadlessTestFixture fixture, Bk2FrameInput input) {
        int mask = input.p1InputMask();
        fixture.stepFrame((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0,
                (mask & 8) != 0, (mask & 16) != 0);
    }
}
