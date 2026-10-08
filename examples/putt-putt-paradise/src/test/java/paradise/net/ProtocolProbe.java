package paradise.net;

import java.io.*;
import java.time.Duration;
import java.util.*;
import static paradise.net.GolfPacket.*;

/** Engine-free executable regression probe; invoked by TestGolfProtocol. */
public final class ProtocolProbe {
    static final UUID MATCH = UUID.randomUUID();
    static ShotId id(long turn, int owner) { return new ShotId(MATCH, 1, turn, turn, owner); }
    static ShotRequest request(ShotId id) { return new ShotRequest(id, 1, 25, 640, -50); }
    static Fingerprints prints() {
        return new Fingerprints(GolfCodec.SCHEMA, "0.7.0", "engine", "paradise", "rules",
                Fingerprints.SONIC_2_SHA1, "competition", 800, 224);
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void rejects(Throwing action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | IOException expected) { return; }
        throw new AssertionError("invalid protocol value accepted");
    }
    @FunctionalInterface interface Throwing { void run() throws Exception; }

    public static void run() throws Exception {
        var shot = request(id(0, 0));
        var score = new Score(3, 1, false);
        byte[] original = {1, 2, 3};
        var view = new ViewFrame(shot.id(), 1, 23, original);
        original[0] = 9;
        check(view.payload()[0] == 1, "constructor must copy scene payload");
        byte[] returned = view.payload(); returned[0] = 8;
        check(view.payload()[0] == 1, "accessor must copy scene payload");
        List<GolfPacket> packets = List.of(new Hello(prints(), "sonic"),
                new Ready(MATCH, 1, UUID.randomUUID(), prints(), "tails"),
                new TurnOpened(shot.id(), 10, 20, 224, 5, score, score), shot,
                new ShotRequest(shot.id(), -1, 90, 1000, 100),
                new ShotAccepted(shot.id(), 23, 640), view,
                new TurnCommitted(shot.id(), Outcome.SETTLED, 100, 200, score, score, 1),
                new Pause(MATCH, "disconnect"), new Resume(MATCH, 25), new Leave(MATCH, 1, "quit"),
                new Reconnect(MATCH, 1, UUID.randomUUID(), 1, 4),
                new SoundCue(shot.id(), 4, 2, "spindash"), new Rejected(shot.id(), "wrong owner"),
                new ShotControl(shot.id(), ShotAction.REWIND),
                new ShotStatus(shot.id(), ShotPhase.WATCH, 3, 1), new ShotStatus(shot.id(), ShotPhase.REWINDING, -1, -1, 12),
                new TurnOpened(shot.id(), 10, 20, 224, 5, score, score, true),
                new ShotControl(shot.id(), ShotAction.READY), new ShotStatus(shot.id(), ShotPhase.HANDOFF, 3, 1));
        var combined = new ByteArrayOutputStream();
        for (var packet : packets) GolfCodec.write(combined, packet);
        InputStream fragmented = new FilterInputStream(new ByteArrayInputStream(combined.toByteArray())) {
            @Override public int read(byte[] b, int off, int len) throws IOException {
                return super.read(b, off, Math.min(1, len));
            }
        };
        for (var expected : packets) check(expected.equals(GolfCodec.read(fragmented)), "typed frame roundtrip: " + expected);
        check(GolfCodec.read(fragmented) == null, "clean EOF");
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(new byte[]{0, 0, 0})));
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(new byte[]{0, 32, 0, 1})));
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(new byte[]{0, 0, 0, 2, 99, 1})));
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(new byte[]{0, 0, 0, 2, 1, 99})));
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(new byte[]{0, 0, 0, 2, 1, 1})));
        // A peer from another protocol generation is named as such, not reported as a broken socket.
        byte[] older = GolfCodec.encode(new Hello(prints(), "sonic")); older[4] = (byte) (GolfCodec.SCHEMA - 1);
        try { GolfCodec.read(new ByteArrayInputStream(older)); throw new AssertionError("old schema accepted"); }
        catch (GolfCodec.IncompatibleProtocolException mismatch) {
            check(mismatch.peerSchema() == GolfCodec.SCHEMA - 1 && mismatch.getMessage().startsWith("incompatible"), "named schema mismatch");
        }
        byte[] readyFlag = GolfCodec.encode(new TurnOpened(shot.id(), 10, 20, 224, 5, score, score, true));
        readyFlag[readyFlag.length - 1] = 2;
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(readyFlag)));
        check(!new TurnOpened(shot.id(), 10, 20, 224, 5, score, score).readyRequired(), "turns are unheld unless the host says so");
        rejects(() -> new ShotRequest(shot.id(), 1, 45, 1000, -101));
        rejects(() -> new ShotRequest(shot.id(), 1, 0, 1000, 1));
        byte[] encoded = GolfCodec.encode(shot);
        byte[] invalidCharge = encoded.clone();
        invalidCharge[44] = 0; invalidCharge[45] = 101; // signed spin 101, outside -100..100.
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(invalidCharge)));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        int size = encoded.length - 4 + 1;
        trailing[0] = (byte)(size >>> 24); trailing[1] = (byte)(size >>> 16);
        trailing[2] = (byte)(size >>> 8); trailing[3] = (byte)size;
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(trailing)));
        rejects(() -> new ShotRequest(shot.id(), 0, 1, 1, 1));
        rejects(() -> new ShotRequest(shot.id(), 1, 91, 1, 1));
        rejects(() -> new ShotRequest(shot.id(), 1, 1, 1001, 1));
        rejects(() -> new ShotId(MATCH, 0, 0, 0, 0));
        rejects(() -> new Hello(prints(), "knuckles"));
        rejects(() -> new ViewFrame(shot.id(), 1, 0, new byte[GolfCodec.MAX_FRAME_BYTES]));
        check(new ShotRequest(shot.id(), -1, 0, 0, 0).isPutt(), "shot kind derives from zero elevation");
        check(prints().compatibleWith(prints()), "matching readiness");
        var other = new Fingerprints(GolfCodec.SCHEMA, "0.7.0", "engine", "paradise", "rules",
                Fingerprints.SONIC_2_SHA1, "competition", 320, 224);
        check(!prints().compatibleWith(other), "mismatched viewport rejected");
        for (int width : new int[]{352, 528}) new Fingerprints(1, "api", "engine", "mod", "rules", Fingerprints.SONIC_2_SHA1, "competition", width, 224);
        for (int width : new int[]{512, 640}) rejects(() -> new Fingerprints(1, "api", "engine", "mod", "rules", Fingerprints.SONIC_2_SHA1, "competition", width, 224));
        rejects(() -> new Fingerprints(1, "api", "engine", "mod", "rules", "BAD", "competition", 320, 224));

        var receipts = new ShotReceipts();
        receipts.openTurn(shot.id());
        check(receipts.accept(shot, 20).status() == ShotReceipts.Status.ACCEPTED, "first acceptance");
        check(receipts.accept(shot, 21).status() == ShotReceipts.Status.DUPLICATE, "same shot accepted once");
        check(receipts.accept(new ShotRequest(shot.id(), -1, 25, 640, -50), 22).status()
                == ShotReceipts.Status.CONFLICT, "contradictory payload rejected");
        check(receipts.accept(request(id(0, 1)), 22).status() == ShotReceipts.Status.WRONG_TURN, "wrong owner rejected");
        check(receipts.accept(shot, 1, 22).status() == ShotReceipts.Status.WRONG_TURN, "sender cannot forge another owner");
        var committed = new TurnCommitted(shot.id(), Outcome.SETTLED, 100, 200, score, score, 1);
        receipts.commit(committed);
        receipts.openTurn(id(1, 1));
        check(receipts.accept(shot, 30).receipt().committed().equals(committed), "last committed receipt retained");
        check(receipts.accept(request(id(2, 1)), 30).status() == ShotReceipts.Status.WRONG_TURN, "future turn rejected");
        var next = request(id(1, 1)); receipts.accept(next, 30);
        receipts.commit(new TurnCommitted(next.id(), Outcome.SETTLED, 0, 0, score, score, 0));
        receipts.openTurn(id(2, 0));
        check(receipts.accept(shot, 35).status() == ShotReceipts.Status.WRONG_TURN, "old receipt discarded");
        var retries = new ShotReceipts(); retries.openTurn(shot.id()); retries.accept(shot, 10);
        var rewound = new TurnCommitted(shot.id(), Outcome.REWOUND, 10, 20, score, score, shot.id().owner());
        retries.commit(rewound);
        var retry = new ShotId(MATCH, shot.id().hole(), shot.id().turn(), shot.id().shot() + 1, shot.id().owner());
        retries.openTurn(retry);
        check(retries.accept(shot, 20).receipt().committed().equals(rewound), "old accepted shot retains rewind receipt");
        check(retries.accept(request(retry), 20).newlyAccepted(), "same-turn retry with fresh shot ID");
        rejects(() -> new ShotStatus(shot.id(), ShotPhase.WATCH, 6, 1));
        rejects(() -> new ShotStatus(shot.id(), ShotPhase.WATCH, 3, -2));
        rejects(() -> new ShotStatus(shot.id(), ShotPhase.REWINDING, 3, 1, 0));
        rejects(() -> new ShotStatus(shot.id(), ShotPhase.REWINDING, 3, 1, 65));
        rejects(() -> new ShotStatus(shot.id(), ShotPhase.WATCH, 3, 1, 2));
        byte[] invalidReverseRate = GolfCodec.encode(new ShotStatus(shot.id(), ShotPhase.REWINDING, 3, 1, 12));
        invalidReverseRate[invalidReverseRate.length - 1] = 0;
        rejects(() -> GolfCodec.read(new ByteArrayInputStream(invalidReverseRate)));

        var reconnect = new ReconnectSessions(Duration.ofSeconds(30));
        UUID token = reconnect.register(MATCH, 1);
        reconnect.disconnected(1, 100);
        check(reconnect.reconnect(new Reconnect(MATCH, 1, token, 0, 0), 129), "reconnect within window");
        check(receipts.accept(next, 31).status() == ShotReceipts.Status.DUPLICATE, "reconnect cannot accept twice");
        reconnect.disconnected(1, 200);
        check(!reconnect.reconnect(new Reconnect(MATCH, 1, UUID.randomUUID(), 0, 0), 201), "opaque token required");
        check(!reconnect.reconnect(new Reconnect(MATCH, 1, token, 0, 0), 230), "reconnect expiry exact boundary");
        var revisions = new ViewRevisions(); revisions.begin(shot.id());
        check(revisions.accept(view), "new frame accepted");
        check(!revisions.accept(view), "same revision discarded");
        check(!revisions.accept(new ViewFrame(id(1, 1), 5, 30, new byte[0])), "other turn discarded");
        var sounds = new SoundCues(); sounds.begin(shot.id(), 3);
        var cue = new SoundCue(shot.id(), 4, 2, "spindash");
        check(sounds.accept(cue), "new sound cue");
        check(!sounds.accept(cue), "sound cue deduplicated");
        sounds.begin(shot.id(), 4);
        check(!sounds.accept(cue), "reconnect never replays old sound");
    }
}
