package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.CheckpointRuntimeStateProvider;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.ZoneEventSchemaSidecar;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.AizObjectEventBridge;
import com.openggf.game.sonic3k.events.CnzObjectEventBridge;
import com.openggf.game.sonic3k.events.HczObjectEventBridge;
import com.openggf.game.sonic3k.events.MgzObjectEventBridge;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.events.Sonic3kICZEvents;
import com.openggf.game.sonic3k.events.Sonic3kLBZEvents;
import com.openggf.game.sonic3k.events.Sonic3kMHZEvents;
import com.openggf.game.sonic3k.events.Sonic3kMGZEvents;
import com.openggf.game.sonic3k.events.S3kTransitionEventBridge;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.CnzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.IczZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.MgzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kZoneRuntimeState;
import com.openggf.game.sonic3k.sidekick.Sonic3kSidekickFollowContext;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.game.sonic3k.features.HCZWaterSkimHandler;
import com.openggf.game.sonic3k.features.HCZWaterTunnelHandler;
import com.openggf.game.sonic3k.objects.Aiz2BossEndSequenceState;
import com.openggf.game.sonic3k.objects.AizCollapsingLogBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesCnz2AInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesCnz2BInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHcz2Instance;
import com.openggf.game.sonic3k.objects.HCZConveyorBeltObjectInstance;
import com.openggf.game.sonic3k.objects.HCZWaterRushObjectInstance;
import com.openggf.game.sonic3k.objects.IczSnowboardArtLoader;
import com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance;
import com.openggf.game.sonic3k.objects.Lbz1GroundLaunchIntroInstance;
import com.openggf.game.sonic3k.objects.MhzPollenSpawnerInstance;
import com.openggf.camera.Camera;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.SidekickCpuController;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 3&K implementation of dynamic level events.
 * ROM equivalent: ScreenEvents (sonic3k.asm:102228)
 *
 * S3K uses dual foreground/background event routines (Events_routine_fg
 * and Events_routine_bg) with a stride of 4 per state transition.
 * Each zone has two parallel event handlers:
 * <ul>
 *   <li>ScreenEvent (FG) - terrain changes, object spawning, camera boundaries</li>
 *   <li>BackgroundEvent (BG) - parallax, deformation, water level, boss arenas</li>
 * </ul>
 *
 * S3K also uses Boss_flag to gate FG events during boss fights, and
 * branches on Player_mode for character-specific event paths (Sonic/Tails
 * vs Knuckles take different routes through most zones).
 *
 * Phase 1 implements bootstrap selection for AIZ1 intro-skip parity.
 * Zone event handlers will be added incrementally per zone.
 */
