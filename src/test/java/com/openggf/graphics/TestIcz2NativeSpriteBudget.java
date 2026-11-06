package com.openggf.graphics;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Actual cold ICZ2 input route: offscreen loaded groups must not exhaust the native SAT. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestIcz2NativeSpriteBudget {
    @Test
    void coldCubeAndBridgeRouteFitsTheNativeTableBeforeLimitsAreApplied() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/icz2-sonic-cold-cube-button-bridge-320.bk2"));
        assertEquals(2292, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, true, false, null, null, false, null, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 5, 1, settings);
            var graphics = GameServices.graphics();
            SpriteSatInternalAccess.bindLineLimitSource(graphics, () -> true);
            try (var counter = new SubmittedTableCounter(graphics)) {
                for (int row = 0; row < movie.getFrameCount(); row++) {
                    counter.inputRow = row;
                    session.step(movie.getFrame(row));
                }
                assertEquals(5, GameServices.level().getCurrentZone());
                assertEquals(1, GameServices.level().getCurrentAct());
                assertTrue(session.player().getCentreX() >= 0x2300,
                        "the controller route must reach the cube/button/bridge area");
                assertTrue(counter.tables >= 2000, "observe actual production tables, not only screenshot redraws");
                assertTrue(counter.maximum > 20, "the route must exercise crowded object groups");
                assertTrue(counter.maximum <= 80,
                        "raw submissions exceeded H40 table capacity before evaluation: " + counter.maximum
                                + " at input row " + counter.peakRow);
            }
        }
    }

    /** Read-only observer at collection teardown; no gameplay or admission values are supplied. */
    private static final class SubmittedTableCounter extends ArrayList<SpriteSatEntry> implements AutoCloseable {
        private final GraphicsManager graphics;
        private final Field field;
        private final List<SpriteSatEntry> original;
        private int tables;
        private int maximum;
        private int inputRow;
        private int peakRow;

        @SuppressWarnings("unchecked")
        SubmittedTableCounter(GraphicsManager graphics) throws ReflectiveOperationException {
            this.graphics = graphics;
            field = GraphicsManager.class.getDeclaredField("spriteSatEntries");
            field.setAccessible(true);
            original = (List<SpriteSatEntry>) field.get(graphics);
            assertTrue(original.isEmpty(), "attach only between production collections");
            field.set(graphics, this);
        }

        @Override public void clear() {
            if (!isEmpty()) {
                tables++;
                if (size() > maximum) {
                    maximum = size();
                    peakRow = inputRow;
                }
            }
            super.clear();
        }

        @Override public void close() throws ReflectiveOperationException {
            field.set(graphics, original);
        }
    }
}
