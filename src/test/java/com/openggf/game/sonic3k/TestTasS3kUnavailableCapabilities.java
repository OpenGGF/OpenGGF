package com.openggf.game.sonic3k;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit exercised capability limits for TAS 9155S/9169S and standalone publications.
 * A successful limitation assertion means unsupported, not native reset parity. */
class TestTasS3kUnavailableCapabilities {
    @TempDir Path directory;
    @Test void currentLimitationGenesisResetAndPowerAreDiscardedByThePlaybackBoundary() throws Exception {
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
        for(int i=0;i<3;i++) {
            var frame=movie.getFrame(i);
            assertEquals(0,frame.p1InputMask());assertEquals(0,frame.p2InputMask());
            assertEquals(0,frame.p1ActionMask());assertFalse(frame.p1StartPressed());
        }
        assertTrue(movie.getFrame(3).p1StartPressed(),"native Start survives the same parser; Reset does not");
    }
}
