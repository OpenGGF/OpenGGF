package com.openggf.game.sonic1.audio;

import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmSourceProgramTiming;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Checked source-cost YM timing for the authenticated S1 FM5 first-attack shape. */
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
    public YmSourceProgramTiming.SourceProgram requireSourceProgram(
            YmSourceProgramTiming.FirstPathShape shape, int carrierMask) {
        return requireProgram(shape, carrierMask);
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
            Integer fixedValue = section == SegmentKind.KEY_OFF ? Integer.valueOf(0x05)
                    : section == SegmentKind.FREQUENCY_AND_KEY_ON
                    && row[1] == 0x28 ? Integer.valueOf(0xF5) : null;
            writes.add(new YmSourceProgramTiming.ProgramWrite(
                    section, (int) row[0], (int) row[1], fixedValue,
                    row[2], row[3], row[4], row[5], SOURCE));
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

    // port, register, fixed-before-status, status-read, BUSY-loop, ready-to-data.
    private static final long[][] NO_PAN = {
            {1, 0xB1, 0, 0, 0, 0}, {1, 0x31, 924, 119, 259, 700},
            {1, 0x39, 910, 119, 259, 700}, {1, 0x35, 910, 119, 259, 700},
            {1, 0x3D, 910, 119, 259, 700}, {1, 0x51, 910, 119, 259, 700},
            {1, 0x59, 910, 119, 259, 700}, {1, 0x55, 910, 119, 259, 700},
            {1, 0x5D, 910, 119, 259, 700}, {1, 0x61, 910, 119, 259, 700},
            {1, 0x69, 910, 119, 259, 700}, {1, 0x65, 910, 119, 259, 700},
            {1, 0x6D, 910, 119, 259, 700}, {1, 0x71, 910, 119, 259, 700},
            {1, 0x79, 910, 119, 259, 700}, {1, 0x75, 910, 119, 259, 700},
            {1, 0x7D, 910, 119, 259, 700}, {1, 0x81, 910, 119, 259, 700},
            {1, 0x89, 910, 119, 259, 700}, {1, 0x85, 910, 119, 259, 700},
            {1, 0x8D, 910, 119, 259, 700}, {1, 0x41, 1330, 119, 259, 700},
            {1, 0x49, 1050, 119, 259, 700}, {1, 0x45, 1036, 119, 259, 700},
            {1, 0x4D, 1050, 119, 259, 700}, {1, 0xB5, 966, 119, 259, 700},
            {0, 0x28, 1204, 119, 259, 700}, {1, 0xA5, 4984, 119, 259, 700},
            {1, 0xA1, 812, 119, 259, 700}, {0, 0x28, 924, 119, 259, 700}
    };

    private static final long[][] PAN = {
            {1, 0xB1, 0, 0, 0, 0}, {1, 0x31, 924, 119, 259, 700},
            {1, 0x39, 910, 119, 259, 700}, {1, 0x35, 910, 119, 259, 700},
            {1, 0x3D, 910, 119, 259, 700}, {1, 0x51, 910, 119, 259, 700},
            {1, 0x59, 910, 119, 259, 700}, {1, 0x55, 910, 119, 259, 700},
            {1, 0x5D, 910, 119, 259, 700}, {1, 0x61, 910, 119, 259, 700},
            {1, 0x69, 910, 119, 259, 700}, {1, 0x65, 910, 119, 259, 700},
            {1, 0x6D, 910, 119, 259, 700}, {1, 0x71, 910, 119, 259, 700},
            {1, 0x79, 910, 119, 259, 700}, {1, 0x75, 910, 119, 259, 700},
            {1, 0x7D, 910, 119, 259, 700}, {1, 0x81, 910, 119, 259, 700},
            {1, 0x89, 910, 119, 259, 700}, {1, 0x85, 910, 119, 259, 700},
            {1, 0x8D, 910, 119, 259, 700}, {1, 0x41, 1330, 119, 259, 700},
            {1, 0x49, 1050, 119, 259, 700}, {1, 0x45, 1036, 119, 259, 700},
            {1, 0x4D, 1050, 119, 259, 700}, {1, 0xB5, 966, 119, 259, 700},
            {1, 0xB5, 2184, 119, 259, 700}, {0, 0x28, 1092, 119, 259, 700},
            {1, 0xA5, 4242, 119, 259, 700}, {1, 0xA1, 812, 119, 259, 700},
            {0, 0x28, 924, 119, 259, 700}
    };

    public static final Sonic1YmServiceTimingProfile PROFILE =
            new Sonic1YmServiceTimingProfile();

}
