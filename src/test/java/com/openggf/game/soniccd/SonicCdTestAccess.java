package com.openggf.game.soniccd;

import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;

/** Test-only access across the tools test package; no production service or accessor is exposed. */
public final class SonicCdTestAccess {
    private SonicCdTestAccess() { }

    public static CdPlayerFrameRuntime frame(SonicCdGameModule module) {
        return module.requirePlayerFrame();
    }
}
