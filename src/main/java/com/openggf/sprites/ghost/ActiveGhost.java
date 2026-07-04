package com.openggf.sprites.ghost;

import com.openggf.game.ghost.GhostFrame;

/** One ghost to draw this frame: stable slot id, character art code, resolved frame. */
public record ActiveGhost(String slotId, String characterCode, GhostFrame frame) {
}
