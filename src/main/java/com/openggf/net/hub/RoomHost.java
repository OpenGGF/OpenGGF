package com.openggf.net.hub;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.identity.DisplayNames;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.net.protocol.ChatMode;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.QuickChatPhrase;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;
import com.openggf.net.protocol.ReadyRules;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;
import com.openggf.net.protocol.AuthenticationTranscript;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Single-threaded room protocol driver and owner of its hub and round state. */
@com.openggf.game.ModApi
public final class RoomHost {
    @com.openggf.game.ModApi
    public enum ListenerAdmission { MANUAL, BROKER_PENDING, BROKER_REQUIRED, BROKER_UNAVAILABLE }

    @FunctionalInterface
    interface BrokerAdmission {
        CompletableFuture<ControlMessage.RoomGrantDecision> redeem(
                String grantReference, long routeGeneration, String fingerprint);
    }

    public static final long ADMISSION_TIMEOUT_MILLIS = 5000;
    public static final long ACCESS_TIMEOUT_MILLIS = 15_000;
    public static final long STANDINGS_PAGE_INTERVAL_MILLIS = 2000;
    public static final int STANDINGS_PAGE_SIZE = 10;
    private static final int TOKEN_STRIKE_LIMIT = 3;
    private static final int MAX_DISPLAY_NAME_BYTES = 64;
    private static final int MAX_CHARACTER_BYTES = 32;

    private enum Admission { IDENTITY, ACCESS_REQUIRED, VERIFYING, ADMITTED, CLOSED }

    private static final class Member {
        final HubConnection connection;
        final HostHandshake handshake;
        final long connectedAt;
        final String expectedFingerprint;
        final String expectedRouteReference;
        final long expectedRouteGeneration;
        Admission admission = Admission.IDENTITY;
        HostHandshake.Admit proof;
        String challenge;
        long challengeEpoch;
        long accessDeadline;
        boolean admitted() { return admission == Admission.ADMITTED; }
        int slot = -1;
        String token;
        String participantId;
        String fingerprint;
        String displayName;
        String character;
        ControlMessage.RaceLaunch launch;
        String grantReference;
        long routeGeneration;
        ControlMessage.RelayGuestOpen trustedTunnel;
        PlayerAppearance appearance = PlayerAppearance.STOCK;
        ControlMessage.PlayerInfo publishedInfo;
        long lastProfileMillis = Long.MIN_VALUE;
        long memberSinceMillis;
        long lastChatMillis = Long.MIN_VALUE;
        long lastStandingsPageMillis = Long.MIN_VALUE;
        int tokenStrikes;
        int violationsThisRound;
        boolean finishedThisRound;
        int activeAttemptId = -1;
        int lastAttemptId = -1;
        // Latest start refused only because the window was not open. Its in-flight
        // frames, reset and finish are dropped quietly rather than counted.
        int refusedAttemptId = -1;
        boolean ready;
        long lastReadyMillis = Long.MIN_VALUE;

        Member(HubConnection connection, HostHandshake handshake, long connectedAt,
               String expectedFingerprint, String expectedRouteReference,
               long expectedRouteGeneration) {
            this.connection = connection;
            this.handshake = handshake;
            this.connectedAt = connectedAt;
            this.expectedFingerprint = expectedFingerprint;
            this.expectedRouteReference = expectedRouteReference;
            this.expectedRouteGeneration = expectedRouteGeneration;
        }
    }

    private final RoomHostConfig config;
    private final PlayerIdentity hostIdentity;
    private final LongSupplier wallClockMillis;
    private final SessionTokenIssuer tokens = new SessionTokenIssuer();
    private final GhostHub hub;
    private final LobbyPresenceRelay lobby;
    private ReadyRules readyRule = ReadyRules.DEFAULT_BY_SIZE;
    private ChatMode chatMode;
    // Whether the owner's round start currently waits for everyone, as last told to the room.
    private boolean readyRequiredAnnounced = true;
    private final HostRoundEngine round;
    private final RoomHostHooks hooks;
    private final ControlJsonCodec codec;
    private final Map<HubConnection, Member> members = new LinkedHashMap<>();
    private ListenerAdmission listenerAdmission = ListenerAdmission.MANUAL;
    private BrokerAdmission brokerAdmission;
    private String listedRoomId;
    // Slots in the last broadcast top ten, for private rank updates on push-out.
    private Set<Integer> broadcastTopSlots = Set.of();
    private String roomGameId;
    private int roomZone;
    private int roomAct;
    private boolean closing;
    private volatile boolean closed;
    private final RoomPasswordService passwordService;
    private final CompletableFuture<RoomPasswordService.Verifier> preparation;
    private static final long MAX_COMPLETION_BYTES = 2L * Protocol.MAX_CONTROL_BYTES;
    private final WorkBudget.Account completionWork = WorkBudget.PROCESS.account(512, 8L * 1024 * 1024);
    private final ConcurrentLinkedQueue<WorkBudget.Task> completions = new ConcurrentLinkedQueue<>();
    private boolean inviteOperationPending;
    private static final class PasswordChange {
        final long fromEpoch;
        final Member requester;
        RoomPasswordService.Verifier verifier;
        boolean providerPending;
        PasswordChange(long fromEpoch, Member requester) {
            this.fromEpoch = fromEpoch;
            this.requester = requester;
        }
    }
    private PasswordChange passwordChange;
    private RoomPasswordService.Verifier activeVerifier;
    private boolean accessAuthorityUnavailable;
    private long inviteServiceGeneration;
    private long inviteOperationGeneration;
    private final Object completionLock = new Object();
    private RoomInviteService inviteService;
    private com.openggf.net.protocol.RoomAccessPolicy accessPolicy;
    private long accessEpoch;
    private long inviteEpoch;

    public RoomHost(RoomHostConfig config, PlayerIdentity hostIdentity,
                    LongSupplier wallClockMillis,
                    TrackValidationProfileSource profiles) {
        this(config, hostIdentity, wallClockMillis, profiles, RoomHostHooks.none());
    }

    public RoomHost(RoomHostConfig config, PlayerIdentity hostIdentity,
                    LongSupplier wallClockMillis,
                    TrackValidationProfileSource profiles,
                    RoomHostHooks hooks) {
        this(config, hostIdentity, wallClockMillis, profiles, hooks, new ControlJsonCodec());
    }

    /** Lets a process hosting many rooms share one thread-safe control codec. */
    public RoomHost(RoomHostConfig config, PlayerIdentity hostIdentity,
                    LongSupplier wallClockMillis,
                    TrackValidationProfileSource profiles,
                    RoomHostHooks hooks, ControlJsonCodec codec) {
        this(config, hostIdentity, wallClockMillis, profiles, hooks, codec, RoomPasswordService.SHARED);
    }

    RoomHost(RoomHostConfig config, PlayerIdentity hostIdentity, LongSupplier wallClockMillis,
             TrackValidationProfileSource profiles, RoomHostHooks hooks, ControlJsonCodec codec,
             RoomPasswordService passwordService) {
        this.passwordService = passwordService;
        preparation = config.password() == null ? CompletableFuture.completedFuture(null)
                : passwordService.prepare(config.password());
        this.codec = java.util.Objects.requireNonNull(codec, "codec");
        this.config = redactedConfig(config);
        accessPolicy = config.accessPolicy();
        this.hostIdentity = hostIdentity;
        this.wallClockMillis = wallClockMillis;
        this.hooks = hooks == null ? RoomHostHooks.none() : hooks;
        chatMode = ChatMode.parse(config.chatMode()).intersect(this.hooks.chatModeLimit());
        roomGameId = config.gameId();
        roomZone = config.zone();
        roomAct = config.act();
        this.hub = new GhostHub(wallClockMillis, profiles,
                (slot, fingerprint, kind, detail) -> {
                    Member member = memberForSlot(slot);
                    if (member != null) {
                        member.violationsThisRound++;
                    }
                    System.getLogger(RoomHost.class.getName()).log(
                            System.Logger.Level.WARNING,
                            "ghost violation slot=" + slot + " fp=" + fingerprint + " "
                                    + kind + ": " + detail);
                }, this.hooks.relevanceFiltering());
        this.lobby = new LobbyPresenceRelay(wallClockMillis, this.hooks.relevanceFiltering(),
                (slot, detail) -> System.getLogger(RoomHost.class.getName()).log(
                        System.Logger.Level.WARNING, "lobby presence violation slot=" + slot + ": " + detail));
        this.round = new HostRoundEngine(wallClockMillis, this::broadcast);
        round.setVerifiedRoom(config.verified());
        // A room without an independent replay verifier may retain claims for
        // trust accrual and operator spot checks, but must not present them to
        // peers as competitive standings.
        round.setPublishUnverifiedStandings(false);
        round.setPendingExpiryListener((slot, attemptId) -> {
            if (this.hooks.verificationHooks() != null) {
                this.hooks.verificationHooks().onPendingExpired(
                        this.hooks.roomId(), slot, attemptId);
            }
        });
        round.setVoteTrackPool(config.voteTrackKeys(), this.hooks.knownVoteTrack());
        hub.setTrack(config.gameId(), config.zone(), config.act());
    }

