package com.openggf.tools;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.recording.RecordedFrameInput;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestInputScriptCompiler {

    @Test
    void holdsExpandToHeldFramesInOrder() {
        List<RecordedFrameInput> frames = InputScriptCompiler.compile("2 R\n1 D+R\n1 A\n1 -");
        assertEquals(5, frames.size());
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, frames.get(0).p1InputMask());
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, frames.get(1).p1InputMask());
        assertEquals(AbstractPlayableSprite.INPUT_DOWN | AbstractPlayableSprite.INPUT_RIGHT,
                frames.get(2).p1InputMask());
        assertEquals(InputActionMasks.ACTION_A, frames.get(3).p1ActionMask());
        assertEquals(AbstractPlayableSprite.INPUT_JUMP, frames.get(3).p1InputMask());
        assertEquals(0, frames.get(4).p1InputMask());
        assertEquals(0, frames.get(4).p1ActionMask());
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(i, frames.get(i).frame());
        }
    }

    @Test
    void wordTokensCommentsSemicolonsAndPlayerTwoLane() {
        List<RecordedFrameInput> frames = InputScriptCompiler.compile(
                "# leader runs, sidekick walks back\n1 right jump / left ; 1 start # tap start");
        assertEquals(2, frames.size());
        RecordedFrameInput first = frames.get(0);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP, first.p1InputMask());
        assertEquals(InputActionMasks.ACTION_A, first.p1ActionMask());
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, first.p2InputMask());
        assertTrue(frames.get(1).p1Start());
        assertFalse(frames.get(1).p2Start());
    }

    @Test
    void repeatBlocksNestAndInlineFormMatchesBlockForm() {
        List<RecordedFrameInput> block = InputScriptCompiler.compile(
                "repeat 2\n  1 A\n  repeat 2\n    1 -\n  end\nend");
        List<RecordedFrameInput> inline = InputScriptCompiler.compile("repeat 2 { 1 A ; 1 - ; 1 - }");
        assertEquals(6, block.size());
        assertEquals(inline.size(), block.size());
        for (int i = 0; i < block.size(); i++) {
            assertEquals(inline.get(i).p1ActionMask(), block.get(i).p1ActionMask(), "frame " + i);
        }
        assertEquals("1xA, 2x-, 1xA, 2x-", InputScriptCompiler.summarize(block));
    }

    @Test
    void malformedScriptsFailWithLineNumbers() {
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> InputScriptCompiler.compile("1 R\n1 X"));
        assertTrue(unknown.getMessage().startsWith("Line 2"), unknown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> InputScriptCompiler.compile("repeat 2\n1 R"));
        assertThrows(IllegalArgumentException.class, () -> InputScriptCompiler.compile("end"));
        assertThrows(IllegalArgumentException.class, () -> InputScriptCompiler.compile("x R"));
    }

    @Test
    void compiledLogRoundTripsThroughTheProductionLoader() throws Exception {
        String script = "3 R\n1 D+R\n2 A+R / L\n1 S\n4 -";
        List<RecordedFrameInput> expected = InputScriptCompiler.compile(script);
        String log = InputScriptCompiler.compileToInputLog(script);
        assertTrue(log.startsWith("[Input]\nLogKey:"), log);
        Bk2Movie movie = new Bk2MovieLoader().parseInputLogText(Path.of("inline"),
                Arrays.asList(log.split("\n")));
        assertEquals(expected.size(), movie.getFrameCount());
        for (int i = 0; i < expected.size(); i++) {
            Bk2FrameInput actual = movie.getFrame(i);
            RecordedFrameInput want = expected.get(i);
            assertEquals(want.p1InputMask(), actual.p1InputMask(), "p1 input at " + i);
            assertEquals(want.p1ActionMask(), actual.p1ActionMask(), "p1 action at " + i);
            assertEquals(want.p1Start(), actual.p1StartPressed(), "p1 start at " + i);
            assertEquals(want.p2InputMask(), actual.p2InputMask(), "p2 input at " + i);
        }
    }
}
