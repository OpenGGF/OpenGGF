package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM {@code Obj_SSZElevatorBar} ({@code $7A}, sonic3k.asm:90793-90928): the horizontal bar that
 * hangs a player from a slow vertical swing and throws them on the jump press. Five act-1
 * placements, all subtype 0 — the subtype is never read.
 *
 * <p>Init: {@code render_flags 4}, {@code height_pixels 4}, {@code width_pixels $30},
 * {@code priority $180}, {@code make_art_tile(ArtTile_SSZMisc+$74,2,0)} over
 * {@code Map_SSZElevatorBar}, and {@code y_vel(a0) = y_pos(a0)} as the swing's base. Unlike the
 * cloud families there is no {@code Random_Number} draw: every bar starts its swing in phase.
 *
 * <p>{@code loc_4539E} adds a {@code Gradual_SwingOffset($18000,$480)} offset to the base Y and
 * then runs {@code sub_453E6} once per player. The hang offset {@code d1} is {@code $14} for
 * Player 1 unless {@code Player_mode == 2} (Knuckles alone), where it is {@code $11}; Player 2
 * always gets {@code $11}.
 *
 * <p>Grab window ({@code loc_45400}): {@code x_pos(a1) - x_pos(a0) + $28} below {@code $50}
 * unsigned and {@code y_pos(a1) - y_pos(a0)} below {@code $18} unsigned — so the bar catches a
 * player anywhere in a {@code $50}-wide band that starts {@code $28} to its left, but only from
 * above. A player already under object control, at routine {@code 4} or higher (hurt or dead), or
 * in debug placement is skipped. On a catch it plays {@code sfx_Grab}, zeroes {@code x_vel} and
 * {@code ground_vel}, takes {@code object_control 3}, clears the player's render flip bits and
 * shows mapping frame {@code $E5}, or {@code $E9} when the facing bit is set.
 *
 * <p>Release ({@code loc_454AA}): the launch X velocity is {@code -$200} with left held,
 * {@code +$200} with right, and zero otherwise; the Y velocity is always the bar's own
 * {@code Gradual_SwingOffset} speed longword shifted down eight, plus {@code -$400} unless down is
 * held, in which case the player simply drops with the bar's speed. Left also leaves the bar's
 * priority at {@code $80} — drawn in front of the player — while right and the neutral release
 * put it back to {@code $180}. Letting go costs a 30-frame re-grab cooldown; being hurt off the
 * bar costs 60.
 */
