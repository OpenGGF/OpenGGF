package com.openggf.game.sonic3k.timing;

import com.openggf.data.Rom;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.sonic3k.timing.S3kKosServiceModel.Outcome;
import com.openggf.game.sonic3k.timing.S3kKosServiceModel.Result;
import com.openggf.game.sonic3k.timing.S3kKosServiceModel.State;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openggf.game.sonic3k.timing.S3kKosServiceModel.BoundaryVInt.CALLS_SET_KOS_BOOKMARK;
import static com.openggf.game.sonic3k.timing.S3kKosServiceModel.BoundaryVInt.NO_KOS_BOOKMARK;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ROM-backed checks of the pure {@link S3kKosServiceModel}. Synthetic streams carry the shipped routine bytes copied
 * from the original ROM into a small image; their expected output is written out by hand from the Kosinski format.
 * Real ROM jobs are checked against the engine's independent {@link KosinskiReader}. The native-window fixture is
 * comparison-only data (budgets and observations from retained GPGX captures) and never runtime input.
 */
class TestS3kKosServiceModel {
    private static final String S3K_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6";
    private static final byte[] END = {0x00, (byte) 0xF0, 0x00};
    private static final int STREAM = 0x2000;
    private static final int DEST = 0xFFFF0000;
    /** Ending FIFO jobs queued together on every captured D01 product (native windows.csv count 3, this order). */
    private static final int[][] ENDING_JOBS = {
            {0x1D17A6, 0xFFFF0000}, {0x1CF2D4, 0xFFFF9000}, {0x1CFB46, 0xFFFFD000}};
    /** Resumed call: tst.w 12 + beq.w not taken 12 + bmi.w taken 10 + Restore_Kos_Bookmark 96+24+16+20. */
    private static final int RESUME_CALL_CYCLES = 12 + 12 + 10 + 96 + 24 + 16 + 20;

    private static byte[] rom;

