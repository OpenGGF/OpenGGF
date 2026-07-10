package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Per-slot remote playback routing with room roster metadata. */
public final class RemoteGhostRegistry {
    public record RemoteGhost(int slot, String displayName, String character,
                              RemoteGhostPlayback.RenderState state) {
    }

    public record FarPlayer(int slot, String displayName, String character,
                            int cellX, int cellY, int status) { }

    private record Roster(String displayName, String character) {
    }

    private final Map<Integer, RemoteGhostPlayback> playbacks = new LinkedHashMap<>();
    private final Map<Integer, Roster> roster = new LinkedHashMap<>();
    private final Map<Integer, GhostPackets.RosterEntry> rosterState =
            new LinkedHashMap<>();

    public void onAggregate(GhostPackets.Aggregate aggregate) {
        for (GhostPackets.AggregateEntry entry : aggregate.entries()) {
            playbacks.computeIfAbsent(entry.playerSlot(), ignored -> new RemoteGhostPlayback())
                    .onEntry(entry);
        }
    }

    public void onRoomState(List<ControlMessage.PlayerInfo> players) {
        roster.clear();
        for (ControlMessage.PlayerInfo player : players) {
            roster.put(player.slot(), new Roster(player.displayName(), player.character()));
        }
        playbacks.keySet().removeIf(slot -> !roster.containsKey(slot));
        rosterState.keySet().removeIf(slot -> !roster.containsKey(slot));
    }

    public void reset() {
        playbacks.clear();
        rosterState.clear();
    }

    public void onRoster(List<GhostPackets.RosterEntry> entries) {
        rosterState.clear();
        for (GhostPackets.RosterEntry entry : entries) {
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
            Roster info = roster.getOrDefault(slot, new Roster("?", "sonic"));
            GhostPackets.RosterEntry coarse = entry.getValue();
            far.add(new FarPlayer(slot, info.displayName(), info.character(),
                    coarse.cellX(), coarse.cellY(), coarse.status()));
        }
        return List.copyOf(far);
    }

    public List<RemoteGhost> advanceAll(int excludeSlot) {
        List<RemoteGhost> ghosts = new ArrayList<>();
        for (Map.Entry<Integer, RemoteGhostPlayback> entry : playbacks.entrySet()) {
            if (entry.getKey() == excludeSlot) {
                continue;
            }
            Optional<RemoteGhostPlayback.RenderState> state = entry.getValue().advance();
            if (state.isPresent()) {
                Roster info = roster.getOrDefault(entry.getKey(), new Roster("?", "sonic"));
                ghosts.add(new RemoteGhost(entry.getKey(), info.displayName(),
                        info.character(), state.get()));
            }
        }
        return List.copyOf(ghosts);
    }
}
