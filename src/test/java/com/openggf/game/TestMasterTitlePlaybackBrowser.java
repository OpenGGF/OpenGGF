package com.openggf.game;

import com.openggf.GameLoop;
import com.openggf.TraceSessionLauncher;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.playback.PlaybackBrowser;
import com.openggf.game.playback.PlaybackEntry;
import com.openggf.game.playback.PlaybackLibrary;
import com.openggf.game.recording.RecordingLaunchContext;
import com.openggf.game.recording.RecordingVersionWarning;
import com.openggf.game.recording.UserRecordingEntry;
import com.openggf.game.recording.UserRecordingManifest;
import com.openggf.game.recording.UserRecordingPlaybackOptions;
import com.openggf.game.recording.RecordingDeterminismMetadata;
import com.openggf.game.recording.UserRecordingSidecarMetadata;
import com.openggf.game.recording.UserRecordingStopReason;
import com.openggf.game.save.SelectedTeam;
import com.openggf.graphics.PixelFont;
import com.openggf.testmode.TraceLaunchStatus;
import com.openggf.testmode.TraceRunFailureStatus;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.*;

class TestMasterTitlePlaybackBrowser {
    @TempDir Path directory;

    @BeforeEach
    @AfterEach
    void reset() {
        TestEnvironment.resetAll();
        TraceLaunchStatus.clear();
        TraceRunFailureStatus.clear();
    }

