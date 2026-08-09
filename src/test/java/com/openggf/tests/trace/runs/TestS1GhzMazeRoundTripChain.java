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
 * <h2>Current RED: the {@code run_tail} dynamic-art edge — one residual left</h2>
 *
 * <p>{@code dynamicArtGapJournal.verifyTerminal} compares the run's LAST
 * dynamic-art gap edge against {@code run_manifest.json}'s
 * {@code dynamic_art_gap_transitions} tail entry. The recorded tail edge is
 * {@code edge_ordinal 4698/4699}, {@code transfer_id 2349},
 * {@code movie_logical_frame 9071}. Ordinals, transfer ids and both ledger
 * fingerprints now match; only {@code movie_logical_frame} still diverges
 * (engine 9035, delta 36).
 *
 * <p>Two earlier theories are REFUTED and must not be reopened: the
 * row-stamping deficit ({@code stateMovieLogicalRow}), and the returning
 * level's load sitting on the wrong side of the results bridge. A third — a
 * {@code ghz1} rolling-animation divergence at local frame 2083 — was real and
 * has been fixed (the landing at 0x822 is an angled-CEILING landing routed
 * through {@code CollisionSystem.doCeilingCollision} ->
 * {@code resetWallCeilingLandingState}, so the walk animation was never set).
 *
 * <p>The former {@code +12} ordinal / {@code +6} transfer skew that fix left
 * behind resolved to a single missing transfer at the run's FIRST level load.
 * A fresh playable's art is established through
 * {@code DynamicArtLifecycleService.primePlayerDplc}, which deliberately
 * publishes no edge because a segment-scoped replay starts at
 * {@code Level_MainLoop} and never owns that transfer. A run's movie DOES span
 * that load: the recorder's transfer 0 is exactly it — {@code mapping_frame 1},
 * {@code submission_origin run_gap}, {@code movie_logical_frame 748}, i.e. the
 * {@code ghz1} offset 774 minus the ROM-counted pre-main-loop tail of 26
 * ({@code Level_Delay} 4 + {@code PalFadeIn_Alt} 22,
 * docs/s1disasm/sonic.asm:2956-2969). Priming now stages that bank and the run
 * publishes it before opening its first segment, which advances the counters by
 * the one transfer / two ordinals the whole run was short.
 *
 * <p>The remaining {@code -36} movie rows are the un-modelled S1 level-load
 * span. Instrumenting the terminal tail gives the engine's phase lengths:
 * {@code SPECIAL_STAGE_RESULTS} 22 rows (8861-8882), {@code TITLE_CARD} 151
 * rows (8883-9033), then {@code LEVEL} to the movie's last row 9092. The ROM's
 * stamp of 9071 implies its first main-loop row is 9097 — BEYOND the movie's
 * end — so on hardware the level was still inside its pre-main-loop load when
 * the recording stopped. The engine spends ZERO rows on {@code ClearScreen} /
 * {@code LevelDataLoad} / {@code LoadTilesFromStart} / {@code ObjPosLoad} /
 * {@code NemDec}; only the trailing {@code Level_Delay} 4 +
 * {@code PalFadeIn_Alt} 22 are modelled. That is the level-load-span strand of
 * docs/architecture/plans/trace/2026-08-06-trace-validation-roadmap.md (section
 * 4), whose stated prerequisite is a RECORDED level-load span segment plus an
 * engine-counted load model. It cannot be closed from frame-granularity ROM
 * control flow — the span's length is cycle cost — and any constant fitted to
 * this fixture's 36 would desync the first different recording.
 *
 * <p>Note the level segments' {@code LiveTraceComparator} does not compare
 * {@code dynamic_art.edges} (only {@code DynamicArtSpecialStageComparator}
 * does), which is why a transfer-stream divergence surfaces only here.
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
