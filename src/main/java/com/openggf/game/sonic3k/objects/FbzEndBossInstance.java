package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.objects.*;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Locked-on {@code Obj_FBZEndBoss} ($AC, sonic3k.asm:148698-149618). */
public final class FbzEndBossInstance extends AbstractBossInstance
        implements SpawnConstructionContextRewindRecreatable, FbzEndBossGraphMember {
    public static final int OBJECT_ID = 0xAC;
    public static final int INITIAL_HITS = 8;
    public static final int ACTIVE_COLLISION_FLAGS = 0x16;
    public static final int INVULNERABILITY_FRAMES = 0x20;
    public static final int BOSS_MUSIC_ID = 0x19;
    public static final int BOSS_HIT_SFX_ID = 0x6E;
    public static final int ARM_ROTATE_SFX_ID = 0xC9;
    private static final Logger LOG = Logger.getLogger(FbzEndBossInstance.class.getName());
    private static final int[] HIT_FLASH_COLOR_INDICES = {3, 4, 9, 14};
    private static final int[] HIT_NORMAL_COLORS = {0x02A, 0x026, 0x020, 0x644};
    private static final int[] HIT_BRIGHT_COLORS = {0x888, 0xAAA, 0xEEE, 0xAAA};

    private static final int[] CIRCLE_1 = {
            0,2,4,5,7,9,0xB,0xC,0xE,0x10,0x11,0x13,0x15,0x17,0x18,0x1A,
            0x1C,0x1D,0x1F,0x20,0x22,0x23,0x25,0x27,0x28,0x29,0x2B,0x2C,
            0x2E,0x2F,0x30,0x32,0x33,0x34,0x35,0x37,0x38,0x39,0x3A,0x3B,
            0x3C,0x3D,0x3E,0x3F,0x3F,0x40,0x41,0x42,0x43,0x43,0x44,0x44,
            0x45,0x45,0x46,0x46,0x47,0x47,0x47,0x47,0x48,0x48,0x48,0x48};
    private static final int[] CIRCLE_2 = {
            0,1,2,2,3,4,5,5,6,7,8,9,9,0xA,0xB,0xC,0xC,0xD,0xE,0xE,
            0xF,0x10,0x10,0x11,0x12,0x12,0x13,0x14,0x14,0x15,0x15,0x16,
            0x17,0x17,0x18,0x18,0x19,0x19,0x1A,0x1A,0x1B,0x1B,0x1B,0x1C,
            0x1C,0x1D,0x1D,0x1D,0x1E,0x1E,0x1E,0x1E,0x1F,0x1F,0x1F,0x1F,
            0x1F,0x20,0x20,0x20,0x20,0x20,0x20,0x20};

    public enum RootChildRole { LEFT_ARM, RIGHT_ARM, WEAPON }
    public enum ForcedExitInput { RIGHT, A_RIGHT_HELD_RIGHT, A_RIGHT }
    public record RootChildSpec(RootChildRole role, int dx, int dy) { }
    public record Position(int x, int y) { }
    public record Fixed8(int position, int fraction) { }
    public record AttackGate(boolean stopTimer, boolean armActive, boolean weaponActive) { }
    public enum Phase { PRE_MUSIC, PRE_MUSIC_INIT, DESCEND, OPENING_ROTATION, ATTACK, ROTATION,
        DEFEAT_RECENTER, DEFEAT_EXPLOSIONS, DEFEAT_HIDE_WAIT, DEFEAT_CAPSULE_DELAY,
        CAPSULE_WAIT, EXIT_READY }

    private int phaseOrdinal;
    private int timer;
    private int angle;
    private int attackRoundsLeft;
    private boolean facingRight;
    private boolean graphSpawned;
    private boolean exitArtQueued;
    private boolean exitArtConsumersPublished;
    private boolean armTrigger;
    /** Native root {@code $38} bit 0: attack countdown/proximity latch. */
    private boolean attackLatch;
    /** Native root {@code $38} bit 1: weapon busy/activation signal. */
    private boolean weaponTrigger;
    /**
     * {@code loc_7081A} returns without drawing only on the zero-angle transition into
     * {@code Wait_FadeToLevelMusic}; later non-expiring wait executions draw the root.
     */
    private boolean suppressRootDrawThisFrame;
    private boolean dismantling;
    private boolean nativeStarted;
    private boolean capsuleSpawnAttempted;
    private boolean pendingHitProcessing;
    private int exitArtQueuedCount;
    private String exitArtQueueFailure;
    private int positionFractionY;
    private FbzEndBossShipChild ship;
    private FbzRobotnikHeadChild head;
    private FbzEndBossWeaponChild weapon;
    private FbzEndBossShipExplosionController shipExplosionController;
    private FbzEndBossShipFlameChild shipFlame;
    private final List<FbzEndBossArmChild> arms = new ArrayList<>();
    private final List<FbzEndBossJointChild> joints = new ArrayList<>();
    private final List<FbzEndBossChainLinkChild> chainLinks = new ArrayList<>();

    public FbzEndBossInstance(ObjectSpawn spawn) { super(spawn, "FBZEndBoss"); }

    public static int attackRounds() { return 9; }
    public static int rotationTimer(PlayerCharacter character) {
        return character == PlayerCharacter.KNUCKLES ? 0xFF : 0x1FF;
    }
    public static List<RootChildSpec> rootChildTable() {
        return List.of(new RootChildSpec(RootChildRole.LEFT_ARM, -0x30, 0x48),
                new RootChildSpec(RootChildRole.RIGHT_ARM, 0x30, 0x48),
                new RootChildSpec(RootChildRole.WEAPON, 0, -0x28));
    }
    public static Position initialPosition(int cameraXCopy, int cameraYCopy) {
        return new Position((cameraXCopy + 0xA0) & 0xFFFF, (cameraYCopy - 0x60) & 0xFFFF);
    }
    public static int[] circleLookup1Sentinels() {
        return new int[]{CIRCLE_1[0],CIRCLE_1[1],CIRCLE_1[2],CIRCLE_1[3],CIRCLE_1[4],CIRCLE_1[5],CIRCLE_1[56],CIRCLE_1[63]};
    }
    public static Fixed8 move8_8(int position, int fraction, int velocity) {
        int fixed = (position << 8) | (fraction & 0xFF);
        fixed += velocity;
        return new Fixed8(fixed >> 8, fixed & 0xFF);
    }
    public static int nativeSteadyObjectCount() { return 16; }
    public static int nativePeakObjectCount() { return 25; }
    public static int nativeInitialDescentVelocity() { return 0; }
    public static int nativeDescentGravity() { return 0x38; }
    public static int circleOffset1(int angle) { return circleOffset(angle, CIRCLE_1); }
    public static int circleOffset2(int angle) { return circleOffset(angle, CIRCLE_2); }
    public static AttackGate attackGate(boolean weaponBusy, boolean armBusy,
                                        boolean knuckles, boolean closeAndGrounded) {
        // loc_7071A branches Knuckles directly to the timer after bset #1;
        // only the non-Knuckles path observes an already-busy weapon bit.
        if (knuckles) return new AttackGate(false, armBusy, true);
        if (weaponBusy) return new AttackGate(true, armBusy, true);
        boolean arm = armBusy;
        boolean weapon = false;
        if (closeAndGrounded) {
            weapon = true;
        }
        return new AttackGate(false, arm, weapon);
    }

    private static int circleOffset(int angle, int[] table) {
        int unsignedAngle = angle & 0xFF;
        int index = unsignedAngle & 0x3F;
        return switch ((unsignedAngle >>> 6) & 3) {
            case 0 -> table[0x3F - index];
            case 1 -> -table[index];
            case 2 -> -table[0x3F - index];
            default -> table[index];
        };
    }

    @Override protected void initializeBossState() {
        state.routine = 0;
        state.hitCount = INITIAL_HITS;
        phaseOrdinal = Phase.PRE_MUSIC.ordinal();
        // The ROM installs Obj_Wait on the creation update, then waits 120
        // executions and spends one callback frame before boss initialization.
        timer = 0x77;
        angle = 0x80;
        attackRoundsLeft = attackRounds();
        facingRight = true;
        graphSpawned = false;
        exitArtQueued = false;
        exitArtConsumersPublished = false;
        armTrigger = false;
        attackLatch = false;
        weaponTrigger = false;
        suppressRootDrawThisFrame = false;
        dismantling = false;
        nativeStarted = false;
        capsuleSpawnAttempted = false;
        pendingHitProcessing = false;
        exitArtQueuedCount = 0;
        exitArtQueueFailure = null;
        positionFractionY = 0;
    }

    @Override protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
        if (!nativeStarted) {
            nativeStarted = true;
            if (services().gameState() != null) services().gameState().setCurrentBossId(OBJECT_ID);
            if (services().levelEventProvider() instanceof AbstractLevelEventManager events) events.setBossActive(true);
            loadArtAndPalette();
            if (services().audioManager() != null) services().audioManager().fadeOutMusic(0x28, 6);
            spawnFreeChild(() -> new SongFadeTransitionInstance(90, Sonic3kMusic.BOSS.id));
            return;
        }
        switch (phase()) {
            case PRE_MUSIC -> updatePreMusic();
            case PRE_MUSIC_INIT -> initializeNativeGraph();
            case DESCEND -> updateDescend();
            case OPENING_ROTATION -> updateOpeningRotation();
            case ATTACK -> updateAttack(player);
            case ROTATION -> updateRotation();
            case DEFEAT_RECENTER -> updateDefeatRecenter();
            case DEFEAT_EXPLOSIONS -> updateDefeatExplosions();
            case DEFEAT_HIDE_WAIT -> updateDefeatHideWait();
            case DEFEAT_CAPSULE_DELAY -> updateDefeatCapsuleDelay();
            case CAPSULE_WAIT -> updateCapsuleWait();
            case EXIT_READY -> updateExitReady();
        }
        updateNativeHitHandler();
    }

    private void updateNativeHitHandler() {
        if (pendingHitProcessing) {
            pendingHitProcessing = false;
            if (state.hitCount <= 0) {
                // sub_70E10 -> BossDefeated_StopTimer: clear Update_HUD_timer
                // on the same root update that recognizes collision_property == 0.
                if (services().levelGamestate() != null) services().levelGamestate().pauseTimer();
                services().gameState().addScore(1000);
                onDefeatStarted();
                return;
            }
            state.invulnerable = true;
            state.invulnerabilityTimer = INVULNERABILITY_FRAMES;
            services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        }
        if (!state.invulnerable) return;
        applyHitPalette((state.invulnerabilityTimer & 1) == 0);
        state.invulnerabilityTimer--;
        if (state.invulnerabilityTimer <= 0) {
            state.invulnerable = false;
            applyHitPalette(false);
        }
    }

    private void applyHitPalette(boolean bright) {
        if (tryServices() == null || services().currentLevel() == null) return;
        int[] colors = bright ? HIT_BRIGHT_COLORS : HIT_NORMAL_COLORS;
        for (int i = 0; i < colors.length; i++) {
            int word = colors[i];
            S3kPaletteWriteSupport.applyContiguousPatch(
                    services().paletteOwnershipRegistryOrNull(), services().currentLevel(), null,
                    S3kPaletteOwners.FBZ_END_BOSS, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1, HIT_FLASH_COLOR_INDICES[i], new byte[]{(byte) (word >>> 8), (byte) word});
        }
    }

    private void updatePreMusic() {
        if (--timer >= 0) return;
        phaseOrdinal = Phase.PRE_MUSIC_INIT.ordinal();
    }

    private void initializeNativeGraph() {
        Position p = initialPosition(services().camera().getXCopy(), services().camera().getYCopy());
        state.x = p.x(); state.y = p.y(); state.xFixed = state.x << 16; state.yFixed = state.y << 16;
        state.yVel = nativeInitialDescentVelocity();
        spawnNativeGraph();
        phaseOrdinal = Phase.DESCEND.ordinal();
    }

    private void updateDescend() {
        Fixed8 moved = move8_8(state.y, positionFractionY, state.yVel);
        state.y = moved.position(); positionFractionY = moved.fraction();
        state.yVel += nativeDescentGravity();
        if (state.y < 0x648) return;
        state.y = 0x648; positionFractionY = 0; timer = 0x5F;
        phaseOrdinal = Phase.OPENING_ROTATION.ordinal();
    }

    private void updateOpeningRotation() {
        playRotationTickIfNativeBoundary();
        angle = (angle + 4) & 0xFF;
        updateCircularPosition();
        if (--timer >= 0) return;
        beginAttack();
    }

    private void beginAttack() {
        // loc_70700: bit 3 starts the immediate arm wave. Bit 0 remains clear
        // until the proximity branch at loc_70736 succeeds.
        armTrigger = true;
        attackLatch = false;
        timer = 0x3F; attackRoundsLeft = attackRounds(); phaseOrdinal = Phase.ATTACK.ordinal();
    }

    private void updateAttack(PlayableEntity player) {
        if (arms.size() == 2) state.x = (short) ((arm(0).getX() + arm(1).getX()) >> 1);
        PlayableEntity target = nearestNativeTarget(player);
        boolean knuckles = resolveCharacter() == PlayerCharacter.KNUCKLES;
        boolean closeAndGrounded = target != null
                && Math.abs((short) (target.getCentreX() - state.x)) < 0x18 && !target.getAir();
        boolean proximityTriggered = !knuckles && !weaponTrigger && !attackLatch && closeAndGrounded;
        AttackGate gate = attackGate(weaponTrigger, armTrigger, knuckles,
                proximityTriggered);
        armTrigger = gate.armActive();
        if (proximityTriggered) attackLatch = true;
        weaponTrigger = gate.weaponActive();
        if (gate.stopTimer()) return;
        if (--timer >= 0) return;
        timer = 0x1F;
        attackLatch = false;
        attackRoundsLeft--;
        if (attackRoundsLeft >= 0) {
            armTrigger = true;
            if (target != null) facingRight = (short) target.getCentreX() >= (short) state.x;
        }
        if (attackRoundsLeft >= 0) return;
        timer = rotationTimer(resolveCharacter());
        phaseOrdinal = Phase.ROTATION.ordinal();
    }

    private void updateRotation() {
        playRotationTickIfNativeBoundary();
        angle = (angle + 2) & 0xFF;
        updateCircularPosition();
        if (--timer >= 0) return;
        beginAttack();
    }

    private void updateCircularPosition() {
        FbzEndBossArmChild anchor = arm(1);
        int anchorY = anchor == null ? 0x690 : anchor.getY();
        state.y = anchorY + circleOffset1(angle);
    }

    private void playRotationTickIfNativeBoundary() {
        if (timer >= 4 && (angle & 0x7F) == 0) services().playSfx(Sonic3kSfx.SPIKE_BALLS.id);
    }

    private void updateDefeatRecenter() {
        if ((angle & 0xFF) != 0) {
            angle = (angle + 2) & 0xFF;
            updateCircularPosition();
            return;
        }
        timer = 0x3F;
        suppressRootDrawThisFrame = true;
        phaseOrdinal = Phase.DEFEAT_EXPLOSIONS.ordinal();
    }

    private void updateDefeatExplosions() {
        // Wait_FadeToLevelMusic ($003F) draws after nonnegative predecrements; the
        // 64th call reaches loc_70836 instead, and the phase change suppresses drawing.
        suppressRootDrawThisFrame = false;
        if (--timer >= 0) return;
        dismantling = true;
        spawnFreeChild(SongFadeTransitionInstance::toCurrentLevelMusic);
        spawnRootDebris();
        timer = 119;
        phaseOrdinal = Phase.DEFEAT_HIDE_WAIT.ordinal();
    }

    private void updateDefeatHideWait() {
        if (--timer >= 0) return;
        timer = 0x7F;
        phaseOrdinal = Phase.DEFEAT_CAPSULE_DELAY.ordinal();
    }

    private void updateDefeatCapsuleDelay() {
        if (--timer >= 0) return;
        if (!capsuleSpawnAttempted) {
            capsuleSpawnAttempted = true;
            services().gameState().setEndOfLevelActive(true);
            spawnFreeChild(() -> new FbzEndEggCapsuleInstance(0x307C, 0x660));
            services().camera().setMaxXTarget((short) 0x2FDC);
            spawnAfterCurrentSibling(() -> new S3kIncLevelEndXGradualInstance(state.x, state.y));
            if (services().gameState() != null) services().gameState().setCurrentBossId(0);
            if (services().levelEventProvider() instanceof AbstractLevelEventManager events) events.setBossActive(false);
        }
        phaseOrdinal = Phase.CAPSULE_WAIT.ordinal();
    }

    private void loadArtAndPalette() {
        if (services().renderManager() != null
                && services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.FBZ_END_BOSS);
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.FBZ_ROBOTNIK_HEAD);
            if (resolveCharacter() == PlayerCharacter.KNUCKLES) {
                provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.FBZ_EGGROBO_HEAD);
            }
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.FBZ_END_BOSS_FLAME);
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
            provider.ensureBossExplosionArtLoaded();
        }
        try {
            var rom = services().rom();
            if (rom == null) return;
            byte[] palette = rom.readBytes(Sonic3kConstants.PAL_FBZ_END_BOSS_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(), services().graphicsManager(), S3kPaletteOwners.FBZ_END_BOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, palette);
        } catch (IOException failure) {
            LOG.log(Level.WARNING, "Could not load FBZ end-boss palette", failure);
        }
    }

    private void updateCapsuleWait() {
        // loc_708AA writes Camera_min_X_pos before testing _unkFAA8, including
        // the exact update on which the capsule clears it.
        services().camera().setMinXCurrent(services().camera().getX());
        if (services().gameState().isEndOfLevelActive()) return;
        restorePlayersAndMusic();
        relockPlayersForExitReady();
        spawnFreeChild(S3kNativeP2LockInstance::new);
        services().camera().setMaxYTarget((short) 0x1000);
        services().camera().setMaxXTarget((short) 0x3738);
        spawnAfterCurrentSibling(() -> new S3kIncLevelEndXGradualInstance(state.x, state.y));
        queueExitArt();
        timer = 0;
        phaseOrdinal = Phase.EXIT_READY.ordinal();
    }

    private void publishExitArtConsumersIfQueueIdle() {
        var queue=services().kosinskiModuleQueue();
        if (queue!=null && !queue.isIdle()) return;
        publishExitArtConsumers();
    }

    private boolean publishExitArtConsumers() {
        if (exitArtConsumersPublished) return true;
        if (services().renderManager()==null
                || !(services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider)
                || services().currentLevel()==null) {
            exitArtConsumersPublished=true;
            return true;
        }
        try {
            var rom=services().rom();
            if (rom==null) {
                exitArtConsumersPublished=true;
                return true;
            }
            provider.registerFbzExitArtSheets(services().currentLevel(),rom);
            services().renderManager().ensurePatternsCached(
                    services().graphicsManager(),PatternAtlasRange.OBJECTS.base());
            exitArtConsumersPublished=true;
            return true;
        } catch (IOException failure) {
            exitArtQueueFailure="Could not publish exit consumers: "+failure.getMessage();
            LOG.log(Level.WARNING,"Could not publish FBZ exit art consumers",failure);
            return false;
        }
    }

    /** {@code loc_7092A -> loc_86334}: forced walk and raw {@code StartNewLevel #$0800}. */
    private void updateExitReady() {
        publishExitArtConsumersIfQueueIdle();
        if ((services().camera().getY() & 0xFFFF) >= exitCameraThreshold()) {
            services().requestSessionSave(com.openggf.game.save.SaveReason.PROGRESSION_SAVE);
            services().requestZoneAndAct(exitZone(), exitAct(), true);
            ObjectLifetimeOps.destroyLatched(this);
            return;
        }
        AbstractPlayableSprite main = services().playerQuery() != null
                && services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite sprite
                ? sprite : null;
        if (main != null) {
            ForcedExitInput input = forcedExitInput(main.getPushing(), timer);
            if (input == ForcedExitInput.A_RIGHT) timer = 0x1F;
            else if (timer != 0) timer--;
            int mask = AbstractPlayableSprite.INPUT_RIGHT
                    | (input == ForcedExitInput.RIGHT ? 0 : AbstractPlayableSprite.INPUT_JUMP);
            boolean jumpPress = input == ForcedExitInput.A_RIGHT;
            main.setForcedInputMask(mask);
            main.writeLogicalInputAndCurrentFollowerHistory(mask, jumpPress);
        }
    }

    public static ForcedExitInput forcedExitInput(boolean pushing, int holdTimer) {
        if (pushing) return ForcedExitInput.A_RIGHT;
        return holdTimer != 0 ? ForcedExitInput.A_RIGHT_HELD_RIGHT : ForcedExitInput.RIGHT;
    }

    public static int exitCameraThreshold() { return 0x720; }
    public static int exitZone() { return Sonic3kZoneIds.ZONE_SOZ; }
    public static int exitAct() { return 0; }
    void updateExitReadyForTest() { updateExitReady(); }
    void clearExitInputTimerForTest() { timer = 0; }

    private void relockPlayersForExitReady() {
        if (services().playerQuery() == null) return;
        if (services().playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite main) {
            main.clearLogicalInputState();
            main.setForcedInputMask(0);
            main.setControlLocked(true);
        }
    }

    private void restorePlayersAndMusic() {
        if (services().playerQuery() != null) {
            for (PlayableEntity entity : services().playerQuery().playersFor(
                    ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
                if (entity instanceof AbstractPlayableSprite sprite) {
                    sprite.setControlLocked(false);
                    ObjectControlState.none().applyTo(sprite);
                    sprite.setHidden(false);
                }
            }
        }
        int music = services().getCurrentLevelMusicId();
        if (music >= 0) services().playMusic(music);
    }

    private void spawnRootDebris() {
        int[][] table = {{-0x14,8,8},{0x14,8,9},{-0x10,0x20,10},{0x10,0x20,11}};
        for (int[] e : table) {
            int dx = facingRight ? e[0] : -e[0];
            if (spawnChild(() -> new FbzEndBossDebrisChild(new ObjectSpawn(
                    state.x + dx, state.y + e[1], OBJECT_ID, e[2], 0, false, 0), !facingRight)) == null) break;
        }
    }

    private PlayableEntity nearestNativeTarget(PlayableEntity fallback) {
        if (tryServices() == null || services().playerQuery() == null) return fallback;
        PlayableEntity nearest = null;
        int distance = Integer.MAX_VALUE;
        for (PlayableEntity candidate : services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            int current = Math.abs((short) (candidate.getCentreX() - state.x));
            if (current < distance) { distance = current; nearest = candidate; }
        }
        return nearest == null ? fallback : nearest;
    }

    @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (getCollisionFlags() == 0 || pendingHitProcessing) return;
        state.hitCount--;
        pendingHitProcessing = true;
        if (state.hitCount <= 0) {
            state.hitCount = 0;
            state.defeated = true;
        }
    }

    @Override protected void onDefeatStarted() {
        phaseOrdinal = Phase.DEFEAT_RECENTER.ordinal();
        timer = 0;
    }
    @Override protected void onHitTaken(int remainingHits) { }
    @Override protected int getInitialHitCount() { return INITIAL_HITS; }
    @Override protected int getCollisionSizeIndex() { return ACTIVE_COLLISION_FLAGS & 0x3F; }
    @Override protected boolean usesBaseHitHandler() { return false; }
    @Override protected boolean usesDefeatSequencer() { return false; }
    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }
    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }
    @Override public int getCollisionFlags() {
        return phase().ordinal() >= Phase.DESCEND.ordinal() && !state.invulnerable && !state.defeated
                && !pendingHitProcessing
                ? ACTIVE_COLLISION_FLAGS : 0;
    }

    public void spawnNativeGraph() {
        if (graphSpawned) return;
        graphSpawned = true;
        ship = accepted(spawnChild(() -> new FbzEndBossShipChild(this)));
        for (RootChildSpec spec : rootChildTable()) {
            if (spec.role() == RootChildRole.WEAPON) {
                weapon = accepted(spawnChild(() -> new FbzEndBossWeaponChild(this, spec.dx(), spec.dy())));
                break;
            }
            int armIndex = spec.role() == RootChildRole.LEFT_ARM ? 0 : 1;
            FbzEndBossArmChild arm = accepted(spawnChild(() -> new FbzEndBossArmChild(this, armIndex, spec.dx(), spec.dy())));
            if (arm == null) break;
            arms.add(arm);
        }
    }

    private <T extends AbstractObjectInstance> T accepted(T child) {
        return child == null || child.isDestroyed() || child.getSlotIndex() < 0 ? null : child;
    }

    private PlayerCharacter resolveCharacter() {
        if (tryServices() == null) return PlayerCharacter.SONIC_ALONE;
        try {
            return S3kRuntimeStates.resolvePlayerCharacter(
                    services().zoneRuntimeRegistry(), services().configuration());
        } catch (RuntimeException missingTestRuntime) {
            return PlayerCharacter.SONIC_ALONE;
        }
    }

    private void queueExitArt() {
        if (exitArtQueued) return;
        exitArtQueued = true;
        try {
            var queue = services().kosinskiModuleQueue();
            var rom = services().rom();
            if (queue == null || rom == null) {
                exitArtQueueFailure = "KosM queue or ROM owner unavailable";
                return;
            }
            Sonic3kPlcLoader.bindRuntimePatternDmaTarget(queue, services());
            for (var entry : Sonic3kPlcLoader.fbzEndBossExitKosmEntries()) {
                if (!queue.enqueue(rom, entry.sourceAddress(), entry.destinationVramBytes())) {
                    exitArtQueueFailure = "KosM capacity exhausted after " + exitArtQueuedCount + " entries";
                    LOG.warning("FBZ end-boss exit art prefix only: " + exitArtQueueFailure);
                    break;
                }
                exitArtQueuedCount++;
            }
        } catch (IOException failure) {
            exitArtQueueFailure = failure.getMessage();
            LOG.log(Level.WARNING, "Could not inspect FBZ end-boss exit KosM archive", failure);
        }
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossInstance(ctx.spawn());
    }
    @Override public String rewindRole() { return "root"; }
    @Override public FbzEndBossInstance boss() { return this; }
    public Phase phase() { return Phase.values()[phaseOrdinal]; }
    public List<FbzEndBossArmChild> arms() { return Collections.unmodifiableList(arms); }
    public List<FbzEndBossChainLinkChild> chainLinks() { return Collections.unmodifiableList(chainLinks); }
    public FbzEndBossShipChild ship() { return ship; }
    public FbzEndBossWeaponChild weapon() { return weapon; }
    FbzEndBossArmChild arm(int index) { return arms.stream().filter(a -> a.rewindRole().equals("arm:" + index)).findFirst().orElse(null); }
    FbzEndBossJointChild joint(int index) { return joints.stream().filter(a -> a.rewindRole().equals("joint:" + index)).findFirst().orElse(null); }
    public boolean isFacingRight() { return facingRight; }
    PlayerCharacter nativeCharacter() { return resolveCharacter(); }
    int angle() { return angle; }
    boolean areArmsAnchored() { return phase().ordinal() >= Phase.OPENING_ROTATION.ordinal(); }
    boolean isHurtFlashActive() { return state.invulnerable; }
    boolean isArmTriggerActive() { return armTrigger; }
    void clearArmTrigger() { armTrigger = false; }
    boolean isWeaponTriggerActive() { return weaponTrigger; }
    void clearWeaponTrigger() { weaponTrigger = false; }
    boolean isDismantling() { return dismantling; }
    void attach(FbzEndBossGraphMember member) {
        if (member instanceof FbzEndBossShipChild value) ship = value;
        else if (member instanceof FbzRobotnikHeadChild value) head = value;
        else if (member instanceof FbzEndBossShipExplosionController value) shipExplosionController = value;
        else if (member instanceof FbzEndBossShipFlameChild value) shipFlame = value;
        else if (member instanceof FbzEndBossWeaponChild value) weapon = value;
        else if (member instanceof FbzEndBossArmChild value && arms.stream().noneMatch(a -> a.rewindRole().equals(value.rewindRole()))) arms.add(value);
        else if (member instanceof FbzEndBossJointChild value && joints.stream().noneMatch(a -> a.rewindRole().equals(value.rewindRole()))) joints.add(value);
        else if (member instanceof FbzEndBossChainLinkChild value && chainLinks.stream().noneMatch(a -> a.rewindRole().equals(value.rewindRole()))) chainLinks.add(value);
    }

    @Override protected void afterRewindRestoreSettled() {
        super.afterRewindRestoreSettled();
        if (tryServices() == null || services().objectManager() == null) return;
        arms.clear(); joints.clear(); chainLinks.clear(); ship = null; head = null; weapon = null;
        shipExplosionController = null;
        shipFlame = null;
        for (ObjectInstance object : services().objectManager().getActiveObjects()) {
            if (object instanceof FbzEndBossGraphMember member && member != this && member.boss() == this) attach(member);
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_END_BOSS);
        if (renderer != null && renderer.isReady()
                && phase().ordinal() >= Phase.DESCEND.ordinal()
                && phase().ordinal() <= Phase.DEFEAT_EXPLOSIONS.ordinal()
                && !suppressRootDrawThisFrame)
            renderer.drawFrameIndex(0, state.x, state.y, !facingRight, false);
    }
    @Override public int getPriorityBucket() {
        if (phase() == Phase.DESCEND) return 4;
        int signedAngle = (byte) angle;
        return signedAngle > 0 ? 0 : 6;
    }
    @Override public boolean isHighPriority() {
        return phase() == Phase.DESCEND || (byte) angle >= 0;
    }
    @Override public boolean isPersistent() { return true; }
}
