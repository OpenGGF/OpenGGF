package com.openggf.sprites.playable;

import java.util.Objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.PerObjectRewindSnapshot.SidekickCpuRewindExtra;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;
import com.openggf.physics.FrameCollisionPlan;
import com.openggf.sprites.managers.SpriteManager;

/**
 * CPU-controlled sidekick follower with daisy-chain support.
 */
public class SidekickCpuController {
    // ROM subtracts $44 bytes from Sonic_Pos_Record_Index in TailsCPU_Normal/Flying.
    // That index points at the next free 4-byte slot, while engine historyPos points
    // at the latest written slot, so the equivalent engine lookback is 16 frames.
    static final int ROM_FOLLOW_DELAY_FRAMES = 16;
    // Provider-approved object-order bridges may need the adjacent older sample
    // when the engine has already cleared transient push state before ROM would
    // have consumed it. ROM loc_13DD0 itself uses the same Stat_table entry as
    // the normal delayed control word (sonic3k.asm:26696-26705).
    private static final int OBJECT_ORDER_INPUT_DELAY_FRAMES = 17;
    /** Fallback used when the sidekick sprite has no PhysicsFeatureSet resolved yet
     *  (e.g. unit tests that bypass the full game-module bootstrap). Matches the S2
     *  value so existing S2 behaviour is preserved. */
    private static final int DEFAULT_HORIZONTAL_SNAP_THRESHOLD =
            PhysicsFeatureSet.SIDEKICK_FOLLOW_SNAP_S2;
    /** Fallback used when the sidekick sprite has no PhysicsFeatureSet resolved yet
     *  (e.g. unit tests that bypass the full game-module bootstrap). Matches the
     *  S2 placeholder so existing S2 traces/tests are unaffected. */
    private static final int DEFAULT_DESPAWN_X =
            PhysicsFeatureSet.SIDEKICK_DESPAWN_X_S2;
    private static final int JUMP_DISTANCE_TRIGGER = 64;
    private static final int JUMP_HEIGHT_THRESHOLD = 32;
    private static final int PUSH_STATUS_GRACE_FRAMES = 16;
    private static final int LOCAL_BELOW_TARGET_PUSH_BRIDGE_MAX_GRACE =
            PUSH_STATUS_GRACE_FRAMES - 4;
    private static final int LOCAL_BELOW_TARGET_PUSH_BRIDGE_MIN_GRACE = 3;
    private static final int OBJECT_ORDER_PUSH_BRIDGE_MIN_GRACE = 4;
    private static final int LOCAL_BELOW_TARGET_REBOUND_NUDGE_MIN_GSPEED = 0x80;
    private static final int FAST_LEADER_NO_LIVE_OBJECT_INPUT_NUDGE_MAX_UPWARD_GAP = 0x40;
    private static final int FAST_LEADER_NO_LIVE_OBJECT_LATE_GRACE_INPUT_NUDGE_MIN_DX = 0x90;
    private static final int FAST_LEADER_NO_LIVE_OBJECT_LATE_GRACE_INPUT_NUDGE_MAX_UPWARD_GAP = 0x50;
    private static final int FAST_LEADER_NO_LIVE_OBJECT_NUDGE_MAX_DX = 0xA0;
    private static final int PUSH_BRIDGE_LOCAL_OBJECT_BAND_Y = 0x80;
    private static final int UNDERWATER_PUSH_PULSE_MIN_X_SPEED = 0xC0;
    private static final int LEVEL_START_X_OFFSET = -0x20;
    private static final int LEVEL_START_Y_OFFSET = 4;
    /**
     * Distance band (pixels) around the spawn-relative placement target inside
     * which a present sidekick at a zone entry is treated as an already
     * established follower (see {@link #isEstablishedFollowerEntry()}). Sized to
     * the ROM follow envelope (the $20 lead offset plus the $30 S3K steering
     * snap threshold, sonic3k.asm:26694,26712,26729) so a genuine carried-in
     * follower passes while a sidekick that still needs a fresh spawn does not.
     */
    private static final int ESTABLISHED_FOLLOWER_ENTRY_BAND = 0x40;
    private static final int DESPAWN_TIMEOUT = 300;
    private static final int MANUAL_CONTROL_FRAMES = 600;
    /**
     * ROM {@code interact(a0)} defaults to SST slot 0 = MainCharacter
     * (s2.constants.asm:1101), whose id is {@code ObjID_Sonic} = 0x01
     * (s2.constants.asm:603). An un-ridden CPU Tails therefore dereferences
     * id 0x01 in {@code TailsCPU_CheckDespawn}'s {@code cmp.b id(a3),d0}
     * (s2.asm:39419), not "empty". The engine uses slot -1 for never-ridden,
     * so the despawn comparator substitutes this concrete default id.
     */
    private static final int ROM_DEFAULT_INTERACT_OBJECT_ID = 0x01;
    /**
     * When a once-ridden interact slot's object is deleted off-screen, ROM
     * {@code DeleteObject} zeroes the whole object RAM (s2.asm:30324-30339), so
     * {@code TailsCPU_CheckDespawn}'s {@code cmp.b id(a3),d0} (s2.asm:39419)
     * reads id 0 from the freed slot and the mismatch fires the despawn. The
     * engine collapses the freed slot to {@code -1}; the despawn comparator
     * substitutes this concrete zero (distinct from the never-ridden default
     * {@link #ROM_DEFAULT_INTERACT_OBJECT_ID} = 0x01).
     */
    private static final int ROM_DELETED_INTERACT_SLOT_ID = 0x00;
    private final int flyAnimId;
    private final int duckAnimId;
    private static final int INPUT_START = 0x20;
    private static final int DIRECTIONAL_INPUT_MASK = AbstractPlayableSprite.INPUT_UP
            | AbstractPlayableSprite.INPUT_DOWN
            | AbstractPlayableSprite.INPUT_LEFT
            | AbstractPlayableSprite.INPUT_RIGHT;
    private static final int MANUAL_HELD_MASK = AbstractPlayableSprite.INPUT_UP
            | AbstractPlayableSprite.INPUT_DOWN
            | AbstractPlayableSprite.INPUT_LEFT
            | AbstractPlayableSprite.INPUT_RIGHT
            | AbstractPlayableSprite.INPUT_JUMP;
    private static final int RESPAWN_BYPASS_MASK = AbstractPlayableSprite.INPUT_JUMP | INPUT_START;

    public enum State {
        INIT,
        SPAWNING,
        APPROACHING,
        NORMAL,
        PANIC,
        MGZ_RESCUE_WAIT,       // ROM Tails_CPU_routine $12: clear Ctrl_2_logical while physics continues
        CARRY_INIT,            // ROM carry init; MGZ boss transition uses Tails_CPU_routine $14
        CARRYING,              // ROM routine 0x0E / 0x20 - per-frame carry body
        // ROM Tails_CPU_routine $10 (loc_1408A, sonic3k.asm:26953-26972). Entered
        // when a throwaway carrier Tails drops a solo Sonic at the CNZ1/MHZ1 intro
        // (SpawnLevelMainSprites loc_68D8 spawns Obj_Tails into Player_2 for
        // Player_mode==1; loc_14068 routes the landing to $10 instead of routine 6).
        // Tails flies up-and-right off-screen, then deletes its own object slot.
        CARRY_FLYOFF,
        CATCH_UP_FLIGHT,       // ROM routine 0x02 (Tails_Catch_Up_Flying, sonic3k.asm:26474)
        FLIGHT_AUTO_RECOVERY,  // ROM routine 0x04 (Tails_FlySwim_Unknown, sonic3k.asm:26534)
        DORMANT_MARKER,        // ROM routine 0x0A (locret_13FC0); AIZ1 intro waits off-screen
        // ROM Tails OBJECT routine 0x06 (death state, dispatch loc_1578E in
        // sonic3k.asm:29263). Entered the frame Player_LevelBound calls
        // Kill_Character (sonic3k.asm:21136) on the sidekick.
        DEAD_FALLING
    }

    /**
     * Why despawn was invoked. Selects between the
     * Kill_Character-equivalent flow (LEVEL_BOUNDARY) that zeroes velocities
     * and runs a one-frame death routine before warping to the despawn
     * marker, and the simpler immediate-warp paths used by off-screen
     * timeout, S2 object-id-mismatch, and explicit cleanup callers.
     */
    public enum DespawnCause {
        LEVEL_BOUNDARY,
        OFF_SCREEN_TIMEOUT,
        OBJECT_ID_MISMATCH,
        EXPLICIT
    }

    private static final int SETTLED_FRAME_THRESHOLD = 15;

    @RewindTransient(reason = "owning sidekick reference is structural and restored from the live playable graph")
    private final AbstractPlayableSprite sidekick;
    @RewindTransient(reason = "sidekick leader link is structural and persists in the live daisy-chain graph")
    private AbstractPlayableSprite leader;
    @RewindTransient(reason = "respawn strategy is structural runtime behavior selected by the live controller setup")
    private SidekickRespawnStrategy respawnStrategy;

    private State state = State.INIT;
    private int despawnCounter;
    private int frameCounter;
    private int controlCounter;
    private int controller2Held;
    private int controller2Logical;
    private boolean inputUp;
    private boolean inputDown;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputJump;
    private boolean inputJumpPress;
    private int diagnosticGeneratedPressedInput;
    private boolean jumpingFlag;
    private int minXBound = Integer.MIN_VALUE;
    private int maxXBound = Integer.MIN_VALUE;
    private int minYBound = Integer.MIN_VALUE;
    private int maxYBound = Integer.MIN_VALUE;
    /**
     * Engine mirror of ROM {@code Tails_interact_ID} (s2.constants.asm): the id
     * byte snapshotted from the live object in the sidekick's persistent
     * {@code interact(a0)} slot, refreshed each non-despawning frame by
     * {@link #refreshInteractIdSnapshot}. {@code -1} means "no snapshot taken
     * yet" (sidekick has never stood on an object), which suppresses the
     * slot-id-mismatch despawn compare until a real id is observed.
     */
    private int lastInteractObjectId = -1;
    /**
     * S3K mirror of ROM {@code Tails_CPU_interact}: word 0 of the stood-on
     * object SST, sampled by {@code sub_13EFC} during Tails CPU control
     * (docs/skdisasm/sonic3k.asm:26816-26843). This is intentionally a CPU
     * global latch rather than a live projection of the final post-collision
     * ride state.
     */
    private int diagnosticS3kInteractWord;
    private int normalFrameCount;
    private int sidekickCount = 1;
    private int normalPushingGraceFrames;
    private boolean suppressNextAirbornePushFollowSteering;
    private boolean objectOrderGracePushBypassThisFrame;
    private int pendingGroundedFollowNudge;
    private int pendingGroundedFollowNudgeFrame = -1;
    private boolean aizIntroDormantMarkerPrimed;
    private boolean suppressNextLevelEventNormalMovement;
    private boolean catchUpUsesRomVisibleLevelFrameCounter;
    private boolean levelEventDormantMarkerReleasePending;
    private boolean skipPhysicsThisFrame;
    // Set by updateDeadFallingDeferredS2 when running the per-frame Obj02_Dead
    // ObjectMoveAndFall continuation (frame N+1+ of the deferred death-fall,
    // before crossing Tails_Max_Y_pos + $100). ROM Obj02_Dead (s2.asm:40736-40742)
    // runs ONLY ObjectMoveAndFall (no Tails_DoLevelCollision), so PlayableSpriteMovement
    // skips its post-kill collision pass when this flag is set. Cleared at the start of
    // every CPU update tick.
    private boolean deferredDespawnDeadFallContinuingThisFrame;
    private boolean bootstrapPreludePlacementApplied;
    /**
     * Leader centre coordinates captured at level-load time
     * ({@link #captureLevelStartLeaderAnchor()}, invoked from
     * {@code LevelManager.spawnSidekicks} — the engine analogue of ROM
     * {@code SpawnLevelMainSprites_SpawnPlayers}, sonic3k.asm:8359-8369). ROM
     * places the CPU sidekick at {@code Player_1 - $20, +4} and prefills
     * {@code Sonic_Pos_Record_Buf} while the leader still sits at its spawn
     * position, BEFORE the first {@code LevelLoop}/{@code Obj01} physics tick
     * moves it. The engine's {@link #updateInit()} placement is deferred to the
     * sidekick controller's first tick, which on a mid-run zone entry happens
     * AFTER the leader has already moved a pixel under held input; using the
     * live leader centre there shifts the spawn anchor (and the delayed-follow
     * Pos_table prefill) by that movement. Anchoring to the captured spawn
     * coordinates reproduces the ROM placement regardless of when the controller
     * first ticks. {@link Integer#MIN_VALUE} means "not captured" — fall back to
     * the live leader centre (the historical behaviour for paths that never run
     * {@code spawnSidekicks}, e.g. focused unit tests).
     */
    private int levelStartLeaderCentreX = Integer.MIN_VALUE;
    private int levelStartLeaderCentreY = Integer.MIN_VALUE;
    /**
     * Set by the trace-replay bootstrap orchestration when the segment's frame 0
     * is a one-time directly-compared seed (S3K complete-run mid-run zone entry
     * that carries residual sidekick follow state from the previous zone's exit
     * — HCZ/ICZ/LBZ). In that case the recorded frame-0 kinematic state has
     * already been seeded onto the sidekick and the leader has NOT been driven
     * through frame 0, so the controller's first tick (trace frame 1) must
     * continue from that seeded state rather than re-running the
     * SpawnLevelMainSprites placement reset (which would zero the carried-in
     * velocity / re-anchor the position). For natively-driven entries (MGZ/CNZ
     * fall-in, fresh level loads) this stays false and the normal spawn
     * placement runs, so those baselines are unaffected. Not a zone carve-out:
     * it reflects whether a mid-run residual entry state was loaded, the same
     * "load the save state at the BK2 start" signal the rest of the bootstrap
     * uses.
     */
    private boolean enteredFromSeedCompareFrame0;
    private boolean cpuFrameCounterFromStoredLevelFrame;
    private int nextCpuFrameCounterOverride = -1;
    private int catchUpFrameCounterOverride = -1;
    private int lastNormalAutoJumpPressFrameCounter = -1;
    /**
     * Models S3K's negative Ctrl_2_locked byte. Tails_Control skips
     * Tails_CPU_Control only when the byte is negative; positive locks still
     * call the CPU routine (sonic3k.asm:26196-26205).
     */
    private boolean controller2SignedLocked;
    private NormalStepDiagnostics latestNormalStepDiagnostics;
    private int diagnosticCtrl2HeldLatch;
    private int diagnosticCtrl2PressedLatch;

    // =====================================================================
    // Tails-carry-Sonic support (S3K-only; null trigger = feature disabled)
    // =====================================================================
    @RewindTransient(reason = "carry trigger is level/runtime-owned behavior installed by the live game module")
    private SidekickCarryTrigger carryTrigger;
    private short carryLatchX;
    private short carryLatchY;
    private boolean flyingCarryingFlag;
    private boolean carryParentagePending;
    private int releaseCooldown;
    /**
     * True only for a throwaway carrier spawned for a solo (no-sidekick) leader's
     * intro carry — ROM SpawnLevelMainSprites loc_68D8 writes Obj_Tails into the
     * Player_2 slot when Player_mode==1 at CNZ1/MHZ1 (sonic3k.asm:8190-8197). When
     * such a carrier drops its cargo on landing, ROM loc_14068 selects routine $10
     * (fly off + self-delete) instead of routine 6 (normal follow), so the engine
     * routes the release to {@link State#CARRY_FLYOFF} and removes the temporary
     * sprite once it leaves the screen. Structural (set at construction), so it is
     * not part of the rewind snapshot.
     */
    @RewindTransient(reason = "transient-carrier marker is structural, set when the throwaway intro Tails is spawned")
    private boolean transientCarrySidekick;
    /** Set once the CARRY_FLYOFF carrier has left the screen and been removed
     *  (ROM loc_140AC clears the object pointer). Comparison/test visibility only. */
    private boolean transientFlyoffDespawned;
    private boolean mgzCarryIntroAscend;
    private int mgzCarryFlapTimer;
    private boolean mgzReleasedChaseLatched;
    private short mgzReleasedChaseXAccel;
    private short mgzReleasedChaseYAccel;

    // =====================================================================
    // Tails flight/catch-up state (ROM Tails_CPU_flight_timer + steering state)
    // =====================================================================
    private int flightTimer;
    private int catchUpTargetX;
    private int catchUpTargetY;

    public SidekickCpuController(AbstractPlayableSprite sidekick) {
        this(sidekick, null);
    }

    public SidekickCpuController(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        this.sidekick = sidekick;
        this.leader = leader;
        if (sidekick.getCpuController() == null) {
            sidekick.setCpuController(this);
        }
        this.respawnStrategy = new TailsRespawnStrategy(this);
        this.flyAnimId = sidekick.resolveAnimationId(CanonicalAnimation.FLY);
        this.duckAnimId = sidekick.resolveAnimationId(CanonicalAnimation.DUCK);
    }

    public void update(int frameCount) {
        this.frameCounter = resolveCpuFrameCounter(frameCount);
        deferredDespawnDeadFallContinuingThisFrame = false;

        if (controller2SignedLocked) {
            clearInputs();
            carryParentagePending = false;
            if (sidekick.isObjectControlled() && !sidekick.isObjectControlAllowsCpu()) {
                mirrorRawController2LogicalForEndingPose();
            }
            beginNormalStepDiagnostics("ctrl2_signed_lock_skip");
            return;
        }

        boolean mgzReleasedCarryCooldown =
                state == State.CARRYING
                        && carryTrigger != null
                        && carryTrigger.usesMgzBossTransitionControl()
                        && !flyingCarryingFlag;

        // Decrement release cooldown every frame regardless of state (applies after carry).
        // MGZ's released carry path decrements inside routine $18 and returns for
        // that frame, matching loc_14534's byte 1(a2) cooldown gate.
        if (releaseCooldown > 0 && !mgzReleasedCarryCooldown) {
            releaseCooldown--;
        }

        if (leader == null) {
            clearInputs();
            carryParentagePending = false;
            return;
        }

        clearInputs();
        carryParentagePending = false;
        if ((controller2Held & MANUAL_HELD_MASK) != 0) {
            controlCounter = MANUAL_CONTROL_FRAMES;
        }

        switch (state) {
            case INIT                 -> updateInit();
            case SPAWNING             -> updateSpawning();
            case APPROACHING          -> updateApproaching();
            case NORMAL               -> updateNormal();
            case PANIC                -> updatePanic();
            case MGZ_RESCUE_WAIT      -> clearInputs();
            case CARRY_INIT           -> updateCarryInit();
            case CARRYING             -> updateCarrying();
            case CARRY_FLYOFF         -> updateCarryFlyoff();
            case CATCH_UP_FLIGHT      -> updateCatchUpFlight();
            case FLIGHT_AUTO_RECOVERY -> updateFlightAutoRecovery();
            case DORMANT_MARKER       -> clearInputs();
            case DEAD_FALLING         -> updateDeadFalling();
        }
    }

