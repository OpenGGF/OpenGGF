package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ROM {@code Obj_SSZMTZBoss} (sonic3k.asm:163023-163363): the Metropolis recreation, the second of
 * Sky Sanctuary act 1's three rebuilt boss fights. Like the Green Hill one it is not placed —
 * {@code sub_575EA}'s {@code loc_5775C} allocates it once the upper arena's lock has eased the
 * camera down to {@code $380}.
 *
 * <p><b>Entry.</b> {@code Obj_SSZMTZBoss} itself is the init: {@code Obj_Wait} for {@code $1F}
 * frames with {@code $34 = loc_7A712}, {@code Boss_flag}, {@code cmd_FadeOut}, an
 * {@code Obj_Song_Fade_Transition} carrying {@code mus_EndBoss}, {@code clr.w (_unkFA88).w},
 * {@code Load_PLC $7B}, {@code Queue_Kos_Module ArtKosM_SSZMTZOrbs}, the
 * {@code Normal_palette_line_2 -> Target_palette_line_2} save and
 * {@code PalLoad_Line1 Pal_SSZMTZOrbs}. {@code loc_7A712} spends its own frame installing
 * {@code loc_7A71A}, so {@code off_7A728} is first dispatched on init + 33 — measured, comparison
 * only, in the {@code s3k-sonic-tails-complete-emeralds} {@code hpz} segment: the boss slot
 * appears on native frame 6156, takes {@code loc_7A71A} on 6189.
 *
 * <p><b>Two levels of dispatch.</b> {@code off_7A728} has two entries. Entry 0
 * ({@code loc_7A72C}) is the one-shot setup; entry 1 ({@code loc_7A7E2}) dispatches again on
 * {@code $26(a0)} through {@code off_7A7F0}'s eight arms. Unlike the Green Hill ship this one does
 * not move through {@code x_pos}/{@code y_pos}: it keeps {@code SSZ_MTZ_boss_X_pos}/{@code _Y_pos}
 * as longwords and {@code _X_vel}/{@code _Y_vel} as words, steps them with {@code Boss_MoveObject}
 * (the Sonic 2 routine: {@code asl.l #8} into a 16.16 pair) and copies {@code x_pos} back out of
 * them at {@code loc_7A8DA}, with {@code sub_7A85A}'s {@code asr.w #6} sine giving {@code y_pos} a
 * four-pixel hover.
 *
 * <ol start="0">
 *   <li>{@code loc_7A800}: fall from {@code ($1700,$300)} at {@code y_vel $100} until
 *       {@code $420}, then face the player and take {@code x_vel +/-$100}.</li>
 *   <li>{@code loc_7A874}: patrol between {@code $1680} and {@code $1780}. {@code $2E} bit 7 is
 *       the direction and bit 6 is "one turn already made" — {@code bset} reads the old bit, so
 *       the second turn is the one that advances and arms {@code y_vel -$100}.</li>
 *   <li>{@code loc_7A8F4}: rise to {@code $3F0} and stop over {@code $1700}; when both velocities
 *       are zero, advance.</li>
 *   <li>{@code loc_7A93C} / {@code loc_7A95A}: the arms go up to {@code $68} and come back to
 *       {@code $27}, which is what swings the orbs out and back.</li>
 *   <li>{@code loc_7A98A} / {@code loc_7A9D4}: the hit reaction. It waits for every launched orb
 *       to be gone ({@code $30(a0)}), then either goes back to routine 0 or, once {@code $3C(a0)}
 *       — seven arm-raise cycles — is spent, takes {@code $E}.</li>
 *   <li>{@code loc_7AA44}: the laser pass, a third dispatch on {@code $32(a0)}. It dives to
 *       {@code $420}, climbs back, and {@code sub_7AB56} fires three pairs of
 *       {@code ChildObjDat_7AB80} children {@code $1E} frames apart.</li>
 * </ol>
 *
 * <p><b>Hits and defeat</b> ({@code sub_7ACF2}, called through {@code sub_7AC06} after every
 * dispatch). It is {@code sub_7A5A0} with different offsets: {@code $1C(a0)} is the {@code $20}
 * window, the flash rows are {@code word_7AD7E} (byte for byte {@code word_7A628}) and the same
 * shipped {@code addi.w #2*2,d0} off-by-one applies. The restore is {@code move.b
 * #$F,collision_flags(a0)} where the init wrote {@code $11}, so the ship's box changes size after
 * its first hit; that is modelled, not smoothed. At zero hits {@code loc_7AD3A} installs
 * {@code Wait_FadeToLevelMusic} with {@code $34 = loc_7AC7A}, writes {@code st (_unkFA88).w} —
 * which pops every live orb — clears {@code $38(a0)} and calls {@code BossDefeated}.
 * {@code loc_7AC7A} then turns the ship right with {@code x_vel $400}, and after {@code loc_7AC92}
 * runs out {@code loc_7ACA4} clears {@code Boss_flag}, writes {@code st (_unkFA89).w} (the head)
 * and {@code st (Events_bg+$02).w} — the byte {@code sub_575EA} reads as beaten and the
 * {@code $79:$F6} gated pad reads as its release — restores palette line 2 and reloads PLC
 * {@code $32}.
 */
public final class SszMtzBossObjectInstance extends AbstractBossInstance
        implements SpawnRewindRecreatable, SszMechaHeadHost {

    /** {@code move.b #8,collision_property(a0)}. */
    private static final int HIT_COUNT = 8;
    /** {@code move.b #$11,collision_flags(a0)} in {@code loc_7A72C}. */
    public static final int COLLISION_SIZE_INITIAL = 0x11;
    /** {@code move.b #$F,collision_flags(a0)} in {@code sub_7ACF2}: the restore is a smaller box. */
    public static final int COLLISION_SIZE_RESTORED = 0x0F;
    private static final int SHIP_MAPPING_FRAME = 0x0A;
    /** {@code move.b #$20,width_pixels(a0)}; the Green Hill ship's {@code $20} height applies. */
    private static final int SHIP_HALF_WIDTH = 0x20;
    private static final int SHIP_HALF_HEIGHT = 0x20;
    /** {@code move.w #$180,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);

    /** {@code move.w #$1F,$2E(a0)} in the init. */
    private static final int ENTRY_WAIT_FRAMES = 0x1F;
    /** {@code move.w #$1700,x_pos(a0)} / {@code move.w #$300,y_pos(a0)}: world, not camera. */
    public static final int SPAWN_X = 0x1700;
    public static final int SPAWN_Y = 0x0300;
    /** {@code move.w #$100,(SSZ_MTZ_boss_Y_vel).w}. */
    private static final int DESCENT_Y_VEL = 0x100;
    /** {@code cmpi.w #$420,(SSZ_MTZ_boss_Y_pos).w} in {@code loc_7A800}. */
    public static final int DESCENT_FLOOR_Y = 0x420;
    /** {@code loc_7A874}'s two turn-around limits. */
    public static final int PATROL_MIN_X = 0x1680;
    public static final int PATROL_MAX_X = 0x1780;
    /** {@code loc_7A8F4}'s hover ceiling and its X centre. */
    public static final int HOVER_Y = 0x3F0;
    public static final int CENTRE_X = 0x1700;
    /** {@code loc_7A98A}/{@code loc_7AA66}'s higher hover ceiling. */
    public static final int HIGH_HOVER_Y = 0x3B0;
    /** {@code loc_7AA66}'s two laser-dive trigger points. */
    public static final int LASER_TRIGGER_LEFT_X = 0x16A0;
    public static final int LASER_TRIGGER_RIGHT_X = 0x1760;
    /** {@code move.w #$100,(SSZ_MTZ_boss_X_vel).w} and the negative of it. */
    private static final int PATROL_SPEED = 0x100;
    /** {@code move.w #-$180,(SSZ_MTZ_boss_Y_vel).w} / {@code #$180}. */
    private static final int LUNGE_SPEED = 0x180;
    /** {@code move.b #$27,$38(a0)} / {@code $3A(a0)} and {@code cmpi.b #$68,$38(a0)}. */
    public static final int ARM_REST = 0x27;
    public static final int ARM_RAISED = 0x68;
    /** {@code move.b #7,$3C(a0)}: the arm-raise cycles this fight has in it. */
    public static final int ARM_CYCLES = 7;
    /** {@code move.b #$40,$1D(a0)}: the hover phase's starting angle. */
    private static final int HOVER_PHASE_START = 0x40;
    /** {@code addq.b #4,$1D(a0)}. */
    private static final int HOVER_PHASE_STEP = 4;
    /** {@code move.b #3,$31(a0)} and {@code move.w #$1E,(SSZ_MTZ_boss_laser_timer).w}. */
    public static final int LASER_SHOTS = 3;
    public static final int LASER_INTERVAL = 0x1E;
    /** {@code move.b #$10,$33(a0)}: the recoil pause a shot costs the ship. */
    public static final int LASER_RECOIL_FRAMES = 0x10;
    /** {@code move.w #$400,x_vel(a0)} in {@code loc_7AC7A}. */
    private static final int ESCAPE_X_VEL = 0x400;
    /** {@code move.w #(2*60)-1,$2E(a0)} in {@code loc_85674}. */
    private static final int ESCAPE_FRAMES = (2 * 60) - 1;
    /** {@code move.w #$3F,$2E(a0)} in {@code BossDefeated} (sonic3k.asm:180822). */
    private static final int DEFEAT_WAIT_FRAMES = 0x3F;
    /** {@code moveq #100,d0 / jsr (HUD_AddToScore)} in the same routine. */
    private static final int DEFEAT_SCORE = 100;

    /**
     * {@code moveq #$7B,d0 / Load_PLC} in the init and, at {@code loc_7ACA4},
     * {@code moveq #$32,d0}. Neither is issued: the engine has no runtime decompression queue for
     * object art, because {@code Sonic3kObjectArtProvider} registers a zone's sheets at level load
     * from {@code Sonic3kPlcArtRegistry}, which is where {@code ArtKosM_SSZMTZOrbs} lives. The two
     * ids are kept as named constants because the tests cite them and because a future runtime
     * queue has to know which entries the fight owns. Same gap, same reason, as the Green Hill
     * sibling's.
     */
    public static final int FIGHT_PLC = 0x7B;
    public static final int LEVEL_PLC = 0x32;
    /** {@code PAL_POINTERS} index for {@code Pal_SSZ1}. */
    private static final int PAL_POINTERS_SSZ1_INDEX = 0x1E;

    /** {@code word_7AD78}: {@code Normal_palette} byte offsets as line-0 colour indices. */
    private static final int FLASH_COLOR_A = 0x0E / 2;
    private static final int FLASH_COLOR_B = 0x1C / 2;
    private static final int FLASH_COLOR_C = 0x1E / 2;
    /** {@code word_7AD7E}: two overlapping three-word rows in one six-word table. */
    public static final int[] FLASH_TABLE = {0x0008, 0x0866, 0x0222, 0x0888, 0x0CCC, 0x0EEE};
    /** {@code btst #0,$1C(a0)} leaves {@code d0} zero on the odd counter values. */
    private static final int FLASH_ROW_NORMAL = 0;
    /**
     * {@code FixBugs = 0} ships {@code addi.w #2*2,d0}, one word short of the second row, so the
     * even frames write {@code $222,$888,$CCC} instead of {@code $888,$CCC,$EEE}. Modelled as
     * shipped, per runtime invariant 7; the {@code FixBugs} branch would be {@code 2*3}.
     */
    private static final int FLASH_ROW_SHIPPED = (2 * 2) / 2;
    public static final int FLASH_ROW_FIXED = (2 * 3) / 2;

    /**
     * {@code loc_7A7C4}: {@code $10,0,3,0,1,0} written over {@code _unkFA82.._unkFA87}. The first
     * word is the EggRobo fly-by pairing word, so every pairing group but bit 12 reads as not yet
     * passed once this fight starts, and group 12 reads as passed.
     *
     * <p>The other four bytes are <b>not</b> modelled, and the reason is not that nothing reads
     * them. {@code 2(a1)} is {@code _unkFA84}, which this engine does model —
     * {@code SszZoneRuntimeState.backgroundCameraDelta()}, read by {@code MoveSprite_SSZBGAdjust}
     * — and {@code loc_7A7C4}'s third byte seeds it {@code $0300}, with {@code sub_7AC06}
     * rewriting its high byte on a hit and on a player death. The drop is safe because
     * {@code loc_6607E} rewrites {@code _unkFA84} from the camera delta every frame, so the seed
     * and both nibble writes are overwritten before anything can read them.
     */
    public static final int PAIRING_WORD_AFTER_SETUP = 0x1000;

    private static final int ROUTINE_SETUP = 0;
    private static final int ROUTINE_RUNNING = 2;

    /** {@code $26(a0)}: {@code off_7A7F0}'s eight arms. */
    public static final int STATE_DESCEND = 0x00;
    public static final int STATE_PATROL = 0x02;
    public static final int STATE_RISE = 0x04;
    public static final int STATE_ARMS_UP = 0x06;
    public static final int STATE_ARMS_DOWN = 0x08;
    public static final int STATE_HIT_RISE = 0x0A;
    public static final int STATE_HIT_RECOVER = 0x0C;
    public static final int STATE_LASER = 0x0E;

    /** {@code $2E(a0)} and the {@code $34(a0)} continuation the entry wait runs at zero. */
    private int waitTimer = ENTRY_WAIT_FRAMES;
    private boolean initExecuted;
    /** {@code loc_7A712} installs {@code loc_7A71A} and returns; the table runs the frame after. */
    private boolean dispatcherInstalled;
    private boolean entryApplied;
    private boolean paletteLoaded;

    /** {@code SSZ_MTZ_boss_X_pos}/{@code _Y_pos} as the 16.16 longwords {@code Boss_MoveObject} steps. */
    private int bossX = SPAWN_X << 16;
    private int bossY = SPAWN_Y << 16;
    private int bossXVel;
    private int bossYVel = DESCENT_Y_VEL;
    /** {@code x_pos(a0)}/{@code y_pos(a0)}: written from the pair at {@code loc_7A8DA}. */
    private int drawX = SPAWN_X;
    private int drawY = SPAWN_Y;

    /** {@code $26(a0)}: the second dispatch's arm. */
    private int phase = STATE_DESCEND;
    /** {@code $32(a0)} and {@code $33(a0)}: the laser pass's own dispatch and its recoil pause. */
    private int laserState;
    private int laserRecoil;
    /** {@code $31(a0)} and {@code SSZ_MTZ_boss_laser_timer}. */
    private int laserShotsLeft;
    private int laserTimer;
    /** {@code $1D(a0)}: the hover phase {@code sub_7A85A} advances by four a frame. */
    private int hoverPhase = HOVER_PHASE_START;
    /** {@code $38(a0)} and {@code $3A(a0)}: the arm lengths the orbit reads as its radii. */
    private int armX = ARM_REST;
    private int armY = ARM_REST;
    /** {@code $3B(a0)}: {@code -1} extends the orbs' vertical angle, {@code $80} retracts it. */
    private int armSignal;
    /** {@code $3C(a0)}: arm-raise cycles left. */
    private int armCycles = ARM_CYCLES;
    /** {@code $39(a0)}: one frame's "launch an orb" flag, consumed by the first orb to see it. */
    private boolean launchRequest;
    /** {@code $30(a0)}: how many orbs are off their orbit right now. */
    private int liveOrbs;
    /** {@code $2E(a0)} bit 7 (facing/heading right) and bit 6 (one turn already made). */
    private boolean headingRight;
    private boolean turnedOnce;
    /** {@code render_flags} bit 0. */
    private boolean renderFlipped;
    /** {@code $1C(a0)}: modelled by the base's invulnerability timer, which is also {@code $20}. */

    /** {@code collision_flags(a0)}: {@code $11} until the first hit's window closes, then {@code $F}. */
    private int collisionSize = COLLISION_SIZE_INITIAL;

    /** {@code _unkFA88}: cleared by the init, set by {@code loc_7AD3A}; the orbs read it. */
    private boolean allOrbsShouldPop;
    /** {@code _unkFA89}: set by {@code loc_7ACA4}; the Mecha Sonic head reads it. */
    private boolean escaped;
    private boolean escaping;
    /** False through {@code Wait_FadeToLevelMusic}, true once {@code loc_7AC7A} has run. */
    private boolean escapeRunning;

    private final List<SszMtzBossOrbChild> orbs = new ArrayList<>();
    private SszMechaSonicHeadChild head;

    public SszMtzBossObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZMTZBoss");
    }

    private record RewindExtra(ObjectRefId headId, List<ObjectRefId> orbIds, int waitTimer,
                               boolean initExecuted, boolean dispatcherInstalled,
                               boolean entryApplied, boolean paletteLoaded,
                               int bossX, int bossY, int bossXVel, int bossYVel,
                               int drawX, int drawY, int phase, int laserState, int laserRecoil,
                               int laserShotsLeft, int laserTimer, int hoverPhase,
                               int armX, int armY, int armSignal, int armCycles,
                               boolean launchRequest, int liveOrbs, boolean headingRight,
                               boolean turnedOnce, boolean renderFlipped, int collisionSize,
                               boolean allOrbsShouldPop, boolean escaped, boolean escaping,
                               boolean escapeRunning, boolean invulnerable,
                               int invulnerabilityTimer)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        var table = context.identityTable();
        List<ObjectRefId> orbIds = new ArrayList<>();
        for (SszMtzBossOrbChild orb : orbs) {
            orbIds.add(table.map(t -> t.encodeObject(orb)).orElse(null));
        }
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                table.map(t -> t.encodeObject(head)).orElse(null), List.copyOf(orbIds),
                waitTimer, initExecuted, dispatcherInstalled, entryApplied, paletteLoaded,
                bossX, bossY, bossXVel, bossYVel, drawX, drawY, phase, laserState, laserRecoil,
                laserShotsLeft, laserTimer, hoverPhase, armX, armY, armSignal, armCycles,
                launchRequest, liveOrbs, headingRight, turnedOnce, renderFlipped, collisionSize,
                allOrbsShouldPop, escaped, escaping, escapeRunning,
                super.state.invulnerable, super.state.invulnerabilityTimer));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (!(snapshot.objectSubclassExtra() instanceof RewindExtra extra)) {
            return;
        }
        waitTimer = extra.waitTimer();
        initExecuted = extra.initExecuted();
        dispatcherInstalled = extra.dispatcherInstalled();
        entryApplied = extra.entryApplied();
        paletteLoaded = extra.paletteLoaded();
        bossX = extra.bossX();
        bossY = extra.bossY();
        bossXVel = extra.bossXVel();
        bossYVel = extra.bossYVel();
        drawX = extra.drawX();
        drawY = extra.drawY();
        phase = extra.phase();
        laserState = extra.laserState();
        laserRecoil = extra.laserRecoil();
        laserShotsLeft = extra.laserShotsLeft();
        laserTimer = extra.laserTimer();
        hoverPhase = extra.hoverPhase();
        armX = extra.armX();
        armY = extra.armY();
        armSignal = extra.armSignal();
        armCycles = extra.armCycles();
        launchRequest = extra.launchRequest();
        liveOrbs = extra.liveOrbs();
        headingRight = extra.headingRight();
        turnedOnce = extra.turnedOnce();
        renderFlipped = extra.renderFlipped();
        collisionSize = extra.collisionSize();
        allOrbsShouldPop = extra.allOrbsShouldPop();
        escaped = extra.escaped();
        escaping = extra.escaping();
        escapeRunning = extra.escapeRunning();
        super.state.invulnerable = extra.invulnerable();
        super.state.invulnerabilityTimer = extra.invulnerabilityTimer();
        head = (SszMechaSonicHeadChild) resolve(context, extra.headId());
        orbs.clear();
        for (ObjectRefId id : extra.orbIds()) {
            SszMtzBossOrbChild orb = (SszMtzBossOrbChild) resolve(context, id);
            if (orb != null) {
                orbs.add(orb);
            }
        }
    }

    private static Object resolve(RewindCaptureContext context, ObjectRefId id) {
        return id == null ? null : context.requireIdentityTable().resolveObject(id, true);
    }

    @Override
    protected void initializeBossState() {
        super.state.routine = ROUTINE_SETUP;
        super.state.hitCount = HIT_COUNT;
    }

    @Override protected int getInitialHitCount() { return HIT_COUNT; }

    @Override protected int getCollisionSizeIndex() { return collisionSize; }

    @Override protected void onHitTaken(int remainingHits) { }

    /** {@code sub_7ACF2} owns its own {@code $1C} window and its own three-colour flash. */
    @Override protected boolean usesBaseHitHandler() { return false; }

    @Override protected int getDefeatScore() { return DEFEAT_SCORE; }

    /**
     * {@code loc_7A71A} reads {@code routine(a0)} at its head and only reaches {@code sub_7ACF2}
     * after the selected arm has run, so {@code loc_7AD3A}'s
     * {@code move.l #Wait_FadeToLevelMusic,(a0)} lands after this slot is done for the frame and
     * the killing frame decrements nothing. Measured, comparison only: in the {@code hpz} segment
     * of {@code s3k-sonic-tails-complete-emeralds} the ship takes {@code Wait_FadeToLevelMusic} on
     * native frame 6651 and {@code loc_7AC92} on 6715 — {@code 64 = $3F + 1}.
     */
    @Override protected boolean defeatDeferralAppliesToThisBoss() { return true; }

    @Override protected boolean usesDefeatSequencer() { return false; }

    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }

    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }

    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }

    @Override public int getOnScreenHalfWidth() { return SHIP_HALF_WIDTH; }

    @Override public int getOnScreenHalfHeight() { return SHIP_HALF_HEIGHT; }

    /** No {@code Obj_WaitOffscreen} anywhere in this chain; {@code loc_7AC92} flies off screen. */
    @Override public boolean isPersistent() { return true; }

    @Override public int getX() { return drawX & 0xFFFF; }

    @Override public int getY() { return drawY & 0xFFFF; }

    @Override
    protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
        if (!initExecuted) {
            // Obj_SSZMTZBoss's own body, which ends jmp (PalLoad_Line1). move.l #Obj_Wait,(a0)
            // at its top only changes what the NEXT frame runs, so this costs no wait frame.
            applyEntryOnce();
            if (entryApplied) {
                initExecuted = true;
            }
            return;
        }
        if (escaping) {
            updateEscape();
            syncBossStatePosition();
            return;
        }
        if (!dispatcherInstalled) {
            // Obj_Wait: subq.w #1,$2E(a0) / bmi -> loc_84892 -> jmp $34(a0), and loc_7A712 is
            // move.l #loc_7A71A,(a0) / rts. Either way off_7A728 first dispatches next frame.
            if (--waitTimer >= 0) {
                return;
            }
            dispatcherInstalled = true;
            return;
        }
        // loc_7A71A: off_7A728 on routine(a0).
        if (super.state.routine == ROUTINE_SETUP) {
            applySetup();
            return;
        }
        // loc_7A7E2: off_7A7F0 on $26(a0), then sub_7AC06.
        switch (phase) {
            case STATE_DESCEND -> updateDescend();
            case STATE_PATROL -> updatePatrol();
            case STATE_RISE -> updateRise();
            case STATE_ARMS_UP -> updateArmsUp();
            case STATE_ARMS_DOWN -> updateArmsDown();
            case STATE_HIT_RISE -> updateHitRise();
            case STATE_HIT_RECOVER -> updateHitRecover();
            case STATE_LASER -> updateLaser();
            default -> { }
        }
        afterDispatch();
        syncBossStatePosition();
    }

    /**
     * {@code loc_7A72C}. {@code SetUp_ObjAttributes} is not used here — the routine writes the
     * mappings, art tile, render flags and priority itself and ends {@code addq.b #2,routine(a0)}
     * in the middle of the block, which is why every write below happens on the same execution.
     */
    private void applySetup() {
        super.state.routine = ROUTINE_RUNNING;
        bossX = SPAWN_X << 16;
        bossY = SPAWN_Y << 16;
        drawX = SPAWN_X;
        drawY = SPAWN_Y;
        bossXVel = 0;
        bossYVel = DESCENT_Y_VEL;
        collisionSize = COLLISION_SIZE_INITIAL;
        armCycles = ARM_CYCLES;
        hoverPhase = HOVER_PHASE_START;
        armX = ARM_REST;
        armY = ARM_REST;
        phase = STATE_DESCEND;
        laserRecoil = 0;
        head = spawnChild(() -> new SszMechaSonicHeadChild(
                new ObjectSpawn(getX() + SszMechaSonicHeadChild.CHILD_DX,
                        getY() + SszMechaSonicHeadChild.CHILD_DY, 0, 0, 0, false, 0), this));
        // jsr (AllocateObject).l / move.l #loc_7AD8A,(a1) / move.l a0,$34(a1): one slot, which
        // loc_7ADA2 then turns into the first of seven orbs and allocates the other six from.
        SszMtzBossOrbChild first = spawnChild(() -> new SszMtzBossOrbChild(
                new ObjectSpawn(getX(), getY(), 0, 0, 0, false, 0), this, 0));
        if (first != null) {
            orbs.add(first);
            for (int index = 1; index < SszMtzBossOrbChild.ORB_COUNT; index++) {
                final int slot = index;
                SszMtzBossOrbChild orb = spawnChild(() -> new SszMtzBossOrbChild(
                        new ObjectSpawn(getX(), getY(), 0, 0, 0, false, 0), this, slot));
                if (orb == null) {
                    // bne.s locret_7AE12: a failed allocation ends the loop with fewer orbs.
                    break;
                }
                orbs.add(orb);
            }
        }
        applyPairingWordOverwrite();
    }

    /** {@code loc_7A7C4}. */
    private void applyPairingWordOverwrite() {
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null) {
            ssz.setEggRoboFlyByBits(PAIRING_WORD_AFTER_SETUP);
        }
    }

    /** {@code loc_7A800}. */
    private void updateDescend() {
        moveBoss();
        // move.w (SSZ_MTZ_boss_Y_pos).w,$14(a0): routine 0 writes y_pos and leaves x_pos alone.
        drawY = (bossY >> 16) & 0xFFFF;
        if (bossWordY() < DESCENT_FLOOR_Y) {
            return;
        }
        phase = STATE_PATROL;
        bossYVel = 0;
        bossXVel = -PATROL_SPEED;
        headingRight = false;
        renderFlipped = false;
        if (Integer.compareUnsigned(playerX(), bossWordX()) >= 0) {
            bossXVel = PATROL_SPEED;
            headingRight = true;
            renderFlipped = true;
        }
    }

    /** {@code loc_7A874}. */
    private void updatePatrol() {
        moveBoss();
        if (!headingRight) {
            if (bossWordX() < PATROL_MIN_X) {
                headingRight = true;
                bossXVel = PATROL_SPEED;
                renderFlipped = true;
                if (markTurn()) {
                    phase = STATE_RISE;
                    bossYVel = -PATROL_SPEED;
                }
            }
        } else if (bossWordX() >= PATROL_MAX_X) {
            headingRight = false;
            bossXVel = -PATROL_SPEED;
            renderFlipped = false;
            if (markTurn()) {
                phase = STATE_RISE;
                bossYVel = -PATROL_SPEED;
            }
        }
        applyHoverPosition();
    }

    /**
     * {@code bset #6,$2E(a0) / beq.s} — {@code bset} sets Z from the bit's <em>old</em> value, so
     * the first turn takes the branch and only the second advances the routine.
     */
    private boolean markTurn() {
        boolean already = turnedOnce;
        turnedOnce = true;
        return already;
    }

    /** {@code loc_7A8F4}. */
    private void updateRise() {
        moveBoss();
        if (bossWordY() < HOVER_Y) {
            bossYVel = 0;
        }
        if (!headingRight) {
            if (bossWordX() < CENTRE_X) {
                bossXVel = 0;
            }
        } else if (bossWordX() >= CENTRE_X) {
            bossXVel = 0;
        }
        if ((bossXVel | bossYVel) == 0) {
            phase = STATE_ARMS_UP;
        }
        applyHoverPosition();
    }

    /** {@code loc_7A93C}. */
    private void updateArmsUp() {
        if (armX < ARM_RAISED) {
            armX++;
            armY++;
        } else {
            armY--;
            if (armY == 0) {
                phase = STATE_ARMS_DOWN;
            }
        }
        applyHoverPosition();
    }

    /** {@code loc_7A95A}. */
    private void updateArmsDown() {
        if (armX >= ARM_REST) {
            armX--;
        } else {
            armY++;
            if (armY >= ARM_REST) {
                bossYVel = DESCENT_Y_VEL;
                phase = STATE_DESCEND;
                turnedOnce = false;
            }
        }
        applyHoverPosition();
    }

    /** {@code loc_7A98A}: the hit reaction, which waits for every launched orb to be gone. */
    private void updateHitRise() {
        if (armY != 0) {
            armY--;
        } else {
            armSignal = -1;
        }
        if (armX >= ARM_REST) {
            armX--;
        }
        moveBoss();
        if (bossWordY() < HIGH_HOVER_Y) {
            bossYVel = 0;
        }
        if (liveOrbs == 0) {
            if (armSignal != 0) {
                armSignal = 0x80;
            }
            phase = STATE_HIT_RECOVER;
        }
        applyHoverPosition();
    }

    /** {@code loc_7A9D4}. */
    private void updateHitRecover() {
        if (armCycles != 0) {
            if (armSignal == 0) {
                if (armY < ARM_REST) {
                    armY++;
                } else {
                    bossYVel = DESCENT_Y_VEL;
                    phase = STATE_DESCEND;
                    turnedOnce = false;
                }
            }
        } else {
            bossYVel = -LUNGE_SPEED;
            bossXVel = -PATROL_SPEED;
            renderFlipped = false;
            if (headingRight) {
                bossXVel = PATROL_SPEED;
                renderFlipped = true;
            }
            phase = STATE_LASER;
            laserState = 0;
            turnedOnce = false;
            laserRecoil = 0;
        }
        applyHoverPosition();
    }

    /** {@code loc_7AA44}: the recoil pause skips the position copy and the hover entirely. */
    private void updateLaser() {
        if (laserRecoil != 0) {
            laserRecoil--;
            return;
        }
        switch (laserState) {
            case 0 -> updateLaserApproach();
            case 2 -> updateLaserDive();
            default -> updateLaserClimb();
        }
        applyHoverPosition();
    }

    /** {@code loc_7AA66}. */
    private void updateLaserApproach() {
        moveBoss();
        if (bossWordY() < HIGH_HOVER_Y) {
            bossYVel = 0;
        }
        if (!headingRight) {
            if (bossWordX() < LASER_TRIGGER_LEFT_X) {
                armLaserDive(true);
            }
        } else if (bossWordX() >= LASER_TRIGGER_RIGHT_X) {
            armLaserDive(false);
        }
    }

    private void armLaserDive(boolean flipped) {
        laserState = 2;
        bossYVel = LUNGE_SPEED;
        laserShotsLeft = LASER_SHOTS;
        laserTimer = LASER_INTERVAL;
        renderFlipped = flipped;
    }

    /** {@code loc_7AACE}. */
    private void updateLaserDive() {
        moveBoss();
        if (bossWordY() >= DESCENT_FLOOR_Y) {
            bossYVel = -LUNGE_SPEED;
            laserState = 4;
            // bchg #7,$2E(a0): the next pass climbs away from the side it dived on.
            headingRight = !headingRight;
        } else if (!headingRight) {
            if (bossWordX() < PATROL_MIN_X) {
                bossXVel = 0;
            }
        } else if (bossWordX() >= PATROL_MAX_X) {
            bossXVel = 0;
        }
        fireLasers();
    }

    /** {@code loc_7AB1A}. */
    private void updateLaserClimb() {
        moveBoss();
        if (bossWordY() < HOVER_Y) {
            bossXVel = headingRight ? PATROL_SPEED : -PATROL_SPEED;
        }
        if (bossWordY() < HIGH_HOVER_Y) {
            bossYVel = 0;
            laserState = 0;
        }
        fireLasers();
    }

    /** {@code sub_7AB56}. */
    private void fireLasers() {
        laserTimer = (laserTimer - 1) & 0xFFFF;
        if (laserTimer != 0 || laserShotsLeft == 0) {
            return;
        }
        laserShotsLeft--;
        laserRecoil = LASER_RECOIL_FRAMES;
        laserTimer = LASER_INTERVAL;
        // ChildObjDat_7AB80: two children at (-$C,-4) and (-$18,-4), the second subtype 1.
        for (int index = 0; index < SszMtzBossLaserChild.LASER_PAIR; index++) {
            final int subtype = index;
            final int dx = index == 0 ? SszMtzBossLaserChild.CHILD_DX_0
                    : SszMtzBossLaserChild.CHILD_DX_1;
            spawnChild(() -> new SszMtzBossLaserChild(new ObjectSpawn(
                    (getX() + (renderFlipped ? -dx : dx)) & 0xFFFF,
                    (getY() + SszMtzBossLaserChild.CHILD_DY) & 0xFFFF,
                    0, subtype, renderFlipped ? 1 : 0, false, 0), renderFlipped));
        }
    }

    /** {@code Boss_MoveObject}: 16.16 pair, 8.8 velocity, no gravity. */
    private void moveBoss() {
        bossX += bossXVel << 8;
        bossY += bossYVel << 8;
    }

    /** {@code loc_7A8DA}: {@code x_pos} from the pair, {@code y_pos} from {@code sub_7A85A}. */
    private void applyHoverPosition() {
        drawX = (bossX >> 16) & 0xFFFF;
        // asr.w #6 on a value the sine table gives as +/-$100: a four-pixel hover either way.
        int offset = TrigLookupTable.sinHex(hoverPhase & 0xFF) >> 6;
        drawY = ((bossY >> 16) + offset) & 0xFFFF;
        hoverPhase = (hoverPhase + HOVER_PHASE_STEP) & 0xFF;
    }

    /** {@code sub_7AC06}, which every arm falls into. */
    private void afterDispatch() {
        boolean windowJustArmed = updateHitWindow();
        if (!windowJustArmed) {
            return;
        }
        // st $39(a0): the next orb to update launches itself.
        launchRequest = true;
        if (armCycles != 0) {
            phase = STATE_HIT_RISE;
            bossYVel = -LUNGE_SPEED;
            armCycles--;
        }
        // loc_7AC42 is reached from both branches: the ship always stops moving sideways.
        bossXVel = 0;
    }

    /**
     * {@code sub_7ACF2}. The gate is {@code tst.b collision_flags(a0)}: the touch response zeroes
     * it on the frame a hit lands, which is exactly this engine's {@code state.invulnerable}.
     *
     * @return true on the frame {@code $1C(a0)} has just reached {@code $1F}, which is what
     *         {@code sub_7AC06} tests.
     */
    private boolean updateHitWindow() {
        if (super.state.defeated || !super.state.invulnerable) {
            return false;
        }
        int row = (super.state.invulnerabilityTimer & 1) != 0 ? FLASH_ROW_NORMAL : FLASH_ROW_SHIPPED;
        writeFlashRow(row);
        super.state.invulnerabilityTimer--;
        if (super.state.invulnerabilityTimer <= 0) {
            super.state.invulnerabilityTimer = 0;
            // move.b #$F,collision_flags(a0): the restore is not the $11 the init wrote.
            super.state.invulnerable = false;
            collisionSize = COLLISION_SIZE_RESTORED;
        }
        return super.state.invulnerabilityTimer == 0x1F;
    }

    /** {@code sub_7AD6A}: {@code CopyWordData_3} of three words into {@code word_7AD78}'s list. */
    private void writeFlashRow(int row) {
        var registry = services().paletteOwnershipRegistryOrNull();
        var level = services().currentLevel();
        var graphics = services().graphicsManager();
        S3kPaletteWriteSupport.applyContiguousPatch(registry, level, graphics,
                S3kPaletteOwners.SSZ_MTZ_BOSS_HIT_FLASH, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                0, FLASH_COLOR_A, segaWords(FLASH_TABLE[row]));
        S3kPaletteWriteSupport.applyContiguousPatch(registry, level, graphics,
                S3kPaletteOwners.SSZ_MTZ_BOSS_HIT_FLASH, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                0, FLASH_COLOR_B, segaWords(FLASH_TABLE[row + 1], FLASH_TABLE[row + 2]));
    }

    private static byte[] segaWords(int... words) {
        byte[] out = new byte[words.length * 2];
        for (int index = 0; index < words.length; index++) {
            out[index * 2] = (byte) (words[index] >> 8);
            out[index * 2 + 1] = (byte) words[index];
        }
        return out;
    }

    /** {@code loc_7AD3A}, reached through the base's {@code triggerDefeat}. */
    @Override
    protected void onDefeatStarted() {
        escaping = true;
        escapeRunning = false;
        waitTimer = DEFEAT_WAIT_FRAMES;
        // st (_unkFA88).w: every orb still off its orbit pops on its next pass.
        allOrbsShouldPop = true;
        // clr.b $38(a0): the arms collapse, which the orbit reads as a zero radius.
        armX = 0;
        // Gap, recorded in docs/status/s3k-known-bugs.md: loc_7AD3A also runs CreateChild1_Normal
        // over Child6_CreateBossExplosion with subtype 4 before it reaches BossDefeated, and
        // usesDefeatSequencer() is false here, so no explosion is drawn at all. The Green Hill
        // sibling carries the same gap.
    }

    /** {@code Wait_FadeToLevelMusic}, {@code loc_7AC7A}, {@code loc_7AC92} and {@code loc_7ACA4}. */
    private void updateEscape() {
        if (--waitTimer >= 0) {
            if (escapeRunning) {
                // MoveSprite2 on x_pos/y_pos themselves, not on the SSZ_MTZ_boss pair; bossX is
                // reused as that 16.16 accumulator from loc_7AC7A onwards.
                bossX += bossXVel << 8;
                drawX = (bossX >> 16) & 0xFFFF;
            }
            return;
        }
        if (!escapeRunning) {
            // loc_85674 then loc_7AC7A.
            escapeRunning = true;
            waitTimer = ESCAPE_FRAMES;
            // bset #0,render_flags(a0): loc_7AC7A always leaves to the right.
            renderFlipped = true;
            bossX = drawX << 16;
            bossXVel = ESCAPE_X_VEL;
            bossYVel = 0;
            return;
        }
        markBeaten();
    }

    /** {@code loc_7ACA4}. */
    private void markBeaten() {
        escaped = true;
        setBossFlag(false);
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null) {
            // st (Events_bg+$02).w: sub_575EA reads it as beaten and the $79:$F6 pad as its
            // release, which is what raises the sunk pad out of the arena floor.
            ssz.setEventsBgByte(0x02, 0xFF);
        }
        restoreLevelPaletteLine();
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    private void syncBossStatePosition() {
        super.state.x = getX();
        super.state.y = getY();
    }

    /** The init block: {@code Boss_flag}, {@code clr.w (_unkFA88).w} and the fight palette. */
    private void applyEntryOnce() {
        if (entryApplied || tryServices() == null) {
            return;
        }
        entryApplied = true;
        setBossFlag(true);
        allOrbsShouldPop = false;
        loadFightPalette();
    }

    private int bossWordX() { return (bossX >> 16) & 0xFFFF; }

    private int bossWordY() { return (bossY >> 16) & 0xFFFF; }

    private int playerX() {
        var sprite = services().spriteManager().getMainPlayable();
        return sprite == null ? 0 : sprite.getCentreX() & 0xFFFF;
    }

    // --- what the children read -------------------------------------------------------------

    /** {@code $38(a0)}: the orbit's horizontal radius. */
    public int armXForTest() { return armX; }

    /** {@code $3A(a0)}: the orbit's vertical radius, and the arm the raise cycle counts down. */
    public int armYForTest() { return armY; }

    /** {@code $3B(a0)}. */
    int armSignal() { return armSignal; }

    void clearArmSignal() { armSignal = 0; }

    /** {@code $39(a0)}: consumed by the first orb whose update sees it. */
    boolean takeLaunchRequest() {
        if (!launchRequest) {
            return false;
        }
        launchRequest = false;
        return true;
    }

    /** {@code addi.b #1,$30(a1)} / {@code subi.b #1,$30(a1)}. */
    void addLiveOrb() { liveOrbs = (liveOrbs + 1) & 0xFF; }

    void removeLiveOrb() { liveOrbs = (liveOrbs - 1) & 0xFF; }

    public int liveOrbsForTest() { return liveOrbs; }

    /** {@code tst.b (_unkFA88).w} in {@code sub_7B0C2}. */
    public boolean allOrbsShouldPop() { return allOrbsShouldPop; }

    @Override
    public boolean headShouldDelete() { return escaped; }

    @Override
    public boolean isRenderFlippedForTest() { return renderFlipped; }

    public int phaseForTest() { return phase; }

    public int laserStateForTest() { return laserState; }

    public int laserShotsLeftForTest() { return laserShotsLeft; }

    /** {@code $33(a0)}: the recoil pause, during which {@code sub_7AB56} is not called. */
    public int laserRecoilForTest() { return laserRecoil; }

    public int armCyclesForTest() { return armCycles; }

    public int waitTimerForTest() { return waitTimer; }

    public boolean initExecutedForTest() { return initExecuted; }

    public boolean dispatcherInstalledForTest() { return dispatcherInstalled; }

    public boolean isEscapingForTest() { return escaping; }

    public boolean hasEscaped() { return escaped; }

    public int hitWindowForTest() {
        return super.state.invulnerable ? super.state.invulnerabilityTimer : 0;
    }

    public int hitsRemainingForTest() { return super.state.hitCount; }

    public int collisionSizeForTest() { return collisionSize; }

    public List<SszMtzBossOrbChild> orbsForTest() { return List.copyOf(orbs); }

    public SszMechaSonicHeadChild headForTest() { return head; }

    /** {@code Boss_flag}; see {@link SszGhzBossObjectInstance#hasEscaped()}'s note on the gap. */
    private void setBossFlag(boolean active) {
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null) {
            ssz.setBossFlag(active);
        }
    }

    private SszZoneRuntimeState sszState() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }

    /** {@code PalLoad_Line1 Pal_SSZMTZOrbs}. */
    private void loadFightPalette() {
        if (paletteLoaded) {
            return;
        }
        try {
            byte[] line = services().rom()
                    .readBytes(Sonic3kConstants.PAL_SSZ_MTZ_ORBS_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.SSZ_MTZ_BOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1, line);
            paletteLoaded = true;
        } catch (IOException | RuntimeException ignored) {
            // Partial object harnesses may not provide a ROM or palette surface.
        }
    }

    /** {@code loc_7ACA4}: copy {@code Target_palette_line_2} back, i.e. reload {@code Pal_SSZ1}. */
    private void restoreLevelPaletteLine() {
        try {
            int entryAddr = Sonic3kConstants.PAL_POINTERS_ADDR
                    + PAL_POINTERS_SSZ1_INDEX * Sonic3kConstants.PAL_POINTER_ENTRY_SIZE;
            int sourceAddr = services().rom().read32BitAddr(entryAddr) & 0x00FFFFFF;
            byte[] line = services().rom().readBytes(sourceAddr, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.ZONE_EVENT_PALETTE_LOAD,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1, line, true);
        } catch (IOException | RuntimeException ignored) {
            // As above.
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(SHIP_MAPPING_FRAME, getX(), getY(), renderFlipped, false, 0);
        }
    }
}
