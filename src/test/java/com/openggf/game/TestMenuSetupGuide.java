package com.openggf.game;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.GamepadStateSource;
import com.openggf.InputBindingFactory;
import com.openggf.control.InputHandler;
import com.openggf.game.internal.HostedGameMedia.MediaKind;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.SyntheticProgramDisc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

/**
 * The setup guide driven through the real menu input path, keyboard and controller, with settings checked in
 * the configuration file the guide wrote. The library is fixed per test, so the worktree's ROMs never leak in.
 */
class TestMenuSetupGuide {
    @TempDir Path dir;

    private final Map<String, Path> available = new HashMap<>();
    private boolean online = true;
    private int libraryChanges;

    private SetupGuideLibrary.Current library(SonicConfigurationService config) {
        return new SetupGuideLibrary.Current() {
            @Override public boolean available(SetupGuideLibrary.Game g) { return available.containsKey(g.id()); }
            @Override public Optional<Path> file(SetupGuideLibrary.Game g) { return Optional.ofNullable(available.get(g.id())); }
        };
    }

    private MenuSetupGuide guide(SonicConfigurationService config, SetupGuideSession session, boolean reentry) {
        return new MenuSetupGuide(config, session, new MenuSetupGuide.Host() {
            @Override public boolean onlineAvailable() { return online; }
            @Override public void libraryChanged() { libraryChanges++; }
        }, reentry, this::library);
    }

    private SonicConfigurationService config() { return SonicConfigurationService.createStandalone(dir); }
    private SonicConfigurationService saved() { return SonicConfigurationService.createStandalone(dir); }

    private static void key(MenuSetupGuide guide, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        guide.update(input);
        input.update();
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.refreshLogicalSnapshot();
        guide.update(input);
        input.update();
    }

    private InputHandler keyboard(SonicConfigurationService config) {
        return new InputHandler(InputBindingFactory.supplier(config), List::of);
    }

    @Test void aFreshPlayerWithAGameReachesPlayInThreePressesWithBalancedSaved() {
        available.put("s2", dir.resolve("s2.gen"));
        var config = config();
        var guide = guide(config, new SetupGuideSession(false), false);
        var input = keyboard(config);
        assertEquals(MenuSetupGuide.Step.GAMES, guide.step());
        assertEquals(2, guide.focus(), "Continue is focused when a game is ready");
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(MenuSetupGuide.Step.STYLE, guide.step());
        assertEquals(List.of(SetupGuidePreset.STOCK, SetupGuidePreset.BALANCED, SetupGuidePreset.ENHANCED,
                SetupGuidePreset.MULTIPLAYER), guide.styles(), "a fresh install has no Keep row");
        assertEquals(SetupGuidePreset.BALANCED, guide.styles().get(guide.focus()), "Balanced is the default");
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(MenuSetupGuide.Step.READY, guide.step(), "a fresh install applies without a diff prompt");
        assertNull(guide.outcome(), "nothing is final before Play");
        assertEquals("", saved().getString(SonicConfiguration.SETUP_GUIDE), "the style is held until the guide finishes");
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(new MenuSetupGuide.Outcome(MenuSetupGuide.Exit.PLAY, "s2"), guide.outcome());
        var file = saved();
        assertEquals("WIDE_16_9", file.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertEquals("NATIVE", file.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE));
        assertEquals(SetupGuidePreset.DONE, file.getString(SonicConfiguration.SETUP_GUIDE));
    }

    @Test void skippingAsksFirstAndKeepsEverythingButTheGuideState() {
        var config = config();
        var guide = guide(config, new SetupGuideSession(false), false);
        var input = keyboard(config);
        assertEquals(0, guide.focus(), "an empty library focuses Scan a folder");
        key(guide, input, GLFW_KEY_ESCAPE);
        assertTrue(guide.promptOpen());
        key(guide, input, GLFW_KEY_ESCAPE);
        assertFalse(guide.promptOpen(), "Back on the prompt keeps setting up");
        assertNull(guide.outcome());
        assertEquals("", saved().getString(SonicConfiguration.SETUP_GUIDE));
        key(guide, input, GLFW_KEY_ESCAPE);
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(MenuSetupGuide.Exit.SKIPPED, guide.outcome().exit());
        var file = saved();
        assertEquals(SetupGuidePreset.SKIPPED, file.getString(SonicConfiguration.SETUP_GUIDE));
        assertEquals("NATIVE_4_3", file.getString(SonicConfiguration.DISPLAY_ASPECT));
    }

