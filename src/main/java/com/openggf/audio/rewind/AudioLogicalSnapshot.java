package com.openggf.audio.rewind;

import com.openggf.audio.GameSound;
import com.openggf.audio.presentation.AudioPresentationSnapshot;

import java.util.Objects;
import java.util.Set;

public record AudioLogicalSnapshot(
        boolean ringLeft,
        long commandTimelineFrame,
        int commandTimelineNextOrder,
        int commandEntryCount,
        AudioPresentationSnapshot presentation,
        Set<String> donorGameIds,
        Set<DonorSfxBindingSnapshot> donorBindings) {

    public AudioLogicalSnapshot {
        presentation = Objects.requireNonNull(presentation, "presentation");
        donorGameIds = Set.copyOf(Objects.requireNonNull(donorGameIds, "donorGameIds"));
        donorBindings = Set.copyOf(Objects.requireNonNull(donorBindings, "donorBindings"));
    }

    public record DonorSfxBindingSnapshot(GameSound sound, String donorGameId, int sfxId) {
        public DonorSfxBindingSnapshot {
            Objects.requireNonNull(sound, "sound");
            Objects.requireNonNull(donorGameId, "donorGameId");
        }
    }
}
