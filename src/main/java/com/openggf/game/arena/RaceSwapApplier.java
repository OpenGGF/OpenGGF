package com.openggf.game.arena;

import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.mods.run.RaceEvent;
import com.openggf.mods.run.RaceEventResult;
import com.openggf.mods.run.RaceSignal;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Native game service for externally commanded race swaps. No transport owns gameplay writes. */
public interface RaceSwapApplier {
    /** Native break path: true means the network request replaced the immediate solo effect. */
    boolean request(ObjectServices services, AbstractPlayableSprite requester);
    RaceSignal consumeSignal();
    RaceEventResult apply(RaceEvent event, GameplayModeContext context);
    default void reset() { }
    /** Upper bound from the loaded ROM layout, used by hosted request admission. */
    default int maxSwapSources(GameplayModeContext context) { return 0; }
    /** The production level VBlank, never host wall time or a trace comparison row. */
    default void afterVBlank(GameplayModeContext context) { executeQueued(context); }
    boolean hasQueuedEvent();
    void queueForStep(RaceEvent event);
    void executeQueued(GameplayModeContext context);
    RaceEventResult consumeApplied();
    default boolean freezesObjects() { return false; }
    default boolean whitensPalette() { return false; }
    /** Read-only display scope; the native palette bytes retain their owners. */
    default void draw(GameplayModeContext context, com.openggf.graphics.GraphicsManager graphics, Runnable draw) { draw.run(); }
}
