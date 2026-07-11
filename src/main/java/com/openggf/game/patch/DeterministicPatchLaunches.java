package com.openggf.game.patch;

import com.openggf.game.GameModule;
import com.openggf.game.recording.RecordingLaunchContext;

import java.util.Objects;

/** Typed deterministic launch-request synthesis shared by non-interactive hosts. */
public final class DeterministicPatchLaunches {
    private DeterministicPatchLaunches() {
    }

    public static GameModule forRecording(ModuleResolutionService resolver,
            GameModule root, RecordingLaunchContext recording) {
        Objects.requireNonNull(recording, "recording");
        return Objects.requireNonNull(resolver, "resolver").resolveForLaunch(root,
                new GameplayLaunchRequest(recording.gameId(), recording.mainCharacter(),
                        recording.sidekickCharacters()),
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC);
    }
}
