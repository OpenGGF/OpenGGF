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
