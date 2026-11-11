package openggf.timeattack;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Input and external race-event recording: one byte per executed step from spawn
 * (idle = 0). Bits 0-4 are the held input mask, bit 5 is Start. Swap payloads are
 * server commands, never comparison trace data. The canonical versioned bytes bind both.
 */
public final class AttemptInputRecording {
    public static final int START_HELD_BIT = 0x20;
    /** Ten minutes thirty seconds, including native countdown and completion steps. */
    public static final int MAX_FRAMES = 37_800;
    public static final int MAX_ENCODED_BYTES = MAX_FRAMES + 1024 * 64 + 8192;

    public static final int FORMAT_VERSION = 3;
    public static final int MAX_EVENTS = 1024;
    private int encodingVersion = FORMAT_VERSION;
    public record RecordedEvent(int step, com.openggf.game.arena.RaceEvent event) { }
    private final java.util.List<RecordedEvent> events = new java.util.ArrayList<>();
    public java.util.List<RecordedEvent> events() { return java.util.List.copyOf(events); }
    /** Append the engine's actual result before its input step is appended. */
    public void appendEvent(int step, com.openggf.game.arena.RaceEvent event) {
        if (start.mode() != CompetitionRoomSettings.Mode.COMPETITION || !start.swapEnabled())
            throw new IllegalArgumentException("race events require a swap-enabled competition attempt");
        if (event == null || step < 0 || step >= MAX_FRAMES || events.size() >= MAX_EVENTS
                || !events.isEmpty() && step <= events.getLast().step())
            throw new IllegalArgumentException("invalid race event order/count");
        events.add(new RecordedEvent(step, event));
    }

    private final AttemptStartDescriptor start;
    private final ByteArrayOutputStream masks;
    private byte[] maskSnapshot;

    public AttemptInputRecording(AttemptStartDescriptor start) {
        this(start, new ByteArrayOutputStream());
    }

    private AttemptInputRecording(AttemptStartDescriptor start, ByteArrayOutputStream masks) {
        this.start = start;
        this.masks = masks;
    }

    public void appendFrame(int heldMask, boolean startHeld) {
        if (masks.size() >= MAX_FRAMES) throw new IllegalStateException("attempt frame cap");
        masks.write((heldMask & 0x1F) | (startHeld ? START_HELD_BIT : 0));
        maskSnapshot = null;
    }

    public AttemptStartDescriptor start() { return start; }
    public int frameCount() { return masks.size(); }
    public int heldMaskAt(int frame) { return maskSnapshot()[frame] & 0xFF; }

