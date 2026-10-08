package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
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
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM object {@code Obj_LRZSpikeBall} -- object id {@code $22} in the {@code SKL} pointer set
 * (sonic3k.asm:88838-89070, ROM {@code $436E8}; {@code Map_LRZSpikeBall} at ROM {@code $43A8E}).
 * The {@code S3KL} set spends the same id on {@code Obj_LBZAlarm}.
 *
 * <p>Two shapes, chosen by {@code tst.b subtype(a0)} at init (:88849). Lava Reef act 1 places five
 * of subtype {@code 0} and one of subtype {@code $C0}; act 2 places none.
 *
 * <h2>Subtype 0 -- the grinding boulder ({@code loc_4397E}, :88978-88996)</h2>
 * <p>Not static: {@code angle(a0)} counts DOWN by two a frame ({@code subq.b #2}, :89016) and
 * {@code x_pos} is {@code $44(a0) + (cos(angle) asr 2)}, so the boulder grinds 64 pixels either
 * side of its placed X on a 128-frame cycle while {@code ObjCheckFloorDist} keeps it on the
 * terrain. {@code collision_flags} is {@code $8F} and the priority bit is set for the whole life
 * (:88852-88853). {@code sfx_MechaLand} plays whenever the boulder was drawn last frame and
 * {@code Level_frame_counter+1 & $F} is zero (:88985-88990).
 *
 * <h2>Subtype {@code $C0} -- the swinging boulder that breaks loose ({@code loc_437FE}, :88858)</h2>
 * <ul>
 *   <li>Every frame it takes the same {@code cos(angle) asr 2} X, then clears
 *       {@code collision_flags} and the priority bit and puts BOTH back only while
 *       {@code angle} is negative, i.e. bit 7 set (:88864-88870). It is harmless for half the
 *       sweep.</li>
 *   <li>{@code loc_43830} (:88872-88884) arms {@code $30(a0)} when Player 1 is within
 *       {@code $40} pixels above or below {@code $46(a0)} -- the {@code addi.w #$40} /
 *       {@code cmpi.w #$80} unsigned window -- AND to the LEFT of {@code $44(a0)}, which is what
 *       the {@code bcc} after {@code sub.w $44(a0),d0} tests, and debug placement mode is off.</li>
 *   <li>{@code loc_4385C} (:88886-88892): once armed, the frame {@code angle} equals the subtype
 *       ({@code $C0}) it switches to {@code loc_4389E} with {@code x_vel = -$400}, {@code y_vel}
 *       zero. Starting from {@code angle} 0 that is the thirty-third update.</li>
 * </ul>
 *
 * <h2>Rolling ({@code loc_4389E}, :88896-88964)</h2>
 * <p>Grounded: {@code MoveSprite2} (no gravity), a left-wall probe {@code $20} pixels ahead that
 * stops the roll and DISARMS {@code $30} on a hit, then {@code ObjCheckFloorDist}. A floor
 * distance of {@code $E} or less re-seats the boulder, zeroes {@code y_vel} and makes
 * {@code x_vel} four units more negative each frame -- it accelerates downhill -- and throws a
 * chip; a larger distance sets {@code status} bit 1 and the boulder falls under
 * {@code MoveSprite}'s gravity until {@code ObjCheckFloorDist} turns negative again
 * (:88945-88957). The three-frame, three-entry animation runs in both states (:88959-88965).
 *
 * <p>Both shapes end at {@code loc_1B666} (Sprite_CheckDeleteTouch3's tail, sonic3k.asm:37372),
 * which unloads the object when {@code ($44(a0) & $FF80) - Camera_X_pos_coarse_back} exceeds
 * {@code $280} -- the PLACED X, not the swung one -- except while the boulder is rolling with
 * {@code $30} set, when the ROM draws it unconditionally (:88892-88894, :88966-88972).
 */
public final class LrzSpikeBallObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:88844). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$20,width_pixels/height_pixels/x_radius/y_radius(a0)} (:88842-88847). */
    private static final int RADIUS = 0x20;
    /** {@code move.b #$8F,collision_flags(a0)} (:88852, :88869). */
    private static final int COLLISION_FLAGS = 0x8F;
    /** {@code subq.b #2,angle(a0)} (:88879, :89016). */
    private static final int ANGLE_STEP = 2;
    /** {@code move.w #-$400,x_vel(a0)} (:88890). */
    private static final int BREAK_LOOSE_X_VEL = -0x400;
    /** {@code subi.w #4,x_vel(a0)} (:88913). */
    private static final int ROLL_ACCELERATION = 4;
    /** {@code cmpi.w #$E,d1 / bgt} (:88901-88902). */
    private static final int GROUND_REACH = 0x0E;
    /** {@code moveq #-$20,d3 / add.w x_pos(a0),d3} (:88898-88899, :88919-88920). */
    private static final int LEFT_PROBE_OFFSET = -0x20;
    /** {@code addi.w #$40,d0 / cmpi.w #$80,d0 / bhs} (:88875-88877). */
    private static final int ARM_Y_WINDOW = 0x40;
    /** {@code move.b #3,anim_frame_timer(a0)} and {@code cmpi.b #3,mapping_frame} (:88951-88956). */
    private static final int ANIM_RELOAD = 3;
    private static final int ANIM_FRAMES = 3;

    /** Which ROM routine word 0 holds. */
    private enum Routine { GRIND, SWING, ROLL }

    /** ROM {@code $44(a0)} / {@code $46(a0)}: the placed position. */
    private int baseX;
    private int baseY;
    /** ROM {@code subtype(a0)}, the angle the swinging boulder breaks loose at. */
    private int subtype;
    /** ROM {@code angle(a0)}. */
    private int angle;
    /** ROM {@code $30(a0)}: armed by a player left of and level with the anchor. */
    private boolean armed;
    /** ROM {@code status(a0)} bit 1, {@code Status_InAir}. */
    private boolean airborne;
    private Routine routine;
    /** ROM {@code collision_flags(a0)} as the routine last wrote it. */
    private int collisionFlags;
    /** ROM {@code art_tile(a0)} bit 15. */
    private boolean highPriority;
    /** ROM {@code anim_frame_timer(a0)} / {@code mapping_frame(a0)}. */
    private int animTimer;
    private int mappingFrame;
    /** ROM {@code render_flags(a0)} bit 7 as the previous frame's render pass left it. */
    private boolean renderedLastFrame;

    private final SubpixelMotion.State motion;

    public LrzSpikeBallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZSpikeBall");
        this.baseX = spawn.x() & 0xFFFF;
        this.baseY = spawn.y() & 0xFFFF;
        this.subtype = spawn.subtype() & 0xFF;
        this.motion = new SubpixelMotion.State(baseX, baseY, 0, 0, 0, 0);
        if (subtype == 0) {
            // ori.w #high_priority,art_tile / move.b #$8F,collision_flags / loc_4397E (:88851-88855).
            this.routine = Routine.GRIND;
            this.collisionFlags = COLLISION_FLAGS;
            this.highPriority = true;
        } else {
            this.routine = Routine.SWING;
            this.collisionFlags = 0;
            this.highPriority = false;
        }
    }

    /**
     * {@code Obj_LRZSpikeBall} is installed from the SKL object pointer table at ROM
     * {@code $000436E8} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzSpikeBallObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzSpikeBallObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        boolean wasRendered = renderedLastFrame;
        renderedLastFrame = isOnScreen();
        switch (routine) {
            case GRIND -> grind(vIntRunCount, wasRendered);
            case SWING -> swing(playerEntity);
            case ROLL -> roll(vIntRunCount, wasRendered);
        }
        updateDynamicSpawn(motion.x, motion.y);
    }

    /** {@code loc_4397E} (sonic3k.asm:88978-89018). */
    private void grind(int vIntRunCount, boolean wasRendered) {
        motion.x = swungX();
        // btst #1,status(a0) / bne: nothing in this branch ever sets the bit, so the boulder
        // always re-seats. add.w d1,y_pos(a0) is unconditional -- there is no tst.w d1 here.
        if (!airborne) {
            TerrainCheckResult floor =
                    ObjectTerrainUtils.checkFloorDist(motion.x, motion.y, RADIUS);
            if (floor.foundSurface()) {
                motion.y = (motion.y + floor.distance()) & 0xFFFF;
            }
        }
        if (wasRendered && (levelFrameCounterLowByte(vIntRunCount) & 0x0F) == 0) {
            playSfx(Sonic3kSfx.MECHA_LAND.id);
        }
        throwChip(vIntRunCount, wasRendered);
        stepAnimation();
        angle = (byte) (angle - ANGLE_STEP);
    }

    /** {@code loc_437FE} (sonic3k.asm:88858-88894). */
    private void swing(PlayableEntity playerEntity) {
        motion.x = swungX();
        // move.b #0,collision_flags / andi.w #drawing_mask,art_tile, then both restored only while
        // angle is negative (:88863-88870).
        boolean harmful = (angle & 0x80) != 0;
        collisionFlags = harmful ? COLLISION_FLAGS : 0;
        highPriority = harmful;

        // loc_43830 (:88872-88884): Player 1 only; the ROM reads (Player_1).w directly.
        if (!armed && playerEntity != null) {
            int dy = ((playerEntity.getCentreY() - baseY) + ARM_Y_WINDOW) & 0xFFFF;
            if (dy < 2 * ARM_Y_WINDOW) {
                // sub.w $44(a0),d0 / bcc: the carry is set only when the subtraction borrows,
                // i.e. only when the player is strictly left of the anchor.
                int playerX = playerEntity.getCentreX() & 0xFFFF;
                if (playerX < baseX) {
                    armed = true;
                }
            }
        }

        // loc_4385C (:88886-88892): the compare uses the angle BEFORE this frame's subq.b.
        if (armed && (angle & 0xFF) == subtype) {
            routine = Routine.ROLL;
            motion.xVel = BREAK_LOOSE_X_VEL;
            motion.yVel = 0;
            return;
        }
        angle = (byte) (angle - ANGLE_STEP);
    }

    /** {@code loc_4389E} (sonic3k.asm:88896-88964). */
    private void roll(int vIntRunCount, boolean wasRendered) {
        if (!airborne) {
            SubpixelMotion.moveSprite2(motion);
            if (probeLeftWall()) {
                motion.xVel = 0;
                // move.b #0,$30(a0) (:88908): a boulder stopped by a wall stops being drawn
                // unconditionally and goes back to the loc_1B666 unload test.
                armed = false;
            }
            TerrainCheckResult floor =
                    ObjectTerrainUtils.checkFloorDist(motion.x, motion.y, RADIUS);
            int distance = floor.foundSurface() ? floor.distance() : Integer.MAX_VALUE;
            if (distance > GROUND_REACH) {
                // bset #1,status(a0) (:88907).
                airborne = true;
            } else {
                motion.y = (motion.y + distance) & 0xFFFF;
                motion.yVel = 0;
                if (motion.xVel != 0 && (levelFrameCounterLowByte(vIntRunCount) & 0x0F) == 0) {
                    playSfx(Sonic3kSfx.MECHA_LAND.id);
                }
                motion.xVel = (short) (motion.xVel - ROLL_ACCELERATION);
                throwChip(vIntRunCount, wasRendered);
            }
        } else {
            if (probeLeftWall()) {
                motion.xVel = 0;
            }
            // jsr (MoveSprite): the airborne branch is the one that applies gravity (:88928).
            SubpixelMotion.moveSprite(motion, SubpixelMotion.S3K_GRAVITY);
            TerrainCheckResult floor =
                    ObjectTerrainUtils.checkFloorDist(motion.x, motion.y, RADIUS);
            if (floor.foundSurface() && floor.distance() < 0) {
                motion.y = (motion.y + floor.distance()) & 0xFFFF;
                airborne = false;
                motion.yVel = 0;
            }
        }
        stepAnimation();
    }

    /**
     * {@code moveq #-$20,d3 / add.w x_pos(a0),d3 / ObjCheckLeftWallDist_Part2 / tst.w d1 / bpl}
     * (:88898-88909). A negative distance is an overlap: the boulder is pushed back out of it.
     */
    private boolean probeLeftWall() {
        TerrainCheckResult wall = ObjectTerrainUtils.checkLeftWallDist(
                (motion.x + LEFT_PROBE_OFFSET) & 0xFFFF, motion.y);
        if (!wall.foundSurface() || wall.distance() >= 0) {
            return false;
        }
        // sub.w d1,x_pos(a0): d1 is negative, so this moves the boulder right.
        motion.x = (motion.x - wall.distance()) & 0xFFFF;
        return true;
    }

    /** {@code move.b angle(a0),d0 / GetSineCosine / asr.w #2,d1 / add.w $44(a0),d1}. */
    private int swungX() {
        return (baseX + (TrigLookupTable.cosHex(angle & 0xFF) >> 2)) & 0xFFFF;
    }

    /** {@code sub_439EC} (sonic3k.asm:88998-89050). */
    private void throwChip(int vIntRunCount, boolean wasRendered) {
        // move.w (Level_frame_counter).w,d0 / andi.w #3,d0: the WORD, every fourth level frame.
        if ((levelFrameCounter(vIntRunCount) & 3) != 0 || !wasRendered) {
            return;
        }
        int random;
        try {
            random = services().rng().nextRaw();
        } catch (Exception e) {
            return;
        }
        // andi.w #$1FF,d0 / subi.w #$100,d0 (:89033-89035).
        int xVel = (short) ((random & 0x1FF) - 0x100);
        // asr.w #4,d0 / add.w d0,x_pos(a1) (:89036-89037): the same draw, before the swap.
        final int chipX = ((motion.x + (xVel >> 4)) & 0xFFFF);
        // addi.w #$20,y_pos(a1) (:89023).
        final int chipY = (motion.y + 0x20) & 0xFFFF;
        // swap d0 / andi.w #$1FF,d0 / addi.w #-$400,d0 (:89038-89041).
        final int yVel = (short) (((random >>> 16) & 0x1FF) - 0x400);
        final int xv = xVel;
        // AllocateObjectAfterCurrent (:89019).
        spawnChild(() -> new LrzRockDebrisInstance(chipX, chipY, xv, yVel));
    }

    /** {@code subq.b #1,anim_frame_timer(a0)} etc. (sonic3k.asm:88948-88957). */
    private void stepAnimation() {
        animTimer = (byte) (animTimer - 1);
        if (animTimer >= 0) {
            return;
        }
        animTimer = ANIM_RELOAD;
        mappingFrame++;
        if (mappingFrame >= ANIM_FRAMES) {
            mappingFrame = 0;
        }
    }

    private int levelFrameCounter(int fallback) {
        return services().levelManager() != null
                ? services().levelManager().getFrameCounter()
                : fallback;
    }

    /** {@code move.b (Level_frame_counter+1).w,d0}: the low byte of the level clock. */
    private int levelFrameCounterLowByte(int fallback) {
        return levelFrameCounter(fallback) & 0xFF;
    }

    private void playSfx(int id) {
        try {
            services().playSfx(id);
        } catch (Exception e) {
            // Headless unit contexts have no audio service; the routine's behaviour is the motion.
        }
    }

    // ===== Lifetime =====

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    /**
     * {@code loc_1B666} tests {@code $44(a0)}, the PLACED X, not the swung one (:89017-89018,
     * :88880-88881), and the rolling boulder with {@code $30} set skips the test entirely
     * (:88892, :88966-88971).
     */
    @Override
    public int getOutOfRangeReferenceX() {
        return baseX;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        if (routine == Routine.ROLL && armed) {
            return false;
        }
        // andi.w #$FF80,d0 / sub.w (Camera_X_pos_coarse_back).w,d0 / cmpi.w #$280,d0 / bhi.
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

    /** ROM {@code angle(a0)}. */
    public int angle() {
        return angle & 0xFF;
    }

    /** ROM {@code $30(a0)}. */
    public boolean armed() {
        return armed;
    }

    /** True once word 0 holds {@code loc_4389E}. */
    public boolean rolling() {
        return routine == Routine.ROLL;
    }

    /** ROM {@code status(a0)} bit 1. */
    public boolean airborne() {
        return airborne;
    }

    /** ROM {@code x_vel(a0)}. */
    public int xVel() {
        return (short) motion.xVel;
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
        return RADIUS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return RADIUS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_SPIKE_BALL);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
