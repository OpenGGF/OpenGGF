package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Cold native-width traversal through both replica defeats and teleporters using the production loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszColdRouteCapture {
    @Test void coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-tails-cold-upper-320.bk2"));
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            boolean sawBoss = false; boolean sawDefeat = false;
            boolean sawMtzBoss = false; boolean sawMtzDefeat = false;
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame)); session.render();
                assertFalse(session.player().getDead(), "death at input " + frame);
                var bosses = GameServices.level().getObjectManager().activeObjectsOfType(SszGhzBossObjectInstance.class);
                sawBoss |= !bosses.isEmpty();
                sawDefeat |= bosses.stream().anyMatch(b -> b.hitsRemainingForTest() == 0);
                var mtzBosses = GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance.class);
                sawMtzBoss |= !mtzBosses.isEmpty();
                sawMtzDefeat |= mtzBosses.stream().anyMatch(b -> b.hitsRemainingForTest() == 0);
                if (frame == 7911) {
                    assertEquals(5157, session.player().getCentreX());
                    assertEquals(1004, session.player().getCentreY(), "upper middle walkway after carrier release");
                }
                if (frame == 4472) {
                    assertEquals(0x160, GameServices.camera().getMaxX() & 0xFFFF);
                    assertEquals(0x7C0, GameServices.camera().getY() & 0xFFFF);
                    assertEquals(1, bosses.size());
                    var shipRenderer = GameServices.level().getObjectRenderManager().getRenderer(
                            com.openggf.game.sonic3k.Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
                    assertNotNull(shipRenderer, "the separate Mecha head must not hide a missing Eggmobile renderer");
                    assertTrue(shipRenderer.isReady());
                }
                if (frame == 5826) {
                    assertEquals(512, session.player().getCentreX());
                    assertEquals(1420, session.player().getCentreY(), "first receiving platform");
                }
                if (frame == 6300) {
                    assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.SszElevatorBarObjectInstance.class)
                            .stream().anyMatch(bar -> bar.holdingForTest(0)), "ordinary elevator-bar grab");
                }
                // Traversal, encounter, transport ascent, elevator hold/release and swinging carrier.
                if (frame != 2888 && frame != 3440 && frame != 3540 && frame != 3818
                        && frame != 4168 && frame != 4400 && frame != 4700
                        && frame != 4930 && frame != 5320 && frame != 5400
                        && frame != 6300 && frame != 6459 && frame != 7076
                        && frame != 7460 && frame != 8260 && frame != 8640
                        && frame != 8810 && frame != 9051 && frame != 9160
                        && frame != 10150 && frame != 10270 && frame != 10360) continue;
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); session.render(); }
                var forward = registry.capture();
                registry.restore(saved);
                // External controller held-button history is not part of gameplay rewind.
                previousInput.set(session, movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) { session.step(movie.getFrame(frame + n)); session.render(); }
                var replay = registry.capture();
                assertEquals(forward.entries().keySet(), replay.entries().keySet());
                for (String key : forward.entries().keySet()) {
                    var differences = RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                    assertTrue(differences.isEmpty(), "input " + frame + " " + key + ": " + differences);
                }
                registry.restore(saved); previousInput.set(session, movie.getFrame(frame));
            }
            assertTrue(sawBoss && sawDefeat, "cold encounter must reach the killing hit");
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(SszGhzBossObjectInstance.class).isEmpty());
            var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
            assertTrue((state.eventsBgByte(0) & 0x80) != 0, "native GHZ defeat flag releases the pad");
            assertTrue(sawMtzBoss && sawMtzDefeat, "cold route must also reach the second killing hit");
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance.class).isEmpty());
            assertEquals(5888, session.player().getCentreX());
            assertEquals(140, session.player().getCentreY(), "upper platform after the second transport pad");
            assertFalse(session.player().isObjectControlled(), "transport releases player control");
        }
    }
}
