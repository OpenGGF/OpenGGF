package com.openggf.tools.audio;

import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPhysicalChipCapture {
    @TempDir
    Path directory;

    @Test
    void boundedCapturePreservesNativeDomainsAndReportsOverflowWithoutThrowing() throws Exception {
        PhysicalChipCapture capture = new PhysicalChipCapture(2);

        capture.onYm2612BusWrite(7, 0, 0x22,
                ChipWriteObserver.PhysicalWriteOrigin.EXTERNAL_BUS);
        capture.onPsgBusWrite(11, 0x90);
        capture.onPhysicalTimelineBoundary(
                ChipWriteObserver.ChipClockDomain.YM2612_INTERNAL_CYCLE, 8,
                ChipWriteObserver.PhysicalTimelineBoundary.MODEL_MUTATION);

        assertEquals(2, capture.size());
        assertTrue(capture.overflowed());
        assertEquals(1, capture.dropped());
        Path output = directory.resolve("capture.txt");
        capture.write(output, "s1", "sfx", 0xA0, 44_100,
                "/rom/s1.gen", "012345", new BuildIdentity("test", "abc", false));
        String text = Files.readString(output);
        assertTrue(text.contains("\"ym_ticks_per_second\":"));
        assertTrue(text.contains("\"rom_sha1\":\"012345\""));
        assertEquals(3, text.lines().count(),
                "JSONL has one header and one line per retained event");
        assertTrue(text.contains("\"psg_ticks_per_second\":"));
        assertTrue(text.contains("\"overflow\":true,\"dropped\":1"));
        assertTrue(text.contains("\"type\":\"ym\",\"ordinal\":0,\"cycle\":7"));
        assertTrue(text.contains("\"type\":\"psg\",\"ordinal\":1,\"tick\":11"));
    }

    @Test
    void exportPropagatesWriterFailures() {
        PhysicalChipCapture capture = new PhysicalChipCapture(1);

        assertThrows(java.io.IOException.class, () -> capture.write(directory,
                "s1", "sfx", 0xA0, 44_100, "/rom/s1.gen", "012345",
                new BuildIdentity("test", "abc", false)));
    }

    @Test
    void exportEscapesControlCharactersInProvenance() throws Exception {
        PhysicalChipCapture capture = new PhysicalChipCapture(1);
        Path output = directory.resolve("escaped.jsonl");

        capture.write(output, "s1", "sfx", 0xA0, 44_100,
                "/rom/line\n\t.gen", "012345",
                new BuildIdentity("test", "abc", false));

        String header = Files.readAllLines(output).getFirst();
        assertTrue(header.contains("/rom/line\\n\\t.gen"));
    }
}
