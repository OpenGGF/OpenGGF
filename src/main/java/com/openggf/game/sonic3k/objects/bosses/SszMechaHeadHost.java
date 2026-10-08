package com.openggf.game.sonic3k.objects.bosses;

/**
 * What {@code Obj_MechaSonicHeadMain} (sonic3k.asm:136265-136272) reads off the ship it rides.
 *
 * <p>Both Sky Sanctuary recreations attach the same head with {@code Child1_MakeMechaHead}, and
 * the head's whole body is {@code Refresh_ChildPositionAdjusted} on {@code parent3(a0)} plus
 * {@code tst.b (_unkFA89).w}. The two ships write that flag from different routines —
 * {@code loc_7A3F8} for Green Hill, {@code loc_7ACA4} for Metropolis — so the head reads it
 * through the parent rather than through a shared word.
 */
public interface SszMechaHeadHost extends com.openggf.level.objects.ObjectInstance {

    /** {@code tst.b (_unkFA89).w}: the ship's own escape sets it, and the head then deletes. */
    boolean headShouldDelete();

    /** {@code render_flags} bit 0, which {@code Refresh_ChildPositionAdjusted} negates {@code child_dx} on. */
    boolean isRenderFlippedForTest();

}
