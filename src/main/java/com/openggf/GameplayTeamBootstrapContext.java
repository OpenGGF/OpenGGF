package com.openggf;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CharacterAvailability;
import com.openggf.game.GameModule;
import com.openggf.game.StockGameDataSources;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.managers.SpriteManager;

import java.util.Objects;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import java.io.IOException;
import com.openggf.game.patch.DeterministicPatchLaunches;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Composition-root context for resolving and publishing one gameplay team. */
final class GameplayTeamBootstrapContext {
    private final Supplier<CharacterAvailability> availabilitySupplier;
    private final boolean registryOnly;

    GameplayTeamBootstrapContext(Supplier<CharacterAvailability> availabilitySupplier) {
        this.availabilitySupplier = Objects.requireNonNull(availabilitySupplier, "availabilitySupplier");
        this.registryOnly = false;
    }

    private GameplayTeamBootstrapContext() {
        this.availabilitySupplier = null;
        this.registryOnly = true;
    }

    static GameplayTeamBootstrapContext registryOnly() {
        return new GameplayTeamBootstrapContext();
    }

    GameplayModeContext openAndLoad(GameModule rootModule, GameModule module,
                                    EngineContext engineServices,
                                    SonicConfigurationService configuration,
                                    int zone, int act,
                                    Consumer<GameplayModeContext> publishMode) throws java.io.IOException {
        return openAndLoad(rootModule, module, engineServices, configuration, zone, act, publishMode, null);
    }

    GameplayModeContext openAndLoad(GameModule rootModule, GameModule module,
            EngineContext engineServices, SonicConfigurationService configuration,
            int zone, int act, Consumer<GameplayModeContext> publishMode,
            com.openggf.mods.scene.ActLaunch launch) throws java.io.IOException {
        Objects.requireNonNull(publishMode, "publishMode");
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(
                rootModule, module,
                StockGameDataSources.pinned(engineServices.roms().getRom(), rootModule), null);
        GameplaySessionFactory.attachManagers(gameplayMode, engineServices);
        SpriteManager sprites = gameplayMode.getSpriteManager();
        PlayableCharacterRegistry characters = gameplayMode.getWorldSession()
                .getPlayableCharacterRegistry();
        publishBootstrapAndLoad(gameplayMode, publishMode,
                () -> {
                    CharacterAvailability availability = resolveAvailability(characters);
                    return GameplayTeamBootstrap.registerActiveTeam(
                            module, characters, availability,
                            sprites, configuration, GameplayTeamBootstrap.DEFAULT_MAIN_X,
                            GameplayTeamBootstrap.DEFAULT_MAIN_Y,
                            ModCharacterFallbackFindings.sink(ModSubsystem.current().runtimeFindings()));
                },
                team -> {
                    gameplayMode.getCamera().setFocusedSprite(team.mainSprite());
                    gameplayMode.getCamera().updatePosition(true);
                },
                () -> {
                    if (launch != null) com.openggf.level.LevelSceneActAccess.prepareSpawn(gameplayMode.getLevelManager(), launch);
                    gameplayMode.getLevelManager().loadZoneAndAct(zone, act);
                });
        return gameplayMode;
    }

    /** Recording and scene acts share this team's production publication/load owner. */
    void restartRecording(GameLoop loop, EngineContext engineServices,
            com.openggf.game.recording.RecordingLaunchContext context) {
        SonicConfigurationService configuration = engineServices.configuration();
        Objects.requireNonNull(context, "context");

        configuration.clearSessionOverrides();
        configuration.setSessionOverride(SonicConfiguration.DEFAULT_ROM, context.gameId());
        configuration.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, context.debugToolsEnabled());
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, context.mainCharacter());
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                String.join(",", context.sidekickCharacters()));
        configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        configuration.resolveDisplayAspect();

        try {
            engineServices.roms().close();
            Rom rom = engineServices.roms().getRom();
            GameModule rootModule = engineServices.romDetection()
                    .detectAndCreateModule(rom)
                    .orElseThrow(() -> new IOException(
                            "ROM not recognized for recording launch context: " + context.gameId()));

            GameModule module = DeterministicPatchLaunches.forRecording(
                    engineServices.moduleResolutionService(), rootModule, context);
            engineServices.audio().setAudioProfile(module.getAudioProfile());
            engineServices.audio().setRom(rom);
            loop.resetModuleScopedProviders();

            openAndLoad(
                    rootModule, module, engineServices, configuration, context.zone(), context.act(),
                    loop::setGameplayMode);

            loop.setGameMode(com.openggf.game.GameMode.LEVEL);
            java.util.logging.Logger.getLogger(GameLoop.class.getName()).info("Restarted recording launch context: " + context.gameId()
                    + " zone " + context.zone() + " act " + context.act()
                    + " team " + context.mainCharacter());
        } catch (IOException e) {
            throw new RuntimeException("Failed to restart from recording launch context", e);
        }
    }

    static <T> T publishBootstrapAndLoad(GameplayModeContext gameplayMode,
                                         Consumer<GameplayModeContext> publishMode,
                                         Supplier<T> bootstrapTeam,
                                         Consumer<T> prepareLevel,
                                         IoAction loadLevel) throws java.io.IOException {
        publishMode.accept(gameplayMode);
        T team = bootstrapTeam.get();
        prepareLevel.accept(team);
        loadLevel.run();
        return team;
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws java.io.IOException;
    }

    CharacterAvailability resolveAvailability(PlayableCharacterRegistry characters) {
        Objects.requireNonNull(characters, "characters");
        if (registryOnly) {
            return CharacterAvailability.fromRegistry(characters);
        }
        return Objects.requireNonNull(availabilitySupplier.get(),
                "availabilitySupplier returned null");
    }
}
