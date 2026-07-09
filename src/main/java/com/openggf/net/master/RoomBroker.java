package com.openggf.net.master;

import com.openggf.net.hub.HostHandshake;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.SessionTokenIssuer;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.identity.ProofOfWork;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Authenticated master-browser connection and room-directory state machine. */
public final class RoomBroker {
    public interface RelayRoomDirectory {
        String createRelayRoom(SessionRegistry.RoomEntry entry);
        void noteGuestTier(String fingerprint, boolean isNew);
        boolean attach(HubConnection connection, String roomId, String fingerprint,
                       String displayName);
        void hostLeft(String roomId);
    }

    public interface DirectTunnelDirectory {
        void registerHost(String roomId, HubConnection hostConnection);
        OptionalInt openGuest(String roomId, HubConnection guestConnection);
        void unregisterHost(String roomId);
    }

    public enum AttachMode { ROOM, TUNNEL_GUEST }
    public record AttachResult(AttachMode mode, String roomId, int guestId) { }

    private enum State {
        HANDSHAKE, PENDING_IDENTITY_POW, PENDING_JOIN_POW, ADMITTED, HANDED_OFF
    }

    private static final int STRIKE_LIMIT = 3;
    private static final long ADMISSION_TIMEOUT_MILLIS = 5_000;
    private static final long LIST_INTERVAL_MILLIS = 2_000;
    private static final long JOIN_WINDOW_MILLIS = 10_000;
    private static final int JOIN_LIMIT = 5;
    private static final long GC_INTERVAL_MILLIS = 3_600_000;

    private static final class Member {
        final HubConnection connection;
        final HostHandshake handshake;
        final long connectedAtMillis;
        final Set<String> joinGrantedRooms = new HashSet<>();
        final ArrayDeque<Long> joinRequests = new ArrayDeque<>();
        State state = State.HANDSHAKE;
        String fingerprint;
        String displayName;
        String determinismFingerprint;
        byte[] publicKeyEncoded;
        byte[] joinChallenge;
        String token;
        int strikes;
        long lastListMillis = Long.MIN_VALUE;

        Member(HubConnection connection, HostHandshake handshake, long connectedAtMillis) {
            this.connection = connection;
            this.handshake = handshake;
            this.connectedAtMillis = connectedAtMillis;
        }
    }

    private final PlayerIdentity masterIdentity;
    private final MasterConfig config;
    private final SessionRegistry registry;
    private final IdentityStore store;
    private final TrustLadder ladder;
    private final NewIdentityCache cache;
    private final LongSupplier clock;
    private final RelayRoomDirectory relays;
    private final DirectTunnelDirectory tunnels;
    private final SecureRandom random = new SecureRandom();
    private final SessionTokenIssuer tokens = new SessionTokenIssuer();
    private final AtomicBoolean attackMode;
    private final Map<HubConnection, Member> members = new LinkedHashMap<>();
    private final Map<String, String> tokenFingerprints = new HashMap<>();
    private final Set<String> stampedThisSession = new HashSet<>();
    private final Map<HubConnection, AttachResult> attachResults = new HashMap<>();
    private long nextGcMillis;

    public RoomBroker(PlayerIdentity masterIdentity, MasterConfig config,
                      SessionRegistry registry, IdentityStore store, TrustLadder ladder,
                      NewIdentityCache cache, LongSupplier clock, RelayRoomDirectory relays,
                      DirectTunnelDirectory tunnels) {
        this.masterIdentity = masterIdentity;
        this.config = config;
        this.registry = registry;
        this.store = store;
        this.ladder = ladder;
        this.cache = cache;
        this.clock = clock;
        this.relays = relays;
        this.tunnels = tunnels;
        attackMode = new AtomicBoolean(config.attackMode());
        nextGcMillis = clock.getAsLong() + GC_INTERVAL_MILLIS;
    }

    public void onConnected(HubConnection connection) {
        members.put(connection, new Member(connection,
                new HostHandshake(masterIdentity.fingerprint(), null), clock.getAsLong()));
    }

    public void onText(HubConnection connection, String text) {
        Member member = members.get(connection);
        if (member == null) {
            connection.close("not connected");
            return;
        }
        final ControlCodec.DecodedControl decoded;
        try {
            decoded = ControlCodec.decode(text, Protocol.MAX_MASTER_FRAME_BYTES);
        } catch (ProtocolViolationException e) {
            strike(member, "protocol violation");
            return;
        }
        if (member.state == State.HANDSHAKE) {
            handleHandshake(member, decoded.message());
            return;
        }
        if (member.state == State.PENDING_IDENTITY_POW
                || member.state == State.PENDING_JOIN_POW) {
            if (decoded.message() instanceof ControlMessage.PowSolution solution) {
                onPowSolution(member, solution);
            } else {
                strike(member, "proof of work required");
            }
            return;
        }
        if (member.state != State.ADMITTED) {
            return;
        }
        if (member.token == null || !member.token.equals(decoded.token())
                || !member.fingerprint.equals(tokenFingerprints.get(decoded.token()))) {
            strike(member, "invalid session token");
            return;
        }
        dispatch(member, decoded.message());
    }

