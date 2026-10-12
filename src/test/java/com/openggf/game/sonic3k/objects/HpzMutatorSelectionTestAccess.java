package com.openggf.game.sonic3k.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Shares the native package seam with cross-game entry admission regressions. */
public final class HpzMutatorSelectionTestAccess {
    private HpzMutatorSelectionTestAccess() { }

    public static boolean beginSelection(HPZSuperEmeraldObjectInstance pedestal, AbstractPlayableSprite player) {
        return pedestal.beginSelection(player);
    }
}
