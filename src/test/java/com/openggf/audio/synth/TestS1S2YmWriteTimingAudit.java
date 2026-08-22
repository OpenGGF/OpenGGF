package com.openggf.audio.synth;

import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.YmServiceTimingProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable materiality check for the source-authenticated S1/S2 ring audit. */
class TestS1S2YmWriteTimingAudit {

    private static final String S1_RING =
            "0,1,177,4;2170,1,49,55;4340,1,57,114;6496,1,53,119;"
            + "8652,1,61,73;10808,1,81,31;12978,1,89,31;15134,1,85,31;"
            + "17290,1,93,31;19446,1,97,7;21616,1,105,10;"
            + "23772,1,101,7;25928,1,109,13;28084,1,113,0;"
            + "30254,1,121,11;32410,1,117,0;34566,1,125,11;"
            + "36722,1,129,31;38892,1,137,15;41048,1,133,31;"
            + "43204,1,141,15;45535,1,65,35;47831,1,73,133;"
            + "50127,1,69,35;52423,1,77,133;54635,1,181,192;"
            + "57575,1,181,64;59654,0,40,5;64666,1,165,43;"
            + "66997,1,161,45;69167,0,40,245";

    private static final String S2_RING =
            "0,1,177,4;3780,1,49,55;7650,1,53,119;11520,1,57,114;"
            + "15390,1,61,73;19560,1,81,31;23430,1,85,31;"
            + "27300,1,89,31;31170,1,93,31;35040,1,97,7;"
            + "38910,1,101,7;42780,1,105,10;46650,1,109,13;"
            + "50520,1,113,0;54390,1,117,0;58260,1,121,11;"
            + "62130,1,125,11;66000,1,129,31;69870,1,133,31;"
            + "73740,1,137,15;77610,1,141,15;81555,1,181,192;"
            + "87465,1,65,35;91635,1,69,35;96225,1,73,133;"
            + "100815,1,77,133;108795,1,181,64;113670,0,40,5;"
            + "127770,1,165,43;131265,1,161,45;135435,0,40,245";

    @Test
    void isolatedS1RingCrossesThePredeclaredAttenuationThreshold() {
        assertMaterial(parse(S1_RING), "S1 Ring/SndB5 FM5");
    }

    @Test
    void isolatedS2RingCrossesThePredeclaredAttenuationThreshold() {
        assertMaterial(parse(S2_RING), "S2 RingRight/Sound35 FM5");
    }

    private static void assertMaterial(List<AuditWrite> writes, String name) {
        long span = writes.getLast().cycle();
        assertTrue(span >= 4 * YmWriteTimeline.MASTER_CYCLES_PER_INTERNAL_SAMPLE,
                name + " must span at least four internal YM samples");
        Ym2612Chip.Snapshot seed = activeRingSeed(writes);
        int[] atomic = replay(seed, writes, true);
        int[] timed = replay(seed, writes, false);
        int maximumDifference = 0;
        for (int operator = 0; operator < 4; operator++) {
            maximumDifference = Math.max(maximumDifference,
                    Math.abs(atomic[operator] - timed[operator]));
        }
        assertTrue(maximumDifference >= 8,
                name + " collapse must change a key-on attenuation by >= 8; "
                        + "actual maximum=" + maximumDifference);
    }

    private static Ym2612Chip.Snapshot activeRingSeed(List<AuditWrite> writes) {
        Ym2612Chip chip = chip();
        for (AuditWrite write : writes) {
            chip.write(write.port(), write.register(), write.value());
        }
        chip.renderStereo(new int[64], new int[64]);
        return chip.captureSnapshot();
    }

    private static int[] replay(Ym2612Chip.Snapshot seed,
                                List<AuditWrite> writes, boolean atomic) {
        Ym2612Chip chip = chip();
        chip.restoreSnapshot(seed);
        YmWriteTimeline timeline = new YmWriteTimeline(writes.size());
        chip.setWriteTimeline(timeline);
        List<Integer> attenuation = new ArrayList<>();
        chip.setWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) { }

            @Override
            public void onYm2612KeyOn(int channel, int operator, int value) {
                if (channel == 4) {
                    attenuation.add(value);
                }
            }

            @Override
            public void onPsgWrite(int value) { }
        });
        List<YmWriteTimeline.Entry> entries = new ArrayList<>();
        for (int ordinal = 0; ordinal < writes.size(); ordinal++) {
            AuditWrite write = writes.get(ordinal);
            entries.add(new YmWriteTimeline.Entry(
                    atomic ? 0 : write.cycle(), ordinal,
                    write.port(), write.register(), write.value(), 0, 0,
                    new SmpsSourceDescriptor(SmpsSourceDescriptor.Kind.UNKNOWN,
                            -1, null, null, 0, 0, 0, false, 0),
                    YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD));
        }
        timeline.commit(entries);
        chip.renderStereo(new int[200], new int[200]);
        return attenuation.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Ym2612Chip chip() {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setChipType(2);
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        return chip;
    }

    private static List<AuditWrite> parse(String encoded) {
        List<AuditWrite> writes = new ArrayList<>();
        for (String row : encoded.split(";")) {
            String[] fields = row.split(",");
            writes.add(new AuditWrite(Long.parseLong(fields[0]),
                    Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                    Integer.parseInt(fields[3])));
        }
        return List.copyOf(writes);
    }

    private record AuditWrite(long cycle, int port, int register, int value) { }
}
