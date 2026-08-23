package com.openggf.game.sonic1.audio;

import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmSourceProgramTiming;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Checked relative YM timing for the authenticated S1 FM5 first-attack shape. */
public final class Sonic1YmServiceTimingProfile implements YmServiceTimingProfile {

    private static final int AUTHENTICATED_CARRIER_MASK = 0b1010;
    private static final YmSourceProgramTiming.SourcePath SOURCE =
            new YmSourceProgramTiming.SourcePath(
                    "s1-fm5-ym-busy-write-program-v1.json (source-authenticated S1 68K ledger)");

    private final Map<YmSourceProgramTiming.FirstPathShape,
            YmSourceProgramTiming.SourceProgram> programs;

    private Sonic1YmServiceTimingProfile() {
        programs = Map.of(
                YmSourceProgramTiming.FirstPathShape.VOICE_NOTE,
                program(YmSourceProgramTiming.FirstPathShape.VOICE_NOTE, NO_PAN),
                YmSourceProgramTiming.FirstPathShape.VOICE_PAN_NOTE,
                program(YmSourceProgramTiming.FirstPathShape.VOICE_PAN_NOTE, PAN));
    }

    public YmSourceProgramTiming.SourceProgram requireProgram(
            YmSourceProgramTiming.FirstPathShape shape, int carrierMask) {
        Objects.requireNonNull(shape, "shape");
        if (carrierMask != AUTHENTICATED_CARRIER_MASK) {
            throw new IllegalArgumentException(
                    "S1 source timing is unavailable for carrier mask " + carrierMask);
        }
        YmSourceProgramTiming.SourceProgram program = programs.get(shape);
        if (program == null) {
            throw new IllegalArgumentException("S1 source timing shape is unavailable");
        }
        return program;
    }

    @Override
    public Segment requireSegment(SegmentKind kind, Variant variant) {
        throw new IllegalArgumentException(
                "S1 first-attack timing is a continuous source program, not a fixed segment");
    }

    @Override
    public boolean supports(SegmentKind kind, Variant variant) {
        return false;
    }

    @Override
    public TimingOwnership timingOwnership() {
        return TimingOwnership.EXCLUSIVE_SFX_FM5;
    }

    @Override
    public int maximumWritesPerDriverService() {
        return 4_096;
    }

    private static YmSourceProgramTiming.SourceProgram program(
            YmSourceProgramTiming.FirstPathShape shape, long[][] rows) {
        var writes = new java.util.ArrayList<YmSourceProgramTiming.ProgramWrite>(rows.length);
        int panCount = shape == YmSourceProgramTiming.FirstPathShape.VOICE_PAN_NOTE ? 1 : 0;
        for (int index = 0; index < rows.length; index++) {
            long[] row = rows[index];
            SegmentKind section;
            if (index < 26) {
                section = SegmentKind.FM_VOICE_UPLOAD;
            } else if (panCount == 1 && index == 26) {
                section = SegmentKind.TRACK_PAN_WRITE;
            } else if (index == 26 + panCount) {
                section = SegmentKind.KEY_OFF;
            } else {
                section = SegmentKind.FREQUENCY_AND_KEY_ON;
            }
            writes.add(new YmSourceProgramTiming.ProgramWrite(
                    section, (int) row[0], (int) row[1], row[2], row[3], row[4], row[5],
                    SOURCE));
        }
        return new YmSourceProgramTiming.SourceProgram(
                YmSourceProgramTiming.ProgramKind.S1_FM5_FIRST_VOICE_ATTACK,
                new YmSourceProgramTiming.ProgramVariant(
                        1, AUTHENTICATED_CARRIER_MASK, shape),
                writes,
                panCount == 0
                        ? List.of(
                                new YmSourceProgramTiming.ProgramSection(
                                        SegmentKind.FM_VOICE_UPLOAD, 0, 26),
                                new YmSourceProgramTiming.ProgramSection(
                                        SegmentKind.KEY_OFF, 26, 1),
                                new YmSourceProgramTiming.ProgramSection(
                                        SegmentKind.FREQUENCY_AND_KEY_ON, 27, 3))
                        : List.of(
                                new YmSourceProgramTiming.ProgramSection(
                                        SegmentKind.FM_VOICE_UPLOAD, 0, 26),
                                new YmSourceProgramTiming.ProgramSection(
                                        SegmentKind.TRACK_PAN_WRITE, 26, 1),
                                new YmSourceProgramTiming.ProgramSection(
                                        SegmentKind.KEY_OFF, 27, 1),
                                new YmSourceProgramTiming.ProgramSection(
                                        SegmentKind.FREQUENCY_AND_KEY_ON, 28, 3)));
    }

    // port, register, fixed-before-status, status-read, taken-loop, ready-to-data.
    private static final long[][] COMMON_PREFIX = {
            {1, 0xB1, 0, 0, 0, 0},
            {1, 0x31, 938, 119, 259, 252}, {1, 0x39, 924, 119, 273, 266},
            {1, 0x35, 924, 119, 259, 252}, {1, 0x3D, 924, 119, 259, 252},
            {1, 0x51, 924, 119, 259, 252}, {1, 0x59, 924, 119, 273, 266},
            {1, 0x55, 924, 119, 259, 252}, {1, 0x5D, 924, 119, 259, 252},
            {1, 0x61, 924, 119, 259, 252}, {1, 0x69, 924, 119, 273, 266},
            {1, 0x65, 924, 119, 259, 252}, {1, 0x6D, 924, 119, 259, 252},
            {1, 0x71, 924, 119, 259, 252}, {1, 0x79, 924, 119, 273, 266},
            {1, 0x75, 924, 119, 259, 252}, {1, 0x7D, 924, 119, 259, 252},
            {1, 0x81, 924, 119, 259, 252}, {1, 0x89, 924, 119, 273, 266},
            {1, 0x85, 924, 119, 259, 252}, {1, 0x8D, 924, 119, 259, 252}
    };

    private static final long[][] NO_PAN = concat(COMMON_PREFIX, new long[][] {
            {1, 0x41, 1358, 119, 448, 252}, {1, 0x49, 1078, 119, 259, 252},
            {1, 0x45, 1050, 119, 259, 252}, {1, 0x4D, 1064, 119, 273, 266},
            {1, 0xB5, 980, 119, 259, 252}, {0, 0x28, 1218, 119, 273, 700},
            {1, 0xA5, 5068, 119, 448, 266}, {1, 0xA1, 812, 119, 273, 252},
            {0, 0x28, 938, 119, 259, 700}
    });

    private static final long[][] PAN = concat(COMMON_PREFIX, new long[][] {
            {1, 0x41, 1358, 119, 259, 266}, {1, 0x49, 1064, 119, 259, 252},
            {1, 0x45, 1050, 119, 259, 266}, {1, 0x4D, 1064, 119, 259, 252},
            {1, 0xB5, 980, 119, 259, 252}, {1, 0xB5, 2226, 119, 448, 266},
            {0, 0x28, 1106, 119, 259, 714}, {1, 0xA5, 4312, 119, 448, 252},
            {1, 0xA1, 826, 119, 259, 266}, {0, 0x28, 938, 119, 259, 714}
    });

    public static final Sonic1YmServiceTimingProfile PROFILE =
            new Sonic1YmServiceTimingProfile();

    private static long[][] concat(long[][] first, long[][] second) {
        long[][] result = new long[first.length + second.length][];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
