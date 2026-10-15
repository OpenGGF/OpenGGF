package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestFadeWindowRetimeTool {
    private static Bk2FrameInput pad(int index, int p1) {
        return new Bk2FrameInput(index, p1, 0, false, "");
    }

    @Test
    void doubledAuthoredWindowRepeatsItsLastInputForTheNativeRemainder() {
        var frames = new ArrayList<Bk2FrameInput>();
        for (int i = 0; i < 20; i++) frames.add(pad(i, i < 10 ? 8 : 4));
        // Native 22-frame window starting at 3: the authored movie spent 11 inputs (3..13).
        var window = FadeWindowRetimeTool.insertRepeats(frames, 3, 22, 2);
        assertEquals(new FadeWindowRetimeTool.Window(3, 22, 11, 11), window);
        assertEquals(31, frames.size());
        for (int i = 14; i < 25; i++) assertEquals(frames.get(13), frames.get(i), "repeat " + i);
        assertEquals(4, frames.get(25).p1InputMask(), "authored frame 14 follows the window");
        assertEquals(14, frames.get(25).frameIndex());
    }

    @Test
    void oddWindowsKeepTheAuthoredCeilingAndOneAdvanceIsANoOp() {
        var frames = new ArrayList<Bk2FrameInput>(List.of(pad(0, 1), pad(1, 2), pad(2, 4), pad(3, 8)));
        assertEquals(new FadeWindowRetimeTool.Window(0, 3, 2, 1), FadeWindowRetimeTool.insertRepeats(frames, 0, 3, 2));
        assertEquals(List.of(1, 2, 2, 4, 8), frames.stream().map(Bk2FrameInput::p1InputMask).toList());
        assertEquals(0, FadeWindowRetimeTool.insertRepeats(frames, 0, 3, 1).inserted());
        assertEquals(5, frames.size());
    }

    @Test
    void scriptKeepsBothLanesInRuns() {
        var frames = List.of(pad(0, 8), pad(1, 8), new Bk2FrameInput(2, 0, 1, false, 2, 0, false, ""), pad(3, 0));
        assertEquals("2 R / -\n1 A / D\n1 - / -\n", FadeWindowRetimeTool.script(frames));
    }
}
