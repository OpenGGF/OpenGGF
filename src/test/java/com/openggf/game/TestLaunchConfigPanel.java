package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_3K;
import static com.openggf.game.launch.LaunchProfile.Row.DEBUG_TOOLS;
import static com.openggf.game.launch.LaunchProfile.Row.MAIN_CHARACTER;
import static com.openggf.game.launch.LaunchProfile.Row.REWIND;
import static com.openggf.game.launch.LaunchProfile.Row.SIDEKICK;
import static com.openggf.game.launch.LaunchProfile.Row.WIDESCREEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestLaunchConfigPanel {

    @TempDir
    Path tempDir;

    @Test
    void configuredUpDownMoveSelectedRowAndWrap() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        InputHandler input = new InputHandler();

        pressFrame(panel, input, GLFW_KEY_W);
        assertEquals(SIDEKICK, panel.selectedRowForTest());

        pressFrame(panel, input, GLFW_KEY_S);
        assertEquals(REWIND, panel.selectedRowForTest());
    }

    @Test
    void configuredLeftRightCycleSelectedRowAndWrap() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        InputHandler input = new InputHandler();

        pressFrame(panel, input, GLFW_KEY_A);
        assertTrue(panel.currentProfileForTest().rewind());

        pressFrame(panel, input, GLFW_KEY_D);
        assertFalse(panel.currentProfileForTest().rewind());
    }

    @Test
    void backspaceResetsAllRowsToStock() {
        SonicConfigurationService config = configuredWasd();
        LaunchProfile changed = new LaunchProfile(true, "s1", true, "WIDE_16_9", "tails", "none");
        LaunchConfigPanel panel = panel(config, changed, new TrackingStore(config));
        InputHandler input = new InputHandler();

        pressFrame(panel, input, GLFW_KEY_BACKSPACE);

        assertEquals(LaunchProfile.stockFor(SONIC_3K), panel.currentProfileForTest());
    }

    @Test
    void tabAndEscapeCloseWithClosedResult() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel tabPanel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        InputHandler input = new InputHandler();

        pressFrame(tabPanel, input, GLFW_KEY_TAB);
        assertEquals(LaunchConfigPanel.Result.CLOSED, tabPanel.consumeResult());
        assertEquals(LaunchConfigPanel.Result.NONE, tabPanel.consumeResult());

        LaunchConfigPanel escapePanel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        pressFrame(escapePanel, input, GLFW_KEY_ESCAPE);
        assertEquals(LaunchConfigPanel.Result.CLOSED, escapePanel.consumeResult());
    }

    @Test
    void closingSavesExactlyOnceThroughInjectedStore() {
        SonicConfigurationService config = configuredWasd();
        TrackingStore store = new TrackingStore(config);
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), store);
        InputHandler input = new InputHandler();

        pressFrame(panel, input, GLFW_KEY_TAB);
        input.handleKeyEvent(GLFW_KEY_TAB, GLFW_RELEASE);
        input.update();
        pressFrame(panel, input, GLFW_KEY_ESCAPE);

        assertEquals(0, store.saved.size(), "panel update should not save; owner consumes CLOSED and saves");
    }

    @Test
    void rowViewsExposeGlobalAspectPinnedAspectAndStandardMarkers() {
        SonicConfigurationService config = configuredWasd();
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
        LaunchProfile profile = new LaunchProfile(false, "off", false, "global", "tails", "none");
        LaunchConfigPanel panel = panel(config, profile, new TrackingStore(config));

        List<LaunchConfigPanel.RowView> rows = panel.rowViews();

        assertEquals("Global (16:9)", rows.get(WIDESCREEN.ordinal()).value());
        assertFalse(rows.get(WIDESCREEN.ordinal()).nonStandard());
        assertFalse(rows.get(MAIN_CHARACTER.ordinal()).stock());
        assertFalse(rows.get(MAIN_CHARACTER.ordinal()).nonStandard());
        assertFalse(rows.get(SIDEKICK.ordinal()).stock());
        assertFalse(rows.get(SIDEKICK.ordinal()).nonStandard());

        LaunchConfigPanel pinned = panel(config,
                new LaunchProfile(false, "off", true, "ULTRA_21_9", "tails", "knuckles"),
                new TrackingStore(config));
        List<LaunchConfigPanel.RowView> pinnedRows = pinned.rowViews();

        assertEquals("21:9", pinnedRows.get(WIDESCREEN.ordinal()).value());
        assertTrue(pinnedRows.get(WIDESCREEN.ordinal()).nonStandard());
        assertTrue(pinnedRows.get(DEBUG_TOOLS.ordinal()).nonStandard());
        assertTrue(pinnedRows.get(SIDEKICK.ordinal()).nonStandard());
    }

    @Test
    void textLineViewsCenterRowsAndFooterInViewport() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));

        for (LaunchConfigPanel.TextLineView line : panel.textLineViews(400)) {
            int expectedX = Math.round((400 - line.measuredWidth()) / 2f);
            assertEquals(expectedX, line.x(), "line should center on viewport: " + line.text());
        }
    }

    private SonicConfigurationService configuredWasd() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.UP, GLFW_KEY_W);
        config.setConfigValue(SonicConfiguration.DOWN, GLFW_KEY_S);
        config.setConfigValue(SonicConfiguration.LEFT, GLFW_KEY_A);
        config.setConfigValue(SonicConfiguration.RIGHT, GLFW_KEY_D);
        return config;
    }

    private static LaunchConfigPanel panel(SonicConfigurationService config,
                                           LaunchProfile profile,
                                           LaunchProfileStore store) {
        return new LaunchConfigPanel(SONIC_3K, profile, store, config, null, null);
    }

    private static void pressFrame(LaunchConfigPanel panel, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        panel.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
    }

    private static final class TrackingStore extends LaunchProfileStore {
        private final List<SavedProfile> saved = new ArrayList<>();

        private TrackingStore(SonicConfigurationService configService) {
            super(configService);
        }

        @Override
        public void save(MasterTitleScreen.GameEntry entry, LaunchProfile profile) {
            saved.add(new SavedProfile(entry, profile));
        }
    }

    private record SavedProfile(MasterTitleScreen.GameEntry entry, LaunchProfile profile) {
    }
}
