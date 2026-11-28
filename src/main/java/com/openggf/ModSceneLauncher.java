package com.openggf;

import com.openggf.architecture.CompositionRoot;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRouting;
import com.openggf.game.GameServices;
import com.openggf.game.save.SavePaths;
import com.openggf.graphics.GraphicsManager;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.mods.scene.host.SceneServices;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Engine side of mod scenes: decides whether the resolved module opens a startup scene,
 * assembles its services (audio, ROM art, storage, mouse mapping, exits), and draws it.
 * Kept out of {@link Engine} so the engine only gains one-line hooks.
 */
@CompositionRoot
final class ModSceneLauncher {
    private static final Logger LOG = Logger.getLogger(ModSceneLauncher.class.getName());

    private ModSceneLauncher() {
    }

    /**
     * Opens the module's startup scene instead of the title screen. Returns false (doing
     * nothing) when there is none or in deterministic test mode.
     */
    static boolean openStartupScene(GameLoop gameLoop, SonicConfigurationService config, long window,
            GraphicsManager graphics, int logicalWidth, int logicalHeight) {
        return openStartupScene(gameLoop, config, null, window, graphics, logicalWidth, logicalHeight, null);
    }

    static boolean openStartupScene(GameLoop gameLoop, SonicConfigurationService config, String gameOwner,
            long window, GraphicsManager graphics, int logicalWidth, int logicalHeight,
            java.util.function.Consumer<com.openggf.mods.scene.SceneLaunch> scopedRelaunch) {
        GameModule module = GameServices.module();
        // Only the engine-internal key opens a scene: ModBackedGamePatch serves it for the mod that
        // registered one, with its fault boundary. A ModSceneFactory served by any patch is ignored.
        OwnedSceneFactory factory = module == null ? null : module.getGameService(OwnedSceneFactory.class);
        if (factory == null || gameOwner != null && !gameOwner.equals(factory.ownerModId())
                || config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)) {
            return false;
        }
        gameLoop.modSceneNavigation.setStartupSceneOpener(
                () -> openStartupScene(gameLoop, config, gameOwner, window, graphics, logicalWidth, logicalHeight, scopedRelaunch));
        prepareSceneAudio(module);
        SceneRomLibrary library = romLibrary(module);
        SceneServices services = new SceneServices(
                GameServices.audio(),
                romArt(module),
                SavePaths.root(),
                window == 0 ? null : mouseMapper(window, graphics, logicalWidth, logicalHeight),
                () -> exitToGameTitle(gameLoop),
                () -> gameLoop.fadeOutTo(gameLoop::returnToMasterTitle),
                library, launch -> {
                    int zone = module.getZoneRegistry().resolveZoneKey(launch.destination()).orElseThrow(
                            () -> new IllegalArgumentException("Unregistered mod zone: " + launch.destination()));
                    if (launch.act() >= module.getZoneRegistry().getActCount(zone))
                        throw new IllegalArgumentException("Unregistered mod act: " + launch.act());
                }, null, gameLoop.modSceneNavigation.forVisit(library, scopedRelaunch));
        gameLoop.setGameMode(GameMode.MOD_SCENE);
        gameLoop.modSceneHost.open(factory, services, logicalWidth, logicalHeight);
        gameLoop.resolveFadeManager().startFadeFromBlack(null);
        return true;
    }

    /** Startup scenes bypass title/level initialization, so attach their base ROM audio here. */
    static void prepareSceneAudio(GameModule module) {
        if (GameModuleRouting.isStandalone(module)) return;
        GameServices.audio().setAudioProfile(module.getAudioProfile());
        var source = com.openggf.game.session.SessionManager.getCurrentWorldSession().getDataSource();
        if (source.rom().isPresent()) GameServices.audio().setRom(source.rom().orElseThrow());
    }

    /**
     * Opens a mod's master-title entry scene. No game is loaded, so the scene has no ROM art;
     * {@code gameplay} lets it launch stock runs, after which it is resumed. Both exits return
     * to the master title.
     */
    static void openTitleEntryScene(GameLoop gameLoop, long window, GraphicsManager graphics,
            int logicalWidth, int logicalHeight, com.openggf.mods.code.OwnedTitleEntry entry,
            com.openggf.mods.scene.SceneGameplay gameplay) {
        Runnable toMasterTitle = () -> gameLoop.fadeOutTo(gameLoop::returnToMasterTitle);
        SceneServices services = new SceneServices(
                GameServices.audio(),
                null,
                SavePaths.root(),
                window == 0 ? null : mouseMapper(window, graphics, logicalWidth, logicalHeight),
                toMasterTitle,
                toMasterTitle,
                null,
                gameplay);
        gameLoop.setGameMode(GameMode.MOD_SCENE);
        gameLoop.modSceneHost.open(entry.scene(), services, logicalWidth, logicalHeight);
        gameLoop.resolveFadeManager().startFadeFromBlack(null);
    }

    /** Fades out of the mod scene to the base game's title screen. */
    private static void exitToGameTitle(GameLoop gameLoop) {
        gameLoop.resolveFadeManager().startFadeToBlack(() -> {
            gameLoop.retireModScene();
            gameLoop.initializeTitleScreenMode();
        });
    }

    private static com.openggf.mods.scene.SceneRomArt romArt(GameModule module) {
        if (GameModuleRouting.isStandalone(module)) {
            return null;
        }
        try {
            var source = com.openggf.game.session.SessionManager.getCurrentWorldSession().getDataSource();
            var media = module.getGameService(com.openggf.game.internal.MediaSceneArt.class);
            return media == null ? SceneRomArtFactory.forModule(module, GameServices.rom().getRom())
                    : SceneRomArtFactory.forMedia(media.defaultArt(source));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare stock media art for mod scene", e);
        }
    }

    private static SceneRomLibrary romLibrary(GameModule module) {
        if (GameModuleRouting.isStandalone(module)) return null;
        return new SceneRomLibrary(module,
                com.openggf.game.session.SessionManager.getCurrentWorldSession().getDataSource(), GameServices.rom());
    }

    /** Window coordinates (GLFW screen units) to logical scene pixels, through the letterboxed viewport. */
    private static SceneServices.MouseMapper mouseMapper(long window, GraphicsManager graphics, int logicalWidth,
            int logicalHeight) {
        return (wx, wy) -> com.openggf.graphics.LogicalMouse.map(window, graphics, wx, wy, logicalWidth, logicalHeight);
    }

    /** Draws the open scene over a run it plays behind, at the run's screen size. */
    static void drawOver(GameLoop gameLoop, GraphicsManager graphics, int width, int height) {
        int[] viewport = {graphics.getViewportX(), graphics.getViewportY(), graphics.getViewportWidth(),
                graphics.getViewportHeight()};
        gameLoop.modSceneHost.drawOver(width, height,
                graphics.isGlInitialized() ? graphics.getProjectionMatrixBuffer() : null, viewport);
    }

    /** Draws the open scene in screen space. */
    static void draw(GameLoop gameLoop, GraphicsManager graphics, float[] projection) {
        int[] viewport = {graphics.getViewportX(), graphics.getViewportY(), graphics.getViewportWidth(),
                graphics.getViewportHeight()};
        gameLoop.modSceneHost.draw(graphics.isGlInitialized() ? projection : null, viewport);
    }
}
