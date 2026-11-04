package com.openggf.game.internal;

/**
 * Title-card capability: which locked object passes also run the level's
 * scroll and dynamic-event routine.
 *
 * <p>A title card that executes level objects while it is locked may still
 * omit the routine the main loop runs after them. Sonic 2's leave loop only
 * runs objects, sprites and the pattern load queue
 * (docs/s2disasm/s2.asm:5060-5066); its {@code DeformBgLayer} -- camera
 * scroll, {@code RunDynamicLevelEvents} and everything those dispatch -- is
 * reached once, from the title card's own {@code Obj34_BackgroundOutInit}
 * (s2.asm:27587-27596). Providers without this capability run the full level
 * step on every locked object pass.
 */
public interface LockedTitleCardLevelStepGate {
    /** Whether the locked pass now running reaches the scroll/event routine. */
    boolean runsLevelScrollAndEventsThisLockedPass();
}
