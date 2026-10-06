package paradise.net;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Objects;
import static paradise.net.GolfPacket.*;

/** Four-byte big-endian payload length, one-byte schema, one-byte type, bounded typed fields. */
public final class GolfCodec {
    public static final int SCHEMA = 4;
    public static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;
    public static final int MAX_VIEW_BYTES = MAX_FRAME_BYTES - 128;
    private GolfCodec() { }

    /** Buffered socket decoding may finish after creator-loader close; no dynamic loading occurs here. */
    static void prepareWorkers() {
        // Keep this finite list aligned with read/encode. Class literals resolve definitions;
        // only the Outcome enum needs initialization. This array is local to connection setup.
        for (Class<?> type : new Class<?>[]{GolfPacket.class, Fingerprints.class, ShotId.class, Score.class,
                Hello.class, Ready.class, TurnOpened.class, ShotRequest.class, ShotAccepted.class,
                ViewFrame.class, TurnCommitted.class, Pause.class, Resume.class, Leave.class,
                Reconnect.class, SoundCue.class, Rejected.class, ShotControl.class, ShotStatus.class}) Objects.requireNonNull(type);
        Objects.requireNonNull(Outcome.SETTLED);
        Objects.requireNonNull(ShotAction.REWIND); Objects.requireNonNull(ShotPhase.AIM);
    }

    public static byte[] encode(GolfPacket packet) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(SCHEMA);
            switch (packet) {
                case Hello p -> { out.writeByte(1); fingerprints(out, p.fingerprints()); text(out, p.character()); }
                case Ready p -> { out.writeByte(2); uuid(out, p.match()); out.writeByte(p.owner()); uuid(out, p.roomToken());
                    fingerprints(out, p.fingerprints()); text(out, p.character()); }
                case TurnOpened p -> { out.writeByte(3); id(out, p.id()); out.writeInt(p.lieX()); out.writeInt(p.lieY()); out.writeByte(p.surfaceAngle()); out.writeByte(p.rollOffset());
                    score(out, p.player0()); score(out, p.player1()); }
                case ShotRequest p -> { out.writeByte(4); id(out, p.id()); out.writeByte(p.facing());
                    out.writeByte(p.elevationDegrees()); out.writeShort(p.normalizedPower()); out.writeShort(p.spin()); }
                case ShotAccepted p -> { out.writeByte(5); id(out, p.id()); out.writeLong(p.acceptedTick()); out.writeShort(p.normalizedPower()); }
                case ViewFrame p -> { out.writeByte(6); id(out, p.id()); out.writeLong(p.revision()); out.writeLong(p.tick());
                    byte[] payload = p.payload(); out.writeInt(payload.length); out.write(payload); }
                case TurnCommitted p -> { out.writeByte(7); id(out, p.id()); out.writeByte(p.outcome().ordinal());
                    out.writeInt(p.lieX()); out.writeInt(p.lieY()); score(out, p.player0()); score(out, p.player1()); out.writeByte(p.nextOwner()); }
                case Pause p -> { out.writeByte(8); uuid(out, p.match()); text(out, p.reason()); }
                case Resume p -> { out.writeByte(9); uuid(out, p.match()); out.writeLong(p.tick()); }
                case Leave p -> { out.writeByte(10); uuid(out, p.match()); out.writeByte(p.owner()); text(out, p.reason()); }
                case Reconnect p -> { out.writeByte(11); uuid(out, p.match()); out.writeByte(p.owner()); uuid(out, p.roomToken());
                    out.writeLong(p.lastRevision()); out.writeLong(p.lastSoundCue()); }
                case SoundCue p -> { out.writeByte(12); id(out, p.id()); out.writeLong(p.cueId()); out.writeLong(p.tickOffset()); text(out, p.sound()); }
                case Rejected p -> { out.writeByte(13); id(out, p.id()); text(out, p.reason()); }
                case ShotControl p -> { out.writeByte(14); id(out, p.id()); out.writeByte(p.action().ordinal()); }
                case ShotStatus p -> { out.writeByte(15); id(out, p.id()); out.writeByte(p.phase().ordinal());
                    out.writeByte(p.holeRemaining()); out.writeByte(p.turnRemaining()); }
            }
            byte[] payload = bytes.toByteArray();
            if (payload.length > MAX_FRAME_BYTES) throw new IllegalArgumentException("frame exceeds bound");
            var framed = new ByteArrayOutputStream(payload.length + 4);
            new DataOutputStream(framed).writeInt(payload.length); framed.write(payload);
            return framed.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public static void write(OutputStream out, GolfPacket packet) throws IOException { out.write(encode(packet)); }

