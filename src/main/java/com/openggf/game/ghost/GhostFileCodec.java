package com.openggf.game.ghost;

import com.openggf.ghost.GhostFrameCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reader/writer for .ggfghost files: header + 7-byte frame stream (main spec §3/§7). */
public final class GhostFileCodec {
    public static final int FORMAT_VERSION = 1;
    /** Ten minutes at 60fps — the ROM act time-over cap; also bounds hostile-file allocations. */
    public static final int MAX_FRAMES = 36_000;
    private static final int HASH_LENGTH = 32;
    private static final byte[] MAGIC = "GGFGHOST".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private GhostFileCodec() {
    }

    public static void write(GhostRecording r, Path path) throws IOException {
        if (r.frameCount() > MAX_FRAMES) {
            throw new IOException("recording has " + r.frameCount()
                    + " frames, exceeding MAX_FRAMES " + MAX_FRAMES);
        }
        Files.createDirectories(path.getParent());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.write(MAGIC);
            GhostHeader h = r.header();
            out.writeShort(h.formatVersion());
            out.writeUTF(h.gameId());
            out.writeByte(h.zone());
            out.writeByte(h.act());
            out.writeUTF(h.character());
            out.writeUTF(h.displayName());
            out.writeInt(h.firstInputFrame());
            out.writeInt(h.finishFrame());
            out.writeByte(h.splitFrames().length);
            for (int split : h.splitFrames()) out.writeInt(split);
            out.writeByte(h.inputRecordingHash().length);
            out.write(h.inputRecordingHash());
            out.writeInt(r.frameCount());
            out.write(r.frameData());
        }
    }

    public static GhostRecording read(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException(path + " is not a .ggfghost file");
            }
            int version = in.readUnsignedShort();
            if (version != FORMAT_VERSION) {
                throw new IOException(path + " has unsupported .ggfghost format version " + version);
            }
            String gameId = in.readUTF();
            int zone = in.readUnsignedByte();
            int act = in.readUnsignedByte();
            String character = in.readUTF();
            String displayName = in.readUTF();
            int firstInput = in.readInt();
            int finish = in.readInt();
            int[] splits = new int[in.readUnsignedByte()];
            for (int i = 0; i < splits.length; i++) splits[i] = in.readInt();
            int hashLength = in.readUnsignedByte();
            if (hashLength != HASH_LENGTH) {
                throw new IOException(path + " has invalid input-recording hash length " + hashLength);
            }
            byte[] hash = new byte[HASH_LENGTH];
            in.readFully(hash);
            int frameCount = in.readInt();
            if (frameCount < 1 || frameCount > MAX_FRAMES) {
                throw new IOException(path + " has invalid frame count " + frameCount);
            }
            byte[] frames = new byte[frameCount * GhostFrameCodec.BYTES];
            in.readFully(frames);
            return new GhostRecording(new GhostHeader(version, gameId, zone, act, character,
                    displayName, firstInput, finish, splits, hash), frames);
        }
    }
}
