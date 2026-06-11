package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.level.resources.CompressionType;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * LBZ2 Robotnik ship + hanging laser turret boss.
 *
 * <p>ROM: {@code Obj_LBZFinalBoss1} at {@code sonic3k.asm:151927}. This class
 * intentionally owns only the Robotnik ship/turret phase and semantic finale
 * hooks; the foreground launch objects and Big Arm branch are separate tasks.
 */
public final class LbzFinalBoss1Instance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {
    private static final Logger LOG = Logger.getLogger(LbzFinalBoss1Instance.class.getName());

    private static final String PALETTE_OWNER = "s3k.lbz.finalBoss1";
    private static final int OBJECT_ID = 0xCA;
    private static final int ROUTINE_WAIT = 0x02;
    private static final int ROUTINE_SHUTTLE = 0x04;
    private static final int ROUTINE_HOLD = 0x06;
    private static final int ROUTINE_RECOIL_WAIT = 0x08;
    private static final int ROUTINE_RECOIL_DROP = 0x0A;
    private static final int INITIAL_HP = 9;
    private static final int COLLISION_FLAGS = 0x0F;
    private static final int BODY_FRAME = 0x0C;
    private static final int DEFEAT_FRAME = 5;
    private static final int ACTIVATION_TIMER = 0x7F;
    private static final int HIT_INVULNERABILITY = 0x20;
    private static final int HIT_RECOIL_WAIT = 0x0F;
    private static final int HIT_RECOIL_DROP_FRAMES = 4;
    private static final int SINK_TIMER = 0x3F;
    private static final int POST_RESULTS_DELAY = 0x1F;
    private static final int FLAG_DETACH_TOP = 1 << 1;
    private static final int FLAG_DETACH_BOTTOM = 1 << 2;
    private static final int FLAG_LASER_FIRING_NOTCH = 1 << 3;
    private static final int STATUS_HIT_SHIFT = 1 << 5;
    private static final int STATUS_HIT_FLASH = 1 << 6;
    private static final int STATUS_DEFEATED = 1 << 7;
    private static final int[] FLASH_COLOR_INDICES = {4, 14};
    private static final int[] FLASH_NORMAL_WORDS = {0x0026, 0x0020};
    private static final int[] FLASH_WHITE_WORDS = {0x0EEE, 0x0EEE};

    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private int routine = ROUTINE_WAIT;
    private int hp = INITIAL_HP;
    private int collisionFlags = COLLISION_FLAGS;
    private int collisionBackup = COLLISION_FLAGS;
    private int mappingFrame = BODY_FRAME;
    private int activationTimer = ACTIVATION_TIMER;
    private int flags;
    private int statusBits;
    private int savedRoutine;
    private int invulnerabilityTimer;
    private int recoilTimer;
    private int recoilDropTimer;
    private int detachCount = 1;
    private boolean renderXFlip;
    private boolean initialized;
    private boolean bossMusicStarted;
    private boolean finalBossPaletteLoaded;
    private boolean resultsComplete;
    private boolean launchMilestoneA;
    private boolean launchMilestoneB;
    private boolean playersReadyForResults;
    private boolean knucklesBigArmStubbed;
    private boolean engineFlamesSpawned;
    private boolean bossExplosionPlcQueued;
    private boolean deathEggSmallArtQueued;
    private boolean cutsceneAnchorRegistered;
    private FinalePhase finalePhase = FinalePhase.NONE;
    private int finaleTimer = -1;
    private final List<SpawnRecord> spawned = new ArrayList<>();

    public enum ChildKind {
        ROBOTNIK_HEAD,
        TOP_ATTACHMENT,
        TURRET_SEGMENT,
        LASER_HEAD,
        MUZZLE_LASER,
        LASER_TRAIL,
        ORBITING_POD,
        GUN_POD,
        HIT_SPARK,
        DEBRIS,
        BOSS_EXPLOSION,
        RESULTS_SCREEN,
        P2_ENDING_POSE_WATCHER,
        TAILS_CPU_RELEASE,
        ENGINE_FLAME,
        EXPLOSION_SEQUENCER,
        DEATH_EGG_MINIATURE,
        DEATH_EGG_SMOKE
    }

    public enum FinalePhase {
        NONE,
        SINK,
        WAIT_PLAYER_READY,
        WAIT_RESULTS_COMPLETE,
        POST_RESULTS_DELAY,
        AUTOWALK,
        WAIT_LAUNCH_MILESTONE_A,
        LOOK_UP,
        WAIT_LAUNCH_MILESTONE_B,
        FINAL_FALL,
        KNUCKLES_STUB
    }

