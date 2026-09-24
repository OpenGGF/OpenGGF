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
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/dez2-through-ddz-" + width + ".bk2"));
        var checked = new HashSet<String>();
        boolean chaseSeen = false;
        int previousCamera = 0;
        // Held-button history belongs to this external input driver, not gameplay.
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 11, 1, settings);
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
                    if (chaseSeen && camera + 0x1000 < previousCamera) spot = "ddz-wrap";
                    if (chaseSeen && routine == 0) spot = "ddz-defeat";
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
