package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * One drop of {@code Obj_LRZLavaFall}, ROM routines {@code loc_4374C} and {@code loc_43764}
 * (sonic3k.asm:88813-88827).
 *
 * <p>The emitter gives it {@code Map_LRZLavaFall} with {@code make_art_tile($0D3,2,0)},
 * {@code render_flags |= $84} -- bit 7 already set, so its first frame counts as drawn --
 * {@code priority $300}, {@code collision_flags $99}, {@code y_vel $800} and a life in
 * {@code $2E(a1)} of {@code $1C} frames, or {@code $24} when the emitter's {@code status} bit 0 is
 * set (:88796-88806).
 *
 * <p>{@code MoveSprite2} (:88823) is {@code ext.l / lsl.l #8 / add.l}, so {@code $800} is eight
 * pixels a frame straight down. The life counter is decremented before the move and a negative
 * value deletes the drop (:88820-88821, :88828), so it lives exactly {@code $2E + 1} frames.
 *
 * <p>Every second drop carries {@code loc_4374C}, which plays {@code sfx_LavaFall} on the frames
 * where {@code Level_frame_counter+1} is a multiple of {@code $10} and the previous render pass
 * left it on screen (:88813-88818).
 *
 * <p>{@code loc_436EE} sets {@code shield_reaction} bit4, so the fire shield
 * blocks these drops. Other shields still take the normal harmful-object hit.
 */
public final class LrzLavaFallDropInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {

    /** {@code move.w #$300,priority(a1)} (sonic3k.asm:88799). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0300);
    /** {@code move.b #$20,width_pixels(a1)} / {@code height_pixels(a1)} (:88800-88801). */
    private static final int HALF_SIZE = 0x20;
    /** {@code move.b #$99,collision_flags(a1)} (:88802). */
    private static final int COLLISION_FLAGS = 0x99;
    /** {@code move.w #$800,y_vel(a1)} (:88804). */
    private static final int FALL_SPEED = 0x800;
    /** {@code move.w #$1C,$2E(a1)} and the flipped {@code #$24} (:88804, :88806). */
    private static final int LIFE_SHORT = 0x1C;
    private static final int LIFE_LONG = 0x24;
    /** {@code andi.b #$F,d0} on {@code Level_frame_counter+1} (:88816). */
    private static final int SOUND_MASK = 0x0F;

    /** ROM {@code $2E(a0)}: the frames left before {@code Delete_Current_Sprite}. */
    private int life;
    /** Whether this drop was given {@code loc_4374C} rather than {@code loc_43764}. */
    private boolean playsSound;
    /** 16.16 position, since {@code MoveSprite2} accumulates sub-pixels. */
    private int xPosition;
    private int yPosition;

    /** Production constructor. */
    public LrzLavaFallDropInstance(int x, int y, boolean playsSound, boolean longLived) {
        super(new ObjectSpawn(x, y, Sonic3kObjectIds.LBZ_LOWERING_GRAPPLE, 0, 0, false, 0),
                "LRZLavaFallDrop");
        this.life = longLived ? LIFE_LONG : LIFE_SHORT;
        this.playsSound = playsSound;
        this.xPosition = (x & 0xFFFF) << 16;
        this.yPosition = (y & 0xFFFF) << 16;
    }

    /** Probe constructor for rewind recreation and reflection-level tests. */
    public LrzLavaFallDropInstance(ObjectSpawn spawn) {
        this(spawn == null ? 0 : spawn.x(), spawn == null ? 0 : spawn.y(), false, false);
    }

    @Override
    public LrzLavaFallDropInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn spawn = ctx.spawn();
        int x = spawn != null ? spawn.x() : 0;
        int y = spawn != null ? spawn.y() : 0;
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzLavaFallDropInstance(x, y, false, false));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (playsSound && isOnScreen()
                && (levelFrameCounterOrFallback(vIntRunCount) & SOUND_MASK) == 0) {
            try {
                services().playSfx(Sonic3kSfx.LAVA_FALL.id);
            } catch (Exception ignored) {
                // Headless replays can omit the audio backend.
            }
        }
        // subq.w #1,$2E(a0) / bmi -> Delete_Current_Sprite (sonic3k.asm:88820-88821, :88828).
        life = (short) (life - 1);
        if (life < 0) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        // jsr (MoveSprite2): ext.l / lsl.l #8 / add.l, so $800 is eight pixels a frame.
        yPosition += FALL_SPEED << 8;
        updateDynamicSpawn((xPosition >> 16) & 0xFFFF, (yPosition >> 16) & 0xFFFF);
    }

    private int levelFrameCounterOrFallback(int fallback) {
        try {
            return services().levelManager() != null
                    ? services().levelManager().getFrameCounter()
                    : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** ROM {@code $2E(a0)}. */
    public int life() {
        return life;
    }

    public boolean playsSound() {
        return playsSound;
    }

    public int getCentreX() {
        return (xPosition >> 16) & 0xFFFF;
    }

    public int getCentreY() {
        return (yPosition >> 16) & 0xFFFF;
    }

    // Declare the same ROM touch semantics for the shared profile dispatcher.
    // The legacy accessors below remain compatible with direct object consumers.
    private static final TouchResponseProfile TOUCH_PROFILE = TouchResponseProfile.fromCanonical(
            new com.openggf.game.profiles.touchresponse.TouchResponseProfile(
                    com.openggf.game.profiles.touchresponse.TouchCategoryDecodeMode.NORMAL,
                    false, true, false,
                    com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability.NONE,
                    0x10,
                    com.openggf.game.profiles.touchresponse.TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                    com.openggf.game.profiles.touchresponse.TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                    com.openggf.game.profiles.touchresponse.TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS));

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_PROFILE;
    }

    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
    }

    @Override
    public int getShieldReactionFlags() {
        // loc_436EE: bset #4,shield_reaction(a1). This selects fire-shield
        // immunity in Touch_ChkHurt, not bit3's projectile deflection.
        return 0x10;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile($0D3,2,0) (sonic3k.asm:88797) leaves the priority bit clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_SIZE;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_SIZE;
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        // The drop's own routine has no out-of-range test: it dies only when $2E runs out
        // (sonic3k.asm:88820-88828).
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return false;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_LAVA_FALL);
        if (renderer == null) {
            return;
        }
        // The emitter leaves mapping_frame at the zero of the cleared slot.
        renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }
}
