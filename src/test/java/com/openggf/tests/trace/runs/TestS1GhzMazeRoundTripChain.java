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

    /**
     * S1-specific ring semantics: skip the ring-count comparison (still assert
     * emeralds). ROM's {@code Level_LoadObj} unconditionally clears
     * {@code v_rings} on any fresh act entry when {@code v_lastlamp == 0}
     * (docs/s1disasm/sonic.asm:2898-2900 -- "are we starting from a
     * lamppost?"), and {@code Got_NextLevel} clears {@code v_lastlamp} for
     * BOTH the normal sign-post route AND the giant-ring route
     * (docs/s1disasm/_incObj/3A Got Through Card.asm:198, run before the
     * {@code f_bigring} branch). So S1, unlike S2/S3K, never carries rings
     * across an act transition -- the settled post-transition ring count is
     * deterministically 0 regardless of the Special Stage outcome.
     * <p>
     * The manifest's recorded {@code rings_after} (67) is the Special Stage's
     * own ring tally at the instant ROM's {@code v_gamemode} first flips back
     * to {@code id_Level} (docs/s1disasm/sonic.asm:3332, inside
     * {@code SS_ChkEnd} -- well before the results-screen/title-card sequence
     * and {@code Level_LoadObj}'s clear, which run afterward under that SAME
     * coarse {@code v_gamemode} value). Our engine models that same window
     * with distinct {@code GameMode} values ({@code SPECIAL_STAGE_RESULTS} ->
     * {@code TITLE_CARD} -> {@code LEVEL}), so {@code currentMode()==LEVEL}
     * only becomes observable AFTER the ROM-faithful reload has already run
     * and organically produced rings=0 -- the recorded 67 is not reproducible
     * at that later observation point by construction, not by an engine bug.
     */
    @Override
    protected boolean requireSharedBoundaryField(String fieldName) {
        return !"run_boundary.rings".equals(fieldName);
    }

}
