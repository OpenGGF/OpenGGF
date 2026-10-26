package com.openggf.debug.playback;

import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameMode;
import com.openggf.sprites.managers.SpriteManager;

/** Owns publication and cleanup of recorded logical input during playback. */
public final class PlaybackInputBridge {
    private boolean inputSuppressed;
    private OwnedOverride ownedOverride;

    private record OwnedOverride(InputHandler input, LogicalInputSnapshot snapshot) { }

    public void sync(
            PlaybackDebugManager playback,
            GameMode mode,
            InputHandler input,
            SpriteManager sprites) {
        boolean shouldDrive = playback.isDriving(mode);
        if (sprites == null) {
            playback.clearLastAppliedState();
            clearOwnedOverride();
            inputSuppressed = false;
            return;
        }
        if (shouldDrive != inputSuppressed) {
            sprites.setPlaybackInputSuppressed(shouldDrive);
            inputSuppressed = shouldDrive;
        }
        if (shouldDrive) {
            publishImmediately(playback, input, sprites);
            return;
        }
        playback.clearLastAppliedState();
        clearOwnedOverride();
    }

    public void publishImmediately(
            PlaybackDebugManager playback,
            InputHandler input,
            SpriteManager sprites) {
        if (sprites != null && !inputSuppressed) {
            sprites.setPlaybackInputSuppressed(true);
            inputSuppressed = true;
        }
        if (input != null) {
            if (ownedOverride != null && ownedOverride.input() != input) {
                clearOwnedOverride();
            }
            input.setLogicalOverride(playback.getCurrentLogicalInputSnapshot());
            // Capture immediately after publication, before a native stage can
            // replace the logical sample while leaving the raw override intact.
            ownedOverride = new OwnedOverride(input, input.logical());
        }
    }

    private void clearOwnedOverride() {
        OwnedOverride owned = ownedOverride;
        ownedOverride = null;
        if (owned != null && InputHandlerInternalAccess.clearLogicalOverrideIfSame(
                owned.input(), owned.snapshot())) {
            owned.input().refreshLogicalSnapshot();
        }
    }
}
