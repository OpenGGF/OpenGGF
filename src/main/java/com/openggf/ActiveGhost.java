package com.openggf;

import com.openggf.ghost.GhostFrame;
import com.openggf.net.identity.PlayerAppearance;

/**
 * One ghost to draw this frame: art, resolved frame, and optional network presentation.
 * Lives in the engine shell beside its producer ({@code HostedRunController}) and consumer
 * ({@link GhostRenderer}), so the sprites layer never depends on the network look type.
 */
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
