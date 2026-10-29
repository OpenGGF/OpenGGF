package com.openggf.game.playback;

import com.openggf.game.recording.UserRecordingEntry;
import com.openggf.trace.catalog.TraceEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        return new PlaybackFacts.Fact("Status", availability.reason(), switch (availability.status()) {
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
        String label();

        record MovieRows(int count) implements Extent {
            @Override public String label() { return count + " BK2 rows"; }
        }

        record TraceFrames(int count) implements Extent {
            @Override public String label() { return count + " trace frames"; }
        }

        record RunTraceFrames(int count) implements Extent {
            @Override public String label() { return count + " run trace frames"; }
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
                return new Availability(Availability.Status.INVALID, "Recording launch metadata is missing");
            }
            if (recording.frameCount() <= 0) {
                return new Availability(Availability.Status.INVALID, "Recording has no input rows");
            }
            return new Availability(Availability.Status.READY, "Replay from its recorded act start");
        }

        @Override public List<PlaybackFacts.Fact> details() {
            List<PlaybackFacts.Fact> facts = new ArrayList<>();
            facts.add(new PlaybackFacts.Fact("Type", "Engine recording"));
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
                return new Availability(Availability.Status.INVALID, "Trace launch metadata or input movie is missing");
            }
            return new Availability(Availability.Status.READY, "Replay with ROM comparison");
        }

        @Override public List<PlaybackFacts.Fact> details() {
            List<PlaybackFacts.Fact> facts = new ArrayList<>();
            facts.add(new PlaybackFacts.Fact("Type", trace.isRun() ? "ROM comparison run" : "ROM comparison trace"));
            facts.add(status(availability()));
            facts.add(new PlaybackFacts.Fact("Length", PlaybackFacts.length(trace.frameCount(),
                    trace.isRun() ? "run trace frames" : "trace frames")));
            String label = trace.displayLabel();
            int special = label.indexOf("SPECIAL STAGE ");
            String start = trace.isRun() ? "Run of " + trace.runManifest().segments().size() + " segments"
                    : special >= 0 ? "Special stage " + label.substring(special + 14)
                    : PlaybackFacts.zoneAct(trace.zone(), trace.act());
            facts.add(new PlaybackFacts.Fact("Start", PlaybackFacts.gameName(trace.gameId()) + ", " + start));
            if (trace.team() != null)
                facts.add(new PlaybackFacts.Fact("Team", PlaybackFacts.team(trace.team().mainCharacter(), trace.team().sidekicks())));
            facts.add(new PlaybackFacts.Fact("Input", "BK2 start row " + trace.bk2StartOffset()));
            facts.add(new PlaybackFacts.Fact("Folder", path().toString()));
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
                    "Needs launch setup: this BK2 does not declare its starting state");
        }

        @Override public List<PlaybackFacts.Fact> details() {
            return List.of(new PlaybackFacts.Fact("Type", "Bare BK2 movie"), status(availability()),
                    new PlaybackFacts.Fact("Length", PlaybackFacts.length(rowCount, "BK2 rows")),
                    new PlaybackFacts.Fact("Start", "Not declared. A filename or the current game cannot supply it."),
                    new PlaybackFacts.Fact("File", path.toString()));
        }
    }
}
