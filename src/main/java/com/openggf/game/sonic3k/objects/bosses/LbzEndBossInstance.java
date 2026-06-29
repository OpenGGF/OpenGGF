package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.objects.S3kBossExplosionController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.physics.TrigLookupTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * S3K S3KL object $CB - LBZ Act 2 spike-ball launcher boss.
 *
 * <p>ROM reference: {@code Obj_LBZEndBoss}, docs/skdisasm/sonic3k.asm
 * 153341-154205. This owns only the local object graph; registry/profile wiring
 * is intentionally left to the integration task.
 */
public final class LbzEndBossInstance extends AbstractBossInstance {
    private static final int HIT_COUNT = 8;
    private static final int COLLISION_INIT = 0x18;
    private static final int COLLISION_RISEN = 0x0F;
    private static final int INVULNERABILITY_TIME = 0x20;
    private static final int ROUTINE_GATE = 0;
    private static final int ROUTINE_INTRO_WAIT = 4;
    private static final int ROUTINE_CAMERA_PAN = 6;
    private static final int ROUTINE_RISING = 8;
    private static final int ROUTINE_FIRE_WAIT = 0x0A;
    private static final int ROUTINE_WAIT_PLATFORM_GATE = 0x0C;
    private static final int ROUTINE_DEFEAT = 0x0E;
    private static final int STARTUP_PLC_ID = 0x77;
    private static final int TIMER_OFFSET = 0x2E;
    private static final int FLAGS_OFFSET = 0x38;
    private static final int FLAG_PLATFORM_BOB_GATE = 1 << 1;
    private static final int FLAG_TUBE_SEGMENTS_FREEZE = 1 << 2;
    private static final int FLAG_TOWER_SHORTEN = 1 << 3;
    private static final int FLAG_CHILD_CLEANUP = 1 << 4;
    private static final int FLAG_DEFEAT_DONE = 1 << 5;
    private static final int FLAG_RUNNER_FINISHED = 1 << 7;
    private static final int CAMERA_RANGE_MIN_Y = 0x0460;
    private static final int CAMERA_RANGE_MAX_Y = 0x06A0;
    private static final int CAMERA_RANGE_MIN_X = 0x3900;
    private static final int CAMERA_RANGE_MAX_X = 0x3B20;
    private static final int ARENA_Y = 0x05A0;
    private static final int ARENA_X = 0x3A20;
    private static final int PAN_TARGET_X = 0x39F0;
    private static final int POST_DEFEAT_CAMERA_MAX_X = 0x3AB8;
    private static final int DEFEAT_TIMER = 0x7F;
    // loc_7403A: moveq #100 -> HUD_AddToScore works in tens, so 1000 points.
    private static final int DEFEAT_SCORE = 1000;
    // sub_73FE2 writes Normal_palette_line_2+$16: palette line 1 (0-based), color 11.
    private static final int HIT_FLASH_PALETTE_LINE = 1;
    private static final int HIT_FLASH_COLOR_INDEX = 0x0B;
    private static final String PALETTE_OWNER = "s3k.lbz.endBoss";
    private static final int PALETTE_PRIORITY = 200;
    // byte_73F3A: raw ROM subtype written to each launched ball ($39 counter & 7).
    private static final int[] LAUNCH_SUBTYPE_TABLE = {0, 2, 1, 2, 0, 1, 2, 1};

    private transient S3kSharedBossCameraGate cameraGate;
    private boolean cameraGateStarted;
    private boolean cameraRangeLatched;
    private boolean introStarted;
    private boolean hitFlashActive;
    private boolean defeatStarted;
    private int collisionFlags;
    private int collisionBackup;
    private int launchCount;
    private int defeatTimer;
    private int requestedPlcId;
    private boolean lbzEndBossArtQueued;
    private boolean lbzEndBossPaletteLine1Requested;
    private boolean paletteRuntimeIntegrationPending;
    private boolean scrollLockActive;
    private int defeatStoredMaxX;
    private boolean localGradualMaxXExtenderActive;
    private int sharedExplosionEmissionCount;
    private List<AbstractBossChild> ownedChildren;
    private List<LbzEndBossPlatformChild> platformChildren;
    private List<Integer> launchedSpikeBallRomSubtypes;
    private LbzEndBossRunnerChild runnerChild;
    private LbzEndBossPlatformChild platformLeader;

    public LbzEndBossInstance(ObjectSpawn spawn) {
        super(spawn, "LBZEndBoss");
    }

    @Override
    protected void initializeBossState() {
        state.x = spawn.x();
        state.y = spawn.y();
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        state.hitCount = HIT_COUNT;
        state.routine = ROUTINE_GATE;
        state.routineSecondary = 0;
        state.routineTertiary = 0;
        collisionFlags = COLLISION_INIT;
        collisionBackup = COLLISION_INIT;
        cameraGate = new S3kSharedBossCameraGate();
        cameraGate.reset();
        cameraGateStarted = false;
        cameraRangeLatched = false;
        introStarted = false;
        hitFlashActive = false;
        defeatStarted = false;
        launchCount = 0;
        defeatTimer = 0;
        requestedPlcId = -1;
        lbzEndBossArtQueued = false;
        lbzEndBossPaletteLine1Requested = false;
        paletteRuntimeIntegrationPending = false;
        scrollLockActive = false;
        defeatStoredMaxX = -1;
        localGradualMaxXExtenderActive = false;
        sharedExplosionEmissionCount = 0;
        ownedChildren = new ArrayList<>();
        platformChildren = new ArrayList<>();
        launchedSpikeBallRomSubtypes = new ArrayList<>();
        requestStartupAssets();
        spawnInitialVisuals();
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity player) {
        if (paletteRuntimeIntegrationPending) {
            requestStartupAssets();
        }
        updateHitFlash();
        if (defeatStarted) {
            updateDefeat();
            return;
        }
        // Check_CameraInRange runs once: a passing check rewrites the object code
        // pointer past the lea/jsr (move.l (sp),(a0)); failing out-of-range frames
        // only delete the object when it is also coarse-offscreen.
        if (!cameraRangeLatched) {
            if (isCameraInRange()) {
                cameraRangeLatched = true;
            } else {
                if (!isOnScreen(0x80)) {
                    setDestroyedByOffscreen();
                }
                return;
            }
        }
        switch (state.routine) {
            case ROUTINE_GATE -> updateCameraGate();
            case ROUTINE_INTRO_WAIT -> updateIntroWait();
            case ROUTINE_CAMERA_PAN -> updateCameraPan();
            case ROUTINE_RISING -> updateRising();
            case ROUTINE_FIRE_WAIT -> updateFireWait();
            case ROUTINE_WAIT_PLATFORM_GATE -> updateWaitPlatformGate();
            default -> {
            }
        }
    }

