package com.openggf.tests;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHeadlessTestRunnerBk2Input {

    @Test
    void detectsNewActionButtonWhileAbstractJumpRemainsHeld() {
        Bk2Movie movie = new Bk2Movie(
                Path.of("synthetic.bk2"),
                "logkey",
                Map.of(),
                List.of(
                        frame(0, 0x02),
                        frame(1, 0x03),
                        frame(2, 0x03),
                        frame(3, 0x01)),
                1);

        assertTrue(HeadlessTestRunner.hasNewP1ActionPress(movie, 0),
                "Initial B hold is a ROM Ctrl_1_Press_Logical action edge");
        assertTrue(HeadlessTestRunner.hasNewP1ActionPress(movie, 1),
                "Adding A while B remains held must still produce a ROM-visible action press");
        assertFalse(HeadlessTestRunner.hasNewP1ActionPress(movie, 2),
                "Keeping the same A+B action set should not repeat the press edge");
        assertFalse(HeadlessTestRunner.hasNewP1ActionPress(movie, 3),
                "Releasing B while keeping A held is not a new action press");
    }

    private static Bk2FrameInput frame(int index, int actionMask) {
        return new Bk2FrameInput(index, 0, actionMask, false, "");
    }
}
