package com.openggf.game;

import com.openggf.architecture.CompositionRoot;
import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.GameMusic;
import com.openggf.audio.GameSound;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.data.SpindashDustArtProvider;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.AnimationTranslator;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Provides cross-game feature donation: loads player sprites, spindash dust,
 * and physics from a donor game (S2 or S3K) while the base game (e.g., S1)
 * handles levels, collision, objects, and audio.
 *
 * <p>Singleton. Activated via {@code CROSS_GAME_FEATURES_ENABLED} config key.
 * The donor ROM is opened as a secondary ROM (no module detection side-effect).
 */
@CompositionRoot
public class CrossGameFeatureProvider implements PlayerSpriteArtProvider, SpindashDustArtProvider {
    private static final Logger LOGGER = Logger.getLogger(CrossGameFeatureProvider.class.getName());

    private static CrossGameFeatureProvider instance;

    private GameId donorGameId;
    private RomByteReader donorReader;
    private CrossGameDonorProvider donorProvider;
    private PlayerSpriteArtProvider donorPlayerArtProvider;
    private SpindashDustArtProvider donorDustArtProvider;
    private SmpsLoader donorSmpsLoader;
    private DacData donorDacData;
    private PhysicsFeatureSet hybridFeatureSet;
    private RenderContext donorRenderContext;
    private PlayerSpriteRenderer instaShieldRenderer;
    private SpriteArtSet instaShieldArtSet;
    private DonorCapabilities donorCapabilities;
    private boolean active;
    private final RomManager romManager;
    private final SonicConfigurationService configService;

    private CrossGameFeatureProvider() {
        this(null, null);
    }

    CrossGameFeatureProvider(RomManager romManager, SonicConfigurationService configService) {
        this.romManager = romManager;
        this.configService = configService;
    }

    private RomManager romManager() {
        return romManager != null ? romManager : GameServices.rom();
    }

    private SonicConfigurationService configService() {
        return configService != null ? configService : GameServices.configuration();
    }

    public static synchronized CrossGameFeatureProvider getInstance() {
        if (instance == null) {
            instance = new CrossGameFeatureProvider();
        }
        return instance;
    }

    /**
     * Initializes the provider by opening the donor ROM and creating art loaders.
     *
     * @param donorGameId "s2" or "s3k"
     * @throws IOException if the donor ROM cannot be opened
     */
    public void initialize(String donorGameCode) throws IOException {
        this.donorGameId = GameId.fromCode(donorGameCode);

        // Same-game guard: disable donation when donor == host
        GameId hostId = resolveHostGameId();
        if (donorGameId == hostId) {
            LOGGER.info("Donor same as host (" + donorGameId.code() + "), donation disabled");
            active = false;
            return;
        }

        Rom donorRom = romManager().getSecondaryRom(donorGameId.code());
        this.donorReader = RomByteReader.fromRom(donorRom);
        GameModule donorModule = resolveDonorModule(donorRom, donorGameId);
        if (donorModule == null) {
            LOGGER.warning("Unable to resolve donor module for: " + donorGameId.code());
            active = false;
            return;
        }
        this.donorProvider = donorModule.getCrossGameDonorProvider();
        if (donorProvider == null) {
            LOGGER.warning("No donor provider for: " + donorGameId.code());
            active = false;
            return;
        }
        this.donorCapabilities = donorProvider.getDonorCapabilities();
        if (donorCapabilities == null) {
            LOGGER.warning("No donor capabilities for: " + donorGameId.code());
            active = false;
            return;
        }
        this.donorPlayerArtProvider = donorProvider.createPlayerArtProvider(donorReader);
        this.donorDustArtProvider = donorProvider.createSpindashDustArtProvider(donorReader);

        hybridFeatureSet = buildHybridFeatureSet();

        // Create donor render context for palette isolation
        donorRenderContext = RenderContext.getOrCreateDonor(donorGameId);
        syncDonorRenderPalette(ActiveGameplayTeamResolver.resolveMainCharacterCode(configService()));

        initializeDonorAudio();
        loadInstaShieldArt();

        active = true;
        LOGGER.info("Cross-game feature provider initialized with donor: " + donorGameId.code());
    }

