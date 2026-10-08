package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.MhzMinibossInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh controller-only MHZ1 completion; no trace or seeded boss/player state. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzTailsAuthoredRoute {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test void freshTailsCompletesActOneAndReplaysItsLiveInteractions() throws Exception {
        var config = SonicConfigurationService.getInstance(); config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0).withFreshLevelStartLifecycle().build();
        assertEquals(320, fixture.camera().getWidth());
        assertEquals("tails", fixture.sprite().getCode());
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz1-tails-cold-complete-320.bk2"));
        assertEquals(24121, movie.getFrameCount());
        fixture.runner().primeInputState(movie.getFrame(0));
        var checked = new HashSet<String>();
        int firstActTwoX = -1;
        for (int frame = 1; frame < movie.getFrameCount(); frame++) {
            step(fixture, movie.getFrame(frame));
            var player = fixture.sprite(); var level = GameServices.level();
            assertFalse(player.getDead(), "cold route death at " + frame);
            var boss = level.getObjectManager().activeObjectsOfType(MhzMinibossInstance.class)
                    .stream().findFirst().orElse(null);
            String spot = null;
            if (level.getCurrentAct() == 0) {
                if (frame == 1500) spot = "early-traversal";
                if (frame == 18800) spot = "lower-approach";
                if (frame == 21500) spot = "upper-flight";
                if (boss != null && boss.getState().hitCount == 5) spot = "first-hit";
                if (boss != null && boss.getState().hitCount == 1) spot = "last-hit-approach";
                if (boss != null && boss.isDefeated()) spot = "defeat";
            } else if (level.getCurrentAct() == 1) {
                if (firstActTwoX < 0) firstActTwoX = player.getCentreX();
                if (!player.isObjectControlled() && player.getCentreX() > 700) spot = "act-two-released";
            }
            if (spot != null && checked.add(spot)) {
                var registry = fixture.gameplayMode().getRewindRegistry();
                var saved = registry.capture();
                int horizon = Math.min(45, movie.getFrameCount() - frame - 1);
                for (int n = 1; n <= horizon; n++) step(fixture, movie.getFrame(frame + n));
                var expected = registry.capture();
                registry.restore(saved); same(saved, registry.capture(), "restore " + spot + " at " + frame);
                fixture.runner().primeInputState(movie.getFrame(frame));
                for (int n = 1; n <= horizon; n++) step(fixture, movie.getFrame(frame + n));
                same(expected, registry.capture(), "replay " + spot + " at " + frame);
                registry.restore(saved); fixture.runner().primeInputState(movie.getFrame(frame));
            }
        }
        assertEquals(java.util.Set.of("early-traversal", "lower-approach", "upper-flight", "first-hit", "last-hit-approach", "defeat", "act-two-released"), checked);
        assertEquals(7, GameServices.level().getCurrentZone());
        assertEquals(1, GameServices.level().getCurrentAct());
        // The next act may already own control for the leaf-blower cutscene.
        // The earlier act-two-released spot proves the title handoff released it.
        assertTrue(fixture.sprite().getCentreX() > firstActTwoX + 300, "real movement after seamless title handoff");
    }

    private static void step(HeadlessTestFixture fixture, Bk2FrameInput input) {
        int mask = input.p1InputMask();
        fixture.stepFrame((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0);
    }

    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
