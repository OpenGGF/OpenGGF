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

/** Actual DEZ boss routes through native ending, credits and terminal Start, without reseeding loads. */
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
        boolean chaseSeen = false, releasedDdzEntrySeen = false;
        long outgoingDdzHistory = 0;
        int historyStart = movie.getFrameCount() - 120;
        int previousCamera = 0;
        // Held-button history belongs to this external input driver, not gameplay.
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, startAct, settings);
            assertEquals(startAct == 0 ? 1 : 0, GameServices.sprites().getRegisteredSidekicks().size());
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == historyStart) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "death at " + frame);
                String spot = null;
                int zone = GameServices.level().getCurrentZone();
                if (zone == 12 && frame >= historyStart) {
                    var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                    if (rewind != null) outgoingDdzHistory = Math.max(outgoingDdzHistory, rewind.currentFrame());
                }
                if (zone == 12 && !releasedDdzEntrySeen
                        && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                        && !GameServices.level().getTransitions().isTitleCardRequested()) {
                    // loc_63B4 publishes the destination counter before loc_8160A
                    // adds50 during the native fall-in. Source HUD rings are not energy.
                    assertEquals(0, session.player().getRingCount());
                    assertSame(GameServices.sprites().getMainPlayable(), session.player());
                    assertSame(session.player(), GameServices.camera().getFocusedSprite());
                    assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty(),
                            "loc_81554 clears Player_2 before native DDZ flight");
                    releasedDdzEntrySeen = true;
                }
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
                drawing.checkpoint(session);
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
            drawing.checkpoint(session);
            assertTrue(releasedDdzEntrySeen, "incoming DDZ assembly must release through the actual route");
            assertEquals(12, GameServices.level().getCurrentZone());
            assertEquals(13, GameServices.level().getRequestedZone());
            assertEquals(1, GameServices.level().getRequestedAct());
            assertEquals(Set.of("hands", "half-hands", "core-half", "core-last", "ship-half",
                    "ship-last", "ship-defeat", "ddz-flight", "ddz-body", "ddz-wrap", "ddz-defeat"), checked);
            assertTrue(outgoingDdzHistory > 10, "real outgoing DDZ history precedes the ending load");
            verifyEndingComposition(session, drawing, 2);
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
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
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
                drawing.checkpoint(session);
                var saved = registry.capture();
                assertFalse(((com.openggf.graphics.FadeManagerSnapshot) saved.get("fademanager")).isPoisoned(),
                        "cold checkpoint must belong to the production rewind contract at " + frame);
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
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(handsSeen && handsDefeated, "all six fingers must be cleared");
            assertTrue(coreSeen && coreDefeated);
            assertTrue(shipSeen && shipDefeated);
            assertEquals(8, coreHits);
            assertEquals(8, shipHits);
            // The unchanged input endpoint may still belong to native PaletteWhiteOut.
            // The existing bounded helper consumes that transition, then requires the
            // actual D01 owner, roster, progress and continuous terminal handoff.
            verifyOrdinaryEndingHandoff(session, drawing);
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
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
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
                drawing.checkpoint(session);
                var saved = registry.capture();
                assertFalse(((com.openggf.graphics.FadeManagerSnapshot) saved.get("fademanager")).isPoisoned(),
                        "cold checkpoint must belong to the production rewind contract at " + frame);
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
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(handsSeen && handsDefeated, "all six fingers must be cleared");
            assertTrue(coreSeen && coreDefeated);
            assertTrue(shipSeen && shipDefeated);
            assertEquals(8, coreHits);
            assertEquals(8, shipHits);
            // The unchanged input endpoint may still belong to native PaletteWhiteOut.
            // The existing bounded helper consumes that transition, then requires the
            // actual D01 owner, roster, progress and continuous terminal handoff.
            verifyOrdinaryEndingHandoff(session, drawing);
        }
    }

    @org.junit.jupiter.api.Test
    void coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding() throws Exception {
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez-sonic-solo-cold-ending-800.bk2"));
        assertEquals(60298, movie.getFrameCount());
        // Cold 800px route: the direct lower-bridge path, repaired timed hazards,
        // all final phases and both actual full-load boundaries. No state seeding.
        var spots = Set.of(
                20800, 20874, 21200, 21600, 21700, 22000, 22400, 22700, 22800,
                23100, 23200, 23600, 23800, 24000, 24200, 24400, 24654, 24720,
                24800, 24834, 24875, 24955, 25075, 25200, 25300, 25600, 26000,
                26400, 26800, 27200, 27464, 27600, 28000, 28400, 28800, 29200,
                29415, 29600, 30000, 30015, 30400, 30800, 31000, 31200, 31600,
                31800, 32000, 32400, 32800, 32919, 32960, 33000, 33200, 33600,
                33700, 34000, 34400, 34800, 35000, 35200, 35600, 36000, 36400,
                36800, 37025, 37128, 37200, 37250, 37400, 37500, 37600, 38000,
                38400, 38800, 39000, 39200, 39600, 40000, 40400, 40800, 41000,
                41200, 41600, 42000, 42400, 42800, 43000, 43200, 43600, 44000,
                44400, 44800, 45000, 45200, 45600, 46000, 46400, 46800, 47000,
                47200, 47600, 48000, 48400, 48800, 49000, 49200, 49600, 50000,
                50200, 50300, 50500, 50400, 50600, 50800, 51000, 51200, 51600,
                52000, 52400, 52800, 53000, 53200, 53400, 53600, 54000, 54220,
                54240, 54400, 54590, 54620, 54800, 55180, 55200, 55210, 55590,
                55600, 55630, 55920, 55960, 56000, 56280, 56320, 56400, 56640,
                56680, 56800, 57110, 57150, 57200, 57300, 57580, 57600, 57630,
                57870, 57910, 57960, 58000, 58400, 58460, 58510, 58760, 58800,
                58810, 59170, 59200, 59210, 59470, 59520, 59600, 59770, 59820,
                59980, 60000, 60030, 60100, 60200, 60240);
        var checked = new HashSet<Integer>();
        boolean handsSeen = false, handsDefeated = false;
        boolean coreSeen = false, coreDefeated = false, shipSeen = false, shipDefeated = false;
        int previousCore = 8, previousShip = 8, coreHits = 0, shipHits = 0;
        int finalLoad = -1, endingLoad = -1;
        boolean checkedPendingFade = false;
        var rewindBlocked = com.openggf.GameLoop.class.getDeclaredMethod("isRewindBlocked");
        rewindBlocked.setAccessible(true);
        long outgoingFinalHistory = 0, outgoingActTwoHistory = 0;
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == 50200 || frame == 60050) GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertEquals(800, GameServices.camera().getWidth());
                if (frame == 50372) {
                    // Incoming callback-bearing fades intentionally reject rewind:
                    // FadeManagerSnapshot.isPoisoned / GameLoop.isRewindBlocked.
                    // Do not bypass that contract with a raw registry restore.
                    var fade = (com.openggf.graphics.FadeManagerSnapshot)
                            SessionManager.getCurrentGameplayMode().getRewindRegistry().capture().get("fademanager");
                    assertTrue(fade.isPoisoned());
                    assertTrue((boolean) rewindBlocked.invoke(session.loop()));
                    checkedPendingFade = true;
                }
                if (frame == 50500) assertFalse((boolean) rewindBlocked.invoke(session.loop()),
                        "ordinary final gameplay becomes rewindable after the incoming fade");
                int zone = GameServices.level().getCurrentZone();
                if (zone == 11 && frame >= 50200) outgoingActTwoHistory = Math.max(outgoingActTwoHistory,
                        SessionManager.getCurrentGameplayMode().getRewindController().currentFrame());
                if (zone == 23 && finalLoad < 0) {
                    finalLoad = frame;
                    var incoming = SessionManager.getCurrentGameplayMode().getRewindController();
                    assertEquals(0, incoming.earliestAvailableFrame());
                    assertTrue(incoming.currentFrame() <= 1);
                    GameServices.configuration().setSessionOverride(
                            com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, false);
                }
                if (zone == 23 && frame >= 60050) outgoingFinalHistory = Math.max(outgoingFinalHistory,
                        SessionManager.getCurrentGameplayMode().getRewindController().currentFrame());
                if (zone == 13 && GameServices.level().getCurrentAct() == 1 && endingLoad < 0) {
                    endingLoad = frame;
                    var incoming = SessionManager.getCurrentGameplayMode().getRewindController();
                    assertEquals(0, incoming.earliestAvailableFrame());
                    assertTrue(incoming.currentFrame() <= 1);
                }
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
                drawing.checkpoint(session);
                var saved = registry.capture();
                assertFalse(((com.openggf.graphics.FadeManagerSnapshot) saved.get("fademanager")).isPoisoned(),
                        "cold checkpoint must belong to the production rewind contract at " + frame);
                for (int n = 1; n <= 45; n++) step(session, movie.getFrame(frame + n));
                var expected = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(saved);
                    same(saved, registry.capture(), "wide restore at " + frame + " cycle " + cycle);
                    session.restoreInputHistory(movie.getFrame(frame));
                    for (int n = 1; n <= 45; n++) step(session, movie.getFrame(frame + n));
                    same(expected, registry.capture(), "wide replay at " + frame + " cycle " + cycle);
                }
                registry.restore(saved);
                session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(checkedPendingFade);
            assertEquals(50371, finalLoad);
            assertTrue(outgoingActTwoHistory > 10);
            if (endingLoad < 0) {
                var fade = (com.openggf.graphics.FadeManagerSnapshot) SessionManager.getCurrentGameplayMode()
                        .getRewindRegistry().capture().get("fademanager");
                assertTrue(fade.isPoisoned(), "the unchanged wide input endpoint retains native PaletteWhiteOut ownership");
                assertEquals(13, GameServices.level().getRequestedZone());
                assertEquals(1, GameServices.level().getRequestedAct());
            }
            assertTrue(outgoingFinalHistory > 10, "ending load must retire a recorded final-stage timeline");
            assertTrue(handsSeen && handsDefeated, "all six fingers must be cleared");
            assertTrue(coreSeen && coreDefeated);
            assertTrue(shipSeen && shipDefeated);
            assertEquals(8, coreHits);
            assertEquals(8, shipHits);
            // The unchanged input endpoint may still belong to native PaletteWhiteOut.
            // The existing bounded helper consumes that transition, then requires the
            // actual D01 owner, roster, progress and continuous terminal handoff.
            verifyOrdinaryEndingHandoff(session, drawing);
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
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
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
                drawing.checkpoint(session);
                var saved = registry.capture();
                assertFalse(((com.openggf.graphics.FadeManagerSnapshot) saved.get("fademanager")).isPoisoned(),
                        "cold checkpoint must belong to the production rewind contract at " + frame);
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
            drawing.checkpoint(session);
            assertEquals(spots, checked);
            assertTrue(handsSeen && handsDefeated, "all six fingers must be cleared");
            assertTrue(coreSeen && coreDefeated);
            assertTrue(shipSeen && shipDefeated);
            assertEquals(8, coreHits);
            assertEquals(8, shipHits);
            // The unchanged input endpoint may still belong to native PaletteWhiteOut.
            // The existing bounded helper consumes that transition, then requires the
            // actual D01 owner, roster, progress and continuous terminal handoff.
            verifyOrdinaryEndingHandoff(session, drawing);
        }
    }

    private static com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance rescueOwner() {
        return GameServices.level().getObjectManager()
                .activeObjectsOfType(com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.class)
                .stream().filter(o -> o.sequence()
                        == com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.RESCUE)
                .findFirst().orElseThrow();
    }

    private static void verifyOrdinaryEndingHandoff(GameplayCaptureSession session,
                                                     RouteFrameDrawing drawing) throws Exception {
        verifyEndingComposition(session, drawing, -2);
    }

    private static void verifyEndingComposition(GameplayCaptureSession session,
                                                RouteFrameDrawing drawing, int expectedProgress) throws Exception {
        var neutral = new Bk2FrameInput(0, 0, 0, false, "");
        var sourceRewind = SessionManager.getCurrentGameplayMode().getRewindController();
        long outgoingHistory = sourceRewind == null ? 0 : sourceRewind.currentFrame();
        boolean incomingLoadPending = GameServices.level().getCurrentZone() != 13;
        if (incomingLoadPending) {
            var source = GameServices.level();
            var fade = (com.openggf.graphics.FadeManagerSnapshot) SessionManager.getCurrentGameplayMode()
                    .getRewindRegistry().capture().get("fademanager");
            assertEquals(13, source.getRequestedZone(), "the real source owner must request D01");
            assertEquals(1, source.getRequestedAct());
            assertTrue(source.hasPendingLevelExit() || fade.isPoisoned(),
                    "the source owns either an unconsumed native exit request or its in-flight fade: " + fade);
            System.out.printf("native-ending-input-endpoint zone=%d act=%d fresh=%s fade=%s%n",
                    source.getCurrentZone(), source.getCurrentAct(), source.hasPendingFreshLevelTransitionBoundary(), fade);
        }
        for (int n = 0; n < 64 && GameServices.level().getCurrentZone() != 13; n++) step(session, neutral);
        assertEquals(13, GameServices.level().getCurrentZone(), "actual ending load follows the source request");
        assertEquals(1, GameServices.level().getCurrentAct());
        if (incomingLoadPending && outgoingHistory > 10) {
            var releasedHistory = SessionManager.getCurrentGameplayMode().getRewindController();
            assertNotNull(releasedHistory);
            assertEquals(0, releasedHistory.earliestAvailableFrame());
            assertTrue(releasedHistory.currentFrame() < outgoingHistory,
                    "actual ending load discards its recorded source timeline");
        }
        if (GameServices.level().hasPendingFreshLevelTransitionBoundary()
                || GameServices.level().getTransitions().isTitleCardRequested()) {
            session.step(neutral); drawing.draw(session);
        }
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertFalse(GameServices.level().getTransitions().isTitleCardRequested());
        assertSame(GameServices.sprites().getMainPlayable(), session.player());
        assertSame(session.player(), GameServices.camera().getFocusedSprite());
        var state = assertInstanceOf(com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState.class,
                GameServices.zoneRuntimeState());
        assertTrue(state.ending().initialized());
        assertEquals(expectedProgress, state.ending().progress(), "native progression is inherited through the real load");
        assertInstanceOf(com.openggf.game.sonic3k.Sonic3kSuperStateController.class,
                session.player().getSuperStateController(), "the rescue owns native P1 presentation");
        assertTrue(session.player().isObjectControlled());
        assertTrue(session.player().isObjectControlSuppressesMovement());
        assertFalse(session.player().isObjectControlAllowsCpu());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty(),
                "Obj_5D8BC clears Player_2 after the actual incoming team route");
        assertEquals(0, session.player().getRingCount(), "native destination owns the cleared counter");
        var owner = rescueOwner();
        assertEquals(2, owner.routine());
        assertEquals(0x100, session.player().getCentreX(), "Obj_5D8BC raw screen-relative x_pos");
        assertEquals(0xE0, session.player().getCentreY(), "Obj_5D8BC raw screen-relative y_pos");
        int inset = Math.max(0, (GameServices.camera().getWidth() - 320) / 2);
        assertEquals(owner.getX() + GameServices.camera().getX() + inset - 0x80,
                session.player().getRenderCentreX());
        assertEquals(owner.getY() + GameServices.camera().getY() - 0x80,
                session.player().getRenderCentreY());
        var actors = GameServices.level().getObjectManager()
                .activeObjectsOfType(com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.Actor.class);
        // Dynamic_object_RAM follows three Object_RAM slots; loc_5D96A uses5/45.
        assertTrue(actors.stream().anyMatch(a -> a.getSlotIndex() == 8));
        assertTrue(actors.stream().anyMatch(a -> a.getSlotIndex() == 48));
        var incoming = SessionManager.getCurrentGameplayMode().getRewindController();
        if (incoming != null) {
            assertEquals(0, incoming.earliestAvailableFrame());
            assertTrue(incoming.currentFrame() <= 2, "source final-stage history cannot enter D01");
        }
        // Keep independent registry replay separate from live-history recording.
        GameServices.configuration().setSessionOverride(
                com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, false);
        var checked = new HashSet<String>();
        boolean caught = false;
        for (int frame = 0; frame < 750; frame++) {
            step(session, neutral);
            assertFalse(session.player().getDead(), "native rescue continuation at " + frame);
            assertEquals(13, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            owner = rescueOwner();
            String spot = owner.routine() >= 8 ? "plane-catch"
                    : owner.planeReleased() ? "plane-release" : null;
            if (spot != null && checked.add(spot)) {
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                assertFalse(((com.openggf.graphics.FadeManagerSnapshot) saved.get("fademanager")).isPoisoned(),
                        "native rescue replay must start outside callback-bearing transition fades");
                for (int n = 0; n < 45; n++) step(session, neutral);
                var expected = registry.capture();
                registry.restore(saved);
                same(saved, registry.capture(), spot + " incoming restore");
                session.restoreInputHistory(neutral);
                for (int n = 0; n < 45; n++) step(session, neutral);
                same(expected, registry.capture(), spot + " incoming replay");
                registry.restore(saved);
                session.restoreInputHistory(neutral);
                owner = rescueOwner();
            }
            var plane = GameServices.level().getObjectManager()
                    .activeObjectsOfType(com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.Actor.class)
                    .stream().filter(a -> a.getSlotIndex() == 8).findFirst().orElseThrow();
            int seat = session.player() instanceof com.openggf.sprites.playable.Tails ? 0x27 : 0x2B;
            caught = checked.contains("plane-catch") && owner.routine() >= 8
                    && plane.getX() == owner.getX() - 0x20 && plane.getY() == owner.getY() + seat;
            if (caught) break;
        }
        assertEquals(Set.of("plane-release", "plane-catch"), checked);
        assertTrue(caught, "native Tornado must catch the incoming P1 within the bounded rescue phase");
        assertEquals(owner.getX(), session.player().getCentreX());
        assertEquals(owner.getY(), session.player().getCentreY());
        assertEquals(session.player() instanceof com.openggf.sprites.playable.Tails ? 0xAD : 0xBA,
                session.player().getMappingFrame());
        GameServices.configuration().setSessionOverride(
                com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
        for (int n = 0; n < 30; n++) step(session, neutral);
        incoming = SessionManager.getCurrentGameplayMode().getRewindController();
        assertNotNull(incoming);
        assertEquals(0, incoming.earliestAvailableFrame());
        assertTrue(incoming.currentFrame() > 10 && incoming.currentFrame() <= 35,
                "native rescue records a fresh destination timeline after independent replay");
        GameServices.configuration().setSessionOverride(
                com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, false);
        boolean creditsSeen = false, postSeen = false, aizReturnSeen = expectedProgress != 2;
        boolean reunionSeen = false, terminalReady = false;
        var continuationSpots = new HashSet<String>();
        // The native script supplies timing; this is only a failure budget, also
        // used by the independent ending scenario checks, never a scene clock seed.
        for (int frame = 0; frame < 60_000; frame++) {
            step(session, neutral);
            assertEquals(com.openggf.game.GameMode.LEVEL, session.loop().getCurrentGameMode(),
                    "native post object must be observed before the requested Start handoff");
            assertFalse(session.player().getDead(), "continuous ending at " + frame);
            state = assertInstanceOf(com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState.class,
                    GameServices.zoneRuntimeState());
            assertEquals(expectedProgress, state.ending().progress());
            var directors = GameServices.level().getObjectManager()
                    .activeObjectsOfType(com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.class);
            aizReturnSeen |= directors.stream().anyMatch(o -> o.sequence()
                    == com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.AIZ_RETURN);
            reunionSeen |= directors.stream().anyMatch(o -> o.sequence()
                    == com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.REUNION);
            creditsSeen |= state.ending().credits().active();
            var post = GameServices.level().getObjectManager()
                    .activeObjectsOfType(com.openggf.game.sonic3k.ending.S3kPostCreditsObjectInstance.class)
                    .stream().findFirst().orElse(null);
            postSeen |= post != null;
            String spot = post != null && post.terminalInputReady() ? "terminal"
                    : state.ending().credits().active() && !state.ending().credits().complete() ? "credits" : null;
            if (spot != null && continuationSpots.add(spot)) {
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                assertFalse(((com.openggf.graphics.FadeManagerSnapshot) saved.get("fademanager")).isPoisoned());
                for (int n = 0; n < 45; n++) step(session, neutral);
                var expected = registry.capture();
                registry.restore(saved); same(saved, registry.capture(), spot + " continuous restore");
                session.restoreInputHistory(neutral);
                for (int n = 0; n < 45; n++) step(session, neutral);
                same(expected, registry.capture(), spot + " continuous replay");
                registry.restore(saved); session.restoreInputHistory(neutral);
                post = GameServices.level().getObjectManager()
                        .activeObjectsOfType(com.openggf.game.sonic3k.ending.S3kPostCreditsObjectInstance.class)
                        .stream().findFirst().orElse(null);
            }
            terminalReady = post != null && post.terminalInputReady();
            if (terminalReady) break;
        }
        assertTrue(aizReturnSeen, "all-Super DDZ progression must take the native AIZ return");
        assertTrue(reunionSeen && creditsSeen && postSeen && terminalReady,
                "the actual incoming route must reach native reunion, credits and terminal input");
        assertTrue(state.ending().credits().complete());
        assertEquals(Set.of("credits", "terminal"), continuationSpots);
        step(session, new Bk2FrameInput(0, 0, 0, true, ""));
        for (int n = 0; n < 128 && session.loop().getCurrentGameMode()
                != com.openggf.game.GameMode.TITLE_SCREEN; n++) step(session, neutral);
        assertEquals(com.openggf.game.GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode(),
                "native terminal Start uses the production title handoff");
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
        session.renderFrame();
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
