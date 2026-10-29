package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Captured-slot cleanup and exactly-once fade handoff when an ending returns to title. */
final class EndingReturnTransition {
    private EndingReturnTransition() { }

    /**
     * Captures all arguments before cleanup can rebind the host's slots, matching the
     * original call boundary. The shared black-screen/title handoff reads live slots later.
     */
    static void start(SpriteManager spriteManager, LevelManager levelManager, String mainCode,
            AudioManager audioManager, FadeManager fadeManager, GameplayModeContext context, Runnable onBlack) {
        // The native caller departs once (S1 TryAg_Exit, docs/s1disasm/sonic.asm:4034-4036).
        // Keep an already-requested departure's callback in charge until its
        // wrapper closes the outgoing fade lease before entering the title.
        if (fadeManager.hasPendingCompletion()) {
            return;
        }
        spriteManager.setInputSuppressed(false);
        levelManager.setForceHudSuppressed(false);
        if (spriteManager.getSprite(mainCode) instanceof AbstractPlayableSprite player) {
            player.setControlLocked(false);
            player.clearForcedInputMask();
        }
        audioManager.fadeOutMusic();
        if (!fadeManager.isActive()) {
            GameLoopPlcLifecycle.startToBlack(context, fadeManager, onBlack);
        } else {
            onBlack.run();
        }
    }
}