    public void onDisconnected(HubConnection connection) {
        Member member = members.remove(connection);
        attachResults.remove(connection);
        if (member == null) {
            return;
        }
        tokens.revoke(member.token);
        tokenFingerprints.remove(member.token);
        if (member.fingerprint != null) {
            for (SessionRegistry.RoomEntry room
                    : registry.removeByHostFingerprint(member.fingerprint)) {
                if ("RELAY".equals(room.routing())) {
                    relays.hostLeft(room.roomId());
                } else {
                    tunnels.unregisterHost(room.roomId());
                }
            }
        }
    }

    public void tick() {
        long now = clock.getAsLong();
        for (Member member : List.copyOf(members.values())) {
            if (member.state != State.ADMITTED && member.state != State.HANDED_OFF
                    && now - member.connectedAtMillis > ADMISSION_TIMEOUT_MILLIS) {
                reject(member, "handshake timeout");
            }
        }
        List<String> directRooms = registry.list(null).stream()
                .filter(room -> "DIRECT".equals(room.routing()))
                .map(SessionRegistry.RoomEntry::roomId).toList();
        registry.expireStale();
        for (String roomId : directRooms) {
            if (registry.find(roomId).isEmpty()) {
                tunnels.unregisterHost(roomId);
            }
        }
        if (now >= nextGcMillis) {
            long cutoff = now - config.identityGcInactiveDays() * 24 * 3_600_000L;
            store.gcInactiveNewIdentities(cutoff);
            nextGcMillis = now + GC_INTERVAL_MILLIS;
        }
    }

    public void setAttackMode(boolean enabled) {
        attackMode.set(enabled);
    }

    public Optional<AttachResult> takeAttachResult(HubConnection connection) {
        return Optional.ofNullable(attachResults.remove(connection));
    }

    /** Broker-loop authentication check for tunnel controls intercepted by transport. */
    public boolean acceptsToken(HubConnection connection, String token) {
        Member member = members.get(connection);
        return member != null && member.state == State.ADMITTED
                && member.token != null && member.token.equals(token)
                && member.fingerprint.equals(tokenFingerprints.get(token));
    }

    private void handleHandshake(Member member, ControlMessage message) {
        HostHandshake.Step step = switch (message) {
            case ControlMessage.Hello hello -> member.handshake.onHello(hello);
            case ControlMessage.AuthProof proof -> member.handshake.onAuthProof(proof);
            default -> new HostHandshake.Reject("unexpected message before admission");
        };
        switch (step) {
            case HostHandshake.SendWelcome welcome -> send(member, welcome.welcome());
            case HostHandshake.Reject rejection -> reject(member, rejection.reason());
            case HostHandshake.Admit admit -> afterAdmit(member, admit);
        }
    }

    private void afterAdmit(Member member, HostHandshake.Admit admit) {
        member.fingerprint = admit.fingerprint();
        member.displayName = admit.displayName().isBlank()
                ? admit.fingerprint().substring(0, 8) : admit.displayName();
        member.publicKeyEncoded = admit.publicKeyEncoded();
        member.determinismFingerprint = admit.determinismFingerprint();
        if (ladder.isBanned(member.fingerprint)) {
            reject(member, "account sanctioned");
            return;
        }
        if (store.find(member.fingerprint).isEmpty()
                && !stampedThisSession.contains(member.fingerprint)) {
            member.state = State.PENDING_IDENTITY_POW;
            send(member, new ControlMessage.PowChallenge(
                    "IDENTITY", "", config.identityPowBits()));
            return;
        }
        maybeJoinPowOrAdmit(member);
    }

    private void onPowSolution(Member member, ControlMessage.PowSolution solution) {
        boolean valid = switch (solution.kind()) {
            case "IDENTITY" -> member.state == State.PENDING_IDENTITY_POW
                    && ProofOfWork.verify(member.publicKeyEncoded, solution.nonce(),
                    config.identityPowBits());
            case "JOIN" -> member.state == State.PENDING_JOIN_POW
                    && ProofOfWork.verify(member.joinChallenge, solution.nonce(),
                    config.attackModePowBits());
            default -> false;
        };
        if (!valid) {
            strike(member, "invalid proof of work");
            return;
        }
        if (member.state == State.PENDING_IDENTITY_POW) {
            stampedThisSession.add(member.fingerprint);
            maybeJoinPowOrAdmit(member);
        } else {
            finishAdmission(member);
        }
    }