    /** Returns null only at clean frame-boundary EOF. Partial, unknown and malformed frames are fatal. */
    public static GolfPacket read(InputStream stream) throws IOException {
        int first = stream.read();
        if (first < 0) return null;
        var header = new DataInputStream(stream);
        int length = first << 24 | header.readUnsignedByte() << 16 | header.readUnsignedByte() << 8 | header.readUnsignedByte();
        if (length < 2 || length > MAX_FRAME_BYTES) throw new IOException("invalid frame length");
        byte[] payload = new byte[length]; header.readFully(payload);
        var in = new DataInputStream(new ByteArrayInputStream(payload));
        if (in.readUnsignedByte() != SCHEMA) throw new IOException("unsupported schema");
        try {
            GolfPacket packet = switch (in.readUnsignedByte()) {
                case 1 -> new Hello(fingerprints(in), text(in));
                case 2 -> new Ready(uuid(in), in.readUnsignedByte(), uuid(in), fingerprints(in), text(in));
                case 3 -> new TurnOpened(id(in), in.readInt(), in.readInt(), in.readUnsignedByte(), in.readUnsignedByte(), score(in), score(in));
                case 4 -> new ShotRequest(id(in), in.readByte(), in.readUnsignedByte(), in.readUnsignedShort(), in.readShort());
                case 5 -> new ShotAccepted(id(in), in.readLong(), in.readUnsignedShort());
                case 6 -> new ViewFrame(id(in), in.readLong(), in.readLong(), scene(in));
                case 7 -> new TurnCommitted(id(in), outcome(in), in.readInt(), in.readInt(), score(in), score(in), in.readByte());
                case 8 -> new Pause(uuid(in), text(in));
                case 9 -> new Resume(uuid(in), in.readLong());
                case 10 -> new Leave(uuid(in), in.readUnsignedByte(), text(in));
                case 11 -> new Reconnect(uuid(in), in.readUnsignedByte(), uuid(in), in.readLong(), in.readLong());
                case 12 -> new SoundCue(id(in), in.readLong(), in.readLong(), text(in));
                case 13 -> new Rejected(id(in), text(in));
                case 14 -> new ShotControl(id(in), enumValue(in, ShotAction.values()));
                case 15 -> new ShotStatus(id(in), enumValue(in, ShotPhase.values()), in.readByte(), in.readByte());
                default -> throw new IOException("unknown packet type");
            };
            if (in.available() != 0) throw new IOException("trailing packet bytes");
            return packet;
        } catch (IllegalArgumentException invalid) { throw new IOException("invalid packet value", invalid); }
    }
    private static byte[] scene(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_VIEW_BYTES || length > in.available()) throw new IOException("invalid scene length");
        byte[] data = new byte[length]; in.readFully(data); return data;
    }
    private static void uuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void id(DataOutputStream out, ShotId value) throws IOException {
        uuid(out, value.match()); out.writeByte(value.hole()); out.writeLong(value.turn()); out.writeLong(value.shot()); out.writeByte(value.owner());
    }
    private static ShotId id(DataInputStream in) throws IOException {
        return new ShotId(uuid(in), in.readUnsignedByte(), in.readLong(), in.readLong(), in.readUnsignedByte());
    }
    private static void text(DataOutputStream out, String value) throws IOException {
        GolfPacket.text(value); byte[] data = value.getBytes(StandardCharsets.US_ASCII); out.writeByte(data.length); out.write(data);
    }
    private static String text(DataInputStream in) throws IOException {
        int length = in.readUnsignedByte();
        if (length == 0 || length > 128) throw new IOException("invalid text length");
        byte[] data = new byte[length]; in.readFully(data);
        for (byte b : data) if (b < 32 || b > 126) throw new IOException("invalid text encoding");
        return new String(data, StandardCharsets.US_ASCII);
    }
    private static void fingerprints(DataOutputStream out, Fingerprints p) throws IOException {
        out.writeByte(p.protocol()); text(out, p.api()); text(out, p.engine()); text(out, p.mod()); text(out, p.rules());
        text(out, p.romSha1()); text(out, p.mode()); out.writeShort(p.viewportWidth()); out.writeShort(p.viewportHeight());
    }
    private static Fingerprints fingerprints(DataInputStream in) throws IOException {
        return new Fingerprints(in.readUnsignedByte(), text(in), text(in), text(in), text(in), text(in), text(in),
                in.readUnsignedShort(), in.readUnsignedShort());
    }
    private static void score(DataOutputStream out, Score score) throws IOException {
        out.writeInt(score.strokes()); out.writeInt(score.penalties()); out.writeByte(score.finished() ? 1 : 0);
    }
    private static Score score(DataInputStream in) throws IOException {
        int strokes = in.readInt(), penalties = in.readInt(), finished = in.readUnsignedByte();
        if (finished > 1) throw new IOException("invalid boolean");
        return new Score(strokes, penalties, finished == 1);
    }
    private static Outcome outcome(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value >= Outcome.values().length) throw new IOException("invalid outcome");
        return Outcome.values()[value];
    }
    private static <T> T enumValue(DataInputStream in, T[] values) throws IOException {
        int index = in.readUnsignedByte();
        if (index >= values.length) throw new IOException("invalid enum");
        return values[index];
    }
}
