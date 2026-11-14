package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the K2 native fixture {@code rehost-k2/trace-r1} (Palmtree Panic Act 1
 * Present, retail route, Right held with five jumps) through {@link CdFixtureReplay}
 * and holds the first divergence at the documented row: every compared field of every
 * earlier row equals the native trace.
 *
 * <p>The fixture is optional user input: {@code -Dsoniccd.media.root} (disc) and
 * {@code -Dsoniccd.native.root} (defaulting beside the media root).
 */
class TestSonicCdR11aTraceReplay {
    /**
     * The first row in error over the whole trace, or -1 when every row matches. Rows 483-701
     * cross the first 3D ramp through the player stage routines (ObjSonic_Handle3DRamp,
     * SonAni_Run3D, the air lock and JumpAngle skip, and the camera's 3D-ramp branch;
     * Sonic/Main.asm:231-283, 782-831, 1566-1580, 2013-2015, 2737-2771), and row 702 is the
     * 3D ramp marker ($2A, Obj3DRamp) writing oYVel = $C00 (Objects/3D Ramp/Main.asm:302-338),
     * hosted since the Palmtree Panic object lane. Re-attribute this value whenever it moves.
     */
    static final int FIRST_ERROR_ROW = Integer.getInteger("soniccd.trace.frontier", -1);

    private static final int DUMP_FROM = Integer.getInteger("soniccd.trace.dumpFrom", -1);
    private static final int DUMP_TO = Integer.getInteger("soniccd.trace.dumpTo", -1);

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
    void replayIsGreenUpToTheFirstOutOfScopeDivergence() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Path traceDir = CdTestMedia.nativeFile("rehost-k2/trace-r1/metadata.json").getParent();
        Path bk2 = CdTestMedia.nativeFile("rehost-k2/movie/r11a-right-jumps.bk2");
        var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R11A, traceDir, bk2);

        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            CdFixtureReplay.Outcome outcome = CdFixtureReplay.replay(disc, tempDir, fixture,
                    Integer.MAX_VALUE, false, DUMP_FROM, DUMP_TO);
            for (CdFixtureReplay.Row row : outcome.rows()) {
                if (row.row() >= DUMP_FROM && row.row() <= DUMP_TO) System.out.println("[scd-row] " + row.format());
            }
            String fields = outcome.fields().stream()
                    .map(f -> f.fieldName() + " " + f.expected() + " vs " + f.actual())
                    .collect(Collectors.joining(", "));
            System.out.println("[scd-trace] R11A first error row " + outcome.firstErrorRow() + ": " + fields);
            outcome.rows().stream().filter(r -> r.row() >= outcome.firstErrorRow() - 4
                            && r.row() <= outcome.firstErrorRow())
                    .forEach(r -> System.out.println("[scd-context] " + r.format()));

            System.out.println("[scd-trace] R11A rows compared " + outcome.trace().frameCount());
            assertTrue(outcome.trace().frameCount() > 1400, "the K2 trace has 1,411 rows");
            assertEquals(FIRST_ERROR_ROW, outcome.firstErrorRow(),
                    "The first divergence moved; re-attribute the frontier: " + fields);
        }
    }
}