    private void maybeJoinPowOrAdmit(Member member) {
        if (attackMode.get()) {
            member.joinChallenge = new byte[16];
            random.nextBytes(member.joinChallenge);
            member.state = State.PENDING_JOIN_POW;
            send(member, new ControlMessage.PowChallenge("JOIN",
                    Base64.getEncoder().encodeToString(member.joinChallenge),
                    config.attackModePowBits()));
        } else {
            finishAdmission(member);
        }
    }

    private void finishAdmission(Member member) {
        member.token = tokens.issue();
        tokenFingerprints.put(member.token, member.fingerprint);
        member.state = State.ADMITTED;
        if (!member.displayName.isBlank()) {
            ladder.onDisplayNameClaim(member.fingerprint, member.displayName);
        } else {
            cache.firstSeenOf(member.fingerprint);
        }
        send(member, new ControlMessage.JoinAccepted(member.token, -1, null, null));
    }

    private void dispatch(Member member, ControlMessage message) {
        switch (message) {
            case ControlMessage.RoomCreate create -> createRoom(member, create);
            case ControlMessage.RoomListRequest request -> listRooms(member, request);
            case ControlMessage.RoomJoinRequest request -> joinRoom(member, request);
            case ControlMessage.RelayAttach attach -> attach(member, attach.roomId());
            case ControlMessage.Heartbeat heartbeat -> heartbeat(member, heartbeat);
            case ControlMessage.RoomLeave leave -> leaveRoom(member, leave.roomId());
            default -> strike(member, "illegal broker message");
        }
    }

    private void createRoom(Member member, ControlMessage.RoomCreate create) {
        String routing = create.routing();
        ControlMessage.RoomDescriptor descriptor = create.room();
        if (!("DIRECT".equals(routing) || "RELAY".equals(routing)) || descriptor == null) {
            strike(member, "invalid room routing");
            return;
        }
        if (descriptor.maxPlayers() < 1 || descriptor.maxPlayers() > Protocol.MAX_PLAYERS_RELAY) {
            send(member, new ControlMessage.RoomCreateRejected("invalid player limit"));
            return;
        }
        if ("DIRECT".equals(routing)
                && descriptor.maxPlayers() > Protocol.MAX_PLAYERS_DIRECT) {
            send(member, new ControlMessage.RoomCreateRejected(
                    "direct rooms are capped at 8 players - use relay routing"));
            return;
        }
        if ("DIRECT".equals(routing)
                && (create.directPort() < 1 || create.directPort() > 65_535)) {
            send(member, new ControlMessage.RoomCreateRejected("invalid direct port"));
            return;
        }
        if (!ladder.canCreateRoom(member.fingerprint)) {
            send(member, new ControlMessage.RoomCreateRejected(
                    "new identities cannot create rooms"));
            return;
        }
        SessionRegistry.RoomEntry entry = null;
        try {
            entry = registry.create(descriptor, routing,
                    member.fingerprint, member.connection.remoteHost(), create.directPort(),
                    create.determinismFingerprint());
            if ("RELAY".equals(routing)) {
                relays.createRelayRoom(entry);
                member.joinGrantedRooms.add(entry.roomId());
            } else {
                tunnels.registerHost(entry.roomId(), member.connection);
            }
            send(member, new ControlMessage.RoomCreated(entry.roomId()));
        } catch (SessionRegistry.RoomCreateException | RuntimeException e) {
            if (entry != null) {
                registry.remove(entry.roomId());
                if ("RELAY".equals(entry.routing())) {
                    relays.hostLeft(entry.roomId());
                } else {
                    tunnels.unregisterHost(entry.roomId());
                }
            }
            send(member, new ControlMessage.RoomCreateRejected(e.getMessage()));
        }
    }

    private void listRooms(Member member, ControlMessage.RoomListRequest request) {
        long now = clock.getAsLong();
        if (member.lastListMillis != Long.MIN_VALUE
                && now - member.lastListMillis < LIST_INTERVAL_MILLIS) {
            return;
        }
        member.lastListMillis = now;
        int page = Math.max(0, request.page());
        List<SessionRegistry.RoomEntry> all = registry.list(request.gameFilter());
        int from = (int) Math.min(all.size(),
                Math.min(Integer.MAX_VALUE, (long) page * config.browserPageSize()));
        int to = Math.min(all.size(), from + config.browserPageSize());
        List<ControlMessage.RoomSummary> summaries = new ArrayList<>();
        for (SessionRegistry.RoomEntry room : all.subList(from, to)) {
            ControlMessage.RoomDescriptor descriptor = room.descriptor();
            summaries.add(new ControlMessage.RoomSummary(room.roomId(), descriptor.name(),
                    descriptor.gameId(), descriptor.zone(), descriptor.act(),
                    descriptor.characterPolicy(), room.playerCount(), descriptor.maxPlayers(),
                    room.routing(), descriptor.verified()));
        }
        send(member, new ControlMessage.RoomListResult(summaries, page,
                registry.totalPages(request.gameFilter())));
    }

