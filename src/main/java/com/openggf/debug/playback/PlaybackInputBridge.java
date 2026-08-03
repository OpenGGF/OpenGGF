package com.openggf.debug.playback;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Owns publication and cleanup of recorded logical input during playback. */
public final class PlaybackInputBridge {
    private boolean inputSuppressed;
    private boolean logicalOverrideApplied;

    public void sync(
            PlaybackDebugManager playback,
            GameMode mode,
            InputHandler input,
            SpriteManager sprites,
            AbstractPlayableSprite player) {
        boolean shouldDrive = playback.isDriving(mode);
        if (sprites == null) {
            playback.clearLastAppliedState();
            clearOwnedOverride(input, player);
            inputSuppressed = false;
            return;
        }
        if (shouldDrive != inputSuppressed) {
            sprites.setPlaybackInputSuppressed(shouldDrive);
            inputSuppressed = shouldDrive;
        }
        if (shouldDrive && player != null) {
            publishImmediately(playback, input, sprites, player);
            return;
        }
        playback.clearLastAppliedState();
        clearOwnedOverride(input, player);
    }

    public void publishImmediately(
            PlaybackDebugManager playback,
            InputHandler input,
            SpriteManager sprites,
            AbstractPlayableSprite player) {
        if (sprites != null && !inputSuppressed) {
            sprites.setPlaybackInputSuppressed(true);
            inputSuppressed = true;
        }
        if (input != null) {
            input.setLogicalOverride(playback.getCurrentLogicalInputSnapshot());
            logicalOverrideApplied = true;
        }
        if (player != null) {
            player.setForcedJumpPress(playback.isCurrentForcedJumpPress());
        }
    }

    private void clearOwnedOverride(InputHandler input, AbstractPlayableSprite player) {
        if (!logicalOverrideApplied) {
            return;
        }
        if (input != null) {
            input.clearLogicalOverride();
            input.refreshLogicalSnapshot();
        }
        if (player != null) {
            player.setForcedJumpPress(false);
        }
        logicalOverrideApplied = false;
    }
}