    /**
     * Returns true if the cross-game feature provider is initialized and active.
     */
    public static boolean isActive() {
        return instance != null && instance.active;
    }

    /**
     * Returns true if the cross-game feature provider is active and the donor
     * game is Sonic 3&amp;K. Used to gate features that require S3K donation
     * specifically (e.g., donated data select presentation).
     */
    public static boolean isS3kDonorActive() {
        return isActive() && instance.donorGameId == GameId.S3K;
    }

    @Override
    public SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException {
        syncDonorRenderPalette(characterCode);
        if (donorCapabilities == null) {
            return null;
        }
        if (donorPlayerArtProvider == null) {
            return null;
        }
        SpriteArtSet donorArt = donorPlayerArtProvider.loadPlayerSpriteArt(characterCode);
        if (donorArt == null || donorArt.animationProfile() == null) {
            return donorArt;
        }
        // Translate the animation profile for host compatibility
        if (donorArt.animationProfile() instanceof ScriptedVelocityAnimationProfile donorProfile) {
            ScriptedVelocityAnimationProfile translated = AnimationTranslator.translate(
                    donorCapabilities, donorProfile, donorArt.animationSet());
            return new SpriteArtSet(donorArt.artTiles(), donorArt.mappingFrames(),
                    donorArt.dplcFrames(), donorArt.paletteIndex(), donorArt.basePatternIndex(),
                    donorArt.frameDelay(), donorArt.bankSize(), translated, donorArt.animationSet());
        }
        return donorArt;
    }

    @Override
    public SpriteArtSet loadSpindashDustArt(String characterCode) throws IOException {
        return donorDustArtProvider == null ? null : donorDustArtProvider.loadSpindashDustArt(characterCode);
    }

    /**
     * Returns a hybrid PhysicsFeatureSet: spindash/insta-shield from donor capabilities,
     * everything else from the current (base) game module.
     */
    public PhysicsFeatureSet getHybridFeatureSet() {
        return hybridFeatureSet;
    }

    /**
     * Returns true if the donor game natively includes a sidekick character (e.g., Tails).
     */
    public boolean supportsSidekick() {
        return donorCapabilities != null && donorCapabilities.hasSidekick();
    }

    /**
     * Loads the character palette (palette line 0) from the donor ROM.
     * This provides the correct Sonic/Tails colors for donor sprites
     * without interfering with the base game's level palettes (lines 1-3).
     *
     * @return the donor's character palette, or null if unavailable
     */
    public Palette loadCharacterPalette() {
        return loadCharacterPalette(null);
    }

    /**
     * Loads the character palette from the donor ROM.
     * Knuckles uses a separate palette (Pal_Knuckles); Sonic/Tails share one.
     *
     * @param characterCode the character code ("sonic", "tails", "knuckles"), or null for default
     * @return the donor's character palette, or null if unavailable
     */
    @Override
    public Palette loadCharacterPalette(String characterCode) {
        if (donorProvider == null || donorReader == null) {
            return null;
        }
        return donorProvider.loadCharacterPalette(donorReader, characterCode);
    }

    /**
     * Returns a palette compatible with the HOST game's palette line 0 layout,
     * but with the donor character's colors. For Knuckles donated from S3K into S2,
     * this returns the S2-compatible Knuckles palette (0x060BEA) which has Knuckles'
     * reds at indices 2-5 but keeps S2's universal colors at indices 6-15.
     *
     * @param characterCode character code
     * @return host-compatible palette, or null if not applicable
     */
    public Palette loadHostCompatiblePalette(String characterCode) {
        if (donorProvider == null || donorReader == null) {
            return null;
        }
        return donorProvider.loadHostCompatiblePalette(donorReader, characterCode);
    }

    public RenderContext getDonorRenderContext() {
        return donorRenderContext;
    }

