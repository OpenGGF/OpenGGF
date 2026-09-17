package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.SidekickLevelEventRelease;

import java.util.List;

/**
 * ROM {@code Obj_57DCC} (sonic3k.asm:116889-116918): the Player 2 half of the Sky Sanctuary
 * arrival, allocated by {@code loc_57D18} when the act is 1 and {@code Player_mode == 0}.
 *
 * <p>{@code subtype} counts down {@code $C} frames. On the frame it reaches zero the helper puts
 * Player 2 on the arrival column, sets {@code $2E(a1) = 1}, the roll animation, the roll status
 * bit and {@code y_vel = -1}; it then holds her on the same
 * {@code Gradual_SwingOffset($20000,$800)} arc the leader uses, based at {@code $3E} — the
 * leader's release Y. When the swing speed turns non-negative it clears {@code object_control}
 * and {@code anim}, zeroes {@code Tails_CPU_flight_timer}, writes {@code Tails_CPU_routine = 6}
 * and deletes itself.
 *
 * <p>Player 2 reaches this object parked at {@code ($7F00,0)} with {@code object_control = $83}:
 * {@code loc_13AB4} sends {@code $A00} through {@code sub_13ECA} and then routine {@code $A}, the
 * same branch AIZ1's intro uses (engine {@code SidekickCpuController.State.DORMANT_MARKER}).
 */
public final class SszTailsArrivalHelperObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int SWING_SPEED = 0x20000;
    private static final int SWING_ACCELERATION = 0x800;

    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; recreateForRewind rebuilds it.")
    private final int x;
    /** {@code $3E(a0)}: the leader's release Y, passed in as the spawn Y. */
    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; recreateForRewind rebuilds it.")
    private final int baseY;
    /** {@code subtype(a0)}: the {@code $C} frame delay. */
    private int delay;
    private boolean swinging;
    private final S3kGradualSwing swing = new S3kGradualSwing();

    private record RewindExtra(int delay, boolean swinging, S3kGradualSwing.Value swing)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszTailsArrivalHelperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SszTailsArrivalHelper");
        this.x = spawn.x();
        this.baseY = spawn.y();
        this.delay = spawn.subtype();
    }

    @Override
    public SszTailsArrivalHelperObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszTailsArrivalHelperObjectInstance(ctx.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                delay, swinging, swing.captureRewindStateValue()));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            delay = extra.delay();
            swinging = extra.swinging();
            swing.restoreRewindStateValue(extra.swing());
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sidekick = sidekick();
        if (sidekick == null) {
            return;
        }
        if (!swinging) {
            // tst.w subtype(a0) / beq.s loc_57DFA: the counter is tested before it is decremented,
            // so a zero subtype would swing on the first pass.
            if (delay != 0) {
                delay--;
                if (delay != 0) {
                    return;
                }
                NativePositionOps.writeXPosPreserveSubpixel(sidekick, x);
                sidekick.setAnimationId(Sonic3kAnimationIds.ROLL);
                sidekick.setRollingFlagPreserveRadii(true);
                sidekick.setYSpeed((short) -1);
            }
            swinging = true;
        }
        int offset = swing.step(SWING_SPEED, SWING_ACCELERATION);
        NativePositionOps.writeYPosPreserveSubpixel(sidekick, (baseY + offset) & 0xFFFF);
        if (swing.rising()) {
            return;
        }
        ObjectControlState.none().applyTo(sidekick);
        sidekick.setObjectMappingFrameControl(false);
        sidekick.setAnimationId(0);
        // clr.w (Tails_CPU_flight_timer).w / move.w #6,(Tails_CPU_routine).w
        SidekickLevelEventRelease.toNormalFollow(sidekick);
        com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
    }

    private AbstractPlayableSprite sidekick() {
        // Player_2 as the ROM sees it: an empty slot is absent, not a configured roster entry.
        return services().playerQuery().nativeP2OrNull()
                instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    int delayForTest() { return delay; }
    boolean swingingForTest() { return swinging; }

    @Override public int getX() { return x; }
    @Override public int getY() { return baseY; }
    @Override public int getOutOfRangeReferenceX() { return x; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No mappings: the helper only scripts Player 2.
    }
}
