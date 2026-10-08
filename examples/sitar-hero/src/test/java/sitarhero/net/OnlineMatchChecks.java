package sitarhero.net;

import com.openggf.mods.scene.*;
import sitarhero.audio.PerformanceAudio;
import com.openggf.mods.scene.host.network.ManagedSceneNetwork;
import sitarhero.model.*;

import java.net.ServerSocket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/** Behavior checks, using sample charts (no ROM/music substitutes) and the real production transport. */
public final class OnlineMatchChecks {
    private static final long SECOND = 1_000_000_000L;
    private static final int RATE = 48_000;
    private static final SongSpec SONG = SongCatalog.all().getFirst();
    private OnlineMatchChecks() { }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void equal(long expected, long actual, String message) {
        require(expected == actual, message + ": expected " + expected + ", got " + actual);
    }
    private static void illegal(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Invalid local call was accepted");
    }
    private static Chart chart(int seconds) {
        return new Chart(List.of(new ChartNote(0, 0, 1, false, -1, 0),
                new ChartNote((long) seconds * RATE - RATE / 20, (long) seconds * RATE - RATE / 20, 2, false, -1, 0)),
                (long) seconds * RATE, RATE / 2);
    }
    private static RhythmSession session(Chart chart) { return new RhythmSession(chart, false, RATE, true); }

    private static final class Wire implements ScenePeer {
        final ArrayDeque<Message> incoming = new ArrayDeque<>();
        final List<String> sent = new ArrayList<>();
        Wire other;
        long clock, skew;
        long transit = 10_000_000L;
        boolean blocked;
        String drop = "";
        State state = State.CONNECTED;
        @Override public State state() { return state; }
        @Override public String error() { return state == State.FAILED ? "socket failed" : null; }
        @Override public boolean send(String text) {
            if (blocked || state != State.CONNECTED) return false;
            sent.add(text);
            if (!text.startsWith("SH2 " + drop + " ") || drop.isEmpty())
                other.incoming.add(new Message(text, clock - skew + other.skew + transit));
            return true;
        }
        @Override public List<Message> poll() { var result = List.copyOf(incoming); incoming.clear();
            for (Message message : result) clock = Math.max(clock, message.receivedNanos()); return result; }
        @Override public void close() { state = State.CLOSED; incoming.clear(); }
        void inject(String text) { incoming.add(new Message(text, clock)); }
        String last(String command) {
            return sent.stream().filter(s -> s.startsWith("SH2 " + command + " ")).reduce((a, b) -> b).orElseThrow();
        }
    }

    private static final class Pair implements AutoCloseable {
        final Wire a = new Wire(), b = new Wire();
        final OnlineMatch host, guest;
        long time, offset;
        Pair() { this(10 * SECOND, 0, List.of("s1", "s2", "s3k"), List.of("s1", "s3k")); }
        Pair(long time, long offset, List<String> gamesA, List<String> gamesB) {
            this.time = time; this.offset = offset; a.other = b; b.other = a; b.skew = offset;
            clocks(); host = new OnlineMatch(a, true, gamesA); guest = new OnlineMatch(b, false, gamesB);
            for (int i = 0; i < 3; i++) step(20_000_000L);
        }
        void clocks() { a.clock = time; b.clock = time + offset; }
        void step(long elapsed) { time += elapsed; clocks(); host.tick(a.clock); guest.tick(b.clock); }
        void healthy() { require(host.error().isEmpty(), host.error()); require(guest.error().isEmpty(), guest.error()); }
        void prepare(Chart chart) { prepare(chart,false); }
        void prepare(Chart chart, boolean coop) {
            host.offer(SONG, Role.SITAR, Difficulty.MEDIUM, coop); step(20_000_000L);
            host.ready(chart, RATE); guest.ready(chart, RATE);
        }
        void start(Chart chart) { start(chart,false); }
        void start(Chart chart, boolean coop) {
            prepare(chart,coop);
            for (int i = 0; i < 100 && !(host.startDue() && guest.startDue()); i++) step(20_000_000L);
            healthy(); require(host.startDue() && guest.startDue(), "both players reached count-in deadline");
            host.started(); guest.started();
        }
        void settle() { for (int i = 0; i < 40; i++) step(20_000_000L); healthy(); }
        @Override public void close() { host.close(); guest.close(); }
    }