    @Test
    void gameActionAndRecordShortcutOpenTheSameMixedLibrary() throws Exception {
        for (boolean shortcut : List.of(false, true)) {
            var config = SonicConfigurationService.createStandalone(directory);
            var screen = screen(config, library());
            if (shortcut) {
                var input = new InputHandler();
                input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
                input.handleKeyEvent(config.getInt(SonicConfiguration.RECORDING_RECORD_KEY), GLFW_PRESS);
                screen.update(input);
                input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_RELEASE);
                input.handleKeyEvent(config.getInt(SonicConfiguration.RECORDING_RECORD_KEY), GLFW_RELEASE);
                input.update();
                screen.update(input);
            } else {
                press(screen, GLFW_KEY_DOWN);
                press(screen, GLFW_KEY_RIGHT);
                press(screen, GLFW_KEY_RIGHT);
                press(screen, GLFW_KEY_ENTER);
            }
            finishLoad(screen);
            assertNotNull(screen.playbackBrowserForTest());
            assertEquals("s1", screen.playbackBrowserForTest().selectedEntry().gameId());
            press(screen, GLFW_KEY_ESCAPE);
            assertNull(screen.playbackBrowserForTest());
            screen.cleanup();
        }
    }

    @Test
    void advancedEntryOpensAllGamesInTheCommonBrowser() throws Exception {
        var screen = screen(SonicConfigurationService.createStandalone(directory), library());
        press(screen, GLFW_KEY_UP);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_ENTER);
        finishLoad(screen);
        assertNotNull(screen.playbackBrowserForTest());
        screen.cleanup();
    }

    @Test
    void automaticTraceModeCannotOpenNativeOptionsAndBackReleasesTheMode() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        var screen = screen(config, library());
        assertFalse(screen.tryOpenUserRecordingMenuForSelectedGame());
        screen.update(new InputHandler());
        finishLoad(screen);
        assertInstanceOf(PlaybackEntry.Trace.class, screen.playbackBrowserForTest().selectedEntry());
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_LEFT);
        assertInstanceOf(PlaybackEntry.Trace.class, screen.playbackBrowserForTest().selectedEntry());
        press(screen, GLFW_KEY_ESCAPE);
        assertFalse(config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
        screen.update(new InputHandler());
        assertNull(screen.playbackBrowserForTest());
        assertNull(field(screen, "catalogLoad"));
        screen.cleanup();
    }

    @Test
    void sourceIssuesReleaseAutomaticModeWithoutHidingHealthyTraceEntries() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        var screen = screen(config, new PlaybackLibrary(library().entries(), List.of("Cannot read one source")));
        screen.update(new InputHandler());
        finishLoad(screen);
        assertFalse(config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
        assertInstanceOf(PlaybackEntry.Trace.class, screen.playbackBrowserForTest().selectedEntry());
        screen.cleanup();
    }

    @Test
    void retryFromPartialCatalogReappliesTracePolicyOnlyAtLaunch() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        var trace = trace("trace");
        var screen = screen(config, new PlaybackLibrary(List.of(trace), List.of("A source could not be read")));
        screen.update(new InputHandler());
        finishLoad(screen);
        assertFalse(config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
        try (var launcher = mockStatic(TraceSessionLauncher.class)) {
            launcher.when(() -> TraceSessionLauncher.launch(trace.trace())).thenAnswer(invocation -> {
                assertTrue(config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
                return true;
            });
            press(screen, GLFW_KEY_ENTER);
            assertFalse(config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
            screen.playbackBrowserForTest().render();
            screen.update(new InputHandler());
            assertTrue(config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
            assertNull(screen.playbackBrowserForTest());
        }
        screen.cleanup();
    }

    @Test
    void recreatedTitleRestoresSelectionFromItsSharedNavigationMemory() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        var memory = new PlaybackBrowser.SelectionMemory();
        var first = screen(config, new PlaybackLibrary(List.of(trace("first"), trace("last")), List.of()));
        first.setPlaybackSelectionMemory(memory);
        assertTrue(first.tryOpenUserRecordingMenuForSelectedGame());
        finishLoad(first);
        first.playbackBrowserForTest().selectEntry(trace("last"));
        first.update(new InputHandler());
        first.cleanup();

        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        var replacement = screen(config, new PlaybackLibrary(List.of(trace("first"), trace("last")), List.of()));
        replacement.setPlaybackSelectionMemory(memory);
        replacement.update(new InputHandler());
        finishLoad(replacement);
        assertEquals(directory.resolve("last"), replacement.playbackBrowserForTest().selectedEntry().path());
        replacement.cleanup();
    }

    @Test
    void gameLoopInstalledStarterStillReceivesSelectedRecordingAndOptions() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        var recording = recording();
        var screen = screen(config, new PlaybackLibrary(List.of(recording), List.of()));
        var input = new InputHandler();
        var loop = new GameLoop(input);
        AtomicReference<UserRecordingEntry> started = new AtomicReference<>();
        AtomicReference<UserRecordingPlaybackOptions> options = new AtomicReference<>();
        loop.setUserRecordingPlaybackStarter((entry, value) -> {
            started.set(entry);
            options.set(value);
        });
        loop.setMasterTitleScreenSupplier(() -> screen);
        loop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
        loop.step();
        assertTrue(screen.tryOpenUserRecordingMenuForSelectedGame());
        finishLoad(screen);
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_ENTER);
        assertSame(recording.recording(), started.get());
        assertEquals(3, options.get().targetFrame());
        assertNull(screen.playbackBrowserForTest());
        screen.cleanup();
    }

    @Test
    void successfulTraceLaunchKeepsItsSessionModeAndPresentsLoadingFirst() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        var trace = trace("trace");
        var screen = screen(config, new PlaybackLibrary(List.of(trace), List.of()));
        screen.update(new InputHandler());
        finishLoad(screen);
        try (var launcher = mockStatic(TraceSessionLauncher.class)) {
            launcher.when(() -> TraceSessionLauncher.launch(trace.trace())).thenReturn(true);
            press(screen, GLFW_KEY_ENTER);
            launcher.verify(() -> TraceSessionLauncher.launch(trace.trace()), never());
            screen.playbackBrowserForTest().render();
            screen.update(new InputHandler());
            launcher.verify(() -> TraceSessionLauncher.launch(trace.trace()));
            assertTrue(config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
            assertNull(screen.playbackBrowserForTest());
        }
        screen.cleanup();
    }

    @Test
    void playbackMouseUsesTheCentredPageAndForwardsEachWheelNotchOnce() throws Exception {
        for (int width : new int[] {320, 400}) {
            var screen = screen(SonicConfigurationService.createStandalone(directory), library());
            screen.setViewportWidth(width);
            screen.setPointerMapperForTest((x, y) -> new int[] {x.intValue(), y.intValue(), 1});
            assertTrue(screen.tryOpenUserRecordingMenuForSelectedGame());
            finishLoad(screen);
            var input = new InputHandler();
            int left = (width - 320) / 2;
            input.handleMouseMove(left + 20, 85); // second native list row
            screen.update(input); input.update();
            assertEquals(directory.resolve("trace"), screen.playbackBrowserForTest().selectedEntry().path());
            com.openggf.control.MouseWheel.of(input).scroll(1);
            screen.update(input); input.update();
            assertEquals(PlaybackEntry.Kind.RECORDING, screen.playbackBrowserForTest().selectedEntry().kind(),
                    "the title must forward the notch it already consumed");
            assertEquals(0, com.openggf.control.MouseWheel.of(input).takeNotches(), "a notch has one owner");
            if (left > 0) {
                input.handleMouseMove(10, 100); input.handleMouseButton(GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
                screen.update(input); input.update();
                assertNotNull(screen.playbackBrowserForTest(), "the wide-page gutter has no Back target");
                input.handleMouseButton(GLFW_MOUSE_BUTTON_RIGHT, GLFW_RELEASE); screen.update(input); input.update();
            }
            input.handleMouseMove(left + 20, 100); input.handleMouseButton(GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS);
            screen.update(input); input.update();
            assertNull(screen.playbackBrowserForTest(), "right click inside the native page goes Back");
            screen.cleanup();
        }
    }

    private MasterTitleScreen screen(SonicConfigurationService config, PlaybackLibrary library) throws Exception {
        var screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        screen.setSelectedIndexForTest(MasterTitleScreen.GameEntry.SONIC_1.ordinal());
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_1, true);
        screen.setPlaybackLibrarySourceForTest(() -> library);
        var font = MasterTitleScreen.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(screen, mock(PixelFont.class));
        return screen;
    }

    private PlaybackLibrary library() {
        return new PlaybackLibrary(List.of(recording(), trace("trace")), List.of());
    }

    private PlaybackEntry.Recording recording() {
        var context = new RecordingLaunchContext("s1", 0, 0, "sonic", List.of(), false, "current-act-fresh-start");
        var manifest = new UserRecordingManifest(1, "My recording", new BuildIdentity("0.7.prerelease", "test", false),
                context, UserRecordingSidecarMetadata.everyFrame(), new RecordingDeterminismMetadata(null, null),
                "A", 4, UserRecordingStopReason.USER_STOPPED, Instant.EPOCH);
        return new PlaybackEntry.Recording("s1", new UserRecordingEntry(directory.resolve("recording.bk2"),
                "My recording", manifest, 4, Instant.EPOCH, RecordingVersionWarning.NONE, null));
    }

    private PlaybackEntry.Trace trace(String name) {
        return new PlaybackEntry.Trace(new TraceEntry(directory.resolve(name), "s1", 0, 0, 4, 0, 0,
                new SelectedTeam("sonic", List.of()), directory.resolve(name + ".bk2"), mock(TraceMetadata.class)));
    }

    static void finishLoad(MasterTitleScreen screen) throws Exception {
        if (screen.playbackBrowserForTest() == null) {
            var task = (MenuLoadTask<?>) field(screen, "catalogLoad");
            assertNotNull(task);
            task.result();
            screen.update(new InputHandler());
        }
        screen.update(new InputHandler());
    }

    static void press(MasterTitleScreen screen, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        screen.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        screen.update(input);
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
