package com.openggf.mods;

import com.openggf.io.ModKeySyntax;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable prepared view with an explicit PCM ownership lease.
 * Callers must close the session before replacing/releasing it and must not retain or
 * use {@link PcmData} references obtained from {@link #tracks()} after close.
 */
public final class PreparedAudioSession implements AutoCloseable {
    private List<PreparedTrack> tracks;
    private final List<ModFinding> findings;
    private final Set<String> failedOwners;
    private Runnable release;
    private boolean closed;

    public PreparedAudioSession(List<PreparedTrack> tracks, List<ModFinding> findings,
                                Set<String> failedOwners) {
        this(tracks, findings, failedOwners, () -> { });
    }

    PreparedAudioSession(List<PreparedTrack> tracks, List<ModFinding> findings,
                         Set<String> failedOwners, Runnable release) {
        this.tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
        this.findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        LinkedHashSet<String> failedCopy = new LinkedHashSet<>();
        for (String owner : Objects.requireNonNull(failedOwners, "failedOwners")) {
            failedCopy.add(ModKeySyntax.requireManifestId(Objects.requireNonNull(owner, "failed owner")));
        }
        this.failedOwners = Collections.unmodifiableSet(failedCopy);
        this.release = Objects.requireNonNull(release, "release");
        Set<TrackKey> keys = new HashSet<>();
        for (PreparedTrack track : this.tracks) {
            if (!keys.add(track.key())) throw new IllegalArgumentException("Duplicate prepared track: " + track.key());
            if (failedCopy.contains(track.key().modId())) {
                throw new IllegalArgumentException("Failed owner cannot retain prepared tracks: " + track.key().modId());
            }
        }
    }

    public synchronized List<PreparedTrack> tracks() {
        if (closed) throw new IllegalStateException("Prepared audio session is closed");
        return tracks;
    }

    public List<ModFinding> findings() { return findings; }
    public Set<String> failedOwners() { return failedOwners; }
    public synchronized boolean isClosed() { return closed; }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        tracks = List.of();
        Runnable ownedRelease = release;
        release = () -> { };
        ownedRelease.run();
    }
}
