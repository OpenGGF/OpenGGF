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
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * {@code Obj_LRZFlameThrower} (sonic3k.asm:89227-89448), id {@code $29} in the locked-on set and
 * 52 of Lava Reef act 2's placements.
 *
 * <p><b>The subtype picks the variant and the rest.</b> {@code bpl.s loc_43DC4}
 * (sonic3k.asm:89235) branches on bit 7: clear is the horizontal thrower ({@code loc_43DDC},
 * :89257-89341) and set the vertical one ({@code loc_43F12}, :89343-89430). Either way
 * {@code $32 = (subtype & $7F) * 4} is the <b>idle</b> length and {@code $30} starts at
 * {@code 2*60}, the firing length, so the subtype is the pause between bursts and nothing else.
 * The vertical variant also mirrors itself: {@code btst #0,status(a0)} sets {@code render_flags}
 * bit 1, the y-flip (:89243-89245).
 *
 * <p><b>The cycle, read from the branch and not from the sprite.</b> {@code $2F} is the phase.
 * At {@code $2F = 0} the thrower is <em>firing</em>: each frame decrements {@code $30} and falls
 * into the emission section while it stays non-negative; when it goes negative {@code $30} reloads
 * from {@code $32}, {@code $2F} becomes 1 and the frame ends on the solid call alone. At
 * {@code $2F = 1} the thrower is <em>idle</em> until {@code $30} goes negative, at which point
 * {@code $30} reloads {@code 2*60}, {@code $2F} returns to 0 and {@code sfx_FlamethrowerLoud}
 * plays (:89257-89274). Zero-length idles are real: a subtype of {@code 0} gives {@code $32 = 0},
 * one idle frame, and a thrower that never stops.
 *
 * <p><b>The emission is on the level clock, not on this object's age.</b> Only frames where
 * {@code (Level_frame_counter+1) & 3 == 0} emit (:89282-89285), so every thrower on screen fires
 * in step. The same section re-plays the loud sound every sixteenth level frame while {@code $30}
 * is still {@code 30} or more (:89286-89291) -- so the burst's sound stops half a second before
 * the flames do.
 *
 * <p><b>The spread is a sine of a sine.</b> {@code $2E = sin(angle) asr 4} and {@code angle}
 * advances by {@code 8} on each emitting frame (:89292-89296); the flame's velocity is then
 * {@code sin/cos($2E) * 4}. So the jet sweeps through a narrow fan whose half-width is
 * {@code $10} of a byte angle, and the sweep's period is {@code $100/8 = 32} emissions.
 *
 * <p>{@code tst.b render_flags(a0) / bpl} (:89297-89298) skips the allocation entirely while the
 * previous render pass left the thrower off screen: an off-screen thrower still runs its cycle and
 * still plays its sound, but makes no flames.
 *
 * <p>The body is a full solid the whole time, with the variant's own width
 * ({@code d1 $23} horizontal, {@code $1B} vertical) and the same {@code d2 $10} / {@code d3 $11}.
 */
public final class LrzFlameThrowerObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** Which routine the subtype's bit 7 selects. */
    public enum Axis {
        /** {@code loc_43DDC}: fires sideways, {@code mapping_frame} 6, solid {@code d1 $23}. */
        HORIZONTAL,
        /** {@code loc_43F12}: fires vertically, {@code mapping_frame} 7, solid {@code d1 $1B}. */
        VERTICAL
    }

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:89232). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$18,width_pixels(a0)} / {@code #$10,height_pixels(a0)} (:89230-89231). */
    private static final int WIDTH_PIXELS = 0x18;
    private static final int HEIGHT_PIXELS = 0x10;
    /** {@code move.w #$23,d1} at {@code loc_43EF6} (:89333). */
    private static final int SOLID_HALF_WIDTH_HORIZONTAL = 0x23;
    /** {@code move.w #$1B,d1} at {@code loc_4402C} (:89428). */
    private static final int SOLID_HALF_WIDTH_VERTICAL = 0x1B;
    /** {@code move.w #$10,d2 / #$11,d3}, the same for both (:89334-89335, :89429-89430). */
    private static final int SOLID_HEIGHT_AIR = 0x10;
    private static final int SOLID_HEIGHT_GROUND = 0x11;
    /** {@code move.w #2*60,$30(a0)} (:89241, :89251, :89269, :89355): the firing length. */
    private static final int FIRING_FRAMES = 2 * 60;
    /** {@code move.b #6/#7,mapping_frame(a0)} (:89242, :89252). */
    private static final int FRAME_HORIZONTAL = 6;
    private static final int FRAME_VERTICAL = 7;
    /** {@code andi.b #3,d0} on {@code Level_frame_counter+1} (:89283). */
    private static final int EMIT_MASK = 3;
    /** {@code andi.b #$F,d1} for the repeat sound (:89286). */
    private static final int LOUD_REPEAT_MASK = 0x0F;
    /** {@code cmpi.w #30,$30(a0) / blo} (:89288-89289). */
    private static final int LOUD_REPEAT_MIN_REMAINING = 30;
    /** {@code addq.b #8,angle(a0)} (:89296). */
    private static final int ANGLE_STEP = 8;
    /** {@code move.b #2,$24(a0)} on the flame-frame timer (:89277). */
    private static final int FLAME_FRAME_RELOAD = 2;
    /** {@code addi.w #$10,x_pos(a1)} / {@code y_pos(a1)} (:89294, :89403). */
    private static final int FLAME_SPAWN_OFFSET = 0x10;
    /** {@code subi.w #2*$10,x_pos(a1)} / {@code #$20,y_pos(a1)} on the flip (:89313, :89422). */
    private static final int FLAME_FLIP_BACKOFF = 0x20;

    // Both are decoded once and never written again, but the rewind coverage guard restores by
    // field: a final field is a gap it cannot close, so they are plain fields and
    // recreateForRewind replays the same spawn and the same axis.
    private Axis axis;
    /** ROM {@code status(a0)} bit 0, the placement's x-flip. */
    private boolean mirrored;

    /** ROM {@code $32(a0)}: {@code (subtype & $7F) * 4}, the idle length. */
    private int idleFrames;
    /** ROM {@code $30(a0)}. */
    private int phaseTimer;
    /** ROM {@code $2F(a0)}: 0 firing, 1 idle. */
    private boolean idle;
    /** ROM {@code angle(a0)}. */
    private int angle;
    /** ROM {@code $2E(a0)}, the emission's byte angle. */
    private int emissionAngle;
    /** ROM {@code $24(a0)}, the flame-frame timer handed to each child as its {@code $25}. */
    private int flameFrameTimer;
    /** ROM {@code $25(a0)}, the flame's starting {@code mapping_frame}. */
    private int flameStartFrame;

    public LrzFlameThrowerObjectInstance(ObjectSpawn spawn, Axis axis) {
        super(spawn, axis == Axis.VERTICAL ? "LRZFlameThrowerVertical" : "LRZFlameThrowerHorizontal");
        this.axis = axis;
        this.mirrored = spawn != null && (spawn.renderFlags() & 1) != 0;
        int subtype = spawn == null ? 0 : spawn.subtype() & 0xFF;
        // andi.w #$7F,d0 / lsl.w #2,d0 on the bit-7 branch (:89238-89239); the other branch's
        // lsl.w #2,d0 runs on a value bpl already proved is below $80 (:89248).
        this.idleFrames = (subtype & 0x7F) << 2;
        this.phaseTimer = FIRING_FRAMES;
        this.idle = false;
        this.angle = 0;
        this.emissionAngle = 0;
        this.flameFrameTimer = 0;
        this.flameStartFrame = 0;
    }

    /** Probe constructor: the subtype's own bit 7 picks the variant, as {@code bpl} does. */
    public LrzFlameThrowerObjectInstance(ObjectSpawn spawn) {
        this(spawn, spawn != null && (spawn.subtype() & 0x80) != 0 ? Axis.VERTICAL : Axis.HORIZONTAL);
    }

    /**
     * {@code Obj_LRZFlameThrower} sits at ROM {@code $00043D68} (sonic3k.lst); its whole code
     * block lies in one bank, so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzFlameThrowerObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn spawn = ctx.spawn();
        Axis restored = axis;
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzFlameThrowerObjectInstance(spawn, restored));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // loc_43DDC / loc_43F12: tst.b $2F(a0) picks the phase, and only the firing phase's
        // fall-through reaches the emission section on this frame.
        boolean emitting;
        if (!idle) {
            phaseTimer--;
            if (phaseTimer >= 0) {
                emitting = true;
            } else {
                // move.w $32(a0),$30(a0) / move.b #1,$2F(a0) / bra loc_43EF6: solid only.
                phaseTimer = idleFrames;
                idle = true;
                emitting = false;
            }
        } else {
            phaseTimer--;
            if (phaseTimer >= 0) {
                emitting = false;
            } else {
                // move.w #2*60,$30 / clr $2F / sfx_FlamethrowerLoud, then fall into loc_43E14.
                phaseTimer = FIRING_FRAMES;
                idle = false;
                playFlamethrowerLoud();
                emitting = true;
            }
        }
        if (!emitting) {
            return;
        }
        advanceFiringFrame(levelFrameCounterOrFallback(vIntRunCount));
    }

    /** {@code loc_43E14} / {@code loc_43F4A} (sonic3k.asm:89275-89320, :89366-89427). */
    private void advanceFiringFrame(int levelFrame) {
        // subq.b #1,$24(a0) / bpl / move.b #2,$24(a0) / addq.b #1,$25(a0) / andi.b #1,$25(a0).
        flameFrameTimer = (flameFrameTimer - 1) & 0xFF;
        if (flameFrameTimer > 0x7F) {
            flameFrameTimer = FLAME_FRAME_RELOAD;
            flameStartFrame = (flameStartFrame + 1) & 1;
        }
        if ((levelFrame & EMIT_MASK) != 0) {
            return;
        }
        if ((levelFrame & LOUD_REPEAT_MASK) == 0 && phaseTimer >= LOUD_REPEAT_MIN_REMAINING) {
            playFlamethrowerLoud();
        }
        // move.b angle(a0),d0 / GetSineCosine / asr.w #4,d0 / move.b d0,$2E / addq.b #8,angle.
        emissionAngle = (TrigLookupTable.sinHex(angle & 0xFF) >> 4) & 0xFF;
        angle = (angle + ANGLE_STEP) & 0xFF;
        // tst.b render_flags(a0) / bpl: no allocation at all while the thrower is off screen.
        if (!isWithinSolidContactBounds()) {
            return;
        }
        spawnFlame();
    }

    /** The {@code AllocateObjectAfterCurrent} block (sonic3k.asm:89292-89320, :89396-89427). */
    private void spawnFlame() {
        int sin = TrigLookupTable.sinHex(emissionAngle);
        int cos = TrigLookupTable.cosHex(emissionAngle);
        int flameX = getCentreX();
        int flameY = getCentreY();
        int xVel;
        int yVel;
        if (axis == Axis.HORIZONTAL) {
            // addi.w #$10,x_pos(a1); x_vel = cos*4, y_vel = sin*4.
            flameX = (flameX + FLAME_SPAWN_OFFSET) & 0xFFFF;
            xVel = cos << 2;
            yVel = sin << 2;
            if (mirrored) {
                xVel = -xVel;
                flameX = (flameX - FLAME_FLIP_BACKOFF) & 0xFFFF;
            }
        } else {
            // addi.w #$10,y_pos(a1); y_vel = cos*4, x_vel = sin*4.
            flameY = (flameY + FLAME_SPAWN_OFFSET) & 0xFFFF;
            yVel = cos << 2;
            xVel = sin << 2;
            if (mirrored) {
                yVel = -yVel;
                flameY = (flameY - FLAME_FLIP_BACKOFF) & 0xFFFF;
            }
        }
        final int x = flameX;
        final int y = flameY;
        final int vx = xVel;
        final int vy = yVel;
        final int startFrame = flameStartFrame;
        final int flicker = flameFrameTimer;
        final boolean flipX = mirrored;
        final boolean flipY = axis == Axis.VERTICAL && mirrored;
        // AllocateObjectAfterCurrent runs the child on this same frame.
        spawnAfterCurrentSibling(() ->
                new LrzFlameObjectInstance(x, y, vx, vy, startFrame, flicker, flipX, flipY));
    }

    /** ROM {@code x_pos}/{@code y_pos} are object centres; the thrower never moves. */
    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    private void playFlamethrowerLoud() {
        try {
            services().playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.FLAMETHROWER_LOUD.id);
        } catch (Exception ignored) {
            // A headless fixture without an audio service still runs the cycle.
        }
    }

    /**
     * {@code move.b (Level_frame_counter+1).w,d0} (sonic3k.asm:89282). The fallback is the
     * dispatch's own {@code V_int_run_count}, which is what a fixture without a level manager
     * can drive; the two agree in production because the level clock is the one the manager keeps.
     */
    private int levelFrameCounterOrFallback(int fallback) {
        try {
            return (services().levelManager() != null
                    ? services().levelManager().getFrameCounter() : fallback) & 0xFF;
        } catch (Exception e) {
            return fallback & 0xFF;
        }
    }

    /** ROM {@code $32(a0)}. */
    public int idleFrames() {
        return idleFrames;
    }

    /** ROM {@code $30(a0)}. */
    public int phaseTimer() {
        return phaseTimer;
    }

    /** ROM {@code $2F(a0)} as a flag: true while the thrower is between bursts. */
    public boolean isIdle() {
        return idle;
    }

    /** ROM {@code angle(a0)}. */
    public int angle() {
        return angle;
    }

    /** ROM {@code $2E(a0)}. */
    public int emissionAngle() {
        return emissionAngle;
    }

    /** ROM {@code $25(a0)}. */
    public int flameStartFrame() {
        return flameStartFrame;
    }

    public Axis axis() {
        return axis;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(
                axis == Axis.VERTICAL ? SOLID_HALF_WIDTH_VERTICAL : SOLID_HALF_WIDTH_HORIZONTAL,
                SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile($090,1,0) (sonic3k.asm:89228): the priority bit is clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return WIDTH_PIXELS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HEIGHT_PIXELS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_FLAME_THROWER);
        if (renderer == null) {
            return;
        }
        int frame = axis == Axis.VERTICAL ? FRAME_VERTICAL : FRAME_HORIZONTAL;
        renderer.drawFrameIndex(frame, getX(), getY(), mirrored,
                axis == Axis.VERTICAL && mirrored);
    }
}
