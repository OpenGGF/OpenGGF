package com.openggf.game.sonic3k.objects;

import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.RomWorldPositionedObject;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Locked-on S3KL object {@code $AA}, {@code Obj_FBZMiniboss}. */
public final class FbzMinibossInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomWorldPositionedObject {
    private static final int[] ACTIVATION_BOUNDS = {0x240, 0x600, 0x2D20, 0x2F20};
    private static final int[] LOCK_BOUNDS = {0x540, 0x540, 0x2E20, 0x2EA0};
    private static final String[] INITIAL_ROLES = {
            "cover-left", "cover-right", "cover-centre", "plunger", "aimer", "arm-left", "arm-right"
    };
    private static final int MUSIC_WAIT = 0x78;
    private static final int END_SIGN_WAIT = (2 * 60) - 1;
    private static final int[] FLASH_INDICES = {2, 4, 11, 15};
    private static final int[] FLASH_DARK = {0x222, 0x644, 0x222, 0x044};
    private static final int[] FLASH_BRIGHT = {0xAAA, 0xAAA, 0xEEE, 0xEEE};

    static final int ROOT_FIGHT_STARTED = 0;
    static final int ROOT_ATTACK_BUSY = 1;
    static final int ROOT_ARM_RETURNED = 2;
    static final int ROOT_DEFEAT_RELEASE = 4;
    static final int ROOT_OUTWARD_BUSY = 6;
    static final int ROOT_PALETTE_REQUEST = 7;

