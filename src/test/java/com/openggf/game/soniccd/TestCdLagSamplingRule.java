package com.openggf.game.soniccd;

import com.openggf.trace.FrameComparison;
import com.openggf.trace.TraceBinder;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The replay's sampling rule ({@link CdFixtureReplay#completedRow}) on the native fixtures, without the engine.
 *
 * <p>Shape: every row the rule moves is followed only by rows with its own {@code levelFrames} on which the
 * V-INT counter advanced by at most one, and each such plateau's first row is a frame the recorder flagged as lag
 * (VInt_Lag, Level/Main.asm:738-766, reads no pads), a V-INT that had not finished at the sample
 * (counter +0) or a frame whose V-INT ran from a blocking loop inside the pass (pads read, counter +1). No other row
 * is moved.
 *
 * <p>No hiding: an engine one level frame late (or early) next to a plateau is the native completed state of the
 * previous (next) level frame. Compared with the rule's target by the replay's own binder, it is an error on every
 * such row where native moved between the two level frames; rows where native did not move cannot show lateness to
 * any comparison and are counted apart.
 */
class TestCdLagSamplingRule {
    private static final String[] ROOTS = {"rehost-fixtures", "rehost-long"};

    @Test
    void theRuleMovesOnlyRowsFollowedByASameLevelFramePlateau() throws Exception {
        int moved = 0, flagged = 0, held = 0, blocking = 0, traces = 0;
        for (String rootName : ROOTS) {
            Path root = root(rootName);
            for (CdStagePayload payload : CdStagePayload.values()) {
                Path dir = root.resolve(payload.name()).resolve("trace");
                if (!Files.isRegularFile(dir.resolve("metadata.json"))) continue;
                TraceData trace = TraceData.load(dir);
                traces++;
                for (int i = 0; i < trace.frameCount(); i++) {
                    int end = CdFixtureReplay.completedRow(trace, i);
                    if (end == i) continue;
                    moved++;
                    TraceFrame row = trace.getFrame(i);
                    for (int k = i + 1; k <= end; k++) {
                        TraceFrame f = trace.getFrame(k), p = trace.getFrame(k - 1);
                        assertEquals(row.gameplayFrameCounter(), f.gameplayFrameCounter(), payload + " row " + k);
                        int dv = (f.vblankCounter() - p.vblankCounter()) & 0xFFFF;
                        assertTrue(dv <= 1, payload + " row " + k + ": V-INT counter +" + dv + " inside a plateau");
                    }
                    TraceFrame first = trace.getFrame(i + 1), before = trace.getFrame(i);
                    var lag = trace.lagStateForFrame(i + 1);
                    if (lag != null && lag.lagged()) flagged++;
                    else if (((first.vblankCounter() - before.vblankCounter()) & 0xFFFF) == 0) held++;
                    else blocking++;
                }
            }
        }
        Assumptions.assumeTrue(traces > 0, "no native fixtures");
        System.out.printf("LAGRULE traces %d moved rows %d: lag-flagged %d, V-INT unfinished %d, blocking loop %d%n",
                traces, moved, flagged, held, blocking);
        assertTrue(moved > 1000, "the rule must apply to the fixtures' plateaus");
    }

    @Test
    void anEngineOneLevelFrameLateOrEarlyBesideAPlateauStillFails() throws Exception {
        TraceBinder binder = CdFixtureReplay.binder();
        int checked = 0, detected = 0, still = 0, traces = 0;
        for (String rootName : ROOTS) {
            Path root = root(rootName);
            for (CdStagePayload payload : CdStagePayload.values()) {
                Path dir = root.resolve(payload.name()).resolve("trace");
                if (!Files.isRegularFile(dir.resolve("metadata.json"))) continue;
                TraceData trace = TraceData.load(dir);
                traces++;
                // Level-frame starts and their completed rows, in order.
                java.util.List<int[]> frames = new java.util.ArrayList<>();
                for (int i = 1; i < trace.frameCount(); i++) {
                    if (trace.getFrame(i).gameplayFrameCounter() == trace.getFrame(i - 1).gameplayFrameCounter()) continue;
                    frames.add(new int[] {i, CdFixtureReplay.completedRow(trace, i)});
                }
                for (int n = 1; n + 1 < frames.size(); n++) {
                    int[] cur = frames.get(n), prev = frames.get(n - 1), next = frames.get(n + 1);
                    boolean beside = cur[1] != cur[0] || prev[1] != prev[0];
                    if (!beside) continue;
                    TraceFrame target = trace.getFrame(cur[1]);
                    for (TraceFrame wrong : new TraceFrame[] {trace.getFrame(prev[1]), trace.getFrame(next[1])}) {
                        checked++;
                        if (wrong.stateEquals(target) && wrong.cameraX() == target.cameraX()
                                && wrong.cameraY() == target.cameraY()) {
                            still++;
                            continue;
                        }
                        FrameComparison c = CdFixtureReplay.compareNative(binder, target, wrong);
                        if (c.hasError()) detected++;
                        else throw new AssertionError(payload + " row " + cur[0] + ": a one-frame-off state passes");
                    }
                }
            }
        }
        Assumptions.assumeTrue(traces > 0, "no native fixtures");
        System.out.printf("LAGRULE one-frame-off beside a plateau: %d checked, %d detected, %d with no native change%n",
                checked, detected, still);
        assertEquals(checked, detected + still);
        assertTrue(detected > 1000, "the check must see one-frame-off states");
    }

    private static Path root(String name) {
        return CdTestMedia.nativeFile(name + "/README.md").getParent();
    }
}
