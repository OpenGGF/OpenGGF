package com.openggf.tools;

import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceCaptureLagFrames {

    private static final Path CHEMICAL_PLANT_2 =
            Path.of("src", "test", "resources", "traces", "s2", "cpz2");

    @Test
    void aHeldGameplayCounterOverAnElapsedVblankIsALagFrame() {
        TraceFrame[] rows = {
                row(0x1155, 0x3253, 0x1143), row(0x1156, 0x3254, 0x1144),
                row(0x1157, 0x3255, 0x1144), row(0x1158, 0x3256, 0x1145)};

        assertTrue(lag(TraceExecutionPhase.VBLANK_ONLY, rows, 2));
        assertTrue(lag(TraceExecutionPhase.PLAYABLE_ANIMATION_ONLY, rows, 2));
        assertFalse(lag(TraceExecutionPhase.VBLANK_ONLY, rows, 1), "its level loop ran");
        assertFalse(lag(TraceExecutionPhase.VBLANK_ONLY, rows, 3), "its level loop ran");
    }

    @Test
    void aRowTheReplayRanGameplayOnIsNeverALagFrame() {
        TraceFrame[] rows = {row(10, 0x200, 0x100), row(11, 0x201, 0x100), row(12, 0x202, 0x101)};

        assertFalse(lag(TraceExecutionPhase.FULL_LEVEL_FRAME, rows, 1));
        assertFalse(lag(TraceExecutionPhase.FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD, rows, 1));
    }

    @Test
    void aRowNeedsAnElapsedVblankAndARowBeforeIt() {
        TraceFrame[] rows = {row(10, 0x200, 0x100), row(11, 0x200, 0x100), row(12, 0x201, 0x101)};

        assertFalse(lag(TraceExecutionPhase.VBLANK_ONLY, rows, 1),
                "no V-blank elapsed: the original displayed nothing");
        assertFalse(lag(TraceExecutionPhase.VBLANK_ONLY, rows, 0),
                "the first row has nothing to repeat");
    }

    @Test
    void aHoldCountsAsLagOnlyWhileItIsShort() {
        TraceFrame[] shortHold = hold(TraceCaptureLagFrames.LONGEST_LAG_HOLD);
        for (int index = 1; index <= TraceCaptureLagFrames.LONGEST_LAG_HOLD; index++) {
            assertTrue(lag(TraceExecutionPhase.VBLANK_ONLY, shortHold, index), "row " + index);
        }

        // A title card, fade, load or pause holds the gameplay counter too, for longer.
        TraceFrame[] longHold = hold(TraceCaptureLagFrames.LONGEST_LAG_HOLD + 1);
        for (int index = 1; index <= TraceCaptureLagFrames.LONGEST_LAG_HOLD + 1; index++) {
            assertFalse(lag(TraceExecutionPhase.VBLANK_ONLY, longHold, index), "row " + index);
        }
        assertEquals(0, TraceCaptureLagFrames.holdRows(i -> longHold[i], longHold.length, 0));
        assertEquals(0, TraceCaptureLagFrames.holdRows(
                i -> longHold[i], longHold.length, longHold.length - 1));
    }

    @Test
    void theRecordedChemicalPlantStutterIsNineteenLagRows() throws Exception {
        TraceData trace = TraceData.load(CHEMICAL_PLANT_2);
        TraceCaptureLagFrames lagFrames = TraceCaptureLagFrames.skipLagFrames();

        List<Integer> lagRows = new ArrayList<>();
        for (int row = 4300; row <= 4559; row++) {
            TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(
                    trace, trace.getFrame(row - 1), trace.getFrame(row));
            if (lagFrames.skips(phase, trace, row)) {
                lagRows.add(row);
            }
        }

        assertEquals(List.of(4439, 4444, 4449, 4452, 4456, 4460, 4463, 4466, 4469, 4471,
                4473, 4476, 4478, 4480, 4483, 4491, 4493, 4500, 4505), lagRows);
    }

    @Test
    void theMappingNamesTheTraceRowOfEveryOutputFrame() {
        TraceCaptureLagFrames lagFrames = TraceCaptureLagFrames.skipLagFrames();
        for (int row : new int[]{4300, 4301, 4302, 4304, 4305, 4307}) {
            lagFrames.filmed(row);
        }
        lagFrames.skipped(4303);
        lagFrames.skipped(4306);

        assertTrue(lagFrames.skipping());
        assertEquals(List.of(4300, 4301, 4302, 4304, 4305, 4307), lagFrames.frameRows());
        assertEquals(List.of(4303, 4306), lagFrames.skippedRows());
        assertEquals("0-2 -> 4300-4302, 3-4 -> 4304-4305, 5 -> 4307",
                lagFrames.describeFrameRows());
    }

    @Test
    void aCaptureThatKeepsItsLagFramesSkipsAndRecordsNothing() throws Exception {
        TraceCaptureLagFrames keep = TraceCaptureLagFrames.KEEP;
        TraceData trace = TraceData.load(CHEMICAL_PLANT_2);

        assertFalse(keep.skipping());
        assertFalse(keep.skips(TraceExecutionPhase.VBLANK_ONLY, trace, 4439));
        keep.filmed(4300);
        keep.skipped(4301);
        assertTrue(keep.frameRows().isEmpty());
        assertTrue(keep.skippedRows().isEmpty());
        assertEquals("", keep.describeFrameRows());
    }

    private static boolean lag(TraceExecutionPhase phase, TraceFrame[] rows, int index) {
        return TraceCaptureLagFrames.isLagRow(phase, i -> rows[i], rows.length, index);
    }

    /** One played row, {@code length} rows holding its gameplay counter, one played row. */
    private static TraceFrame[] hold(int length) {
        TraceFrame[] rows = new TraceFrame[length + 2];
        rows[0] = row(0, 0x400, 0x300);
        for (int index = 1; index <= length; index++) {
            rows[index] = row(index, 0x400 + index, 0x300);
        }
        rows[length + 1] = row(length + 1, 0x401 + length, 0x301);
        return rows;
    }

    private static TraceFrame row(int frame, int vblankCounter, int gameplayFrameCounter) {
        return TraceFrame.executionTestFrame(frame, vblankCounter, gameplayFrameCounter, 0);
    }
}
