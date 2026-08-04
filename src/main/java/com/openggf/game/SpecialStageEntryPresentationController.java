package com.openggf.game;

import com.openggf.graphics.FadeManager;
import com.openggf.game.resources.NativeFadeLifecycle;

import java.util.Objects;

/**
 * Owns the special stage's own entry presentation: the white-out that precedes
 * the stage, the opaque hold while it loads, and the reveal that follows.
 * <p>
 * ROM: {@code GM_Special} runs {@code PaletteWhiteOut} (sonic.asm:3227), then
 * the instant setup block, then {@code PaletteWhiteIn} (sonic.asm:3296) before
 * {@code SS_MainLoop} ticks a single object; S2's {@code SpecialStage} is built
 * the same way around {@code Pal_FadeToWhite} (s2.asm:6546). None of that
 * belongs to the level the player came from — the level-side owner only writes
 * the game mode.
 */
public final class SpecialStageEntryPresentationController {
    private boolean pending;
    private boolean revealFromBlack;
    private boolean obscuring;

    public void begin(SpecialStageProvider provider, boolean fromBlack,
                      FadeManager fade, Runnable musicStart) {
        begin(provider, fromBlack, fade, musicStart,
                com.openggf.game.resources.NoOpNativeFadeLifecycle.INSTANCE);
    }

    public void begin(SpecialStageProvider provider, boolean fromBlack,
                      FadeManager fade, Runnable musicStart,
                      NativeFadeLifecycle lifecycle) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(fade, "fade");
        Objects.requireNonNull(musicStart, "musicStart");
        clear();
        pending = true;
        revealFromBlack = fromBlack;
        if (!fromBlack
                && fade.getState() != FadeManager.FadeState.HOLD_WHITE) {
            // GM_Special's PaletteWhiteOut. A screen a native fade owner already
            // drove fully white is left alone rather than re-faded from clear.
            obscuring = true;
            fade.startFadeToWhite(null, Integer.MAX_VALUE);
        }
        if (maybeReveal(provider, fade, musicStart, lifecycle)) {
            return;
        }
        // Not ready yet: pin the screen opaque until it is. A white-out still in
        // flight already ends in an indefinite hold of its own.
        if (fromBlack) {
            fade.holdBlack();
        } else if (!obscuring) {
            fade.holdWhite();
        }
    }

    public void update(SpecialStageProvider provider, FadeManager fade, Runnable musicStart,
                       NativeFadeLifecycle lifecycle) {
        maybeReveal(provider, fade, musicStart, lifecycle);
    }

    public void update(SpecialStageProvider provider, FadeManager fade, Runnable musicStart) {
        update(provider, fade, musicStart,
                com.openggf.game.resources.NoOpNativeFadeLifecycle.INSTANCE);
    }

    public void clear() {
        pending = false;
        revealFromBlack = false;
        obscuring = false;
    }

    public boolean isPending() {
        return pending;
    }

    /** @return true when this call performed the reveal. */
    private boolean maybeReveal(SpecialStageProvider provider, FadeManager fade,
                                Runnable musicStart, NativeFadeLifecycle lifecycle) {
        if (!pending) {
            return false;
        }
        if (obscuring) {
            if (fade.getState() != FadeManager.FadeState.HOLD_WHITE) {
                return false;
            }
            obscuring = false;
        }
        if (!provider.isEntryPresentationReady()) {
            return false;
        }
        reveal(revealFromBlack, fade, musicStart, lifecycle);
        return true;
    }

    private void reveal(boolean fromBlack, FadeManager fade, Runnable musicStart,
                        NativeFadeLifecycle lifecycle) {
        pending = false;
        musicStart.run();
        Runnable completion = lifecycle.beginNativeBlockingFade()
                .wrapCompletion(() -> { });
        if (fromBlack) {
            fade.startFadeFromBlack(completion);
        } else {
            fade.startFadeFromWhite(completion);
        }
    }
}
