package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_91756}-{@code loc_917B4} (sonic3k.asm:198674-198712): the EggRobo's laser shot,
 * created from {@code ChildObjDat_919DE}.
 *
 * <p>{@code word_919CA} gives it priority {@code $280}, {@code $20} by {@code 4} pixels and mapping
 * frame 7, and {@code $2E} starts at {@code $1F}. For those thirty-two frames the shot is a
 * charging muzzle flash: it alternates between frames 0 and 7 on {@code V_int_run_count+3} bit 0,
 * <em>has no collision at all</em> ({@code word_919CA}'s collision byte is zero) and does not move.
 * Only when the timer runs out does {@code collision_flags} become {@code $9C}, {@code x_vel}
 * become {@code -$800} away from the arm's facing, and {@code sfx_Laser} play. That charge window
 * is why a player standing in the line of fire can still walk out of it.
 *
 * <p>The moving phase is {@code MoveSprite2} plus {@code Sprite_CheckDeleteTouch}; there is no
 * gravity and no lifetime beyond leaving the screen.
 */
public final class EggRoboShotInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {
    /** {@code dc.w $280}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x280);
    /** {@code move.w #$1F,$2E(a0)}. */
    static final int CHARGE_FRAMES = 0x1F;
    /** {@code move.b #$9C,collision_flags(a0)}. */
    static final int ARMED_COLLISION_FLAGS = 0x9C;
    /** {@code move.w #-$800,d0}. */
    static final int SHOT_X_VEL = 0x800;
    /** {@code moveq #0,d0} / {@code moveq #7,d0} on {@code V_int_run_count+3} bit 0. */
    private static final int CHARGE_FRAME_A = 0;
    private static final int CHARGE_FRAME_B = 7;
    private static final int SHIELD_REACTION = 1 << 3;
    private static final TouchResponseProfile TOUCH = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL, false, true, false,
            TouchShieldDeflectCapability.SHIELD_DEFLECT, SHIELD_REACTION,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

    @RewindTransient(reason = "Shot spawn renderFlags stores direction; recreateForRewind reconstructs it.")
    private final boolean facingLeft;
    private int x;
    private int y;
    private int xFixed;
    private int charge = CHARGE_FRAMES;
    private int xVelocity;
    private int mappingFrame = CHARGE_FRAME_B;

    public EggRoboShotInstance(ObjectSpawn spawn, boolean facingLeft) {
        super(spawn, "SSZEggRoboShot");
        this.facingLeft = facingLeft;
        this.x = spawn.x();
        this.y = spawn.y();
        this.xFixed = spawn.x() << 16;
    }

    /** Probe/rewind constructor. */
    public EggRoboShotInstance(ObjectSpawn spawn) {
        this(spawn, (spawn.renderFlags() & 1) == 0);
    }

    @Override
    public EggRoboShotInstance recreateForRewind(RewindRecreateContext ctx) {
        return new EggRoboShotInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        if (charge >= 0) {
            mappingFrame = ((vIntRunCount + 3) & 1) != 0 ? CHARGE_FRAME_B : CHARGE_FRAME_A;
            if (--charge >= 0) {
                return;
            }
            mappingFrame = CHARGE_FRAME_B;
            xVelocity = facingLeft ? -SHOT_X_VEL : SHOT_X_VEL;
            services().playSfx(Sonic3kSfx.LASER.id);
            return;
        }
        xFixed += xVelocity << 8;
        x = (xFixed >> 16) & 0xFFFF;
        updateDynamicSpawn(x, y);
        var camera = services().camera();
        if (camera != null && isCoarseXOutOfRange(x, camera.getX(), coarseXCullRange())) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 4; }
    /** word_919CA's collision byte is zero: the muzzle flash cannot hurt anybody. */
    @Override public int getCollisionFlags() { return charge >= 0 ? 0 : ARMED_COLLISION_FLAGS; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getShieldReactionFlags() { return SHIELD_REACTION; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TOUCH; }

    public boolean armedForTest() { return charge < 0; }
    public int chargeForTest() { return charge; }
    public int xVelocityForTest() { return xVelocity; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_EGG_ROBO);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, facingLeft, false, 0);
        }
    }
}