    private static RoomHostConfig redactedConfig(RoomHostConfig source) {
        if (source.password() == null) return source;
        char[] characters = source.password().copyCharacters();
        try (RoomPassword shadow = new RoomPassword(characters)) {
            return new RoomHostConfig(source.roomName(), source.gameId(), source.zone(), source.act(),
                    source.characterPolicy(), source.lockedCharacter(), source.maxPlayers(),
                    source.requiredDeterminismFingerprint(), source.voteTrackKeys(), source.verified(),
                    source.accessPolicy(), shadow, source.instanceId());
        } finally { java.util.Arrays.fill(characters, '\0'); }
    }

    /** Completes when private enforcement is ready; callers publish only after success. */
    public CompletableFuture<RoomHost> ready() { return preparation.thenApply(ignored -> this); }

    public String instanceId() { return config.instanceId(); }

    public com.openggf.net.protocol.RoomAccessPolicy accessPolicy() { return accessPolicy; }

    public long accessEpoch() { return accessEpoch; }

    public long inviteEpoch() { return inviteEpoch; }

    public boolean accessChangePending() { return passwordChange != null || accessAuthorityUnavailable; }

    public void setInviteService(RoomInviteService service) {
        inviteService = service;
        inviteServiceGeneration++;
        inviteOperationGeneration++;
        inviteOperationPending = false;
        if (passwordChange != null) passwordChange.providerPending = false;
        broadcast(new ControlMessage.RoomInviteInvalidated(instanceId()));
    }

    public void setInviteService(RoomInviteService service, long registeredInviteEpoch) {
        if (registeredInviteEpoch < inviteEpoch) throw new IllegalArgumentException("stale invite epoch");
        setInviteService(service);
        inviteEpoch = registeredInviteEpoch;
        broadcast(accessState());
    }

    public void onConnected(HubConnection connection) {
        connect(connection, null, null, null, 0);
    }

    void onConnected(HubConnection connection,
                            ControlMessage.RelayGuestOpen trustedTunnel) {
        connect(connection, trustedTunnel, null, null, 0);
    }

    void onConnectedRelay(HubConnection connection, String brokerFingerprint,
                                 String grantReference, long routeGeneration) {
        if (brokerFingerprint == null || brokerFingerprint.isBlank()
                || listenerAdmission != ListenerAdmission.MANUAL
                || (grantReference == null) != (routeGeneration == 0)
                || grantReference != null && (grantReference.isBlank() || routeGeneration < 1)
                || HandshakeTransport.role(connection)
                        != com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY) {
            connection.close("relay identity unavailable");
            return;
        }
        connect(connection, null, brokerFingerprint, grantReference, routeGeneration);
    }

    private void connect(HubConnection connection,
                         ControlMessage.RelayGuestOpen trustedTunnel,
                         String expectedFingerprint, String expectedRouteReference,
                         long expectedRouteGeneration) {
        drainCompletions();
        if (closed) { connection.close("room closed"); return; }
        if (listenerAdmission == ListenerAdmission.BROKER_PENDING
                || listenerAdmission == ListenerAdmission.BROKER_UNAVAILABLE) {
            connection.close("listed room admission unavailable");
            return;
        }
        if (trustedTunnel != null && listenerAdmission == ListenerAdmission.BROKER_REQUIRED
                && (HandshakeTransport.role(connection)
                        != com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY
                    || !java.util.Objects.equals(listedRoomId, trustedTunnel.roomId())
                    || !instanceId().equals(trustedTunnel.instanceId())
                    || trustedTunnel.fingerprint() == null
                    || trustedTunnel.grantReference() == null
                    || trustedTunnel.routeGeneration() < 1
                    || trustedTunnel.registrationGeneration() < 1)) {
            connection.close("listed tunnel authority mismatch");
            return;
        }
        if (trustedTunnel == null && listenerAdmission == ListenerAdmission.BROKER_REQUIRED
                && HandshakeTransport.role(connection)
                        == com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_RELAY) {
            connection.close("listed tunnel authority missing");
            return;
        }
        Member member = new Member(connection,
                new HostHandshake(hostIdentity.fingerprint(),
                        config.requiredDeterminismFingerprint(), HandshakeTransport.role(connection),
                        HandshakeTransport.binding(connection)),
                wallClockMillis.getAsLong(), expectedFingerprint,
                expectedRouteReference, expectedRouteGeneration);
        member.trustedTunnel = trustedTunnel;
        members.put(connection, member);
    }

    public ListenerAdmission listenerAdmission() { return listenerAdmission; }

    public void brokerPending() {
        if (listenerAdmission != ListenerAdmission.MANUAL) {
            throw new IllegalStateException("listener admission already selected");
        }
        listenerAdmission = ListenerAdmission.BROKER_PENDING;
    }

    void brokerRequired(String roomId, BrokerAdmission admission) {
        if (listenerAdmission != ListenerAdmission.BROKER_PENDING
                || roomId == null || roomId.isBlank() || admission == null) {
            throw new IllegalStateException("listed admission not prepared");
        }
        listedRoomId = roomId;
        brokerAdmission = admission;
        listenerAdmission = ListenerAdmission.BROKER_REQUIRED;
    }

    public void brokerUnavailable() {
        if (listenerAdmission != ListenerAdmission.BROKER_REQUIRED
                && listenerAdmission != ListenerAdmission.BROKER_PENDING) return;
        listenerAdmission = ListenerAdmission.BROKER_UNAVAILABLE;
        brokerAdmission = null;
        for (Member member : List.copyOf(members.values())) {
            if (!member.admitted()) rejectAdmission(member, "listed room admission unavailable");
        }
    }

    public void retireRoute(String reference, long generation) {
        for (Member member : List.copyOf(members.values())) {
            if (reference != null && reference.equals(member.grantReference)
                    && generation == member.routeGeneration) {
                drop(member, "direct join route retired");
            }
        }
    }

    public void onText(HubConnection connection, String text) {
        drainCompletions();
        Member member = members.get(connection);
        if (member == null) {
            connection.close("not connected");
            return;
        }
        final ControlJsonCodec.Decoded decoded;
        try {
            decoded = codec.decodeRoomClient(text);
        } catch (ProtocolViolationException e) {
            drop(member, "protocol violation: " + e.getMessage());
            return;
        }
        try {
            if (!member.admitted()) {
                if (member.admission == Admission.IDENTITY) handleHandshake(member, decoded.message());
                else handleAccess(member, decoded.message());
                return;
            }
            if (!member.token.equals(decoded.token())) {
                if (++member.tokenStrikes >= TOKEN_STRIKE_LIMIT) {
                    drop(member, "session token violations");
                }
                return;
            }
            dispatch(member, decoded.message());
        } finally {
            switch (decoded.message()) {
                case ControlMessage.RoomAccessSubmit submit -> { if (submit.password() != null) submit.password().close(); }
                case ControlMessage.RoomPasswordChange change -> { if (change.password() != null) change.password().close(); }
                case ControlMessage.RoomCreate create -> { if (create.password() != null) create.password().close(); }
                default -> { }
            }
        }
    }