    private int resolveCpuFrameCounter(int fallbackFrameCount) {
        if (nextCpuFrameCounterOverride >= 0 && state == State.NORMAL) {
            int override = nextCpuFrameCounterOverride;
            nextCpuFrameCounterOverride = -1;
            cpuFrameCounterFromStoredLevelFrame = false;
            return override;
        }
        LevelManager levelManager = sidekick.currentLevelManager();
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs != null && fs.sidekickCpuUsesLevelFrameCounter() && fallbackFrameCount > 0) {
            // ROM increments Level_frame_counter before object/player CPU slots
            // (s2.asm:5092, sonic3k.asm:7889). SpriteManager passes that
            // already-incremented cadence into the normal sprite CPU path; the
            // LevelManager copy is stored later in the engine frame and can be
            // one tick stale for Tails' $3F jump gate.
            cpuFrameCounterFromStoredLevelFrame = false;
            return fallbackFrameCount;
        }
        if (fs != null && fs.sidekickCpuUsesLevelFrameCounter()
                && levelManager != null && levelManager.getFrameCounter() > 0) {
            // S3K Tails CPU reads (Level_frame_counter).w inside sprite CPU
            // handlers such as Tails_Catch_Up_Flying (sonic3k.asm:26474-26531).
            // Bootstrap paths that do not pass a sprite-frame cadence preload
            // LevelManager with the already visible ROM counter for the current
            // frame.
            cpuFrameCounterFromStoredLevelFrame = true;
            return levelManager.getFrameCounter();
        }
        cpuFrameCounterFromStoredLevelFrame = false;
        if (fallbackFrameCount > 0) {
            // ROM increments Level_frame_counter before object/player CPU slots
            // (s2.asm:5092, sonic3k.asm:7889). SpriteManager passes that
            // already-incremented cadence; LevelManager stores it later in the
            // engine frame and is one tick stale for Tails' $3F jump gate.
            return fallbackFrameCount;
        }
        if (levelManager != null && levelManager.getFrameCounter() > 0) {
            return levelManager.getFrameCounter();
        }
        if (levelManager != null && levelManager.getObjectManager() != null
                && levelManager.getObjectManager().getFrameCounter() > 0) {
            // Legacy object-manager update paths mirror the same cadence source
            // here when the level counter has not been initialized yet.
            return levelManager.getObjectManager().getFrameCounter();
        }
        return fallbackFrameCount;
    }

    private int resolvePanicPhaseCounter() {
        // ROM TailsCPU_Panic reads the low byte at Level_frame_counter+1; the
        // "+1" is the 68000 byte address within the word, not a frame increment
        // (S3K sonic3k.asm:26869-26884; S2 s2.asm:39122-39139). At CNZ f8958
        // the ROM-visible word is $22FF, so loc_13F94 keeps DOWN held for one
        // more frame and releases at $2300.
        return frameCounter;
    }

    public void setController2Input(int held, int logical) {
        controller2Held = held;
        controller2Logical = logical;
    }

    /**
     * Returns whether the RAW player-2 controller jump button was just-pressed
     * this frame (the engine equivalent of the {@code (Ctrl_2)} press low byte).
     * <p>
     * This is the manual second-controller input fed in via
     * {@link #setController2Input(int, int)} -- NOT the AI follow-steering jump
     * the controller synthesizes for the sidekick's own movement (the equivalent
     * of {@code Ctrl_2_Logical}, which the CPU writes from the delayed leader
     * input history). In 1-player Sonic+Tails mode no second controller is
     * plugged in, so {@code (Ctrl_2)} press bits are 0 every frame.
     * <p>
     * ROM objects that gate on the raw controller word for the Sidekick -- e.g.
     * Obj7F (MCZ vine switch, s2.asm:56491 {@code move.w (Ctrl_2).w,d0}) -- must
     * read this, not the buffered CPU jump, so the vine never releases Tails on
     * the leader's replayed follow jump.
     */
    public boolean isRawController2JumpJustPressed() {
        return (controller2Logical & AbstractPlayableSprite.INPUT_JUMP) != 0;
    }

    public void setController2SignedLocked(boolean locked) {
        controller2SignedLocked = locked;
    }

    public boolean isController2SignedLocked() {
        return controller2SignedLocked;
    }

    public void mirrorRawController2LogicalForEndingPose() {
        // Check_TailsEndPose clears Ctrl_2_locked, then Player_2's next
        // Tails_Control pass copies raw Ctrl_2 into Ctrl_2_logical before
        // Set_PlayerEndingPose's object_control=$81 freezes movement
        // (docs/skdisasm/sonic3k.asm:26196-26203,181919-181988).
        diagnosticCtrl2HeldLatch = controller2Held & MANUAL_HELD_MASK;
        diagnosticCtrl2PressedLatch = controller2Logical & MANUAL_HELD_MASK;
    }

    /**
     * Comparison-only trace replay diagnostic for the latest normal CPU step.
     * It is never used to drive gameplay state.
     */
    public NormalStepDiagnostics getLatestNormalStepDiagnostics() {
        return latestNormalStepDiagnostics;
    }

    public int getCurrentCpuFrameCounter() {
        return frameCounter;
    }

    /**
     * Comparison-only diagnostic mirror of ROM Tails CPU globals. These
     * accessors are intentionally read-only and are used by trace replay to
     * report CPU-state divergence before it echoes into position drift.
     */
    public int getDiagnosticControlCounter() {
        return controlCounter;
    }

    public int getDiagnosticRespawnCounter() {
        if (state == State.APPROACHING) {
            return respawnStrategy.diagnosticRespawnCounter(despawnCounter);
        }
        if (state == State.FLIGHT_AUTO_RECOVERY) {
            return flightTimer;
        }
        return despawnCounter;
    }

    public int getDiagnosticInteractId() {
        if (usesS3kPointerInteract()) {
            // S3K Tails_CPU_interact is a RAM word copied from a stood-on
            // object's routine pointer and cleared with object RAM
            // (docs/skdisasm/sonic3k.asm:5415,7621,26816-26843). The engine's
            // lastInteractObjectId is the S2 id-snapshot model, so S3K projects
            // the CPU-latched ROM code-pointer high word when known.
            return diagnosticS3kInteractWord & 0xFFFF;
        }
        return lastInteractObjectId;
    }

    private boolean usesS3kPointerInteract() {
        PhysicsFeatureSet fs = resolvedPhysicsFeatureSet();
        return fs != null
                && fs.sidekickDespawnUsesRidingInstanceLoss()
                && !fs.sidekickDespawnUsesObjectIdMismatch();
    }

    private PhysicsFeatureSet resolvedPhysicsFeatureSet() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs != null) {
            return fs;
        }
        GameModule module = sidekick.currentGameModule();
        if (module != null && module.getPhysicsProvider() != null) {
            return module.getPhysicsProvider().getFeatureSet();
        }
        return null;
    }

    private Integer currentS3kInteractWord() {
        ObjectInstance instance = sidekick.getLatchedSolidObjectInstance();
        if (instance == null || isLatchedRideSlotFreed(instance)) {
            return null;
        }
        if (instance instanceof RomObjectCodePointerProvider provider) {
            return provider.romObjectCodePointerHighWord() & 0xFFFF;
        }
        return null;
    }

    public int getDiagnosticRomCpuRoutine() {
        return romCpuRoutineForState(state);
    }

    public int getDiagnosticGeneratedHeldInput() {
        NormalStepDiagnostics d = latestNormalStepDiagnostics;
        if (state == State.NORMAL
                && d != null
                && d.frameCounter() == frameCounter
                && d.hasCpuResult()) {
            return d.generatedInput() & 0xFF;
        }
        return diagnosticCtrl2HeldLatch & 0xFF;
    }

    public int getDiagnosticGeneratedPressedInput() {
        NormalStepDiagnostics d = latestNormalStepDiagnostics;
        if (state == State.NORMAL
                && d != null
                && d.frameCounter() == frameCounter
                && d.hasCpuResult()) {
            return d.generatedPressedInput() & 0xFF;
        }
        return diagnosticCtrl2PressedLatch & 0xFF;
    }

    public int getDiagnosticFollowHistorySlot() {
        NormalStepDiagnostics d = latestNormalStepDiagnostics;
        return d != null && d.frameCounter() == frameCounter
                ? d.followHistorySlot()
                : -1;
    }

    public int getDiagnosticJumpingFlag() {
        return jumpingFlag ? 1 : 0;
    }

    public String formatLatestNormalStepDiagnostics() {
        if (latestNormalStepDiagnostics == null) {
            return "eng-tails-cpu none";
        }
        NormalStepDiagnostics d = latestNormalStepDiagnostics;
        return String.format(
                "eng-tails-cpu f=%d state=%s branch=%s hist=%d/%02d in=%04X stat=%02X push=%02X "
                        + "pre=obj%02X st%02X x=%04X.%04X xv%04X yv%04X gv%04X a=%02X "
                        + "gen=%04X jp=%s postCpu=obj%02X st%02X xv%04X yv%04X gv%04X "
                        + "x=%04X.%04X a=%02X nudge=%d postPhys=%s obj%02X st%02X "
                        + "xv%04X yv%04X gv%04X x=%04X.%04X a=%02X dx=%04X dy=%04X skip=%s grace=%d",
                d.frameCounter(),
                d.state(),
                d.followBranch(),
                d.followDelayFrames(),
                d.followHistorySlot(),
                d.recordedInput() & 0xFFFF,
                d.recordedStatus() & 0xFF,
                d.pushBypassStatus() & 0xFF,
                d.preObjectControl() & 0xFF,
                d.preStatus() & 0xFF,
                d.preCpuX() & 0xFFFF,
                d.preCpuXSubpixel() & 0xFFFF,
                d.preXVel() & 0xFFFF,
                d.preYVel() & 0xFFFF,
                d.preGroundVel() & 0xFFFF,
                d.preAngle() & 0xFF,
                d.generatedInput() & 0xFFFF,
                d.inputJumpPress(),
                d.postCpuObjectControl() & 0xFF,
                d.postCpuStatus() & 0xFF,
                d.postCpuXVel() & 0xFFFF,
                d.postCpuYVel() & 0xFFFF,
                d.postCpuGroundVel() & 0xFFFF,
                d.postCpuX() & 0xFFFF,
                d.postCpuXSubpixel() & 0xFFFF,
                d.postCpuAngle() & 0xFF,
                d.appliedFollowNudge(),
                d.postPhysicsRecorded() ? "seen" : "missing",
                d.postPhysicsObjectControl() & 0xFF,
                d.postPhysicsStatus() & 0xFF,
                d.postPhysicsXVel() & 0xFFFF,
                d.postPhysicsYVel() & 0xFFFF,
                d.postPhysicsGroundVel() & 0xFFFF,
                d.postPhysicsX() & 0xFFFF,
                d.postPhysicsXSubpixel() & 0xFFFF,
                d.postPhysicsAngle() & 0xFF,
                d.dx() & 0xFFFF,
                d.dy() & 0xFFFF,
                d.skipFollowSteering(),
                normalPushingGraceFrames);
    }

    public void recordDiagnosticPostPhysics() {
        if (latestNormalStepDiagnostics == null
                || latestNormalStepDiagnostics.frameCounter() != frameCounter) {
            return;
        }
        latestNormalStepDiagnostics = latestNormalStepDiagnostics.withPostPhysics(
                diagnosticStatusByte(),
                diagnosticObjectControlByte(),
                sidekick.getXSpeed(),
                sidekick.getYSpeed(),
                sidekick.getGSpeed(),
                sidekick.getCentreX(),
                (short) sidekick.getXSubpixelRaw(),
                sidekick.getAngle());
    }

    /**
     * Returns true only for a provider-approved object-order bridge that extends
     * S3K's Status_Push handoff after the live push bit has already cleared locally.
     * ROM loc_13DD0 branches from the current Status_Push bit and preserves the
     * already-loaded Ctrl_2 sample (sonic3k.asm:26702-26705,26775-26785); MGZ
     * F1466-F1470 uses that same no-input deceleration path, so this flag is
     * limited to bridge contexts exposed by the owning level-event provider.
     */
    public boolean usedObjectOrderGracePushBypassThisFrame() {
        return objectOrderGracePushBypassThisFrame;
    }

    private NormalStepDiagnostics beginNormalStepDiagnostics(String branch) {
        latestNormalStepDiagnostics = new NormalStepDiagnostics(
                frameCounter,
                state,
                branch,
                diagnosticStatusByte(),
                diagnosticObjectControlByte(),
                sidekick.getXSpeed(),
                sidekick.getYSpeed(),
                sidekick.getGSpeed(),
                sidekick.getCentreX(),
                (short) sidekick.getXSubpixelRaw(),
                sidekick.getAngle(),
                -1,
                -1,
                0,
                0,
                0,
                -1,
                -1,
                0,
                0,
                diagnosticStatusByte(),
                diagnosticObjectControlByte(),
                sidekick.getXSpeed(),
                sidekick.getYSpeed(),
                sidekick.getGSpeed(),
                sidekick.getCentreX(),
                (short) sidekick.getXSubpixelRaw(),
                sidekick.getAngle(),
                0,
                false,
                0,
                0,
                (short) 0,
                (short) 0,
                (short) 0,
                (short) 0,
                (short) 0,
                (byte) 0,
                false,
                false);
        return latestNormalStepDiagnostics;
    }

    private void finishNormalStepDiagnostics(NormalStepDiagnostics base,
                                             String branch,
                                             int followDelayFrames,
                                             int followHistorySlot,
                                             int recordedInput,
                                             int recordedStatus,
                                             int pushBypassStatus,
                                             int dx,
                                             int dy,
                                             boolean skipFollowSteering,
                                             int appliedFollowNudge) {
        finishNormalStepDiagnosticsWithCtrl2(base, branch,
                followDelayFrames, followHistorySlot, recordedInput, recordedStatus, pushBypassStatus,
                dx, dy, diagnosticGeneratedInput(), diagnosticGeneratedPressedInput,
                skipFollowSteering, appliedFollowNudge);
    }

    private void finishNormalStepDiagnosticsWithCtrl2(NormalStepDiagnostics base,
                                                      String branch,
                                                      int followDelayFrames,
                                                      int followHistorySlot,
                                                      int recordedInput,
                                                      int recordedStatus,
                                                      int pushBypassStatus,
                                                      int dx,
                                                      int dy,
                                                      int generatedInput,
                                                      int generatedPressedInput,
                                                      boolean skipFollowSteering,
                                                      int appliedFollowNudge) {
        diagnosticCtrl2HeldLatch = generatedInput & 0xFF;
        diagnosticCtrl2PressedLatch = generatedPressedInput & 0xFF;
        latestNormalStepDiagnostics = base.withCpuResult(
                branch,
                followDelayFrames,
                followHistorySlot,
                recordedInput,
                recordedStatus,
                pushBypassStatus,
                dx,
                dy,
                diagnosticCtrl2HeldLatch,
                diagnosticCtrl2PressedLatch,
                diagnosticStatusByte(),
                diagnosticObjectControlByte(),
                sidekick.getXSpeed(),
                sidekick.getYSpeed(),
                sidekick.getGSpeed(),
                sidekick.getCentreX(),
                (short) sidekick.getXSubpixelRaw(),
                sidekick.getAngle(),
                appliedFollowNudge,
                inputJumpPress,
                skipFollowSteering);
    }

    private int diagnosticGeneratedInput() {
        int input = 0;
        if (inputUp) input |= AbstractPlayableSprite.INPUT_UP;
        if (inputDown) input |= AbstractPlayableSprite.INPUT_DOWN;
        if (inputLeft) input |= AbstractPlayableSprite.INPUT_LEFT;
        if (inputRight) input |= AbstractPlayableSprite.INPUT_RIGHT;
        if (inputJump) input |= AbstractPlayableSprite.INPUT_JUMP;
        return input;
    }

    private int diagnosticStatusByte() {
        int status = 0;
        if (sidekick.getDirection() == Direction.LEFT) status |= AbstractPlayableSprite.STATUS_FACING_LEFT;
        if (sidekick.getAir()) status |= AbstractPlayableSprite.STATUS_IN_AIR;
        if (sidekick.getRolling()) status |= AbstractPlayableSprite.STATUS_ROLLING;
        if (sidekick.isOnObject()) status |= AbstractPlayableSprite.STATUS_ON_OBJECT;
        if (sidekick.getPushing()) status |= AbstractPlayableSprite.STATUS_PUSHING;
        if (sidekick.isInWater()) status |= AbstractPlayableSprite.STATUS_UNDERWATER;
        return status;
    }

    private int diagnosticObjectControlByte() {
        int objectControl = 0;
        if (sidekick.isObjectControlled()) objectControl |= 0x80;
        if (sidekick.isObjectControlAllowsCpu()) objectControl |= 0x40;
        if (sidekick.isObjectControlSuppressesMovement()) objectControl |= 0x01;
        return objectControl;
    }

    public record NormalStepDiagnostics(
            int frameCounter,
            State state,
            String followBranch,
            int preStatus,
            int preObjectControl,
            short preXVel,
            short preYVel,
            short preGroundVel,
            short preCpuX,
            short preCpuXSubpixel,
            byte preAngle,
            int followDelayFrames,
            int followHistorySlot,
            int recordedInput,
            int recordedStatus,
            int pushBypassStatus,
            int dx,
            int dy,
            int generatedInput,
            int generatedPressedInput,
            int postCpuStatus,
            int postCpuObjectControl,
            short postCpuXVel,
            short postCpuYVel,
            short postCpuGroundVel,
            short postCpuX,
            short postCpuXSubpixel,
            byte postCpuAngle,
            int appliedFollowNudge,
            boolean inputJumpPress,
            int postPhysicsStatus,
            int postPhysicsObjectControl,
            short postPhysicsXVel,
            short postPhysicsYVel,
            short postPhysicsGroundVel,
            short postPhysicsX,
            short postPhysicsXSubpixel,
            byte postPhysicsAngle,
            boolean skipFollowSteering,
            boolean postPhysicsRecorded) {

        boolean hasCpuResult() {
            return followDelayFrames != -1 || followHistorySlot != -1 || dx != -1 || dy != -1;
        }

        NormalStepDiagnostics withCpuResult(String branch,
                                            int followDelayFrames,
                                            int followHistorySlot,
                                            int recordedInput,
                                            int recordedStatus,
                                            int pushBypassStatus,
                                            int dx,
                                            int dy,
                                            int generatedInput,
                                            int generatedPressedInput,
                                            int postCpuStatus,
                                            int postCpuObjectControl,
                                            short postCpuXVel,
                                            short postCpuYVel,
                                            short postCpuGroundVel,
                                            short postCpuX,
                                            short postCpuXSubpixel,
                                            byte postCpuAngle,
                                            int appliedFollowNudge,
                                            boolean inputJumpPress,
                                            boolean skipFollowSteering) {
            return new NormalStepDiagnostics(frameCounter, state, branch,
                    preStatus, preObjectControl, preXVel, preYVel, preGroundVel,
                    preCpuX, preCpuXSubpixel, preAngle,
                    followDelayFrames, followHistorySlot,
                    recordedInput, recordedStatus, pushBypassStatus,
                    dx, dy, generatedInput, generatedPressedInput,
                    postCpuStatus, postCpuObjectControl, postCpuXVel, postCpuYVel, postCpuGroundVel,
                    postCpuX, postCpuXSubpixel, postCpuAngle, appliedFollowNudge,
                    inputJumpPress,
                    postPhysicsStatus, postPhysicsObjectControl, postPhysicsXVel, postPhysicsYVel,
                    postPhysicsGroundVel, postPhysicsX, postPhysicsXSubpixel, postPhysicsAngle,
                    skipFollowSteering, postPhysicsRecorded);
        }

        NormalStepDiagnostics withPostPhysics(int postPhysicsStatus,
                                              int postPhysicsObjectControl,
                                              short postPhysicsXVel,
                                              short postPhysicsYVel,
                                              short postPhysicsGroundVel,
                                              short postPhysicsX,
                                              short postPhysicsXSubpixel,
                                              byte postPhysicsAngle) {
            return new NormalStepDiagnostics(frameCounter, state, followBranch,
                    preStatus, preObjectControl, preXVel, preYVel, preGroundVel,
                    preCpuX, preCpuXSubpixel, preAngle,
                    followDelayFrames, followHistorySlot,
                    recordedInput, recordedStatus, pushBypassStatus,
                    dx, dy, generatedInput, generatedPressedInput,
                    postCpuStatus, postCpuObjectControl, postCpuXVel, postCpuYVel, postCpuGroundVel,
                    postCpuX, postCpuXSubpixel, postCpuAngle, appliedFollowNudge,
                    inputJumpPress,
                    postPhysicsStatus, postPhysicsObjectControl, postPhysicsXVel, postPhysicsYVel,
                    postPhysicsGroundVel, postPhysicsX, postPhysicsXSubpixel, postPhysicsAngle,
                    skipFollowSteering, true);
        }
    }

    private void updateInit() {
        // S3K Tails-carry hook (null trigger = no-op, keeps S1/S2 behaviour).
        if (carryTrigger != null && leader != null) {
            LevelManager lm = sidekick.currentLevelManager();
            if (lm != null) {
                int zone = lm.getCurrentZone();
                int act = lm.getCurrentAct();
                PlayerCharacter pc = resolvePlayerCharacter();
                if (carryTrigger.shouldEnterCarry(zone, act, pc)
                        && carryTrigger.isLeaderAtIntroPosition(leader)) {
                    // ROM loc_13A10 (sonic3k.asm:26414) for CNZ act 0:
                    //   move.w #$C,(Tails_CPU_routine).w
                    //   rts
                    // The INIT handler sets routine=0x0C and RETURNS. It does
                    // NOT fall through into loc_13FC2 (the 0x0C body). That
                    // body (which writes x_vel=$100) only runs on the NEXT
                    // tick. The same-frame fall-through that DOES exist is
                    // 0x0C -> 0x0E (loc_13FC2 -> loc_13FFA, no rts at the
                    // end of loc_13FC2); see updateCarryInit() which mirrors
                    // that fall-through by calling updateCarrying() directly.
                    carryTrigger.applyInitialPlacement(sidekick, leader);
                    // CNZ loc_13A5A sets status=$02 before returning
                    // (sonic3k.asm:26410-26415). The 0x0C body waits until
                    // the next CPU tick, but the current Tails object tick
                    // still runs airborne movement and applies the +$38
                    // first-frame gravity visible in the CNZ trace seed row.
                    sidekick.setAir(true);
                    sidekick.setXSpeed((short) 0);
                    sidekick.setYSpeed((short) 0);
                    sidekick.setGSpeed((short) 0);
                    state = State.CARRY_INIT;
                    return;
                }
            }
        }

        if (shouldEnterLevelEventDormantMarker()) {
            // ROM Tails_Init (sonic3k.asm:26101-26156) plus the loc_13A10
            // dormant-marker block (sonic3k.asm:26389-26397) run inside one
            // ROM tick: SpawnLevelMainSprites installs the Tails object,
            // Tails_Init seeds default fields, then Tails_Control dispatches
            // Tails_CPU_Control with routine 0, which calls sub_13ECA and
            // overwrites Tails_CPU_routine with $A. The trace records the
            // post-sub_13ECA position (0x7F00, 0) on the same frame Tails
            // becomes routine 2 in the index dispatch.
            //
            // The engine previously split this into two frames: a "prime"
            // tick that seeded the leader's Pos_table history via the
            // level-start placement, then an "apply" tick that wrote the
            // dormant marker. That mismatch surfaced as an off-by-one at
            // AIZ trace frame 290 (engine had Tails at level-start placement
            // while ROM had her at the dormant sentinel). Combine the two
            // phases so the marker lands on the same engine tick.
            initializeLevelStartSidekickPlacementIfNeeded();
            applyLevelEventDormantMarker();
            aizIntroDormantMarkerPrimed = false;
            return;
        }

        aizIntroDormantMarkerPrimed = false;

        if (isDormantMarkerSentinelEntry()) {
            // ROM keeps the CPU sidekick at the off-screen dormant-marker
            // sentinel ($7F00,0 via sub_13ECA, sonic3k.asm:26800-26809) with
            // Tails_CPU_routine == $0A (locret_13FC0, an empty rts —
            // sonic3k.asm:26374) when she enters a zone parked off-screen, e.g.
            // the ICZ snowboard intro. ROM runs no movement or physics on the
            // marker until a later routine flips her to catch-up flight. The
            // engine resets the controller to INIT on every level (re)load, so
            // a sidekick that entered already at the sentinel must re-enter the
            // dormant marker rather than the normal spawn placement (which would
            // re-anchor her to the leader and apply gravity). Semantic predicate
            // (no zone carve-out): the sidekick's carried-in centre is the
            // off-screen despawn sentinel.
            applyLevelEventDormantMarker();
            return;
        }

        boolean establishedFollowerEntry = isEstablishedFollowerEntry();
        if (establishedFollowerEntry) {
            // ROM only runs SpawnLevelMainSprites' Tails placement / kinematic
            // reset for a fresh spawn with Tails_CPU_routine == 0
            // (sonic3k.asm:8359-8369). When Tails is already an established CPU
            // follower at a mid-run zone entry (Tails_CPU_routine == 6, present
            // and within follow range of the leader), ROM preserves her
            // position, velocity, and status across the handoff and her CPU slot
            // simply continues TailsCPU_Normal. The engine resets the controller
            // to INIT on every level (re)load, so reproduce ROM by skipping the
            // destructive spawn-reset here: keep the carried-in position /
            // velocity / air state and only prefill the leader Pos_table from the
            // captured spawn anchor so the delayed-follow target reproduces ROM's
            // spawn-anchored ring (Tails held still for 16 frames).
            establishFollowerWithoutSpawnReset();
        } else {
            initializeLevelStartSidekickPlacementIfNeeded();
        }

        state = State.NORMAL;

        if (!establishedFollowerEntry && freshS3kSpawnUsesInitOnlyFrame()) {
            // S3K SpawnLevelMainSprites creates a fresh Obj_Tails with
            // routine=0; Tails_Init advances the routine and returns before
            // TailsCPU_Normal runs on the following object tick
            // (sonic3k.asm:8359-8369,26101-26156). Established mid-run
            // followers are already past routine 0 and continue immediately.
            return;
        }

        // S2 trace/bootstrap paths have historically compared after Obj02/Tails
        // init has already completed, while established S3K followers are not a
        // fresh routine-0 spawn. Continue those paths into normal CPU follow.
        updateNormal();
    }

    private boolean freshS3kSpawnUsesInitOnlyFrame() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        return fs != null && fs.sidekickRespawnEntersCatchUpFlight();
    }

    private void initializeLevelStartSidekickPlacement() {
        // Default updateInit path: keep the legacy "fill with current Sonic
        // centre" behaviour. Native title-card prelude paths that have already
        // reproduced the ROM Pos_table pre-fill go through
        // applyLevelStartSidekickPlacementForBootstrap() first.
        applyLevelStartSidekickPlacement(false);
    }

    /**
     * Native-prelude-aware variant of
     * {@link #initializeLevelStartSidekickPlacement()}: when level startup has
     * already established the ROM-accurate Pos_table pre-fill (S2 only today,
     * via {@link #applyLevelStartSidekickPlacementForBootstrap()}),
     * {@code updateInit} must NOT re-pre-fill the ring or it would obliterate
     * the rows that the native leader-record path has already written. Falls
     * back to the legacy reset for non-bootstrap paths.
     */
    private void initializeLevelStartSidekickPlacementIfNeeded() {
        if (bootstrapPreludePlacementApplied) {
            applyLevelStartSidekickPlacementSkipPrefill();
        } else {
            applyLevelStartSidekickPlacement(false);
        }
    }

    /**
     * Public bootstrap entry point used by S2 trace-replay setup (before the
     * title-card prelude runs). Establishes the ROM-accurate Pos_table
     * pre-fill on the leader and re-anchors each sidekick before the
     * sidekick-only prelude begins ticking, so the prelude's first
     * leader-record write goes to slot 0 of the freshly seeded ring rather
     * than overwriting the pre-fill from the inside of {@code updateInit}.
     */
    public void applyLevelStartSidekickPlacementForBootstrap() {
        applyLevelStartSidekickPlacement(true);
    }

    private void applyLevelStartSidekickPlacement(boolean useRomAccuratePrefill) {
        // S2 InitPlayers (s2.asm:5192-5195) and S3K SpawnLevelMainSprites
        // (s3.asm:6334-6337, sonic3k.asm:8364-8367) place Player_2 with centre
        // coordinates at Player_1 - $20 X, +4 Y. The engine's level-load
        // reanchor path uses sprite top-left coordinates, so correct the first
        // native CPU tick before follow AI reads the sidekick position.
        //
        // ROM runs this placement inside SpawnLevelMainSprites at level-load,
        // while the leader is still at its spawn position, BEFORE the first
        // LevelLoop physics tick moves it. The engine defers this to the
        // sidekick controller's first updateInit tick, which on a mid-run zone
        // entry can land AFTER the leader has already moved a pixel under held
        // input — so anchor to the leader's captured spawn centre when
        // available rather than the live (already-moved) centre.
        int anchorX = levelStartLeaderCentreX != Integer.MIN_VALUE
                ? levelStartLeaderCentreX
                : leader.getCentreX();
        int anchorY = levelStartLeaderCentreY != Integer.MIN_VALUE
                ? levelStartLeaderCentreY
                : leader.getCentreY();
        sidekick.setCentreXPreserveSubpixel((short) (anchorX + LEVEL_START_X_OFFSET));
        sidekick.setCentreYPreserveSubpixel((short) (anchorY + LEVEL_START_Y_OFFSET));

        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        lastInteractObjectId = -1; // ROM Tails_interact_ID unset until next UpdateObjInteract
        diagnosticS3kInteractWord = 0;
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        ObjectControlState.none().applyTo(sidekick);
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        // Preserve zone-event-set in-air state. S3K MGZ1 / HCZ1 / LRZ1 / SSZ
        // set status_InAir on the sidekick during applyZonePlayerState
        // (ROM sonic3k.asm:8132-8205 mirrors loc_6886 / loc_68A6 setting
        // Status_InAir on Player_2). Resetting to false here would
        // override the falling-intro state before physics applies the
        // first gravity tick. Leave the air state as set by level load.

        if (useRomAccuratePrefill) {
            // ROM Obj01_Init (s2.asm:35907-35918, sonic3k.asm:21936-21940)
            // temporarily applies the same Tails-spawn offset to Sonic's centre,
            // fills Sonic_Pos_Record_Buf 64 times via Sonic_RecordPos, then
            // restores Sonic's centre. The result is a pre-fill ring containing
            // the sidekick's spawn position rather than Sonic's actual spawn —
            // so the first ~16 frames of TailsCPU_Normal read targetX =
            // Tails' own x_pos (dx = 0, no follow acceleration). The S2
            // trace-replay bootstrap uses this branch so the prelude's first
            // 16 leader-record writes layer on top of the sidekick-offset
            // pre-fill rather than the live Sonic centre.
            //
            // Note: only the bootstrap path requests the ROM-accurate pre-fill
            // today. Trace replay setups without a recorded title-card prelude
            // (S1, S3K) keep the legacy "fill with current Sonic centre"
            // behaviour to avoid altering already-tuned sidekick CPU traces.
            leader.prefillPositionHistoryWithCentre(
                    (short) (anchorX + LEVEL_START_X_OFFSET),
                    (short) (anchorY + LEVEL_START_Y_OFFSET));
        } else {
            // The ROM CPU routine reads Sonic's delayed position buffer
            // (S2 s2.asm:38808-38815, S3K sonic3k.asm:26564-26565).
            // Trace/bootstrap level placement can move the leader after sprite
            // construction, so seed the engine's native buffer before the first
            // follow read instead of reading trace sidekick state back in.
            leader.resetPositionHistory();
        }
        bootstrapPreludePlacementApplied = true;
    }

    /**
     * Lighter-weight placement refresh used when the bootstrap path has
     * already populated the leader's Pos_table pre-fill. Re-anchors the
     * sidekick at the Tails-spawn offset and clears transient CPU counters
     * WITHOUT touching the leader's Pos_table — the bootstrap pre-fill plus
     * the prelude's per-frame Sonic_RecordPos writes are the authoritative
     * source for the ring at this point.
     */
    private void applyLevelStartSidekickPlacementSkipPrefill() {
        sidekick.setCentreXPreserveSubpixel((short) (leader.getCentreX() + LEVEL_START_X_OFFSET));
        sidekick.setCentreYPreserveSubpixel((short) (leader.getCentreY() + LEVEL_START_Y_OFFSET));

        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        lastInteractObjectId = -1; // ROM Tails_interact_ID unset until next UpdateObjInteract
        diagnosticS3kInteractWord = 0;
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        ObjectControlState.none().applyTo(sidekick);
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setAir(false);
    }

    /**
     * True when the CPU sidekick enters this zone already established as a
     * follower, so ROM does NOT re-run SpawnLevelMainSprites' placement /
     * kinematic reset (that only happens for a fresh spawn with
     * {@code Tails_CPU_routine == 0}, sonic3k.asm:8359-8369).
     *
     * <p>Semantic predicate (no zone/route/frame carve-out): the sidekick is
     * present (not parked at the off-screen despawn sentinel) and already sits
     * within follow range of where the spawn placement would put it relative to
     * the leader's captured spawn anchor. A genuinely fresh spawn has just been
     * placed at exactly that offset by {@code spawnSidekicks}; a level-event
     * intro routine (AIZ dormant marker / MGZ fall-in / CNZ carry) is filtered
     * out earlier in {@code updateInit} before this is reached. This only
     * suppresses the destructive reset; the normal follow routine still runs.
     */
    /**
     * True when the CPU sidekick enters this zone parked at the off-screen
     * dormant-marker sentinel (ROM {@code Tails_CPU_routine == $0A},
     * x_pos = $7F00 from sub_13ECA, sonic3k.asm:26800-26809,26374). Only
     * considered when a level-start spawn anchor was captured (so focused unit
     * tests that bypass {@code spawnSidekicks} keep the historical path) and the
     * sidekick CPU enters via the catch-up-flight feature set (S3K). Gated on
     * the semantic sentinel position, not the zone.
     */
    private boolean isDormantMarkerSentinelEntry() {
        if (!enteredFromSeedCompareFrame0 || levelStartLeaderCentreX == Integer.MIN_VALUE) {
            return false;
        }
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null || !fs.sidekickRespawnEntersCatchUpFlight()) {
            return false;
        }
        return sidekick.getCentreX() == resolveDespawnX();
    }

    private boolean isEstablishedFollowerEntry() {
        if (!enteredFromSeedCompareFrame0 || levelStartLeaderCentreX == Integer.MIN_VALUE) {
            // Not a mid-run seed-compared entry, or no captured spawn anchor
            // (focused unit tests that bypass spawnSidekicks): keep the
            // historical fresh-spawn placement path so natively-driven entries
            // (MGZ/CNZ fall-in, fresh level loads) and unit tests are unchanged.
            return false;
        }
        short despawnSentinelX = resolveDespawnX();
        if (sidekick.getCentreX() == despawnSentinelX) {
            // Parked at the off-screen sentinel ($7F00 etc.): not a live
            // follower — let the normal init/dormant paths handle it.
            return false;
        }
        int placementTargetX = levelStartLeaderCentreX + LEVEL_START_X_OFFSET;
        int placementTargetY = levelStartLeaderCentreY + LEVEL_START_Y_OFFSET;
        // Within a small follow envelope of the spawn-relative placement target.
        // A fresh spawn lands exactly on it; an established follower carried in
        // from the prior zone is within a few pixels of it (the delayed-follow
        // ring keeps Tails near the leader's spawn offset for the first frames).
        int dxToTarget = Math.abs(sidekick.getCentreX() - placementTargetX);
        int dyToTarget = Math.abs(sidekick.getCentreY() - placementTargetY);
        return dxToTarget <= ESTABLISHED_FOLLOWER_ENTRY_BAND
                && dyToTarget <= ESTABLISHED_FOLLOWER_ENTRY_BAND;
    }

    /**
     * Establishes the follower for a mid-run zone entry without running the
     * SpawnLevelMainSprites kinematic reset: preserves the carried-in
     * position / velocity / air state and only prefills the leader Pos_table
     * from the captured spawn anchor (sonic3k.asm:8359-8369,22166-22193) so the
     * delayed-follow target reproduces ROM's spawn-anchored ring.
     */
    private void establishFollowerWithoutSpawnReset() {
        // Apply the same centre-based spawn-anchor placement as a fresh spawn
        // (so the sidekick sits at the ROM centre coordinates Player_1 - $20, +4,
        // sonic3k.asm:8364-8367) and clear the transient CPU counters, but
        // PRESERVE the carried-in kinematic state (velocity / air). ROM does not
        // re-run SpawnLevelMainSprites' velocity reset for an established CPU
        // follower across a mid-run handoff — only her slot's TailsCPU_Normal
        // continues — so a Tails that entered already falling (HCZ, y_vel = $38)
        // keeps that velocity, while a freshly placed Tails (whose velocity is
        // already zero from spawnSidekicks) is unaffected.
        short carriedXSpeed = sidekick.getXSpeed();
        short carriedYSpeed = sidekick.getYSpeed();
        short carriedGSpeed = sidekick.getGSpeed();
        boolean carriedAir = sidekick.getAir();
        // The leader Pos_table ring was already prefilled from the spawn anchor
        // at level-load (captureLevelStartLeaderAnchor) and has since taken the
        // leader's real per-frame records; pass useRomAccuratePrefill=false so
        // the spawn-anchor branch does not re-prefill and clobber those recorded
        // slots (which would shift the delayed follow by a frame). With a
        // captured anchor present, that branch only re-establishes the spawn ring
        // when no live records exist — here they do, so guard it.
        applyEstablishedFollowerCentrePlacement();
        sidekick.setXSpeed(carriedXSpeed);
        sidekick.setYSpeed(carriedYSpeed);
        sidekick.setGSpeed(carriedGSpeed);
        sidekick.setAir(carriedAir);
    }

    /**
     * Centre-based spawn-anchor placement for an established follower: positions
     * the sidekick at the captured spawn anchor + level-start offset and clears
     * the transient CPU counters, WITHOUT resetting the leader Pos_table (it was
     * already prefilled at level-load and has taken live records since).
     */
    private void applyEstablishedFollowerCentrePlacement() {
        int anchorX = levelStartLeaderCentreX != Integer.MIN_VALUE
                ? levelStartLeaderCentreX
                : leader.getCentreX();
        int anchorY = levelStartLeaderCentreY != Integer.MIN_VALUE
                ? levelStartLeaderCentreY
                : leader.getCentreY();
        sidekick.setCentreXPreserveSubpixel((short) (anchorX + LEVEL_START_X_OFFSET));
        sidekick.setCentreYPreserveSubpixel((short) (anchorY + LEVEL_START_Y_OFFSET));
        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        // Established followers do not re-run SpawnLevelMainSprites; preserve
        // ROM-visible interact globals until the next TailsCPU_UpdateObjInteract
        // / sub_13EFC pass refreshes them.
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        ObjectControlState.none().applyTo(sidekick);
        bootstrapPreludePlacementApplied = true;
    }

    private boolean shouldEnterLevelEventDormantMarker() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null || !fs.sidekickRespawnEntersCatchUpFlight()) {
            return false;
        }
        // ROM loc_13A10 (sonic3k.asm:26389-26397): when Tails_CPU_Control runs
        // with routine=0 (uninitialized Tails) and Current_zone_and_act=0
        // (AIZ Act 1), the special AIZ1 dormant-marker branch fires
        // unconditionally — call sub_13ECA (write x_pos=$7F00/y_pos=0), set
        // Tails_CPU_routine=$A, object_control=$83. The Last_star_post_hit
        // pre-check at line 26390 only diverts to the standard intro recovery
        // path if a checkpoint was hit before — irrelevant on first AIZ1 entry
        // where Last_star_post_hit=0.
        //
        // The previous gate also required {@code !camera.isLevelStarted()} so
        // the marker only fired during the engine's AIZ intro cutscene window.
        // ROM does NOT gate on Level_started_flag here; Tails enters dormant
        // whenever her first CPU tick lands on AIZ1, regardless of whether the
        // intro cutscene is still running. The cutscene exit handoff
        // ({@link CutsceneKnucklesAiz1Instance#completeIntroExitHandoff}) sets
        // {@code levelStarted=true} before Tails sees her first INIT in some
        // replay timings (notably the AIZ trace recorded with bk2_frame_offset
        // 511 where Tails' first CPU tick lands AFTER the cutscene exits),
        // so the levelStarted gate would skip the dormant marker entirely and
        // leave Tails alive at the level-start offset instead of the
        // {@code (0x7F00, 0x0000)} sentinel ROM writes — drives the first
        // divergence at trace frame 290.
        LevelEventProvider provider = levelEventProvider();
        return provider != null && provider.shouldEnterSidekickDormantMarker(sidekick);
    }

    private void applyLevelEventDormantMarker() {
        // ROM loc_13A10 (sonic3k.asm:26389-26397) special-cases
        // Current_zone_and_act=0: call sub_13ECA, then overwrite
        // Tails_CPU_routine with $0A and object_control with $83.
        state = State.DORMANT_MARKER;
        despawnCounter = 0;
        controlCounter = 0;
        flightTimer = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        sidekick.setAir(true);
        sidekick.setCentreXPreserveSubpixel(resolveDespawnX());
        sidekick.setCentreYPreserveSubpixel((short) 0);
        sidekick.setDoubleJumpFlag(0);
        sidekick.setControlLocked(true);
        ObjectControlState.nativeBit7FullControl().applyTo(sidekick);
        sidekick.setForcedAnimationId(flyAnimId);
        lastInteractObjectId = -1; // ROM Tails_interact_ID unset until next UpdateObjInteract
        diagnosticS3kInteractWord = 0;
    }

    /**
     * Applies the AIZ1 level-event dormant marker during level bootstrap, before
     * visual trace playback can render skipped pre-LevelLoop frames.
     */
    public void applyLevelEventDormantMarkerForBootstrap() {
        initializeLevelStartSidekickPlacementIfNeeded();
        applyLevelEventDormantMarker();
        aizIntroDormantMarkerPrimed = false;
    }

    /**
     * Returns true when {@link #updateDeadFallingDeferredS2()} ran the
     * below-threshold Obj02_Dead continuation step this frame. The S2
     * deferred-despawn flow keeps Tails in DEAD_FALLING for N+1..N+threshold
     * frames; on each of those frames ROM {@code Obj02_Dead}
     * (docs/s2disasm/s2.asm:40736-40742) executes
     * {@code jsr (ObjectMoveAndFall).l} but does NOT call
     * {@code Tails_DoLevelCollision} — Tails simply falls without colliding
     * with terrain or moving solids. The kill frame itself (frame N) runs
     * through ROM {@code Obj02_MdAir} (s2.asm:39259-39274) which DOES call
     * {@code Tails_DoLevelCollision} as its final step, so it stays in the
     * engine's post-kill collision pass.
     */
    public boolean isDeferredDespawnDeadFallContinuingThisFrame() {
        return deferredDespawnDeadFallContinuingThisFrame;
    }

    public boolean deadFallBypassesScreenYWrapValue() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        return fs != null && fs.sidekickDeathUsesDeferredDespawn();
    }

    /**
     * S2 can also enter Tails' routine-6 dead fall through generic damage /
     * crush paths that set the sprite's {@code dead} flag without routing the
     * CPU controller through {@link State#DEAD_FALLING}. ROM still dispatches
     * those frames through {@code Obj02_Dead}: when
     * {@code y_pos > Tails_Max_Y_pos + $100}, it branches to
     * {@code TailsCPU_Despawn} before the same frame's
     * {@code ObjectMoveAndFall} (s2.asm:40736-40759, 39043-39052).
     */
    public boolean applyDeferredGenericDeadDespawnIfCrossed() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null || !fs.sidekickDeathUsesDeferredDespawn() || !sidekick.getDead()) {
            return false;
        }
        int killPlane = getMaxYBound(Integer.MIN_VALUE);
        if (killPlane == Integer.MIN_VALUE || sidekick.getCentreY() <= killPlane + 0x100) {
            return false;
        }
        short oldXSpeed = sidekick.getXSpeed();
        short oldYSpeed = sidekick.getYSpeed();
        applyDespawnMarker();
        sidekick.move(oldXSpeed, oldYSpeed);
        sidekick.setYSpeed((short) (oldYSpeed + 0x38));
        return true;
    }

    public boolean consumeSkipPhysicsThisFrame() {
        boolean result = skipPhysicsThisFrame;
        skipPhysicsThisFrame = false;
        return result;
    }

    /**
     * Per-game snap threshold for the follow-AI input override in updateNormal().
     *
     * <p>Read from the sidekick's physics feature set (ROM parity). Falls back
     * to the S2 default (0x10) when no feature set is resolved yet — this only
     * happens in unit tests that construct a standalone {@code AbstractPlayableSprite}
     * without a game module, and those tests assert the existing S2 threshold.
     */
    private int resolveFollowSnapThreshold() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null) {
            return DEFAULT_HORIZONTAL_SNAP_THRESHOLD;
        }
        return fs.sidekickFollowSnapThreshold();
    }

    /**
     * Per-game off-screen marker X-position written by {@link #triggerDespawn()}.
     *
     * <p>Read from the sidekick's physics feature set (ROM parity). Falls back
     * to the S2 placeholder ({@code 0x4000}) when no feature set is resolved
     * yet — this only happens in unit tests that construct a standalone
     * {@code AbstractPlayableSprite} without a game module, and those tests
     * assert the existing S2 placeholder value.
     */
    private short resolveDespawnX() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null) {
            return (short) DEFAULT_DESPAWN_X;
        }
        return (short) fs.sidekickDespawnX();
    }

    /**
     * Whether the sidekick CPU post-kill flow defers the despawn warp until
     * the body falls below the death-routine marker threshold.
     *
     * <p>Read from the sidekick's physics feature set (ROM parity). Falls
     * back to {@code false} (legacy immediate-warp semantics) when no feature
     * set is resolved. This matches the historical engine behaviour and
     * the existing unit-test assertions in
     * {@code TestSidekickCpuDespawnParity}, which were calibrated against
     * the immediate-warp baseline.
     */
    private boolean resolveSidekickDeathUsesDeferredDespawn() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null) {
            return false;
        }
        return fs.sidekickDeathUsesDeferredDespawn();
    }

    private PlayerCharacter resolvePlayerCharacter() {
        GameModule gameModule = sidekick.currentGameModule();
        if (gameModule != null) {
            LevelEventProvider lep = gameModule.getLevelEventProvider();
            if (lep instanceof AbstractLevelEventManager alem) {
                return alem.getPlayerCharacter();
            }
        }
        return PlayerCharacter.SONIC_AND_TAILS;
    }

    private void updateSpawning() {
        // Use effective leader for respawn checks — if our direct leader is also
        // despawned, chain-heal to whoever IS available (allows parallel respawn
        // instead of sequential cascade).
        AbstractPlayableSprite target = getEffectiveLeader();
        if (target == null || target.getDead()) {
            return;
        }
        if ((controller2Logical & RESPAWN_BYPASS_MASK) != 0) {
            respawnToApproaching(target);
            return;
        }
        if ((frameCounter & 0x3F) != 0) {
            return;
        }
        // Per-game grounded-leader gate: S2 TailsCPU_Spawning checks for
        // grounded / not in water / not roll-jumping (s2.asm:38751-38762);
        // S3K Tails_Catch_Up_Flying does NOT (sonic3k.asm:26474-26486) —
        // it only honours the 64-frame gate, leader.object_control bit 7,
        // and leader.Status_Super. Without gating, CNZ's catch-up handover
        // never fires because Sonic stays airborne after the carry release
        // and the engine's SPAWNING state would block forever.
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean strictGate = fs == null || fs.sidekickSpawningRequiresGroundedLeader();
        if (target.isObjectControlled() || (strictGate && target.isObjectControlSuppressesMovement())) {
            return;
        }
        if (strictGate) {
            if (target.getAir() || target.getRollingJump() || target.isInWater() || target.isPreventTailsRespawn()) {
                return;
            }
        }
        respawnToApproaching(target);
    }

    private void respawnToApproaching(AbstractPlayableSprite target) {
        boolean started = respawnStrategy.beginApproach(sidekick, target);
        if (!started) {
            return; // Strategy can't start — stay in SPAWNING
        }
        state = State.APPROACHING;
        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        suppressNextAirbornePushFollowSteering = false;
    }

    void returnApproachToSpawningAfterFlyingTimeout() {
        state = State.SPAWNING;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        suppressNextAirbornePushFollowSteering = false;
    }

    private void updateApproaching() {
        if (!respawnStrategy.handlesApproachDespawn() && checkDespawn()) {
            normalPushingGraceFrames = 0;
            suppressNextAirbornePushFollowSteering = false;
            return;
        }

        AbstractPlayableSprite effectiveLeader = getEffectiveLeader();
        if (effectiveLeader == null) {
            return;
        }
        if (respawnStrategy.updateApproaching(sidekick, effectiveLeader, frameCounter)) {
            respawnStrategy.onApproachComplete(sidekick, effectiveLeader);
            sidekick.setForcedAnimationId(-1);
            sidekick.setControlLocked(false);
            ObjectControlState.none().applyTo(sidekick);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setMoveLockTimer(0);
            sidekick.setHurt(false);
            sidekick.setAir(true);
            state = State.NORMAL;
            normalFrameCount = 0;
            despawnCounter = Math.max(0, respawnStrategy.consumeApproachDespawnCarryFrames());
        }
    }

    private void updateNormal() {
        normalFrameCount++;
        boolean currentPushing = sidekick.getPushing();
        NormalStepDiagnostics diagnostics = beginNormalStepDiagnostics("entry");

        if (leader.getDead()) {
            PhysicsFeatureSet featureSet = sidekick.getPhysicsFeatureSet();
            // ROM loc_13D4A (sonic3k.asm:26656-26665):
            //   cmpi.b #6, (Player_1+routine).w
            //   blo.s  loc_13D78               ; continue NORMAL if routine < 6
            //   move.w #4, (Tails_CPU_routine).w
            // `blo.s` is branch-if-lower (unsigned <); the fall-through path
            // therefore fires only when Sonic's routine byte is >= 6, which
            // engine-side is {@code leader.getDead()}. Routine 0x04 is the
            // hurt bounce (before a potential death) — that case is NOT
            // covered by this ROM branch; Tails stays in NORMAL and the
            // follow AI continues to track the bouncing Sonic. An earlier
            // iteration of this branch also called leader.isHurt() here,
            // which mis-routed AIZ1's Rhinobot-hurt sequence (Sonic hurt
            // for ~43 frames at F1047+) into a spurious flight transition
            // and caused a new first-divergence at AIZ frame 1611.
            flightTimer = 0;
            normalPushingGraceFrames = 0;
            suppressNextAirbornePushFollowSteering = false;
            if (respawnStrategy instanceof TailsRespawnStrategy tailsStrategy
                    && (featureSet == null || !featureSet.sidekickRespawnEntersCatchUpFlight())) {
                tailsStrategy.beginDeadLeaderFlight(sidekick, leader);
                state = State.APPROACHING;
                controlCounter = 0;
                despawnCounter = 0;
                normalFrameCount = 0;
                jumpingFlag = false;
                finishNormalStepDiagnostics(diagnostics, "leader_dead", -1, -1,
                        0, 0, 0, 0, 0, false, 0);
                return;
            }
            // S2 TailsCPU_Normal writes obj_control=$81 on dead-Sonic
            // recovery (s2.asm:38910-38915); S3K loc_13D4A does the same
            // before entering Tails_FlySwim_Unknown (sonic3k.asm:26656-26665).
            ObjectControlState.nativeBit7FullControl().applyTo(sidekick);
            sidekick.setAir(true);
            sidekick.setDoubleJumpFlag(1);
            sidekick.setForcedAnimationId(flyAnimId);
            state = State.FLIGHT_AUTO_RECOVERY;
            finishNormalStepDiagnostics(diagnostics, "leader_dead", -1, -1,
                    0, 0, 0, 0, 0, false, 0);
            return;
        }
        if (sidekick.getDead()) {
            normalPushingGraceFrames = 0;
            suppressNextAirbornePushFollowSteering = false;
            finishNormalStepDiagnostics(diagnostics, "sidekick_dead", -1, -1,
                    0, 0, 0, 0, 0, false, 0);
            return;
        }
        PhysicsFeatureSet featureSet = sidekick.getPhysicsFeatureSet();
        if (sidekick.isHurt()
                && featureSet != null
                && featureSet.sidekickNormalCpuSkipsHurtRoutine()) {
            // The off-screen respawn/despawn timer is owned by the CPU control
            // path (TailsCPU_CheckDespawn). Both games dispatch a hurt sidekick
            // to a routine that does NOT call that path, so the timer freezes
            // while Tails is in the hurt routine:
            //   - S3K Tails_Index dispatches routine 4 to the hurt/object path
            //     instead of Tails_Control (docs/skdisasm/sonic3k.asm:26091-26096).
            //     sub_13EFC is called only from Tails_Control's normal CPU route
            //     (sonic3k.asm:26159-26190,26816-26833).
            //   - S2 Obj02_Index dispatches routine 4 to Obj02_Hurt
            //     (docs/s2disasm/s2.asm:38883-38891), which runs ObjectMove /
            //     Tails_HurtStop / Tails_LevelBound / Tails_RecordPos / Animate /
            //     DisplaySprite and returns WITHOUT ever reaching Obj02_Control ->
            //     TailsCPU_Control -> TailsCPU_CheckDespawn
            //     (docs/s2disasm/s2.asm:41057-41073, vs the normal-routine call at
            //     38964/39268). So Tails_respawn_counter does NOT tick during the
            //     hurt routine. Verified against the MCZ1 level-select BizHawk trace:
            //     Tails is hurt off-screen for gfc 0x0967-0x0994 (~45 frames) and
            //     Tails_respawn_counter freezes at 0xBA the whole time, leaving it
            //     at 0xFF (255) < $12C (300) at the engine's spurious-despawn frame.
            updateNormalPushingGrace(currentPushing);
            // Obj02_Hurt bypasses Obj02_Control entirely, so ROM-visible
            // Ctrl_2_Logical keeps the last word written by TailsCPU_Normal
            // rather than being cleared by the skipped CPU path.
            finishNormalStepDiagnosticsWithCtrl2(diagnostics, "sidekick_hurt_object_routine", -1, -1,
                    0, 0, 0, 0, 0,
                    diagnosticCtrl2HeldLatch, diagnosticCtrl2PressedLatch, false, 0);
            return;
        }
        if (checkDespawn()) {
            finishNormalStepDiagnostics(diagnostics, "despawn", -1, -1,
                    0, 0, 0, 0, 0, false, 0);
            return;
        }
        if (controlCounter != 0) {
            applyManualControl();
            updateNormalPushingGrace(currentPushing);
            finishNormalStepDiagnostics(diagnostics, "manual_control", -1, -1,
                    controller2Held, 0, 0, 0, 0, false, 0);
            return;
        }
        // ROM Tails_Normal Part 2 entry sonic3k.asm:26672:
        //   tst.b   object_control(a0)
        //   bmi.w   loc_13EBE          ; only branch on sign bit (bit 7)
        // ROM's `bmi.w` only suppresses the CPU controller when bit 7 of
        // object_control is set (flight $81, despawn $81, super $83, debug
        // $83). Bits 0-6 (CNZ wire cage's $42, MGZ twisting loop's $43,
        // etc.) leave Tails_CPU_Control running so the auto-jump trigger
        // at loc_13E9C can still fire while the player is "stuck" on the
        // controlling object — that's how ROM launches Tails off the CNZ
        // wire cage (CNZ1 trace F1791: cage's loc_33ADE reads
        // Ctrl_2_logical=$78 set by the auto-jump trigger).
        // Engine's setObjectControlled(true) maps to ANY bit set, so the
        // ROM-bit-7 distinction is carried via objectControlAllowsCpu —
        // bit-7 callers leave it false (default), bits 0-6 callers set
        // it true. See AbstractPlayableSprite#setObjectControlAllowsCpu.
        if (sidekick.isObjectControlled() && !sidekick.isObjectControlAllowsCpu()) {
            updateNormalPushingGrace(currentPushing);
            finishNormalStepDiagnostics(diagnostics, "object_control_bit7", -1, -1,
                    0, 0, 0, 0, 0, false, 0);
            return;
        }
        if (leader.isWallCling()) {
            // S3K loc_13D78 tests Player_1 status_tertiary with BMI before
            // loading Pos_table/Stat_table for Tails normal CPU control
            // (sonic3k.asm:26672-26675). MGZ top platform sets bit 7 while
            // Sonic is grabbed (sonic3k.asm:71831-71835), so P2 must not start
            // follow steering until the platform release clears it.
            updateNormalPushingGrace(currentPushing);
            finishNormalStepDiagnostics(diagnostics, "leader_status_tertiary_bit7", -1, -1,
                    0, 0, 0, 0, 0, false, 0);
            return;
        }

        if (sidekick.getMoveLockTimer() > 0 && sidekick.getGSpeed() == 0) {
            state = State.PANIC;
            normalFrameCount = 0;
        }

        AbstractPlayableSprite effectiveLeader = getEffectiveLeader();
        if (effectiveLeader == null) {
            updateNormalPushingGrace(currentPushing);
            finishNormalStepDiagnostics(diagnostics, "no_effective_leader", -1, -1,
                    0, 0, 0, 0, 0, false, 0);
            return;
        }
        int followStatDelayFrames = resolveFollowStatDelayFrames();
        short recordedInput = effectiveLeader.getInputHistory(followStatDelayFrames);
        boolean recordedJumpPress = delayedJumpPress(effectiveLeader, followStatDelayFrames, recordedInput);
        byte recordedStatus = effectiveLeader.getStatusHistory(followStatDelayFrames);
        int targetX = effectiveLeader.getCentreX(ROM_FOLLOW_DELAY_FRAMES);
        int targetY = effectiveLeader.getCentreY(ROM_FOLLOW_DELAY_FRAMES);

        // ROM loc_13DA6 (sonic3k.asm:26688-26694): bias the leader-x history
        // target a fixed amount to the LEFT before computing dx, so Tails
        // tracks slightly behind Sonic on flat ground. Suppressed when:
        //   - leader's Status_OnObj bit is set (not just a stale object reference;
        //     sonic3k.asm:26690-26691) — no useful position to lead to.
        //   - leader.ground_vel >= $400 (sonic3k.asm:26692-26693) — leader
        //     is already faster than the follower can chase.
        // S2 has no equivalent (s2.asm:38933 reads d2 directly), so the
        // offset is gated by PhysicsFeatureSet.sidekickFollowLeadOffset().
        //
        // The OnObj read here is mid-frame relative to the leader's tick:
        // ROM only clears Status_OnObj later, in solid-object processing
        // (sub_1FF1E sonic3k.asm:44306-44319, loc_1FFC4 sonic3k.asm:44369-44381),
        // which runs AFTER Tails_CPU_Control. Sonic_Jump (sonic3k.asm:
        // 23288-23354) sets Status_InAir but never clears Status_OnObj.
        // The engine's PlayableSpriteMovement.doJump (line 642) and the
        // air-unseat path in ObjectManager.processInlineObjectForPlayer clear
        // onObject EARLIER in the same frame, so the live isOnObject() value
        // here can already reflect the leader's post-tick state. The
        // frame-start snapshot {@link AbstractPlayableSprite#getOnObjectAtFrameStart()}
        // (captured by SpriteManager.beginPlayableFrame before any player
        // ticks run) is intended to recover the ROM mid-frame view, but is
        // NOT plumbed in here yet — the engine's own frame-start OnObj
        // diverges from ROM's mid-frame OnObj at some object-release
        // transitions (see docs/S3K_KNOWN_BUGS.md, CNZ F7872 / AIZ F7381),
        // so swapping in the snapshot alone regresses AIZ1 around F2021.
        // Resolving that requires aligning the engine's OnObj clear timing
        // with ROM's solid-object-processing-driven clear; until then this
        // gate keeps the existing live read plus {@code !getAir()} heuristic.
        int leadOffset = sidekick.getPhysicsFeatureSet() != null
                ? sidekick.getPhysicsFeatureSet().sidekickFollowLeadOffset()
                : 0;
        // ROM loc_13DA6 (sonic3k.asm:26690-26691, s2.asm:38933+) reads
        // Status_OnObj on the leader BEFORE solid-object processing has run for
        // the frame, so the spec view is the leader's frame-start OnObj snapshot
        // (captured by SpriteManager.beginPlayableFrame). The previous live
        // isOnObject() && !getAir() heuristic compensated for engine paths that
        // SET or KEPT OnObj for an airborne leader (e.g. Sonic3kSpringObjectInstance
        // before the sub_22F98 bclr Status_OnObj fix landed at sonic3k.asm:47723-47724).
        // With the spring trigger now clearing OnObj to match ROM, the snapshot
        // matches ROM's mid-frame view and the air filter is no longer required;
        // ROM btst #Status_OnObj at sonic3k.asm:26690 has no air gate.
        boolean leaderStatusOnObject = effectiveLeader.getOnObjectAtFrameStart();
        if (leadOffset > 0
                && !leaderStatusOnObject
                && effectiveLeader.getGSpeed() < 0x400) {
            targetX -= leadOffset;
        }

        int dx = targetX - sidekick.getCentreX();
        int dy = targetY - sidekick.getCentreY();
        inputLeft = (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputUp = (recordedInput & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (recordedInput & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputJump = (recordedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;
        // ROM copies the delayed Ctrl_1_Logical word into Ctrl_2_Logical
        // (s2.asm:38939-38946, 39025-39027). The compact follower history
        // stores abstract held bits, so press edges are reconstructed from
        // adjacent delayed Stat_Record_Buf slots. AIZ F2822/F2823 records
        // delayed inputs $4840 -> $4800: held jump remains set while the
        // press edge clears on the following slot.
        inputJumpPress = recordedJumpPress;
        diagnosticGeneratedPressedInput =
                delayedDirectionalPress(effectiveLeader, followStatDelayFrames, recordedInput)
                        | (recordedJumpPress ? AbstractPlayableSprite.INPUT_JUMP : 0);

        byte pushBypassStatus = effectiveLeader.getStatusHistory(OBJECT_ORDER_INPUT_DELAY_FRAMES);
        // ROM loc_13DD0 tests Tails' current Status_Push byte before loc_13E9C
        // (sonic3k.asm:26702-26705), before the follow-left/right steering
        // blocks that can write Ctrl_2 left/right or nudge x_pos
        // (sonic3k.asm:26717-26741). AIZ giant-vine/collapsing-platform object
        // order can leave the engine's transient push flag clear for an inline
        // player tick even though ROM still treats Tails as pushing: the platform
        // resolves solid/release before its collapse transition (sonic3k.asm:
        // 44784-44883), and the vine handles P1 then P2 after capture has cleared
        // velocities once but does not keep clearing them while held
        // (sonic3k.asm:46481-46743,46749-46950). For follow steering, bridge the
        // engine-side clear only while Tails is still in the same local object
        // band as the delayed leader target. The AIZ collapsing-platform/vine
        // bridge at F2709-F2720 has Tails object_control=$20/status=$20 and a
        // small vertical delta; F3075 is far below the target, so ROM's normal
        // height gate (sonic3k.asm:26768-26775) has already left this bridge
        // context and the current Ctrl_2 RIGHT pulse remains live. Grounded
        // Tails_InputAcceleration_Path converts that into +$000C ground_vel/x_vel
        // (sonic3k.asm:27798-27805,28103-28122).
        boolean delayedObjectOrPushContext =
                (pushBypassStatus
                        & (AbstractPlayableSprite.STATUS_ON_OBJECT
                        | AbstractPlayableSprite.STATUS_PUSHING)) != 0;
        boolean currentStatusPush =
                (diagnostics.preStatus() & AbstractPlayableSprite.STATUS_PUSHING) != 0;
        boolean frameStartStatusPush = sidekick.getPushingAtFrameStart();
        boolean frameStartPushBypass = frameStartStatusPush
                && (recordedStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0
                && isCurrentPushBypassContext(delayedObjectOrPushContext, dy);
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        // Live Status_Push is the direct ROM loc_13DD0 branch (sonic3k.asm:
        // 26702-26705) and can skip follow steering even with a large dy. The
        // engine can still carry a stale push bit into offscreen underwater
        // sidekick frames after the ROM has already cleared it (AIZ F14302:
        // ROM status=$41, engine pre-status=$61). In the AIZ2 reload water
        // rebound, the ROM-visible Status_Push samples are the side-contact
        // pulses that zero inertia and leave a substantial horizontal rebound
        // before Tails_InputAcceleration_Path clears the bit again; tiny
        // follow/accel residue is stale and must fall through FollowLeft.
        boolean restrictUnderwaterPushBypassToContactPulses =
                fs != null && fs.sidekickPushBypassUsesGraceStatus();
        boolean currentPushBypass = (currentStatusPush
                && (recordedStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0
                && (!sidekick.isInWater()
                || !restrictUnderwaterPushBypassToContactPulses
                || delayedObjectOrPushContext
                || isUnderwaterCurrentPushPulse()))
                || frameStartPushBypass;
        boolean pushBypassGraceEnabled = fs != null && fs.sidekickPushBypassUsesGraceStatus();
        boolean gracePushBypass = !sidekick.getAir()
                && pushBypassGraceEnabled
                && normalPushingGraceFrames > 0
                && (recordedStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0
                && (pushBypassStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0;
        boolean localGracePushBypass = gracePushBypass
                && Math.abs(dy) < PUSH_BRIDGE_LOCAL_OBJECT_BAND_Y;
        boolean supportGraceKeepsFollowSteering =
                localGracePushBypass && isDoorSupportGraceFollowSteeringContext();
        int followSnapThreshold = resolveFollowSnapThreshold();
        boolean localBelowTargetFacingIntoFollowSide =
                (dx > 0 && sidekick.getDirection() == Direction.RIGHT)
                        || (dx < 0 && sidekick.getDirection() == Direction.LEFT);
        boolean delayedInputIntoFollowSide =
                (dx > 0 && (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0)
                        || (dx < 0 && (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0);
        boolean freshBelowTargetReboundGrace =
                localGracePushBypass
                        && Math.abs(sidekick.getGSpeed()) >= LOCAL_BELOW_TARGET_REBOUND_NUDGE_MIN_GSPEED
                        && dy >= 0
                        && localBelowTargetFacingIntoFollowSide;
        boolean localBelowTargetBridgeWindow =
                normalPushingGraceFrames >= LOCAL_BELOW_TARGET_PUSH_BRIDGE_MIN_GRACE
                        && normalPushingGraceFrames <= LOCAL_BELOW_TARGET_PUSH_BRIDGE_MAX_GRACE;
        boolean suppressLocalGraceFollowNudge =
                localGracePushBypass
                        && normalPushingGraceFrames >= OBJECT_ORDER_PUSH_BRIDGE_MIN_GRACE
                        && !leaderStatusOnObject
                        && !supportGraceKeepsFollowSteering
                        && !freshBelowTargetReboundGrace;
        int localGraceAbsDx = Math.abs(dx);
        boolean fastLeaderNoLiveObjectNudge =
                effectiveLeader.getGSpeed() >= 0x400
                        && currentRidingObject() == null
                        && (localGraceAbsDx < followSnapThreshold
                        || (normalPushingGraceFrames <= OBJECT_ORDER_PUSH_BRIDGE_MIN_GRACE + 1
                        && dy >= -JUMP_HEIGHT_THRESHOLD)
                        || (localGraceAbsDx <= FAST_LEADER_NO_LIVE_OBJECT_NUDGE_MAX_DX
                        && dy >= -JUMP_HEIGHT_THRESHOLD)
                        || (normalPushingGraceFrames <= OBJECT_ORDER_PUSH_BRIDGE_MIN_GRACE + 1
                        && localGraceAbsDx >= FAST_LEADER_NO_LIVE_OBJECT_LATE_GRACE_INPUT_NUDGE_MIN_DX
                        && localGraceAbsDx <= FAST_LEADER_NO_LIVE_OBJECT_NUDGE_MAX_DX
                        && dy >= -FAST_LEADER_NO_LIVE_OBJECT_LATE_GRACE_INPUT_NUDGE_MAX_UPWARD_GAP
                        && delayedInputIntoFollowSide)
                        || (localGraceAbsDx <= FAST_LEADER_NO_LIVE_OBJECT_NUDGE_MAX_DX
                        && dy >= -FAST_LEADER_NO_LIVE_OBJECT_INPUT_NUDGE_MAX_UPWARD_GAP
                        && delayedInputIntoFollowSide));
        boolean smallDxDelayedInputNudge =
                effectiveLeader.getGSpeed() < 0x400
                        && localGraceAbsDx < followSnapThreshold
                        && (delayedInputIntoFollowSide
                        || (dx < 0 && sidekick.getGSpeed() < 0)
                        || (dx > 0 && sidekick.getGSpeed() > 0))
                        && dy >= -JUMP_HEIGHT_THRESHOLD;
        suppressLocalGraceFollowNudge =
                suppressLocalGraceFollowNudge && !fastLeaderNoLiveObjectNudge && !smallDxDelayedInputNudge;
        boolean suppressFastLeaderTinyFollowNudge =
                fs != null
                        && fs.sidekickSuppressesFastLeaderTinyFollowNudge()
                        && effectiveLeader.getGSpeed() >= 0x400
                        && !leaderStatusOnObject
                        && Math.abs(sidekick.getGSpeed()) < 0x100
                        && localGraceAbsDx < followSnapThreshold
                        && dy < -JUMP_HEIGHT_THRESHOLD
                        && !sidekick.getAir()
                        && !sidekick.getRolling();
        if (suppressNextAirbornePushFollowSteering) {
            // Earlier builds treated the first airborne tick after an
            // object-order push jump as one sample ahead of ROM. AIZ F2722's
            // cpu_state trace shows the opposite: Tails falls through
            // loc_13DD0 and consumes the fresh 16-frame Stat_table Ctrl_1 word
            // immediately (sonic3k.asm:26696-26729,28330-28401).
            suppressNextAirbornePushFollowSteering = false;
        }
        // Engine-side push grace only skips follow steering when it models a
        // ROM-visible current push bit at Tails' CPU slot. Object-order grace
        // covers provider-approved SolidObjectFull timing; the local
        // below-target bridge covers the same side-contact continuity only in
        // the fresh local push window, while Tails is still facing into the
        // delayed follow side. Once that early continuity window is gone, or
        // the facing bit flips away from the contact, ROM current Status_Push
        // is clear and Ctrl_2 falls through FollowLeft/FollowRight
        // (sonic3k.asm:26702-26729).
        boolean localBelowTargetGrace =
                localGracePushBypass
                        && !freshBelowTargetReboundGrace
                        && localBelowTargetBridgeWindow
                        && !delayedInputIntoFollowSide
                        && dy >= 0
                        && localBelowTargetFacingIntoFollowSide;
        boolean objectOrderFollowSteeringContext = isObjectOrderFollowSteeringContext(effectiveLeader);
        boolean objectOrderGrace = localGracePushBypass
                && normalPushingGraceFrames >= OBJECT_ORDER_PUSH_BRIDGE_MIN_GRACE
                && objectOrderFollowSteeringContext
                && (leaderStatusOnObject
                || (recordedStatus & AbstractPlayableSprite.STATUS_ON_OBJECT) != 0);
        objectOrderGracePushBypassThisFrame = objectOrderGrace;
        boolean followNudgeBlockedByObjectControlBit0 =
                fs != null
                        && fs.sidekickFollowNudgeBlockedByObjectControlBit0()
                        && sidekick.isObjectControlSuppressesMovement();
        boolean skipFollowSteering = currentPushBypass
                || localBelowTargetGrace
                || (objectOrderGrace && !supportGraceKeepsFollowSteering);
        String followBranch = currentPushBypass ? "current_push_bypass"
                : localBelowTargetGrace ? "grace_push_bypass"
                : (objectOrderGrace && !supportGraceKeepsFollowSteering) ? "grace_push_bypass"
                : leaderStatusOnObject ? "leader_on_object"
                : effectiveLeader.getGSpeed() >= 0x400 ? "leader_fast"
                : "follow_steering";
        // ROM loc_13DD0 only uses d4 (the delayed status byte) to decide
        // whether to bypass FollowLeft/FollowRight. The Ctrl_2 word in d1 was
        // already loaded from the same Stat_table entry and is preserved when
        // branching to loc_13E9C (sonic3k.asm:26696-26705,26775-26785; S2
        // s2.asm:38939-38946). Do not re-read an older input slot here: CNZ1
        // F3925 has Status_Push set but still carries delayed RIGHT in d1, and
        // Tails_InputAcceleration_Path consumes it for +$000C ground speed
        // (sonic3k.asm:27798-27805,28103-28122).
        //
        // The object-order grace/airborne handoff is not a direct ROM branch;
        // it is a provider-owned bridge for zones where object ordering can
        // clear transient push before Tails' CPU slot. Keep its older input
        // sample only in those provider-approved contexts (for the current S3K
        // implementation, the S3K hollow-tree/collapsing-platform/vine object
        // ordering routes: sonic3k.asm:26690-26705,41668-41679,41793-41818,
        // 43649-43810).
        // MGZ F1466 has the same delayed
        // Status_OnObj bit but ROM keeps the already-loaded d1 sample
        // (input=0000/stat=08) through loc_13DD0; re-reading the older sample
        // manufactures a right input and over-accelerates Tails.
        // Grounded grace with no provider-approved object-order status is the CNZ cylinder
        // release shape instead; it preserves the already-loaded d1 Ctrl_2 word
        // after Tails_CPU_Control, and the cylinder/P2 and path-acceleration
        // paths consume that same sample (sonic3k.asm:26195-26208,
        // 67656-67672,27798-27805,28103-28122).
        boolean currentPushObjectOrderInputSample = currentPushBypass
                && objectOrderFollowSteeringContext
                && delayedObjectOrPushContext
                && !sidekick.getAir();
        int bridgedInputDelayFrames = currentPushObjectOrderInputSample
                ? ROM_FOLLOW_DELAY_FRAMES
                : OBJECT_ORDER_INPUT_DELAY_FRAMES;
        if (objectOrderGrace || currentPushObjectOrderInputSample) {
            recordedInput = effectiveLeader.getInputHistory(bridgedInputDelayFrames);
            recordedJumpPress = delayedJumpPress(effectiveLeader, bridgedInputDelayFrames, recordedInput);
            inputLeft = (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0;
            inputRight = (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
            inputUp = (recordedInput & AbstractPlayableSprite.INPUT_UP) != 0;
            inputDown = (recordedInput & AbstractPlayableSprite.INPUT_DOWN) != 0;
            inputJump = (recordedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;
            inputJumpPress = recordedJumpPress;
            diagnosticGeneratedPressedInput =
                    delayedDirectionalPress(effectiveLeader, bridgedInputDelayFrames, recordedInput)
                            | (recordedJumpPress ? AbstractPlayableSprite.INPUT_JUMP : 0);
        }
        int appliedFollowNudge = 0;
        if (!skipFollowSteering) {
            // ROM enters FollowLeft/FollowRight for any nonzero dx; the per-game
            // snap threshold only decides whether Tails overrides left/right input,
            // not whether the +/-1 x_pos nudge runs.
            //
            // S2:  0x10 (s2.asm:38952 TailsCPU_Normal_FollowLeft,
            //            s2.asm:38967 TailsCPU_Normal_FollowRight).
            // S3K: 0x30 (sonic3k.asm:26712 loc_13DF2,
            //            sonic3k.asm:26729 loc_13E26).
            int snapThreshold = followSnapThreshold;
            int steeringDx = resolveFollowSteeringDx(dx, effectiveLeader, leadOffset, leaderStatusOnObject,
                    snapThreshold);
            if (steeringDx < 0) {
                int absDx = -steeringDx;
                if (absDx >= snapThreshold) {
                    inputLeft = true;
                    inputRight = false;
                    diagnosticGeneratedPressedInput =
                            (diagnosticGeneratedPressedInput
                                    & ~(AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT))
                                    | AbstractPlayableSprite.INPUT_LEFT;
                }
            } else if (steeringDx > 0) {
                if (steeringDx >= snapThreshold) {
                    inputRight = true;
                    inputLeft = false;
                    diagnosticGeneratedPressedInput =
                            (diagnosticGeneratedPressedInput
                                    & ~(AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT))
                                    | AbstractPlayableSprite.INPUT_RIGHT;
                }
            } else if ((recordedStatus & AbstractPlayableSprite.STATUS_FACING_LEFT) != 0) {
                sidekick.setDirection(Direction.LEFT);
            } else {
                sidekick.setDirection(Direction.RIGHT);
            }
            int nudgeDx = resolveFollowNudgeDx(dx, effectiveLeader);
            if (nudgeDx < 0
                    && sidekick.getDirection() == Direction.LEFT
                    && !suppressLocalGraceFollowNudge
                    && !suppressFastLeaderTinyFollowNudge
                    // S3K loc_13E0A gates this nudge on object_control bit 0
                    // (sonic3k.asm:26722-26724). S2 TailsCPU_Normal has no
                    // equivalent object_control test (s2.asm:38952-38975), so
                    // the check is feature-set gated.
                    && !followNudgeBlockedByObjectControlBit0) {
                if (sidekick.getGSpeed() != 0) {
                    sidekick.shiftX(-1);
                    appliedFollowNudge = -1;
                    // ROM loc_13E0A applies this nudge immediately and has no
                    // deferred queue (sonic3k.asm:26717-26724). If the engine
                    // had queued a late solid-contact bridge while airborne,
                    // the current grounded CPU pass has now consumed the ROM
                    // effect and the queued bridge must not also run.
                    pendingGroundedFollowNudge = 0;
                    pendingGroundedFollowNudgeFrame = -1;
                } else if (!sidekick.getAir()) {
                    pendingGroundedFollowNudge = -1;
                    pendingGroundedFollowNudgeFrame = frameCounter;
                }
            } else if (nudgeDx > 0
                    && sidekick.getDirection() == Direction.RIGHT
                    && !suppressLocalGraceFollowNudge
                    && !suppressFastLeaderTinyFollowNudge
                    // S3K loc_13E34 gates this nudge on object_control bit 0
                    // (sonic3k.asm:26739-26741). S2 keeps nudging under
                    // object_control bit 0.
                    && !followNudgeBlockedByObjectControlBit0) {
                if (sidekick.getGSpeed() != 0) {
                    sidekick.shiftX(1);
                    appliedFollowNudge = 1;
                    // ROM loc_13E34 applies this nudge immediately and has no
                    // deferred queue (sonic3k.asm:26734-26741).
                    pendingGroundedFollowNudge = 0;
                    pendingGroundedFollowNudgeFrame = -1;
                } else if (!sidekick.getAir()) {
                    pendingGroundedFollowNudge = 1;
                    pendingGroundedFollowNudgeFrame = frameCounter;
                }
            }
        }

        if (jumpingFlag) {
            inputJump = true;
            boolean delayedJumpOnly = (recordedInput & (AbstractPlayableSprite.INPUT_UP
                    | AbstractPlayableSprite.INPUT_DOWN
                    | AbstractPlayableSprite.INPUT_LEFT
                    | AbstractPlayableSprite.INPUT_RIGHT)) == 0;
            if (sidekick.getAir()
                    && delayedJumpOnly
                    && normalPushingGraceFrames <= 2) {
                // ROM loc_13E64 carries Tails_CPU_auto_jump_flag by ORing the
                // high-byte A/B/C held bits into Ctrl_2_Logical, then branches
                // directly to the final write while airborne. It does not
                // manufacture another low-byte press for the engine's jump-only
                // held-history shape after the fresh push-grace handoff has
                // decayed, until loc_13E9C's cadence gate runs again.
                inputJumpPress = false;
                diagnosticGeneratedPressedInput &= ~AbstractPlayableSprite.INPUT_JUMP;
            }
            if (!sidekick.getAir()) {
                jumpingFlag = false;
            }
        }

        if (!jumpingFlag) {
            // ROM runs the auto-jump distance/height/gate path regardless of
            // Status_InAir; the in-air check only belongs to the existing
            // Tails_CPU_auto_jump_flag clear path above (S2 s2.asm:38994-39022,
            // S3K sonic3k.asm:26753-26782). CNZ1 uses this when delayed Sonic
            // jump input makes Tails airborne one frame before the auto-jump
            // latch itself fires.
            // ROM sonic3k.asm:26702-26705 (loc_13DD0) and s2.asm:38943-38946
            // (TailsCPU_Normal): if Tails is currently pushing AND the leader was
            // NOT pushing 16 frames ago, branch directly to the auto-jump trigger
            // gate (loc_13E9C / TailsCPU_Normal_FilterAction_Part2), bypassing the
            // dx/dy distance and height gates entirely. Without this bypass, Tails
            // gets stuck pushing against terrain whenever Sonic has moved past it,
            // because dx-distance is typically too large to pass the standard
            // distance gate. AIZ trace F2721 reproduces this: Tails was pushing
            // (status=0x20) at the end of F2720, Sonic was on an object (status=0x08
            // = OnObject, not Pushing) 16 frames before, dx=0x4D so distance gate
            // would fail, but ROM auto-jumps via the bypass and y_speed becomes
            // -0x680 (Tails_Jump initial velocity).
            //
            // Live Status_Push is the direct ROM loc_13DD0 branch and always
            // bypasses the distance/height gates before loc_13E9C. Provider
            // approval is only for the engine-side object-order grace path,
            // where transient push may have cleared locally before ROM would
            // read status(a0) in Tails' sprite slot (sonic3k.asm:26702-26705).
            // Ordinary stale grace still falls through loc_13E7C and must pass
            // the normal distance/height gates.
            // Vertical S2 Obj85 can hand Tails into a curled, zero-speed push
            // state after release. That object-owned state preserves rolling
            // without ROM's pinball-mode auto-push, and the CNZ trace shows it
            // must not take the generic push auto-jump shortcut at the $3F
            // frame gate (s2.asm:38943-38946, 39015-39022; Obj85 release:
            // s2.asm:57611-57625).
            boolean suppressObjectPreservedPushJump =
                    sidekick.getRolling() && sidekick.shouldPreserveRollingOnNextRollStop();
            boolean pushingBypass = (currentPushBypass || objectOrderGrace)
                    && !suppressObjectPreservedPushJump;
            int autoJumpFrameCounter = frameCounter;
            boolean freshAutoJumpFrame = autoJumpFrameCounter != lastNormalAutoJumpPressFrameCounter;
            boolean passesDistanceGate = pushingBypass
                    || (autoJumpFrameCounter & 0xFF) == 0
                    || Math.abs(dx) < JUMP_DISTANCE_TRIGGER;
            boolean passesHeightGate = pushingBypass
                    || dy <= -JUMP_HEIGHT_THRESHOLD;
            if (passesDistanceGate
                    && passesHeightGate
                    && freshAutoJumpFrame
                    && (autoJumpFrameCounter & 0x3F) == 0
                    && sidekick.getAnimationId() != duckAnimId) {
                inputJump = true;
                inputJumpPress = true;
                diagnosticGeneratedPressedInput |= AbstractPlayableSprite.INPUT_JUMP;
                lastNormalAutoJumpPressFrameCounter = autoJumpFrameCounter;
                jumpingFlag = true;
            }
        }

        int normalDiagnosticHeldInput = -1;

        // Obj85's preserved roll-stop handoff can keep a stale held jump bit in
        // the delayed leader sample while Tails is still grounded. Suppress that
        // stale hold, but allow the later fresh delayed jump press that ROM uses
        // to leave the stopper chamber (S2 s2.asm:38939-38946, 57611-57625).
        if (sidekick.getRolling()
                && sidekick.shouldPreserveRollingOnNextRollStop()
                && !sidekick.getAir()
                && (currentPushBypass || localGracePushBypass || !recordedJumpPress)) {
            inputJump = false;
            inputJumpPress = false;
            diagnosticGeneratedPressedInput &= ~AbstractPlayableSprite.INPUT_JUMP;
            jumpingFlag = false;
        }

        // ROM Obj02_MdRoll skips bsr.w Tails_Jump entirely when pinball_mode is
        // set (s2.asm:39279-39282). Tails_Jump reads Ctrl_2_Press_Logical; without
        // this check the 16-frame-delayed fresh jump press fires a jump even though
        // the ROM would never reach Tails_Jump in this rolling-pinball-mode state.
        if (sidekick.getRolling()
                && sidekick.getPinballMode()
                && !sidekick.getAir()) {
            normalDiagnosticHeldInput = diagnosticGeneratedInput();
            inputJump = false;
            inputJumpPress = false;
            jumpingFlag = false;
        }

        if (suppressNextLevelEventNormalMovement) {
            // Level events can release a dormant marker before Tails' first
            // visible follow pulse. Consume this on the first post-release CPU
            // tick regardless of generated input: the ROM event write occurs
            // outside Tails normal movement, so the release-side object frame is
            // suppressed but the following frame's fresh Ctrl_2 state is usable.
            suppressNextLevelEventNormalMovement = false;
            skipPhysicsThisFrame = true;
        }
        updateNormalPushingGrace(currentPushing);
        int reportedDelayFrames = (objectOrderGrace || currentPushObjectOrderInputSample)
                ? bridgedInputDelayFrames
                : followStatDelayFrames;
        finishNormalStepDiagnosticsWithCtrl2(diagnostics, followBranch,
                reportedDelayFrames,
                effectiveLeader.getHistorySlotIndex(reportedDelayFrames),
                recordedInput & 0xFFFF,
                recordedStatus & 0xFF,
                pushBypassStatus & 0xFF,
                dx,
                dy,
                normalDiagnosticHeldInput >= 0 ? normalDiagnosticHeldInput : diagnosticGeneratedInput(),
                diagnosticGeneratedPressedInput,
                skipFollowSteering,
                appliedFollowNudge);
    }

    private int resolveFollowStatDelayFrames() {
        // ROM Sonic_RecordPos writes Pos_table and Stat_table with the same
        // Pos_table_index (sonic3k.asm:22124-22136), then Tails_Normal reads the
        // delayed stat word in loc_13DD0 (sonic3k.asm:26683-26700). The engine
        // updates CPU sidekicks before the main player, so the latest completed
        // player history entry already corresponds to the previous ROM sample.
        return ROM_FOLLOW_DELAY_FRAMES;
    }

    private int delayedDirectionalPress(AbstractPlayableSprite effectiveLeader, int delayFrames, short recordedInput) {
        short previousInput = effectiveLeader.getInputHistory(delayFrames + 1);
        return (recordedInput & ~previousInput) & DIRECTIONAL_INPUT_MASK;
    }

    private boolean delayedJumpPress(AbstractPlayableSprite effectiveLeader, int delayFrames, short recordedInput) {
        // ROM copies the delayed Ctrl_1_logical low-byte press bits directly
        // into Ctrl_2_logical; consecutive recorded press bytes remain presses
        // and are not reconstructed as edges here (s2.asm:38939-38946,
        // 39025-39027; sonic3k.asm:26683-26689,26775-26782).
        if ((recordedInput & AbstractPlayableSprite.INPUT_JUMP) == 0
                || !effectiveLeader.getJumpPressHistory(delayFrames)) {
            return false;
        }
        PhysicsFeatureSet featureSet = sidekick.getPhysicsFeatureSet();
        if (featureSet != null && featureSet.sidekickDelayedJumpPressUsesHistoryEdge()) {
            return !effectiveLeader.getJumpPressHistory(delayFrames + 1);
        }
        return true;
    }

    private int resolveFollowSteeringDx(int dx, AbstractPlayableSprite effectiveLeader, int leadOffset,
            boolean leaderStatusOnObject, int snapThreshold) {
        if (dx >= 0
                || sidekick.getPhysicsFeatureSet() == null
                || sidekick.getPhysicsFeatureSet().sidekickFollowLeadOffset() <= 0
                || !sidekick.getAir()
                || !sidekick.getRolling()
                || effectiveLeader.getOnObjectAtFrameStart()
                || effectiveLeader.getGSpeed() >= 0x400
                || !isObjectOrderNudgeSteeringContext(effectiveLeader)) {
            return dx;
        }

        // Tails_Normal reads the delayed Pos_table entry before applying the
        // FollowLeft/FollowRight threshold (sonic3k.asm:26683-26732). In AIZ,
        // Obj_AIZHollowTree runs later in Process_Sprites after Player_1 and
        // Player_2 (sonic3k.asm:35965-35988,43649-43655) and rewrites both
        // player slots with AIZTree_SetPlayerPos (sonic3k.asm:43776-43810).
        // During the airborne release, the engine's completed player history
        // can sit one object-order sample behind the ROM-visible handoff. Use
        // the adjacent newer sample only when it keeps the same follow side but
        // falls back below S3K's $30 steering override, preserving the delayed
        // Ctrl_2 RIGHT/jump bits instead of manufacturing a LEFT pulse.
        // Do not apply this bridge to the fast-leader branch: ROM loc_13DA6 only
        // skips the S3K lead bias when leader ground_vel >= $400, then still runs
        // FollowLeft/FollowRight from the original delayed Pos_table sample
        // (sonic3k.asm:26692-26694,26707-26732).
        int objectOrderTargetX = effectiveLeader.getCentreX(ROM_FOLLOW_DELAY_FRAMES - 1);
        if (leadOffset > 0
                && !leaderStatusOnObject
                && effectiveLeader.getGSpeed() < 0x400) {
            objectOrderTargetX -= leadOffset;
        }
        int objectOrderDx = objectOrderTargetX - sidekick.getCentreX();
        if (objectOrderDx <= 0 && -objectOrderDx < snapThreshold) {
            return objectOrderDx;
        }
        return dx;
    }

    private int resolveFollowNudgeDx(int dx, AbstractPlayableSprite effectiveLeader) {
        if (dx <= 0
                && sidekick.getPhysicsFeatureSet() != null
                && sidekick.getPhysicsFeatureSet().sidekickFollowLeadOffset() > 0
                // ROM loc_13DA6 branches at Status_OnObj before applying the
                // S3K follow bias (sonic3k.asm:26690-26694). While that bit is
                // set, keep the same delayed position sample for the +/-1 nudge
                // instead of substituting an adjacent object-order bridge sample.
                && !effectiveLeader.getOnObjectAtFrameStart()
                // The fast-leader branch uses the same unadjusted delayed d2 for
                // both steering and the loc_13E0A/loc_13E34 +/-1 x_pos nudge
                // (sonic3k.asm:26692-26694,26707-26741).
                && effectiveLeader.getGSpeed() < 0x400
                && isObjectOrderNudgeSteeringContext(effectiveLeader)) {
            // S3K reads Pos_table_index-$44 for the positional follow target
            // (sonic3k.asm:26683-26689), then applies the +1 x_pos nudge in
            // FollowRight when Tails faces right and object_control bit 0 is
            // clear (sonic3k.asm:26734-26741). Around AIZ's hollow-tree handoff,
            // Sonic is on Obj_AIZHollowTree (sonic3k.asm:43605,43649-43655);
            // that object-order player update can leave the nudge sign on either
            // adjacent completed leader-position sample while the delayed
            // input/status sample remains aligned.
            int sidekickX = sidekick.getCentreX();
            int olderObjectOrderDx = resolveObjectOrderNudgeDx(effectiveLeader, ROM_FOLLOW_DELAY_FRAMES + 1,
                    sidekickX);
            if (olderObjectOrderDx > 0) {
                return olderObjectOrderDx;
            }
            int newerObjectOrderDx = resolveObjectOrderNudgeDx(effectiveLeader, ROM_FOLLOW_DELAY_FRAMES - 1,
                    sidekickX);
            if (newerObjectOrderDx > 0) {
                return newerObjectOrderDx;
            }
        }
        return dx;
    }

    private int resolveObjectOrderNudgeDx(AbstractPlayableSprite effectiveLeader, int delayFrames, int sidekickX) {
        int targetX = effectiveLeader.getCentreX(delayFrames);
        int leadOffset = sidekick.getPhysicsFeatureSet() != null
                ? sidekick.getPhysicsFeatureSet().sidekickFollowLeadOffset()
                : 0;
        // ROM loc_13DA6 (sonic3k.asm:26690-26691, s2.asm:38933+) reads
        // Status_OnObj on the leader BEFORE solid-object processing has run for
        // the frame, so the spec view is the leader's frame-start OnObj snapshot
        // (captured by SpriteManager.beginPlayableFrame). The previous live
        // isOnObject() && !getAir() heuristic compensated for engine paths that
        // SET or KEPT OnObj for an airborne leader (e.g. Sonic3kSpringObjectInstance
        // before the sub_22F98 bclr Status_OnObj fix landed at sonic3k.asm:47723-47724).
        // With the spring trigger now clearing OnObj to match ROM, the snapshot
        // matches ROM's mid-frame view and the air filter is no longer required;
        // ROM btst #Status_OnObj at sonic3k.asm:26690 has no air gate.
        boolean leaderStatusOnObject = effectiveLeader.getOnObjectAtFrameStart();
        if (leadOffset > 0
                && !leaderStatusOnObject
                && effectiveLeader.getGSpeed() < 0x400) {
            targetX -= leadOffset;
        }
        return targetX - sidekickX;
    }

    private boolean isObjectOrderFollowSteeringContext(AbstractPlayableSprite effectiveLeader) {
        LevelEventProvider provider = levelEventProvider();
        return provider != null
                && provider.isSidekickObjectOrderFollowSteeringContext(sidekick, effectiveLeader);
    }

    private boolean isObjectOrderNudgeSteeringContext(AbstractPlayableSprite effectiveLeader) {
        LevelEventProvider provider = levelEventProvider();
        return provider != null
                && provider.isSidekickObjectOrderFollowNudgeContext(sidekick, effectiveLeader);
    }

    private boolean isDoorSupportGraceFollowSteeringContext() {
        if (!sidekick.isOnObject() || sidekick.getAir()) {
            return false;
        }
        LevelEventProvider provider = levelEventProvider();
        if (provider == null) {
            return false;
        }
        return provider.isSidekickDoorSupportGraceFollowSteeringContext(sidekick, currentRidingObject());
    }

    private ObjectInstance currentRidingObject() {
        LevelManager levelManager = sidekick.currentLevelManager();
        if (levelManager != null && levelManager.getObjectManager() != null) {
            return levelManager.getObjectManager().getRidingObject(sidekick);
        }
        return sidekick.getLatchedSolidObjectInstance();
    }

    private boolean usesSidekickRomVisibleCatchUpMarkerFrameCounterBridge() {
        LevelEventProvider provider = levelEventProvider();
        return provider != null
                && provider.usesSidekickRomVisibleCatchUpMarkerFrameCounterBridge(sidekick);
    }

    public boolean hasLevelEventDormantMarkerReleasePending() {
        return levelEventDormantMarkerReleasePending;
    }

    public void clearLevelEventDormantMarkerReleasePending() {
        levelEventDormantMarkerReleasePending = false;
    }

    private LevelEventProvider levelEventProvider() {
        GameModule module = sidekick.currentGameModule();
        return module != null ? module.getLevelEventProvider() : null;
    }

    private void updateNormalPushingGrace(boolean currentPushing) {
        if (currentPushing) {
            normalPushingGraceFrames = PUSH_STATUS_GRACE_FRAMES;
        } else if (normalPushingGraceFrames > 0) {
            normalPushingGraceFrames--;
        }
    }

    private boolean isCurrentPushBypassContext(boolean delayedObjectOrPushContext, int dy) {
        if (delayedObjectOrPushContext) {
            return true;
        }
        // Live Status_Push bypasses follow steering only while Tails is still
        // in the local contact band that can plausibly feed ROM loc_13DD0.
        // Do not infer ROM push state from speed alone; velocity is not the
        // status byte branch that the sidekick CPU reads here.
        return Math.abs(dy) < PUSH_BRIDGE_LOCAL_OBJECT_BAND_Y;
    }

    private boolean isUnderwaterCurrentPushPulse() {
        return sidekick.isInWater()
                && sidekick.getGSpeed() == 0
                && Math.abs((int) sidekick.getXSpeed()) >= UNDERWATER_PUSH_PULSE_MIN_X_SPEED;
    }

    private void updatePanic() {
        PhysicsFeatureSet featureSet = sidekick.getPhysicsFeatureSet();
        if (sidekick.getDead()
                || (sidekick.isHurt()
                && featureSet != null
                && featureSet.sidekickNormalCpuSkipsHurtRoutine())) {
            // S2/S3K hurt/dead sidekick object routines bypass the CPU control
            // dispatcher entirely, so PANIC must freeze the same respawn timer
            // and Ctrl_2 logical latch as NORMAL instead of running
            // TailsCPU_CheckDespawn (S2 s2.asm:38883-38891; S3K
            // sonic3k.asm:26091-26096).
            return;
        }
        diagnosticCtrl2HeldLatch = controller2Held & MANUAL_HELD_MASK;
        diagnosticCtrl2PressedLatch = controller2Logical & MANUAL_HELD_MASK;
        refreshPanicDiagnosticInputBeforeDespawn();
        if (checkDespawn()) {
            return;
        }
        if (controlCounter != 0) {
            applyManualControl();
            return;
        }
        if (sidekick.getMoveLockTimer() > 0) {
            return;
        }

        // ROM tests spin_dash_flag here. S3K AutoSpin shares that byte in ROM,
        // while the engine stores the AutoSpin state in pinballMode; S2's
        // separate pinball_mode byte must not take this branch (s2.asm:39458,
        // sonic3k.asm:26858).
        boolean panicSpinDashFlagSet = panicSpinDashFlagSet(featureSet);
        if (!panicSpinDashFlagSet) {
            if (sidekick.getGSpeed() != 0) {
                return;
            }
            // TailsCPU_Panic subtracts leader x from sidekick x after clearing
            // facing; equality does not set carry, so it sets the left-facing bit.
            sidekick.setDirection(leader.getCentreX() <= sidekick.getCentreX() ? Direction.LEFT : Direction.RIGHT);
            inputDown = true;
            setPanicDiagnosticInput(AbstractPlayableSprite.INPUT_DOWN);
            int phase = resolvePanicPhaseCounter() & 0x7F;
            if (phase == 0) {
                clearInputs();
                setPanicDiagnosticInput(0);
                state = State.NORMAL;
                normalFrameCount = 0;
                return;
            }
            if (sidekick.getAnimationId() == duckAnimId) {
                inputJump = true;
                inputJumpPress = true;
                setPanicDiagnosticInput(AbstractPlayableSprite.INPUT_DOWN | AbstractPlayableSprite.INPUT_JUMP);
            }
            return;
        }

        inputDown = true;
        setPanicDiagnosticInput(AbstractPlayableSprite.INPUT_DOWN);
        int phase = resolvePanicPhaseCounter() & 0x7F;
        if (phase == 0) {
            clearInputs();
            setPanicDiagnosticInput(0);
            state = State.NORMAL;
            normalFrameCount = 0;
            return;
        }
        if ((phase & 0x1F) == 0) {
            inputJump = true;
            inputJumpPress = true;
            setPanicDiagnosticInput(AbstractPlayableSprite.INPUT_DOWN | AbstractPlayableSprite.INPUT_JUMP);
        }
    }

    private void refreshPanicDiagnosticInputBeforeDespawn() {
        if (controlCounter != 0 || sidekick.getMoveLockTimer() > 0) {
            return;
        }
        int input = 0;
        PhysicsFeatureSet featureSet = sidekick.getPhysicsFeatureSet();
        if (!panicSpinDashFlagSet(featureSet)) {
            if (sidekick.getGSpeed() == 0) {
                input = AbstractPlayableSprite.INPUT_DOWN;
                if (sidekick.getAnimationId() == duckAnimId) {
                    input |= AbstractPlayableSprite.INPUT_JUMP;
                }
            }
        } else {
            input = AbstractPlayableSprite.INPUT_DOWN;
            int phase = resolvePanicPhaseCounter() & 0x7F;
            if ((phase & 0x1F) == 0) {
                input |= AbstractPlayableSprite.INPUT_JUMP;
            }
        }
        setPanicDiagnosticInput(input);
    }

    private boolean panicSpinDashFlagSet(PhysicsFeatureSet featureSet) {
        return sidekick.getSpindash()
                || (featureSet != null
                && featureSet.sidekickPanicTreatsPinballModeAsSpindashFlag()
                && sidekick.getPinballMode());
    }

    private void setPanicDiagnosticInput(int input) {
        diagnosticCtrl2HeldLatch = input & MANUAL_HELD_MASK;
        diagnosticCtrl2PressedLatch = input & MANUAL_HELD_MASK;
    }

    /** ROM routine 0x0C. Mirrors sub_1459E (pickup) then falls through to 0x20. */
    private void updateCarryInit() {
        // Tails's per-carry state
        sidekick.setAir(true);
        sidekick.setXSpeed(carryTrigger.carryInitXVel());
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setDoubleJumpFlag(1);
        sidekick.setDoubleJumpProperty((byte) 0xF0);
        // CNZ carry setup (loc_13A5A -> loc_13FC2) does not set
        // object_control on Tails; the CPU routine drives Ctrl_2_logical, and
        // that input must remain visible to Tails_Move_FlySwim.
        sidekick.setControlLocked(false);
        sidekick.setForcedAnimationId(flyAnimId);
        pickupLeaderForCarry();

        // Initialize the latch
        flyingCarryingFlag = true;
        mgzCarryIntroAscend = carryTrigger.usesMgzBossTransitionControl();
        mgzCarryFlapTimer = 0;
        releaseCooldown = 0;

        state = State.CARRYING;
        // ROM 0x0C -> 0x20 fall-through: one tick of the body this same frame.
        updateCarrying();
    }

    /** ROM routines 0x0E / 0x20 body. Runs each carry frame. */
    private void updateCarrying() {
        // ROM order inside Tails_Carry_Sonic:

        // Tails's hurt/death/drown object routines bypass Tails_CPU_Control and
        // immediately clear Player_1 object_control plus Flying_carrying_Sonic_flag
        // before running hurt/death motion (sonic3k.asm:29180, 29272, 29316).
        if (sidekick.isHurt() || sidekick.getDead()) {
            releaseCarryForCarrierDisabled();
            return;
        }

        if (carryTrigger.usesMgzBossTransitionControl() && !flyingCarryingFlag) {
            updateMgzReleasedCarry();
            return;
        }

        // ROM routine $E with Flying_carrying_Sonic_flag clear: a throwaway solo
        // carrier that dropped Sonic mid-air (jump-out / external velocity / hurt)
        // stays in routine $E and runs the loc_14534 cooldown/regrab loop — Tails
        // keeps flying and either re-grabs Sonic or, once Sonic lands, transitions
        // to routine $10 (fly off). It never enters the NORMAL follow AI.
        if (transientCarrySidekick && !flyingCarryingFlag) {
            updateTransientReleasedCarry();
            return;
        }

        // 1. Hurt/dead (Sonic routine >= 4)
        if (leader.isHurt() || leader.getDead()) {
            carryParentagePending = false;
            releaseCarry(carryTrigger.carryLatchReleaseCooldownFrames());
            return;
        }

        // 2. External velocity change (release path C: latch mismatch)
        if (leader.getXSpeed() != carryLatchX || leader.getYSpeed() != carryLatchY) {
            carryParentagePending = false;
            releaseCarry(carryTrigger.carryLatchReleaseCooldownFrames());
            return;
        }

        // 3. A/B/C just-pressed (release path B)
        if (leader.isJumpJustPressed()) {
            carryParentagePending = false;
            performJumpRelease();
            return;
        }

        // 4. Ground release (release path A): Sonic in-air bit clear
        if (!leader.getAir()) {
            // ROM loc_14016 (sonic3k.asm:26923-26946) runs BEFORE Tails_Carry_Sonic
            // branches to loc_1445A. It resets Tails's own airborne state so the
            // next tick runs Tails_FlyingSwimming from a freshly-zeroed velocity
            // (y_vel=0 + Tails_Move_FlySwim's +0x08 gravity -> trace y_vel=0x008):
            //   move.w #0, x_vel(a0)     ; Tails
            //   move.w #0, y_vel(a0)     ; Tails
            //   move.w #0, ground_vel(a0); Tails
            //   move.b #1<<Status_InAir, status(a0)  ; Tails stays airborne
            // double_jump_flag(a0) is deliberately NOT cleared here; the flight
            // physics persist until Tails actually lands.
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setAir(true);

            // ROM loc_1445A (sonic3k.asm:27268): move.w #-$100, y_vel(a1)
            // Small upward impulse on the carried Sonic before clearing
            // object_control, matching ROM fall-through into loc_14460/loc_14466.
            leader.setYSpeed((short) -0x100);
            carryParentagePending = false;
            releaseCarry(0);
            if (transientCarrySidekick) {
                // ROM loc_14016: when Sonic lands while being carried, the routine
                // goes STRAIGHT to $10 (fly off) — not the loc_14534 cooldown/regrab
                // loop that a mid-air jump-off enters. Override releaseCarry()'s
                // released sub-state with the fly-off transition.
                enterCarryFlyoff();
            }
            return;
        }

        if (carryTrigger.usesMgzBossTransitionControl()) {
            updateMgzBossTransitionCarryInput();
            carryParentagePending = true;
            return;
        }

        // Synthetic input injection. For S3K carry states, resolveCpuFrameCounter()
        // reads the stored level counter after replay/bootstrap alignment, which
        // already corresponds to the ROM-visible (Level_frame_counter+1) cadence
        // used by loc_13FFA. CNZ pulses Right every 32 frames; other carry
        // triggers may pulse A/B/C instead.
        if ((frameCounter & carryTrigger.carryInputInjectMask()) == 0) {
            if (carryTrigger.carryInjectsJump()) {
                inputJump = true;
                inputJumpPress = true;
            } else {
                inputRight = true;
            }
        }

        // ROM loc_13FC2 writes x_vel=$100 only when carry starts. The
        // loc_13FFA body only injects a right press every 32 frames, letting
        // normal Tails flight movement raise x_vel ($118/$130/$148...).
        carryParentagePending = true;
    }

    private void updateMgzBossTransitionCarryInput() {
        // ROM loc_14106 ($16): keep flight timer full and pulse A/B/C every
        // eight frames until Tails reaches Camera_Y+$90.
        sidekick.setDoubleJumpProperty((byte) 0xF0);
        if (mgzCarryIntroAscend) {
            if (((frameCounter + 1) & 0x07) == 0) {
                inputJump = true;
                inputJumpPress = true;
            }
            Camera camera = sidekick.currentCamera();
            if (camera != null
                    && ((camera.getY() & 0xFFFF) + 0x90) >= (sidekick.getCentreY() & 0xFFFF)) {
                mgzCarryIntroAscend = false;
                mgzCarryFlapTimer = 0;
            }
            return;
        }

        // ROM loc_14164 ($18): P1 has coarse control over carrier Tails.
        inputLeft = leader.isLeftPressed();
        inputRight = leader.isRightPressed();
        int threshold = leader.isDownPressed() ? 0xC0
                : leader.isUpPressed() ? 0x20
                : 0x58;
        mgzCarryFlapTimer++;
        if (mgzCarryFlapTimer >= threshold) {
            mgzCarryFlapTimer = 0;
            inputJump = true;
            inputJumpPress = true;
        }
    }

    private void updateMgzReleasedCarry() {
        sidekick.setAir(true);
        sidekick.setDoubleJumpProperty((byte) 0xF0);
        sidekick.setForcedAnimationId(flyAnimId);
        carryParentagePending = false;

        // ROM loc_142E2 runs Tails's released rescue/chase body before
        // falling through to Tails_Carry_Sonic's cooldown/proximity probe.
        updateMgzReleasedCarryChase();

        // ROM loc_14534: if byte 1(a2) is nonzero, decrement and return
        // only while it remains nonzero. When the decrement reaches zero, the
        // same frame continues into the proximity pickup test.
        if (releaseCooldown > 0) {
            releaseCooldown--;
            if (releaseCooldown > 0) {
                return;
            }
        }

        if (canRegrabLeaderInPickupRange()) {
            pickupLeaderForCarry();
            flyingCarryingFlag = true;
            carryParentagePending = true;
            mgzReleasedChaseLatched = false;
            return;
        }
    }

    /**
     * ROM routine $E body with {@code Flying_carrying_Sonic_flag} clear, for the
     * solo-leader throwaway carrier (sonic3k.asm loc_13FFA -> loc_14016 ->
     * Tails_Carry_Sonic -> loc_14534). After Sonic is dropped mid-air (jump-out,
     * external velocity change, or carrier hurt), Tails stays in routine $E:
     *
     * <ul>
     *   <li>loc_14016: if Sonic has landed, the routine transitions to $10 (fly
     *       off) regardless of the carry flag — handled here by entering
     *       {@link State#CARRY_FLYOFF}.</li>
     *   <li>loc_13FFA: a Right pulse on the carry cadence keeps Tails drifting.</li>
     *   <li>loc_14534: the cooldown byte counts down; once it reaches zero the
     *       loc_14542 proximity test can re-grab Sonic (sub_1459E) and resume the
     *       carry.</li>
     * </ul>
     *
     * The throwaway carrier therefore never enters the NORMAL follow AI: its only
     * exits are a regrab (back to CARRYING) or, once Sonic lands, the fly-off.
     */
    private void updateTransientReleasedCarry() {
        sidekick.setAir(true);
        sidekick.setDoubleJumpProperty((byte) 0xF0);
        sidekick.setForcedAnimationId(flyAnimId);

        // ROM loc_14016: a landed Sonic ends the carry routine -> routine $10.
        if (leader == null || !leader.getAir()) {
            enterCarryFlyoff();
            return;
        }

        // ROM loc_13FFA: pulse Right on the carry cadence so Tails keeps drifting.
        if ((frameCounter & carryTrigger.carryInputInjectMask()) == 0) {
            inputRight = true;
        }

        // ROM loc_14534: while the cooldown byte is nonzero, decrement and wait.
        // It only falls through to the regrab test on the frame it reaches zero.
        if (releaseCooldown > 0) {
            releaseCooldown--;
            if (releaseCooldown > 0) {
                return;
            }
        }

        // ROM loc_14542: proximity test -> sub_1459E re-parents Sonic and the
        // carry resumes (Flying_carrying_Sonic_flag set again), playing sfx_Grab.
        if (canRegrabLeaderInPickupRange()) {
            pickupLeaderForCarry();
            playGrabSfx();
            flyingCarryingFlag = true;
            carryParentagePending = true;
        }
    }

    /** ROM loc_14542: {@code moveq #sfx_Grab,d0 / jsr Play_SFX} on a successful regrab. */
    private void playGrabSfx() {
        AudioManager audioManager = sidekick.currentAudioManager();
        if (audioManager != null) {
            audioManager.playSfx(GameSound.GRAB);
        }
    }

    private void releaseCarryForCarrierDisabled() {
        boolean mgzBossTransitionCarry = carryTrigger != null && carryTrigger.usesMgzBossTransitionControl();
        if (leader != null && flyingCarryingFlag) {
            ObjectControlState.none().applyTo(leader);
            leader.setForcedAnimationId(-1);
            leader.setAir(true);
        }
        flyingCarryingFlag = false;
        carryParentagePending = false;
        mgzCarryIntroAscend = false;
        mgzCarryFlapTimer = 0;
        mgzReleasedChaseLatched = false;
        releaseCooldown = 0;
        if (!mgzBossTransitionCarry) {
            state = State.NORMAL;
            normalFrameCount = 0;
        }
    }

    private boolean canRegrabLeaderInPickupRange() {
        int dxWindow = signedWord(leader.getCentreX() - sidekick.getCentreX() + 0x10);
        if (dxWindow < 0 || dxWindow >= 0x20) {
            return false;
        }
        int dyWindow = signedWord(leader.getCentreY() - sidekick.getCentreY() - 0x20);
        if (dyWindow < 0 || dyWindow >= 0x10) {
            return false;
        }
        return !leader.isObjectControlled()
                && !leader.isHurt()
                && !leader.getDead()
                && !leader.isDebugMode()
                && !leader.getSpindash();
    }

    private void updateMgzReleasedCarryChase() {
        if (!mgzReleasedChaseLatched) {
            boolean leaderOnScreen = leader.hasRenderFlagOnScreenState()
                    ? leader.isRenderFlagOnScreen()
                    : isSpriteCurrentlyVisible(leader);
            if (leaderOnScreen && leader.getYSpeed() < 0x0300) {
                sidekick.setXSpeed((short) 0);
                if (sidekick.getYSpeed() >= 0x0200) {
                    inputJump = true;
                    inputJumpPress = true;
                } else {
                    mgzCarryFlapTimer++;
                    if (mgzCarryFlapTimer >= 0x58) {
                        mgzCarryFlapTimer = 0;
                        inputJump = true;
                        inputJumpPress = true;
                    }
                }
                return;
            }

            mgzReleasedChaseLatched = true;
            int dy = Math.abs(signedWord(leader.getCentreY() - sidekick.getCentreY()));
            int quarterDy = dy >> 2;
            mgzReleasedChaseYAccel = (short) (quarterDy + (quarterDy >> 1));
            int dx = Math.abs(signedWord(leader.getCentreX() - sidekick.getCentreX()));
            mgzReleasedChaseXAccel = (short) (dx >> 2);
            return;
        }

        int xAccel = mgzReleasedChaseXAccel;
        int sidekickX = sidekick.getCentreX() & 0xFFFF;
        int leaderX = leader.getCentreX() & 0xFFFF;
        if (sidekickX >= leaderX) {
            sidekick.setDirection(Direction.LEFT);
            xAccel = -xAccel;
        } else {
            sidekick.setDirection(Direction.RIGHT);
        }
        sidekick.setXSpeed((short) (sidekick.getXSpeed() + xAccel));

        int probeY = signedWord(sidekick.getCentreY() - 0x10);
        int leaderY = signedWord(leader.getCentreY());
        if (probeY < leaderY) {
            sidekick.setYSpeed((short) (sidekick.getYSpeed() + mgzReleasedChaseYAccel));
        }
    }

    private boolean isSpriteCurrentlyVisible(AbstractPlayableSprite sprite) {
        Camera camera = sprite.currentCamera();
        return camera != null && camera.isOnScreen(sprite);
    }

    private void pickupLeaderForCarry() {
        // ROM sub_1459E (sonic3k.asm:27399): clear Sonic's velocities/angle,
        // parent him to Tails, then copy Tails's current x/y velocity into both
        // Sonic and the latch globals used by Tails_Carry_Sonic.
        ObjectControlState.nativeBit7FullControl().applyTo(leader);
        leader.setAir(true);
        leader.setRolling(false);
        leader.setRollingJump(false);
        leader.setSpindash(false);
        leader.setSpindashCounter((short) 0);
        leader.setJumping(false);
        leader.setGSpeed((short) 0);
        leader.setCentreXPreserveSubpixel(sidekick.getCentreX());
        leader.setCentreYPreserveSubpixel(
                (short) (sidekick.getCentreY() + carryTrigger.carryDescendOffsetY()));
        leader.setDirection(sidekick.getDirection());
        leader.setForcedAnimationId(leader.resolveAnimationId(CanonicalAnimation.TAILS_CARRIED));
        leader.setXSpeed(sidekick.getXSpeed());
        leader.setYSpeed(sidekick.getYSpeed());
        carryLatchX = leader.getXSpeed();
        carryLatchY = leader.getYSpeed();
    }

    private int signedWord(int value) {
        return (short) value;
    }

    /**
     * ROM {@code Tails_Catch_Up_Flying} (sonic3k.asm:26474). Entered when
     * {@code Tails_CPU_routine == 2}. Waits on either (a) the sidekick's Ctrl_2
     * A/B/C/START press, or (b) a 64-frame gate firing while Sonic's
     * object_control sign bit is clear and Sonic is not super. On trigger, teleports Tails to
     * (Sonic.x, Sonic.y - 0xC0), sets routine = 4, and enters flight AI.
     *
     * <p>Stubbed in Task 2; body lands in Task 4.
     */
    private void updateCatchUpFlight() {
        // ROM Tails_Catch_Up_Flying (sonic3k.asm:26474-26531)
        boolean trigger = false;
        // Routine 2 does not write a delayed leader word into Ctrl_2_logical.
        // After the marker frame, expose only live Player 2 logical input;
        // in one-player traces this clears the normal-routine latch that
        // Kill_Character/sub_13ECA left visible for the previous sample.
        diagnosticCtrl2HeldLatch = controller2Held & MANUAL_HELD_MASK;
        diagnosticCtrl2PressedLatch = controller2Logical & MANUAL_HELD_MASK;
        int catchUpFrameCounter = catchUpFrameCounterOverride >= 0
                ? catchUpFrameCounterOverride
                : (catchUpUsesRomVisibleLevelFrameCounter && cpuFrameCounterFromStoredLevelFrame
                        ? frameCounter + 1
                        : frameCounter);
        catchUpFrameCounterOverride = -1;

        // Ctrl_2_logical A/B/C/START press → immediate trigger
        if ((controller2Logical & (AbstractPlayableSprite.INPUT_JUMP | INPUT_START)) != 0) {
            trigger = true;
        } else {
            // ROM checks Sonic's object_control with `bmi`, so only bit 7 suppresses
            // the 64-frame catch-up warp (sonic3k.asm:26478-26488).
            if ((catchUpFrameCounter & 0x3F) == 0
                    && (!leader.isObjectControlled() || leader.isObjectControlAllowsCpu())
                    && !leader.isSuperSonic()) {
                trigger = true;
            }
        }

        if (!trigger) {
            // ROM routine 2's wait path only returns: Tails_Catch_Up_Flying
            // branches to locret_13BF6 without writing object_control until
            // the catch-up trigger fires (sonic3k.asm:26474-26500). Preserve
            // the current object-control state so CNZ cylinder releases
            // (sonic3k.asm:68071-68077) can expose the marker to the same
            // screen-boundary/movement writes recorded at CNZ1 F4790.
            return;
        }

        // sonic3k.asm:26487 (loc_13B50) — teleport and enter FLIGHT_AUTO_RECOVERY.
        int targetX = leader.getCentreX() & 0xFFFF;
        int targetY = leader.getCentreY() & 0xFFFF;
        catchUpTargetX = targetX;
        catchUpTargetY = targetY;
        sidekick.setCentreXPreserveSubpixel((short) targetX);
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        int catchUpYOffset = fs != null
                ? fs.sidekickCatchUpYOffset()
                : PhysicsFeatureSet.SIDEKICK_CATCH_UP_Y_OFFSET_S3K;
        sidekick.setCentreYPreserveSubpixel((short) (targetY - catchUpYOffset));
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setAir(true);
        sidekick.setRolling(false);
        sidekick.setRollingJump(false);
        sidekick.setJumping(false);
        sidekick.setPushing(false);
        sidekick.setOnObject(false);
        sidekick.setMoveLockTimer(0);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        ObjectControlState.nativeBit7FullControl().applyTo(sidekick);
        // ROM loc_13B50 (sonic3k.asm:26502-26508) writes double_jump_flag=0,
        // status=2, and object_control=$81. Movement remains owned by the CPU
        // flight routine; normal air physics must not be used to carry Tails.
        sidekick.setDoubleJumpFlag(0);

        flightTimer = 0;
        catchUpUsesRomVisibleLevelFrameCounter = false;
        state = State.FLIGHT_AUTO_RECOVERY;
    }

    /**
     * ROM {@code Tails_FlySwim_Unknown} (sonic3k.asm:26534). Entered when
     * {@code Tails_CPU_routine == 4}. Per-frame: increments Tails_CPU_flight_timer;
     * after 5*60 frames off-screen, falls back to {@code CATCH_UP_FLIGHT}.
     * Otherwise computes the 16-frame delayed Sonic position, steers Tails toward
     * it (X step &le; 0xC, Y step = 1 plus optional -0x20 lead), and transitions
     * to {@code NORMAL} (routine 0x06) once Tails is close enough to Sonic and
     * Sonic isn't hurt/dead.
     *
     * <p>Stubbed in Task 2; body lands in Task 5.
     */
    private void updateFlightAutoRecovery() {
        // ROM Tails_FlySwim_Unknown (sonic3k.asm:26534-26653).
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        final int AUTO_LAND_FRAMES = fs != null
                ? fs.sidekickFlightAutoLandFrames()
                : PhysicsFeatureSet.SIDEKICK_FLIGHT_AUTO_LAND_FRAMES_S3K;
        final int MAX_X_STEP = fs != null
                ? fs.sidekickFlightMaxXStep()
                : PhysicsFeatureSet.SIDEKICK_FLIGHT_MAX_X_STEP_S3K;
        final int Y_STEP = fs != null
                ? fs.sidekickFlightYStep()
                : PhysicsFeatureSet.SIDEKICK_FLIGHT_Y_STEP_S3K;
        final int LEAD_SUPPRESS = fs != null
                ? fs.sidekickFlightLeadSuppressGSpeed()
                : PhysicsFeatureSet.SIDEKICK_FLIGHT_LEAD_SUPPRESS_GSPEED_S3K;
        final int LEAD_OFFSET = fs != null
                ? fs.sidekickFlightLeadXOffset()
                : PhysicsFeatureSet.SIDEKICK_FLIGHT_LEAD_X_OFFSET_S3K;
        final int FLIGHT_FUEL = (8 * 60) / 2;   // ROM loc_13C3A:26552 double_jump_property reload

        // 1. Off-screen timer. The ROM check is `tst.b render_flags(a0); bmi.s loc_13C3A`.
        //    Engine: hasRenderFlagOnScreenState() + isRenderFlagOnScreen() mirrors the bit.
        boolean onScreen = sidekick.hasRenderFlagOnScreenState()
                ? sidekick.isRenderFlagOnScreen()
                : isCurrentlyVisible();
        if (!onScreen) {
            flightTimer++;
            if (flightTimer >= AUTO_LAND_FRAMES) {
                // ROM sonic3k.asm:26540-26547 — reset and bounce back to CATCH_UP.
                // S2 uses the same word writes at s2.asm:38769-38775. These
                // write x_pos/y_pos only, preserving x_sub/y_sub for the later
                // MoveSprite position add.
                flightTimer = 0;
                sidekick.setCentreXPreserveSubpixel((short) 0);
                sidekick.setCentreYPreserveSubpixel((short) 0);
                ObjectControlState.nativeBit7FullControl().applyTo(sidekick);
                sidekick.setAir(true);
                sidekick.setDoubleJumpFlag(1);
                sidekick.setDoubleJumpProperty((byte) FLIGHT_FUEL);
                sidekick.setForcedAnimationId(flyAnimId);
                state = State.CATCH_UP_FLIGHT;
                return;
            }
        } else {
            // ROM loc_13C3A (sonic3k.asm:26551-26555): every on-screen frame
            // resets the flight timer, refuels double_jump_property, and ORs
            // Status_InAir to keep Tails in flight recovery even if terrain
            // collision touched the flag on the previous movement tick.
            // (8*60)/2 = 240. The refuel is what keeps Tails's flapping
            // animation + flight state active indefinitely while on-screen.
            flightTimer = 0;
            sidekick.setDoubleJumpProperty((byte) FLIGHT_FUEL);
            sidekick.setAir(true);
            // Tails_Set_Flying_Animation is normally called here; the engine's
            // animation is driven by the forced-anim slot already set at entry.
        }

        // 3. Target = Sonic's 16-frame-delayed position. ROM
        //    Tails_FlySwim_Unknown reads Pos_table directly
        //    (sonic3k.asm:26564-26565) with NO lead offset — the `subi.w #$20, d2`
        //    adjustment lives only in the NORMAL follow AI at loc_13DA6
        //    (sonic3k.asm:26690-26694). An earlier iteration of this body
        //    mis-applied that offset here and produced a chronic -0x20 X drift.
        int targetX = leader.getCentreX(ROM_FOLLOW_DELAY_FRAMES) & 0xFFFF;
        int targetY = leader.getCentreY(ROM_FOLLOW_DELAY_FRAMES) & 0xFFFF;
        catchUpTargetX = targetX;
        catchUpTargetY = targetY;

        // 4. X steer: dx = Tails.x - target.x. Track residual distance AFTER
        //    the step (ROM d0 is zeroed in the overshoot-clamp branch at
        //    loc_13CA6/loc_13CAA, so the close-enough check uses the
        //    post-step value, not the pre-step value).
        int dx = (sidekick.getCentreX() & 0xFFFF) - targetX;
        int residualX = dx;
        if (dx != 0) {
            int absDx = Math.abs(dx);
            int step = absDx >> 4;
            if (step > MAX_X_STEP) {
                step = MAX_X_STEP;
            }
            // ROM sonic3k.asm:26580-26586: move.b x_vel(a1), d1 reads the HIGH
            // byte of Sonic's 16-bit x_vel (big-endian 68000). Engine x_vel is
            // stored in subpixels (256/px), so the ROM's "pixel velocity" byte
            // is (xSpeed >> 8) & 0xFF. Use the signed 8-bit absolute value.
            int sonicPixelXVel = (leader.getXSpeed() >> 8);
            int sonicXVelMag = Math.abs((byte) sonicPixelXVel);
            step += sonicXVelMag + 1;   // ROM addq.w #1, d2
            if (step >= absDx) {
                step = absDx;           // Clamp to |dx| — overshoot branch
                residualX = 0;          //   (loc_13CA6 / loc_13CAA clear d0)
            }
            int newX = (dx > 0)
                    ? (sidekick.getCentreX() & 0xFFFF) - step
                    : (sidekick.getCentreX() & 0xFFFF) + step;
            sidekick.setCentreXPreserveSubpixel((short) newX);
        }

        // 5. Y steer: +/-1 per frame. ROM branches on the signed word result
        // of y_pos - Tails_CPU_target_Y (`sub.w` followed by `bmi`;
        // sonic3k.asm:26614-26622). This matters when catch-up starts above
        // the level top: $FFD9 is a negative Y, not a huge unsigned value.
        int dy = signedWord((sidekick.getCentreY() & 0xFFFF) - targetY);
        int residualY = dy;
        if (dy != 0) {
            int newY = (dy > 0)
                    ? (sidekick.getCentreY() & 0xFFFF) - Y_STEP
                    : (sidekick.getCentreY() & 0xFFFF) + Y_STEP;
            sidekick.setCentreYPreserveSubpixel((short) newY);
        }

        // 6. Transition to NORMAL when close enough AND the delayed Stat_table
        //    sample allows it. ROM reads byte 2 from the same delayed
        //    Stat_table slot as the target position (S3K sonic3k.asm:26623-26630;
        //    S2 s2.asm:38871-38876), not live Sonic object_control. This matters
        //    while Sonic is riding ROM object-controlled carriers such as MGZ's
        //    top platform: the delayed status byte can be clear while live
        //    object_control is still nonzero.
        boolean closeEnough = residualX == 0 && residualY == 0;
        byte delayedStatus = leader.getStatusHistory(ROM_FOLLOW_DELAY_FRAMES);
        int statusBlockerMask = fs != null
                ? fs.sidekickFlyLandStatusBlockerMask()
                : PhysicsFeatureSet.SIDEKICK_FLY_LAND_BLOCKERS_S2;
        boolean delayedStatusAllowsLand = (delayedStatus & statusBlockerMask) == 0;
        boolean leaderRoutineAllowsLand = fs == null
                || !fs.sidekickFlyLandRequiresLeaderAlive()
                || !leader.getDead();

        if (closeEnough && delayedStatusAllowsLand && leaderRoutineAllowsLand) {
            // ROM sonic3k.asm:26631-26648 — return to NORMAL (routine 0x06).
            ObjectControlState.none().applyTo(sidekick);
            sidekick.setControlLocked(false);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setMoveLockTimer(0);
            sidekick.setForcedAnimationId(-1);
            sidekick.setAir(true);
            // ROM loc_1384A (sonic3k.asm:26213): while object_control bit 0 is
            // set (FLIGHT_AUTO_RECOVERY keeps it high), double_jump_flag is
            // cleared every frame by the dispatcher. On the NORMAL transition
            // the engine just cleared object_control, so the dispatcher's
            // auto-clear won't fire next tick; without this explicit write,
            // doubleJumpFlag would still be 1 and
            // PlayableSpriteMovement.applyGravity() would keep applying the
            // +0x08 flight gravity to a grounded Tails in NORMAL.
            sidekick.setDoubleJumpFlag(0);
            state = State.NORMAL;
            normalFrameCount = 0;
            if (suppressNextLevelEventNormalMovement) {
                // A level-event marker release reaches NORMAL through this
                // handoff tick, which does not run normal follow AI yet. Clear
                // the release-side suppression here so the next object tick can
                // use fresh Ctrl_2 state; do not suppress handoff physics,
                // because ROM still applies the airborne +$38 gravity step.
                suppressNextLevelEventNormalMovement = false;
            }
            return;
        }

        // 7. Otherwise keep object_control locked to keep flight AI active.
        ObjectControlState.nativeBit7FullControl().applyTo(sidekick);
    }

    /**
     * Finishes the Tails-carry body after Tails has run current-frame movement.
     *
     * <p>ROM {@code Tails_FlyingSwimming} runs {@code Tails_Move_FlySwim},
     * input acceleration, {@code MoveSprite_TestGravity2}, and
     * {@code Tails_DoLevelCollision} before calling {@code Tails_Carry_Sonic}.
     * The controller update only prepares carry input/release checks; this hook
     * mirrors the later {@code Tails_Carry_Sonic} parentage/probe timing.
     */
    public void finishCarryAfterCarrierMovement() {
        if (!carryParentagePending || state != State.CARRYING || !flyingCarryingFlag
                || leader == null || carryTrigger == null) {
            return;
        }
        carryParentagePending = false;

        // Sonic parentage (Tails_Carry_Sonic steps 5 + 8):
        //   x_pos = Tails.x_pos
        //   y_pos = Tails.y_pos + carryDescendOffsetY()
        //   x_vel = Tails.x_vel
        //   y_vel = Tails.y_vel
        leader.setCentreXPreserveSubpixel(sidekick.getCentreX());
        leader.setCentreYPreserveSubpixel(
                (short) (sidekick.getCentreY() + carryTrigger.carryDescendOffsetY()));
        leader.setDirection(sidekick.getDirection());
        leader.setXSpeed(sidekick.getXSpeed());
        leader.setYSpeed(sidekick.getYSpeed());

        // ROM Tails_Carry_Sonic loc_144F8 (sonic3k.asm:27328-27331):
        //   movem.l d0-a6,-(sp)
        //   lea     (Player_1).w,a0
        //   bsr.w   SonicKnux_DoLevelCollision
        //   movem.l (sp)+,d0-a6
        //
        // After parentage writes the ROM explicitly runs the full airborne
        // collision on Sonic (Player_1).  That probe's Player_TouchFloor tail
        // (sonic3k.asm:24366) clears Status_InAir when Tails's descended
        // position puts Sonic on ground.  Next frame's in-air check at
        // sonic3k.asm:27227 then branches to loc_1445A (release path A).
        //
        // Without this probe, the engine leaves Sonic's air flag set forever
        // while object_control holds him to Tails, so the ground-release path
        // is unreachable once Tails lands.
        //
        // NOTE: the normal landing handler (PlayableSpriteMovement.calculateLanding)
        // calls resetOnFloor(), which early-returns when isObjectControlled() is
        // true, so it would not apply this carried landing state. The inline
        // handler mirrors the flat-floor result used by SonicKnux_DoLevelCollision:
        // y_vel = 0, inertia = x_vel, then the Player_TouchFloor tail clears
        // Status_InAir/Push/RollJump (sonic3k.asm:24366-24369).
        CollisionSystem collision = Objects.requireNonNull(
                leader.currentCollisionSystem(),
                "CollisionSystem must be available during CARRYING state "
                        + "(Tails-carry post-parentage probe, sonic3k.asm:27330)");
        collision.resolveAirCollision(FrameCollisionPlan.terrainOnly(), leader, sprite -> {
            // ROM Player_TouchFloor (sonic3k.asm:24366-24369):
            //   bclr #Status_InAir,status(a0)
            //   bclr #Status_Push,status(a0)
            //   bclr #Status_RollJump,status(a0)
            //   move.b #0,jumping(a0)
            sprite.setSubpixelRaw(sprite.getXSubpixelRaw(), 0);
            sprite.setYSpeed((short) 0);
            sprite.setGSpeed(sprite.getXSpeed());
            sprite.setAir(false);
            sprite.setPushing(false);
            sprite.setRollingJump(false);
            sprite.setJumping(false);
        });

        // Refresh the latch AFTER our writes so the next frame's compare is
        // against what we just wrote, not stale values.  The probe above may
        // have cleared the leader's y_vel via the collision adjustment, so
        // the latch captures the post-probe values to avoid a false latch
        // mismatch on the next frame.
        carryLatchX = leader.getXSpeed();
        carryLatchY = leader.getYSpeed();
    }

    private void performJumpRelease() {
        short xMag = carryTrigger.carryReleaseJumpXVel();
        short xVel = leader.getDirection() == Direction.LEFT
                ? (short) -xMag
                : xMag;
        leader.setXSpeed(xVel);
        leader.setYSpeed(carryTrigger.carryReleaseJumpYVel());
        leader.setAir(true);
        leader.setJumping(true);
        leader.setRolling(true);
        leader.setRollingJump(false);
        releaseCarry(carryTrigger.carryJumpReleaseCooldownFrames());
    }

    private void releaseCarry(int cooldownFrames) {
        boolean mgzBossTransitionCarry = carryTrigger != null && carryTrigger.usesMgzBossTransitionControl();
        ObjectControlState.none().applyTo(leader);
        leader.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setForcedAnimationId(mgzBossTransitionCarry ? flyAnimId : -1);
        flyingCarryingFlag = false;
        carryParentagePending = false;
        mgzCarryIntroAscend = false;
        mgzCarryFlapTimer = 0;
        mgzReleasedChaseLatched = false;
        releaseCooldown = cooldownFrames;
        if (mgzBossTransitionCarry) {
            state = State.CARRYING;
            sidekick.setAir(true);
            sidekick.setDoubleJumpProperty((byte) 0xF0);
        } else if (transientCarrySidekick) {
            // ROM Tails_Carry_Sonic jump-out / external-velocity / hurt release
            // (loc_1445A/loc_14460/loc_14466) clears Flying_carrying_Sonic_flag and
            // sets the cooldown byte but leaves Tails in routine $E. Tails keeps
            // flying and runs the loc_14534 cooldown/regrab loop
            // (updateTransientReleasedCarry) until Sonic lands -> routine $10
            // fly-off, or returns to pickup range -> regrab. The throwaway carrier
            // never enters the NORMAL follow AI. (The landing branch in
            // updateCarrying overrides this with enterCarryFlyoff(), matching
            // loc_14016's direct $10 transition when Sonic touches down.)
            flyingCarryingFlag = false;
            carryParentagePending = false;
            state = State.CARRYING;
            sidekick.setAir(true);
            sidekick.setDoubleJumpProperty((byte) 0xF0);
            sidekick.setForcedAnimationId(flyAnimId);
        } else {
            state = State.NORMAL;
            normalFrameCount = 0;
        }
    }

    /**
     * Enters ROM Tails_CPU_routine $10 (loc_1408A, sonic3k.asm:26953-26972) for a
     * throwaway intro carrier that has just dropped a solo leader. Mirrors the
     * routine's setup: keep Tails airborne with the flight animation and a topped-
     * up double_jump_property so flight stays active while it leaves the screen.
     */
    private void enterCarryFlyoff() {
        state = State.CARRY_FLYOFF;
        transientFlyoffDespawned = false;
        flightTimer = 0;
        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        flyingCarryingFlag = false;
        carryParentagePending = false;
        // ROM routine $10 (loc_1408A) does NOT object-control Tails or write its
        // position; Tails stays FLYING (double_jump_flag persists from the carry)
        // with the flight timer (double_jump_property) topped up, and normal
        // Tails_FlyingSwimming physics moves it — driven by the synthetic Ctrl_2
        // pulses injected in updateCarryFlyoff(). Mirror the carry's flight state
        // (setControlLocked(false), no object-control) so the flyaway runs at
        // flight pace instead of a fixed per-frame position step.
        sidekick.setAir(true);
        sidekick.setDoubleJumpFlag(1);
        sidekick.setDoubleJumpProperty((byte) 0xF0);
        sidekick.setControlLocked(false);
        sidekick.setForcedAnimationId(flyAnimId);
        ObjectControlState.none().applyTo(sidekick);
    }

    /**
     * ROM Tails_CPU_routine $10 body (loc_1408A, sonic3k.asm:26953-26972). The ROM
     * pulses A/B/C + Right into Ctrl_2 every 16 frames so Tails flaps up and to the
     * right; once {@code render_flags} reports it off-screen, loc_140AC clears the
     * object code pointer (deleting the slot). This is a one-shot intro cutscene
     * with no recorded trace, so the engine drives the flyaway directly through the
     * object-controlled flight path (matching {@link #updateCatchUpFlight()} style)
     * and removes the temporary sprite when it leaves the camera.
     */
    private void updateCarryFlyoff() {
        // ROM loc_1408A (sonic3k.asm:26953-26972): each frame clear Ctrl_2_logical
        // and top up the flight timer; then every 16 frames (andi.b #$F) pulse
        // A/B/C + Right into Ctrl_2 so Tails flaps up and drifts right through the
        // normal Tails_FlyingSwimming flight physics. There is no direct position
        // write — the old fixed +6px/-4px-per-frame step made the carrier shoot
        // off far faster than the ROM's flight-paced bob.
        sidekick.setAir(true);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setDoubleJumpProperty((byte) 0xF0);

        if ((frameCounter & 0xF) == 0) {
            inputJump = true;        // A/B/C — flight flap (upward thrust)
            inputJumpPress = true;
            inputRight = true;       // drift right
        }

        // ROM loc_140AC: once render_flags reports the carrier off-screen, clear
        // its object pointer (delete the slot).
        boolean onScreen = sidekick.hasRenderFlagOnScreenState()
                ? sidekick.isRenderFlagOnScreen()
                : isCurrentlyVisible();
        if (!onScreen) {
            completeCarryFlyoffDespawn();
        }
    }

    /**
     * ROM loc_140AC (sonic3k.asm:26963-26969) clears the carrier's object code
     * pointer once it is off-screen, freeing the slot. The engine removes the
     * temporary sidekick from the {@link SpriteManager} so it never respawns.
     */
    private void completeCarryFlyoffDespawn() {
        transientFlyoffDespawned = true;
        leader = null;
        // The owning SpriteManager observes this flag after running the
        // controller and removes the throwaway carrier from its temporary
        // sidekick roster (see SpriteManager.update). The controller does not
        // reach back into global services to mutate the sprite roster.
    }

    private void applyManualControl() {
        inputUp = (controller2Held & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (controller2Held & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputLeft = (controller2Held & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (controller2Held & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputJump = (controller2Held & AbstractPlayableSprite.INPUT_JUMP) != 0;
        inputJumpPress = (controller2Logical & AbstractPlayableSprite.INPUT_JUMP) != 0;
        diagnosticGeneratedPressedInput = controller2Logical & MANUAL_HELD_MASK;
        controlCounter--;
    }

    private void enterApproachingState() {
        AbstractPlayableSprite target = getEffectiveLeader();
        if (target == null) {
            triggerDespawn(DespawnCause.EXPLICIT);
            return;
        }
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        boolean started = respawnStrategy.beginApproach(sidekick, target);
        if (!started) {
            triggerDespawn(DespawnCause.EXPLICIT);
            return;
        }
        state = State.APPROACHING;
        despawnCounter = 0;
        controlCounter = 0;
        normalFrameCount = 0;
    }

    int clampTargetYToWater(int targetY) {
        LevelManager levelManager = sidekick.currentLevelManager();
        if (levelManager == null) {
            return targetY;
        }
        WaterSystem waterSystem = sidekick.currentWaterSystem();
        // TailsCPU_Respawn / TailsCPU_Flying clamp target_y against
        // Water_Level_1, the gameplay waterline used by Sonic_Water, not the
        // non-oscillated base/current water register.
        int waterY = waterSystem.getGameplayWaterLevelY(levelManager.getCurrentZone(), levelManager.getCurrentAct());
        if (waterY == 0) {
            return targetY;
        }
        return Math.min(targetY, waterY - 0x10);
    }

    /**
     * ROM {@code TailsCPU_CheckDespawn} (docs/s2disasm/s2.asm:39403-39446) with
     * the slot-based {@code interact(a0)} model.
     *
     * <p>ROM control flow:
     * <pre>
     *   if on_screen:                            ResetRespawnTimer; UpdateObjInteract; rts
     *   else if NOT on_object:                   TickRespawnTimer
     *   else (off-screen, on_object):
     *       a3 = Object_RAM[interact(a0)]        (live slot dereference)
     *       if Tails_interact_ID != id(a3):      Despawn
     *       else:                                fall to TickRespawnTimer
     *   TickRespawnTimer:
     *       respawn_counter++
     *       if respawn_counter < $12C:           UpdateObjInteract; rts
     *       else:                                Despawn
     *   UpdateObjInteract:
     *       Tails_interact_ID = id(Object_RAM[interact(a0)])
     * </pre>
     *
     * <p>The id snapshot ({@link #lastInteractObjectId} = {@code Tails_interact_ID})
     * is refreshed every non-despawning frame from the LIVE object currently in
     * the persistent {@code interact} slot, NOT from the (instance-resolved)
     * latch id. The off-screen compare therefore fires only when the slot's live
     * object id changed between consecutive frames — i.e. the slot was recycled
     * to a different object (mtz1 f375: object {@code 0x01} → SteamSpring
     * {@code 0x42}) — and stays quiet when the same object persists in the slot
     * off-screen (htz2 f795: HTZ platform {@code 0x41} stays put). An emptied
     * once-ridden engine slot ({@link ObjectManager#objectIdInSlot} returns
     * {@code -1}) maps back to ROM's zeroed object id, so the compare sees the
     * same id change ROM sees after {@code DeleteObject} clears the slot.
     *
     * <p>S3K's {@code sub_13EFC} (docs/skdisasm/sonic3k.asm:26816-26833) compares
     * the routine-pointer high word, which is identical for virtually all
     * gameplay objects, so its only practical despawn trigger is a slot freed by
     * {@code Delete_Referenced_Sprite} (id word → 0, sonic3k.asm:36116-36124).
     * That stays modelled by the riding-instance-loss path, gated by
     * {@link PhysicsFeatureSet#sidekickDespawnUsesRidingInstanceLoss()} (S3K
     * true). The S2 slot-id-mismatch path is gated by
     * {@link PhysicsFeatureSet#sidekickDespawnUsesObjectIdMismatch()} (S2 true,
     * S3K false), so the two games never both fire.
     */
    private boolean checkDespawn() {
        boolean onScreen = sidekick.hasRenderFlagOnScreenState()
                ? sidekick.isRenderFlagOnScreen()
                : isCurrentlyVisible();

        // RAW id of the LIVE object currently occupying the persistent
        // interact(a0) slot. -1 == slot empty in the engine (covers BOTH
        // never-ridden, interactSlotIndex < 0, AND a since-deleted occupant,
        // interactSlotIndex >= 0 but no live object).
        int rawLiveSlotId = rawInteractSlotObjectId();

        // ROM TailsCPU_UpdateObjInteract writes Tails_interact_ID = id(slot)
        // (s2.asm:39435-39446) every non-despawning frame. For the never-ridden
        // case ROM dereferences default slot 0 = ObjID_Sonic 0x01, so the
        // snapshot must be seeded with that concrete value (this is what makes
        // a later off-screen first-landing on a different-id object mismatch).
        // For a ridden-then-emptied slot ROM writes the zeroed object id. The
        // off-screen mismatch path still compares before this refresh, so a
        // deleted ride slot despawns before the zero write can mask it.
        int snapshotSeedId = snapshotSeedInteractSlotId(rawLiveSlotId);

        if (onScreen) {
            // ROM TailsCPU_ResetRespawnTimer -> TailsCPU_UpdateObjInteract.
            despawnCounter = 0;
            refreshInteractIdSnapshot(snapshotSeedId);
            return false;
        }

        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean useRidingInstanceLossDespawn = fs != null
                && fs.sidekickDespawnUsesRidingInstanceLoss();
        boolean useSlotIdMismatchDespawn = fs == null
                || fs.sidekickDespawnUsesObjectIdMismatch();

        if (sidekick.isOnObject()) {
            // S3K: sub_13EFC's only practical trigger is a slot freed by
            // Delete_Referenced_Sprite (id word zeroed -> mismatch -> despawn).
            // Tracked via the latched-instance reference because S3K's
            // latchedSolidObjectId is sticky across destruction.
            if (useRidingInstanceLossDespawn) {
                ObjectInstance ridingInstance = sidekick.getLatchedSolidObjectInstance();
                if ((sidekick.getLatchedSolidObjectId() & 0xFF) != 0
                        && ridingInstance != null
                        && isLatchedRideSlotFreed(ridingInstance)) {
                    refreshInteractIdSnapshot(snapshotSeedId);
                    triggerDespawn(DespawnCause.OBJECT_ID_MISMATCH);
                    return true;
                }
            }

            // S2: cmp.b id(a3),d0 — latched Tails_interact_ID snapshot vs the
            // live id byte ROM dereferences at interact(a0). romEffectiveInteractSlotId
            // resolves the single unified ROM model across all three cases:
            //   never-ridden (slot < 0)        -> ROM default slot 0 = 0x01,
            //   slot occupied by a live object -> that object's id,
            //   slot once-ridden then emptied  -> ROM zeroed slot id 0.
            // (docs/s2disasm/s2.asm:39403-39429,35980-36006,30324-30339;
            //  s2.constants.asm:603,1101). The lastInteractObjectId >= 0
            // precondition still excludes the never-stood-on-anything snapshot,
            // preserving the EHZ1 guard semantics. Gated to S2's id-mismatch model.
            if (useSlotIdMismatchDespawn) {
                int romLiveSlotId = romEffectiveInteractSlotId(rawLiveSlotId);
                if (lastInteractObjectId >= 0
                        && romLiveSlotId != lastInteractObjectId) {
                    refreshInteractIdSnapshot(snapshotSeedId);
                    triggerDespawn(DespawnCause.OBJECT_ID_MISMATCH);
                    return true;
                }
            }
        }

        // ROM TailsCPU_TickRespawnTimer.
        despawnCounter++;
        if (despawnCounter >= DESPAWN_TIMEOUT) {
            refreshInteractIdSnapshot(snapshotSeedId);
            triggerDespawn(DespawnCause.OFF_SCREEN_TIMEOUT);
            return true;
        }
        // ROM falls through to TailsCPU_UpdateObjInteract.
        refreshInteractIdSnapshot(snapshotSeedId);
        return false;
    }

    /**
     * Raw engine read of the persistent {@code interact(a0)} slot: the live
     * object's id byte, or {@code -1} when the engine slot is empty. "Empty"
     * here covers BOTH a never-ridden sidekick ({@code interactSlotIndex < 0})
     * and a once-occupied slot whose object has been deleted/recycled away
     * ({@code interactSlotIndex >= 0} but {@code objectIdInSlot} finds no live
     * occupant). This raw form is converted through
     * {@link #snapshotSeedInteractSlotId(int)} before updating the ROM-visible
     * snapshot, so a ridden-then-emptied slot stores the concrete zero id that
     * ROM reads from cleared object RAM.
     *
     * <p>For the ROM despawn comparison itself, use
     * {@link #romEffectiveInteractSlotId(int)}, which maps these two engine
     * "empty" cases to the two DISTINCT concrete ids ROM actually dereferences.
     * Mirrors ROM {@code a3 = Object_RAM + interact(a0)*object_size; id(a3)}.
     */
    private int rawInteractSlotObjectId() {
        int slot = sidekick.getInteractSlotIndex();
        if (slot < 0) {
            return -1;
        }
        LevelManager levelManager = sidekick.currentLevelManagerIfAvailable();
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return -1;
        }
        return levelManager.getObjectManager().objectIdInSlot(slot);
    }

    /**
     * ROM-faithful live id of the {@code interact(a0)} slot for the
     * {@code TailsCPU_CheckDespawn} {@code cmp.b id(a3),d0} compare
     * (docs/s2disasm/s2.asm:39403-39429). ROM dereferences the slot's object
     * RAM unconditionally; the engine collapses two physically-distinct ROM
     * states into a single {@code -1} ({@link #rawInteractSlotObjectId()}), so
     * this method re-derives the concrete byte ROM would read in each:
     *
     * <ul>
     *   <li><b>Never rode anything</b> ({@code interactSlotIndex < 0}): ROM
     *       {@code interact(a0)} is a byte slot index that DEFAULTS TO 0 and is
     *       written only by {@code RideObject_SetRide} (s2.asm:35980-36006); it
     *       is never cleared. Slot 0 is {@code MainCharacter}
     *       (s2.constants.asm:1101), whose id is {@code ObjID_Sonic} = 0x01
     *       (s2.constants.asm:603). So an un-ridden Tails dereferences id 0x01,
     *       not "empty" (e.g. mtz1 f375: interact stays 0 -> 0x01 through the
     *       airborne approach, then a different-id SteamSpring 0x42 landing
     *       off-screen yields 0x01 != 0x42 -> immediate despawn).</li>
     *   <li><b>Rode something, slot still occupied</b>
     *       ({@code interactSlotIndex >= 0}, live object present): the live
     *       occupant's id ({@code objectIdInSlot}).</li>
     *   <li><b>Rode something, slot now emptied</b>
     *       ({@code interactSlotIndex >= 0}, object deleted/recycled away): ROM
     *       reads id 0 because {@code DeleteObject} zeroes the whole object RAM
     *       (s2.asm:30324-30339), so {@code cmp.b id(a3),d0} sees 0 != the
     *       latched {@code Tails_interact_ID} -> despawn (e.g. mtz3 f2638: the
     *       MTZ long platform Obj65 Tails rode deletes itself off-screen).</li>
     * </ul>
     *
     * <p>Only called on the S2 id-mismatch despawn path (the caller gates with
     * {@link PhysicsFeatureSet#sidekickDespawnUsesObjectIdMismatch()}); S3K uses
     * the freed-slot instance path
     * ({@link PhysicsFeatureSet#sidekickDespawnUsesRidingInstanceLoss()}) and
     * never reaches here. The {@code lastInteractObjectId >= 0} precondition at
     * the call site still excludes the never-stood-on-anything snapshot.
     *
     * @param rawLiveSlotId the value from {@link #rawInteractSlotObjectId()}
     */
    private int romEffectiveInteractSlotId(int rawLiveSlotId) {
        if (rawLiveSlotId >= 0) {
            return rawLiveSlotId;
        }
        // rawLiveSlotId == -1: engine "empty". Distinguish the two ROM states.
        if (sidekick.getInteractSlotIndex() < 0) {
            // Never rode anything: ROM dereferences default slot 0 = ObjID_Sonic.
            return ROM_DEFAULT_INTERACT_OBJECT_ID;
        }
        // Rode something, slot since emptied: ROM reads the zeroed slot id 0.
        return ROM_DELETED_INTERACT_SLOT_ID;
    }

    /**
     * The id ROM {@code TailsCPU_UpdateObjInteract} would store into
     * {@code Tails_interact_ID} ({@code = id(slot)}, s2.asm:39435-39446) on a
     * non-despawning frame. All slot states match
     * {@link #romEffectiveInteractSlotId(int)} exactly:
     *
     * <ul>
     *   <li><b>Never rode anything</b> ({@code interactSlotIndex < 0}): ROM
     *       writes {@code id(slot 0)} = {@code ObjID_Sonic} 0x01 every frame, so
     *       seed the snapshot with 0x01. This is what makes a subsequent
     *       off-screen first landing on a different-id object register as a real
     *       {@code cmp.b} mismatch (mtz1 f375: snapshot 0x01 vs SteamSpring 0x42
     *       -> despawn). Without this seed the {@code lastInteractObjectId >= 0}
     *       guard would never arm for an un-ridden sidekick.</li>
     *   <li><b>Slot occupied by a live object</b>: the real occupant id.</li>
     *   <li><b>Rode something, slot since emptied</b>
     *       ({@code interactSlotIndex >= 0}, no live occupant): ROM reads the
     *       zeroed slot id and stores {@code 0}. The off-screen despawn compare
     *       runs before this refresh, so a deleted ride slot is still detected
     *       before the snapshot is overwritten.</li>
     * </ul>
     */
    private int snapshotSeedInteractSlotId(int rawLiveSlotId) {
        if (rawLiveSlotId >= 0) {
            return rawLiveSlotId;
        }
        if (sidekick.getInteractSlotIndex() < 0) {
            // Never rode anything: ROM writes id(slot 0) = ObjID_Sonic 0x01.
            return ROM_DEFAULT_INTERACT_OBJECT_ID;
        }
        // Ridden-then-emptied: ROM writes the zeroed slot id.
        return ROM_DELETED_INTERACT_SLOT_ID;
    }

    /**
     * ROM {@code TailsCPU_UpdateObjInteract}: {@code Tails_interact_ID = id(slot)}.
     * Fed {@link #snapshotSeedInteractSlotId(int)} (NOT the raw read): the
     * never-ridden case seeds 0x01, an occupied slot stores the real id, and a
     * ridden-then-emptied slot stores ROM's zeroed slot id.
     */
    private void refreshInteractIdSnapshot(int snapshotSeedId) {
        lastInteractObjectId = snapshotSeedId;
        if (usesS3kPointerInteract() && sidekick.isOnObject()) {
            Integer interactWord = currentS3kInteractWord();
            if (interactWord != null) {
                diagnosticS3kInteractWord = interactWord;
            }
        }
    }

    public void refreshS3kInteractLatchFromCurrentRide() {
        if (!usesS3kPointerInteract()) {
            return;
        }
        Integer interactWord = currentS3kInteractWord();
        if (interactWord != null) {
            diagnosticS3kInteractWord = interactWord;
        }
    }

    private boolean isLatchedRideSlotFreed(ObjectInstance instance) {
        if (instance.isDestroyed()) {
            return true;
        }
        LevelManager levelManager = sidekick.currentLevelManagerIfAvailable();
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return false;
        }
        return !levelManager.getObjectManager().getActiveObjects().contains(instance);
    }

    private boolean isCurrentlyVisible() {
        Camera camera = sidekick.currentCamera();
        return camera != null && camera.isOnScreen(sidekick);
    }

    /**
     * Legacy entry point. Existing callers default to
     * EXPLICIT (immediate marker warp).
     */
    public void despawn() {
        despawn(DespawnCause.EXPLICIT);
    }

    /**
     * Trigger a sidekick despawn with explicit cause. LEVEL_BOUNDARY
     * mirrors ROM Kill_Character (sonic3k.asm:21136): Frame N zeroes
     * velocities and enters DEAD_FALLING; Frame N+1 (updateDeadFalling)
     * runs sub_123C2 -> sub_13ECA equivalent (warp + +$38 gravity).
     * Other causes go straight to applyDespawnMarker.
     */
    public void despawn(DespawnCause cause) {
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        triggerDespawn(cause);
    }

    private void triggerDespawn(DespawnCause cause) {
        if (cause == DespawnCause.LEVEL_BOUNDARY) {
            // ROM Kill_Character writes routine = 6 (Obj02_Dead), so subsequent
            // Tails_LevelBound calls from MdAir/MdJump no longer dispatch to
            // the airborne path; the dispatcher routes routine 6 to Obj02_Dead
            // (s2.asm:38652-38656). ROM Obj02_Dead does NOT call
            // Tails_LevelBound -- only Obj02_CheckGameOver's kill-plane test
            // (s2.asm:40736-40759). For S3K, sub_13ECA writes object_control
            // bit 7 in the same frame (sonic3k.asm:26804-26807), which
            // short-circuits all subsequent boundary checks via
            // isObjectControlSuppressesMovement.
            //
            // The engine routes both games' bottom-kill through
            // PlayableSpriteMovement.doLevelBoundary, which re-fires
            // despawn(LEVEL_BOUNDARY) every frame while tails_y exceeds the
            // kill plane. For S2's deferred-despawn flow (the kill plane stays
            // above Tails until he falls past Tails_Max_Y_pos + $100) this
            // would clobber the per-frame ObjectMoveAndFall gravity step with
            // a fresh y_vel = -$700 every tick. Mirror ROM by treating the
            // initial DEAD_FALLING entry as terminal: once the kill has
            // begun, additional LEVEL_BOUNDARY triggers are no-ops until the
            // dispatcher returns to a non-dead routine.
            if (state == State.DEAD_FALLING) {
                return;
            }
            beginLevelBoundaryKill();
            return;
        }
        applyDespawnMarker();
    }

    /**
     * ROM Kill_Character (sonic3k.asm:21136-21159) entry reached from
     * Tails_Check_Screen_Boundaries (sonic3k.asm:28442-28443
     * `loc_14F56: jmp (Kill_Character).l`) when the sidekick crosses the
     * bottom kill plane. ROM Kill_Character at sonic3k.asm:21148-21151
     * writes:
     *
     * <pre>
     *     bset    #Status_InAir,status(a0)
     *     move.w  #-$700,y_vel(a0)
     *     move.w  #0,x_vel(a0)
     *     move.w  #0,ground_vel(a0)
     * </pre>
     *
     * y_vel is set to {@code -$700}, NOT zero. Because Kill_Character was
     * reached via {@code jmp} (not {@code jsr}), the {@code rts} at
     * sonic3k.asm:21159 unwinds to Kill_Character's caller's caller — for
     * Tails the relevant chain is Tails_Stand_Path
     * (sonic3k.asm:27520-27526), so control falls through to
     * {@code jsr (MoveSprite_TestGravity2).l} on line 27526.
     * MoveSprite_TestGravity2 with Reverse_gravity_flag clear is just
     * MoveSprite2 (sonic3k.asm:36088-36101) which applies the freshly
     * written {@code y_vel = -$700} to {@code y_pos}, shifting Tails up by
     * 7 pixels in the same frame. Trace AIZ F7171 records the post-shift
     * state: {@code y_pos = $0477} (down 7 from $047E) with
     * {@code y_vel = -$700} retained. Engine therefore preserves the
     * negative y-velocity so the airborne movement manager's
     * SpeedToPos-equivalent ({@code modeNormal} → {@code sprite.move})
     * applies the same 7-pixel shift inside the kill frame.
     *
     * Position is intentionally NOT warped this frame; ROM keeps Tails at
     * post-MoveSprite2 position for one frame, then sub_13ECA writes the
     * marker on Frame N+1 (see updateDeadFalling).
     */
    private void beginLevelBoundaryKill() {
        state = State.DEAD_FALLING;
        normalFrameCount = 0;
        applyKillCharacterTouchFloorReset();
        sidekick.setXSpeed((short) 0);
        // ROM Kill_Character (sonic3k.asm:21149) writes y_vel=-$700.
        sidekick.setYSpeed((short) -0x700);
        sidekick.setGSpeed((short) 0);
        sidekick.setHurt(false);
        sidekick.setRollingJump(false);
        sidekick.setOnObject(false);
        sidekick.setPushing(false);
        sidekick.setLatchedSolidObjectId(0);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setAir(true);
        sidekick.setMoveLockTimer(0);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        // NOT object_controlled - DEAD_FALLING is its own dispatch state
        // so updateDeadFalling fires on the next tick regardless.
        lastInteractObjectId = -1; // ROM Tails_interact_ID unset until next UpdateObjInteract
        // S3K Kill_Character does not write Tails_CPU_interact; preserve the
        // ROM-visible pointer word until sub_13EFC samples another stood-on
        // object or active play RAM is explicitly reset.
    }

    private void applyKillCharacterTouchFloorReset() {
        int centreX = sidekick.getCentreX();
        int centreY = sidekick.getCentreY();
        if (sidekick.getRolling()) {
            // ROM Kill_Character calls Player_TouchFloor before setting death
            // velocities (sonic3k.asm:21142-21151). For Tails this restores
            // default radii, clears Status_Roll, and adds the current y_radius
            // delta to y_pos (sonic3k.asm:29133-29156).
            //
            // ROM Tails_TouchFloor (sonic3k.asm:29133-29156):
            //   move.b y_radius(a0),d0          ; d0 = OLD y_radius
            //   move.b default_y_radius(a0),y_radius(a0)
            //   ...
            //   sub.b default_y_radius(a0),d0   ; d0 = old_y_radius - default_y_radius
            //   ext.w d0
            //   ...
            //   add.w d0,y_pos(a0)              ; y_pos += d0 (sign-flipped by angle)
            //
            // The delta is the radius difference, NOT half the height difference.
            // Reading sidekick.getHeight() (full height = 2 * y_radius) instead
            // of getYRadius() previously returned ~13 px on Tails roll->stand,
            // shifting end-of-frame y by +13 — see AIZ F4679 (16 px gap).
            int delta = sidekick.getYRadius() - sidekick.getStandYRadius();
            if ((((sidekick.getAngle() & 0xFF) + 0x40) & 0x80) != 0) {
                delta = -delta;
            }
            sidekick.setRolling(false);
            sidekick.setCentreXPreserveSubpixel((short) centreX);
            sidekick.setCentreYPreserveSubpixel((short) (centreY + delta));
        } else if (sidekick.getYRadius() != sidekick.getStandYRadius()
                || sidekick.getXRadius() != sidekick.getStandXRadius()) {
            sidekick.restoreDefaultRadii();
        }
        sidekick.setAir(false);
        sidekick.setPushing(false);
        sidekick.setRollingJump(false);
        sidekick.setJumping(false);
        sidekick.setDoubleJumpFlag(0);
    }

    /**
     * Death-routine equivalent of ROM loc_1578E -> loc_157C8 -> sub_123C2.
     * Runs after beginLevelBoundaryKill.  sub_123C2 first checks whether Tails
     * has fallen below the marker threshold; while he is still above it, the
     * routine returns to loc_157C8 and only MoveSprite_TestGravity runs
     * (sonic3k.asm:24538-24578,29284-29285).  Once the threshold is crossed,
     * sub_123C2 writes Tails_CPU_routine=2 and branches to sub_13ECA
     * (sonic3k.asm:26800-26809), which warps x_pos=0x7F00, y_pos=0 and sets
     * object_control=$81/Status_InAir.  Control then unwinds via the bsr at
     * sonic3k.asm:29284 back to loc_157C8, where MoveSprite applies the still-
     * preserved y_vel before the +$38 gravity write
     * (sonic3k.asm:36032-36042).
     * Trace AIZ F7172 records exactly that: {@code y = -0x0007},
     * {@code y_vel = -0x06C8}.
     *
     * <p>{@link #applyDespawnMarker()} flips
     * {@link AbstractPlayableSprite#setObjectControlled(boolean)} to true,
     * which enables {@code objectControlSuppressesMovement} and short-circuits
     * the regular {@link com.openggf.sprites.managers.PlayableSpriteMovement}
     * path entirely.  The post-warp MoveSprite step is therefore inlined here
     * to mirror the ROM call chain.  We capture {@code y_vel} before the warp
     * because {@link #applyDespawnMarker()} preserves velocity (sub_13ECA does
     * not touch x_vel/y_vel/ground_vel) but we still want to be explicit about
     * the order of operations matching {@code MoveSprite}.
     */
    private void updateDeadFalling() {
        if (resolveSidekickDeathUsesDeferredDespawn()) {
            updateDeadFallingDeferredS2();
            return;
        }
        // ROM MoveSprite (sonic3k.asm:36037-36041) uses the OLD y_vel for
        // position before adding gravity; sub_13ECA does not touch y_vel so
        // the value entering MoveSprite is the Kill_Character write of -$700.
        short oldYSpeed = sidekick.getYSpeed();
        applyDespawnMarker();
        // sub_13ECA wrote y_pos=0; now apply MoveSprite's position step
        // using the pre-gravity y_vel.
        int newCentreY = (sidekick.getCentreY() & 0xFFFF) + (oldYSpeed >> 8);
        sidekick.setCentreYPreserveSubpixel((short) newCentreY);
        // MoveSprite then adds +$38 (sonic3k.asm:36038) to y_vel.
        sidekick.setYSpeed((short) (oldYSpeed + 0x38));
    }

    /**
     * Per-frame death-routine equivalent for games whose dead sidekick waits
     * before the off-screen marker. S2 uses Tails_Max_Y_pos+$100
     * (docs/s2disasm/s2.asm:40736-40759); S3K uses Camera_Y_pos+$100 in
     * sub_123C2 (sonic3k.asm:24538-24578) before branching to sub_13ECA.
     * Each frame ROM runs:
     *
     * <pre>
     *   Obj02_Dead:
     *     bsr.w   Obj02_CheckGameOver
     *     jsr     (ObjectMoveAndFall).l
     *     ...
     *
     *   Obj02_CheckGameOver:
     *     move.w  (Tails_Max_Y_pos).w,d0
     *     addi.w  #$100,d0
     *     cmp.w   y_pos(a0),d0
     *     bge.w   return_1CD8E           ; not yet past kill plane
     *     move.b  #2,routine(a0)
     *     bra.w   TailsCPU_Despawn       ; warp to $4000, $0000
     * </pre>
     *
     * The {@code bge.w return_1CD8E} branch returns BEFORE
     * {@code ObjectMoveAndFall} when Tails is above the threshold and the
     * threshold has already been crossed (i.e. {@code routine=2} for the
     * despawn re-spawn flow). Until that branch fires, every Frame N+k
     * applies {@code ObjectMoveAndFall} (s2.asm:29967-29981): position
     * gets the OLD {@code y_vel}, then {@code y_vel += $38} gravity. The
     * engine reproduces this without writing the off-screen marker so
     * trace baselines see Tails fall naturally from his death position.
     *
     * <p>The threshold check on the FIRST deferred frame uses the
     * post-{@code MoveSprite2} {@code y_pos} from
     * {@link #beginLevelBoundaryKill()} (ROM Kill_Character's caller
     * already ran MoveSprite2 on the kill frame), which mirrors ROM
     * {@code Obj02_CheckGameOver} reading the same {@code y_pos} value
     * via the {@code Obj02_Dead} entry on the next tick.
     */
    private void updateDeadFallingDeferredS2() {
        // ROM Obj02_CheckGameOver reads y_pos BEFORE ObjectMoveAndFall on
        // each frame (s2.asm:40747-40759). Use the current centre-Y, which
        // matches y_pos for a CPU sidekick.
        int currentY = sidekick.getCentreY();
        int markerThreshold = resolveDeadFallMarkerThresholdY();
        if (markerThreshold != Integer.MIN_VALUE && currentY > markerThreshold) {
            // Crossed below Tails_Max_Y_pos + $100: ROM sets routine=2 and
            // branches to TailsCPU_Despawn (s2.asm:40756-40759, 39043-39052)
            // which writes x_pos=$4000, y_pos=0 via `move.w` (word-sized)
            // stores that preserve the low 16-bit y_sub/x_sub fields. ROM
            // control then unwinds via `rts` back to Obj02_Dead, which
            // continues with `jsr (ObjectMoveAndFall).l` (s2.asm:40738).
            // ObjectMoveAndFall (s2.asm:29967-29981) does the 16:16
            // fixed-point update:
            //     move.l y_pos(a0),d3        ; load full y_pos:y_sub long
            //     move.w y_vel(a0),d0
            //     ext.l  d0 / asl.l #8,d0    ; d0 = y_vel sign-extended << 8
            //     addi.w #$38,y_vel(a0)      ; gravity (memory only; d0 keeps pre-grav)
            //     add.l  d0,d3               ; y_pos:y_sub += y_vel<<8, with carry
            //     move.l d3,y_pos(a0)        ; store full long
            // The y_sub overflow CARRIES into y_pos. Using integer math
            // `y_pos += (y_vel >> 8)` drops that carry, which under-counts
            // y_pos by 1 when (y_sub_preserved + (y_vel & 0xFF00)) overflows
            // (HTZ F538: y_sub 0x2C00 + 0xE000 = 0x10C00, carry of 1 takes
            // y_pos from 7 to ROM's 8). AbstractSprite.move performs the
            // same 32-bit add as ROM, so call it instead of doing manual
            // pixel arithmetic.
            short oldXSpeed = sidekick.getXSpeed();
            short oldYSpeed = sidekick.getYSpeed();
            applyDespawnMarker();
            // applyDespawnMarker has called setCentreXPreserveSubpixel and
            // setCentreYPreserveSubpixel((short) 0), matching ROM's two
            // word writes; x_sub and y_sub are intact. It also sets
            // objectControlled=true so PlayableSpriteMovement short-circuits
            // the rest of this frame — we must therefore inline the
            // post-warp ObjectMoveAndFall here.
            sidekick.move(oldXSpeed, oldYSpeed);
            sidekick.setYSpeed((short) (oldYSpeed + 0x38));
            return;
        }
        // Threshold not yet crossed: ROM Obj02_Dead's per-frame
        // ObjectMoveAndFall is reproduced by PlayableSpriteMovement.modeAirborne's
        // isCpuLevelBoundaryKillActive branch (it calls doObjectMoveAndFall
        // exactly once per dead-falling frame, matching ROM s2.asm:40738).
        // Do NOT apply gravity here — that would compound with the airborne
        // path's gravity step and double-count y_vel and y_pos per frame.
        //
        // Mark this frame as a deferred-fall continuation so
        // PlayableSpriteMovement skips its post-kill Tails_DoLevelCollision
        // pass: ROM Obj02_Dead (s2.asm:40736-40742) calls ObjectMoveAndFall
        // but NOT Tails_DoLevelCollision. Without this gate, MCZ Tails
        // (kill triggered above CollapsingPlatform s17) lands on the
        // platform at trace F443 because the engine's normal post-kill
        // collision pass — correct for ROM Obj02_MdAir's KILL-FRAME
        // continuation — keeps firing on every deferred-fall frame.
        deferredDespawnDeadFallContinuingThisFrame = true;
    }

    private int resolveDeadFallMarkerThresholdY() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean s3kCatchUpMarker = fs != null && fs.sidekickRespawnEntersCatchUpFlight();
        if (s3kCatchUpMarker) {
            Camera camera = sidekick.currentCamera();
            if (camera != null) {
                // S3K sub_123C2 reads Camera_Y_pos, then adds $100 before
                // comparing against y_pos(a0) (sonic3k.asm:24549-24565).
                return (camera.getY() & 0xFFFF) + 0x100;
            }
        }
        int killPlane = getMaxYBound(Integer.MIN_VALUE);
        if (killPlane == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return killPlane + 0x100;
    }

    /**
     * ROM sub_13ECA (sonic3k.asm:26800-26809) marker warp body. Writes
     * despawn marker x/y, sets Tails_CPU_routine=2, and leaves the next
     * S3K CPU tick in Tails_Catch_Up_Flying. S2 keeps the older SPAWNING
     * flow because its TailsCPU_Respawn path owns the approach sequence.
     */
    private void applyDespawnMarker() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean s3kCatchUpMarker = fs != null && fs.sidekickRespawnEntersCatchUpFlight();
        state = s3kCatchUpMarker
                ? State.CATCH_UP_FLIGHT
                : State.SPAWNING;
        despawnCounter = 0;
        controlCounter = 0;
        if (s3kCatchUpMarker) {
            flightTimer = 0;
            sidekick.setDoubleJumpFlag(0);
            // sub_13ECA changes Tails_CPU_routine during Tails' current
            // Process_Sprites slot without clearing velocity (sonic3k.asm:
            // 26800-26809). The later routine-2 wait reads Level_frame_counter
            // directly and masks the low 6 bits before loc_13B50 snaps Tails
            // to Sonic and clears x/y/ground velocity (sonic3k.asm:
            // 26478-26511). Some level-event/reload marker paths expose that
            // ROM-visible counter one tick ahead of the stored engine counter;
            // use the provider-owned marker predicate so ordinary sub_13ECA
            // marker cadence is not shifted.
            catchUpUsesRomVisibleLevelFrameCounter =
                    usesSidekickRomVisibleCatchUpMarkerFrameCounterBridge();
        }
        normalFrameCount = 0;
        sidekick.setHurt(false);
        // ROM sub_13ECA writes status=Status_InAir directly
        // (sonic3k.asm:26804-26808). It clears Status_Roll and
        // Status_Underwater, but does not restore x_radius/y_radius or water
        // speed constants, so preserve those separate ROM fields.
        sidekick.clearRollingFlagPreserveRadii();
        sidekick.clearUnderwaterStatusPreserveWaterPhysics();
        sidekick.setRollingJump(false);
        sidekick.setOnObject(false);
        sidekick.setPushing(false);
        sidekick.setLatchedSolidObjectId(0);
        sidekick.setDirection(Direction.RIGHT);
        sidekick.setAir(true);
        sidekick.setCentreXPreserveSubpixel(resolveDespawnX());
        sidekick.setCentreYPreserveSubpixel((short) 0);
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        ObjectControlState.nativeBit7FullControl().applyTo(sidekick);
        // ROM sub_13ECA (sonic3k.asm:26800-26809) only writes x_pos,
        // y_pos, Tails_CPU_routine, object_control, status, and
        // double_jump_flag - it does NOT touch x_vel/y_vel/ground_vel.
        // Trace AIZ F2405 confirms this: ROM applies the marker warp
        // mid-trajectory and the recorded sidekick_x_speed/y_speed/g_speed
        // at F2405 retain the pre-warp values (0xFE07, 0x022D, 0xFD0D).
        // Don't zero velocities here. The LEVEL_BOUNDARY kill chain
        // (beginLevelBoundaryKill) does its own zeroing earlier in the
        // Kill_Character (sonic3k.asm:21148-21151) phase, which runs
        // before this marker warp on Frame N+1.
        lastInteractObjectId = -1; // ROM Tails_interact_ID unset until next UpdateObjInteract
        // S3K sub_13ECA does not write Tails_CPU_interact; preserve the
        // ROM-visible pointer word until sub_13EFC samples another stood-on
        // object or active play RAM is explicitly reset.
    }

    /**
     * Level-event owned marker release used when ROM writes
     * {@code Tails_CPU_routine=2} while leaving the marker position and
     * object-control byte intact until routine 2 performs its own catch-up warp.
     */
    public boolean releaseDormantMarkerForLevelEvent() {
        if (state != State.DORMANT_MARKER) {
            return false;
        }
        LevelManager levelManager = sidekick.currentLevelManager();
        if (levelManager != null) {
            // ROM LevelLoop increments Level_frame_counter before Process_Sprites
            // (sonic3k.asm:7888-7894). Engine zone pre-physics runs before the
            // stored LevelManager counter advances, so expose the ROM-visible
            // cadence to the first routine-2 tick without changing S3K's normal
            // stored-counter rule.
            catchUpFrameCounterOverride = levelManager.getFrameCounter() + 1;
        }
        state = State.CATCH_UP_FLIGHT;
        despawnCounter = 0;
        controlCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        suppressNextLevelEventNormalMovement = true;
        catchUpUsesRomVisibleLevelFrameCounter = true;
        levelEventDormantMarkerReleasePending = true;
        sidekick.setAir(true);
        sidekick.setControlLocked(true);
        ObjectControlState.nativeBit7FullControl().applyTo(sidekick);
        sidekick.setForcedAnimationId(flyAnimId);
        return true;
    }

    private void clearInputs() {
        inputUp = false;
        inputDown = false;
        inputLeft = false;
        inputRight = false;
        inputJump = false;
        inputJumpPress = false;
        diagnosticGeneratedPressedInput = 0;
        objectOrderGracePushBypassThisFrame = false;
    }

    public void setRespawnStrategy(SidekickRespawnStrategy strategy) {
        this.respawnStrategy = strategy;
    }

    public SidekickRespawnStrategy getRespawnStrategy() {
        return respawnStrategy;
    }

    public int consumePendingGroundedFollowNudge(int maxAgeFrames) {
        if (pendingGroundedFollowNudgeFrame < 0
                || frameCounter - pendingGroundedFollowNudgeFrame > maxAgeFrames) {
            pendingGroundedFollowNudge = 0;
            pendingGroundedFollowNudgeFrame = -1;
            return 0;
        }
        int nudge = pendingGroundedFollowNudge;
        pendingGroundedFollowNudge = 0;
        pendingGroundedFollowNudgeFrame = -1;
        return nudge;
    }

    public void setLeader(AbstractPlayableSprite leader) {
        this.leader = leader;
    }

    public AbstractPlayableSprite getLeader() {
        return leader;
    }

    public void setSidekickCount(int sidekickCount) {
        this.sidekickCount = sidekickCount;
    }

    /**
     * Returns true when this sidekick has been in NORMAL state for at least
     * {@link #SETTLED_FRAME_THRESHOLD} consecutive frames, meaning it has
     * "caught up" to its position in the chain.
     */
    public boolean isSettled() {
        return state == State.NORMAL && normalFrameCount >= SETTLED_FRAME_THRESHOLD;
    }

    /**
     * Walks up the leader chain to find the nearest settled leader (or the main
     * player). If the direct leader is not CPU-controlled or is settled, it is
     * returned immediately. Otherwise the chain is walked until a settled
     * sidekick or the main player is found.
     */
    public AbstractPlayableSprite getEffectiveLeader() {
        AbstractPlayableSprite current = leader;
        int maxSteps = sidekickCount;
        while (current != null && current.isCpuControlled() && maxSteps-- > 0) {
            SidekickCpuController ctrl = current.getCpuController();
            if (ctrl == null) {
                return current;
            }
            if (ctrl.isSettled()) {
                return current;
            }
            current = ctrl.getLeader();
        }
        return current;
    }

    /**
     * Sets the initial state for production use (e.g. pre-setting SPAWNING
     * after a level transition).
     */
    public void setInitialState(State state) {
        this.state = state;
        aizIntroDormantMarkerPrimed = false;
        suppressNextLevelEventNormalMovement = false;
        catchUpUsesRomVisibleLevelFrameCounter = false;
        levelEventDormantMarkerReleasePending = false;
        skipPhysicsThisFrame = false;
        lastNormalAutoJumpPressFrameCounter = -1;
        normalFrameCount = state == State.NORMAL ? SETTLED_FRAME_THRESHOLD : 0;
        normalPushingGraceFrames = 0;
        suppressNextAirbornePushFollowSteering = false;
        objectOrderGracePushBypassThisFrame = false;
        if (state != State.CARRYING && state != State.CARRY_INIT) {
            mgzCarryIntroAscend = false;
            mgzCarryFlapTimer = 0;
            mgzReleasedChaseLatched = false;
        }
    }

    /**
     * Seeds ROM Tails CPU globals for focused routine tests. Production replay
     * must reach these values by executing the native startup/object paths, not
     * by copying comparison snapshots into engine state.
     */
    void hydrateFromRomCpuState(int cpuRoutine, int controlCounter,
                                int respawnCounter, int interactId,
                                boolean jumping, int targetX, int targetY) {
        state = mapRomCpuRoutine(cpuRoutine);
        aizIntroDormantMarkerPrimed = false;
        suppressNextLevelEventNormalMovement = false;
        catchUpUsesRomVisibleLevelFrameCounter = false;
        levelEventDormantMarkerReleasePending = false;
        skipPhysicsThisFrame = false;
        lastNormalAutoJumpPressFrameCounter = -1;
        this.controlCounter = Math.max(0, controlCounter);
        this.despawnCounter = Math.max(0, respawnCounter);
        this.lastInteractObjectId = interactId & 0xFF;
        this.diagnosticS3kInteractWord = usesS3kPointerInteract() ? interactId & 0xFFFF : 0;
        sidekick.setLatchedSolidObjectId(interactId);
        this.jumpingFlag = jumping;
        this.normalFrameCount = state == State.NORMAL ? SETTLED_FRAME_THRESHOLD : 0;
        normalPushingGraceFrames = 0;
        suppressNextAirbornePushFollowSteering = false;
        objectOrderGracePushBypassThisFrame = false;
        // ROM Tails_CPU_target_X / Tails_CPU_target_Y (sonic3k.asm $F70A/$F70C).
        // The engine stores them in the existing catch-up steering fields,
        // which mirror that ROM word pair.
        this.catchUpTargetX = targetX & 0xFFFF;
        this.catchUpTargetY = targetY & 0xFFFF;
        clearInputs();
    }

    /**
     * Package-private test helper: sets both state and normalFrameCount directly.
     */
    void forceStateForTest(State state, int normalFrames) {
        this.state = state;
        aizIntroDormantMarkerPrimed = false;
        suppressNextLevelEventNormalMovement = false;
        catchUpUsesRomVisibleLevelFrameCounter = false;
        levelEventDormantMarkerReleasePending = false;
        skipPhysicsThisFrame = false;
        lastNormalAutoJumpPressFrameCounter = -1;
        this.normalFrameCount = normalFrames;
        suppressNextAirbornePushFollowSteering = false;
        objectOrderGracePushBypassThisFrame = false;
    }

    /**
     * ROM {@code Tails_CPU_Control_Index} (sonic3k.asm:26368-26386) is an 18-entry
     * word table indexed by {@code Tails_CPU_routine}, which the dispatcher reads at
     * sonic3k.asm:26362-26364. Each entry value is the CPU routine byte (0x00, 0x02,
     * 0x04, ...) — the table stride is 2 bytes, so the value equals the offset.
     *
     * <pre>
     *   0x00  loc_13A10               engine State.INIT  (zone-specific init, carry gate)
     *   0x02  Tails_Catch_Up_Flying   engine State.CATCH_UP_FLIGHT  (teleport-to-Sonic gate, sonic3k.asm:26474)
     *   0x04  Tails_FlySwim_Unknown   engine State.FLIGHT_AUTO_RECOVERY (fly-toward-Sonic + 5s timer, sonic3k.asm:26534)
     *   0x06  loc_13D4A               engine State.NORMAL (ground follow AI, sonic3k.asm:26656)
     *   0x08  loc_13F40               engine State.PANIC  (idle/standing ground, sonic3k.asm:26851)
     *   0x0A  locret_13FC0            engine State.DORMANT_MARKER (empty; used by AIZ1 intro marker)
     *   0x0C  loc_13FC2               engine State.CARRY_INIT (carry body init)
     *   0x0E  loc_13FFA               engine State.CARRYING  (carry body per-frame)
     *   0x10  loc_1408A               engine State.CARRY_FLYOFF (solo-leader carrier fly-off + self-delete)
     *   0x12  Obj_MGZ2_BossTransition engine State.MGZ_RESCUE_WAIT
     *   0x14-0x22  super/Knuckles/2P variants — not modelled
     * </pre>
     *
     * <p>Note: earlier versions of this file mapped 0x02 and 0x04 to SPAWNING and
     * APPROACHING respectively. Those engine states are behavioural inventions
     * (despawn-respawn flow, approach strategy) that the ROM doesn't have a
     * matching routine for; hydrating them from a recorded CPU routine byte was
     * never semantically correct. Prefer to leave hydration undefined for
     * engine-only states until there's a concrete trace that exercises them.
     */
    private static State mapRomCpuRoutine(int cpuRoutine) {
        return switch (cpuRoutine) {
            case 0x00 -> State.INIT;
            case 0x02 -> State.CATCH_UP_FLIGHT;
            case 0x04 -> State.FLIGHT_AUTO_RECOVERY;
            case 0x06 -> State.NORMAL;
            case 0x08 -> State.PANIC;
            case 0x0A -> State.DORMANT_MARKER;
            case 0x12 -> State.MGZ_RESCUE_WAIT;
            case 0x0C -> State.CARRY_INIT;
            case 0x0E, 0x20 -> State.CARRYING;
            case 0x10 -> State.CARRY_FLYOFF;
            default -> throw new IllegalArgumentException(
                    "Unsupported ROM Tails CPU routine: 0x"
                            + Integer.toHexString(cpuRoutine));
        };
    }

    private int romCpuRoutineForState(State state) {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean usesS3kCatchUpRoutines = fs != null && fs.sidekickRespawnEntersCatchUpFlight();
        return switch (state) {
            case INIT -> 0x00;
            case SPAWNING -> usesS3kCatchUpRoutines ? -1 : 0x02;
            case APPROACHING -> usesS3kCatchUpRoutines ? -1 : 0x04;
            case CATCH_UP_FLIGHT -> 0x02;
            case FLIGHT_AUTO_RECOVERY -> 0x04;
            case DEAD_FALLING -> 0x06;
            case NORMAL -> 0x06;
            case PANIC -> 0x08;
            case DORMANT_MARKER -> 0x0A;
            case CARRY_INIT -> 0x0C;
            case CARRYING -> 0x0E;
            case CARRY_FLYOFF -> 0x10;
            case MGZ_RESCUE_WAIT -> 0x12;
            default -> -1;
        };
    }

    public boolean getInputUp() { return inputUp; }
    public boolean getInputDown() { return inputDown; }
    public boolean getInputLeft() { return inputLeft; }
    public boolean getInputRight() { return inputRight; }
    public boolean getInputJumpPress() { return inputJumpPress; }

    /** Package-private: allows respawn strategies to set directional input toward the leader. */
    void setApproachInput(boolean left, boolean right) {
        this.inputLeft = left;
        this.inputRight = right;
    }
    public boolean getInputJump() { return inputJump; }
    public State getState() { return state; }
    public boolean isApproaching() { return state == State.APPROACHING; }

    public int getMinXBound(int fallback) {
        return minXBound == Integer.MIN_VALUE ? fallback : minXBound;
    }

    public int getMaxXBound(int fallback) {
        return maxXBound == Integer.MIN_VALUE ? fallback : maxXBound;
    }

    public int getMinYBound(int fallback) {
        return minYBound == Integer.MIN_VALUE ? fallback : minYBound;
    }

    public int getMaxYBound(int fallback) {
        return maxYBound == Integer.MIN_VALUE ? fallback : maxYBound;
    }

    /**
     * Captures the leader's current centre coordinates as the level-start spawn
     * anchor. Invoked from {@code LevelManager.spawnSidekicks} (the engine
     * analogue of ROM {@code SpawnLevelMainSprites_SpawnPlayers},
     * sonic3k.asm:8359-8369), while the leader is still at its spawn position
     * before any LevelLoop physics tick. {@link #applyLevelStartSidekickPlacement}
     * then anchors the deferred sidekick placement and Pos_table prefill to
     * these coordinates instead of the live (possibly already-moved) leader
     * centre. See {@link #levelStartLeaderCentreX}.
     */
    public void captureLevelStartLeaderAnchor(int leaderCentreX, int leaderCentreY) {
        this.levelStartLeaderCentreX = leaderCentreX;
        this.levelStartLeaderCentreY = leaderCentreY;
    }

    /**
     * Marks that this sidekick entered via a one-time directly-compared frame-0
     * seed that loaded residual mid-run follow state (see
     * {@link #enteredFromSeedCompareFrame0}). Called by the trace-replay
     * bootstrap orchestration when an S3K complete-run segment's frame 0 is
     * seed-compared.
     */
    public void setEnteredFromSeedCompareFrame0(boolean value) {
        this.enteredFromSeedCompareFrame0 = value;
        if (value && levelStartLeaderCentreX != Integer.MIN_VALUE && leader != null) {
            // Prefill the leader Pos_table ring with the captured spawn anchor at
            // bootstrap time (before any frame is driven), mirroring ROM filling
            // Sonic_Pos_Record_Buf with the spawn position at level-load
            // (SpawnLevelMainSprites / Reset_Player_Position_Array,
            // sonic3k.asm:8359-8369,22166-22193). For a seed-compared mid-run
            // entry the leader is not driven through frame 0, so the first live
            // Sonic_RecordPos write happens on trace frame 1 and lands on the next
            // ring slot on top of this spawn fill — the delayed-follow target then
            // stays at the spawn-anchored position until the ring rotates to the
            // leader's real positions ~16 frames later, reproducing ROM's
            // "Tails held still for 16 frames" entry. Doing this here (not in the
            // controller's first tick, which runs after the leader's frame-1
            // record) avoids clobbering that already-written record.
            leader.prefillPositionHistoryWithCentre(
                    (short) levelStartLeaderCentreX,
                    (short) levelStartLeaderCentreY);
        }
    }

    public void setLevelBounds(Integer minX, Integer maxX, Integer maxY) {
        setLevelBounds(minX, maxX, null, maxY);
    }

    public void setLevelBounds(Integer minX, Integer maxX, Integer minY, Integer maxY) {
        if (minX != null) {
            minXBound = minX;
        }
        if (maxX != null) {
            maxXBound = maxX;
        }
        if (minY != null) {
            minYBound = minY;
        }
        if (maxY != null) {
            maxYBound = maxY;
        }
    }

    /**
     * Installs the game-specific carry trigger. Null (default) disables the
     * carry state machine; S1/S2 game modules pass null and the driver behaves
     * as before.
     */
    public void setCarryTrigger(SidekickCarryTrigger trigger) {
        this.carryTrigger = trigger;
        mgzReleasedChaseLatched = false;
    }

    /**
     * Marks this controller as driving a throwaway intro carrier (ROM
     * SpawnLevelMainSprites loc_68D8 Player_2 spawn for a solo leader). When set,
     * the carry release routes to {@link State#CARRY_FLYOFF} and the sprite is
     * removed once it flies off-screen, rather than entering the normal follow AI.
     */
    public void setTransientCarrySidekick(boolean value) {
        this.transientCarrySidekick = value;
    }

    public boolean isTransientCarrySidekick() {
        return transientCarrySidekick;
    }

    /** True once a CARRY_FLYOFF carrier has left the screen and been removed. */
    public boolean isTransientFlyoffDespawned() {
        return transientFlyoffDespawned;
    }

    /**
     * True while Tails is actively carrying Sonic in flight (ROM
     * Flying_carrying_Sonic_flag). Used by PlayableSpriteMovement.applyGravity
     * to substitute Tails's flight gravity (+0x08/frame, Tails_Move_FlySwim
     * loc_1488C in sonic3k.asm:27633) for the standard +0x38 air gravity.
     */
    public boolean isFlyingCarrying() {
        return flyingCarryingFlag;
    }

    public boolean usesFlyingCarryMovement() {
        if (sidekick.isHurt() || sidekick.getDead()) {
            return false;
        }
        return flyingCarryingFlag
                // ROM routine $10 (loc_1408A) keeps the throwaway carrier in
                // Tails_FlyingSwimming: the A/B/C flaps injected by
                // updateCarryFlyoff drive the same Tails_Move_FlySwim ascent
                // (applyFlyingCarryVerticalVelocity) as the carry, so it flies up
                // instead of sinking under plain +0x08 flight gravity.
                || state == State.CARRY_FLYOFF
                || (state == State.CARRYING
                && carryTrigger != null
                && carryTrigger.usesMgzBossTransitionControl());
    }

    public SidekickCpuRewindExtra captureRewindState() {
        return new SidekickCpuRewindExtra(
                state,
                despawnCounter,
                frameCounter,
                controlCounter,
                controller2Held,
                controller2Logical,
                inputUp,
                inputDown,
                inputLeft,
                inputRight,
                inputJump,
                inputJumpPress,
                jumpingFlag,
                minXBound,
                maxXBound,
                minYBound,
                maxYBound,
                lastInteractObjectId,
                diagnosticS3kInteractWord,
                normalFrameCount,
                sidekickCount,
                normalPushingGraceFrames,
                suppressNextAirbornePushFollowSteering,
                objectOrderGracePushBypassThisFrame,
                pendingGroundedFollowNudge,
                pendingGroundedFollowNudgeFrame,
                aizIntroDormantMarkerPrimed,
                suppressNextLevelEventNormalMovement,
                catchUpUsesRomVisibleLevelFrameCounter,
                levelEventDormantMarkerReleasePending,
                skipPhysicsThisFrame,
                deferredDespawnDeadFallContinuingThisFrame,
                bootstrapPreludePlacementApplied,
                cpuFrameCounterFromStoredLevelFrame,
                nextCpuFrameCounterOverride,
                catchUpFrameCounterOverride,
                lastNormalAutoJumpPressFrameCounter,
                controller2SignedLocked,
                latestNormalStepDiagnostics,
                carryLatchX,
                carryLatchY,
                flyingCarryingFlag,
                carryParentagePending,
                releaseCooldown,
                mgzCarryIntroAscend,
                mgzCarryFlapTimer,
                mgzReleasedChaseLatched,
                mgzReleasedChaseXAccel,
                mgzReleasedChaseYAccel,
                flightTimer,
                catchUpTargetX,
                catchUpTargetY);
    }

    public void restoreRewindState(SidekickCpuRewindExtra snapshot) {
        state = snapshot.state();
        despawnCounter = snapshot.despawnCounter();
        frameCounter = snapshot.frameCounter();
        controlCounter = snapshot.controlCounter();
        controller2Held = snapshot.controller2Held();
        controller2Logical = snapshot.controller2Logical();
        inputUp = snapshot.inputUp();
        inputDown = snapshot.inputDown();
        inputLeft = snapshot.inputLeft();
        inputRight = snapshot.inputRight();
        inputJump = snapshot.inputJump();
        inputJumpPress = snapshot.inputJumpPress();
        jumpingFlag = snapshot.jumpingFlag();
        minXBound = snapshot.minXBound();
        maxXBound = snapshot.maxXBound();
        minYBound = snapshot.minYBound();
        maxYBound = snapshot.maxYBound();
        lastInteractObjectId = snapshot.lastInteractObjectId();
        diagnosticS3kInteractWord = snapshot.diagnosticS3kInteractWord();
        normalFrameCount = snapshot.normalFrameCount();
        sidekickCount = snapshot.sidekickCount();
        normalPushingGraceFrames = snapshot.normalPushingGraceFrames();
        suppressNextAirbornePushFollowSteering = snapshot.suppressNextAirbornePushFollowSteering();
        objectOrderGracePushBypassThisFrame = snapshot.objectOrderGracePushBypassThisFrame();
        pendingGroundedFollowNudge = snapshot.pendingGroundedFollowNudge();
        pendingGroundedFollowNudgeFrame = snapshot.pendingGroundedFollowNudgeFrame();
        aizIntroDormantMarkerPrimed = snapshot.aizIntroDormantMarkerPrimed();
        suppressNextLevelEventNormalMovement = snapshot.suppressNextLevelEventNormalMovement();
        catchUpUsesRomVisibleLevelFrameCounter = snapshot.catchUpUsesRomVisibleLevelFrameCounter();
        levelEventDormantMarkerReleasePending = snapshot.levelEventDormantMarkerReleasePending();
        skipPhysicsThisFrame = snapshot.skipPhysicsThisFrame();
        deferredDespawnDeadFallContinuingThisFrame = snapshot.deferredDespawnDeadFallContinuingThisFrame();
        bootstrapPreludePlacementApplied = snapshot.bootstrapPreludePlacementApplied();
        cpuFrameCounterFromStoredLevelFrame = snapshot.cpuFrameCounterFromStoredLevelFrame();
        nextCpuFrameCounterOverride = snapshot.nextCpuFrameCounterOverride();
        catchUpFrameCounterOverride = snapshot.catchUpFrameCounterOverride();
        lastNormalAutoJumpPressFrameCounter = snapshot.lastNormalAutoJumpPressFrameCounter();
        controller2SignedLocked = snapshot.controller2SignedLocked();
        latestNormalStepDiagnostics = snapshot.latestNormalStepDiagnostics();
        carryLatchX = snapshot.carryLatchX();
        carryLatchY = snapshot.carryLatchY();
        flyingCarryingFlag = snapshot.flyingCarryingFlag();
        carryParentagePending = snapshot.carryParentagePending();
        releaseCooldown = snapshot.releaseCooldown();
        mgzCarryIntroAscend = snapshot.mgzCarryIntroAscend();
        mgzCarryFlapTimer = snapshot.mgzCarryFlapTimer();
        mgzReleasedChaseLatched = snapshot.mgzReleasedChaseLatched();
        mgzReleasedChaseXAccel = snapshot.mgzReleasedChaseXAccel();
        mgzReleasedChaseYAccel = snapshot.mgzReleasedChaseYAccel();
        flightTimer = snapshot.flightTimer();
        catchUpTargetX = snapshot.catchUpTargetX();
        catchUpTargetY = snapshot.catchUpTargetY();
    }

    public void applyFlyingCarryVerticalVelocity() {
        if (!usesFlyingCarryMovement()) {
            return;
        }

        int flightTimer = sidekick.getDoubleJumpProperty() & 0xFF;
        if (((frameCounter + 1) & 1) != 0 && flightTimer != 0) {
            flightTimer = (flightTimer - 1) & 0xFF;
            sidekick.setDoubleJumpProperty((byte) flightTimer);
        }

        int flag = sidekick.getDoubleJumpFlag() & 0xFF;
        int ySpeed = sidekick.getYSpeed();
        if (flag != 1) {
            if (ySpeed >= -0x100) {
                ySpeed -= 0x20;
                flag = (flag + 1) & 0xFF;
                if (flag == 0x20) {
                    flag = 1;
                }
            } else {
                flag = 1;
            }
        } else {
            if (inputJumpPress && ySpeed >= -0x100 && flightTimer != 0) {
                flag = 2;
            }
            ySpeed += 0x08;
        }

        Camera camera = sidekick.currentCamera();
        if (camera != null && ySpeed < 0) {
            int cameraMinY = camera.getMinY() & 0xFFFF;
            if ((sidekick.getCentreY() & 0xFFFF) <= cameraMinY + 0x10) {
                ySpeed = 0;
            }
        }

        sidekick.setDoubleJumpFlag(flag);
        sidekick.setYSpeed((short) ySpeed);
    }

    /** Test/debug accessor for the release-cooldown byte (ROM Flying_carrying_Sonic_flag+1). */
    int getReleaseCooldownForTest() { return releaseCooldown; }

    int resolveAnimationId(CanonicalAnimation animation) {
        return sidekick.resolveAnimationId(animation);
    }

    public void reset() {
        state = State.INIT;
        despawnCounter = 0;
        controlCounter = 0;
        controller2Held = 0;
        controller2Logical = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        normalPushingGraceFrames = 0;
        suppressNextAirbornePushFollowSteering = false;
        aizIntroDormantMarkerPrimed = false;
        suppressNextLevelEventNormalMovement = false;
        catchUpUsesRomVisibleLevelFrameCounter = false;
        levelEventDormantMarkerReleasePending = false;
        skipPhysicsThisFrame = false;
        controller2SignedLocked = false;
        nextCpuFrameCounterOverride = -1;
        catchUpFrameCounterOverride = -1;
        // Note: leader is NOT cleared — it's a structural chain relationship set at
        // construction time, not per-level state. Clearing it would break the sidekick
        // permanently since findLeader() scanning was removed in favor of explicit assignment.
        lastInteractObjectId = -1; // ROM Tails_interact_ID unset until next UpdateObjInteract
        diagnosticS3kInteractWord = 0;
        minXBound = Integer.MIN_VALUE;
        maxXBound = Integer.MIN_VALUE;
        minYBound = Integer.MIN_VALUE;
        maxYBound = Integer.MIN_VALUE;
        clearInputs();
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        ObjectControlState.none().applyTo(sidekick);
        // Carry state (carryTrigger is intentionally NOT cleared — level-load-scoped)
        carryLatchX = 0;
        carryLatchY = 0;
        flyingCarryingFlag = false;
        releaseCooldown = 0;
        flightTimer = 0;
        catchUpTargetX = 0;
        catchUpTargetY = 0;
    }

    /**
     * Accessor for the hydrated ROM {@code Tails_CPU_target_X} word
     * (sonic3k.asm $F70A). Surfaces the value written by {@link #hydrateFromRomCpuState}
     * and persisted in the catch-up steering field. Always masked to 16 bits.
     */
    public int targetX() {
        int value = catchUpTargetX & 0xFFFF;
        if (state == State.APPROACHING) {
            return respawnStrategy.diagnosticTargetX(value) & 0xFFFF;
        }
        return value;
    }

    /**
     * Accessor for the hydrated ROM {@code Tails_CPU_target_Y} word
     * (sonic3k.asm $F70C). Surfaces the value written by {@link #hydrateFromRomCpuState}
     * and persisted in the catch-up steering field. Always masked to 16 bits.
     */
    public int targetY() {
        int value = catchUpTargetY & 0xFFFF;
        if (state == State.APPROACHING) {
            return respawnStrategy.diagnosticTargetY(value) & 0xFFFF;
        }
        return value;
    }
}
