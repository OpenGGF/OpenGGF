package com.openggf.game.sonic3k;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.Bk2SystemAction;
import com.openggf.debug.playback.Bk2SystemActions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Historical Reset/Power loss witness, upgraded without turning system requests into pad buttons.
 * The original negative result is retained in the dated 2026-10-28 reset validation record. */
class TestTasS3kUnavailableCapabilities {
    @TempDir Path directory;
    @Test void genesisResetAndPowerSurviveSeparatelyFromNeutralPadAndStartControls() throws Exception {
        var input=directory.resolve("reset-input.txt");
        Files.writeString(input,"""
                [Input]
                LogKey:#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|
                |..|........|
                |.r|........|
                |P.|........|
                |..|.......S|
                [/Input]
                """);
        var movie=new Bk2MovieLoader().loadMovieOrInputLog(input);
        assertEquals(4,movie.getFrameCount());
        assertEquals(Bk2SystemAction.NONE, Bk2SystemActions.at(movie, 0));
        assertEquals(Bk2SystemAction.RESET, Bk2SystemActions.at(movie, 1));
        assertEquals(Bk2SystemAction.POWER, Bk2SystemActions.at(movie, 2));
        assertEquals(Bk2SystemAction.NONE, Bk2SystemActions.at(movie, 3));
        for(int i=0;i<3;i++) {
            var frame=movie.getFrame(i);
            assertEquals(0,frame.p1InputMask());assertEquals(0,frame.p2InputMask());
            assertEquals(0,frame.p1ActionMask());assertFalse(frame.p1StartPressed());
        }
        assertTrue(movie.getFrame(3).p1StartPressed(),"native Start survives the same parser as a pad button");
    }
}
