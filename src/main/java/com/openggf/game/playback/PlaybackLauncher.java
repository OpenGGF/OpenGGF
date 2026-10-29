package com.openggf.game.playback;

import com.openggf.game.recording.menu.UserRecordingMenu;
import com.openggf.trace.catalog.TraceEntry;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class PlaybackLauncher {
    private final UserRecordingMenu.PlaybackStarter recordingStarter;
    private final Predicate<TraceEntry> traceStarter;
    private final BooleanSupplier occupied;
    private final BooleanSupplier testMode;

    public PlaybackLauncher(UserRecordingMenu.PlaybackStarter recordingStarter, Predicate<TraceEntry> traceStarter,
                            BooleanSupplier occupied, BooleanSupplier testMode) {
        this.recordingStarter = Objects.requireNonNull(recordingStarter, "recordingStarter");
        this.traceStarter = Objects.requireNonNull(traceStarter, "traceStarter");
        this.occupied = Objects.requireNonNull(occupied, "occupied");
        this.testMode = Objects.requireNonNull(testMode, "testMode");
    }

    public boolean launch(PlaybackRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        if (occupied.getAsBoolean()) {
            throw new IllegalStateException("Another playback session already owns game input");
        }
        return switch (request) {
            case PlaybackRequest.Recording recording -> {
                if (testMode.getAsBoolean()) {
                    throw new IllegalStateException("Test mode only permits ROM trace playback");
                }
                requireReady(recording.entry());
                recordingStarter.start(recording.entry().recording(), recording.options());
                yield true;
            }
            case PlaybackRequest.Trace trace -> {
                requireReady(trace.entry());
                yield traceStarter.test(trace.entry().trace());
            }
        };
    }

    private static void requireReady(PlaybackEntry entry) {
        PlaybackEntry.Availability availability = entry.availability();
        if (!availability.ready()) throw new IllegalStateException(availability.reason());
    }
}
