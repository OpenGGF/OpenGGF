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
                () -> gameplayMode.getLevelManager().loadZoneAndAct(zone, act));
        return gameplayMode;
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
