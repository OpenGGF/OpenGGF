package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.objects.AbstractS3kUprightEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;

/**
 * Fixed-position MHZ2 post-boss egg capsule.
 *
 * <p>ROM anchor: {@code Obj_MHZEndBoss loc_761E8} allocates
 * {@code Obj_EggCapsule} at {@code x_pos=$4640, y_pos=$0320}. The spawned
 * capsule uses the standard upright route, with the top button and body solid
 * behavior supplied by {@link AbstractS3kUprightEggCapsuleInstance}.
 */
public final class MhzEndBossEggCapsuleInstance extends AbstractS3kUprightEggCapsuleInstance
        implements SpawnCoordinateRewindRecreatable {
    public MhzEndBossEggCapsuleInstance(int x, int y) {
        super(x, y, "MHZEggCapsule");
    }

    private MhzEndBossEggCapsuleInstance() {
        this(0, 0);
    }

    @Override
    protected S3kResultsScreenObjectInstance createResultsScreen(
            PlayerCharacter character, int act) {
        return new MhzResults(character, act);
    }

    /** loc_2DCF8 publishes completion; loc_76270 retains camera/control ownership. */
    private static final class MhzResults
            extends S3kResultsScreenObjectInstance {
        private MhzResults(PlayerCharacter character, int act) { super(character, act); }
        private MhzResults() { super(true); }
        @Override protected boolean shouldRestoreCameraBoundsOnExit(int zone, int act) { return false; }
        @Override protected boolean shouldRestorePlayerControlsOnExit() { return false; }
        @Override protected void applyCameraFollowExitState(Camera camera, boolean retained) {
            // Generic level bounds are the pre-boss bounds, not the expanded
            // MHZ capsule/ship arena. Only the retained boss may release them.
        }
        @Override public MhzResults recreateForRewind(RewindRecreateContext context) {
            return ObjectConstructionContext.construct(
                    context.objectServices(), MhzResults::new);
        }
    }

}
