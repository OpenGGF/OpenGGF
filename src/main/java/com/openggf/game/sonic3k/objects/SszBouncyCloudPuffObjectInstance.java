package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_452DA}/{@code loc_45304} (sonic3k.asm:90717-90790): one of the four puffs
 * {@code Obj_SSZBouncyCloud} throws when a player launches off it.
 *
 * <p>{@code loc_452DA} is the puff's first pass and is a <em>cull</em>, not an init: it compares
 * its own spawn X — the player's X plus the {@code word_466C8} row's offset — against the parent
 * cloud's X plus or minus {@code $18} depending on which way the puff is travelling, and deletes
 * itself when the puff would sit outside that window. A player who bounces off the end of a cloud
 * therefore sheds fewer than four puffs, which is why the count is not fixed.
 *
 * <p>{@code loc_45304} then installs the real state: {@code render_flags 4},
 * {@code height_pixels 4}, {@code width_pixels 4}, {@code priority $80},
 * {@code make_art_tile(ArtTile_SSZMisc+$102,3,1)} over {@code Map_SSZBouncyCloud} and
 * {@code anim 2}. {@code loc_45336} runs {@code Animate_SpriteIrregularDelay} over
 * {@code byte_46AD4} — the (frame, delay) pairs {@code ($01,$0B)}, {@code ($02,$05)} and then
 * {@code $FC}, whose handler at {@code loc_1AD0C} does {@code addq.b #2,routine(a0)}; the next
 * pass sees a non-zero {@code routine} and installs {@code Delete_Current_Sprite}. So a puff lives
 * {@code $0C + $06} frames and then goes, with no off-screen test of its own.
 *
 * <p>Motion is {@code MoveSprite2} — velocity only, no gravity — followed by {@code sub_45364} on
 * each velocity word: {@code d0 = v >> 2} arithmetically, then {@code v -= d0}. That is three
 * quarters of the velocity per frame with the 68000's rounding, so a negative velocity decays
 * towards zero one unit slower than its positive mirror.
 */
public final class SszBouncyCloudPuffObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code move.w #$80,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x80);
    /** {@code make_art_tile(ArtTile_SSZMisc+$102,3,1)}. */
    private static final int PALETTE_LINE = 3;
    /** {@code byte_46AD4}: frame $01 for {@code $0B}+1 frames, then frame $02 for {@code $05}+1. */
    private static final int FIRST_FRAME = 0x01;
    private static final int FIRST_DELAY = 0x0B;
    private static final int SECOND_FRAME = 0x02;
    private static final int SECOND_DELAY = 0x05;

    /** 16.16 position, as {@code MoveSprite2} keeps it. */
    private int subX;
    private int subY;
    private int xVel;
    private int yVel;
    /**
     * {@code anim_frame}/{@code anim_frame_timer} over {@code byte_46AD4}. Both start at zero
     * because {@code anim 2} differs from {@code prev_anim 0}, so the animator's first pass takes
     * the reset branch and then immediately loads pair 0.
     */
    private int animFrame;
    private int animTimer;
    private int mappingFrame = FIRST_FRAME;
    /** {@code routine(a0)} — the {@code $FC} script command's {@code addq.b #2}. */
    private int routine;
    /** The parent cloud's {@code x_pos}, which {@code loc_452DA} reads through {@code $2E(a0)}. */
    private int cloudX;
    /** False until {@code loc_452DA} has run, i.e. until the object's first pass. */
    private boolean windowChecked;

    public SszBouncyCloudPuffObjectInstance(ObjectSpawn spawn, int xVel, int yVel, int cloudX) {
        super(spawn, "SSZBouncyCloudPuff");
        this.subX = (spawn.x() & 0xFFFF) << 16;
        this.subY = (spawn.y() & 0xFFFF) << 16;
        this.xVel = xVel;
        this.yVel = yVel;
        this.cloudX = cloudX;
    }

    /** Probe/rewind constructor. */
    public SszBouncyCloudPuffObjectInstance(ObjectSpawn spawn) {
        this(spawn, 0, 0, 0);
    }

    @Override
    public SszBouncyCloudPuffObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszBouncyCloudPuffObjectInstance(ctx.spawn());
    }

    /**
     * {@code loc_452DA}: true when the puff survives the parent-cloud window check. Evaluated by
     * the cloud before it allocates, because the ROM's delete happens on the child's first pass
     * and the observable result is identical.
     */
    public static boolean survivesWindow(int cloudX, int puffX, int puffXVel) {
        int cloud = cloudX & 0xFFFF;
        int puff = puffX & 0xFFFF;
        if (puffXVel < 0) {
            // subi.w #$18,d0 / cmp.w x_pos(a0),d0 / bls.s loc_45304
            return ((cloud - 0x18) & 0xFFFF) <= puff;
        }
        // addi.w #$18,d0 / cmp.w x_pos(a0),d0 / bhs.s loc_45304
        return ((cloud + 0x18) & 0xFFFF) >= puff;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // loc_45336: the animation runs before the routine test, so the frame the script bumps
        // routine is still drawn; the pass after it installs Delete_Current_Sprite.
        if (!windowChecked) {
            windowChecked = true;
            if (!survivesWindow(cloudX, getX(), xVel)) {
                // loc_452FE: the puff deletes itself on the pass it was allocated for.
                ObjectLifetimeOps.deleteNoRespawn(this);
                return;
            }
        }
        if (routine != 0) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        animate();
        // MoveSprite2: 16.16 position plus velocity, no gravity term.
        subX += xVel << 8;
        subY += yVel << 8;
        // sub_45364 on x_vel then y_vel: asr #2 and subtract, i.e. three quarters with 68000
        // rounding — asr of a negative rounds towards minus infinity, so -4 decays to -3.
        xVel = (short) (xVel - (xVel >> 2));
        yVel = (short) (yVel - (yVel >> 2));
    }

    /** {@code Animate_SpriteIrregularDelay} over {@code byte_46AD4}. */
    private void animate() {
        if (--animTimer >= 0) {
            return;
        }
        switch (animFrame) {
            case 0 -> {
                mappingFrame = FIRST_FRAME;
                animTimer = FIRST_DELAY;
                animFrame = 1;
            }
            case 1 -> {
                mappingFrame = SECOND_FRAME;
                animTimer = SECOND_DELAY;
                animFrame = 2;
            }
            // $FC -> loc_1AD0C: addq.b #2,routine(a0), clr anim_frame_timer, addq.b #1,anim_frame.
            default -> {
                routine += 2;
                animTimer = 0;
                animFrame++;
            }
        }
    }

    @Override public int getX() { return (subX >>> 16) & 0xFFFF; }
    @Override public int getY() { return (subY >>> 16) & 0xFFFF; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 4; }
    @Override public int getOnScreenHalfHeight() { return 4; }

    public int mappingFrameForTest() { return mappingFrame; }
    public int xVelForTest() { return xVel; }
    public int yVelForTest() { return yVel; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_BOUNCY_CLOUD);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false, PALETTE_LINE);
        }
    }
}
