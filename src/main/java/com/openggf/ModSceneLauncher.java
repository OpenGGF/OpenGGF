package com.openggf;

import com.openggf.architecture.CompositionRoot;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.game.GameId;
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
import org.lwjgl.glfw.GLFW;

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
        ModSceneFactory factory = module == null ? null : module.startupScene();
        if (factory == null || config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)) {
            return false;
        }
        SceneServices services = new SceneServices(
                GameServices.audio(),
                romArt(module),
                SavePaths.root(),
                window == 0 ? null : mouseMapper(window, graphics, logicalWidth, logicalHeight),
                gameLoop::exitModSceneToGameTitle,
                gameLoop::exitModSceneToMasterTitle);
        gameLoop.enterModScene(factory, services, logicalWidth, logicalHeight);
        return true;
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
        return (wx, wy) -> {
            int[] ww = new int[1];
            int[] wh = new int[1];
            int[] fw = new int[1];
            int[] fh = new int[1];
            GLFW.glfwGetWindowSize(window, ww, wh);
            GLFW.glfwGetFramebufferSize(window, fw, fh);
            double scaleX = ww[0] > 0 ? fw[0] / (double) ww[0] : 1.0;
            double scaleY = wh[0] > 0 ? fh[0] / (double) wh[0] : 1.0;
            double fx = wx * scaleX;
            // Framebuffer y grows upwards from the bottom; window y grows downwards.
            double fy = fh[0] - wy * scaleY;
            int vx = graphics.getViewportX();
            int vy = graphics.getViewportY();
            int vw = Math.max(1, graphics.getViewportWidth());
            int vh = Math.max(1, graphics.getViewportHeight());
            int x = (int) Math.floor((fx - vx) * logicalWidth / vw);
            int y = (int) Math.floor((vy + vh - fy) * logicalHeight / vh);
            boolean inside = x >= 0 && y >= 0 && x < logicalWidth && y < logicalHeight;
            return new int[] {x, y, inside ? 1 : 0};
        };
    }

    /** Draws the open scene in screen space. */
    static void draw(GameLoop gameLoop, GraphicsManager graphics, float[] projection) {
        int[] viewport = {graphics.getViewportX(), graphics.getViewportY(), graphics.getViewportWidth(),
                graphics.getViewportHeight()};
        gameLoop.getModSceneHost().draw(graphics.isGlInitialized() ? projection : null, viewport);
    }
}
