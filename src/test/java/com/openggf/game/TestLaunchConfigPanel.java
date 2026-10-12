package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.launch.LaunchToggle;
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
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_BACK;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
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
    void upDownMoveThroughGroupedRowsAndWrap() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));

        assertEquals(MAIN_CHARACTER, panel.selectedRowForTest(), "Game Setup opens on Players");
        pressFrame(panel, input, GLFW_KEY_W);
        assertTrue(panel.isModsRowFocusedForTest(), "Up from the first row wraps to the Mods row");
        pressFrame(panel, input, GLFW_KEY_S);
        assertEquals(MAIN_CHARACTER, panel.selectedRowForTest());
        for (int i = 0; i < 3; i++) pressFrame(panel, input, GLFW_KEY_S);
        assertEquals(REWIND, panel.selectedRowForTest(), "Character, Sidekick, Aspect ratio, then Rewind");
    }

    @Test
    void leftRightCycleSwitchesThroughDefaultOnAndOff() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        panel.focus(REWIND);

        assertEquals(LaunchToggle.DEFAULT, panel.currentProfileForTest().rewind());
        pressFrame(panel, input, GLFW_KEY_D);
        assertEquals(LaunchToggle.ON, panel.currentProfileForTest().rewind());
        pressFrame(panel, input, GLFW_KEY_D);
        assertEquals(LaunchToggle.OFF, panel.currentProfileForTest().rewind());
        pressFrame(panel, input, GLFW_KEY_D);
        assertEquals(LaunchToggle.DEFAULT, panel.currentProfileForTest().rewind());
        pressFrame(panel, input, GLFW_KEY_A);
        assertEquals(LaunchToggle.OFF, panel.currentProfileForTest().rewind());
    }

    @Test
    void backspaceResetsOnlyTheFocusedRow() {
        SonicConfigurationService config = configuredWasd();
        LaunchProfile changed = new LaunchProfile(true, "s1", true, "WIDE_16_9", "sonic", "none");
        LaunchConfigPanel panel = panel(config, changed, new TrackingStore(config));
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        panel.focus(REWIND);

        pressFrame(panel, input, GLFW_KEY_BACKSPACE);

        LaunchProfile result = panel.currentProfileForTest();
        assertEquals(LaunchToggle.DEFAULT, result.rewind(), "Backspace returns Rewind to the all-games value");
        assertEquals(LaunchToggle.ON, result.debugTools(), "other rows keep their overrides");
        assertEquals("WIDE_16_9", result.aspect());
        assertEquals("s1", result.crossGameSource());
    }

    @Test
    void constructorClampsCharacterRowsUnavailableFromCurrentDonor() {
        SonicConfigurationService config = configuredWasd();
        LaunchProfile invalid = new LaunchProfile(false, "s1", false, "global", "tails", "tails");

        LaunchConfigPanel panel = panel(config, invalid, new TrackingStore(config));

        assertEquals("sonic", panel.currentProfileForTest().mainCharacter());
        assertEquals("none", panel.currentProfileForTest().sidekick());
    }

    @Test
    void tabSavesAndEscapeLeavesAnUnchangedDraft() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel tabPanel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));

        pressFrame(tabPanel, input, GLFW_KEY_TAB);
        assertEquals(LaunchConfigPanel.Result.CLOSED, tabPanel.consumeResult());
        assertEquals(LaunchConfigPanel.Result.NONE, tabPanel.consumeResult());

        LaunchConfigPanel escapePanel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        pressFrame(escapePanel, input, GLFW_KEY_ESCAPE);
        assertEquals(LaunchConfigPanel.Result.CANCELLED, escapePanel.consumeResult());
    }

    @Test
    void escapeWithUnsavedChangesAsksSaveDiscardOrKeepEditing() {
        SonicConfigurationService config = configuredWasd();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));

        LaunchConfigPanel save = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        save.focus(REWIND);
        pressFrame(save, input, GLFW_KEY_D);
        assertEquals(1, save.unsavedCount());
        pressFrame(save, input, GLFW_KEY_ESCAPE);
        assertTrue(save.isPromptOpenForTest());
        assertEquals(LaunchConfigPanel.Result.NONE, save.consumeResult());
        pressFrame(save, input, GLFW_KEY_ENTER);
        assertEquals(LaunchConfigPanel.Result.CLOSED, save.consumeResult(), "Save is the default choice");
        assertEquals(LaunchToggle.ON, save.currentProfileForTest().rewind());

        LaunchConfigPanel discard = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        discard.focus(REWIND);
        pressFrame(discard, input, GLFW_KEY_D);
        pressFrame(discard, input, GLFW_KEY_ESCAPE);
        pressFrame(discard, input, GLFW_KEY_D);
        pressFrame(discard, input, GLFW_KEY_ENTER);
        assertEquals(LaunchConfigPanel.Result.CANCELLED, discard.consumeResult());
        assertEquals(LaunchProfile.stockFor(SONIC_3K), discard.currentProfileForTest());

        LaunchConfigPanel keep = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        keep.focus(REWIND);
        pressFrame(keep, input, GLFW_KEY_D);
        pressFrame(keep, input, GLFW_KEY_ESCAPE);
        pressFrame(keep, input, GLFW_KEY_ESCAPE);
        assertFalse(keep.isPromptOpenForTest(), "Back from the prompt keeps editing");
        assertEquals(LaunchConfigPanel.Result.NONE, keep.consumeResult());
        assertEquals(LaunchToggle.ON, keep.currentProfileForTest().rewind());
    }

    @Test
    void gamepadDpadMovesRowsAndCyclesValues() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        FakeGamepadStateSource source = new FakeGamepadStateSource();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), source);

        for (int i = 0; i < 3; i++) pressGamepadFrame(panel, input, source, GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        assertEquals(REWIND, panel.selectedRowForTest());
        pressGamepadFrame(panel, input, source, GLFW_GAMEPAD_BUTTON_DPAD_UP);
        assertEquals(WIDESCREEN, panel.selectedRowForTest());
        pressGamepadFrame(panel, input, source, GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        pressGamepadFrame(panel, input, source, GLFW_GAMEPAD_BUTTON_DPAD_RIGHT);
        assertEquals(LaunchToggle.ON, panel.currentProfileForTest().rewind());
        pressGamepadFrame(panel, input, source, GLFW_GAMEPAD_BUTTON_DPAD_LEFT);
        assertEquals(LaunchToggle.DEFAULT, panel.currentProfileForTest().rewind());
    }

    @Test
    void gamepadBackButtonClosesWithClosedResult() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        FakeGamepadStateSource source = new FakeGamepadStateSource();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), source);

        source.setDevices(GamepadStateSource.DeviceState.connected(0, "pad", buttons(GLFW_GAMEPAD_BUTTON_BACK), 0f, 0f));
        input.refreshLogicalSnapshot();
        panel.update(input);

        assertEquals(LaunchConfigPanel.Result.CLOSED, panel.consumeResult());
    }

    @Test
    void closingLeavesSavingToTheOwner() {
        SonicConfigurationService config = configuredWasd();
        TrackingStore store = new TrackingStore(config);
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), store);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));

        pressFrame(panel, input, GLFW_KEY_TAB);
        pressFrame(panel, input, GLFW_KEY_ESCAPE);

        assertEquals(0, store.saved.size(), "panel update should not save; owner consumes CLOSED and saves");
    }

    @Test
    void rowViewsShowInheritedValuesAndExperimentalAspects() {
        SonicConfigurationService config = configuredWasd();
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));

        List<LaunchConfigPanel.RowView> rows = panel.rowViews();
        assertIterableEquals(List.of("Character", "Sidekick", "Aspect ratio", "Rewind", "Borrow from", "Debug tools"),
                rows.stream().map(LaunchConfigPanel.RowView::label).toList());
        assertEquals("DEFAULT (16:9)", rows.get(2).value());
        assertEquals("DEFAULT (ON)", rows.get(3).value(), "Default shows the all-games value it follows");
        assertEquals("OFF", rows.get(4).value());
        assertEquals("DEFAULT (OFF)", rows.get(5).value());

        LaunchConfigPanel pinned = panel(config,
                new LaunchProfile(LaunchToggle.OFF, "s2", LaunchToggle.ON, "ULTRA_21_9", "sonic", "tails"),
                new TrackingStore(config));
        List<LaunchConfigPanel.RowView> pinnedRows = pinned.rowViews();
        assertEquals("21:9", pinnedRows.get(2).value());
        assertTrue(pinnedRows.get(2).experimental());
        assertEquals("OFF", pinnedRows.get(3).value());
        assertEquals("SONIC 2", pinnedRows.get(4).value());
        assertEquals("ON", pinnedRows.get(5).value());
    }

    @Test
    void s3kSaveScreenReplacesCharacterRowsWithAFixedTeamRow() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = new LaunchConfigPanel(MasterTitleScreen.GameEntry.SONIC_2,
                new LaunchProfile(false, "s3k", false, "global", "knuckles", "tails"),
                new TrackingStore(config), config, null, null);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));

        assertIterableEquals(List.of("Aspect ratio", "Rewind", "Borrow from", "Debug tools"),
                panel.rowViews().stream().map(LaunchConfigPanel.RowView::label).toList());
        assertEquals(WIDESCREEN, panel.selectedRowForTest(), "the team row is information, not a focus stop");
        pressFrame(panel, input, GLFW_KEY_W);
        assertTrue(panel.isModsRowFocusedForTest());
    }

    @Test
    void enterOnModsRowAsksTheOwnerToOpenTheModManagerAndResumes() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        panel.setEnabledMods(List.of("Widescreen+"));
        panel.focusMods();
        assertTrue(panel.isModsRowFocusedForTest());

        pressFrame(panel, input, GLFW_KEY_ENTER);
        assertEquals(LaunchConfigPanel.Result.OPEN_MODS, panel.consumeResult());
        panel.resumeAfterMods();
        pressFrame(panel, input, GLFW_KEY_S);
        assertEquals(MAIN_CHARACTER, panel.selectedRowForTest(), "the panel keeps working after the mod manager");
    }

    @Test
    void focusOpensOnTheRowAChipNamed() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        panel.focus(DEBUG_TOOLS);
        assertEquals(DEBUG_TOOLS, panel.selectedRowForTest());
        panel.focus(SIDEKICK);
        assertEquals(SIDEKICK, panel.selectedRowForTest());
    }

    @Test
    void enterOnAValueRowStepsItForward() {
        SonicConfigurationService config = configuredWasd();
        LaunchConfigPanel panel = panel(config, LaunchProfile.stockFor(SONIC_3K), new TrackingStore(config));
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        panel.focus(DEBUG_TOOLS);
        pressFrame(panel, input, GLFW_KEY_ENTER);
        assertEquals(LaunchToggle.ON, panel.currentProfileForTest().debugTools());
        assertEquals(LaunchConfigPanel.Result.NONE, panel.consumeResult(), "Enter edits; it never closes Game Setup");
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
        input.refreshLogicalSnapshot();
        panel.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        input.refreshLogicalSnapshot();
        panel.update(input);
    }

    private static void pressGamepadFrame(
            LaunchConfigPanel panel, InputHandler input, FakeGamepadStateSource source, int gamepadButton) {
        source.setDevices(GamepadStateSource.DeviceState.connected(0, "pad", buttons(gamepadButton), 0f, 0f));
        input.refreshLogicalSnapshot();
        panel.update(input);
        source.setDevices(GamepadStateSource.DeviceState.connected(0, "pad", buttons(), 0f, 0f));
        input.refreshLogicalSnapshot();
    }

    private static boolean[] buttons(int... pressedButtons) {
        boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
        for (int button : pressedButtons) {
            buttons[button] = true;
        }
        return buttons;
    }

    private static final class FakeGamepadStateSource implements GamepadStateSource {
        private final List<DeviceState> devices = new ArrayList<>();

        void setDevices(DeviceState... devices) {
            this.devices.clear();
            this.devices.addAll(List.of(devices));
        }

        @Override
        public List<DeviceState> pollDevices() {
            return List.copyOf(devices);
        }
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
