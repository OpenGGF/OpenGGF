package openggf.racing.server.master;

import com.openggf.net.hub.HostHandshake;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.hub.SessionTokenIssuer;
import com.openggf.net.hub.RoomInviteService;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.identity.ProofOfWork;
import openggf.racing.protocol.ControlCodec;
import com.openggf.net.identity.DisplayNames;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Authenticated master-browser connection and room-directory state machine. */
public final class RoomBroker {
    public interface RelayRoomDirectory {
        interface PreparedRoom extends AutoCloseable {
            void publish(RoomInviteService inviteService);
            @Override void close();
        }

        String createRelayRoom(SessionRegistry.RoomEntry entry);
        default CompletableFuture<PreparedRoom> prepareRelayRoom(
                SessionRegistry.RoomEntry entry, RoomPassword password) {
            if (entry.descriptor().accessPolicy().passwordRequired()) {
                return CompletableFuture.failedFuture(new IllegalStateException("private relay unavailable"));
            }
            return CompletableFuture.completedFuture(new PreparedRoom() {
                @Override public void publish(RoomInviteService service) { createRelayRoom(entry); }
                @Override public void close() { }
            });
        }
        void noteGuestTier(String fingerprint, boolean isNew);
        boolean attach(HubConnection connection, String roomId, String fingerprint,
                       String displayName, String instanceId,
                       String grantReference, long routeGeneration);
        void hostLeft(String roomId);
    }

    public interface DirectTunnelDirectory {
        void registerHost(String roomId, SessionRegistry.RegistrationLease registration);
        OptionalInt openGuest(String roomId, HubConnection guestConnection,
                              String instanceId, String fingerprint,
                              String grantReference, long routeGeneration,
                              SessionRegistry.RegistrationLease registration);
        void unregisterHost(String roomId, SessionRegistry.RegistrationLease registration);
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
    // Allows the client's direct-connect timeout and fallback, without retaining
    // abandoned browser joins for the lifetime of a master connection.
    private static final long JOIN_GRANT_TTL_MILLIS = 30_000;
    private static final long GC_INTERVAL_MILLIS = 3_600_000;
    private static final long CREATE_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_PENDING_CREATES = 64;
    private static final int MAX_PENDING_PER_CONNECTION = 2;
    private static final int MAX_RECENT_CREATES = 8;
    private static final int MAX_PENDING_RETIREMENTS = 64;
    private static final long ROUTE_RETIRE_TIMEOUT_MILLIS = 5_000;

    private record AttachGrant(String roomId, String instanceId, long accessEpoch,
                               long inviteEpoch, long expiresAtMillis,
                               InviteDirectory.Resolution invite,
                               SessionRegistry.RegistrationLease lease,
                               String reference, long generation) { }

    private static final class AttachGrants {
        private final LinkedHashMap<String, AttachGrant> grants = new LinkedHashMap<>();
        private final LinkedHashMap<String, AttachGrant> redeemed = new LinkedHashMap<>();
        private final LinkedHashMap<String, AttachGrant> retiring = new LinkedHashMap<>();
        private long nextGeneration;

        AttachGrant grant(SessionRegistry.RoomEntry room, long now, InviteDirectory.Resolution invite) {
            expire(now);
            if (redeemed.containsKey(room.roomId()) || retiring.containsKey(room.roomId())
                    || redeemed.size() + retiring.size() >= JOIN_LIMIT) return null;
            long deadline = now + JOIN_GRANT_TTL_MILLIS;
            if (invite != null) deadline = Math.min(deadline, invite.expiresAtMillis());
            grants.remove(room.roomId());
            AttachGrant issued = new AttachGrant(room.roomId(), room.instanceId(),
                    room.accessEpoch(), room.inviteEpoch(), deadline, invite, room.lease(),
                    java.util.UUID.randomUUID().toString(), ++nextGeneration);
            grants.put(room.roomId(), issued);
            while (grants.size() > JOIN_LIMIT) grants.pollFirstEntry();
            return issued;
        }

        AttachGrant consume(String roomId, String reference, long generation, long now) {
            AttachGrant grant = issued(roomId, reference, generation, now);
            return grant != null && grants.remove(roomId, grant) ? grant : null;
        }

        AttachGrant issued(String roomId, String reference, long generation, long now) {
            AttachGrant grant = grants.get(roomId);
            return grant != null && now < grant.expiresAtMillis()
                    && grant.reference().equals(reference) && grant.generation() == generation
                    ? grant : null;
        }

        void redeem(AttachGrant grant) {
            if (grants.remove(grant.roomId(), grant)) redeemed.put(grant.roomId(), grant);
        }

        AttachGrant retire(String roomId, String reference, long generation, long now) {
            AttachGrant grant = issued(roomId, reference, generation, now);
            if (grant != null) grants.remove(roomId, grant);
            else {
                grant = redeemed.get(roomId);
                if (grant == null || !grant.reference().equals(reference)
                        || grant.generation() != generation) return null;
                redeemed.remove(roomId, grant);
            }
            retiring.put(roomId, grant);
            return grant;
        }

        boolean finishRetire(AttachGrant grant) {
            return retiring.remove(grant.roomId(), grant);
        }

        void expire(long now) { grants.values().removeIf(grant -> now >= grant.expiresAtMillis()); }
    }

    private record PendingCreate(Member member, long requestId,
                                 SessionRegistry.RoomEntry entry, long expiresAtMillis) { }
    private record CreatedRequest(SessionRegistry.RoomEntry entry, long expiresAtMillis) { }
    private record AccessChange(SessionRegistry.RegistrationLease lease, String instanceId,
                                long accessEpoch, long fromInviteEpoch, long toInviteEpoch) { }
    private record PendingRetire(Member guest, Member host, AttachGrant grant,
                                 long requestId, long expiresAtMillis, boolean renew) { }

    /** Broker-loop-confined LRU set with expiry fixed at the latest grant. */
    private static final class ExpiringGrants {
        private final int maxSize;
        private final long ttlMillis;
        private final LinkedHashMap<String, Long> grantedAt = new LinkedHashMap<>(16, 0.75f, true);

        ExpiringGrants(int maxSize, long ttlMillis) {
            this.maxSize = maxSize;
            this.ttlMillis = ttlMillis;
        }

        void grant(String key, long now) {
            expire(now);
            grantedAt.put(key, now);
            while (grantedAt.size() > maxSize) {
                grantedAt.pollFirstEntry();
            }
        }

        boolean contains(String key, long now) {
            Long issuedAt = grantedAt.get(key);
            if (issuedAt == null) {
                return false;
            }
            if (now - issuedAt >= ttlMillis) {
                grantedAt.remove(key);
                return false;
            }
            return true;
        }

        boolean consume(String key, long now) {
            Long issuedAt = grantedAt.remove(key);
            return issuedAt != null && now - issuedAt < ttlMillis;
        }

