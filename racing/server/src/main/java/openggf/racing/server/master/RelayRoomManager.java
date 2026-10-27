package openggf.racing.server.master;

import openggf.timeattack.TimeAttackTrackCatalog;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.RoomHostHooks;
import com.openggf.net.hub.RoomRoundAccess;
import com.openggf.net.hub.RoomInviteService;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.RoomPassword;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import com.openggf.net.protocol.VerdictCodec;

/** Master-owned relay rooms, each confined to one event-loop executor. */
public final class RelayRoomManager implements RoomBroker.RelayRoomDirectory, AutoCloseable {
    public record RoomAccess(RoomHost room, Executor loop, WorkBudget.Account inboundWork,
                             WorkBudget.Account lifecycleWork) {
        public RoomAccess(RoomHost room, Executor loop) {
            this(room, loop, WorkBudget.PROCESS.account(512, 8L * 1024 * 1024));
        }
        public RoomAccess(RoomHost room, Executor loop, WorkBudget.Account inboundWork) {
            this(room, loop, inboundWork, WorkBudget.PROCESS.account(8192, 1));
        }
    }
    private record RoomRef(String roomId, String participantId,
                           int attemptId, String recordingHash, boolean spotCheck,
                           RelayLifetime lifetime, WorkBudget.Reservation delivery) {
        void release() { if (delivery != null) delivery.close(); }
    }
    private record TierHint(boolean isNew, long observedAtMillis) { }

    @FunctionalInterface
    public interface PlayerCountSink {
        void accept(String roomId, int playerCount);
    }

    @FunctionalInterface
    public interface TrackUpdateSink {
        void accept(String roomId, String ownerFingerprint, int zone, int act);
    }

    @FunctionalInterface
    public interface ChatModeUpdateSink {
        void accept(String roomId, String ownerFingerprint, String mode);
    }

    public static final int PLAYER_COUNT_INTERVAL_TICKS = 20;
    public static final long MAX_STALLED_JOB_MILLIS = 3_600_000L;
    private static final long TIER_HINT_MILLIS = 24L * 3_600_000L;
    private static final int MAX_IDENTITY_HINTS = 10_000;

    private final PlayerIdentity masterIdentity;
    private final TrustLadder ladder;
    private final TrackValidationProfileSource profiles;
    private final List<Executor> roomLoops;
    private final Executor brokerLoop;
    private final LongSupplier clock;
    private final PlayerCountSink playerCounts;
    private final TrackUpdateSink trackUpdates;
    private final ChatModeUpdateSink chatModeUpdates;
    private final MasterConfig verificationConfig;
    private final VerificationJobQueue verificationJobs;
    private final VerdictConsequences verdictConsequences;
    private final VerifierRegistry verifiers;
    private final Map<String, RelayLifetime> rooms = new ConcurrentHashMap<>();
    private final Set<RelayLifetime> lifetimes = ConcurrentHashMap.newKeySet();
    private final WorkBudget.Account lifecycleWork = WorkBudget.PROCESS.account(4096, 1);
    private final WorkBudget.Account brokerWork = WorkBudget.PROCESS.account(512, 8L * 1024 * 1024);
    private final WorkBudget.Account verificationWork = WorkBudget.PROCESS.account(128, 8L * 1024 * 1024);
    private final Map<String, TierHint> newTierByFingerprint = new ConcurrentHashMap<>();
    private final Map<String, RoomRef> verificationRoutes = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpotCheckByFingerprint = new ConcurrentHashMap<>();
    private final AtomicInteger nextLoop = new AtomicInteger();
    private volatile boolean closed;
    private int tickCounter;

    private final class RelayLifetime {
        final SessionRegistry.RoomEntry entry;
        final RoomAccess access;
        final WorkBudget.Reservation retirement;
        final WorkBudget.Account source = WorkBudget.PROCESS.account(512, 8L * 1024 * 1024);
        final AtomicBoolean closing = new AtomicBoolean();
        final AtomicBoolean tickPending = new AtomicBoolean();
        final AtomicBoolean countPublishPending = new AtomicBoolean();
        final ArrayDeque<WorkBudget.Task> pendingVerdicts = new ArrayDeque<>();
        final ArrayDeque<WorkBudget.Task> pendingBroker = new ArrayDeque<>();
        String lastTrack;
        String lastChatMode;

        RelayLifetime(SessionRegistry.RoomEntry entry, RoomAccess access,
                      WorkBudget.Reservation retirement) {
            this.entry = entry;
            this.access = access;
            this.retirement = retirement;
            lastTrack = trackKey(entry.descriptor());
            lastChatMode = access.room().descriptor().chatMode();
        }

