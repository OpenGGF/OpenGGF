package com.openggf.game.soniccd;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Game;
import com.openggf.game.*;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.save.SaveSnapshotProvider;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerEngineRules;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.level.Level;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.SecondaryAbility;

import java.util.List;
import java.util.Objects;

/**
 * Real stock CD module retaining its default STATIC R11A terrain admission and
 * a separately named terrain-only development PLAYER entry. Neither admits native
 * title/new-game, placements/rings/HUD/audio driver, saves, export or editor entry.
 * The full native metadata catalogue is not a load-capability list. GameLoop
 * remains the sole mode writer.
 */
public final class SonicCdGameModule implements GameModule {
    private static final String SHARED_REWIND_KEY = "soniccd-terrain-player-shared";
    private static final String RESTORE_CALLBACK_KEY = "soniccd-terrain-player-projection";
    private final boolean playerAdmission;
    private final SonicCdZoneRegistry zones = new SonicCdZoneRegistry();
    private final PhysicsProvider rules;
    private final SonicCdStaticAudioProfile audio = new SonicCdStaticAudioProfile();
    private final SonicCdStaticPresentation presentation = new SonicCdStaticPresentation(this);
    private final SonicCdLevelInitProfile profile = new SonicCdLevelInitProfile(this);
    private final SonicCdPlayerPresentation playerPresentation;
    private final SonicCdPlayerInitProfile playerProfile;
    private final PlayableCharacterRegistry characters;
    private CdGameDataSource source;
    private GameplayModeContext playerMode;
    private CdPlayerFrameRuntime playerFrame;
    private CdPlayableSonic playerActor;
    private boolean constructingPlayer;

    // This stable module adapter owns shared state only. Actor Body/history/display,
    // sounds and the actual sprite packet live exclusively in the actor extra.
    private final RewindSnapshottable<CdPlayerFrameRuntime.SharedState> sharedPlayerAdapter = new RewindSnapshottable<>() {
        @Override public String key() { return SHARED_REWIND_KEY; }
        @Override public CdPlayerFrameRuntime.SharedState capture() {
            requireTerrainPlayerReady();
            playerActor.requireCompletedNativeFrame();
            return playerFrame.captureShared();
        }
        @Override public void restore(CdPlayerFrameRuntime.SharedState snapshot) {
            requireTerrainPlayerReady();
            playerActor.requireCompletedNativeFrame();
            playerFrame.validateSharedRestore(snapshot);
            playerFrame.restoreShared(snapshot);
        }
    };

    /** The stock/default entry retains its STATIC/HOLD/no-actor contract. */
    public SonicCdGameModule() { this(false); }

    private SonicCdGameModule(boolean playerAdmission) {
        this.playerAdmission = playerAdmission;
        rules = playerAdmission ? new CdPlayerEngineRules() : new SonicCdStaticRules();
        playerPresentation = playerAdmission ? new SonicCdPlayerPresentation(this) : null;
        playerProfile = playerAdmission ? new SonicCdPlayerInitProfile(this, profile) : null;
        characters = playerAdmission ? PlayableCharacterRegistry.empty().register(CharacterKey.SONIC,
                new CharacterDefinition(CharacterKey.SONIC, "Sonic (CD terrain development)",
                        this::constructTerrainPlayer, null, PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                        code -> { throw SonicCd.unsupported("generic playable-art loading; CD owns its complete resident bank"); }))
                : PlayableCharacterRegistry.empty();
    }

    /** Explicit private development entry; does not change the stock registry or menu. */
    public static SonicCdGameModule terrainOnlyDevelopmentPlayer() { return new SonicCdGameModule(true); }

    @Override public String getIdentifier() {
        return playerAdmission ? "Sonic CD (development terrain-only R11A PLAYER)" : "Sonic CD (development STATIC R11A)";
    }
    @Override public GameId getGameId() { return GameId.SCD; }

    @Override public Game createGame(GameDataSource dataSource) {
        CdGameDataSource candidate = SonicCd.requireSource(dataSource);
        if (source != null && !source.identity().equals(candidate.identity())) {
            throw new IllegalArgumentException("CD module is pinned to another authenticated source; open a new module/world");
        }
        if (playerAdmission && playerFrame != null && !playerFrame.closed()) {
            throw new IllegalStateException("Retire the admitted CD player before binding a fresh level");
        }
        source = candidate;
        zones.bind(candidate);
        if (playerAdmission) playerPresentation.bind(candidate.assets());
        else presentation.bind(candidate.assets());
        return new SonicCd(candidate);
    }

    boolean terrainPlayerAdmission() { return playerAdmission; }

