package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * How an object's wall probe reads a block row's width when the block is flipped against the
 * probe's direction. A host's {@code FindLevelWall} that negates the signed width
 * ({@code neg.w d0}) and then takes its zero, negative and full-width branches registers
 * {@link #NEGATE} (scddisasm src/Level/Collision Floor.asm:874-1030). Without a registration the
 * object probes keep the complement model they have been certified on.
 */
public enum ObjectWallProbeWidth {
    /** A flipped width w reads as {@code 16 - w}; the probe has no negative-width branch. */
    COMPLEMENT,
    /**
     * A flipped width is negated; a negative width is solid from the probe's far edge, so a probe
     * outside it reads the next block and a probe inside it regresses to the previous block.
     */
    NEGATE;

    public static ObjectWallProbeWidth forHost(GameModule host) {
        ObjectWallProbeWidth model = host == null ? null : host.getGameService(ObjectWallProbeWidth.class);
        return model == null ? COMPLEMENT : model;
    }
}
