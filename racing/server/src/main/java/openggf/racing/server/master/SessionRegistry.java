package openggf.racing.server.master;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.protocol.RoomAccessPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Broker-loop-confined directory of live authenticated rooms. */
public final class SessionRegistry {
    public record RegistrationLease(HubConnection source, long generation) {
        public RegistrationLease {
            java.util.Objects.requireNonNull(source);
            if (generation < 1) throw new IllegalArgumentException("invalid registration generation");
        }

        @Override public boolean equals(Object other) {
            return other instanceof RegistrationLease lease
                    && source == lease.source && generation == lease.generation;
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(source) + Long.hashCode(generation);
        }
    }

    public static final class RoomCreateException extends Exception {
        public RoomCreateException(String reason) {
            super(reason);
        }
    }

    public record RoomEntry(String roomId, ControlMessage.RoomDescriptor descriptor,
                            String routing, String hostFingerprint, String hostAddress,
                            int directPort, String determinismFingerprint, int playerCount,
                            long lastHeartbeatMillis, List<String> voteTrackKeys,
                            String certificateSha256, RegistrationLease lease,
                            String instanceId, long accessEpoch, long inviteEpoch) {
        public RoomEntry {
            voteTrackKeys = List.copyOf(voteTrackKeys == null ? List.of() : voteTrackKeys);
        }
    }

    private final LongSupplier clock;
    private final MasterConfig config;
    private final int capacity;
    private final Map<String, RoomEntry> rooms = new LinkedHashMap<>();
    private final Map<String, RoomEntry> pending = new LinkedHashMap<>();
    private long counter;

    public SessionRegistry(LongSupplier clock, MasterConfig config) {
        this(clock, config, 10_000);
    }

    SessionRegistry(LongSupplier clock, MasterConfig config, int capacity) {
        this.clock = clock;
        this.config = config;
        if (capacity < 1) throw new IllegalArgumentException("invalid room capacity");
        this.capacity = capacity;
    }

    public RoomEntry create(ControlMessage.RoomDescriptor descriptor, String routing,
                            String hostFingerprint, String hostAddress, int directPort,
                            String determinismFingerprint) throws RoomCreateException {
        return create(descriptor, routing, hostFingerprint, hostAddress, directPort,
                determinismFingerprint, List.of(), null);
    }

    public RoomEntry create(ControlMessage.RoomDescriptor descriptor, String routing,
                            String hostFingerprint, String hostAddress, int directPort,
                            String determinismFingerprint, List<String> voteTrackKeys)
            throws RoomCreateException {
        return create(descriptor, routing, hostFingerprint, hostAddress, directPort,
                determinismFingerprint, voteTrackKeys, null);
    }

    public RoomEntry create(ControlMessage.RoomDescriptor descriptor, String routing,
                            String hostFingerprint, String hostAddress, int directPort,
                            String determinismFingerprint, List<String> voteTrackKeys,
                            String certificateSha256) throws RoomCreateException {
        return create(descriptor, routing, hostFingerprint, hostAddress, directPort,
                determinismFingerprint, voteTrackKeys, certificateSha256, null);
    }

    public RoomEntry create(ControlMessage.RoomDescriptor descriptor, String routing,
                            String hostFingerprint, String hostAddress, int directPort,
                            String determinismFingerprint, List<String> voteTrackKeys,
                            String certificateSha256, RegistrationLease lease)
            throws RoomCreateException {
        RoomEntry entry = reserve(descriptor, routing, hostFingerprint, hostAddress,
                directPort, determinismFingerprint, voteTrackKeys, certificateSha256,
                lease, java.util.UUID.randomUUID().toString(), 0);
        publish(entry);
        return entry;
    }

    public RoomEntry reserve(ControlMessage.RoomDescriptor descriptor, String routing,
                             String hostFingerprint, String hostAddress, int directPort,
                             String determinismFingerprint, List<String> voteTrackKeys,
                             String certificateSha256, RegistrationLease lease,
                             String instanceId, long accessEpoch) throws RoomCreateException {
        if (rooms.size() + pending.size() >= capacity) {
            throw new RoomCreateException("room directory full");
        }
        List<RoomEntry> allocated = new ArrayList<>(rooms.values());
        allocated.addAll(pending.values());
        long byIdentity = allocated.stream().filter(room ->
                room.hostFingerprint().equals(hostFingerprint)).count();
        if (byIdentity >= config.maxRoomsPerIdentity()) {
            throw new RoomCreateException("room cap per identity reached");
        }
        long byIp = allocated.stream().filter(room ->
                room.hostAddress().equals(hostAddress)).count();
        if (byIp >= config.maxRoomsPerIp()) {
            throw new RoomCreateException("room cap per address reached");
        }
        RoomEntry entry = new RoomEntry("r-" + ++counter, descriptor, routing,
                hostFingerprint, hostAddress, directPort, determinismFingerprint, 0,
                clock.getAsLong(), voteTrackKeys, certificateSha256, lease,
                instanceId, accessEpoch, 0);
        pending.put(entry.roomId(), entry);
        return entry;
    }

