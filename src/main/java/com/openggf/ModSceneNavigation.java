package com.openggf;

import com.openggf.architecture.CompositionRoot;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.mods.scene.SceneLaunch;
import com.openggf.mods.scene.host.SceneNavigator;
import com.openggf.mods.scene.host.SceneRomLibrary;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mod-scene navigation that crosses engine modes: a startup scene starting a stock act, a
 * controlled course's title request reopening the startup scene, and a scene relaunching the
 * session in another game or team through the master title. {@link GameLoop} forwards one call
 * per hook; the transitions reuse its existing title, level-start and master-title paths.
 */
@CompositionRoot
final class ModSceneNavigation {
    private static final Logger LOG = Logger.getLogger(ModSceneNavigation.class.getName());
    private final GameLoop loop;
    private BooleanSupplier startupSceneOpener;
    private SceneLaunch pendingRelaunch;

    ModSceneNavigation(GameLoop loop) {
        this.loop = loop;
    }

    /** Remembers how the engine opens the running module's startup scene. */
    void setStartupSceneOpener(BooleanSupplier opener) {
        startupSceneOpener = opener;
    }

    /** Opens the running module's startup scene in place of its title; false when it has none. */
    boolean reopenStartupScene() {
        return startupSceneOpener != null && startupSceneOpener.getAsBoolean();
    }

    /** Navigation for one scene visit; {@code library} lists the supplied games. */
    SceneNavigator forVisit(SceneRomLibrary library) {
        return new SceneNavigator() {
            @Override public Runnable prepareStockAct(int zone, int act) {
                var module = GameServices.module();
                var registry = module == null ? null : module.getZoneRegistry();
                if (registry == null || zone < 0 || zone >= registry.getZoneCount()
                        || act < 0 || act >= registry.getActCount(zone) || loop.resolveFadeManager().isActive()) {
                    return null;
                }
                return () -> loop.fadeOutTo(() -> {
                    loop.modSceneHost.close();
                    loop.startLevelFromTitleScreenImmediate(zone, act);
                });
            }

            @Override public SceneLaunch currentLaunch() {
                var module = GameServices.module();
                if (module == null) return null;
                var config = GameServices.configuration();
                return new SceneLaunch(module.getGameCode(),
                        ActiveGameplayTeamResolver.resolveMainCharacterCode(config),
                        ActiveGameplayTeamResolver.resolveSidekicks(config));
            }

            @Override public Runnable prepareRelaunch(SceneLaunch launch) {
                List<String> available = library == null ? List.of() : library.availableGames();
                if (!available.contains(launch.gameId()) || loop.resolveFadeManager().isActive()) return null;
                return () -> loop.fadeOutTo(() -> {
                    loop.returnToMasterTitle();
                    pendingRelaunch = launch;
                });
            }
        };
    }

    /**
     * Runs on every master-title frame: a pending relaunch selects its game programmatically,
     * carrying its team as session-only overrides through the master-title exit.
     */
    void onMasterTitleFrame() {
        SceneLaunch launch = pendingRelaunch;
        if (launch == null || !loop.canLaunchGameNow()) return;
        pendingRelaunch = null;
        try {
            loop.launchGameByEntry(MasterTitleScreen.GameEntry.fromGameId(launch.gameId()), null, java.util.Map.of(
                    SonicConfiguration.MAIN_CHARACTER_CODE, launch.mainCharacter(),
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE, String.join(",", launch.sidekicks())));
        } catch (RuntimeException refused) {
            LOG.log(Level.WARNING, "Mod scene relaunch into " + launch.gameId() + " was refused", refused);
        }
    }
}
