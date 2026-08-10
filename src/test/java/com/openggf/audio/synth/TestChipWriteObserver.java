package com.openggf.audio.synth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestChipWriteObserver {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void directChipWritesReportResolvedUnsignedValuesExactlyOnce() {
        RecordingObserver observer = new RecordingObserver();
        Ym2612Chip ym = new Ym2612Chip();
        PsgChip psg = new PsgChip();
        ym.setWriteObserver(observer);
        psg.setWriteObserver(observer);

        ym.write(0, 0x1B4, 0x1C7);
        psg.write(0x19F);

        assertEquals(List.of("YM:1:B4:C7", "PSG:9F"), observer.events);
    }

    @Test
    void removingSynthObserverDisablesBothChipStreams() {
        RecordingObserver observer = new RecordingObserver();
        VirtualSynthesizer synth = new VirtualSynthesizer();
        synth.setChipWriteObserver(observer);
        synth.writeFm(this, 0, 0x22, 0x08);
        synth.writePsg(this, 0x90);
        assertEquals(List.of("YM:0:22:08", "PSG:90"), observer.events);

        observer.events.clear();
        synth.setChipWriteObserver(null);
        synth.writeFm(this, 1, 0xA4, 0x21);
        synth.writePsg(this, 0xBF);

        assertEquals(List.of(), observer.events);
    }

    @Test
    void setInstrumentReportsExistingKeyOffB0AndOperatorExpansionOrder() {
        RecordingObserver observer = new RecordingObserver();
        Ym2612Chip ym = new Ym2612Chip();
        ym.setWriteObserver(observer);

        ym.setInstrument(4, new byte[] {
                0x2D,
                0x01, 0x02, 0x03, 0x04,
                0x05, 0x06, 0x07, 0x08,
                0x09, 0x0A, 0x0B, 0x0C,
                0x0D, 0x0E, 0x0F, 0x10,
                0x11, 0x12, 0x13, 0x14,
                0x15, 0x16, 0x17, 0x18
        });

        assertEquals(List.of(
                "YM:0:28:05", "YM:1:B1:2D",
                "YM:1:31:01", "YM:1:41:15", "YM:1:51:05", "YM:1:61:09", "YM:1:71:0D", "YM:1:81:11", "YM:1:91:00",
                "YM:1:35:03", "YM:1:45:17", "YM:1:55:07", "YM:1:65:0B", "YM:1:75:0F", "YM:1:85:13", "YM:1:95:00",
                "YM:1:39:02", "YM:1:49:16", "YM:1:59:06", "YM:1:69:0A", "YM:1:79:0E", "YM:1:89:12", "YM:1:99:00",
                "YM:1:3D:04", "YM:1:4D:18", "YM:1:5D:08", "YM:1:6D:0C", "YM:1:7D:10", "YM:1:8D:14", "YM:1:9D:00"
        ), observer.events);
    }

    @Test
    void silenceAllReportsEveryYmAndPsgWriteInProductionOrder() {
        RecordingObserver observer = new RecordingObserver();
        VirtualSynthesizer synth = new VirtualSynthesizer();
        synth.setChipWriteObserver(observer);

        synth.silenceAll();

        List<String> expected = new ArrayList<>();
        expected.addAll(List.of(
                "YM:0:28:00", "YM:0:28:04",
                "YM:0:28:01", "YM:0:28:05",
                "YM:0:28:02", "YM:0:28:06"));
        for (int register = 0x30; register < 0x90; register++) {
            expected.add("YM:0:%02X:FF".formatted(register));
            expected.add("YM:1:%02X:FF".formatted(register));
        }
        expected.addAll(List.of("PSG:9F", "PSG:BF", "PSG:DF", "PSG:FF"));
        assertEquals(expected, observer.events);
    }

    @Test
    void observationLeavesSnapshotsAndFutureOutputBitExact() {
        VirtualSynthesizer unobserved = new VirtualSynthesizer();
        VirtualSynthesizer observed = new VirtualSynthesizer();
        observed.setChipWriteObserver(new RecordingObserver());

        exercise(unobserved);
        exercise(observed);

        assertEquals(
                JSON.valueToTree(unobserved.captureSynthSnapshot()),
                JSON.valueToTree(observed.captureSynthSnapshot()));

        short[] expected = new short[256];
        short[] actual = new short[256];
        unobserved.render(expected);
        observed.render(actual);
        assertArrayEquals(expected, actual);
    }

    private static void exercise(VirtualSynthesizer synth) {
        synth.writeFm(synth, 0, 0x22, 0x0B);
        synth.writeFm(synth, 1, 0x1B4, 0xC7);
        synth.writePsg(synth, 0x84);
        synth.writePsg(synth, 0x12);
        synth.writePsg(synth, 0x92);
    }

    private static final class RecordingObserver implements ChipWriteObserver {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onYm2612Write(int port, int register, int value) {
            events.add("YM:%d:%02X:%02X".formatted(port, register, value));
        }

        @Override
        public void onPsgWrite(int value) {
            events.add("PSG:%02X".formatted(value));
        }
    }
}
