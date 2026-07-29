package com.openggf.level;

import com.openggf.level.resources.DeferredLevelResourceManifest;

/**
 * Session-owned resources carried through one seamless level reload.
 */
@com.openggf.game.ModApi
public interface SeamlessTransitionResourceHandoff {
    DeferredLevelResourceManifest deferredResources();

    void transferAfterTargetInit();
}
