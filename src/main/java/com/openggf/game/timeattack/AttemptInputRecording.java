package com.openggf.game.timeattack;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Input-only attempt recording (security spec §6.2): one byte per frame from the
 * SPAWN frame onward (idle = 0). Bits 0-4 = AbstractPlayableSprite INPUT_* held
 * mask, bit 5 = start held. Deliberately contains NO sidecar/physics data.
 */
public final class AttemptInputRecording {
    public static final int START_HELD_BIT = 0x20;
    /** Ten minutes at 60fps — matches GhostFileCodec.MAX_FRAMES and the ROM time-over cap. */
    public static final int MAX_FRAMES = 36_000;

    private final AttemptStartDescriptor start;
    private final ByteArrayOutputStream masks;

    public AttemptInputRecording(AttemptStartDescriptor start) {
        this(start, new ByteArrayOutputStream());
    }

    private AttemptInputRecording(AttemptStartDescriptor start, ByteArrayOutputStream masks) {
        this.start = start;
        this.masks = masks;
    }

    public void appendFrame(int heldMask, boolean startHeld) {
        masks.write((heldMask & 0x1F) | (startHeld ? START_HELD_BIT : 0));
    }

    public AttemptStartDescriptor start() { return start; }
    public int frameCount() { return masks.size(); }
    public int heldMaskAt(int frame) { return masks.toByteArray()[frame] & 0xFF; }

    public byte[] encode() {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(start.gameId());
            out.writeByte(start.zone());
            out.writeByte(start.act());
            out.writeUTF(start.character());
            out.writeUTF(start.fingerprint());
            byte[] data = masks.toByteArray();
            out.writeInt(data.length);
            out.write(data);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static AttemptInputRecording decode(byte[] encoded) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            AttemptStartDescriptor start = new AttemptStartDescriptor(in.readUTF(),
                    in.readUnsignedByte(), in.readUnsignedByte(), in.readUTF(), in.readUTF());
            int length = in.readInt();
            if (length < 0 || length > MAX_FRAMES) {
                throw new IOException("invalid attempt recording frame count " + length);
            }
            byte[] data = new byte[length];
            in.readFully(data);
            ByteArrayOutputStream masks = new ByteArrayOutputStream();
            masks.write(data, 0, data.length);
            return new AttemptInputRecording(start, masks);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] sha256() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(encode());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
