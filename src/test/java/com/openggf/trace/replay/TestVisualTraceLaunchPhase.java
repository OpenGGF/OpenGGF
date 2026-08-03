package com.openggf.trace.replay;

import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestVisualTraceLaunchPhase {

    @Test
    void titleCardMustReturnToLevelAndFinishOverlayBeforeReplayStarts() {
        VisualTraceLaunchPhase phase = new VisualTraceLaunchPhase();
        phase.beginTitleCardPresentation();

        assertTrue(phase.ownsEarlyExit());
        assertFalse(phase.beginReplayBootstrapIfReady(
                GameMode.TITLE_CARD, false));
        assertFalse(phase.beginReplayBootstrapIfReady(
                GameMode.LEVEL, false));
        assertTrue(phase.beginReplayBootstrapIfReady(
                GameMode.LEVEL, true));
        assertFalse(phase.beginReplayBootstrapIfReady(
                GameMode.LEVEL, true),
                "replay bootstrap must be admitted exactly once");
    }

    @Test
    void activeAndAbortedPhasesDoNotRetainPresentationEarlyExitState() {
        VisualTraceLaunchPhase active = new VisualTraceLaunchPhase();
        active.beginTitleCardPresentation();
        assertTrue(active.beginReplayBootstrapIfReady(GameMode.LEVEL, true));
        active.markActive();
        assertFalse(active.isPresentingTitleCard());

        VisualTraceLaunchPhase aborted = new VisualTraceLaunchPhase();
        aborted.beginTitleCardPresentation();
        aborted.abort();
        assertFalse(aborted.ownsEarlyExit());
        assertFalse(aborted.beginReplayBootstrapIfReady(GameMode.LEVEL, true));
    }
}