    private void syncDonorRenderPalette(String characterCode) {
        if (donorRenderContext == null) {
            return;
        }
        Palette charPalette = loadCharacterPalette(characterCode);
        if (charPalette != null) {
            donorRenderContext.setPalette(0, charPalette);
        }
    }

    /**
     * Initializes donor audio: creates a donor SmpsLoader and DacData from
     * the donor ROM, then registers all donor sounds with AudioManager.
     * Base game's sound map always takes priority at playback time, so
     * shared sounds (JUMP, RING) still use the base game's versions.
     */
    private void initializeDonorAudio() {
        GameAudioProfile donorProfile;
        donorProfile = donorProvider.getAudioProfile();
        if (donorProfile == null) {
            LOGGER.warning("No donor audio profile for: " + donorGameId.code());
            return;
        }

        try {
            Rom donorRom = romManager().getSecondaryRom(donorGameId.code());
            donorSmpsLoader = donorProfile.createSmpsLoader(donorRom);
            donorDacData = donorSmpsLoader.loadDacData();

            AudioManager am = GameServices.audio();
            am.registerDonorLoader(donorGameId.code(), donorSmpsLoader, donorDacData,
                    donorProfile.getSequencerConfig());
            Map<GameMusic, Integer> donorMusic = donorProfile.getMusicMap();
            am.registerDonorMusicMap(donorGameId.code(), donorMusic);

            Map<GameSound, Integer> donorSounds = donorProfile.getSoundMap();
            for (Map.Entry<GameSound, Integer> entry : donorSounds.entrySet()) {
                am.registerDonorSound(entry.getKey(), donorGameId.code(), entry.getValue());
            }

            LOGGER.info("Donor audio initialized from " + donorGameId.code()
                    + " (" + donorSounds.size() + " sounds, " + donorMusic.size() + " music cues registered)");
        } catch (IOException e) {
            LOGGER.warning("Failed to initialize donor audio from " + donorGameId.code()
                    + ": " + e.getMessage());
        }
    }

    public String getDonorGameId() {
        return donorGameId == null ? null : donorGameId.code();
    }

    /**
     * Returns true if the donor game uses separate art for Tails' tail appendage (Obj05).
     * S3K has separate Map_Tails_Tail / DPLC_Tails_Tail tables; S2 reuses the main body art.
     */
    public boolean hasSeparateTailsTailArt() {
        return donorProvider != null && donorProvider.hasSeparateTailsTailArt();
    }

    /**
     * Loads the separate tail appendage art set from the donor game.
     * Only valid when {@link #hasSeparateTailsTailArt()} returns true.
     */
    public SpriteArtSet loadTailsTailArt() throws IOException {
        if (donorProvider == null || donorReader == null) {
            return SpriteArtSet.EMPTY;
        }
        return donorProvider.loadTailsTailArt(donorReader);
    }

    /**
     * Creates a Super Sonic state controller using the donor game's implementation
     * and pre-loads ROM data from the donor ROM.
     *
     * @param player the player sprite to attach the controller to
     * @return a donor-game SuperStateController with ROM data pre-loaded, or null
     */
    public SuperStateController createSuperStateController(AbstractPlayableSprite player) {
        if (!active || donorReader == null || donorCapabilities == null || donorProvider == null) {
            return null;
        }
        if (!donorCapabilities.hasSuperTransform()) {
            return null;  // S1 donor: no super transformation
        }
        SuperStateController ctrl = donorProvider.createSuperStateController(player);
        if (ctrl == null) {
            return null;
        }
        try {
            ctrl.loadRomData(donorReader);
            ctrl.setRomDataPreLoaded(true);
            LOGGER.fine("Created cross-game Super Sonic controller from donor: " + donorGameId.code());
        } catch (Exception e) {
            LOGGER.warning("Failed to load donor Super ROM data: " + e.getMessage());
            return null;
        }
        return ctrl;
    }

    public void resetState() {
        close();
    }

