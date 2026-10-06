package paradise.net;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import paradise.model.GolfRules;

/** Immutable wire values. No gameplay snapshots, ROM bytes, executable objects or pitch values. */
public sealed interface GolfPacket {
    record ShotId(UUID match, int hole, long turn, long shot, int owner) {
        public ShotId {
            Objects.requireNonNull(match); range(hole, 1, 2, "hole"); player(owner);
            positive(turn, "turn"); positive(shot, "shot");
        }
    }

    record Fingerprints(int protocol, String api, String engine, String mod, String rules,
                        String romSha1, String mode, int viewportWidth, int viewportHeight) {
        public static final String SONIC_2_SHA1 = "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9";
        public Fingerprints {
            range(protocol, 1, 255, "protocol"); text(api); text(engine); text(mod); text(rules); text(mode);
            if (!SONIC_2_SHA1.equals(romSha1)) throw new IllegalArgumentException("native S2 ROM identity required");
            if (viewportWidth != 320 && viewportWidth != 352 && viewportWidth != 400
                    && viewportWidth != 528 && viewportWidth != 800) throw new IllegalArgumentException("viewport width");
            if (viewportHeight != 224) throw new IllegalArgumentException("viewport height");
        }
        /** Character choice is deliberately separate: the golfers may choose independently. */
        public boolean compatibleWith(Fingerprints other) { return equals(other) && protocol == GolfCodec.SCHEMA; }
    }

