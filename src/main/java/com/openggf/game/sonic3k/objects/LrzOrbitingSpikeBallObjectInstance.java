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
 * ROM objects {@code Obj_LRZOrbitingSpikeBallHorizontal} (id {@code $2B}, sonic3k.asm:89077-89145)
 * and {@code Obj_LRZOrbitingSpikeBallVertical} (id {@code $2C}, :89149-89222), both drawn from
 * {@code Map_LRZOrbitingSpikeBall} at ROM {@code $43D24}. Lava Reef act 2 places twelve of
 * {@code $2B} and forty of {@code $2C}; act 1 and the boss act place none. The {@code S3KL} set
 * spends the two ids on {@code Obj_AIZFlippingBridge} and {@code Obj_AIZCollapsingLogBridge},
 * which is the name the id constants carry.
 *
 * <p>The two objects are the same routine on a different axis, so they are one class here with an
 * {@link Axis}; every branch below cites the pair of ROM labels it transcribes.
 *
 * <h2>Init (:89077-89094 / :89149-89166)</h2>
 * <p>{@code $44(a0)} and {@code $46(a0)} take the placed position and are the orbit's centre for
 * the rest of its life. {@code bclr #0,subtype(a0)} both TESTS and CLEARS bit 0: a set bit selects
 * the large ball ({@code width_pixels}/{@code height_pixels} {@code $20}, {@code mapping_frame} 1)
 * and a clear one the small ball ({@code $10}, frame 0). Because the {@code bclr} writes back, the
 * angle arithmetic below reads the subtype with bit 0 already zero -- act 2's {@code $2B}
 * placements are {@code $00} and {@code $80}, and its {@code $2C} placements step the high nibble
 * {@code $00}-{@code $F0}, so none of them is affected, but the clear is the ROM's and is
 * reproduced.
 *
 * <h2>Every frame (:89096-89116 / :89168-89188)</h2>
 * <ol>
 *   <li>{@code collision_flags} is cleared and the priority bit is stripped from
 *       {@code art_tile}, unconditionally.</li>
 *   <li>{@code d0 = (Level_frame_counter+1) * 2} as a byte -- the low byte of the level clock
 *       doubled, so the orbit is 128 level frames long -- negated when {@code status} bit 0 is
 *       set, which is the placement's x-flip bit and here reverses the orbit.</li>
 *   <li>{@code add.b subtype(a0),d0}: the placed subtype is the phase offset.</li>
 *   <li>{@code bpl} skips the restore, so ONLY while the resulting byte angle has bit 7 set --
 *       half the orbit -- does the ball become harmful ({@code collision_flags} {@code $9A}
 *       small, {@code $8F} large) and draw in front. The other half it is scenery.</li>
 *   <li>{@code GetSineCosine} then places the ball on one axis from the anchor; the other
 *       coordinate never moves.</li>
 * </ol>
 *
 * <p>The four displacement shapes are each a different fraction of {@code cos}:
 * horizontal small {@code cos asr 3} (:89122), horizontal large {@code (cos + cos asr 1) asr 3}
 * (:89140-89143), vertical small {@code (cos + cos asr 2) asr 3} (:89194-89197) and vertical large
 * {@code (cos asr 2) - (cos asr 5)} (:89216-89219). They are transcribed as written; the
 * shifts are arithmetic and the ROM's own rounding towards negative infinity is what
 * {@code >>} gives in Java.
 *
 * <p>Both end at {@code loc_1B666} (sonic3k.asm:37372-37378), which unloads the object when
 * {@code ($44(a0) & $FF80) - Camera_X_pos_coarse_back} exceeds {@code $280}. That is the ANCHOR's
 * x for both axes -- {@code move.w $44(a0),d0} (:89126, :89144, :89198, :89220) -- not the orbited
 * position.
 */
public final class LrzOrbitingSpikeBallObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** Which ROM object this placement is. */
    public enum Axis { HORIZONTAL, VERTICAL }

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:89081, :89153). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels/height_pixels(a0)} (:89085-89086, :89157-89158). */
    private static final int SMALL_RADIUS = 0x10;
    /** {@code move.b #$20,width_pixels/height_pixels(a0)} (:89089-89090, :89161-89162). */
    private static final int LARGE_RADIUS = 0x20;
    /** {@code move.b #$9A,collision_flags(a0)} for the small ball (:89108, :89180). */
    private static final int SMALL_COLLISION_FLAGS = 0x9A;
    /** {@code move.b #$8F,collision_flags(a0)} for the large ball (:89132, :89204). */
    private static final int LARGE_COLLISION_FLAGS = 0x8F;
    /** {@code move.b #1,mapping_frame(a0)} for the large ball (:89091, :89163). */
    private static final int LARGE_MAPPING_FRAME = 1;
    private static final int SMALL_MAPPING_FRAME = 0;

    /**
     * Which of the two ROM objects this placement is. Non-final so the rewind coverage guard sees
     * it as restorable state; {@link #recreateForRewind} replays the same axis, and the generic
     * scalar pass restores it.
     */
    private Axis axis;

    /** ROM {@code $44(a0)} / {@code $46(a0)}: the placed anchor the orbit is centred on. */
    private int baseX;
    private int baseY;
    /** {@code subtype(a0)} AFTER {@code bclr #0}: the phase offset added to the clock. */
    private int phase;
    /** True when {@code bclr #0,subtype(a0)} found bit 0 set. */
    private boolean large;
    /** {@code status(a0)} bit 0, from the placement's x-flip: reverses the orbit. */
    private boolean reversed;

    /** ROM {@code x_pos(a0)} / {@code y_pos(a0)} as this frame's routine left them. */
    private int currentX;
    private int currentY;
    /** ROM {@code collision_flags(a0)} as this frame's routine left it. */
    private int collisionFlags;
    /** ROM {@code art_tile(a0)} bit 15 as this frame's routine left it. */
    private boolean highPriority;

    public LrzOrbitingSpikeBallObjectInstance(ObjectSpawn spawn, Axis axis) {
        super(spawn, axis == Axis.HORIZONTAL
                ? "LRZOrbitingSpikeBallHorizontal" : "LRZOrbitingSpikeBallVertical");
        this.axis = axis;
        this.baseX = spawn.x() & 0xFFFF;
        this.baseY = spawn.y() & 0xFFFF;
        int subtype = spawn.subtype() & 0xFF;
        // bclr #0,subtype(a0): the test AND the write-back (sonic3k.asm:89083, :89155).
        this.large = (subtype & 1) != 0;
        this.phase = subtype & 0xFE;
        // rol.w #3,d2 / andi.w #3 puts the placement's bit 13 into status bit 0
        // (sonic3k.asm:37756-37758).
        this.reversed = (spawn.renderFlags() & 1) != 0;
        this.currentX = baseX;
        this.currentY = baseY;
        this.collisionFlags = 0;
        this.highPriority = false;
    }

    /**
     * Both objects sit in the {@code $0004} bank: {@code Obj_LRZOrbitingSpikeBallHorizontal} at
     * ROM {@code $00043B34} and {@code Obj_LRZOrbitingSpikeBallVertical} at {@code $00043C26}
     * (sonic3k.lst), so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzOrbitingSpikeBallObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzOrbitingSpikeBallObjectInstance(ctx.spawn(), axis));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // move.b #0,collision_flags(a0) / andi.w #drawing_mask,art_tile(a0): both cleared before
        // anything else, every frame (sonic3k.asm:89097-89098, :89169-89170).
        collisionFlags = 0;
        highPriority = false;

        int angle = orbitAngle(vIntRunCount);
        // bpl.s: the restore happens only for a byte angle with bit 7 set
        // (sonic3k.asm:89105-89109, :89177-89181).
        if ((angle & 0x80) != 0) {
            highPriority = true;
            collisionFlags = large ? LARGE_COLLISION_FLAGS : SMALL_COLLISION_FLAGS;
        }

        int cos = TrigLookupTable.cosHex(angle);
        if (axis == Axis.HORIZONTAL) {
            currentX = (baseX + horizontalOffset(cos)) & 0xFFFF;
        } else {
            currentY = (baseY + verticalOffset(cos)) & 0xFFFF;
        }
        updateDynamicSpawn(currentX, currentY);
    }

    /**
     * {@code move.b (Level_frame_counter+1).w,d0 / add.b d0,d0}, negated for {@code status} bit 0,
     * then {@code add.b subtype(a0),d0} (sonic3k.asm:89099-89104, :89171-89176). Every step is a
     * byte, so the orbit wraps every 128 level frames.
     */
    private int orbitAngle(int vIntRunCount) {
        int d0 = (levelFrameCounterLowByte(vIntRunCount) * 2) & 0xFF;
        if (reversed) {
            d0 = (-d0) & 0xFF;
        }
        return (d0 + phase) & 0xFF;
    }

    /** {@code loc_43B96} {@code asr #3} / {@code loc_43BDE} {@code (cos + cos asr 1) asr 3}. */
    private int horizontalOffset(int cos) {
        return large ? ((cos + (cos >> 1)) >> 3) : (cos >> 3);
    }

    /** {@code loc_43C88} {@code (cos + cos asr 2) asr 3} / {@code loc_43CD6} {@code (cos asr 2) - (cos asr 5)}. */
    private int verticalOffset(int cos) {
        if (!large) {
            return (cos + (cos >> 2)) >> 3;
        }
        int quarter = cos >> 2;
        return quarter - (quarter >> 3);
    }

    private int levelFrameCounterLowByte(int fallback) {
        int counter = services().levelManager() != null
                ? services().levelManager().getFrameCounter() : fallback;
        return counter & 0xFF;
    }

    // ===== Lifetime =====

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    /** {@code move.w $44(a0),d0} before {@code loc_1B666}: the ANCHOR, not the orbited x. */
    @Override
    public int getOutOfRangeReferenceX() {
        return baseX;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // andi.w #$FF80,d0 / sub.w (Camera_X_pos_coarse_back).w,d0 / cmpi.w #$280,d0 / bhi
        // (sonic3k.asm:37373-37376).
        int delta = ((baseX & 0xFF80) - cameraX) & 0xFFFF;
        return delta > 0x280;
    }

    // ===== Accessors =====

    /** ROM {@code $44(a0)}. */
    public int baseX() {
        return baseX;
    }

    /** ROM {@code $46(a0)}. */
    public int baseY() {
        return baseY;
    }

    /** {@code subtype(a0)} after {@code bclr #0}. */
    public int phase() {
        return phase;
    }

    /** True when the placement's subtype bit 0 selected the 32x32 ball. */
    public boolean isLarge() {
        return large;
    }

    /** {@code status(a0)} bit 0. */
    public boolean isReversed() {
        return reversed;
    }

    public int getCentreX() {
        return currentX & 0xFFFF;
    }

    public int getCentreY() {
        return currentY & 0xFFFF;
    }

    @Override
    public int getCollisionFlags() {
        return collisionFlags;
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
        return highPriority;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return large ? LARGE_RADIUS : SMALL_RADIUS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return large ? LARGE_RADIUS : SMALL_RADIUS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_ORBITING_SPIKE_BALL);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(large ? LARGE_MAPPING_FRAME : SMALL_MAPPING_FRAME,
                getX(), getY(), false, false);
    }
}