        boolean current() { return !closing.get() && rooms.get(entry.roomId()) == this; }

        void retire(String reason) {
            synchronized (this) {
                if (!closing.compareAndSet(false, true)) return;
                rooms.remove(entry.roomId(), this);
                WorkBudget.Task waiting;
                while ((waiting = pendingVerdicts.pollFirst()) != null) waiting.close();
                while ((waiting = pendingBroker.pollFirst()) != null) waiting.close();
                verificationRoutes.forEach((id, route) -> {
                    if (route.lifetime() == this && verificationRoutes.remove(id, route))
                        route.release();
                });
            }
            WorkBudget.Task task = WorkBudget.Task.reserved(retirement, List.of(retirement),
                    () -> {
                        try { access.room().close(reason); }
                        finally { lifetimes.remove(this); }
                    }, () -> lifetimes.remove(this));
            if (!execute(access.loop(), task))
                throw new IllegalStateException("relay room owner unavailable during retirement");
        }
    }

    private static boolean execute(Executor executor, WorkBudget.Task task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException unavailable) {
            task.close();
            return false;
        } catch (RuntimeException | Error failure) {
            task.close();
            throw failure;
        }
    }

    private boolean toRoom(RelayLifetime lifetime, long bytes, Runnable action, Runnable discarded) {
        synchronized (lifetime) {
            if (lifetime.closing.get()) return false;
            WorkBudget.Reservation reserved = WorkBudget.PROCESS.tryReserve(lifetime.source,
                    lifetime.access.inboundWork(), new WorkBudget.Cost(1, bytes)).orElse(null);
            return reserved != null && execute(lifetime.access.loop(), WorkBudget.Task.reserved(reserved,
                    List.of(reserved), () -> {
                        if (lifetime.closing.get()) discarded.run();
                        else action.run();
                    }, discarded));
        }
    }

    private boolean toBroker(RelayLifetime lifetime, long bytes, Runnable action) {
        if (lifetime.closing.get()) return false;
        WorkBudget.Cost cost = new WorkBudget.Cost(1, bytes);
        var admission = WorkBudget.PROCESS.tryReserveDetailed(lifetime.source,
                brokerWork, cost);
        if (admission.accepted()) {
            WorkBudget.Reservation reserved = admission.value();
            return execute(brokerLoop, WorkBudget.Task.reserved(reserved,
                    List.of(reserved), () -> { if (lifetime.current()) action.run(); },
                    () -> { }));
        }
        if (admission.refusal() != WorkBudget.Refusal.DESTINATION) return false;
        WorkBudget.Reservation reserved = WorkBudget.PROCESS.tryReserve(lifetime.source,
                lifetime.source, cost).orElse(null);
        if (reserved == null) return false;
        WorkBudget.Task pending = WorkBudget.Task.reserved(reserved, List.of(reserved),
                () -> { if (lifetime.current()) action.run(); }, () -> { });
        synchronized (lifetime) {
            if (lifetime.closing.get()) {
                pending.close();
                return false;
            }
            lifetime.pendingBroker.addLast(pending);
        }
        return true;
    }

    private boolean toBrokerStatus(RelayLifetime lifetime, long bytes, Runnable action) {
        if (lifetime.closing.get()) return false;
        WorkBudget.Reservation reserved = WorkBudget.PROCESS.tryReserve(lifetime.source,
                brokerWork, new WorkBudget.Cost(1, bytes)).orElse(null);
        return reserved != null && execute(brokerLoop, WorkBudget.Task.reserved(reserved,
                List.of(reserved), action, () -> { }));
    }

    private WorkBudget.Reservation reserveBroker(RelayLifetime lifetime, long bytes) {
        return WorkBudget.PROCESS.tryReserve(lifetime.source,
                brokerWork, new WorkBudget.Cost(1, bytes)).orElse(null);
    }

    private static long retainedBytes(String... values) {
        long bytes = 0;
        for (String value : values) if (value != null) bytes += 2L * value.length();
        return bytes;
    }

    @Override public void close() {
        List<RelayLifetime> retiring;
        synchronized (lifetimes) {
            if (closed) return;
            closed = true;
            retiring = List.copyOf(lifetimes);
        }
        RuntimeException failure = null;
        for (RelayLifetime lifetime : retiring) {
            try { lifetime.retire("master closed"); }
            catch (RuntimeException unavailable) {
                if (failure == null) failure = unavailable;
                else failure.addSuppressed(unavailable);
            }
        }
        verificationRoutes.values().forEach(RoomRef::release);
        verificationRoutes.clear();
        if (failure != null) throw failure;
    }

    public RelayRoomManager(PlayerIdentity masterIdentity, TrustLadder ladder,
                            TrackValidationProfileSource profiles, List<Executor> roomLoops,
                            Executor brokerLoop, LongSupplier clock,
                            PlayerCountSink playerCounts) {
        this(masterIdentity, ladder, profiles, roomLoops, brokerLoop, clock,
                playerCounts, (roomId, owner, zone, act) -> { }, null, null, null, null);
    }

    public RelayRoomManager(PlayerIdentity masterIdentity, TrustLadder ladder,
                            TrackValidationProfileSource profiles, List<Executor> roomLoops,
                            Executor brokerLoop, LongSupplier clock,
                            PlayerCountSink playerCounts, TrackUpdateSink trackUpdates) {
        this(masterIdentity, ladder, profiles, roomLoops, brokerLoop, clock,
                playerCounts, trackUpdates, null, null, null, null);
    }

    public RelayRoomManager(PlayerIdentity masterIdentity, TrustLadder ladder,
                            TrackValidationProfileSource profiles, List<Executor> roomLoops,
                            Executor brokerLoop, LongSupplier clock,
                            PlayerCountSink playerCounts, TrackUpdateSink trackUpdates,
                            MasterConfig verificationConfig,
                            VerificationJobQueue verificationJobs,
                            VerdictConsequences verdictConsequences) {
        this(masterIdentity, ladder, profiles, roomLoops, brokerLoop, clock,
                playerCounts, trackUpdates, verificationConfig, verificationJobs,
                verdictConsequences, null);
    }

    public RelayRoomManager(PlayerIdentity masterIdentity, TrustLadder ladder,
                            TrackValidationProfileSource profiles, List<Executor> roomLoops,
                            Executor brokerLoop, LongSupplier clock,
                            PlayerCountSink playerCounts, TrackUpdateSink trackUpdates,
                            MasterConfig verificationConfig,
                            VerificationJobQueue verificationJobs,
                            VerdictConsequences verdictConsequences,
                            VerifierRegistry verifiers) {
        this(masterIdentity, ladder, profiles, roomLoops, brokerLoop, clock, playerCounts,
                trackUpdates, verificationConfig, verificationJobs, verdictConsequences,
                verifiers, (roomId, owner, mode) -> { });
    }

    public RelayRoomManager(PlayerIdentity masterIdentity, TrustLadder ladder,
                            TrackValidationProfileSource profiles, List<Executor> roomLoops,
                            Executor brokerLoop, LongSupplier clock,
                            PlayerCountSink playerCounts, TrackUpdateSink trackUpdates,
                            MasterConfig verificationConfig,
                            VerificationJobQueue verificationJobs,
                            VerdictConsequences verdictConsequences,
                            VerifierRegistry verifiers, ChatModeUpdateSink chatModeUpdates) {
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
        this.trackUpdates = trackUpdates;
        this.chatModeUpdates = Objects.requireNonNull(chatModeUpdates, "chatModeUpdates");
        this.verificationConfig = verificationConfig;
        this.verificationJobs = verificationJobs;
        this.verdictConsequences = verdictConsequences;
        this.verifiers = verifiers;
    }

    @Override
    public void noteGuestTier(String fingerprint, boolean isNew) {
        long now = clock.getAsLong();
        newTierByFingerprint.entrySet().removeIf(entry ->
                now - entry.getValue().observedAtMillis() >= TIER_HINT_MILLIS);
        if (!newTierByFingerprint.containsKey(fingerprint)
                && newTierByFingerprint.size() >= MAX_IDENTITY_HINTS) {
            newTierByFingerprint.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(entry ->
                            entry.getValue().observedAtMillis()))
                    .ifPresent(entry -> newTierByFingerprint.remove(entry.getKey()));
        }
        newTierByFingerprint.put(fingerprint, new TierHint(isNew, now));
    }

    private boolean isNewPlayer(String fingerprint) {
        TierHint hint = newTierByFingerprint.get(fingerprint);
        return hint == null || clock.getAsLong() - hint.observedAtMillis()
                >= TIER_HINT_MILLIS || hint.isNew();
    }

    @Override
    public String createRelayRoom(SessionRegistry.RoomEntry entry) {
        if (entry.descriptor().accessPolicy().passwordRequired()) {
            throw new IllegalStateException("private relay requires asynchronous preparation");
        }
        prepareRelayRoom(entry, null).join().publish(null);
        return entry.roomId();
    }

    @Override
    public CompletableFuture<PreparedRoom> prepareRelayRoom(SessionRegistry.RoomEntry entry,
                                                           RoomPassword password) {
        RelayLifetime lifetime;
        synchronized (lifetimes) {
            if (closed) return CompletableFuture.failedFuture(
                    new IllegalStateException("relay manager is closed"));
            lifetime = buildRoom(entry, password);
        }
        RoomAccess access = lifetime.access;
        return access.room().ready().handle((ignored, failure) -> {
            if (failure != null) {
                lifetime.retire("room preparation failed");
                throw new java.util.concurrent.CompletionException(failure);
            }
            return new PreparedRelayRoom(lifetime);
        });
    }

    private enum Publication { PREPARED, PUBLISHED, CLOSED }

    private final class PreparedRelayRoom implements PreparedRoom {
        private final RelayLifetime lifetime;
        private Publication state = Publication.PREPARED;

        PreparedRelayRoom(RelayLifetime lifetime) { this.lifetime = lifetime; }

        @Override public synchronized void publish(RoomInviteService inviteService) {
            synchronized (lifetime) {
                if (state != Publication.PREPARED || lifetime.closing.get()) {
                    throw new IllegalStateException("relay preparation no longer available");
                }
                state = Publication.PUBLISHED;
                lifetime.access.room().setInviteService(inviteService);
                if (rooms.putIfAbsent(lifetime.entry.roomId(), lifetime) != null) {
                    state = Publication.CLOSED;
                    lifetime.retire("duplicate relay registration");
                    throw new IllegalStateException("relay room already exists");
                }
            }
        }

        @Override public synchronized void close() {
            Publication previous = state;
            state = Publication.CLOSED;
            if (previous == Publication.CLOSED) return;
            lifetime.retire("relay preparation canceled");
        }
    }

    private RelayLifetime buildRoom(SessionRegistry.RoomEntry entry, RoomPassword password) {
        WorkBudget.Reservation retirement = WorkBudget.PROCESS.tryReserve(lifecycleWork,
                lifecycleWork, new WorkBudget.Cost(1, 0)).orElseThrow(
                        () -> new IllegalStateException("relay retirement capacity unavailable"));
        try {
            return buildRoom(entry, password, retirement);
        } catch (RuntimeException | Error failure) {
            retirement.close();
            throw failure;
        }
    }

    private RelayLifetime buildRoom(SessionRegistry.RoomEntry entry, RoomPassword password,
                                    WorkBudget.Reservation retirement) {
        AtomicReference<RelayLifetime> lifetime = new AtomicReference<>();
        ControlMessage.RoomDescriptor descriptor = entry.descriptor();
        MasterConfig policy = verificationConfig == null ? MasterConfig.defaults() : verificationConfig;
        RoomHostConfig config = new RoomHostConfig(descriptor.name(), descriptor.gameId(),
                descriptor.zone(), descriptor.act(), descriptor.characterPolicy(),
                descriptor.lockedCharacter(), descriptor.maxPlayers(),
                entry.determinismFingerprint(), entry.voteTrackKeys(),
                descriptor.verified(), descriptor.accessPolicy(), password, entry.instanceId(),
                policy.relayChatMode(descriptor.chatMode()));
        RoomHostHooks hooks = new RoomHostHooks(descriptor.maxPlayers() > 8,
                (fingerprint, memberSince) ->
                        !isNewPlayer(fingerprint)
                                || clock.getAsLong() - memberSince
                                > TrustLadder.NEW_CHAT_MUTE_MILLIS,
                (fingerprint, clean) -> toBroker(lifetime.get(), retainedBytes(fingerprint), () -> {
                    if (clean) {
                        ladder.onCleanRound(fingerprint);
                    }
                }), entry.hostFingerprint(), fingerprint ->
                isNewPlayer(fingerprint),
                this::knownVoteTrack, entry.roomId(), new RoomHostHooks.VerificationHooks() {
                    @Override
                    public void onFinishNeedingVerification(String roomId, int slot,
                            String identityFingerprint, ControlMessage.AttemptFinish finish,
                            String trackKey, String character,
                            String determinismFingerprint, boolean spotCheck) {
                        requestVerification(roomId, slot, identityFingerprint, finish,
                                trackKey, character, determinismFingerprint, spotCheck);
                    }

                }, (fingerprint, displayName) -> toBroker(lifetime.get(),
                        retainedBytes(fingerprint, displayName), () ->
                        ladder.onDisplayNameClaim(fingerprint, displayName)),
                com.openggf.net.protocol.ChatMode.parse(policy.chatModeLimit()));
        RoomHost room = new RoomHost(config, masterIdentity, clock, profiles, hooks);
        RoomRoundAccess.onPendingResultExpiry(room, result -> toBroker(lifetime.get(),
                retainedBytes(entry.roomId(), result.participantId(),
                        result.finish().inputRecordingHashHex()), () ->
                voidPendingRoute(entry.roomId(), result.participantId(),
                        result.finish().attemptId(),
                        result.finish().inputRecordingHashHex())));
        if (descriptor.verified() && verificationConfig != null) {
            room.round().setPendingHoldMillis(
                    verificationConfig.verifiedUploadDeadlineSeconds() * 1000L
                            + verificationConfig.verdictGraceMillis());
        }
        Executor loop = roomLoops.get(Math.floorMod(
                nextLoop.getAndIncrement(), roomLoops.size()));
        RoomAccess access = new RoomAccess(room, loop);
        if (!retirement.transfer(access.lifecycleWork()))
            throw new IllegalStateException("relay retirement destination unavailable");
        RelayLifetime owned = new RelayLifetime(entry, access, retirement);
        lifetime.set(owned);
        lifetimes.add(owned);
        return owned;
    }

    @Override
    public boolean attach(HubConnection connection, String roomId, String fingerprint,
                          String displayName, String instanceId,
                          String grantReference, long routeGeneration) {
        RelayLifetime lifetime = rooms.get(roomId);
        RoomAccess access = lifetime == null ? null : lifetime.access;
        if (access == null || !access.room().instanceId().equals(instanceId)
                || fingerprint == null || fingerprint.isBlank()
                || grantReference == null || grantReference.isBlank()
                || routeGeneration < 1) {
            return false;
        }
        return toRoom(lifetime, retainedBytes(fingerprint, grantReference), () -> {
            com.openggf.net.hub.RoomAdmissionBridge.connectRelay(access.room(), connection, fingerprint,
                    grantReference, routeGeneration);
        }, () -> connection.close("relay room unavailable"));
    }

    @Override
    public void hostLeft(String roomId) {
        RelayLifetime lifetime = rooms.get(roomId);
        if (lifetime != null) lifetime.retire("relay host left");
    }

    public Optional<RoomAccess> find(String roomId) {
        return Optional.ofNullable(rooms.get(roomId)).map(lifetime -> lifetime.access);
    }

    public int roomCount() {
        return rooms.size();
    }

    public void tickAll() {
        boolean publishCounts = ++tickCounter % PLAYER_COUNT_INTERVAL_TICKS == 0;
        for (Map.Entry<String, RelayLifetime> entry : rooms.entrySet()) {
            String roomId = entry.getKey();
            RelayLifetime lifetime = entry.getValue();
            RoomAccess access = lifetime.access;
            drainPendingBroker(lifetime);
            drainPendingVerdicts(lifetime);
            if (publishCounts) lifetime.countPublishPending.set(true);
            if (!lifetime.tickPending.compareAndSet(false, true)) continue;
            long queuedAt = RelayTimingEvents.roomEnabled() ? System.nanoTime() : 0;
            if (!toRoom(lifetime, retainedBytes(roomId), () -> {
                try {
                    com.openggf.net.hub.HostRoundEngine.Phase previous =
                            access.room().round().phase();
                    if (queuedAt == 0) {
                        access.room().tick();
                    } else {
                        RelayTimingEvents.roomTick(roomId, queuedAt, access.room()::tick);
                    }
                    if (previous == com.openggf.net.hub.HostRoundEngine.Phase.RUNNING
                            && access.room().round().phase()
                            == com.openggf.net.hub.HostRoundEngine.Phase.ROUND_END) {
                        selectSpotChecks(lifetime);
                    }
                    ControlMessage.RoomDescriptor descriptor = access.room().descriptor();
                    String previousMode = lifetime.lastChatMode;
                    if (!descriptor.chatMode().equals(previousMode)) {
                        String owner = lifetime.entry.hostFingerprint();
                        if (toBrokerStatus(lifetime, retainedBytes(roomId, owner, descriptor.chatMode()), () -> {
                            if (lifetime.current()) chatModeUpdates.accept(roomId, owner, descriptor.chatMode());
                        })) lifetime.lastChatMode = descriptor.chatMode();
                    }
                    String currentTrack = trackKey(descriptor);
                    String previousTrack = lifetime.lastTrack;
                    if (previousTrack != null && !previousTrack.equals(currentTrack)) {
                        String owner = lifetime.entry.hostFingerprint();
                        if (toBrokerStatus(lifetime, retainedBytes(roomId, owner), () -> {
                            if (lifetime.current()) trackUpdates.accept(roomId, owner,
                                    descriptor.zone(), descriptor.act());
                        })) lifetime.lastTrack = currentTrack;
                    }
                    if (lifetime.countPublishPending.getAndSet(false)) {
                        int count = access.room().playerCount();
                        if (!toBrokerStatus(lifetime, retainedBytes(roomId), () -> {
                            if (lifetime.current()) playerCounts.accept(roomId, count);
                        })) lifetime.countPublishPending.set(true);
                    }
                } finally { lifetime.tickPending.set(false); }
            }, () -> lifetime.tickPending.set(false))) lifetime.tickPending.set(false);
        }
    }

    private void drainPendingVerdicts(RelayLifetime lifetime) {
        List<WorkBudget.Task> ready = new ArrayList<>();
        synchronized (lifetime) {
            while (!lifetime.pendingVerdicts.isEmpty()) {
                WorkBudget.Task next = lifetime.pendingVerdicts.peekFirst();
                if (!next.transferTo(lifetime.access.inboundWork())) break;
                ready.add(lifetime.pendingVerdicts.removeFirst());
            }
        }
        ready.forEach(task -> execute(lifetime.access.loop(), task));
    }

    private void drainPendingBroker(RelayLifetime lifetime) {
        List<WorkBudget.Task> ready = new ArrayList<>();
        synchronized (lifetime) {
            while (!lifetime.pendingBroker.isEmpty()) {
                WorkBudget.Task next = lifetime.pendingBroker.peekFirst();
                if (!next.transferTo(brokerWork)) break;
                ready.add(lifetime.pendingBroker.removeFirst());
            }
        }
        ready.forEach(task -> execute(brokerLoop, task));
    }

    private void selectSpotChecks(RelayLifetime lifetime) {
        RoomHost room = lifetime.access.room();
        String roomId = lifetime.entry.roomId();
        if (lifetime.closing.get() || verificationConfig == null || verificationJobs == null || verifiers == null
                || room.descriptor().verified()) {
            return;
        }
        WorkBudget.Reservation reserved = reserveBroker(lifetime,
                2L * Protocol.MAX_CONTROL_BYTES * Math.max(1, verificationConfig.spotCheckTopTimes()));
        if (reserved == null) return;
        try {
            List<RoomRoundAccess.Result> candidates = RoomRoundAccess.results(room).stream()
                    .limit(verificationConfig.spotCheckTopTimes())
                    .filter(result -> result.fingerprint() != null
                            && result.finish() != null)
                    .toList();
            String trackKey = trackKey(room.descriptor());
            String fingerprint = room.determinismFingerprint();
            execute(brokerLoop, WorkBudget.Task.reserved(reserved, List.of(reserved), () -> {
                if (!lifetime.current()) return;
                if (!verifiers.verifierAvailable(fingerprint)) {
                    return;
                }
                long now = clock.getAsLong();
                lastSpotCheckByFingerprint.entrySet().removeIf(entry ->
                        now - entry.getValue() >= 3_600_000L);
                for (RoomRoundAccess.Result candidate : candidates) {
                    Long last = lastSpotCheckByFingerprint.get(candidate.fingerprint());
                    if (last != null && now - last < 3_600_000L) {
                        continue;
                    }
                    if (!lastSpotCheckByFingerprint.containsKey(candidate.fingerprint())
                            && lastSpotCheckByFingerprint.size() >= MAX_IDENTITY_HINTS) {
                        lastSpotCheckByFingerprint.entrySet().stream()
                                .min(java.util.Map.Entry.comparingByValue())
                                .ifPresent(entry -> lastSpotCheckByFingerprint.remove(
                                        entry.getKey()));
                    }
                    lastSpotCheckByFingerprint.put(candidate.fingerprint(), now);
                    submitVerification(roomId, candidate.slot(), candidate.participantId(),
                            candidate.fingerprint(),
                            candidate.finish(), trackKey, candidate.character(),
                            fingerprint, true);
                }
            }, () -> { }));
        } catch (RuntimeException | Error failure) {
            reserved.close();
            throw failure;
        }
    }

    public void onVerdict(VerificationJobQueue.Job job, boolean pass) {
        RoomRef route = verificationRoutes.remove(job.jobId());
        if (route == null || route.spotCheck()) {
            if (route != null) route.release();
            return;
        }
        RelayLifetime lifetime = route.lifetime();
        if (lifetime == null || route.delivery() == null) {
            route.release();
            return;
        }
        WorkBudget.Task delivery = WorkBudget.Task.reserved(route.delivery(),
                List.of(route.delivery()), () -> {
                    if (lifetime.current()) RoomRoundAccess.onVerdictForParticipant(
                            lifetime.access.room(), route.participantId(), route.attemptId(),
                            route.recordingHash(), pass);
                }, () -> { });
        synchronized (lifetime) {
            if (!lifetime.current()) {
                delivery.close();
                return;
            }
            if (!delivery.transferTo(lifetime.access.inboundWork())) {
                lifetime.pendingVerdicts.addLast(delivery);
                return;
            }
        }
        execute(lifetime.access.loop(), delivery);
    }

    public int voidExpiredUploads() {
        if (verificationJobs == null || verdictConsequences == null) {
            return 0;
        }
        List<VerificationJobQueue.Job> expired = verificationJobs.voidExpiredUploads();
        for (VerificationJobQueue.Job job : expired) {
            verdictConsequences.apply(new IdentityStore.VerdictRecord(
                    job.identityFingerprint(), job.attemptRef(),
                    job.inputRecordingHashHex(), VerdictCodec.RESULT_VOID_NO_UPLOAD,
                    null, clock.getAsLong()), "master");
            onVerdict(job, false);
        }
        return expired.size();
    }

    public int voidStalledVerificationJobs() {
        if (verificationJobs == null || verdictConsequences == null) {
            return 0;
        }
        List<VerificationJobQueue.Job> expired = verificationJobs
                .expireStalledJobs(MAX_STALLED_JOB_MILLIS);
        for (VerificationJobQueue.Job job : expired) {
            recordUnavailable(job);
            onVerdict(job, false);
        }
        return expired.size();
    }

    private void recordUnavailable(VerificationJobQueue.Job job) {
        verdictConsequences.apply(new IdentityStore.VerdictRecord(
                job.identityFingerprint(), job.attemptRef(),
                job.inputRecordingHashHex(),
                VerdictCodec.RESULT_VOID_VERIFIER_UNAVAILABLE,
                null, clock.getAsLong()), "master");
    }

    public void requestVerification(String roomId, int slot,
                                    String identityFingerprint,
                                    ControlMessage.AttemptFinish finish,
                                    String trackKey, String character,
                                    String determinismFingerprint,
                                    boolean spotCheck) {
        RelayLifetime lifetime = rooms.get(roomId);
        RoomAccess access = lifetime == null ? null : lifetime.access;
        String participantId = access == null ? null
                : RoomRoundAccess.participantIdForSlot(access.room(), slot,
                        identityFingerprint);
        if (!spotCheck && participantId == null) {
            return;
        }
        long bytes = retainedBytes(roomId, participantId, identityFingerprint,
                trackKey, character, determinismFingerprint, finish.inputRecordingHashHex(),
                finish.ghostStreamHashHex(), finish.inputRecordingRef());
        Runnable submission = () -> submitVerification(roomId, slot, participantId,
                identityFingerprint, finish, trackKey, character,
                determinismFingerprint, spotCheck);
        if (lifetime != null) toBroker(lifetime, bytes, submission);
        else {
            synchronized (lifetimes) {
                if (closed) return;
                WorkBudget.Reservation reserved = WorkBudget.PROCESS.tryReserve(verificationWork,
                        brokerWork, new WorkBudget.Cost(1, bytes)).orElse(null);
                if (reserved != null) execute(brokerLoop, WorkBudget.Task.reserved(reserved,
                        List.of(reserved), submission, () -> { }));
            }
        }
    }

    private void submitVerification(String roomId, int slot, String participantId,
                                    String identityFingerprint,
                                    ControlMessage.AttemptFinish finish,
                                    String trackKey, String character,
                                    String determinismFingerprint,
                                    boolean spotCheck) {
        if (closed || verificationConfig == null || verificationJobs == null) {
            return;
        }
        long deadlineSeconds = spotCheck ? verificationConfig.uploadDeadlineSeconds()
                : verificationConfig.verifiedUploadDeadlineSeconds();
        VerificationJobQueue.Job candidate = new VerificationJobQueue.Job(
                null, roomId, slot, identityFingerprint,
                roomId + "#" + slot + "#"
                        + (participantId == null ? "" : participantId + "#")
                        + finish.attemptId(),
                determinismFingerprint, trackKey, character, finish.timeFrames(),
                finish.firstInputFrame(), finish.finishFrame(),
                finish.inputRecordingHashHex(), finish.ghostStreamHashHex(),
                spotCheck, clock.getAsLong());
        RelayLifetime lifetime = rooms.get(roomId);
        WorkBudget.Reservation delivery = null;
        if (!spotCheck && lifetime != null && participantId != null) {
            delivery = WorkBudget.PROCESS.tryReserve(lifetime.source, lifetime.source,
                    new WorkBudget.Cost(1, retainedBytes(participantId,
                            finish.inputRecordingHashHex()))).orElse(null);
            if (delivery == null) {
                if (verdictConsequences != null) recordUnavailable(candidate);
                return;
            }
        }
        String jobId;
        try {
            jobId = verificationJobs.submit(candidate,
                    clock.getAsLong() + deadlineSeconds * 1000L);
        } catch (IllegalStateException full) {
            if (delivery != null) delivery.close();
            if (verdictConsequences != null) {
                recordUnavailable(candidate);
            }
            RelayLifetime rejected = rooms.get(roomId);
            if (rejected != null && !spotCheck && participantId != null) {
                toRoom(rejected, retainedBytes(participantId, finish.inputRecordingHashHex()),
                        () -> RoomRoundAccess.onVerdictForParticipant(rejected.access.room(),
                                participantId, finish.attemptId(), finish.inputRecordingHashHex(), false),
                        () -> { });
            }
            return;
        } catch (RuntimeException | Error failure) {
            if (delivery != null) delivery.close();
            throw failure;
        }
        RoomRef route = new RoomRef(roomId, participantId, finish.attemptId(),
                finish.inputRecordingHashHex(), spotCheck, lifetime, delivery);
        if (lifetime != null) {
            synchronized (lifetime) {
                if (!lifetime.current()) {
                    verificationJobs.voidJob(jobId);
                    route.release();
                    return;
                }
                verificationRoutes.put(jobId, route);
            }
        } else {
            synchronized (lifetimes) {
                if (closed) {
                    verificationJobs.voidJob(jobId);
                    route.release();
                    return;
                }
                verificationRoutes.put(jobId, route);
            }
        }
        if (lifetime != null && participantId != null) {
            String base = verificationConfig.publicBaseUrl();
            String path = "/recordings/" + finish.inputRecordingHashHex();
            String uploadUrl = base == null || base.isBlank() ? path
                    : base.replaceAll("/+$", "") + path;
            if (!toRoom(lifetime, retainedBytes(participantId, uploadUrl,
                    finish.inputRecordingHashHex()), () -> RoomRoundAccess.sendToParticipantInSlot(
                    lifetime.access.room(), slot, participantId,
                    new ControlMessage.RecordingRequest(finish.attemptId(),
                            finish.inputRecordingHashHex(), uploadUrl)), () -> { })) {
                verificationJobs.voidJob(jobId);
                RoomRef removed = verificationRoutes.remove(jobId);
                if (removed != null) removed.release();
                if (verdictConsequences != null) recordUnavailable(candidate);
            }
        }
    }

    private void voidPendingRoute(String roomId, String participantId,
                                  int attemptId, String recordingHash) {
        verificationRoutes.entrySet().stream()
                .filter(entry -> entry.getValue().roomId().equals(roomId)
                        && Objects.equals(entry.getValue().participantId(), participantId)
                        && entry.getValue().attemptId() == attemptId
                        && entry.getValue().recordingHash().equals(recordingHash)
                        && !entry.getValue().spotCheck())
                .findFirst().ifPresent(entry -> {
                    // A superseded claim can expire at the round-end linger while its
                    // recording is already uploaded and queued or leased; only a job
                    // still awaiting upload is voided for want of an upload.
                    boolean uploaded = verificationJobs.stateOf(entry.getKey())
                            != VerificationJobQueue.State.AWAITING_UPLOAD;
                    VerificationJobQueue.Job job = verificationJobs
                            .voidJob(entry.getKey()).orElse(null);
                    RoomRef removed = verificationRoutes.remove(entry.getKey());
                    if (removed != null) removed.release();
                    if (job != null && verdictConsequences != null) {
                        verdictConsequences.apply(new IdentityStore.VerdictRecord(
                                job.identityFingerprint(), job.attemptRef(),
                                job.inputRecordingHashHex(), uploaded
                                ? VerdictCodec.RESULT_VOID_VERIFIER_UNAVAILABLE
                                : VerdictCodec.RESULT_VOID_NO_UPLOAD, null,
                                clock.getAsLong()), "master");
                    }
                });
    }

    /** A relay room votes only among eligible Time Attack acts the master holds bounds for. */
    private boolean knownVoteTrack(String key) {
        String[] parts = key == null ? new String[0] : key.split(":", -1);
        if (parts.length != 3 || !TimeAttackTrackCatalog.isEligibleKey(key)) {
            return false;
        }
        try {
            return profiles.profileFor(parts[0], Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])).isPresent();
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String trackKey(ControlMessage.RoomDescriptor descriptor) {
        return descriptor.gameId() + ":" + descriptor.zone() + ":" + descriptor.act();
    }
}
