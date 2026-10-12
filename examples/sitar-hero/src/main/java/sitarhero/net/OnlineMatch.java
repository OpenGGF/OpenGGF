package sitarhero.net;

import com.openggf.mods.scene.ScenePeer;
import sitarhero.model.Chart;
import sitarhero.model.ChartNote;
import sitarhero.model.Difficulty;
import sitarhero.model.RhythmSession;
import sitarhero.model.Role;
import sitarhero.model.SongCatalog;
import sitarhero.model.SongSpec;

import java.util.List;
import java.util.Objects;

/**
 * Direct peer match protocol. Calls belong to the scene thread; tick uses its local
 * monotonic input clock. Remote progress is display data, never judgment or record authority.
 * SH2 is an application protocol over the ordered, bounded ScenePeer transport.
 */
public final class OnlineMatch implements AutoCloseable {
    /** Peer cues are presentation-only, in the agreed song sample coordinate. */
    public record Cue(long sample, int noteIndex, int lanes, boolean strike) { }
    private final java.util.ArrayDeque<Cue> remoteCues = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Cue> outgoingCues = new java.util.ArrayDeque<>();
    private boolean remoteAudible = true, localAudible = true, lastEventAudible = true, feedbackDirty;
    private long feedbackPosition, feedbackSentAt = Long.MIN_VALUE, feedbackSequence, remoteFeedbackSequence = -1;
    private long remoteFeedbackPosition = Long.MIN_VALUE;
    private int lastRemoteMiss = -1;

    public record Choice(String song, Role role, Difficulty difficulty, boolean coop) { }

    private static final long SECOND = 1_000_000_000L;
    private static final long RETRY = SECOND / 4;
    private static final long CONTROL_TIMEOUT = 5 * SECOND;
    private static final int MAX_ROUND = 1_000_000;
    private static final int MAX_NOTES = 1_000_000;
    private static final long MAX_SEQUENCE = 1_000_000_000L;

    private final ScenePeer peer;
    private final boolean host;
    private final List<String> games;
    private List<String> remoteGames = List.of();
    private boolean hello, sentHello, localReady, remoteReady, localFinished, remoteFinished, closed;
    private boolean started, paused, clockSet, startScheduled, startAck, pingPending, syncStarted;
    private String error = "", localHash = "", remoteHash = "";
    private int round, localRate, remoteRate, noteCount, pingSamples;
    private long now, connectedAt, lastReceive, heartbeat, offeredAt, syncAt;
    private long localSequence, remoteSequence = -1;
    private long pingSent, pingRetryAt, bestRtt = Long.MAX_VALUE, offset, startAt, startSentAt;
    private long chartLength;
    private Chart localChart;
    private Choice choice;

    // Each player owns one pause intent. Only the host assigns effective-control sequences.
    private boolean localPause, remotePause, intentPending, controlScheduled, controlPause, controlAck;
    private long localIntent, remoteIntent, controlSequence, appliedControl, controlAt, wireControlAt;
    private long intentSince, intentSentAt, controlSince, controlSentAt;
    private long remoteScore, remotePosition;
    private int remoteHits, remoteMisses;
    private double remoteRock = .5;

    public OnlineMatch(ScenePeer peer, boolean host, List<String> games) {
        this.peer = Objects.requireNonNull(peer);
        this.host = host;
        this.games = List.copyOf(games);
        validateGames(this.games);
    }

