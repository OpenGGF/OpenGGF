package com.openggf.audio.smps;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable source-timing metadata for the YM writes performed by one SMPS
 * driver service. A profile describes timing only; it does not select or
 * publish hardware writes.
 */
public interface YmServiceTimingProfile {
    enum SegmentKind {
        SFX_ADMISSION_PREP,
        SFX_MAX_RELEASE,
        FM_VOICE_UPLOAD,
        KEY_OFF,
        FREQUENCY_AND_KEY_ON,
        COMPLETION_RESTORE
    }

    enum PathKind {
        FIRST_ADMISSION,
        FIRST_VOICE_ATTACK,
        ORDINARY_NOTE,
        COMPLETION_RESTORE
    }

    record Variant(int port, int operatorCount, boolean bankedVoice,
                   boolean ssgEg, int carrierMask, PathKind path) {
        public Variant {
            if (port < 0 || port > 1) {
                throw new IllegalArgumentException("YM port must be 0 or 1");
            }
            if (operatorCount < 1 || operatorCount > 4) {
                throw new IllegalArgumentException(
                        "operator count must be between 1 and 4");
            }
            if (carrierMask < 0 || (carrierMask & ~0xF) != 0) {
                throw new IllegalArgumentException(
                        "carrier mask must fit four stored operators");
            }
            Objects.requireNonNull(path, "path");
        }
    }

    record Segment(SegmentKind kind, Variant variant,
                   long[] advanceBeforeWriteMasterCycles) {
        public Segment {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(variant, "variant");
            Objects.requireNonNull(advanceBeforeWriteMasterCycles,
                    "advanceBeforeWriteMasterCycles");
            if (advanceBeforeWriteMasterCycles.length == 0) {
                throw new IllegalArgumentException(
                        "timed segment must contain at least one write");
            }
            advanceBeforeWriteMasterCycles = Arrays.copyOf(
                    advanceBeforeWriteMasterCycles,
                    advanceBeforeWriteMasterCycles.length);
            if (advanceBeforeWriteMasterCycles[0] != 0) {
                throw new IllegalArgumentException(
                        "segment slot zero must be the normalized anchor");
            }
            for (long advance : advanceBeforeWriteMasterCycles) {
                if (advance < 0) {
                    throw new IllegalArgumentException(
                            "write advance cannot be negative");
                }
            }
        }

        @Override
        public long[] advanceBeforeWriteMasterCycles() {
            return Arrays.copyOf(advanceBeforeWriteMasterCycles,
                    advanceBeforeWriteMasterCycles.length);
        }
    }

    Segment requireSegment(SegmentKind kind, Variant variant);

    int maximumWritesPerDriverService();

    static YmServiceTimingProfile none() {
        return Holder.NONE;
    }

    static YmServiceTimingProfile of(
            int maximumWritesPerDriverService, Segment... segments) {
        Objects.requireNonNull(segments, "segments");
        if (maximumWritesPerDriverService < 0) {
            throw new IllegalArgumentException(
                    "maximum writes per service cannot be negative");
        }
        if (segments.length == 0) {
            if (maximumWritesPerDriverService != 0) {
                throw new IllegalArgumentException(
                        "empty profile must have a zero service maximum");
            }
            return none();
        }

        Map<Key, Segment> byKey = new HashMap<>();
        for (Segment segment : segments) {
            Objects.requireNonNull(segment, "segment");
            long total = 0;
            long[] advances = segment.advanceBeforeWriteMasterCycles();
            if (advances.length > maximumWritesPerDriverService) {
                throw new IllegalArgumentException(
                        "service maximum is below segment write count");
            }
            for (long advance : advances) {
                total = Math.addExact(total, advance);
            }
            Key key = new Key(segment.kind(), segment.variant());
            if (byKey.putIfAbsent(key, segment) != null) {
                throw new IllegalArgumentException(
                        "duplicate timing segment " + key);
            }
        }
        return new ImmutableProfile(maximumWritesPerDriverService,
                Map.copyOf(byKey));
    }

    final class Holder {
        private static final YmServiceTimingProfile NONE =
                new ImmutableProfile(0, Map.of());

        private Holder() {
        }
    }

    record Key(SegmentKind kind, Variant variant) {
    }

    final class ImmutableProfile implements YmServiceTimingProfile {
        private final int maximumWritesPerDriverService;
        private final Map<Key, Segment> segments;

        private ImmutableProfile(
                int maximumWritesPerDriverService,
                Map<Key, Segment> segments) {
            this.maximumWritesPerDriverService =
                    maximumWritesPerDriverService;
            this.segments = segments;
        }

        @Override
        public Segment requireSegment(SegmentKind kind, Variant variant) {
            Key key = new Key(Objects.requireNonNull(kind, "kind"),
                    Objects.requireNonNull(variant, "variant"));
            Segment segment = segments.get(key);
            if (segment == null) {
                throw new IllegalArgumentException(
                        "no YM service timing for " + key);
            }
            return segment;
        }

        @Override
        public int maximumWritesPerDriverService() {
            return maximumWritesPerDriverService;
        }
    }
}
