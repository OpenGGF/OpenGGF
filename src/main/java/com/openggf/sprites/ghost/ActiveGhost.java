package com.openggf.sprites.ghost;

import com.openggf.ghost.GhostFrame;
import com.openggf.net.identity.PlayerAppearance;

/** One ghost to draw this frame: art, resolved frame, and optional network presentation. */
public record ActiveGhost(String slotId, String characterCode, GhostFrame frame,
                          String nameplate, float opacityScale, PlayerAppearance appearance) {
    public ActiveGhost {
        appearance = appearance == null ? PlayerAppearance.STOCK : appearance;
    }

    public ActiveGhost(String slotId, String characterCode, GhostFrame frame,
                       String nameplate, float opacityScale) {
        this(slotId, characterCode, frame, nameplate, opacityScale, PlayerAppearance.STOCK);
    }

    public ActiveGhost(String slotId, String characterCode, GhostFrame frame) {
        this(slotId, characterCode, frame, null, 1f, PlayerAppearance.STOCK);
    }
}
