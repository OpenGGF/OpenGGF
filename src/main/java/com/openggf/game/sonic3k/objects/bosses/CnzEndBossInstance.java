package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.objects.S3kBossExplosionController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.Palette;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.physics.SwingMotion;
import com.openggf.sprites.NativePositionOps;

import java.util.List;
import java.io.IOException;

/**
 * ROM port of CNZ Act 2's {@code Obj_CNZEndBoss} magnetic boss.
 *
 * <p>ROM anchor: {@code Obj_CNZEndBoss}.
 *
 * <p>The eight native routines cover the entry swing, player tracking, magnet
 * drop/bounce, alignment, charge, attraction, descent and return. The magnetic
 * arms retain their four phase offsets from {@code ChildObjDat_6EDD4}; the
 * attraction curve is the exact {@code dword_6EC7E} table.
 */
public final class CnzEndBossInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable, SpawnRewindRecreatable {
    private static final int START_CAMERA_X_MIN = 0x4660;
    private static final int START_CAMERA_X_MAX = 0x4860;
    private static final int STORED_BOUND_CAPSULE_DELTA = 0x190;
    private static final int STORED_BOUND_CANNON_DELTA = 0x310;
    private static final int CAPSULE_X = 0x4990;
    private static final int CAPSULE_Y = 0x02E0;
    private static final int CANNON_TRIGGER_X = 0x4A30;
    private static final int CANNON_X = 0x4B20;
    private static final int CANNON_Y = 0x02A8;
    private static final int CANNON_LAUNCH_WAIT = 0xBF;
    private static final int ICZ_START_ZONE_WORD = 0x500;
    private static final int HIT_COUNT = 8;
    private static final int COLLISION_FLAGS = 0x06;
    private static final int HIT_INVULNERABILITY_FRAMES = 0x20;
    private static final int SWING_ACCEL = 0x10;
    private static final int SWING_MAX = 0xC0;
    private static final int TRACK_SPEED = 0x100;
    private static final int ENTRY_WAIT = 0x7F;
    private static final int TRACK_WAIT = 3 * 60;
    private static final int CHARGE_WAIT = 0xBF;
    private static final int MAGNET_ACTIVE_WAIT = 0xFF;
    private static final int[] MAGNET_PULL_16_16 = {
            0x28000, 0x20000, 0x1C000, 0x18000, 0x14000, 0x10000, 0x0C000, 0x08000
    };

    enum Routine {
        WAIT_CAMERA, CAMERA_LOCK, ENTRY, TRACK, MAGNET_DROP, ALIGN, CHARGE, DESCEND, ASCEND, DEFEATED
    }

    private int centreX;
    private int centreY;
    private int xSubpixel;
    private int ySubpixel;
    private int xVelocity;
    private int yVelocity;
    private int swingVelocity;
    private boolean swingDown;
    private boolean facingRight;
    private int routineTimer;
    private Routine routine = Routine.WAIT_CAMERA;
    private int savedHoverY;
    private boolean magneticFieldActive;
    private int mappingFrame;
    private boolean startupComplete;
    private int cameraLockTimer;
    private int savedCameraMinX;
    private int savedCameraMaxX;
    private int savedCameraMinY;
    private int savedCameraMaxY;
    private CnzEndBossMagnetChild magnetChild;

    private boolean defeatHandoffComplete;
    private int hitCount = HIT_COUNT;
    private int hitInvulnerabilityTimer;
    private boolean capsuleResultsComplete;
    private boolean postCapsuleReleaseComplete;
    private boolean cannonSpawned;
    private boolean cannonArmed;
    private boolean cannonLaunched;
    private boolean transitionRequested;
    private int cannonLaunchTimer = -1;
    private int postCapsuleReleaseCountForTest;
    private CnzCannonInstance endCannon;
    @com.openggf.game.rewind.RewindTransient(
            reason = "Visual-only explosion scheduler; defeatWaitTimer owns gameplay progression.")
    private S3kBossExplosionController defeatExplosions;
    private int defeatWaitTimer;
    private int storedBoundBase;

