package com.openggf.sprites.ghost;

import com.openggf.ghost.GhostFrame;

/** One ghost to draw this frame: art, resolved frame, and optional network presentation. */
@com.openggf.game.ModApi
public record ActiveGhost(String slotId, String characterCode, GhostFrame frame,
                          String nameplate, float opacityScale) {
    public ActiveGhost(String slotId, String characterCode, GhostFrame frame) {
        this(slotId, characterCode, frame, null, 1f);
    }
}