    private void joinRoom(Member member, ControlMessage.RoomJoinRequest request) {
        long now = clock.getAsLong();
        while (!member.joinRequests.isEmpty()
                && now - member.joinRequests.peekFirst() >= JOIN_WINDOW_MILLIS) {
            member.joinRequests.removeFirst();
        }
        if (member.joinRequests.size() >= JOIN_LIMIT) {
            send(member, new ControlMessage.RoomJoinRejected("join rate limited"));
            return;
        }
        member.joinRequests.addLast(now);
        SessionRegistry.RoomEntry room = registry.find(request.roomId()).orElse(null);
        if (room == null) {
            send(member, new ControlMessage.RoomJoinRejected("room not found"));
            return;
        }
        if (!java.util.Objects.equals(member.determinismFingerprint,
                room.determinismFingerprint())) {
            send(member, new ControlMessage.RoomJoinRejected(
                    "determinism fingerprint mismatch"));
            return;
        }
        boolean underPressure = room.descriptor().maxPlayers() > 0
                && room.playerCount() * 5 >= room.descriptor().maxPlayers() * 4;
        if (underPressure && ladder.tierOf(member.fingerprint) == TrustLadder.Tier.NEW) {
            send(member, new ControlMessage.RoomJoinRejected("room under pressure"));
            return;
        }
        member.joinGrantedRooms.add(room.roomId());
        send(member, new ControlMessage.RoomJoinResult(room.roomId(), room.routing(),
                "DIRECT".equals(room.routing()) ? room.hostAddress() : null,
                room.directPort(), "DIRECT".equals(room.routing())
                ? room.hostFingerprint() : masterIdentity.fingerprint(),
                room.determinismFingerprint()));
    }

    private void attach(Member member, String roomId) {
        if (!member.joinGrantedRooms.remove(roomId)) {
            send(member, new ControlMessage.RoomJoinRejected("join not granted"));
            return;
        }
        SessionRegistry.RoomEntry room = registry.find(roomId).orElse(null);
        if (room == null) {
            send(member, new ControlMessage.RoomJoinRejected("room not found"));
            return;
        }
        if ("RELAY".equals(room.routing())) {
            boolean isNew = ladder.tierOf(member.fingerprint) == TrustLadder.Tier.NEW;
            relays.noteGuestTier(member.fingerprint, isNew);
            if (!relays.attach(member.connection, roomId, member.fingerprint,
                    member.displayName)) {
                send(member, new ControlMessage.RoomJoinRejected("room not found"));
                return;
            }
            attachResults.put(member.connection,
                    new AttachResult(AttachMode.ROOM, roomId, -1));
        } else {
            OptionalInt guestId = tunnels.openGuest(roomId, member.connection);
            if (guestId.isEmpty()) {
                send(member, new ControlMessage.RoomJoinRejected("relay unavailable"));
                return;
            }
            attachResults.put(member.connection,
                    new AttachResult(AttachMode.TUNNEL_GUEST, roomId, guestId.getAsInt()));
        }
        member.state = State.HANDED_OFF;
    }

    private void heartbeat(Member member, ControlMessage.Heartbeat heartbeat) {
        registry.find(heartbeat.roomId()).filter(room ->
                room.hostFingerprint().equals(member.fingerprint))
                .ifPresent(room -> registry.heartbeat(room.roomId(), heartbeat.playerCount()));
    }

    private void leaveRoom(Member member, String roomId) {
        SessionRegistry.RoomEntry room = registry.find(roomId).orElse(null);
        if (room == null || !room.hostFingerprint().equals(member.fingerprint)) {
            return;
        }
        registry.remove(roomId);
        if ("RELAY".equals(room.routing())) {
            relays.hostLeft(roomId);
        } else {
            tunnels.unregisterHost(roomId);
        }
    }

    private void strike(Member member, String reason) {
        if (++member.strikes >= STRIKE_LIMIT) {
            drop(member, reason);
        }
    }

    private void reject(Member member, String reason) {
        send(member, new ControlMessage.JoinRejected(reason));
        drop(member, reason);
    }

    private void drop(Member member, String reason) {
        members.remove(member.connection);
        tokens.revoke(member.token);
        tokenFingerprints.remove(member.token);
        member.connection.close(reason);
    }

    private static void send(Member member, ControlMessage message) {
        member.connection.sendText(ControlCodec.encode(null, message));
    }
}