    private record Sound(long source, double gain, double pan) { }
    private static final class MixPlayer implements SceneMusicPlayer {
        boolean audible = true;
        final List<Sound> sounds = new ArrayList<>();
        public long samplePosition() { return 0; }
        public long samplePositionAt(long nanos) { return 0; }
        public void pause() { }
        public void resume() { }
        public void setPartAudible(boolean value) { audible = value; }
        public void setWhammy(double value) { }
        public boolean cuePart(long source, int duration, double from, double to, double gain, double pan) {
            sounds.add(new Sound(source,gain,pan)); return true;
        }
        public boolean finished() { return false; }
        public boolean paused() { return false; }
        public long underrunCount() { return 0; }
        public void stop() { }
    }
    private static PerformanceAudio mix(MixPlayer player, Chart chart, boolean coop) {
        ScenePreparedMusic music = new ScenePreparedMusic() {
            public int sampleRate() { return RATE; }
            public long lengthSamples() { return chart.length(); }
            public List<SceneNoteEvent> notes() {
                return List.of(new SceneNoteEvent(0,SceneNoteEvent.Kind.FM,0,128,0,0,RATE));
            }
        };
        return new PerformanceAudio(player,music,chart,List.of(new SceneMusicPart(0,1,0,false)),Role.SITAR,false,coop);
    }
    public static void presentationMixPolicy() {
        for (boolean coop : List.of(false,true)) try (Pair p = new Pair()) {
            Chart chart = chart(2); p.start(chart,coop);
            RhythmSession missed=session(chart), good=session(chart);
            MixPlayer own=new MixPlayer(), partner=new MixPlayer();
            var ownAudio=mix(own,chart,coop); var partnerAudio=mix(partner,chart,coop);
            good.input(0,1,0,true,false,false); good.advance(6000); missed.advance(6000);
            ownAudio.update(missed,null,p.host,6000);
            partnerAudio.update(good,null,p.guest,6000); p.step(60_000_000L);
            ownAudio.update(missed,null,p.host,6000); partnerAudio.update(good,null,p.guest,6000);
            require(own.audible==coop,"only co-op lets a successful peer preserve the shared part");
            require(partner.audible,"remote miss cannot mute a successful local duel player");
            require(partner.sounds.size()==1 && partner.sounds.getFirst().pan()==.4
                    && partner.sounds.getFirst().gain()<own.sounds.getFirst().gain(),"remote mistake is distinct and quieter");
            equal(0,missed.hits(),"presentation cannot award local hits"); equal(1,good.hits(),"presentation cannot alter peer hits");
        }
        for (long offset : new long[]{-RATE/4,RATE/4}) try (Pair p = new Pair()) {
            Chart chart=chart(2);p.start(chart,false);
            RhythmSession missed=session(chart),good=session(chart);missed.advance(30000);good.input(0,1,0,true,false,false);
            // An overstrike at the current calibrated judgment coordinate, not the stale first miss.
            missed.input(30000,1,0,true,false,false);
            MixPlayer sender=new MixPlayer(),receiver=new MixPlayer();
            mix(sender,chart,false).update(missed,null,p.host,30000+offset);p.step(60_000_000L);
            mix(receiver,chart,false).update(good,null,p.guest,30000+offset);p.healthy();
            require(receiver.sounds.size()==1,"calibrated current strike is fresh on the peer song clock");
        }
        try (Pair p = new Pair()) {
            Chart chart=chart(2);p.start(chart,true);
            RhythmSession first=session(chart),second=session(chart);first.advance(6000);second.advance(6000);
            MixPlayer a=new MixPlayer(),b=new MixPlayer();var aa=mix(a,chart,true);var bb=mix(b,chart,true);
            aa.update(first,null,p.host,6000);bb.update(second,null,p.guest,6000);p.step(60_000_000L);p.step(20_000_000L);
            aa.update(first,null,p.host,6000);bb.update(second,null,p.guest,6000);
            require(!a.audible && !b.audible,"both co-op misses suppress both shared parts");
            require(a.sounds.size()==2 && b.sounds.size()==2,"each co-op player owns one audible mistake");
        }
        try (Pair p = new Pair()) {
            Chart chart=chart(2);p.start(chart,false);
            RhythmSession missed=session(chart),good=session(chart);missed.advance(6000);good.input(0,1,0,true,false,false);
            p.host.feedback(missed.position(),false,missed.drainFeedback());p.step(60_000_000L);
            MixPlayer player=new MixPlayer();mix(player,chart,false).update(good,null,p.guest,20000);
            require(player.audible && player.sounds.isEmpty(),"late peer cue is silent without altering local judgment");
        }
    }

    public static void pairedJudgments() {
        try (Pair p = new Pair()) {
            require(p.host.commonGames().equals(List.of("s1", "s3k")), "library intersection");
            Chart chart = chart(60); p.start(chart);
            RhythmSession good = session(chart), missed = session(chart);
            good.input(0, 1, 0, true, false, false); missed.advance(RATE);
            p.host.progress(good, good.position(), false); p.guest.progress(missed, missed.position(), false); p.step(20_000_000L);
            equal(good.score(), p.guest.remoteScore(), "remote score is display telemetry");
            equal(missed.hits(), p.host.remoteHits(), "independent remote hits");
            equal(1, good.hits(), "remote miss did not change local judgment");
            equal(0, missed.hits(), "remote hit did not award a local hit");
            equal(1, missed.misses(), "local miss retained");
        }
    }

