package com.openggf.game.internal;

import com.openggf.game.GameModule;

/** Internal module lineage supplied only by a verified engine-owned projection handler. */
public interface InheritedGameModuleProvider {
    GameModule inheritedModule();
}