    public void onBinary(HubConnection connection, byte[] data) {
        Member member = members.get(connection);
        if (member == null || !member.admitted()) {
            connection.close("binary before admission");
            members.remove(connection);
            return;
        }
        if (data != null && data.length > 0 && (data[0] & 0xFF) == GhostPackets.TYPE_LOBBY_FRAMES) {
            // Lobby poses outside the between-round phases are a transition race, not abuse.
            if (betweenRounds() && lobby.onFrames(member.slot, data) == LobbyPresenceRelay.Verdict.KICK) {
                drop(member, "lobby presence violations");
            }
            return;
        }
        if (round.phase() != HostRoundEngine.Phase.RUNNING
                || member.activeAttemptId < 0) {
            if (!isRefusedAttemptBatch(member, data)) {
                recordAttemptViolation(member, "binary outside active attempt");
            }
            return;
        }
        hub.onBinary(member.slot, data);
    }

    public void onDisconnected(HubConnection connection) {
        Member member = members.remove(connection);
        if (member != null && member.admitted()) {
            removeAdmittedMember(member);
            publishDeparture(member);
        }
    }

    public void tick() {
        drainCompletions();
        if (closed) return;
        long now = wallClockMillis.getAsLong();
        List<Member> loiterers = new ArrayList<>();
        for (Member member : members.values()) {
            if (!member.admitted() && (member.admission == Admission.IDENTITY
                    ? now - member.connectedAt > ADMISSION_TIMEOUT_MILLIS
                    : now >= member.accessDeadline)) {
                loiterers.add(member);
            }
        }
        for (Member loiterer : loiterers) {
            drop(loiterer, "handshake timeout");
        }
        hub.tick();
        if (betweenRounds()) {
            lobby.tick();
        }
        HostRoundEngine.Phase previousPhase = round.phase();
        round.onTick();
        if (previousPhase == HostRoundEngine.Phase.RUNNING
                && round.phase() == HostRoundEngine.Phase.ROUND_END) {
            for (Member member : List.copyOf(members.values())) {
                if (member.admitted()) {
                    clearActiveAttempt(member);
                    if (hooks.roundOutcomeListener() != null) {
                        hooks.roundOutcomeListener().onRoundComplete(member.fingerprint,
                                member.finishedThisRound && member.violationsThisRound == 0);
                    }
                }
            }
        }
    }

    public boolean requestStartRound(ControlMessage.RoundConfig roundConfig) {
        if (roundConfig == null || !trackAllowed(roundConfig)) {
            return false;
        }
        if (!round.startRound(roundConfig)) {
            return false;
        }
        for (Member member : members.values()) {
            member.violationsThisRound = 0;
            member.finishedThisRound = false;
            member.activeAttemptId = -1;
            member.lastAttemptId = -1;
            member.refusedAttemptId = -1;
            member.ready = false;
        }
        // Clients clear every ready state on RoundStart; a rule that needs nobody to wait
        // readies everyone again at once.
        lobby.clear();
        if (!readyRequired()) {
            readyEveryone();
        }
        hub.setTrack(roundConfig.gameId(), roundConfig.zone(), roundConfig.act());
        roomGameId = roundConfig.gameId();
        roomZone = roundConfig.zone();
        roomAct = roundConfig.act();
        return true;
    }

    public void applyTrackValidationProfile(TrackValidationProfile profile) {
        hub.applyProfile(profile);
    }

    public int playerCount() {
        return (int) members.values().stream().filter(member -> member.admitted()).count();
    }

    /** Closes every transport currently owned by this ephemeral room. */
    public void close(String reason) {
        if (closed) return;
        synchronized (completionLock) { closed = true; }
        preparation.thenAccept(verifier -> { if (verifier != null) verifier.close(); });
        if (activeVerifier != null) activeVerifier.close();
        if (passwordChange != null && passwordChange.verifier != null) passwordChange.verifier.close();
        drainCompletions();
        closing = true;
        try {
            for (Member member : List.copyOf(members.values())) {
                drop(member, reason);
            }
        } finally {
            closing = false;
        }
    }