    public static void longSong() {
        try (Pair p = new Pair()) {
            Chart chart = chart(600); p.start(chart);
            RhythmSession song = session(chart); song.input(0, 1, 0, true, false, false);
            song.advance(599L * RATE); p.host.progress(song, song.position(), false); p.step(20_000_000L); p.healthy();
            equal(song.position(), p.guest.remotePosition(), "progress past 92 seconds");
            song.input(chart.length() - RATE / 20, 2, 0, true, false, false);
            song.advance(chart.length() + RATE / 10 + 1); require(song.finished(), "natural end resolves final note");
            p.host.progress(song, song.position(), true); p.step(20_000_000L); p.healthy();
            require(p.guest.remoteFinished(), "natural finish reaches peer");
            equal(2, p.guest.remoteHits(), "final late-window hit retained");
            p.b.inject("SH2 STATE 1 999 0 0 0 0 0.5 0 2"); p.step(20_000_000L); p.healthy();
            require(p.guest.remoteFinished(), "finished result remains terminal");
        }
        try (Pair p = new Pair()) {
            Chart chart = chart(600); p.start(chart);
            p.b.inject("SH2 STATE 1 1 " + (chart.length() + RATE * 11L) + " 0 0 0 0.5 0 2");
            p.step(20_000_000L); require(!p.guest.error().isEmpty(), "tail is bounded by prepared chart");
        }
    }

    public static void rematch() {
        try (Pair p = new Pair()) {
            Chart chart = chart(60); p.start(chart);
            String ping = p.a.last("PING"), pong = p.b.last("PONG"), start = p.a.last("START");
            p.guest.requestPause(); p.settle();
            String pause = p.b.last("PAUSE"), hold = p.a.last("HOLD"), holdAck = p.b.last("HOLDACK"), startAck = p.b.last("STARTACK");
            p.guest.requestResume(); p.settle(); String resume = p.b.last("RESUME");
            illegal(() -> p.guest.offer(SONG, Role.SITAR, Difficulty.MEDIUM, false));
            p.host.offer(SONG, Role.SITAR, Difficulty.MEDIUM, true); p.step(20_000_000L);
            equal(2, p.guest.round(), "host owns rematch generation"); require(p.guest.choice().coop(), "new mode received");
            equal(0, p.guest.remoteScore(), "rematch clears old scores"); equal(0, p.guest.remotePosition(), "rematch clears old sample clock");
            p.b.inject(ping); p.a.inject(pong); p.b.inject(start); p.a.inject(pause); p.a.inject(resume); p.b.inject(hold); p.a.inject(holdAck); p.a.inject(startAck);
            p.b.inject("SH2 READY 1 48000 " + OnlineMatch.fingerprint(chart));
            p.b.inject("SH2 STATE 1 100 0 0 0 0 0.5 1 2");
            p.step(20_000_000L); p.healthy(); require(!p.guest.ready() && !p.guest.paused(), "old round cannot prepare or pause rematch");
            p.host.ready(chart, RATE); p.guest.ready(chart, RATE);
            for (int i = 0; i < 100 && !(p.host.startDue() && p.guest.startDue()); i++) p.step(20_000_000L);
            p.healthy(); require(p.host.startDue() && p.guest.startDue(), "rematch starts afresh");
        }
        try (Pair p = new Pair()) {
            p.a.inject("SH2 OFFER 1 green-hill SITAR MEDIUM versus"); p.step(20_000_000L);
            require(!p.host.error().isEmpty(), "guest cannot offer a round over wire");
        }
    }

    public static void offeredRoles() {
        try (Pair p = new Pair()) {
            SongSpec absent = SongCatalog.all().stream().filter(s -> s.id().equals("chemical-plant")).findFirst().orElseThrow();
            SongSpec noSynth = SongCatalog.all().stream().filter(s -> s.id().equals("marble-garden-1")).findFirst().orElseThrow();
            illegal(() -> p.host.offer(absent, Role.SITAR, Difficulty.MEDIUM, false));
            illegal(() -> p.host.offer(noSynth, Role.SYNTH, Difficulty.MEDIUM, false));
            equal(0, p.host.round(), "failed offers do not advance round");
            p.b.inject("SH2 OFFER 1 marble-garden-1 SYNTH MEDIUM coop"); p.step(20_000_000L);
            require(!p.guest.error().isEmpty(), "unsupported offered part is rejected before audio preparation");
        }
        try (Pair p = new Pair(10 * SECOND, 0, List.of("s1"), List.of("s2"))) {
            require(!p.host.error().isEmpty() && !p.guest.error().isEmpty(), "missing common ROM fails clearly");
        }
        illegal(() -> new OnlineMatch(new Wire(), true, List.of("s1", "s1")));
    }