    @Override
    protected int getInitialHitCount() {
        return HIT_COUNT;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Hit handling is local to Obj_LBZEndBoss; TouchResponse clears the raw
        // collision byte, then sub_73FE2 restores the $25 backup after $20 frames.
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_INIT;
    }

    @Override
    protected boolean usesBaseHitHandler() {
        return false;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected int getBossHitSfxId() {
        return Sonic3kSfx.BOSS_HIT.id;
    }

    @Override
    protected int getBossExplosionSfxId() {
        return Sonic3kSfx.EXPLODE.id;
    }

    @Override
    public int getCollisionFlags() {
        return state.defeated ? 0 : collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        return state.hitCount;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (state.defeated || collisionFlags == 0 || state.hitCount <= 0) {
            return;
        }
        state.hitCount--;
        // Touch_Enemy saves the live collision byte into $25 at the moment of the
        // hit, so a post-rise hit ($0F) restores $0F, not the ObjDat $18.
        collisionBackup = collisionFlags;
        collisionFlags = 0;
        if (state.hitCount == 0) {
            // Touch_Enemy sets status bit 7; sub_73FE2 takes the defeat branch
            // without starting a flash or playing the boss-hit sfx.
            startDefeat();
            return;
        }
        state.invulnerabilityTimer = INVULNERABILITY_TIME;
        state.invulnerable = true;
        hitFlashActive = true;
        playSfxIfReady(Sonic3kSfx.BOSS_HIT.id);
    }

    @Override
    public boolean isPersistent() {
        return defeatStarted || state.routine >= ROUTINE_INTRO_WAIT;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5); // ObjDat priority $280
    }

