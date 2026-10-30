package com.openggf.game.playback;

import com.openggf.game.recording.UserRecordingEntry;
import com.openggf.i18n.UiMessages;
import com.openggf.trace.catalog.TraceEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;

public sealed interface PlaybackEntry {
    Path path();
    String label();
    String gameId();
    Kind kind();
    Availability availability();
    Extent extent();
    /** Labelled facts for the details page: type, readiness, extent, start and source. */
    List<PlaybackFacts.Fact> details();

    private static PlaybackFacts.Fact status(Availability availability) {
        return new PlaybackFacts.Fact(UiMessages.text("menu.playback.status_label"), availability.reason(), switch (availability.status()) {
            case READY -> PlaybackFacts.Tone.READY;
            case NEEDS_SETUP -> PlaybackFacts.Tone.WARNING;
            case INVALID -> PlaybackFacts.Tone.ERROR;
        });
    }

    enum Kind { RECORDING, TRACE, MOVIE }

    record Availability(Status status, String reason) {
        public enum Status { READY, NEEDS_SETUP, INVALID }

        public Availability {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
        }

        public boolean ready() { return status == Status.READY; }
    }

    sealed interface Extent {
        int count();
        String label();

        record MovieRows(int count) implements Extent {
            @Override public String label() { return UiMessages.text("menu.playback.extent.movie_rows", Map.of("count", count, "value", Integer.toString(count))); }
        }

        record TraceFrames(int count) implements Extent {
            @Override public String label() { return UiMessages.text("menu.playback.extent.trace_frames", Map.of("count", count, "value", Integer.toString(count))); }
        }

        record RunTraceFrames(int count) implements Extent {
            @Override public String label() { return UiMessages.text("menu.playback.extent.run_frames", Map.of("count", count, "value", Integer.toString(count))); }
        }
    }

    record Recording(String gameId, UserRecordingEntry recording) implements PlaybackEntry {
        public Recording {
            Objects.requireNonNull(gameId, "gameId");
            Objects.requireNonNull(recording, "recording");
        }

        @Override public Path path() { return recording.path(); }
        @Override public String label() { return recording.displayName(); }
        @Override public Kind kind() { return Kind.RECORDING; }
        @Override public Extent extent() { return new Extent.MovieRows(recording.frameCount()); }

        @Override public Availability availability() {
            if (!recording.isLoadable()) {
                return new Availability(Availability.Status.INVALID, recording.loadError());
            }
            if (recording.manifest() == null || recording.manifest().launchContext() == null) {
                return new Availability(Availability.Status.INVALID, UiMessages.text("menu.playback.recording_metadata_missing"));
            }
            if (recording.frameCount() <= 0) {
                return new Availability(Availability.Status.INVALID, UiMessages.text("menu.playback.recording_rows_missing"));
            }
            return new Availability(Availability.Status.READY, UiMessages.text("menu.playback.recording_ready"));
        }

        @Override public List<PlaybackFacts.Fact> details() {
            List<PlaybackFacts.Fact> facts = new ArrayList<>();
            facts.add(new PlaybackFacts.Fact(UiMessages.text("menu.playback.type_label"), UiMessages.text("menu.playback.type.recording")));
            facts.add(status(availability()));
            facts.addAll(PlaybackFacts.recording(recording));
            return List.copyOf(facts);
        }
    }

    record Trace(TraceEntry trace) implements PlaybackEntry {
        public Trace { Objects.requireNonNull(trace, "trace"); }

        @Override public Path path() { return trace.dir(); }
        @Override public String label() {
            return trace.isRun() ? trace.displayLabel() : trace.dir().getFileName() + "  " + trace.displayLabel();
        }
        @Override public String gameId() { return trace.gameId(); }
        @Override public Kind kind() { return Kind.TRACE; }
        @Override public Extent extent() {
            return trace.isRun() ? new Extent.RunTraceFrames(trace.frameCount())
                    : new Extent.TraceFrames(trace.frameCount());
        }

        @Override public Availability availability() {
            if (trace.metadata() == null || trace.bk2Path() == null || trace.frameCount() <= 0) {
                return new Availability(Availability.Status.INVALID, UiMessages.text("menu.playback.trace_metadata_missing"));
            }
            return new Availability(Availability.Status.READY, UiMessages.text("menu.playback.trace_ready"));
        }

        @Override public List<PlaybackFacts.Fact> details() {
            List<PlaybackFacts.Fact> facts = new ArrayList<>();
            facts.add(new PlaybackFacts.Fact(UiMessages.text("menu.playback.type_label"),
                    UiMessages.text(trace.isRun() ? "menu.playback.type.run" : "menu.playback.type.trace")));
            facts.add(status(availability()));
            facts.add(new PlaybackFacts.Fact(UiMessages.text("editor.stageplay_menu_presentation.length_11kyxom"), PlaybackFacts.length(extent())));
            facts.add(new PlaybackFacts.Fact(UiMessages.text("editor.stageplay_hud.start_1br2gy"),
                    PlaybackFacts.gameStart(trace.gameId(), PlaybackFacts.traceStart(trace))));
            if (trace.team() != null)
                facts.add(new PlaybackFacts.Fact(UiMessages.text("launch.item.team"), PlaybackFacts.team(trace.team().mainCharacter(), trace.team().sidekicks())));
            facts.add(new PlaybackFacts.Fact(UiMessages.text("menu.playback.input_label"),
                    UiMessages.text("menu.playback.bk2_start_row", Map.of("row", Integer.toString(trace.bk2StartOffset())))));
            if (trace.bk2Path() != null) facts.add(new PlaybackFacts.Fact("BK2", trace.bk2Path().toString()));
            facts.add(new PlaybackFacts.Fact(UiMessages.text("menu.playback.folder_label"), path().toString()));
            return List.copyOf(facts);
        }
    }

    record Movie(Path path, int rowCount, String loadError) implements PlaybackEntry {
        public Movie { Objects.requireNonNull(path, "path"); }
        @Override public String label() { return path.getFileName().toString(); }
        @Override public String gameId() { return ""; }
        @Override public Kind kind() { return Kind.MOVIE; }
        @Override public Extent extent() { return new Extent.MovieRows(rowCount); }

        @Override public Availability availability() {
            if (loadError != null && !loadError.isBlank()) {
                return new Availability(Availability.Status.INVALID, loadError);
            }
            return new Availability(Availability.Status.NEEDS_SETUP,
                    UiMessages.text("menu.playback.movie_needs_setup"));
        }

        @Override public List<PlaybackFacts.Fact> details() {
            return List.of(new PlaybackFacts.Fact(UiMessages.text("menu.playback.type_label"), UiMessages.text("menu.playback.type.movie")), status(availability()),
                    new PlaybackFacts.Fact(UiMessages.text("editor.stageplay_menu_presentation.length_11kyxom"), PlaybackFacts.length(extent())),
                    new PlaybackFacts.Fact(UiMessages.text("editor.stageplay_hud.start_1br2gy"), UiMessages.text("menu.playback.movie_start_unknown")),
                    new PlaybackFacts.Fact(UiMessages.text("editor.stageplay_menu_presentation.file_1axlo"), path.toString()));
        }
    }
}