    public static void mismatches() {
        for (boolean rateMismatch : List.of(false, true)) try (Pair p = new Pair()) {
            Chart chart = chart(60); p.host.offer(SONG, Role.SITAR, Difficulty.MEDIUM, false); p.step(20_000_000L);
            p.host.ready(chart, RATE);
            p.guest.ready(rateMismatch ? chart : chart(59), rateMismatch ? 44_100 : RATE);
            p.step(20_000_000L); require(!p.host.error().isEmpty() || !p.guest.error().isEmpty(), "mismatch fails before START");
            require(!p.host.startDue() && !p.guest.startDue(), "no count-in from mismatched preparation");
        }
        try (Pair p = new Pair()) {
            p.host.offer(SONG, Role.SITAR, Difficulty.MEDIUM, false);
            illegal(() -> p.host.ready(chart(60), 0)); illegal(() -> p.host.ready(chart(601), RATE));
            require(!p.host.ready(), "invalid readiness does not mutate session");
        }
    }

    public static void malformed() {
        for (String packet : List.of("", "SH2 HELLO s1", "SH2 WHAT", "SH2 HEARTBEAT extra", "SH2 HELLO s1,s1",
                "SH2 HELLO s4", "SH2 HELLO s1\nSTATE", "SH2\tHEARTBEAT", "SH2 OFFER 2 green-hill SITAR BAD versus",
                "SH2 OFFER 2 unknown SITAR MEDIUM versus", "SH2 READY 1 48000 NaN", "SH2 PING 1 9223372036854775808",
                "SH2 READY 2 48000 abc", "SH2 PONG 1 0 4 3", "SH2 START 1 9223372036854775807 1",
                "SH2 PING 01 0", "SH2 READY 1 7999 abc", "SH2 OFFER 3 green-hill SITAR MEDIUM coop")) {
            try (Pair p = new Pair()) {
                p.prepare(chart(60)); p.b.inject(packet); p.step(20_000_000L);
                require(!p.guest.error().isEmpty(), "malformed packet accepted: " + packet);
            }
        }
        for (String state : List.of("1 0 0 0 0 NaN 0 2", "1 0 0 2 2 0.5 0 2", "1 0 -1 0 0 0.5 0 2",
                "1 0 0 0 0 1.1 0 2", "1 0 0 0 0 0.5 2 2", "1 0 0 0 0 0.5 0 3")) {
            try (Pair p = new Pair()) {
                p.start(chart(60)); p.b.inject("SH2 STATE 1 " + state); p.step(20_000_000L);
                require(!p.guest.error().isEmpty(), "invalid state accepted: " + state);
            }
        }
    }

    public static void clockObservations() {
        try (Pair p = new Pair(10 * SECOND, 7 * SECOND, List.of("s1"), List.of("s1"))) {
            p.prepare(chart(60));
            // Different scene polling delays: the NTP receive edges must be the transport's observations.
            for (int i = 0; i < 3; i++) {
                p.time += 20_000_000L; p.clocks(); p.host.tick(p.a.clock);
                p.b.clock += 200_000_000L; p.guest.tick(p.b.clock);
                p.a.clock += 700_000_000L; p.host.tick(p.a.clock);
                p.time += SECOND; p.clocks();
            }
            p.healthy();
            String[] start = p.a.last("START").split(" ");
            long estimatedOffset = Long.parseLong(start[4]);
            require(Math.abs(estimatedOffset - p.offset) <= 15_000_000L, "clock offset used polling time instead of receive edge: " + estimatedOffset);
            for (int i = 0; i < 150 && !(p.host.startDue() && p.guest.startDue()); i++) p.step(20_000_000L);
            p.healthy(); require(p.host.startDue() && p.guest.startDue(), "offset count-in reached on both local clocks");
        }
    }

    public static void minimumRtt() {
        try (Pair p = new Pair(10 * SECOND, 7 * SECOND, List.of("s1"), List.of("s1"))) {
            p.b.drop = "PONG"; p.prepare(chart(60)); p.step(20_000_000L);
            long[][] delays = {{90_000_000L, 10_000_000L}, {10_000_000L, 10_000_000L}, {10_000_000L, 50_000_000L}};
            for (long[] delay : delays) {
                long t1 = Long.parseLong(p.a.last("PING").split(" ")[3]);
                long t2 = t1 + p.offset + delay[0], t3 = t2 + 200_000_000L;
                long t4 = t1 + delay[0] + 200_000_000L + delay[1];
                p.a.incoming.add(new ScenePeer.Message("SH2 PONG 1 " + t1 + " " + t2 + " " + t3, t4));
                p.time = t4 + 500_000_000L; p.clocks(); p.host.tick(p.a.clock);
            }
            p.healthy(); equal(p.offset, Long.parseLong(p.a.last("START").split(" ")[4]), "minimum RTT wins despite asymmetric later sample and slow polling");
            p.guest.tick(p.b.clock); p.healthy();
        }
    }