    public void tick(long timestamp) {
        if (closed || !error.isEmpty()) return;
        now = clockSet ? Math.max(now, timestamp) : timestamp;
        clockSet = true;
        ScenePeer.State state = peer.state();
        if (state == ScenePeer.State.FAILED || state == ScenePeer.State.CLOSED) {
            fail(peer.error() == null ? "Peer disconnected. Return to title and reconnect." : peer.error());
            return;
        }
        if (state != ScenePeer.State.CONNECTED) return;
        if (!sentHello) {
            connectedAt = lastReceive = heartbeat = now;
            sentHello = true;
            send("HELLO " + String.join(",", games));
        }
        for (ScenePeer.Message message : peer.poll()) {
            try {
                // The selector can observe a packet after the scene sampled this tick's clock.
                now = Math.max(now, message.receivedNanos());
                receive(message);
                lastReceive = Math.max(lastReceive, message.receivedNanos());
            } catch (IllegalArgumentException | ArithmeticException bad) {
                fail("Invalid peer message; match stopped");
            }
            if (!error.isEmpty()) return;
        }
        if (!hello && now - connectedAt >= 10 * SECOND) { fail("Peer handshake timed out; match stopped"); return; }
        if (now - lastReceive >= 30 * SECOND) { fail("Peer timed out; match stopped"); return; }
        if (now - heartbeat >= SECOND) { send("HEARTBEAT"); heartbeat = now; }
        if (choice != null && (!localReady || !remoteReady) && now - offeredAt >= 120 * SECOND) {
            fail("Song preparation timed out; match stopped"); return;
        }
        if (host && localReady && remoteReady && !startScheduled) {
            if (!compatible()) return;
            if (now - syncAt >= 15 * SECOND) { fail("Peer clock synchronization timed out; match stopped"); return; }
            if (pingSamples < 3) {
                if (!pingPending || now - pingRetryAt >= 3 * SECOND) {
                    pingSent = pingRetryAt = now;
                    pingPending = true;
                    send("PING " + round + " " + pingSent);
                }
            } else {
                // Allow a complete acknowledgment round trip before the count-in begins.
                startAt = Math.addExact(now, Math.max(SECOND, 2 * bestRtt + SECOND / 2));
                startScheduled = true;
                sendStart();
            }
        }
        if (host && startScheduled && !startAck) {
            if (now >= startAt) { fail("Peer did not acknowledge the count-in; match stopped"); return; }
            if (now - startSentAt >= RETRY) sendStart();
        }
        if (intentPending) {
            if (now - intentSince >= CONTROL_TIMEOUT) { fail("Peer pause request timed out; match stopped"); return; }
            if (now - intentSentAt >= RETRY) sendIntent();
        }
        if (host && controlSequence > 0 && !controlAck) {
            if (now - controlSince >= CONTROL_TIMEOUT) { fail("Peer pause synchronization timed out; match stopped"); return; }
            if (now - controlSentAt >= RETRY) sendControl();
        }
        if (controlScheduled && now >= controlAt) {
            paused = controlPause;
            appliedControl = controlSequence;
            controlScheduled = false;
        }
        if (paused) { outgoingCues.clear(); remoteCues.clear(); }
        if (started && !paused && !localFinished && feedbackDirty
                && (feedbackSentAt == Long.MIN_VALUE || now - feedbackSentAt >= SECOND / 20)) sendFeedback();
    }

