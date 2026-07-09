package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Authoritative lobby, countdown, race-window, round-end, and standings state. */
public final class HostRoundEngine {
    public enum Phase { LOBBY, COUNTDOWN, RUNNING, ROUND_END }

    public static final long COUNTDOWN_MILLIS = 3000;
    public static final long FINISH_GRACE_MILLIS = 2000;
    public static final long ROUND_END_LINGER_MILLIS = 10_000;

    private record Best(String displayName, String character, int timeFrames,
                        long achievedOrder) {
    }

    private final LongSupplier hubClockMillis;
    private final Consumer<ControlMessage> broadcaster;
    private final Map<Integer, Best> bests = new LinkedHashMap<>();

    private Phase phase = Phase.LOBBY;
    private ControlMessage.RoundConfig config;
    private long countdownEndsAt;
    private long deadline;
    private long roundEndAt;
    private long achievedCounter;

    public HostRoundEngine(LongSupplier hubClockMillis,
                           Consumer<ControlMessage> broadcaster) {
        this.hubClockMillis = hubClockMillis;
        this.broadcaster = broadcaster;
    }

    public Phase phase() {
        return phase;
    }

    public boolean startRound(ControlMessage.RoundConfig newConfig) {
        if (phase != Phase.LOBBY && phase != Phase.ROUND_END) {
            return false;
        }
        long now = hubClockMillis.getAsLong();
        config = newConfig;
        countdownEndsAt = now + COUNTDOWN_MILLIS;
        deadline = countdownEndsAt + newConfig.windowSeconds() * 1000L;
        bests.clear();
        achievedCounter = 0;
        phase = Phase.COUNTDOWN;
        broadcaster.accept(new ControlMessage.RoundStart(config, countdownEndsAt, deadline));
        return true;
    }

    public void onTick() {
        long now = hubClockMillis.getAsLong();
        if (phase == Phase.COUNTDOWN && now >= countdownEndsAt) {
            phase = Phase.RUNNING;
        }
        if (phase == Phase.RUNNING && now > deadline) {
            phase = Phase.ROUND_END;
            roundEndAt = now;
            broadcaster.accept(new ControlMessage.RoundEnd(standings()));
        }
        if (phase == Phase.ROUND_END
                && now >= roundEndAt + ROUND_END_LINGER_MILLIS) {
            phase = Phase.LOBBY;
        }
    }

    public void onAttemptFinish(int slot, String displayName, String character,
                                ControlMessage.AttemptFinish finish,
                                boolean attemptFlagged) {
        long now = hubClockMillis.getAsLong();
        if (phase != Phase.RUNNING || now > deadline + FINISH_GRACE_MILLIS
                || attemptFlagged || finish.timeFrames() <= 0) {
            return;
        }
        Best existing = bests.get(slot);
        if (existing != null && existing.timeFrames() <= finish.timeFrames()) {
            return;
        }
        bests.put(slot, new Best(displayName, character, finish.timeFrames(),
                achievedCounter++));
        broadcaster.accept(new ControlMessage.StandingsDelta(standings()));
    }

    public void onPlayerLeft(int slot) {
        // Best remains visible for the rest of the round.
    }

    public List<ControlMessage.StandingsRow> standings() {
        List<Map.Entry<Integer, Best>> sorted = new ArrayList<>(bests.entrySet());
        sorted.sort((left, right) -> {
            int byTime = Integer.compare(
                    left.getValue().timeFrames(), right.getValue().timeFrames());
            return byTime != 0 ? byTime : Long.compare(
                    left.getValue().achievedOrder(), right.getValue().achievedOrder());
        });
        List<ControlMessage.StandingsRow> rows = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            Map.Entry<Integer, Best> entry = sorted.get(index);
            Best best = entry.getValue();
            rows.add(new ControlMessage.StandingsRow(entry.getKey(), best.displayName(),
                    best.character(), best.timeFrames(), index + 1));
        }
        return List.copyOf(rows);
    }

    public ControlMessage.RoundSnapshot snapshot() {
        return new ControlMessage.RoundSnapshot(
                phase.name(), config, countdownEndsAt, deadline, standings());
    }
}
