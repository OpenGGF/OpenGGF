package com.openggf.game.sonic3k.dataselect;

import com.openggf.Engine;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.dataselect.DataSelectSessionController;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.*;

/**
 * Renders each hand-placed roster in the NO SAVE window and an occupied save slot, and checks
 * the composed portrait against the windows' own art: inside the slot's 56 px between its
 * rails, inside the NO SAVE frame and below its up arrow, and standing on the native feet
 * line. Sprite pixels are those that differ from the same screen with an empty portrait.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCustomTeamPortraitFit {
    private static final int W = 320;
    private static final int H = 224;
    // Screen anchors: S3kSaveScreenLayoutObjects NO SAVE (0xB0,0xC8) and slot 1 (0x110,0x108), less 128.
    private static final int NO_SAVE_X = 0xB0 - 128;
    private static final int NO_SAVE_Y = 0xC8 - 128;
    private static final int SLOT_X = 0x110 - 128;
    private static final int SLOT_Y = 0x108 - 128;
    // Measured window art: slot rails end at x=115 and start at x=172; the NO SAVE frame's red
    // border is x=11..12 / 83..84; its up arrow ends at y=27 and its down arrow starts at y=74.
    private static final int SLOT_INNER_LEFT = 116;
    private static final int SLOT_INNER_RIGHT = 171;
    private static final int NO_SAVE_INNER_LEFT = 13;
    private static final int NO_SAVE_INNER_RIGHT = 82;
    private static final int UP_ARROW_BOTTOM = 27;

    @TempDir
    Path saves;

    @ParameterizedTest
    @ValueSource(strings = {
            "sonic,tails,knuckles",
            "sonic,tails,tails",
            "sonic,tails,tails,tails,tails,tails,tails,tails,tails",
            "sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic,sonic"})
    void authoredRosterFitsBothWindows(String roster) throws Exception {
        SonicConfigurationService config = null;
        String previousCombos = null;
        try (var ctx = PresentationOffscreenTestContext.openConfigured(W, H,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            GraphicsManager.destroyForReinit();
            EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
            GraphicsManager graphics = GraphicsManager.getInstance();
            config = EngineServices.current().configuration();
            previousCombos = config.getString(SonicConfiguration.DATA_SELECT_EXTRA_PLAYER_COMBOS);
            try (Rom rom = new Rom()) {
                graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
                glViewport(0, 0, W, H);
                float[] projection = new float[16];
                new Matrix4f().ortho2D(0, W, 0, H).get(projection);
                graphics.setProjectionMatrixBuffer(projection);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                graphics.setViewport(0, 0, W, H);
                assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
                GameModuleRegistry.detectAndSetModule(rom);
                RomManager.getInstance().setRom(rom);
                TestEnvironment.activeGameplayMode();
                GameServices.camera().resetState();
                config.setConfigValue(SonicConfiguration.DATA_SELECT_EXTRA_PLAYER_COMBOS, roster);
                SelectedTeam team = new S3kDataSelectProfile().parseExtraTeams(roster).getFirst();
                SaveManager saveManager = new SaveManager(saves);
                saveManager.writeSlot("s3k", 1, Map.of("zone", 0, "act", 0,
                        "mainCharacter", team.mainCharacter(), "sidekicks", team.sidekicks(),
                        "lives", 3, "continues", 0, "chaosEmeralds", List.of(), "clear", false));
                S3kDataSelectAssetSource assets = S3kDataSelectPresentation.createDefaultAssets();

                RgbaImage portrait = render(graphics, config, saveManager, assets, team, false);
                RgbaImage background = render(graphics, config, saveManager, assets, team, true);

                Bounds noSave = spriteBounds(portrait, background, 0, 0, 100, 100);
                Bounds slot = spriteBounds(portrait, background, 100, 80, 200, 150);
                assertTrue(noSave.exists() && slot.exists(), roster + " portrait did not render");

                assertTrue(slot.minX() >= SLOT_INNER_LEFT && slot.maxX() <= SLOT_INNER_RIGHT,
                        roster + " crosses the save-slot rails: " + slot);
                assertTrue(noSave.minX() >= NO_SAVE_INNER_LEFT && noSave.maxX() <= NO_SAVE_INNER_RIGHT,
                        roster + " crosses the NO SAVE frame: " + noSave);
                assertTrue(noSave.minY() > UP_ARROW_BOTTOM, roster + " reaches the up arrow: " + noSave);
                assertEquals(NO_SAVE_Y - 1, noSave.maxY(), roster + " leaves the native feet line: " + noSave);
                assertEquals(SLOT_Y - 1, slot.maxY(), roster + " leaves the native feet line: " + slot);
            } finally {
                GraphicsManager.destroyForReinit();
            }
        } finally {
            if (config != null) {
                config.setConfigValue(SonicConfiguration.DATA_SELECT_EXTRA_PLAYER_COMBOS, previousCombos);
            }
            SessionManager.clear();
        }
    }

    private static RgbaImage render(GraphicsManager graphics, SonicConfigurationService config,
                                    SaveManager saveManager, S3kDataSelectAssetSource assets,
                                    SelectedTeam team, boolean emptyPortrait) throws Exception {
        DataSelectSessionController controller = new DataSelectSessionController(new S3kDataSelectProfile());
        S3kDataSelectPresentation presentation = new S3kDataSelectPresentation(controller, saveManager, config,
                assets, new S3kDataSelectRenderer(), ignored -> { });
        presentation.initialize();
        presentation.update(new InputHandler());
        // The four built-in teams precede the configured extra.
        for (int i = 0; i < 4; i++) {
            controller.cycleTeam(1);
        }
        assertEquals(team, controller.noSaveTeam());
        if (emptyPortrait) {
            replacePortrait(presentation, team, new SpriteMappingFrame(List.of()));
        }
        for (int i = 0; i < 90; i++) {
            presentation.update(new InputHandler());
        }
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0);
        presentation.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        presentation.draw();
        graphics.flushScreenSpace();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(W, H);
    }

    @SuppressWarnings("unchecked")
    private static void replacePortrait(S3kDataSelectPresentation presentation, SelectedTeam team,
                                        SpriteMappingFrame frame) throws Exception {
        for (String name : List.of("cachedPortraitFrames", "cachedNoSavePortraitFrames")) {
            Field field = S3kDataSelectPresentation.class.getDeclaredField(name);
            field.setAccessible(true);
            ((Map<SelectedTeam, SpriteMappingFrame>) field.get(presentation)).put(team, frame);
        }
    }

    private static Bounds spriteBounds(RgbaImage image, RgbaImage background, int x0, int y0, int x1, int y1) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (image.argb(x, y) != background.argb(x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private record Bounds(int minX, int minY, int maxX, int maxY) {
        boolean exists() {
            return minX != Integer.MAX_VALUE;
        }
    }
}
