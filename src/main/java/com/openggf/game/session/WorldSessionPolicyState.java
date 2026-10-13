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
    default void bindGameplay(GameplayModeContext context) { }
    default void beforeSpecialStageForwardTick() { }
    default void observeTick(com.openggf.LevelFrameContext context, com.openggf.control.LogicalInputSnapshot input) { }
    default void beforeNativeTick(com.openggf.LevelFrameContext context, com.openggf.control.LogicalInputSnapshot input) { }
    default void afterNativeTick(com.openggf.LevelFrameContext context, com.openggf.control.LogicalInputSnapshot input) { }
    default void onPresentation(GameplayModeContext context, com.openggf.control.LogicalInputSnapshot input,
                                com.openggf.game.GameplayObservation.Phase phase) { }
    default void onMenuPresentation(com.openggf.control.LogicalInputSnapshot input,int width,int height,
                                    java.util.function.Consumer<com.openggf.audio.GameSound> sounds) { }
    default void leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase phase) { }
    default void drawOverlay(com.openggf.mods.ui.PixelCanvas canvas) { }
    default boolean holdsGameplay() { return false; }
    void failedAssembly(LevelLoadCause cause);
    void closeScreens();
    void retire();
}