    public byte[] encode() {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(encodingVersion);
            out.writeUTF(start.gameId());
            out.writeByte(start.zone());
            out.writeByte(start.act());
            out.writeUTF(start.character());
            out.writeUTF(start.fingerprint());
            out.writeUTF(start.trackKind().name());
            out.writeUTF(start.mode().name());
            out.writeUTF(start.monitors().name());
            out.writeUTF(start.deathRestart().name());
            out.writeBoolean(start.swapEnabled());
            byte[] data = maskSnapshot();
            out.writeInt(data.length);
            out.write(data);
            if (encodingVersion == 2) out.writeByte(events.size());
            else out.writeShort(events.size());
            for (RecordedEvent e : events) {
                if (e.step() >= data.length) throw new IllegalStateException("event has no recorded step");
                out.writeInt(e.step());
                boolean apply = e.event() instanceof com.openggf.game.arena.RaceEvent.SwapApply;
                out.writeByte(apply ? 1 : 2);
                long id = apply ? ((com.openggf.game.arena.RaceEvent.SwapApply)e.event()).swapId()
                        : ((com.openggf.game.arena.RaceEvent.SwapFallback)e.event()).swapId();
                if (id < 0 || id > 0xffffffffL) throw new IllegalArgumentException("swap id outside u32");
                out.writeInt((int)id);
                if (e.event() instanceof com.openggf.game.arena.RaceEvent.SwapApply a) {
                    var t = a.target();
                    out.writeInt(t.centreX()); out.writeInt(t.centreY());
                    out.writeInt(t.xVel()); out.writeInt(t.yVel());
                    out.writeBoolean(t.facingLeft()); out.writeBoolean(t.secondaryPath());
                    out.writeInt(t.priorityBucket()); out.writeBoolean(t.highPriority());
                    out.writeInt(t.shoeKind()); out.writeInt(t.shoeTimer()); out.writeInt(t.progress());
                }
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static AttemptInputRecording decode(byte[] encoded) {
        try { return decodeChecked(encoded); }
        catch(IOException e) { throw new UncheckedIOException(e); }
    }

    /** Checked bounded decoding for persisted/uploadable sidecars; the compatibility decoder retains its exception shape. */
    public static AttemptInputRecording decodeChecked(byte[] encoded) throws IOException {
        if(encoded==null || encoded.length>MAX_ENCODED_BYTES) throw new IOException("Attempt recording exceeds byte limit");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int version = in.readUnsignedByte();
            if (version != 2 && version != FORMAT_VERSION) throw new IOException("Unsupported recording version");
            AttemptStartDescriptor start;
            try {
                start = new AttemptStartDescriptor(in.readUTF(), in.readUnsignedByte(), in.readUnsignedByte(),
                        in.readUTF(), in.readUTF(), com.openggf.mods.run.TrackKind.valueOf(in.readUTF()),
                        CompetitionRoomSettings.Mode.valueOf(in.readUTF()),
                        com.openggf.game.arena.MonitorRule.valueOf(in.readUTF()),
                        com.openggf.game.session.GameplayRunPolicy.DeathRestart.valueOf(in.readUTF()), in.readBoolean());
            } catch (IllegalArgumentException invalid) { throw new IOException("Invalid recording rules", invalid); }
            openggf.timeattack.ghost.GhostFileCodec.validateRoute(start.gameId(),start.zone(),start.act(),start.character());
            int length = in.readInt();
            if (length < 0 || length > MAX_FRAMES) {
                throw new IOException("invalid attempt recording frame count " + length);
            }
            byte[] data = new byte[length];
            in.readFully(data);

            for(byte mask:data) if((mask & 0xC0)!=0) throw new IOException("Attempt recording has invalid input mask");
            ByteArrayOutputStream masks = new ByteArrayOutputStream();
            masks.write(data, 0, data.length);
            AttemptInputRecording recording=new AttemptInputRecording(start,masks);
            recording.encodingVersion = version;
            int eventCount = version == 2 ? in.readUnsignedByte() : in.readUnsignedShort();
            if (eventCount > MAX_EVENTS) throw new IOException("Too many race events");
            for (int i = 0; i < eventCount; i++) {
                int step = in.readInt();
                int kind = in.readUnsignedByte();
                long id = Integer.toUnsignedLong(in.readInt());
                com.openggf.game.arena.RaceEvent event;
                if (kind == 1) event = new com.openggf.game.arena.RaceEvent.SwapApply(id,
                        new com.openggf.game.arena.SwapTarget(in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                                in.readBoolean(), in.readBoolean(), in.readInt(), in.readBoolean(),
                                in.readInt(), in.readInt(), in.readInt()));
                else if (kind == 2) event = new com.openggf.game.arena.RaceEvent.SwapFallback(id);
                else throw new IOException("Unknown race event kind");
                if (step >= length) throw new IOException("Race event outside recording");
                try { recording.appendEvent(step, event); }
                catch (IllegalArgumentException invalid) { throw new IOException("Invalid race event", invalid); }
            }
            if (in.read() != -1) throw new IOException("Attempt recording has trailing bytes");
            // Hashes bind encoded bytes. Reject alternate modified-UTF encodings before sha256()
            // can canonicalize them or encounter an unchecked writeUTF overflow.
            try {
                if(!java.util.Arrays.equals(encoded,recording.encode()))throw new IOException("Attempt recording has noncanonical encoding");
            } catch(UncheckedIOException invalidEncoding) {
                throw new IOException("Attempt recording cannot be encoded canonically",invalidEncoding.getCause());
            }
            return recording;
        }
    }

    public byte[] sha256() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(encode());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] maskSnapshot() {
        byte[] current = maskSnapshot;
        if (current == null) {
            current = masks.toByteArray();
            maskSnapshot = current;
        }
        return current;
    }
}
