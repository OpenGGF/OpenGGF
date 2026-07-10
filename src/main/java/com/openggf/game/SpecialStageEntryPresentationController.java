package com.openggf.game;

import com.openggf.graphics.FadeManager;

import java.util.Objects;

/** Coordinates an opaque special-stage entry hold with its reveal and music. */
public final class SpecialStageEntryPresentationController {
    private boolean pending;
    private boolean revealFromBlack;

    public void begin(SpecialStageProvider provider, boolean fromBlack,
                      FadeManager fade, Runnable musicStart) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(fade, "fade");
        Objects.requireNonNull(musicStart, "musicStart");
        clear();
        if (provider.isEntryPresentationReady()) {
            reveal(fromBlack, fade, musicStart);
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

    public void update(SpecialStageProvider provider, FadeManager fade, Runnable musicStart) {
        if (pending && provider.isEntryPresentationReady()) {
            reveal(revealFromBlack, fade, musicStart);
        }
    }

    public void clear() {
        pending = false;
        revealFromBlack = false;
    }

    public boolean isPending() {
        return pending;
    }

    private void reveal(boolean fromBlack, FadeManager fade, Runnable musicStart) {
        pending = false;
        musicStart.run();
        if (fromBlack) {
            fade.startFadeFromBlack(null);
        } else {
            fade.startFadeFromWhite(null);
        }
    }
}
