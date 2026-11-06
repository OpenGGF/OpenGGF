package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestGameplayInputBranchTool {
    @Test void callbackBearingFadeIsRejectedWithoutDiscardingItsOwner() {
        var fade = new com.openggf.graphics.FadeManager();
        fade.startFadeToBlack(() -> { });
        var checkpoint = new com.openggf.game.rewind.CompositeSnapshot(
                java.util.Map.of("fademanager", fade.capture()));
        var failure = assertThrows(IllegalStateException.class,
                () -> GameplayInputBranchTool.requireRestorableCheckpoint(checkpoint, false));
        assertTrue(failure.getMessage().contains("callback-bearing fade"));
        assertTrue(fade.hasPendingCompletion(), "validation must preserve the live completion");
    }

    @Test void callbackFreeVisualFadeRemainsRestorable() {
        var fade = new com.openggf.graphics.FadeManager();
        fade.startFadeFromBlack(null);
        var checkpoint = new com.openggf.game.rewind.CompositeSnapshot(
                java.util.Map.of("fademanager", fade.capture()));
        assertDoesNotThrow(() -> GameplayInputBranchTool.requireRestorableCheckpoint(checkpoint, false));
    }

    @Test void parkedFreshAssemblyIsRejectedBeforeItCanBeRestored() {
        var checkpoint = new com.openggf.game.rewind.CompositeSnapshot(java.util.Map.of());
        var failure = assertThrows(IllegalStateException.class,
                () -> GameplayInputBranchTool.requireRestorableCheckpoint(checkpoint, true));
        assertTrue(failure.getMessage().contains("pending fresh level assembly"));
    }

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

    @Test void movieRangeScriptStartsInsideTheMovie(@TempDir Path dir) throws Exception {
        var source = dir.resolve("source.bk2");
        InputLogAuthorTool.author("4 L;3 R+A;2 -;5 D", source, "s1");
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(source);
        assertEquals("2 R+A / -\n2 - / -\n1 D / -\n", GameplayInputBranchTool.movieScript(movie, 5, 5));
        assertEquals(GameplayInputBranchTool.prefixScript(movie, 9), GameplayInputBranchTool.movieScript(movie, 0, 9));
        assertEquals("", GameplayInputBranchTool.movieScript(movie, 14, 0));
        assertThrows(IllegalArgumentException.class, () -> GameplayInputBranchTool.movieScript(movie, 10, 5));
        assertThrows(IllegalArgumentException.class, () -> GameplayInputBranchTool.movieScript(movie, -1, 2));
    }

    @Test void polledScriptDropsOnlyTheDeclaredLagRows(@TempDir Path dir) throws Exception {
        var source = dir.resolve("source.bk2");
        InputLogAuthorTool.author("2 L;1 A;3 R;1 B;2 -", source, "s1");
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(source);
        assertEquals("1 L / -\n3 R / -\n2 - / -\n",
                GameplayInputBranchTool.polledScript(movie, 1, java.util.Set.of(2, 6)));
        assertThrows(IllegalArgumentException.class,
                () -> GameplayInputBranchTool.polledScript(movie, 3, java.util.Set.of(2)));
    }

    @Test void continuationResumesAfterTheCandidateOrFromADeclaredRow(@TempDir Path dir) throws Exception {
        var source = dir.resolve("source.bk2");
        InputLogAuthorTool.author("3 L;3 R;3 A;3 -", source, "s1");
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(source);
        assertEquals("1 A / -\n", GameplayInputBranchTool.continuation(movie, 0, 4, "2 D;2 U", 1, -1));
        assertEquals("1 R / -\n1 A / -\n", GameplayInputBranchTool.continuation(movie, 1, 4, "2 D", 2, 4));
        assertEquals("", GameplayInputBranchTool.continuation(movie, 0, 4, "2 D", 0, 2));
    }
}
