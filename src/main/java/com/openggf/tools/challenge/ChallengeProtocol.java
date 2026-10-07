package com.openggf.tools.challenge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounded, versioned local-process transport. Gameplay authority remains in the worker. */
public final class ChallengeProtocol {
    public static final int VERSION = 1;
    public static final int WIDTH = 320, HEIGHT = 224, RATE = 48_000;
    public static final int RGBA_BYTES = WIDTH * HEIGHT * 4;
    public static final int MAX_PCM_SHORTS = 4096;
    public static final byte START = 1, STEP = 2, CLOSE = 3;
    private static final int MAGIC = 0x47474643;
    private static final byte FRAME = 10, ERROR = 11;
    private static final int MAX_TEXT = 1024;

    private ChallengeProtocol() { }

    public record Command(byte kind, long generation, long sequence, int heldMask) {
        public Command {
            if (kind != START && kind != STEP && kind != CLOSE) throw new IllegalArgumentException("Unknown command");
            identity(generation, sequence);
            if (heldMask < 0 || heldMask > 255) throw new IllegalArgumentException("Pad must be a Genesis byte");
            if (kind != STEP && heldMask != 0) throw new IllegalArgumentException("Only STEP carries input");
        }
    }

    /** Complete top-down RGBA plus final stereo PCM, before host focus gain. */
    public record Frame(long generation, long sequence, byte[] rgba, short[] pcm,
                        String mode, int x, int y, int rings, long vInt,
                        long levelFrame, int polledMask) {
        public Frame {
            identity(generation, sequence);
            Objects.requireNonNull(rgba); Objects.requireNonNull(pcm); Objects.requireNonNull(mode);
            if (rgba.length != RGBA_BYTES) throw new IllegalArgumentException("Wrong native framebuffer size");
            if (pcm.length > MAX_PCM_SHORTS || pcm.length % 2 != 0) throw new IllegalArgumentException("Invalid stereo PCM size");
            if (mode.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT) throw new IllegalArgumentException("Mode too long");
        }
    }

    public static void writeCommand(DataOutputStream out, Command command) throws IOException {
        header(out, command.kind(), command.generation(), command.sequence());
        out.writeByte(command.heldMask()); out.flush();
    }

    public static Command readCommand(DataInputStream in) throws IOException {
        byte kind = readHeader(in); long generation = in.readLong(), sequence = in.readLong();
        try { return new Command(kind, generation, sequence, in.readUnsignedByte()); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid command", e); }
    }

    public static void writeFrame(DataOutputStream out, Frame frame) throws IOException {
        header(out, FRAME, frame.generation(), frame.sequence());
        out.write(frame.rgba()); out.writeInt(frame.pcm().length);
        for (short sample : frame.pcm()) out.writeShort(sample);
        text(out, frame.mode()); out.writeInt(frame.x()); out.writeInt(frame.y()); out.writeInt(frame.rings());
        out.writeLong(frame.vInt()); out.writeLong(frame.levelFrame()); out.writeInt(frame.polledMask()); out.flush();
    }

    public static Frame readFrame(DataInputStream in) throws IOException {
        byte kind = readHeader(in); long generation = in.readLong(), sequence = in.readLong();
        try { identity(generation, sequence); } catch (IllegalArgumentException e) { throw new IOException("Invalid media identity", e); }
        if (kind == ERROR) throw new IOException("Worker fault [" + generation + ":" + sequence + "]: " + text(in));
        if (kind != FRAME) throw new IOException("Expected complete native frame");
        byte[] rgba = new byte[RGBA_BYTES]; in.readFully(rgba);
        int length = in.readInt();
        if (length < 0 || length > MAX_PCM_SHORTS || length % 2 != 0) throw new IOException("Unbounded or invalid PCM packet");
        short[] pcm = new short[length]; for (int i = 0; i < length; i++) pcm[i] = in.readShort();
        return new Frame(generation, sequence, rgba, pcm, text(in), in.readInt(), in.readInt(), in.readInt(),
                in.readLong(), in.readLong(), in.readInt());
    }

    public static void writeError(DataOutputStream out, long generation, long sequence, String message) throws IOException {
        header(out, ERROR, generation, sequence);
        String clean = message == null ? "Unknown worker failure" : message.replaceAll("[\\p{Cntrl}]", " ");
        byte[] bytes = clean.getBytes(StandardCharsets.UTF_8);
        out.writeInt(Math.min(bytes.length, MAX_TEXT)); out.write(bytes, 0, Math.min(bytes.length, MAX_TEXT)); out.flush();
    }

    private static void identity(long generation, long sequence) {
        if (generation <= 0 || sequence < 0) throw new IllegalArgumentException("Invalid generation/sequence");
    }
    private static void header(DataOutputStream out, byte kind, long generation, long sequence) throws IOException {
        identity(generation, sequence); out.writeInt(MAGIC); out.writeInt(VERSION); out.writeByte(kind);
        out.writeLong(generation); out.writeLong(sequence);
    }
    private static byte readHeader(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Incompatible challenge protocol");
        return in.readByte();
    }
    private static void text(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT) throw new IOException("Text too long");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String text(DataInputStream in) throws IOException {
        int length = in.readInt(); if (length < 0 || length > MAX_TEXT) throw new IOException("Unbounded text packet");
        byte[] bytes = new byte[length]; in.readFully(bytes); return new String(bytes, StandardCharsets.UTF_8);
    }
}
