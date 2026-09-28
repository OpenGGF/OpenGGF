package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzDoorObjectInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary cold Act1 entry owns every preceding clock and object admission. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzWideActTwoColdRouteCapture {
    @Test
    void coldTeamCompletesWideActTwoWithRepeatableWorldAndIsolatedBossHistory() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz2-sonic-tails-cold-boss-act-arrival-800.bk2"));
        assertEquals(41348, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        // Periodic traversal plus mechanism admission/release, both doors, ceiling
        // movement, collapsing bridges and the boulder. Windows never cross a load.
        var spots = new TreeSet<Integer>();
        for (int frame = 25600; frame <= 40800; frame += 400) spots.add(frame);
        spots.addAll(Set.of(25650, 25690, 26510, 26555, 26660, 26800, 27060,
                27150, 27530, 27750, 27810, 27880, 27940, 28060, 28660, 28960,
                29080, 29310, 29540, 29740, 29980, 30140, 30220, 30380, 30600,
                31000, 31220, 31750, 31920, 32000, 34000, 34075, 34320, 34600,
                34800, 35020, 35310, 35570, 35760, 35820, 36010, 36220, 36550,
                36850, 36960, 37070, 37110, 37220, 37260, 37400, 37520, 37580,
                37660, 37780, 37950, 38000, 38110, 38500, 38700, 38890, 39050,
                39120, 39260, 39880, 40650, 40720, 40900, 41070, 41200, 41270));
        var checked = new HashSet<Integer>();
        long outgoingHistory = 0;
        int loadFrame = -1;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 40700) GameServices.configuration().setSessionOverride(
                        SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                assertFalse(session.player().isSuperSonic());
                assertInstanceOf(Sonic.class, session.player());
                assertEquals(800, GameServices.camera().getWidth());
                assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
                assertEquals(frame < 41127 ? 9 : 22, GameServices.level().getCurrentZone());
                if (frame >= 25600 && frame < 41127) {
                    assertEquals(1, GameServices.level().getCurrentAct());
                }
                if (frame >= 40700 && frame < 41127) {
                    outgoingHistory = Math.max(outgoingHistory,
                            SessionManager.getCurrentGameplayMode().getRewindController().currentFrame());
                }
                if (GameServices.level().getCurrentZone() == 22 && loadFrame < 0) loadFrame = frame;
                if (frame == 31000 || frame == 37080) {
                    int trigger = frame == 31000 ? 8 : 6;
                    assertTrue(GameServices.level().getObjectManager()
                            .activeObjectsOfType(LrzDoorObjectInstance.class).stream()
                            .anyMatch(door -> door.triggerIndex() == trigger && door.isFullyOpen()),
                            "ordinary button approach opens door " + trigger);
                }
                if (frame == 41120) {
                    assertTrue(session.player().isObjectControlled(), "boulder owns the descent");
                    assertEquals(9, session.player().getRingCount());
                }
                // loc_59B1C consumes the continuation bank at the destination
                // screen-event boundary, after the raw fresh-player load frame.
                if (frame == 41128) assertEquals(9, session.player().getRingCount());
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); session.render(); }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); session.render(); }
                    var replay = registry.capture();
                    assertEquals(forward.entries().keySet(), replay.entries().keySet());
                    for (String key : forward.entries().keySet()) {
                        var diff = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                        assertTrue(diff.isEmpty(), "input " + frame + " cycle " + cycle + " " + key + ": " + diff);
                    }
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertEquals(41127, loadFrame);
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(296, session.player().getCentreX());
            assertEquals(1196, session.player().getCentreY());
            assertEquals(10, session.player().getRingCount());
            assertFalse(session.player().isObjectControlled());
            assertTrue(outgoingHistory > 10, "record actual outgoing Act2 history");
            var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(rewind.currentFrame() < outgoingHistory, "full boss-act load retires the outgoing timeline");
            System.out.println("LRZ wide cold Act2 clear: " + checked.size() + " two-cycle rewind windows");
        }
    }
}
