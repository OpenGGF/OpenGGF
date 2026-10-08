package com.openggf.net.host;

import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Real transport with room time advanced on the host's owning room thread. */
public final class ControlledRaceHost {
    private final AtomicLong offsetMillis = new AtomicLong();
    private final RaceHostImpl transport;
    private RaceRoomHost server;

    public ControlledRaceHost() {
        this(RaceHostImpl.NETTY);
    }

    public ControlledRaceHost(RaceHostImpl transport) {
        this.transport = transport;
    }

    public RaceRoomHost start(int port, RoomHostConfig config,
                              PlayerIdentity identity,
                              TrackValidationProfileSource profiles) {
        server = transport.start(port, config, identity, profiles,
                () -> System.currentTimeMillis() + offsetMillis.get());
        return server;
    }

    public void advance(long millis) throws Exception {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        server.execute(() -> {
            try {
                offsetMillis.addAndGet(millis);
                server.room().tick();
                completed.complete(null);
            } catch (Throwable failure) {
                completed.completeExceptionally(failure);
            }
        });
        completed.get(5, TimeUnit.SECONDS);
    }

    public void startRound(ControlMessage.RoundConfig config) throws Exception {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        server.execute(() -> {
            try {
                if (!server.room().requestStartRound(config)) {
                    throw new IllegalStateException("round start rejected");
                }
                completed.complete(null);
            } catch (Throwable failure) {
                completed.completeExceptionally(failure);
            }
        });
        completed.get(5, TimeUnit.SECONDS);
    }
}
