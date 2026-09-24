package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.MhzShipSequenceControllerInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Cold MHZ1 incoming route through MHZ2 and the playable FBZ load; no gameplay seeds. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzAct2AuthoredRoute {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test void incomingSonicCompletesActTwoWithLiveRewindBoundaries() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0)
                .withFreshLevelStartLifecycle().build();
        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/mhz2-sonic-incoming-320.bk2"));
        assertEquals(23775, movie.getFrameCount());
        fixture.runner().primeInputState(movie.getFrame(0));
        var checked = new HashSet<String>();
        // Captures omit title-card presentation; the recording driver retains
        // the native title owner. Allow neutral input for that destination tail.
        int frameLimit = movie.getFrameCount() + 600;
        for (int frame = 1; frame < frameLimit; frame++) {
            step(fixture, inputAt(movie, frame));
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), "cold route death at " + frame);
            var level = GameServices.level();
            var manager = level.getObjectManager();
            var boss = manager.activeObjectsOfType(MhzEndBossInstance.class).stream().findFirst().orElse(null);
            String spot = null;
            if (boss != null && boss.getCustomFlag(0x100) != 0) {
                if (boss.getState().hitCount == 9) spot = "admission";
                if (boss.getState().hitCount == 5) spot = "chase-hit";
                if (boss.getState().hitCount == 1) spot = "last-hit";
                if (boss.isDefeated()) spot = "defeat";
                if (boss.isDefeated() && GameServices.gameState().isEndOfLevelActive()) spot = "capsule-results";
                if (!manager.activeObjectsOfType(MhzShipSequenceControllerInstance.class).isEmpty()) spot = "ship";
                if (GameServices.sprites().getMainPlayable().isObjectControlled()
                        && spot != null && spot.equals("ship")) spot = "ship-carry";
            }
            // The fresh-load row still belongs to the non-rewindable title/fade
            // boundary (RecordingFrameDriver deliberately runs no gameplay there).
            // Observe the released destination, not a mid-load registry snapshot.
            if (level.getCurrentZone() == 4 && level.getCurrentAct() == 0
                    && !level.hasPendingFreshLevelTransitionBoundary()
                    && !SessionManager.getCurrentGameplayMode().getFadeManager().hasPendingCompletion()
                    && level.getFrameCounter() > 0) spot = "fbz-loaded";
            if (spot != null && checked.add(spot)) {
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                int horizon = Math.min(45, frameLimit - frame - 1);
                for (int n = 1; n <= horizon; n++) step(fixture, inputAt(movie, frame + n));
                var expected = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), spot + " restore at " + frame);
                fixture.runner().primeInputState(inputAt(movie, frame));
                for (int n = 1; n <= horizon; n++) step(fixture, inputAt(movie, frame + n));
                same(expected, registry.capture(), spot + " replay at " + frame);
                registry.restore(saved);
                fixture.runner().primeInputState(inputAt(movie, frame));
                if (spot.equals("fbz-loaded")) break;
            }
        }
        assertEquals(Set.of("admission", "chase-hit", "last-hit", "defeat", "capsule-results",
                "ship", "ship-carry", "fbz-loaded"), checked);
        assertEquals(4, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
        assertFalse(GameServices.sprites().getMainPlayable().isObjectControlled());
    }

    private static Bk2FrameInput inputAt(Bk2Movie movie, int frame) {
        return frame < movie.getFrameCount() ? movie.getFrame(frame)
                : new Bk2FrameInput(frame, 0, 0, false, "neutral destination tail");
    }

    private static void step(HeadlessTestFixture fixture, Bk2FrameInput input) {
        int mask = input.p1InputMask();
        fixture.stepFrame((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0,
                (mask & 8) != 0, (mask & 16) != 0);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences
                    + (expected.get(key) instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot objects
                    ? " dynamic classes=" + objects.dynamicObjects().stream()
                            .map(entry -> entry.objectId() + "=" + entry.className()).toList() : ""));
        }
    }
}
