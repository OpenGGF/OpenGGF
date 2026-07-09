package com.openggf.net.master;

import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Broker-loop-confined directory of live authenticated rooms. */
public final class SessionRegistry {
    public static final class RoomCreateException extends Exception {
        public RoomCreateException(String reason) {
            super(reason);
        }
    }

    public record RoomEntry(String roomId, ControlMessage.RoomDescriptor descriptor,
                            String routing, String hostFingerprint, String hostAddress,
                            int directPort, String determinismFingerprint, int playerCount,
                            long lastHeartbeatMillis) {
    }

    private final LongSupplier clock;
    private final MasterConfig config;
    private final Map<String, RoomEntry> rooms = new LinkedHashMap<>();
    private long counter;

    public SessionRegistry(LongSupplier clock, MasterConfig config) {
        this.clock = clock;
        this.config = config;
    }

    public RoomEntry create(ControlMessage.RoomDescriptor descriptor, String routing,
                            String hostFingerprint, String hostAddress, int directPort,
                            String determinismFingerprint) throws RoomCreateException {
        long byIdentity = rooms.values().stream().filter(room ->
                room.hostFingerprint().equals(hostFingerprint)).count();
        if (byIdentity >= config.maxRoomsPerIdentity()) {
            throw new RoomCreateException("room cap per identity reached");
        }
        long byIp = rooms.values().stream().filter(room ->
                room.hostAddress().equals(hostAddress)).count();
        if (byIp >= config.maxRoomsPerIp()) {
            throw new RoomCreateException("room cap per address reached");
        }
        RoomEntry entry = new RoomEntry("r-" + ++counter, descriptor, routing,
                hostFingerprint, hostAddress, directPort, determinismFingerprint, 0,
                clock.getAsLong());
        rooms.put(entry.roomId(), entry);
        return entry;
    }

    public void heartbeat(String roomId, int playerCount) {
        RoomEntry entry = rooms.get(roomId);
        if (entry != null) {
            rooms.put(roomId, new RoomEntry(entry.roomId(), entry.descriptor(),
                    entry.routing(), entry.hostFingerprint(), entry.hostAddress(),
                    entry.directPort(), entry.determinismFingerprint(),
                    Math.max(0, playerCount), clock.getAsLong()));
        }
    }

    public int expireStale() {
        long cutoff = clock.getAsLong() - config.roomHeartbeatTimeoutSeconds() * 1000;
        int before = rooms.size();
        rooms.values().removeIf(room -> "DIRECT".equals(room.routing())
                && room.lastHeartbeatMillis() < cutoff);
        return before - rooms.size();
    }

    public void remove(String roomId) {
        rooms.remove(roomId);
    }

    public List<RoomEntry> removeByHostFingerprint(String hostFingerprint) {
        List<RoomEntry> removed = new ArrayList<>();
        rooms.values().removeIf(room -> {
            if (room.hostFingerprint().equals(hostFingerprint)) {
                removed.add(room);
                return true;
            }
            return false;
        });
        return List.copyOf(removed);
    }

    public Optional<RoomEntry> find(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public List<RoomEntry> list(String gameFilterOrNull) {
        List<RoomEntry> result = new ArrayList<>();
        for (RoomEntry room : rooms.values()) {
            if (gameFilterOrNull == null
                    || room.descriptor().gameId().equals(gameFilterOrNull)) {
                result.add(room);
            }
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    public int totalPages(String gameFilterOrNull) {
        int count = list(gameFilterOrNull).size();
        return count == 0 ? 0
                : (count + config.browserPageSize() - 1) / config.browserPageSize();
    }
}
