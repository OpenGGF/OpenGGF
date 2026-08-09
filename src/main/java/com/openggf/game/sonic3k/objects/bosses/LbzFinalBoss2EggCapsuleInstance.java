package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.AbstractS3kFloatingEndEggCapsuleInstance;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;

/**
 * LBZ2 route-8 floating capsule used by Big Arm's post-defeat sequence.
 *
 * <p>ROM {@code loc_7473A} waits on two independent signals. This capsule is
 * the native owner of {@code _unkFAA2}: after every opened-capsule
 * {@code Swing_UpAndDown}, it moves left while its unsigned X is above
 * {@code Camera_X_pos-$60}; at the threshold it latches the dynamic-water
 * lock and skips that entry's {@code MoveSprite2} step.</p>
 */
public final class LbzFinalBoss2EggCapsuleInstance
        extends AbstractS3kFloatingEndEggCapsuleInstance
        implements SpawnCoordinateRewindRecreatable {

    private static final int LOCK_THRESHOLD_OFFSET = -0x60;

    public LbzFinalBoss2EggCapsuleInstance(int initialX, int initialY) {
        super(initialX, initialY, "LBZFinalBoss2EggCapsule");
    }

    private LbzFinalBoss2EggCapsuleInstance(int initialX, int initialY, boolean routeInitPending) {
        super(initialX, initialY, "LBZFinalBoss2EggCapsule", routeInitPending);
    }

    private LbzFinalBoss2EggCapsuleInstance() {
        this(0, 0);
    }

    public static LbzFinalBoss2EggCapsuleInstance createForCamera(int cameraX, int cameraY) {
        return new LbzFinalBoss2EggCapsuleInstance(
                cameraX + X_OFFSET, cameraY + Y_START_OFFSET, true);
    }

    @Override
    protected boolean applyPostOpenRouteMovement() {
        int threshold = (services().camera().getX() + LOCK_THRESHOLD_OFFSET) & 0xFFFF;
        if (Integer.compareUnsigned(capsuleX(), threshold) > 0) {
            setCapsuleX(capsuleX() - 2);
            return true;
        }

        WaterSystem water = services().waterSystem();
        if (water != null) {
            water.setDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1, true);
        }
        return false;
    }
}