    public CnzEndBossInstance(ObjectSpawn spawn) {
        super(spawn, "CNZEndBoss");
        this.centreX = spawn.x();
        this.centreY = spawn.y();
    }

    @Override
    public int getX() {
        return centreX - 0x40;
    }

    @Override
    public int getY() {
        return centreY - 0x14;
    }

    public int getCentreX() {
        return centreX;
    }

    public int getCentreY() {
        return centreY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (hitInvulnerabilityTimer > 0) {
            hitInvulnerabilityTimer--;
        }
        if (!defeatHandoffComplete) {
            updateNativeBoss(player);
        }
        updateDefeatWait();
        updatePostDefeatSequence(frameCounter, player);
    }

    private void updateNativeBoss(PlayableEntity player) {
        switch (routine) {
            case WAIT_CAMERA -> updateCameraGate();
            case CAMERA_LOCK -> updateCameraLock();
            case ENTRY -> {
                swingAndMove();
                if (waitExpired()) beginTracking();
            }
            case TRACK -> {
                trackClosestPlayer(player);
                swingAndMove();
                if (waitExpired()) beginMagnetDrop();
            }
            case MAGNET_DROP -> updateMagnetDrop();
            case ALIGN -> updateAlign();
            case CHARGE -> updateCharge(player);
            case DESCEND -> updateDescent();
            case ASCEND -> updateAscent();
            case DEFEATED -> { }
        }
    }

    private void updateCameraGate() {
        if (services().camera() == null) return;
        int cameraX = services().camera().getX() & 0xFFFF;
        int cameraY = services().camera().getY() & 0xFFFF;
        if (cameraX < START_CAMERA_X_MIN || cameraX > START_CAMERA_X_MAX
                || cameraY < 0 || cameraY > 0x300) return;

        savedCameraMinX = services().camera().getMinX() & 0xFFFF;
        savedCameraMaxX = services().camera().getMaxX() & 0xFFFF;
        savedCameraMinY = services().camera().getMinY() & 0xFFFF;
        savedCameraMaxY = services().camera().getMaxY() & 0xFFFF;
        setStoredCameraBounds(savedCameraMinX, savedCameraMaxX, savedCameraMinY, savedCameraMaxY);
        services().camera().setMinY((short) 0x0240);
        services().camera().setMaxYTarget((short) 0x0240);
        services().camera().setMinX((short) 0x4760);
        services().camera().setMaxX((short) 0x47E0);
        cameraLockTimer = 2 * 60;
        routine = Routine.CAMERA_LOCK;
        S3kCnzEventWriteSupport.setBossFlag(services(), true);
        services().fadeOutMusic();
        cnzArtProvider().queueCnzEndBossArt();
    }

    private void updateCameraLock() {
        if (cameraLockTimer-- > 0 || !cnzArtProvider().isCnzEndBossArtComplete()) {
            return;
        }
        startupComplete = true;
        routine = Routine.ENTRY;
        routineTimer = ENTRY_WAIT;
        savedHoverY = centreY;
        xVelocity = -TRACK_SPEED;
        swingVelocity = SWING_MAX;
        swingDown = false;
        services().gameState().setCurrentBossId(Sonic3kObjectIds.CNZ_END_BOSS);
        services().playMusic(Sonic3kMusic.BOSS.id);
        installBossPalette();
        spawnNativeChildren();
    }

    private Sonic3kObjectArtProvider cnzArtProvider() {
        if (services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            return provider;
        }
        throw new IllegalStateException("CNZ end boss requires the S3K object-art provider");
    }

    private void setStoredCameraBounds(int minX, int maxX, int minY, int maxY) {
        Object provider = services().levelEventProvider();
        if (provider instanceof Sonic3kLevelEventManager manager && manager.getCnzEvents() != null) {
            manager.getCnzEvents().setCameraStoredMinXPos((short) minX);
            manager.getCnzEvents().setCameraStoredMaxXPos((short) maxX);
            manager.getCnzEvents().setCameraStoredMinYPos((short) minY);
            manager.getCnzEvents().setCameraStoredMaxYPos((short) maxY);
        }
    }

