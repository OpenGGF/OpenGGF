package com.openggf.tools;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestInputLogAuthorTool {

    @Test
    void writesPlainInputLogThatTheLoaderReadsBack(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("nested/run.txt");
        InputLogAuthorTool.Result result = InputLogAuthorTool.author("10 R\n1 A\n5 -", out, "s3k");
        assertEquals(16, result.frameCount());
        assertEquals("10xR, 1xA, 5x-", result.summary());
        String text = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(text.contains("|...R....|........|"), text);
        Bk2Movie movie = new Bk2MovieLoader().loadInputLog(out);
        assertEquals(16, movie.getFrameCount());
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, movie.getFrame(0).p1InputMask());
        assertEquals(movie.getFrameCount(), new Bk2MovieLoader().loadMovieOrInputLog(out).getFrameCount());
    }

    @Test
    void writesBk2ContainerThatTheZipLoaderReadsBack(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("run.bk2");
        InputLogAuthorTool.author("repeat 3 { 1 A ; 1 - }", out, "s1");
        Bk2Movie movie = new Bk2MovieLoader().load(out);
        assertEquals(6, movie.getFrameCount());
        assertEquals(6, new Bk2MovieLoader().loadMovieOrInputLog(out).getFrameCount());
    }

    @Test
    void rejectsEmptyScriptsAndMissingArguments(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> InputLogAuthorTool.author("# nothing", dir.resolve("x.txt"), null));
        assertThrows(IllegalArgumentException.class,
                () -> InputLogAuthorTool.Arguments.parse(new String[] {"--out", "x.txt"}));
        assertThrows(IllegalArgumentException.class,
                () -> InputLogAuthorTool.Arguments.parse(new String[] {"--inline", "1 R"}));
    }

    @Test
    void cliArgumentsAcceptScriptFilesAndInlineText(@TempDir Path dir) throws Exception {
        Path script = dir.resolve("s.txt");
        Files.writeString(script, "2 L");
        InputLogAuthorTool.Arguments fromFile = InputLogAuthorTool.Arguments.parse(
                new String[] {"--script", script.toString(), "--out", dir.resolve("o.txt").toString()});
        assertEquals("2 L", fromFile.script());
        InputLogAuthorTool.Arguments inline = InputLogAuthorTool.Arguments.parse(
                new String[] {"--inline", "3 R", "--out", "o.bk2", "--game", "s2"});
        assertEquals("3 R", inline.script());
        assertEquals("s2", inline.game());
    }
}
