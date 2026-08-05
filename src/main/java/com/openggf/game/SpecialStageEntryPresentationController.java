package com.openggf.game;

import com.openggf.graphics.FadeManager;
import com.openggf.game.resources.NativeFadeLifecycle;

import java.util.Objects;

/** Coordinates an opaque special-stage entry hold with its reveal and music. */
public final class SpecialStageEntryPresentationController {
    private boolean pending;
    private boolean revealFromBlack;

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
        if (provider.isEntryPresentationReady()) {
            reveal(fromBlack, fade, musicStart, lifecycle);
            return;
        }
        pending = true;
        revealFromBlack = fromBlack;
        if (fromBlack) {
            fade.holdBlack();
        } else {
            fade.holdWhite();
        }
    }

    public void update(SpecialStageProvider provider, FadeManager fade, Runnable musicStart,
                       NativeFadeLifecycle lifecycle) {
        if (pending && provider.isEntryPresentationReady()) {
            reveal(revealFromBlack, fade, musicStart, lifecycle);
        }
    }

    public void update(SpecialStageProvider provider, FadeManager fade, Runnable musicStart) {
        update(provider, fade, musicStart,
                com.openggf.game.resources.NoOpNativeFadeLifecycle.INSTANCE);
    }

    public void clear() {
        pending = false;
        revealFromBlack = false;
    }

    public boolean isPending() {
        return pending;
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