    private void spawnNativeChildren() {
        magnetChild = spawnChild(() -> new CnzEndBossMagnetChild(this));
        for (int subtype = 0; subtype < 4; subtype++) {
            int phase = subtype << 5;
            spawnChild(() -> new CnzEndBossArmChild(this, phase));
        }
    }

    private void installBossPalette() {
        if (services().levelManager() == null || services().levelManager().getCurrentLevel() == null) return;
        byte[] bytes;
        try {
            bytes = services().rom().readBytes(Sonic3kConstants.PAL_CNZ_END_BOSS_ADDR, 32);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read Pal_CNZEndBoss", e);
        }
        if (bytes == null || bytes.length < 32) return;
        Palette palette = new Palette();
        palette.fromSegaFormat(bytes);
        S3kPaletteWriteSupport.applyPaletteLine(
                services().paletteOwnershipRegistryOrNull(),
                services().levelManager().getCurrentLevel(),
                services().graphicsManager(),
                S3kPaletteOwners.CNZ_END_BOSS,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                0,
                palette,
                false);
    }

    private void beginTracking() {
        routine = Routine.TRACK;
        routineTimer = TRACK_WAIT;
    }

    private void trackClosestPlayer(PlayableEntity fallback) {
        PlayableEntity target = fallback;
        int best = fallback == null ? Integer.MAX_VALUE : Math.abs(centreX - fallback.getCentreX());
        for (PlayableEntity candidate : services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            int distance = Math.abs(centreX - candidate.getCentreX());
            if (distance < best) {
                best = distance;
                target = candidate;
            }
        }
        if (target == null || best < 0x10) return;
        facingRight = target.getCentreX() > centreX;
        xVelocity = facingRight ? TRACK_SPEED : -TRACK_SPEED;
    }

    private void beginMagnetDrop() {
        routine = Routine.MAGNET_DROP;
        xVelocity = 0;
        magnetChild.beginDrop();
    }

    private void updateMagnetDrop() {
        swingAndMove();
        if (magnetChild.isLanded()) routine = Routine.ALIGN;
    }

    private void updateAlign() {
        swingAndMove();
        int magnetCentreX = magnetChild.getCentreX();
        if (centreX != magnetCentreX) {
            centreX += Integer.compare(magnetCentreX, centreX);
            facingRight = magnetCentreX > centreX;
            return;
        }
        routine = Routine.CHARGE;
        routineTimer = CHARGE_WAIT;
    }

    private void updateCharge(PlayableEntity player) {
        swingAndMove();
        if (!magneticFieldActive) {
            if (!waitExpired()) return;
            magneticFieldActive = true;
            routineTimer = MAGNET_ACTIVE_WAIT;
            spawnChild(() -> new CnzEndBossFieldChild(this, -0x0C));
            spawnChild(() -> new CnzEndBossFieldChild(this, 0x0C));
            return;
        }
        applyMagnetPull();
        if (!waitExpired()) return;
        magneticFieldActive = false;
        routine = Routine.DESCEND;
    }

    private void applyMagnetPull() {
        for (PlayableEntity candidate : services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            int delta = centreX - candidate.getCentreX();
            int distance = Math.min(Math.abs(delta) & 0xFFC0, 0x1C0);
            int index = Math.min(distance >> 6, MAGNET_PULL_16_16.length - 1);
            int pull16 = MAGNET_PULL_16_16[index];
            int signed = delta >= 0 ? pull16 : -pull16;
            if (candidate instanceof AbstractPlayableSprite sprite) {
                NativePositionOps.addXPos16_16(sprite, signed);
            }
        }
    }

    private void updateDescent() {
        swingAndMove();
        centreY++;
        int target = magnetChild.getCentreY() - 0x14;
        if (centreY < target) return;
        centreY = target;
        routine = Routine.ASCEND;
    }

