package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.HubProgressStore;
import com.openggf.game.launch.LaunchProfileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestHubHistory {
    @TempDir Path root;

    @Test void lastFocusedGameSurvivesNewConfigurationAndMissingEntryFallsBackToSonic2() {
        var config = SonicConfigurationService.createStandalone(root);
        var screen = new MasterTitleScreen(config);
        screen.setSelectedIndexForTest(2);
        var fresh = SonicConfigurationService.createStandalone(root);
        assertEquals("s3k", new MasterTitleScreen(fresh).getSelectedGameId());
        fresh.setConfigValue(SonicConfiguration.HUB_LAST_GAME, "removed-game");
        assertEquals("s2", new MasterTitleScreen(fresh).getSelectedGameId());
        new HubProgressStore(fresh).rememberGame("custom-game");
        var entries = List.<MasterTitleEntry>of(new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2),
                new MasterTitleEntry.Standalone("custom-game", "Custom Game", false));
        assertEquals("custom-game", new MasterTitleScreen(fresh, new LaunchProfileStore(fresh), entries).getSelectedGameId());
    }

    @Test void legacyProgressDoesNotAddContinueOrChangeStart() {
        var config = SonicConfigurationService.createStandalone(root);
        config.setConfigValue(SonicConfiguration.HUB_CONTINUE_S2_ZONE, 2);
        config.setConfigValue(SonicConfiguration.HUB_CONTINUE_S2_ACT, 1);
        config.setConfigValue(SonicConfiguration.HUB_CONTINUE_S2_CHARACTER, "sonic");
        var screen = active(config);
        assertEquals(3, screen.hubLayout().actions().size());
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_ENTER);
        assertEquals(MasterTitleEntry.Action.NEW_GAME, screen.getSelectedLaunch().action());
        var other = active(config);
        press(other, GLFW_KEY_ENTER);
        assertEquals(MasterTitleEntry.Action.NEW_GAME, other.getSelectedLaunch().action());
    }

    @Test void directHelpConsumesConfirmUntilItIsClosed() {
        var screen = active(SonicConfigurationService.createStandalone(root));
        press(screen, GLFW_KEY_F1);
        press(screen, GLFW_KEY_ENTER);
        assertFalse(screen.isGameSelected());
        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected());
    }

    @Test void helpFooterIsClickable() {
        var screen = active(SonicConfigurationService.createStandalone(root));
        screen.setPointerMapperForTest((x,y) -> new int[] {x.intValue(), y.intValue(), 1});
        var help = screen.helpHintBounds();
        assertNotNull(help);
        var input = new InputHandler();
        input.handleMouseMove(help.x() + help.w() / 2, help.y() + help.h() / 2);
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        screen.update(input);
        input.update();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        screen.update(input);
        press(screen, GLFW_KEY_ENTER);
        assertFalse(screen.isGameSelected(), "help opened without starting a game");
        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected());
    }

    @Test void idlePreviewFadesBackWhenInputArrives() throws Exception {
        var screen = active(SonicConfigurationService.createStandalone(root));
        var type = Class.forName("com.openggf.game.MasterTitleScreen$RomPreviewState");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        var preview = constructor.newInstance();
        var texture = type.getDeclaredField("terrainTextureId");
        texture.setAccessible(true);
        texture.setInt(preview, 1);
        var logo = type.getDeclaredField("textureId");
        logo.setAccessible(true);
        logo.setInt(preview, 1);
        var sequence = type.getDeclaredField("sequence");
        sequence.setAccessible(true);
        sequence.set(preview, MasterTitleRomPreview.sequenceForTest(new MasterTitleRomPreview.Image[] {
                new MasterTitleRomPreview.Image(1, 1, new byte[4])}));
        var previews = MasterTitleScreen.class.getDeclaredField("romPreviews");
        previews.setAccessible(true);
        @SuppressWarnings("unchecked") var map = (java.util.Map<Object,Object>) previews.get(screen);
        map.put(MasterTitleScreen.GameEntry.SONIC_2, preview);
        for (int i = 0; i < MasterTitleScreen.IDLE_PREVIEW_DELAY; i++) screen.advanceIdlePreview(false);
        assertEquals(0, screen.idlePreviewAlphaForTest());
        for (int i = 0; i < MasterTitleScreen.IDLE_PREVIEW_FADE; i++) screen.advanceIdlePreview(false);
        assertEquals(1, screen.idlePreviewAlphaForTest(), 0.0001);
        screen.advanceIdlePreview(true);
        assertTrue(screen.idlePreviewAlphaForTest() > 0 && screen.idlePreviewAlphaForTest() < 1,
                "input fades back instead of removing the picture abruptly");
        for (int i = 0; i < MasterTitleScreen.IDLE_PREVIEW_FADE; i++) screen.advanceIdlePreview(false);
        assertEquals(0, screen.idlePreviewAlphaForTest());
        assertFalse(screen.isGameSelected());
    }

    private MasterTitleScreen active(SonicConfigurationService config) {
        var screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (var game : MasterTitleScreen.GameEntry.values()) screen.setRomAvailableForTest(game, true);
        return screen;
    }

    private void press(MasterTitleScreen screen, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        screen.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        screen.update(input);
    }
}
