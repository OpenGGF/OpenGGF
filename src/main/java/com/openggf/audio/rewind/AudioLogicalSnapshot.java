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
        AudioBackendLogicalSnapshot backend,
        AudioPresentationSnapshot presentation,
        Set<String> donorGameIds,
        Set<DonorSfxBindingSnapshot> donorBindings) {

    public AudioLogicalSnapshot {
        backend = Objects.requireNonNull(backend, "backend");
        presentation = Objects.requireNonNull(presentation, "presentation");
        donorGameIds = Set.copyOf(Objects.requireNonNull(donorGameIds, "donorGameIds"));
        donorBindings = Set.copyOf(Objects.requireNonNull(donorBindings, "donorBindings"));
    }

    public AudioLogicalSnapshot(
            boolean ringLeft,
            long commandTimelineFrame,
            int commandTimelineNextOrder,
            int commandEntryCount,
            AudioBackendLogicalSnapshot backend,
            Set<String> donorGameIds,
            Set<DonorSfxBindingSnapshot> donorBindings) {
        this(ringLeft, commandTimelineFrame, commandTimelineNextOrder,
                commandEntryCount, backend,
                com.openggf.audio.presentation.AudioPresentationSnapshot.empty(),
                donorGameIds, donorBindings);
    }

    public record DonorSfxBindingSnapshot(GameSound sound, String donorGameId, int sfxId) {
        public DonorSfxBindingSnapshot {
            Objects.requireNonNull(sound, "sound");
            Objects.requireNonNull(donorGameId, "donorGameId");
        }
    }
}
