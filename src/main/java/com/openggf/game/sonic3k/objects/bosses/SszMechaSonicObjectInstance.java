package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.game.sonic3k.objects.SongFadeTransitionInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ROM {@code Obj_SSZEndBoss} (sonic3k.asm:164162-164490), act 1: Mecha Sonic, the third and last
 * of Sky Sanctuary act 1's fights and the only one that is a character rather than a ship.
 *
 * <p><b>It is not placed and not spawned by the zone event.</b> The {@code $79} pad at
 * {@code ($1A40,$670)} allocates it: {@code loc_45A84} waits for
 * {@code Camera_Y_pos == Camera_max_Y_pos} — the final arena's lock easing the camera down to
 * {@code $5C0} — and then a <b>plain</b> {@code AllocateObject}, so the new slot may be below this
 * one and may not run in the same frame. The pad keeps the slot in {@code $30(a0)} and writes it
 * to {@code _unkFAA4}, and {@code loc_45AB0} explodes the pad once the boss has run past it.
 *
 * <p><b>Every dispatch is four calls, not one.</b> {@code Obj_SSZEndBoss} runs the routine, then
 * {@code sub_7D312} (the hit and flash), then {@code sub_7D2D8} (the collision byte), then
 * {@code Perform_DPLC} over {@code DPLCPtr_MechaSonic}. The art is a per-frame DPLC exactly as a
 * player's is, which is why it is registered as {@code ArtUnc_MechaSonic} plus
 * {@code DPLC_MechaSonic} rather than as a flat sheet.
 *
 * <p><b>The collision byte is a table lookup, not a state.</b> {@code sub_7D2D8} indexes
 * {@code byte_7D2FC} by {@code mapping_frame} every frame the boss is not flashing, so which
 * frames hurt, which are attackable and which are harmless is a property of the animation and
 * nothing else. {@code $38} bit 7 ORs {@code $80} into it, which act 2 uses.
 *
 * <p><b>The hit handler is shared with act 2.</b> {@code sub_7D312} runs only while
 * {@code collision_flags} is zero — which is what the touch pass leaves behind after a hit — so
 * it is the hit that opens the {@code $20}-frame window, sets {@code status} bit 6 and plays
 * {@code sfx_BossHit}. {@code sub_7D2D8} then leaves the byte alone while bit 6 stands, so the
 * boss is uncollidable for the whole window. At {@code collision_property} zero it jumps
 * {@code (a4)}, which for act 1 is {@code sub_7D35A}.
 *
 * <p><b>The act-1 graph</b>, from {@code SSZEndBoss_Index} (routine byte is the table's own byte
 * offset, so the values are {@code 0,2,4,…,$28}):
 * <ol start="0">
 *   <li>{@code loc_7B2DC} init. {@code collision_property 8}, {@code RNG_seed = V_int_run_count},
 *       and for act 1 {@code loc_7B308}: routine 4, frame 2, {@code x_vel -$800}, the
 *       {@code ChildObjDat_7D47A} trail, and the box — {@code _unkFAB4 = Camera_X + $20},
 *       {@code _unkFAB6 = +$120}, {@code x_pos = +$160}, {@code _unkFAB0 = Camera_Y + $30},
 *       {@code y_pos = +$A0}.</li>
 *   <li>routine 4 {@code loc_7B3E6}: run left off the screen until {@code Camera_X - $20} has
 *       caught {@code x_pos}, then routine 6 and {@code $2E = $3F}.</li>
 *   <li>routine 6 {@code loc_7B416}: {@code Obj_Wait}, then {@code loc_7B41C} turns it round —
 *       {@code y_pos = Camera_Y + $40}, {@code x_vel $400}, frame 3, run animation.</li>
 *   <li>routine 8 {@code loc_7B44A}: run right until {@code _unkFAB6}, then {@code loc_7B462}'s
 *       {@code $1F}-frame pause, which is the one every attack returns to.</li>
 *   <li>{@code loc_7B484} picks the landing on {@code Random_Number}'s sign and on
 *       {@code _unkFAB8} bit 4, drops through routine {@code $C} and lands at {@code loc_7B4EC}
 *       with {@code sfx_MechaLand}.</li>
 *   <li>Then one of two openings: {@code loc_7B544}'s dash ({@code x_vel $820},
 *       {@code $40 = -$20} decelerating into {@code loc_7D216}'s limit) or {@code loc_7B57A}'s
 *       turn-and-face, which both end at {@code loc_7B5E8}'s jump. That jump reads
 *       {@code byte_7B62E[$3B & 7]} and takes routine {@code $16}, {@code $1A} or {@code $1E} —
 *       the air dash, the ground pound, or the landing that spawns a
 *       {@code ChildObjDat_7D480} child and then dashes twice.</li>
 * </ol>
 * The sequence in {@code byte_7B62E} is {@code 0,1,2,0,1,2,0,1}: it is a fixed eight-entry cycle
 * on its own counter, not a random choice, and only {@code loc_7B484}'s landing consults the RNG.
 *
 * <p><b>Still owed on this class</b>, recorded in {@code docs/status/s3k-known-bugs.md} rather
 * than faked: the {@code loc_7C9BA} child, {@code Obj_MechaSonic_Sparks}, and the post-defeat
 * graph at {@code loc_7B81A} beyond {@code sub_7D35A}'s immediate writes.
 */