    private void receive(ScenePeer.Message message) {
        String text = message.text();
        if (text == null || text.length() > 1024) throw new IllegalArgumentException();
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) < ' ' || text.charAt(i) > '~') throw new IllegalArgumentException();
        String[] f = text.split(" ", -1);
        if (f.length < 2) throw new IllegalArgumentException();
        if (!f[0].equals("SH2")) { fail("Protocol mismatch. Use matching Sitar Hero mod versions."); return; }
        for (String field : f) if (field.isEmpty()) throw new IllegalArgumentException();
        switch (f[1]) {
            case "HELLO" -> {
                length(f, 3);
                List<String> offeredGames = List.of(f[2].split(",", -1));
                validateGames(offeredGames);
                if (hello && !remoteGames.equals(offeredGames)) throw new IllegalArgumentException();
                remoteGames = offeredGames;
                hello = true;
                if (commonGames().isEmpty()) fail("No shared ROM library. Both players need at least one matching game.");
            }
            case "HEARTBEAT" -> { length(f, 2); if (!hello) throw new IllegalArgumentException(); }
            case "OFFER" -> {
                length(f, 7);
                int next = integer(f[2], 1, MAX_ROUND);
                Role role = Role.valueOf(f[4]);
                Difficulty level = Difficulty.valueOf(f[5]);
                if (!f[6].equals("coop") && !f[6].equals("versus")) throw new IllegalArgumentException();
                SongSpec song = sharedSong(f[3]);
                if (!song.availableRoles().contains(role) || !hello || host) throw new IllegalArgumentException();
                Choice nextChoice = new Choice(song.id(), role, level, f[6].equals("coop"));
                if (next < round) return;
                if (next == round) {
                    if (!nextChoice.equals(choice)) throw new IllegalArgumentException();
                    return;
                }
                if (next != round + 1) throw new IllegalArgumentException();
                resetRound(next, nextChoice);
            }
            case "READY" -> {
                length(f, 5);
                int rate = integer(f[3], 8_000, 192_000);
                if (!f[4].matches("[0-9a-f]{1,16}")) throw new IllegalArgumentException();
                if (!currentRound(f[2])) return;
                if (remoteReady && (remoteRate != rate || !remoteHash.equals(f[4]))) throw new IllegalArgumentException();
                remoteRate = rate; remoteHash = f[4]; remoteReady = true;
                preparationComplete();
            }
            case "PING" -> {
                length(f, 4);
                long sent = number(f[3]);
                if (!currentRound(f[2])) return;
                if (host || !localReady || !remoteReady || !compatible()) throw new IllegalArgumentException();
                long received = message.receivedNanos();
                send("PONG " + round + " " + sent + " " + received + " " + now);
            }
            case "PONG" -> {
                length(f, 6);
                long t1 = number(f[3]), t2 = number(f[4]), t3 = number(f[5]);
                long processing = Math.subtractExact(t3, t2);
                if (processing < 0 || processing > 15 * SECOND) throw new IllegalArgumentException();
                if (!currentRound(f[2])) return;
                if (!host || !localReady || !remoteReady) throw new IllegalArgumentException();
                // Replies to a superseded probe (or duplicate replies) carry no new authority.
                if (!pingPending || t1 != pingSent) return;
                long t4 = message.receivedNanos();
                long rtt = Math.subtractExact(Math.subtractExact(t4, t1), processing);
                if (rtt < 0) throw new IllegalArgumentException();
                pingPending = false;
                if (rtt > 2 * SECOND) return; // discard slow samples; the synchronization deadline still runs
                if (rtt < bestRtt) {
                    bestRtt = rtt;
                    long first = Math.subtractExact(t2, t1), second = Math.subtractExact(t3, t4);
                    offset = first / 2 + second / 2 + (first % 2 + second % 2) / 2;
                }
                pingSamples++;
            }
            case "START" -> {
                length(f, 5);
                long hostStart = number(f[3]), estimate = number(f[4]);
                long localStart = Math.addExact(hostStart, estimate);
                if (!currentRound(f[2])) return;
                if (host || !localReady || !remoteReady || !compatible()) throw new IllegalArgumentException();
                if (startScheduled) {
                    if (localStart != startAt || estimate != offset) throw new IllegalArgumentException();
                } else {
                    future(localStart, 10 * SECOND, 0);
                    offset = estimate; startAt = localStart; startScheduled = true; startAck = true;
                }
                send("STARTACK " + round + " " + hostStart);
            }
            case "STARTACK" -> {
                length(f, 4);
                long acknowledged = number(f[3]);
                if (!currentRound(f[2])) return;
                if (!host || !startScheduled || acknowledged != startAt) throw new IllegalArgumentException();
                startAck = true;
            }
            case "PAUSE", "RESUME" -> {
                length(f, 4);
                long request = boundedLong(f[3], 1, MAX_SEQUENCE);
                if (!currentRound(f[2])) return;
                // Offset uncertainty and a clearing network queue can put a valid request
                // before our local due edge. It must not advance the scene's start callback.
                if (!host || !startScheduled || !startAck) throw new IllegalArgumentException();
                boolean pause = f[1].equals("PAUSE");
                if (remoteFinished) return;
                if (request < remoteIntent) return;
                if (request == remoteIntent) {
                    if (pause != remotePause) throw new IllegalArgumentException();
                    sendControl();
                    return;
                }
                remoteIntent = request; remotePause = pause;
                scheduleControl();
            }
            case "HOLD" -> {
                length(f, 7);
                long sequence = boundedLong(f[3], 1, MAX_SEQUENCE);
                long deadline = number(f[4]);
                boolean pause = pauseFlag(f[5]);
                long acknowledgedIntent = boundedLong(f[6], 0, MAX_SEQUENCE);
                if (!currentRound(f[2])) return;
                if (host || !startScheduled || !startAck || acknowledgedIntent > localIntent) throw new IllegalArgumentException();
                if (sequence < controlSequence) return;
                long localDeadline = Math.addExact(deadline, offset);
                if (sequence == controlSequence) {
                    if (deadline != wireControlAt || pause != controlPause) throw new IllegalArgumentException();
                } else {
                    future(localDeadline, 10 * SECOND, CONTROL_TIMEOUT);
                    if (localDeadline < startAt) throw new IllegalArgumentException();
                    controlSequence = sequence; wireControlAt = deadline;
                    controlAt = localDeadline; controlPause = pause;
                    controlScheduled = sequence > appliedControl;
                }
                if (acknowledgedIntent == localIntent) intentPending = false;
                send("HOLDACK " + round + " " + sequence);
            }
            case "HOLDACK" -> {
                length(f, 4);
                long sequence = boundedLong(f[3], 1, MAX_SEQUENCE);
                if (!currentRound(f[2])) return;
                if (!host || sequence > controlSequence) throw new IllegalArgumentException();
                if (sequence == controlSequence) controlAck = true;
            }
            case "STATE" -> receiveState(f);
            case "FEEDBACK" -> receiveFeedback(f);
            default -> throw new IllegalArgumentException();
        }
    }

    private void receiveFeedback(String[] f) {
        length(f, 7);
        long sequence = boundedLong(f[3], 0, MAX_SEQUENCE);
        long position = number(f[4]);
        boolean audible = switch (f[5]) { case "0" -> false; case "1" -> true; default -> throw new IllegalArgumentException(); };
        var cues = new java.util.ArrayList<Cue>();
        if (!f[6].equals("-")) {
            String[] entries = f[6].split(",", -1);
            if (entries.length > 6) throw new IllegalArgumentException();
            for (String entry : entries) {
                String[] item = entry.split(":", -1);
                if (item.length != 4) throw new IllegalArgumentException();
                long sample = number(item[0]);
                boolean strike = switch (item[1]) { case "S" -> true; case "M" -> false; default -> throw new IllegalArgumentException(); };
                int note = integer(item[2], 0, MAX_NOTES - 1), lanes = integer(item[3], 0, 31);
                cues.add(new Cue(sample,note,lanes,strike));
            }
        }
        if (!currentRound(f[2])) return;
        if (!localReady || !remoteReady || !startScheduled || !startAck
                || position < -localRate * 10L || position > chartLength + localRate * 10L)
            throw new IllegalArgumentException();
        for (Cue cue : cues) {
            if (cue.sample() > position || cue.sample() < -localRate * 10L || cue.sample() > chartLength + localRate * 10L
                    || cue.noteIndex() >= noteCount || !cue.strike() && cue.lanes() != localChart.notes().get(cue.noteIndex()).lanes())
                throw new IllegalArgumentException();
        }
        if (sequence <= remoteFeedbackSequence || remoteFinished) return;
        remoteFeedbackSequence = sequence;
        if (position < remoteFeedbackPosition) return;
        remoteFeedbackPosition = position; remoteAudible = audible;
        if (paused) return;
        for (Cue cue : cues) {
            if (!cue.strike()) {
                if (cue.noteIndex() <= lastRemoteMiss) continue;
                lastRemoteMiss = cue.noteIndex();
            }
            if (remoteCues.size() == 32) remoteCues.removeFirst();
            remoteCues.addLast(cue);
        }
    }

    /** Coalesced at <=20 packets/second; scoring STATE has a separate sequence and authority. */
    public void feedback(long position, boolean audible, List<RhythmSession.Feedback> events) {
        if (!connected() || !started || localFinished) return;
        if (position < -localRate * 10L || position > chartLength + localRate * 10L) throw new IllegalArgumentException();
        feedbackDirty |= localAudible != audible || !events.isEmpty()
                || feedbackSentAt == Long.MIN_VALUE || now - feedbackSentAt >= SECOND / 4;
        feedbackPosition = position; localAudible = audible;
        for (RhythmSession.Feedback event : events) {
            boolean cue = event.kind() == RhythmSession.FeedbackKind.STRIKE
                    || event.kind() == RhythmSession.FeedbackKind.MISS && lastEventAudible;
            lastEventAudible = event.audible();
            if (!cue || paused || event.noteIndex() < 0 || event.noteIndex() >= noteCount) continue;
            if (outgoingCues.size() == 6) outgoingCues.removeFirst();
            outgoingCues.addLast(new Cue(event.sample(),event.noteIndex(),event.lanes(),event.kind()==RhythmSession.FeedbackKind.STRIKE));
        }
    }
    private void sendFeedback() {
        StringBuilder entries = new StringBuilder();
        for (Cue cue : outgoingCues) {
            if (!entries.isEmpty()) entries.append(',');
            entries.append(cue.sample()).append(':').append(cue.strike()?'S':'M').append(':')
                    .append(cue.noteIndex()).append(':').append(cue.lanes());
        }
        send("FEEDBACK " + round + " " + feedbackSequence + " " + feedbackPosition + " " + (localAudible?"1":"0")
                + " " + (entries.isEmpty()?"-":entries));
        feedbackSequence = nextSequence(feedbackSequence); feedbackSentAt = now;
        outgoingCues.clear(); feedbackDirty = false;
    }
    public boolean remoteAudible() { return remoteAudible && !remoteFinished; }
    public List<Cue> drainCues() { var result=List.copyOf(remoteCues);remoteCues.clear();return result; }
    public void discardCues() { remoteCues.clear();outgoingCues.clear(); }

    private void receiveState(String[] f) {
        length(f, 11);
        long sequence = boundedLong(f[3], 0, MAX_SEQUENCE);
        long position = number(f[4]);
        long score = boundedLong(f[5], 0, 1_000_000_000L);
        int hits = integer(f[6], 0, MAX_NOTES), misses = integer(f[7], 0, MAX_NOTES);
        double rock = Double.parseDouble(f[8]);
        boolean finished = switch (f[9]) { case "0" -> false; case "1" -> true; default -> throw new IllegalArgumentException(); };
        int count = integer(f[10], 0, MAX_NOTES);
        if (!Double.isFinite(rock) || rock < 0 || rock > 1 || hits + misses > count) throw new IllegalArgumentException();
        if (!currentRound(f[2])) return;
        // Reports remain display-only even if their sender reaches its estimated
        // start earlier. Rejecting them here would lose a terminal short-session report.
        if (!localReady || !remoteReady || !startScheduled || !startAck) throw new IllegalArgumentException();
        if (position < -localRate * 10L || position > chartLength + localRate * 10L || count != noteCount)
            throw new IllegalArgumentException();
        if (sequence <= remoteSequence || remoteFinished) return;
        if (score < remoteScore || hits < remoteHits || misses < remoteMisses) throw new IllegalArgumentException();
        remoteSequence = sequence; remotePosition = position; remoteScore = score;
        remoteHits = hits; remoteMisses = misses; remoteRock = rock; remoteFinished = finished;
        if (host && finished && remotePause) { remotePause = false; scheduleControl(); }
    }

    /** Only obsolete, well-formed round messages are ignored. Future rounds require a host OFFER. */
    private boolean currentRound(String text) {
        int messageRound = integer(text, 1, MAX_ROUND);
        if (messageRound < round) return false;
        if (!hello || choice == null || messageRound != round) throw new IllegalArgumentException();
        return true;
    }

    public void offer(SongSpec song, Role role, Difficulty level, boolean coop) {
        if (!host || !connected()) throw new IllegalStateException("Host and shared ROM library required");
        SongSpec shared = sharedSong(Objects.requireNonNull(song).id());
        Objects.requireNonNull(level);
        if (!shared.availableRoles().contains(role)) throw new IllegalArgumentException("Unavailable musical role");
        if (round >= MAX_ROUND) throw new IllegalStateException("Reconnect before starting another round");
        resetRound(round + 1, new Choice(shared.id(), role, level, coop));
        send("OFFER " + round + " " + shared.id() + " " + role.name() + " " + level.name() + " " + (coop ? "coop" : "versus"));
    }

    private SongSpec sharedSong(String id) {
        return SongCatalog.available(commonGames()).stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Song requires a shared ROM"));
    }

    private void resetRound(int next, Choice nextChoice) {
        round = next; choice = nextChoice; offeredAt = now;
        remoteCues.clear(); outgoingCues.clear();
        remoteAudible = localAudible = lastEventAudible = true; feedbackDirty = false;
        feedbackSentAt = remoteFeedbackPosition = Long.MIN_VALUE;
        feedbackPosition = feedbackSequence = 0; remoteFeedbackSequence = -1; lastRemoteMiss = -1;
        localReady = remoteReady = localFinished = remoteFinished = false;
        localHash = remoteHash = ""; localRate = remoteRate = noteCount = 0; chartLength = 0; localChart = null;
        startScheduled = startAck = started = paused = pingPending = syncStarted = false;
        startAt = offset = 0; pingSamples = 0; bestRtt = Long.MAX_VALUE;
        localPause = remotePause = intentPending = controlScheduled = controlPause = controlAck = false;
        localIntent = remoteIntent = controlSequence = appliedControl = controlAt = wireControlAt = 0;
        localSequence = 0; remoteSequence = -1; remotePosition = remoteScore = 0;
        remoteHits = remoteMisses = 0; remoteRock = .5;
    }

    public void ready(Chart chart, int rate) {
        if (choice == null || localReady || !connected()) throw new IllegalStateException("No offered round");
        Objects.requireNonNull(chart);
        if (rate < 8_000 || rate > 192_000 || chart.length() > rate * 600L || chart.notes().size() > MAX_NOTES)
            throw new IllegalArgumentException("Chart exceeds supported rate, ten-minute duration or note count");
        localChart = chart; chartLength = chart.length(); noteCount = chart.notes().size();
        localHash = fingerprint(chart); localRate = rate; localReady = true;
        send("READY " + round + " " + rate + " " + localHash);
        preparationComplete();
    }

    private void preparationComplete() {
        if (localReady && remoteReady && !syncStarted && compatible()) {
            syncAt = now;
            syncStarted = true;
        }
    }

    private boolean compatible() {
        if (localRate == remoteRate && localHash.equals(remoteHash)) return true;
        fail("Chart or audio-rate mismatch. Use matching mod builds and audio rates.");
        return false;
    }

    public static String fingerprint(Chart chart) {
        long hash = 0xcbf29ce484222325L;
        hash = (hash ^ chart.length()) * 0x100000001b3L;
        hash = (hash ^ chart.samplesPerBeat()) * 0x100000001b3L;
        hash = (hash ^ chart.notes().size()) * 0x100000001b3L;
        for (ChartNote note : chart.notes()) {
            long[] fields = {note.onset(), note.end(), note.lanes(), note.hopo() ? 1 : 0, note.phrase(), note.sustainTicks()};
            for (long value : fields) hash = (hash ^ value) * 0x100000001b3L;
        }
        return Long.toHexString(hash);
    }

    public boolean startDue() {
        return connected() && localReady && remoteReady && startScheduled && startAck && now >= startAt && !started;
    }
    public void started() {
        if (!startDue()) throw new IllegalStateException("Count-in is not due");
        started = true;
    }
    public void requestPause() { requestControl(true); }
    public void requestResume() { requestControl(false); }

    private void requestControl(boolean pause) {
        if (!connected() || !started || localFinished || localPause == pause) return;
        localPause = pause;
        if (host) scheduleControl();
        else {
            localIntent = nextSequence(localIntent);
            intentPending = true; intentSince = now;
            sendIntent();
        }
    }

    private void sendIntent() {
        send((localPause ? "PAUSE " : "RESUME ") + round + " " + localIntent);
        intentSentAt = now;
    }

    private void scheduleControl() {
        boolean target = localPause || remotePause;
        if (controlSequence == 0 || target != (controlScheduled ? controlPause : paused)) {
            controlSequence = nextSequence(controlSequence);
            controlPause = target;
            controlAt = wireControlAt = Math.addExact(Math.max(now, startAt), Math.max(SECOND / 2, bestRtt + SECOND / 4));
            controlScheduled = true;
        }
        // Also acknowledges a new request that leaves the effective pause unchanged.
        controlAck = false; controlSince = now;
        sendControl();
    }

    private void sendControl() {
        send("HOLD " + round + " " + controlSequence + " " + wireControlAt + " " + (controlPause ? "P" : "R") + " " + remoteIntent);
        controlSentAt = now;
    }

    private void sendStart() {
        send("START " + round + " " + startAt + " " + offset);
        startSentAt = now;
    }

    public void progress(RhythmSession session, long position, boolean finished) {
        if (!connected() || !started || localFinished) return;
        if (!localChart.equals(session.chart()) || position < -localRate * 10L || position > chartLength + localRate * 10L)
            throw new IllegalArgumentException("Progress must belong to the prepared chart and bounded clock");
        send("STATE " + round + " " + localSequence + " " + position + " " + session.score() + " " + session.hits()
                + " " + session.misses() + " " + session.rock() + " " + (finished ? "1" : "0") + " " + noteCount);
        localSequence = nextSequence(localSequence);
        if (finished) {
            intentPending = false;
            boolean ownedPause = localPause;
            localPause = false;
            if (host && ownedPause) scheduleControl();
        }
        localFinished = finished;
    }

    private static long nextSequence(long sequence) {
        if (sequence >= MAX_SEQUENCE) throw new IllegalStateException("Reconnect before sequence exhaustion");
        return sequence + 1;
    }
    private void future(long deadline, long lead, long lateness) {
        long remaining = Math.subtractExact(deadline, now);
        if (remaining < -lateness || remaining > lead) throw new IllegalArgumentException();
    }
    private void send(String text) {
        if (!closed && error.isEmpty() && !peer.send("SH2 " + text)) fail("Peer send queue unavailable; match stopped");
    }
    private void fail(String text) { if (error.isEmpty()) error = text; peer.close(); }
    private static void validateGames(List<String> ids) {
        if (ids.isEmpty() || ids.size() > 3 || ids.stream().distinct().count() != ids.size() || !List.of("s1", "s2", "s3k").containsAll(ids))
            throw new IllegalArgumentException("Invalid ROM library");
    }
    private static boolean pauseFlag(String text) {
        return switch (text) { case "P" -> true; case "R" -> false; default -> throw new IllegalArgumentException(); };
    }
    private static void length(String[] fields, int count) { if (fields.length != count) throw new IllegalArgumentException(); }
    private static int integer(String text, int min, int max) { return (int) boundedLong(text, min, max); }
    private static long number(String text) {
        if (!text.matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException();
        return Long.parseLong(text);
    }
    private static long boundedLong(String text, long min, long max) {
        long value = number(text);
        if (value < min || value > max) throw new IllegalArgumentException();
        return value;
    }

    public boolean connected() { return !closed && hello && error.isEmpty() && peer.state() == ScenePeer.State.CONNECTED; }
    public boolean host() { return host; }
    public int round() { return round; }
    public Choice choice() { return choice; }
    public boolean paused() { return paused; }
    public boolean ready() { return localReady; }
    public String error() { return error; }
    public String status() {
        if (!error.isEmpty()) return error;
        if (!connected()) return peer.state().name();
        if (started) return localFinished ? "Waiting for peer result / host rematch" : paused ? "Match paused" : "Match playing";
        if (!localReady) return "Connected / host chooses song";
        return startScheduled ? "Synchronized count-in" : "Waiting for peer readiness";
    }
    public List<String> commonGames() { return games.stream().filter(remoteGames::contains).toList(); }
    public long remoteScore() { return remoteScore; }
    public int remoteHits() { return remoteHits; }
    public double remoteRock() { return remoteRock; }
    public long remotePosition() { return remotePosition; }
    public boolean remoteFinished() { return remoteFinished; }
    @Override public void close() { closed = true; peer.close(); }
}