    public static void completionAndTelemetry() {
        for (boolean guestFinishes : List.of(false, true)) try (Pair p = new Pair()) {
            Chart chart = chart(60); p.start(chart);
            if (guestFinishes) p.guest.requestPause(); else p.host.requestPause();
            // Finish can race a pause that has been scheduled but not yet applied.
            RhythmSession done = session(chart); done.advance(chart.length() + RATE / 10 + 1);
            if (guestFinishes) p.guest.progress(done, done.position(), true); else p.host.progress(done, done.position(), true);
            p.settle(); require(!p.host.paused() && !p.guest.paused(), "finished player's intent cannot strand remaining performer");
        }
        try (Pair p = new Pair()) {
            Chart chart = chart(60); p.start(chart);
            RhythmSession good = session(chart); good.input(0, 1, 0, true, false, false);
            p.host.progress(good, 0, false); p.step(20_000_000L);
            p.b.inject("SH2 STATE 1 0 0 0 0 0 0.5 0 2"); p.step(20_000_000L); p.healthy();
            equal(good.score(), p.guest.remoteScore(), "duplicate sequence cannot roll score back");
            p.b.inject("SH2 STATE 1 1 0 0 0 0 0.5 0 2"); p.step(20_000_000L);
            require(!p.guest.error().isEmpty(), "newer telemetry cannot roll judgments back");
        }
    }

    public static void timingUncertainty() {
        try (Pair p = new Pair()) {
            // A congested outbound path during calibration gives the guest a later
            // estimated start. The path then clears before the first host control.
            p.a.transit = 500_000_000L; p.b.transit = 2_000_000L;
            p.prepare(chart(60));
            for (int i = 0; i < 8 && p.a.sent.stream().noneMatch(s -> s.startsWith("SH2 START ")); i++) p.step(700_000_000L);
            p.healthy();
            long deadline = Long.parseLong(p.a.last("START").split(" ")[3]);
            p.a.transit = p.b.transit = 2_000_000L;
            p.time = deadline; p.clocks(); p.host.tick(p.a.clock); p.guest.tick(p.b.clock); p.healthy();
            require(p.host.startDue() && !p.guest.startDue(), "asymmetry exposes legitimate different local due edges");
            p.host.started(); p.host.requestPause(); p.host.progress(session(chart(60)), 0, false);
            p.step(20_000_000L); p.healthy();
            // Peer control must not consume or advance the local start callback.
            require(!p.guest.startDue(), "peer traffic cannot advance the guest count-in");
            for (int i = 0; i < 30 && !p.guest.startDue(); i++) p.step(20_000_000L);
            require(p.guest.startDue(), "guest still gets its own scheduled start"); p.guest.started();
            p.settle(); require(p.host.paused() && p.guest.paused(), "early-arriving HOLD applies at its shared future deadline");
        }
        try (Pair p = new Pair()) {
            // Reverse the congested path: the guest starts earlier and its request
            // and final short-session report can reach the host before host due.
            p.a.transit = 2_000_000L; p.b.transit = 500_000_000L;
            p.prepare(chart(60));
            for (int i = 0; i < 8 && p.a.sent.stream().noneMatch(s -> s.startsWith("SH2 START ")); i++) p.step(700_000_000L);
            p.healthy(); String[] start = p.a.last("START").split(" ");
            p.time = Long.parseLong(start[3]) + Long.parseLong(start[4]);
            p.a.transit = p.b.transit = 2_000_000L; p.clocks(); p.host.tick(p.a.clock); p.guest.tick(p.b.clock); p.healthy();
            require(!p.host.startDue() && p.guest.startDue(), "guest's earlier estimated due edge");
            p.guest.started(); p.guest.requestPause(); p.guest.progress(session(chart(60)), 0, true);
            p.step(20_000_000L); p.healthy();
            require(p.host.remoteFinished(), "early terminal telemetry cannot be lost or stop the match");
            require(!p.host.startDue(), "guest traffic cannot consume the host's start callback");
            for (int i = 0; i < 30 && !p.host.startDue(); i++) p.step(20_000_000L);
            require(p.host.startDue(), "host still starts on its own deadline");
        }
    }

    public static void signedClocks() {
        for (long clock : new long[] {-10 * SECOND, 0, -100_000_000L}) try (Pair p = new Pair(clock, -3 * SECOND, List.of("s1"), List.of("s1"))) {
            p.start(chart(60));
            if (clock == -100_000_000L) require(p.a.sent.contains("SH2 PING 1 0"), "zero probe timestamp is not a sentinel");
            p.guest.requestPause(); p.settle(); require(p.host.paused() && p.guest.paused(), "signed clock control");
        }
    }

