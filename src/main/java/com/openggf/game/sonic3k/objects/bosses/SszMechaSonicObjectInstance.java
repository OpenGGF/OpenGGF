package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.game.sonic3k.objects.SszRuntimeArtRequest;
import com.openggf.sprites.playable.AbstractPlayableSprite;
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
 * than faked: the Knuckles act-2 graph and native hit-window phase comparison.
 * Act 1 includes the secondary collision child, palette-driven defeat sparks,
 * results and launch handover.
 */
public final class SszMechaSonicObjectInstance extends AbstractBossInstance
        implements SpawnRewindRecreatable, SszBossExplosionController.StopFlag {

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
        /** {@code loc_7B996}. */ ACT2_CHARGE,
        /** {@code loc_7BA00}. */ ACT2_SPIN_UP,
        /** {@code loc_7BAD0}. */ ACT2_EMERALD_JUMP,
        /** {@code loc_7BB20}. */ ACT2_EMERALD_LANDING,
        /** {@code loc_7BBC6}. */ ACT2_FORCE_PLAYER,
        /** {@code loc_7BDF6}. */ SUPER_RISE,
        /** {@code loc_7BE4E}. */ SUPER_FLASH,
        /** {@code loc_7BEA8}. */ SUPER_FIRST_ATTACK,
        /** {@code loc_7BEB0}. */ SUPER_CROSS,
        /** {@code loc_7BED2}. */ SUPER_TARGET_DASH,
        /** {@code loc_7C06E}. */ SUPER_CHOOSE_ATTACK,
        SUPER_DIVE, SUPER_DIVE_LANDED, SUPER_DIVE_RISE, SUPER_RESET_CROSS,
        SUPER_SLAM_BOUNCE, SUPER_SLAM_SKID, SUPER_SLAM_STOP, SUPER_SLAM_RISE,
        SUPER_SHOOT, SUPER_RECHARGE_FALL, SUPER_RECHARGE_LAND, SUPER_RECHARGE_STAND,
        SUPER_RECHARGE_RUN, SUPER_RECHARGE_JUMP, SUPER_RECHARGE_LANDED, SUPER_CHARGE,
        SUPER_HOVER_TURN, SUPER_BURST_FINISHED,
    }

    // --- state -------------------------------------------------------------------------------

    /** {@code x_pos}/{@code y_pos} as the ROM keeps them: 16.16, velocity shifted left eight. */
    private final SszRuntimeArtRequest emeraldArt = new SszRuntimeArtRequest();
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
    /** Native code pointer Obj_SSZ2_Boss, independent of its later defeat pointer. */
    private boolean superMode;
    /** Native $39, $3C, child_dx and child_dy. */
    private int superCounter, targetX, targetSector, playerSector;
    private int swingMax;
    private boolean launchDeletePending;
    public void deleteOnNextObjectPass() { launchDeletePending = true; }
    /** {@code PalLoad_Line1} is a one-shot; the restore belongs to the owed defeat graph. */
    private boolean paletteLoaded;
    private final SszMechaPalette palette = new SszMechaPalette();
    private int emeraldSlot = -1;
    /** Native code pointer stages: 1=7BC32, 2=Obj_Wait, 3=7BCB0, 4=7BCFC. */
    private int finalDefeatStage;
    private int finalSoundTimer, finalSoundCount;
    private SszMechaScreenFlash finalFade;

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

    /**
     * ObjSlot_MechaSonic uses make_art_tile(ArtTile_MechaSonic,1,1): bit 15 stays
     * set through both fights, including the airborne power-down. The separate
     * $280 display-list priority only orders sprites against other sprites;
     * it cannot keep the body in front of the high-priority Plane B island.
     */
    @Override public boolean isHighPriority() { return true; }

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
        emeraldArt.service(services());
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
            // being rewritten. Act2 later clears status at loc_7B996 before transforming.
            dispatchDefeat(vIntRunCount);
            return;
        }
        if (superMode) dispatchSuper(vIntRunCount);
        else dispatch();
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

        if (services().currentAct() != 0) {
            // loc_7B2DC: SetUp_ObjAttributesSlotted has advanced routine to 2.
            // Act 2 starts standing at the crane encounter, without the act-1
            // offscreen sprint, trail allocation or camera-relative relocation.
            super.state.routine = 2;
            setPosition(0x220, 0x4A0);
        } else {
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
        }
        // loc_7B35A, in its own order: Queue_Kos_Module ArtKosM_MechaSonicExtra (the engine's
        // standalone sheet needs no queue), Load_PLC_Raw PLC_BossExplosion, and then
        // PalLoad_Line1 Pal_SSZGHZMisc -- which matters, because ObjSlot_MechaSonic draws this
        // object on line 1 and without the load he takes whatever the level left there.
        loadFightPalette();
        // Then the act-1 branch: AllocateObject / move.l #Obj_Song_Fade_Transition,(a1) /
        // move.b #mus_EndBoss,subtype(a1). The object's own init is move.w #90,$2E(a0) and a
        // cmd_FadeOut, so the theme arrives 91 updates later, not on this frame.
        if (services().currentAct() == 0) {
            spawnFreeChild(() -> new SongFadeTransitionInstance(90, Sonic3kMusic.BOSS.id));
        }
        SszMechaSonicCollisionChild.spawnFor(services(), getSlotIndex(), getX(), getY());
        // loc_7B39C's trailing AllocateObject only searches and writes no SST bytes.
        // Reserving an engine slot for that bare call would create a ROM-inaccurate occupant.
    }

    /** {@code SSZEndBoss_Index}: both acts share the attack graph after their entry. */
    private void dispatch() {
        switch (super.state.routine) {
            case 2 -> waitForCraneCamera();
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

    /** loc_7B3AC: the crane camera signals bit 4 only after finishing its pan. */
    private void waitForCraneCamera() {
        SszZoneRuntimeState ssz = sszState();
        if (ssz == null || !ssz.cutsceneFlag(4)) return;
        attackCounter = 2;
        ssz.setBossLeftX((cameraX() + 0x20) & 0xFFFF);
        ssz.setBossRightX((cameraX() + 0x120) & 0xFFFF);
        ssz.setBossCeilingY((cameraY() + 0x30) & 0xFFFF);
        onFacePlayer();
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
        // sub_7D312/sub_7D3BE use FixBugs=0: the bright row starts eight bytes
        // after word_7D3D6, overlapping its final dark colour. The fixed branch
        // uses ten bytes; preserve the shipped overlap instead of correcting it.
        try {
            var rom = services().rom();
            int row = 0x7D3D6 + ((super.state.invulnerabilityTimer & 1) == 0 ? 8 : 0);
            var registry = services().paletteOwnershipRegistryOrNull();
            for (int i = 0; i < 5; i++) {
                int destination = rom.read16BitAddr(0x7D3CC + i * 2) & 0xFFFF;
                S3kPaletteWriteSupport.applyContiguousPatch(registry, services().currentLevel(),
                        services().graphicsManager(), S3kPaletteOwners.SSZ_MECHA_SONIC,
                        S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, (destination - 0xFC20) / 2,
                        rom.readBytes(row + i * 2, 2));
            }
            S3kPaletteWriteSupport.resolvePendingWritesNow(registry, services().currentLevel(), services().graphicsManager());
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
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
     * <p>The base commits the score before calling this. This owner stops the HUD timer.
     * The native worker allocates repeated explosions after its own slot and consumes
     * RNG only when allocation succeeds; the generic boss explosion helper cannot
     * represent that graph. Landing raises $38 bit 5 to retire the worker.
     */
    @Override
    protected void onDefeatStarted() {
        if (superMode) {
            // loc_7D39E marks the second defeat, then fixes both native X limits
            // to the current camera before reloading Pal_SSZGHZMisc.
            flags |= 0x80;
            var camera = services().camera();
            short nativeX = (short) cameraX();
            camera.setMinX(nativeX); camera.setMaxX(nativeX);
            paletteLoaded = false; loadFightPalette();
        }
        stopLevelTimerOnBossDefeat(); // sub_7D35A: clr.b (Update_HUD_timer).w
        defeated = true;
        // move.l #loc_7B81A,(a0) / clr.b routine(a0) / move.l #loc_7B858,$34(a0)
        super.state.routine = 0;
        // bset #6,status(a0), and move.w #$7F,$2E(a0)
        super.state.invulnerable = true;
        timer = DEFEAT_WAIT;
        callback = Callback.DEFEAT_FALL;
        // lea (Child6_CreateBossExplosion).l,a2 / CreateChild1_Normal, subtype 4. The score is
        // the base's, added before this runs. Nothing here clears x_vel, y_vel or the trail bit.
        SszBossExplosionController.spawnFor(services(), getSlotIndex(), getX(), getY());
    }

    @Override
    public boolean stopsDefeatExplosions() {
        return (flags & (1 << FLAG_DEFEAT_LANDED)) != 0;
    }

    /**
     * {@code off_7B838}, the shared first-defeat/act2-transformation table. Routine 0 is
     * {@code loc_7B852}'s Obj_Wait, routine 2 is loc_7B87C's fall. Act1 routine 4
     * (loc_7B93E) only rotates the palette and animates: loc_7D056 owns the handover
     * timer independently. Act2 routine 8 (loc_7B984) additionally runs Obj_Wait.
     *
     * <p>Dated against the native {@code hpz_3} segment of
     * {@code s3k-sonic-tails-complete-emeralds}, comparison only: the slot takes
     * {@code loc_7B81A} on frame 1310, routine 2 on 1438 — {@code $7F + 1}, an {@code Obj_Wait}
     * completing on update N+1 — and routine 4 on 1439, one frame later, because at
     * {@code y $660} with {@code loc_7B858}'s {@code $1F} radius his feet are already below the
     * {@code $67C} floor and {@code ObjHitFloor_DoRoutine} lands him on its first dispatch.
     */
    private void dispatchDefeat(int vIntRunCount) {
        if (finalDefeatStage != 0) { updateFinalDefeat(); return; }
        switch (super.state.routine) {
            case 0 -> objWait();
            case 2 -> {
                moveSpriteWithGravity(LIGHT_GRAVITY);
                objHitFloorDoRoutine();
            }
            // loc_7B93E: palette writes precede the spark child's same-pass colour read.
            case 4 -> { runDefeatPalette(); animate(); }
            // loc_7B984: act2 spends $BF+1 updates while the defeat palette continues.
            case 8 -> { runDefeatPalette(); animate(); objWait(); }
            case 0xA -> {
                if ((flags & 0x40) == 0) {
                    super.state.routine = 0xC;
                    setRawAnimation(0x7D5EF);
                    anim.mappingFrame = 0xE;
                    callback = Callback.ACT2_SPIN_UP;
                }
            }
            case 0xC -> animate();
            case 0xE -> {
                // Play_SFX_Continuous reads the native V-int clock's low nibble.
                if ((vIntRunCount & 0xF) == 0) services().playSfx(Sonic3kSfx.SPINDASH.id);
                timer = (short) (timer - 1);
                if (timer < 0) beginAct2Rush();
            }
            case 0x10 -> {
                moveSprite2();
                if (getX() >= ((boxRightX() - 0xAA) & 0xFFFF)) {
                    super.state.routine = 0x12;
                    callback = Callback.ACT2_EMERALD_JUMP;
                    setRawAnimation(0x7D5F6);
                }
            }
            case 0x12 -> { xVel = (short) (xVel - 0x30); moveSprite2(); animate(); }
            case 0x14 -> {
                var script = animator();
                script.animateNoSstMultiDelayFlipX(anim, anim.script, this::runCallback,
                        () -> renderFlipped = !renderFlipped);
                moveSpriteWithGravity(LIGHT_GRAVITY);
                objHitFloorDoRoutine();
            }
            case 0x16 -> {
                if ((vIntRunCount & 0xF) == 0) services().playSfx(Sonic3kSfx.MECHA_TRANSFORM.id);
                runDefeatPalette(); toggleEmeraldFrame(); animate();
            }
            case 0x18 -> runAct2PlayerToArena();
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

    /** {@code loc_7B888}: install word_7D842, create sparks, then arm the act handover. */
    private void onDefeatLanded() {
        yRadius = DEFEAT_LANDED_Y_RADIUS;
        setRawAnimation(Sonic3kConstants.SSZ_MECHA_ANIM_DEFEATED_ADDR);
        palette.install(services(), 0x7D842);
        SszMechaSonicSparkChild.spawnFor(services(), getSlotIndex(), getX(), getY());
        services().playSfx(Sonic3kSfx.MECHA_LAND.id);
        if (services().currentAct() != 0) {
            if ((flags & 0x80) != 0) {
                // loc_7B916 changes the code pointer without changing the routine byte.
                finalDefeatStage = 1; timer = 0xBF; stopLevelTimerOnBossDefeat();
                // loc_7B916 uses plain AllocateObject, independently of the three forward children.
                spawnFreeChild(() -> new SszMechaDefeatRumble(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
                // ChildObjDat_7D4D0 / CreateChild6_Simple stops at its first allocation failure.
                for (int subtype = 0; subtype < 6; subtype += 2)
                    if (!SszMechaDefeatRunner.spawnFor(services(), getSlotIndex(), getX(), getY(), subtype)) break;
                return;
            }
            // loc_7B8E6: first act2 defeat never creates the act1 results/handover.
            // Its independent P2 input lock does not take control away from Knuckles.
            flags |= 1 << FLAG_DEFEAT_LANDED;
            super.state.routine = 8;
            timer = 0xBF;
            callback = Callback.ACT2_CHARGE;
            services().fadeOutMusic();
            if (services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite p2)
                p2.setControlLocked(true);
            return;
        }
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

    /** loc_7B996: the child animation, rather than a fitted timer, opens the next gate. */
    private void beginAct2Charge() {
        super.state.routine = 0xA;
        services().levelGamestate().resumeTimer();
        super.state.invulnerable = false;
        flags = 0x50;
        services().playMusic(Sonic3kMusic.DDZ.id);
        SszMechaSonicChargeChild.spawnFor(services(), getSlotIndex(), getX(), getY());
        emeraldArt.submit(services(), 0x17FCBA, 0x52E);
    }

    /** loc_7BC32 through loc_7BCFC: final-defeat timing and progression publication. */
    int finalDefeatStageForTest() { return finalDefeatStage; }

    private void updateFinalDefeat() {
        switch (finalDefeatStage) {
            case 1 -> {
                if ((timer = (short) (timer - 1)) >= 0) return;
                finalDefeatStage = 2; timer = 0x1F;
                services().fadeOutMusic(); setPosition(getX(), getY() + 0x20);
                // ChildObjDat_7D4CA / CreateChild6_Simple: preserve successful prefixes only.
                for (int subtype = 0; subtype < 32; subtype += 2)
                    if (!SszMechaDebris.spawnFor(services(), getSlotIndex(), getX(), getY(), subtype)) break;
            }
            case 2 -> {
                if ((timer = (short) (timer - 1)) >= 0) return;
                finalDefeatStage = 3; flags |= 0x20; finalSoundTimer = 0; finalSoundCount = 4;
                SszMechaScreenFlash.saveTarget(services());
                finalFade = spawnFreeChild(SszMechaScreenFlash::finalWhiteFade);
            }
            case 3 -> {
                updateFinalDefeatSound();
                if (finalFade == null || !finalFade.nativeFadeCompleted()) return;
                finalDefeatStage = 4; timer = 119;
                sszState().setAct2EndingActive(true); sszState().setEventsFg4Low(0xFF);
                if (services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p1) {
                    // Native object_control=$83: scripted owner suppresses normal motion/contact.
                    com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(p1);
                    p1.setObjectMappingFrameControl(true);
                    p1.setMappingFrame(0); p1.setOnObject(false); p1.setRollingJump(false); p1.setSpindash(false);
                }
                services().requestSessionSave(com.openggf.game.save.SaveReason.PROGRESSION_SAVE);
            }
            case 4 -> {
                updateFinalDefeatSound();
                // Accepted SSZ scope ends immediately before loc_7BCFC would allocate
                // loc_5E6C0/loc_85EE6 and change Player_mode to3. Hold at that cold stop
                // until the separately scoped ending owner exists; never pretend it ran.
                if (timer > 0) timer--;
            }
            default -> throw new IllegalStateException("Invalid SSZ final defeat stage");
        }
    }

    /** sub_7BD30 emits four explosion sounds on a signed-word predecrement clock. */
    private void updateFinalDefeatSound() {
        finalSoundTimer = (short) (finalSoundTimer - 1);
        if (finalSoundTimer >= 0) return;
        finalSoundTimer = 0x1F; finalSoundCount = (byte) (finalSoundCount - 1);
        if (finalSoundCount >= 0) services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);
    }

    /** loc_7C8C4 writes the parent's $38 bit6 through its SST address. */
    void finishChargeAnimation() { flags &= ~0x40; }

    /** loc_7BA00: two direction-specific trails and a $3B predecrement delay. */
    private void beginAct2SpinUp() {
        super.state.routine = 0xE;
        timer = 0x3B;
        flags |= 1 << FLAG_TRAIL;
        createTrail(2, renderFlippedForTest() ? 0 : 4);
    }

    /** loc_7BA38: freeze native input, open the second arena, then allocate its two owners. */
    private void beginAct2Rush() {
        super.state.routine = 0x10;
        if (services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p1) {
            p1.clearForcedInputMask(); p1.clearLogicalInputState(); p1.setControlLocked(true);
            p1.setXSpeed((short) 0); p1.setYSpeed((short) 0); p1.setGSpeed((short) 0);
        }
        var camera = services().camera();
        int max = (camera.getMaxX() + 0x140) & 0xFFFF;
        camera.setMaxX((short) max);
        sszState().setBossRightX((max + 0x100) & 0xFFFF);
        xVel = 0x600; yVel = 0;
        var emerald = spawnFreeChild(() -> new SszMasterEmerald(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        if (emerald != null) emeraldSlot = emerald.getSlotIndex();
        spawnAfterCurrentSibling(() -> new SszMechaArenaPan(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
    }

    /** loc_7BAD0: keep the skid's remaining X velocity through the emerald jump. */
    private void beginAct2EmeraldJump() {
        super.state.routine = 0x14;
        flags &= ~(1 << FLAG_TRAIL);
        yVel = -0x400; yRadius = 0x4B;
        callback = Callback.ACT2_EMERALD_LANDING;
        setRawAnimation(renderFlipped ? 0x7D60A : 0x7D61A);
    }

    /** loc_7BB20 / loc_7BB7C: native FA82..FA8E positions, palette and raised spark subtype. */
    private void landOnAct2Emerald() {
        var camera = services().camera();
        int min = (camera.getMinX() + 0xA0) & 0xFFFF;
        camera.setMinX((short) min);
        int x = min;
        int[] steps = {0x10, 0x50, 0x50, 0x40, 0x40, 0x50, 0x50};
        for (int i = 0; i < steps.length; i++) {
            x = (x + steps[i]) & 0xFFFF;
            sszState().setAct2AttackPosition(i, x);
        }
        super.state.routine = 0x16;
        callback = Callback.ACT2_FORCE_PLAYER;
        setRawAnimation(0x7D5AB);
        flags = 0x80;
        services().playSfx(Sonic3kSfx.MECHA_LAND.id);
        // word_7D9EA is another infinite 16-colour header; its first row is $7D9F8.
        palette.install(services(), 0x7D9EA);
        SszMechaSonicSparkChild.spawnFor(services(), getSlotIndex(), getX(), getY(), 8);
    }

    private void toggleEmeraldFrame() {
        var runtime = sszState();
        if (runtime.cutsceneFlag(6)) runtime.clearCutsceneFlag(6);
        else runtime.setCutsceneFlag(6);
    }

    /** loc_7BBC6: clear the native status byte without rolling-radius position adjustments. */
    private void prepareAct2PlayerRun() {
        super.state.routine = 0x18;
        if (services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p1) {
            p1.setDirection(com.openggf.physics.Direction.RIGHT);
            p1.setAir(false); p1.setRollingFlagPreserveRadii(false); p1.setOnObject(false);
            p1.setRollingJump(false); p1.setPushing(false);
            p1.setSpindash(false); p1.setSpindashCounter((short) 0x782);
        }
    }

    /** loc_7BBE0: P1 walks under locked input until FA8A+$10; only then release the camera. */
    private void runAct2PlayerToArena() {
        runDefeatPalette(); toggleEmeraldFrame();
        if (!(services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p1)) return;
        p1.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
        p1.setLogicalInputState(false, false, false, true, false, false);
        if ((p1.getCentreX() & 0xFFFF) < ((sszState().act2AttackPosition(4) + 0x10) & 0xFFFF)) return;
        services().camera().setScrollLocked(false);
        services().camera().setHorizScrollDelay(0);
        p1.clearForcedInputMask(); p1.clearLogicalInputState(); p1.setControlLocked(false);
        p1.setXSpeed((short) 0); p1.setYSpeed((short) 0); p1.setGSpeed((short) 0);
        defeated = false; superMode = true;
        super.state.defeated = false; super.state.hitCount = HIT_COUNT;
        collisionFlags = 0x23;
        beginSuperCharge();
    }

    /** loc_7BDD8 is also the return from an emerald recharge. */
    private void beginSuperCharge() {
        super.state.routine = 2; flags |= 0x40;
        callback = Callback.SUPER_RISE; setRawAnimation(0x7D5FF);
    }

    /** loc_7BDF6. */
    private void beginSuperRise() {
        super.state.routine = 4; xVel = 0; yVel = -0x480;
        sszState().clearCutsceneFlag(6);
    }

    /** loc_7BE4E: copy Normal to Target even if AllocateObject cannot create the flash worker. */
    private void beginSuperFlash() {
        super.state.routine = 8; superCounter = 0; flags &= ~2;
        palette.install(services(), 0x7DA60);
        timer = 0x3F; callback = Callback.SUPER_FIRST_ATTACK;
        SszMechaScreenFlash.saveTarget(services());
        spawnFreeChild(() -> new SszMechaScreenFlash(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        services().playSfx(Sonic3kSfx.SUPER_TRANSFORM.id);
    }

    /** Obj_SSZ2_Boss / SSZ2_Boss_Index: routine bytes retain the native table offsets. */
    private void dispatchSuper(int vIntRunCount) {
        switch (super.state.routine) {
            case 0, 2 -> {
                if ((vIntRunCount & 0xF) == 0) services().playSfx(Sonic3kSfx.MECHA_TRANSFORM.id);
                runDefeatPalette(); toggleEmeraldFrame(); animate();
            }
            case 4 -> {
                runDefeatPalette(); moveSpriteWithGravity(LIGHT_GRAVITY);
                int ceiling = sszState().bossCeilingY();
                if (getY() <= ceiling) {
                    setPosition(getX(), ceiling); super.state.routine = 6;
                    callback = Callback.SUPER_FLASH; setRawAnimation(0x7D645);
                }
            }
            case 6 -> { runDefeatPalette(); animate(); }
            case 8, 0x10 -> { runDefeatPalette(); objWait(); }
            case 0xA -> {
                runDefeatPalette(); animate(); xVel = (short) (xVel + xAccel);
                // loc_7BF52 returns without MoveSprite2 on the speed-cap update.
                if (xVel <= -0x800 || xVel >= 0x800) super.state.routine = 0xC;
                else moveSprite2();
            }
            case 0xC -> {
                runDefeatPalette(); moveSprite2();
                int remaining = (xVel < 0 ? getX() - targetX : targetX - getX()) & 0xFFFF;
                if (remaining < 0x38) {
                    super.state.routine = 0xE; xAccel = (short) -xAccel; setRawAnimation(0x7D626);
                }
            }
            case 0xE -> {
                runDefeatPalette(); animateFlip(); xVel = (short) (xVel + xAccel); moveSprite2();
                if (xVel < 0 ? targetX >= getX() : targetX <= getX()) finishSuperDash();
            }
            case 0x12, 0x16 -> { runDefeatPalette(); animate(); objWait(); }
            case 0x14, 0x1E -> { runDefeatPalette(); animate(); moveSprite2(); objHitFloorDoRoutine(); }
            case 0x18 -> {
                runDefeatPalette(); animate(); moveSprite2();
                if (getY() <= sszState().bossCeilingY()) {
                    // loc_7C1AA deliberately does not clamp the overshot Y word.
                    super.state.routine = 0x1A; callback = Callback.SUPER_RESET_CROSS;
                    renderFlipped = getX() < sszState().act2AttackPosition(3); setRawAnimation(0x7D538);
                }
            }
            case 0x1A, 0x24 -> { runDefeatPalette(); animate(); }
            case 0x1C -> {
                runDefeatPalette(); animate(); xVel = (short) (xVel + xAccel); yVel = (short) (yVel + 0x80);
                if ((yVel & 0xFFFF) >= 0x800) {
                    super.state.routine = 0x1E; yRadius = 0x57; callback = Callback.SUPER_SLAM_BOUNCE;
                }
                moveSprite2();
            }
            case 0x20 -> {
                runDefeatPalette(); animate(); yVel = (short) (yVel - 0x80); moveSprite2(); objHitFloorDoRoutine();
            }
            case 0x22 -> { runDefeatPalette(); animate(); xVel = (short) (xVel + xAccel); moveSprite2(); }
            case 0x26 -> {
                runDefeatPalette(); animateFlip(); moveSpriteWithGravity(LIGHT_GRAVITY);
                if (getY() <= sszState().bossCeilingY()) {
                    setPosition(getX(), sszState().bossCeilingY()); resetSuperCross();
                }
            }
            case 0x28 -> {
                runDefeatPalette(); animateFlip(); moveSpriteWithGravity(0x10);
                if ((yVel & 0xFFFF) >= 0x100) { super.state.routine = 0x2A; beginSuperHover(); }
            }
            case 0x2A, 0x2C -> {
                runDefeatPalette(); swingUpDown(); moveSprite2(); renderFlipped = nearestNativePlayerIsRight(); objWait();
            }
            case 0x2E -> { runDefeatPalette(); animate(); moveSpriteWithGravity(NORMAL_GRAVITY); objHitFloorDoRoutine(); }
            case 0x30, 0x38 -> animate();
            case 0x32, 0x3A -> { animate(); moveSpriteWithGravity(LIGHT_GRAVITY); objHitFloorDoRoutine(); }
            case 0x34 -> {
                animate(); moveSprite2();
                int right = boxRightX();
                if (xVel < 0 ? right >= getX() : right <= getX()) {
                    setPosition(right, getY()); super.state.routine = 0x36;
                    yRadius = 0x1F; xVel = 0; yVel = -0x200;
                }
            }
            case 0x36 -> {
                animate(); moveSpriteWithGravity(LIGHT_GRAVITY);
                // loc_7C4E0 branches on BPL, unlike ObjHitFloor_DoRoutine's BMI/BEQ.
                // The probe result is consumed even when it found no solid surface.
                var floor = ObjectTerrainUtils.checkFloorDist(getX(), getY(), yRadius);
                if (floor.distance() >= 0) {
                    setPosition(getX(), getY() + floor.distance()); super.state.routine = 0x38;
                    callback = Callback.SUPER_RECHARGE_JUMP;
                    services().playSfx(Sonic3kSfx.MECHA_LAND.id); setRawAnimation(0x7D5C1);
                }
            }
            case 0x3C -> updateSuperHover();
            case 0x3E -> { runDefeatPalette(); animateFlip(); swingUpDown(); moveSprite2(); }
            case 0x40 -> runDefeatPalette();
            case 0x42 -> {
                moveSpriteWithGravity(LIGHT_GRAVITY);
                if (yVel >= 0x400) { super.state.routine = 0x44; palette.install(services(), 0x7DC06); }
            }
            case 0x44 -> {
                runDefeatPalette(); flags &= ~0x80;
                if (palette.headerAddress() != 0x7DC10) flags |= 0x80;
                int next = (short) (yVel - 0x40);
                if (next >= -0x400) yVel = next;
                moveSprite2();
                if (getY() <= sszState().bossCeilingY()) {
                    setPosition(getX(), sszState().bossCeilingY()); super.state.routine = 0x3C;
                    superCounter = 6; swingMax = 0x100; yVel = -0x100; xAccel = 0x10; flags |= 1;
                }
            }
            case 0x46 -> {
                runDefeatPalette();
                int result = animator().animateMultiDelay(anim, this::runCallback);
                if (result != S3kRawAnimation.WAITING && anim.animFrame == 8) {
                    services().playSfx(Sonic3kSfx.BOSS_PROJECTILE.id);
                    // ChildObjDat_7D4A8: stop on the first failed forward allocation; no retry/healing.
                    for (int subtype = 0; subtype < 16; subtype += 2)
                        if (SszMechaProjectile.spawnFor(services(), getSlotIndex(), getX(), getY(), true, subtype) == null) break;
                }
            }
            default -> { }
        }
    }

    /** loc_7BEA8 selects the low-health hover or the opening crossing. */
    private void beginSuperFirstAttack() {
        if (super.state.hitCount <= 2) {
            super.state.routine = 0x3C;
            anim.mappingFrame = 8;
            // loc_7BF0C's $80 timer and five-count are overwritten by loc_7C34C.
            beginSuperHover();
            return;
        }
        beginSuperCross();
    }

    /** loc_7BEB0: choose the opposite arena edge from FA88, not the viewport centre. */
    private void beginSuperCross() {
        renderFlipped = getX() < sszState().act2AttackPosition(3);
        targetX = sszState().act2AttackPosition(renderFlipped ? 6 : 0);
        beginSuperTargetDash();
    }

    /** loc_7BED2 / ChildObjDat_7D49A. */
    private void beginSuperTargetDash() {
        super.state.routine = 0xA; xVel = 0; yVel = 0;
        xAccel = renderFlipped ? 0x80 : -0x80;
        setRawAnimation(0x7D652); flags |= 0x40;
        SszMechaSuperGlow.spawnFor(services(), getSlotIndex(), getX(), getY());
    }

    /** loc_7BFD4: two initial crossings, then choose a player-sector-dependent target. */
    private void finishSuperDash() {
        super.state.routine = 0x10; flags &= ~0x40;
        if ((flags & 2) != 0) {
            timer = 0x1F; callback = Callback.SUPER_CHOOSE_ATTACK;
            return;
        }
        timer = 7; superCounter = (superCounter + 1) & 255;
        callback = Callback.SUPER_CROSS;
        if (superCounter < 2) return;
        playerSector = nativePlayerSector();
        boolean right = nativePlayerOffset() >= 0xF0;
        if (getX() >= sszState().act2AttackPosition(3)) right = !right;
        if (right) return;
        flags |= 2; callback = Callback.SUPER_TARGET_DASH;
        // byte_7C050 / word_7C056: the two sides use opposite sector destinations.
        targetSector = new int[] {4, 5, 6, 1, 2, 3}[playerSector - 1];
        targetX = sszState().act2AttackPosition(new int[] {4, 5, 6, 0, 1, 2}[playerSector - 1]);
    }

    private int nativePlayerOffset() {
        var p1 = services().playerQuery().mainPlayerOrNull();
        return ((p1 == null ? 0 : p1.getCentreX()) - services().camera().getMinX()) & 0xFFFF;
    }

    /**
     * sub_7D1EA compares the original d0 against $50/$A0 after subtracting $F0
     * into d1 only. Consequently the ordinary right half selects sector6;
     * sectors4/5 are not reachable there. Preserve this shipped arithmetic.
     */
    private int nativePlayerSector() {
        int offset = nativePlayerOffset();
        return (offset >= 0xF0 ? 4 : 1) + (offset >= 0x50 ? 1 : 0) + (offset >= 0xA0 ? 1 : 0);
    }

    /** loc_7C06E / sub_7D1B6: the sector lookup consumes no RNG on a direct dive. */
    private void chooseSuperAttack() {
        int sector = nativePlayerSector();
        try {
            int row = 0x7D1D2 + (targetSector - 1) * 4;
            for (int i = 0; i < 4; i++) {
                int entry = services().romReader().readU8(row + i);
                if (entry == 0) break;
                if (entry == sector) {
                    super.state.routine = 0x12; timer = 0x1F; callback = Callback.SUPER_DIVE;
                    services().playSfx(Sonic3kSfx.ROLL.id);
                    var p1 = services().playerQuery().mainPlayerOrNull();
                    var velocity = SszMechaAim.toward(getX(), getY(), p1 == null ? 0 : p1.getCentreX(),
                            (cameraY() + 0xB0) & 0xFFFF, 3);
                    xVel = velocity.x(); yVel = velocity.y(); setRawAnimation(0x7D52A);
                    return;
                }
            }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        xVel = 0; yVel = 0;
        if ((services().rng().nextRaw() & 3) == 0) {
            super.state.routine = 0x1C;
            xAccel = getX() >= sszState().act2AttackPosition(3) ? -0x80 : 0x80;
            services().playSfx(Sonic3kSfx.MISSILE_SHOOT.id); setRawAnimation(0x7D652); flags |= 0x40;
            SszMechaSuperGlow.spawnFor(services(), getSlotIndex(), getX(), getY());
        } else {
            super.state.routine = 0x28; setRawAnimation(0x7D57C);
        }
    }

    /** loc_7C1E4. */
    private void resetSuperCross() { superCounter = 0; flags &= ~2; beginSuperCross(); }

    /** loc_7C34C; shared by the normal projectile phase and the low-health hover. */
    private void beginSuperHover() {
        swingMax = 0x100; yVel = 0x100; xAccel = 0x10; flags &= ~1;
        superCounter = 3; timer = 0x1F; callback = Callback.SUPER_SHOOT;
    }

    /** loc_7C39E: three live charges followed by a subtype8 disappearing charge. */
    private void shootSuperProjectile() {
        timer = 0x2F;
        superCounter = (byte) (superCounter - 1);
        SszMechaProjectile.spawnFor(services(), getSlotIndex(), getX(), getY(), false,
                superCounter < 0 ? 8 : 0);
        if (superCounter < 0) { super.state.routine = 0x2C; callback = Callback.SUPER_RECHARGE_FALL; }
    }

    /** Swing_UpAndDown retains the native same-pass bounce-back at both velocity limits. */
    private boolean swingUpDown() {
        boolean peak = false;
        if ((flags & 1) == 0) {
            yVel = (short) (yVel - xAccel);
            if (yVel > -swingMax) return false;
            flags |= 1; peak = true;
        }
        yVel = (short) (yVel + xAccel);
        if (yVel >= swingMax) { flags &= ~1; yVel = (short) (yVel - xAccel); peak = true; }
        return peak;
    }

    private boolean nearestNativePlayerIsRight() {
        var nearest = services().playerQuery().nearestByRomX(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2, getX()).player();
        return nearest != null && (short) (getX() - nearest.getCentreX()) < 0;
    }

    /** loc_7C59A: timer expiration wins over swing-count expiration on the same pass. */
    private void updateSuperHover() {
        runDefeatPalette(); timer = (short) (timer - 1);
        if (timer < 0) {
            super.state.routine = 0x46; callback = Callback.SUPER_BURST_FINISHED; setRawAnimation(0x7D587);
            return;
        }
        if (swingUpDown() && (superCounter = (byte) (superCounter - 1)) < 0) {
            super.state.routine = 0x40; flags &= ~0x80; xVel = renderFlipped ? 0x100 : -0x100;
            palette.install(services(), 0x7DC7E);
            return;
        }
        moveSprite2();
        if (nearestNativePlayerIsRight() == renderFlipped) xVel = renderFlipped ? 0x100 : -0x100;
        else {
            super.state.routine = 0x3E; callback = Callback.SUPER_HOVER_TURN; setRawAnimation(0x7D636);
        }
    }

    private void animateFlip() {
        animator().animateNoSstMultiDelayFlipX(anim, anim.script, this::runCallback,
                () -> renderFlipped = !renderFlipped);
    }

    /** The custom command's native callback is loc_7C654 during Super Mecha's recharge. */
    private void runDefeatPalette() {
        palette.tick(services(), () -> super.state.routine = 0x42);
    }

    boolean superGlowActive() { return (flags & 0x40) != 0; }

    boolean stopsSparks() { return (flags & 0x40) != 0; }

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
            case ACT2_CHARGE -> beginAct2Charge();
            case ACT2_SPIN_UP -> beginAct2SpinUp();
            case ACT2_EMERALD_JUMP -> beginAct2EmeraldJump();
            case ACT2_EMERALD_LANDING -> landOnAct2Emerald();
            case ACT2_FORCE_PLAYER -> prepareAct2PlayerRun();
            case SUPER_RISE -> beginSuperRise();
            case SUPER_FLASH -> beginSuperFlash();
            case SUPER_FIRST_ATTACK -> beginSuperFirstAttack();
            case SUPER_CROSS -> beginSuperCross();
            case SUPER_TARGET_DASH -> beginSuperTargetDash();
            case SUPER_CHOOSE_ATTACK -> chooseSuperAttack();
            case SUPER_DIVE -> { // loc_7C12E
                super.state.routine = 0x14; yRadius = 0x13; callback = Callback.SUPER_DIVE_LANDED;
            }
            case SUPER_DIVE_LANDED -> { // loc_7C15C
                super.state.routine = 0x16; timer = 0x3F; callback = Callback.SUPER_DIVE_RISE;
                services().playSfx(Sonic3kSfx.CRASH.id);
            }
            case SUPER_DIVE_RISE -> { // loc_7C17A
                super.state.routine = 0x18; xVel = 0; yVel = -0x400;
            }
            case SUPER_RESET_CROSS -> resetSuperCross();
            case SUPER_SLAM_BOUNCE -> { // loc_7C232
                super.state.routine = 0x20; yRadius = 0x1F; callback = Callback.SUPER_SLAM_SKID;
                services().playSfx(Sonic3kSfx.MECHA_LAND.id);
            }
            case SUPER_SLAM_SKID -> { // loc_7C274: NEG.W then ASR.W
                super.state.routine = 0x22; xAccel = ((short) -xAccel) >> 1; yVel = 0;
                callback = Callback.SUPER_SLAM_STOP;
                services().playSfx(Sonic3kSfx.MECHA_LAND.id); setRawAnimation(0x7D556);
            }
            case SUPER_SLAM_STOP -> { // loc_7C2BE
                super.state.routine = 0x24; flags &= ~0x40; callback = Callback.SUPER_SLAM_RISE;
                setRawAnimation(0x7D561);
            }
            case SUPER_SLAM_RISE -> { // loc_7C2E8
                super.state.routine = 0x26; xVel = 0; yVel = -0x600; setRawAnimation(0x7D56C);
            }
            case SUPER_SHOOT -> shootSuperProjectile();
            case SUPER_RECHARGE_FALL -> { // loc_7C3CA
                super.state.routine = 0x2E; yRadius = 0x1F; callback = Callback.SUPER_RECHARGE_LAND;
                flags &= ~0x80; palette.install(services(), 0x7DB66); setRawAnimation(0x7D508);
            }
            case SUPER_RECHARGE_LAND -> { // loc_7C410
                super.state.routine = 0x30; callback = Callback.SUPER_RECHARGE_STAND;
                services().playSfx(Sonic3kSfx.MECHA_LAND.id); setRawAnimation(0x7D510);
            }
            case SUPER_RECHARGE_STAND -> { // loc_7C436
                super.state.routine = 0x32;
                // CLR.B y_vel clears the high byte of the big-endian word, not the whole velocity.
                yVel &= 0xFF; yRadius = 0x13; callback = Callback.SUPER_RECHARGE_RUN; setAnimation(0x7D519);
            }
            case SUPER_RECHARGE_RUN -> { // loc_7C46A
                super.state.routine = 0x34; services().playSfx(Sonic3kSfx.MECHA_LAND.id);
                renderFlipped = getX() < boxRightX(); xVel = renderFlipped ? 0x400 : -0x400; yVel = 0;
            }
            case SUPER_RECHARGE_JUMP -> { // loc_7C522: pointer-only animation retains the outgoing counters.
                super.state.routine = 0x3A; yVel = -0x400; yRadius = 0x4B;
                callback = Callback.SUPER_RECHARGE_LANDED;
                if (renderFlipped) { anim.mappingFrame = 0xC; setAnimation(0x7D5D8); }
                else setAnimation(0x7D61A);
                renderFlipped = false;
            }
            case SUPER_RECHARGE_LANDED -> { // loc_7C578 falls through loc_7BB7C, including its second sound.
                super.state.routine = 0; callback = Callback.SUPER_CHARGE;
                services().playSfx(Sonic3kSfx.MECHA_LAND.id); setRawAnimation(0x7D5B6);
                flags = 0x80; services().playSfx(Sonic3kSfx.MECHA_LAND.id);
                palette.install(services(), 0x7D9EA);
                SszMechaSonicSparkChild.spawnFor(services(), getSlotIndex(), getX(), getY(), 8);
            }
            case SUPER_CHARGE -> beginSuperCharge();
            case SUPER_HOVER_TURN -> super.state.routine = 0x3C;
            case SUPER_BURST_FINISHED -> { super.state.routine = 0x3C; timer = 0x7F; }
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
        if (camera == null) return 0;
        var ssz = sszState();
        // Camera-relative ROM attack coordinates use the original 320px window,
        // not the projected wide left edge used to draw the arena.
        return com.openggf.game.sonic3k.runtime.SszArenaCamera.nativeX(camera, ssz);
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
        if (finalDefeatStage >= 2) return;
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
