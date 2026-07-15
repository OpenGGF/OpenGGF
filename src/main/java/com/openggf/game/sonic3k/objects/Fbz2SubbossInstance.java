package com.openggf.game.sonic3k.objects;

import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.*;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Locked-on object {@code $AB}, {@code Obj_FBZ2Subboss} (sonic3k.asm:148033-148695). */
public final class Fbz2SubbossInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable,
        SpawnRewindRecreatable, RomWorldPositionedObject {
    private static final int[] ACTIVATION_BOUNDS = {0x560, 0x660, 0x2900, 0x2C00};
    private static final int[] FLASH_INDICES = {12, 13, 14};
    private static final int[] FLASH_DARK = {0x866, 0x644, 0x020};
    private static final int[] FLASH_BRIGHT = {0xEEE, 0xEEE, 0xEEE};
    private static final Logger LOG = Logger.getLogger(Fbz2SubbossInstance.class.getName());
    static final TouchResponseProfile CONTINUOUS_ENEMY_TOUCH_PROFILE = TouchResponseProfile.fromCanonical(
            new com.openggf.game.profiles.touchresponse.TouchResponseProfile(
                    com.openggf.game.profiles.touchresponse.TouchCategoryDecodeMode.NORMAL,
                    true,
                    true,
                    false,
                    com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability.NONE,
                    0,
                    com.openggf.game.profiles.touchresponse.TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                    com.openggf.game.profiles.touchresponse.TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                    com.openggf.game.profiles.touchresponse.TouchOverlapStopPolicy
                            .STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS));
    static final int CONTROL_LASER_READY = 1;
    static final int CONTROL_RELEASE_SOLIDS = 2;
    static final int CONTROL_MOVE_RIGHT = 3;
    static final int CONTROL_CHARACTER_ESCAPE = 4;
    static final int CONTROL_DELETE_MASK = 5;
    static final int STATUS_CHARACTER_DEFEAT = 7;
    static final int STATUS_CHARACTER_FACE = 6;

    enum Phase { RANGE_INIT, WAIT_P1, DROP, PRE_LASER_WAIT, ACTIVE, CYCLE_WAIT,
        DEFEAT_QUEUE_WAIT, DEFEAT_RESTORE_WAIT, RELEASE_CULL }
    private static final Phase[] PHASES = Phase.values();

    private int x;
    private int y;
    private int yFixed;
    private int xVelocity;
    private int phaseOrdinal;
    private int timer;
    /** Native byte $39 starts at six and defeats after decrementing below zero. */
    private int cycleCounter = 6;
    private int collisionFlags = 0x1C;
    private int collisionProperty = 0x7F;
    private int hitFlashTimer;
    private int controlBits;
    private int statusBits;
    private int storedCameraMaxY;
    private boolean initialized;
    private boolean sideTableAttempted;
    private boolean defeated;
    private boolean nativeDeletePending;
    private int defeatArtQueuedCount;
    private String defeatArtQueueFailure;
    private String paletteLoadFailure;
    private int releaseRawPlcAttempted;
    private int releaseRawPlcApplied;
    private String releaseRawPlcFailure;
    private PlayerCharacter character = PlayerCharacter.SONIC_AND_TAILS;
    private Fbz2SubbossCornerChild upperLeft;
    private Fbz2SubbossCornerChild upperRight;

    public Fbz2SubbossInstance(ObjectSpawn spawn) {
        super(spawn, "FBZ2Subboss");
        x = spawn.x();
        y = spawn.y();
        yFixed = y << 8;
    }

    @Override public void update(int frameCounter, PlayableEntity mainPlayer) {
        if (!initialized) { initialize(); return; }
        if (!defeated && !cameraInActivationRange()) {
            destroyRespawnableIfPastNativeCameraWindow();
            return;
        }
        switch (phase()) {
            case RANGE_INIT -> { }
            case WAIT_P1 -> {
                if (mainPlayer != null && Math.abs((mainPlayer.getCentreX() & 0xFFFF) - x) < 0x18) startDrop();
            }
            case DROP -> {
                yFixed += 0x80;
                y = yFixed >> 8;
                if (--timer < 0) { phaseOrdinal = Phase.PRE_LASER_WAIT.ordinal(); timer = 0x3F; }
            }
            case PRE_LASER_WAIT -> {
                if (--timer < 0) startLaser(mainPlayer);
            }
            case ACTIVE -> {
                if (bit(controlBits, CONTROL_LASER_READY)) {
                    controlBits &= ~(1 << CONTROL_LASER_READY);
                    phaseOrdinal = Phase.CYCLE_WAIT.ordinal(); timer = 0x7F;
                } else if ((frameCounter & 0x1F) == 0 && mainPlayer != null) {
                    aimAt(mainPlayer);
                }
                moveWithinCorners();
            }
            case CYCLE_WAIT -> { if (--timer < 0) completeLaserCycle(); }
            case DEFEAT_QUEUE_WAIT -> { if (--timer < 0) beginCharacterEscape(); }
            case DEFEAT_RESTORE_WAIT -> { if (--timer < 0) releaseArena(); }
            case RELEASE_CULL -> {
                if (tryServices() != null && services().camera() != null) {
                    services().camera().setMinX(services().camera().getX());
                    if (nativeDeletePending) ObjectLifetimeOps.expireDynamic(this);
                    else nativeDeletePending = nativeDeleteXY();
                }
            }
        }
        updateHitFlash();
    }

    private void initialize() {
        if (tryServices() != null && services().camera() != null) {
            if (!cameraInActivationRange()) {
                destroyRespawnableIfPastNativeCameraWindow();
                return;
            }
            storedCameraMaxY = Short.toUnsignedInt(services().camera().getMaxYTarget());
            services().camera().setMinX((short) 0x2900);
            services().camera().setMaxYTarget((short) 0x5E0);
        }
        initialized = true;
        phaseOrdinal = Phase.WAIT_P1.ordinal();
        character = resolveCharacter();
        setBossFlag(true);
        if (tryServices() != null && services().gameState() != null)
            services().gameState().setCurrentBossId(Sonic3kObjectIds.FBZ2_SUBBOSS);
        loadArtAndPalette();
        if (tryServices() != null && services().objectManager() != null) {
            spawnFreeChild(() -> new SongFadeTransitionInstance(90, Sonic3kMusic.MINIBOSS_S3.id));
            AbstractObjectInstance child = spawnChild(() -> new Fbz2SubbossMachineChild(this));
            if (!child.isDestroyed()) child = spawnChild(() -> new Fbz2SubbossCharacterChild(this, character));
            child = spawnChild(() -> new Fbz2SubbossSpriteMaskChild(this));
            spawnCornerTable();
        }
    }

    private void spawnCornerTable() {
        for (int subtype : Fbz2SubbossCornerChild.nativeSubtypes()) {
            Fbz2SubbossCornerChild child = spawnChild(() -> new Fbz2SubbossCornerChild(this, subtype));
            if (child.isDestroyed()) return;
            if (subtype == 0) upperLeft = child;
            if (subtype == 2) upperRight = child;
        }
    }

    private void startDrop() {
        phaseOrdinal = Phase.DROP.ordinal(); timer = 0x37;
        if (!sideTableAttempted && tryServices() != null && services().objectManager() != null) {
            sideTableAttempted = true;
            for (int subtype : Fbz2SubbossSolidSideChild.nativeSubtypes()) {
                AbstractObjectInstance child = spawnChild(() -> new Fbz2SubbossSolidSideChild(this, subtype));
                if (child.isDestroyed()) break;
            }
        }
    }

    private void startLaser(PlayableEntity mainPlayer) {
        phaseOrdinal = Phase.ACTIVE.ordinal();
        aimAt(mainPlayer);
        if (tryServices() != null && services().objectManager() != null)
            spawnChild(() -> new Fbz2SubbossLaserChild(this));
    }

    private void aimAt(PlayableEntity mainPlayer) {
        if (mainPlayer != null)
            xVelocity = (mainPlayer.getCentreX() & 0xFFFF) <= x ? -0x100 : 0x100;
    }

    private void moveWithinCorners() {
        int min = upperLeft == null ? x - 0xB0 : upperLeft.getX() + 0x20;
        int max = upperRight == null ? x + 0xB0 : upperRight.getX() - 0x20;
        if (x < min) xVelocity = 0x100;
        else if (x >= max) xVelocity = -0x100;
        x += xVelocity >> 8;
    }

    private void completeLaserCycle() {
        cycleCounter = (byte) (cycleCounter - 1);
        if (cycleCounter < 0) { startDefeat(); return; }
        controlBits |= 1 << CONTROL_MOVE_RIGHT;
        statusBits &= ~(1 << STATUS_CHARACTER_FACE);
        startLaser(null);
    }

    private void startDefeat() {
        if (defeated) return;
        defeated = true;
        statusBits |= 1 << STATUS_CHARACTER_DEFEAT;
        controlBits |= 1 << CONTROL_DELETE_MASK;
        phaseOrdinal = Phase.DEFEAT_QUEUE_WAIT.ordinal(); timer = 0x5F;
        applyFlashColors(FLASH_DARK);
        enqueueDefeatArt();
    }

    private void enqueueDefeatArt() {
        if (tryServices() == null) return;
        defeatArtQueuedCount = 0;
        defeatArtQueueFailure = null;
        if (services().kosinskiModuleQueue() == null) {
            defeatArtQueueFailure = "KosM queue or ROM owner unavailable";
            LOG.warning("FBZ2 subboss defeat art was not queued: " + defeatArtQueueFailure);
            return;
        }
        try {
            var rom = services().rom();
            if (rom == null) {
                defeatArtQueueFailure = "KosM queue or ROM owner unavailable";
                LOG.warning("FBZ2 subboss defeat art was not queued: " + defeatArtQueueFailure);
                return;
            }
            Sonic3kPlcLoader.bindRuntimePatternDmaTarget(services().kosinskiModuleQueue(), services());
            for (Sonic3kPlcLoader.KosmQueueEntry entry : Sonic3kPlcLoader.fbz2SubbossDefeatKosmEntries()) {
                if (!services().kosinskiModuleQueue().enqueue(
                        rom, entry.sourceAddress(), entry.destinationVramBytes())) {
                    defeatArtQueueFailure = "KosM capacity exhausted after "
                            + defeatArtQueuedCount + " FBZ2 defeat entries";
                    LOG.warning("FBZ2 subboss defeat art prefix only: " + defeatArtQueueFailure);
                    break;
                }
                defeatArtQueuedCount++;
            }
        } catch (IOException failure) {
            defeatArtQueueFailure = failure.getMessage();
            LOG.log(Level.WARNING, "Could not inspect FBZ2 subboss defeat KosM archive", failure);
        }
    }

    private void beginCharacterEscape() {
        controlBits |= 1 << CONTROL_CHARACTER_ESCAPE;
        phaseOrdinal = Phase.DEFEAT_RESTORE_WAIT.ordinal(); timer = 0x5F;
        if (tryServices() != null && services().objectManager() != null)
            spawnFreeChild(() -> new SongFadeTransitionInstance(120, Sonic3kMusic.FBZ2.id));
    }

    private void releaseArena() {
        controlBits |= 1 << CONTROL_RELEASE_SOLIDS;
        setBossFlag(false);
        if (tryServices() != null) {
            if (services().gameState() != null) services().gameState().setCurrentBossId(0);
            Sonic3kPlcLoader.RawPlcApplyResult result =
                    Sonic3kPlcLoader.applyRawQuietly(Sonic3kPlcLoader.monitorPlcEntries(), services());
            releaseRawPlcAttempted = result.attemptedEntries();
            releaseRawPlcApplied = result.appliedEntries();
            releaseRawPlcFailure = result.failure();
        }
        phaseOrdinal = Phase.RELEASE_CULL.ordinal();
    }

    private void updateHitFlash() {
        if (collisionFlags != 0) return;
        if (hitFlashTimer == 0) {
            hitFlashTimer = 0x20;
            collisionProperty = 0x7E;
            if (tryServices() != null) services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        }
        int[] colors = defeated ? FLASH_DARK : (hitFlashTimer & 1) == 0 ? FLASH_BRIGHT : FLASH_DARK;
        applyFlashColors(colors);
        if (--hitFlashTimer == 0) { collisionFlags = 0x1C; collisionProperty = 0x7F; }
    }

    private void applyFlashColors(int[] colors) {
        if (tryServices() != null && services().currentLevel() != null)
            S3kPaletteWriteSupport.applyColors(services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                    services().graphicsManager(), S3kPaletteOwners.FBZ2_SUBBOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE + 1, 1, FLASH_INDICES, colors);
    }

    private boolean cameraInActivationRange() {
        if (tryServices() == null || services().camera() == null) return true;
        int cx=Short.toUnsignedInt(services().camera().getX());
        int cy=Short.toUnsignedInt(services().camera().getY());
        return cameraInActivationRange(cx, cy);
    }

    private boolean nativeDeleteXY() {
        int cameraX=Short.toUnsignedInt(services().camera().getX());
        int cameraY=Short.toUnsignedInt(services().camera().getY());
        int dy=(y-cameraY+0x80)&0xFFFF;
        return !nativeSpriteCheckDeleteXKeepsAlive(x, cameraX)||dy>0x200;
    }

    private void destroyRespawnableIfPastNativeCameraWindow() {
        if (tryServices() == null || services().camera() == null) return;
        int cameraX = Short.toUnsignedInt(services().camera().getX());
        if (!nativeSpriteCheckDeleteXKeepsAlive(x, cameraX))
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);
    }

    private PlayerCharacter resolveCharacter() {
        if (tryServices() == null) return PlayerCharacter.SONIC_AND_TAILS;
        if (services().configuration() == null)
            return PlayerCharacter.SONIC_AND_TAILS;
        return S3kRuntimeStates.resolvePlayerCharacter(services().zoneRuntimeRegistry(), services().configuration());
    }

    private void loadArtAndPalette() {
        if (tryServices() == null) return;
        if (services().renderManager() != null && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider p) {
            p.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.FBZ2_SUBBOSS);
            p.ensureStandaloneArtLoaded(Fbz2SubbossCharacterChild.standArtKey(character));
            p.ensureStandaloneArtLoaded(Fbz2SubbossCharacterChild.runArtKey(character));
            p.ensureBossExplosionArtLoaded();
        }
        paletteLoadFailure = null;
        try {
            var rom = services().rom();
            if (rom == null) {
                paletteLoadFailure = "ROM owner unavailable";
                LOG.warning("FBZ2 subboss palette not loaded: " + paletteLoadFailure);
                return;
            }
            byte[] palette = rom.readBytes(Sonic3kConstants.PAL_FBZ2_SUBBOSS_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                    services().graphicsManager(), S3kPaletteOwners.FBZ2_SUBBOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, palette);
        } catch (IOException failure) {
            paletteLoadFailure = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            LOG.log(Level.WARNING, "Could not load FBZ2 subboss palette", failure);
        }
    }

    private void setBossFlag(boolean active) {
        if (tryServices() != null && services().levelEventProvider() instanceof AbstractLevelEventManager events)
            events.setBossActive(active);
    }

    @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (!defeated && collisionFlags != 0) collisionFlags = 0;
    }
    public void onPlayerAttack(PlayableEntity player) { onPlayerAttack(player, null); }
    @Override public int getCollisionFlags() { return collisionFlags; }
    @Override public int getCollisionProperty() { return collisionProperty; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public TouchResponseProfile getTouchResponseProfile() {
        return CONTINUOUS_ENEMY_TOUCH_PROFILE;
    }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return CONTINUOUS_ENEMY_TOUCH_PROFILE;
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public boolean isHighPriority() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ2_SUBBOSS);
        if (r != null && r.isReady()) r.drawFrameIndex(0, x, y, false, false);
    }
    @Override public void offsetNativePositionWordsPreserveSubpixel(int dx, int dy) {
        x = (x + dx) & 0xFFFF; y = (y + dy) & 0xFFFF; yFixed = (y << 8) | (yFixed & 0xFF);
    }
    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) Fbz2SubbossRewindLinks.settle(services().objectManager(), getSlotIndex());
    }
    Phase phase() { return PHASES[phaseOrdinal]; }
    boolean controlBit(int bit) { return bit(controlBits, bit); }
    boolean statusBit(int bit) { return bit(statusBits, bit); }
    void setControlBit(int bit) { controlBits |= 1 << bit; }
    void clearControlBit(int bit) { controlBits &= ~(1 << bit); }
    void setStatusBit(int bit) { statusBits |= 1 << bit; }
    void setUpperLeft(Fbz2SubbossCornerChild c) { upperLeft = c; }
    void setUpperRight(Fbz2SubbossCornerChild c) { upperRight = c; }
    Fbz2SubbossCornerChild upperLeft() { return upperLeft; }
    Fbz2SubbossCornerChild upperRight() { return upperRight; }
    int cyclesRemaining() { return cycleCounter + 1; }
    boolean isDefeated() { return defeated; }
    int defeatArtQueuedCount() { return defeatArtQueuedCount; }
    String defeatArtQueueFailure() { return defeatArtQueueFailure; }
    String paletteLoadFailure() { return paletteLoadFailure; }
    Sonic3kPlcLoader.RawPlcApplyResult releaseRawPlcResultForTest() {
        return new Sonic3kPlcLoader.RawPlcApplyResult(
                releaseRawPlcAttempted, releaseRawPlcApplied, releaseRawPlcFailure);
    }
    int hitFlashUpdatesRemaining() { return hitFlashTimer; }
    int waitWordForTest() { return timer; }
    void completeLaserCycleForTest() { completeLaserCycle(); }
    static int[] activationBounds() { return ACTIVATION_BOUNDS.clone(); }
    static boolean cameraInActivationRange(int cameraX, int cameraY) {
        return cameraX >= ACTIVATION_BOUNDS[2] && cameraX <= ACTIVATION_BOUNDS[3]
                && cameraY >= ACTIVATION_BOUNDS[0] && cameraY <= ACTIVATION_BOUNDS[1];
    }
    static boolean nativeSpriteCheckDeleteXKeepsAlive(int objectX, int cameraX) {
        int coarseCameraBack = (cameraX - 0x80) & 0xFF80;
        int unsignedDistance = ((objectX & 0xFF80) - coarseCameraBack) & 0xFFFF;
        return unsignedDistance <= 0x280;
    }
    static int triggerDistanceExclusive() { return 0x18; }
    static int dropUpdates() { return 0x38; }
    static int preLaserWaitUpdates() { return 0x40; }
    static int cycleWaitUpdates() { return 0x80; }
    static int defeatWaitUpdates() { return 0x60; }
    String phaseName() { return phase().name(); }
    private static boolean bit(int bits, int bit) { return (bits & (1 << bit)) != 0; }
}