    /** Load admission; the presentation can call this before each row's input/service too. */
    void validateTerrainPlayerRequest() {
        if (!playerAdmission) throw new IllegalStateException("Not the named CD player admission");
        var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode(), "gameplay mode");
        var world = mode.getWorldSession();
        if (world.rootGameModule() != this || world.resolvedGameModule() != this
                || world.getPlayableCharacterRegistry() != characters || WorldSessionPolicyAccess.hasPolicies(world)
                || GameplayInputFilterAccess.current(mode) != GameplayInputFilter.IDENTITY) {
            throw SonicCd.unsupported("decorated modules, world policies or input filters in terrain-player admission");
        }
        var config = GameServices.configuration();
        String sidekick = config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        if (!"sonic".equals(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE))
                || (sidekick != null && !sidekick.isBlank())
                || config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED)) {
            throw SonicCd.unsupported("terrain-player admission requires Sonic alone, without a donor");
        }
        if (mode.getCamera().getWidth() != 320 || mode.getCamera().getHeight() != 224) {
            throw SonicCd.unsupported("terrain-player admission requires an actual 320x224 viewport");
        }
        requireRuntimeSource();
        // A fresh load may retire only this admission's previous actor. Never clear
        // an unknown roster in order to make the new load appear admissible.
        for (var actor : mode.getSpriteManager().getAllSprites()) {
            if (mode != playerMode || actor != playerActor) {
                throw SonicCd.unsupported("foreign or additional terrain-player roster");
            }
        }
        if (!mode.getSpriteManager().getRegisteredSidekicks().isEmpty()) {
            throw SonicCd.unsupported("terrain-player sidekicks");
        }
        if (mode == playerMode && playerActor != null
                && (com.openggf.sprites.managers.SpriteManagerInternalAccess.motionMutators(playerActor) != null
                || !com.openggf.sprites.playable.PlayableMutatorPolicy.STOCK.equals(
                        com.openggf.sprites.playable.PlayableSpriteInternalAccess.mutatorPolicy(playerActor)))) {
            throw SonicCd.unsupported("terrain-player motion or presentation mutators");
        }
    }

    private CdPlayableSonic constructTerrainPlayer(String code, int centreX, int centreY) {
        validateTerrainPlayerRequest();
        if (!constructingPlayer || playerMode != SessionManager.getCurrentGameplayMode()
                || playerFrame == null || playerFrame.closed() || playerActor != null
                || !"sonic".equals(code)
                || centreX != (playerFrame.state().motion().centreX16_16() >> 16)
                || centreY != (playerFrame.state().motion().centreY16_16() >> 16)) {
            throw new IllegalStateException("CD factory is available only for its staged cold player");
        }
        return new CdPlayableSonic(code, playerFrame, GameServices.graphics());
    }

    /** Runs only after the composed terrain/reset/adapter stages have finished. */
    void installTerrainPlayer() {
        validateTerrainPlayerRequest();
        if (playerFrame != null || playerActor != null) throw new IllegalStateException("CD player already staged");
        var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode());
        var candidate = requireRuntimeSource();
        if (!(mode.getLevelManager().getCurrentLevel() instanceof SonicCdLevel level)
                || level.assets() != candidate.assets() || !mode.getSpriteManager().getAllSprites().isEmpty()) {
            throw new IllegalStateException("CD player requires its installed source terrain and an empty roster");
        }
        playerMode = mode;
        try {
            playerFrame = CdPlayerFrameRuntime.terrainOnlyDevelopment(candidate.assets());
            var definition = mode.getWorldSession().getPlayableCharacterRegistry().find(CharacterKey.SONIC).orElseThrow();
            var centre = candidate.assets().loadPlan().initialCentre();
            constructingPlayer = true;
            try {
                playerActor = (CdPlayableSonic) CharacterConstructionScope.call(CharacterKey.SONIC, () ->
                        CharacterConstructionScope.validateFactoryResult(CharacterKey.SONIC,
                                definition.spriteFactory().create("sonic", centre.centreX(), centre.centreY())));
            } finally {
                constructingPlayer = false;
            }
            mode.getSpriteManager().addSprite(playerActor);
            playerPresentation.bindFrame(playerFrame, playerActor);
            mode.registerLevelAdapters(mode.getLevelManager());
            mode.registerGameModuleRewindAdapters();
            var rewind = Objects.requireNonNull(mode.getRewindRegistry(), "rewind registry");
            rewind.deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
            rewind.registerPostRestoreCallback(RESTORE_CALLBACK_KEY, () -> {
                requireTerrainPlayerReady();
                playerPresentation.afterRestore();
            });
            requireTerrainPlayerReady();
        } catch (RuntimeException | Error failure) {
            try { closeTerrainPlayer(); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    void requireTerrainPlayerReady() {
        validateTerrainPlayerRequest();
        var mode = SessionManager.getCurrentGameplayMode();
        if (mode != playerMode || playerFrame == null || playerFrame.closed() || playerActor == null
                || playerFrame.assets() != requireRuntimeSource().assets()
                || mode.getSpriteManager().getAllSprites().size() != 1
                || mode.getSpriteManager().getSprite("sonic") != playerActor
                || mode.getCamera().getFocusedSprite() != playerActor) {
            throw new IllegalStateException("CD terrain-player admission is incomplete or its fixed roster changed");
        }
    }

    CdPlayerFrameRuntime requirePlayerFrame() { requireTerrainPlayerReady(); return playerFrame; }
    CdPlayableSonic requirePlayerActor() { requireTerrainPlayerReady(); return playerActor; }

    /** Also called by the player controller's close(): session teardown does not run the profile. */
    void closeTerrainPlayer() {
        if (!playerAdmission) return;
        var oldFrame = playerFrame;
        var oldActor = playerActor;
        var oldMode = playerMode;
        constructingPlayer = false;
        try {
            if (oldFrame != null) oldFrame.close();
        } finally {
            try {
                playerPresentation.unbind();
            } finally {
                try {
                    try {
                        if (oldMode != null && oldMode.getRewindRegistry() != null) {
                            oldMode.getRewindRegistry().deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
                        }
                    } finally {
                        if (oldMode != null && oldActor != null && oldMode.getSpriteManager() != null
                                && oldMode.getSpriteManager().getSprite(oldActor.getCode()) == oldActor) {
                            oldMode.getSpriteManager().removeSprite(oldActor.getCode());
                        }
                    }
                } finally {
                    playerFrame = null;
                    playerActor = null;
                    playerMode = null;
                }
            }
        }
    }

    void rebindTerrainPresentation() { playerPresentation.bind(requireRuntimeSource().assets()); }

    CdGameDataSource requireRuntimeSource() {
        CdGameDataSource candidate = SonicCd.requireSource(GameServices.level().getWorldSession().getDataSource());
        if (source != null && !source.identity().equals(candidate.identity())) {
            throw new IllegalArgumentException("CD runtime source differs from this module's authenticated binding");
        }
        return candidate;
    }

    @Override public Level transformDecodedLevel(Level decoded) {
        var candidate = requireRuntimeSource();
        if (!(decoded instanceof SonicCdLevel level)
                || !candidate.assets().identity().equals(level.assets().identity())) {
            throw new IllegalArgumentException("CD decoded terrain differs from the authenticated runtime stage");
        }
        SonicCdStaticPresentation.publishDecodedGraphics(level, GameServices.graphics());
        return level;
    }

    void retirePriorAudio() { audio.retirePriorAudio(GameServices.audio()); }

    @Override public TouchResponseTable createTouchResponseTable(GameDataSource dataSource) {
        SonicCd.requireSource(dataSource);
        throw SonicCd.unsupported("native touch response (no actors are installed)");
    }

    @Override public ObjectRegistry createObjectRegistry() {
        return new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) {
                throw SonicCd.unsupported("native object spawning");
            }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) {
                if (!spawns.isEmpty()) throw SonicCd.unsupported("executable object placements");
            }
            @Override public String getPrimaryName(int objectId) {
                throw SonicCd.unsupported("native object registry lookup");
            }
            @Override public ObjectSlotLayout objectSlotLayout() {
                throw SonicCd.unsupported("native object slot layout");
            }
        };
    }
    @Override public ObjectPlacementEncoding getObjectPlacementEncoding() {
        throw SonicCd.unsupported("editor object encoding; raw native placements remain in CdR11aAssets");
    }
    @Override public int getPlaneSwitcherObjectId() { return 0; }
    @Override public PlaneSwitcherConfig getPlaneSwitcherConfig() { throw SonicCd.unsupported("native plane switching"); }
    @Override public LevelEventProvider getLevelEventProvider() { return null; }
    @Override public RespawnState createRespawnState() { return new SonicCdStaticState.Respawn(); }
    @Override public LevelState createLevelState() {
        return playerAdmission ? new SonicCdDevelopmentPlayerState() : new SonicCdStaticState.Level();
    }
    @Override public ZoneRegistry getZoneRegistry() { return zones; }
    @Override public ScrollHandlerProvider getScrollHandlerProvider() { return playerAdmission ? playerPresentation : presentation; }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return null; }
    @Override public DebugOverlayProvider getDebugOverlayProvider() { return null; }
    @Override public ObjectArtProvider getObjectArtProvider() { return null; }
    @Override public PhysicsProvider getPhysicsProvider() { return rules; }
    @Override public GameAudioProfile getAudioProfile() { return audio; }
    @Override public LevelInitProfile getLevelInitProfile() { return playerAdmission ? playerProfile : profile; }
    @Override public GameplayFrameController gameplayFrameController() { return playerAdmission ? playerPresentation : presentation; }
    @Override public PlayableCharacterRegistry getPlayableCharacterRegistry() { return characters; }
    @Override public List<RewindSnapshottable<?>> rewindAdapters() {
        return playerAdmission ? List.of(sharedPlayerAdapter) : List.of();
    }
    @Override public <T> T getGameService(Class<T> type) {
        return playerAdmission && type == PlcLifecycleService.class && playerFrame != null ? type.cast(playerFrame) : null;
    }
    @Override public boolean suppressesLevelSelect() { return true; }
    @Override public void onNewGameFromTitle(GameStateManager gameState) { throw SonicCd.unsupported("native new-game entry"); }
    @Override public TitleScreenProvider getTitleScreenProvider() { throw SonicCd.unsupported("native title entry"); }
    @Override public LevelSelectProvider getLevelSelectProvider() { throw SonicCd.unsupported("native level select"); }
    @Override public SaveSnapshotProvider getSaveSnapshotProvider() { throw SonicCd.unsupported("campaign saves/BRAM"); }
}
