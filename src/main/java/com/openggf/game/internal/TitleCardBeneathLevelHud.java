package com.openggf.game.internal;

import com.openggf.game.TitleCardProvider;

/**
 * A title card whose pieces the game's sprite renderer places beneath the
 * level HUD while the level runs, so the host paints the card inside the
 * level's HUD pass instead of over the finished frame.
 */
public interface TitleCardBeneathLevelHud {
    /** Whether this frame's card pieces sit beneath the level HUD. */
    boolean drawsBeneathLevelHud();

    static boolean appliesTo(TitleCardProvider card) {
        return card instanceof TitleCardBeneathLevelHud beneath && beneath.drawsBeneathLevelHud();
    }
}
