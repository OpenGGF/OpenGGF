package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.physics.GroundSensor;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Native check of CD's {@code SolidObject} (Object Solid.asm:188-479) on Quartz Quadrant act 1
 * Present (R51A, the stage's {@code rehost-fixtures} movie). At trace row 478 Sonic falls onto
 * the monitor at ($390, $390) and lands at {@code oY - ($E + $13)} = $36F with ground speed =
 * X speed ({@code StandOnObject}, :120-143); rows 479-483 ride it; on row 484 the edge distance
 * equals the top depth (2) and the 4-pixel side release lets go (:263-276). Player and
 * camera rows, the on-object status bit included, equal native for rows 0-674.
 *
 * <p>Row 675 is a lag-sampled row (the camera is compared with row 676, as the other games' replays
 * do). Row 744, where native lands rolling on the bridge $2F (slot 50), equals native since the bridge
 * stands the player through {@code CdStandOnObject} (src/common/solid_object.asm:50-137), so the whole
 * movie (841 rows) equals native.
 */
class TestSonicCdSolidObjectTrace {
    private static final int MONITOR_LANDING_ROW = 478;
    private static final int MONITOR_EXIT_ROW = 484;

    @TempDir
    Path tempDir;
    private EngineContext previous;

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
    }

    @AfterEach
    void cleanup() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
    }

    @Test
    void quartzQuadrantMonitorLandingRideAndExitEqualNative() throws Exception {
        List<Boolean> onObject = new ArrayList<>();
        List<Integer> riddenId = new ArrayList<>();
        var result = CdFixtureReplay.replay(tempDir, CdStagePayload.R51A, Integer.MAX_VALUE, row -> {
            if (row < MONITOR_LANDING_ROW - 1 || row > MONITOR_EXIT_ROW) return;
            var sonic = GameServices.camera().getFocusedSprite();
            onObject.add(sonic.isOnObject());
            var ridden = GameServices.level().getObjectManager().getRidingObject(sonic);
            riddenId.add(ridden == null ? -1 : ridden.getSpawn().objectId());
        });
        // The native rows themselves carry the contact: status bit 3 on rows 478-483 only.
        for (int row = MONITOR_LANDING_ROW - 1; row <= MONITOR_EXIT_ROW; row++) {
            boolean nativeOnObject = (result.trace().getFrame(row).statusByte() & 0x08) != 0;
            boolean expected = row >= MONITOR_LANDING_ROW && row < MONITOR_EXIT_ROW;
            assertEquals(expected, nativeOnObject, "native status bit 3 at row " + row);
            int i = row - (MONITOR_LANDING_ROW - 1);
            assertEquals(expected, onObject.get(i), "engine on-object at row " + row);
            if (expected) assertEquals(0x19, riddenId.get(i), "engine stands on the monitor at row " + row);
        }
        assertEquals(-1, result.firstErrorRow(),
                "R51A diverged: " + result.fields() + "\n"
                        + String.join("\n", result.rows().stream().map(CdFixtureReplay.Row::format).toList()));
        assertEquals(result.trace().frameCount(), result.rowsCompared(), "every row of the movie is compared");
    }
}
