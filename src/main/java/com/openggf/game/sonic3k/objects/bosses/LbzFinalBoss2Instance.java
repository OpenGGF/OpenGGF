package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.S3kTransitionWriteSupport;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.objects.SongFadeTransitionInstance;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.SwingMotion;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * S3KL object {@code $CC}, {@code Obj_LBZFinalBoss2}: Big Arm.
 *
 * <p>This is a direct port of the native object graph in
 * {@code docs/skdisasm/sonic3k.asm:154231-155584}.  The root dispatch keeps the
 * ROM routine values (including the grab {@code $1E -> $2A} path); child creation
 * follows {@code ChildObjDat_75122}, {@code ChildObjDat_75144}, and the native
 * defeat child tables in slot order.  Sprite mappings, art, and palette bytes
 * are loaded by the S3K ROM art pipeline; this class only supplies mapping frame,
 * palette-line, and priority selections.</p>
 */
public final class LbzFinalBoss2Instance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable, SpawnRewindRecreatable {
    private static final Logger LOG = Logger.getLogger(LbzFinalBoss2Instance.class.getName());
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE =
            TouchResponseProfile.fromCanonical(
                    com.openggf.game.profiles.touchresponse.TouchResponseProfile
                            .singleRegionContinuousCallbacks());

    private static final int OBJECT_ID = Sonic3kObjectIds.LBZ_FINAL_BOSS_2;

    // LBZFinalBoss2_Index (the table is indexed by the even native routine byte).
    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_WAIT = 0x02;
    private static final int ROUTINE_FALL = 0x06;
    private static final int ROUTINE_BOB_WAIT = 0x08;
    private static final int ROUTINE_ACTION = 0x0A;
    private static final int ROUTINE_BOUNCE = 0x0C;
    private static final int ROUTINE_DROP_WAIT = 0x0E;
    private static final int ROUTINE_DROP = 0x10;
    private static final int ROUTINE_ASCEND = 0x12;
    private static final int ROUTINE_DESCEND = 0x14;
    private static final int ROUTINE_LAND_WAIT = 0x16;
    private static final int ROUTINE_LAND_BOUNCE = 0x18;
    private static final int ROUTINE_RISE_WAIT = 0x1A;
    private static final int ROUTINE_RISE = 0x1C;
    private static final int ROUTINE_GRAB_WAIT = 0x1E;
    private static final int ROUTINE_GRAB_RISE = 0x20;
    private static final int ROUTINE_GRAB_ALIGN_WAIT = 0x22;
    private static final int ROUTINE_GRAB_ALIGN = 0x24;
    private static final int ROUTINE_GRAB_FLOOR = 0x26;
    private static final int ROUTINE_GRAB_FLOOR_WAIT = 0x28;
    private static final int ROUTINE_GRAB_THROW = 0x2A;

    private static final int INITIAL_HITS = 8;
    private static final int INITIAL_WAIT = 0x59;
    private static final int BOB_WAIT = 0x7F;
    private static final int DEBRIS_WAIT = 0xAF;
    private static final int GRAB_WAIT = 0x40;
    private static final int GRAB_ALIGN_WAIT = 0x3F;
    private static final int THROW_WAIT = 0x3F;

    private static final int COLLISION_INIT = 0; // ObjDat_LBZFinalBoss2 collision byte.
    private static final int COLLISION_ACTIVE = 0x0F; // loc_74340 writes $F.
    private static final int ARM_COLLISION = 0xAD; // loc_74A14.
    private static final int OUTER_COLLISION = 0x9A; // loc_74BD0/sub_74EBC.
    private static final int LANDING_COLLISION = 0x9C; // loc_74C00.
    private static final int HIT_FLASH_TIME = 0x3C; // sub_74FD2 writes $3C to field $20.

    private static final int FLAG_ARM_FALLING = 1 << 2;
    private static final int FLAG_BOB_NOTCH = 1 << 3;
    private static final int FLAG_DEFEAT_DEBRIS = 1 << 4;
    private static final int FLAG_CAPSULE_WAIT = 1 << 5;
    private static final int FLAG_FLOOR = 1 << 1;
    private static final int STATUS_NATIVE_BIT6 = 1 << 6;
    private static final int STATUS_HIT_FLASH = STATUS_NATIVE_BIT6;
    private static final int STATUS_DEFEATED = 1 << 7;

    private static final String PALETTE_OWNER = "s3k.lbz.finalBoss2";

    private enum WaitTarget {
        NONE,
        ARM_GRAPH,
        BOB_LOOP,
        ACTION_LOOP,
        BOUNCE_TO_DROP,
        DROP_START,
        LAND_BOUNCE_LOOP,
        RISE,
        GRAB_RISE,
        GRAB_ALIGN,
        THROW
    }

    private enum DefeatStage {
        NONE,
        DELAY,
        DEBRIS_RISE,
        CAPSULE_WAIT,
        AUTOWALK,
        SHIP_RISE,
        SHIP_CRUISE,
        FLOOR_WAIT,
        SHIP_ESCAPE,
        WAIT_FLOOR_SIGNAL,
        WALK_TO_FALL,
        PLAYER_FALL
    }

    public enum ChildKind {
        ROBOTNIK_HEAD,
        ARM_GRAPH,
        ARM_ATTACHMENT,
        ARM_VISUAL,
        ARM_OUTER_COLLISION,
        ARM_SEGMENT,
        ARM_JOINT,
        GRAB,
        ARM_UPPER_COLLISION,
        DEFEAT_DEBRIS,
        DEFEAT_CAPSULE,
        DEFEAT_EXPLOSION_CONTROLLER,
        DEFEAT_FOLLOW_VISUAL,
        ESCAPE_FLAME,
        ESCAPE_FLOOR,
        ESCAPE_FLOOR_EXPLOSION,
        ESCAPE_EXPLOSION_EMITTER
    }

    // ROM x_pos/y_pos are centres. Their low words are full 16.16 position state;
    // MoveSprite2 adds signed 8.8 velocity shifted left by eight.
    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;

    private int routine = ROUTINE_INIT;
    private int waitTimer;
    private WaitTarget waitTarget = WaitTarget.NONE;
    private int hitCount = INITIAL_HITS;
    private int collisionFlags = COLLISION_INIT;
    private int collisionBackup = COLLISION_INIT;
    private int hitFlashTimer;
    private int statusBits;
    private int flags;
    private int mappingFrame = 5;
    private int randomAction;
    private int randomCounter;
    private int angle;
    private boolean renderXFlip;
    private boolean artTileHigh;
    private boolean initialized;
    private boolean deathPlaneDisablePublished;
    private boolean grabActive;
    private boolean defeatStarted;
    private int defeatStartY;
    private int defeatTimer;
    private DefeatStage defeatStage = DefeatStage.NONE;
    private boolean capsuleSpawned;
    private boolean capsuleReleased;
    private int defeatMappingFrame;
    private boolean swingDirectionDown;
    private int carriedFrameTimer;
    private int carriedFrameCounter;
    private boolean rootHidden;
    private boolean rootDrawThisEntry;
    private EscapeFloorChild escapeFloor;
    private RobotnikShipFlameChild escapeFlame;
    private BigArmExplosionControllerChild defeatExplosionController;

    /** Native child graph, captured as rewind object IDs and resolved in phase two. */
    private final List<BossChild> children;
    private final List<AbstractObjectInstance> graphChildren;
    private final List<ChildKind> childOrder;
    private final Map<ChildKind, Integer> childOrdinals;
    private ArmControllerChild armController;
    private LbzFinalBoss2EggCapsuleInstance capsuleChild;
    private transient LbzFinalBoss2RomData romData;

    public LbzFinalBoss2Instance(ObjectSpawn spawn) {
        super(spawn, "LBZFinalBoss2");
        this.x = spawn.x() & 0xFFFF;
        this.y = spawn.y() & 0xFFFF;
        this.children = new ArrayList<>();
        this.graphChildren = new ArrayList<>();
        this.childOrder = new ArrayList<>();
        this.childOrdinals = new EnumMap<>(ChildKind.class);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getX() {
        return x & 0xFFFF;
    }

    @Override
    public int getY() {
        return y & 0xFFFF;
    }

    public int getCentreX() {
        return getX();
    }

    public int getCentreY() {
        return getY();
    }

    public void setCentreX(int centreX) {
        x = centreX & 0xFFFF;
        updateDynamicSpawn(getX(), getY());
    }

    public void setCentreY(int centreY) {
        y = centreY & 0xFFFF;
        updateDynamicSpawn(getX(), getY());
    }

    @Override
    public int getCollisionFlags() {
        return (defeatStarted || (statusBits & STATUS_DEFEATED) != 0) ? 0 : collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        return hitCount;
    }

    @Override
    public boolean usesCurrentTouchResponseState() {
        // Obj_LBZFinalBoss2 calls Draw_And_Touch_Sprite after its routine dispatch.
        return true;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        // The native Touch_Loop polls the collision byte while an overlap remains.
        return true;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (!initialized || defeatStarted || hitFlashTimer > 0 || collisionFlags == 0 || hitCount <= 0) {
            return;
        }
        hitCount--;
        collisionFlags = 0;
        if (hitCount == 0) {
            // sub_74FD2 observes collision_property == 0 after the current routine.
            return;
        }
        pendingHitFlash = true;
    }

    private boolean pendingHitFlash;

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        rootDrawThisEntry = false;
        pruneDestroyedGraphChildren();
        if (!initialized) {
            initializeNative();
            rootDrawThisEntry = true;
            return;
        }

        if (hitFlashTimer > 0) {
            tickHitFlash();
        }

        if (defeatStarted) {
            updateDefeat(player);
        } else {
            dispatchRoutine(player);
            resolveNativeCollision(player);
            // Obj_LBZFinalBoss2's ordinary wrapper always rejoins
            // Draw_And_Touch_Sprite, including the final-hit entry.
            rootDrawThisEntry = true;
        }

        // In a live level spawnChild registers each native child with ObjectManager,
        // which executes it in slot order.  The isolated object tests have no manager;
        // only that harness needs the fallback inline dispatch.
        if (services().objectManager() == null) {
            updateChildren(vIntRunCount, player);
        }
        updateDynamicSpawn(getX(), getY());
    }

    private void initializeNative() {
        initialized = true;
        // SetUp_ObjAttributes advances routine from $00 to $02.
        routine = ROUTINE_WAIT;
        hitCount = INITIAL_HITS;
        collisionFlags = COLLISION_INIT;
        collisionBackup = COLLISION_INIT;
        mappingFrame = 5;
        statusBits = 0;
        flags = FLAG_BOB_NOTCH;
        deathPlaneDisablePublished = true;
        x = (cameraX() + 0xA0) & 0xFFFF;
        y = (cameraY() - 0x50) & 0xFFFF;
        xSub = 0;
        ySub = 0;
        waitTimer = INITIAL_WAIT;
        waitTarget = WaitTarget.ARM_GRAPH;
        ensureBossArtLoaded();
        loadFinalBossPalette();
        // Obj_LBZFinalBoss2's first CreateChild1_Normal is the Robotnik head.
        recordChild(ChildKind.ROBOTNIK_HEAD,
                spawnChild(() -> new RobotnikHead4Child(
                        this, currentPlayerCharacter() == PlayerCharacter.KNUCKLES)));
    }

