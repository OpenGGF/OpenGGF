package com.openggf.level.objects;

/**
 * Marker for player-bound shield visuals recreated by the post-restore player
 * refresh rather than by directly constructing an object during the dynamic
 * object restore pass.
 *
 * <p>The refresh completes captured admission and restores scalar state later.
 * Until then the effect is absent from the ordinary graph's reference table;
 * it cannot be a required field-reference target in that pass. Optional absent
 * references receive no automatic later fixup.
 */
@com.openggf.game.ModApi
public interface PlayerBoundShieldRewindRecreatable extends RewindRecreatable {

    @Override
    default AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        ctx.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class);
        return null;
    }
}