    public static void startAcknowledgment() {
        try (Pair p = new Pair()) {
            p.b.drop = "STARTACK"; p.prepare(chart(60));
            for (int i = 0; i < 20; i++) p.step(20_000_000L);
            require(!p.host.startDue(), "host must wait for peer start acknowledgment");
            String start = p.a.last("START"); p.b.inject(start); p.step(20_000_000L); p.healthy();
            p.b.drop = "";
            for (int i = 0; i < 100 && !(p.host.startDue() && p.guest.startDue()); i++) p.step(20_000_000L);
            p.healthy(); require(p.host.startDue() && p.guest.startDue(), "retried START acknowledged before deadline");
            p.host.started(); p.guest.started(); p.b.inject(start); p.step(20_000_000L); p.healthy();
            require(!p.guest.startDue(), "duplicate START cannot restart performance");
            illegal(() -> p.host.ready(chart(60), RATE));
        }
    }

    public static void callbackRace() {
        try (Pair p = new Pair()) {
            p.prepare(chart(60));
            for (int i = 0; i < 100 && !(p.host.startDue() && p.guest.startDue()); i++) p.step(20_000_000L);
            p.guest.started(); p.guest.requestPause(); p.step(20_000_000L); p.healthy();
            require(p.host.startDue(), "peer control cannot consume host's start callback");
            p.host.started(); p.settle(); require(p.host.paused() && p.guest.paused(), "control preceding started callback converges");
        }
    }

    public static void pauseOwners() {
        try (Pair p = new Pair()) {
            p.start(chart(60)); p.host.requestPause(); p.guest.requestPause(); p.settle();
            require(p.host.paused() && p.guest.paused(), "both paused");
            p.host.requestResume(); p.settle(); require(p.host.paused() && p.guest.paused(), "guest's pause survives host resume");
            p.guest.requestResume(); p.settle(); require(!p.host.paused() && !p.guest.paused(), "both intents released");
            p.host.requestPause(); p.guest.requestPause(); p.settle();
            p.guest.requestResume(); p.settle(); require(p.host.paused() && p.guest.paused(), "host's pause survives guest resume");
            p.host.requestResume(); p.settle(); require(!p.host.paused() && !p.guest.paused(), "host releases remaining intent");
        }
    }

    public static void controlRace() {
        try (Pair p = new Pair()) {
            p.start(chart(60)); p.guest.requestPause(); p.step(20_000_000L);
            p.guest.requestResume(); p.settle(); require(!p.host.paused() && !p.guest.paused(), "resume cannot be lost behind pending pause");
            p.host.requestPause(); p.host.requestResume(); p.settle();
            require(!p.host.paused() && !p.guest.paused(), "host can replace pending pause intent");
        }
    }

    public static void controlBursts() {
        try (Pair p = new Pair()) {
            p.start(chart(600)); Random random = new Random(0x51a7);
            boolean hostIntent = false, guestIntent = false;
            for (int burst = 0; burst < 60; burst++) {
                for (int edge = 0; edge < 1 + random.nextInt(5); edge++) {
                    boolean host = random.nextBoolean(), pause = random.nextBoolean();
                    OnlineMatch player = host ? p.host : p.guest;
                    if (pause) player.requestPause(); else player.requestResume();
                    if (host) hostIntent = pause; else guestIntent = pause;
                    if (random.nextBoolean()) p.step(20_000_000L);
                }
                p.settle(); boolean expected = hostIntent || guestIntent;
                require(p.host.paused() == expected && p.guest.paused() == expected,
                        "burst " + burst + " lost final pause ownership");
            }
        }
    }

    public static void controlRetry() {
        try (Pair p = new Pair()) {
            p.start(chart(60)); p.b.drop = "PAUSE"; p.guest.requestPause();
            for (int i = 0; i < 20; i++) p.step(20_000_000L);
            require(!p.host.paused(), "dropped request has no effect");
            p.b.drop = ""; p.settle(); require(p.host.paused() && p.guest.paused(), "pending guest intent retries");
            String old = p.a.last("HOLD"); p.guest.requestResume(); p.settle();
            p.b.inject(old); p.step(20_000_000L); p.healthy(); require(!p.guest.paused(), "obsolete HOLD cannot restore old pause");
            p.b.drop = "HOLDACK"; p.host.requestPause();
            long sentBefore = p.a.sent.stream().filter(s -> s.startsWith("SH2 HOLD ")).count();
            for (int i = 0; i < 20; i++) p.step(20_000_000L);
            require(p.a.sent.stream().filter(s -> s.startsWith("SH2 HOLD ")).count() > sentBefore, "unacknowledged HOLD retries");
            p.b.drop = ""; p.settle();
            require(p.host.paused() && p.guest.paused(), "HOLD and acknowledgment retry idempotently");
        }
    }

