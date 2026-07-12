package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * ROM {@code Obj_FBZOutdoorBGMotion} ($52D78).
 *
 * <p>This is an ordinary dynamically allocated, non-rendering SST object. It
 * deliberately never performs an out-of-range/delete check, so it survives the
 * Act 1 seamless load just like the ROM slot. ExecuteObjects runs it before the
 * later screen/background deformation phase, allowing that phase to consume the
 * newly published {@code Events_bg+$08} value in the same frame.
 */
public final class FbzOutdoorBgMotionObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int INITIAL_VELOCITY = 0x00002800;
    private static final int ACCELERATION = 0x00000080;

    private int swingVelocity;
    private int swingPosition;
    private boolean returning;

    public FbzOutdoorBgMotionObjectInstance() {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "FBZOutdoorBGMotion");
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        int offset = advanceMotion();
        S3kRuntimeStates.currentFbz(services().zoneRuntimeRegistry())
                .ifPresent(state -> state.setOutdoorBobOffset(offset));
    }

    /** One exact {@code Gradual_SwingOffset($2800,$80)} update. */
    int advanceMotion() {
        int oldVelocity = swingVelocity;
        if (returning) {
            swingPosition += oldVelocity;
            if (swingPosition < 0) {
                swingVelocity += ACCELERATION;
            } else {
                swingVelocity = INITIAL_VELOCITY;
                swingPosition = 0;
                returning = false;
            }
        } else {
            swingPosition += oldVelocity;
            if (swingPosition <= 0) {
                swingVelocity = -INITIAL_VELOCITY;
                swingPosition = 0;
                returning = true;
            } else {
                swingVelocity -= ACCELERATION;
            }
        }
        return (short) (swingPosition >> 16);
    }

    int getSwingVelocity() { return swingVelocity; }
    int getSwingPosition() { return swingPosition; }
    boolean isReturning() { return returning; }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzOutdoorBgMotionObjectInstance();
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