    public void close() {
        donorGameId = null;
        donorReader = null;
        donorProvider = null;
        donorPlayerArtProvider = null;
        donorDustArtProvider = null;
        donorSmpsLoader = null;
        donorDacData = null;
        hybridFeatureSet = null;
        donorRenderContext = null;
        instaShieldRenderer = null;
        instaShieldArtSet = null;
        donorCapabilities = null;
        active = false;
    }

    private GameId resolveHostGameId() {
        if (GameServices.hasRuntime()) {
            return GameServices.module().getGameId();
        }
        return GameServices.currentOrBootstrapGameModule().getGameId();
    }

    /**
     * Loads insta-shield art tiles, mappings, DPLCs, and animations from the S3K donor ROM.
     * Only runs when the donor is S3K; silently skips for S2 donors.
     */
    private void loadInstaShieldArt() {
        if (donorProvider == null || donorReader == null) {
            return;
        }
        try {
            instaShieldArtSet = donorProvider.loadInstaShieldArt(donorReader);
            if (instaShieldArtSet == null || instaShieldArtSet.isEmpty()) {
                return;
            }
            instaShieldRenderer = new PlayerSpriteRenderer(instaShieldArtSet);

            LOGGER.info("Loaded donor insta-shield art: " + instaShieldArtSet.artTiles().length + " tiles, "
                    + instaShieldArtSet.mappingFrames().size() + " mapping frames");
        } catch (IOException e) {
            LOGGER.warning("Failed to load donor insta-shield art: " + e.getMessage());
        }
    }

    public PlayerSpriteRenderer getInstaShieldRenderer() {
        return instaShieldRenderer;
    }

    public SpriteArtSet getInstaShieldArtSet() {
        return instaShieldArtSet;
    }

