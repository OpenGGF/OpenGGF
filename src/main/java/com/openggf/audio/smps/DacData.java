package com.openggf.audio.smps;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@com.openggf.game.ModApi
public final class DacData {
    /**
     * Compatibility snapshot for the published Mod API. The playback owner
     * never reads this map; its independently cloned {@link Sample} catalog is
     * the immutable runtime authority.
     */
    public final Map<Integer, byte[]> samples;
    public final Map<Integer, DacEntry> mapping; // NoteID -> Entry
    public final int baseCycles; // Game-specific DAC base cycles (S1=301, S2=295, S3K=297)
    private final Map<Integer, Sample> ownedSamples;

    public DacData(Map<Integer, byte[]> samples, Map<Integer, DacEntry> mapping) {
        this(samples, mapping, 288); // Default to S2 value for backwards compatibility
    }

    public DacData(Map<Integer, byte[]> samples, Map<Integer, DacEntry> mapping, int baseCycles) {
        Map<Integer, Sample> ownedSamples = new HashMap<>();
        Map<Integer, byte[]> compatibilitySamples = new HashMap<>();
        for (Map.Entry<Integer, byte[]> entry : samples.entrySet()) {
            byte[] bytes = entry.getValue();
            ownedSamples.put(entry.getKey(), bytes == null ? null : new Sample(bytes));
            compatibilitySamples.put(entry.getKey(), bytes == null ? null : bytes.clone());
        }
        this.ownedSamples = Collections.unmodifiableMap(ownedSamples);
        this.samples = Collections.unmodifiableMap(compatibilitySamples);
        this.mapping = Collections.unmodifiableMap(new HashMap<>(mapping));
        this.baseCycles = baseCycles;
    }

    public Sample sample(int sampleId) {
        return ownedSamples.get(sampleId);
    }

    public DacEntry mappingForNote(int noteId) {
        return mapping.get(noteId);
    }

    public int baseCycles() {
        return baseCycles;
    }

    public int sampleCount() {
        return ownedSamples.size();
    }

    public int mappingCount() {
        return mapping.size();
    }

    public boolean hasSample(int sampleId) {
        return ownedSamples.containsKey(sampleId);
    }

    @com.openggf.game.ModApi
    public static final class Sample {
        private final byte[] bytes;

        private Sample(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        public int length() {
            return bytes.length;
        }

        public byte byteAt(int index) {
            return bytes[index];
        }
    }

    @com.openggf.game.ModApi
    public static final class DacEntry {
        public final int sampleId;
        public final int rate;

        public DacEntry(int sampleId, int rate) {
            this.sampleId = sampleId;
            this.rate = rate;
        }

        public int sampleId() {
            return sampleId;
        }

        public int rate() {
            return rate;
        }
    }
}