    @Override
    public boolean isHighPriority() {
        return defeatStarted;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 0x20;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 0x10;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LBZ_END_BOSS);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, state.x, state.y, false, false);
        }
    }

    public String getArtKeyForTests() {
        return Sonic3kObjectArtKeys.LBZ_END_BOSS;
    }

    public List<AbstractBossChild> getOwnedChildrenForTests() {
        return List.copyOf(ownedChildren);
    }

    public List<LbzEndBossPlatformChild> getPlatformChildrenForTests() {
        return List.copyOf(platformChildren);
    }

    public boolean hasRunnerForTests() {
        return runnerChild != null && !runnerChild.isDestroyed();
    }

    public boolean isFlagSetForTests(int bit) {
        return (flags() & (1 << bit)) != 0;
    }

    public boolean isHitFlashActiveForTests() {
        return hitFlashActive;
    }

    public boolean isDefeatActiveForTests() {
        return defeatStarted;
    }

    public boolean spawnsCapsuleForTests() {
        return false;
    }

    public List<Integer> getLaunchedSpikeBallRomSubtypesForTests() {
        return List.copyOf(launchedSpikeBallRomSubtypes);
    }

    public int getRequestedPlcIdForTests() {
        return requestedPlcId;
    }

    public boolean isLbzEndBossArtQueuedForTests() {
        return lbzEndBossArtQueued;
    }

    public boolean isLbzEndBossPaletteLine1RequestedForTests() {
        return lbzEndBossPaletteLine1Requested;
    }

    public boolean isPaletteRuntimeIntegrationPendingForTests() {
        return paletteRuntimeIntegrationPending;
    }

    public boolean isScrollLockActiveForTests() {
        return scrollLockActive;
    }

    public int getDefeatStoredMaxXForTests() {
        return defeatStoredMaxX;
    }

    public boolean usesLocalGradualMaxXExtenderForTests() {
        return localGradualMaxXExtenderActive;
    }

    public int getSharedExplosionEmissionCountForTests() {
        return sharedExplosionEmissionCount;
    }

    private void requestStartupAssets() {
        requestedPlcId = STARTUP_PLC_ID;
        lbzEndBossArtQueued = true;
        lbzEndBossPaletteLine1Requested = true;
        var svc = tryServices();
        if (svc == null) {
            paletteRuntimeIntegrationPending = true;
            return;
        }
        Level level = svc.currentLevel();
        if (level == null) {
            paletteRuntimeIntegrationPending = true;
            return;
        }
        try {
            var rom = svc.rom();
            if (rom == null) {
                paletteRuntimeIntegrationPending = true;
                return;
            }
            byte[] line = rom.readBytes(Sonic3kConstants.PAL_LBZ_END_BOSS_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(
                    svc.paletteOwnershipRegistryOrNull(),
                    level,
                    svc.graphicsManager(),
                    PALETTE_OWNER,
                    PALETTE_PRIORITY,
                    1,
                    line);
            paletteRuntimeIntegrationPending = false;
        } catch (IOException e) {
            paletteRuntimeIntegrationPending = true;
        }
    }

    private void spawnInitialVisuals() {
        recordChild(spawnChild(() -> new LbzEndBossCockpitChild(this)));
        recordChild(spawnChild(() -> new LbzEndBossTowerChild(this)));
    }

    private <T extends AbstractBossChild> T recordChild(T child) {
        ownedChildren.add(child);
        childComponents.add(child);
        return child;
    }

    private boolean isCameraInRange() {
        var svc = tryServices();
        Camera camera = svc != null ? svc.camera() : null;
        if (camera == null) {
            return true;
        }
        int cameraX = camera.getX() & 0xFFFF;
        int cameraY = camera.getY() & 0xFFFF;
        return cameraY >= CAMERA_RANGE_MIN_Y && cameraY <= CAMERA_RANGE_MAX_Y
                && cameraX >= CAMERA_RANGE_MIN_X && cameraX <= CAMERA_RANGE_MAX_X;
    }

    private void updateCameraGate() {
        var svc = tryServices();
        Camera camera = svc != null ? svc.camera() : null;
        if (!cameraGateStarted) {
            cameraGateStarted = true;
            if (svc != null) {
                // sub_85D6A: Boss_flag is set immediately in routine 0, before the
                // 120-frame music fade completes.
                svc.fadeOutMusic();
                if (svc.gameState() != null) {
                    svc.gameState().setCurrentBossId(0xCB);
                }
            }
            cameraGate.begin(camera,
                    new S3kSharedBossCameraGate.LockBounds(ARENA_Y, ARENA_Y, ARENA_X, ARENA_X),
                    120);
        }
        boolean complete = cameraGate.update(camera, () -> {
            var services = tryServices();
            if (services != null) {
                services.playMusic(Sonic3kMusic.BOSS.id);
            }
        });
        if (complete) {
            startIntro();
        }
    }

    private void startIntro() {
        if (introStarted) {
            return;
        }
        introStarted = true;
        state.routine = ROUTINE_INTRO_WAIT;
        // ChildObjDat_7418A via CreateChild4_LinkListRepeated: subtypes 0,2,4,6,
        // parent3 chained boss -> leader -> follower -> follower.
        LbzEndBossPlatformChild chainParent = null;
        for (int subtype = 0; subtype <= 6; subtype += 2) {
            int childSubtype = subtype;
            LbzEndBossPlatformChild previous = chainParent;
            LbzEndBossPlatformChild platform = recordChild(spawnChild(
                    () -> new LbzEndBossPlatformChild(this, childSubtype, previous)));
            platformChildren.add(platform);
            if (subtype == 0) {
                platformLeader = platform;
            }
            chainParent = platform;
        }
        runnerChild = recordChild(spawnChild(() -> new LbzEndBossRunnerChild(this)));
    }

    private void updateIntroWait() {
        if ((flags() & FLAG_RUNNER_FINISHED) == 0) {
            return;
        }
        state.routine = ROUTINE_CAMERA_PAN;
        setScrollLock(true);
        Camera camera = cameraOrNull();
        if (camera != null) {
            camera.setMinX((short) PAN_TARGET_X);
            camera.setMaxX((short) PAN_TARGET_X);
        }
    }

    private void updateCameraPan() {
        Camera camera = cameraOrNull();
        if (camera != null) {
            int x = camera.getX() & 0xFFFF;
            if (x > PAN_TARGET_X) {
                camera.setX((short) Math.max(PAN_TARGET_X, x - 2));
                return;
            }
        }
        startRising();
    }

    private void startRising() {
        state.routine = ROUTINE_RISING;
        setScrollLock(false);
        clearFlag(FLAG_TUBE_SEGMENTS_FREEZE);
        state.yVel = -0x40;
        setCustomFlag(TIMER_OFFSET, 0xDF);
        playSfxIfReady(Sonic3kSfx.RISING.id);
        recordChild(spawnChild(() -> new LbzEndBossTubeSegmentChild(this, -0x18, 0x38, 0)));
        recordChild(spawnChild(() -> new LbzEndBossTubeSegmentChild(this, 0x18, 0x38, 2)));
        recordChild(spawnChild(() -> new LbzEndBossTubeSegmentChild(this, 0, 0x38, 4)));
    }

    private void updateRising() {
        state.applyVelocity();
        int timer = getCustomFlag(TIMER_OFFSET) - 1;
        setCustomFlag(TIMER_OFFSET, timer);
        if (timer >= 0) {
            return;
        }
        setFlag(FLAG_TUBE_SEGMENTS_FREEZE);
        collisionFlags = COLLISION_RISEN;
        setFlag(FLAG_TOWER_SHORTEN);
        enterFireWait();
    }

    private void enterFireWait() {
        state.routine = ROUTINE_FIRE_WAIT;
        state.routineSecondary = 0;
        setCustomFlag(TIMER_OFFSET, 7);
    }

    private void updateFireWait() {
        int timer = getCustomFlag(TIMER_OFFSET) - 1;
        setCustomFlag(TIMER_OFFSET, timer);
        if (state.routineSecondary == 0) {
            if (timer >= 0) {
                return;
            }
            setFlag(FLAG_PLATFORM_BOB_GATE);
            state.routineSecondary = 1;
            setCustomFlag(TIMER_OFFSET, 0x3F);
            return;
        }
        if (timer >= 0) {
            return;
        }
        launchSpikeBall();
        state.routine = ROUTINE_WAIT_PLATFORM_GATE;
    }

    private void updateWaitPlatformGate() {
        if ((flags() & FLAG_PLATFORM_BOB_GATE) != 0) {
            return;
        }
        enterFireWait();
    }

    private void launchSpikeBall() {
        playSfxIfReady(Sonic3kSfx.TUBE_LAUNCHER.id);
        int romSubtype = LAUNCH_SUBTYPE_TABLE[launchCount++ & 7];
        launchedSpikeBallRomSubtypes.add(romSubtype);
        recordChild(spawnChild(() -> new LbzEndBossSpikeBallChild(this, romSubtype)));
    }

    private void updateHitFlash() {
        if (!hitFlashActive) {
            return;
        }
        state.invulnerable = true;
        applyHitFlashPalette((state.invulnerabilityTimer & 1) == 0 ? 0x0EEE : 0x0222);
        state.invulnerabilityTimer--;
        if (state.invulnerabilityTimer > 0) {
            return;
        }
        applyHitFlashPalette(0x0222);
        hitFlashActive = false;
        state.invulnerable = false;
        if (!defeatStarted) {
            collisionFlags = collisionBackup;
        }
    }

    private void applyHitFlashPalette(int segaWord) {
        var svc = tryServices();
        if (svc == null) {
            return;
        }
        PaletteWriteSupport.applyColor(
                svc.paletteOwnershipRegistryOrNull(),
                svc.currentLevel(),
                svc.graphicsManager(),
                PALETTE_OWNER,
                200,
                HIT_FLASH_PALETTE_LINE,
                HIT_FLASH_COLOR_INDEX,
                segaWord);
    }

    private void startDefeat() {
        defeatStarted = true;
        state.defeated = true;
        state.routine = ROUTINE_DEFEAT;
        collisionFlags = 0;
        hitFlashActive = false;
        state.invulnerable = false;
        setFlag(FLAG_CHILD_CLEANUP);
        defeatTimer = DEFEAT_TIMER;
        state.yVel = -0x200;
        state.xVel = 0;
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
        var svc = tryServices();
        if (svc != null) {
            if (svc.levelGamestate() != null) {
                svc.levelGamestate().pauseTimer();
            }
            if (svc.gameState() != null) {
                svc.gameState().addScore(DEFEAT_SCORE);
            }
            defeatStoredMaxX = POST_DEFEAT_CAMERA_MAX_X;
            // loc_7403A: Camera_stored_max_X_pos=$3AB8 + Child6_IncLevX child.
            recordChild(spawnChild(() -> new LbzEndBossGradualMaxXExtenderChild(this)));
            recordChild(spawnChild(() -> new LbzEndBossExplosionControllerChild(this)));
        }
    }

    private void updateDefeat() {
        // loc_73A52: gravity is added before MoveSprite2.
        state.yVel += 0x20;
        state.applyVelocity();
        if (--defeatTimer >= 0) {
            return;
        }
        // loc_73A6A
        setFlag(FLAG_DEFEAT_DONE);
        var svc = tryServices();
        if (svc != null) {
            if (svc.gameState() != null) {
                svc.gameState().setCurrentBossId(0);
            }
            svc.playMusic(Sonic3kMusic.LBZ2.id);
        }
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    private void noteSharedExplosionEmission() {
        sharedExplosionEmissionCount++;
    }

    private void setScrollLock(boolean active) {
        scrollLockActive = active;
        Camera camera = cameraOrNull();
        if (camera != null) {
            camera.setFrozen(active);
        }
    }

    private Camera cameraOrNull() {
        var svc = tryServices();
        return svc != null ? svc.camera() : null;
    }

    private void playSfxIfReady(int sfxId) {
        var svc = tryServices();
        if (svc != null) {
            svc.playSfx(sfxId);
        }
    }

    private int flags() {
        return getCustomFlag(FLAGS_OFFSET);
    }

    private void setFlag(int flag) {
        setCustomFlag(FLAGS_OFFSET, flags() | flag);
    }

    private void clearFlag(int flag) {
        setCustomFlag(FLAGS_OFFSET, flags() & ~flag);
    }

    private boolean flagSet(int flag) {
        return (flags() & flag) != 0;
    }

    /** Robotnik in the launcher cockpit: loc_73E0E, ObjDat3_74104. */
    public static final class LbzEndBossCockpitChild extends AbstractBossChild {
        private int frame;
        private int animTimer;
        private int animIndex;
        private boolean animStarted;

        private LbzEndBossCockpitChild(LbzEndBossInstance parent) {
            super(parent, "LBZEndBossCockpit", 6, 0xCB);
            frame = 4;
            animTimer = 0;
            animIndex = 0;
            animStarted = false;
            syncPositionWithParent();
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!shouldUpdate(frameCounter)) {
                return;
            }
            LbzEndBossInstance boss = boss();
            syncPositionWithParent();
            currentY -= 8;
            if (!animStarted) {
                // loc_73E3A: routine 2 waits for $38 bit 7, then sets frame 0.
                if (boss.flagSet(FLAG_RUNNER_FINISHED)) {
                    animStarted = true;
                    frame = 0;
                    animIndex = 0;
                    animTimer = 0;
                }
            } else if (boss.defeatStarted) {
                // loc_73E80: beat-up Robotnik rides the falling launcher until the
                // boss object itself deletes ($38 bit 5 / parent destruction).
                frame = 3;
            } else if (boss.hitFlashActive) {
                frame = 2;
            } else if (--animTimer < 0) {
                // byte_741F0: frames 0/1 with delay 7.
                animTimer = 7;
                animIndex ^= 1;
                frame = animIndex;
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(frame, currentX, currentY, false, false);
            }
        }

        private LbzEndBossInstance boss() {
            return (LbzEndBossInstance) parent;
        }
    }

    /**
     * Launcher tower solid: loc_73EA2/loc_73EB4. The ROM positions it once at
     * creation (boss spawn + (-$18,-$70)) and never refreshes it from the parent,
     * so it does NOT ride the boss upward during the rise.
     */
    public static final class LbzEndBossTowerChild extends AbstractBossChild implements SolidObjectProvider {
        private static final SolidObjectParams FULL_PARAMS = new SolidObjectParams(0x13, 0x80, 0x120);
        private static final SolidObjectParams SHORT_PARAMS = new SolidObjectParams(0x13, 0x14, 0);
        private final int baseX;
        private final int baseY;
        private boolean shortened;

        private LbzEndBossTowerChild(LbzEndBossInstance parent) {
            super(parent, "LBZEndBossTower", 0, 0xCB);
            baseX = parent.getX() - 0x18;
            baseY = parent.getY() - 0x70;
            currentX = baseX;
            currentY = baseY;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!shouldUpdate(frameCounter)) {
                return;
            }
            LbzEndBossInstance boss = boss();
            if (boss.flagSet(FLAG_CHILD_CLEANUP)) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            if (!shortened && boss.flagSet(FLAG_TOWER_SHORTEN)) {
                // loc_73EE2: one-time y += $60 with shortened solid box.
                shortened = true;
                currentY = baseY + 0x60;
            }
            updateDynamicSpawn();
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return shortened ? SHORT_PARAMS : FULL_PARAMS;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LBZ_END_BOSS);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(0x0C, currentX, currentY, false, false);
            }
        }

        private LbzEndBossInstance boss() {
            return (LbzEndBossInstance) parent;
        }
    }

    /** Tube segments: loc_73C0A, refreshed from the parent every frame. */
    private static final class LbzEndBossTubeSegmentChild extends AbstractBossChild {
        private final int dx;
        private final int dy;
        private final int subtype;
        private int riseCounter;
        private boolean debrisSpawned;

        private LbzEndBossTubeSegmentChild(LbzEndBossInstance parent, int dx, int dy, int subtype) {
            super(parent, "LBZEndBossTubeSegment", 5, 0xCB);
            this.dx = dx;
            this.dy = dy;
            this.subtype = subtype;
            syncPositionWithParent();
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!shouldUpdate(frameCounter)) {
                return;
            }
            LbzEndBossInstance boss = boss();
            if (boss.defeatStarted) {
                spawnDebrisOnce();
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            syncPositionWithParent();
            currentX += dx;
            currentY += dy;
            if (!boss.flagSet(FLAG_TUBE_SEGMENTS_FREEZE)) {
                currentY -= (riseCounter++ & 3);
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LBZ_END_BOSS);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(2 + subtype / 2, currentX, currentY, false, false);
            }
        }

        private void spawnDebrisOnce() {
            if (debrisSpawned) {
                return;
            }
            debrisSpawned = true;
            LbzEndBossInstance boss = boss();
            // ChildObjDat_74176 + word_73CAA: the trio gets debris subtypes 0,2,4
            // which index x velocities -$100/$200/-$200 individually.
            int[] xVel = {-0x100, 0x200, -0x200};
            int[] yOffsets = {-0x18, -8, 8};
            for (int i = 0; i < 3; i++) {
                int yOff = yOffsets[i];
                int vel = xVel[i];
                boss.recordChild(boss.spawnChild(() -> new LbzEndBossDebrisChild(
                        boss, currentX, currentY + yOff, vel, -0x200, 0x0D, false, false, true)));
            }
        }

        private LbzEndBossInstance boss() {
            return (LbzEndBossInstance) parent;
        }
    }

    /**
     * Bobbing platform chain: loc_73CB0/loc_73D56 (ChildObjDat_7418A,
     * CreateChild4_LinkListRepeated). The leader (subtype 0) is static at
     * boss + (-$14,+$40) and only drives the phase byte; followers position
     * themselves at chainParent + (sin,cos)($3C)>>4 via MoveSprite_CircularSimple.
     */
    public static final class LbzEndBossPlatformChild extends AbstractBossChild implements SolidObjectProvider {
        private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x12, 7, 7);
        private static final int MODE_WAIT = 0;
        private static final int MODE_SWING_OUT = 1;
        private static final int MODE_HOLD = 2;
        private static final int MODE_SWING_BACK = 3;
        private final int subtype;
        private final LbzEndBossPlatformChild chainParent;
        private int angle;
        private int mode;
        private int holdTimer;
        private int xFixed;
        private int yFixed;
        private boolean hasRunBobGate;
        private boolean converted;

        private LbzEndBossPlatformChild(LbzEndBossInstance parent, int subtype,
                LbzEndBossPlatformChild chainParent) {
            super(parent, "LBZEndBossPlatform", 5, 0xCB);
            this.subtype = subtype;
            this.chainParent = chainParent;
            angle = 0;
            mode = MODE_WAIT;
            // loc_73CD8: position set once at init from the (pre-rise) boss position.
            currentX = parent.getX() - 0x14;
            currentY = parent.getY() + 0x40;
            xFixed = currentX << 16;
            yFixed = currentY << 16;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!shouldUpdate(frameCounter)) {
                return;
            }
            LbzEndBossInstance boss = boss();
            if (boss.defeatStarted) {
                becomeDebris();
                return;
            }
            if (subtype == 0) {
                updateLeaderPhase(boss);
            } else {
                // loc_73D62: copy $3C from the chain parent, then circular offset.
                angle = chainParent.angle;
                xFixed = chainParent.xFixed + (TrigLookupTable.sinHex(angle) << 12);
                yFixed = chainParent.yFixed + (TrigLookupTable.cosHex(angle) << 12);
                currentX = xFixed >> 16;
                currentY = yFixed >> 16;
            }
            updateDynamicSpawn();
        }

        @Override
        public boolean isTopSolidOnly() {
            return true;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return SOLID_PARAMS;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LBZ_END_BOSS);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(0x0E, currentX, currentY, false, false);
            }
        }

        public boolean hasRunBobGateForTests() {
            return hasRunBobGate;
        }

        public int getAngleForTests() {
            return angle & 0xFF;
        }

        private void updateLeaderPhase(LbzEndBossInstance boss) {
            switch (mode) {
                case MODE_WAIT -> {
                    if (boss.flagSet(FLAG_PLATFORM_BOB_GATE)) {
                        mode = MODE_SWING_OUT;
                        hasRunBobGate = true;
                    }
                }
                case MODE_SWING_OUT -> {
                    // loc_73D0E: subq.b then unsigned compare against $C0.
                    angle = (angle - 1) & 0xFF;
                    if (angle <= 0xC0) {
                        mode = MODE_HOLD;
                        holdTimer = 0x80;
                    }
                }
                case MODE_HOLD -> {
                    if (--holdTimer < 0) {
                        mode = MODE_SWING_BACK;
                    }
                }
                case MODE_SWING_BACK -> {
                    angle = (angle + 1) & 0xFF;
                    if (angle == 0) {
                        mode = MODE_WAIT;
                        boss.clearFlag(FLAG_PLATFORM_BOB_GATE);
                    }
                }
                default -> {
                }
            }
        }

        private void becomeDebris() {
            if (converted) {
                return;
            }
            converted = true;
            LbzEndBossInstance boss = boss();
            // loc_740AA / word_740E4: per-subtype x velocity, MoveChkDel (no flicker).
            int[] xVel = {0x300, 0x200, -0x200, -0x300};
            boss.recordChild(boss.spawnChild(() -> new LbzEndBossDebrisChild(
                    boss, currentX, currentY, xVel[subtype / 2], -0x200, 0x0E, false, false, false)));
            ObjectLifetimeOps.expireDynamic(this);
        }

        private LbzEndBossInstance boss() {
            return (LbzEndBossInstance) parent;
        }
    }

    /** Robotnik dash cameo: loc_73D74, ObjDat3_740F8. */
    private static final class LbzEndBossRunnerChild extends AbstractBossChild {
        private static final int[] RUN_SCRIPT = {0, 1, 2, 1};
        private int phase;
        private int timer;
        private int xVel;
        private int yVel;
        private int xFixed;
        private int yFixed;
        private int animIndex;
        private int animTimer;
        private int frame;

        private LbzEndBossRunnerChild(LbzEndBossInstance parent) {
            super(parent, "LBZEndBossRunner", 5, 0xCB);
            phase = 0;
            timer = 0x1F;
            xVel = -0x180;
            yVel = 0;
            animIndex = 0;
            animTimer = 0;
            frame = 0;
            syncPositionWithParent();
            currentX += 0x70;
            currentY -= 0x18;
            xFixed = currentX << 16;
            yFixed = currentY << 16;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            if (phase == 0) {
                // loc_73DB6: MoveSprite2 + Animate_Raw + Obj_Wait($1F).
                move();
                updateRunAnimation();
                if (--timer < 0) {
                    // loc_73DC8: x_vel is NOT cleared - Robotnik jumps up-left.
                    phase = 1;
                    timer = 0x29;
                    yVel = -0x200;
                    frame = 3;
                }
            } else {
                // loc_73DEA: MoveSprite_LightGravity moves with the old velocity,
                // then adds $20 gravity.
                move();
                yVel += 0x20;
                if (--timer < 0) {
                    boss().setFlag(FLAG_RUNNER_FINISHED);
                    ObjectLifetimeOps.expireDynamic(this);
                }
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_ROBOTNIK_RUN);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(frame, currentX, currentY, false, false);
            }
        }

        private void move() {
            xFixed += xVel << 8;
            yFixed += yVel << 8;
            currentX = xFixed >> 16;
            currentY = yFixed >> 16;
        }

        private void updateRunAnimation() {
            if (--animTimer >= 0) {
                return;
            }
            animTimer = 7;
            animIndex = (animIndex + 1) & 3;
            frame = RUN_SCRIPT[animIndex];
        }

        private LbzEndBossInstance boss() {
            return (LbzEndBossInstance) parent;
        }
    }

    /** Spike ball projectile: loc_73A92, word_7412A (collision $9A). */
    public static final class LbzEndBossSpikeBallChild extends AbstractBossChild implements TouchResponseProvider {
        private static final int TERRAIN_RADIUS = 0x10;
        // word_73F64: per-subtype (dx, dy, x_vel, y_vel).
        private static final int[][] SPAWN_DATA = {
                {-0x10, 0x60, -0x600, -0x200},
                {-0x10, 0x60, -0x400, -0x100},
                {-0x10, 0x90, -0x400, 0}
        };
        // word_73FAE: defeat-spray debris velocities.
        private static final int[][] SPRAY_VELOCITIES = {
                {-0x300, -0x300}, {0x300, -0x300}, {-0x300, -0x200}, {0x300, -0x200},
                {-0x400, -0x400}, {0x400, -0x400}, {-0x400, -0x300}, {0x400, -0x300}
        };
        private static final int[][] SPRAY_OFFSETS = {
                {-8, -8}, {8, -8}, {-8, 8}, {8, 8},
                {-0x10, -0x10}, {0x10, -0x10}, {-0x10, 0x10}, {0x10, 0x10}
        };
        private static final int[][] SPRAY_SMOKE_OFFSETS = {
                {-8, 6}, {6, 8}, {-8, -6}, {6, -8}
        };
        private final int romSubtype;
        private int phase;
        private int timer;
        private int xVel;
        private int yVel;
        private int xFixed;
        private int yFixed;
        private boolean frameToggle;
        private boolean exploded;

        private LbzEndBossSpikeBallChild(LbzEndBossInstance parent, int romSubtype) {
            super(parent, "LBZEndBossSpikeBall", 5, 0xCB);
            this.romSubtype = romSubtype;
            int[] data = SPAWN_DATA[Math.min(2, romSubtype)];
            currentX = parent.getX() + data[0];
            currentY = parent.getY() + data[1];
            xFixed = currentX << 16;
            yFixed = currentY << 16;
            xVel = data[2];
            yVel = data[3];
            phase = 0;
            timer = 0x0F;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            if (exploded) {
                return;
            }
            LbzEndBossInstance boss = boss();
            if (boss.defeatStarted) {
                // loc_73A92: parent status bit 7 -> loc_73B82 spray.
                explode();
                return;
            }
            // byte_741F4 has delay 0: mapping frame alternates 5/6 every frame.
            frameToggle = !frameToggle;
            switch (phase) {
                case 0 -> {
                    // loc_73AF6: MoveSprite2 + Obj_Wait($F).
                    move();
                    if (--timer < 0) {
                        phase = 1;
                    }
                }
                case 1 -> updateFalling();
                case 2 -> updateRolling(frameCounter);
                default -> {
                }
            }
            if (!exploded) {
                updateDynamicSpawn();
            }
        }

        @Override
        public int getCollisionFlags() {
            return isDestroyed() ? 0 : 0x9A;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LBZ_END_BOSS);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(frameToggle ? 6 : 5, currentX, currentY, false, false);
            }
        }

        public int getRomSubtypeForTests() {
            return romSubtype;
        }

        public int getPhaseForTests() {
            return phase;
        }

        public int getXVelForTests() {
            return xVel;
        }

        public int getYVelForTests() {
            return yVel;
        }

        public void forceRollingForTests(int xVelocity) {
            phase = 2;
            xVel = xVelocity;
            yVel = 0;
            xFixed = currentX << 16;
            yFixed = currentY << 16;
        }

        public void forceFallingForTests(int yVelocity) {
            phase = 1;
            yVel = yVelocity;
            xVel = 0;
            xFixed = currentX << 16;
            yFixed = currentY << 16;
        }

        private void move() {
            xFixed += xVel << 8;
            yFixed += yVel << 8;
            currentX = xFixed >> 16;
            currentY = yFixed >> 16;
        }

        private void adjustX(int delta) {
            currentX += delta;
            xFixed += delta << 16;
        }

        private void adjustY(int delta) {
            currentY += delta;
            yFixed += delta << 16;
        }

        private void updateFalling() {
            // loc_73B12: gravity, MoveSprite2, then ObjHitFloor_DoRoutine (which
            // only probes while y_vel >= 0 and lands on distance <= 0).
            yVel += 0x20;
            move();
            if (yVel < 0) {
                return;
            }
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, TERRAIN_RADIUS);
            if (floor != null && floor.hasCollision()) {
                adjustY(floor.distance());
                phase = 2;
                yVel = 0;
            }
        }

        private void updateRolling(int frameCounter) {
            // loc_73B3C with $34 = loc_73B82: a wall contact or losing the floor
            // detonates the ball - the ROM never bounces or re-falls here.
            TerrainCheckResult wall = ObjectTerrainUtils.checkRightWallDist(currentX + TERRAIN_RADIUS, currentY);
            if (wall != null && wall.foundSurface() && wall.distance() < 0) {
                adjustX(wall.distance());
                explode();
                return;
            }
            int nextX = (xFixed + (xVel << 8)) >> 16;
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(nextX, currentY, TERRAIN_RADIUS);
            boolean onFloor = floor != null && floor.foundSurface()
                    && floor.distance() >= -1 && floor.distance() < 0x0C;
            if (!onFloor) {
                explode();
                return;
            }
            adjustY(floor.distance());
            // tst.b d3 quirk: acceleration is skipped when the look-ahead integer
            // x position's low byte is zero. No upper speed cap exists.
            if ((nextX & 0xFF) != 0) {
                xVel += 0x10;
            }
            move();
            // Smoke gate is an unsigned compare: x_vel + $200 >= $400, i.e.
            // |speed| beyond $200 in either direction, every 4th global frame.
            if (((xVel + 0x200) & 0xFFFF) >= 0x400 && (frameCounter & 3) == 0) {
                LbzEndBossInstance boss = boss();
                boss.recordChild(boss.spawnChild(() -> new LbzEndBossSmokePuffChild(
                        boss, currentX, currentY + TERRAIN_RADIUS, 0)));
            }
        }

        /** loc_73B82 + ChildObjDat_741A0: 8 flickering debris + 4 delayed smokes. */
        private void explode() {
            if (exploded) {
                return;
            }
            exploded = true;
            LbzEndBossInstance boss = boss();
            boss.playSfxIfReady(Sonic3kSfx.BOSS_HIT.id);
            for (int i = 0; i < SPRAY_OFFSETS.length; i++) {
                int ox = SPRAY_OFFSETS[i][0];
                int oy = SPRAY_OFFSETS[i][1];
                int xv = SPRAY_VELOCITIES[i][0];
                int yv = SPRAY_VELOCITIES[i][1];
                int frame = i < 4 ? 0x0A : 0x0B;
                int flipBits = i & 3;
                boolean hFlip = (flipBits & 1) != 0;
                boolean vFlip = (flipBits & 2) != 0;
                boss.recordChild(boss.spawnChild(() -> new LbzEndBossDebrisChild(
                        boss, currentX + ox, currentY + oy, xv, yv, frame, hFlip, vFlip, true)));
            }
            for (int i = 0; i < SPRAY_SMOKE_OFFSETS.length; i++) {
                int ox = SPRAY_SMOKE_OFFSETS[i][0];
                int oy = SPRAY_SMOKE_OFFSETS[i][1];
                int subtype = 0x10 + i * 2;
                boss.recordChild(boss.spawnChild(() -> new LbzEndBossSmokePuffChild(
                        boss, currentX + ox, currentY + oy, subtype)));
            }
            ObjectLifetimeOps.expireDynamic(this);
        }

        private LbzEndBossInstance boss() {
            return (LbzEndBossInstance) parent;
        }
    }

    /**
     * Smoke puff: loc_73BA0 / byte_741F8. Subtype 0 rises at -$200; subtypes
     * $10/$12/$14/$16 wait -2*(subtype-$10) frames before animating. The ROM's
     * $F4 delete never actually fires (loc_73BDC re-increments $2E every frame),
     * so the engine implements the intended play-once-then-delete behaviour; see
     * docs/S3K_KNOWN_DISCREPANCIES.md.
     */
    private static final class LbzEndBossSmokePuffChild extends AbstractBossChild {
        private static final int[] ANIM_FRAMES = {7, 7, 8, 9};
        private int delayTimer;
        private int frame;
        private int animIndex;
        private int animTimer;
        private int yVel;
        private int yFixed;

        private LbzEndBossSmokePuffChild(LbzEndBossInstance parent, int x, int y, int subtype) {
            super(parent, "LBZEndBossSmoke", 4, 0xCB);
            currentX = x;
            currentY = y;
            yFixed = y << 16;
            delayTimer = subtype >= 0x10 ? -2 * (subtype - 0x10) : 0;
            frame = 7;
            animIndex = 0;
            animTimer = 0;
            yVel = subtype == 0 ? -0x200 : 0;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            if (delayTimer < 0) {
                delayTimer++;
                return;
            }
            yFixed += yVel << 8;
            currentY = yFixed >> 16;
            if (--animTimer < 0) {
                animTimer = 5;
                animIndex++;
                if (animIndex > 3) {
                    ObjectLifetimeOps.expireDynamic(this);
                    return;
                }
                frame = ANIM_FRAMES[animIndex];
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LBZ_END_BOSS);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(frame, currentX, currentY, false, false);
            }
        }
    }

    /**
     * Debris fragment shared by the tube segments, platforms, and spike-ball
     * spray. ROM movement is MoveSprite ($38 gravity applied after the move);
     * Obj_FlickerMove pieces draw every other frame, platform pieces
     * (MoveChkDel) draw every frame. Both delete via camera-relative bounds.
     */
    private static final class LbzEndBossDebrisChild extends AbstractBossChild {
        private final int frame;
        private final boolean hFlip;
        private final boolean vFlip;
        private final boolean flicker;
        private int xVel;
        private int yVel;
        private int xFixed;
        private int yFixed;
        private boolean flickerSkip;

        private LbzEndBossDebrisChild(LbzEndBossInstance parent, int x, int y, int xVel, int yVel,
                int frame, boolean hFlip, boolean vFlip, boolean flicker) {
            super(parent, "LBZEndBossDebris", 5, 0xCB);
            currentX = x;
            currentY = y;
            xFixed = x << 16;
            yFixed = y << 16;
            this.xVel = xVel;
            this.yVel = yVel;
            this.frame = frame;
            this.hFlip = hFlip;
            this.vFlip = vFlip;
            this.flicker = flicker;
        }

        @Override
        protected boolean destroyWhenParentDestroyed() {
            return false;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            // MoveSprite: position moves by the old velocity, then $38 gravity.
            xFixed += xVel << 8;
            yFixed += yVel << 8;
            currentX = xFixed >> 16;
            currentY = yFixed >> 16;
            yVel += 0x38;
            if (flicker) {
                flickerSkip = !flickerSkip;
            }
            if (isOutOfCameraBounds()) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            updateDynamicSpawn();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (flicker && flickerSkip) {
                return;
            }
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LBZ_END_BOSS);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(frame, currentX, currentY, hFlip, vFlip);
            }
        }

        private boolean isOutOfCameraBounds() {
            Camera camera = ((LbzEndBossInstance) parent).cameraOrNull();
            if (camera == null) {
                return currentY > 0x800;
            }
            int cameraX = camera.getX() & 0xFFFF;
            int cameraY = camera.getY() & 0xFFFF;
            int dx = ((currentX & ~0x7F) - ((cameraX - 0x80) & ~0x7F)) & 0xFFFF;
            if (dx > 0x280) {
                return true;
            }
            int dy = (currentY - cameraY + 0x80) & 0xFFFF;
            return dy > 0x200;
        }
    }

    /** Child6_CreateBossExplosion subtype 4 controller wrapper. */
    private static final class LbzEndBossExplosionControllerChild extends AbstractBossChild {
        private final transient S3kBossExplosionController controller;

        private LbzEndBossExplosionControllerChild(LbzEndBossInstance parent) {
            super(parent, "LBZEndBossExplosionController", 0, 0xCB);
            controller = new S3kBossExplosionController(parent.getX(), parent.getY(), 4);
        }

        @Override
        protected boolean destroyWhenParentDestroyed() {
            return false;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            LbzEndBossInstance boss = boss();
            if (controller.isFinished()) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            controller.tick();
            for (S3kBossExplosionController.PendingExplosion pending : controller.drainPendingExplosions()) {
                boss.noteSharedExplosionEmission();
                if (pending.playSfx()) {
                    boss.playSfxIfReady(Sonic3kSfx.EXPLODE.id);
                }
                boss.spawnChild(() -> new S3kBossExplosionChild(pending.x(), pending.y()));
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        private LbzEndBossInstance boss() {
            return (LbzEndBossInstance) parent;
        }
    }

    /**
     * Obj_IncLevEndXGradual: a $4000/frame sub-pixel accumulator whose integer
     * part is added to Camera_max_X each frame (accelerating extension), then
     * snapped to Camera_stored_max_X_pos ($3AB8) and deleted.
     */
    private static final class LbzEndBossGradualMaxXExtenderChild extends AbstractBossChild {
        private int accumulator;

        private LbzEndBossGradualMaxXExtenderChild(LbzEndBossInstance parent) {
            super(parent, "LBZEndBossMaxXExtender", 0, 0xCB);
            parent.localGradualMaxXExtenderActive = true;
        }

        @Override
        protected boolean destroyWhenParentDestroyed() {
            return false;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            Camera camera = ((LbzEndBossInstance) parent).cameraOrNull();
            if (camera == null) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            accumulator += 0x4000;
            int step = accumulator >>> 16;
            int newMax = (camera.getMaxX() & 0xFFFF) + step;
            if (newMax >= POST_DEFEAT_CAMERA_MAX_X) {
                camera.setMaxX((short) POST_DEFEAT_CAMERA_MAX_X);
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            camera.setMaxX((short) newMax);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