    /**
     * Builds a hybrid feature set: spindash/insta-shield enabled based on donor capabilities,
     * collision model and other flags inherited from the current (base) game module.
     * This ensures that S2/S3K levels keep DUAL_PATH collision (required for plane switching)
     * while S1 levels keep UNIFIED collision.
     */
    private PhysicsFeatureSet buildHybridFeatureSet() {
        PhysicsFeatureSet donorFeatureSet = resolveDonorFeatureSet();
        short[] spindashSpeedTable = donorCapabilities.hasSpindash()
                ? donorFeatureSet.spindashSpeedTable()
                : null;

        // Inherit collision model from the base game module so plane switching
        // works correctly in S2/S3K levels with cross-game features enabled
        PhysicsFeatureSet baseFeatureSet = GameServices.module()
                .getPhysicsProvider().getFeatureSet();

        return new PhysicsFeatureSet(
                donorCapabilities.hasSpindash(),                // spindashEnabled (from donor)
                spindashSpeedTable,                             // spindashSpeedTable (from donor)
                baseFeatureSet.collisionModel(),                // collisionModel (from base game)
                baseFeatureSet.fixedAnglePosThreshold(),        // fixedAnglePosThreshold (from base game)
                baseFeatureSet.lookScrollDelay(),               // lookScrollDelay (from base game)
                baseFeatureSet.waterShimmerEnabled(),           // waterShimmerEnabled (from base game)
                baseFeatureSet.inputAlwaysCapsGroundSpeed(),    // inputAlwaysCapsGroundSpeed (from base game)
                donorCapabilities.hasElementalShields(),        // elementalShieldsEnabled (from donor)
                donorCapabilities.hasInstaShield(),             // instaShieldEnabled (from donor)
                baseFeatureSet.jumpRepressClearsRollJumpBeforeAbility(), // jumpRepressClearsRollJumpBeforeAbility (from base game)
                baseFeatureSet.angleDiffCardinalSnap(),         // angleDiffCardinalSnap (from base game)
                baseFeatureSet.extendedEdgeBalance(),           // extendedEdgeBalance (from base game)
                baseFeatureSet.singleFacingBalanceAnimationSet(), // singleFacingBalanceAnimationSet (from base game)
                baseFeatureSet.ringFloorCheckMask(),            // ringFloorCheckMask (from base game)
                baseFeatureSet.ringCollisionWidth(),            // ringCollisionWidth (from base game)
                baseFeatureSet.ringCollisionHeight(),           // ringCollisionHeight (from base game)
                donorCapabilities.hasElementalShields(),        // lightningShieldEnabled (from donor — lightning requires elemental shields)
                baseFeatureSet.superSpindashSpeedTable(),       // superSpindashSpeedTable (from base game)
                baseFeatureSet.movingCrouchThreshold(),         // movingCrouchThreshold (from base game)
                baseFeatureSet.groundWallCollisionEnabled(),    // groundWallCollisionEnabled (from base game)
                baseFeatureSet.groundWallPushRequiresFacingIntoWall(), // groundWallPushRequiresFacingIntoWall (from base game)
                baseFeatureSet.animationChangeClearsPush(),     // animationChangeClearsPush (from base game)
                baseFeatureSet.airSuperspeedPreserved(),        // airSuperspeedPreserved (from base game)
                baseFeatureSet.slopeResistStartsFromRest(),     // slopeResistStartsFromRest (from base game)
                baseFeatureSet.slopeRepelChecksOnObject(),      // slopeRepelChecksOnObject (from base game)
                baseFeatureSet.slopeRepelUsesS3kSlipKick(),     // slopeRepelUsesS3kSlipKick (from base game)
                baseFeatureSet.pinballLandingPreservesRoll(),   // pinballLandingPreservesRoll (from base game)
                baseFeatureSet.pinballLandingPreservesPinballMode(), // pinballLandingPreservesPinballMode (from base game)
                baseFeatureSet.topSolidLandingAllowsZeroDist(), // topSolidLandingAllowsZeroDist (from base game)
                baseFeatureSet.airBottomSolidHitClearsGroundSpeed(), // airBottomSolidHitClearsGroundSpeed (from base game)
                baseFeatureSet.airRightWallHitContinuesIntoCeilingSeparation(), // airRightWallHitContinuesIntoCeilingSeparation (from base game)
                baseFeatureSet.airLeftWallHitContinuesIntoCeilingSeparation(), // airLeftWallHitContinuesIntoCeilingSeparation (from base game)
                baseFeatureSet.fullSolidBottomOverlapUsesCurrentYRadiusOnly(), // fullSolidBottomOverlapUsesCurrentYRadiusOnly (from base game)
                baseFeatureSet.fastScrollCap(),                 // fastScrollCap (from base game)
                baseFeatureSet.bossHitNegatesGroundSpeed(),     // bossHitNegatesGroundSpeed (from base game)
                baseFeatureSet.stageRingsUseObjectTouchCollection(), // stageRingsUseObjectTouchCollection (from base game)
                baseFeatureSet.stageRingSweepUsesRawCameraWindow(), // stageRingSweepUsesRawCameraWindow (from base game)
                baseFeatureSet.sidekickFollowSnapThreshold(),   // sidekickFollowSnapThreshold (from base game)
                baseFeatureSet.sidekickDespawnX(),              // sidekickDespawnX (from base game)
                baseFeatureSet.sidekickFollowLeadOffset(),      // sidekickFollowLeadOffset (from base game)
                baseFeatureSet.sidekickSpawningRequiresGroundedLeader(), // sidekickSpawningRequiresGroundedLeader (from base game)
                baseFeatureSet.useScreenYWrapValueForVisibility(),  // useScreenYWrapValueForVisibility (from base game)
                baseFeatureSet.sidekickDespawnUsesObjectIdMismatch(), // sidekickDespawnUsesObjectIdMismatch (from base game)
                baseFeatureSet.sidekickFlyLandStatusBlockerMask(),  // sidekickFlyLandStatusBlockerMask (from base game)
                baseFeatureSet.sidekickFlyLandRequiresLeaderAlive(), // sidekickFlyLandRequiresLeaderAlive (from base game)
                baseFeatureSet.solidObjectOffscreenGate(),       // solidObjectOffscreenGate (from base game)
                baseFeatureSet.solidObjectRequiresSidekickOnScreen(), // solidObjectRequiresSidekickOnScreen (from base game)
                baseFeatureSet.sidekickDespawnUsesRidingInstanceLoss(), // sidekickDespawnUsesRidingInstanceLoss (from base game)
                baseFeatureSet.sidekickRespawnEntersCatchUpFlight(), // sidekickRespawnEntersCatchUpFlight (from base game)
                baseFeatureSet.sidekickClearsStalePushVelocityBeforeGroundMove(), // sidekickClearsStalePushVelocityBeforeGroundMove (from base game)
                baseFeatureSet.sidekickCpuUsesLevelFrameCounter(), // sidekickCpuUsesLevelFrameCounter (from base game)
                baseFeatureSet.landingRollClearUsesCurrentYRadiusDelta(), // landingRollClearUsesCurrentYRadiusDelta (from base game)
                baseFeatureSet.levelBoundaryRightStrict(), // levelBoundaryRightStrict (from base game)
                baseFeatureSet.levelBoundaryUsesCentreY(), // levelBoundaryUsesCentreY (from base game)
                baseFeatureSet.solidObjectTopBranchAlwaysLiftsOnUpwardVelocity(), // solidObjectTopBranchAlwaysLiftsOnUpwardVelocity (from base game)
                baseFeatureSet.sidekickPushBypassUsesGraceStatus(), // sidekickPushBypassUsesGraceStatus (from base game)
                baseFeatureSet.sidekickNormalCpuSkipsHurtRoutine(), // sidekickNormalCpuSkipsHurtRoutine (from base game)
                baseFeatureSet.controlLockLatchesLogicalInput(), // controlLockLatchesLogicalInput (from base game)
                baseFeatureSet.hurtRoutineLatchesLogicalInput(), // hurtRoutineLatchesLogicalInput (from base game)
                baseFeatureSet.waterExitBoostSkipsFastUpwardVelocity(), // waterExitBoostSkipsFastUpwardVelocity (from base game)
                baseFeatureSet.slopeResistAppliesAtZeroInertia(), // slopeResistAppliesAtZeroInertia (from base game)
                baseFeatureSet.permanentRespawnTableLatch(), // permanentRespawnTableLatch (from base game)
                baseFeatureSet.objectsExecuteAfterPlayerPhysics(), // objectsExecuteAfterPlayerPhysics (from base game)
                baseFeatureSet.speedShoesTimerPrePhysicsExtraTicks(), // speedShoesTimerPrePhysicsExtraTicks (from base game)
                baseFeatureSet.shieldObjectFixedSlotIndex(), // shieldObjectFixedSlotIndex (from base game)
                baseFeatureSet.invincibilityStarsFixedSlotIndex(), // invincibilityStarsFixedSlotIndex (from base game)
                baseFeatureSet.touchResponseUsesRenderFlagYGate(), // touchResponseUsesRenderFlagYGate (from base game)
                baseFeatureSet.sidekickDeathUsesDeferredDespawn(), // sidekickDeathUsesDeferredDespawn (from base game)
                baseFeatureSet.rightWallDeepProbePreservesPenetration(), // rightWallDeepProbePreservesPenetration (from base game)
                baseFeatureSet.solidObjectBarelyPokingResolvesAsSide(), // solidObjectBarelyPokingResolvesAsSide (from base game)
                baseFeatureSet.speedShoesTimerDecimation() // speedShoesTimerDecimation (from base game)
        );
    }

    private PhysicsFeatureSet resolveDonorFeatureSet() {
        return switch (donorGameId) {
            case S1 -> PhysicsFeatureSet.SONIC_1;
            case S2 -> PhysicsFeatureSet.SONIC_2;
            case S3K -> PhysicsFeatureSet.SONIC_3K;
        };
    }

    private GameModule resolveDonorModule(Rom donorRom, GameId expectedGameId) {
        return GameServices.romDetection()
                .detectAndCreateModule(donorRom)
                .filter(module -> module.getGameId() == expectedGameId)
                .orElse(null);
    }
}