    private void updateAscent() {
        if (centreY > savedHoverY) {
            centreY--;
            return;
        }
        centreY = savedHoverY;
        swingVelocity = SWING_MAX;
        swingDown = false;
        beginTracking();
        magnetChild.resetForNextCycle();
    }

    private void swingAndMove() {
        SwingMotion.Result swing = SwingMotion.update(SWING_ACCEL, swingVelocity, SWING_MAX, swingDown);
        swingVelocity = swing.velocity();
        swingDown = swing.directionDown();
        yVelocity = swingVelocity;
        xSubpixel += xVelocity;
        ySubpixel += yVelocity;
        centreX += xSubpixel >> 8;
        centreY += ySubpixel >> 8;
        xSubpixel &= 0xFF;
        ySubpixel &= 0xFF;
    }

    private boolean waitExpired() {
        return --routineTimer < 0;
    }

    @Override
    public int getCollisionFlags() {
        if (defeatHandoffComplete || hitInvulnerabilityTimer > 0 || hitCount <= 0) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return hitCount;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (defeatHandoffComplete || hitInvulnerabilityTimer > 0 || hitCount <= 0) {
            return;
        }
        hitCount--;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        if (hitCount <= 0) {
            beginDefeatSequence();
        } else {
            hitInvulnerabilityTimer = HIT_INVULNERABILITY_FRAMES;
        }
    }

    /**
     * Returns whether the wider CNZ script has already declared this boss slot
     * active through shared state.
     *
     * <p>Task 8 intentionally does not let the promoted production slot claim
     * boss mode on its own. The real startup gate belongs to the later attack
     * choreography and CNZ event flow; this predicate also supports tests that
     * enter at the native externally-owned boss seam.
     */
    private boolean isBossModeAlreadyOwnedExternally() {
        if (services().gameState().getCurrentBossId() == Sonic3kObjectIds.CNZ_END_BOSS) {
            return true;
        }
        Object provider = services().levelEventProvider();
        if (provider instanceof Sonic3kLevelEventManager manager) {
            Sonic3kCNZEvents events = manager.getCnzEvents();
            return events != null && events.isBossFlag();
        }
        return false;
    }

    /**
     * Verified Task 8 defeat handoff.
     *
     * <p>This is the honest boundary from the ROM findings:
     * <ol>
     *   <li>clear {@code Boss_flag}</li>
     *   <li>widen the camera max so the player can move past the boss arena</li>
     *   <li>spawn the CNZ-local egg capsule wrapper</li>
     *   <li>stay alive as the post-results cannon-launch controller</li>
     * </ol>
     */
    private void beginDefeatSequence() {
        if (!isBossModeAlreadyOwnedExternally()) {
            return;
        }
        routine = Routine.DEFEATED;
        magneticFieldActive = false;
        hitInvulnerabilityTimer = 0;
        defeatWaitTimer = 2 * 60;
        defeatExplosions = new S3kBossExplosionController(centreX, centreY, 4, services().rng());
        services().fadeOutMusic();
        spawnChild(() -> new CnzEndBossDefeatDebrisChild(this, -8, -0x300));
        spawnChild(() -> new CnzEndBossDefeatDebrisChild(this, 8, 0x300));
    }

    private void updateDefeatWait() {
        if (routine != Routine.DEFEATED || defeatHandoffComplete) return;
        if (defeatExplosions != null) {
            defeatExplosions.tick();
            for (var pending : defeatExplosions.drainPendingExplosions()) {
                if (pending.playSfx()) services().playSfx(Sonic3kSfx.EXPLODE.id);
                spawnChild(() -> new S3kBossExplosionChild(pending.x(), pending.y()));
            }
        }
        if (defeatWaitTimer-- <= 0) applyDefeatHandoff();
    }