    public static void deadlines() {
        Wire a = new Wire(), b = new Wire(); a.other = b; b.other = a; a.clock = b.clock = SECOND;
        try (OnlineMatch match = new OnlineMatch(a, true, List.of("s1"))) {
            match.tick(SECOND); match.tick(12 * SECOND); require(!match.error().isEmpty(), "HELLO has deadline");
        }
        try (Pair p = new Pair()) {
            p.start(chart(60)); p.a.incoming.clear(); p.host.tick(p.a.clock + 31 * SECOND);
            require(!p.host.error().isEmpty(), "silent established peer times out");
        }
        try (Pair p = new Pair()) {
            p.host.offer(SONG, Role.SITAR, Difficulty.MEDIUM, false); p.step(20_000_000L);
            for (int i = 0; i < 130 && p.host.error().isEmpty(); i++) p.step(SECOND);
            require(!p.host.error().isEmpty(), "heartbeats cannot keep unprepared round forever");
        }
        try (Pair p = new Pair()) {
            p.b.drop = "PONG"; p.prepare(chart(60));
            for (int i = 0; i < 20 && p.host.error().isEmpty(); i++) p.step(SECOND);
            require(!p.host.error().isEmpty(), "heartbeats cannot keep missing clock exchange forever");
        }
        try (Pair p = new Pair()) {
            p.b.drop = "PONG"; Chart chart = chart(60); p.prepare(chart);
            for (int i = 0; i < 20 && p.host.error().isEmpty(); i++) {
                p.a.inject("SH2 READY 1 48000 " + OnlineMatch.fingerprint(chart)); p.step(SECOND);
            }
            require(!p.host.error().isEmpty(), "duplicate READY cannot renew clock-sync deadline");
        }
        try (Pair p = new Pair()) {
            p.start(chart(60)); p.a.drop = "HOLD"; p.guest.requestPause();
            for (int i = 0; i < 6 && p.guest.error().isEmpty(); i++) p.step(SECOND);
            require(!p.guest.error().isEmpty(), "unacknowledged pause intent times out despite heartbeat");
        }
        try (Pair p = new Pair()) {
            p.start(chart(60)); p.b.drop = "HOLDACK"; p.host.requestPause();
            for (int i = 0; i < 6 && p.host.error().isEmpty(); i++) p.step(SECOND);
            require(!p.host.error().isEmpty(), "unacknowledged effective control times out despite heartbeat");
        }
    }

    public static void disconnect() {
        for (ScenePeer.State state : List.of(ScenePeer.State.CLOSED, ScenePeer.State.FAILED)) try (Pair p = new Pair()) {
            p.a.state = state; p.step(20_000_000L); require(!p.host.error().isEmpty(), "terminal transport stops session");
        }
        try (Pair p = new Pair()) {
            p.start(chart(60)); p.a.blocked = true; p.host.progress(session(chart(60)), 0, false);
            require(!p.host.error().isEmpty(), "send backpressure stops session");
        }
    }

    private static void await(BooleanSupplier condition, Runnable tick) throws Exception {
        long deadline = System.nanoTime() + 7 * SECOND;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { tick.run(); Thread.sleep(2); }
        require(condition.getAsBoolean(), "real loopback condition timed out");
    }