public final class SszElevatorBarObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    /** {@code move.w #$180,priority(a0)} and the {@code $80} the left-hand release leaves. */
    private static final int PRIORITY_BEHIND = 0x180;
    private static final int PRIORITY_IN_FRONT = 0x80;
    /** {@code make_art_tile(ArtTile_SSZMisc+$74,2,0)}. */
    private static final int PALETTE_LINE = 2;
    /** {@code move.l #$18000,d0} / {@code move.l #$480,d1}. */
    private static final int SWING_SPEED = 0x18000;
    private static final int SWING_ACCELERATION = 0x480;
    /** {@code moveq #$14,d1} and the {@code moveq #$11,d1} both other cases use. */
    public static final int HANG_OFFSET_LEADER = 0x14;
    public static final int HANG_OFFSET_OTHER = 0x11;
    /** {@code addi.w #$28,d0} / {@code cmpi.w #$50,d0}. */
    private static final int GRAB_X_BIAS = 0x28;
    private static final int GRAB_X_SPAN = 0x50;
    /** {@code cmpi.w #$18,d0}. */
    private static final int GRAB_Y_SPAN = 0x18;
    /** {@code move.w #$E5,d0} and its {@code addq.w #4,d0}. */
    private static final int HANG_FRAME_RIGHT = 0xE5;
    private static final int HANG_FRAME_LEFT = 0xE9;
    /** {@code move.w #-$200,d1} / {@code move.w #-$400,d2}. */
    public static final int RELEASE_X_VEL = 0x200;
    static final int RELEASE_Y_BOOST = -0x400;
    /** {@code move.b #30,2(a2)} and {@code move.b #60,2(a2)}. */
    public static final int RELEASE_COOLDOWN = 30;
    public static final int HURT_COOLDOWN = 60;

    private final int x;
    private final int baseY;
    private int y;
    /** {@code $2E}/{@code $32}/{@code $36}: the {@code Gradual_SwingOffset} longwords and flag. */
    private int swingSpeed;
    private int swingOffset;
    private boolean swingReversed;
    private int priorityWord = PRIORITY_BEHIND;
    /** {@code $38}/{@code $39}: whether each player is hanging. */
    private boolean p1Holding;
    private boolean p2Holding;
    /** {@code $3A}/{@code $3B}: the re-grab cooldown. */
    private int p1Cooldown;
    private int p2Cooldown;

    public SszElevatorBarObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZElevatorBar");
        this.x = spawn.x();
        this.baseY = spawn.y();
        this.y = spawn.y();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        y = (baseY + gradualSwingOffset()) & 0xFFFF;
        servicePlayer(0, player);
        // lea (Player_2).w,a1: the routine's second pass is the native Player 2 slot.
        servicePlayer(1, services().playerQuery().nativeP2OrNull());
    }

    /** One {@code sub_453E6} call. */
    private void servicePlayer(int slot, PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        int hangOffset = hangOffset(slot);
        if (holding(slot)) {
            release(slot, sprite, hangOffset);
            return;
        }
        int cooldown = cooldown(slot);
        if (cooldown != 0) {
            setCooldown(slot, cooldown - 1);
            if (cooldown - 1 != 0) {
                return;
            }
            priorityWord = PRIORITY_BEHIND;
        }
        grab(slot, sprite, hangOffset);
    }

    /**
     * {@code moveq #$14,d1} / {@code cmpi.w #2,(Player_mode).w} / {@code moveq #$11,d1}. Player
     * mode 2 is Tails alone, whose hang pose sits three pixels higher; Player 2 always uses the
     * shorter offset, so a Tails who is the sidekick gets it too.
     */
    private int hangOffset(int slot) {
        if (slot != 0) {
            return HANG_OFFSET_OTHER;
        }
        return resolvePlayerCharacter() == PlayerCharacter.TAILS_ALONE
                ? HANG_OFFSET_OTHER : HANG_OFFSET_LEADER;
    }

    private PlayerCharacter resolvePlayerCharacter() {
        if (services().configuration() == null) {
            return PlayerCharacter.SONIC_ALONE;
        }
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(), services().configuration());
    }

    /** {@code loc_45400}. */
    private void grab(int slot, AbstractPlayableSprite sprite, int hangOffset) {
        int dx = ((sprite.getCentreX() & 0xFFFF) - x + GRAB_X_BIAS) & 0xFFFF;
        if (dx >= GRAB_X_SPAN) {
            return;
        }
        int dy = ((sprite.getCentreY() & 0xFFFF) - y) & 0xFFFF;
        if (dy >= GRAB_Y_SPAN) {
            return;
        }
        if (sprite.isObjectControlled() || sprite.isHurt() || sprite.getDead()
                || sprite.isDebugMode()) {
            return;
        }
        services().playSfx(Sonic3kSfx.GRAB.id);
        setHolding(slot, true);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, (y + hangOffset) & 0xFFFF);
        sprite.setXSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setSpindash(false);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
        sprite.setAnimationId(Sonic3kAnimationIds.WALK);
        // object_control 3 keeps Animate_Sonic off, so the hang frame stays put.
        sprite.setObjectMappingFrameControl(true);
        sprite.setMappingFrame(sprite.getDirection() == com.openggf.physics.Direction.LEFT
                ? HANG_FRAME_LEFT : HANG_FRAME_RIGHT);
    }

    /** {@code loc_4548A}. */
    private void release(int slot, AbstractPlayableSprite sprite, int hangOffset) {
        NativePositionOps.writeYPosPreserveSubpixel(sprite, (y + hangOffset) & 0xFFFF);
        if (sprite.isHurt() || sprite.getDead()) {
            letGo(slot, sprite, HURT_COOLDOWN);
            return;
        }
        if (!sprite.isLogicalJumpPressActive()) {
            return;
        }
        letGo(slot, sprite, RELEASE_COOLDOWN);
        priorityWord = PRIORITY_IN_FRONT;
        int xVel;
        int yBoost = RELEASE_Y_BOOST;
        if (sprite.isLeftPressed()) {
            xVel = -RELEASE_X_VEL;
        } else {
            priorityWord = PRIORITY_BEHIND;
            if (sprite.isRightPressed()) {
                xVel = RELEASE_X_VEL;
            } else {
                xVel = 0;
                if (sprite.isDownPressed()) {
                    yBoost = 0;
                }
            }
        }
        sprite.setXSpeed((short) xVel);
        // move.l $2E(a0),d0 / asr.l #8,d0 / add.w d2,d0: the throw inherits the bar's own swing
        // speed, so a bar caught on the way up throws harder than one caught at the bottom.
        sprite.setYSpeed((short) ((swingSpeed >> 8) + yBoost));
        sprite.setAir(true);
        sprite.setJumping(true);
        sprite.setRolling(true);
        sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
        services().playSfx(Sonic3kSfx.JUMP.id);
    }

    private void letGo(int slot, AbstractPlayableSprite sprite, int cooldown) {
        ObjectControlState.none().applyTo(sprite);
        sprite.setObjectMappingFrameControl(false);
        setHolding(slot, false);
        setCooldown(slot, cooldown);
    }

    /** {@code Gradual_SwingOffset} (sonic3k.asm:92484-92515); returns the offset's high word. */
    private int gradualSwingOffset() {
        int step = SWING_ACCELERATION;
        if (swingReversed) {
            step = -step;
            swingOffset += swingSpeed;
            if (swingOffset < 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = SWING_SPEED;
                swingOffset = 0;
                swingReversed = false;
            }
        } else {
            swingOffset += swingSpeed;
            if (swingOffset > 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = -SWING_SPEED;
                swingOffset = 0;
                swingReversed = true;
            }
        }
        return (short) (swingOffset >> 16);
    }

    private boolean holding(int slot) { return slot == 0 ? p1Holding : p2Holding; }

    private void setHolding(int slot, boolean value) {
        if (slot == 0) {
            p1Holding = value;
        } else {
            p2Holding = value;
        }
    }

    private int cooldown(int slot) { return slot == 0 ? p1Cooldown : p2Cooldown; }

    private void setCooldown(int slot, int value) {
        if (slot == 0) {
            p1Cooldown = value;
        } else {
            p2Cooldown = value;
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(priorityWord); }
    @Override public int getOnScreenHalfWidth() { return 0x30; }
    @Override public int getOnScreenHalfHeight() { return 4; }

    public boolean holdingForTest(int slot) { return holding(slot); }
    public int cooldownForTest(int slot) { return cooldown(slot); }
    public int priorityWordForTest() { return priorityWord; }
    int swingSpeedForTest() { return swingSpeed; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_ELEVATOR_BAR);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, y, false, false, PALETTE_LINE);
        }
    }
}
