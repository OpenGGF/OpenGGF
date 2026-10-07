package com.openggf.tools.challenge;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestChallengeProtocol {
    @Test
    void collectedTupleCannotPublishAfterConcurrentClose(@TempDir Path tmp) {
        var host = new ChallengeHost(List.of(new ChallengeHost.Member("one", "s1", tmp)), 7);
        var collected = new ChallengeProtocol.Frame(
                7, 1, new byte[ChallengeProtocol.RGBA_BYTES], new short[0], "LEVEL", 1, 2, 0, 12, 4, 8);
        host.close();
        assertThrows(IllegalStateException.class,
                () -> host.commitTuple(List.of(collected), ChallengeHost.Commit.TICK));
        assertEquals(0, host.tick());
        assertTrue(host.committed().isEmpty());
    }
    @Test
    void teardownFailureStillReleasesEveryRemainingOwner() {
        java.util.List<String> closed = new java.util.ArrayList<>();
        IOException original = new IOException("worker cleanup_pending");
        Throwable result = ChallengeCleanup.closeAll(original,
                ()
                        -> {
                    closed.add("runner");
                    throw new IOException("runner");
                },
                ()
                        -> closed.add("menu"),
                ()
                        -> {
                    closed.add("capture");
                    throw new IOException("capture");
                },
                () -> closed.add("sink"), () -> closed.add("gpu"), () -> closed.add("window"));
        assertSame(original, result);
        assertEquals(2, result.getSuppressed().length);
        assertEquals(java.util.List.of("runner", "menu", "capture", "sink", "gpu", "window"), closed);
    }
    @Test
    void exactBinaryMediaRoundTripAndTruncation() throws Exception {
        byte[] rgba = new byte[ChallengeProtocol.RGBA_BYTES];
        rgba[17] = (byte) 234;
        short[] pcm = {-32768, 32767, 12, -98};
        var source = new ChallengeProtocol.Frame(8, 42, rgba, pcm, "TITLE_CARD", 47, 82, 3, 129, 0, 255);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ChallengeProtocol.writeFrame(new DataOutputStream(bytes), source);
        var copy = ChallengeProtocol.readFrame(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertArrayEquals(rgba, copy.rgba());
        assertArrayEquals(pcm, copy.pcm());
        assertEquals(42, copy.sequence());
        assertEquals("TITLE_CARD", copy.mode());
        assertEquals(255, copy.polledMask());
        assertThrows(IOException.class,
                ()
                        -> ChallengeProtocol.readFrame(new DataInputStream(new ByteArrayInputStream(
                                java.util.Arrays.copyOf(bytes.toByteArray(), bytes.size() - 1)))));
    }
    @Test
    void versionGenerationSequenceAndInputAreEnforced() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new ChallengeProtocol.Command(ChallengeProtocol.STEP, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ChallengeProtocol.Command(ChallengeProtocol.STEP, 1, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ChallengeProtocol.Command(ChallengeProtocol.START, 1, 0, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new ChallengeProtocol.Command(ChallengeProtocol.STEP, 1, 1, 256));
        var frame = new ChallengeProtocol.Frame(
                1, 8, new byte[ChallengeProtocol.RGBA_BYTES], new short[0], "LEVEL", 0, 0, 0, 0, 0, 0);
        assertThrows(IOException.class, () -> ProcessGameEndpoint.requireIdentity(frame, 2, 8));
        assertThrows(IOException.class, () -> ProcessGameEndpoint.requireIdentity(frame, 1, 9));
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ChallengeProtocol.writeCommand(
                new DataOutputStream(b), new ChallengeProtocol.Command(ChallengeProtocol.STEP, 1, 8, 255));
        byte[] malformed = b.toByteArray();
        malformed[7] = 2;
        assertThrows(IOException.class,
                ()
                        -> ChallengeProtocol.readCommand(
                                new DataInputStream(new ByteArrayInputStream(malformed))));
    }
    @Test
    void hostileLengthsRejectedBeforeAllocation() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        out.writeInt(0x47474643);
        out.writeInt(1);
        out.writeByte(10);
        out.writeLong(1);
        out.writeLong(0);
        out.write(new byte[ChallengeProtocol.RGBA_BYTES]);
        out.writeInt(Integer.MAX_VALUE);
        assertThrows(IOException.class,
                ()
                        -> ChallengeProtocol.readFrame(
                                new DataInputStream(new ByteArrayInputStream(b.toByteArray()))));
    }
    @Test
    void allRomsValidatedBeforeLaunchingAnyWorker(@TempDir Path tmp) {
        try (var host = new ChallengeHost(
                     List.of(new ChallengeHost.Member("one", "s1", tmp.resolve("missing.gen"))), 1)) {
            assertThrows(IOException.class, host::prepare);
            assertTrue(host.pids().isEmpty());
        }
        assertThrows(IllegalArgumentException.class,
                ()
                        -> new ChallengeHost(List.of(new ChallengeHost.Member("one", "s1", tmp),
                                                     new ChallengeHost.Member("one", "s2", tmp)),
                                1));
    }
    @Test
    void maintainedInputProgramHasRealBounds(@TempDir Path tmp) throws Exception {
        Path input = tmp.resolve("common.pad");
        Files.writeString(input, "# common\n2 NEUTRAL\n3 RIGHT+A+C\n");
        var p = ChallengeInputProgram.read(input);
        assertEquals(5, p.length());
        assertEquals(0, p.heldAt(1));
        assertEquals(104, p.heldAt(2));
        assertThrows(IndexOutOfBoundsException.class, () -> p.heldAt(5));
        for (String invalid : List.of("0 RIGHT", "1 LEFT+RIGHT", "1 JUMP", "36001 C", "1 NEUTRAL 2", "1 C+")) {
            Files.writeString(input, invalid);
            assertThrows(IOException.class, () -> ChallengeInputProgram.read(input));
        }
    }
}