    private void applyDefeatHandoff() {
        defeatHandoffComplete = true;

        S3kCnzEventWriteSupport.setBossFlag(services(), false);
        services().gameState().setCurrentBossId(0);

        storedBoundBase = savedCameraMaxX;
        if (storedBoundBase == 0) {
            Object provider = services().levelEventProvider();
            if (provider instanceof Sonic3kLevelEventManager manager && manager.getCnzEvents() != null) {
                storedBoundBase = manager.getCnzEvents().getCameraStoredMaxXPos() & 0xFFFF;
            }
            if (storedBoundBase == 0) storedBoundBase = services().camera().getMaxX() & 0xFFFF;
        }
        setStoredMaxX(storedBoundBase + STORED_BOUND_CAPSULE_DELTA);
        spawnChild(() -> CnzEndBossBoundaryController.increaseMaxX(
                centreX, centreY, storedBoundBase + STORED_BOUND_CAPSULE_DELTA));

        spawnChild(() -> new CnzEggCapsuleInstance(
                new ObjectSpawn(CAPSULE_X, CAPSULE_Y, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0),
                CnzEggCapsuleInstance.CompletionContinuation.CNZ_END_BOSS_SEQUENCE));
    }

    private void updatePostDefeatSequence(int frameCounter, PlayableEntity player) {
        if (!defeatHandoffComplete || transitionRequested) {
            return;
        }
        services().camera().setMinX(services().camera().getX());
        if (capsuleResultsComplete && !cannonSpawned) {
            releasePostCapsuleStateOnce();
            if (player instanceof AbstractPlayableSprite sprite
                    && (sprite.getCentreX() & 0xFFFF) >= CANNON_TRIGGER_X) {
                spawnEndCannon();
            }
            return;
        }
        if (!cannonSpawned || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        if (!cannonArmed && endCannon != null && endCannon.isEndSequenceLaunchReady()) {
            cannonArmed = true;
            cannonLaunchTimer = CANNON_LAUNCH_WAIT;
            services().camera().setMaxYTarget((short) 0x0200);
            sprite.setControlLocked(true);
            return;
        }
        if (cannonArmed && !cannonLaunched) {
            if (cannonLaunchTimer-- > 0) {
                return;
            }
            if (endCannon != null) {
                endCannon.triggerEndSequenceLaunch(frameCounter);
            }
            cannonLaunched = true;
            return;
        }
        if (cannonLaunched && isPlayerPastIczLaunchThreshold(sprite)) {
            requestIczTransition();
        }
    }

    public void onCapsuleResultsComplete() {
        capsuleResultsComplete = true;
    }

    public int getPostCapsuleReleaseCountForTest() {
        return postCapsuleReleaseCountForTest;
    }

    private void spawnEndCannon() {
        cannonSpawned = true;
        endCannon = spawnChild(() -> new CnzCannonInstance(
                new ObjectSpawn(CANNON_X, CANNON_Y, Sonic3kObjectIds.CNZ_CANNON, 0, 0, false, 0)));
    }

    private boolean isPlayerPastIczLaunchThreshold(AbstractPlayableSprite sprite) {
        int cameraYPlusWindow = (services().camera().getY() & 0xFFFF) + 0x20;
        int playerY = sprite.getCentreY() & 0xFFFF;
        return cameraYPlusWindow >= playerY;
    }

    private void requestIczTransition() {
        transitionRequested = true;
        int act = ICZ_START_ZONE_WORD & 0xFF;
        preparePlayersForIczFade();
        services().requestZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, act, true);
        setDestroyed(true);
    }

    private void preparePlayersForIczFade() {
        PlayableEntity focused = services().camera().getFocusedSprite();
        List<PlayableEntity> players = services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
        for (PlayableEntity candidate : players) {
            if (candidate instanceof AbstractPlayableSprite sprite) {
                neutralizeLauncherStateForFade(sprite);
            }
        }
        if (focused instanceof AbstractPlayableSprite focusedPlayer && !players.contains(focusedPlayer)) {
            neutralizeLauncherStateForFade(focusedPlayer);
        }
    }

