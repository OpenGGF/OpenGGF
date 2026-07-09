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

    private record Roster(String displayName, String character) {
    }

    private final Map<Integer, RemoteGhostPlayback> playbacks = new LinkedHashMap<>();
    private final Map<Integer, Roster> roster = new LinkedHashMap<>();

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
    }

    public void reset() {
        playbacks.clear();
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
