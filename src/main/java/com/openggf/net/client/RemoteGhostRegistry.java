package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Per-slot remote playback routing with room roster metadata. */
@com.openggf.game.ModApi
public final class RemoteGhostRegistry {
    @com.openggf.game.ModApi
    public record RemoteGhost(int slot, String displayName, String character,
                              RemoteGhostPlayback.RenderState state) {
    }

    @com.openggf.game.ModApi
    public record FarPlayer(int slot, String displayName, String character,
                            int cellX, int cellY, int status) { }

    private record Roster(String fingerprint, String displayName, String character) {
    }

    // Allow short jitter stalls, then return to the current coarse roster position.
    private static final int STALE_PLAYBACK_FRAMES = 120;
    private final Map<Integer, Integer> quietFrames = new LinkedHashMap<>();
    private final Map<Integer, RemoteGhostPlayback> playbacks = new LinkedHashMap<>();
    private final Map<Integer, Roster> roster = new LinkedHashMap<>();
    private final Map<Integer, GhostPackets.RosterEntry> rosterState =
            new LinkedHashMap<>();

    public void onAggregate(GhostPackets.Aggregate aggregate) {
        for (GhostPackets.AggregateEntry entry : aggregate.entries()) {
            if (!roster.containsKey(entry.playerSlot())) {
                continue;
            }
            quietFrames.put(entry.playerSlot(), 0);
            playbacks.computeIfAbsent(entry.playerSlot(), ignored -> new RemoteGhostPlayback())
                    .onEntry(entry);
        }
    }

    /** A connection's initial snapshot establishes a fresh playback baseline. */
    public void onRoomState(List<ControlMessage.PlayerInfo> players) {
        reset();
        roster.clear();
        for (ControlMessage.PlayerInfo player : players) {
            onPlayerUpsert(player);
        }
    }

    public void onPlayerUpsert(ControlMessage.PlayerInfo player) {
        roster.entrySet().stream()
                .filter(entry -> entry.getKey() != player.slot()
                        && entry.getValue().fingerprint().equals(player.fingerprint()))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::onPlayerLeft);
        Roster previous = roster.get(player.slot());
        if (previous != null && !previous.fingerprint().equals(player.fingerprint())) {
            onPlayerLeft(player.slot());
        }
        roster.put(player.slot(), new Roster(player.fingerprint(), player.displayName(), player.character()));
    }

    public void onPlayerLeft(int slot) {
        roster.remove(slot);
        playbacks.remove(slot);
        quietFrames.remove(slot);
        rosterState.remove(slot);
    }

    public void reset() {
        playbacks.clear();
        quietFrames.clear();
        rosterState.clear();
    }

    public void onRoster(List<GhostPackets.RosterEntry> entries) {
        rosterState.clear();
        for (GhostPackets.RosterEntry entry : entries) {
            if (!roster.containsKey(entry.playerSlot())) continue;
            rosterState.put(entry.playerSlot(), entry);
        }
    }

    public List<FarPlayer> farPlayers(int excludeSlot) {
        List<FarPlayer> far = new ArrayList<>();
        for (Map.Entry<Integer, GhostPackets.RosterEntry> entry : rosterState.entrySet()) {
            int slot = entry.getKey();
            if (slot == excludeSlot || playbacks.containsKey(slot)) {
                continue;
            }
            Roster info = roster.getOrDefault(slot, new Roster("", "?", "sonic"));
            GhostPackets.RosterEntry coarse = entry.getValue();
            far.add(new FarPlayer(slot, info.displayName(), info.character(),
                    coarse.cellX(), coarse.cellY(), coarse.status()));
        }
        return List.copyOf(far);
    }

    public List<RemoteGhost> advanceAll(int excludeSlot) {
        quietFrames.replaceAll((slot, age) -> age + 1);
        playbacks.keySet().removeIf(slot -> quietFrames.getOrDefault(slot, 0) > STALE_PLAYBACK_FRAMES);
        quietFrames.keySet().retainAll(playbacks.keySet());
        List<RemoteGhost> ghosts = new ArrayList<>();
        for (Map.Entry<Integer, RemoteGhostPlayback> entry : playbacks.entrySet()) {
            if (entry.getKey() == excludeSlot) {
                continue;
            }
            Optional<RemoteGhostPlayback.RenderState> state = entry.getValue().advance();
            if (state.isPresent()) {
                Roster info = roster.getOrDefault(entry.getKey(), new Roster("", "?", "sonic"));
                ghosts.add(new RemoteGhost(entry.getKey(), info.displayName(),
                        info.character(), state.get()));
            }
        }
        return List.copyOf(ghosts);
    }
}