    public boolean publish(RoomEntry entry) {
        if (!pending.remove(entry.roomId(), entry)) return false;
        rooms.put(entry.roomId(), entry);
        return true;
    }

    public void heartbeat(String roomId, int playerCount) {
        RoomEntry entry = rooms.get(roomId);
        if (entry != null) {
            rooms.put(roomId, new RoomEntry(entry.roomId(), entry.descriptor(),
                    entry.routing(), entry.hostFingerprint(), entry.hostAddress(),
                    entry.directPort(), entry.determinismFingerprint(),
                    Math.max(0, Math.min(playerCount, entry.descriptor().maxPlayers())),
                    clock.getAsLong(), entry.voteTrackKeys(), entry.certificateSha256(),
                    entry.lease(), entry.instanceId(), entry.accessEpoch(), entry.inviteEpoch()));
        }
    }

    public boolean updateTrack(String roomId, String hostFingerprint, int zone, int act) {
        RoomEntry entry = rooms.get(roomId);
        if (entry == null || !entry.hostFingerprint().equals(hostFingerprint)) {
            return false;
        }
        ControlMessage.RoomDescriptor old = entry.descriptor();
        ControlMessage.RoomDescriptor updated = new ControlMessage.RoomDescriptor(
                old.name(), old.gameId(), zone, act, old.characterPolicy(),
                old.lockedCharacter(), old.maxPlayers(), old.verified(), old.accessPolicy());
        rooms.put(roomId, new RoomEntry(entry.roomId(), updated, entry.routing(),
                entry.hostFingerprint(), entry.hostAddress(), entry.directPort(),
                entry.determinismFingerprint(), entry.playerCount(),
                entry.lastHeartbeatMillis(), entry.voteTrackKeys(),
                entry.certificateSha256(), entry.lease(), entry.instanceId(),
                entry.accessEpoch(), entry.inviteEpoch()));
        return true;
    }

    public boolean updateAccess(String roomId, RegistrationLease lease,
                                RoomAccessPolicy policy, long accessEpoch, long inviteEpoch) {
        RoomEntry entry = rooms.get(roomId);
        if (entry == null || !java.util.Objects.equals(entry.lease(), lease)) return false;
        ControlMessage.RoomDescriptor old = entry.descriptor();
        ControlMessage.RoomDescriptor updated = new ControlMessage.RoomDescriptor(
                old.name(), old.gameId(), old.zone(), old.act(), old.characterPolicy(),
                old.lockedCharacter(), old.maxPlayers(), old.verified(), policy);
        rooms.put(roomId, new RoomEntry(entry.roomId(), updated, entry.routing(),
                entry.hostFingerprint(), entry.hostAddress(), entry.directPort(),
                entry.determinismFingerprint(), entry.playerCount(), entry.lastHeartbeatMillis(),
                entry.voteTrackKeys(), entry.certificateSha256(), entry.lease(),
                entry.instanceId(), accessEpoch, inviteEpoch));
        return true;
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
        pending.remove(roomId);
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

    public List<RoomEntry> removeByLease(RegistrationLease lease) {
        pending.values().removeIf(room -> lease.equals(room.lease()));
        List<RoomEntry> removed = new ArrayList<>();
        rooms.values().removeIf(room -> {
            if (lease.equals(room.lease())) {
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

    public List<RoomEntry> listed(String gameFilterOrNull) {
        return list(gameFilterOrNull).stream()
                .filter(room -> !room.descriptor().accessPolicy().passwordRequired()).toList();
    }

    public int totalPages(String gameFilterOrNull) {
        return totalPagesForCount(listed(gameFilterOrNull).size());
    }

    int totalPagesForCount(int count) {
        if (count < 0) throw new IllegalArgumentException("negative room count");
        return count == 0 ? 0
                : (count + config.browserPageSize() - 1) / config.browserPageSize();
    }
}
