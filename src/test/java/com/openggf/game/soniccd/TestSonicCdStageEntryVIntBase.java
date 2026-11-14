package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceVIntServiceCoordinator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code levelVIntCounter} and the ring count on native rows over whole full-act routes, once the replay seeds the
 * timing contract's section 4 base before the stage load ({@link CdLevelVIntCounter},
 * {@code CdFixtureReplay.stageEntryBase}) and services no V-INT on the rows the trace's lost-V-INT stream names
 * (contract 2026-11-13 extension, {@code TraceVIntServiceCoordinator}).
 *
 * <p>Native: physics {@code vblank_counter} is the low word of {@code levelVIntCounter} ($FF190C, incremented by every
 * V-INT, Level/Main.asm:711-712). It is compared on its own row, and only where it advanced from the previous row: on
 * a row where it did not, the V-INT that started the frame had not reached {@code VInt_Done} at the sample
 * ({@code CdFixtureReplay.completedRow}). Row 0 holds the load's V-INTs (38 to 51 over the stage recordings).
 *
 * <p>Nine times over the 158 Sonic CD stage recordings, all on the four Quartz Quadrant full-act routes, a V-INT
 * handler is still running when the next VBlank arrives and that VBlank produces no interrupt entry, so native counts
 * one V-INT fewer than frames (R52A: 1523 on row 1475, 1526 on row 1479). The re-recorded routes carry the rows the
 * recorder observed with no entry at {@code VInterrupt} (Level/Main.asm:681): R52A 1478; R51A 1369, 3417; R51B 1583,
 * 1651, 1896, 4318; R51C 4081, 6086. The level V-INT counter phases a spilled ring's floor check (Ring/Main.asm:413-418),
 * so the ring count is compared too on those four routes, on every row whose gameplay is compared.
 */
class TestSonicCdStageEntryVIntBase {
    @TempDir
    Path tempDir;

    private record Run(boolean recorded, int compared, List<String> counter, List<String> rings) { }

    @ParameterizedTest(name = "{0}: counter on every row, rings compared {1}")
    @CsvSource({"R52A, true", "R51A, true", "R51B, true", "R51C, true", "R42A, false", "R81A, false"})
    void counterEqualsNativeOnEveryRowOfTheRoute(CdStagePayload payload, boolean ringsCompared) throws Exception {
        Path traceDir = route(payload).traceDir();
        Run run = replay(payload, traceDir, 0);
        System.out.println("[scd-vint-base] " + payload + " stream " + (run.recorded() ? "recorded" : "absent")
                + "; rows compared " + run.compared() + ", counter mismatches " + run.counter().size() + " "
                + run.counter().stream().limit(4).toList() + ", ring mismatches " + run.rings().size() + " "
                + run.rings().stream().limit(4).toList());
        assertTrue(run.recorded(), payload + " must carry the lost-V-INT stream (re-recorded 2026-11-13)");
        assertTrue(run.compared() > 3000, "too few rows compared: " + run.compared());
        assertTrue(run.counter().isEmpty(), "levelVIntCounter differs from native on " + run.counter().size()
                + " rows, first " + run.counter().stream().findFirst().orElse(""));
        if (ringsCompared) {
            assertTrue(run.rings().isEmpty(), "rings differ from native on " + run.rings().size() + " rows, first "
                    + run.rings().stream().findFirst().orElse(""));
        }
    }

    /** Negative control: the base one V-INT late fails the comparison from row 0. */
    @Test
    void baseOffByOneFailsR52A() throws Exception {
        Run run = replay(CdStagePayload.R52A, route(CdStagePayload.R52A).traceDir(), 1);
        System.out.println("[scd-vint-base] R52A base+1: mismatches " + run.counter().size() + " of " + run.compared());
        assertFalse(run.counter().isEmpty(), "a base one V-INT off must fail the counter comparison");
        assertTrue(run.counter().getFirst().startsWith("row 0 "), "row 0 must already differ: " + run.counter().getFirst());
    }

    /** Break: R51B without its row-1651 event services that VBlank, and the counter leads native from row 1652. */
    @Test
    void droppingOneEventFailsTheCounterFromThatRow() throws Exception {
        Path copy = copyWithStream(CdStagePayload.R51B, "R51B-drop-1651", List.of(1583, 1896, 4318));
        Run run = replay(CdStagePayload.R51B, copy, 0);
        System.out.println("[scd-vint-base] R51B without 1651: first " + run.counter().stream().findFirst().orElse(""));
        assertFalse(run.counter().isEmpty(), "dropping an event must fail the counter comparison");
        assertEquals("row 1652 native 1699 engine 1700", run.counter().getFirst());
    }