    @Test void anUpgradeKeepsSettingsByDefaultAndAppliesAPresetOnlyAfterTheDiffIsAccepted() throws Exception {
        available.put("s1", dir.resolve("s1.gen"));
        var config = config();
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.DISPLAY_INTERPOLATION, true));
        var guide = guide(config, new SetupGuideSession(true), false);
        var input = keyboard(config);
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(SetupGuidePreset.KEEP, guide.styles().getFirst());
        assertEquals(SetupGuidePreset.KEEP, guide.styles().get(guide.focus()), "Keep my settings is the default");
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_ENTER);
        assertTrue(guide.promptOpen(), "Balanced over an existing install shows its changes first");
        key(guide, input, GLFW_KEY_ENTER);
        assertFalse(guide.promptOpen());
        assertEquals(MenuSetupGuide.Step.STYLE, guide.step(), "Keep editing, the default, changes nothing");
        assertEquals("NATIVE_4_3", saved().getString(SonicConfiguration.DISPLAY_ASPECT));
        key(guide, input, GLFW_KEY_ENTER);
        key(guide, input, GLFW_KEY_RIGHT);
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(MenuSetupGuide.Step.READY, guide.step());
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(MenuSetupGuide.Exit.HUB, guide.outcome().exit());
        var file = saved();
        assertEquals("WIDE_16_9", file.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertEquals("NATIVE", file.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE));
        assertTrue(file.getBoolean(SonicConfiguration.DISPLAY_INTERPOLATION), "Balanced keeps values it does not list");
    }

    @Test void keepMySettingsFinishesWithoutWritingSettings() throws Exception {
        available.put("s3k", dir.resolve("s3k.gen"));
        var config = config();
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.DISPLAY_ASPECT, "ULTRA_21_9"));
        var guide = guide(config, new SetupGuideSession(true), false);
        var input = keyboard(config);
        key(guide, input, GLFW_KEY_ENTER);
        key(guide, input, GLFW_KEY_ENTER);
        assertFalse(guide.promptOpen());
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(new MenuSetupGuide.Outcome(MenuSetupGuide.Exit.PLAY, "s3k"), guide.outcome());
        assertEquals("ULTRA_21_9", saved().getString(SonicConfiguration.DISPLAY_ASPECT));
        assertEquals(SetupGuidePreset.DONE, saved().getString(SonicConfiguration.SETUP_GUIDE));
    }

    @Test void multiplayerIsAShortcutThatWritesNoSettings() throws Exception {
        available.put("s2", dir.resolve("s2.gen"));
        var config = config();
        var guide = guide(config, new SetupGuideSession(false), false);
        var input = keyboard(config);
        key(guide, input, GLFW_KEY_ENTER);
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(MenuSetupGuide.Step.TOGETHER, guide.step());
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(MenuSetupGuide.Exit.ONLINE, guide.outcome().exit());
        var file = saved();
        assertEquals("NATIVE_4_3", file.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertEquals("EDGE", file.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE));
        assertEquals(SetupGuidePreset.DONE, file.getString(SonicConfiguration.SETUP_GUIDE));
    }

    @Test void raceOnlineAndTwoPlayersRefuseWhenUnavailable() {
        online = false;
        available.put("s1", dir.resolve("s1.gen"));
        var config = config();
        var guide = guide(config, new SetupGuideSession(false), false);
        var input = keyboard(config);
        key(guide, input, GLFW_KEY_ENTER);
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_ENTER);
        key(guide, input, GLFW_KEY_ENTER);
        assertNull(guide.outcome(), "Time Attack turned off in Mods");
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_ENTER);
        assertNull(guide.outcome(), "two players need Sonic 2");
        assertFalse(guide.twoPlayerAvailable());
    }

    @Test void aControllerDrivesTheSameSteps() {
        available.put("s2", dir.resolve("s2.gen"));
        var config = config();
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        var pad = new Pad();
        var input = new InputHandler(InputBindingFactory.supplier(config), pad);
        var guide = guide(config, new SetupGuideSession(false), false);
        pad.press(guide, input, GLFW_GAMEPAD_BUTTON_A);
        assertEquals(MenuSetupGuide.Step.STYLE, guide.step());
        pad.press(guide, input, GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        assertEquals(SetupGuidePreset.ENHANCED, guide.styles().get(guide.focus()));
        pad.press(guide, input, GLFW_GAMEPAD_BUTTON_A);
        pad.press(guide, input, GLFW_GAMEPAD_BUTTON_B);
        assertEquals(MenuSetupGuide.Step.STYLE, guide.step(), "B goes back a step");
        pad.press(guide, input, GLFW_GAMEPAD_BUTTON_A);
        pad.press(guide, input, GLFW_GAMEPAD_BUTTON_A);
        assertEquals(MenuSetupGuide.Exit.PLAY, guide.outcome().exit());
        var file = saved();
        assertTrue(file.getBoolean(SonicConfiguration.DISPLAY_INTERPOLATION));
        assertTrue(file.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED));
        assertEquals("NATIVE", file.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE));
    }

    @Test void reentryFromExtrasLeavesWithoutPromptingAndResumesWhereItWasLeft() throws Exception {
        available.put("s2", dir.resolve("s2.gen"));
        var config = config();
        SetupGuidePreset.skip(config);
        var session = new SetupGuideSession(false);
        var guide = guide(config, session, true);
        var input = keyboard(config);
        key(guide, input, GLFW_KEY_ENTER);
        assertEquals(SetupGuidePreset.KEEP, guide.styles().getFirst(), "re-entry offers Keep my settings");
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_DOWN);
        key(guide, input, GLFW_KEY_ESCAPE);
        key(guide, input, GLFW_KEY_ESCAPE);
        assertEquals(MenuSetupGuide.Exit.CLOSED, guide.outcome().exit(), "a settled guide closes without the leave prompt");
        assertEquals(SetupGuidePreset.SKIPPED, saved().getString(SonicConfiguration.SETUP_GUIDE));
        var again = guide(config, session, true);
        assertEquals(MenuSetupGuide.Step.GAMES, again.step());
        assertEquals(2, again.focus(), "progress resumes in the same run");
    }

    @Test void aScannedSonicCdIsFoundNotReadyAndADifferentDiscForAWorkingGameAsksFirst() throws Exception {
        var config = config();
        var guide = guide(config, new SetupGuideSession(false), false);
        Path usa = SyntheticProgramDisc.write(dir.resolve("games/cd"), CdReleaseVariant.USA);
        guide.acceptFindings(SetupGuideLibrary.scan(dir.resolve("games"), SetupGuideLibrary.games()));
        assertEquals(usa.toAbsolutePath().normalize().toString(), saved().getString(SonicConfiguration.SONIC_CD_DISC));
        assertEquals(1, libraryChanges);
        available.put("scd", usa);
        var shown = guide(config, new SetupGuideSession(false), false);
        var cd = SetupGuideLibrary.games().getLast();
        assertEquals(SetupGuideLibrary.Status.FOUND, shown.status(cd), "identified, verified when it starts");
        Path other = SyntheticProgramDisc.write(dir.resolve("other"), CdReleaseVariant.USA);
        shown.acceptFindings(SetupGuideLibrary.locate(other, SetupGuideLibrary.games()));
        assertTrue(shown.promptOpen(), "a different disc for a working game is a conflict");
        var input = keyboard(config);
        key(shown, input, GLFW_KEY_ENTER);
        assertEquals(usa.toAbsolutePath().normalize().toString(), saved().getString(SonicConfiguration.SONIC_CD_DISC),
                "Keep current file is the default");
    }

    @Test void problemDiscsShowTheirKindAndDoNotBlockTheOtherGames() throws Exception {
        available.put("s2", dir.resolve("s2.gen"));
        var config = config();
        var guide = guide(config, new SetupGuideSession(false), false);
        Path cue = SyntheticProgramDisc.write(dir.resolve("broken"), CdReleaseVariant.USA);
        java.nio.file.Files.delete(cue.resolveSibling("audio12.bin"));
        guide.acceptFindings(SetupGuideLibrary.locate(cue, SetupGuideLibrary.games()));
        var cd = SetupGuideLibrary.games().getLast();
        assertEquals(SetupGuideLibrary.Status.UNREADABLE, guide.status(cd));
        assertEquals("", saved().getString(SonicConfiguration.SONIC_CD_DISC));
        assertEquals(2, guide.focus(), "Continue stays available without Sonic CD");
        var europe = SyntheticProgramDisc.write(dir.resolve("eu"), CdReleaseVariant.EUROPE);
        guide.acceptFindings(SetupGuideLibrary.locate(europe, SetupGuideLibrary.games()));
        assertEquals(SetupGuideLibrary.Status.WRONG_RELEASE, guide.status(cd));
        assertEquals(MediaKind.OTHER_RELEASE, SetupGuideLibrary.locate(europe, SetupGuideLibrary.games()).findings().getFirst().kind());
    }

    @Test void everyStepRendersWithoutTruncationWarningsAt320And400() {
        available.put("s2", dir.resolve("s2.gen"));
        var config = config();
        for (int width : new int[] {320, 400}) {
            var guide = guide(config, new SetupGuideSession(true), false);
            var input = keyboard(config);
            var font = new TestHubDialogGeometry.Recorder().font;
            for (int i = 0; i < 4; i++) {
                guide.render(font, width);
                key(guide, input, GLFW_KEY_ENTER);
            }
        }
    }

    private static final class Pad implements GamepadStateSource {
        private final List<DeviceState> devices = new ArrayList<>();
        void set(int... pressed) {
            boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            for (int b : pressed) buttons[b] = true;
            devices.clear();
            devices.add(DeviceState.connected(0, "pad", buttons, 0f, 0f));
        }
        void press(MenuSetupGuide guide, InputHandler input, int button) {
            set(button); input.refreshLogicalSnapshot(); guide.update(input); input.update();
            set(); input.refreshLogicalSnapshot(); guide.update(input); input.update();
        }
        @Override public List<DeviceState> pollDevices() { return List.copyOf(devices); }
    }
}
