package com.openggf.net.master;

import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.RoomHostHooks;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Master-owned relay rooms, each confined to one event-loop executor. */
public final class RelayRoomManager implements RoomBroker.RelayRoomDirectory {
    public record RoomAccess(RoomHost room, Executor loop) { }

    @FunctionalInterface
    public interface PlayerCountSink {
        void accept(String roomId, int playerCount);
    }

    public static final int PLAYER_COUNT_INTERVAL_TICKS = 20;

    private final PlayerIdentity masterIdentity;
    private final TrustLadder ladder;
    private final TrackValidationProfileSource profiles;
    private final List<Executor> roomLoops;
    private final Executor brokerLoop;
    private final LongSupplier clock;
    private final PlayerCountSink playerCounts;
    private final Map<String, RoomAccess> rooms = new ConcurrentHashMap<>();
    private final Map<String, Boolean> newTierByFingerprint = new ConcurrentHashMap<>();
    private final AtomicInteger nextLoop = new AtomicInteger();
    private int tickCounter;

    public RelayRoomManager(PlayerIdentity masterIdentity, TrustLadder ladder,
                            TrackValidationProfileSource profiles, List<Executor> roomLoops,
                            Executor brokerLoop, LongSupplier clock,
                            PlayerCountSink playerCounts) {
        if (roomLoops == null || roomLoops.isEmpty()) {
            throw new IllegalArgumentException("at least one relay room loop is required");
        }
        this.masterIdentity = masterIdentity;
        this.ladder = ladder;
        this.profiles = profiles;
        this.roomLoops = List.copyOf(roomLoops);
        this.brokerLoop = brokerLoop;
        this.clock = clock;
        this.playerCounts = playerCounts;
    }

    @Override
    public void noteGuestTier(String fingerprint, boolean isNew) {
        newTierByFingerprint.put(fingerprint, isNew);
    }

    @Override
    public String createRelayRoom(SessionRegistry.RoomEntry entry) {
        ControlMessage.RoomDescriptor descriptor = entry.descriptor();
        RoomHostConfig config = new RoomHostConfig(descriptor.name(), descriptor.gameId(),
                descriptor.zone(), descriptor.act(), descriptor.characterPolicy(),
                descriptor.lockedCharacter(), descriptor.maxPlayers(),
                entry.determinismFingerprint());
        RoomHostHooks hooks = new RoomHostHooks(descriptor.maxPlayers() > 8,
                (fingerprint, memberSince) ->
                        !newTierByFingerprint.getOrDefault(fingerprint, false)
                                || clock.getAsLong() - memberSince
                                > TrustLadder.NEW_CHAT_MUTE_MILLIS,
                (fingerprint, clean) -> brokerLoop.execute(() -> {
                    if (clean) {
                        ladder.onCleanRound(fingerprint);
                    }
                }), entry.hostFingerprint(), fingerprint ->
                newTierByFingerprint.getOrDefault(fingerprint, false));
        RoomHost room = new RoomHost(config, masterIdentity, clock, profiles, hooks);
        Executor loop = roomLoops.get(Math.floorMod(
                nextLoop.getAndIncrement(), roomLoops.size()));
        RoomAccess old = rooms.putIfAbsent(entry.roomId(), new RoomAccess(room, loop));
        if (old != null) {
            throw new IllegalStateException("relay room already exists: " + entry.roomId());
        }
        return entry.roomId();
    }

    @Override
    public boolean attach(HubConnection connection, String roomId, String fingerprint,
                          String displayName) {
        RoomAccess access = rooms.get(roomId);
        if (access == null) {
            return false;
        }
        access.loop().execute(() -> {
            access.room().expectFingerprint(connection, fingerprint);
            access.room().onConnected(connection);
        });
        return true;
    }

    @Override
    public void hostLeft(String roomId) {
        RoomAccess access = rooms.remove(roomId);
        if (access != null) {
            access.loop().execute(() -> access.room().close("relay host left"));
        }
    }

    public Optional<RoomAccess> find(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public int roomCount() {
        return rooms.size();
    }

    public void tickAll() {
        boolean publishCounts = ++tickCounter % PLAYER_COUNT_INTERVAL_TICKS == 0;
        for (Map.Entry<String, RoomAccess> entry : rooms.entrySet()) {
            String roomId = entry.getKey();
            RoomAccess access = entry.getValue();
            access.loop().execute(() -> {
                access.room().tick();
                if (publishCounts) {
                    int count = access.room().playerCount();
                    brokerLoop.execute(() -> playerCounts.accept(roomId, count));
                }
            });
        }
    }
}
