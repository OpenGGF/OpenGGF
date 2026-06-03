package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.sonic3k.objects.AbstractS3kUprightEggCapsuleInstance;

/**
 * Fixed-position MHZ2 post-boss egg capsule.
 *
 * <p>ROM anchor: {@code Obj_MHZEndBoss loc_761E8} allocates
 * {@code Obj_EggCapsule} at {@code x_pos=$4640, y_pos=$0320}. The spawned
 * capsule uses the standard upright route, with the top button and body solid
 * behavior supplied by {@link AbstractS3kUprightEggCapsuleInstance}.
 */
public final class MhzEndBossEggCapsuleInstance extends AbstractS3kUprightEggCapsuleInstance {
    public MhzEndBossEggCapsuleInstance(int x, int y) {
        super(x, y, "MHZEggCapsule");
    }
}