    /**
     * Restores main-player and sidekick control after the defeat handoff.
     *
     * <p>The teleporter beam can leave the player object-controlled, rolled, and
     * hidden. CNZ's boss release must clear all three so the capsule handoff
     * leaves the player in a normal controllable state.
     */
    private void restorePlayerControl() {
        PlayableEntity focused = services().camera().getFocusedSprite();
        List<PlayableEntity> players = services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
        for (PlayableEntity candidate : players) {
            if (candidate instanceof AbstractPlayableSprite sprite) {
                releaseSprite(sprite);
            }
        }
        if (focused instanceof AbstractPlayableSprite focusedPlayer && !players.contains(focusedPlayer)) {
            releaseSprite(focusedPlayer);
        }
    }

    private void releasePostCapsuleStateOnce() {
        if (postCapsuleReleaseComplete) {
            return;
        }
        restorePlayerControl();
        restoreLevelMusic();
        services().camera().setMaxYTarget((short) 0x0200);
        setStoredMinY(0x0200);
        spawnChild(() -> CnzEndBossBoundaryController.decreaseMinY(centreX, centreY, 0x0200));
        setStoredMaxX(storedBoundBase + STORED_BOUND_CANNON_DELTA);
        spawnChild(() -> CnzEndBossBoundaryController.increaseMaxX(
                centreX, centreY, storedBoundBase + STORED_BOUND_CANNON_DELTA));
        postCapsuleReleaseComplete = true;
        postCapsuleReleaseCountForTest++;
    }

    private void releaseSprite(AbstractPlayableSprite sprite) {
        sprite.setControlLocked(false);
        ObjectControlState.none().applyTo(sprite);
        sprite.setHidden(false);
        sprite.setRolling(false);
    }

    private void neutralizeLauncherStateForFade(AbstractPlayableSprite sprite) {
        releaseSprite(sprite);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(false);
        sprite.setJumping(false);
        sprite.setOnObject(false);
        sprite.setObjectMappingFrameControl(false);
        sprite.setHidden(true);
        sprite.setControlLocked(true);
        sprite.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        sprite.setHighPriority(false);
    }

    /**
     * Restores CNZ Act 2 music instead of claiming a full boss music / fade
     * state machine.
     */
    private void restoreLevelMusic() {
        services().playMusic(Sonic3kMusic.CNZ2.id);
    }

    private void setStoredMaxX(int value) {
        Object provider = services().levelEventProvider();
        if (provider instanceof Sonic3kLevelEventManager manager && manager.getCnzEvents() != null) {
            manager.getCnzEvents().setCameraStoredMaxXPos((short) value);
        }
    }

    private void setStoredMinY(int value) {
        Object provider = services().levelEventProvider();
        if (provider instanceof Sonic3kLevelEventManager manager && manager.getCnzEvents() != null) {
            manager.getCnzEvents().setCameraStoredMinYPos((short) value);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_END_BOSS);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, centreX, centreY, !facingRight, false);
        PatternSpriteRenderer ship = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (ship != null) {
            // Child1_MakeRoboShip4: offset (0,-8), subtype 9; ObjDat ship frame 5,
            // palette 0 and high_priority art bit.
            ship.drawFrameIndexForcedPriority(5, centreX, centreY - 8,
                    !facingRight, false, 0, true);
        }
    }

    public String getRoutineForTest() {
        return routine.name();
    }

    public boolean isStartupCompleteForTest() {
        return startupComplete;
    }

    public boolean isMagneticFieldActiveForTest() {
        return magneticFieldActive;
    }

    public int getMappingFrameForTest() {
        return mappingFrame;
    }

    Routine nativeRoutine() {
        return routine;
    }

    boolean magneticFieldActive() {
        return magneticFieldActive;
    }

    boolean defeatStarted() {
        return routine == Routine.DEFEATED;
    }

    boolean facingRight() {
        return facingRight;
    }

    void relinkMagnetChild(CnzEndBossMagnetChild child) {
        magnetChild = child;
    }
}