    /** Break: R52A's event moved one row later lands on row 1479, which runs a main loop: loading fails. */
    @Test
    void movingAnEventOntoAMainLoopRowFailsLoading() throws Exception {
        Path copy = copyWithStream(CdStagePayload.R52A, "R52A-move-1479", List.of(1479));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> replay(CdStagePayload.R52A, copy, 0));
        assertEquals("vint_not_serviced on raw_frame 1479, which runs a main loop; only a VBlank-only row can lose "
                + "its V-INT", failure.getMessage());
    }

    /**
     * Break: an empty stream on R52A (every VBlank serviced) puts the engine one V-INT ahead from row 1479 and
     * reproduces the ring difference the TIMING lane measured: from row 2005, ending at 25 rings against native's 24.
     */
    @Test
    void anEmptyStreamOnR52AReproducesTheRingDifference() throws Exception {
        Path copy = copyWithStream(CdStagePayload.R52A, "R52A-empty", List.of());
        Run run = replay(CdStagePayload.R52A, copy, 0);
        System.out.println("[scd-vint-base] R52A empty stream: counter first "
                + run.counter().stream().findFirst().orElse("") + ", rings first "
                + run.rings().stream().findFirst().orElse(""));
        assertEquals("row 1479 native 1526 engine 1527", run.counter().getFirst());
        assertTrue(run.rings().getFirst().startsWith("row 2005 "), "rings first differ: " + run.rings().getFirst());
        assertTrue(run.rings().getLast().endsWith(" native 24 engine 25"), "rings last differ: " + run.rings().getLast());
    }

    private static CdFixtureReplay.Fixture route(CdStagePayload payload) {
        Path root = CdTestMedia.nativeFile("rehost-long/README.md").getParent();
        var fixture = CdFixtureReplay.Fixture.under(root, payload);
        Assumptions.assumeTrue(fixture.present(), "Native route absent: " + payload);
        return fixture;
    }

    /** The route's trace rows beside a deliberately altered lost-V-INT stream (one event row per entry). */
    private Path copyWithStream(CdStagePayload payload, String name, List<Integer> rows) throws Exception {
        Path source = route(payload).traceDir();
        Path copy = Files.createDirectories(tempDir.resolve(name));
        for (String file : List.of("metadata.json", "physics.csv", "aux_state.jsonl")) {
            Files.copy(source.resolve(file), copy.resolve(file));
        }
        StringBuilder stream = new StringBuilder();
        for (int row : rows) stream.append("{\"event\":\"vint_not_serviced\",\"raw_frame\":").append(row).append("}\n");
        Files.writeString(copy.resolve("vint_service_timing.jsonl"), stream.toString());
        return copy;
    }

    private Run replay(CdStagePayload payload, Path traceDir, int baseShift) throws Exception {
        var fixture = new CdFixtureReplay.Fixture(payload, traceDir, route(payload).movie());
        TraceData trace = TraceData.load(traceDir);
        List<String> counter = new ArrayList<>();
        List<String> rings = new ArrayList<>();
        int[] compared = {0};
        boolean[] recorded = {false};
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, Files.createDirectories(tempDir.resolve("config-" + traceDir.getFileName())),
                    fixture, Integer.MAX_VALUE, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override public int stageEntryBase(int recorded) { return recorded + baseShift; }

                        @Override public void row(int row, AbstractPlayableSprite sonic) {
                            if (row == 0) {
                                try {
                                    recorded[0] = TraceVIntServiceCoordinator.open(traceDir, trace,
                                            SessionManager.requireCurrentGameModule()).recorded();
                                } catch (java.io.IOException e) {
                                    throw new java.io.UncheckedIOException(e);
                                }
                            }
                            if (row > 0) {
                                TraceFrame expected = trace.getFrame(row);
                                TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace,
                                        trace.getFrame(row - 1), expected);
                                if (TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                                    int nativeRings = CdFixtureReplay.completedLevelFrame(trace, row,
                                            trace.getFrame(row - 1), expected, phase).rings();
                                    if (nativeRings != sonic.getRingCount()) {
                                        rings.add("row " + row + " native " + nativeRings + " engine "
                                                + sonic.getRingCount());
                                    }
                                }
                            }
                            if (!vIntFinished(trace, row)) return;
                            int nativeCounter = trace.getFrame(row).vblankCounter();
                            int engine = GameServices.level().getObjectManager().getVblaCounter() & 0xFFFF;
                            compared[0]++;
                            if (engine != nativeCounter) {
                                counter.add("row " + row + " native " + nativeCounter + " engine " + engine);
                            }
                        }
                    });
        }
        return new Run(recorded[0], compared[0], counter, rings);
    }

    private static boolean vIntFinished(TraceData trace, int row) {
        return row == 0 || trace.getFrame(row).vblankCounter() != trace.getFrame(row - 1).vblankCounter();
    }
}