    @BeforeAll
    static void loadRom() throws IOException, NoSuchAlgorithmException {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(file != null, "S3K ROM not available (set -Ds3k.rom.path)");
        try (Rom r = new Rom()) {
            assertTrue(r.open(file.getAbsolutePath()));
            rom = r.readAllBytes();
        }
        String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(rom));
        assertEquals(S3K_SHA1, sha1, "Kos service pins and native windows are for the original locked-on ROM");
    }

    // ---- synthetic streams (expected bytes written out from the format)

    private static byte[] image(byte[]... parts) {
        byte[] img = new byte[0x4000];
        System.arraycopy(rom, 0x1C1A, img, 0x1C1A, 0x1D18 - 0x1C1A);
        int at = STREAM;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, img, at, p.length);
            at += p.length;
        }
        return img;
    }

    /** One little-endian descriptor word; the first flag is bit 0. */
    private static byte[] flags(int... bits) {
        int v = 0;
        for (int k = 0; k < bits.length; k++) {
            v |= bits[k] << k;
        }
        return new byte[] {(byte) v, (byte) (v >> 8)};
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int k = 0; k < values.length; k++) {
            out[k] = (byte) values[k];
        }
        return out;
    }

    private static State queued(int[]... jobs) {
        State s = new State();
        for (int[] job : jobs) {
            s.queueKos(job[0], job[1]);
        }
        return s;
    }

    private static void assertDecodes(byte[] img, String expected) throws IOException {
        State s = queued(new int[] {STREAM, DEST});
        Result r = new S3kKosServiceModel(img).serve(s, Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK);
        assertEquals(Outcome.RETURNED, r.outcome());
        assertTrue(r.retired());
        assertEquals(expected.length(), r.bytesOut());
        byte[] want = Arrays.copyOf(ascii(expected), expected.length() + 4);
        assertArrayEquals(want, s.ramRange(DEST, expected.length() + 4), "model output and the zero tail");
        assertArrayEquals(ascii(expected), reference(img, STREAM), "engine KosinskiReader agrees on the vector");
    }

    @Test
    void literalsThenEndMarker() throws IOException {
        // 1,1,1 literals; 0,1 separate copy 00 F0 -> count bits 0 -> count byte 00 = end of stream
        assertDecodes(image(flags(1, 1, 1, 0, 1), ascii("ABC"), END), "ABC");
    }

    @Test
    void inlineCopyTwoBitCount() throws IOException {
        // 1,1 literals; 0,0 inline copy, count bits 1,0 -> 2+2 = 4 bytes from offset byte FE (-2); 0,1 end
        assertDecodes(image(flags(1, 1, 0, 0, 1, 0, 0, 1), ascii("AB"), bytes(0xFE), END), "ABABAB");
    }

    @Test
    void separateCopyThreeBitCount() throws IOException {
        // 0,1 separate copy: FD FA -> 13-bit offset ((FA & F8) << 5 | FD) = -3, count (FA & 7) = 2 -> 2+2 = 4 bytes
        assertDecodes(image(flags(1, 1, 1, 0, 1, 0, 1), ascii("XYZ"), bytes(0xFD, 0xFA), END), "XYZXYZX");
    }

    @Test
    void extendedCountAndNoOp() throws IOException {
        // separate copy offset -1 with count bits 0 and count byte 4 -> 5 bytes; then count byte 1 = no-op; then end
        assertDecodes(image(flags(1, 0, 1, 0, 1, 0, 1), ascii("Q"), bytes(0xFF, 0xF8, 0x04), bytes(0x00, 0xF0, 0x01),
                END), "QQQQQQ");
    }

    @Test
    void descriptorRefillPrecedesSixteenthFlagsByte() throws IOException {
        // dbf d4 expires on the 16th flag and the next descriptor is read before that flag's literal byte, so the
        // second descriptor sits after the 15th literal
        int[] ones = new int[16];
        Arrays.fill(ones, 1);
        assertDecodes(image(flags(ones), ascii("ABCDEFGHIJKLMNO"), flags(1, 0, 1), ascii("PQ"), END),
                "ABCDEFGHIJKLMNOPQ");
    }

    @Test
    void refusesChangedOperandEvenWhenEveryOpcodeStillMatches() {
        byte[] changed = rom.clone();
        changed[0x1C29] ^= 1; // ori.w #$8000: immediate operand, not the opcode word
        assertThrows(IllegalArgumentException.class, () -> new S3kKosServiceModel(changed));
    }

    @Test
    void refusesRomWhoseRoutineDiffersFromThePins() {
        byte[] img = image(END);
        img[0x1CB7] ^= 0x01;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new S3kKosServiceModel(img));
        assertTrue(e.getMessage().contains("001CB6"), e.getMessage());
    }

    // ---- real ROM jobs against the engine's independent decoder

    private static byte[] reference(byte[] image, int offset) throws IOException {
        return KosinskiReader.decompress(Channels.newChannel(
                new ByteArrayInputStream(image, offset, image.length - offset)));
    }

    @Test
    void endingJobsMatchKosinskiReaderAcrossManyBookmarkSplits() throws IOException {
        S3kKosServiceModel model = new S3kKosServiceModel(rom);
        for (int source : new int[] {0x1D17A6, 0x1CF2D4, 0x1CFB46, 0x1D02B6}) {
            byte[] expected = reference(rom, source);
            assertTrue(expected.length > 1000, "job is a real multi-window asset");
            State whole = queued(new int[] {source, DEST});
            Result once = model.serve(whole, Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK);
            assertEquals(expected.length, once.bytesOut());
            assertArrayEquals(expected, whole.ramRange(DEST, expected.length));
            for (long budget : new long[] {907, 2_311, 6_007, 11_113, 15_013}) {
                State s = queued(new int[] {source, DEST});
                int windows = 0;
                long cycles = 0;
                int bytes = 0;
                boolean retired = false;
                Result r;
                do {
                    r = model.serve(s, budget, CALLS_SET_KOS_BOOKMARK);
                    windows++;
                    cycles += r.cycles();
                    bytes += r.bytesOut();
                    retired |= r.retired();
                    if (r.outcome() == Outcome.INTERRUPTED_LIVE) {
                        // a boundary in the retiring tail: the same call completes after the V-int
                        r = model.finishInterruptedCall(s);
                        cycles += r.cycles();
                        bytes += r.bytesOut();
                        retired |= r.retired();
                    }
                } while (r.outcome() == Outcome.BOOKMARKED);
                assertEquals(Outcome.RETURNED, r.outcome());
                assertTrue(retired);
                assertEquals(0, s.queueCount());
                assertEquals(expected.length, bytes, "bytes over all windows");
                assertArrayEquals(expected, s.ramRange(DEST, expected.length), "budget " + budget);
                assertEquals(once.cycles() + (windows - 1L) * RESUME_CALL_CYCLES, cycles,
                        "each resumed window adds exactly its entry prefix plus Restore_Kos_Bookmark");
            }
        }
    }

    @Test
    void bookmarkCaptureAndTwoRestoreCyclesReplayExactly() {
        S3kKosServiceModel model = new S3kKosServiceModel(rom);
        State s = queued(ENDING_JOBS[0]);
        Result w1 = model.serve(s, 9_000, CALLS_SET_KOS_BOOKMARK);
        assertEquals(Outcome.BOOKMARKED, w1.outcome());
        assertEquals(0, w1.executions(S3kKosServiceModel.RESTORE_KOS_BOOKMARK));
        assertBookmarkSaved(s, w1);
        State capture1 = s.copy();

        Result w2 = model.serve(s, 9_000, CALLS_SET_KOS_BOOKMARK);
        assertEquals(Outcome.BOOKMARKED, w2.outcome());
        assertEquals(1, w2.executions(S3kKosServiceModel.RESTORE_KOS_BOOKMARK), "first restore cycle");
        assertEquals(1, w2.executions(0x1D0A), "rte into the bookmark");
        assertBookmarkSaved(s, w2);
        State capture2 = s.copy();

        Result w3 = model.serve(s, Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK);
        assertEquals(Outcome.RETURNED, w3.outcome());
        assertTrue(w3.retired());
        assertEquals(1, w3.executions(S3kKosServiceModel.RESTORE_KOS_BOOKMARK), "second restore cycle");

        // replay both restore cycles from the first capture, then the last from the second capture
        State replay = capture1.copy();
        assertEquals(capture1, replay);
        assertEquals(w2, model.serve(replay, 9_000, CALLS_SET_KOS_BOOKMARK));
        assertEquals(capture2, replay);
        assertEquals(w3, model.serve(replay, Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK));
        assertEquals(s, replay);
        State replay2 = capture2.copy();
        assertEquals(w3, model.serve(replay2, Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK));
        assertEquals(s, replay2);

        assertEquals(0, s.queueCount(), "job retired, busy bit clear");
        int total = w1.bytesOut() + w2.bytesOut() + w3.bytesOut();
        assertEquals(3_712, total, "706+1499+1459+48 bytes over the native Tails/+2 windows");
        assertEquals(124, w1.postVIntCycles(), "Backup_Kos_Registers move sr 16 + movem.l 92 + rts 16");
        assertEquals(0, w3.postVIntCycles());
    }

    private static void assertBookmarkSaved(State s, Result r) {
        assertTrue(r.stopPc() >= S3kKosServiceModel.PROCESS_KOS_QUEUE_MAIN
                && r.stopPc() < S3kKosServiceModel.PROCESS_KOS_QUEUE_DONE);
        assertEquals(r.stopPc(), readLong(s, 0xFF3A), "Kos_decomp_bookmark holds the stacked return PC");
        assertEquals(0x2300 | s.conditionCodes(), readWord(s, 0xFF38), "Kos_decomp_stored_SR");
        for (int k = 0; k < 7; k++) {
            assertEquals(s.dataRegister(k), readLong(s, 0xFF10 + 4 * k), "stored d" + k);
        }
        for (int k = 0; k < 3; k++) {
            assertEquals(s.addressRegister(k), readLong(s, 0xFF2C + 4 * k), "stored a" + k);
        }
        assertEquals(0x8000, s.queueCount() & 0x8000, "busy bit stays set across the bookmark");
        assertEquals(-1, s.livePc());
    }

    private static int readWord(State s, int address) {
        return s.ramByte(address) << 8 | s.ramByte(address + 1);
    }

    private static int readLong(State s, int address) {
        return readWord(s, address) << 16 | readWord(s, address + 2);
    }

    @Test
    void interruptionStopsAtTheFirstBoundaryAtOrAfterTheBudget() {
        S3kKosServiceModel model = new S3kKosServiceModel(rom);
        Result whole = model.serve(queued(ENDING_JOBS[0]), Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK);
        int bookmarked = 0;
        for (long budget = 1; budget < 4_000; budget += 1) {
            Result r = model.serve(queued(ENDING_JOBS[0]), budget, CALLS_SET_KOS_BOOKMARK);
            assertTrue(r.cycles() >= budget, "no early stop");
            assertTrue(r.cycles() - r.lastInstructionCycles() < budget, "no late stop at budget " + budget);
            assertTrue(r.cycles() < whole.cycles());
            if (r.outcome() == Outcome.BOOKMARKED) {
                bookmarked++;
            } else {
                assertEquals(Outcome.INTERRUPTED_LIVE, r.outcome());
                // before ori.w #$8000 the busy bit is clear, so Set_Kos_Bookmark's bpl declines even at Main
                assertTrue(r.stopPc() <= S3kKosServiceModel.PROCESS_KOS_QUEUE_MAIN, "only the entry prefix is live");
            }
        }
        assertTrue(bookmarked > 3_900);
    }

    @Test
    void retiringWindowStartsNoSuccessor() throws IOException {
        S3kKosServiceModel model = new S3kKosServiceModel(rom);
        State s = queued(ENDING_JOBS);
        byte[] first = reference(rom, ENDING_JOBS[0][0]);
        Result r = model.serve(s, Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK);
        assertEquals(Outcome.RETURNED, r.outcome());
        assertTrue(r.retired());
        assertEquals(first.length, r.bytesOut());
        assertEquals(2, s.queueCount(), "subq.w #1 leaves two jobs, busy bit clear");
        assertArrayEquals(new byte[64], s.ramRange(ENDING_JOBS[1][1], 64), "successor did not start");
        assertEquals(ENDING_JOBS[1][0], readLong(s, 0xFF40), "queue shifted up");
        assertEquals(ENDING_JOBS[2][0], readLong(s, 0xFF48));
        assertEquals(6, r.executions(0x1CEE) + r.executions(0x1CF0) + r.executions(0x1CF2)
                + r.executions(0x1CF4) + r.executions(0x1CF6) + r.executions(0x1CF8));
        Result next = model.serve(s, Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK);
        assertTrue(next.retired());
        assertArrayEquals(reference(rom, ENDING_JOBS[1][0]), s.ramRange(ENDING_JOBS[1][1], next.bytesOut()));
    }

    @Test
    void nonBookmarkingVIntLeavesTheCallRunningToItsRts() throws IOException {
        // VInt_4 (Wait_TitleS3K) returns without Set_Kos_Bookmark: the same call keeps decompressing after the V-int
        S3kKosServiceModel model = new S3kKosServiceModel(rom);
        byte[] expected = reference(rom, ENDING_JOBS[0][0]);
        Result whole = model.serve(queued(ENDING_JOBS[0]), Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK);
        State s = queued(ENDING_JOBS[0]);
        Result first = model.serve(s, 9_000, NO_KOS_BOOKMARK);
        assertEquals(Outcome.INTERRUPTED_LIVE, first.outcome());
        assertEquals(first.stopPc(), s.livePc());
        assertEquals(0, readLong(s, 0xFF3A), "no bookmark written");
        assertThrows(IllegalStateException.class, () -> model.serve(s, Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK));
        Result rest = model.finishInterruptedCall(s);
        assertEquals(Outcome.RETURNED, rest.outcome());
        assertTrue(rest.retired());
        assertEquals(whole.cycles(), first.cycles() + rest.cycles(), "one call: no second entry, no restore");
        assertEquals(expected.length, first.bytesOut() + rest.bytesOut());
        assertArrayEquals(expected, s.ramRange(DEST, expected.length));
        assertThrows(IllegalStateException.class, () -> model.finishInterruptedCall(s));
    }

    @Test
    void boundariesOutsideTheBookmarkRangeAreLive() throws IOException {
        S3kKosServiceModel model = new S3kKosServiceModel(rom);
        Result whole = model.serve(queued(ENDING_JOBS[0]), Long.MAX_VALUE, CALLS_SET_KOS_BOOKMARK);

        // budget already reached at the call entry: the stacked PC is Process_Kos_Queue itself
        State atEntry = queued(ENDING_JOBS[0]);
        Result entry = model.serve(atEntry, 0, CALLS_SET_KOS_BOOKMARK);
        assertEquals(Outcome.INTERRUPTED_LIVE, entry.outcome());
        assertEquals(S3kKosServiceModel.PROCESS_KOS_QUEUE, entry.stopPc());
        assertEquals(0, entry.cycles());

        // after beq.w at $1C1E: still before Process_Kos_Queue_Main, so the whole job finishes in this call
        State early = queued(ENDING_JOBS[0]);
        Result prefix = model.serve(early, 24, CALLS_SET_KOS_BOOKMARK);
        assertEquals(Outcome.INTERRUPTED_LIVE, prefix.outcome());
        assertEquals(0x1C22, prefix.stopPc());
        Result rest = model.finishInterruptedCall(early);
        assertTrue(rest.retired());
        assertEquals(whole.cycles(), prefix.cycles() + rest.cycles());
        assertArrayEquals(reference(rom, ENDING_JOBS[0][0]), early.ramRange(DEST, rest.bytesOut()));

        // at Process_Kos_Queue_Done after the busy bit was cleared: retired, only the rts remains
        State tail = queued(ENDING_JOBS[0]);
        Result retiring = model.serve(tail, whole.cycles() - 16, CALLS_SET_KOS_BOOKMARK);
        assertEquals(Outcome.INTERRUPTED_LIVE, retiring.outcome());
        assertEquals(S3kKosServiceModel.PROCESS_KOS_QUEUE_DONE, retiring.stopPc());
        assertTrue(retiring.retired());
        assertEquals(0, tail.queueCount());
        Result rts = model.finishInterruptedCall(tail);
        assertEquals(16, rts.cycles());
        assertFalse(rts.retired());

        // busy bit clear inside the range ($1CE0, after andi.w #$7FFF): Set_Kos_Bookmark's bpl declines
        State cleared = queued(ENDING_JOBS[0]);
        Result afterAndi = model.serve(cleared, whole.cycles() - 16 - 10 - 16, CALLS_SET_KOS_BOOKMARK);
        assertEquals(0x1CE0, afterAndi.stopPc());
        assertEquals(Outcome.INTERRUPTED_LIVE, afterAndi.outcome());
    }

    // ---- native comparison windows (comparison-only fixture)

    private record NativeWindow(String product, int levelFrame, int countAtEntry, int source, int destination,
                                long budget, Outcome outcome, int bytesOut, int stopPc, Map<Integer, Integer> counts) {
    }

    private static List<NativeWindow> nativeWindows() throws IOException {
        List<NativeWindow> rows = new ArrayList<>();
        try (InputStream in = TestS3kKosServiceModel.class.getResourceAsStream("/timing/s3k-kos-native-windows-v1.tsv")) {
            assertNotNull(in, "fixture resource");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] f = line.split("\t");
                Map<Integer, Integer> counts = new LinkedHashMap<>();
                for (String entry : f[9].split(",")) {
                    String[] kv = entry.split(":");
                    counts.put(Integer.parseInt(kv[0], 16), Integer.parseInt(kv[1]));
                }
                rows.add(new NativeWindow(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2], 16),
                        Integer.parseInt(f[3], 16), Integer.parseUnsignedInt(f[4], 16), Long.parseLong(f[5]),
                        Outcome.valueOf(f[6]), Integer.parseInt(f[7]), "-".equals(f[8]) ? -1 : Integer.parseInt(f[8], 16),
                        counts));
            }
        }
        return rows;
    }

    @Test
    void reproducesEveryRetainedNativeServiceWindow() throws IOException {
        S3kKosServiceModel model = new S3kKosServiceModel(rom);
        List<NativeWindow> rows = nativeWindows();
        assertEquals(63, rows.size());
        Map<String, State> states = new LinkedHashMap<>();
        for (NativeWindow w : rows) {
            State s = states.computeIfAbsent(w.product(), p -> queued(ENDING_JOBS));
            String where = w.product() + " LF " + w.levelFrame();
            assertEquals(w.countAtEntry(), s.queueCount(), where + " Kos_decomp_queue_count at entry");
            int[] head = ENDING_JOBS[3 - (w.countAtEntry() & 0x7FFF)];
            assertEquals(head[0], w.source(), where + " FIFO head source");
            assertEquals(head[1], w.destination(), where + " FIFO head destination");
            Result r = model.serve(s, w.budget(), CALLS_SET_KOS_BOOKMARK);
            assertEquals(w.outcome(), r.outcome(), where);
            assertEquals(w.bytesOut(), r.bytesOut(), where + " bytes");
            assertEquals(w.stopPc(), r.stopPc(), where + " interrupted PC");
            assertEquals(w.budget(), r.cycles(), where + " Java cost table against the GPGX-costed native counts");
            for (int[] row : S3kKosServiceModel.COSTS) {
                if (row[0] < S3kKosServiceModel.BACKUP_KOS_REGISTERS) {
                    assertEquals(w.counts().getOrDefault(row[0], 0).intValue(), r.executions(row[0]),
                            where + String.format(" executions at %04X", row[0]));
                }
            }
            assertEquals(Map.of(), unknownPcs(w.counts()), where);
        }
        assertEquals(6, states.size());
        for (State s : states.values()) {
            assertEquals(0, s.queueCount(), "all three FIFO jobs retired on every product");
        }
    }

    private static Map<Integer, Integer> unknownPcs(Map<Integer, Integer> counts) {
        Map<Integer, Integer> unknown = new LinkedHashMap<>(counts);
        for (int[] row : S3kKosServiceModel.COSTS) {
            unknown.remove(row[0]);
        }
        return unknown;
    }

    @Test
    void backupCostConstantMatchesItsPinnedRows() {
        int sum = 0;
        for (int[] row : S3kKosServiceModel.COSTS) {
            if (row[0] >= S3kKosServiceModel.BACKUP_KOS_REGISTERS) {
                sum += row[2];
            }
        }
        assertEquals(S3kKosServiceModel.BACKUP_KOS_REGISTERS_CYCLES, sum);
    }
}
