package com.openggf.game.playback;

import com.openggf.game.recording.UserRecordingCatalog;
import com.openggf.trace.catalog.TraceCatalog;
import com.openggf.version.BuildIdentity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PlaybackLibrary(List<PlaybackEntry> entries, List<String> issues) {
    public PlaybackLibrary {
        entries = List.copyOf(entries);
        issues = List.copyOf(issues);
    }

    public static PlaybackLibrary scan(Path workspace, Path traceRoot, Path configuredMoviePath,
                                       BuildIdentity identity) {
        Objects.requireNonNull(workspace, "workspace");
        Path base = workspace.toAbsolutePath().normalize();
        List<PlaybackEntry> entries = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Set<Path> representedMovies = new HashSet<>();
        scanRecordings(base, identity, entries, issues, representedMovies);
        if (traceRoot != null) {
            Path traces = resolve(base, traceRoot);
            if (readableDirectory(traces, "Trace catalog", issues)) {
                try {
                    for (var trace : TraceCatalog.scan(traces, issues::add)) {
                        entries.add(new PlaybackEntry.Trace(trace));
                        representedMovies.add(identityPath(trace.bk2Path()));
                    }
                } catch (UncheckedIOException | SecurityException failure) {
                    issues.add("Trace catalog: " + failure.getMessage());
                }
            }
        }
        if (configuredMoviePath != null && !configuredMoviePath.toString().isBlank()) {
            Path movie = resolve(base, configuredMoviePath);
            if (representedMovies.add(identityPath(movie))) {
                entries.add(entry("", UserRecordingCatalog.inspectMovie(movie, identity)));
            }
        }
        entries.sort(Comparator.comparing(PlaybackEntry::gameId)
                .thenComparing(PlaybackEntry::kind)
                .thenComparing(PlaybackLibrary::modifiedAt, Comparator.reverseOrder())
                .thenComparing(PlaybackEntry::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.path().toString()));
        return new PlaybackLibrary(entries, issues);
    }

    private static void scanRecordings(Path base, BuildIdentity identity, List<PlaybackEntry> entries,
                                       List<String> issues, Set<Path> representedMovies) {
        Path recordings = base.resolve("recordings");
        if (!readableDirectory(recordings, "Recordings", issues)) return;
        try (var directories = Files.list(recordings)) {
            for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                String gameId = directory.getFileName().toString();
                try {
                    for (var movie : UserRecordingCatalog.scanMovies(base, gameId, identity)) {
                        if (representedMovies.add(identityPath(movie.path()))) entries.add(entry(gameId, movie));
                    }
                } catch (IOException | UncheckedIOException | SecurityException failure) {
                    issues.add("Recordings for " + gameId + ": " + failure.getMessage());
                }
            }
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            issues.add("Recordings: " + failure.getMessage());
        }
    }

    private static PlaybackEntry entry(String gameId, UserRecordingCatalog.InspectedMovie movie) {
        return switch (movie) {
            case UserRecordingCatalog.InspectedMovie.Recording recording -> {
                var manifest = recording.entry().manifest();
                String recordedGame = manifest != null && manifest.launchContext() != null
                        ? manifest.launchContext().gameId() : gameId;
                yield new PlaybackEntry.Recording(recordedGame, recording.entry());
            }
            case UserRecordingCatalog.InspectedMovie.Movie bare ->
                    new PlaybackEntry.Movie(bare.path(), bare.rowCount(), bare.loadError());
        };
    }

    private static boolean readableDirectory(Path path, String source, List<String> issues) {
        try {
            if (!Files.exists(path)) return false;
            if (Files.isDirectory(path) && Files.isReadable(path)) return true;
            issues.add(source + " is not a readable directory: " + path);
        } catch (SecurityException failure) {
            issues.add(source + ": " + failure.getMessage());
        }
        return false;
    }

    private static Path resolve(Path base, Path path) {
        return path.isAbsolute() ? path.normalize() : base.resolve(path).normalize();
    }

    private static Path identityPath(Path path) {
        try { return path.toRealPath(); }
        catch (IOException | SecurityException failure) { return path.toAbsolutePath().normalize(); }
    }

    private static Instant modifiedAt(PlaybackEntry entry) {
        return entry instanceof PlaybackEntry.Recording recording
                ? recording.recording().modifiedAt() : Instant.EPOCH;
    }
}