    private enum Phase {
        CAMERA_APPROACH, WAIT_PLUNGER, MUSIC_WAIT, ACTIVE, DEFEAT_WAIT,
        END_SIGN_WAIT, END_SIGN_AWAIT_RESULTS, END_SIGN_AWAIT_ACT
    }
    private static final Phase[] PHASES = Phase.values();
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x23, 0x20, 0x1C);

    private int x;
    private int y;
    private int phaseOrdinal = Phase.CAMERA_APPROACH.ordinal();
    private int timer;
    private int remainingHits = 6;
    private int hitFlashTimer;
    private int rootControlBits;
    private int scriptedImpacts;
    private int storedCameraMaxX;
    private int storedCameraMaxY;
    private boolean initialized;
    private boolean initialChildrenSpawned;
    private boolean defeated;
    private boolean defeatAllocationsMade;
    private boolean bossSlotConverted;
    private boolean signSpawned;
    private boolean resultsObserved;
    private boolean act2SizeWorkersSpawned;
    private boolean rootHitPending;
    private int paletteRequestCount;
    private int paletteSpawnCount;
    private int armTableInvocations;
    private FbzMinibossArmChild leftArm;
    private FbzMinibossArmChild rightArm;

    public FbzMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "FBZMiniboss");
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public void update(int frameCounter, PlayableEntity mainPlayer) {
        if (!ensureInitialized()) return;
        switch (phase()) {
            case CAMERA_APPROACH -> updateCameraApproach();
            case WAIT_PLUNGER -> {
                if (rootBit(ROOT_FIGHT_STARTED)) startMusicWait();
            }
            case MUSIC_WAIT -> {
                if (--timer < 0) startFight();
            }
            case ACTIVE -> consumeAttackPaletteRequest();
            case DEFEAT_WAIT -> updateDefeatWait();
            case END_SIGN_WAIT -> {
                if (--timer < 0) spawnEndSign();
            }
            case END_SIGN_AWAIT_RESULTS -> awaitResults();
            case END_SIGN_AWAIT_ACT -> {
                if (tryServices() != null && services().gameState() != null
                        && services().gameState().isEndOfLevelFlag()) startAct2Sizes();
            }
        }
        updateHitState();
    }

    private boolean ensureInitialized() {
        if (initialized) return true;
        if (tryServices() != null && services().camera() != null && !cameraInOuterBounds()) return false;
        initialized = true;
        loadArtAndPalette();
        if (tryServices() != null) {
            if (services().camera() != null) {
                storedCameraMaxX = Short.toUnsignedInt(services().camera().getMaxX());
                storedCameraMaxY = Short.toUnsignedInt(services().camera().getMaxY());
                services().camera().setMaxYTarget((short) 0x540);
            }
            if (services().gameState() != null) {
                services().gameState().setCurrentBossId(Sonic3kObjectIds.FBZ_MINIBOSS);
            }
            setBossFlag(true);
        }
        spawnInitialChildren();
        // Obj_FBZMiniboss's setup routine installs the next callback and
        // returns. Dynamic-resize work starts on the following object update.
        return false;
    }

    private boolean cameraInOuterBounds() {
        int cx = Short.toUnsignedInt(services().camera().getX());
        int cy = Short.toUnsignedInt(services().camera().getY());
        return cx >= ACTIVATION_BOUNDS[2] && cx <= ACTIVATION_BOUNDS[3]
                && cy >= ACTIVATION_BOUNDS[0] && cy <= ACTIVATION_BOUNDS[1];
    }

    private void updateCameraApproach() {
        if (tryServices() == null || services().camera() == null) {
            phaseOrdinal = Phase.WAIT_PLUNGER.ordinal();
            return;
        }
        int cameraX = Short.toUnsignedInt(services().camera().getX());
        services().camera().setMinX((short) cameraX);
        // loc_85C7E waits until the live lower boundary has reached the target;
        // it must not force minY or the horizontal arena lock while maxY is
        // still below-screen of $540.
        if (Short.toUnsignedInt(services().camera().getMaxY()) > 0x540) return;
        services().camera().setMinY((short) 0x540);
        if (cameraX < 0x2E20) return;
        storedCameraMaxX = Short.toUnsignedInt(services().camera().getMaxX());
        services().camera().setMinX((short) 0x2E20);
        services().camera().setMaxX((short) 0x2EA0);
        services().camera().setMaxYTarget((short) 0x540);
        phaseOrdinal = Phase.WAIT_PLUNGER.ordinal();
    }

    private void startMusicWait() {
        phaseOrdinal = Phase.MUSIC_WAIT.ordinal();
        timer = MUSIC_WAIT;
        if (tryServices() != null) services().fadeOutMusic();
    }

    private void startFight() {
        phaseOrdinal = Phase.ACTIVE.ordinal();
        if (tryServices() != null) services().playMusic(Sonic3kMusic.MINIBOSS.id);
    }

    private void consumeAttackPaletteRequest() {
        if (!clearRootBit(ROOT_PALETTE_REQUEST)) return;
        paletteRequestCount++;
        if (tryServices() == null || services().objectManager() == null) return;
        FbzMinibossPaletteChild child = spawnChild(() -> new FbzMinibossPaletteChild(this));
        if (!child.isDestroyed()) paletteSpawnCount++;
    }

    private void updateHitState() {
        if (!rootHitPending || defeated) return;
        if (hitFlashTimer == 0) {
            remainingHits--;
            scriptedImpacts++;
            if (remainingHits == 0) {
                startDefeat();
                return;
            }
            hitFlashTimer = 0x20;
            if (tryServices() != null) services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        }
        int[] words = (hitFlashTimer & 1) == 0 ? FLASH_BRIGHT : FLASH_DARK;
        if (tryServices() != null && services().currentLevel() != null) {
            S3kPaletteWriteSupport.applyColors(services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                    services().graphicsManager(), S3kPaletteOwners.FBZ_MINIBOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE + 1, 1, FLASH_INDICES, words);
        }
        if (--hitFlashTimer == 0) rootHitPending = false;
    }

    private void startDefeat() {
        defeated = true;
        rootHitPending = false;
        phaseOrdinal = Phase.DEFEAT_WAIT.ordinal();
        if (tryServices() != null) {
            if (services().levelGamestate() != null) services().levelGamestate().pauseTimer();
            if (services().gameState() != null) services().gameState().setBossDefeatedFlag(true);
            if (services().objectManager() != null) spawnChild(() -> new FbzMinibossExplosionController(this));
        }
    }

    private void updateDefeatWait() {
        // loc_6EF88 inherits the already-negative Obj_Wait word; it converts on
        // its first subsequent call rather than introducing another 120-frame delay.
        if (defeatAllocationsMade) return;
        defeatAllocationsMade = true;
        bossSlotConverted = true;
        setRootBit(ROOT_DEFEAT_RELEASE);
        phaseOrdinal = Phase.END_SIGN_WAIT.ordinal();
        timer = END_SIGN_WAIT;
        if (tryServices() != null && services().gameState() != null) {
            // Obj_EndSignControl sets _unkFAA8 immediately, before its $77 wait.
            services().gameState().setEndOfLevelActive(true);
        }
        if (tryServices() == null || services().objectManager() == null) return;
        // AllocateObject is independent from every following CreateChild table.
        spawnFreeChild(() -> new SongFadeTransitionInstance(2 * 60, 0));
        // CreateChild6_Simple and the two CreateChild1_Normal tables are independent calls.
        spawnChild(() -> new FbzMinibossPrisonChild(this));
        spawnDefeatPrefix(true);
        spawnDefeatPrefix(false);
    }

    private void spawnDefeatPrefix(boolean animals) {
        for (int i = 0; i < 5; i++) {
            final int role = i;
            AbstractObjectInstance child = animals
                    ? spawnChild(() -> new FbzMinibossAnimalChild(this, role))
                    : spawnChild(() -> new FbzMinibossFragmentChild(this, role));
            if (child.isDestroyed()) return;
        }
    }

    private void spawnEndSign() {
        phaseOrdinal = Phase.END_SIGN_AWAIT_RESULTS.ordinal();
        setBossFlag(false);
        if (tryServices() == null) return;
        if (services().gameState() != null) {
            services().gameState().setCurrentBossId(0);
        }
        if (services().objectManager() != null) {
            // Child6_EndSign uses CreateChild6_Simple: allocate after the boss
            // slot and only report a sign when that allocation succeeds.
            S3kSignpostInstance sign = spawnChild(() -> new S3kSignpostInstance(x, 0));
            signSpawned = !sign.isDestroyed();
        }
    }

    private void awaitResults() {
        if (tryServices() == null || services().gameState() == null) return;
        if (!services().gameState().isEndOfLevelActive()) {
            resultsObserved = true;
            restoreAllPlayerControls();
            phaseOrdinal = Phase.END_SIGN_AWAIT_ACT.ordinal();
        }
    }

    private void restoreAllPlayerControls() {
        for (PlayableEntity participant : services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (participant instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
                sprite.setControlLocked(false);
                ObjectControlState.none().applyTo(sprite);
                sprite.setForcedAnimationId(-1);
            }
        }
    }

    private void startAct2Sizes() {
        if (act2SizeWorkersSpawned) return;
        act2SizeWorkersSpawned = true;
        var level = services().currentLevel();
        int targetMaxX = level != null ? level.getMaxX() : 0x6000;
        int targetMinY = level != null ? level.getMinY() : 0;
        int targetMaxY = level != null ? level.getMaxY() : 0x0B00;

        // Change_Act2Sizes publishes the stored Act 2 sizes first, including
        // Camera_target_max_Y_pos, without snapping any current boundary.
        storedCameraMaxX = targetMaxX;
        storedCameraMaxY = targetMaxY;
        services().camera().setMaxYTarget((short) targetMaxY);

        int[] kinds = {
                FbzAct2CameraResizeWorker.MAX_X,
                FbzAct2CameraResizeWorker.MIN_Y,
                FbzAct2CameraResizeWorker.MAX_Y
        };
        int[] targets = {targetMaxX, targetMinY, targetMaxY};
        for (int i = 0; i < kinds.length; i++) {
            int kind = kinds[i];
            int target = targets[i];
            FbzAct2CameraResizeWorker worker = spawnAfterCurrentSibling(
                    () -> new FbzAct2CameraResizeWorker(kind, target));
            if (worker.isDestroyed()) break; // CreateChild1 stops at first allocation failure.
        }
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    @Override
    public boolean isPersistent() {
        // Once the root slot converts to Obj_EndSignControl it remains a live
        // SST completion consumer across the long results/title presentation.
        // Change_Act2Sizes deletes it explicitly after allocating its worker
        // prefix; ordinary placement-window unload must not retire it first.
        return bossSlotConverted;
    }

    private void setBossFlag(boolean value) {
        if (tryServices() == null) return;
        if (services().levelEventProvider() instanceof AbstractLevelEventManager events) {
            events.setBossActive(value);
        }
    }

    private void loadArtAndPalette() {
        if (tryServices() == null) return;
        if (services().renderManager() != null
                && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.FBZ_MINIBOSS);
            provider.ensureBossExplosionArtLoaded();
        }
        try {
            byte[] palette = services().rom().readBytes(Sonic3kConstants.PAL_FBZ_MINIBOSS_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                    services().graphicsManager(), S3kPaletteOwners.FBZ_MINIBOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, palette);
        } catch (Exception ignored) {
            // ROM-less state-machine tests intentionally omit art.
        }
    }

    private void spawnInitialChildren() {
        if (initialChildrenSpawned || tryServices() == null || services().objectManager() == null) return;
        initialChildrenSpawned = true;
        AbstractObjectInstance child = spawnChild(() -> new FbzMinibossCoverChild(this, 0, -0x10, -8));
        if (child.isDestroyed()) return;
        child = spawnChild(() -> new FbzMinibossCoverChild(this, 1, 0x10, -8));
        if (child.isDestroyed()) return;
        child = spawnChild(() -> new FbzMinibossCoverChild(this, 2, 0, -8));
        if (child.isDestroyed()) return;
        child = spawnChild(() -> new FbzMinibossPlungerChild(this));
        if (child.isDestroyed()) return;
        child = spawnChild(() -> new FbzMinibossAimerChild(this));
        if (child.isDestroyed()) return;
        leftArm = spawnChild(() -> new FbzMinibossArmChild(this, 0));
        if (leftArm.isDestroyed()) return;
        rightArm = spawnChild(() -> new FbzMinibossArmChild(this, 1));
    }

    /** Root is not attackable; only the normal chain-terminal cycle publishes this signal. */
    public void onPlayerAttack(PlayableEntity ignored) { }

    boolean publishScriptedTerminalImpact() {
        if (defeated || rootHitPending) return false;
        rootHitPending = true;
        return true;
    }

    void requestAttackPalette() { setRootBit(ROOT_PALETTE_REQUEST); }
    boolean claimNormalAttack(FbzMinibossArmChild arm) {
        if (!rootBit(ROOT_FIGHT_STARTED) || rootBit(ROOT_ATTACK_BUSY)) return false;
        setRootBit(ROOT_ATTACK_BUSY);
        arm.setControlBit(FbzMinibossArmChild.ARM_NORMAL_ATTACK);
        requestAttackPalette();
        return true;
    }
    boolean claimOutwardAttack(FbzMinibossArmChild arm) {
        if (rootBit(ROOT_FIGHT_STARTED) || rootBit(ROOT_OUTWARD_BUSY)) return false;
        setRootBit(ROOT_OUTWARD_BUSY);
        arm.setControlBit(FbzMinibossArmChild.ARM_OUTWARD_ATTACK);
        requestAttackPalette();
        return true;
    }
    void releaseNormalAttack() { clearRootBit(ROOT_ATTACK_BUSY); }
    void releaseOutwardAttack() { clearRootBit(ROOT_OUTWARD_BUSY); }
    void activateFromNativeP1Plunger() { setRootBit(ROOT_FIGHT_STARTED); }

    boolean rootBit(int bit) { return (rootControlBits & (1 << bit)) != 0; }
    void setRootBit(int bit) { rootControlBits |= 1 << bit; }
    boolean clearRootBit(int bit) {
        int mask = 1 << bit;
        boolean wasSet = (rootControlBits & mask) != 0;
        rootControlBits &= ~mask;
        return wasSet;
    }
    private Phase phase() { return PHASES[phaseOrdinal]; }

    public int remainingHits() { return remainingHits; }
    public int hitFlashUpdatesRemaining() { return hitFlashTimer; }
    public int scriptedImpactCount() { return scriptedImpacts; }
    public boolean isDefeated() { return defeated; }
    public boolean isPlungerStarted() { return rootBit(ROOT_FIGHT_STARTED); }
    public String phaseName() { return phase().name(); }
    boolean isFightActive() { return phase() == Phase.ACTIVE; }
    boolean hasConvertedToEndSign() { return bossSlotConverted; }
    boolean signSpawned() { return signSpawned; }
    boolean resultsObserved() { return resultsObserved; }
    int storedCameraMaxX() { return storedCameraMaxX; }
    int storedCameraMaxY() { return storedCameraMaxY; }
    int paletteRequestCount() { return paletteRequestCount; }
    int paletteSpawnCount() { return paletteSpawnCount; }
    int armTableInvocationCount() { return armTableInvocations; }
    void noteArmTableInvocation() { armTableInvocations++; }
    void relinkArm(FbzMinibossArmChild arm) { if (arm.side() == 0) leftArm = arm; else rightArm = arm; }
    public static int[] activationBounds() { return ACTIVATION_BOUNDS.clone(); }
    public static int[] lockBounds() { return LOCK_BOUNDS.clone(); }
    public static int musicWaitUpdates() { return MUSIC_WAIT + 1; }
    public static String[] initialRoleNames() { return INITIAL_ROLES.clone(); }
    public static int fullPersistentGraphSlots() { return 18; }
    public static int peakGraphSlots() { return 19; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return SOLID_PARAMS; }
    @Override public boolean usesInclusiveRightEdge() { return true; }
    @Override public boolean isSolidFor(PlayableEntity player) { return !bossSlotConverted; }
    @Override public boolean skipsCpuSidekickWhenRenderFlagOffScreen() { return true; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int getPriorityBucket() { return 4; }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY) {
        x = (x + offsetX) & 0xFFFF;
        y = (y + offsetY) & 0xFFFF;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (bossSlotConverted) return;
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_MINIBOSS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, x, y, false, false);
    }
}