public final class SszMechaSonicObjectInstance extends AbstractBossInstance
        implements SpawnRewindRecreatable {

    // --- ObjSlot_MechaSonic ------------------------------------------------------------------

    /** {@code move.b #8,collision_property(a0)} in {@code loc_7B2DC}. */
    public static final int HIT_COUNT = 8;
    /** {@code ObjSlot_MechaSonic}: {@code dc.w $280}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x280);
    /**
     * {@code dc.b $20,$20,0,$23}. {@code SetUp_ObjAttributesSlotted} consumes these as
     * {@code width_pixels}, {@code height_pixels}, {@code mapping_frame} and
     * {@code collision_flags} — <b>not</b> as radii. {@code y_radius} is a different SST field
     * that this routine never touches; only {@code loc_7B484} ({@code $1F}) and
     * {@code loc_7B5E8} ({@code $F}) write it, and until the first of those it is whatever the
     * cleared slot left, which is zero.
     */
    public static final int HALF_WIDTH = 0x20;
    public static final int HALF_HEIGHT = 0x20;

    // --- loc_7B308 ---------------------------------------------------------------------------

    /** {@code addi.w #$20,d0} / {@code move.w d0,(_unkFAB4).w}. */
    public static final int BOX_LEFT_CAMERA_OFFSET = 0x20;
    /** {@code addi.w #$100,d0}: {@code _unkFAB6} is another {@code $100} on. */
    public static final int BOX_RIGHT_CAMERA_OFFSET = 0x120;
    /** {@code addi.w #$40,d0} / {@code move.w d0,x_pos(a0)}. */
    public static final int SPAWN_CAMERA_X_OFFSET = 0x160;
    /** {@code addi.w #$30,d0} / {@code move.w d0,(_unkFAB0).w}. */
    public static final int CEILING_CAMERA_Y_OFFSET = 0x30;
    /** {@code addi.w #$70,d0} / {@code move.w d0,y_pos(a0)}. */
    public static final int SPAWN_CAMERA_Y_OFFSET = 0xA0;
    /** {@code move.w #-$800,x_vel(a0)}. */
    public static final int ENTRY_X_VEL = -0x800;
    /** {@code move.b #2,mapping_frame(a0)}. */
    public static final int ENTRY_MAPPING_FRAME = 2;

    /** {@code subi.w #$20,d0} in {@code loc_7B3E6}. */
    private static final int OFFSCREEN_LEFT_MARGIN = 0x20;
    /** {@code move.w #$3F,$2E(a0)}. */
    private static final int OFFSCREEN_WAIT = 0x3F;
    /** {@code addi.w #$40,d0} in {@code loc_7B41C}. */
    private static final int RETURN_CAMERA_Y_OFFSET = 0x40;
    /** {@code move.w #$400,x_vel(a0)}. */
    private static final int RETURN_X_VEL = 0x400;
    /** {@code move.b #3,mapping_frame(a0)}. */
    private static final int RETURN_MAPPING_FRAME = 3;
    /** {@code move.w #$1F,$2E(a0)} in {@code loc_7B462}: the pause between attacks. */
    public static final int ATTACK_PAUSE = 0x1F;
    /** {@code move.b #$1F,y_radius(a0)} in {@code loc_7B484}. */
    private static final int FALLING_Y_RADIUS = 0x1F;
    /** {@code move.b #$F,y_radius(a0)} in {@code loc_7B5E8}. */
    private static final int JUMPING_Y_RADIUS = 0x0F;
    /** {@code move.w #$820,d0} / {@code move.w #-$20,d1} in {@code loc_7B544}. */
    private static final int GROUND_DASH_X_VEL = 0x820;
    private static final int DASH_DECELERATION = -0x20;
    /** {@code addi.w #$A0,d0} in {@code loc_7B57A}: the screen centre, in the ROM's own frame. */
    private static final int FACING_CAMERA_X_OFFSET = 0xA0;
    /** {@code move.w #$200,d0} / {@code move.w #-$600,y_vel(a0)} in {@code loc_7B5E8}. */
    private static final int JUMP_X_VEL = 0x200;
    private static final int JUMP_Y_VEL = -0x600;
    /** {@code move.w #$780,d0} in {@code loc_7B64E}. */
    private static final int AIR_DASH_X_VEL = 0x780;
    /** {@code move.w #-$900,y_vel(a0)} in {@code loc_7B6C0}. */
    private static final int BOUNCE_Y_VEL = -0x900;
    /** {@code move.w #$F,$2E(a0)} in {@code loc_7B70E} and {@code loc_7B7D6}. */
    private static final int LANDING_PAUSE = 0x0F;
    /** {@code move.w #$100,d0} / {@code move.w #7,$2E(a0)} in {@code loc_7B754}. */
    private static final int BACKSTEP_X_VEL = 0x100;
    private static final int BACKSTEP_FRAMES = 7;
    /** {@code move.w #$640,d0} in {@code loc_7B790} and {@code move.w #-$640,y_vel} in 7B7EC. */
    private static final int FINAL_DASH_X_VEL = 0x640;
    private static final int FINAL_HOP_Y_VEL = -0x640;
    /** {@code moveq #$20,d1} in {@code MoveSprite_LightGravity}. */
    private static final int LIGHT_GRAVITY = 0x20;
    /** {@code MoveSprite}'s own {@code $38}. */
    private static final int NORMAL_GRAVITY = 0x38;

    /** {@code byte_7B62E}: the eight-entry cycle {@code loc_7B5E8} steps with {@code $3B(a0)}. */
    public static final int[] ATTACK_CYCLE = {0, 1, 2, 0, 1, 2, 0, 1};
    /** {@code byte_7B636}: routine {@code $16}, {@code $1A} or {@code $1E}. */
    public static final int[] ATTACK_ROUTINES = {0x16, 0x1A, 0x1E};

    /**
     * {@code byte_7D2FC}, indexed by {@code mapping_frame}: the collision byte {@code sub_7D2D8}
     * writes every frame the boss is not flashing. Twenty-two entries, written out as literals
     * rather than recomputed, so a change in the production lookup cannot pass this silently.
     */
    public static final int[] COLLISION_BY_FRAME = {
            0x23, 0x23, 0x09, 0x86, 0x86, 0x86, 0x86, 0x1A,
            0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x09, 0x00,
            0x09, 0x23, 0x06, 0x23, 0x23, 0x23,
    };

    /** {@code move.b #$20,$20(a0)} in {@code sub_7D312}. */
    public static final int FLASH_WINDOW = 0x20;
    /** {@code move.w #$7F,$2E(a0)} in {@code sub_7D35A}. */
    public static final int DEFEAT_WAIT = 0x7F;
    /** {@code move.b #$E,mapping_frame(a0)} in {@code loc_7B858}. */
    public static final int DEFEAT_MAPPING_FRAME = 0x0E;
    /** {@code move.b #$1F,y_radius(a0)} in {@code loc_7B858}. */
    private static final int DEFEAT_FALL_Y_RADIUS = 0x1F;
    /** {@code move.b #$23,y_radius(a0)} in {@code loc_7B888}. */
    private static final int DEFEAT_LANDED_Y_RADIUS = 0x23;
    /** {@code move.w #(2*60)-1,$2E(a0)} in {@code loc_7B888}. */
    public static final int DEFEAT_LANDED_WAIT = (2 * 60) - 1;
    /** {@code bset #5,$38(a0)} in {@code loc_7B888}. */
    private static final int FLAG_DEFEAT_LANDED = 5;

    // --- $38(a0) bits ------------------------------------------------------------------------

    /** {@code bset #2,$38(a0)}: the trail children draw only while this stands. */
    private static final int FLAG_TRAIL = 2;
    /** {@code bset #3,$38(a0)} in {@code loc_7B4CA}: the longer of the two landing animations. */
    private static final int FLAG_LONG_LANDING = 3;
    /** {@code btst #7,$38(a0)} in {@code sub_7D2D8}: ORs {@code $80} into the collision byte. */
    private static final int FLAG_COLLISION_HIGH_BIT = 7;

    /** {@code btst #4,(_unkFAB8).w}, shared with the cutscene word. */
    private static final int CUTSCENE_BIT_LANDING = 4;

    /** The {@code $34(a0)} callbacks this act's graph installs. */
    private enum Callback {
        NONE,
        /** {@code loc_7B41C}. */ RETURN,
        /** {@code loc_7B484}. */ CHOOSE_LANDING,
        /** {@code loc_7B4EC}. */ LANDED,
        /** {@code loc_7B544}. */ GROUND_DASH,
        /** {@code loc_7B57A}. */ FACE_PLAYER,
        /** {@code loc_7B5E8}. */ JUMP,
        /** {@code loc_7B754}. */ BACKSTEP,
        /** {@code loc_7B790}. */ FINAL_DASH,
        /** {@code loc_7B7EC}. */ FINAL_HOP,
        /** {@code loc_7B858}, installed by {@code sub_7D35A}. */ DEFEAT_FALL,
        /** {@code loc_7B888}. */ DEFEAT_LANDED,
    }

    // --- state -------------------------------------------------------------------------------

    /** {@code x_pos}/{@code y_pos} as the ROM keeps them: 16.16, velocity shifted left eight. */
    private int posX;
    private int posY;
    private int xVel;
    private int yVel;
    /** {@code $40(a0)}: the per-frame addition to {@code x_vel} during a dash. */
    private int xAccel;
    /** {@code $2E(a0)}. */
    private int timer;
    /** {@code $3A(a0)}: which of {@code byte_7B636}'s three attacks was selected. */
    private int attackChoice;
    /** {@code $3B(a0)}: the cycle counter {@code loc_7B5E8} steps. */
    private int attackCounter;
    /** {@code y_radius(a0)}: used by the floor checks only, and zero until loc_7B484. */
    private int yRadius;
    /** {@code render_flags} bit 0. */
    private boolean renderFlipped;
    /** {@code $38(a0)}. */
    private int flags;
    private Callback callback = Callback.NONE;
    private boolean initExecuted;
    private boolean defeated;
    private boolean launchDeletePending;
    public void deleteOnNextObjectPass() { launchDeletePending = true; }
    /** {@code PalLoad_Line1} is a one-shot; the restore belongs to the owed defeat graph. */
    private boolean paletteLoaded;

    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();
    /** A lazily sliced read-only window over the ROM's script block; nothing to restore. */
    private transient S3kRawAnimation animator;

    private final List<SszMechaSonicTrailChild> trail = new ArrayList<>();

    public SszMechaSonicObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZEndBoss");
        posX = (spawn.x() & 0xFFFF) << 16;
        posY = (spawn.y() & 0xFFFF) << 16;
    }

    // --- rewind ------------------------------------------------------------------------------

    private record RewindExtra(List<ObjectRefId> trailIds, int posX, int posY, int xVel, int yVel,
                               int xAccel, int timer, int attackChoice, int attackCounter,
                               int yRadius, boolean renderFlipped, int flags,
                               int callback, boolean initExecuted, boolean defeated, boolean paletteLoaded,
                               int animScript, int animFrame, int animFrameTimer,
                               int mappingFrame, int routine, int hitCount, boolean invulnerable,
                               int invulnerabilityTimer, boolean launchDeletePending)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        var table = context.identityTable();
        List<ObjectRefId> trailIds = new ArrayList<>();
        for (SszMechaSonicTrailChild child : trail) {
            trailIds.add(table.map(t -> t.encodeObject(child)).orElse(null));
        }
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                List.copyOf(trailIds), posX, posY, xVel, yVel, xAccel, timer, attackChoice,
                attackCounter, yRadius, renderFlipped, flags, callback.ordinal(),
                initExecuted, defeated, paletteLoaded, anim.script, anim.animFrame, anim.animFrameTimer,
                anim.mappingFrame, super.state.routine, super.state.hitCount,
                super.state.invulnerable, super.state.invulnerabilityTimer, launchDeletePending));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (!(snapshot.objectSubclassExtra() instanceof RewindExtra extra)) {
            return;
        }
        posX = extra.posX();
        posY = extra.posY();
        xVel = extra.xVel();
        yVel = extra.yVel();
        xAccel = extra.xAccel();
        timer = extra.timer();
        attackChoice = extra.attackChoice();
        attackCounter = extra.attackCounter();
        yRadius = extra.yRadius();
        renderFlipped = extra.renderFlipped();
        flags = extra.flags();
        callback = Callback.values()[extra.callback()];
        initExecuted = extra.initExecuted();
        defeated = extra.defeated();
        launchDeletePending = extra.launchDeletePending();
        paletteLoaded = extra.paletteLoaded();
        anim.script = extra.animScript();
        anim.animFrame = extra.animFrame();
        anim.animFrameTimer = extra.animFrameTimer();
        anim.mappingFrame = extra.mappingFrame();
        super.state.routine = extra.routine();
        super.state.hitCount = extra.hitCount();
        super.state.invulnerable = extra.invulnerable();
        super.state.invulnerabilityTimer = extra.invulnerabilityTimer();
        trail.clear();
        for (ObjectRefId id : extra.trailIds()) {
            Object resolved = id == null
                    ? null : context.requireIdentityTable().resolveObject(id, true);
            if (resolved instanceof SszMechaSonicTrailChild child) {
                trail.add(child);
            }
        }
    }

    // --- AbstractBossInstance contract --------------------------------------------------------

    @Override
    protected void initializeBossState() {
        super.state.routine = 0;
        super.state.hitCount = HIT_COUNT;
    }

    @Override protected int getInitialHitCount() { return HIT_COUNT; }

    /** {@code sub_7D2D8} rewrites the byte every frame; the base's size index is unused here. */
    @Override protected int getCollisionSizeIndex() { return 0; }

    @Override protected void onHitTaken(int remainingHits) { }

    /** {@code sub_7D312} is its own window, its own sound and its own flash. */
    @Override protected boolean usesBaseHitHandler() { return false; }

    @Override protected boolean usesDefeatSequencer() { return false; }

    /**
     * {@code sub_7D312} runs at the <em>tail</em> of {@code Obj_SSZEndBoss}'s own dispatch, after
     * the routine has already run, so the frame that reaches zero hits selects {@code loc_7B81A}
     * and writes {@code $2E = $7F} and stops — {@code loc_7B852}'s first {@code Obj_Wait} is the
     * next frame. Without the deferral the engine's touch pass, which runs before this slot,
     * would spend a decrement on the killing frame itself.
     *
     * <p>The native {@code hpz_3} segment settles it, comparison only: the slot takes
     * {@code loc_7B81A} on frame 1310 and routine 2 on 1438. That is 128 dispatches, which is
     * {@code $7F} decremented to negative starting at 1311 — the deferred shape. Starting at
     * 1310 would have reached routine 2 on 1437.
     */
    @Override protected boolean defeatDeferralAppliesToThisBoss() { return true; }


    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }

    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }

    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }

    @Override public int getOnScreenHalfWidth() { return HALF_WIDTH; }

    /** {@code height_pixels}, a constant {@code $20}; the cull box is not {@code y_radius}. */
    @Override public int getOnScreenHalfHeight() { return HALF_HEIGHT; }

    /** Nothing in this chain is an {@code Obj_WaitOffscreen}: routine 4 runs off screen on purpose. */
    @Override public boolean isPersistent() { return true; }

    @Override public int getX() { return (posX >>> 16) & 0xFFFF; }

    @Override public int getY() { return (posY >>> 16) & 0xFFFF; }

    /**
     * {@code Touch_Enemy} clears {@code collision_flags(a0)} on a hit and {@code sub_7D2D8} does
     * not write it back while {@code status} bit 6 stands, so the boss really is uncollidable —
     * not merely invulnerable — for the whole {@code $20}-frame window.
     */
    @Override
    public int getCollisionFlags() {
        if (super.state.invulnerable || super.state.defeated) {
            return 0;
        }
        return collisionFlags;
    }

    /** {@code move.b #$20,$20(a0)} in {@code sub_7D312}. */
    @Override protected int getInvulnerabilityDuration() { return FLASH_WINDOW; }

    /** {@code collision_flags(a0)}, as {@code sub_7D2D8} last wrote it. */
    private int collisionFlags = COLLISION_BY_FRAME[0];

    // --- update ------------------------------------------------------------------------------

    @Override
    protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
        if (launchDeletePending) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        // Obj_SSZEndBoss's tail is unconditional: the routine, then sub_7D312, then sub_7D2D8,
        // then Perform_DPLC. The init is routine 0's body, not something that happens instead of
        // a dispatch, so the collision byte is written on that frame too -- loc_7B308 has just
        // set mapping_frame 2, and byte_7D2FC[2] is $09 where the slot table left $23.
        if (!initExecuted) {
            runInit(vIntRunCount);
            initExecuted = true;
            applyHitAndFlash();
            applyCollisionByte();
            return;
        }
        if (defeated) {
            // sub_7D35A swapped the object's code pointer to loc_7B81A, whose own tail is the
            // DPLC and Draw_Sprite -- no sub_7D312 and no sub_7D2D8, so the collision byte stops
            // being rewritten and status bit 6 stands for the rest of the act.
            dispatchDefeat();
            return;
        }
        dispatch();
        applyHitAndFlash();
        applyCollisionByte();
    }

    /** {@code loc_7B2DC} and, for act 1, {@code loc_7B308} and {@code loc_7B35A}. */
    private void runInit(int vIntRunCount) {
        // move.l (V_int_run_count).w,(RNG_seed).w. Declared clock seed: the ROM reseeds the
        // shared generator from the frame counter here, so the landing choice at loc_7B484 is a
        // function of when the fight started. The clock this reads is V_int_run_count, not
        // Level_frame_counter and not the executed-frame count.
        if (services().rng() != null) {
            services().rng().seedFromFrameCounter(vIntRunCount);
        }
        super.state.hitCount = HIT_COUNT;
        collisionFlags = COLLISION_BY_FRAME[0];

        SszZoneRuntimeState ssz = sszState();
        var camera = services().camera();
        // Camera bounds describe the native arena; a wider viewport moves only its
        // displayed left edge. Match SSZ's event gates and GHZ recreation framing.
        int focusExcess = camera == null ? 0 : Math.max(0,
                com.openggf.camera.DeadzoneGeometry.rightEdge(camera.getWidth())
                        - com.openggf.camera.DeadzoneGeometry.rightEdge(320));
        int cameraX = camera == null ? 0 : (camera.getX() + focusExcess) & 0xFFFF;
        int cameraY = camera == null ? 0 : camera.getY() & 0xFFFF;

        super.state.routine = 4;
        anim.mappingFrame = ENTRY_MAPPING_FRAME;
        xVel = ENTRY_X_VEL;
        flags |= 1 << FLAG_TRAIL;
        createTrail(2);
        if (ssz != null) {
            ssz.setBossLeftX((cameraX + BOX_LEFT_CAMERA_OFFSET) & 0xFFFF);
            ssz.setBossRightX((cameraX + BOX_RIGHT_CAMERA_OFFSET) & 0xFFFF);
            // _unkFAB0 is written here and read by nothing in act 1's graph; loc_7D216 only
            // ever consults the two X limits. It is kept because act 2 and the launch read it.
            ssz.setBossCeilingY((cameraY + CEILING_CAMERA_Y_OFFSET) & 0xFFFF);
        }
        setPosition((cameraX + SPAWN_CAMERA_X_OFFSET) & 0xFFFF,
                (cameraY + SPAWN_CAMERA_Y_OFFSET) & 0xFFFF);
        // loc_7B35A, in its own order: Queue_Kos_Module ArtKosM_MechaSonicExtra (the engine's
        // standalone sheet needs no queue), Load_PLC_Raw PLC_BossExplosion, and then
        // PalLoad_Line1 Pal_SSZGHZMisc -- which matters, because ObjSlot_MechaSonic draws this
        // object on line 1 and without the load he takes whatever the level left there.
        loadFightPalette();
        // Then the act-1 branch: AllocateObject / move.l #Obj_Song_Fade_Transition,(a1) /
        // move.b #mus_EndBoss,subtype(a1). The object's own init is move.w #90,$2E(a0) and a
        // cmd_FadeOut, so the theme arrives 91 updates later, not on this frame.
        spawnFreeChild(() -> new SongFadeTransitionInstance(90, Sonic3kMusic.BOSS.id));
    }

    /** {@code SSZEndBoss_Index}, act 1 entries only. */
    private void dispatch() {
        switch (super.state.routine) {
            case 4 -> runOffScreen();
            case 6 -> objWait();
            case 8 -> runBackAcrossTheArena();
            case 0x0A -> { animate(); objWait(); }
            case 0x0C -> fallToTheFloor();
            case 0x0E, 0x12, 0x14 -> animate();
            case 0x10 -> groundDash();
            case 0x16 -> jumpRise();
            case 0x18 -> airDash();
            case 0x1A -> groundPoundFall();
            case 0x1C -> groundPoundBounce();
            case 0x1E -> slamFall();
            case 0x20, 0x26 -> { animate(); objWait(); }
            case 0x22 -> { animate(); moveSprite2(); objWait(); }
            case 0x24 -> finalDash();
            case 0x28 -> finalHop();
            default -> { }
        }
    }

    /** {@code loc_7B3E6}: run left until the camera's left margin has passed {@code x_pos}. */
    private void runOffScreen() {
        moveSprite2();
        int cameraX = cameraX();
        if (((cameraX - OFFSCREEN_LEFT_MARGIN) & 0xFFFF) < getX()) {
            return;
        }
        super.state.routine = 6;
        flags &= ~(1 << FLAG_TRAIL);
        timer = OFFSCREEN_WAIT;
        callback = Callback.RETURN;
    }

    /** {@code loc_7B41C}. */
    private void onReturn() {
        super.state.routine = 8;
        renderFlipped = true;
        setPosition(getX(), (cameraY() + RETURN_CAMERA_Y_OFFSET) & 0xFFFF);
        xVel = RETURN_X_VEL;
        anim.mappingFrame = RETURN_MAPPING_FRAME;
        setAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_ENTRY_ADDR);
    }

    /** {@code loc_7B44A}. */
    private void runBackAcrossTheArena() {
        animate();
        moveSprite2();
        if (getX() >= boxRightX()) {
            enterAttackPause();
        }
    }

    /** {@code loc_7B462}: the pause every attack comes back to. */
    private void enterAttackPause() {
        super.state.routine = 0x0A;
        timer = ATTACK_PAUSE;
        callback = Callback.CHOOSE_LANDING;
    }

    /** {@code loc_7B484}. */
    private void onChooseLanding() {
        super.state.routine = 0x0C;
        yRadius = FALLING_Y_RADIUS;
        xVel = 0;
        yVel = 0;
        anim.animFrame = 0;
        anim.animFrameTimer = 0;
        callback = Callback.LANDED;
        int random = services().rng() == null ? 0 : services().rng().nextWord();
        SszZoneRuntimeState ssz = sszState();
        boolean longLanding = (random & 0x8000) != 0;
        if (!longLanding && ssz != null) {
            // bclr #4,(_unkFAB8).w sets Z from the OLD bit, so the first landing that draws a
            // positive number takes the short animation and clears the bit; a later one, with
            // the bit already clear, still takes the short branch. The bit is only ever set by
            // act 2's loc_7B3AC, so in act 1 this is the short branch on every positive draw.
            longLanding = ssz.clearCutsceneFlag(CUTSCENE_BIT_LANDING);
        }
        if (longLanding) {
            setAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_LAND_LONG_ADDR);
            flags |= 1 << FLAG_LONG_LANDING;
        } else {
            setAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_LAND_SHORT_ADDR);
            flags &= ~(1 << FLAG_LONG_LANDING);
        }
    }

    /** {@code loc_7B4DA}. */
    private void fallToTheFloor() {
        animate();
        moveSpriteWithGravity(LIGHT_GRAVITY);
        objHitFloorDoRoutine();
    }

    /** {@code loc_7B4EC}. */
    private void onLanded() {
        super.state.routine = 0x0E;
        posY &= 0xFFFF0000;
        anim.animFrame = 0;
        anim.animFrameTimer = 0;
        services().playSfx(Sonic3kSfx.MECHA_LAND.id);
        if ((flags & (1 << FLAG_LONG_LANDING)) != 0) {
            // loc_7B520: bchg #0,render_flags — the long landing turns round where the short one
            // keeps facing the way it came.
            renderFlipped = !renderFlipped;
            anim.mappingFrame = 1;
            setAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_TURN_ADDR);
            callback = Callback.FACE_PLAYER;
        } else {
            setAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_STAND_ADDR);
            callback = Callback.GROUND_DASH;
        }
    }

    /** {@code loc_7B544}. */
    private void onGroundDash() {
        super.state.routine = 0x10;
        int vel = GROUND_DASH_X_VEL;
        int accel = DASH_DECELERATION;
        if (renderFlipped) {
            vel = -vel;
            accel = -accel;
        }
        xVel = vel;
        xAccel = accel;
        yVel = 0;
        flags |= 1 << FLAG_TRAIL;
        // ChildObjDat_7D486 runs loc_7C8FE, which adds four to each subtype before loc_7C902.
        createTrail(2, 4);
    }

    /** {@code loc_7B5AC}. */
    private void groundDash() {
        // add.w d0,x_vel(a0): a word add, as MoveSprite's own y_vel gain is.
        xVel = (short) (xVel + xAccel);
        moveSprite2();
        Integer limit = boundaryReached();
        if (limit == null) {
            return;
        }
        // loc_7B5C2
        super.state.routine = 0x12;
        setPosition(limit, getY());
        posX &= 0xFFFF0000;
        flags &= ~(1 << FLAG_TRAIL);
        setAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_SKID_ADDR);
        callback = Callback.FACE_PLAYER;
    }

    /** {@code loc_7B57A}. */
    private void onFacePlayer() {
        super.state.routine = 0x14;
        setAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_RUN_ADDR);
        callback = Callback.JUMP;
        renderFlipped = ((cameraX() + FACING_CAMERA_X_OFFSET) & 0xFFFF) >= getX();
    }

    /** {@code loc_7B5E8}: the jump, and the eight-entry cycle that picks what follows it. */
    private void onJump() {
        yRadius = JUMPING_Y_RADIUS;
        setRawAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_JUMP_ADDR);
        xVel = renderFlipped ? JUMP_X_VEL : -JUMP_X_VEL;
        yVel = JUMP_Y_VEL;
        int index = attackCounter & 7;
        attackCounter = (attackCounter + 1) & 0xFF;
        attackChoice = ATTACK_CYCLE[index];
        super.state.routine = ATTACK_ROUTINES[attackChoice];
    }

    /** {@code loc_7B63A}. */
    private void jumpRise() {
        animate();
        moveSpriteWithGravity(NORMAL_GRAVITY);
        if (yVel < 0) {
            return;
        }
        // loc_7B64E
        super.state.routine = 0x18;
        int vel = AIR_DASH_X_VEL;
        int accel = DASH_DECELERATION;
        if (!renderFlipped) {
            vel = -vel;
            accel = -accel;
        }
        xVel = vel;
        xAccel = accel;
        yVel = 0;
        services().playSfx(Sonic3kSfx.DASH.id);
    }

    /** {@code loc_7B67C}. */
    private void airDash() {
        animate();
        // add.w d0,x_vel(a0): a word add, as MoveSprite's own y_vel gain is.
        xVel = (short) (xVel + xAccel);
        moveSprite2();
        Integer limit = boundaryReached();
        if (limit == null) {
            return;
        }
        // loc_7B698
        setPosition(limit, getY());
        enterAttackPause();
    }

    /** {@code loc_7B6A0}. */
    private void groundPoundFall() {
        animate();
        moveSpriteWithGravity(NORMAL_GRAVITY);
        if (yVel < 0) {
            return;
        }
        Integer floor = floorDistance();
        if (floor == null || floor > 0) {
            return;
        }
        // loc_7B6C0
        setPosition(getX(), (getY() + floor) & 0xFFFF);
        super.state.routine = 0x1C;
        yVel = BOUNCE_Y_VEL;
        services().playSfx(Sonic3kSfx.THUMP.id);
    }

    /** {@code loc_7B6DA}. */
    private void groundPoundBounce() {
        animate();
        moveSpriteWithGravity(NORMAL_GRAVITY);
        if (boundaryReached() != null) {
            enterAttackPause();
        }
    }

    /** {@code loc_7B6EE}. */
    private void slamFall() {
        animate();
        moveSpriteWithGravity(NORMAL_GRAVITY);
        if (yVel < 0) {
            return;
        }
        Integer floor = floorDistance();
        if (floor == null || floor > 0) {
            return;
        }
        // loc_7B70E
        setPosition(getX(), (getY() + floor) & 0xFFFF);
        super.state.routine = 0x20;
        timer = LANDING_PAUSE;
        callback = Callback.BACKSTEP;
        flags |= 1 << FLAG_TRAIL;
        services().playSfx(Sonic3kSfx.MECHA_LAND.id);
        // move.b #8,subtype(a1): ChildObjDat_7D480's single child is loc_7C902 with subtype 8,
        // which sub_7D236 reads as byte_7D24C's fifth row.
        createTrail(1, 8);
    }

    /** {@code loc_7B754}. */
    private void onBackstep() {
        super.state.routine = 0x22;
        xVel = xVel < 0 ? BACKSTEP_X_VEL : -BACKSTEP_X_VEL;
        yVel = 0;
        timer = BACKSTEP_FRAMES;
        callback = Callback.FINAL_DASH;
    }

    /** {@code loc_7B790}. */
    private void onFinalDash() {
        super.state.routine = 0x24;
        int vel = FINAL_DASH_X_VEL;
        int accel = DASH_DECELERATION;
        if (xVel >= 0) {
            vel = -vel;
            accel = -accel;
        }
        xVel = vel;
        xAccel = accel;
        services().playSfx(Sonic3kSfx.DASH.id);
    }

    /** {@code loc_7B7BA}. */
    private void finalDash() {
        animate();
        // add.w d0,x_vel(a0): a word add, as MoveSprite's own y_vel gain is.
        xVel = (short) (xVel + xAccel);
        moveSprite2();
        if (boundaryReached() == null) {
            return;
        }
        // loc_7B7D6
        super.state.routine = 0x26;
        timer = LANDING_PAUSE;
        callback = Callback.FINAL_HOP;
    }

    /** {@code loc_7B7EC}. */
    private void onFinalHop() {
        super.state.routine = 0x28;
        flags &= ~(1 << FLAG_TRAIL);
        xVel = 0;
        yVel = FINAL_HOP_Y_VEL;
    }

    /** {@code loc_7B804}. */
    private void finalHop() {
        animate();
        moveSpriteWithGravity(NORMAL_GRAVITY);
        if (yVel >= 0) {
            enterAttackPause();
        }
    }

    // --- shared mechanics ---------------------------------------------------------------------

    /**
     * {@code sub_7D312}'s window. The hit itself — {@code collision_property} down one,
     * {@code sfx_BossHit}, {@code status} bit 6 — is the shared touch pass, which is where the
     * ROM's {@code Touch_Enemy} does it too; what belongs here is the {@code $20(a0)} countdown
     * and the {@code bclr #6,status(a0)} that ends it, because {@code sub_7D2D8} is what would
     * otherwise put the collision byte straight back.
     */
    private void applyHitAndFlash() {
        if (!super.state.invulnerable) {
            return;
        }
        super.state.invulnerabilityTimer--;
        if (super.state.invulnerabilityTimer <= 0) {
            super.state.invulnerabilityTimer = 0;
            super.state.invulnerable = false;
        }
    }

    /** {@code sub_7D2D8}. */
    private void applyCollisionByte() {
        if (super.state.invulnerable) {
            return;
        }
        int frame = anim.mappingFrame;
        if (frame < 0 || frame >= COLLISION_BY_FRAME.length) {
            return;
        }
        int value = COLLISION_BY_FRAME[frame];
        if ((flags & (1 << FLAG_COLLISION_HIGH_BIT)) != 0) {
            value |= 0x80;
        }
        collisionFlags = value;
    }

    /**
     * {@code sub_7D35A}, the act-1 {@code (a4)}: install {@code loc_7B81A}, routine 0,
     * {@code $34 = loc_7B858}, {@code status} bit 6, {@code $2E = $7F}, 1,000 displayed points and a
     * {@code Child6_CreateBossExplosion} child with subtype 4.
     *
     * <p>The score is the base's, because {@code commitDefeat} adds 1,000 displayed points
     * before calling this. The rest of {@code loc_7B81A}'s graph — the fall at {@code loc_7B87C},
     * {@code loc_7B888}'s landing and {@code loc_7D056}'s act handover — is slice 7's remaining
     * work and is recorded in {@code docs/status/s3k-known-bugs.md} rather than approximated.
     */
    @Override
    protected void onDefeatStarted() {
        defeated = true;
        // move.l #loc_7B81A,(a0) / clr.b routine(a0) / move.l #loc_7B858,$34(a0)
        super.state.routine = 0;
        // bset #6,status(a0), and move.w #$7F,$2E(a0)
        super.state.invulnerable = true;
        timer = DEFEAT_WAIT;
        callback = Callback.DEFEAT_FALL;
        // lea (Child6_CreateBossExplosion).l,a2 / CreateChild1_Normal, subtype 4. The score is
        // the base's, added before this runs. Nothing here clears x_vel, y_vel or the trail bit.
        spawnDefeatExplosion();
    }

    /**
     * {@code off_7B838}, act-1 entries only. Routine 0 is {@code loc_7B852}'s {@code Obj_Wait},
     * routine 2 is {@code loc_7B87C}'s fall, and routine 4 is {@code loc_7B984}, which waits out
     * its own {@code $2E} while {@code loc_7D056} runs the act's handover beside it.
     *
     * <p>Dated against the native {@code hpz_3} segment of
     * {@code s3k-sonic-tails-complete-emeralds}, comparison only: the slot takes
     * {@code loc_7B81A} on frame 1310, routine 2 on 1438 — {@code $7F + 1}, an {@code Obj_Wait}
     * completing on update N+1 — and routine 4 on 1439, one frame later, because at
     * {@code y $660} with {@code loc_7B858}'s {@code $1F} radius his feet are already below the
     * {@code $67C} floor and {@code ObjHitFloor_DoRoutine} lands him on its first dispatch.
     */
    private void dispatchDefeat() {
        switch (super.state.routine) {
            case 0 -> objWait();
            case 2 -> {
                moveSpriteWithGravity(LIGHT_GRAVITY);
                objHitFloorDoRoutine();
            }
            // loc_7B984: Run_PalRotationScript, then the animation, then Obj_Wait. The palette
            // rotation sub_7C678 sets up over word_7D842 is owed with the spark child it feeds.
            case 4 -> { animate(); objWait(); }
            default -> { }
        }
    }

    /** {@code loc_7B858}. */
    private void onDefeatFall() {
        super.state.routine = 2;
        anim.mappingFrame = DEFEAT_MAPPING_FRAME;
        yRadius = DEFEAT_FALL_Y_RADIUS;
        xVel = 0;
        yVel = 0;
        callback = Callback.DEFEAT_LANDED;
    }

    /**
     * {@code loc_7B888}, act-1 branch. {@code sub_7C678}'s palette rotation over
     * {@code word_7D842} and {@code ChildObjDat_7D48C}'s {@code Obj_MechaSonic_Sparks} are owed
     * together — the sparks' own gate is a read of the rotating colour
     * ({@code cmpi.w #$E88,(Normal_palette_line_2+$12).w}), so neither is useful without the
     * other. Recorded in {@code docs/status/s3k-known-bugs.md} rather than half-built.
     */
    private void onDefeatLanded() {
        yRadius = DEFEAT_LANDED_Y_RADIUS;
        setRawAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_DEFEATED_ADDR);
        services().playSfx(Sonic3kSfx.MECHA_LAND.id);
        super.state.routine = 4;
        // st (_unkFAA8).w: Check_TailsEndPose reads it, and loc_7D078 waits for it to clear.
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null) {
            ssz.setMechaSonicBeaten(true);
        }
        flags |= 1 << FLAG_DEFEAT_LANDED;
        flags &= ~(1 << FLAG_LONG_LANDING);
        timer = DEFEAT_LANDED_WAIT;
        callback = Callback.NONE;
        // jsr (AllocateObject).l / move.l #loc_7D056,(a1): the act's own handover, beside the
        // boss rather than owned by it.
        spawnFreeChild(() -> new SszMechaSonicActEndObjectInstance(
                new ObjectSpawn(getX(), getY(), 0, 0, 0, false, 0)));
    }

    /** {@code Obj_Wait}: {@code subq.w #1,$2E(a0)} and, when it goes negative, {@code jmp $34}. */
    private void objWait() {
        timer--;
        if (timer >= 0) {
            return;
        }
        runCallback();
    }

    private void runCallback() {
        Callback pending = callback;
        switch (pending) {
            case RETURN -> onReturn();
            case CHOOSE_LANDING -> onChooseLanding();
            case LANDED -> onLanded();
            case GROUND_DASH -> onGroundDash();
            case FACE_PLAYER -> onFacePlayer();
            case JUMP -> onJump();
            case BACKSTEP -> onBackstep();
            case FINAL_DASH -> onFinalDash();
            case FINAL_HOP -> onFinalHop();
            case DEFEAT_FALL -> onDefeatFall();
            case DEFEAT_LANDED -> onDefeatLanded();
            case NONE -> { }
        }
    }

    /** {@code MoveSprite2}. */
    private void moveSprite2() {
        posX += xVel << 8;
        posY += yVel << 8;
        syncBaseState();
    }

    /** {@code MoveSprite_CustomGravity}: the position moves first, then {@code y_vel} gains d1. */
    private void moveSpriteWithGravity(int gravity) {
        posX += xVel << 8;
        int before = yVel;
        yVel = (short) (yVel + gravity);
        posY += before << 8;
        syncBaseState();
    }

    /** {@code ObjHitFloor_DoRoutine}. */
    private void objHitFloorDoRoutine() {
        if (yVel < 0) {
            return;
        }
        Integer floor = floorDistance();
        if (floor == null || floor > 0) {
            return;
        }
        setPosition(getX(), (getY() + floor) & 0xFFFF);
        runCallback();
    }

    /** {@code ObjCheckFloorDist}: null when no surface was found at all. */
    private Integer floorDistance() {
        TerrainCheckResult floor =
                ObjectTerrainUtils.checkFloorDist(getX(), getY(), yRadius);
        return floor.foundSurface() ? floor.distance() : null;
    }

    /**
     * {@code loc_7D216}: the boss turns at whichever of {@code _unkFAB4}/{@code _unkFAB6} its
     * {@code x_vel} is heading for, and the caller is handed that limit in d0.
     */
    private Integer boundaryReached() {
        if (xVel >= 0) {
            int right = boxRightX();
            return right <= getX() ? right : null;
        }
        int left = boxLeftX();
        return left >= getX() ? left : null;
    }

    /** {@code Animate_RawMultiDelay}; the {@code $F4} command calls {@code $34(a0)}. */
    private void animate() {
        S3kRawAnimation script = animator();
        if (script == null || anim.script == 0) {
            return;
        }
        script.animateMultiDelay(anim, this::runCallback);
    }

    /**
     * {@code move.l #script,$30(a0)} on its own. The ROM writes the pointer without touching
     * {@code anim_frame} or {@code anim_frame_timer}, so a script installed this way resumes at
     * whatever index the previous one left behind. The act-1 graph does it seven times —
     * {@code loc_7B41C}, {@code loc_7B484}'s two branches, {@code loc_7B4EC},
     * {@code loc_7B520}, {@code loc_7B57A} and {@code loc_7B5C2} — against one
     * {@code Set_Raw_Animation}, at {@code loc_7B5E8}, which is the only one that clears them.
     * (The {@code $F8} command rewrites {@code $30} again at run time, from inside the script.)
     */
    private void setAnimation(int address) {
        anim.script = address;
    }

    /** {@code Set_Raw_Animation}: the pointer, and both counters cleared. */
    private void setRawAnimation(int address) {
        anim.script = address;
        anim.animFrame = 0;
        anim.animFrameTimer = 0;
    }

    private S3kRawAnimation animator() {
        if (animator != null) {
            return animator;
        }
        try {
            animator = S3kRawAnimation.load(services().romReader(),
                    Sonic3kConstants.SSZ_MECHA_ANIM_BLOCK_ADDR,
                    Sonic3kConstants.SSZ_MECHA_ANIM_BLOCK_SIZE);
        } catch (IOException | RuntimeException ignored) {
            // Partial harnesses without a ROM surface simply hold the current frame.
            return null;
        }
        return animator;
    }

    /** sub_5750C writes the carried slot's y_pos word, retaining its fraction. */
    public void carryOnCollapsingColumn(int y) { setPosition(getX(), y); }

    private void setPosition(int x, int y) {
        posX = (posX & 0xFFFF) | ((x & 0xFFFF) << 16);
        posY = (posY & 0xFFFF) | ((y & 0xFFFF) << 16);
        syncBaseState();
    }

    /** The base keeps its own {@code x}/{@code y} for the touch pass and the explosion offset. */
    private void syncBaseState() {
        super.state.x = getX();
        super.state.y = getY();
    }

    private int cameraX() {
        var camera = services().camera();
        return camera == null ? 0 : camera.getX() & 0xFFFF;
    }

    private int cameraY() {
        var camera = services().camera();
        return camera == null ? 0 : camera.getY() & 0xFFFF;
    }

    private int boxLeftX() {
        SszZoneRuntimeState ssz = sszState();
        return ssz == null ? 0 : ssz.bossLeftX();
    }

    private int boxRightX() {
        SszZoneRuntimeState ssz = sszState();
        return ssz == null ? 0xFFFF : ssz.bossRightX();
    }

    /**
     * {@code PalLoad_Line1 Pal_SSZGHZMisc} in {@code loc_7B35A}: the same palette the Green Hill
     * recreation loads, and the same line. Act 1 ends with the Death Egg launch rather than with
     * a restore, so nothing here puts the level's line 1 back; that belongs to
     * {@code loc_7B81A}'s graph, which is owed.
     */
    private void loadFightPalette() {
        if (paletteLoaded) {
            return;
        }
        try {
            byte[] line = services().rom()
                    .readBytes(Sonic3kConstants.PAL_SSZ_GHZ_MISC_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.SSZ_MECHA_SONIC,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1, line);
            paletteLoaded = true;
        } catch (IOException | RuntimeException ignored) {
            // Partial object harnesses may not provide a ROM or palette surface.
        }
    }

    private SszZoneRuntimeState sszState() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }

    /** {@code CreateChild6_Simple ChildObjDat_7D47A}/{@code _7D486}/{@code _7D480}. */
    private void createTrail(int count) {
        createTrail(count, 0);
    }

    /**
     * {@code CreateChild6_Simple} numbers its children {@code addq.w #2,d2} — 0, 2, 4 — because
     * {@code sub_7D236} doubles the subtype against four-byte rows. {@code baseSubtype} is the
     * {@code addq.b #4} {@code loc_7C8FE} applies before the shared init, or the explicit
     * {@code move.b #8,subtype(a1)} after {@code ChildObjDat_7D480}.
     */
    private void createTrail(int count, int baseSubtype) {
        for (int index = 0; index < count; index++) {
            final int subtype = baseSubtype + index * 2;
            SszMechaSonicTrailChild child = spawnChild(() -> new SszMechaSonicTrailChild(
                    new ObjectSpawn(getX(), getY(), 0, subtype, 0, false, 0), this));
            if (child != null) {
                trail.add(child);
            }
        }
    }

    // --- child contract -----------------------------------------------------------------------

    /** {@code btst #2,$38(a1)}: the trail draws only while the boss is dashing. */
    public boolean trailVisible() {
        return (flags & (1 << FLAG_TRAIL)) != 0 && !isDestroyed();
    }

    // --- test surface ---------------------------------------------------------------------------

    public int routineForTest() { return super.state.routine; }
    public int timerForTest() { return timer; }
    public int xVelForTest() { return xVel; }
    public int yVelForTest() { return yVel; }
    /** {@code $16(a0)}, the low word of the 16.16 {@code y_pos}. */
    public int ySubForTest() { return posY & 0xFFFF; }
    public int mappingFrameForTest() { return anim.mappingFrame; }
    /** {@code $20(a0)}: the base keeps the same counter. */
    public int flashTimerForTest() { return super.state.invulnerabilityTimer; }
    public int yRadiusForTest() { return yRadius; }
    public boolean renderFlippedForTest() { return renderFlipped; }
    public int attackCounterForTest() { return attackCounter; }
    public int attackChoiceForTest() { return attackChoice; }
    public boolean initExecutedForTest() { return initExecuted; }
    public boolean defeatedForTest() { return defeated; }
    void releaseTrail(SszMechaSonicTrailChild child) { trail.remove(child); }

    public List<SszMechaSonicTrailChild> trailForTest() { return List.copyOf(trail); }

    // --- draw -------------------------------------------------------------------------------

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC);
        if (renderer != null && renderer.isReady()) {
            // ObjSlot_MechaSonic is make_art_tile(ArtTile_MechaSonic,1,1): palette line 1, which
            // is the line loc_7B35A's PalLoad_Line1 has just filled from Pal_SSZGHZMisc. -1 keeps
            // the sheet's own registered line rather than forcing line 0.
            renderer.drawFrameIndex(anim.mappingFrame, getX(), getY(), renderFlipped, false, -1);
        }
    }
}