    record Score(int strokes, int penalties, boolean finished) {
        public Score { range(strokes, 0, 1_000_000, "strokes"); range(penalties, 0, 1_000_000, "penalties"); }
    }
    enum Outcome { SETTLED, FINISHED, PENALTY, CONCEDED, REWOUND }
    enum ShotAction { REWIND, KEEP }
    enum ShotPhase { AIM, CHARGING, WATCH, REVIEW, REWINDING }
    record ShotControl(ShotId id, ShotAction action) implements GolfPacket {
        public ShotControl { Objects.requireNonNull(id); Objects.requireNonNull(action); }
    }
    record ShotStatus(ShotId id, ShotPhase phase, int holeRemaining, int turnRemaining) implements GolfPacket {
        public ShotStatus {
            Objects.requireNonNull(id); Objects.requireNonNull(phase);
            range(holeRemaining, -1, 5, "hole rewinds"); range(turnRemaining, -1, 3, "turn rewinds");
        }
        public boolean canRewind() { return holeRemaining != 0 && turnRemaining != 0
                && (phase == ShotPhase.CHARGING || phase == ShotPhase.WATCH || phase == ShotPhase.REVIEW); }
    }
    record Hello(Fingerprints fingerprints, String character) implements GolfPacket {
        public Hello { Objects.requireNonNull(fingerprints); GolfPacket.character(character); }
    }
    record Ready(UUID match, int owner, UUID roomToken, Fingerprints fingerprints, String character) implements GolfPacket {
        public Ready { Objects.requireNonNull(match); player(owner); Objects.requireNonNull(roomToken);
            Objects.requireNonNull(fingerprints); GolfPacket.character(character); }
    }
    record TurnOpened(ShotId id, int lieX, int lieY, int surfaceAngle, int rollOffset, Score player0, Score player1) implements GolfPacket {
        public TurnOpened { Objects.requireNonNull(id); Objects.requireNonNull(player0); Objects.requireNonNull(player1);
            range(surfaceAngle, 0, 255, "surface angle"); range(rollOffset, 0, 64, "roll offset"); }
    }
    /** Elevation 0..90, power 0..1000, chip back/topspin -100..100. No client-supplied score. */
    record ShotRequest(ShotId id, int facing, int elevationDegrees, int normalizedPower, int spin) implements GolfPacket {
        public ShotRequest {
            Objects.requireNonNull(id);
            if (facing != -1 && facing != 1) throw new IllegalArgumentException("facing");
            range(elevationDegrees, 0, 90, "elevation"); range(normalizedPower, 0, GolfRules.MAX_POWER, "power");
            range(spin, -GolfRules.MAX_SPIN, GolfRules.MAX_SPIN, "spin");
            if (elevationDegrees == 0 && spin != 0) throw new IllegalArgumentException("putt spin");
        }
        public boolean isPutt() { return elevationDegrees == 0; }
    }
    record ShotAccepted(ShotId id, long acceptedTick, int normalizedPower) implements GolfPacket {
        public ShotAccepted { Objects.requireNonNull(id); positive(acceptedTick, "accepted tick");
            range(normalizedPower, 0, 1000, "power"); }
    }
    /** Full revisioned presentation envelope; the caller supplies the bounded value-scene codec. */
    record ViewFrame(ShotId id, long revision, long tick, byte[] payload) implements GolfPacket {
        public ViewFrame {
            Objects.requireNonNull(id); positive(revision, "revision"); positive(tick, "tick");
            Objects.requireNonNull(payload);
            if (payload.length > GolfCodec.MAX_VIEW_BYTES) throw new IllegalArgumentException("scene exceeds frame bound");
            payload = payload.clone();
        }
        @Override public byte[] payload() { return payload.clone(); }
        @Override public boolean equals(Object object) {
            return object instanceof ViewFrame other && id.equals(other.id) && revision == other.revision
                    && tick == other.tick && Arrays.equals(payload, other.payload);
        }
        @Override public int hashCode() { return 31 * Objects.hash(id, revision, tick) + Arrays.hashCode(payload); }
        @Override public String toString() { return "ViewFrame[" + id + ", revision=" + revision + ", bytes=" + payload.length + "]"; }
    }
    record TurnCommitted(ShotId id, Outcome outcome, int lieX, int lieY,
                         Score player0, Score player1, int nextOwner) implements GolfPacket {
        public TurnCommitted { Objects.requireNonNull(id); Objects.requireNonNull(outcome);
            Objects.requireNonNull(player0); Objects.requireNonNull(player1); range(nextOwner, -1, 1, "next owner"); }
    }
    record Pause(UUID match, String reason) implements GolfPacket {
        public Pause { Objects.requireNonNull(match); text(reason); }
    }
    record Resume(UUID match, long tick) implements GolfPacket {
        public Resume { Objects.requireNonNull(match); positive(tick, "tick"); }
    }
    record Leave(UUID match, int owner, String reason) implements GolfPacket {
        public Leave { Objects.requireNonNull(match); player(owner); text(reason); }
    }
    record Reconnect(UUID match, int owner, UUID roomToken, long lastRevision, long lastSoundCue) implements GolfPacket {
        public Reconnect { Objects.requireNonNull(match); player(owner); Objects.requireNonNull(roomToken);
            positive(lastRevision, "last revision"); positive(lastSoundCue, "last cue"); }
    }
    /** Reliable cue IDs and presentation tick offsets survive coalesced views. Never specifies pitch. */
    record SoundCue(ShotId id, long cueId, long tickOffset, String sound) implements GolfPacket {
        public SoundCue { Objects.requireNonNull(id); positive(cueId, "cue ID"); positive(tickOffset, "cue offset"); text(sound); }
    }
    record Rejected(ShotId id, String reason) implements GolfPacket {
        public Rejected { Objects.requireNonNull(id); text(reason); }
    }

    private static void character(String value) {
        if (!"sonic".equals(value) && !"tails".equals(value)) throw new IllegalArgumentException("character");
    }
    private static void player(int owner) { range(owner, 0, 1, "owner"); }
    private static void positive(long value, String name) { if (value < 0) throw new IllegalArgumentException(name); }
    private static void range(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name);
    }
    static void text(String value) {
        Objects.requireNonNull(value);
        if (value.isEmpty() || value.length() > 128 || value.chars().anyMatch(c -> c < 32 || c > 126))
            throw new IllegalArgumentException("bounded printable ASCII text required");
    }
}
