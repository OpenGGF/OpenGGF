package com.openggf.game.ghost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostFileCodec {
    private static GhostRecording sample() {
        byte[] hash = new byte[32];
        for (int i = 0; i < 32; i++) hash[i] = (byte) i;
        GhostHeader h = new GhostHeader(GhostFileCodec.FORMAT_VERSION, "s3k", 0, 0, "sonic",
                "Farrell", 12, 3612, new int[] {900, 2400}, hash);
        byte[] frames = new byte[3 * GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(new GhostFrame(100, 200, 1, false, false, false, 2, false), frames, 0);
        GhostFrameCodec.encode(new GhostFrame(110, 200, 2, true, false, false, 2, false), frames, 7);
        GhostFrameCodec.encode(new GhostFrame(120, 200, 3, true, false, true, 2, true), frames, 14);
        return new GhostRecording(h, frames);
    }

    @Test
    void roundTripsHeaderAndFrames(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("best.ggfghost");
        GhostRecording original = sample();
        GhostFileCodec.write(original, p);
        GhostRecording back = GhostFileCodec.read(p);
        assertEquals(original.header(), back.header());
        assertEquals(3, back.frameCount());
        assertEquals(original.frameAt(2), back.frameAt(2));
        assertEquals(3600, back.header().finalTimeFrames());
    }

    @Test
    void frameAtClampsToLastFrame(@TempDir Path dir) {
        GhostRecording r = sample();
        assertEquals(r.frameAt(2), r.frameAt(99)); // playback holds final pose
    }

    @Test
    void rejectsBadMagic(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("junk.ggfghost");
        Files.write(p, "NOTAGHOSTFILE----".getBytes());
        IOException ex = assertThrows(IOException.class, () -> GhostFileCodec.read(p));
        assertTrue(ex.getMessage().contains("not a .ggfghost"));
    }

    @Test
    void rejectsUnsupportedVersion(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("v99.ggfghost");
        GhostFileCodec.write(sample(), p);
        byte[] bytes = Files.readAllBytes(p);
        bytes[9] = 99; // version u16 low byte sits right after the 8-byte magic
        Files.write(p, bytes);
        IOException ex = assertThrows(IOException.class, () -> GhostFileCodec.read(p));
        assertTrue(ex.getMessage().contains("format version"));
    }

    @Test
    void rejectsHostileFrameCountWithoutAllocating(@TempDir Path dir) throws IOException {
        // Hand-craft a header claiming Integer.MAX_VALUE frames with no frame bytes.
        Path p = dir.resolve("hostile.ggfghost");
        try (var out = new java.io.DataOutputStream(Files.newOutputStream(p))) {
            out.write("GGFGHOST".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.writeShort(GhostFileCodec.FORMAT_VERSION);
            out.writeUTF("s3k"); out.writeByte(0); out.writeByte(0);
            out.writeUTF("sonic"); out.writeUTF("x");
            out.writeInt(0); out.writeInt(1);
            out.writeByte(0);                 // no splits
            out.writeByte(32); out.write(new byte[32]);
            out.writeInt(Integer.MAX_VALUE);  // hostile frame count
        }
        IOException ex = assertThrows(IOException.class, () -> GhostFileCodec.read(p));
        assertTrue(ex.getMessage().contains("frame count"));
    }

    @Test
    void headerAndRecordingAreDefensivelyCopied() {
        int[] splits = {900};
        byte[] hash = new byte[32];
        GhostHeader h = new GhostHeader(1, "s3k", 0, 0, "sonic", "x", 0, 1000, splits, hash);
        splits[0] = 7; hash[0] = 7;                       // mutate sources
        assertEquals(900, h.splitFrames()[0]);
        assertEquals(0, h.inputRecordingHash()[0]);
        h.splitFrames()[0] = 5;                           // mutate returned copy
        assertEquals(900, h.splitFrames()[0]);

        byte[] frames = new byte[GhostFrameCodec.BYTES];
        GhostRecording r = new GhostRecording(h, frames);
        frames[0] = 0x7F;
        assertEquals(0, r.frameAt(0).x());
        r.frameData()[0] = 0x7F;
        assertEquals(0, r.frameAt(0).x());
    }

    @Test
    void writeRejectsOverCapRecording(@TempDir Path dir) {
        byte[] frames = new byte[(GhostFileCodec.MAX_FRAMES + 1) * GhostFrameCodec.BYTES];
        GhostHeader h = new GhostHeader(1, "s3k", 0, 0, "sonic", "x", 0, 1, new int[0], new byte[32]);
        GhostRecording overCap = new GhostRecording(h, frames);
        IOException ex = assertThrows(IOException.class,
                () -> GhostFileCodec.write(overCap, dir.resolve("big.ggfghost")));
        assertTrue(ex.getMessage().contains("MAX_FRAMES"));
    }

    @Test
    void rejectsEmptyFrameData() {
        GhostHeader h = new GhostHeader(1, "s3k", 0, 0, "sonic", "x", 0, 1, new int[0], new byte[32]);
        assertThrows(IllegalArgumentException.class, () -> new GhostRecording(h, new byte[0]));
    }
}
