package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.TexturedQuadRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.openggf.game.TestMasterTitleHub.press;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.mock;

class TestMasterTitleHubMessages {
    @TempDir Path directory;
    private final RecordingFont font = new RecordingFont();

    @Test void unavailableStandaloneActionCannotLeakIntoRomLocation() throws Exception {
        var screen = screen(true);
        screen.setSelectedIndexForTest(3);
        press(screen, GLFW_KEY_TAB); // Game Setup shortcut on a standalone game.
        for (int i = 0; i < 600; i++) screen.update(new InputHandler());
        draw(screen);
        assertTrue(font.has("ACTION UNAVAILABLE"), "error stays open until acknowledged");
        assertTrue(font.allText().contains("Game setup is not available for standalone games"));
        assertFalse(font.has("Check Files in Engine Settings"));

        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_LEFT);
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_ENTER);
        draw(screen);
        assertTrue(screen.isRomLocatorOpenForTest());
        assertTrue(font.has("LOCATE SONIC 3K"));
        assertFalse(font.has("ROM NOT FOUND"));
        assertFalse(font.allText().contains("is not available for standalone games"));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void missingRomActionsExplainTheMissingFile(int action) throws Exception {
        var screen = screen(false);
        press(screen, GLFW_KEY_DOWN); // No chips without a ROM: straight to the actions.
        for (int i = 0; i < action; i++) press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        draw(screen);
        assertTrue(font.has("ROM NOT FOUND"));
        assertTrue(font.has("missing-s2.gen"));
        assertFalse(font.has("ACTION UNAVAILABLE"));
    }

    @Test void shelfShowsTheSelectedGameNeighboursAndMissingRoms() throws Exception {
        var screen = screen(false);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        for (int width : new int[] {320, 400, 528}) {
            screen.setViewportWidth(width);
            draw(screen);
            String name = "SONIC THE HEDGEHOG 2";
            assertTrue(font.has(name));
            assertEquals((width - name.length() * 9) / 2f, font.x(name), "the name is centred under the card");
            assertTrue(font.has("ROM MISSING"), "neighbours without a ROM say so on their card");
            assertTrue(font.has("SETTINGS"));
            assertTrue(font.has("ADVANCED"));
            assertTrue(font.has("QUIT"));
            assertTrue(font.has("START"));
            assertTrue(font.has("GAME SETUP"));
            assertTrue(font.has("PLAYBACK"));
        }
    }

    @Test void setupChipsOnlyAppearForAnAvailableGame() throws Exception {
        var screen = screen(false);
        draw(screen);
        assertTrue(font.has("Choose your Sonic 2 ROM file to play"));
        assertTrue(font.has("LOCATE ROM"));
        assertFalse(font.has("SONIC & TAILS"));
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        draw(screen);
        assertTrue(font.has("SONIC & TAILS"));
        assertTrue(font.has("4:3"));
        assertTrue(font.has("Game Setup changes players, screen and extras"));
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, false);
        draw(screen);
        assertFalse(font.has("SONIC & TAILS"));
    }

    @Test void enabledModsFollowTheSelectedGameAsAChip() throws Exception {
        var screen = screen(false);
        screen.setEnabledModsByGame(Map.of(
                MasterTitleScreen.GameEntry.SONIC_1, List.of("Green Hill Remix"),
                MasterTitleScreen.GameEntry.SONIC_2, List.of("Tide Circuit", "Music Pack")));
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_1, true);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_3K, true);

        draw(screen);
        assertTrue(font.has("2 MODS"));
        assertTrue(font.has("MODS 3"), "the Mods tab counts every enabled mod once");
        screen.setSelectedIndexForTest(0);
        draw(screen);
        assertTrue(font.has("1 MOD"));
        screen.setSelectedIndexForTest(2);
        draw(screen);
        assertFalse(font.allText().contains(" MOD"), "no mods chip when no mod changes the game");
    }

    @Test void optionalExtrasRetainBrandSpaceAtNativeWidth() throws Exception {
        var screen = screen(false);
        screen.setTitleEntries(List.of("Extra mode"), (games, index) -> { });
        screen.setEnabledModsByGame(Map.of(MasterTitleScreen.GameEntry.SONIC_2, List.of("One", "Two")));
        draw(screen);
        assertTrue(screen.hubLayout().tabs().getFirst().x() >= 80);
        assertTrue(font.has("MORE"));
        assertTrue(font.has("EXTRAS"));
    }

    private MasterTitleScreen screen(boolean standalone) throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.SONIC_2_ROM, "missing-s2.gen");
        config.setConfigValue(SonicConfiguration.SONIC_3K_ROM, "missing-s3k.gen");
        var entries = new ArrayList<MasterTitleEntry>();
        for (var game : MasterTitleScreen.GameEntry.values()) entries.add(new MasterTitleEntry.Stock(game));
        if (standalone) entries.add(new MasterTitleEntry.Standalone("sample", "Sample game", false));
        var screen = new MasterTitleScreen(config, new LaunchProfileStore(config), entries, cue -> {});
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (var game : MasterTitleScreen.GameEntry.values()) screen.setRomAvailableForTest(game, false);
        var rendererField = MasterTitleScreen.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        rendererField.set(screen, mock(TexturedQuadRenderer.class));
        var fontField = MasterTitleScreen.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(screen, font);
        return screen;
    }

    private void draw(MasterTitleScreen screen) {
        font.texts.clear();
        screen.drawForRecording();
    }

    private record Text(String text, float x, float y, float red) { }
    private static final class RecordingFont extends PixelFont {
        final List<Text> texts = new ArrayList<>();
        @Override public void beginMegaBatch() { }
        @Override public void endMegaBatch() { }
        @Override public void drawText(String text, float x, float y, float scale,
                float r, float g, float b, float a) { texts.add(new Text(text, x, y, r)); }
        boolean has(String text) { return texts.stream().anyMatch(t -> t.text.equals(text)); }
        String allText() { return texts.stream().map(Text::text).collect(java.util.stream.Collectors.joining()); }
        float x(String text) { return texts.stream().filter(t -> t.text.equals(text)).findFirst().orElseThrow().x; }
    }
}
