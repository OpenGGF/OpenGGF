package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * Game service marker: the game's native recordings observe V-INT entry and may carry the hardware-timing
 * contract's lost-V-INT admission (2026-11-13 extension): a VBlank that arrived while the previous V-INT handler
 * was still running produced no interrupt entry, so replay services no V-INT on that row. Only trace replay reads
 * it; production never models a handler overrun. Without it a trace carrying the stream fails loading.
 */
public interface UnservicedVBlankAdmission {
    /** The one shared instance a declaring game returns. */
    UnservicedVBlankAdmission DECLARED = new UnservicedVBlankAdmission() { };

    static boolean forHost(GameModule host) {
        return host != null && host.getGameService(UnservicedVBlankAdmission.class) != null;
    }
}