    public List<ControlMessage.PlayerInfo> players() {
        List<ControlMessage.PlayerInfo> result = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.admitted()) {
                result.add(playerInfo(member));
            }
        }
        return List.copyOf(result);
    }

    private ControlMessage.PlayerInfo playerInfo(Member member) {
        return new ControlMessage.PlayerInfo(member.slot, member.fingerprint,
                member.displayName, member.character, hooks.isNewPlayer() != null
                        && hooks.isNewPlayer().test(member.fingerprint));
    }

    public HostRoundEngine round() {
        return round;
    }

    public GhostHub hub() {
        return hub;
    }

    public ControlMessage.RoomDescriptor descriptor() {
        return new ControlMessage.RoomDescriptor(config.roomName(), roomGameId,
                roomZone, roomAct, config.characterPolicy(),
                config.lockedCharacter(), config.maxPlayers(), config.verified(), accessPolicy, chatMode.name());
    }

    public void sendToSlot(int slot, ControlMessage message) {
        Member member = memberForSlot(slot);
        if (member != null) {
            send(member, message);
        }
    }

    void sendToParticipantInSlot(int slot, String participantId,
                                 ControlMessage message) {
        Member member = memberForSlot(slot);
        if (member != null && member.participantId.equals(participantId)) {
            send(member, message);
        }
    }

    String participantIdForSlot(int slot, String fingerprint) {
        Member member = memberForSlot(slot);
        return member != null && member.fingerprint.equals(fingerprint)
                ? member.participantId : null;
    }

    public String identityFingerprintForSlot(int slot) {
        Member member = memberForSlot(slot);
        return member == null ? null : member.fingerprint;
    }

    public String determinismFingerprint() {
        return config.requiredDeterminismFingerprint();
    }

    private void handleHandshake(Member member, ControlMessage message) {
        if (message instanceof ControlMessage.Hello hello) {
            if (listenerAdmission == ListenerAdmission.BROKER_REQUIRED) {
                if (hello.grantReference() == null || hello.routeGeneration() <= 0) {
                    rejectAdmission(member, "broker grant required");
                    return;
                }
                member.grantReference = hello.grantReference();
                member.routeGeneration = hello.routeGeneration();
                if (member.trustedTunnel != null
                        && (!hello.grantReference().equals(member.trustedTunnel.grantReference())
                        || hello.routeGeneration() != member.trustedTunnel.routeGeneration())) {
                    rejectAdmission(member, "tunnel route mismatch");
                    return;
                }
            } else {
                if (hello.grantReference() != null && (member.expectedRouteReference == null
                        || !hello.grantReference().equals(member.expectedRouteReference)
                        || hello.routeGeneration() != member.expectedRouteGeneration)
                        || hello.grantReference() == null
                        && member.expectedRouteReference != null) {
                    rejectAdmission(member, "room route mismatch");
                    return;
                }
            }
        }
        HostHandshake.Step step = switch (message) {
            case ControlMessage.Hello hello -> member.handshake.onHello(hello);
            case ControlMessage.AuthProof proof -> member.handshake.onAuthProof(proof);
            default -> new HostHandshake.Reject("unexpected message before admission");
        };
        switch (step) {
            case HostHandshake.SendWelcome welcome -> member.connection.sendText(
                    codec.encode(null, welcome.welcome()));
            case HostHandshake.Reject reject -> {
                member.connection.sendText(codec.encode(null,
                        new ControlMessage.JoinRejected(reject.reason())));
                drop(member, reject.reason());
            }
            case HostHandshake.Admit admit -> beginAccess(member, admit);
        }
    }

    private WorkBudget.Reservation reserveCompletion() {
        synchronized (completionLock) {
            if (closed) return null;
            return WorkBudget.PROCESS.tryReserve(completionWork, completionWork,
                    new WorkBudget.Cost(1, MAX_COMPLETION_BYTES)).orElse(null);
        }
    }

    private <T> void observe(CompletableFuture<T> future, WorkBudget.Reservation reservation,
                             Predicate<T> retainable, BiConsumer<T, Boolean> adopted,
                             Consumer<T> discarded) {
        try {
            future.whenComplete((value, failure) -> {
                WorkBudget.Task task = null;
                try {
                    T accepted = retainable.test(value) ? value : null;
                    boolean failed = failure != null || accepted != value;
                    task = WorkBudget.Task.reserved(reservation, List.of(reservation),
                            () -> adopted.accept(accepted, failed), () -> discarded.accept(accepted));
                    synchronized (completionLock) {
                        if (!closed) {
                            completions.add(task);
                            return;
                        }
                    }
                    task.close();
                } catch (RuntimeException | Error rejected) {
                    if (task != null) task.close();
                    else {
                        try { discarded.accept(value); }
                        finally { reservation.close(); }
                    }
                    throw rejected;
                }
            });
        } catch (RuntimeException | Error failure) {
            reservation.close();
            throw failure;
        }
    }

    private static boolean retainable(ControlMessage.RoomGrantDecision decision) {
        return decision == null || replyBytes(decision.roomId(), decision.instanceId(),
                decision.grantReference(), decision.fingerprint()) <= MAX_COMPLETION_BYTES;
    }

    private static boolean retainable(ControlMessage.RoomInviteControlResult result) {
        return result == null || result.invite() == null
                || replyBytes(result.invite().code(), result.invite().instanceId()) <= MAX_COMPLETION_BYTES;
    }

    private static long replyBytes(String... values) {
        long bytes = 0;
        for (String value : values) if (value != null) bytes += 2L * value.length();
        return bytes;
    }

    private void drainCompletions() {
        WorkBudget.Task task;
        while ((task = completions.poll()) != null) {
            if (closed) task.close();
            else task.run();
        }
    }

    private boolean current(Member member) {
        return !closed && members.get(member.connection) == member;
    }

    private void beginAccess(Member member, HostHandshake.Admit proof) {
        if (listenerAdmission == ListenerAdmission.BROKER_REQUIRED) {
            if (member.trustedTunnel != null) {
                if (!member.trustedTunnel.fingerprint().equals(proof.fingerprint())) {
                    rejectAdmission(member, "tunnel identity mismatch");
                } else {
                    beginAccessAfterBroker(member, proof);
                }
                return;
            }
            BrokerAdmission authority = brokerAdmission;
            if (authority == null || member.grantReference == null) {
                rejectAdmission(member, "broker grant unavailable");
                return;
            }
            WorkBudget.Reservation completion = reserveCompletion();
            if (completion == null) {
                rejectAdmission(member, "broker completion capacity unavailable");
                return;
            }
            member.admission = Admission.VERIFYING;
            member.accessDeadline = wallClockMillis.getAsLong() + ADMISSION_TIMEOUT_MILLIS;
            try {
                observe(authority.redeem(member.grantReference, member.routeGeneration,
                        proof.fingerprint()).copy().orTimeout(ADMISSION_TIMEOUT_MILLIS,
                        java.util.concurrent.TimeUnit.MILLISECONDS), completion, RoomHost::retainable,
                        (decision, failed) -> {
                            if (!current(member) || member.admission != Admission.VERIFYING) return;
                            if (failed || decision == null || !decision.allowed()
                                    || !listedRoomId.equals(decision.roomId())
                                    || !instanceId().equals(decision.instanceId())
                                    || !member.grantReference.equals(decision.grantReference())
                                    || member.routeGeneration != decision.routeGeneration()
                                    || !proof.fingerprint().equals(decision.fingerprint())
                                    || wallClockMillis.getAsLong() >= member.accessDeadline
                                    || listenerAdmission != ListenerAdmission.BROKER_REQUIRED) {
                                rejectAdmission(member, "broker grant denied");
                            } else beginAccessAfterBroker(member, proof);
                        }, ignored -> { });
            } catch (RuntimeException | Error unavailable) {
                completion.close();
                rejectAdmission(member, "broker grant unavailable");
                if (unavailable instanceof Error fatal) throw fatal;
            }
            return;
        }
        beginAccessAfterBroker(member, proof);
    }

    private void beginAccessAfterBroker(Member member, HostHandshake.Admit proof) {
        String expected = member.expectedFingerprint;
        if (expected != null && !expected.equals(proof.fingerprint())) {
            rejectAdmission(member, "identity mismatch");
            return;
        }
        member.proof = proof;
        member.fingerprint = proof.fingerprint();
        if (!preparation.isDone() || preparation.isCompletedExceptionally() || accessChangePending()) {
            rejectAdmission(member, "room access unavailable");
            return;
        }
        member.challenge = UUID.randomUUID().toString();
        member.challengeEpoch = accessEpoch;
        member.accessDeadline = wallClockMillis.getAsLong() + ACCESS_TIMEOUT_MILLIS;
        member.admission = Admission.ACCESS_REQUIRED;
        member.connection.sendText(codec.encode(null, new ControlMessage.RoomAccessChallenge(
                member.challenge, instanceId(), accessEpoch, accessPolicy)));
        if (!accessPolicy.passwordRequired()) admitMember(member, proof);
    }

    private void handleAccess(Member member, ControlMessage message) {
        if (!(message instanceof ControlMessage.RoomAccessSubmit submission)) {
            rejectAdmission(member, "room access denied");
            return;
        }
        try (RoomPassword password = submission.password()) {
            if (password == null || member.admission != Admission.ACCESS_REQUIRED
                    || !java.util.Objects.equals(member.challenge, submission.challengeId())
                    || member.challengeEpoch != accessEpoch || accessChangePending()
                    || wallClockMillis.getAsLong() >= member.accessDeadline) {
                rejectAdmission(member, "room access denied");
                return;
            }
            String source = HandshakeTransport.role(member.connection) == AuthenticationTranscript.Role.ROOM_RELAY
                    ? null : member.connection.remoteHost();
            if (!isOwner(member) && !RoomAdmissionLimiter.SHARED.allow(member.fingerprint, source, instanceId(),
                    System.currentTimeMillis())) {
                rejectAdmission(member, "room access denied");
                return;
            }
            WorkBudget.Reservation completion = reserveCompletion();
            if (completion == null) {
                rejectAdmission(member, "room access unavailable");
                return;
            }
            member.admission = Admission.VERIFYING;
            long epoch = accessEpoch;
            try {
                observe(passwordService.verify(password, activeVerifier == null ? preparation.join() : activeVerifier),
                    completion, ignored -> true, (valid, failed) -> {
                        if (!current(member) || member.admission != Admission.VERIFYING) return;
                        if (failed || !Boolean.TRUE.equals(valid) || epoch != accessEpoch || accessChangePending()
                                || wallClockMillis.getAsLong() >= member.accessDeadline) {
                            rejectAdmission(member, "room access denied");
                        } else admitMember(member, member.proof);
                    }, ignored -> { });
            } catch (RuntimeException | Error unavailable) {
                completion.close();
                rejectAdmission(member, "room access unavailable");
                if (unavailable instanceof Error fatal) throw fatal;
            }
        }
    }

    private ControlMessage.RoomAccessState accessState() {
        return new ControlMessage.RoomAccessState(instanceId(), accessPolicy, accessEpoch, inviteEpoch);
    }

    private void inviteControl(Member member, ControlMessage.InviteOperation operation,
                               boolean membersCanInvite, long disclosureId) {
        if (!isOwner(member) && (operation != ControlMessage.InviteOperation.FETCH
                || !accessPolicy.membersCanInvite())) {
            send(member, new ControlMessage.RoomInviteRejected("only the host can invite", 0));
            return;
        }
        if (inviteService == null || inviteOperationPending
                || passwordChange != null && (operation != ControlMessage.InviteOperation.FETCH
                        || passwordChange.verifier == null)) {
            send(member, new ControlMessage.RoomInviteRejected("room invite unavailable", 0));
            return;
        }
        WorkBudget.Reservation completion = reserveCompletion();
        if (completion == null) {
            send(member, new ControlMessage.RoomInviteRejected("room invite unavailable", 0));
            return;
        }
        inviteOperationPending = true;
        long operationGeneration = ++inviteOperationGeneration;
        long providerGeneration = inviteServiceGeneration;
        long access = accessEpoch;
        long invite = inviteEpoch;
        CompletableFuture<ControlMessage.RoomInviteControlResult> future;
        try {
            if (operation != ControlMessage.InviteOperation.FETCH) {
                broadcast(new ControlMessage.RoomInviteInvalidated(instanceId()));
            }
            future = java.util.Objects.requireNonNull(inviteService.control(instanceId(), access,
                    invite, operation, membersCanInvite));
        } catch (RuntimeException | Error failure) {
            completion.close();
            inviteOperationPending = false;
            send(member, new ControlMessage.RoomInviteRejected("room invite unavailable", 0));
            if (failure instanceof Error fatal) throw fatal;
            return;
        }
        observe(future.copy().orTimeout(5, java.util.concurrent.TimeUnit.SECONDS), completion,
                RoomHost::retainable, (result, failed) -> {
            if (operationGeneration != inviteOperationGeneration || providerGeneration != inviteServiceGeneration) return;
            inviteOperationPending = false;
            if (failed || !validInviteResult(result)) {
                if (current(member)) send(member, new ControlMessage.RoomInviteRejected("room invite unavailable", 0));
                return;
            }
            if (operation == ControlMessage.InviteOperation.FETCH) {
                if (result.invite().accessEpoch() != accessEpoch) {
                    if (passwordChange != null && passwordChange.verifier != null
                            && passwordChange.fromEpoch == accessEpoch
                            && result.invite().accessEpoch() == accessEpoch + 1) {
                        installPassword(passwordChange, result);
                    } else {
                        accessAuthorityUnavailable = true;
                        if (current(member)) send(member, new ControlMessage.RoomInviteRejected("room access unavailable", 0));
                        return;
                    }
                }
                accessPolicy = result.accessPolicy();
                inviteEpoch = result.invite().inviteEpoch();
                accessAuthorityUnavailable = false;
                broadcast(accessState());
                if (passwordChange != null) {
                    submitPasswordChange(passwordChange);
                    if (current(member)) send(member, new ControlMessage.RoomInviteRejected("room access change pending", 0));
                    return;
                }
            } else {
                boolean policyMatches = operation == ControlMessage.InviteOperation.SET_SHARING
                        ? result.accessPolicy().membersCanInvite() == membersCanInvite
                        : result.accessPolicy().equals(accessPolicy);
                if (accessEpoch != access || inviteEpoch != invite
                        || result.invite().accessEpoch() != access || result.invite().inviteEpoch() <= invite
                        || !policyMatches) {
                    if (current(member)) send(member, new ControlMessage.RoomInviteRejected("room invite unavailable", 0));
                    return;
                }
                accessPolicy = result.accessPolicy();
                inviteEpoch = result.invite().inviteEpoch();
                broadcast(accessState());
            }
            if (current(member) && member.admitted()) {
                if (isOwner(member) || accessPolicy.membersCanInvite()) {
                    var disclosed = result.invite();
                    send(member, new ControlMessage.RoomInvite(disclosed.code(), disclosed.instanceId(),
                            disclosed.accessEpoch(), disclosed.inviteEpoch(), disclosed.expiresAtMillis(), disclosureId));
                } else send(member, new ControlMessage.RoomInviteRejected("only the host can invite", 0));
            }
        }, ignored -> { });
    }

    private boolean validInviteResult(ControlMessage.RoomInviteControlResult result) {
        if (result == null || result.accessPolicy() == null || result.invite() == null
                || result.accessPolicy().mode() != accessPolicy.mode()) return false;
        ControlMessage.RoomInvite invite = result.invite();
        if (!instanceId().equals(invite.instanceId()) || invite.accessEpoch() < accessEpoch
                || invite.inviteEpoch() < inviteEpoch
                || invite.expiresAtMillis() <= wallClockMillis.getAsLong()) return false;
        try { return com.openggf.net.protocol.RoomInviteCode.parse(invite.code()) != null; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private void changePassword(Member member, RoomPassword password) {
        try (password) {
            if (password == null || !isOwner(member) || !accessPolicy.passwordRequired()
                    || passwordChange != null || inviteOperationPending || accessAuthorityUnavailable
                    || !RoomAdmissionLimiter.SHARED.allow(member.fingerprint, null, instanceId(),
                            System.currentTimeMillis())) {
                send(member, new ControlMessage.RoomInviteRejected("room access change unavailable", 0));
                return;
            }
            WorkBudget.Reservation completion = reserveCompletion();
            if (completion == null) {
                send(member, new ControlMessage.RoomInviteRejected("room access change unavailable", 0));
                return;
            }
            PasswordChange change = new PasswordChange(accessEpoch, member);
            passwordChange = change;
            try {
                broadcast(new ControlMessage.RoomInviteInvalidated(instanceId()));
                observe(passwordService.prepare(password), completion, ignored -> true, (verifier, failed) -> {
                    if (passwordChange != change || accessEpoch != change.fromEpoch) {
                        if (verifier != null) verifier.close();
                        return;
                    }
                    if (failed) {
                        passwordChange = null;
                        if (current(member)) send(member, new ControlMessage.RoomInviteRejected("room access change unavailable", 0));
                        return;
                    }
                    change.verifier = verifier;
                    submitPasswordChange(change);
                }, verifier -> { if (verifier != null) verifier.close(); });
            } catch (RuntimeException | Error unavailable) {
                completion.close();
                passwordChange = null;
                send(member, new ControlMessage.RoomInviteRejected("room access change unavailable", 0));
                if (unavailable instanceof Error fatal) throw fatal;
            }
        }
    }

    private void submitPasswordChange(PasswordChange change) {
        if (passwordChange != change || change.providerPending) return;
        if (inviteService == null) {
            installPassword(change, null);
            return;
        }
        WorkBudget.Reservation completion = reserveCompletion();
        if (completion == null) {
            if (current(change.requester)) send(change.requester,
                    new ControlMessage.RoomInviteRejected("room access change pending", 0));
            return;
        }
        long providerGeneration = inviteServiceGeneration;
        CompletableFuture<ControlMessage.RoomInviteControlResult> future;
        try {
            change.providerPending = true;
            future = java.util.Objects.requireNonNull(inviteService.control(instanceId(), change.fromEpoch + 1,
                    inviteEpoch, ControlMessage.InviteOperation.ACCESS_CHANGED, accessPolicy.membersCanInvite()));
        } catch (RuntimeException | Error error) {
            completion.close();
            change.providerPending = false;
            if (current(change.requester)) send(change.requester,
                    new ControlMessage.RoomInviteRejected("room access change pending", 0));
            if (error instanceof Error fatal) throw fatal;
            return;
        }
        observe(future.copy().orTimeout(5, java.util.concurrent.TimeUnit.SECONDS), completion,
                RoomHost::retainable, (result, failed) -> {
            if (passwordChange != change || providerGeneration != inviteServiceGeneration) return;
            change.providerPending = false;
            if (failed || !validInviteResult(result)
                    || result.invite().accessEpoch() != change.fromEpoch + 1) {
                // A lost reply cannot prove whether the master committed. Retain the prepared
                // verifier and refuse admission until a canonical FETCH reconciles the epoch.
                if (current(change.requester)) send(change.requester,
                        new ControlMessage.RoomInviteRejected("room access change pending", 0));
                return;
            }
            installPassword(change, result);
        }, ignored -> { });
    }

    private void installPassword(PasswordChange change, ControlMessage.RoomInviteControlResult result) {
        if (closed || passwordChange != change || accessEpoch != change.fromEpoch) return;
        if (activeVerifier != null) activeVerifier.close();
        else preparation.join().close();
        activeVerifier = change.verifier;
        passwordChange = null;
        accessEpoch++;
        if (result != null) {
            accessPolicy = result.accessPolicy();
            inviteEpoch = result.invite().inviteEpoch();
        }
        for (Member pending : List.copyOf(members.values())) {
            if (!pending.admitted()) rejectAdmission(pending, "room access denied");
        }
        broadcast(accessState());
        if (current(change.requester) && change.requester.admitted() && isOwner(change.requester)) {
            send(change.requester, new ControlMessage.RoomPasswordChanged(accessEpoch));
        }
    }

    private void admitMember(Member member, HostHandshake.Admit admit) {
        if (members.values().stream().anyMatch(existing -> existing.admitted()
                && existing.fingerprint.equals(admit.fingerprint()))) {
            member.connection.sendText(codec.encode(null,
                    new ControlMessage.JoinRejected("identity already connected")));
            drop(member, "duplicate identity");
            return;
        }
        int slot = lowestFreeSlot();
        if (slot < 0) {
            member.connection.sendText(codec.encode(null,
                    new ControlMessage.JoinRejected("room full")));
            drop(member, "room full");
            return;
        }
        member.admission = Admission.ADMITTED;
        member.slot = slot;
        member.fingerprint = admit.fingerprint();
        if (!admit.displayName().isEmpty() && !DisplayNames.isCanonical(admit.displayName())) {
            rejectAdmission(member, "invalid display name");
            return;
        }
        PlayerAppearance appearance;
        try {
            appearance = PlayerAppearance.decode(admit.appearance());
        } catch (IllegalArgumentException invalid) {
            rejectAdmission(member, "invalid appearance");
            return;
        }
        member.appearance = appearance;
        member.displayName = admit.displayName().isEmpty()
                ? admit.fingerprint().substring(0, 8) : admit.displayName();
        member.character = "LOCKED".equals(config.characterPolicy())
                ? config.lockedCharacter() : "sonic";
        ControlMessage.RoundSnapshot snapshot = round.snapshot();
        if ((round.phase() == HostRoundEngine.Phase.COUNTDOWN
                || round.phase() == HostRoundEngine.Phase.RUNNING) && snapshot.config() != null) {
            if ("LOCKED".equals(snapshot.config().characterPolicy())) {
                member.character = snapshot.config().lockedCharacter();
            }
        }
        if (!boundedLabel(member.displayName, MAX_DISPLAY_NAME_BYTES)
                || !boundedLabel(member.character, MAX_CHARACTER_BYTES)
                || !roomStateFits()) {
            member.admission = Admission.CLOSED;
            member.connection.sendText(codec.encode(null,
                    new ControlMessage.JoinRejected("room state limit exceeded")));
            drop(member, "room state limit exceeded");
            return;
        }
        member.memberSinceMillis = wallClockMillis.getAsLong();
        member.token = tokens.issue();
        member.participantId = UUID.randomUUID().toString();
        if (round.phase() == HostRoundEngine.Phase.COUNTDOWN
                || round.phase() == HostRoundEngine.Phase.RUNNING) {
            member.launch = launchFor(member, snapshot.config(),
                    snapshot.countdownEndsAtHubMillis(), snapshot.deadlineHubMillis());
            snapshot = new ControlMessage.RoundSnapshot(snapshot.phase(), snapshot.config(),
                    snapshot.countdownEndsAtHubMillis(), snapshot.deadlineHubMillis(),
                    snapshot.standings(), member.launch);
        }
        hub.addPlayer(slot, member.fingerprint, member.connection);
        lobby.addMember(slot, member.connection);
        member.connection.sendText(codec.encode(null,
                new ControlMessage.JoinAccepted(member.token, slot, descriptor(),
                        snapshot)));
        send(member, accessState());
        send(member, new ControlMessage.ChatModeState(chatMode.name()));
        round.sendVoteState(message -> send(member, message));
        send(member, new ControlMessage.RoomState(players()));
        publishPlayerChanges(member);
        // Looks travel per player: RoomState must keep a full roster inside one frame.
        for (Member other : members.values()) {
            if (other.admitted() && !other.appearance.isStock()) {
                send(member, new ControlMessage.PlayerLook(other.slot, other.appearance.encode()));
            }
        }
        if (!member.appearance.isStock()) {
            broadcastLook(member, member);
        }
        send(member, new ControlMessage.ReadyRule(readyRule.name(), readyRule.required(playerCount())));
        for (Member other : members.values()) {
            if (other.admitted() && other != member && other.ready) {
                send(member, new ControlMessage.PlayerReady(other.slot, true));
            }
        }
        if (!readyRule.required(playerCount())) {
            // Everyone counts as ready on joining a room that needs nobody to wait.
            member.ready = true;
            broadcast(new ControlMessage.PlayerReady(member.slot, true));
        }
        announceReadyRule();
    }

    /** Ready traffic belongs between rounds: the lobby, the results linger and the track vote. */
    private boolean betweenRounds() {
        return switch (round.phase()) {
            case LOBBY, ROUND_END, VOTE -> true;
            case COUNTDOWN, RUNNING -> false;
        };
    }

    private boolean readyRequired() {
        return readyRule.required(playerCount());
    }

    /** True when the owner may start a round: the rule does not wait, or everyone is ready. */
    public boolean readyToStart() {
        return !readyRequired() || members.values().stream()
                .filter(member -> member.admitted()).allMatch(member -> member.ready);
    }

    /** The room's ready rule. */
    public ReadyRules readyRule() {
        return readyRule;
    }

    /** Whether the player in {@code slot} is ready (tests and diagnostics). */
    public boolean isReady(int slot) {
        Member member = memberForSlot(slot);
        return member != null && member.ready;
    }

    private void readyEveryone() {
        for (Member member : members.values()) {
            if (member.admitted()) member.ready = true;
        }
        broadcast(new ControlMessage.ReadyReset(true));
    }

    /**
     * Tells the room when the rule's effect changes as players join or leave. Moving to a rule
     * that needs nobody to wait readies everyone; players already ready keep it either way.
     */
    private void announceReadyRule() {
        boolean required = readyRequired();
        if (required == readyRequiredAnnounced) {
            return;
        }
        readyRequiredAnnounced = required;
        broadcast(new ControlMessage.ReadyRule(readyRule.name(), required));
        if (!required) {
            readyEveryone();
        }
    }

    private void handleSetReady(Member member, ControlMessage.SetReady ready, long now) {
        if (!betweenRounds() || ready.ready() == member.ready
                || (member.lastReadyMillis != Long.MIN_VALUE
                    && now - member.lastReadyMillis < Protocol.READY_MIN_INTERVAL_MILLIS)) {
            return;
        }
        member.lastReadyMillis = now;
        member.ready = ready.ready();
        broadcast(new ControlMessage.PlayerReady(member.slot, member.ready));
    }

    private void handleSetReadyRule(Member member, ControlMessage.SetReadyRule update) {
        ReadyRules rule = ReadyRules.parse(update.rule());
        if (!isOwner(member) || !betweenRounds() || rule == null || rule == readyRule) {
            return;
        }
        readyRule = rule;
        readyRequiredAnnounced = rule.required(playerCount());
        broadcast(new ControlMessage.ReadyRule(rule.name(), readyRequiredAnnounced));
        if (!readyRequiredAnnounced) {
            readyEveryone();
        }
    }

    private void handleSetChatMode(Member member, ControlMessage.SetChatMode update) {
        ChatMode requested = ChatMode.parse(update.mode());
        if (!isOwner(member) || !betweenRounds() || requested == null) {
            return;
        }
        ChatMode effective = requested.intersect(hooks.chatModeLimit());
        if (effective != chatMode) {
            chatMode = effective;
            broadcast(new ControlMessage.ChatModeState(chatMode.name()));
        }
    }

    private boolean isOwner(Member member) {
        String owner = hooks.roundOwnerFingerprint() != null
                ? hooks.roundOwnerFingerprint() : hostIdentity.fingerprint();
        return member.fingerprint.equals(owner);
    }

    private void rejectAdmission(Member member, String reason) {
        member.admission = Admission.CLOSED;
        member.connection.sendText(codec.encode(null, new ControlMessage.JoinRejected(reason)));
        drop(member, reason);
    }

    /** Sends {@code changed}'s look to every admitted member except {@code skip}. */
    private void broadcastLook(Member changed, Member skip) {
        String encoded = codec.encode(null, new ControlMessage.PlayerLook(changed.slot, changed.appearance.encode()));
        for (Member recipient : members.values()) {
            if (recipient.admitted() && recipient != skip) {
                recipient.connection.sendText(encoded);
            }
        }
    }

    /** Lobby-only rename/recolour; anything invalid or too frequent is ignored, like SelectCharacter. */
    private void handleProfileUpdate(Member member, ControlMessage.UpdateProfile update, long now) {
        if (round.phase() != HostRoundEngine.Phase.LOBBY
                || (member.lastProfileMillis != Long.MIN_VALUE
                    && now - member.lastProfileMillis < Protocol.PROFILE_MIN_INTERVAL_MILLIS)
                || !DisplayNames.isCanonical(update.displayName())
                || !boundedLabel(update.displayName(), MAX_DISPLAY_NAME_BYTES)) {
            return;
        }
        PlayerAppearance appearance;
        try {
            appearance = PlayerAppearance.decode(update.appearance());
        } catch (IllegalArgumentException invalid) {
            return;
        }
        member.lastProfileMillis = now;
        String previousName = member.displayName;
        PlayerAppearance previousLook = member.appearance;
        member.displayName = update.displayName();
        member.appearance = appearance;
        if (!roomStateFits()) {
            member.displayName = previousName;
            member.appearance = previousLook;
            return;
        }
        publishPlayerChanges(null);
        if (!appearance.equals(previousLook)) {
            broadcastLook(member, null);
        }
        if (!previousName.equals(member.displayName) && hooks.profileListener() != null) {
            hooks.profileListener().onDisplayNameChanged(member.fingerprint, member.displayName);
        }
    }

    private void dispatch(Member member, ControlMessage message) {
        if (message instanceof ControlMessage.AttemptFinish finish
                && isRefusedAttempt(member, finish.attemptId())) {
            // The window was closed when this attempt started: nothing to score.
            return;
        }
        long now = wallClockMillis.getAsLong();
        switch (message) {
            case ControlMessage.RoomInviteRequest request -> inviteControl(member,
                    ControlMessage.InviteOperation.FETCH, accessPolicy.membersCanInvite(), request.disclosureId());
            case ControlMessage.RoomInviteRotate ignored -> inviteControl(member,
                    ControlMessage.InviteOperation.ROTATE, accessPolicy.membersCanInvite(), 0);
            case ControlMessage.RoomInviteSharing sharing -> inviteControl(member,
                    ControlMessage.InviteOperation.SET_SHARING, sharing.membersCanInvite(), 0);
            case ControlMessage.RoomPasswordChange change -> changePassword(member, change.password());
            case ControlMessage.Chat chat -> handleChat(member, chat, now);
            case ControlMessage.QuickChat quick -> handleQuickChat(member, quick, now);
            case ControlMessage.SetChatMode mode -> handleSetChatMode(member, mode);
            case ControlMessage.UpdateProfile update -> handleProfileUpdate(member, update, now);
            case ControlMessage.SetReady ready -> handleSetReady(member, ready, now);
            case ControlMessage.SetReadyRule rule -> handleSetReadyRule(member, rule);
            case ControlMessage.Ping ping -> member.connection.sendText(codec.encode(
                    null, new ControlMessage.Pong(ping.t0ClientMillis(), now)));
            case ControlMessage.SelectCharacter select -> {
                if (round.phase() == HostRoundEngine.Phase.LOBBY
                        && !"LOCKED".equals(config.characterPolicy())
                        && boundedLabel(select.character(), MAX_CHARACTER_BYTES)
                        && !select.character().equals(member.character)) {
                    String previous = member.character;
                    member.character = select.character();
                    if (roomStateFits()) {
                        publishPlayerChanges(null);
                    } else {
                        member.character = previous;
                    }
                }
            }
            case ControlMessage.RoundConfigure configure -> {
                // A rule that waits for everyone refuses the start until all present are ready.
                if (isOwner(member) && readyToStart()
                        && (round.votedNextConfig() == null && configure.planRevision() == 0
                        || round.votedNextConfig() != null
                        && configure.planRevision() == round.planRevision())) {
                    requestStartRound(configure.config());
                }
            }
            case ControlMessage.AttemptStart start -> startAttempt(member, start.attemptId());
            case ControlMessage.AttemptReset reset -> resetAttempt(member, reset.attemptId());
            case ControlMessage.TrackVote vote ->
                    round.onTrackVote(member.slot, vote.trackKey());
            case ControlMessage.AttemptFinish finish -> {
                if (round.phase() != HostRoundEngine.Phase.RUNNING
                        || finish.attemptId() != member.activeAttemptId) {
                    recordAttemptViolation(member, "finish outside matching active attempt");
                    break;
                }
                HostRoundEngine.FinishOutcome outcome = round.onAttemptFinish(
                        member.slot, member.participantId, member.fingerprint,
                        member.displayName, member.launch.effectiveCharacter(),
                        finish, !hub.hasFinishEvidence(member.slot,
                                finish.attemptId(), finish.finishFrame(),
                                finish.ghostStreamHashHex()));
                clearActiveAttempt(member);
                // After the reset clearActiveAttempt reported, a finish ends FINISHED
                // even when uncredited: roster status is coarse course progress (a
                // sender's own finished frame bit also reports it), not standings.
                hub.onAttemptFinished(member.slot, finish.attemptId());
                member.finishedThisRound = round.hasBestForParticipant(member.participantId);
                if (outcome != null && config.verified() && outcome.outsideBroadcastCap()) {
                    send(member, new ControlMessage.RankUpdate(
                            outcome.rank(), finish.timeFrames()));
                }
                if (outcome != null && config.verified()
                        && hooks.verificationHooks() != null) {
                    hooks.verificationHooks().onFinishNeedingVerification(
                            hooks.roomId(), member.slot, member.fingerprint, finish,
                            roomGameId + ":" + roomZone + ":" + roomAct,
                            member.launch.effectiveCharacter(),
                            config.requiredDeterminismFingerprint(), false);
                }
            }
            case ControlMessage.StandingsPageRequest request -> {
                if (member.lastStandingsPageMillis == Long.MIN_VALUE
                        || now - member.lastStandingsPageMillis
                        >= STANDINGS_PAGE_INTERVAL_MILLIS) {
                    member.lastStandingsPageMillis = now;
                    send(member, new ControlMessage.StandingsPage(
                            round.page(request.page(), STANDINGS_PAGE_SIZE),
                            request.page(), round.totalPages(STANDINGS_PAGE_SIZE)));
                }
            }
            default -> drop(member,
                    "illegal client message " + message.getClass().getSimpleName());
        }
    }

    private void handleChat(Member member, ControlMessage.Chat chat, long now) {
        if (!chatMode.allowsText()) {
            return;
        }
        String text = chat.text() == null ? "" : chat.text();
        if (text.isBlank() || !admitChat(member, now)) {
            return;
        }
        if (text.length() > Protocol.MAX_CHAT_CHARS) {
            text = text.substring(0, Protocol.MAX_CHAT_CHARS);
        }
        broadcast(new ControlMessage.ChatBroadcast(member.slot, member.displayName, text));
    }

    private void handleQuickChat(Member member, ControlMessage.QuickChat quick, long now) {
        QuickChatPhrase phrase = QuickChatPhrase.parse(quick.phraseId());
        if (!chatMode.allowsQuickChat() || phrase == null || !admitChat(member, now)) {
            return;
        }
        broadcast(new ControlMessage.QuickChatBroadcast(member.slot, member.displayName,
                phrase.name(), roomGameId, roomZone, roomAct));
    }

    /** Both player message kinds spend the same per-member cooldown and trust allowance. */
    private boolean admitChat(Member member, long now) {
        if (hooks.chatGate() != null
                && !hooks.chatGate().mayChat(member.fingerprint, member.memberSinceMillis)) {
            return false;
        }
        if (member.lastChatMillis != Long.MIN_VALUE
                && now - member.lastChatMillis < Protocol.CHAT_MIN_INTERVAL_MILLIS) {
            return false;
        }
        member.lastChatMillis = now;
        return true;
    }

    private void startAttempt(Member member, int attemptId) {
        if (member.launch == null || member.launch.roundId() != round.roundId()) {
            recordAttemptViolation(member, "attempt without frozen launch");
            return;
        }
        if (member.activeAttemptId >= 0 || attemptId <= member.lastAttemptId) {
            recordAttemptViolation(member, "invalid attempt start " + attemptId);
            return;
        }
        // Clock skew near the countdown or deadline is not misbehaviour. Keep the
        // id so ordering stays strict and its trailing traffic can be recognised.
        member.lastAttemptId = attemptId;
        if (!round.mayStartAttempt()) {
            member.refusedAttemptId = attemptId;
            return;
        }
        member.refusedAttemptId = -1;
        member.activeAttemptId = attemptId;
        hub.onAttemptStart(member.slot, attemptId);
    }

    private void resetAttempt(Member member, int attemptId) {
        if (isRefusedAttempt(member, attemptId)) {
            return;
        }
        if (attemptId != member.activeAttemptId) {
            recordAttemptViolation(member, "reset outside matching active attempt");
            return;
        }
        clearActiveAttempt(member);
    }

    private static boolean isRefusedAttempt(Member member, int attemptId) {
        return member.refusedAttemptId >= 0 && member.activeAttemptId < 0
                && attemptId == member.refusedAttemptId;
    }

    private static boolean isRefusedAttemptBatch(Member member, byte[] data) {
        if (member.refusedAttemptId < 0) {
            return false;
        }
        try {
            return isRefusedAttempt(member, GhostPackets.decodeFrames(data).attemptId());
        } catch (ProtocolViolationException malformed) {
            return false;
        }
    }

    private void clearActiveAttempt(Member member) {
        if (member.activeAttemptId >= 0) {
            hub.onAttemptReset(member.slot, member.activeAttemptId);
            member.activeAttemptId = -1;
        }
    }

    private void recordAttemptViolation(Member member, String detail) {
        member.violationsThisRound++;
        System.getLogger(RoomHost.class.getName()).log(
                System.Logger.Level.WARNING,
                "attempt violation slot=" + member.slot + " fp=" + member.fingerprint
                        + ": " + detail);
        if (member.violationsThisRound >= GhostStreamValidator.KICK_THRESHOLD) {
            drop(member, "attempt protocol violations");
        }
    }

    private void broadcast(ControlMessage message) {
        if (closing) return;
        if (message instanceof ControlMessage.RoundStart start) {
            for (Member member : members.values()) {
                if (!member.admitted()) continue;
                if ("LOCKED".equals(start.config().characterPolicy())) {
                    member.character = start.config().lockedCharacter();
                }
                member.launch = launchFor(member, start.config(),
                        start.countdownEndsAtHubMillis(), start.deadlineHubMillis());
            }
            publishPlayerChanges(null);
            for (Member member : members.values()) {
                if (member.admitted()) send(member, new ControlMessage.RoundStart(start.config(),
                        start.countdownEndsAtHubMillis(), start.deadlineHubMillis(), member.launch));
            }
            broadcastTopSlots = Set.of();
            return;
        }
        String encoded = codec.encode(null, message);
        for (Member member : members.values()) {
            if (member.admitted()) {
                member.connection.sendText(encoded);
            }
        }
        switch (message) {
            case ControlMessage.RoundStart ignored -> broadcastTopSlots = Set.of();
            case ControlMessage.StandingsDelta delta -> notifyPushedOut(delta.rows());
            default -> { }
        }
    }

    private ControlMessage.RaceLaunch launchFor(Member member, ControlMessage.RoundConfig selected,
                                                long countdownEndsAt, long deadline) {
        return new ControlMessage.RaceLaunch(round.roundId(), round.planRevision(), selected,
                member.participantId, member.slot, member.fingerprint, member.character,
                countdownEndsAt, deadline);
    }

    /**
     * Clients learn their own rank from the top-ten broadcast. A participant
     * pushed out of it gets its new rank privately, or it would keep showing its
     * last top-ten rank and never page the standings around itself.
     */
    private void notifyPushedOut(List<ControlMessage.StandingsRow> top) {
        Set<Integer> pushedOut = new HashSet<>(broadcastTopSlots);
        broadcastTopSlots = top.stream().map(ControlMessage.StandingsRow::slot)
                .collect(Collectors.toUnmodifiableSet());
        pushedOut.removeAll(broadcastTopSlots);
        if (pushedOut.isEmpty()) {
            return;
        }
        // A slot without a row lost its only result and has no rank to report.
        for (ControlMessage.StandingsRow row : round.standings()) {
            Member member = pushedOut.remove(row.slot()) ? memberForSlot(row.slot()) : null;
            if (member != null) {
                send(member, new ControlMessage.RankUpdate(row.rank(), row.bestTimeFrames()));
            }
        }
    }

    private boolean roomStateFits() {
        return codec.encode(null, new ControlMessage.RoomState(players()))
                .getBytes(StandardCharsets.UTF_8).length <= Protocol.MAX_CONTROL_BYTES;
    }

    private static boolean boundedLabel(String value, int maxBytes) {
        return value != null && !value.isBlank()
                && value.getBytes(StandardCharsets.UTF_8).length <= maxBytes
                && value.codePoints().noneMatch(codePoint -> codePoint < 0x20
                || codePoint == '"' || codePoint == '\\');
    }

    private void send(Member member, ControlMessage message) {
        member.connection.sendText(codec.encode(null, message));
    }

    private int lowestFreeSlot() {
        if (playerCount() >= config.maxPlayers()) return -1;
        boolean[] used = new boolean[Protocol.MAX_PLAYERS_RELAY];
        for (Member member : members.values()) {
            if (member.admitted() && member.slot >= 0 && member.slot < used.length) {
                used[member.slot] = true;
            }
        }
        for (int slot = 0; slot < used.length; slot++) {
            // A retained result still owns its wire identity until the next round.
            if (!used[slot] && round.bestFinish(slot) == null) {
                return slot;
            }
        }
        return -1;
    }

    private void drop(Member member, String reason) {
        members.remove(member.connection);
        if (member.admitted()) {
            removeAdmittedMember(member);
        }
        member.connection.close(reason);
        if (member.admitted() && !closing) {
            publishDeparture(member);
        }
    }

    private void publishDeparture(Member member) {
        if (!closing) {
            broadcast(new ControlMessage.PlayerLeft(member.slot));
            publishPlayerChanges(null);
            announceReadyRule();
        }
    }

    /** Refresh dynamic metadata at the same membership events that used to send snapshots. */
    private void publishPlayerChanges(Member snapshotRecipient) {
        for (Member changed : members.values()) {
            if (!changed.admitted()) continue;
            ControlMessage.PlayerInfo info = playerInfo(changed);
            if (!info.equals(changed.publishedInfo)) {
                String encoded = codec.encode(null, new ControlMessage.PlayerUpsert(info));
                for (Member recipient : members.values()) {
                    if (recipient.admitted() && recipient != snapshotRecipient) {
                        recipient.connection.sendText(encoded);
                    }
                }
                changed.publishedInfo = info;
            }
        }
    }

    private void removeAdmittedMember(Member member) {
        tokens.revoke(member.token);
        hub.removePlayer(member.slot);
        lobby.removeMember(member.slot);
        round.onPlayerLeft(member.slot);
    }

    private Member memberForSlot(int slot) {
        for (Member member : members.values()) {
            if (member.admitted() && member.slot == slot) {
                return member;
            }
        }
        return null;
    }

    private boolean trackAllowed(ControlMessage.RoundConfig requested) {
        ControlMessage.RoundConfig voted = round.votedNextConfig();
        if (voted != null) {
            return voted.equals(requested);
        }
        if (requested.gameId().equals(roomGameId)
                && requested.zone() == roomZone && requested.act() == roomAct) {
            return true;
        }
        return false;
    }
}
