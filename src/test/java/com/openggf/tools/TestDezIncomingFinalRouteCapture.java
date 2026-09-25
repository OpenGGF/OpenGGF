package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.DdzDiagnostics;
import com.openggf.game.sonic3k.objects.DdzEndBossObjectInstance;
import com.openggf.game.sonic3k.objects.DdzEndBossBodyObjectInstance;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Actual DEZ2 boss → final hands/core/escape → DDZ, with no reseeding at either load. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDezIncomingFinalRouteCapture {
    @ParameterizedTest @ValueSource(ints = {320, 800})
    void incomingFinalFightRestoresAndReplaysEveryPhase(int width) throws Exception {
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null,
                0x34B0, 0x300, "3333333", false, false, null, null, false, 200, false);
        verifyIncomingRoute(settings, Path.of(
                "src/test/resources/routes/s3k/dez2-through-ddz-" + width + ".bk2"), 1);
    }

    @org.junit.jupiter.api.Test
    void coldEmeraldTeamClearsBothActsFinalFightAndDoomsday() throws Exception {
        // Emerald inventory is declared once at the real DEZ1 boot. Position,
        // rings, health, clocks and every continuation remain production-owned.
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, "3333333", false, false, null, null, false, null, false);
        verifyIncomingRoute(settings, Path.of(
                "src/test/resources/routes/s3k/dez-sonic-tails-cold-ddz-320.bk2"), 0);
    }

    private void verifyIncomingRoute(GameplayCaptureSession.Settings settings, Path input,
                                     int startAct) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(input);
        var checked = new HashSet<String>();
        boolean chaseSeen = false;
        int previousCamera = 0;
        // Held-button history belongs to this external input driver, not gameplay.
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, startAct, settings);
            assertEquals(startAct == 0 ? 1 : 0, GameServices.sprites().getRegisteredSidekicks().size());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                step(session, movie.getFrame(frame));
                assertFalse(session.player().getDead(), "death at " + frame);
                String spot = null;
                int zone = GameServices.level().getCurrentZone();
                if (zone == 23) {
                    int fingers = health("DezFinalHand$Finger");
                    if (fingers == 18) spot = "hands";
                    if (fingers > 0 && fingers <= 9) spot = "half-hands";
                    int core = health("DezFinalCore");
                    if (core == 4) spot = "core-half";
                    if (core == 1) spot = "core-last";
                    int ship = health("DezFinalEscapeShip");
                    if (ship == 4) spot = "ship-half";
                    if (ship == 1) spot = "ship-last";
                    if (ship == 0) spot = "ship-defeat";
                }
                if (zone == 12 && session.player().getRingCount() > 0
                        && GameServices.level().getFrameCounter() > 2) spot = "ddz-flight";
                if (zone == 12) {
                    var manager = GameServices.level().getObjectManager();
                    var body = manager.activeObjectsOfType(DdzEndBossBodyObjectInstance.class).stream().findFirst().orElse(null);
                    var boss = manager.activeObjectsOfType(DdzEndBossObjectInstance.class).stream().findFirst().orElse(null);
                    int routine = boss == null ? -1 : DdzDiagnostics.bossRoutine(boss);
                    chaseSeen |= routine == 14;
                    int camera = GameServices.camera().getX() & 0xFFFF;
                    if (body != null && DdzDiagnostics.bodyHitPoints(body) == 6) spot = "ddz-body";
                    if (chaseSeen && routine == 0) spot = "ddz-defeat";
                    // A fast chase can finish before its first wrap. Prefer the
                    // one-frame wrap edge over the persistent exit routine.
                    if (chaseSeen && camera + 0x1000 < previousCamera) spot = "ddz-wrap";
                    previousCamera = camera;
                }
                if (spot == null || !checked.add(spot)) continue;
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                int horizon = Math.min(45, movie.getFrameCount() - frame - 1);
                assertEquals(45, horizon, "input tail must cover the complete replay window");
                for (int n = 1; n <= horizon; n++) step(session, movie.getFrame(frame + n));
                var expected = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), spot + " restore at " + frame);
                previousInput.set(session, movie.getFrame(frame));
                for (int n = 1; n <= horizon; n++) step(session, movie.getFrame(frame + n));
                same(expected, registry.capture(), spot + " replay at " + frame);
                registry.restore(saved);
                previousInput.set(session, movie.getFrame(frame));
            }
            assertEquals(12, GameServices.level().getCurrentZone());
            assertEquals(13, GameServices.level().getRequestedZone());
            assertEquals(1, GameServices.level().getRequestedAct());
            assertEquals(Set.of("hands", "half-hands", "core-half", "core-last", "ship-half",
                    "ship-last", "ship-defeat", "ddz-flight", "ddz-body", "ddz-wrap", "ddz-defeat"), checked);
        }
    }

    @org.junit.jupiter.api.Test
    void coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez-sonic-tails-cold-ending-320.bk2"));
        // Earlier independently runnable cold-route tests own DEZ1/2. These
        // checkpoints cover final entry, finger retirement, core/button/beam,
        // the ordinary player's chase rebounds, defeat and ending departure.
        var spots = Set.of(40406, 40706, 41006, 41356, 41636, 42066,
                42926, 43346, 43576, 43766, 44006, 44306, 45366, 46806,
                48236, 49706, 50716, 50806, 51156, 51256, 51476, 51516,
                51816, 52246, 52706, 53576, 53816, 54146, 54376, 54436, 54556);
        var checked = new HashSet<Integer>();
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        boolean handsSeen = false, handsDefeated = false;
        boolean coreSeen = false, coreDefeated = false, shipSeen = false, shipDefeated = false;
        int previousCore = 8, previousShip = 8, coreHits = 0, shipHits = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                step(session, movie.getFrame(frame));
                assertFalse(session.player().getDead(), "death at " + frame);
                assertFalse(session.player().isSuperSonic(), "ordinary cold route at " + frame);
                if (GameServices.level().getCurrentZone() == 23) {
                    int fingers = health("DezFinalHand$Finger");
                    handsSeen |= fingers == 18;
                    handsDefeated |= handsSeen && fingers == 0;
                    int core = health("DezFinalCore");
                    // Newly allocated children have zero property until init.
                    // Start counting at the production eight-hit initialization.
                    coreSeen |= core == 8;
                    if (coreSeen && core >= 0) {
                        assertTrue(core <= previousCore, "core must not regain health");
                        coreHits += previousCore - core;
                        previousCore = core;
                        coreDefeated |= core == 0;
                    }
                    int ship = health("DezFinalEscapeShip");
                    shipSeen |= ship == 8;
                    if (shipSeen && ship >= 0) {
                        assertTrue(ship <= previousShip, "ship must not regain health");
                        shipHits += previousShip - ship;
                        previousShip = ship;
                        shipDefeated |= ship == 0;
                    }
                }
                if (!spots.contains(frame)) continue;
                checked.add(frame);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                for (int n = 1; n <= 45; n++) step(session, movie.getFrame(frame + n));
                var expected = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), "cold restore at " + frame);
                previousInput.set(session, movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) step(session, movie.getFrame(frame + n));
                same(expected, registry.capture(), "cold replay at " + frame);
                registry.restore(saved);
                previousInput.set(session, movie.getFrame(frame));
            }
            assertEquals(spots, checked);
            assertTrue(handsSeen && handsDefeated, "all six fingers must be cleared");
            assertTrue(coreSeen && coreDefeated);
            assertTrue(shipSeen && shipDefeated);
            assertEquals(8, coreHits);
            assertEquals(8, shipHits);
            // With no emerald override this is the ordinary ending, not DDZ.
            assertEquals(13, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(96, session.player().getCentreX());
            assertEquals(300, session.player().getCentreY());
            assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        }
    }

    private static int health(String name) {
        var objects = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o.getClass().getName().equals("com.openggf.game.sonic3k.objects." + name))
                .toList();
        return objects.isEmpty() ? -1 : objects.stream()
                .mapToInt(o -> ((TouchResponseProvider) o).getCollisionProperty()).sum();
    }

    private static void step(GameplayCaptureSession session, Bk2FrameInput input) {
        session.step(input);
        session.render();
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
