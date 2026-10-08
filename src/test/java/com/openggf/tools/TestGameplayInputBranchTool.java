package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestGameplayInputBranchTool {
    @Test void compactPrefixPreservesBothPadsAndHeldStart(@TempDir Path dir) throws Exception {
        var source = dir.resolve("source.bk2");
        InputLogAuthorTool.author("3 R+A / L+B;2 D+S / U+C;5 -", source, "s3k");
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(source);
        String script = GameplayInputBranchTool.prefixScript(movie, 7);
        assertEquals(3, script.lines().count());
        var output = dir.resolve("prefix.bk2");
        InputLogAuthorTool.author(script, output, "s3k");
        var actual = loader.loadMovieOrInputLog(output);
        assertEquals(7, actual.getFrameCount());
        for (int i = 0; i < actual.getFrameCount(); i++) {
            assertEquals(movie.getFrame(i), actual.getFrame(i));
        }
        assertThrows(IllegalArgumentException.class, () -> GameplayInputBranchTool.prefixScript(movie, -1));
        assertThrows(IllegalArgumentException.class, () -> GameplayInputBranchTool.prefixScript(movie, 11));
        assertEquals("", GameplayInputBranchTool.prefixScript(movie, 0));
    }
}
