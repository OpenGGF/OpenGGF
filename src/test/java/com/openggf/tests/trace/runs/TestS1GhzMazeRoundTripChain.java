package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.AdmitDestination;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.BeginTerminalTail;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.CloseSegment;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.CompleteRun;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator.EnterTransitionGap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chain integration test for the committed {@code s1-ghz-maze-roundtrip} run
 * (3 segments: ghz1 -> ss -> ghz2, a {@code giant_ring} entry and
 * {@code stage_exit} return). Drives ONE continuous {@code GameLoop} via the
 * shared {@link AbstractRunChainTest} base and asserts the S1 special-stage
 * return-boundary shape: a NEXT-ACT advance (no positional restore) plus the
 * emerald-count increment.
 *
 * <h2>Current RED: the {@code run_tail} dynamic-art edge, and what the residual
 * is actually made of</h2>
 *
 * <p>{@code dynamicArtGapJournal.verifyTerminal} compares the run's LAST
 * dynamic-art gap edge against {@code run_manifest.json}'s
 * {@code dynamic_art_gap_transitions} tail entry. The recorded tail edge is
 * {@code edge_ordinal 4698/4699}, {@code transfer_id 2349},
 * {@code movie_logical_frame 9071}; the engine produces {@code 4710/4711},
 * {@code 2355}, {@code 9035}. The row-stamping fix that closed the former
 * 810-row deficit ({@code stateMovieLogicalRow}) is correct and is NOT the
 * remaining cause; nor is the returning level's load on the wrong side of the
 * results bridge (that theory is refuted — the load is already on the correct
 * side). The residual is TWO independent causes, measured by dumping every
 * engine transfer and diffing it against the recorded per-row
 * {@code dynamic_art_transfer_state} stream of all three segments:
 *
 * <ol>
 *   <li><b>+12 ordinals / +6 transfers — a Sonic ANIMATION divergence in
 *   {@code ghz1} at local frame 2083.</b> The two transfer streams are edge-for-edge
 *   identical up to transfer 862. The recorded run leaves the rolling animation
 *   there ({@code physics.csv} 0x822: {@code player_rolling 1 -> 0},
 *   {@code player_animation_id 02 -> 00}, {@code player_mapping_frame 0x2E -> 0x1A})
 *   after two airborne frames, and its DPLC then steps DOWN the walk set
 *   (26,27,28,29,24,25,20,21,16,11,6..10) at 5-9 row intervals as Sonic decelerates.
 *   The engine stays on the ROLL set (46..50) across the same span and emits 17
 *   transfers where the ROM emits 9. The streams re-align immediately afterwards
 *   and stay aligned for the remaining ~1500 transfers, so the whole ordinal /
 *   transfer_id skew is banked in that one window. Note the level segments'
 *   {@code LiveTraceComparator} does not compare {@code dynamic_art.edges} (only
 *   {@code DynamicArtSpecialStageComparator} does), which is why a real divergence
 *   here surfaces only at the terminal edge.</li>
 *
 *   <li><b>-36 movie rows — the un-modelled S1 level-load span.</b> The tail edge
 *   is the level-load player DPLC preparation ({@code mapping_frame 1},
 *   {@code submission_origin run_gap}, the same shape as the run's very first edge
 *   at row 748 = the {@code ghz1} offset 774 minus the ROM-derived 26). Instrumenting
 *   the terminal tail gives the engine's phase lengths: {@code SPECIAL_STAGE_RESULTS}
 *   22 rows (8861-8882), {@code TITLE_CARD} 151 rows (8883-9033), then {@code LEVEL}
 *   to the movie's last row 9092. The engine reaches the next level's first main-loop
 *   row 36 rows earlier than the ROM did — the ROM's stamp of 9071 implies its first
 *   main-loop row is 9097, i.e. BEYOND the movie's end, so on hardware the level was
 *   still inside its pre-main-loop load when the recording stopped. The engine spends
 *   ZERO rows on {@code ClearScreen} / {@code LevelDataLoad} / {@code LoadTilesFromStart}
 *   / {@code ObjPosLoad} / {@code NemDec}; only the trailing {@code Level_Delay} 4 +
 *   {@code PalFadeIn_Alt} 22 are modelled. That is exactly the level-load-span strand of
 *   {@code docs/architecture/plans/trace/2026-08-06-trace-validation-roadmap.md} (§4),
 *   whose stated prerequisite is a RECORDED level-load span segment plus an
 *   engine-counted load model. It cannot be closed from frame-granularity ROM control
 *   flow — the span's length is 68000 cycle cost — and any constant fitted to this
 *   fixture's 36 would desync the first different recording.</li>
 * </ol>
 */
@RequiresRom(SonicGame.SONIC_1)
class TestS1GhzMazeRoundTripChain extends AbstractRunChainTest {

    private static final Path DEFAULT_RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s1", "runs", "s1-ghz-maze-roundtrip");
    private static final String EXTERNAL_RUN_DIR_PROPERTY = "openggf.trace.s1.run.dir";

    private Path activeRunDir;

    @Test
    void ghzMazeSpecialStageReturnPresentationBridge() throws Exception {
        assertChainReplayThroughSegmentRow(
                DEFAULT_RUN_DIR, 2, 812);
    }

    @Test
    void ghzMazeRoundTrip() throws Exception {
        String configuredRunDir = System.getProperty(EXTERNAL_RUN_DIR_PROPERTY);
        activeRunDir = configuredRunDir == null || configuredRunDir.isBlank()
                ? DEFAULT_RUN_DIR
                : Path.of(configuredRunDir).toAbsolutePath().normalize();
        DynamicArtGapJournalEvidence evidence = assertChainReplay(activeRunDir);
        assertEquals(List.of(
                        AdmitDestination.class,
                        CloseSegment.class, EnterTransitionGap.class,
                        AdmitDestination.class,
                        CloseSegment.class, EnterTransitionGap.class,
                        AdmitDestination.class,
                        CloseSegment.class, BeginTerminalTail.class,
                        CompleteRun.class),
                evidence.coordinatorActions().stream()
                        .map(Object::getClass).toList(),
                "the real headless chain must follow the shared coordinator transcript");
        DynamicArtStructuralGapEvidence returnGap =
                evidence.structuralGap("ss", "ghz2");
        assertTrue(returnGap.transitionCountAfterNextArm()
                        > evidence.transitionCountAfterFirstArm(),
                "the real S1 represented-segment -> named-run gap -> next-segment "
                        + "boundary must grow the journal beyond first-arm bootstrap");
        assertTrue(returnGap.transitionCountAfterNextArm()
                        > returnGap.transitionCountAtGapStart(),
                "the real S1 ss -> ghz2 structural gap must append production art");
        assertTrue(returnGap.lastEdgeOrdinalAfterNextArm()
                        > evidence.lastEdgeOrdinalAfterFirstArm(),
                "the real S1 named-run gap must append a later production edge ordinal");
        assertTrue(returnGap.lastEdgeOrdinalAfterNextArm()
                        > returnGap.lastEdgeOrdinalAtGapStart(),
                "the real S1 ss -> ghz2 structural gap must advance the edge ordinal");
        assertTrue(returnGap.transitionsAddedAcrossBoundary().stream()
                        .map(transition -> transition.edge())
                        .anyMatch(edge -> edge.movieLogicalFrame()
                                >= returnGap.gapStartMovieLogicalFrame()
                                && edge.movieLogicalFrame()
                                <= returnGap.nextSegmentArmMovieLogicalFrame()),
                "the real S1 named-run boundary must add a production art edge "
                        + "inside its structural gap");
    }

}