public class Sonic3kLevelEventManager extends AbstractLevelEventManager
        implements CheckpointRuntimeStateProvider,
        AizObjectEventBridge, CnzObjectEventBridge, HczObjectEventBridge, MgzObjectEventBridge,
        S3kTransitionEventBridge {
    private static final Logger LOG = Logger.getLogger(Sonic3kLevelEventManager.class.getName());
    private static final int PACHINKO_TOP_EXIT_Y = -0x20;
    private static final int CNZ_POST_TRANSITION_ACT2_SIZE_CHANGE_FRAMES = 751;
    private static final int CNZ2_CAMERA_MIN_X = 0x0000;
    private static final int CNZ2_CAMERA_MAX_X = 0x6000;
    private static final int CNZ2_CAMERA_MIN_Y = 0x0580;
    private static final int CNZ2_CAMERA_MAX_Y = 0x1000;
    private static final int EXTRA_MANAGER_BYTES = 30;

    private Sonic3kLoadBootstrap bootstrap = Sonic3kLoadBootstrap.NORMAL;
    private Sonic3kAIZEvents aizEvents;
    private Sonic3kCNZEvents cnzEvents;
    private Sonic3kHCZEvents hczEvents;
    private Sonic3kICZEvents iczEvents;
    private Sonic3kLBZEvents lbzEvents;
    private Sonic3kMGZEvents mgzEvents;
    private Sonic3kMHZEvents mhzEvents;
    private final S3kFixedAirCountdownManager fixedAirCountdownManager =
            new S3kFixedAirCountdownManager();

    // Tracks whether the intro-fall forced animation is active on each player.
    // Cleared per-player when they land (air → ground transition).
    private boolean introFallActiveOnPlayer;
    private boolean introFallActiveOnSidekick;

    // Set by HCZ Act 1 transition: after the seamless reload to Act 2, the
    // whirlpool descent cutscene should play. Consumed on the first onUpdate()
    // after the transition completes.
    private boolean hczPendingPostTransitionCutscene;

    // Set by MGZ Act 1 transition: after the seamless reload to Act 2, the
    // player (still in signpost victory pose) must be released so they can
    // resume playing. Consumed on the first onUpdate() in MGZ Act 2.
    private boolean mgzPendingPostTransitionRelease;

    // Set by CNZ Act 1 transition: after the seamless reload to Act 2, the
    // ROM's surviving results/end-sign-control object chain later clears
    // _unkFAA8 and restores player control. The engine reload rebuild removes
    // that object chain, so the event manager carries the delayed handoff.
    private int cnzPendingPostTransitionReleaseFrames;
    private int cnzPendingPostTransitionAct2SizeFrames;
    private boolean cnzPostTransitionAct2SizeActive;
    private int cnzAct2MinXAccumulator;
    private int cnzAct2MaxXAccumulator;
    private int cnzAct2MinYAccumulator;
    private int cnzAct2MaxYAccumulator;

    public Sonic3kLevelEventManager() {
        super();
    }

    // =========================================================================
    // AbstractLevelEventManager contract
    // =========================================================================

    @Override
    protected int getRoutineStride() {
        return 4;
    }

    @Override
    protected int getEventDataFgSize() {
        return 6; // Events_fg_0..5
    }

    @Override
    protected int getEventDataBgSize() {
        return 24; // Events_bg[24]
    }

    @Override
    public PlayerCharacter getPlayerCharacter() {
        return ActiveGameplayTeamResolver.resolvePlayerCharacter(GameServices.configuration());
    }

    @Override
    protected void onInitLevel(int zone, int act) {
        bootstrap = Sonic3kBootstrapResolver.resolve(zone, act);
        introFallActiveOnPlayer = false;
        introFallActiveOnSidekick = false;
        fixedAirCountdownManager.reset();

        // ROM: Level_FromSavedGame skips intro when Last_star_post_hit != 0.
        // This covers both special stage return (big ring) and bonus stage return.
        // Guard with hasRuntime() so initLevel can be called from unit tests
        // or snapshot-restore paths that have no active gameplay mode.
        if (bootstrap.mode() == Sonic3kLoadBootstrap.Mode.INTRO
                && GameServices.hasRuntime()
                && (GameServices.level().hasBigRingReturn()
                    || GameServices.level().isBonusStageReturn())) {
            bootstrap = new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, null);
            LOG.info("S3K bootstrap: skipping intro (returning from stage)");
        }

        if (bootstrap.isSkipIntro()) {
            LOG.info("S3K bootstrap: skipping intro for zone " + zone + " act " + act);
        }

        // Create zone-specific event handlers after bootstrap resolution
        if (zone == Sonic3kZoneIds.ZONE_AIZ) {
            aizEvents = new Sonic3kAIZEvents(bootstrap);
            aizEvents.init(act);
        } else {
            aizEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_CNZ) {
            cnzEvents = new Sonic3kCNZEvents();
            cnzEvents.init(act);
        } else {
            cnzEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_HCZ) {
            hczEvents = new Sonic3kHCZEvents();
            hczEvents.init(act);
        } else {
            hczEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_ICZ) {
            iczEvents = new Sonic3kICZEvents();
            iczEvents.init(act);
        } else {
            iczEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_LBZ) {
            lbzEvents = new Sonic3kLBZEvents();
            lbzEvents.init(act);
        } else {
            lbzEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_MGZ) {
            mgzEvents = new Sonic3kMGZEvents();
            mgzEvents.init(act);
        } else {
            mgzEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_MHZ) {
            mhzEvents = new Sonic3kMHZEvents();
            mhzEvents.init(act);
        } else {
            mhzEvents = null;
        }

        // Install typed zone runtime state into the registry.
        // Uses getActiveRuntime() to avoid the mode-checking side effects of
        // getCurrent() which can destroy the runtime during level loading.
        installZoneRuntimeState(zone, act);
        installFixedDynamicObjects(zone);
    }

    @Override
    public void updateFixedInLevelObjects() {
        fixedAirCountdownManager.update();
    }

    @Override
    public boolean ownsFixedDrowningBubbleCadence() {
        return true;
    }

    @Override
    public boolean ownsFixedDrowningBubbleCadence(AbstractPlayableSprite player) {
        return fixedAirCountdownManager.ownsCadenceFor(player);
    }

    @Override
    public boolean isSidekickObjectOrderFollowSteeringContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        return Sonic3kSidekickFollowContext.isObjectOrderFollowSteeringContext(sidekick, effectiveLeader);
    }

    @Override
    public boolean isSidekickObjectOrderFollowNudgeContext(
            AbstractPlayableSprite sidekick,
            AbstractPlayableSprite effectiveLeader) {
        return Sonic3kSidekickFollowContext.isObjectOrderFollowNudgeContext(sidekick, effectiveLeader);
    }

    @Override
    public boolean isSidekickDoorSupportGraceFollowSteeringContext(
            AbstractPlayableSprite sidekick,
            ObjectInstance ridingObject) {
        return Sonic3kSidekickFollowContext.isDoorSupportGraceFollowSteeringContext(sidekick, ridingObject);
    }

    @Override
    public boolean usesSidekickRomVisibleCatchUpMarkerFrameCounterBridge(AbstractPlayableSprite sidekick) {
        return Sonic3kSidekickFollowContext.usesRomVisibleCatchUpMarkerFrameCounterBridge(sidekick);
    }

    @Override
    public boolean shouldEnterSidekickDormantMarker(AbstractPlayableSprite sidekick) {
        return (aizEvents != null && aizEvents.shouldEnterIntroSidekickDormantMarker(sidekick))
                || (iczEvents != null && iczEvents.shouldEnterIntroSidekickDormantMarker(sidekick));
    }

    private void installZoneRuntimeState(int zone, int act) {
        if (!GameServices.hasRuntime()) {
            LOG.fine("Skipping S3K zone runtime registration because no active runtime is installed");
            return;
        }
        ZoneRuntimeRegistry registry = GameServices.zoneRuntimeRegistry();
        PlayerCharacter playerCharacter = getPlayerCharacter();
        if (zone == Sonic3kZoneIds.ZONE_AIZ && aizEvents != null) {
            registry.install(new AizZoneRuntimeState(act, playerCharacter, aizEvents));
        } else if (zone == Sonic3kZoneIds.ZONE_CNZ && cnzEvents != null) {
            registry.install(new CnzZoneRuntimeState(act, playerCharacter, cnzEvents));
        } else if (zone == Sonic3kZoneIds.ZONE_HCZ && hczEvents != null) {
            registry.install(new HczZoneRuntimeState(act, playerCharacter, hczEvents));
        } else if (zone == Sonic3kZoneIds.ZONE_MGZ && mgzEvents != null) {
            registry.install(new MgzZoneRuntimeState(act, playerCharacter, mgzEvents));
        } else if (zone == Sonic3kZoneIds.ZONE_ICZ && iczEvents != null) {
            registry.install(new IczZoneRuntimeState(act, playerCharacter, iczEvents));
        } else if (zone == Sonic3kZoneIds.ZONE_MHZ && mhzEvents != null) {
            registry.install(new MhzZoneRuntimeState(act, playerCharacter, mhzEvents));
        } else if (zone == Sonic3kZoneIds.ZONE_LBZ) {
            registry.install(new LbzZoneRuntimeState(act, playerCharacter));
        } else {
            registry.clear();
        }
    }

    private void installFixedDynamicObjects(int zone) {
        if (!GameServices.hasRuntime() || zone != Sonic3kZoneIds.ZONE_MHZ) {
            return;
        }
        ObjectManager objectManager = GameServices.level().getObjectManager();
        if (objectManager == null) {
            return;
        }
        boolean alreadyInstalled = objectManager.getActiveObjects().stream()
                .anyMatch(MhzPollenSpawnerInstance.class::isInstance);
        if (!alreadyInstalled) {
            objectManager.createDynamicObject(MhzPollenSpawnerInstance::new);
        }
    }

    @Override
    protected void onUpdate() {
        handleBonusStageTopExit();
        // After HCZ seamless transition to Act 2: start the whirlpool descent
        // cutscene that spirals Sonic down into the Act 2 starting area.
        if (hczPendingPostTransitionCutscene && hczEvents != null) {
            hczPendingPostTransitionCutscene = false;
            hczEvents.startPostTransitionCutscene();
        }

        // Clear intro-fall forced animation when players land
        updateIntroFallState();

        // ROM: ScreenEvents dispatches to both FG and BG handlers each frame.
        // Boss_flag gates FG events during boss fights.
        if (aizEvents != null && currentZone == Sonic3kZoneIds.ZONE_AIZ) {
            aizEvents.update(currentAct, frameCounter);
        }
        if (cnzEvents != null && currentZone == Sonic3kZoneIds.ZONE_CNZ) {
            cnzEvents.update(currentAct, frameCounter);
        }
        if (hczEvents != null && currentZone == Sonic3kZoneIds.ZONE_HCZ) {
            hczEvents.update(currentAct, frameCounter);
        }
        if (iczEvents != null && currentZone == Sonic3kZoneIds.ZONE_ICZ) {
            iczEvents.update(currentAct, frameCounter);
        }
        if (lbzEvents != null && currentZone == Sonic3kZoneIds.ZONE_LBZ) {
            lbzEvents.update(currentAct, frameCounter);
        }
        if (mgzEvents != null && currentZone == Sonic3kZoneIds.ZONE_MGZ) {
            mgzEvents.update(currentAct, frameCounter);
        }
        if (mhzEvents != null && currentZone == Sonic3kZoneIds.ZONE_MHZ) {
            mhzEvents.update(currentAct, frameCounter);
        }
        releasePendingMgzPostTransition();
        releasePendingCnzPostTransition();
        updatePendingCnzAct2LevelSizeChange();
        syncSidekickBoundsToCamera();
    }

    /**
     * Keep CPU sidekick level bounds aligned with S3K's dynamic camera bounds.
     * Unlike S2, S3K currently has no zone-specific sidekick bound overrides, so
     * mirroring the live camera bounds each frame prevents stale respawn/death
     * limits after resize scripts move the arena.
     */
    private void syncSidekickBoundsToCamera() {
        Camera camera = GameServices.cameraOrNull();
        if (camera == null) {
            return;
        }
        int minX = camera.getMinX();
        int maxX = camera.getMaxX();
        int maxY = Math.max(camera.getMaxY(), camera.getMaxYTarget());
        for (AbstractPlayableSprite sidekick : sidekickSpritesFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (sidekick.getCpuController() != null) {
                sidekick.getCpuController().setLevelBounds(minX, maxX, maxY);
            }
        }
    }

    private void handleBonusStageTopExit() {
        if (currentZone != Sonic3kZoneIds.ZONE_GLOWING_SPHERE) {
            return;
        }
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null || player.getCentreY() >= PACHINKO_TOP_EXIT_Y) {
            return;
        }
        var provider = GameServices.bonusStageOrNull();
        if (provider != null) {
            provider.requestExit();
        }
    }

    /**
     * Clears the forced intro-fall animation on each player once they land.
     * In the ROM, normal movement code overwrites obAnim on landing; here the
     * profile-based animation system needs the forced override cleared so it
     * can resolve the correct ground animation.
     */
    private void updateIntroFallState() {
        if (introFallActiveOnPlayer) {
            AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
            if (player != null && !player.getAir()) {
                player.setForcedAnimationId(-1);
                introFallActiveOnPlayer = false;
            }
        }
        if (introFallActiveOnSidekick) {
            boolean anySidekickStillFalling = false;
            for (AbstractPlayableSprite sidekick : sidekickSpritesFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
                if (sidekick.getAir()) {
                    anySidekickStillFalling = true;
                } else if (sidekick.getForcedAnimationId() >= 0) {
                    sidekick.setForcedAnimationId(-1);
                }
            }
            if (!anySidekickStillFalling) {
                introFallActiveOnSidekick = false;
            }
        }
    }

    // =========================================================================
    // SpawnLevelMainSprites — zone-specific player state
    // =========================================================================

    /**
     * ROM equivalent: SpawnLevelMainSprites zone-specific branches
     * (sonic3k.asm:8132–8178).
     *
     * <p>Sets animation, airborne flag, and jumping state for zones where
     * the player starts mid-air (falling intros). Called after both the main
     * player and sidekicks have been spawned.
     *
     * <p>Zones handled:
     * <ul>
     *   <li>AIZ1 ($0000): Sonic+Tails intro parks Player 2 in dormant marker state
     *       while Obj_AIZPlaneIntro owns the opening pan</li>
     *   <li>HCZ1 ($0100): Sonic/Tails anim $1B (tumble), Knuckles anim $21 (glide drop)</li>
     *   <li>MGZ1 ($0200): anim $1B, airborne (loc_68A6)</li>
     *   <li>ICZ1 ($0500): Sonic player modes spawn Obj_LevelIntroICZ1 and
     *       park CPU Tails in routine-$0A dormant marker (loc_690A / loc_13A74)</li>
     *   <li>LRZ1 ($0900) Knuckles: anim $1B, airborne (loc_68A6)</li>
     *   <li>SSZ ($1600): anim $1B, airborne (loc_68A6)</li>
     * </ul>
     */
    public void applyZonePlayerState() {
        if (currentZone == Sonic3kZoneIds.ZONE_AIZ && currentAct == 0
                && AizPlaneIntroInstance.getActiveIntroInstance() != null) {
            applyAizIntroSidekickDormantMarkersAfterSpawn();
        }
        if (currentZone == Sonic3kZoneIds.ZONE_HCZ && currentAct == 0) {
            applyHcz1IntroState();
        }
        // ROM: sonic3k.asm loc_68A6 — simple falling intro (anim $1B + airborne).
        // Applied to MGZ1, SSZ, and LRZ1 (non-Knuckles only).
        if (currentZone == Sonic3kZoneIds.ZONE_MGZ && currentAct == 0) {
            applySimpleFallingIntro("MGZ1");
        }
        if (currentZone == Sonic3kZoneIds.ZONE_ICZ && currentAct == 0
                && iczEvents != null && iczEvents.hasSonicSnowboardIntroPlayerMode()) {
            IczSnowboardIntroInstance.applyInitialPlayerLock(GameServices.camera().getFocusedSprite());
            applyIczIntroSidekickDormantMarkersAfterSpawn();
        }
        if (currentZone == Sonic3kZoneIds.ZONE_LBZ && currentAct == 0) {
            spawnLbz1GroundLaunchIntro(false);
        }
        // ROM SpawnLevelMainSprites loc_68D8 (sonic3k.asm:8187-8197): at CNZ Act 1
        // a throwaway Player_2 Tails is spawned to carry solo Sonic in. This runs
        // after the spawnSidekicks load step (which clears temporary sidekicks),
        // so the carrier survives. The handler self-gates on act 0 + SONIC_ALONE.
        if (currentZone == Sonic3kZoneIds.ZONE_CNZ && cnzEvents != null) {
            cnzEvents.spawnSoloLeaderCarryInTailsIfNeeded(currentAct);
        }
        // TODO: LRZ1 non-Knuckles, SSZ falling intros (same loc_68A6 path)
    }

    private void applyAizIntroSidekickDormantMarkersAfterSpawn() {
        SpriteManager spriteManager = GameServices.spritesOrNull();
        if (spriteManager == null) {
            return;
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getRegisteredSidekicks()) {
            SidekickCpuController controller = sidekick.getCpuController();
            if (controller != null && shouldEnterSidekickDormantMarker(sidekick)) {
                controller.applyLevelEventDormantMarkerForBootstrap();
            }
        }
    }

    private void applyIczIntroSidekickDormantMarkersAfterSpawn() {
        SpriteManager spriteManager = GameServices.spritesOrNull();
        if (spriteManager == null || iczEvents == null) {
            return;
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getRegisteredSidekicks()) {
            SidekickCpuController controller = sidekick.getCpuController();
            if (controller != null && shouldEnterSidekickDormantMarker(sidekick)) {
                controller.applyLevelEventDormantMarkerForBootstrap();
            }
        }
    }

    public void applyZonePlayerStateAfterTitleCard() {
        applyZonePlayerState();
        if (currentZone == Sonic3kZoneIds.ZONE_LBZ && currentAct == 0) {
            spawnLbz1GroundLaunchIntro(true);
        }
    }

    private void spawnLbz1GroundLaunchIntro(boolean armImmediately) {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        if (objectManager == null) {
            return;
        }
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (object instanceof Lbz1GroundLaunchIntroInstance intro && !intro.isDestroyed()) {
                if (armImmediately) {
                    intro.applyInitialHoldForLevelStart();
                }
                return;
            }
        }
        ObjectSpawn spawn = new ObjectSpawn(0, 0, 0, 0, 0, false, 0);
        Lbz1GroundLaunchIntroInstance intro =
                objectManager.createDynamicObject(() -> new Lbz1GroundLaunchIntroInstance(spawn));
        if (intro != null && armImmediately) {
            intro.applyInitialHoldForLevelStart();
        }
    }

    /**
     * HCZ Act 1 intro: all characters start falling from near the top of the
     * level (Y=$0020). ROM: sonic3k.asm loc_6834–loc_6886.
     *
     * <ul>
     *   <li>Sonic/Tails: animation $1B (HURT_FALL — tumble/flail), airborne</li>
     *   <li>Knuckles: animation $21 (GLIDE_DROP), anim_frame 1, airborne</li>
     *   <li>Tails-alone (Player_mode 2): additionally sets jumping=true</li>
     *   <li>Player 2 (sidekick Tails): animation $1B, airborne, jumping=true</li>
     * </ul>
     */
    private void applyHcz1IntroState() {
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null) {
            return;
        }

        PlayerCharacter character = getPlayerCharacter();

        if (character == PlayerCharacter.KNUCKLES) {
            // ROM: move.w #($21<<8)|$21,anim(a1)  — anim=$21, prev_anim=$21
            //      move.b #1,anim_frame(a1)
            player.setForcedAnimationId(Sonic3kAnimationIds.GLIDE_DROP);
        } else {
            // ROM: move.b #$1B,anim(a1)
            player.setForcedAnimationId(Sonic3kAnimationIds.HURT_FALL);
        }
        player.setAir(true);
        introFallActiveOnPlayer = true;

        // ROM: Tails alone (Player_mode == 2) gets jumping=1 so flight is available
        if (character == PlayerCharacter.TAILS_ALONE) {
            player.setJumping(true);
        }

        // Sidekick (Player 2): anim $1B, airborne, jumping=1
        // ROM: sonic3k.asm:8153–8158
        for (AbstractPlayableSprite sidekick : sidekickSpritesFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            sidekick.setForcedAnimationId(Sonic3kAnimationIds.HURT_FALL);
            sidekick.setAir(true);
            sidekick.setJumping(true);
            introFallActiveOnSidekick = true;
        }

        LOG.info("HCZ1 intro: set falling state on player(s)");
    }

    /**
     * Simple falling intro shared by MGZ1, SSZ, and LRZ1 (non-Knuckles).
     * ROM: sonic3k.asm loc_68A6.
     *
     * <p>Sets animation $1B (HURT_FALL) and airborne on both players.
     * Unlike HCZ1, no Knuckles-specific animation or jumping flag.
     */
    private void applySimpleFallingIntro(String zoneName) {
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null) {
            return;
        }

        player.setForcedAnimationId(Sonic3kAnimationIds.HURT_FALL);
        player.setAir(true);
        introFallActiveOnPlayer = true;

        for (AbstractPlayableSprite sidekick : sidekickSpritesFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            sidekick.setForcedAnimationId(Sonic3kAnimationIds.HURT_FALL);
            sidekick.setAir(true);
            introFallActiveOnSidekick = true;
        }

        LOG.info(zoneName + " intro: set falling state on player(s)");
    }

    // =========================================================================
    // S3K-specific accessors
    // =========================================================================

    public Sonic3kLoadBootstrap getBootstrap() {
        return bootstrap;
    }

    /** Returns the AIZ zone events handler, or null if not in AIZ. */
    public Sonic3kAIZEvents getAizEvents() {
        return aizEvents;
    }

    @Override
    public void setBossFlag(boolean value) {
        if (aizEvents != null) {
            aizEvents.setBossFlag(value);
        }
        if (cnzEvents != null) {
            cnzEvents.setBossFlag(value);
        }
    }

    @Override
    public void setEventsFg5(boolean value) {
        if (aizEvents != null) {
            aizEvents.setEventsFg5(value);
        }
        if (cnzEvents != null) {
            cnzEvents.setEventsFg5(value);
        }
        if (iczEvents != null) {
            iczEvents.setEventsFg5(value);
        }
    }

    @Override
    public void triggerScreenShake(int frames) {
        if (aizEvents != null) {
            aizEvents.triggerScreenShake(frames);
        }
        if (cnzEvents != null) {
            cnzEvents.triggerScreenShake(frames);
        }
    }

    @Override
    public void onBattleshipComplete() {
        if (aizEvents != null) {
            aizEvents.onBattleshipComplete();
        }
    }

    @Override
    public void onBossSmallComplete() {
        if (aizEvents != null) {
            aizEvents.onBossSmallComplete();
        }
    }

    @Override
    public boolean isFireTransitionActive() {
        return aizEvents != null && aizEvents.isFireTransitionActive();
    }

    public boolean isEventsFg5() {
        if (aizEvents != null) {
            return aizEvents.isEventsFg5();
        }
        return iczEvents != null && iczEvents.isEventsFg5();
    }

    @Override
    public boolean isAct2TransitionRequested() {
        if (aizEvents != null) {
            return aizEvents.isAct2TransitionRequested();
        }
        if (cnzEvents != null) {
            return cnzEvents.isAct2TransitionRequested();
        }
        return iczEvents != null && iczEvents.isAct2TransitionRequested();
    }

    /** Returns the CNZ zone events handler, or null if not in CNZ. */
    public Sonic3kCNZEvents getCnzEvents() {
        return cnzEvents;
    }

    @Override
    public void setPendingArenaChunkDestruction(int chunkWorldX, int chunkWorldY) {
        if (cnzEvents != null) {
            cnzEvents.setPendingArenaChunkDestruction(chunkWorldX, chunkWorldY);
        }
    }

    @Override
    public void setBossScrollState(int offsetY, int velocityY) {
        if (cnzEvents != null) {
            cnzEvents.setBossScrollState(offsetY, velocityY);
        }
    }

    @Override
    public void signalMinibossDefeatedForScrollControl() {
        if (cnzEvents != null) {
            cnzEvents.signalMinibossDefeatedForScrollControl();
        }
    }

    @Override
    public boolean consumeMinibossDefeatSignalForScrollControl() {
        return cnzEvents != null && cnzEvents.consumeMinibossDefeatSignalForScrollControl();
    }

    @Override
    public void advanceMinibossBackgroundRoutineAfterScrollSnap() {
        if (cnzEvents != null) {
            cnzEvents.advanceMinibossBackgroundRoutineAfterScrollSnap();
        }
    }

    public void ensureZoneRuntimeStateInstalled() {
        if (!GameServices.hasRuntime()) {
            return;
        }
        var current = GameServices.zoneRuntimeRegistry().current();
        if (current instanceof S3kZoneRuntimeState s3kState
                && s3kState.zoneIndex() == currentZone
                && s3kState.actIndex() == currentAct
                && currentRuntimeStateUsesThisEventInstance(s3kState)) {
            return;
        }
        installZoneRuntimeState(currentZone, currentAct);
    }

    private boolean currentRuntimeStateUsesThisEventInstance(S3kZoneRuntimeState state) {
        return switch (currentZone) {
            case Sonic3kZoneIds.ZONE_AIZ ->
                    state instanceof AizZoneRuntimeState aizState && aizState.isBackedBy(aizEvents);
            case Sonic3kZoneIds.ZONE_CNZ ->
                    state instanceof CnzZoneRuntimeState cnzState && cnzState.isBackedBy(cnzEvents);
            case Sonic3kZoneIds.ZONE_HCZ ->
                    state instanceof HczZoneRuntimeState hczState && hczState.isBackedBy(hczEvents);
            case Sonic3kZoneIds.ZONE_MGZ ->
                    state instanceof MgzZoneRuntimeState mgzState && mgzState.isBackedBy(mgzEvents);
            case Sonic3kZoneIds.ZONE_ICZ ->
                    state instanceof IczZoneRuntimeState iczState && iczState.isBackedBy(iczEvents);
            case Sonic3kZoneIds.ZONE_MHZ ->
                    state instanceof MhzZoneRuntimeState mhzState && mhzState.isBackedBy(mhzEvents);
            case Sonic3kZoneIds.ZONE_LBZ -> state instanceof LbzZoneRuntimeState;
            default -> false;
        };
    }

    @Override
    public void setWallGrabSuppressed(boolean value) {
        if (cnzEvents != null) {
            cnzEvents.setWallGrabSuppressed(value);
        }
    }

    @Override
    public void setWaterButtonArmed(boolean value) {
        if (cnzEvents != null) {
            cnzEvents.setWaterButtonArmed(value);
        }
    }

    @Override
    public boolean isWaterButtonArmed() {
        return cnzEvents != null && cnzEvents.isWaterButtonArmed();
    }

    @Override
    public void setWaterTargetY(int targetY) {
        if (cnzEvents != null) {
            cnzEvents.setWaterTargetY(targetY);
        }
    }

    @Override
    public void setWaterMeanLevel(int meanY) {
        if (cnzEvents != null) {
            cnzEvents.setWaterMeanLevel(meanY);
        }
    }

    @Override
    public void beginKnucklesTeleporterRoute() {
        if (cnzEvents != null) {
            cnzEvents.beginKnucklesTeleporterRoute();
        }
    }

    @Override
    public void endKnucklesTeleporterRoute() {
        if (cnzEvents != null) {
            cnzEvents.endKnucklesTeleporterRoute();
        }
    }

    @Override
    public void markTeleporterBeamSpawned() {
        if (cnzEvents != null) {
            cnzEvents.markTeleporterBeamSpawned();
        }
    }

    /** Returns the HCZ zone events handler, or null if not in HCZ. */
    public Sonic3kMGZEvents getMgzEvents() {
        return mgzEvents;
    }

    /** Returns the LBZ zone events handler, or null if not in LBZ. */
    public Sonic3kLBZEvents getLbzEvents() {
        return lbzEvents;
    }

    @Override
    public void triggerBossCollapseHandoff() {
        if (mgzEvents != null) {
            mgzEvents.triggerBossCollapseHandoff();
        }
    }

    public Sonic3kHCZEvents getHczEvents() {
        return hczEvents;
    }

    public Sonic3kICZEvents getIczEvents() {
        return iczEvents;
    }

    public Sonic3kMHZEvents getMhzEvents() {
        return mhzEvents;
    }

    @Override
    public void setHczBossFlag(boolean value) {
        if (hczEvents != null) {
            hczEvents.setBossFlag(value);
        }
    }

    /**
     * Sets/clears the pending post-transition cutscene flag for HCZ Act 1→2.
     */
    public void setHczPendingPostTransitionCutscene(boolean pending) {
        this.hczPendingPostTransitionCutscene = pending;
    }

    /**
     * Sets Events_fg_5 on the current zone's event handler.
     * ROM: Obj_LevelResultsCreate sets this for Act 1 zones (except AIZ and ICZ)
     * to trigger the background event act transition.
     */
    public void setEventsFg5ForActTransition() {
        if (cnzEvents != null) {
            cnzEvents.setEventsFg5(true);
        }
        if (hczEvents != null) {
            hczEvents.setEventsFg5(true);
        }
        if (mgzEvents != null) {
            mgzEvents.setEventsFg5(true);
        }
        if (mhzEvents != null) {
            mhzEvents.setActTransitionFlag(true);
        }
        if (lbzEvents != null) {
            lbzEvents.setEventsFg5(true);
        }
        // Other zones' event handlers will be added here as implemented.
    }

    @Override
    public void signalActTransition() {
        setEventsFg5ForActTransition();
    }

    @Override
    public void requestHczPostTransitionCutscene() {
        setHczPendingPostTransitionCutscene(true);
    }

    @Override
    public void requestMgzPostTransitionRelease() {
        this.mgzPendingPostTransitionRelease = true;
    }

    @Override
    public void requestCnzPostTransitionRelease(int framesUntilRelease) {
        this.cnzPendingPostTransitionReleaseFrames = Math.max(0, framesUntilRelease);
        this.cnzPendingPostTransitionAct2SizeFrames = CNZ_POST_TRANSITION_ACT2_SIZE_CHANGE_FRAMES;
        this.cnzPostTransitionAct2SizeActive = false;
        this.cnzAct2MinXAccumulator = 0;
        this.cnzAct2MaxXAccumulator = 0;
        this.cnzAct2MinYAccumulator = 0;
        this.cnzAct2MaxYAccumulator = 0;
    }

    /**
     * After the MGZ1 → MGZ2 seamless reload, release the player (and sidekicks)
     * from the signpost victory pose so normal play resumes. The ROM's
     * MGZ1BGE_Transition does not run a cutscene; the player simply continues
     * under their own control once the level has reloaded.
     */
    private void releasePendingMgzPostTransition() {
        if (!mgzPendingPostTransitionRelease) {
            return;
        }
        if (currentZone != Sonic3kZoneIds.ZONE_MGZ || currentAct != 1) {
            return;
        }
        mgzPendingPostTransitionRelease = false;

        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player != null) {
            ObjectControlState.none().applyTo(player);
            player.setControlLocked(false);
            player.setForcedAnimationId(-1);
        }
        for (AbstractPlayableSprite sidekick : sidekickSpritesFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            ObjectControlState.none().applyTo(sidekick);
            sidekick.setControlLocked(false);
            sidekick.setForcedAnimationId(-1);
        }
        LOG.info("MGZ: released player from victory pose after Act 1 → Act 2 reload");
    }

    /**
     * CNZ1's act reload happens while Obj_LevelResults and Obj_EndSignControl
     * are still alive in ROM. Later, LevelResults loc_2DD06 clears _unkFAA8 and
     * EndSignControlAwaitStart calls Restore_PlayerControl for P1/P2
     * (docs/skdisasm/sonic3k.asm:62708-62720,180407-180412,180359-180367).
     * The engine reload rebuilds the object manager, so this local handoff
     * preserves the ROM release timing without retaining act-1 objects.
     */
    private void releasePendingCnzPostTransition() {
        if (cnzPendingPostTransitionReleaseFrames <= 0) {
            return;
        }
        if (currentZone != Sonic3kZoneIds.ZONE_CNZ || currentAct != 1) {
            return;
        }

        cnzPendingPostTransitionReleaseFrames--;
        if (cnzPendingPostTransitionReleaseFrames > 0) {
            return;
        }

        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player != null) {
            restoreControlAfterCnzActTransition(player);
        }
        for (AbstractPlayableSprite sidekick : sidekickSpritesFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            restoreControlAfterCnzActTransition(sidekick);
        }
        LOG.info("CNZ: released player control after Act 1 → Act 2 results handoff");
    }

    private void restoreControlAfterCnzActTransition(AbstractPlayableSprite sprite) {
        ObjectControlState.none().applyTo(sprite);
        sprite.setControlLocked(false);
        sprite.setForcedAnimationId(-1);
        sprite.setAir(false);
    }

    /**
     * ROM: after the surviving EndSignControl object has restored control, its
     * DoStart phase waits for the in-level title-card End_of_level_flag, calls
     * Change_Act2Sizes, and spawns the gradual level-size children. CNZ's
     * seamless reload removes that object chain in the engine, so this bridge
     * mirrors the later Change_Act2Sizes/Obj_*Gradual sequence locally
     * (docs/skdisasm/sonic3k.asm:180415-180419,180575-180632,
     * 178154-178168,178192-178224,197460-197468).
     */
    private void updatePendingCnzAct2LevelSizeChange() {
        if (currentZone != Sonic3kZoneIds.ZONE_CNZ || currentAct != 1) {
            return;
        }
        if (cnzPendingPostTransitionAct2SizeFrames > 0) {
            cnzPendingPostTransitionAct2SizeFrames--;
            if (cnzPendingPostTransitionAct2SizeFrames > 0) {
                return;
            }
            cnzPostTransitionAct2SizeActive = true;
            cnzAct2MinXAccumulator = 0;
            cnzAct2MaxXAccumulator = 0;
            cnzAct2MinYAccumulator = 0;
            cnzAct2MaxYAccumulator = 0;
        }
        if (!cnzPostTransitionAct2SizeActive) {
            return;
        }

        Camera camera = GameServices.cameraOrNull();
        if (camera == null) {
            return;
        }

        boolean minXDone = decrementCameraMinXGradual(camera);
        boolean maxXDone = incrementCameraMaxXGradual(camera);
        boolean minYDone = decrementCameraMinYGradual(camera);
        boolean maxYDone = incrementCameraMaxYGradual(camera);
        camera.setMaxYTarget((short) CNZ2_CAMERA_MAX_Y);
        if (minXDone && maxXDone && minYDone && maxYDone) {
            cnzPostTransitionAct2SizeActive = false;
        }
    }

    private boolean decrementCameraMinXGradual(Camera camera) {
        int current = camera.getMinX() & 0xFFFF;
        cnzAct2MinXAccumulator += 0x4000;
        int delta = cnzAct2MinXAccumulator >> 16;
        int next = current - delta;
        if (next <= CNZ2_CAMERA_MIN_X) {
            camera.setMinX((short) CNZ2_CAMERA_MIN_X);
            return true;
        }
        camera.setMinX((short) next);
        return false;
    }

    private boolean incrementCameraMaxXGradual(Camera camera) {
        int current = camera.getMaxX() & 0xFFFF;
        cnzAct2MaxXAccumulator += 0x4000;
        int delta = cnzAct2MaxXAccumulator >> 16;
        int next = current + delta;
        if (next >= CNZ2_CAMERA_MAX_X) {
            camera.setMaxX((short) CNZ2_CAMERA_MAX_X);
            return true;
        }
        camera.setMaxX((short) next);
        return false;
    }

    private boolean decrementCameraMinYGradual(Camera camera) {
        int current = camera.getMinY() & 0xFFFF;
        cnzAct2MinYAccumulator += 0x4000;
        int delta = cnzAct2MinYAccumulator >> 16;
        int next = current - delta;
        if (next <= CNZ2_CAMERA_MIN_Y) {
            camera.setMinY((short) CNZ2_CAMERA_MIN_Y);
            return true;
        }
        camera.setMinY((short) next);
        return false;
    }

    private boolean incrementCameraMaxYGradual(Camera camera) {
        int current = camera.getMaxY() & 0xFFFF;
        cnzAct2MaxYAccumulator += 0x8000;
        int delta = cnzAct2MaxYAccumulator >> 16;
        int next = current + delta;
        if (next >= CNZ2_CAMERA_MAX_Y) {
            camera.setMaxY((short) CNZ2_CAMERA_MAX_Y);
            return true;
        }
        camera.setMaxY((short) next);
        return false;
    }

    private List<AbstractPlayableSprite> sidekickSpritesFor(ObjectPlayerParticipationPolicy policy) {
        ObjectPlayerQuery query = playerQueryFromGameServices();
        PlayableEntity mainPlayer = query.mainPlayerOrNull();
        List<AbstractPlayableSprite> sidekicks = new ArrayList<>();
        for (PlayableEntity participant : query.playersFor(policy)) {
            if (participant != mainPlayer && participant instanceof AbstractPlayableSprite sidekick) {
                sidekicks.add(sidekick);
            }
        }
        return sidekicks;
    }

    private ObjectPlayerQuery playerQueryFromGameServices() {
        Camera camera = GameServices.cameraOrNull();
        AbstractPlayableSprite mainPlayer = camera != null ? camera.getFocusedSprite() : null;
        SpriteManager sprites = GameServices.spritesOrNull();
        List<? extends PlayableEntity> sidekicks = sprites != null
                ? List.copyOf(sprites.getSidekicks())
                : List.of();
        return new ObjectPlayerQuery(
                () -> mainPlayer,
                () -> sidekicks);
    }

    /**
     * Returns the current Dynamic_resize_routine value from the active zone
     * events handler. ROM: Saved2_dynamic_resize_routine.
     */
    public int getDynamicResizeRoutine() {
        if (aizEvents != null) {
            return aizEvents.getDynamicResizeRoutine();
        }
        if (cnzEvents != null) {
            return cnzEvents.getDynamicResizeRoutine();
        }
        if (hczEvents != null) {
            return hczEvents.getDynamicResizeRoutine();
        }
        if (iczEvents != null) {
            return iczEvents.getDynamicResizeRoutine();
        }
        if (mgzEvents != null) {
            return mgzEvents.getDynamicResizeRoutine();
        }
        return 0;
    }

    @Override
    public int checkpointDynamicResizeRoutine() {
        return getDynamicResizeRoutine();
    }

    /**
     * Restores the Dynamic_resize_routine after a big ring special stage return.
     * Must be called AFTER initLevel() (which resets it to 0).
     */
    public void setDynamicResizeRoutine(int routine) {
        if (aizEvents != null) {
            aizEvents.setDynamicResizeRoutine(routine);
        }
        if (cnzEvents != null) {
            cnzEvents.setDynamicResizeRoutine(routine);
        }
        if (hczEvents != null) {
            hczEvents.setDynamicResizeRoutine(routine);
        }
        if (iczEvents != null) {
            iczEvents.setDynamicResizeRoutine(routine);
        }
        if (mgzEvents != null) {
            mgzEvents.setDynamicResizeRoutine(routine);
        }
    }

    /**
     * S3K zone handlers maintain their own routine counters independently of
     * the base class fields. Override to read from the active zone handler so
     * callers (GameLoop bonus stage capture) get the correct value.
     */
    @Override
    public int getEventRoutineFg() {
        return getDynamicResizeRoutine();
    }

    /**
     * S3K zone handlers maintain their own BG routine counters independently of
     * the base class fields. CNZ now persists a local background routine for
     * save/restore parity, so callers must read from the active zone handler.
     */
    @Override
    public int getEventRoutineBg() {
        if (cnzEvents != null) {
            return cnzEvents.getBackgroundRoutine();
        }
        return super.getEventRoutineBg();
    }

    /**
     * Restores the event routine state after a bonus/special stage return.
     * Propagates to the active zone handler so its internal state machine
     * resumes from the saved position instead of replaying from 0.
     */
    @Override
    public void restoreEventRoutineState(int routineFg, int routineBg) {
        super.restoreEventRoutineState(routineFg, routineBg);
        setDynamicResizeRoutine(routineFg);
        if (cnzEvents != null) {
            cnzEvents.setBackgroundRoutine(routineBg);
        }
    }

    /**
     * Resets mutable state including static/global state in S3K event helpers.
     * Extends the base {@link AbstractLevelEventManager#resetState()} to also
     * clear cross-object cutscene refs, HCZ helper gates, AIZ fire/tree/intro
     * state, and other runtime handoff state that would otherwise leak across
     * level loads and test iterations.
     */
    @Override
    public void resetState() {
        super.resetState();
        introFallActiveOnPlayer = false;
        introFallActiveOnSidekick = false;
        clearPostTransitionHandoffState();
        Sonic3kAIZEvents.resetGlobalState();
        CutsceneKnucklesCnz2AInstance.clearActiveInstance();
        CutsceneKnucklesCnz2BInstance.clearActiveInstance();
        CutsceneKnucklesHcz2Instance.clearActiveInstance();
        HCZWaterTunnelHandler.reset();
        HCZWaterSkimHandler.reset();
        HCZWaterRushObjectInstance.HCZBreakableBarState.reset();
        HCZWaterRushObjectInstance.HCZWaterRushPaletteCycleGate.reset();
        HCZConveyorBeltObjectInstance.resetLoadArray();
        Aiz2BossEndSequenceState.reset();
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
        AizHollowTreeObjectInstance.resetTreeRevealCounter();
        AizPlaneIntroInstance.resetIntroPhaseState();
        IczSnowboardArtLoader.reset();
    }

    private void clearPostTransitionHandoffState() {
        hczPendingPostTransitionCutscene = false;
        mgzPendingPostTransitionRelease = false;
        cnzPendingPostTransitionReleaseFrames = 0;
        cnzPendingPostTransitionAct2SizeFrames = 0;
        cnzPostTransitionAct2SizeActive = false;
        cnzAct2MinXAccumulator = 0;
        cnzAct2MaxXAccumulator = 0;
        cnzAct2MinYAccumulator = 0;
        cnzAct2MaxYAccumulator = 0;
    }
    /**
     * Intercepts pit death in S3K bonus stages (Gumball, Pachinko, Slots).
     * ROM: Obj_GumballMachine init does st (Disable_death_plane).w — bonus
     * stages don't kill the player for falling off the bottom. Instead,
     * falling through triggers the stage exit via the exit trigger child.
     * <p>
     * If the player falls below the exit trigger (past the bottom of the stage),
     * force the stage to end.
     */
    @Override
    public boolean interceptPitDeath(AbstractPlayableSprite player) {
        if (mgzEvents != null && mgzEvents.isBossTransitionDeathPlaneDisabled()) {
            return true;
        }
        if (isInBonusStage()) {
            // Trigger bonus stage exit if player has fallen out of the arena
            com.openggf.game.BonusStageProvider provider =
                    com.openggf.game.GameServices.bonusStageOrNull();
            if (provider != null) {
                provider.requestExit();
            }
            return true; // Suppress death
        }
        return false;
    }

    private boolean isInBonusStage() {
        return currentZone == Sonic3kZoneIds.ZONE_GUMBALL
                || currentZone == Sonic3kZoneIds.ZONE_GLOWING_SPHERE
                || currentZone == Sonic3kZoneIds.ZONE_SLOT_MACHINE;
    }

    // =========================================================================
    // RewindSnapshottable extra-state hooks (C.4)
    // =========================================================================

    /** Accessor for test/diagnostic use — returns the S3K zone event handler for AIZ. */
    public Sonic3kAIZEvents getAizEventsForTest()  { return aizEvents; }
    /** Accessor for test/diagnostic use — returns the S3K zone event handler for HCZ. */
    public Sonic3kHCZEvents getHczEventsForTest()  { return hczEvents; }
    /** Accessor for test/diagnostic use — returns the S3K zone event handler for CNZ. */
    public Sonic3kCNZEvents getCnzEventsForTest()  { return cnzEvents; }
    /** Accessor for test/diagnostic use — returns the S3K zone event handler for MGZ. */
    public Sonic3kMGZEvents getMgzEventsForTest()  { return mgzEvents; }
    /** Accessor for test/diagnostic use — returns the S3K zone event handler for MHZ. */
    public Sonic3kMHZEvents getMhzEventsForTest()  { return mhzEvents; }

    @Override
    protected byte[] captureExtra() {
        // Layout:
        //   30 bytes  manager-level (bootstrap mode ordinal + 4 booleans + CNZ release/size counters + size-change state)
        //   1 byte    aiz handler present flag
        //   4 bytes   aiz schema payload length, when present
        //   N bytes   aiz schema payload, when present
        //   1 byte    hcz handler present flag
        //   4 bytes   hcz schema payload length, when present
        //   N bytes   hcz schema payload, when present
        //   1 byte    cnz handler present flag
        //   4 bytes   cnz schema payload length, when present
        //   N bytes   cnz schema payload, when present
        //   1 byte    mgz handler present flag
        //   4 bytes   mgz schema payload length, when present
        //   N bytes   mgz schema payload, when present
        //   1 byte    mhz handler present flag
        //   184 bytes mhz state (see Sonic3kMHZEvents.rewindStateBytes())
        //   1 byte    icz handler present flag
        //   25 bytes  icz state (5 booleans + 5 ints; see Sonic3kICZEvents.rewindStateBytes())
        //   28 bytes  fixed Breathing_bubbles/Breathing_bubbles_P2 sidecars
        byte[] aizBytes = aizEvents != null ? ZoneEventSchemaSidecar.capture(aizEvents) : null;
        byte[] hczBytes = hczEvents != null ? ZoneEventSchemaSidecar.capture(hczEvents) : null;
        byte[] cnzBytes = cnzEvents != null ? ZoneEventSchemaSidecar.capture(cnzEvents) : null;
        byte[] mgzBytes = mgzEvents != null ? ZoneEventSchemaSidecar.capture(mgzEvents) : null;
        int mhzSize = Sonic3kMHZEvents.rewindStateBytes(); // 184
        int iczSize = Sonic3kICZEvents.rewindStateBytes(); // 25
        int size = EXTRA_MANAGER_BYTES;
        size += aizBytes != null ? 1 + Integer.BYTES + aizBytes.length : 1;
        size += hczBytes != null ? 1 + Integer.BYTES + hczBytes.length : 1;
        size += cnzBytes != null ? 1 + Integer.BYTES + cnzBytes.length : 1;
        size += mgzBytes != null ? 1 + Integer.BYTES + mgzBytes.length : 1;
        size += 1 + (mhzEvents != null ? mhzSize : 0);
        size += 1 + (iczEvents != null ? iczSize : 0);
        size += S3kFixedAirCountdownManager.REWIND_STATE_BYTES;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(size);
        // Manager-level
        buf.put((byte) bootstrap.mode().ordinal());
        buf.put((byte) (introFallActiveOnPlayer  ? 1 : 0));
        buf.put((byte) (introFallActiveOnSidekick ? 1 : 0));
        buf.put((byte) (hczPendingPostTransitionCutscene ? 1 : 0));
        buf.put((byte) (mgzPendingPostTransitionRelease  ? 1 : 0));
        buf.putInt(cnzPendingPostTransitionReleaseFrames);
        buf.putInt(cnzPendingPostTransitionAct2SizeFrames);
        buf.put((byte) (cnzPostTransitionAct2SizeActive ? 1 : 0));
        buf.putInt(cnzAct2MinXAccumulator);
        buf.putInt(cnzAct2MaxXAccumulator);
        buf.putInt(cnzAct2MinYAccumulator);
        buf.putInt(cnzAct2MaxYAccumulator);
        // AIZ
        if (aizBytes != null) {
            buf.put((byte) 1);
            buf.putInt(aizBytes.length);
            buf.put(aizBytes);
        } else {
            buf.put((byte) 0);
        }
        // HCZ
        if (hczBytes != null) {
            buf.put((byte) 1);
            buf.putInt(hczBytes.length);
            buf.put(hczBytes);
        } else {
            buf.put((byte) 0);
        }
        // CNZ
        if (cnzBytes != null) {
            buf.put((byte) 1);
            buf.putInt(cnzBytes.length);
            buf.put(cnzBytes);
        } else {
            buf.put((byte) 0);
        }
        // MGZ
        if (mgzBytes != null) {
            buf.put((byte) 1);
            buf.putInt(mgzBytes.length);
            buf.put(mgzBytes);
        } else {
            buf.put((byte) 0);
        }
        // MHZ
        if (mhzEvents != null) {
            buf.put((byte) 1);
            mhzEvents.writeRewindState(buf);
        } else {
            buf.put((byte) 0);
        }
        // ICZ
        if (iczEvents != null) {
            buf.put((byte) 1);
            writeIczState(buf, iczEvents);
        } else {
            buf.put((byte) 0);
        }
        fixedAirCountdownManager.writeRewindState(buf);
        return buf.array();
    }

    @Override
    protected void restoreExtra(byte[] extra) {
        if (extra == null || extra.length < 5) {
            return;
        }
        if (!hasValidExtraFraming(extra)) {
            LOG.warning("Skipping malformed S3K level-event rewind extra: invalid sidecar framing");
            return;
        }
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(extra);
        // Manager-level
        int modeOrdinal = buf.get() & 0xFF;
        Sonic3kLoadBootstrap.Mode[] modes = Sonic3kLoadBootstrap.Mode.values();
        Sonic3kLoadBootstrap.Mode mode = (modeOrdinal < modes.length) ? modes[modeOrdinal] : Sonic3kLoadBootstrap.Mode.NORMAL;
        bootstrap = new Sonic3kLoadBootstrap(mode, bootstrap.introStartPosition());
        introFallActiveOnPlayer           = buf.get() != 0;
        introFallActiveOnSidekick         = buf.get() != 0;
        hczPendingPostTransitionCutscene  = buf.get() != 0;
        mgzPendingPostTransitionRelease   = buf.get() != 0;
        cnzPendingPostTransitionReleaseFrames = buf.remaining() >= Integer.BYTES ? buf.getInt() : 0;
        cnzPendingPostTransitionAct2SizeFrames = buf.remaining() >= Integer.BYTES ? buf.getInt() : 0;
        cnzPostTransitionAct2SizeActive = buf.remaining() >= 1 && buf.get() != 0;
        cnzAct2MinXAccumulator = buf.remaining() >= Integer.BYTES ? buf.getInt() : 0;
        cnzAct2MaxXAccumulator = buf.remaining() >= Integer.BYTES ? buf.getInt() : 0;
        cnzAct2MinYAccumulator = buf.remaining() >= Integer.BYTES ? buf.getInt() : 0;
        cnzAct2MaxYAccumulator = buf.remaining() >= Integer.BYTES ? buf.getInt() : 0;
        // Size constants must match the write methods
        final int mhzBytes = Sonic3kMHZEvents.rewindStateBytes(); // 184
        final int iczBytes = Sonic3kICZEvents.rewindStateBytes(); // 25
        // AIZ
        if (buf.remaining() >= 1) {
            boolean aizPresent = buf.get() != 0;
            if (aizPresent) {
                if (buf.remaining() < Integer.BYTES) {
                    return;
                }
                int aizLength = buf.getInt();
                if (aizLength < 0 || buf.remaining() < aizLength) {
                    return;
                }
                byte[] bytes = new byte[aizLength];
                buf.get(bytes);
                if (aizEvents != null) {
                    restoreAizSidecar(bytes);
                }
            }
        }
        // HCZ
        if (buf.remaining() >= 1) {
            boolean hczPresent = buf.get() != 0;
            if (hczPresent) {
                if (buf.remaining() < Integer.BYTES) {
                    return;
                }
                int hczLength = buf.getInt();
                if (hczLength < 0 || buf.remaining() < hczLength) {
                    return;
                }
                byte[] bytes = new byte[hczLength];
                buf.get(bytes);
                if (hczEvents != null) {
                    restoreHczSidecar(bytes);
                }
            }
        }
        // CNZ
        if (buf.remaining() >= 1) {
            boolean cnzPresent = buf.get() != 0;
            if (cnzPresent) {
                if (buf.remaining() < Integer.BYTES) {
                    return;
                }
                int cnzLength = buf.getInt();
                if (cnzLength < 0 || buf.remaining() < cnzLength) {
                    return;
                }
                byte[] bytes = new byte[cnzLength];
                buf.get(bytes);
                if (cnzEvents != null) {
                    restoreCnzSidecar(bytes);
                }
            }
        }
        // MGZ
        if (buf.remaining() >= 1) {
            boolean mgzPresent = buf.get() != 0;
            if (mgzPresent) {
                if (buf.remaining() < Integer.BYTES) {
                    return;
                }
                int mgzLength = buf.getInt();
                if (mgzLength < 0 || buf.remaining() < mgzLength) {
                    return;
                }
                byte[] bytes = new byte[mgzLength];
                buf.get(bytes);
                if (mgzEvents != null) {
                    restoreMgzSidecar(bytes);
                }
            }
        }
        // MHZ
        if (buf.remaining() >= 1) {
            boolean mhzPresent = buf.get() != 0;
            if (mhzPresent && mhzEvents != null && buf.remaining() >= mhzBytes) {
                mhzEvents.readRewindState(buf);
            } else if (mhzPresent && buf.remaining() >= mhzBytes) {
                buf.position(buf.position() + mhzBytes);
            }
        }
        // ICZ
        if (buf.remaining() >= 1) {
            boolean iczPresent = buf.get() != 0;
            if (iczPresent && iczEvents != null && buf.remaining() >= iczBytes) {
                readIczState(buf, iczEvents);
            } else if (iczPresent && buf.remaining() >= iczBytes) {
                buf.position(buf.position() + iczBytes);
            }
        }
        if (buf.remaining() >= S3kFixedAirCountdownManager.REWIND_STATE_BYTES) {
            fixedAirCountdownManager.readRewindState(buf);
        }
    }

    private boolean hasValidExtraFraming(byte[] extra) {
        if (extra.length < 5) {
            return false;
        }
        if (extra.length < EXTRA_MANAGER_BYTES) {
            return true;
        }
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(extra);
        buf.position(EXTRA_MANAGER_BYTES);
        if (!skipLengthPrefixedSidecar(buf,
                () -> expectedSidecarBytes(aizEvents, Sonic3kLevelEventManager::newAizFramingProbe))) {
            return false;
        }
        if (!skipLengthPrefixedSidecar(buf,
                () -> expectedSidecarBytes(hczEvents, Sonic3kLevelEventManager::newHczFramingProbe))) {
            return false;
        }
        if (!skipLengthPrefixedSidecar(buf,
                () -> expectedSidecarBytes(cnzEvents, Sonic3kLevelEventManager::newCnzFramingProbe))) {
            return false;
        }
        if (!skipLengthPrefixedSidecar(buf,
                () -> expectedSidecarBytes(mgzEvents, Sonic3kLevelEventManager::newMgzFramingProbe))) {
            return false;
        }
        if (!skipFixedSidecar(buf, Sonic3kMHZEvents.rewindStateBytes())) {
            return false;
        }
        if (!skipFixedSidecar(buf, Sonic3kICZEvents.rewindStateBytes())) {
            return false;
        }
        return buf.remaining() == 0
                || buf.remaining() >= S3kFixedAirCountdownManager.REWIND_STATE_BYTES;
    }

    private static boolean skipLengthPrefixedSidecar(
            java.nio.ByteBuffer buf,
            java.util.function.IntSupplier expectedLength) {
        if (buf.remaining() < 1) {
            return true;
        }
        boolean present = buf.get() != 0;
        if (!present) {
            return true;
        }
        if (buf.remaining() < Integer.BYTES) {
            return false;
        }
        int length = buf.getInt();
        if (length != expectedLength.getAsInt() || buf.remaining() < length) {
            return false;
        }
        buf.position(buf.position() + length);
        return true;
    }

    private static int expectedSidecarBytes(
            Object handler,
            java.util.function.Supplier<Object> fallback) {
        return ZoneEventSchemaSidecar.capture(handler != null ? handler : fallback.get()).length;
    }

    private static Object newAizFramingProbe() {
        return new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
    }

    private static Object newHczFramingProbe() {
        Sonic3kHCZEvents probe = new Sonic3kHCZEvents();
        probe.init(0);
        return probe;
    }

    private static Object newCnzFramingProbe() {
        Sonic3kCNZEvents probe = new Sonic3kCNZEvents();
        probe.init(0);
        return probe;
    }

    private static Object newMgzFramingProbe() {
        Sonic3kMGZEvents probe = new Sonic3kMGZEvents();
        probe.init(1);
        return probe;
    }

    private static boolean skipFixedSidecar(java.nio.ByteBuffer buf, int bytes) {
        if (buf.remaining() < 1) {
            return true;
        }
        boolean present = buf.get() != 0;
        if (!present) {
            return true;
        }
        if (buf.remaining() < bytes) {
            return false;
        }
        buf.position(buf.position() + bytes);
        return true;
    }

    private void restoreAizSidecar(byte[] bytes) {
        byte[] before = ZoneEventSchemaSidecar.capture(aizEvents);
        try {
            ZoneEventSchemaSidecar.restore(aizEvents, bytes);
        } catch (RuntimeException e) {
            try {
                ZoneEventSchemaSidecar.restore(aizEvents, before);
            } catch (RuntimeException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            LOG.warning("Skipping malformed AIZ zone-event rewind sidecar: " + e.getMessage());
        }
    }

    private void restoreHczSidecar(byte[] bytes) {
        byte[] before = ZoneEventSchemaSidecar.capture(hczEvents);
        try {
            ZoneEventSchemaSidecar.restore(hczEvents, bytes);
        } catch (RuntimeException e) {
            try {
                ZoneEventSchemaSidecar.restore(hczEvents, before);
            } catch (RuntimeException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            LOG.warning("Skipping malformed HCZ zone-event rewind sidecar: " + e.getMessage());
        }
    }

    private void restoreCnzSidecar(byte[] bytes) {
        byte[] before = ZoneEventSchemaSidecar.capture(cnzEvents);
        try {
            ZoneEventSchemaSidecar.restore(cnzEvents, bytes);
        } catch (RuntimeException e) {
            try {
                ZoneEventSchemaSidecar.restore(cnzEvents, before);
            } catch (RuntimeException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            LOG.warning("Skipping malformed CNZ zone-event rewind sidecar: " + e.getMessage());
        }
    }

    private void restoreMgzSidecar(byte[] bytes) {
        byte[] before = ZoneEventSchemaSidecar.capture(mgzEvents);
        try {
            ZoneEventSchemaSidecar.restore(mgzEvents, bytes);
        } catch (RuntimeException e) {
            try {
                ZoneEventSchemaSidecar.restore(mgzEvents, before);
            } catch (RuntimeException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            LOG.warning("Skipping malformed MGZ zone-event rewind sidecar: " + e.getMessage());
        }
    }

    // --- ICZ write/read (25 bytes) ---

    private static void writeIczState(java.nio.ByteBuffer buf, Sonic3kICZEvents icz) {
        icz.writeRewindState(buf);
    }

    private static void readIczState(java.nio.ByteBuffer buf, Sonic3kICZEvents icz) {
        icz.readRewindState(buf);
    }
}
