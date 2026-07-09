package com.openggf.game.timeattack.mp;

import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.timeattack.TimeAttackRuntime;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RemoteGhostRegistry;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.sprites.ghost.ActiveGhost;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bridges a room connection to one attached time-attack runtime per round. */
public final class MultiplayerRaceCoordinator implements TimeAttackRuntime.AttemptListener {
    private static final long PING_INTERVAL_MILLIS = 500;

    private final RaceTransport transport;
    private final ClientRaceSession session;
    private final RemoteGhostRegistry registry = new RemoteGhostRegistry();
    private final LongSupplier clockMillis;

    private TimeAttackRuntime runtime;
    private GhostStreamPublisher publisher;
    private boolean publisherActive;
    private List<ActiveGhost> remoteGhosts = List.of();
    private boolean connectionLost;
    private long lastPingAtMillis = Long.MIN_VALUE;

    public MultiplayerRaceCoordinator(RaceTransport transport, ClientRaceSession session) {
        this(transport, session, System::currentTimeMillis);
    }

    public MultiplayerRaceCoordinator(RaceTransport transport, ClientRaceSession session,
                                      LongSupplier clockMillis) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.session = Objects.requireNonNull(session, "session");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    }

    public void attachRuntime(TimeAttackRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        detachRuntime();
        this.runtime = runtime;
        this.publisher = new GhostStreamPublisher(transport::sendBinary);
        runtime.setAttemptListener(this);
        runtime.setExtraGhostSupplier(this::remoteActiveGhosts);
    }

    public void detachRuntime() {
        if (runtime != null) {
            runtime.setAttemptListener(null);
            runtime.setExtraGhostSupplier(null);
            runtime = null;
        }
        if (publisherActive && publisher != null) {
            publisher.abandonAttempt();
        }
        publisherActive = false;
        publisher = null;
        remoteGhosts = List.of();
    }

    public boolean isRuntimeAttached() {
        return runtime != null;
    }

    /** Single network drain, used by both lobby and gameplay contexts. */
    public void pump() {
        for (RaceClient.InboundEvent event : transport.drainInbound()) {
            switch (event) {
                case RaceClient.Control control -> {
                    session.onControl(control.message());
                    if (control.message() instanceof ControlMessage.RoomState state) {
                        registry.onRoomState(state.players());
                    } else if (control.message() instanceof ControlMessage.RoundStart) {
                        registry.reset();
                        remoteGhosts = List.of();
                    }
                }
                case RaceClient.GhostData ghost -> registry.onAggregate(ghost.aggregate());
                case RaceClient.Disconnected ignored -> connectionLost = true;
            }
        }
        maybePing();
    }

    public void beforeLevelFrame() {
        pump();
    }

    public boolean holdGameplay() {
        return runtime != null && session.phase() == ClientRaceSession.Phase.COUNTDOWN;
    }

    public void afterLevelFrame() {
        if (runtime != null && !session.isWindowOpen() && runtime.isAttemptActive()) {
            runtime.voidCurrentAttempt();
        }
        remoteGhosts = toActiveGhosts(registry.advanceAll(session.localSlot()));
    }

    public List<ActiveGhost> remoteActiveGhosts() {
        return remoteGhosts;
    }

    public MultiplayerHudState hudState() {
        return new MultiplayerHudState(transport.isOpen() || connectionLost,
                session.phase().name(), session.remainingWindowMillis(),
                session.remainingCountdownMillis(), session.standings(),
                session.chatLines(), connectionLost, session.kickReason());
    }

    public ClientRaceSession session() {
        return session;
    }

    public void sendChat(String text) {
        transport.sendControl(new ControlMessage.Chat(text));
    }

    public void sendRoundConfigure(ControlMessage.RoundConfig config) {
        transport.sendControl(new ControlMessage.RoundConfigure(config));
    }

    public void shutdown() {
        detachRuntime();
        transport.close();
    }

    @Override
    public void onAttemptBegan(int attemptOrdinal) {
        requirePublisher();
        publisher.beginAttempt(attemptOrdinal);
        publisherActive = true;
        transport.sendControl(new ControlMessage.AttemptStart(attemptOrdinal));
    }

    @Override
    public void onFrameSampled(int attemptOrdinal, GhostFrame frame) {
        requirePublisher();
        publisher.onFrame(frame);
    }

    @Override
    public void onAttemptFinished(int attemptOrdinal, int timeFrames,
                                  int firstInputFrame, int finishFrame,
                                  byte[] inputRecordingSha256) {
        requirePublisher();
        publisher.finishAttempt();
        publisherActive = false;
        transport.sendControl(new ControlMessage.AttemptFinish(
                attemptOrdinal, timeFrames, firstInputFrame, finishFrame,
                HexFormat.of().formatHex(inputRecordingSha256),
                HexFormat.of().formatHex(publisher.streamHashSha256()), null));
    }

    @Override
    public void onAttemptVoided(int attemptOrdinal) {
        requirePublisher();
        publisher.abandonAttempt();
        publisherActive = false;
        transport.sendControl(new ControlMessage.AttemptReset(attemptOrdinal));
    }

    private GhostStreamPublisher requirePublisher() {
        if (publisher == null) {
            throw new IllegalStateException("no runtime attached");
        }
        return publisher;
    }

    private static List<ActiveGhost> toActiveGhosts(
            List<RemoteGhostRegistry.RemoteGhost> ghosts) {
        List<ActiveGhost> active = new ArrayList<>(ghosts.size());
        for (RemoteGhostRegistry.RemoteGhost ghost : ghosts) {
            active.add(new ActiveGhost("net:" + ghost.slot(), ghost.character(),
                    ghost.state().frame()));
        }
        return List.copyOf(active);
    }

    private void maybePing() {
        if (!session.needsMoreClockSamples() || !transport.isOpen()) {
            return;
        }
        long now = clockMillis.getAsLong();
        if (lastPingAtMillis == Long.MIN_VALUE
                || now - lastPingAtMillis >= PING_INTERVAL_MILLIS) {
            lastPingAtMillis = now;
            transport.sendControl(new ControlMessage.Ping(now));
        }
    }
}
