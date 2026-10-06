package com.openggf;

import com.openggf.architecture.CompositionRoot;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kZoneArt;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.ZonePictureSource;
import com.openggf.mods.scene.ModSceneFactory;
import com.openggf.mods.scene.SceneRomArtFactory;
import com.openggf.mods.scene.SceneServices;
import java.io.IOException;
import java.util.function.Supplier;
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
        GameModule module = GameServices.module();
        ModSceneFactory factory = module == null ? null : module.getGameService(ModSceneFactory.class);
        if (factory == null || config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)) {
            return false;
        }
        SceneServices services = new SceneServices(
                GameServices.audio(),
                romArt(module),
                SavePaths.root(),
                window == 0 ? null : mouseMapper(window, graphics, logicalWidth, logicalHeight),
                () -> exitToGameTitle(gameLoop),
                gameLoop::startEscapeToMasterTitleTransition);
        gameLoop.setGameMode(GameMode.MOD_SCENE);
        gameLoop.modSceneHost.open(factory, services, logicalWidth, logicalHeight);
        gameLoop.resolveFadeManager().startFadeFromBlack(null);
        return true;
    }

    /** Fades out of the mod scene to the base game's title screen. */
    private static void exitToGameTitle(GameLoop gameLoop) {
        gameLoop.resolveFadeManager().startFadeToBlack(() -> {
            gameLoop.modSceneHost.close();
            gameLoop.initializeTitleScreenMode();
        });
    }

    private static com.openggf.mods.scene.SceneRomArt romArt(GameModule module) {
        GameId id = module.getGameId();
        if (id == GameId.STANDALONE) {
            return null;
        }
        try {
            Rom rom = GameServices.rom().getRom();
            Supplier<PlayerSpriteArtProvider> players = new Supplier<>() {
                private PlayerSpriteArtProvider cached;

                @Override
                public PlayerSpriteArtProvider get() {
                    if (cached == null) {
                        var session = SessionManager.getCurrentWorldSession();
                        Object game = session == null ? null : module.createGame(session.getDataSource());
                        cached = game instanceof PlayerSpriteArtProvider provider ? provider : null;
                    }
                    return cached;
                }
            };
            return SceneRomArtFactory.create(rom, id, players, module::loadTailsTailArt, zoneArt(id, rom));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Mod scene opened without ROM art", e);
            return null;
        }
    }

    /**
     * The stock game's zone backdrop and overview builder (Sonic 3 &amp; Knuckles only so far),
     * reading the same ROM; null when the game has none. It builds detached levels and never
     * touches the running one.
     */
    private static ZonePictureSource zoneArt(GameId id, Rom rom) {
        return id == GameId.S3K ? new Sonic3kZoneArt(rom) : null;
    }

    /** Window coordinates (GLFW screen units) to logical scene pixels, through the letterboxed viewport. */
    private static SceneServices.MouseMapper mouseMapper(long window, GraphicsManager graphics, int logicalWidth,
            int logicalHeight) {
        return (wx, wy) -> com.openggf.graphics.LogicalMouse.map(window, graphics, wx, wy, logicalWidth, logicalHeight);
    }

    /** Draws the open scene in screen space. */
    static void draw(GameLoop gameLoop, GraphicsManager graphics, float[] projection) {
        int[] viewport = {graphics.getViewportX(), graphics.getViewportY(), graphics.getViewportWidth(),
                graphics.getViewportHeight()};
        gameLoop.modSceneHost.draw(graphics.isGlInitialized() ? projection : null, viewport);
    }
}
