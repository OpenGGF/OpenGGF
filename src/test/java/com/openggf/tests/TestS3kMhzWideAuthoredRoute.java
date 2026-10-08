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
class TestS3kMhzWideAuthoredRoute {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test void wideSonicCompletesActOneAndReplaysItsLiveInteractions() throws Exception {
        var config = SonicConfigurationService.getInstance(); config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "SUPER_32_9");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 800);
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0).withFreshLevelStartLifecycle().build();
        assertEquals(800, fixture.camera().getWidth());
        assertEquals("sonic", fixture.sprite().getCode());
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz1-sonic-cold-complete-800.bk2"));
        assertEquals(10330, movie.getFrameCount());
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
                if (frame == 3070) spot = "spike-passage";
                if (frame == 4500) spot = "lower-loop";
                if (frame == 4824) spot = "sticky-vine";
                if (frame == 5850) spot = "upper-loop";
                if (frame == 7120) spot = "before-pulley";
                if (frame == 7350) spot = "pulley-pull";
                if (frame == 7700) spot = "pulley-release";
                if (frame == 8100) spot = "arena-approach";
                if (boss != null && boss.getState().hitCount >= 0 && boss.getState().hitCount < 6)
                    spot = "boss-hp-" + boss.getState().hitCount;
                if (boss != null && boss.isDefeated()) spot = "defeat";
            } else if (level.getCurrentAct() == 1) {
                if (firstActTwoX < 0) firstActTwoX = player.getCentreX();
                if ((fixture.camera().getMaxX() & 0xffff) == 0x98) {
                    assertTrue(com.openggf.game.internal.NativeArenaCameraFraming.current().centerNativeArenaCamera(),
                            "incoming results retain native camera projection at " + frame);
                    assertEquals(-88, fixture.camera().getX(), "centered inherited results window at " + frame);
                    if (frame == 9340) spot = "incoming-results";
                }
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
        assertEquals(java.util.Set.of("early-traversal", "spike-passage", "lower-loop", "sticky-vine",
                "upper-loop", "before-pulley", "pulley-pull", "pulley-release", "arena-approach",
                "boss-hp-1", "boss-hp-2", "boss-hp-3", "boss-hp-4", "boss-hp-5", "defeat", "incoming-results", "act-two-released"), checked);
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