    public LbzFinalBoss1Instance(ObjectSpawn spawn) {
        super(spawn, "LBZFinalBoss1");
        this.x = spawn.x() & 0xFFFF;
        this.y = spawn.y() & 0xFFFF;
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

    public void setCentreX(int x) {
        this.x = x & 0xFFFF;
        this.xSub = 0;
        updateDynamicSpawn(getX(), getY());
    }

    public void setCentreY(int y) {
        this.y = y & 0xFFFF;
        this.ySub = 0;
        updateDynamicSpawn(getX(), getY());
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getCollisionFlags() {
        return collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        return hp;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (hp <= 0 || invulnerabilityTimer > 0 || collisionFlags == 0) {
            return;
        }
        hp = Math.max(0, hp - 1);
        collisionFlags = 0;
        if (hp == 0) {
            startDefeat();
            return;
        }
        startHitReaction();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        boolean wasInitialized = initialized;
        ensureInitialized();
        if (!wasInitialized) {
            updateDynamicSpawn(getX(), getY());
            return;
        }
        if (finalePhase != FinalePhase.NONE) {
            updateFinale(player);
            updateDynamicSpawn(getX(), getY());
            return;
        }
        updateHitFlash();
        switch (routine) {
            case ROUTINE_WAIT -> updateActivationWait();
            case ROUTINE_SHUTTLE -> updateVerticalShuttle();
            case ROUTINE_HOLD -> {
                // Static hit-stun hold.
            }
            case ROUTINE_RECOIL_WAIT -> updateRecoilWait();
            case ROUTINE_RECOIL_DROP -> updateRecoilDrop();
            default -> {
                // Keep unknown test-forced routines inert instead of inventing behavior.
            }
        }
        updateDynamicSpawn(getX(), getY());
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        collisionFlags = COLLISION_FLAGS;
        collisionBackup = COLLISION_FLAGS;
        hp = INITIAL_HP;
        mappingFrame = BODY_FRAME;
        if (currentPlayerCharacter() != PlayerCharacter.KNUCKLES) {
            activationTimer = ACTIVATION_TIMER;
            if (!bossMusicStarted) {
                services().playMusic(Sonic3kMusic.BOSS.id);
                bossMusicStarted = true;
            }
        }
        spawnInitialChildren();
    }

    private void spawnInitialChildren() {
        recordChild(ChildKind.ROBOTNIK_HEAD, spawnChild(() -> new RobotnikHeadChild(this)));
        recordChild(ChildKind.TOP_ATTACHMENT, spawnChild(() -> new TopAttachmentChild(this)));
        recordChild(ChildKind.TURRET_SEGMENT, spawnChild(() -> new TurretSegmentChild(this, 0, 0, 8)));
        recordChild(ChildKind.TURRET_SEGMENT, spawnChild(() -> new TurretSegmentChild(this, 1, 0, 0x30)));
        recordChild(ChildKind.TURRET_SEGMENT, spawnChild(() -> new TurretSegmentChild(this, 2, 0, 0x5C)));
        recordChild(ChildKind.ORBITING_POD, spawnChild(() -> new OrbitingPodChild(this, 0)));
    }

    private void updateActivationWait() {
        if (currentPlayerCharacter() == PlayerCharacter.KNUCKLES) {
            return;
        }
        activationTimer--;
        if (activationTimer < 0) {
            activate();
        }
    }

    private void activate() {
        routine = ROUTINE_SHUTTLE;
        yVel = -0x100;
        loadFinalBossPalette();
    }

    private void updateVerticalShuttle() {
        int cameraY = cameraY();
        boolean atLimit;
        if (yVel >= 0) {
            atLimit = unsignedCompare(getCentreY(), (cameraY + 0x118) & 0xFFFF) >= 0;
        } else {
            atLimit = unsignedCompare(getCentreY(), (cameraY - 0xB0) & 0xFFFF) <= 0;
        }
        if (!atLimit) {
            moveSprite2();
            return;
        }
        if (hp > 2 && !isLaserFiringNotchSet()) {
            return;
        }
        repositionForNextPass();
    }

    private void repositionForNextPass() {
        int random = services().rng().nextRaw();
        boolean leftSide = (random & 1) != 0;
        renderXFlip = leftSide;
        int cameraX = cameraX();
        setCentreX(cameraX + (leftSide ? 0x30 : 0x110));
        yVel = yVel < 0 ? 0x100 : -0x100;
    }

    private void startHitReaction() {
        savedRoutine = routine;
        routine = ROUTINE_HOLD;
        invulnerabilityTimer = HIT_INVULNERABILITY;
        statusBits |= STATUS_HIT_FLASH;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        yVel = Math.max(-0x800, Math.min(0x800, yVel * 2));
        if (hp == 5 || hp == 1) {
            if (hp == 1) {
                flags |= FLAG_LASER_FIRING_NOTCH;
            }
            int bit = detachCount == 1 ? FLAG_DETACH_TOP : FLAG_DETACH_BOTTOM;
            flags |= bit;
            detachCount++;
            routine = ROUTINE_RECOIL_WAIT;
            recoilTimer = HIT_RECOIL_WAIT;
            statusBits |= STATUS_HIT_SHIFT;
            spawnHitSparkPair();
        }
    }

    private void updateHitFlash() {
        if (invulnerabilityTimer <= 0) {
            return;
        }
        boolean white = (invulnerabilityTimer & 1) == 0;
        S3kPaletteWriteSupport.applyColors(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                PALETTE_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                2,
                FLASH_COLOR_INDICES,
                white ? FLASH_WHITE_WORDS : FLASH_NORMAL_WORDS);
        invulnerabilityTimer--;
        if (invulnerabilityTimer == 0) {
            S3kPaletteWriteSupport.applyColors(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    PALETTE_OWNER,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    2,
                    new int[] {1},
                    new int[] {0x0EEE});
            collisionFlags = collisionBackup;
            statusBits &= ~STATUS_HIT_FLASH;
            routine = savedRoutine;
        }
    }

    private void updateRecoilWait() {
        recoilTimer--;
        if (recoilTimer < 0) {
            routine = ROUTINE_RECOIL_DROP;
            recoilDropTimer = HIT_RECOIL_DROP_FRAMES;
            updateRecoilDrop();
        }
    }

    private void updateRecoilDrop() {
        if (recoilDropTimer <= 0) {
            routine = ROUTINE_HOLD;
            return;
        }
        setCentreY(getCentreY() + 8);
        recoilDropTimer--;
        if (recoilDropTimer == 0) {
            routine = ROUTINE_HOLD;
        }
    }

    private void spawnHitSparkPair() {
        recordChild(ChildKind.HIT_SPARK, spawnChild(() -> new HitSparkChild(this, -0x10, 0, false)));
        recordChild(ChildKind.HIT_SPARK, spawnChild(() -> new HitSparkChild(this, 0x10, 0, true)));
    }

    private void startDefeat() {
        mappingFrame = DEFEAT_FRAME;
        statusBits |= STATUS_HIT_FLASH | STATUS_DEFEATED;
        collisionFlags = 0;
        flags |= detachCount == 1 ? FLAG_DETACH_TOP : FLAG_DETACH_BOTTOM;
        recordChild(ChildKind.BOSS_EXPLOSION, spawnChild(() -> new S3kBossExplosionChild(getCentreX(), getCentreY())));
        if (currentPlayerCharacter() == PlayerCharacter.KNUCKLES) {
            finalePhase = FinalePhase.KNUCKLES_STUB;
            knucklesBigArmStubbed = true;
            LOG.info("Obj_LBZFinalBoss1 Knuckles defeat branch reached; Obj_LBZFinalBoss2/Big Arm is stubbed for Task D.");
        } else {
            finalePhase = FinalePhase.SINK;
        }
    }

    private void updateFinale(PlayableEntity player) {
        switch (finalePhase) {
            case SINK -> updateSonicSink(player);
            case WAIT_PLAYER_READY -> updateWaitPlayerReady(player);
            case WAIT_RESULTS_COMPLETE -> updateWaitResultsComplete();
            case POST_RESULTS_DELAY -> updatePostResultsDelay(player);
            case AUTOWALK -> updateAutoWalk(player);
            case WAIT_LAUNCH_MILESTONE_A -> updateWaitLaunchMilestoneA(player);
            case LOOK_UP -> finalePhase = FinalePhase.WAIT_LAUNCH_MILESTONE_B;
            case WAIT_LAUNCH_MILESTONE_B -> updateWaitLaunchMilestoneB();
            case FINAL_FALL -> updateFinalFall(player);
            case KNUCKLES_STUB, NONE -> {
                // Stubbed/idle.
            }
        }
    }

    private void updateSonicSink(PlayableEntity player) {
        int limit = (cameraY() + 0x140) & 0xFFFF;
        if (unsignedCompare(getCentreY(), limit) < 0) {
            setCentreY(getCentreY() + 1);
            return;
        }
        finalePhase = FinalePhase.WAIT_PLAYER_READY;
        if (finaleTimer < 0) {
            finaleTimer = SINK_TIMER;
        }
        if (finaleTimer <= 0) {
            updateWaitPlayerReady(player);
        }
    }

    private void updateWaitPlayerReady(PlayableEntity player) {
        finaleTimer--;
        if (finaleTimer >= 0 || !isPlayerReadyForResults(player)) {
            return;
        }
        applyEndingPose(player);
        if (services().gameState() != null) {
            services().gameState().setEndOfLevelFlag(false);
        }
        spawnResultsScreen();
        recordChild(ChildKind.P2_ENDING_POSE_WATCHER, spawnFreeChild(() -> new P2EndingPoseWatcherChild(this)));
        finalePhase = FinalePhase.WAIT_RESULTS_COMPLETE;
    }

    private boolean isPlayerReadyForResults(PlayableEntity player) {
        if (playersReadyForResults) {
            return true;
        }
        return player == null || (!player.getDead() && !player.getAir());
    }

    private void applyEndingPose(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            ObjectControlState.nativeBit7FullControl().applyTo(sprite);
            sprite.setControlLocked(true);
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) 0);
            sprite.setGSpeed((short) 0);
            sprite.setForcedInputMask(0);
            sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
            sprite.setForcedAnimationId(Sonic3kAnimationIds.VICTORY);
            sprite.forceAnimationRestart();
        } else if (player != null) {
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
        }
    }

    private void spawnResultsScreen() {
        PlayerCharacter character = currentPlayerCharacter();
        recordChild(ChildKind.RESULTS_SCREEN, spawnFreeChild(() -> new S3kResultsScreenObjectInstance(character, 1)));
    }

    private void updateWaitResultsComplete() {
        if (!resultsComplete) {
            return;
        }
        int levelMusicId = services().getCurrentLevelMusicId();
        if (levelMusicId >= 0) {
            services().playMusic(levelMusicId);
        }
        finalePhase = FinalePhase.POST_RESULTS_DELAY;
        finaleTimer = POST_RESULTS_DELAY;
    }

    private void updatePostResultsDelay(PlayableEntity player) {
        finaleTimer--;
        if (finaleTimer >= 0) {
            return;
        }
        loadBossExplosionPlcRaw();
        restorePlayerForLaunch(player);
        recordChild(ChildKind.TAILS_CPU_RELEASE, spawnFreeChild(() -> new TailsCpuReleaseChild(this)));
        queueDeathEggSmallArt();
        registerCutsceneAnchor();
        finalePhase = FinalePhase.AUTOWALK;
    }

    private void updateAutoWalk(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            spawnEngineFlamesOnce();
            finalePhase = FinalePhase.WAIT_LAUNCH_MILESTONE_A;
            return;
        }
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        sprite.setControlLocked(true);
        int targetX = cameraX() + 0xA0;
        int dx = targetX - (sprite.getCentreX() & 0xFFFF);
        if (Math.abs(dx) > 4) {
            sprite.setForcedAnimationId(Sonic3kAnimationIds.WALK);
            sprite.setAnimationId(Sonic3kAnimationIds.WALK);
            sprite.setForcedInputMask(dx > 0
                    ? AbstractPlayableSprite.INPUT_RIGHT
                    : AbstractPlayableSprite.INPUT_LEFT);
            return;
        }
        sprite.setXSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
        sprite.setAnimationId(Sonic3kAnimationIds.WAIT);
        sprite.setForcedAnimationId(Sonic3kAnimationIds.WAIT);
        spawnEngineFlamesOnce();
        finalePhase = FinalePhase.WAIT_LAUNCH_MILESTONE_A;
    }

    private void updateWaitLaunchMilestoneA(PlayableEntity player) {
        if (launchMilestoneA) {
            AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite playable
                    ? playable
                    : services().camera() == null ? null : services().camera().getFocusedSprite();
            applyLookUpPose(sprite);
            finalePhase = FinalePhase.LOOK_UP;
        }
    }

    private void updateWaitLaunchMilestoneB() {
        if (!launchMilestoneB) {
            return;
        }
        if (services().zoneRuntimeState() instanceof LbzZoneRuntimeState lbz) {
            lbz.requestFinalFall();
        }
        finalePhase = FinalePhase.FINAL_FALL;
    }

    private void updateFinalFall(PlayableEntity player) {
        int playerY = player == null ? Integer.MIN_VALUE : player.getCentreY() & 0xFFFF;
        if (playerY > ((cameraY() + 0x120) & 0xFFFF)) {
            services().requestZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 0, true);
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    private void restorePlayerForLaunch(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            ObjectControlState.nativeBit7FullControl().applyTo(sprite);
            sprite.setControlLocked(true);
            sprite.setForcedAnimationId(-1);
            sprite.setForcedInputMask(0);
        }
    }

    private void loadBossExplosionPlcRaw() {
        bossExplosionPlcQueued = true;
        if (services().renderManager() != null
                && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureBossExplosionArtLoaded();
        }
        if (!(services().currentLevel() instanceof Sonic3kLevel level)) {
            return;
        }
        try {
            byte[] raw = new ResourceLoader(services().rom()).loadSingle(LoadOp.overlay(
                    Sonic3kConstants.ART_NEM_BOSS_EXPLOSION_ADDR,
                    CompressionType.NEMESIS,
                    0));
            level.applyPatternOverlay(raw, Sonic3kConstants.ART_TILE_BOSS_EXPLOSION * 32, false);
        } catch (IOException ex) {
            LOG.fine("Unable to apply raw PLC_BossExplosion in current context: " + ex.getMessage());
        }
    }

    private void queueDeathEggSmallArt() {
        deathEggSmallArtQueued = true;
        if (services().renderManager() != null
                && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.LBZ2_DEATH_EGG_SMALL);
        }
        if (!(services().currentLevel() instanceof Sonic3kLevel level)) {
            return;
        }
        try {
            byte[] raw = new ResourceLoader(services().rom()).loadSingle(
                    LoadOp.kosinskiMBase(Sonic3kConstants.ART_KOSM_LBZ2_DEATH_EGG_SMALL_ADDR));
            level.applyPatternOverlay(raw, Sonic3kConstants.ART_TILE_LBZ2_DEATH_EGG_SMALL * 32, false);
        } catch (IOException ex) {
            LOG.fine("Unable to queue ArtKosM_LBZ2DeathEggSmall in current context: " + ex.getMessage());
        }
    }

    private void registerCutsceneAnchor() {
        cutsceneAnchorRegistered = true;
        if (services().zoneRuntimeState() instanceof LbzZoneRuntimeState lbz) {
            lbz.registerFinaleCutsceneAnchor(System.identityHashCode(this));
        }
    }

    private void applyLookUpPose(AbstractPlayableSprite sprite) {
        if (sprite == null) {
            return;
        }
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        sprite.setControlLocked(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
        sprite.setAnimationId(Sonic3kAnimationIds.LOOK_UP);
        sprite.setForcedAnimationId(Sonic3kAnimationIds.LOOK_UP);
        sprite.forceAnimationRestart();
    }

    private void spawnEngineFlamesOnce() {
        if (engineFlamesSpawned) {
            return;
        }
        engineFlamesSpawned = true;
        recordChild(ChildKind.ENGINE_FLAME, spawnFreeChild(() -> new EngineFlameChild(this, 0)));
        recordChild(ChildKind.ENGINE_FLAME, spawnFreeChild(() -> new EngineFlameChild(this, 2)));
    }

    private void signalPadCollapseStart() {
        if (services().zoneRuntimeState() instanceof LbzZoneRuntimeState lbz) {
            lbz.requestPadCollapseStart();
        }
    }

    private void signalLaunchMilestoneA() {
        launchMilestoneA = true;
    }

    private void signalLaunchMilestoneB() {
        launchMilestoneB = true;
    }

    private void spawnDeathEggMiniatures() {
        loadEndingPalette();
        for (int i = 0; i < 7; i++) {
            int index = i;
            recordChild(ChildKind.DEATH_EGG_MINIATURE,
                    spawnFreeChild(() -> new DeathEggMiniatureChild(this, index)));
        }
    }

    private void loadEndingPalette() {
        byte[] line = null;
        try {
            if (services().romReader() != null
                    && services().romReader().size() >= Sonic3kConstants.PAL_LBZ_ENDING_ADDR + 32) {
                line = services().romReader().slice(Sonic3kConstants.PAL_LBZ_ENDING_ADDR, 32);
            } else if (services().rom() != null) {
                line = services().rom().readBytes(Sonic3kConstants.PAL_LBZ_ENDING_ADDR, 32);
            }
        } catch (Exception ex) {
            LOG.fine("Unable to load Pal_LBZEnding from ROM in current context: " + ex.getMessage());
        }
        S3kPaletteWriteSupport.applyLine(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                PALETTE_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                1,
                line);
    }

    private void loadFinalBossPalette() {
        finalBossPaletteLoaded = true;
        byte[] line = null;
        try {
            if (services().romReader() != null
                    && services().romReader().size() >= Sonic3kConstants.PAL_LBZ_FINAL_BOSS_1_ADDR + 32) {
                line = services().romReader().slice(Sonic3kConstants.PAL_LBZ_FINAL_BOSS_1_ADDR, 32);
            } else if (services().rom() != null) {
                line = services().rom().readBytes(Sonic3kConstants.PAL_LBZ_FINAL_BOSS_1_ADDR, 32);
            }
        } catch (Exception ex) {
            LOG.fine("Unable to load Pal_LBZFinalBoss1 from ROM in current context: " + ex.getMessage());
        }
        S3kPaletteWriteSupport.applyLine(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                PALETTE_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                1,
                line);
    }

    private PlayerCharacter currentPlayerCharacter() {
        if (services().zoneRuntimeState() instanceof S3kZoneRuntimeState s3kState) {
            return s3kState.playerCharacter();
        }
        return PlayerCharacter.SONIC_AND_TAILS;
    }

    private int cameraX() {
        return services().camera() == null ? 0 : Short.toUnsignedInt(services().camera().getX());
    }

    private int cameraY() {
        return services().camera() == null ? 0 : Short.toUnsignedInt(services().camera().getY());
    }

    private void moveSprite2() {
        int nextX = ((getCentreX() << 8) | xSub) + xVel;
        int nextY = ((getCentreY() << 8) | ySub) + yVel;
        x = (nextX >> 8) & 0xFFFF;
        y = (nextY >> 8) & 0xFFFF;
        xSub = nextX & 0xFF;
        ySub = nextY & 0xFF;
    }

    private static int unsignedCompare(int a, int b) {
        return Integer.compare(a & 0xFFFF, b & 0xFFFF);
    }

    private void recordChild(ChildKind kind, Object child) {
        spawned.add(new SpawnRecord(kind, child));
    }

    void registerChildForTest(ChildKind kind, Object child) {
        recordChild(kind, child);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public int getPriorityBucket() {
        return 5;
    }

    public String getBodyArtKey() {
        return Sonic3kObjectArtKeys.ROBOTNIK_SHIP;
    }

    public String getTurretArtKey() {
        return Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_1;
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    public int getActivationTimer() {
        return activationTimer;
    }

    public int getRoutine() {
        return routine;
    }

    public int getYVelocity() {
        return yVel;
    }

    public int getRecoilTimer() {
        return recoilTimer;
    }

    public boolean isFinalBossPaletteLoaded() {
        return finalBossPaletteLoaded;
    }

    public boolean isLaserFiringNotchSet() {
        return (flags & FLAG_LASER_FIRING_NOTCH) != 0;
    }

    public boolean isDetachFlagSetForTest(int detachIndex) {
        return (flags & (1 << detachIndex)) != 0;
    }

    public FinalePhase getFinalePhase() {
        return finalePhase;
    }

    public boolean isKnucklesBigArmStubbedForTest() {
        return knucklesBigArmStubbed;
    }

    public boolean isBossExplosionPlcQueuedForTest() {
        return bossExplosionPlcQueued;
    }

    public boolean isDeathEggSmallArtQueuedForTest() {
        return deathEggSmallArtQueued;
    }

    public boolean isCutsceneAnchorRegisteredForTest() {
        return cutsceneAnchorRegistered;
    }

    public List<Object> childrenOfKindForTest(ChildKind kind) {
        return spawned.stream()
                .filter(record -> record.kind == kind)
                .map(SpawnRecord::child)
                .toList();
    }

    public List<String> spawnedClassNamesForTest() {
        return spawned.stream()
                .map(record -> record.child.getClass().getSimpleName())
                .toList();
    }

    public void activateForTest() {
        ensureInitialized();
        activate();
    }

    public void setRenderXFlipForTest(boolean renderXFlip) {
        this.renderXFlip = renderXFlip;
    }

    public void forceHitCountForTest(int hp) {
        this.hp = hp;
        this.collisionFlags = hp > 0 ? COLLISION_FLAGS : 0;
    }

    public void finishHitFlashForTest() {
        this.invulnerabilityTimer = 0;
        this.collisionFlags = collisionBackup;
        this.statusBits &= ~STATUS_HIT_FLASH;
        this.routine = savedRoutine;
    }

    public void forceYVelocityForTest(int yVel) {
        this.yVel = yVel;
    }

    public void setCentreYForTest(int y) {
        setCentreY(y);
    }

    public void forceFinaleTimerForTest(int timer) {
        this.finaleTimer = timer;
    }

    public void setPlayersReadyForResultsForTest(boolean ready) {
        this.playersReadyForResults = ready;
    }

    public void signalResultsCompleteForTest() {
        this.resultsComplete = true;
    }

    public void signalLaunchMilestoneAForTest() {
        this.launchMilestoneA = true;
    }

    public void signalLaunchMilestoneBForTest() {
        this.launchMilestoneB = true;
    }

    public void forceSonicFinalePhaseForTest(FinalePhase phase) {
        this.finalePhase = phase;
    }

    private record SpawnRecord(ChildKind kind, Object child) {
    }

    public abstract static class BossChild extends AbstractObjectInstance {
        protected final LbzFinalBoss1Instance boss;
        protected int x;
        protected int y;
        protected final int dx;
        protected final int dy;
        protected int mappingFrame;
        protected int collisionFlags;

        protected BossChild(LbzFinalBoss1Instance boss, String name, int dx, int dy) {
            super(new ObjectSpawn((boss.getCentreX() + dx) & 0xFFFF, (boss.getCentreY() + dy) & 0xFFFF,
                    OBJECT_ID, 0, 0, false, 0), name);
            this.boss = boss;
            this.dx = dx;
            this.dy = dy;
            refreshFromBoss();
        }

        protected void refreshFromBoss() {
            x = (boss.getCentreX() + dx) & 0xFFFF;
            y = (boss.getCentreY() + dy) & 0xFFFF;
            updateDynamicSpawn(getX(), getY());
        }

        @Override
        public int getX() {
            return x & 0xFFFF;
        }

        @Override
        public int getY() {
            return y & 0xFFFF;
        }

        public int getMappingFrame() {
            return mappingFrame;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (boss.isDestroyed() || (boss.statusBits & STATUS_DEFEATED) != 0) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            refreshFromBoss();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public int getPriorityBucket() {
            return 4;
        }
    }

    public static final class RobotnikHeadChild extends BossChild {
        private RobotnikHeadChild(LbzFinalBoss1Instance boss) {
            super(boss, "LBZFinalBoss1RobotnikHead", 0, -0x1C);
        }
    }

    public static final class TopAttachmentChild extends BossChild {
        private TopAttachmentChild(LbzFinalBoss1Instance boss) {
            super(boss, "LBZFinalBoss1TopAttachment", 0, -0x14);
            this.mappingFrame = 2;
        }
    }

    public static final class TurretSegmentChild extends BossChild implements TouchResponseProvider {
        private final int tag;
        private boolean detached;

        private TurretSegmentChild(LbzFinalBoss1Instance boss, int tag, int dx, int dy) {
            super(boss, "LBZFinalBoss1TurretSegment", dx, dy);
            this.tag = tag;
            this.mappingFrame = tag == 2 ? 1 : 0;
            this.collisionFlags = 0xAD;
            if (tag == 0) {
                boss.recordChild(ChildKind.LASER_HEAD, boss.spawnChild(() -> new LaserHeadChild(boss, this, 0)));
                boss.recordChild(ChildKind.LASER_HEAD, boss.spawnChild(() -> new LaserHeadChild(boss, this, 2)));
            } else if (tag == 2) {
                boss.recordChild(ChildKind.GUN_POD, boss.spawnChild(() -> new GunPodChild(boss, this, -0x14, 0x30)));
                boss.recordChild(ChildKind.GUN_POD, boss.spawnChild(() -> new GunPodChild(boss, this, 0x14, 0x30)));
            }
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (detached) {
                return;
            }
            super.update(frameCounter, player);
            int detachBit = 1 << tag;
            if ((boss.flags & detachBit) != 0) {
                detached = true;
                ObjectLifetimeOps.expireDynamic(this);
                spawnDebris();
            }
        }

        private void spawnDebris() {
            int count = tag == 2 ? 6 : 4;
            int[] frames = tag == 2
                    ? new int[] {0x27, 0x29, 0x28, 0x2A, 0x2B, 0x2C}
                    : new int[] {0x23, 0x25, 0x24, 0x26};
            for (int i = 0; i < count; i++) {
                int frame = frames[i];
                boss.recordChild(ChildKind.DEBRIS, boss.spawnChild(() -> new DebrisChild(boss, x, y, frame)));
            }
            boss.recordChild(ChildKind.BOSS_EXPLOSION, boss.spawnChild(() -> new S3kBossExplosionChild(x, y)));
        }

        @Override
        public int getCollisionFlags() {
            return collisionFlags;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }
    }

    public static final class LaserHeadChild extends BossChild {
        private static final int[] FRAMES = {3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 0x2D};
        private static final boolean[] FRAME_LEFT = {true, true, true, true, true, true, false, false, false, false, false, false};
        private static final int[] WAITS = {7, 0x5F, 7, 7, 7, 7, 7, 7, 7, 0x5F, 7, 0x27};
        private static final int[] X_OFFSETS = {0x27, 0x24, 0x24, 0x14, 0x0C, 0, -0x0C, -0x14, -0x24, -0x24, -0x27, -0x27};

        private final TurretSegmentChild segment;
        private int step;
        private int stepTimer;
        private boolean faceLeft;

        public LaserHeadChild(LbzFinalBoss1Instance boss, TurretSegmentChild segment, int subtype) {
            super(boss, "LBZFinalBoss1LaserHead", 0, 0);
            this.segment = segment;
            this.step = subtype == 0 ? 0 : 8;
            applyStep();
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (boss.isDestroyed() || (boss.statusBits & STATUS_DEFEATED) != 0) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            if (--stepTimer < 0) {
                advanceStep();
            }
            int anchorX = segment == null ? boss.getCentreX() : segment.getX();
            int anchorY = segment == null ? boss.getCentreY() : segment.getY();
            x = (anchorX + X_OFFSETS[step]) & 0xFFFF;
            y = (anchorY - 8) & 0xFFFF;
            updateDynamicSpawn(getX(), getY());
        }

        private void advanceStep() {
            if (boss.renderXFlip) {
                step = step == 0 ? 11 : step - 1;
            } else {
                step = (step + 1) % 12;
            }
            applyStep();
            boss.flags &= ~FLAG_LASER_FIRING_NOTCH;
            if (mappingFrame == 4) {
                boss.flags |= FLAG_LASER_FIRING_NOTCH;
                boolean directionMatches = faceLeft != boss.renderXFlip;
                if (directionMatches) {
                    int muzzleDx = faceLeft ? -8 : 8;
                    boss.recordChild(ChildKind.MUZZLE_LASER,
                            boss.spawnChild(() -> new MuzzleLaserChild(boss, this, muzzleDx, 0, faceLeft)));
                }
            }
        }

        private void applyStep() {
            mappingFrame = FRAMES[step];
            faceLeft = FRAME_LEFT[step];
            stepTimer = WAITS[step];
        }

        public void forceArcStepForTest(int step) {
            this.step = Math.floorMod(step, 12);
            applyStep();
        }

        public void forceStepTimerExpiredForTest() {
            this.stepTimer = 0;
        }
    }

    public static final class MuzzleLaserChild extends BossChild implements TouchResponseProvider {
        private final LaserHeadChild head;
        private final boolean faceLeft;
        private int chargeTimer = 8;
        private int chargeStep = 8;
        private int blinkTimer = 0x18;
        private int xVel;
        private boolean fired;

        private MuzzleLaserChild(LbzFinalBoss1Instance boss, LaserHeadChild head, int dx, int dy, boolean faceLeft) {
            super(boss, "LBZFinalBoss1MuzzleLaser", dx, dy);
            this.head = head;
            this.faceLeft = faceLeft;
            this.mappingFrame = 0x0F;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (boss.isDestroyed() || (boss.statusBits & STATUS_DEFEATED) != 0) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            if (!fired) {
                x = (head.getX() + dx) & 0xFFFF;
                y = (head.getY() + dy) & 0xFFFF;
                if (chargeTimer-- < 0) {
                    chargeTimer = chargeStep--;
                    if (chargeStep < 0 && blinkTimer-- < 0) {
                        fire();
                    }
                }
                return;
            }
            x = (x + (xVel >> 8)) & 0xFFFF;
            boss.recordChild(ChildKind.LASER_TRAIL, boss.spawnChild(() -> new LaserTrailChild(boss, x + 0x20, y)));
        }

        private void fire() {
            fired = true;
            collisionFlags = 0x9C;
            mappingFrame = 0x11;
            x += faceLeft ? 0x2C : -0x2C;
            xVel = faceLeft ? 0x800 : -0x800;
            boss.services().playSfx(Sonic3kSfx.LASER.id);
        }

        @Override
        public int getCollisionFlags() {
            return collisionFlags;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }
    }

    public static final class LaserTrailChild extends BossChild {
        private LaserTrailChild(LbzFinalBoss1Instance boss, int x, int y) {
            super(boss, "LBZFinalBoss1LaserTrail", 0, 0);
            this.x = x & 0xFFFF;
            this.y = y & 0xFFFF;
            this.mappingFrame = 0x1C;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    public static final class OrbitingPodChild extends BossChild implements TouchResponseProvider {
        private int phase;

        private OrbitingPodChild(LbzFinalBoss1Instance boss, int subtype) {
            super(boss, "LBZFinalBoss1OrbitingPod", 0, 0);
            this.phase = subtype == 0 ? 0 : 0x80;
            this.mappingFrame = 0x0C;
            this.collisionFlags = 0x97;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            phase = (phase + 1) & 0xFF;
            int angle = phase;
            int xOffset = (int) Math.round(Math.cos(angle * Math.PI / 128.0) * 24.0);
            int yOffset = -0x14 + (int) Math.round(Math.sin(angle * Math.PI / 128.0) * 24.0);
            x = (boss.getCentreX() + xOffset) & 0xFFFF;
            y = (boss.getCentreY() + yOffset) & 0xFFFF;
        }

        @Override
        public int getCollisionFlags() {
            return collisionFlags;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }
    }

    public static final class GunPodChild extends BossChild implements TouchResponseProvider {
        private static final int SHIELD_REACTION_FIRE = 1 << 4;
        private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
                TouchCategoryDecodeMode.NORMAL,
                false,
                true,
                false,
                TouchShieldDeflectCapability.NONE,
                SHIELD_REACTION_FIRE,
                TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

        private final TurretSegmentChild segment;

        private GunPodChild(LbzFinalBoss1Instance boss, TurretSegmentChild segment, int dx, int dy) {
            super(boss, "LBZFinalBoss1GunPod", dx, dy);
            this.segment = segment;
            this.mappingFrame = 9;
            this.collisionFlags = 0x89;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            x = (segment.getX() + dx) & 0xFFFF;
            y = (segment.getY() + dy) & 0xFFFF;
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
        public int getShieldReactionFlags() {
            return SHIELD_REACTION_FIRE;
        }

        @Override
        public TouchResponseProfile getTouchResponseProfile() {
            return TOUCH_RESPONSE_PROFILE;
        }

        @Override
        public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
            return TOUCH_RESPONSE_PROFILE;
        }
    }

    public static final class HitSparkChild extends BossChild {
        private HitSparkChild(LbzFinalBoss1Instance boss, int dx, int dy, boolean alternate) {
            super(boss, "LBZFinalBoss1HitSpark", dx, dy);
            this.mappingFrame = alternate ? 0x22 : 0x10;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if ((boss.statusBits & STATUS_HIT_FLASH) == 0) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            refreshFromBoss();
        }
    }

    public static final class DebrisChild extends BossChild {
        private int life = 0x60;

        private DebrisChild(LbzFinalBoss1Instance boss, int x, int y, int frame) {
            super(boss, "LBZFinalBoss1Debris", 0, 0);
            this.x = x & 0xFFFF;
            this.y = y & 0xFFFF;
            this.mappingFrame = frame;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (--life < 0) {
                ObjectLifetimeOps.expireDynamic(this);
            }
        }
    }

    private static final class P2EndingPoseWatcherChild extends BossChild {
        private P2EndingPoseWatcherChild(LbzFinalBoss1Instance boss) {
            super(boss, "LBZFinalBoss1P2EndingPoseWatcher", 0, 0);
        }
    }

    private static final class TailsCpuReleaseChild extends BossChild {
        private TailsCpuReleaseChild(LbzFinalBoss1Instance boss) {
            super(boss, "LBZFinalBoss1TailsCpuRelease", 0, 0);
        }
    }

    private abstract static class LaunchForegroundChild extends AbstractObjectInstance {
        protected final LbzFinalBoss1Instance boss;
        protected int x;
        protected int y;
        protected int xSub;
        protected int ySub;
        protected int xVel;
        protected int yVel;
        protected int mappingFrame;

        protected LaunchForegroundChild(LbzFinalBoss1Instance boss, String name, int x, int y, int subtype) {
            super(new ObjectSpawn(x, y, OBJECT_ID, subtype, 0, false, y), name);
            this.boss = boss;
            this.x = x & 0xFFFF;
            this.y = y & 0xFFFF;
        }

        @Override
        public int getX() {
            return x & 0xFFFF;
        }

        @Override
        public int getY() {
            return y & 0xFFFF;
        }

        protected void moveSprite2() {
            int nextX = ((getX() << 8) | xSub) + xVel;
            int nextY = ((getY() << 8) | ySub) + yVel;
            x = (nextX >> 8) & 0xFFFF;
            y = (nextY >> 8) & 0xFFFF;
            xSub = nextX & 0xFF;
            ySub = nextY & 0xFF;
            updateDynamicSpawn(getX(), getY());
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return true;
        }

        @Override
        public int getPriorityBucket() {
            return 3;
        }
    }

    private static final class EngineFlameChild extends LaunchForegroundChild {
        private static final int FLAME_X = 0x4430;
        private static final int FLAME_Y = 0x0728;
        private static final int EXPLOSION_X = 0x4430;
        private static final int EXPLOSION_Y = 0x0678;
        private int phase;
        private int timer = 0x41;
        private final int subtype;

        private EngineFlameChild(LbzFinalBoss1Instance boss, int subtype) {
            super(boss, "LBZFinalBoss1EngineFlame", FLAME_X, FLAME_Y, subtype);
            this.subtype = subtype;
            this.xVel = subtype == 0 ? 0x0200 : -0x0200;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            mappingFrame = (frameCounter >> 2) & 3;
            moveSprite2();
            if (--timer >= 0) {
                return;
            }
            if (phase == 0) {
                phase = 1;
                timer = 0x57;
                xVel = 0;
                yVel = -0x0200;
                return;
            }
            boss.recordChild(ChildKind.BOSS_EXPLOSION,
                    boss.spawnFreeChild(() -> new S3kBossExplosionChild(EXPLOSION_X, EXPLOSION_Y)));
            if (subtype == 0) {
                boss.recordChild(ChildKind.EXPLOSION_SEQUENCER,
                        boss.spawnFreeChild(() -> new ExplosionSequencerChild(boss)));
            }
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    private static final class ExplosionSequencerChild extends LaunchForegroundChild {
        private int interval = 5;
        private int timer = 5;
        private int count;
        private int milestoneWait = -1;

        private ExplosionSequencerChild(LbzFinalBoss1Instance boss) {
            super(boss, "LBZFinalBoss1ExplosionSequencer",
                    boss.cameraX() + 0xD0, boss.cameraY() - 0x20, 0);
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (milestoneWait >= 0) {
                if (milestoneWait-- <= 0) {
                    boss.signalLaunchMilestoneA();
                    boss.spawnDeathEggMiniatures();
                    ObjectLifetimeOps.expireDynamic(this);
                }
                return;
            }
            if (timer-- > 0) {
                return;
            }
            timer = interval++;
            spawnRandomExplosion();
            count++;
            if (count == 8) {
                boss.signalPadCollapseStart();
            }
            if (count == 0x18) {
                milestoneWait = 0x17F;
            }
        }

        private void spawnRandomExplosion() {
            int random = boss.services().rng().nextRaw();
            int explosionX = boss.cameraX() + 0x20 + (random & 0xFF);
            int explosionY = boss.cameraY() - 0x20;
            boss.recordChild(ChildKind.BOSS_EXPLOSION,
                    boss.spawnFreeChild(() -> new S3kBossExplosionChild(explosionX, explosionY)));
        }
    }

    private static final class DeathEggMiniatureChild extends LaunchForegroundChild {
        private static final int[][] OFFSETS = {
                {0, 0}, {-0x10, 0x28}, {-0x70, 0}, {-0x48, 0x28},
                {0x18, 0x10}, {-0x24, -8}, {-0x50, 0x1C}
        };
        private static final int[] FRAMES = {0, 1, 1, 2, 2, 2, 2};
        private static final int[] Y_VELS = {0x40, 0x38, 0x3C, 0x40, 0x44, 0x48, 0x4C};
        private final int index;
        private int smokeTimer;

        private DeathEggMiniatureChild(LbzFinalBoss1Instance boss, int index) {
            super(boss, "LBZFinalBoss1DeathEggMiniature",
                    0x4430 + OFFSETS[index][0],
                    0x0678 + OFFSETS[index][1],
                    index);
            this.index = index;
            this.mappingFrame = FRAMES[index];
            this.yVel = Y_VELS[index];
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            moveSprite2();
            if (index == 0) {
                if ((smokeTimer++ & 0x0F) == 0) {
                    spawnSmoke();
                }
                if (boss.cameraY() + 0x80 >= getY()) {
                    boss.signalLaunchMilestoneB();
                }
            }
        }

        private void spawnSmoke() {
            int random = boss.services().rng().nextRaw();
            int dx = ((random & 0x1F) - 0x10);
            int dy = (((random >> 8) & 0x1F) - 0x10);
            boss.recordChild(ChildKind.DEATH_EGG_SMOKE,
                    boss.spawnFreeChild(() -> new DeathEggSmokeChild(boss, getX() + dx, getY() + dy)));
        }
    }

    private static final class DeathEggSmokeChild extends LaunchForegroundChild {
        private int life = 0x7F;

        private DeathEggSmokeChild(LbzFinalBoss1Instance boss, int x, int y) {
            super(boss, "LBZFinalBoss1DeathEggSmoke", x, y, 0);
            this.mappingFrame = 3;
            this.yVel = -0x40;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            moveSprite2();
            if (--life < 0) {
                ObjectLifetimeOps.expireDynamic(this);
            }
        }
    }
}
