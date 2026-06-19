package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.sonic3k.objects.AbstractS3kUprightEggCapsuleInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

/**
 * Fixed-position MHZ2 post-boss egg capsule.
 *
 * <p>ROM anchor: {@code Obj_MHZEndBoss loc_761E8} allocates
 * {@code Obj_EggCapsule} at {@code x_pos=$4640, y_pos=$0320}. The spawned
 * capsule uses the standard upright route, with the top button and body solid
 * behavior supplied by {@link AbstractS3kUprightEggCapsuleInstance}.
 */
public final class MhzEndBossEggCapsuleInstance extends AbstractS3kUprightEggCapsuleInstance
        implements RewindRecreatable {
    public MhzEndBossEggCapsuleInstance(int x, int y) {
        super(x, y, "MHZEggCapsule");
    }

    private MhzEndBossEggCapsuleInstance() {
        this(0, 0);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new MhzEndBossEggCapsuleInstance(ctx.spawn().x(), ctx.spawn().y());
    }
}
