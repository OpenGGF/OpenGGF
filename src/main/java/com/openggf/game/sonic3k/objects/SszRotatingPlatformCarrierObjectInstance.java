package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM {@code loc_45F10}-{@code loc_460A6} (sonic3k.asm:91848-92022): the invisible wide bar
 * {@code Obj_SSZRotatingPlatform} allocates under itself. It never draws — it has no
 * {@code Draw_Sprite} of its own beyond the parent's — and exists to swing a player in a circle
 * around the post while keeping them solid.
 *
 * <p>Init: the child starts {@code $30} pixels below the post and takes
 * {@code width_pixels $60}, or {@code $A0} when bit 0 of the inherited subtype is set. That is the
 * only thing any part of {@code $76} reads its subtype for. Its solid call is
 * {@code SolidObjectTop} with {@code d1 = $B + width_pixels} and {@code d2 = d3 = $11}, so the
 * standing surface is eleven pixels wider on each side than the bar's nominal half-width.
 *
 * <p>{@code loc_45F74} keeps six bytes per player: a state byte, a "was riding" latch, a pose
 * angle, and a radius word. A player who steps on gets {@code $1FF} — armed, latched — plus the
 * signed distance from the post as the initial radius and {@code -$80} as the initial angle when
 * they came from the left. From the following frame {@code loc_46042} advances the angle by two,
 * grows the radius one pixel per frame to a maximum of {@code $14}, and writes
 * {@code x_pos(a1) = x_pos(a0) + (cos(angle) * radius) >> 8}.
 *
 * <p>The priority the player is drawn at follows the angle's sign bit — {@code $180} behind the
 * bar on the far half of the circle, {@code $100} in front on the near half — and drops a further
 * step ({@code $200} or {@code $80}) when the <em>other</em> player is riding with a smaller
 * radius, which is what keeps two riders from z-fighting.
 *
 * <p>{@code loc_45FAC} restores a released player's priority to {@code $100}, but only once they
 * are more than {@code $64} above the bar or outside its doubled span; until then the latch stays
 * set and the player keeps the priority the swing gave them.
 */
public final class SszRotatingPlatformCarrierObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable {
    /** {@code addi.w #$30,y_pos(a0)}. */
    public static final int Y_OFFSET = 0x30;
    /** {@code moveq #$60,d0} and {@code move.w #$A0,d0} for subtype bit 0. */
    public static final int NARROW_WIDTH = 0x60;
    public static final int WIDE_WIDTH = 0xA0;
    /** {@code moveq #$B,d1} / {@code add.b width_pixels(a0),d1}. */
    private static final int SOLID_WIDTH_BIAS = 0x0B;
    /** {@code moveq #$11,d2} / {@code moveq #$11,d3}. */
    private static final int SOLID_HALF_HEIGHT = 0x11;
    /** {@code cmpi.w #$14,4(a3)}. */
    public static final int MAX_RADIUS = 0x14;
    /** {@code addq.b #2,2(a3)}. */
    static final int ANGLE_STEP = 2;
    /** {@code moveq #-$80,d1}: a player who arrived from the left starts half a turn round. */
    private static final int LEFT_START_ANGLE = 0x80;
    /** {@code subi.w #$64,d0}. */
    private static final int RELEASE_HEIGHT = 0x64;
    /** The four priority words {@code loc_46052}-{@code loc_4608A} choose between. */
    private static final int PRIORITY_FAR = 0x180;
    private static final int PRIORITY_FAR_BEHIND = 0x200;
    private static final int PRIORITY_NEAR = 0x100;
    private static final int PRIORITY_NEAR_FRONT = 0x80;

    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; rewind recreation rebuilds it.")
    private final int x;
    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; rewind recreation rebuilds it.")
    private final int y;
    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; rewind recreation rebuilds it.")
    private final int widthPixels;
    /** {@code routine(a0)}: set to {@code $FF} by the post when it culls. */
    private boolean killed;

    /** {@code $2E}/{@code $34}: state byte, release latch, pose angle and radius per player. */
    private int p1State;
    private boolean p1Latched;
    private int p1Angle;
    private int p1Radius;
    private int p2State;
    private boolean p2Latched;
    private int p2Angle;
    private int p2Radius;

    public SszRotatingPlatformCarrierObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZRotatingPlatformCarrier");
        this.x = spawn.x();
        this.y = (spawn.y() + Y_OFFSET) & 0xFFFF;
        this.widthPixels = (spawn.subtype() & 1) != 0 ? WIDE_WIDTH : NARROW_WIDTH;
    }

    @Override
    public SszRotatingPlatformCarrierObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszRotatingPlatformCarrierObjectInstance(ctx.spawn());
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    /** {@code st routine(a1)} from {@code loc_45E14}. */
    void killFromPost() {
        killed = true;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        if (killed) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        checkpointAll();
        servicePlayer(0, player);
        // lea (Player_2).w,a1: the routine's second pass is the native Player 2 slot.
        servicePlayer(1, services().playerQuery().nativeP2OrNull());
    }

    /** One {@code loc_45F74} call. */
    private void servicePlayer(int slot, PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        boolean standing = isRiding(sprite);
        int state = state(slot);
        if (state == 0) {
            if (standing) {
                grab(slot, sprite);
            } else {
                releaseLatch(slot, sprite);
            }
            return;
        }
        if (state >= 0 && !standing) {
            setState(slot, 0);
            return;
        }
        setState(slot, -1);
        sprite.setXSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setSpindash(false);
        sprite.setAnimationId(Sonic3kAnimationIds.WALK);
        sprite.setRollingFlagPreserveRadii(false);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
        if (sprite.isLogicalJumpPressActive()) {
            setState(slot, 0);
            SszRotatingPlatformObjectInstance.jumpOff(sprite, services(), this);
            return;
        }
        swing(slot, sprite);
    }

    /** {@code loc_45FB8}. */
    private void grab(int slot, AbstractPlayableSprite sprite) {
        // move.w #$1FF,(a3): the state byte arms and the release latch is set in one write.
        setState(slot, 1);
        setLatched(slot, true);
        int delta = ((sprite.getCentreX() & 0xFFFF) - x) & 0xFFFF;
        int signed = (short) delta;
        if (signed < 0) {
            setRadius(slot, -signed);
            setAngle(slot, LEFT_START_ANGLE);
        } else {
            setRadius(slot, signed);
            setAngle(slot, 0);
        }
    }

    /** {@code loc_45F8C}-{@code loc_45FAC}: the released player keeps their priority until clear. */
    private void releaseLatch(int slot, AbstractPlayableSprite sprite) {
        if (!latched(slot)) {
            return;
        }
        int above = (y - RELEASE_HEIGHT) & 0xFFFF;
        boolean clearedAbove = Integer.compareUnsigned(above, sprite.getCentreY() & 0xFFFF) >= 0;
        if (!clearedAbove) {
            int span = widthPixels + SOLID_WIDTH_BIAS;
            int delta = (((sprite.getCentreX() & 0xFFFF) - x) + span) & 0xFFFF;
            if (Integer.compareUnsigned(delta, span * 2) < 0) {
                return;
            }
        }
        setLatched(slot, false);
        sprite.setPriorityBucket(RenderPriority.fromS3kWord(PRIORITY_NEAR));
    }

    /** {@code loc_46042}-{@code loc_4608A}. */
    private void swing(int slot, AbstractPlayableSprite sprite) {
        int angle = (angle(slot) + ANGLE_STEP) & 0xFF;
        setAngle(slot, angle);
        int radius = radius(slot);
        if (radius < MAX_RADIUS) {
            setRadius(slot, ++radius);
        }
        int otherRadius = radius(1 - slot);
        boolean otherRiding = state(1 - slot) != 0;
        int priority;
        if (angle >= 0x80) {
            priority = otherRiding && otherRadius < radius ? PRIORITY_FAR_BEHIND : PRIORITY_FAR;
        } else {
            priority = otherRiding && otherRadius < radius ? PRIORITY_NEAR_FRONT : PRIORITY_NEAR;
        }
        sprite.setPriorityBucket(RenderPriority.fromS3kWord(priority));
        // muls.w 4(a3),d1 / asr.l #8,d1 — d1 is GetSineCosine's cosine, not its sine.
        int offset = (TrigLookupTable.cosHex(angle) * radius) >> 8;
        NativePositionOps.writeXPosPreserveSubpixel(sprite, (x + offset) & 0xFFFF);
        try {
            SszCarriedPlayerPose.apply(services().rom(), sprite, angle);
        } catch (java.io.IOException ignored) {
            // A read failure here leaves the last pose drawn; the swing itself is unaffected.
        }
    }

    private boolean isRiding(AbstractPlayableSprite sprite) {
        var objectManager = services().objectManager();
        return objectManager != null && objectManager.isRidingObject(sprite, this);
    }

    private int state(int slot) { return slot == 0 ? p1State : p2State; }

    private void setState(int slot, int value) {
        if (slot == 0) {
            p1State = value;
        } else {
            p2State = value;
        }
    }

    private boolean latched(int slot) { return slot == 0 ? p1Latched : p2Latched; }

    private void setLatched(int slot, boolean value) {
        if (slot == 0) {
            p1Latched = value;
        } else {
            p2Latched = value;
        }
    }

    private int angle(int slot) { return slot == 0 ? p1Angle : p2Angle; }

    private void setAngle(int slot, int value) {
        if (slot == 0) {
            p1Angle = value;
        } else {
            p2Angle = value;
        }
    }

    private int radius(int slot) { return slot == 0 ? p1Radius : p2Radius; }

    private void setRadius(int slot, int value) {
        if (slot == 0) {
            p1Radius = value;
        } else {
            p2Radius = value;
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(widthPixels + SOLID_WIDTH_BIAS,
                SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override public boolean isTopSolidOnly() { return true; }
    @Override public int getOnScreenHalfWidth() { return widthPixels; }
    @Override public int getOnScreenHalfHeight() { return SOLID_HALF_HEIGHT; }

    public int widthPixelsForTest() { return widthPixels; }
    public int stateForTest(int slot) { return state(slot); }
    public int radiusForTest(int slot) { return radius(slot); }
    public int angleForTest(int slot) { return angle(slot); }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // loc_45F2E never calls Draw_Sprite: the carrier is solid geometry only.
    }
}
