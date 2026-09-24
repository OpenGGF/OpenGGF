package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The four fragments {@code Obj_Iwamodoki} throws when it detonates:
 * {@code ChildObjDat_8FBD6} -> {@code loc_8FB90} -> {@code loc_8FBB8}
 * (sonic3k.asm:188085-188125, ROM {@code $8FB90}).
 *
 * <p>{@code CreateChild2_Complex} writes the offset and velocity pairs from the table
 * (:188117-188135): {@code (-4,4)} at {@code (-$400,-$200)}, {@code (4,4)} at
 * {@code ($400,-$200)}, {@code (-8,-8)} at {@code (-$200,-$400)} and {@code (8,-8)} at
 * {@code ($200,-$400)} -- two low and wide, two high and steep.
 *
 * <p>{@code loc_8FB90} runs {@code SetUp_ObjAttributes3} from {@code word_8FBD0}
 * ({@code priority $280}, an {@code 8 x 4} box, {@code collision_flags $98}), sets
 * {@code shield_reaction} bit 3 so a shield deflects it, and picks
 * {@code mapping_frame = (subtype >> 2) + 6} -- the child subtypes are {@code 0, 2, 4, 6}, so the
 * first pair draws frame 6 and the second frame 7. {@code loc_8FBB8} is {@code MoveSprite}, which
 * applies the {@code $38} gravity, followed by {@code Animate_Raw}; the tail is
 * {@code Sprite_CheckDeleteTouchXY}.
 */
public final class IwamodokiShrapnelInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {

    /** {@code word_8FBD0}: {@code dc.w $280} (sonic3k.asm:188110). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code dc.b 8,4,0,$98} (:188111). */
    private static final int HALF_WIDTH = 8;
    private static final int HALF_HEIGHT = 4;
    private static final int COLLISION_FLAGS = 0x98;
    /** {@code byte_8FC8B} / {@code byte_8FC87}: {@code (0,7,9,$FC)} and {@code (0,6,8,$FC)}. */
    private static final int ANIM_DELAY_LOW = 7;
    private static final int ANIM_DELAY_HIGH = 6;

    /** ROM {@code subtype(a0)}: the child index times two. */
    private int subtype;
    /** ROM {@code mapping_frame(a0)}. */
    private int mappingFrame;
    private int animTimer;
    private final SubpixelMotion.State motion;
    private boolean collisionEnabled = true;
    private static final TouchResponseProfile TOUCH_PROFILE = TouchResponseProfile.fromCanonical(
            com.openggf.game.profiles.touchresponse.TouchResponseProfile.singleRegionShieldDeflect());

    /** Restore entry; SubpixelMotion restores both velocity and fractional position. */
    private IwamodokiShrapnelInstance(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y(), spawn.subtype(), 0, 0);
    }

    public IwamodokiShrapnelInstance(int x, int y, int subtype, int xVel, int yVel) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, subtype, 0, false, 0),
                "IwamodokiShrapnel");
        this.subtype = subtype & 0xFF;
        // move.b subtype(a0),d0 / lsr.b #2,d0 / addq.b #6,d0 (sonic3k.asm:188095-188098).
        this.mappingFrame = (this.subtype >> 2) + 6;
        this.animTimer = this.subtype < 4 ? ANIM_DELAY_LOW : ANIM_DELAY_HIGH;
        this.motion = new SubpixelMotion.State(x & 0xFFFF, y & 0xFFFF, 0, 0, xVel, yVel);
    }

    @Override
    public IwamodokiShrapnelInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new IwamodokiShrapnelInstance(ctx.spawn().x(), ctx.spawn().y(),
                        ctx.spawn().subtype(), 0, 0));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // jsr (MoveSprite): the gravity branch (sonic3k.asm:36038).
        SubpixelMotion.moveSprite(motion, SubpixelMotion.S3K_GRAVITY);
        // Animate_Raw over a two-frame script that ends in $FC, i.e. a loop back to entry 0.
        animTimer--;
        if (animTimer < 0) {
            animTimer = subtype < 4 ? ANIM_DELAY_LOW : ANIM_DELAY_HIGH;
            mappingFrame = mappingFrame == (subtype >> 2) + 6
                    ? (subtype >> 2) + 8
                    : (subtype >> 2) + 6;
        }
        updateDynamicSpawn(motion.x, motion.y);
        // Sprite_CheckDeleteTouchXY: off the camera in either axis and the fragment is gone.
        if (!isOnScreen()) {
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    /** ROM {@code x_vel(a0)}. */
    public int xVel() {
        return (short) motion.xVel;
    }

    /** ROM {@code y_vel(a0)}. */
    public int yVel() {
        return (short) motion.yVel;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    public int getCentreX() {
        return motion.x & 0xFFFF;
    }

    public int getCentreY() {
        return motion.y & 0xFFFF;
    }

    @Override
    public int getCollisionFlags() {
        return collisionEnabled ? COLLISION_FLAGS : 0;
    }

    @Override
    public int getShieldReactionFlags() {
        // loc_8FB90: bset #3,shield_reaction(a0).
        return 1 << 3;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_PROFILE;
    }

    @Override
    public boolean onShieldDeflect(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player)) return false;
        // Touch_ChkHurt_Bounce_Projectile writes signed-word deltas through
        // GetArcTan/GetSineCosine, then muls #-$800 / asr.l #8. It clears
        // collision_flags but leaves the fractions and loc_8FBB8 gravity intact.
        int angle = TrigLookupTable.calcAngle((short) (player.getCentreX() - getCentreX()),
                (short) (player.getCentreY() - getCentreY()));
        motion.xVel = (TrigLookupTable.cosHex(angle) * -0x800) >> 8;
        motion.yVel = (TrigLookupTable.sinHex(angle) * -0x800) >> 8;
        collisionEnabled = false;
        return true;
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
        // The child copies the parent's art_tile, make_art_tile(ArtTile_Iwamodoki,0,0)
        // (sonic3k.asm:188107), whose priority bit is clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_HEIGHT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.IWAMODOKI);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
