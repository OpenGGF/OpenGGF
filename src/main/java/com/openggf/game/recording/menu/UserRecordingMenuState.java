package com.openggf.game.recording.menu;

import com.openggf.game.recording.RecordingLaunchContext;
import com.openggf.game.recording.UserRecordingEntry;
import com.openggf.game.recording.UserRecordingManifest;
import com.openggf.game.recording.UserRecordingPlaybackOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Options for one recording selected in the common playback browser. */
public final class UserRecordingMenuState {
    private static final int TARGET_FRAME_STEP = 60;

    private final String gameId;
    private final UserRecordingEntry entry;
    private UserRecordingPlaybackOptions options;

    public UserRecordingMenuState(String gameId, UserRecordingEntry entry) {
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.entry = Objects.requireNonNull(entry, "entry");
        options = new UserRecordingPlaybackOptions(Math.max(0, entry.frameCount() - 1), false, false);
    }

    public void pressLeft() { setTargetFrame(options.targetFrame() - TARGET_FRAME_STEP); }
    public void pressRight() { setTargetFrame(options.targetFrame() + TARGET_FRAME_STEP); }

    public void pressP() {
        options = new UserRecordingPlaybackOptions(options.targetFrame(), !options.pauseOnDesync(), options.fastForward());
    }

    public void pressF() {
        options = new UserRecordingPlaybackOptions(options.targetFrame(), options.pauseOnDesync(), !options.fastForward());
    }

    void setTargetFrame(int frame) {
        int target = Math.max(0, Math.min(Math.max(0, entry.frameCount() - 1), frame));
        options = new UserRecordingPlaybackOptions(target, options.pauseOnDesync(), options.fastForward());
    }

    public String gameId() { return gameId; }
    public UserRecordingEntry selectedEntry() { return entry; }
    public UserRecordingPlaybackOptions options() { return options; }

    public String warningText() {
        return switch (entry.versionWarning()) {
            case MISSING_METADATA -> "Missing engine metadata";
            case OFFICIAL_VERSION_MISMATCH -> "Engine version mismatch";
            case PRERELEASE_BUILD_MISMATCH -> "Prerelease build mismatch";
            case DIRTY_BUILD -> "Dirty build recording";
            case NONE -> null;
        };
    }

    public List<String> selectedInfoLines() {
        UserRecordingManifest manifest = entry.manifest();
        RecordingLaunchContext context = manifest == null ? null : manifest.launchContext();
        String engine = manifest == null || manifest.engineIdentity() == null
                ? "unknown" : manifest.engineIdentity().displayVersion();
        String createdAt = manifest == null || manifest.createdAt() == null
                ? "unknown" : manifest.createdAt().toString();
        List<String> lines = new ArrayList<>();
        lines.add(entry.displayName() + "  BK2 rows: " + entry.frameCount());
        lines.add("File: " + entry.path());
        lines.add("Created: " + createdAt);
        lines.add("Engine: " + engine);
        if (context != null) {
            String team = context.mainCharacter();
            if (!context.sidekickCharacters().isEmpty()) team += "+" + String.join("+", context.sidekickCharacters());
            lines.add(String.format("Launch: %s  Zone:%02X  Act:%d  Team:%s", context.gameId(), context.zone(), context.act(), team));
            lines.add("Route: " + context.launchRoute());
        } else lines.add("Launch: unknown");
        if (entry.loadError() != null && !entry.loadError().isBlank()) lines.add(entry.loadError());
        return List.copyOf(lines);
    }
}