    private void dispatchRoutine(PlayableEntity player) {
        switch (routine) {
            case ROUTINE_INIT, ROUTINE_WAIT, ROUTINE_DROP_WAIT, ROUTINE_LAND_WAIT,
                    ROUTINE_RISE_WAIT, ROUTINE_GRAB_ALIGN_WAIT, ROUTINE_GRAB_FLOOR_WAIT -> updateWait(player);
            case ROUTINE_FALL -> updateFall();
            case ROUTINE_BOB_WAIT -> updateBobWait();
            case ROUTINE_ACTION -> updateAction();
            case ROUTINE_BOUNCE -> updateBounce();
            case ROUTINE_DROP -> updateDrop();
            case ROUTINE_ASCEND -> updateAscend();
            case ROUTINE_DESCEND -> updateDescend();
            case ROUTINE_LAND_BOUNCE -> updateLandBounce();
            case ROUTINE_RISE -> updateRise();
            case ROUTINE_GRAB_WAIT -> updateGrabWait();
            case ROUTINE_GRAB_RISE -> updateGrabRise();
            case ROUTINE_GRAB_ALIGN -> updateGrabAlign();
            case ROUTINE_GRAB_FLOOR -> updateGrabFloor();
            case ROUTINE_GRAB_THROW -> updateGrabThrow();
            default -> {
                // The native root has no other primary routine values.
            }
        }
    }

    private void updateWait(PlayableEntity player) {
        if (--waitTimer >= 0) {
            return;
        }
        switch (waitTarget) {
            case ARM_GRAPH -> enterArmGraph();
            case BOB_LOOP -> enterBobLoop();
            case ACTION_LOOP -> enterActionLoop();
            case BOUNCE_TO_DROP -> enterDropWait();
            case DROP_START -> enterDrop();
            case LAND_BOUNCE_LOOP -> enterLandBounceLoop();
            case RISE -> enterRise();
            case GRAB_RISE -> enterGrabRise();
            case GRAB_ALIGN -> enterGrabAlign();
            case THROW -> enterGrabThrow(player);
            case NONE -> {
            }
        }
        waitTarget = WaitTarget.NONE;
    }

    private void enterArmGraph() {
        routine = ROUTINE_FALL;
        mappingFrame = 8;
        spawnArmGraph();
    }

    private void updateFall() {
        moveLightGravity();
        if (unsignedGreater(y, cameraY() + 0x120)) {
            artTileHigh = true;
            yVel = 0;
            collisionFlags = COLLISION_ACTIVE;
            renderXFlip = !renderXFlip;
            recordChild(ChildKind.ARM_UPPER_COLLISION,
                    spawnChild(() -> {
                        int[] offset = romData().childOffset(
                                Sonic3kConstants.LBZ_FINAL_BOSS_2_LANDING_CHILD_TABLE_ADDR, 0);
                        return new LandingCollisionChild(this, offset[0], offset[1]);
                    }));
            routine = ROUTINE_BOB_WAIT;
            waitTimer = BOB_WAIT;
            waitTarget = WaitTarget.BOB_LOOP;
        }
    }

    private void updateBobWait() {
        if (--waitTimer >= 0) {
            return;
        }
        enterBobLoop();
    }

    private void enterBobLoop() {
        renderXFlip = !renderXFlip;
        int previousCounter = randomCounter;
        randomCounter = (randomCounter + 1) & 0xFF;
        if ((previousCounter & 4) != 0) {
            routine = ROUTINE_BOUNCE;
            // loc_743AE clears the same byte after testing the old bit 2.
            randomCounter = 0;
            waitTimer = 0;
            waitTarget = WaitTarget.BOUNCE_TO_DROP;
            yVel = 0;
            y = cameraY();
            setHorizontalBounceVelocity();
            return;
        }
        routine = ROUTINE_ACTION;
        flags &= ~(FLAG_BOB_NOTCH | FLAG_ARM_FALLING);
        waitTimer = DEBRIS_WAIT;
        waitTarget = WaitTarget.ACTION_LOOP;
        randomDebrisMotion();
    }

    /** loc_74360: return to the native $7F wait before choosing the next motion. */
    private void enterBobWait() {
        routine = ROUTINE_BOB_WAIT;
        waitTimer = BOB_WAIT;
        waitTarget = WaitTarget.BOB_LOOP;
    }

    private void enterActionLoop() {
        enterBobWait();
    }

    private void updateAction() {
        switch (randomAction) {
            case 2 -> yVel -= 4;
            case 4 -> {
                yVel += angle;
                if (yVel == 0) {
                    angle = 2;
                }
            }
            case 8 -> y = (y - 4) & 0xFFFF;
            default -> {
            }
        }
        moveSprite2();
        if (--waitTimer < 0) {
            enterActionLoop();
        }
    }

    private void updateBounce() {
        bounceAtCameraBounds(0x50, 0xF0);
        moveSprite2();
        if (--waitTimer < 0) {
            enterDropWait();
        }
    }

    private void enterDropWait() {
        routine = ROUTINE_DROP_WAIT;
        xVel = 0;
        waitTimer = 0x1F;
        waitTarget = WaitTarget.DROP_START;
    }

    /** loc_74448: enter routine $10 after the $1F drop wait. */
    private void enterDrop() {
        routine = ROUTINE_DROP;
        flags |= FLAG_BOB_NOTCH;
    }

    private void updateDrop() {
        moveLightGravity();
        if (unsignedGreater(y, cameraYCopy() + 0xC0)) {
            routine = ROUTINE_ASCEND;
            flags |= FLAG_ARM_FALLING;
        }
    }

    private void updateAscend() {
        yVel -= 0x80;
        moveSprite2();
        if (yVel < 0 && unsignedLessOrEqual(y, cameraYCopy() + 0xE0)) {
            routine = ROUTINE_DESCEND;
        }
    }

    private void updateDescend() {
        yVel += 0x40;
        moveSprite2();
        if (yVel >= 0 && unsignedGreaterOrEqual(y, cameraYCopy() + 0xD0)) {
            y = (cameraYCopy() + 0xD0) & 0xFFFF;
            yVel = 0;
            routine = ROUTINE_LAND_WAIT;
            waitTimer = 0x1F;
            waitTarget = WaitTarget.LAND_BOUNCE_LOOP;
        }
    }

    private void updateLandBounce() {
        // Native loc_744FC uses the same Obj_Wait/motion helper as routine $0C,
        // with the tighter $30/$110 camera bounds.
        bounceAtCameraBounds(0x30, 0x110);
        moveSprite2();
        if (--waitTimer < 0) {
            enterRiseWait();
        }
    }

    /** loc_744EA: start the bounded horizontal bounce and its random wait. */
    private void enterLandBounceLoop() {
        routine = ROUTINE_LAND_BOUNCE;
        setHorizontalBounceVelocity();
    }

    private void updateRise() {
        moveSprite2();
        if (unsignedLessOrEqual(y, cameraY() - 0x60)) {
            enterBobWait();
        }
    }

    private void enterRise() {
        routine = ROUTINE_RISE;
        yVel = -0x400;
        flags &= ~FLAG_BOB_NOTCH;
    }

    /** loc_74512: the native $1F wait before routine $1C. */
    private void enterRiseWait() {
        routine = ROUTINE_RISE_WAIT;
        waitTimer = 0x1F;
        waitTarget = WaitTarget.RISE;
    }

    private void updateGrabWait() {
        if (--waitTimer < 0) {
            enterGrabRise();
        }
    }

    private void enterGrabRise() {
        routine = ROUTINE_GRAB_RISE;
        yVel = -0x400;
        randomCounter ^= 2;
    }

    private void updateGrabRise() {
        moveSprite2();
        if (unsignedLessOrEqual(y, cameraY() - 0x60)) {
            routine = ROUTINE_GRAB_ALIGN_WAIT;
            waitTimer = GRAB_ALIGN_WAIT;
            waitTarget = WaitTarget.GRAB_ALIGN;
        }
    }

    private void enterGrabAlign() {
        routine = ROUTINE_GRAB_ALIGN;
        angle = 0;
        if (armController != null) {
            // loc_745A8 clears the controller's circular-lookup phase ($3C).
            armController.angle = 0;
        }
        int desiredX = cameraX() + 0xA0;
        int side = 0xE0;
        renderXFlip = false;
        if (!unsignedGreater(x, desiredX)) {
            renderXFlip = true;
            side = 0x60;
        }
        x = (cameraX() + side) & 0xFFFF;
    }

    private void updateGrabAlign() {
        int targetY = cameraYCopy() + 0x88;
        if (unsignedLess(y, targetY)) {
            y = (y + 8) & 0xFFFF;
            return;
        }
        routine = ROUTINE_GRAB_FLOOR;
        flags |= FLAG_FLOOR;
        S3kTransitionWriteSupport.startLbzBigArmTimedShake(services(), 0x14);
        waitTimer = 3;
        safePlaySfx(Sonic3kSfx.BOSS_HIT_FLOOR);
    }

    private void updateGrabFloor() {
        y = (y + 4) & 0xFFFF;
        if (--waitTimer < 0) {
            routine = ROUTINE_GRAB_FLOOR_WAIT;
            waitTimer = 7;
            waitTarget = WaitTarget.THROW;
        }
    }

    private void enterGrabThrow(PlayableEntity player) {
        routine = ROUTINE_GRAB_THROW;
        grabActive = false;
        flags &= ~FLAG_FLOOR;
        y = (y - 0x10) & 0xFFFF;
        xVel = renderXFlip ? -0x400 : 0x400;
        yVel = -0x600;
        waitTimer = THROW_WAIT;
        releaseGrabbedPlayer(player);
    }

    private void updateGrabThrow() {
        moveSprite(0x38);
        if (--waitTimer < 0) {
            // loc_746C8 branches to loc_74360, restoring the full $7F wait.
            enterBobWait();
        }
    }

    private void resolveNativeCollision(PlayableEntity player) {
        if (routine < ROUTINE_BOB_WAIT || collisionFlags != 0) {
            return;
        }
        if (pendingHitFlash) {
            pendingHitFlash = false;
            hitFlashTimer = HIT_FLASH_TIME;
            collisionBackup = COLLISION_ACTIVE;
            if (armController != null) {
                armController.collisionFlags = 0;
            }
            statusBits |= STATUS_HIT_FLASH;
            if (routine == ROUTINE_ACTION) {
                randomAction = 8;
            }
            safePlaySfx(Sonic3kSfx.BOSS_HIT);
            // sub_74FD2 performs the first white write and timer decrement on
            // the same object entry that observes collision_flags == 0.
            tickHitFlash();
        }
        if (hitCount == 0 && !defeatStarted) {
            startDefeat(player);
        }
    }

    private void tickHitFlash() {
        applyHitFlashPalette((hitFlashTimer & 1) == 0);
        if (--hitFlashTimer <= 0) {
            statusBits &= ~STATUS_HIT_FLASH;
            collisionFlags = collisionBackup;
            if (armController != null) {
                armController.collisionFlags = ARM_COLLISION;
            }
        }
    }

