package com.openggf.debug.playback;

import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ROM-free public-boundary coverage for playback input cleanup and owner handoff. */
class TestPlaybackInputBridgeOwnership {

    @Test
    void ordinaryStopClearsItsOwnOverrideAndPublishesLiveNeutralInput() {
        Fixture f = new Fixture();
        f.publish(snapshot(AbstractPlayableSprite.INPUT_RIGHT, 0x04));

        f.stop(f.input);

        assertFalse(f.input.hasLogicalOverride());
        assertEquals(LogicalInputSnapshot.neutral(), f.input.logical(),
                "cleanup must refresh the live sample as well as clear the override");
    }

    @Test
    void missingSpriteManagerClearsItsOwnImmediatePublication() {
        Fixture f = new Fixture();
        LogicalInputSnapshot owned = snapshot(AbstractPlayableSprite.INPUT_RIGHT, 0x04);
        when(f.playback.getCurrentLogicalInputSnapshot()).thenReturn(owned);
        f.bridge.publishImmediately(f.playback, f.input, f.sprites);
        assertSame(owned, f.input.logical());

        f.bridge.sync(f.playback, GameMode.LEVEL, f.input, null);

        assertFalse(f.input.hasLogicalOverride());
        assertEquals(LogicalInputSnapshot.neutral(), f.input.logical());
    }

    @Test
    void laterDifferentSnapshotSurvivesTheOldPlaybackOwnerStopping() {
        Fixture f = new Fixture();
        f.publish(snapshot(AbstractPlayableSprite.INPUT_RIGHT, 0x04));
        LogicalInputSnapshot replacement = snapshot(AbstractPlayableSprite.INPUT_LEFT, 0x01);
        f.input.setLogicalOverride(replacement);

        f.stop(f.input);

        assertOverrideSurvivesRefresh(f.input, replacement);
    }

    @Test
    void laterValueEqualButDistinctSnapshotAlsoSurvives() {
        Fixture f = new Fixture();
        LogicalInputSnapshot owned = snapshot(AbstractPlayableSprite.INPUT_RIGHT, 0x04);
        LogicalInputSnapshot replacement = snapshot(AbstractPlayableSprite.INPUT_RIGHT, 0x04);
        assertEquals(owned, replacement);
        assertNotSame(owned, replacement, "record equality does not identify an input publication owner");
        f.publish(owned);
        f.input.setLogicalOverride(replacement);

        f.stop(f.input);

        assertOverrideSurvivesRefresh(f.input, replacement);
    }

    @Test
    void nativeStagePublishedSampleDoesNotDisownTheSameRawOverride() {
        Fixture f = new Fixture();
        LogicalInputSnapshot owned = snapshot(AbstractPlayableSprite.INPUT_RIGHT, 0x04);
        f.publish(owned);
        InputHandlerInternalAccess.publishNativeStagePlayers(f.input,
                PlayerInputState.of(AbstractPlayableSprite.INPUT_LEFT,
                        AbstractPlayableSprite.INPUT_LEFT, 0, 0, false, false),
                PlayerInputState.neutral());
        assertNotSame(owned, f.input.logical(), "native stage replaces the published logical sample only");
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, f.input.logical().player1().heldMask());
        // Do not refresh here: doing so would hide the distinction between the
        // stage's published sample and the still-owned raw logical override.

        f.stop(f.input);

        assertFalse(f.input.hasLogicalOverride(), "the original raw override is still playback's to release");
        assertEquals(LogicalInputSnapshot.neutral(), f.input.logical());
    }

    @Test
    void cleanupOnceCannotClearASubsequentPublication() {
        Fixture f = new Fixture();
        f.publish(snapshot(AbstractPlayableSprite.INPUT_RIGHT, 0x04));
        f.stop(f.input);
        assertFalse(f.input.hasLogicalOverride());
        LogicalInputSnapshot replacement = snapshot(AbstractPlayableSprite.INPUT_LEFT, 0x01);
        f.input.setLogicalOverride(replacement);

        f.stop(f.input);

        assertOverrideSurvivesRefresh(f.input, replacement);
    }

    @Test
    void stoppingOnAnotherHandlerCannotClearEvenTheSameSnapshotInstanceThere() {
        Fixture f = new Fixture();
        LogicalInputSnapshot sharedValue = snapshot(AbstractPlayableSprite.INPUT_RIGHT, 0x04);
        f.publish(sharedValue);
        InputHandler replacementHandler = newInput();
        replacementHandler.setLogicalOverride(sharedValue);

        f.stop(replacementHandler);

        assertOverrideSurvivesRefresh(replacementHandler, sharedValue);
        // This control only requires preservation of the different handler.
        // It does not prescribe eager cleanup versus retained ownership on f.input.
    }

    private static void assertOverrideSurvivesRefresh(InputHandler input, LogicalInputSnapshot expected) {
        assertTrue(input.hasLogicalOverride(), "the replacement still owns the raw override");
        assertSame(expected, input.logical(), "stopping playback cannot replace another owner's sample");
        input.refreshLogicalSnapshot();
        assertSame(expected, input.logical(), "the replacement must remain the active override after live refresh");
    }

    private static InputHandler newInput() {
        // The default constructor uses synthetic standalone bindings and a
        // no-device gamepad source, with no desktop/global input ownership.
        return new InputHandler();
    }

    private static LogicalInputSnapshot snapshot(int direction, int action) {
        return LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(direction, direction, action, action, false, false),
                PlayerInputState.neutral());
    }

    private static final class Fixture {
        final PlaybackInputBridge bridge = new PlaybackInputBridge();
        final PlaybackDebugManager playback = mock(PlaybackDebugManager.class);
        final SpriteManager sprites = mock(SpriteManager.class);
        final InputHandler input = newInput();

        void publish(LogicalInputSnapshot snapshot) {
            when(playback.isDriving(GameMode.LEVEL)).thenReturn(true);
            when(playback.getCurrentLogicalInputSnapshot()).thenReturn(snapshot);
            bridge.sync(playback, GameMode.LEVEL, input, sprites);
            assertTrue(input.hasLogicalOverride());
            assertSame(snapshot, input.logical());
        }

        void stop(InputHandler input) {
            when(playback.isDriving(GameMode.LEVEL)).thenReturn(false);
            bridge.sync(playback, GameMode.LEVEL, input, sprites);
        }
    }
}
