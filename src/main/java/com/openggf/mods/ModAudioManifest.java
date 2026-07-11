package com.openggf.mods;

import com.openggf.io.ModInputLimits;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ModAudioManifest(int formatVersion, List<ModAudioTrack> tracks,
                               List<ModAudioSfx> sfx) {
    public ModAudioManifest {
        if (formatVersion != 1) throw new IllegalArgumentException("formatVersion must be 1");
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
        sfx = List.copyOf(Objects.requireNonNull(sfx, "sfx"));
        if (tracks.size() > ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES
                || sfx.size() > ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES) {
            throw new IllegalArgumentException("Audio manifest exceeds collection limit");
        }
        Set<TrackKey> trackKeys = new HashSet<>();
        for (ModAudioTrack track : tracks) {
            if (!trackKeys.add(track.key())) throw new IllegalArgumentException("Duplicate track id: " + track.key());
        }
        Set<SfxKey> sfxKeys = new HashSet<>();
        for (ModAudioSfx value : sfx) {
            if (!sfxKeys.add(value.key())) throw new IllegalArgumentException("Duplicate SFX id: " + value.key());
        }
    }
}
