package com.openggf.debug.playback;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestBk2SystemActions {
    private final PlaybackDebugManager playback = PlaybackDebugManager.getInstance();

    @AfterEach void close() { playback.endSession(); }

    @Test void reorderedSystemGroupAndP2PadRemainIndependent() throws Exception {
        var movie = parse("#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|#Reset|Power|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|",
                "|.......S|r.|........|", "|........|.P|.......S|");
        assertEquals(Bk2SystemAction.RESET, Bk2SystemActions.at(movie, 0));
        assertTrue(movie.getFrame(0).p2StartPressed());
        assertFalse(movie.getFrame(0).p1StartPressed());
        assertEquals(Bk2SystemAction.POWER, Bk2SystemActions.at(movie, 1));
        assertTrue(movie.getFrame(1).p1StartPressed());
    }

    @Test void directNamedFieldsAndSimultaneousPowerHaveExplicitMeaning() throws Exception {
        var movie = parse("|Reset|Power|P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|",
                "|r|.|.|.|.|.|.|.|.|.|", "|r|P|.|.|.|.|.|.|.|S|");
        assertEquals(Bk2SystemAction.RESET, Bk2SystemActions.at(movie, 0));
        assertEquals(Bk2SystemAction.POWER, Bk2SystemActions.at(movie, 1));
        assertTrue(movie.getFrame(1).p1StartPressed());
    }

    @Test void pauseSeekAndComparisonPadOffsetCannotSelectAResetFromAnotherRow() throws Exception {
        var movie = parse("#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|",
                "|..|........|", "|.r|........|", "|P.|........|", "|..|.......S|");
        playback.startSession(movie, 0);
        playback.setFrameObserver(new PlaybackDebugManager.PlaybackFrameObserver() {
            @Override public int appliedInputOffset(Bk2FrameInput frame) { return 1; }
            @Override public boolean shouldSkipGameplayTick(Bk2FrameInput frame) { return false; }
            @Override public void afterFrameAdvanced(Bk2FrameInput frame, boolean skipped) { }
        });
        playback.prepareCurrentFrame();
        assertEquals(Bk2SystemAction.NONE, Bk2SystemActions.current(playback));
        playback.advanceCurrentFrameWithoutGameplay();
        assertEquals(Bk2SystemAction.RESET, Bk2SystemActions.current(playback));
        Bk2SystemActions.completeReset(playback);
        assertEquals(2, playback.getCursorFrame());
        assertEquals(Bk2SystemAction.POWER, Bk2SystemActions.current(playback));
        playback.seekSessionFrame(1, false);
        assertEquals(Bk2SystemAction.NONE, Bk2SystemActions.current(playback));
        playback.seekSessionFrame(1, true);
        assertEquals(Bk2SystemAction.RESET, Bk2SystemActions.current(playback));
    }

    @Test void unlabeledPadTextDoesNotCreateAResetAndTruncatedSystemRowsFail() throws Exception {
        var pad = parse("|UDLRSABC|", "|...R....|");
        assertEquals(Bk2SystemAction.NONE, Bk2SystemActions.at(pad, 0));
        assertThrows(IllegalArgumentException.class, () -> parse(
                "#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|",
                "|P|........|"));
    }

    @Test void releasingAnOldMenuSamplePreservesAnotherInputOwnerAndPendingPlaybackEdge() throws Exception {
        var movie = parse("#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|",
                "|....A...|");
        playback.startSession(movie, 0);
        var input = new com.openggf.control.InputHandler();
        var menu = new PlaybackInputBridge();
        var destination = new PlaybackInputBridge();
        menu.publishImmediately(playback, input, null);
        destination.publishImmediately(playback, input, null);
        var owned = input.logical();
        menu.releaseOwnedInput();
        assertSame(owned, input.logical());
        assertTrue(input.hasLogicalOverride());
        assertTrue(playback.isCurrentForcedJumpPress(), "the destination still owns its pending edge");
        destination.releaseOwnedInput();
        assertFalse(input.hasLogicalOverride());
    }

    private static Bk2Movie parse(String key, String... rows) throws Exception {
        var lines = new java.util.ArrayList<>(List.of("[Input]", "LogKey:" + key));
        lines.addAll(List.of(rows));
        lines.add("[/Input]");
        return new Bk2MovieLoader().parseInputLogText(Path.of("reset-input.txt"), lines);
    }
}
