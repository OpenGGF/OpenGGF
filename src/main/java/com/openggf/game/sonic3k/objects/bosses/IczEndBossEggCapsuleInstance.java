package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.AbstractS3kUprightEggCapsuleInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

/**
 * Fixed-position ICZ2 post-boss egg capsule spawned by {@link IczEndBossInstance}.
 *
 * <p>ROM anchor: {@code Obj_EggCapsule} spawned by {@code Obj_ICZEndBoss} at
 * {@code x_pos=$4560, y_pos=$06A3}. ICZ uses the shared upright capsule route:
 * body solid {@code d1=$2B,d2=$18,d3=$18} plus top-button child
 * {@code d1=$1B,d2=4,d3=6}.
 */
public final class IczEndBossEggCapsuleInstance extends AbstractS3kUprightEggCapsuleInstance
        implements RewindRecreatable {
    public IczEndBossEggCapsuleInstance(int x, int y) {
        super(x, y, "ICZEggCapsule");
    }

    private IczEndBossEggCapsuleInstance() {
        this(0, 0);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new IczEndBossEggCapsuleInstance(ctx.spawn().x(), ctx.spawn().y());
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!isResultsStarted() && services().camera() != null) {
            services().camera().setMinX(services().camera().getX());
        }
        super.update(frameCounter, player);
    }
}
