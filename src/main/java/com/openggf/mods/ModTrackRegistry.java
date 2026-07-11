package com.openggf.mods;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModTrackRegistry {
    public static final ModTrackRegistry EMPTY = new ModTrackRegistry(List.of());
    private final List<ModAudioTrack> tracks;
    private final Map<TrackKey, ModAudioTrack> byKey;

    public ModTrackRegistry(List<ModAudioTrack> tracks) {
        this.tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
        Map<TrackKey, ModAudioTrack> values = new LinkedHashMap<>();
        for (ModAudioTrack track : this.tracks) {
            if (values.putIfAbsent(track.key(), track) != null) {
                throw new IllegalArgumentException("Duplicate track key: " + track.key());
            }
        }
        byKey = Map.copyOf(values);
    }

    public Optional<ModAudioTrack> find(TrackKey key) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(key, "key")));
    }

    public List<ModAudioTrack> tracks() { return tracks; }
}
