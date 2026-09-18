package com.openggf.tests.route;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-Java checks for the route program parser, BK2 encoder and cursor. */
class TestInputProgram {

    @Test
    void parseAcceptsWhitespaceAndNamesMalformedEntries() {
        assertEquals(List.of(new InputRun(12, 0x08), new InputRun(4, 0x00)),
                InputProgram.parse("12:8, 4 : 0"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> InputProgram.parse("12:8,4-0"));
        assertTrue(error.getMessage().startsWith("entry 1 "), error.getMessage());
        assertEquals(16, InputProgram.frames(InputProgram.parse("12:8,4:0")));
    }

    @Test
    void fromRecordingRunLengthEncodesTheWindowAndRoundTrips() {
        Bk2Movie movie = movie(0x00, 0x08, 0x08, 0x18, 0x08, 0x08, 0x00);
        List<InputRun> runs = InputProgram.fromRecording(movie, 1, 6);
        assertEquals(List.of(new InputRun(2, 0x08), new InputRun(1, 0x18), new InputRun(2, 0x08)), runs);
        assertEquals("2:8,1:18,2:8", InputProgram.format(runs));
        assertEquals(runs, InputProgram.parse(InputProgram.format(runs)));
        assertEquals(List.of(), InputProgram.fromRecording(movie, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> InputProgram.fromRecording(movie, 0, 8));
    }

    @Test
    void cursorReportsRowsAndSeeksAcrossRunBoundaries() {
        InputProgram.Cursor cursor = new InputProgram.Cursor(InputProgram.parse("2:8,3:4,1:10"), 100);
        assertEquals(100, cursor.row());
        assertEquals(0x08, cursor.next());
        assertEquals(0x08, cursor.next());
        assertEquals(0x04, cursor.next());
        assertEquals(103, cursor.row());
        cursor.seekToRow(104);
        assertEquals(0x04, cursor.next());
        assertEquals(0x10, cursor.next());
        assertTrue(cursor.exhausted());
        assertEquals(0, cursor.next());
        assertEquals(107, cursor.row());
        cursor.seekToRow(100);
        assertFalse(cursor.exhausted());
        assertEquals(0x08, cursor.next());
        assertThrows(IllegalArgumentException.class, () -> cursor.seekToRow(99));
    }

    private static Bk2Movie movie(int... masks) {
        List<Bk2FrameInput> frames = new java.util.ArrayList<>();
        for (int index = 0; index < masks.length; index++) {
            frames.add(new Bk2FrameInput(index, masks[index], 0, false, "|..|"));
        }
        return new Bk2Movie(Path.of("synthetic.bk2"), "test", Map.of(), frames, 0);
    }
}