    private void startDefeat(PlayableEntity player) {
        defeatStarted = true;
        defeatStartY = y;
        defeatMappingFrame = mappingFrame;
        collisionFlags = 0;
        statusBits |= STATUS_DEFEATED;
        defeatTimer = 0x3F;
        defeatStage = DefeatStage.DELAY;
        pauseLevelTimer();
        if (services().gameState() != null) {
            services().gameState().addScore(1000);
        }
        defeatExplosionController = recordChild(ChildKind.DEFEAT_EXPLOSION_CONTROLLER,
                spawnChild(() -> new BigArmExplosionControllerChild(this)));
        // FixBugs=0 is the shipped ROM path at loc_7506E. Its branch is
        // inverted: $30 == 0 restores control, while $30 != 0 (actually held)
        // skips restoration. A FixBugs=1 build would restore only when held.
        if (!grabActive) {
            restorePlayerControl(player);
        }
        int[] followOffset = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_FOLLOW_CHILD_TABLE_ADDR, 0);
        recordChild(ChildKind.DEFEAT_FOLLOW_VISUAL,
                spawnChild(() -> new DefeatFollowVisualChild(
                        this, followOffset[0], followOffset[1])));
    }

    private void updateDefeat(PlayableEntity player) {
        switch (defeatStage) {
            case DELAY -> updateDefeatDelay();
            case DEBRIS_RISE -> updateDefeatRise();
            case CAPSULE_WAIT -> updateCapsuleWait(player);
            case AUTOWALK -> updateAutoWalk(player);
            case SHIP_RISE -> {
                rootDrawThisEntry = true;
                updateShipRise(player);
            }
            case SHIP_CRUISE -> {
                rootDrawThisEntry = true;
                updateShipCruise(player);
            }
            case FLOOR_WAIT -> {
                // loc_74894 resumes after Obj_Wait and draws even on the
                // expiry entry whose callback installs loc_748AE.
                rootDrawThisEntry = true;
                updateFloorWait();
            }
            case SHIP_ESCAPE -> updateShipEscape(player);
            case WAIT_FLOOR_SIGNAL -> updateWaitFloorSignal(player);
            case WALK_TO_FALL -> updateWalkToFall(player);
            case PLAYER_FALL -> updatePlayerFall(player);
            case NONE -> defeatStage = DefeatStage.DEBRIS_RISE;
        }
    }

    private void updateDefeatDelay() {
        if (--defeatTimer >= 0) {
            rootDrawThisEntry = true;
            return;
        }
        // Wait_FadeToLevelMusic callback loc_746D8.
        defeatTimer = (2 * 60) - 1;
        spawnFreeChild(() -> SongFadeTransitionInstance.createNativeLevelMusicFade(
                services().getCurrentLevelMusicId()));
        mappingFrame = 5;
        defeatMappingFrame = 5;
        flags |= FLAG_DEFEAT_DEBRIS;
        defeatStage = DefeatStage.DEBRIS_RISE;
        recordDefeatDebris();
    }

    private void updateDefeatRise() {
        // loc_746F4 is position-driven: the root rises until Camera_Y-$40,
        // with no frame-count substitute.
        y = (y - 1) & 0xFFFF;
        if (unsignedLess(y, cameraY() - 0x40)) {
            beginCapsuleHandoff();
            return;
        }
        rootDrawThisEntry = true;
    }

    private void beginCapsuleHandoff() {
        defeatStage = DefeatStage.CAPSULE_WAIT;
        flags |= FLAG_CAPSULE_WAIT;
        renderXFlip = true;
        x = (cameraX() + 0x40) & 0xFFFF;
        if (services().gameState() != null) {
            // Boss_LoadEggCapsuleAndAnimals owns _unkFAA8 and writes it before
            // allocating the later capsule slot.
            services().gameState().setEndOfLevelActive(true);
        }
        // Boss_LoadEggCapsuleAndAnimals uses CreateChild6_Simple: the capsule
        // is a later-slot child of the retained root, so its two-signal write
        // cannot become visible to loc_7473A on the root's current dispatch.
        LbzFinalBoss2EggCapsuleInstance candidate = spawnChild(() ->
                LbzFinalBoss2EggCapsuleInstance.createForCamera(cameraX(), cameraY()));
        capsuleSpawned = recordChild(ChildKind.DEFEAT_CAPSULE, candidate);
        if (capsuleSpawned) {
            capsuleChild = candidate;
        } else {
            capsuleChild = null;
        }
    }

    private void updateCapsuleWait(PlayableEntity player) {
        // loc_7473A polls two separately-owned RAM bytes in this order. The
        // root writes neither signal: Obj_LevelResults clears _unkFAA8 and the
        // later capsule slot latches _unkFAA2 after its X threshold.
        if (services().gameState() != null && services().gameState().isEndOfLevelActive()) {
            return;
        }
        if (services().waterSystem() == null
                || !services().waterSystem().isDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1)) {
            return;
        }
        capsuleReleased = true;
        statusBits &= ~STATUS_DEFEATED;
        if (capsuleChild != null) {
            forgetChild(capsuleChild);
            capsuleChild = null;
        }
        ensurePostCapsuleArtLoaded();
        restorePlayerControl(player);
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setControlLocked(true);
            sprite.setForcedInputMask(0);
        }
        defeatStage = DefeatStage.AUTOWALK;
        // loc_7473A installs loc_74768 and falls through on the gate entry.
        updateAutoWalk(player);
    }

    private void updateAutoWalk(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            startShipRise();
            return;
        }
        int target = cameraX() + 0x50;
        int dx = (short) (target - sprite.getCentreX());
        if (Math.abs(dx) >= 8) {
            sprite.setForcedInputMask(dx >= 0
                    ? AbstractPlayableSprite.INPUT_RIGHT
                    : AbstractPlayableSprite.INPUT_LEFT);
            return;
        }
        sprite.setForcedInputMask(0);
        sprite.setDirection(Direction.LEFT);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        // loc_74768 loads PLC $71 and queues the Knuckles head before
        // clearing root $38 bit 5 and allocating the replacement head.
        S3kTransitionWriteSupport.loadLbzBigArmPostGatePlc(services());
        ensureEggRoboHeadArtLoaded();
        flags &= ~FLAG_CAPSULE_WAIT;
        recordChild(ChildKind.ROBOTNIK_HEAD,
                spawnChild(() -> new RobotnikHead4Child(this, true)));
        startShipRise();
    }

    private void startShipRise() {
        defeatStage = DefeatStage.SHIP_RISE;
    }

    private void updateShipRise(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
        }
        int nextY = (y + 1) & 0xFFFF;
        if (unsignedLess(nextY, cameraY() + 0x40)) {
            y = nextY;
            return;
        }
        xVel = 0x200;
        yVel = 0xC0;
        swingDirectionDown = false;
        defeatStage = DefeatStage.SHIP_CRUISE;
        escapeFlame = (RobotnikShipFlameChild) recordChild(ChildKind.ESCAPE_FLAME,
                spawnChild(() -> new RobotnikShipFlameChild(this)));
    }

    private void updateShipCruise(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
        }
        swingAndMove();
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setDirection(unsignedLess(x, sprite.getCentreX()) ? Direction.LEFT : Direction.RIGHT);
        }
        if (unsignedLess(x, cameraX() + 0xA0)) {
            return;
        }
        defeatStage = DefeatStage.FLOOR_WAIT;
        defeatTimer = 0x1F;
        flags &= ~FLAG_BOB_NOTCH;
        int[] offset = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_FLOOR_CHILD_TABLE_ADDR, 0);
        escapeFloor = (EscapeFloorChild) recordChild(ChildKind.ESCAPE_FLOOR,
                spawnChild(() -> new EscapeFloorChild(this, offset[0], offset[1])));
    }

    private void updateFloorWait() {
        if (--defeatTimer >= 0) {
            return;
        }
        xVel = 0x400;
        defeatStage = DefeatStage.SHIP_ESCAPE;
    }

    private void updateShipEscape(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
        }
        swingAndMove();
        if (unsignedLess(x, cameraX() + 0x1C0)) {
            rootDrawThisEntry = true;
            return;
        }
        if (services().gameState() != null) {
            services().gameState().setCurrentBossId(0);
        }
        // loc_748D0 retains status bit 6 after Boss_flag is cleared. This is
        // the same bit used by the earlier hit-flash state, but it is not
        // coupled to a live hit-flash timer in the shipped escape callback.
        statusBits |= STATUS_NATIVE_BIT6;
        flags |= FLAG_DEFEAT_DEBRIS | FLAG_CAPSULE_WAIT;
        rootHidden = true;
        defeatStage = DefeatStage.WAIT_FLOOR_SIGNAL;
    }

    private void updateWaitFloorSignal(PlayableEntity player) {
        if ((flags & FLAG_BOB_NOTCH) == 0) {
            return;
        }
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
        }
        defeatStage = DefeatStage.WALK_TO_FALL;
    }

    private void updateWalkToFall(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)
                || unsignedLess(sprite.getCentreX(), 0x4510)) {
            return;
        }
        x = sprite.getCentreX() & 0xFFFF;
        y = sprite.getCentreY() & 0xFFFF;
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        sprite.setControlLocked(true);
        sprite.setForcedInputMask(0);
        sprite.setObjectMappingFrameControl(true);
        sprite.setMappingFrame(0x8C);
        carriedFrameCounter = 0;
        carriedFrameTimer = 0x0A;
        xVel = 0x200;
        yVel = -0x400;
        defeatStage = DefeatStage.PLAYER_FALL;
    }

    private void swingAndMove() {
        SwingMotion.Result swing = SwingMotion.update(0x10, yVel, 0xC0, swingDirectionDown);
        yVel = swing.velocity();
        swingDirectionDown = swing.directionDown();
        moveSprite2();
    }

    private void updatePlayerFall(PlayableEntity player) {
        moveLightGravity();
        if (player instanceof AbstractPlayableSprite sprite) {
            NativePositionOps.writeXPosPreserveSubpixel(sprite, getCentreX());
            NativePositionOps.writeYPosPreserveSubpixel(sprite, getCentreY());
            if (--carriedFrameTimer < 0) {
                carriedFrameTimer = 0x0A;
                carriedFrameCounter++;
                sprite.setMappingFrame((carriedFrameCounter & 1) != 0 ? 0x8C : 0x8D);
            }
        }
        int threshold = (romData().escapeMinimumY() + 0x200) & 0xFFFF;
        if (!unsignedLess(y, threshold)) {
            services().requestZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 0, true);
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    private void spawnArmGraph() {
        if (armController != null && !armController.isDestroyed()) {
            return;
        }
        int[] controllerOffset = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_INITIAL_CHILD_TABLE_ADDR, 0);
        if (recordChild(ChildKind.ARM_GRAPH,
                spawnChild(() -> new ArmControllerChild(
                        this, controllerOffset[0], controllerOffset[1]))) == null) {
            return;
        }
        int[] attachmentOffset = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_INITIAL_CHILD_TABLE_ADDR, 1);
        if (recordChild(ChildKind.ARM_ATTACHMENT,
                spawnChild(() -> new ArmAttachmentChild(this, attachmentOffset[0], attachmentOffset[1],
                        0, 0x200, 1))) == null) {
            return;
        }
        int[] visualOffset = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_INITIAL_CHILD_TABLE_ADDR, 2);
        if (recordChild(ChildKind.ARM_VISUAL,
                spawnChild(() -> new ArmVisualJointChild(this, visualOffset[0], visualOffset[1],
                        1, 0x300, 1))) == null) {
            return;
        }
        int[] outerOffset = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_INITIAL_CHILD_TABLE_ADDR, 3);
        recordChild(ChildKind.ARM_OUTER_COLLISION,
                spawnChild(() -> new ArmOuterCollisionChild(this, outerOffset[0], outerOffset[1])));
    }

    private void spawnNestedArmGraph() {
        if (armController == null || armController.nestedSpawned) {
            return;
        }
        armController.nestedSpawned = true;
        int[] segment0 = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_ARM_CHILD_TABLE_ADDR, 0);
        if (recordChild(ChildKind.ARM_SEGMENT,
                spawnChildAfterSlot(armController.getSlotIndex(),
                        () -> new ArmSegmentChild(
                                this, armController, 0, segment0[0], segment0[1]))) == null) {
            return;
        }
        int[] segment1 = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_ARM_CHILD_TABLE_ADDR, 1);
        if (recordChild(ChildKind.ARM_SEGMENT,
                spawnChildAfterSlot(armController.getSlotIndex(),
                        () -> new ArmSegmentChild(
                                this, armController, 1, segment1[0], segment1[1]))) == null) {
            return;
        }
        int[] joint = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_ARM_CHILD_TABLE_ADDR, 2);
        if (recordChild(ChildKind.ARM_JOINT,
                spawnChildAfterSlot(armController.getSlotIndex(),
                        () -> new ArmKinematicJointChild(
                                this, armController, joint[0], joint[1]))) == null) {
            return;
        }
        int[] grab = romData().childOffset(
                Sonic3kConstants.LBZ_FINAL_BOSS_2_ARM_CHILD_TABLE_ADDR, 3);
        recordChild(ChildKind.GRAB,
                spawnChildAfterSlot(armController.getSlotIndex(),
                        () -> new GrabOwnerChild(this, armController, grab[0], grab[1])));
    }

    private void forgetChild(AbstractObjectInstance child) {
        int index = graphChildren.indexOf(child);
        if (index >= 0) {
            graphChildren.remove(index);
            childOrder.remove(index);
        }
        if (child instanceof BossChild bossChild) {
            children.remove(bossChild);
        }
        if (child == armController) {
            armController = null;
        }
        if (child == escapeFlame) {
            escapeFlame = null;
        }
        if (child == escapeFloor) {
            escapeFloor = null;
        }
        if (child == defeatExplosionController) {
            defeatExplosionController = null;
        }
    }

    private void updateChildren(int vIntRunCount, PlayableEntity player) {
        for (BossChild child : List.copyOf(children)) {
            if (!child.isDestroyed()) {
                child.update(vIntRunCount, player);
            }
        }
    }

    private void randomDebrisMotion() {
        int random = randomWord();
        randomAction = random & 6;
        int side = renderXFlip ? -0x68 : 0x1A8;
        x = (cameraX() + side) & 0xFFFF;
        xVel = renderXFlip ? 0x300 : -0x300;
        int index = (randomAction >> 1) & 3;
        int[] yOffsets = romData().motionWords(0);
        int[] yVelocities = romData().motionWords(1);
        y = (cameraY() + yOffsets[index]) & 0xFFFF;
        yVel = yVelocities[index];
        angle = 8;
    }

    private void setHorizontalBounceVelocity() {
        xVel = renderXFlip ? 0x300 : -0x300;
        int random = randomWord();
        waitTimer = 0xC0 + (random & 0x7F);
    }

    private void bounceAtCameraBounds(int min, int max) {
        int left = cameraX() + min;
        int right = cameraX() + max;
        if (xVel >= 0 && unsignedGreaterOrEqual(x, right)
                || xVel < 0 && unsignedLessOrEqual(x, left)) {
            renderXFlip = !renderXFlip;
            xVel = -xVel;
        }
    }

    private void recordDefeatDebris() {
        for (int i = 0; i < 5; i++) {
            int subtype = i * 2;
            int[] offset = romData().childOffset(
                    Sonic3kConstants.LBZ_FINAL_BOSS_2_DEBRIS_CHILD_TABLE_ADDR, i);
            if (recordChild(ChildKind.DEFEAT_DEBRIS,
                    spawnChild(() -> new DefeatDebrisChild(
                            this, offset[0], offset[1], subtype))) == null) {
                return;
            }
        }
    }

    private void ensureBossArtLoaded() {
        try {
            if (services().renderManager() != null
                    && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
                provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_2);
            }
        } catch (RuntimeException ex) {
            LOG.fine("Big Arm art preflight unavailable: " + ex.getMessage());
        }
    }

    /** {@code sub_7302E}: raw PLC containing Robotnik ship and boss-explosion art. */
    private void ensurePostCapsuleArtLoaded() {
        try {
            if (services().renderManager() != null
                    && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
                provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
                provider.ensureBossExplosionArtLoaded();
            }
        } catch (RuntimeException ex) {
            LOG.fine("Big Arm post-capsule PLC unavailable: " + ex.getMessage());
        }
    }

    private void ensureEggRoboHeadArtLoaded() {
        try {
            if (services().renderManager() != null
                    && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
                provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.EGG_ROBO_HEAD);
            }
        } catch (RuntimeException ex) {
            LOG.fine("Big Arm Egg Robo head queue unavailable: " + ex.getMessage());
        }
    }

    private void loadFinalBossPalette() {
        S3kPaletteWriteSupport.applyLine(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                PALETTE_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                1,
                romData().paletteLine());
    }

    private void applyHitFlashPalette(boolean white) {
        // sub_75084 copies six words into Normal_palette_line_2.  Keep the
        // source-backed colour sequence; the ownership surface restores the
        // ROM palette after the $3C timer expires.
        S3kPaletteWriteSupport.applyColors(
                services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                services().graphicsManager(), PALETTE_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1,
                romData().flashPaletteIndices(), romData().flashPaletteWords(white));
    }

    private void pauseLevelTimer() {
        if (services().levelGamestate() != null) {
            services().levelGamestate().pauseTimer();
        }
    }

    private void restorePlayerControl(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            ObjectControlState.none().applyTo(sprite);
            sprite.setControlLocked(false);
        }
    }

    private void releaseGrabbedPlayer(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        if (sprite.getInvincibleFrames() == 0) {
            // loc_74664 passes the root as HurtCharacter's damage source.
            sprite.applyHurt(getCentreX());
            return;
        }
        ObjectControlState.none().applyTo(sprite);
        sprite.setXSpeed((short) -xVel);
        sprite.setYSpeed((short) -0x400);
        sprite.setAir(true);
        sprite.setJumping(false);
        sprite.setSpindash(false);
        sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
        safePlaySfx(Sonic3kSfx.SPRING);
    }

    private PlayerCharacter currentPlayerCharacter() {
        if (services().zoneRuntimeState() instanceof S3kZoneRuntimeState state) {
            return state.playerCharacter();
        }
        return PlayerCharacter.SONIC_AND_TAILS;
    }

    private int randomWord() {
        try {
            return services().rng().nextWord();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void safePlaySfx(Sonic3kSfx sfx) {
        try {
            services().playSfx(sfx.id);
        } catch (RuntimeException ignored) {
            // Headless object tests intentionally provide no audio backend.
        }
    }

    private void moveSprite2() {
        int nextX = ((x & 0xFFFF) << 16) | (xSub & 0xFFFF);
        int nextY = ((y & 0xFFFF) << 16) | (ySub & 0xFFFF);
        nextX += ((short) xVel) << 8;
        nextY += ((short) yVel) << 8;
        x = (nextX >>> 16) & 0xFFFF;
        y = (nextY >>> 16) & 0xFFFF;
        xSub = nextX & 0xFFFF;
        ySub = nextY & 0xFFFF;
    }

    private void moveLightGravity() {
        int oldYVel = (short) yVel;
        moveSprite2();
        yVel = (short) (oldYVel + 0x20);
    }

    private void moveSprite(int gravity) {
        int oldYVel = (short) yVel;
        moveSprite2();
        yVel = (short) (oldYVel + gravity);
    }

    private LbzFinalBoss2RomData romData() {
        if (romData == null) {
            try {
                romData = new LbzFinalBoss2RomData(services().romReader());
            } catch (IOException ex) {
                throw new IllegalStateException("Big Arm requires the verified S3K ROM", ex);
            }
        }
        return romData;
    }

    private int cameraX() {
        return services().camera() == null ? 0 : services().camera().getX() & 0xFFFF;
    }

    private int cameraY() {
        return services().camera() == null ? 0 : services().camera().getY() & 0xFFFF;
    }

    private int cameraYCopy() {
        int base = cameraY();
        if (services().zoneRuntimeState() instanceof LbzZoneRuntimeState state) {
            return state.getCameraYCopy(base);
        }
        return base;
    }

    private static int unsigned(int value) {
        return value & 0xFFFF;
    }

    private static int nativePlayerRoutine(AbstractPlayableSprite player) {
        if (player.getObjectRoutineOverride() != null) {
            return player.getObjectRoutineOverride() & 0xFF;
        }
        if (player.getDead()) {
            return 6;
        }
        return player.isHurt() ? 4 : 2;
    }

    private static boolean unsignedGreater(int a, int b) {
        return unsigned(a) > unsigned(b);
    }

    private static boolean unsignedLess(int a, int b) {
        return unsigned(a) < unsigned(b);
    }

    private static boolean unsignedGreaterOrEqual(int a, int b) {
        return unsigned(a) >= unsigned(b);
    }

    private static boolean unsignedLessOrEqual(int a, int b) {
        return unsigned(a) <= unsigned(b);
    }

    /**
     * Java equivalent of the ROM MoveSprite_CircularLookup helper. The 68k
     * word index {@code ~d0} addresses the lookup's mirrored final entry, so
     * its Java equivalent is {@code 0x3F - index}.
     */
    private int[] circularOffset(int angle, boolean secondTable, boolean flip) {
        int unsignedAngle = angle & 0xFF;
        int index = unsignedAngle & 0x3F;
        int mirrored = 0x3F - index;
        int x = secondTable ? romData().circleOffset2(index) : romData().circleOffset(index);
        int y = secondTable ? romData().circleOffset2(mirrored) : romData().circleOffset(mirrored);
        int quadrant = unsignedAngle >>> 6;
        int offsetX;
        int offsetY;
        switch (quadrant) {
            case 0 -> { offsetX = x; offsetY = y; }
            case 1 -> { offsetX = y; offsetY = -x; }
            case 2 -> { offsetX = -x; offsetY = -y; }
            case 3 -> { offsetX = -y; offsetY = x; }
            default -> throw new AssertionError(quadrant);
        }
        if (flip) {
            offsetX = -offsetX;
        }
        return new int[]{offsetX, offsetY};
    }

    private int nextChildOrdinal(ChildKind kind) {
        return childOrdinals.getOrDefault(kind, 0);
    }

    private <T extends BossChild> T recordChild(ChildKind kind, T child) {
        if (!isSuccessfulSpawn(child)) {
            return null;
        }
        child.setServices(services());
        child.kind = kind;
        children.add(child);
        graphChildren.add(child);
        childOrder.add(kind);
        childOrdinals.merge(kind, 1, Integer::sum);
        if (child instanceof ArmControllerChild controller) {
            armController = controller;
        }
        return child;
    }

    private boolean recordChild(ChildKind kind, AbstractObjectInstance child) {
        if (child instanceof BossChild bossChild) {
            return recordChild(kind, bossChild) != null;
        }
        if (!isSuccessfulSpawn(child)) {
            return false;
        }
        child.setServices(services());
        graphChildren.add(child);
        childOrder.add(kind);
        childOrdinals.merge(kind, 1, Integer::sum);
        return true;
    }

    private boolean isSuccessfulSpawn(AbstractObjectInstance child) {
        if (child == null || child.isDestroyed()) {
            return false;
        }
        return services().objectManager() == null || child.getSlotIndex() >= 0;
    }

    private void pruneDestroyedGraphChildren() {
        for (int i = graphChildren.size() - 1; i >= 0; i--) {
            AbstractObjectInstance child = graphChildren.get(i);
            if (!child.isDestroyed()) {
                continue;
            }
            graphChildren.remove(i);
            childOrder.remove(i);
            if (child instanceof BossChild bossChild) {
                children.remove(bossChild);
            }
            if (child == armController) {
                armController = null;
            }
            if (child == escapeFlame) {
                escapeFlame = null;
            }
            if (child == escapeFloor) {
                escapeFloor = null;
            }
            if (child == defeatExplosionController) {
                defeatExplosionController = null;
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || rootHidden || !rootDrawThisEntry) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), renderXFlip, false, 0);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5); // ObjDat_LBZFinalBoss2 priority $280.
    }

    @Override
    public boolean isHighPriority() {
        return artTileHigh;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 0x1C;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 0x20;
    }

    // Stable, deliberately small inspection surface used by route/ROM tests.
    public int getRoutineForTest() { return routine; }
    public int getTimerForTest() { return waitTimer; }
    public int getMappingFrameForTest() { return mappingFrame; }
    public int getXVelocityForTest() { return (short) xVel; }
    public int getYVelocityForTest() { return (short) yVel; }
    public int getHitFlashTimerForTest() { return hitFlashTimer; }
    public int getRandomCounterForTest() { return randomCounter; }
    public int getRandomActionForTest() { return randomAction; }
    public boolean isRenderXFlipForTest() { return renderXFlip; }
    public int getArmCollisionForTest() { return armController == null ? 0 : armController.collisionFlags; }
    public boolean isArtTileHighForTest() { return artTileHigh; }
    public String getBodyArtKeyForTest() { return Sonic3kObjectArtKeys.ROBOTNIK_SHIP; }
    public String getArmArtKeyForTest() { return Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_2; }
    public String getHeadArtKeyForTest() {
        return children.stream()
                .filter(child -> child instanceof RobotnikHead4Child)
                .map(child -> ((RobotnikHead4Child) child).renderArtKey())
                .findFirst()
                .orElse(null);
    }
    public boolean isGrabActiveForTest() { return grabActive; }
    public boolean isDefeatStartedForTest() { return defeatStarted; }
    public int getDefeatStartYForTest() { return defeatStartY; }
    public int getDefeatMappingFrameForTest() { return defeatMappingFrame; }
    public boolean isCapsuleChildSpawnedForTest() { return capsuleSpawned; }
    public boolean isCapsuleReleasedForTest() { return capsuleReleased; }
    public boolean hasPublishedDeathPlaneDisable() { return deathPlaneDisablePublished; }
    public List<Object> childrenOfKindForTest(ChildKind kind) {
        List<Object> matches = new ArrayList<>();
        for (int i = 0; i < childOrder.size(); i++) {
            if (childOrder.get(i) == kind) {
                matches.add(graphChildren.get(i));
            }
        }
        return List.copyOf(matches);
    }
    public List<ChildKind> getChildOrderForTest() {
        return List.copyOf(childOrder);
    }
    public int getChildAllocationCountForTest(ChildKind kind) {
        return childOrdinals.getOrDefault(kind, 0);
    }

    /** Common ROM child state. Parent links restore through object IDs in phase two. */
    public abstract static class BossChild extends AbstractObjectInstance
            implements SpawnRewindRecreatable, TouchResponseProvider {
        protected enum PendingDelete {
            NONE,
            GO_DELETE,
            GO_DELETE_2,
            GO_DELETE_3
        }

        protected LbzFinalBoss2Instance boss;
        private ChildKind constructorKind;
        protected int dx;
        protected int dy;
        protected int currentX;
        protected int currentY;
        protected int mappingFrame;
        protected int collisionFlags;
        protected int paletteOverride = -1;
        protected boolean hFlip;
        protected boolean artTileHigh;
        protected boolean nestedSpawned;
        protected boolean flickerMove;
        protected boolean flickerVisible = true;
        protected int flickerXVelocity;
        protected int flickerYVelocity;
        protected int currentXSub;
        protected int currentYSub;
        private PendingDelete pendingDelete = PendingDelete.NONE;
        ChildKind kind;

        protected BossChild(LbzFinalBoss2Instance boss, ChildKind kind,
                            String name, int dx, int dy, int priority) {
            super(new ObjectSpawn((boss.getCentreX() + dx) & 0xFFFF,
                    (boss.getCentreY() + dy) & 0xFFFF, OBJECT_ID,
                    boss.nextChildOrdinal(kind), 0, false, 0), name);
            this.boss = boss;
            this.constructorKind = kind;
            this.dx = dx;
            this.dy = dy;
            this.mappingFrame = 0;
            this.priority = priority;
            refreshPosition();
        }

        /**
         * Phase-one rewind shell. Semantic parent/sibling edges are deliberately
         * null until the compact phase-two restore resolves their exact object IDs.
         */
        protected BossChild(ObjectSpawn spawn, ChildKind kind, String name, int priority) {
            super(spawn, name);
            this.constructorKind = kind;
            this.currentX = spawn.x() & 0xFFFF;
            this.currentY = spawn.y() & 0xFFFF;
            this.priority = priority;
        }

        protected int priority;

        @Override
        public boolean isPersistent() {
            return boss != null && boss.isPersistent();
        }

        @Override
        public int getX() { return currentX & 0xFFFF; }

        @Override
        public int getY() { return currentY & 0xFFFF; }

        @Override
        public int getPriorityBucket() {
            // Child_GetPriority publishes the native priority word divided by
            // $80. Treating $200/$300 as already-normalized buckets clamps
            // both to 7 and destroys the ROM ordering.
            return RenderPriority.clamp((priority & 0xFFFF) >>> 7);
        }

        @Override
        public boolean isHighPriority() {
            return artTileHigh;
        }

        @Override
        public boolean usesCurrentTouchResponseState() { return true; }

        @Override
        public int getCollisionFlags() {
            return boss == null || boss.isDestroyed() ? 0 : collisionFlags;
        }

        @Override
        public int getCollisionProperty() { return 0; }

        protected void refreshPosition() {
            int offsetX = boss.renderXFlip ? -dx : dx;
            currentX = (boss.getCentreX() + offsetX) & 0xFFFF;
            currentY = (boss.getCentreY() + dy) & 0xFFFF;
            updateDynamicSpawn(getX(), getY());
        }

        protected void refreshPositionFrom(BossChild parent) {
            hFlip = parent.hFlip;
            int offsetX = hFlip ? -dx : dx;
            currentX = (parent.getX() + offsetX) & 0xFFFF;
            currentY = (parent.getY() + dy) & 0xFFFF;
            updateDynamicSpawn(getX(), getY());
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed() || pendingDelete != PendingDelete.NONE
                    || (flickerMove && !flickerVisible)) {
                return;
            }
            PatternSpriteRenderer renderer = getRenderer(renderArtKey());
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(mappingFrame, getX(), getY(), hFlip, false, paletteOverride);
            }
        }

        protected String renderArtKey() {
            return Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_2;
        }

        protected final boolean deleteOnPendingEntry() {
            if (pendingDelete == PendingDelete.NONE) {
                return false;
            }
            onPendingDeleteEntry();
            return true;
        }

        protected void onPendingDeleteEntry() {
            boss.forgetChild(this);
            ObjectLifetimeOps.expireDynamic(this);
        }

        protected final void schedulePendingDelete(PendingDelete callback) {
            pendingDelete = callback;
        }

        protected boolean updateFlickerMove() {
            if (!flickerMove) {
                return false;
            }
            int nextX = ((currentX & 0xFFFF) << 16) | (currentXSub & 0xFFFF);
            int nextY = ((currentY & 0xFFFF) << 16) | (currentYSub & 0xFFFF);
            nextX += ((short) flickerXVelocity) << 8;
            nextY += ((short) flickerYVelocity) << 8;
            currentX = (nextX >>> 16) & 0xFFFF;
            currentY = (nextY >>> 16) & 0xFFFF;
            currentXSub = nextX & 0xFFFF;
            currentYSub = nextY & 0xFFFF;
            flickerYVelocity = (short) (flickerYVelocity + 0x38);
            updateDynamicSpawn(getX(), getY());

            if (S3kBossFlickerMove.isOutsideNativeBounds(
                    currentX, currentY, boss.cameraX(), boss.cameraY())) {
                // Go_Delete_Sprite_3 installs Delete_Current_Sprite and keeps
                // the SST alive until this object's next own callback.
                schedulePendingDelete(PendingDelete.GO_DELETE_3);
                return true;
            }
            // Obj_FlickerMove toggles $38 bit 6 before its conditional draw.
            // The first movement entry changes zero to one and does not draw.
            flickerVisible = !flickerVisible;
            return true;
        }

        protected void enterFlickerMoveIfDefeated(int nativeSubtype) {
            if (flickerMove || (boss.statusBits & STATUS_DEFEATED) == 0) {
                return;
            }
            int[] velocity = boss.romData().indexedVelocityAtByteOffset(
                    0x0C + nativeSubtype * 2);
            // Set_IndexedVelocity tests this child's latched render_flags bit
            // after its source-specific parent refresh, not the root's current
            // flip (which can differ by one object dispatch).
            flickerXVelocity = hFlip ? -velocity[0] : velocity[0];
            flickerYVelocity = velocity[1];
            collisionFlags = 0;
            flickerMove = true;
            flickerVisible = true;
        }

        public int paletteOverrideForTest() { return paletteOverride; }
        public ChildKind kindForTest() { return kind != null ? kind : constructorKind; }
        public int mappingFrameForTest() { return mappingFrame; }
        public int xOffsetForTest() { return dx; }
        public int yOffsetForTest() { return dy; }
        public boolean isFlickerMoveForTest() { return flickerMove; }
        public int flickerXVelocityForTest() { return (short) flickerXVelocity; }
        public int flickerYVelocityForTest() { return (short) flickerYVelocity; }
        public boolean isPendingDeleteForTest() {
            return pendingDelete != PendingDelete.NONE;
        }
    }

    /** Obj_RobotnikHead4 / Child1_MakeRoboHead4. */
    public static final class RobotnikHead4Child extends BossChild {
        private boolean eggRoboRoute;
        private boolean initialized;
        private int animationTimer;
        private int rawCursor;

        private RobotnikHead4Child(LbzFinalBoss2Instance boss, boolean eggRoboRoute) {
            super(boss, ChildKind.ROBOTNIK_HEAD, "LBZFinalBoss2RobotnikHead", 0, -0x1C, 0x280);
            this.eggRoboRoute = eggRoboRoute;
        }

        private RobotnikHead4Child(ObjectSpawn spawn) {
            super(spawn, ChildKind.ROBOTNIK_HEAD, "LBZFinalBoss2RobotnikHead", 0x280);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!initialized) {
                // Obj_RobotnikHead3Init installs attributes/script and returns;
                // its first Animate_Raw dispatch is the next own slot entry.
                initialized = true;
                if (eggRoboRoute) {
                    boss.ensureEggRoboHeadArtLoaded();
                }
            } else if ((boss.statusBits & STATUS_DEFEATED) != 0) {
                mappingFrame = 3;
            } else {
                animationTimer = (animationTimer - 1) & 0xFF;
                if ((byte) animationTimer < 0) {
                    int[] script = eggRoboRoute
                            ? boss.romData().eggRoboHeadAnimation()
                            : boss.romData().robotnikHeadAnimation();
                    rawCursor = (rawCursor + 1) & 0xFF;
                    int nextFrame = script[rawCursor + 1];
                    if (nextFrame == 0xFC) {
                        rawCursor = 0;
                        animationTimer = script[0];
                        mappingFrame = script[1];
                    } else {
                        animationTimer = script[0];
                        mappingFrame = nextFrame;
                    }
                }
                if ((boss.statusBits & STATUS_HIT_FLASH) != 0) {
                    mappingFrame = 2;
                }
            }
            artTileHigh = boss.artTileHigh;
            hFlip = boss.renderXFlip;
            refreshPosition();
            if ((boss.flags & FLAG_CAPSULE_WAIT) != 0) {
                boss.forgetChild(this);
                ObjectLifetimeOps.expireDynamic(this);
            }
        }

        @Override
        protected String renderArtKey() {
            // sub_67B14 switches mappings to Map_EggRoboHead and queues
            // ArtKosM_EggRoboHead at ArtTile_RobotnikShip for Knuckles.
            return eggRoboRoute
                    ? Sonic3kObjectArtKeys.EGG_ROBO_HEAD
                    : Sonic3kObjectArtKeys.ROBOTNIK_SHIP;
        }

        public boolean usesEggRoboMappingForTest() { return eggRoboRoute; }
        public int rawCursorForTest() { return rawCursor; }
        public int animationTimerForTest() { return animationTimer; }
    }

    /** loc_749D0 / ObjDat3_750CE; owns ChildObjDat_75144. */
    public static final class ArmControllerChild extends BossChild {
        private int angle;
        private boolean controllerInitialized;
        private boolean collisionActivated;

        private ArmControllerChild(LbzFinalBoss2Instance boss, int dx, int dy) {
            super(boss, ChildKind.ARM_GRAPH, "LBZFinalBoss2ArmController", dx, dy, 0x180);
            this.mappingFrame = 2;
        }

        private ArmControllerChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ARM_GRAPH, "LBZFinalBoss2ArmController", 0x180);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            if (updateFlickerMove()) {
                return;
            }
            if (!controllerInitialized) {
                // loc_749EC overwrites ChildObjDat_75122's +$24 Y offset only
                // when the controller gets its first own slot dispatch.
                controllerInitialized = true;
                dy = -6;
                boss.spawnNestedArmGraph();
                enterFlickerMoveIfDefeated(0);
                return;
            }
            if (!collisionActivated) {
                if (boss.artTileHigh) {
                    // loc_74A14 changes the routine to $04 after publishing $AD;
                    // the activation entry still executes loc_74A14's circular
                    // lookup with the old angle. loc_74A3E starts next entry.
                    collisionActivated = true;
                    collisionFlags = ARM_COLLISION;
                    mappingFrame = 2;
                    artTileHigh = true;
                }
                refreshCircularPosition();
                enterFlickerMoveIfDefeated(0);
                return;
            }
            if ((boss.flags & FLAG_FLOOR) != 0) {
                // loc_74A3E returns before angle, flip, and position work.
                enterFlickerMoveIfDefeated(0);
                return;
            }
            // loc_74A3E changes $3C only while neither the floor nor falling
            // flags are set. It compares the controller y to Player_1 y and
            // clamps the native signed angle to [-$30, 0].
            if ((boss.flags & (FLAG_FLOOR | FLAG_ARM_FALLING)) == 0 && player != null) {
                int yDelta = (getY() - player.getCentreY());
                if (yDelta < -2) {
                    angle = Math.min(0, (byte) angle + 1) & 0xFF;
                } else if (yDelta > 2) {
                    angle = Math.max(-0x30, (byte) angle - 1) & 0xFF;
                }
            }
            hFlip = boss.renderXFlip;
            refreshCircularPosition();
            enterFlickerMoveIfDefeated(0);
        }

        private void refreshCircularPosition() {
            int[] offset = boss.circularOffset(angle, false, hFlip);
            currentX = (boss.getCentreX() + (hFlip ? -dx : dx) + offset[0]) & 0xFFFF;
            currentY = (boss.getCentreY() + dy + offset[1]) & 0xFFFF;
            updateDynamicSpawn(getX(), getY());
        }
    }

    /** loc_749AE visual attachment / ObjDat3_750C2. */
    public static final class ArmAttachmentChild extends BossChild {
        private ArmAttachmentChild(LbzFinalBoss2Instance boss, int dx, int dy,
                                   int frame, int priority, int palette) {
            super(boss, ChildKind.ARM_ATTACHMENT, "LBZFinalBoss2ArmAttachment", dx, dy, priority);
            mappingFrame = frame;
            paletteOverride = palette;
        }

        private ArmAttachmentChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ARM_ATTACHMENT, "LBZFinalBoss2ArmAttachment", 0x200);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            refreshPosition();
            hFlip = boss.renderXFlip;
            if (boss.artTileHigh) {
                artTileHigh = true;
            }
            if ((boss.flags & FLAG_DEFEAT_DEBRIS) != 0) {
                // Child_Draw_Sprite2 refreshes first, then Go_Delete_Sprite_2
                // installs the next-entry delete callback without drawing.
                schedulePendingDelete(PendingDelete.GO_DELETE_2);
            }
        }
    }

    /** loc_74B9E visual joint / ObjDat3_750E6. */
    public static final class ArmVisualJointChild extends BossChild {
        private ArmVisualJointChild(LbzFinalBoss2Instance boss, int dx, int dy,
                                    int frame, int priority, int palette) {
            super(boss, ChildKind.ARM_VISUAL, "LBZFinalBoss2ArmVisualJoint", dx, dy, priority);
            mappingFrame = frame;
            paletteOverride = palette;
        }

        private ArmVisualJointChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ARM_VISUAL, "LBZFinalBoss2ArmVisualJoint", 0x300);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            refreshPosition();
            hFlip = boss.renderXFlip;
            if (boss.artTileHigh) {
                artTileHigh = true;
            }
            if ((boss.flags & FLAG_DEFEAT_DEBRIS) != 0) {
                schedulePendingDelete(PendingDelete.GO_DELETE_2);
            }
        }
    }

    /** loc_74BC0 / ObjDat3_750F2: palette line 0 collision visual. */
    public static final class ArmOuterCollisionChild extends BossChild {
        private boolean visibleThisEntry;
        private ArmOuterCollisionChild(LbzFinalBoss2Instance boss, int dx, int dy) {
            super(boss, ChildKind.ARM_OUTER_COLLISION, "LBZFinalBoss2OuterCollision", dx, dy, 0x300);
            mappingFrame = 0x0C;
            paletteOverride = 0;
        }

        private ArmOuterCollisionChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ARM_OUTER_COLLISION, "LBZFinalBoss2OuterCollision", 0x300);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            visibleThisEntry = false;
            refreshPosition();
            hFlip = boss.renderXFlip;
            if (!artTileHigh && boss.artTileHigh) {
                // sub_74EBC latches both art_tile bit 7 and collision $9A,
                // then replaces the callback so neither is cleared later.
                artTileHigh = true;
                collisionFlags = OUTER_COLLISION;
            }
            if ((boss.statusBits & STATUS_DEFEATED) != 0) {
                boss.forgetChild(this);
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            visibleThisEntry = (vIntRunCount & 1) == 0;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (visibleThisEntry) {
                super.appendRenderCommands(commands);
            }
        }
    }

    /** loc_74C00 / ChildObjDat_7513C. */
    public static final class LandingCollisionChild extends BossChild {
        private LandingCollisionChild(LbzFinalBoss2Instance boss, int dx, int dy) {
            super(boss, ChildKind.ARM_UPPER_COLLISION, "LBZFinalBoss2LandingCollision", dx, dy, 0);
            mappingFrame = 0;
            paletteOverride = 0;
            artTileHigh = boss.artTileHigh;
        }

        private LandingCollisionChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ARM_UPPER_COLLISION, "LBZFinalBoss2LandingCollision", 0);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            refreshPosition();
            collisionFlags = LANDING_COLLISION;
            if ((boss.statusBits & STATUS_DEFEATED) != 0) {
                boss.forgetChild(this);
                ObjectLifetimeOps.expireDynamic(this);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // loc_74C0C is collision-list only; it never calls Draw_Sprite.
        }
    }

    /** loc_74AFA: articulated arm segment, native subtypes 0 and 1. */
    public static final class ArmSegmentChild extends BossChild {
        private ArmControllerChild controller;
        private int subtype;
        private int animationTimer;
        private int animationIndex;
        private boolean heldCallback;

        private ArmSegmentChild(LbzFinalBoss2Instance boss, ArmControllerChild controller,
                                int subtype, int dx, int dy) {
            super(boss, ChildKind.ARM_SEGMENT, "LBZFinalBoss2ArmSegment", dx, dy,
                    subtype == 0 ? 0x80 : 0x180);
            this.controller = controller;
            this.subtype = subtype;
            mappingFrame = subtype == 0 ? 4 : 8;
            paletteOverride = 1;
        }

        private ArmSegmentChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ARM_SEGMENT, "LBZFinalBoss2ArmSegment", 0x80);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            if (updateFlickerMove()) {
                return;
            }
            if (heldCallback) {
                refreshPositionFrom(controller);
                if (!boss.grabActive) {
                    heldCallback = false;
                    if (subtype != 0) {
                        dx = (byte) (dx - 8);
                    }
                }
                collisionFlags = 0;
                enterFlickerMoveIfDefeated(subtype * 2);
                return;
            }
            if (controller.artTileHigh) {
                // loc_74B36's Child_GetPriorityOnce tests the immediate
                // controller parent before the raw animation callback.
                artTileHigh = true;
            }
            refreshPositionFrom(controller);
            if (--animationTimer < 0) {
                int[] script = boss.romData().segmentAnimation(subtype);
                animationTimer = script[0];
                animationIndex = (animationIndex + 1) & 0xFF;
                int nextFrame = script[animationIndex + 1];
                if (nextFrame == 0xFC) {
                    mappingFrame = script[1];
                    animationIndex = 0;
                } else {
                    mappingFrame = nextFrame;
                }
            }
            if (boss.grabActive) {
                heldCallback = true;
                mappingFrame = subtype == 0 ? 7 : 0x0B;
                if (subtype != 0) {
                    // loc_74B36 adds eight to child_dx for subtype 1 while
                    // the player is held. The current entry already refreshed,
                    // so the adjusted byte affects the next loc_74B76 entry.
                    dx = (byte) (dx + 8);
                }
            }
            // loc_74AFA's moveq #$C feeds Child_DrawTouch's draw radius; it
            // never writes collision_flags or calls the collision-list helper.
            collisionFlags = 0;
            // CreateChild1_Normal assigns native subtypes 0 and 2 to the two
            // entries; Java's semantic subtype remains 0/1 for animation.
            enterFlickerMoveIfDefeated(subtype * 2);
        }
    }

    /** loc_74A9A / articulated joint. */
    public static final class ArmKinematicJointChild extends BossChild {
        private ArmControllerChild controller;
        private boolean jointInitialized;

        private ArmKinematicJointChild(LbzFinalBoss2Instance boss, ArmControllerChild controller,
                                       int dx, int dy) {
            super(boss, ChildKind.ARM_JOINT, "LBZFinalBoss2ArmKinematicJoint", dx, dy, 0x180);
            this.controller = controller;
            mappingFrame = 3;
            paletteOverride = 1;
        }

        private ArmKinematicJointChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ARM_JOINT, "LBZFinalBoss2ArmKinematicJoint", 0x180);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            if (updateFlickerMove()) {
                return;
            }
            if (!jointInitialized) {
                // loc_74A9A overwrites ChildObjDat_75144's zero offset on the
                // joint's first own dispatch, before the circular lookup.
                jointInitialized = true;
                dx = 0x18;
                dy = -6;
            }
            if (controller.artTileHigh) {
                // loc_74ACA latches the immediate controller parent's bit.
                artTileHigh = true;
            }
            int angle = (controller.angle + 0x14) & 0xFF;
            int[] offset = boss.circularOffset(angle, true, boss.renderXFlip);
            // loc_74A9A rewires parent3 to the root and stores the controller
            // in $44; CircularLookup therefore uses the root as its base and
            // the controller only as the angle source.
            currentX = (boss.getCentreX() + (boss.renderXFlip ? -dx : dx) + offset[0]) & 0xFFFF;
            currentY = (boss.getCentreY() + dy + offset[1]) & 0xFFFF;
            hFlip = boss.renderXFlip;
            // loc_74A9A likewise leaves collision zero; its moveq #$C is the
            // unused draw/touch helper argument, not a collision assignment.
            collisionFlags = 0;
            updateDynamicSpawn(getX(), getY());
            // The joint is ChildObjDat_75144 entry 2, native subtype 4.
            enterFlickerMoveIfDefeated(4);
        }
    }

    /** loc_74C24: player-range grab helper; root routine transitions are native. */
    public static final class GrabOwnerChild extends BossChild {
        private ArmControllerChild controller;
        private AbstractPlayableSprite grabbedPlayer;
        private int releaseCooldown;
        private boolean releaseCooldownActive;

        private GrabOwnerChild(LbzFinalBoss2Instance boss, ArmControllerChild controller, int dx, int dy) {
            super(boss, ChildKind.GRAB, "LBZFinalBoss2Grab", dx, dy, 0);
            this.controller = controller;
            mappingFrame = 4;
            // Child1 creation copies the controller art word; loc_74C24 has
            // no later Child_GetPriority call.
            artTileHigh = controller.artTileHigh;
        }

        private GrabOwnerChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.GRAB, "LBZFinalBoss2Grab", 0);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (releaseCooldownActive) {
                tickReleaseCooldown();
                return;
            }
            if (grabbedPlayer != null) {
                if (!boss.grabActive) {
                    grabbedPlayer = null;
                    releaseCooldown = GRAB_WAIT;
                    releaseCooldownActive = true;
                    tickReleaseCooldown();
                    return;
                }
                if ((boss.statusBits & STATUS_DEFEATED) != 0) {
                    // FixBugs=0: loc_74CCC takes loc_74BFA directly. The
                    // shipped path deletes the held owner without clearing
                    // Player_1 object_control; loc_7473A owns the later clear.
                    grabbedPlayer = null;
                    boss.forgetChild(this);
                    ObjectLifetimeOps.expireDynamic(this);
                    return;
                }
                refreshPositionFrom(controller);
                NativePositionOps.writeXPosPreserveSubpixel(grabbedPlayer, getX());
                NativePositionOps.writeYPosPreserveSubpixel(grabbedPlayer, getY());
                return;
            }

            refreshPositionFrom(controller);
            if ((boss.statusBits & STATUS_DEFEATED) != 0) {
                if (player instanceof AbstractPlayableSprite sprite) {
                    ObjectControlState.none().applyTo(sprite);
                }
                boss.forgetChild(this);
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            if ((boss.flags & FLAG_BOB_NOTCH) != 0 || (boss.statusBits & STATUS_HIT_FLASH) != 0) {
                return;
            }
            if (!(player instanceof AbstractPlayableSprite sprite)
                    || sprite.getInvulnerableFrames() != 0
                    || nativePlayerRoutine(sprite) >= 6) {
                return;
            }
            int dxToPlayer = (short) (sprite.getCentreX() - getX());
            int dyToPlayer = (short) (sprite.getCentreY() - getY());
            if (dxToPlayer >= -0x10 && dxToPlayer < 0x20
                    && dyToPlayer >= -0x10 && dyToPlayer < 0x20) {
                boss.grabActive = true;
                grabbedPlayer = sprite;
                boss.routine = ROUTINE_GRAB_WAIT;
                boss.waitTimer = GRAB_WAIT;
                boss.waitTarget = WaitTarget.GRAB_RISE;
                ObjectControlState.nativeBit7FullControl().applyTo(sprite);
                sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
                sprite.setXSpeed((short) 0);
                sprite.setYSpeed((short) 0);
                sprite.setGSpeed((short) 0);
                // loc_74C8C falls straight through loc_74CCC in the same
                // child dispatch; word writes preserve both position lows.
                refreshPositionFrom(controller);
                NativePositionOps.writeXPosPreserveSubpixel(sprite, getX());
                NativePositionOps.writeYPosPreserveSubpixel(sprite, getY());
            }
        }

        private void tickReleaseCooldown() {
            releaseCooldown = (releaseCooldown - 1) & 0xFFFF;
            if ((short) releaseCooldown < 0) {
                releaseCooldownActive = false;
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // loc_74C24/loc_74CCC are invisible range/carry owners.
        }
    }

    /**
     * {@code Child6_CreateBossExplosion} subtype 4.  This must remain a real
     * later-slot object: {@code CreateBossExp04} selects
     * {@code Obj_WaitForParent}/{@code Obj_BossExpControl1}, and the signed
     * byte {@code $39=$80} takes {@code bmi} before the decrement.  It therefore
     * emits immediately and every three own entries without counting down,
     * until its parent sets {@code $38} bit 5 or is deleted.
     */
    public static final class BigArmExplosionControllerChild extends BossChild {
        private static final int COUNTER = 0x80;
        private static final int RANGE = 0x20;
        private static final int INTERVAL_RELOAD = 2;

        private EscapeExplosionEmitterChild emitterParent;
        private boolean emitterOwned;
        private boolean initialized;
        private int counter = COUNTER;
        private int intervalCounter;
        private int emissionCount;

        private BigArmExplosionControllerChild(LbzFinalBoss2Instance boss) {
            this(boss, null, false);
        }

        private BigArmExplosionControllerChild(LbzFinalBoss2Instance boss,
                                               EscapeExplosionEmitterChild emitterParent) {
            this(boss, emitterParent, true);
        }

        private BigArmExplosionControllerChild(LbzFinalBoss2Instance boss,
                                               EscapeExplosionEmitterChild emitterParent,
                                               boolean emitterOwned) {
            super(boss, ChildKind.DEFEAT_EXPLOSION_CONTROLLER,
                    "LBZFinalBoss2ExplosionController", 0, 0, 0);
            this.emitterParent = emitterParent;
            this.emitterOwned = emitterOwned;
            followParent();
        }

        private BigArmExplosionControllerChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.DEFEAT_EXPLOSION_CONTROLLER,
                    "LBZFinalBoss2ExplosionController", 0);
        }

        @Override
        public boolean usesCurrentTouchResponseState() {
            return false;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            if (parentStopped()) {
                // Obj_WaitForParent branches to Go_Delete_Sprite. The signal
                // entry installs Delete_Current_Sprite; deletion is next own
                // entry even when the parent slot has already disappeared.
                schedulePendingDelete(PendingDelete.GO_DELETE);
                return;
            }
            followParent();
            if (!initialized) {
                initialized = true;
                // Obj_CreateBossExplosion tail-jumps through Obj_WaitForParent;
                // the zero-filled $2E pre-decrements negative on this entry.
                emitExplosion();
                return;
            }
            if (--intervalCounter < 0) {
                emitExplosion();
            }
        }

        private boolean parentStopped() {
            if (emitterOwned) {
                return emitterParent == null
                        || emitterParent.isDestroyed()
                        || emitterParent.controllerStopSignal;
            }
            return boss.isDestroyed() || (boss.flags & FLAG_CAPSULE_WAIT) != 0;
        }

        private void followParent() {
            if (emitterOwned && emitterParent != null) {
                currentX = emitterParent.getX();
                currentY = emitterParent.getY();
            } else {
                currentX = boss.getCentreX();
                currentY = boss.getCentreY();
            }
            updateDynamicSpawn(getX(), getY());
        }

        private void emitExplosion() {
            // `$80` is negative as a byte. Obj_BossExpControl1 branches before
            // subq.b, so the shipped controller deliberately never changes it.
            counter = COUNTER;
            intervalCounter = INTERVAL_RELOAD;
            // sub_83E84 performs CreateChild6_Simple before Random_Number. A
            // full SST therefore consumes neither RNG nor audio state.
            S3kBossExplosionChild explosion = spawnChild(() ->
                    S3kBossExplosionChild.createWithNativeInitSfx(currentX, currentY));
            if (services().objectManager() != null
                    && (explosion.isDestroyed() || explosion.getSlotIndex() < 0)) {
                return;
            }
            int random = services().rng().nextRaw();
            int xOffset = (random & ((RANGE * 2) - 1)) - RANGE;
            // Random_Number's second coordinate follows `swap d0`, hence the
            // original high word rather than an 8-bit shift of the low word.
            int yOffset = ((random >>> 16) & ((RANGE * 2) - 1)) - RANGE;
            // CreateChild6_Simple gives Obj_BossExplosion1 no behavioral
            // parent edge. ObjectManager alone owns its independent lifetime.
            explosion.writeNativePositionWords(
                    (currentX + xOffset) & 0xFFFF,
                    (currentY + yOffset) & 0xFFFF);
            emissionCount++;
        }

        @Override
        protected void onPendingDeleteEntry() {
            boss.forgetChild(this);
            ObjectLifetimeOps.expireDynamic(this);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // Obj_CreateBossExplosion is an invisible wait/control owner.
        }

        public int counterForTest() { return counter; }
        public int intervalCounterForTest() { return intervalCounter; }
        public int emissionCountForTest() { return emissionCount; }
    }

    /** loc_74D14 / ChildObjDat_7515E. */
    public static final class DefeatDebrisChild extends BossChild {
        private int subtype;
        private int xVel;
        private int yVel;
        private int xSub;
        private int ySub;
        private boolean initialized;

        private DefeatDebrisChild(LbzFinalBoss2Instance boss, int dx, int dy, int subtype) {
            super(boss, ChildKind.DEFEAT_DEBRIS, "LBZFinalBoss2DefeatDebris", dx, dy, 0x100);
            this.subtype = subtype;
            paletteOverride = 1;
            artTileHigh = true;
        }

        private DefeatDebrisChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.DEFEAT_DEBRIS, "LBZFinalBoss2DefeatDebris", 0x100);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            if (!initialized) {
                // loc_74D14 performs adjusted refresh, latches the child flip,
                // selects/reflects indexed velocity, installs Obj_FlickerMove,
                // and draws once. Movement begins on the next own entry.
                hFlip = boss.renderXFlip;
                refreshPosition();
                mappingFrame = 0x0D + (subtype >> 1);
                int[] velocity = boss.romData().indexedVelocity(subtype);
                xVel = hFlip ? -velocity[0] : velocity[0];
                yVel = velocity[1];
                flickerMove = true;
                flickerVisible = true;
                initialized = true;
                return;
            }
            int nextX = ((currentX & 0xFFFF) << 16) | (xSub & 0xFFFF);
            int nextY = ((currentY & 0xFFFF) << 16) | (ySub & 0xFFFF);
            nextX = S3kBossFlickerMove.integrate(nextX, ((short) xVel) << 8);
            nextY = S3kBossFlickerMove.integrate(nextY, ((short) yVel) << 8);
            currentX = (nextX >>> 16) & 0xFFFF;
            currentY = (nextY >>> 16) & 0xFFFF;
            xSub = nextX & 0xFFFF;
            ySub = nextY & 0xFFFF;
            yVel = (short) (yVel + 0x38);
            updateDynamicSpawn(getX(), getY());
            if (S3kBossFlickerMove.isOutsideNativeBounds(
                    currentX, currentY, boss.cameraX(), boss.cameraY())) {
                schedulePendingDelete(PendingDelete.GO_DELETE_3);
                return;
            }
            flickerVisible = !flickerVisible;
        }

        public int subtypeForTest() { return subtype; }
        public int xVelocityForTest() { return (short) xVel; }
        public int yVelocityForTest() { return (short) yVel; }
    }

    /** loc_74E12 / ChildObjDat_75186: the FinalBoss1 visual retained under the ship. */
    public static final class DefeatFollowVisualChild extends BossChild {
        private boolean initialized;
        private boolean visibleThisEntry;

        private DefeatFollowVisualChild(LbzFinalBoss2Instance boss, int dx, int dy) {
            super(boss, ChildKind.DEFEAT_FOLLOW_VISUAL,
                    "LBZFinalBoss2DefeatFollowVisual", dx, dy, 0x200);
            mappingFrame = 0x15;
            paletteOverride = 1;
            artTileHigh = true;
        }

        private DefeatFollowVisualChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.DEFEAT_FOLLOW_VISUAL,
                    "LBZFinalBoss2DefeatFollowVisual", 0x200);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            visibleThisEntry = false;
            if (deleteOnPendingEntry()) {
                return;
            }
            if (!initialized) {
                initialized = true;
                return;
            }
            // Refresh_ChildPosition is unadjusted: it does not mirror dx or
            // inherit the root's render flip.
            currentX = (boss.getCentreX() + dx) & 0xFFFF;
            currentY = (boss.getCentreY() + dy) & 0xFFFF;
            hFlip = false;
            updateDynamicSpawn(getX(), getY());
            if ((boss.flags & FLAG_DEFEAT_DEBRIS) != 0) {
                schedulePendingDelete(PendingDelete.GO_DELETE_2);
                return;
            }
            visibleThisEntry = true;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (visibleThisEntry) {
                super.appendRenderCommands(commands);
            }
        }

        @Override
        protected String renderArtKey() {
            return Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_1;
        }
    }

    /** Obj_RobotnikShipFlame / Child1_MakeRoboShipFlame. */
    public static final class RobotnikShipFlameChild extends BossChild {
        private boolean initialized;
        private boolean visibleThisEntry;

        private RobotnikShipFlameChild(LbzFinalBoss2Instance boss) {
            super(boss, ChildKind.ESCAPE_FLAME,
                    "LBZFinalBoss2RobotnikShipFlame", 0x1E, 0, 0x280);
            mappingFrame = 6;
            // Child1_MakeRoboShipFlame has no art word of its own, so the
            // CreateChild1_Normal copy is retained for the flame lifetime.
            artTileHigh = boss.artTileHigh;
        }

        private RobotnikShipFlameChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ESCAPE_FLAME,
                    "LBZFinalBoss2RobotnikShipFlame", 0x280);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            visibleThisEntry = false;
            if (!initialized) {
                initialized = true;
                return;
            }
            // The shipped routine tests parent $38 bit 4 before refreshing.
            // Big Arm already set it at loc_746D8, so this deliberately
            // preserves the ROM's short-lived/no-visible flame behavior.
            if ((boss.flags & FLAG_DEFEAT_DEBRIS) != 0) {
                boss.forgetChild(this);
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            refreshPosition();
            visibleThisEntry = (vIntRunCount & 1) == 0 && (short) boss.xVel != 0;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (visibleThisEntry) {
                super.appendRenderCommands(commands);
            }
        }

        @Override
        protected String renderArtKey() {
            return Sonic3kObjectArtKeys.ROBOTNIK_SHIP;
        }
    }

    /** loc_74D48-loc_74E0C: the falling FinalBoss1 floor and emission owner. */
    public static final class EscapeFloorChild extends BossChild {
        private enum FloorStage { INIT, FALL, EMIT }

        private FloorStage stage = FloorStage.INIT;
        private int xSub;
        private int ySub;
        private int yVel;
        private int emitterCounter;
        private boolean emitterRoutineDispatched;
        private final List<EscapeFloorExplosionChild> explosions = new ArrayList<>(7);
        private final List<EscapeExplosionEmitterChild> emitters = new ArrayList<>();

        private EscapeFloorChild(LbzFinalBoss2Instance boss, int dx, int dy) {
            super(boss, ChildKind.ESCAPE_FLOOR,
                    "LBZFinalBoss2EscapeFloor", dx, dy, 0x300);
            mappingFrame = 0x16;
            paletteOverride = 1;
            artTileHigh = true;
        }

        private EscapeFloorChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ESCAPE_FLOOR,
                    "LBZFinalBoss2EscapeFloor", 0x300);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (stage) {
                case INIT -> stage = FloorStage.FALL;
                case FALL -> updateFall();
                case EMIT -> updateEmitter(vIntRunCount);
            }
            updateDynamicSpawn(getX(), getY());
        }

        private void updateFall() {
            int nextX = ((currentX & 0xFFFF) << 16) | (xSub & 0xFFFF);
            int nextY = ((currentY & 0xFFFF) << 16) | (ySub & 0xFFFF);
            nextY += ((short) yVel) << 8;
            currentX = (nextX >>> 16) & 0xFFFF;
            currentY = (nextY >>> 16) & 0xFFFF;
            xSub = nextX & 0xFFFF;
            ySub = nextY & 0xFFFF;
            yVel = (short) (yVel + 0x38);
            if ((short) yVel < 0) {
                return;
            }
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, 0x10);
            if (!floor.hasCollision()) {
                return;
            }
            currentY = (currentY + floor.distance()) & 0xFFFF;
            if ((short) yVel >= 0x100) {
                yVel = (short) -((short) yVel >> 1);
                return;
            }
            settle();
        }

        private void settle() {
            stage = FloorStage.EMIT;
            emitterCounter = 0x7F;
            boss.flags |= FLAG_BOB_NOTCH;
            for (int i = 0; i < 7; i++) {
                int subtype = i * 2;
                int[] offset = boss.romData().childOffset(
                        Sonic3kConstants.BOSS_EXPLOSION_HITBOX_CHILD_TABLE_ADDR, i);
                EscapeFloorExplosionChild explosion = boss.recordChild(
                        ChildKind.ESCAPE_FLOOR_EXPLOSION,
                        spawnChild(() -> new EscapeFloorExplosionChild(
                                boss, this, offset[0], offset[1], subtype)));
                if (explosion == null) {
                    // CreateChild1_Normal stops this seven-entry table on the
                    // first failed SST allocation, but loc_74DDC continues to
                    // the camera and level-size writes below.
                    break;
                }
                explosions.add(explosion);
            }
            // loc_74DA4 writes literal stored/target bounds and allocates the
            // three gradual workers; it never snaps the current camera bounds.
            // Their rewind-visible state remains owned by the LBZ event owner.
            S3kTransitionWriteSupport.prepareLbzBigArmFloorTransition(services());
        }

        private void updateEmitter(int vIntRunCount) {
            emitterRoutineDispatched = true;
            if ((vIntRunCount & 3) != 0) {
                return;
            }
            emitterCounter = (emitterCounter - 1) & 0xFF;
            if ((byte) emitterCounter < 0) {
                for (EscapeExplosionEmitterChild emitter : List.copyOf(emitters)) {
                    emitter.detachFloorAfterOwnerDeletion();
                }
                boss.forgetChild(this);
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            EscapeExplosionEmitterChild emitter = boss.recordChild(
                    ChildKind.ESCAPE_EXPLOSION_EMITTER,
                    spawnChild(() -> new EscapeExplosionEmitterChild(boss, this)));
            if (emitter != null) {
                emitters.add(emitter);
            }
        }

        private void forgetExplosion(EscapeFloorExplosionChild explosion) {
            explosions.remove(explosion);
            boss.forgetChild(explosion);
        }

        private void forgetEmitter(EscapeExplosionEmitterChild emitter) {
            emitters.remove(emitter);
            boss.forgetChild(emitter);
        }

        @Override
        protected String renderArtKey() {
            return Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_1;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            super.appendRenderCommands(commands);
        }

        public int emitterCounterForTest() { return emitterCounter; }
        public boolean isSettledForTest() { return stage == FloorStage.EMIT; }
        public int yVelocityForTest() { return (short) yVel; }
    }

    /** BossExplosionHitbox instances allocated by ChildObjDat_690D8. */
    public static final class EscapeFloorExplosionChild extends BossChild {
        private EscapeFloorChild floor;
        private int subtype;
        private boolean initialized;
        private boolean animating;
        private int waitTimer;
        private int rawCursor;
        private int rawTimer;

        private EscapeFloorExplosionChild(LbzFinalBoss2Instance boss, EscapeFloorChild floor,
                                          int dx, int dy, int subtype) {
            super(boss, ChildKind.ESCAPE_FLOOR_EXPLOSION,
                    "LBZFinalBoss2FloorExplosion", dx, dy, 0x80);
            this.floor = floor;
            this.subtype = subtype;
            currentX = (floor.getX() + dx) & 0xFFFF;
            currentY = (floor.getY() + dy) & 0xFFFF;
            collisionFlags = 0;
        }

        private EscapeFloorExplosionChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ESCAPE_FLOOR_EXPLOSION,
                    "LBZFinalBoss2FloorExplosion", 0x80);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            if (!initialized) {
                initialized = true;
                // BossChild_SetSubtypeDelay: (12-evenSubtype)*2.
                waitTimer = (0x0C - subtype) * 2;
                return;
            }
            if (!animating) {
                if (--waitTimer >= 0) {
                    return;
                }
                // BossExplosionHitbox_StartAnim changes only routine/callback.
                // Animate_RawMultiDelay begins on the following own entry.
                animating = true;
                return;
            }
            rawTimer = (rawTimer - 1) & 0xFF;
            if ((byte) rawTimer >= 0) {
                return;
            }
            rawCursor = (rawCursor + 2) & 0xFF;
            int[] script = boss.romData().bossExplosionAnimation();
            int nextFrame = script[rawCursor];
            if ((byte) nextFrame < 0) {
                rawCursor = 0;
                rawTimer = 0;
                // AnimateRaw_CustomCode's $F4 calls Go_Delete_Sprite. The old
                // mapping remains drawable/touchable on this terminal entry.
                schedulePendingDelete(PendingDelete.GO_DELETE);
                return;
            }
            mappingFrame = nextFrame;
            rawTimer = script[rawCursor + 1];
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed() || !animating
                    || (rawCursor == 0 && !isPendingDeleteForTest())
                    || services().renderManager() == null) {
                return;
            }
            PatternSpriteRenderer renderer = services().renderManager().getBossExplosionRenderer();
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
            }
        }

        @Override
        protected void onPendingDeleteEntry() {
            if (floor != null) {
                floor.forgetExplosion(this);
            } else {
                boss.forgetChild(this);
            }
            ObjectLifetimeOps.expireDynamic(this);
        }

        public int subtypeForTest() { return subtype; }
        public boolean isAnimatingForTest() { return animating; }
        public int rawCursorForTest() { return rawCursor; }
        public int rawTimerForTest() { return rawTimer; }
    }

    /** loc_74E30: one absolute-position explosion owner with a $60 wait. */
    public static final class EscapeExplosionEmitterChild extends BossChild {
        private EscapeFloorChild floor;
        private boolean initialized;
        private int waitTimer;
        private int positionIndex;
        private boolean controllerStopSignal;
        private BigArmExplosionControllerChild explosionController;

        private EscapeExplosionEmitterChild(LbzFinalBoss2Instance boss, EscapeFloorChild floor) {
            super(boss, ChildKind.ESCAPE_EXPLOSION_EMITTER,
                    "LBZFinalBoss2ExplosionEmitter", 0, 0, 0);
            this.floor = floor;
        }

        private EscapeExplosionEmitterChild(ObjectSpawn spawn) {
            super(spawn, ChildKind.ESCAPE_EXPLOSION_EMITTER,
                    "LBZFinalBoss2ExplosionEmitter", 0);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (deleteOnPendingEntry()) {
                return;
            }
            if (!initialized) {
                initialized = true;
                positionIndex = (boss.randomWord() & 0x3C) >>> 2;
                int[] position = boss.romData().escapeExplosionPosition(positionIndex);
                currentX = position[0];
                currentY = position[1];
                waitTimer = 0x60;
                explosionController = boss.recordChild(
                        ChildKind.DEFEAT_EXPLOSION_CONTROLLER,
                        spawnChild(() -> new BigArmExplosionControllerChild(boss, this)));
                updateDynamicSpawn(getX(), getY());
                return;
            }
            if (--waitTimer < 0) {
                // loc_74E70 sets parent $38 bit 5 before Go_Delete_Sprite; the
                // later controller slot observes it on this same object pass.
                controllerStopSignal = true;
                schedulePendingDelete(PendingDelete.GO_DELETE);
            }
        }

        @Override
        protected void onPendingDeleteEntry() {
            if (floor != null) {
                floor.forgetEmitter(this);
            } else {
                boss.forgetChild(this);
            }
            ObjectLifetimeOps.expireDynamic(this);
        }

        private void detachFloorAfterOwnerDeletion() {
            floor = null;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // Native loc_74E30 is an invisible wait owner.
        }

        public int positionIndexForTest() { return positionIndex; }
        public boolean controllerStopSignalForTest() { return controllerStopSignal; }
        public BigArmExplosionControllerChild explosionControllerForTest() {
            return explosionController;
        }
    }
}
