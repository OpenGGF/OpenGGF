package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzPostDefeatCameraReleaseInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real miniboss/results/act change owns the ramp; only the positioned entry is seeded. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzPostBossPaletteRouteCapture {
    @ParameterizedTest @ValueSource(ints = {320, 800})
    void realHandoffRunsTheCompleteRampAndReplaysItsMiddle(int width) throws Exception {
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null,
                0x2B70, 0x750, "1111111", false, false, null, null, false, 355, false);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/lrz1-miniboss-through-act2-320.bk2"));
        var cursor = LrzPostDefeatCameraReleaseInstance.class.getDeclaredField("paletteCursor");
        cursor.setAccessible(true);
        var previousInput = GameplayCaptureSession.class.getDeclaredField("previousInput");
        previousInput.setAccessible(true);
        List<Integer> writes = new ArrayList<>();
        int prior = 0;
        boolean checked = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                session.step(movie.getFrame(frame)); session.render();
                assertFalse(session.player().getDead(), "death at " + frame);
                var waiter = GameServices.level().getObjectManager()
                        .activeObjectsOfType(LrzPostDefeatCameraReleaseInstance.class).stream()
                        .filter(w -> w.gate() == LrzPostDefeatCameraReleaseInstance.Gate.WAITER)
                        .findFirst().orElse(null);
                int value = waiter == null ? 0 : cursor.getInt(waiter);
                if (value != 0 && value != prior) writes.add(frame);
                prior = value;
                if (checked || writes.size() != 6) continue;
                checked = true;
                assertTrue(session.player().isHighPriority(), "approach crosses the placed switch");
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var before = registry.capture();
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                var expected = registry.capture();
                registry.restore(before);
                same(before, registry.capture());
                // External input driver history is separate from captured gameplay state.
                previousInput.set(session, movie.getFrame(frame));
                for (int n = 1; n <= 45; n++) {
                    session.step(movie.getFrame(frame + n)); session.render();
                }
                same(expected, registry.capture());
                registry.restore(before);
                previousInput.set(session, movie.getFrame(frame));
            }
            assertTrue(checked);
            assertEquals(List.of(0, 4, 8, 12, 16, 32, 36, 40, 44, 48, 52, 56, 60),
                    writes.stream().map(frame -> frame - writes.getFirst()).toList());
            assertEquals(9, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(1, GameServices.level().getObjectManager().activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.LrzDeathEggBackgroundInstance.class).size(),
                    "loc_5700C allocates one Death Egg owner on the seamless path");
        }
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot a,
                             com.openggf.game.rewind.CompositeSnapshot b) {
        assertEquals(a.entries().keySet(), b.entries().keySet());
        for (String key : a.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key));
            assertTrue(differences.isEmpty(), () -> key + ": " + differences);
        }
    }
}
