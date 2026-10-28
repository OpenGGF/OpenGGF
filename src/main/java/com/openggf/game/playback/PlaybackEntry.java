package com.openggf.game.playback;

import com.openggf.game.recording.RecordingVersionWarning;
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
    List<String> details();

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

        @Override public List<String> details() {
            List<String> lines = new ArrayList<>();
            lines.add("Engine recording");
            lines.add(extent().label());
            var manifest = recording.manifest();
            if (manifest != null) {
                var launch = manifest.launchContext();
                if (launch != null) {
                    lines.add("Game " + launch.gameId() + ", zone " + launch.zone() + ", act " + launch.act());
                    lines.add("Team " + launch.mainCharacter()
                            + (launch.sidekickCharacters().isEmpty() ? "" : " + " + String.join(" + ", launch.sidekickCharacters())));
                }
                if (manifest.engineIdentity() != null) lines.add("OpenGGF " + manifest.engineIdentity().displayVersion());
            }
            if (recording.versionWarning() != null && recording.versionWarning() != RecordingVersionWarning.NONE) {
                lines.add(switch (recording.versionWarning()) {
                    case MISSING_METADATA -> "Recorded build identity is unknown";
                    case OFFICIAL_VERSION_MISMATCH -> "Recorded with a different released version";
                    case PRERELEASE_BUILD_MISMATCH -> "Recorded with a different development build";
                    case DIRTY_BUILD -> "Recorded or current build has local changes";
                    case NONE -> throw new IllegalStateException("Unexpected warning");
                });
            }
            lines.add(availability().reason());
            lines.add(path().toString());
            return List.copyOf(lines);
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

        @Override public List<String> details() {
            List<String> lines = new ArrayList<>();
            lines.add(trace.isRun() ? "ROM comparison run" : "ROM comparison trace");
            lines.add(extent().label());
            lines.add("BK2 start row " + trace.bk2StartOffset());
            if (trace.team() != null) {
                lines.add("Team " + trace.team().mainCharacter()
                        + (trace.team().sidekicks().isEmpty() ? "" : " + " + String.join(" + ", trace.team().sidekicks())));
            }
            lines.add(availability().reason());
            lines.add(path().toString());
            return List.copyOf(lines);
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

        @Override public List<String> details() {
            return List.of("Bare BK2 movie", extent().label(), availability().reason(),
                    "A filename or current game cannot supply its launch state", path.toString());
        }
    }
}