        void expire(long now) {
            grantedAt.values().removeIf(issuedAt -> now - issuedAt >= ttlMillis);
        }
    }

    private static final class Member {
        final HubConnection connection;
        final HostHandshake handshake;
        final long connectedAtMillis;
        final SessionRegistry.RegistrationLease lease;
        final AttachGrants joinGrantedRooms = new AttachGrants();
        final Map<Long, PendingCreate> pendingCreates = new HashMap<>();
        final WorkBudget.Account creationWork = WorkBudget.PROCESS.account(
                MAX_PENDING_PER_CONNECTION, 2L * Protocol.MAX_CONTROL_BYTES * MAX_PENDING_PER_CONNECTION);
        final Map<Long, CreatedRequest> createdRequests = new HashMap<>();
        final ArrayDeque<Long> joinRequests = new ArrayDeque<>();
        State state = State.HANDSHAKE;
        String fingerprint;
        String displayName;
        boolean displayNameClaimed;
        String determinismFingerprint;
        byte[] publicKeyEncoded;
        byte[] joinChallenge;
        String token;
        int strikes;
        long lastListMillis = Long.MIN_VALUE;

        Member(HubConnection connection, HostHandshake handshake, long connectedAtMillis,
               long generation) {
            this.connection = connection;
            this.handshake = handshake;
            this.connectedAtMillis = connectedAtMillis;
            lease = new SessionRegistry.RegistrationLease(connection, generation);
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
    private final WorkBudget.Account inboundWork = WorkBudget.PROCESS.account(4096,
            32L * 1024 * 1024);
    private final WorkBudget.Account lifecycleWork = WorkBudget.PROCESS.account(8192, 1);

    WorkBudget.Account inboundWork() { return inboundWork; }
    WorkBudget.Account lifecycleWork() { return lifecycleWork; }
    private final Predicate<String> knownTrack;
    private final VerifierRegistry verifiers;
    private final Executor brokerLoop;
    private final InviteDirectory invites;
    private final PasswordSourceBudget passwordSources;
    private final SecureRandom random = new SecureRandom();
    private final SessionTokenIssuer tokens = new SessionTokenIssuer();
    private final AtomicBoolean attackMode;
    private final Map<HubConnection, Member> members = new LinkedHashMap<>();
    private final Map<String, String> tokenFingerprints = new HashMap<>();
    private final ExpiringGrants identityPowStamps;
    private final Map<HubConnection, AttachResult> attachResults = new HashMap<>();
    private final Map<String, AccessChange> accessChanges = new HashMap<>();
    private final Map<Long, PendingRetire> pendingRetires = new HashMap<>();
    private long nextRetirementId;
    private long nextGcMillis;
    private long registrationGeneration;
    private int pendingCreateCount;

    public RoomBroker(PlayerIdentity masterIdentity, MasterConfig config,
                      SessionRegistry registry, IdentityStore store, TrustLadder ladder,
                      NewIdentityCache cache, LongSupplier clock, RelayRoomDirectory relays,
                      DirectTunnelDirectory tunnels) {
        this(masterIdentity, config, registry, store, ladder, cache, clock,
                relays, tunnels, ignored -> true, null);
    }

    public RoomBroker(PlayerIdentity masterIdentity, MasterConfig config,
                      SessionRegistry registry, IdentityStore store, TrustLadder ladder,
                      NewIdentityCache cache, LongSupplier clock, RelayRoomDirectory relays,
                      DirectTunnelDirectory tunnels, Predicate<String> knownTrack) {
        this(masterIdentity, config, registry, store, ladder, cache, clock,
                relays, tunnels, knownTrack, null);
    }

    public RoomBroker(PlayerIdentity masterIdentity, MasterConfig config,
                      SessionRegistry registry, IdentityStore store, TrustLadder ladder,
                      NewIdentityCache cache, LongSupplier clock, RelayRoomDirectory relays,
                      DirectTunnelDirectory tunnels, Predicate<String> knownTrack,
                      VerifierRegistry verifiers) {
        this(masterIdentity, config, registry, store, ladder, cache, clock, relays,
                tunnels, knownTrack, verifiers, Runnable::run);
    }

    public RoomBroker(PlayerIdentity masterIdentity, MasterConfig config,
                      SessionRegistry registry, IdentityStore store, TrustLadder ladder,
                      NewIdentityCache cache, LongSupplier clock, RelayRoomDirectory relays,
                      DirectTunnelDirectory tunnels, Predicate<String> knownTrack,
                      VerifierRegistry verifiers, Executor brokerLoop) {
        this.masterIdentity = masterIdentity;
        this.config = config;
        this.registry = registry;
        this.store = store;
        this.ladder = ladder;
        this.cache = cache;
        this.clock = clock;
        this.relays = relays;
        this.tunnels = tunnels;
        this.knownTrack = knownTrack;
        this.verifiers = verifiers;
        this.brokerLoop = brokerLoop;
        invites = new InviteDirectory(clock);
        passwordSources = new PasswordSourceBudget(clock);
        identityPowStamps = new ExpiringGrants(config.newIdentityCacheSize(),
                TimeUnit.MINUTES.toMillis(config.newIdentityCacheTtlMinutes()));
        attackMode = new AtomicBoolean(config.attackMode());
        nextGcMillis = clock.getAsLong() + GC_INTERVAL_MILLIS;
    }

    public void onConnected(HubConnection connection) {
        members.put(connection, new Member(connection,
                new HostHandshake(masterIdentity.fingerprint(), null,
                        com.openggf.net.protocol.AuthenticationTranscript.Role.MASTER,
                        com.openggf.net.hub.HandshakeTransport.binding(connection)),
                clock.getAsLong(), ++registrationGeneration));
    }

    /** Evict every live connection for a newly sanctioned identity on the broker loop. */
    public void revokeSanctioned(String fingerprint) {
        if (!ladder.isBanned(fingerprint)) {
            return;
        }
        for (Member member : List.copyOf(members.values())) {
            if (fingerprint.equals(member.fingerprint)) {
                onDisconnected(member.connection);
                member.connection.close("account sanctioned");
            }
        }
    }

    public boolean isSessionTokenValid(String token) {
        return tokens.isValid(token) && tokenFingerprints.containsKey(token);
    }

    public void onText(HubConnection connection, String text) {
        Member member = members.get(connection);
        if (member == null) {
            connection.close("not connected");
            return;
        }
        final ControlCodec.DecodedControl decoded;
        try {
            decoded = ControlCodec.decodeMasterClient(text, Protocol.MAX_MASTER_FRAME_BYTES);
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
        dispatch(member, decoded.message(), decoded.requestId());
    }

    public void onDisconnected(HubConnection connection) {
        Member member = members.remove(connection);
        attachResults.remove(connection);
        if (member == null) {
            return;
        }
        tokens.revoke(member.token);
        tokenFingerprints.remove(member.token);
        cancelPendingCreates(member);
        removeHostedRooms(member);
    }

    public void tick() {
        long now = clock.getAsLong();
        for (Map.Entry<Long, PendingRetire> entry : List.copyOf(pendingRetires.entrySet())) {
            if (now >= entry.getValue().expiresAtMillis()
                    && pendingRetires.remove(entry.getKey(), entry.getValue())) {
                PendingRetire pending = entry.getValue();
                if (members.get(pending.guest().connection) == pending.guest()) {
                    send(pending.guest(), new ControlMessage.RoomRouteUnavailable(
                            "direct route retirement unavailable"), pending.requestId());
                }
            }
        }
        identityPowStamps.expire(now);
        invites.prune();
        for (Member member : List.copyOf(members.values())) {
            member.joinGrantedRooms.expire(now);
            member.createdRequests.values().removeIf(created -> now >= created.expiresAtMillis());
            for (PendingCreate pending : List.copyOf(member.pendingCreates.values())) {
                if (now >= pending.expiresAtMillis()) {
                    cancelPendingCreate(pending);
                    send(member, new ControlMessage.RoomCreateRejected("room creation timed out"),
                            pending.requestId());
                }
            }
            if (member.state != State.ADMITTED && member.state != State.HANDED_OFF
                    && now - member.connectedAtMillis > ADMISSION_TIMEOUT_MILLIS) {
                reject(member, "handshake timeout");
            }
        }
        List<SessionRegistry.RoomEntry> directRooms = registry.list(null).stream()
                .filter(room -> "DIRECT".equals(room.routing()))
                .toList();
        registry.expireStale();
        for (SessionRegistry.RoomEntry room : directRooms) {
            if (registry.find(room.roomId()).isEmpty()) {
                invites.remove(room.roomId(), room.lease());
                accessChanges.remove(room.roomId());
                tunnels.unregisterHost(room.roomId(), room.lease());
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

    /** Binary tunnel wrappers authenticate by their admitted transport and exact tunnel owner. */
    boolean isAdmitted(HubConnection connection) {
        Member member = members.get(connection);
        return member != null && member.state == State.ADMITTED;
    }

    /** Broker-loop gate before room or tunneled password work reaches any verifier. */
    public boolean allowRoomText(HubConnection connection, String text) {
        return !isPasswordSubmission(text) || allowPasswordSubmission(connection);
    }

    static boolean isPasswordSubmission(String text) {
        final ControlCodec.DecodedControl decoded;
        try {
            decoded = ControlCodec.decodeRoom(text);
        } catch (ProtocolViolationException invalid) {
            return false;
        }
        switch (decoded.message()) {
            case ControlMessage.RoomAccessSubmit submit -> {
                if (submit.password() != null) submit.password().close();
                return true;
            }
            case ControlMessage.RoomPasswordChange change -> {
                if (change.password() != null) change.password().close();
            }
            case ControlMessage.RoomCreate create -> {
                if (create.password() != null) create.password().close();
            }
            default -> { }
        }
        return false;
    }

    boolean allowPasswordSubmission(HubConnection connection) {
        Member member = members.get(connection);
        if (member == null || member.state != State.HANDED_OFF
                || !passwordSources.admit(connection.remoteHost())) {
            connection.sendText(ControlCodec.encode(null,
                    new ControlMessage.JoinRejected("room admission rate limited")));
            return false;
        }
        return true;
    }

    public java.util.Optional<String> fingerprintForSessionToken(String token) {
        return java.util.Optional.ofNullable(tokenFingerprints.get(token));
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
        if (!admit.displayName().isEmpty() && !DisplayNames.isCanonical(admit.displayName())) {
            // Clients send canonical names; anything else would persist unsafe text in the store.
            reject(member, "invalid display name");
            return;
        }
        member.fingerprint = admit.fingerprint();
        member.displayNameClaimed = !admit.displayName().isBlank();
        member.displayName = admit.displayName().isBlank()
                ? admit.fingerprint().substring(0, 8) : admit.displayName();
        member.publicKeyEncoded = admit.publicKeyEncoded();
        member.determinismFingerprint = admit.determinismFingerprint();
        if (ladder.isBanned(member.fingerprint)) {
            reject(member, "account sanctioned");
            return;
        }
        if (store.find(member.fingerprint).isEmpty()
                && !identityPowStamps.contains(member.fingerprint, clock.getAsLong())) {
            member.state = State.PENDING_IDENTITY_POW;
            send(member, new ControlMessage.PowChallenge(
                    "IDENTITY", "", config.identityPowBits()));
            return;
        }
        maybeJoinPowOrAdmit(member);
    }

    private void onPowSolution(Member member, ControlMessage.PowSolution solution) {
        boolean valid = switch (solution.kind()) {
            case null -> false;
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
            identityPowStamps.grant(member.fingerprint, clock.getAsLong());
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
        if (member.displayNameClaimed) {
            ladder.onDisplayNameClaim(member.fingerprint, member.displayName);
        } else {
            cache.firstSeenOf(member.fingerprint);
        }
        send(member, new ControlMessage.JoinAccepted(member.token, -1, null, null));
    }

    private void dispatch(Member member, ControlMessage message, long requestId) {
        switch (message) {
            case ControlMessage.RoomCreate create -> createRoom(member, create, requestId);
            case ControlMessage.RoomActivate activate -> activateRoom(member, activate, requestId);
            case ControlMessage.RoomListRequest request -> listRooms(member, request, requestId);
            case ControlMessage.RoomJoinRequest request -> joinRoom(member, request, requestId);
            case ControlMessage.RoomGrantRedeem redeem -> redeemGrant(member, redeem, requestId);
            case ControlMessage.RoomRouteRetire retire -> retireRoute(member, retire, requestId);
            case ControlMessage.RoomRouteRetireAck ack -> acknowledgeRetirement(member, ack);
            case ControlMessage.RoomInviteResolve request -> resolveInvite(member, request, requestId);
            case ControlMessage.RoomInviteControl request -> controlInvite(member, request, requestId);
            case ControlMessage.MasterRequestCancel cancel -> cancelCreate(member, cancel.requestId());
            case ControlMessage.RelayAttach attach -> attach(member, attach, requestId);
            case ControlMessage.Heartbeat heartbeat -> heartbeat(member, heartbeat);
            case ControlMessage.RoomLeave leave -> leaveRoom(member, leave.roomId());
            case ControlMessage.RoomTrackUpdate update -> updateTrack(member, update);
            case ControlMessage.RoomChatModeUpdate update -> updateChatMode(member, update);
            default -> strike(member, "illegal broker message");
        }
    }

    private void createRoom(Member member, ControlMessage.RoomCreate create, long requestId) {
        try {
            beginCreate(member, create, requestId);
        } finally {
            if (create.password() != null) create.password().close();
        }
    }

    private void beginCreate(Member member, ControlMessage.RoomCreate create, long requestId) {
        java.util.function.Consumer<String> refused = reason -> send(member,
                new ControlMessage.RoomCreateRejected(reason), requestId);
        if (ladder.isBanned(member.fingerprint)) {
            refused.accept("account sanctioned");
            return;
        }
        String routing = create.routing();
        ControlMessage.RoomDescriptor descriptor = create.room();
        if (!("DIRECT".equals(routing) || "RELAY".equals(routing)) || descriptor == null) {
            refused.accept("invalid room routing");
            strike(member, "invalid room routing");
            return;
        }
        if (!validDescriptorFields(descriptor, create.determinismFingerprint())) {
            refused.accept("invalid room fields");
            return;
        }
        if (descriptor.maxPlayers() < 1 || descriptor.maxPlayers() > Protocol.MAX_PLAYERS_RELAY) {
            refused.accept("invalid player limit");
            return;
        }
        String poolProblem = voteTrackPoolProblem(descriptor.gameId(), create.voteTrackKeys());
        if (poolProblem != null) {
            refused.accept("invalid vote track pool: " + poolProblem);
            return;
        }
        if (descriptor.verified() && !"RELAY".equals(routing)) {
            refused.accept(
                    "verified rooms are relay-only");
            return;
        }
        if (descriptor.verified()
                && ladder.tierOf(member.fingerprint) != TrustLadder.Tier.TRUSTED) {
            refused.accept(
                    "verified rooms require TRUSTED");
            return;
        }
        if (descriptor.verified() && (verifiers == null
                || !verifiers.verifierAvailable(create.determinismFingerprint()))) {
            refused.accept(
                    "no verifier available for this build");
            return;
        }
        if ("DIRECT".equals(routing)
                && descriptor.maxPlayers() > Protocol.MAX_PLAYERS_DIRECT) {
            refused.accept(
                    "direct rooms are capped at 8 players - use relay routing");
            return;
        }
        if ("DIRECT".equals(routing)
                && (create.directPort() < 1 || create.directPort() > 65_535)) {
            refused.accept("invalid direct port");
            return;
        }
        if ("DIRECT".equals(routing) && (create.certificateSha256() == null
                || !create.certificateSha256().matches("[0-9a-f]{64}"))) {
            refused.accept(
                    "direct rooms require a TLS certificate pin");
            return;
        }
        if (!ladder.canCreateRoom(member.fingerprint)) {
            refused.accept(
                    "new identities cannot create rooms");
            return;
        }
        boolean direct = "DIRECT".equals(routing);
        if (create.accessEpoch() < 0
                || direct && (create.password() != null
                || create.instanceId() != null && !boundedField(create.instanceId(), 128)
                || descriptor.accessPolicy().passwordRequired()
                && !boundedField(create.instanceId(), 128))
                || !direct && descriptor.accessPolicy().passwordRequired() != (create.password() != null)) {
            refused.accept("invalid room access configuration");
            return;
        }
        member.createdRequests.values().removeIf(created -> clock.getAsLong() >= created.expiresAtMillis());
        if (member.pendingCreates.containsKey(requestId) || requestId > 0
                && (member.createdRequests.containsKey(requestId)
                || member.createdRequests.size() >= MAX_RECENT_CREATES)
                || member.pendingCreates.size() >= MAX_PENDING_PER_CONNECTION
                || pendingCreateCount >= MAX_PENDING_CREATES) {
            refused.accept("room creation busy");
            return;
        }
        SessionRegistry.RoomEntry entry = null;
        try {
            String instanceId = direct ? create.instanceId() : java.util.UUID.randomUUID().toString();
            entry = registry.reserve(descriptor, routing, member.fingerprint,
                    member.connection.remoteHost(), create.directPort(),
                    create.determinismFingerprint(), create.voteTrackKeys(),
                    create.certificateSha256(), member.lease, instanceId,
                    direct ? create.accessEpoch() : 0);
            if (direct) {
                if (!registry.publishPendingDirect(entry)) {
                    throw new IllegalStateException("registration canceled");
                }
                sendCreated(member, entry, requestId);
                return;
            }
            PendingCreate pending = new PendingCreate(member, requestId, entry,
                    clock.getAsLong() + CREATE_TIMEOUT_MILLIS);
            WorkBudget.Reservation completion = WorkBudget.PROCESS.tryReserve(member.creationWork,
                    inboundWork, new WorkBudget.Cost(1, 2L * Protocol.MAX_CONTROL_BYTES)).orElseThrow(
                            () -> new IllegalStateException("room completion capacity unavailable"));
            member.pendingCreates.put(requestId, pending);
            pendingCreateCount++;
            try {
                relays.prepareRelayRoom(entry, create.password()).whenComplete((prepared, error) -> {
                    WorkBudget.Task task = WorkBudget.Task.reserved(completion, List.of(completion),
                            () -> completeCreate(pending, prepared, error),
                            () -> { if (prepared != null) prepared.close(); });
                    try { brokerLoop.execute(task); }
                    catch (RuntimeException | Error stopped) {
                        task.close();
                        if (stopped instanceof Error fatal) throw fatal;
                    }
                });
            } catch (RuntimeException | Error failure) {
                completion.close();
                throw failure;
            }
        } catch (SessionRegistry.RoomCreateException | RuntimeException failure) {
            PendingCreate pending = member.pendingCreates.get(requestId);
            if (pending != null) cancelPendingCreate(pending);
            if (entry != null) removeRoom(entry);
            refused.accept(failure instanceof SessionRegistry.RoomCreateException
                    ? failure.getMessage() : "room creation unavailable");
        }
    }

    private void completeCreate(PendingCreate pending, RelayRoomDirectory.PreparedRoom prepared,
                                Throwable failure) {
        Member member = pending.member();
        if (member.pendingCreates.get(pending.requestId()) == pending
                && members.get(member.connection) == member && member.state == State.ADMITTED
                && clock.getAsLong() >= pending.expiresAtMillis()) {
            cancelPendingCreate(pending);
            if (prepared != null) prepared.close();
            send(member, new ControlMessage.RoomCreateRejected("room creation timed out"), pending.requestId());
            return;
        }
        if (member.pendingCreates.get(pending.requestId()) != pending
                || members.get(member.connection) != member || member.state != State.ADMITTED
                || clock.getAsLong() >= pending.expiresAtMillis()
                || ladder.isBanned(member.fingerprint)) {
            cancelPendingCreate(pending);
            if (prepared != null) prepared.close();
            return;
        }
        member.pendingCreates.remove(pending.requestId());
        pendingCreateCount--;
        if (failure != null || prepared == null) {
            registry.remove(pending.entry().roomId());
            if (prepared != null) prepared.close();
            send(member, new ControlMessage.RoomCreateRejected("room creation unavailable"),
                    pending.requestId());
            return;
        }
        SessionRegistry.RoomEntry entry = pending.entry();
        try {
            invites.register(entry.roomId(), member.lease, entry.instanceId());
            prepared.publish(inviteService(entry));
            if (!registry.publish(entry)) throw new IllegalStateException("registration canceled");
            member.joinGrantedRooms.grant(entry, clock.getAsLong(), null);
            sendCreated(member, entry, pending.requestId());
        } catch (RuntimeException unavailable) {
            removeRoom(entry);
            prepared.close();
            send(member, new ControlMessage.RoomCreateRejected("room creation unavailable"),
                    pending.requestId());
        }
    }

    private void sendCreated(Member member, SessionRegistry.RoomEntry entry, long requestId) {
        if (requestId > 0) member.createdRequests.put(requestId,
                new CreatedRequest(entry, clock.getAsLong() + CREATE_TIMEOUT_MILLIS));
        send(member, new ControlMessage.RoomCreated(entry.roomId(), entry.instanceId(),
                entry.accessEpoch(), entry.inviteEpoch()), requestId);
    }

    private void activateRoom(Member member, ControlMessage.RoomActivate request, long requestId) {
        SessionRegistry.RoomEntry room = registry.find(request.roomId()).orElse(null);
        boolean active = room != null && "DIRECT".equals(room.routing())
                && member.lease.equals(room.lease())
                && room.instanceId().equals(request.instanceId());
        if (active && !registry.isJoinable(room)) {
            try {
                tunnels.registerHost(room.roomId(), room.lease());
                invites.register(room.roomId(), room.lease(), room.instanceId());
                active = registry.activateDirect(room);
            } catch (InviteDirectory.UnavailableException unavailable) {
                removeRoom(room);
                active = false;
            } catch (RuntimeException | Error failure) {
                removeRoom(room);
                throw failure;
            }
        }
        send(member, new ControlMessage.RoomActivated(request.roomId(), request.instanceId(), active),
                requestId);
    }

    private void cancelPendingCreate(PendingCreate pending) {
        if (pending.member().pendingCreates.remove(pending.requestId(), pending)) {
            pendingCreateCount--;
            registry.remove(pending.entry().roomId());
        }
    }

    private void cancelPendingCreates(Member member) {
        for (PendingCreate pending : List.copyOf(member.pendingCreates.values())) {
            cancelPendingCreate(pending);
        }
    }

    private void cancelCreate(Member member, long requestId) {
        if (requestId < 1) return;
        PendingCreate pending = member.pendingCreates.get(requestId);
        if (pending != null) cancelPendingCreate(pending);
        CreatedRequest created = member.createdRequests.remove(requestId);
        if (created == null || clock.getAsLong() >= created.expiresAtMillis()) return;
        SessionRegistry.RoomEntry room = registry.find(created.entry().roomId()).orElse(null);
        if (room != null && member.lease.equals(room.lease())
                && java.util.Objects.equals(room.instanceId(), created.entry().instanceId())) removeRoom(room);
    }

    private void listRooms(Member member, ControlMessage.RoomListRequest request, long requestId) {
        long now = clock.getAsLong();
        if (member.lastListMillis != Long.MIN_VALUE
                && now - member.lastListMillis < LIST_INTERVAL_MILLIS) {
            send(member, new ControlMessage.RoomListResult(List.of(), request.page(),
                    Protocol.ROOM_LIST_RATE_LIMITED), requestId);
            return;
        }
        member.lastListMillis = now;
        int page = Math.max(0, request.page());
        List<SessionRegistry.RoomEntry> all = registry.listed(request.gameFilter());
        int from = (int) Math.min(all.size(),
                Math.min(Integer.MAX_VALUE, (long) page * config.browserPageSize()));
        int to = Math.min(all.size(), from + config.browserPageSize());
        List<ControlMessage.RoomSummary> summaries = new ArrayList<>();
        for (SessionRegistry.RoomEntry room : all.subList(from, to)) {
            ControlMessage.RoomDescriptor descriptor = room.descriptor();
            summaries.add(new ControlMessage.RoomSummary(room.roomId(), descriptor.name(),
                    descriptor.gameId(), descriptor.zone(), descriptor.act(),
                    descriptor.characterPolicy(), room.playerCount(), descriptor.maxPlayers(),
                    room.routing(), descriptor.verified(), descriptor.chatMode()));
        }
        send(member, new ControlMessage.RoomListResult(summaries, page,
                registry.totalPagesForCount(all.size())), requestId);
    }

    private void joinRoom(Member member, ControlMessage.RoomJoinRequest request, long requestId) {
        SessionRegistry.RoomEntry room = registry.find(request.roomId()).orElse(null);
        if (!registry.isJoinable(room)) room = null;
        if (room != null && room.descriptor().accessPolicy().passwordRequired()
                && !room.hostFingerprint().equals(member.fingerprint)) room = null;
        joinRoom(member, room, null, requestId);
    }

    private void resolveInvite(Member member, ControlMessage.RoomInviteResolve request, long requestId) {
        InviteDirectory.Lookup lookup = invites.resolve(request.code(), member.fingerprint,
                member.connection.remoteHost());
        switch (lookup) {
            case InviteDirectory.Lookup.RateLimited limited -> send(member,
                    new ControlMessage.RoomInviteRejected("invite lookup rate limited",
                            limited.retryAfterMillis()), requestId);
            case InviteDirectory.Lookup.Unavailable ignored -> send(member,
                    new ControlMessage.RoomInviteRejected("invite unavailable", 0), requestId);
            case InviteDirectory.Lookup.Found found -> {
                InviteDirectory.Resolution invite = found.resolution();
                SessionRegistry.RoomEntry room = registry.find(invite.roomId()).orElse(null);
                if (!registry.isJoinable(room) || !invite.lease().equals(room.lease())
                        || !invite.instanceId().equals(room.instanceId())
                        || invite.epoch() != room.inviteEpoch()) {
                    send(member, new ControlMessage.RoomInviteRejected("invite unavailable", 0), requestId);
                } else {
                    joinRoom(member, room, invite, requestId);
                }
            }
        }
    }

    private void joinRoom(Member member, SessionRegistry.RoomEntry room,
                          InviteDirectory.Resolution invite, long requestId) {
        long now = clock.getAsLong();
        while (!member.joinRequests.isEmpty()
                && now - member.joinRequests.peekFirst() >= JOIN_WINDOW_MILLIS) {
            member.joinRequests.removeFirst();
        }
        String problem;
        if (member.joinRequests.size() >= JOIN_LIMIT) {
            problem = "join rate limited";
        } else {
            member.joinRequests.addLast(now);
            problem = joinProblem(member, room);
        }
        if (problem != null) {
            send(member, new ControlMessage.RoomJoinRejected(problem), requestId);
            return;
        }
        AttachGrant grant = member.joinGrantedRooms.grant(room, now, invite);
        if (grant == null) {
            send(member, new ControlMessage.RoomJoinRejected("join route still active"), requestId);
            return;
        }
        send(member, joinResult(room, grant, invite), requestId);
    }

    private ControlMessage.RoomJoinResult joinResult(SessionRegistry.RoomEntry room,
                                                      AttachGrant grant,
                                                      InviteDirectory.Resolution invite) {
        return new ControlMessage.RoomJoinResult(room.roomId(), room.routing(),
                "DIRECT".equals(room.routing()) ? room.hostAddress() : null,
                room.directPort(), "DIRECT".equals(room.routing())
                ? room.hostFingerprint() : masterIdentity.fingerprint(),
                room.determinismFingerprint(), room.certificateSha256(), room.instanceId(),
                room.descriptor().accessPolicy(), room.accessEpoch(), room.inviteEpoch(),
                invite == null ? 0 : invite.expiresAtMillis(),
                grant.reference(), grant.generation());
    }

    private void redeemGrant(Member host, ControlMessage.RoomGrantRedeem request,
                             long requestId) {
        SessionRegistry.RoomEntry room = registry.find(request.roomId()).orElse(null);
        long now = clock.getAsLong();
        boolean allowed = false;
        if (room != null && "DIRECT".equals(room.routing())
                && room.lease().equals(host.lease)
                && room.instanceId().equals(request.instanceId())) {
            for (Member guest : members.values()) {
                if (!request.fingerprint().equals(guest.fingerprint)
                        || guest.state != State.ADMITTED) continue;
                AttachGrant grant = guest.joinGrantedRooms.issued(request.roomId(),
                        request.grantReference(), request.routeGeneration(), now);
                if (grant == null || !room.lease().equals(grant.lease())
                        || !room.instanceId().equals(grant.instanceId())
                        || room.accessEpoch() != grant.accessEpoch()
                        || room.inviteEpoch() != grant.inviteEpoch()
                        || grant.invite() != null && !invites.isCurrent(grant.invite())
                        || joinProblem(guest, room) != null) continue;
                guest.joinGrantedRooms.redeem(grant);
                allowed = true;
                break;
            }
        }
        send(host, new ControlMessage.RoomGrantDecision(request.roomId(),
                request.instanceId(), request.grantReference(), request.routeGeneration(),
                request.fingerprint(), allowed), requestId);
    }

    private void retireRoute(Member guest, ControlMessage.RoomRouteRetire request,
                             long requestId) {
        SessionRegistry.RoomEntry room = registry.find(request.roomId()).orElse(null);
        Member host = room == null || room.lease() == null ? null
                : members.get(room.lease().source());
        if (requestId <= 0 || room == null || !"DIRECT".equals(room.routing())
                || !room.instanceId().equals(request.instanceId())
                || host == null || host.state != State.ADMITTED
                || !host.lease.equals(room.lease())
                || pendingRetires.size() >= MAX_PENDING_RETIREMENTS
                || nextRetirementId == Long.MAX_VALUE) {
            send(guest, new ControlMessage.RoomRouteUnavailable(
                    "direct route retirement unavailable"), requestId);
            return;
        }
        AttachGrant grant = guest.joinGrantedRooms.retire(request.roomId(),
                request.grantReference(), request.routeGeneration(), clock.getAsLong());
        if (grant == null || !room.lease().equals(grant.lease())
                || !room.instanceId().equals(grant.instanceId())) {
            send(guest, new ControlMessage.RoomRouteUnavailable(
                    "direct route retirement unavailable"), requestId);
            return;
        }
        long retirementId = ++nextRetirementId;
        pendingRetires.put(retirementId, new PendingRetire(guest, host, grant, requestId,
                clock.getAsLong() + ROUTE_RETIRE_TIMEOUT_MILLIS, request.renew()));
        try {
            send(host, new ControlMessage.RoomRouteRetireNotice(request.roomId(),
                    request.instanceId(), grant.reference(), grant.generation(), retirementId));
        } catch (RuntimeException | Error failure) {
            pendingRetires.remove(retirementId);
            if (!(failure instanceof java.util.concurrent.RejectedExecutionException)) throw failure;
            send(guest, new ControlMessage.RoomRouteUnavailable(
                    "direct route retirement unavailable"), requestId);
        }
    }

    private void acknowledgeRetirement(Member host, ControlMessage.RoomRouteRetireAck ack) {
        PendingRetire pending = pendingRetires.get(ack.retirementId());
        if (pending == null || pending.host() != host) return;
        AttachGrant old = pending.grant();
        if (!old.roomId().equals(ack.roomId())
                || !old.instanceId().equals(ack.instanceId())
                || !old.reference().equals(ack.grantReference())
                || old.generation() != ack.routeGeneration()) return;
        pendingRetires.remove(ack.retirementId(), pending);
        Member guest = pending.guest();
        if (members.get(guest.connection) != guest) return;
        if (!guest.joinGrantedRooms.finishRetire(old)) return;
        if (!pending.renew()) {
            send(guest, new ControlMessage.RoomRouteRetired(old.roomId(), old.instanceId(),
                    old.reference(), old.generation()), pending.requestId());
            return;
        }
        SessionRegistry.RoomEntry room = registry.find(old.roomId()).orElse(null);
        if (room == null || !"DIRECT".equals(room.routing())
                || !room.lease().equals(host.lease)
                || !old.instanceId().equals(room.instanceId())
                || old.accessEpoch() != room.accessEpoch()
                || old.inviteEpoch() != room.inviteEpoch()
                || old.invite() != null && !invites.isCurrent(old.invite())
                || joinProblem(guest, room) != null) {
            send(guest, new ControlMessage.RoomRouteUnavailable(
                    "room access changed during route retirement"), pending.requestId());
            return;
        }
        AttachGrant fresh = guest.joinGrantedRooms.grant(room, clock.getAsLong(), old.invite());
        if (fresh == null) {
            send(guest, new ControlMessage.RoomRouteUnavailable(
                    "fresh join route unavailable"), pending.requestId());
            return;
        }
        send(guest, new ControlMessage.RoomRouteRenewed(
                joinResult(room, fresh, old.invite())), pending.requestId());
    }

    private String joinProblem(Member member, SessionRegistry.RoomEntry room) {
        if (room == null) return "room not found";
        if (ladder.isBanned(member.fingerprint)) return "account sanctioned";
        if (!java.util.Objects.equals(member.determinismFingerprint, room.determinismFingerprint())) {
            return "determinism fingerprint mismatch";
        }
        if (room.descriptor().verified()
                && ladder.tierOf(member.fingerprint) != TrustLadder.Tier.TRUSTED) {
            return "verified rooms require TRUSTED";
        }
        // Directory counts are periodic discovery snapshots, not slot leases:
        // a departure may already have freed capacity in the owning RoomHost.
        // Keep the NEW-identity pressure policy; the room enforces actual capacity.
        boolean underPressure = room.playerCount() * 5 >= room.descriptor().maxPlayers() * 4;
        if (underPressure && ladder.tierOf(member.fingerprint) == TrustLadder.Tier.NEW) {
            return "room under pressure";
        }
        return null;
    }

    private void attach(Member member, ControlMessage.RelayAttach attach, long requestId) {
        String roomId = attach.roomId();
        AttachGrant grant = member.joinGrantedRooms.consume(roomId,
                attach.grantReference(), attach.routeGeneration(), clock.getAsLong());
        SessionRegistry.RoomEntry room = registry.find(roomId).orElse(null);
        if (grant == null || room == null || !java.util.Objects.equals(grant.instanceId(), room.instanceId())
                || grant.accessEpoch() != room.accessEpoch()
                || grant.inviteEpoch() != room.inviteEpoch()
                || grant.invite() != null && !invites.isCurrent(grant.invite())) {
            send(member, new ControlMessage.RoomJoinRejected("join not granted"), requestId);
            return;
        }
        String problem = joinProblem(member, room);
        if (problem != null) {
            send(member, new ControlMessage.RoomJoinRejected(problem), requestId);
            return;
        }
        if ("DIRECT".equals(room.routing()) && room.hostFingerprint().equals(member.fingerprint)) {
            send(member, new ControlMessage.RoomJoinRejected(
                    "host cannot tunnel into its own direct room"), requestId);
            return;
        }
        if ("RELAY".equals(room.routing())) {
            boolean isNew = ladder.tierOf(member.fingerprint) == TrustLadder.Tier.NEW;
            relays.noteGuestTier(member.fingerprint, isNew);
            if (!relays.attach(member.connection, roomId, member.fingerprint, member.displayName,
                    grant.instanceId(), grant.reference(), grant.generation())) {
                send(member, new ControlMessage.RoomJoinRejected("room not found"), requestId);
                return;
            }
            attachResults.put(member.connection, new AttachResult(AttachMode.ROOM, roomId, -1));
        } else {
            OptionalInt guestId = tunnels.openGuest(roomId, member.connection,
                    grant.instanceId(), member.fingerprint, grant.reference(),
                    grant.generation(), grant.lease());
            if (guestId.isEmpty()) {
                send(member, new ControlMessage.RoomJoinRejected("relay unavailable"), requestId);
                return;
            }
            attachResults.put(member.connection,
                    new AttachResult(AttachMode.TUNNEL_GUEST, roomId, guestId.getAsInt()));
        }
        cancelPendingCreates(member);
        Member owner = room.lease() == null ? null : members.get(room.lease().source());
        if (owner != null && room.hostFingerprint().equals(member.fingerprint)) {
            owner.createdRequests.values().removeIf(created ->
                    created.entry().roomId().equals(roomId) && created.entry().lease().equals(room.lease()));
        }
        member.state = State.HANDED_OFF;
    }

    private RoomInviteService inviteService(SessionRegistry.RoomEntry registration) {
        WorkBudget.Account source = WorkBudget.PROCESS.account(128, 1024 * 1024);
        return (instanceId, accessEpoch, inviteEpoch, operation, membersCanInvite) -> {
            CompletableFuture<ControlMessage.RoomInviteControlResult> result = new CompletableFuture<>();
            long bytes = 2L * registration.roomId().length()
                    + (instanceId == null ? 0 : 2L * instanceId.length());
            WorkBudget.Reservation reservation = WorkBudget.PROCESS.tryReserve(source,
                    inboundWork, new WorkBudget.Cost(1, bytes)).orElse(null);
            if (reservation == null) return CompletableFuture.failedFuture(
                    new IllegalStateException("room invite capacity unavailable"));
            WorkBudget.Task task = WorkBudget.Task.reserved(reservation, List.of(reservation), () -> {
                try {
                    result.complete(applyInviteControl(registration.roomId(), registration.lease(),
                            instanceId, accessEpoch, inviteEpoch, operation, membersCanInvite));
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            }, () -> result.completeExceptionally(new IllegalStateException("broker unavailable")));
            try {
                brokerLoop.execute(task);
            } catch (RuntimeException | Error stopped) {
                task.close();
                if (stopped instanceof Error fatal) throw fatal;
            }
            return result;
        };
    }

    private void controlInvite(Member member, ControlMessage.RoomInviteControl request, long requestId) {
        try {
            SessionRegistry.RoomEntry room = registry.find(request.roomId()).orElse(null);
            if (room == null || !"DIRECT".equals(room.routing())) {
                throw new InviteDirectory.UnavailableException(0);
            }
            send(member, applyInviteControl(request.roomId(), member.lease, request.instanceId(),
                    request.accessEpoch(), request.inviteEpoch(), request.operation(),
                    request.membersCanInvite()), requestId);
        } catch (InviteDirectory.UnavailableException unavailable) {
            send(member, new ControlMessage.RoomInviteRejected("invite unavailable",
                    unavailable.retryAfterMillis()), requestId);
        } catch (RuntimeException unavailable) {
            send(member, new ControlMessage.RoomInviteRejected("invite unavailable", 0), requestId);
        }
    }

    private ControlMessage.RoomInviteControlResult applyInviteControl(String roomId,
            SessionRegistry.RegistrationLease lease, String instanceId, long accessEpoch,
            long inviteEpoch, ControlMessage.InviteOperation operation, boolean membersCanInvite) {
        SessionRegistry.RoomEntry room = registry.find(roomId).orElse(null);
        if (room == null || !lease.equals(room.lease()) || !java.util.Objects.equals(room.instanceId(), instanceId)
                || operation == null) {
            throw new InviteDirectory.UnavailableException(0);
        }
        if (operation == ControlMessage.InviteOperation.FETCH && "DIRECT".equals(room.routing())
                && room.inviteEpoch() == 0 && accessEpoch == room.accessEpoch() && inviteEpoch > 0) {
            long floor = invites.seedEpochFloor(roomId, lease, inviteEpoch);
            registry.updateAccess(roomId, lease, room.descriptor().accessPolicy(), accessEpoch, floor);
            room = registry.find(roomId).orElseThrow();
        }
        AccessChange committed = accessChanges.get(roomId);
        if (operation == ControlMessage.InviteOperation.ACCESS_CHANGED && committed != null
                && lease.equals(committed.lease()) && instanceId.equals(committed.instanceId())
                && accessEpoch == committed.accessEpoch() && inviteEpoch == committed.fromInviteEpoch()
                && room.accessEpoch() == committed.accessEpoch()
                && room.inviteEpoch() == committed.toInviteEpoch()) {
            return inviteResult(room.descriptor().accessPolicy(), room.accessEpoch(),
                    invites.current(roomId, lease));
        }
        boolean fetch = operation == ControlMessage.InviteOperation.FETCH;
        long expectedAccessEpoch = operation == ControlMessage.InviteOperation.ACCESS_CHANGED
                ? room.accessEpoch() + 1 : room.accessEpoch();
        if (fetch && (accessEpoch > room.accessEpoch() || inviteEpoch > room.inviteEpoch())
                || !fetch && (room.inviteEpoch() != inviteEpoch || accessEpoch != expectedAccessEpoch)) {
            throw new InviteDirectory.UnavailableException(0);
        }
        long nextAccessEpoch = operation == ControlMessage.InviteOperation.ACCESS_CHANGED
                ? accessEpoch : room.accessEpoch();
        RoomAccessPolicy old = room.descriptor().accessPolicy();
        RoomAccessPolicy policy = operation == ControlMessage.InviteOperation.SET_SHARING
                ? new RoomAccessPolicy(old.mode(), membersCanInvite) : old;
        boolean rotate = operation == ControlMessage.InviteOperation.ROTATE
                || operation == ControlMessage.InviteOperation.ACCESS_CHANGED
                || operation == ControlMessage.InviteOperation.SET_SHARING;
        InviteDirectory.Invitation invitation = rotate
                ? invites.rotate(roomId, lease) : invites.reveal(roomId, lease);
        if (!registry.updateAccess(roomId, lease, policy, nextAccessEpoch, invitation.epoch())) {
            throw new InviteDirectory.UnavailableException(0);
        }
        if (operation == ControlMessage.InviteOperation.ACCESS_CHANGED) {
            accessChanges.put(roomId, new AccessChange(lease, instanceId, nextAccessEpoch,
                    inviteEpoch, invitation.epoch()));
        }
        return inviteResult(policy, nextAccessEpoch, invitation);
    }

    private static ControlMessage.RoomInviteControlResult inviteResult(RoomAccessPolicy policy,
            long accessEpoch, InviteDirectory.Invitation invitation) {
        return new ControlMessage.RoomInviteControlResult(policy, new ControlMessage.RoomInvite(
                invitation.code(), invitation.instanceId(), accessEpoch,
                invitation.epoch(), invitation.expiresAtMillis()));
    }

    private void heartbeat(Member member, ControlMessage.Heartbeat heartbeat) {
        registry.find(heartbeat.roomId()).filter(room ->
                member.lease.equals(room.lease()))
                .ifPresent(room -> registry.heartbeat(room.roomId(), heartbeat.playerCount()));
    }

    private void updateTrack(Member member, ControlMessage.RoomTrackUpdate update) {
        SessionRegistry.RoomEntry room = registry.find(update.roomId()).orElse(null);
        if (room == null || !member.lease.equals(room.lease())) {
            strike(member, "unauthorized room track update");
            return;
        }
        String key = room.descriptor().gameId() + ":" + update.zone() + ":" + update.act();
        if (!knownTrack.test(key)) {
            strike(member, "unknown room track update");
            return;
        }
        registry.updateTrack(room.roomId(), member.fingerprint, update.zone(), update.act());
    }

    /**
     * Why a client-supplied vote pool is refused, or null: every key must be a well-formed
     * {@code game:zone:act} of the room's own game that the master knows as an eligible track
     * (custom pools cannot mix games). The 32-key bound is enforced by {@code RoomCreate} itself.
     */
    private String voteTrackPoolProblem(String gameId, List<String> trackKeys) {
        for (String key : trackKeys) {
            String[] parts = key.split(":", -1);
            if (parts.length != 3 || !parts[1].matches("[0-9]{1,3}") || !parts[2].matches("[0-9]{1,3}")) {
                return "malformed track key " + key;
            }
            if (!parts[0].equals(gameId)) {
                return key + " is not a " + gameId + " act";
            }
            if (!knownTrack.test(key)) {
                return key + " is not an eligible Time Attack act";
            }
        }
        if (trackKeys.stream().distinct().count() != trackKeys.size()) {
            return "duplicate track key";
        }
        return null;
    }

    private static boolean validDescriptorFields(ControlMessage.RoomDescriptor descriptor,
                                                 String determinismFingerprint) {
        return boundedField(descriptor.name(), 64)
                && com.openggf.net.protocol.ChatMode.parse(descriptor.chatMode()) != null
                && boundedField(descriptor.gameId(), 32)
                && ("OPEN".equals(descriptor.characterPolicy())
                || "LOCKED".equals(descriptor.characterPolicy()))
                && (!"LOCKED".equals(descriptor.characterPolicy())
                || boundedField(descriptor.lockedCharacter(), 32))
                && boundedField(determinismFingerprint, 128);
    }

    private void updateChatMode(Member member, ControlMessage.RoomChatModeUpdate update) {
        SessionRegistry.RoomEntry room = registry.find(update.roomId()).orElse(null);
        // Relay descriptors come from RelayRoomManager; a creator cannot advertise a mode
        // that differs from the public host's enforced policy.
        if (room != null && member.lease.equals(room.lease()) && "DIRECT".equals(room.routing())) {
            registry.updateChatMode(update.roomId(), member.fingerprint, update.mode());
        }
    }

    private static boolean boundedField(String value, int maxBytes) {
        return value != null && !value.isBlank()
                && value.getBytes(StandardCharsets.UTF_8).length <= maxBytes
                && value.codePoints().noneMatch(codePoint -> codePoint < 0x20
                || codePoint == '"' || codePoint == '\\');
    }

    private void leaveRoom(Member member, String roomId) {
        SessionRegistry.RoomEntry room = registry.find(roomId).orElse(null);
        if (room == null || !member.lease.equals(room.lease())) {
            return;
        }
        removeRoom(room);
    }

    private void removeRoom(SessionRegistry.RoomEntry room) {
        String roomId = room.roomId();
        registry.remove(roomId);
        invites.remove(roomId, room.lease());
        accessChanges.remove(roomId);
        if ("RELAY".equals(room.routing())) {
            relays.hostLeft(roomId);
        } else {
            tunnels.unregisterHost(roomId, room.lease());
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
        if (members.remove(member.connection) == null) {
            return;
        }
        attachResults.remove(member.connection);
        tokens.revoke(member.token);
        tokenFingerprints.remove(member.token);
        cancelPendingCreates(member);
        removeHostedRooms(member);
        member.connection.close(reason);
    }

    private void removeHostedRooms(Member member) {
        if (member.fingerprint == null) {
            return;
        }
        for (SessionRegistry.RoomEntry room
                : registry.removeByLease(member.lease)) {
            invites.remove(room.roomId(), room.lease());
            accessChanges.remove(room.roomId());
            if ("RELAY".equals(room.routing())) {
                relays.hostLeft(room.roomId());
            } else {
                tunnels.unregisterHost(room.roomId(), room.lease());
            }
        }
    }

    private static void send(Member member, ControlMessage message) {
        member.connection.sendText(ControlCodec.encode(null, message));
    }

    private static void send(Member member, ControlMessage message, long requestId) {
        member.connection.sendText(ControlCodec.encode(null, message, requestId));
    }
}