    public static void loopback() throws Exception {
        try (ManagedSceneNetwork networkA = new ManagedSceneNetwork(); ManagedSceneNetwork networkB = new ManagedSceneNetwork()) {
            int port; try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
            ScenePeer a = networkA.host(port), b = null;
            long deadline = System.nanoTime() + 5 * SECOND;
            do {
                b = networkB.connect("127.0.0.1", port); ScenePeer attempt = b;
                await(() -> attempt.state() == ScenePeer.State.CONNECTED || attempt.state() == ScenePeer.State.FAILED, () -> { });
                if (b.state() != ScenePeer.State.CONNECTED) Thread.sleep(5);
            } while (b.state() != ScenePeer.State.CONNECTED && System.nanoTime() < deadline);
            require(b.state() == ScenePeer.State.CONNECTED, "real socket connected");
            try (OnlineMatch host = new OnlineMatch(a, true, List.of("s1", "s2"));
                 OnlineMatch guest = new OnlineMatch(b, false, List.of("s1"))) {
                Runnable tick = () -> { host.tick(System.nanoTime()); guest.tick(System.nanoTime());
                    require(host.error().isEmpty(), host.error()); require(guest.error().isEmpty(), guest.error()); };
                await(() -> host.connected() && guest.connected(), tick);
                Chart chart = chart(600);
                host.offer(SONG, Role.SITAR, Difficulty.EXPERT, true);
                await(() -> guest.round() == 1, tick); host.ready(chart, RATE); guest.ready(chart, RATE);
                await(() -> host.startDue() && guest.startDue(), tick); host.started(); guest.started();
                guest.requestPause(); await(() -> host.paused() && guest.paused(), tick);
                guest.requestResume(); await(() -> !host.paused() && !guest.paused(), tick);
                RhythmSession local = session(chart); local.input(0, 1, 0, true, false, false);
                local.advance(chart.length() + RATE / 10 + 1); host.progress(local, local.position(), true);
                await(guest::remoteFinished, tick); equal(local.score(), guest.remoteScore(), "real socket final score");
                host.offer(SONG, Role.SITAR, Difficulty.EASY, false); await(() -> guest.round() == 2, tick);
                require(!guest.remoteFinished(), "real socket rematch clears results");
                host.ready(chart, RATE); guest.ready(chart, RATE); await(() -> host.startDue() && guest.startDue(), tick);
                guest.close(); await(() -> a.state() == ScenePeer.State.CLOSED, () -> { });
                host.tick(System.nanoTime()); require(!host.error().isEmpty(), "real EOF stops match");
            }
        }
    }
    public static void presentationFeedback() throws Exception {
        try (Pair p = new Pair()) {
            p.start(chart(60));
            java.lang.reflect.Method method;
            try { method = OnlineMatch.class.getMethod("feedback", long.class, boolean.class, List.class); }
            catch (NoSuchMethodException missing) { throw new AssertionError("Peer performance feedback is missing", missing); }
            method.invoke(p.host, 1000L, false, List.of());
            p.step(60_000_000L);
            var audible = OnlineMatch.class.getMethod("remoteAudible");
            require(!(boolean) audible.invoke(p.guest), "peer must hear the latest shared-part mute");
            equal(0, p.guest.remoteScore(), "presentation does not award score");
            String wire = p.a.last("FEEDBACK"); p.b.inject(wire); p.step(60_000_000L);
            require(!(boolean) audible.invoke(p.guest), "duplicate cannot recover the part");
        }
    }

    public static void oldFeedbackAndPause() {
        try(Pair p=new Pair()) {
            Chart c=chart(60);p.start(c);
            RhythmSession missed=session(c);missed.advance(RATE/5);
            p.host.feedback(missed.position(),false,missed.drainFeedback());p.step(60_000_000L);
            require(!p.guest.remoteAudible(),"partner mute received");
            String old=p.a.last("FEEDBACK");
            p.guest.requestPause();p.settle();require(p.guest.drainCues().isEmpty(),"pause drops pending peer transients");
            p.guest.requestResume();p.settle();require(!p.guest.remoteAudible(),"resume cannot invent a successful hit");
            p.host.offer(SONG,Role.SITAR,Difficulty.MEDIUM,true);p.step(20_000_000L);
            p.b.inject(old);p.step(60_000_000L);p.healthy();
            require(p.guest.remoteAudible() && p.guest.drainCues().isEmpty(),"old cue cannot affect an unprepared rematch");
        }
    }
    public static void feedbackBurstsAndDuplicates() {
        try(Pair p=new Pair()) {
            p.start(chart(60));
            for(int i=0;i<100;i++)p.host.feedback(i,false,List.of(new RhythmSession.Feedback(i,i,0,1,RhythmSession.FeedbackKind.STRIKE,false)));
            p.step(60_000_000L);
            equal(1,p.a.sent.stream().filter(v->v.startsWith("SH2 FEEDBACK ")).count(),"burst coalesces to one packet");
            equal(6,p.guest.drainCues().size(),"cue batch bounded");
            p.b.inject(p.a.last("FEEDBACK"));p.step(60_000_000L);equal(0,p.guest.drainCues().size(),"duplicate packet cannot replay cues");
            p.host.feedback(200,false,List.of(new RhythmSession.Feedback(199,190,0,1,RhythmSession.FeedbackKind.HIT,true),
                    new RhythmSession.Feedback(200,200,0,1,RhythmSession.FeedbackKind.MISS,false)));
            p.step(60_000_000L);equal(1,p.guest.drainCues().size(),"first miss reaches the peer once");
            p.b.inject("SH2 FEEDBACK 1 999 300 0 250:M:0:1");p.step(60_000_000L);p.healthy();
            equal(0,p.guest.drainCues().size(),"same missed note cannot replay under another sequence");
            equal(0,p.guest.remoteScore(),"feedback never awards score");
        }
    }
    public static void feedbackValidation() {
        for(String payload:List.of("0 0 1 0:S:999:1","0 0 0 0:M:0:2","0 0 0 0:S:0:32","0 0 2 -")) {
            try(Pair p=new Pair()){p.start(chart(60));p.b.inject("SH2 FEEDBACK 1 "+payload);p.step(60_000_000L);require(!p.guest.error().isEmpty(),"invalid peer feedback rejected");}
        }
    }

}
