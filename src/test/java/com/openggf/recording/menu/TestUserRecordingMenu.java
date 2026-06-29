package com.openggf.recording.menu;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen;
import com.openggf.recording.RecordingDeterminismMetadata;
import com.openggf.recording.RecordingLaunchContext;
import com.openggf.recording.RecordingVersionWarning;
import com.openggf.recording.UserRecordingEntry;
import com.openggf.recording.UserRecordingManifest;
import com.openggf.recording.UserRecordingPlaybackOptions;
import com.openggf.recording.UserRecordingSidecarMetadata;
import com.openggf.recording.UserRecordingStopReason;
import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestUserRecordingMenu {
    @TempDir
    Path tempDir;

    @Test
    void masterTitleOpensRecordingsMenuForSelectedGameWhenTestModeIsDisabled() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        List<String> openedGameIds = new ArrayList<>();
        MasterTitleScreen screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        screen.setSelectedIndexForTest(MasterTitleScreen.GameEntry.SONIC_3K.ordinal());
        screen.setUserRecordingMenuFactoryForTest((gameId, font) -> {
            openedGameIds.add(gameId);
            return new UserRecordingMenu(gameId, List.of(entry("s3k", 90)), null, (recording, options) -> { });
        });

        assertTrue(screen.tryOpenUserRecordingMenuForSelectedGame());

        assertEquals(List.of("s3k"), openedGameIds);
        assertTrue(screen.isUserRecordingMenuOpenForTest());
        assertEquals("s3k", screen.userRecordingMenuStateForTest().gameId());
    }

    @Test
    void masterTitleDoesNotOpenRecordingsMenuWhenTestModeIsEnabled() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        List<String> openedGameIds = new ArrayList<>();
        MasterTitleScreen screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        screen.setUserRecordingMenuFactoryForTest((gameId, font) -> {
            openedGameIds.add(gameId);
            return new UserRecordingMenu(gameId, List.of(), null, (recording, options) -> { });
        });

        assertFalse(screen.tryOpenUserRecordingMenuForSelectedGame());

        assertTrue(openedGameIds.isEmpty());
        assertFalse(screen.isUserRecordingMenuOpenForTest());
    }

    @Test
    void targetPromptAcceptsDigitsAndClampsToMovieLength() {
        UserRecordingMenuState state = new UserRecordingMenuState("s2", List.of(entry("s2", 125)));

        state.pressEnter();
        state.typeDigit('9');
        state.typeDigit('9');
        state.typeDigit('9');
        state.pressEnter();

        assertFalse(state.isPromptingForTargetFrame());
        assertEquals(124, state.options().targetFrame());
    }

    @Test
    void togglesAndLeftRightUpdatePlaybackOptions() {
        UserRecordingMenuState state = new UserRecordingMenuState("s1", List.of(entry("s1", 180)));

        assertFalse(state.options().pauseOnDesync());
        assertFalse(state.options().fastForward());
        assertEquals(179, state.options().targetFrame());

        state.pressP();
        state.pressF();
        state.pressLeft();
        state.pressLeft();
        state.pressRight();

        UserRecordingPlaybackOptions options = state.options();
        assertTrue(options.pauseOnDesync());
        assertTrue(options.fastForward());
        assertEquals(119, options.targetFrame());
    }

    @Test
    void selectedInfoFieldsIncludeFileCreatedEngineAndLaunchContext() {
        UserRecordingMenuState state = new UserRecordingMenuState("s3k", List.of(entry("s3k", 180)));

        List<String> lines = state.selectedInfoLines();

        assertTrue(lines.stream().anyMatch(line -> line.contains("s3k-180.bk2")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("2026-06-29T14:30:22Z")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("0.6.prerelease-abcdef123")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("s3k  Zone:02  Act:1")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("sonic+tails")));
    }

    @Test
    void escapeClosesMenuAndPromptEscapeReturnsToList() {
        UserRecordingMenuState state = new UserRecordingMenuState("s1", List.of(entry("s1", 60)));

        state.pressEnter();
        assertTrue(state.isPromptingForTargetFrame());
        state.pressEscape();
        assertFalse(state.isPromptingForTargetFrame());
        assertFalse(state.consumeCloseRequested());

        state.pressEscape();
        assertTrue(state.consumeCloseRequested());
    }

    @Test
    void amberWarningAppearsForMismatchButStillAllowsPlayback() {
        UserRecordingEntry warned = entry("s3k", 240, RecordingVersionWarning.PRERELEASE_BUILD_MISMATCH);
        UserRecordingMenuState state = new UserRecordingMenuState("s3k", List.of(warned));

        assertTrue(state.hasAmberWarning());
        assertNotNull(state.warningText());

        state.pressPlay();

        UserRecordingMenuState.PlaybackRequest request = state.consumePlaybackRequest();
        assertNotNull(request);
        assertEquals(warned, request.entry());
        assertEquals(239, request.options().targetFrame());
        assertNull(state.consumePlaybackRequest());
    }

    private static UserRecordingEntry entry(String gameId, int frameCount) {
        return entry(gameId, frameCount, RecordingVersionWarning.NONE);
    }

    private static UserRecordingEntry entry(String gameId, int frameCount, RecordingVersionWarning warning) {
        return new UserRecordingEntry(
                Path.of(gameId + "-" + frameCount + ".bk2"),
                gameId + " movie " + frameCount,
                manifest(gameId, frameCount),
                frameCount,
                Instant.parse("2026-06-29T14:30:22Z"),
                warning,
                null);
    }

    private static UserRecordingManifest manifest(String gameId, int frameCount) {
        return new UserRecordingManifest(
                UserRecordingManifest.CURRENT_SCHEMA_VERSION,
                gameId + " movie",
                new BuildIdentity("0.6.prerelease", "abcdef123", false),
                new RecordingLaunchContext(
                        gameId,
                        2,
                        1,
                        "sonic",
                        List.of("tails"),
                        false,
                        "current-act-fresh-start"),
                UserRecordingSidecarMetadata.everyFrame(),
                new RecordingDeterminismMetadata(0, null),
                "A",
                frameCount,
                UserRecordingStopReason.USER_STOPPED,
                Instant.parse("2026-06-29T14:30:22Z"));
    }
}
