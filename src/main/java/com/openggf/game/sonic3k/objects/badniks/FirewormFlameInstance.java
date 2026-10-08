package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
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

import java.io.IOException;
import java.util.List;

/**
 * The flame every Fireworm segment carries: {@code ChildObjDat_8FA30} -> {@code loc_8F95C}
 * (sonic3k.asm:196382-196452, ROM {@code $8F95C}), created at {@code (0,-$E)} from its segment.
 *
 * <p>{@code loc_8F95C} runs {@code Refresh_ChildPositionAdjusted} first, so the flame is pinned to
 * its segment every frame, flips included, and never moves on its own. Its init
 * ({@code loc_8F97C}, :196392-196399) takes {@code word_8FA08} -- {@code priority $180}, an
 * {@code 8 x 8} box, {@code mapping_frame} 3, {@code collision_flags $98} -- through
 * {@code SetUp_ObjAttributes3}, which does <b>not</b> write mappings or {@code art_tile}: the flame
 * keeps the {@code Map_FirewormSegments} sheet {@code CreateChild1_Normal} copied from the segment.
 * It also sets {@code shield_reaction} bit 4, so this is fire and a fire shield ignores it.
 *
 * <p>The flicker is two states. Routine 2 animates {@code byte_8FA56} through {@code Animate_Raw};
 * the script's {@code $F4} runs {@code loc_8F9A4} (:196406-196414), which parks
 * {@code mapping_frame} on 7 and waits a {@code Random_Number & $3F} number of frames in
 * {@code $2E(a0)}. {@code loc_8F9CE} (:196420-196423) then puts routine 2 back. The wait is the
 * only randomness in the object, which is why every flame in a chain flickers out of step.
 */
public final class FirewormFlameInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {

    /** {@code word_8FA08}: {@code dc.w $180} (sonic3k.asm:196450). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0180);
    /** {@code dc.b 8,8,3,$98} (:196451). */
    private static final int HALF_SIZE = 8;
    private static final int INITIAL_MAPPING_FRAME = 3;
    private static final int COLLISION_FLAGS = 0x98;
    /** {@code bset #4,shield_reaction(a0)} (loc_8F97C): fire. */
    private static final int SHIELD_REACTION = 1 << 4;
    /**
     * {@code Child_DrawTouch_Sprite} publishes an ordinary hurt region; the only unusual thing
     * about the flame is the fire bit, which a fire shield answers.
     */
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL,
            false,
            false,
            false,
            TouchShieldDeflectCapability.NONE,
            SHIELD_REACTION,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    /** {@code move.b #7,mapping_frame(a0)} (loc_8F9A4). */
    private static final int HOLD_MAPPING_FRAME = 7;
    /** {@code andi.w #$3F,d0} on the {@code Random_Number} draw (loc_8F9A4). */
    private static final int HOLD_MASK = 0x3F;
    /** {@code ChildObjDat_8FA30}: {@code dc.b 0,-$E}. */
    static final int CHILD_DX = 0;
    static final int CHILD_DY = -0x0E;

    private enum Phase { FLICKER, HOLD }

    private Phase phase = Phase.FLICKER;
    /** ROM {@code $2E(a0)} during the hold. */
    private int holdTimer;
    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();
    @RewindTransient(reason = "read-only ROM script window, reloaded lazily from the ROM reader")
    private S3kRawAnimation scripts;

    private int centreX;
    private int centreY;
    private boolean flipX;
    private boolean flipY;

    public FirewormFlameInstance(int x, int y) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, 0, 0, false, 0), "FirewormFlame");
        this.centreX = x & 0xFFFF;
        this.centreY = y & 0xFFFF;
        this.anim.script = Sonic3kConstants.LRZ_FIREWORM_ANIM_FLAME_ADDR;
        this.anim.mappingFrame = INITIAL_MAPPING_FRAME;
    }

    @Override
    public FirewormFlameInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new FirewormFlameInstance(ctx.spawn().x(), ctx.spawn().y()));
    }

    /** {@code Refresh_ChildPositionAdjusted} (sonic3k.asm:177300-177327). */
    void refreshFrom(int parentX, int parentY, boolean parentFlipX, boolean parentFlipY) {
        this.flipX = parentFlipX;
        this.flipY = parentFlipY;
        int dx = parentFlipX ? -CHILD_DX : CHILD_DX;
        int dy = parentFlipY ? -CHILD_DY : CHILD_DY;
        this.centreX = (parentX + dx) & 0xFFFF;
        this.centreY = (parentY + dy) & 0xFFFF;
        updateDynamicSpawn(centreX, centreY);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        S3kRawAnimation loaded = scripts();
        if (phase == Phase.FLICKER) {
            if (loaded != null) {
                loaded.animateNoSst(anim, anim.script, this::beginHold);
            }
            return;
        }
        // loc_8F9C8: Obj_Wait on $2E(a0), then loc_8F9CE puts routine 2 back.
        holdTimer--;
        if (holdTimer < 0) {
            phase = Phase.FLICKER;
        }
    }

    /** {@code loc_8F9A4}. */
    private void beginHold() {
        phase = Phase.HOLD;
        anim.mappingFrame = HOLD_MAPPING_FRAME;
        holdTimer = randomWord() & HOLD_MASK;
    }

    private int randomWord() {
        try {
            return services().rng().nextRaw() & 0xFFFF;
        } catch (Exception e) {
            return 0;
        }
    }

    private S3kRawAnimation scripts() {
        if (scripts == null) {
            try {
                scripts = S3kRawAnimation.load(services().romReader(),
                        Sonic3kConstants.LRZ_FIREWORM_RAW_ANIM_BASE_ADDR,
                        Sonic3kConstants.LRZ_FIREWORM_RAW_ANIM_SIZE);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
        return scripts;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return anim.mappingFrame;
    }

    /** ROM {@code $2E(a0)}; negative outside the hold. */
    public int holdTimer() {
        return phase == Phase.HOLD ? holdTimer : -1;
    }

    public int getCentreX() {
        return centreX;
    }

    public int getCentreY() {
        return centreY;
    }

    @Override
    public int getShieldReactionFlags() {
        return SHIELD_REACTION;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
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
        // The flame inherits make_art_tile(ArtTile_FirewormSegments,1,1) from the segment
        // (ObjDat3_8F9FC, sonic3k.asm:196445), whose priority bit is set.
        return true;
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
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FIREWORM_SEGMENTS);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(anim.mappingFrame, getX(), getY(), flipX, flipY);
    }
}
