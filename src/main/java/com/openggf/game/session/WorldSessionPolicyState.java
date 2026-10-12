package com.openggf.game.session;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.sprites.managers.SpriteManager;

/** Engine-facing lifecycle of world-owned policies; provider-specific values stay behind typed services. */
public interface WorldSessionPolicyState {
    <T> T getService(Class<T> type);
    RewindSnapshottable<?> rewindAdapter();
    void beforeAssembly(LevelLoadCause cause);
    void bindRoster(SpriteManager sprites);
    default void beforeSpecialStageForwardTick() { }
    void failedAssembly(LevelLoadCause cause);
    void closeScreens();
    void retire();
}
