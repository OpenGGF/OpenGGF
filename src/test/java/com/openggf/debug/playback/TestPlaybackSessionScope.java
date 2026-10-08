package com.openggf.debug.playback;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestPlaybackSessionScope {
    private final PlaybackDebugManager playback = PlaybackDebugManager.getInstance();

    @AfterEach void cleanup() { playback.endSession(); }

    @Test void closesOwnedActiveAndDeferredPlaybackOnFailure() {
        Bk2Movie movie = movie();
        assertThrows(IllegalStateException.class, () -> {
            try (var scope = new PlaybackSessionScope(playback, movie)) {
                playback.startSession(movie, 0);
                playback.scheduleSessionAtNextLevelLoad(movie, 0);
                throw new IllegalStateException("prefix interrupted");
            }
        });
        assertFalse(playback.hasActiveOrScheduledSession());
        assertFalse(playback.hasLoadedMovie());
    }

    @Test void preservesReplacementActiveMovie() {
        Bk2Movie owned = movie();
        try (var scope = new PlaybackSessionScope(playback, owned)) {
            playback.startSession(owned, 0);
            playback.startSession(movie(), 0);
        }
        assertTrue(playback.hasActiveOrScheduledSession());
    }

    @Test void preservesReplacementDeferredMovie() {
        Bk2Movie owned = movie();
        try (var scope = new PlaybackSessionScope(playback, owned)) {
            playback.startSession(owned, 0);
            playback.scheduleSessionAtNextLevelLoad(movie(), 0);
        }
        assertTrue(playback.activateScheduledLevelLoadSession());
        assertTrue(playback.hasActiveOrScheduledSession());
    }

    @Test void repeatedCloseDoesNotEndLaterPlaybackOfSameMovie() {
        Bk2Movie owned = movie();
        var scope = new PlaybackSessionScope(playback, owned);
        playback.startSession(owned, 0);
        scope.close();
        playback.startSession(owned, 0);
        scope.close();
        assertTrue(playback.hasActiveOrScheduledSession());
    }

    private static Bk2Movie movie() {
        return new Bk2Movie(Path.of("owner.bk2"), "", Map.of(),
                List.of(new Bk2FrameInput(0, 0, 0, false, "neutral")), 0);
    }
}
