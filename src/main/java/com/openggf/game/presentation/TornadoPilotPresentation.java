package com.openggf.game.presentation;

import com.openggf.game.EndingSpriteSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Instance-owned cockpit overlay; native pilot clocks and DPLC decisions stay host-owned. */
public final class TornadoPilotPresentation {
    private boolean loaded;
    private EndingSpriteSet set;
    private PlayerSpriteRenderer renderer;

    public boolean draw(int frame, int x, int y) {
        try {
            if (!loaded) {
                set = EndingCharacterPresentation.sidekick(EndingSpriteSet.Surface.S2_TORNADO_PILOT);
                if (set != null) renderer = EndingCharacterPresentation.renderer(set);
                loaded = true;
            }
            if (set == null || !set.sprites().frames().containsKey(frame)) return false;
            renderer.drawFrame(frame, x, y, false, false);
            return true;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
