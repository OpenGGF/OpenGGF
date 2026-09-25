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
        var spots = Set.of(40406, 40706, 41006, 41356, 41636, 42066, 42500, 43000, 43500, 43565, 43947, 44309, 44692, 45161, 45573, 45948, 46382, 46430, 46600, 46860, 47136, 47168, 47519, 47921, 48226, 48626, 48836, 49141, 49200, 49300, 49380);
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

    @org.junit.jupiter.api.Test
    void coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez-sonic-solo-cold-ending-320.bk2"));
        assertEquals(60920, movie.getFrameCount());
        // Cover the shortened Act2 waits, final entry, every core/ship hit,
        // collapse, chase and departure. Original full Act2 coverage remains separate.
        var spots = Set.of(23532, 28849, 28910, 31810, 32000, 32200, 32500, 33000, 33700, 34000, 34500, 35000, 36000, 36250, 36500, 36800, 36930, 37100, 37500, 38000, 38500, 39000, 39500, 40000, 41000, 42000, 43000, 44000, 45000, 45535, 46000, 47000, 48000, 49000, 50000, 50920, 51020, 51364, 52069, 52500, 53000, 53500, 54000, 54210, 54600, 54835, 55207, 55798, 56212, 56540, 56903, 57264, 57739, 57780, 57900, 58100, 58220, 58495, 58542, 59085, 59390, 59792, 60098, 60402, 60613, 60650, 60750, 60850);
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
                assertInstanceOf(com.openggf.sprites.playable.Sonic.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
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
            assertEquals(0, GameServices.sprites().getRegisteredSidekicks().size());
        }
    }

    @org.junit.jupiter.api.Test
    void coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez-tails-solo-cold-ending-320.bk2"));
        assertEquals(68266, movie.getFrameCount());
        // Cover the shortened Act2 waits, final entry, every core/ship hit,
        // collapse, chase and departure. Original full Act2 coverage remains separate.
        var spots = Set.of(29520, 38800, 38950, 38960, 38991, 39300, 41000, 41380,
                41411, 41500, 42500, 42879, 42910, 43000, 43388, 43419,
                44000, 44140, 44171, 45000, 46000, 46148, 46179, 46311,
                46342, 47000, 47330, 47361, 48000, 48039, 48070, 48137,
                48168, 49000, 49393, 49424, 49947, 49978, 50000, 50233,
                50264, 51000, 52000, 53000, 54000, 55000, 56000, 57000,
                58000, 58260, 58370, 58600, 59000, 59400, 59800, 60200,
                60600, 61000, 61400, 61800, 62181, 62553, 63144, 63558,
                63886, 64249, 64610, 65085, 65120, 65200, 65300, 65400,
                65560, 65841, 65888, 66431, 66736, 67138, 67444, 67748,
                67959, 68010, 68100, 68200);
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
                assertInstanceOf(com.openggf.sprites.playable.Tails.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
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
            assertEquals(0, GameServices.sprites().getRegisteredSidekicks().size());
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
