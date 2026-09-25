package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM object {@code Obj_LRZSwingingSpikeBall} -- object id {@code $20} in the {@code SKL} pointer
 * set (sonic3k.asm:88652-88759, ROM {@code $43500}; {@code Map_LRZSwingingSpikeBall} at ROM
 * {@code $43666} and the act 2 skin {@code Map_LRZSwingingSpikeBall2} at {@code $4367E}). The
 * {@code S3KL} set spends the same id on the MGZ/LBZ smashing pillar.
 *
 * <p>A spiked ball on a chain that sweeps a full circle. {@code $34(a0)} is the angle and
 * {@code $36(a0)} its step: {@code 2} a frame, or {@code -2} when the placement's X-flip is set
 * (:88698-88704), so a full sweep takes 128 frames either way. {@code $44}/{@code $46} hold the
 * placed position, which the swing is measured from and which the ROM also hands to
 * {@code loc_1B666} as the unload test's X (:88710-88711).
 *
 * <p><b>The chain geometry</b> ({@code sub_43604}, :88719-88757). {@code GetSineCosine} gives the
 * sine in {@code d0} and the cosine in {@code d1}; both are swapped into the high word of a long
 * and shifted right four, so one link step is {@code sin >> 4} vertically and {@code cos >> 4}
 * horizontally -- up to sixteen pixels. The subtype's low nibble is the number of links
 * (:88681-88683), and the ball itself sits one step beyond the last of them. A subtype with bit 7
 * set starts the accumulator at two steps instead of one (:88727-88730); none of Lava Reef's
 * twenty-five placements sets it, and their low nibbles are 2, 3 and 4.
 *
 * <p><b>Open question, with a kill condition.</b> {@code GetSineCosine} writes only the low word of
 * {@code d0} and {@code d1} (sonic3k.asm:3021-3028), so after the {@code swap} the long's low word
 * is whatever the caller left in the register's high half. That junk cannot reach the first link --
 * the high word after {@code asr.l #4} is exactly {@code sin >> 4} -- but it is accumulated by the
 * {@code add.l} in the link loop, so with four links it could carry one pixel into the ball's own
 * position. This class models the register as clean. Kill it by finding a trace row where the ball
 * is one pixel off the clean geometry; until then, assuming zero is the only reading that does not
 * invent a value.
 */
public final class LrzSwingingSpikeBallObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:88658). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels(a0)} / {@code height_pixels(a0)} (:88656-88657). */
    private static final int HALF_SIZE = 0x10;
    /** {@code move.b #$9A,collision_flags(a0)} (:88661). */
    private static final int COLLISION_FLAGS = 0x9A;
    /** {@code moveq #2,d0} / {@code neg.w d0} (:88699-88703). */
    private static final int ANGLE_STEP = 2;

    /** ROM {@code $44(a0)} / {@code $46(a0)}: the placed position the swing is measured from. */
    private int baseX;
    private int baseY;
    /** ROM {@code mainspr_childsprites(a1)}: {@code subtype & $F} chain links. */
    private int linkCount;
    /** ROM {@code tst.b subtype(a0) / bpl}: bit 7 starts the accumulator one step further out. */
    private boolean startsOneStepOut;
    /** ROM {@code $36(a0)}. */
    private int angleStep;
    /** ROM {@code $34(a0)}. */
    private int angle;
    /** The chain's link positions, x then y, as {@code sub2_x_pos} holds them. */
    private int[] linkX;
    private int[] linkY;
    /** ROM {@code x_pos(a0)} / {@code y_pos(a0)}: the ball, one step past the last link. */
    private int ballX;
    private int ballY;

    private final String artKey;
    private final String chainArtKey;

    public LrzSwingingSpikeBallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZSwingingSpikeBall");
        this.baseX = spawn.x() & 0xFFFF;
        this.baseY = spawn.y() & 0xFFFF;
        int subtype = spawn.subtype() & 0xFF;
        this.linkCount = subtype & 0x0F;
        this.startsOneStepOut = (subtype & 0x80) != 0;
        // moveq #2,d0 / btst #0,status(a0) / neg.w d0 (sonic3k.asm:88699-88703).
        this.angleStep = (spawn.renderFlags() & 0x1) != 0 ? -ANGLE_STEP : ANGLE_STEP;
        this.angle = 0;
        this.linkX = new int[linkCount];
        this.linkY = new int[linkCount];
        this.ballX = baseX;
        this.ballY = baseY;
        boolean act2 = actIndexOrZero() != 0;
        this.artKey = act2
                ? Sonic3kObjectArtKeys.LRZ2_SWINGING_SPIKE_BALL
                : Sonic3kObjectArtKeys.LRZ_SWINGING_SPIKE_BALL;
        this.chainArtKey = act2
                ? Sonic3kObjectArtKeys.LRZ2_SWINGING_SPIKE_BALL_CHAIN
                : Sonic3kObjectArtKeys.LRZ_SWINGING_SPIKE_BALL_CHAIN;
    }

    private int actIndexOrZero() {
        try {
            return services().currentAct();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * {@code Obj_LRZSwingingSpikeBall} is installed from the SKL object pointer table at ROM
     * {@code $00043500} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzSwingingSpikeBallObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzSwingingSpikeBallObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // loc_435E0 (sonic3k.asm:88706-88711): place the chain and the ball from the CURRENT
        // angle, and only then advance it.
        applyGeometry();
        angle = (byte) (angle + angleStep);
        updateDynamicSpawn(ballX, ballY);
    }

    /** {@code sub_43604} (sonic3k.asm:88719-88757). */
    private void applyGeometry() {
        int sine = TrigLookupTable.sinHex(angle & 0xFF);
        int cosine = TrigLookupTable.cosHex(angle & 0xFF);
        // swap / asr.l #4: the word becomes the high half of a long, so one step is value >> 4
        // pixels with a twelve-bit fraction underneath it.
        int stepY = (sine << 16) >> 4;
        int stepX = (cosine << 16) >> 4;

        int accumulatorY = startsOneStepOut ? stepY * 2 : stepY;
        int accumulatorX = startsOneStepOut ? stepX * 2 : stepX;

        for (int i = 0; i < linkCount; i++) {
            linkX[i] = (baseX + (accumulatorX >> 16)) & 0xFFFF;
            linkY[i] = (baseY + (accumulatorY >> 16)) & 0xFFFF;
            accumulatorX += stepX;
            accumulatorY += stepY;
        }
        // The tail past the dbf loop writes the parent's own position (:88750-88755).
        ballX = (baseX + (accumulatorX >> 16)) & 0xFFFF;
        ballY = (baseY + (accumulatorY >> 16)) & 0xFFFF;
    }

    /** ROM {@code $44(a0)}. */
    public int baseX() {
        return baseX;
    }

    /** ROM {@code $46(a0)}. */
    public int baseY() {
        return baseY;
    }

    /** ROM {@code $34(a0)}. */
    public int angle() {
        return angle;
    }

    /** ROM {@code $36(a0)}. */
    public int angleStep() {
        return angleStep;
    }

    /** ROM {@code mainspr_childsprites(a1)}. */
    public int linkCount() {
        return linkCount;
    }

    /** The chain link positions, nearest the anchor first. */
    public int linkX(int index) {
        return linkX[index];
    }

    public int linkY(int index) {
        return linkY[index];
    }

    public int getCentreX() {
        return ballX;
    }

    public int getCentreY() {
        return ballY;
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
        // make_art_tile(ArtTile_LRZMisc,1,1) (sonic3k.asm:88654) sets the priority bit.
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
        // The ROM draws the chain from a separate main-sprite object whose art_tile is the
        // parent's with andi.w #$9FFF applied (:88669), i.e. the same tiles on palette line 0, and
        // whose mapping_frame is 1 (:88693). The engine draws both from here: the child carries no
        // behaviour of its own beyond Sprite_OnScreen_Test (:88714).
        PatternSpriteRenderer chain = getRenderer(chainArtKey);
        if (chain != null) {
            for (int i = 0; i < linkCount; i++) {
                chain.drawFrameIndex(1, linkX[i] - HALF_SIZE, linkY[i] - HALF_SIZE, false, false);
            }
        }
        PatternSpriteRenderer ball = getRenderer(artKey);
        if (ball == null) {
            return;
        }
        ball.drawFrameIndex(0, getX(), getY(), false, false);
    }
}
