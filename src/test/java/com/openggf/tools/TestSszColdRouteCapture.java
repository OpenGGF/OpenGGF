package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Cold native-width SSZ traversal, bosses and DEZ handover using the production loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszColdRouteCapture {
    @Test void coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport() throws Exception {
        runColdRoute(false);
    }

    @Test void coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents() throws Exception {
        runColdRoute(true);
    }

    private void runColdRoute(boolean complete) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ssz1-sonic-tails-cold-"
                        + (complete ? "complete" : "upper") + "-320.bk2"));
        // Keep the qualified SSZ branch/history inlet; the independent neutral
        // tail allows a full one-owner fade and native DEZ entrance release.
        int routePrefixFrames = movie.getFrameCount() - (complete ? 180 : 0);
        int historyStart = routePrefixFrames - 120;
        int lastReplaySpot = routePrefixFrames - 170;
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            boolean sawBoss = false; boolean sawDefeat = false;
            boolean sawMtzBoss = false; boolean sawMtzDefeat = false;
            long largestOutgoingRewindFrame = 0;
            boolean sawMecha = false; boolean sawMechaDefeat = false;
            int destinationLoaded = -1, destinationAssembled = -1, checkedWindows = 0;
            boolean isolatedHistory = false;
            var incomingWindows = new java.util.HashSet<String>();
            var outgoingManager = GameServices.level().getObjectManager();
            var outgoingRuntime = GameServices.zoneRuntimeState();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // Arm live history only after the independent snapshot replay spots.
                // This exercises the real SSZ→DEZ boundary without storing the entire route.
                if (complete && frame == historyStart) {
                    GameServices.configuration().setSessionOverride(
                            com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                }
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                if (complete && frame >= historyStart && GameServices.level().getCurrentZone() == 10) {
                    var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                    if (rewind != null) largestOutgoingRewindFrame = Math.max(largestOutgoingRewindFrame,
                            rewind.currentFrame());
                }
                assertFalse(session.player().getDead(), "death at input " + frame);
                var bosses = GameServices.level().getObjectManager().activeObjectsOfType(SszGhzBossObjectInstance.class);
                sawBoss |= !bosses.isEmpty();
                sawDefeat |= bosses.stream().anyMatch(b -> b.hitsRemainingForTest() == 0);
                var mtzBosses = GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance.class);
                sawMtzBoss |= !mtzBosses.isEmpty();
                sawMtzDefeat |= mtzBosses.stream().anyMatch(b -> b.hitsRemainingForTest() == 0);
                var mecha = GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance.class);
                sawMecha |= !mecha.isEmpty();
                sawMechaDefeat |= mecha.stream().anyMatch(b -> b.getCollisionProperty() == 0);
                String incomingSpot = null;
                if (complete && GameServices.level().getCurrentZone() == 11) {
                    var level = GameServices.level();
                    var manager = level.getObjectManager();
                    if (destinationLoaded < 0) destinationLoaded = frame;
                    if (destinationAssembled < 0 && !level.hasPendingFreshLevelTransitionBoundary()
                            && !level.getTransitions().isTitleCardRequested()) {
                        destinationAssembled = frame;
                        assertNotSame(outgoingManager, manager);
                        assertNotSame(outgoingRuntime, GameServices.zoneRuntimeState());
                        assertSame(GameServices.sprites().getMainPlayable(), session.player());
                        assertSame(session.player(), GameServices.camera().getFocusedSprite());
                        assertFalse(session.player().isObjectControlled());
                        assertTrue(session.player().isControlLocked(), "loc_44A26 owns the DEZ entrance");
                        assertEquals(0, session.player().getRingCount(), "loc_63B4 publishes fresh rings");
                        assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
                        assertEquals(48, session.player().getCentreX());
                        assertEquals(2476, session.player().getCentreY());
                        assertFalse(manager.activeObjectsOfType(
                                com.openggf.game.sonic3k.objects.LevelIntroPlayerRunInstance.class).isEmpty());
                        incomingSpot = "dez-intro";
                    }
                    var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                    if (!isolatedHistory && rewind != null) {
                        assertTrue(largestOutgoingRewindFrame > 10, "outgoing SSZ history exists");
                        assertTrue(rewind.currentFrame() < largestOutgoingRewindFrame,
                                "DEZ starts a fresh timeline at its actual boundary");
                        isolatedHistory = true;
                    }
                    if (destinationAssembled >= 0 && !session.player().isControlLocked()
                            && manager.activeObjectsOfType(
                                com.openggf.game.sonic3k.objects.LevelIntroPlayerRunInstance.class).isEmpty()
                            && !incomingWindows.contains("dez-released")) {
                        incomingSpot = "dez-released";
                    }
                }
                if (complete && frame == 19683) {
                    assertFalse(session.player().getRenderHFlip(),
                            "loc_58016 clears render_flags bits 0/1 before the fixed spiral mappings");
                    assertFalse(session.player().getRenderVFlip());
                }
                if (frame == 11050) {
                    var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
                    assertTrue((state.eventsBgByte(0) & 0x80) != 0, "native GHZ defeat flag releases the pad");
                    assertTrue(bosses.isEmpty() && mtzBosses.isEmpty(), "both replicas have escaped");
                    assertEquals(5888, session.player().getCentreX());
                    assertEquals(140, session.player().getCentreY(), "upper platform after the second transport pad");
                    assertFalse(session.player().isObjectControlled(), "transport releases player control");
                }
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
                if (incomingSpot == null && frame != 2888 && frame != 3440 && frame != 3540 && frame != 3818
                        && frame != 4168 && frame != 4400 && frame != 4700
                        && frame != 4930 && frame != 5320 && frame != 5400
                        && frame != 6300 && frame != 6459 && frame != 7087
                        && frame != 7460 && frame != 8260 && frame != 8640
                        && frame != 8810 && frame != 9051 && frame != 9160
                        && frame != 9175 && frame != 9776 && frame != 10037 && frame != 10150 && frame != 10270 && frame != 10360
                        && frame != 11850 && frame != 12050 && frame != 12177
                        && frame != 12227 && frame != 12268 && frame != 12690 && frame != 12820
                        && frame != 13110 && frame != 13400 && frame != 13669 && frame != 17000 && frame != 17230 && frame != 14330
                        && frame != 14450 && frame != 14840 && frame != 15050
                        && frame != 15890 && frame != 16925 && frame != 17220
                        && frame != 18000 && frame != 19683 && frame != 19350 && (!complete || frame != lastReplaySpot)) continue;
                var level = GameServices.level();
                var mode = SessionManager.getCurrentGameplayMode();
                assertFalse(level.hasPendingFreshLevelTransitionBoundary());
                assertFalse(mode.getFadeManager().hasPendingCompletion());
                var registry = mode.getRewindRegistry();
                drawing.checkpoint(session);
                var saved = registry.capture();
                var world = level.getCurrentLevel();
                var manager = level.getObjectManager();
                var runtime = GameServices.zoneRuntimeState();
                int lives = GameServices.gameState().getLives();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); drawing.draw(session);
                    sameWorld(session, world, manager, runtime, lives);
                }
                var forward = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "restore at " + frame + " cycle " + cycle);
                    // External held-button history belongs to the input driver.
                    previousInput.set(session, movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) {
                        session.step(movie.getFrame(frame + n)); drawing.draw(session);
                        sameWorld(session, world, manager, runtime, lives);
                    }
                    same(forward, registry.capture(), "replay at " + frame + " cycle " + cycle);
                }
                registry.restore(saved); previousInput.set(session, movie.getFrame(frame));
                checkedWindows++;
                if (incomingSpot != null) incomingWindows.add(incomingSpot);
            }
            drawing.checkpoint(session);
            assertTrue(sawBoss && sawDefeat, "cold encounter must reach the killing hit");
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(SszGhzBossObjectInstance.class).isEmpty());
            assertTrue(sawMtzBoss && sawMtzDefeat, "cold route must also reach the second killing hit");
            if (complete) {
                assertTrue(sawMecha && sawMechaDefeat, "cold route must defeat the final Mecha Sonic");
                assertEquals(11, GameServices.level().getCurrentZone(), "actual DEZ load, not just a request");
                assertEquals(0, GameServices.level().getCurrentAct());
                assertEquals(destinationLoaded + 1, destinationAssembled,
                        "loc_6468 ordinary destination assembly follows the actual load");
                assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
                assertFalse(GameServices.level().getTransitions().isTitleCardRequested());
                assertSame(GameServices.sprites().getMainPlayable(), session.player());
                assertFalse(session.player().isObjectControlled());
                assertFalse(session.player().isControlLocked());
                assertEquals(java.util.Set.of("dez-intro", "dez-released"), incomingWindows);
                assertTrue(isolatedHistory, "incoming history was observed at its fresh boundary");
                assertNotNull(SessionManager.getCurrentGameplayMode().getRewindController());
                System.out.println("SSZ320 complete: " + checkedWindows
                        + " two-cycle45 whole-world windows; actual DEZ load/assembly "
                        + destinationLoaded + "/" + destinationAssembled);
            }
        }
    }

    private static void sameWorld(GameplayCaptureSession session, Object world,
            Object manager, Object runtime, int lives) {
        assertSame(world, GameServices.level().getCurrentLevel());
        assertSame(manager, GameServices.level().getObjectManager());
        assertSame(runtime, GameServices.zoneRuntimeState());
        assertFalse(session.player().getDead());
        assertTrue(GameServices.gameState().getLives() >= lives);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), context + " " + key + ": " + differences);
        }
    }
}
