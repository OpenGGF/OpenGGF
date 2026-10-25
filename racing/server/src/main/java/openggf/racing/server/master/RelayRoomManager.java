package openggf.racing.server.master;

import openggf.timeattack.TimeAttackTrackCatalog;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.RoomHostHooks;
import com.openggf.net.hub.RoomRoundAccess;
import com.openggf.net.hub.RoomInviteService;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.RoomPassword;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import com.openggf.net.protocol.VerdictCodec;

/** Master-owned relay rooms, each confined to one event-loop executor. */
public final class RelayRoomManager implements RoomBroker.RelayRoomDirectory {
    public record RoomAccess(RoomHost room, Executor loop) { }
    private record RoomRef(String roomId, String participantId,
                           int attemptId, String recordingHash, boolean spotCheck) { }
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
    private final Map<String, RoomAccess> rooms = new ConcurrentHashMap<>();
    private final Map<String, TierHint> newTierByFingerprint = new ConcurrentHashMap<>();
    private final Map<String, String> roomOwners = new ConcurrentHashMap<>();
    private final Map<String, String> lastTrackKeys = new ConcurrentHashMap<>();
    private final Map<String, String> lastChatModes = new ConcurrentHashMap<>();
    private final Map<String, RoomRef> verificationRoutes = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpotCheckByFingerprint = new ConcurrentHashMap<>();
    private final AtomicInteger nextLoop = new AtomicInteger();
    private int tickCounter;

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
        RoomAccess access = buildRoom(entry, password);
        return access.room().ready().handle((ignored, failure) -> {
            if (failure != null) {
                access.loop().execute(() -> access.room().close("room preparation failed"));
                throw new java.util.concurrent.CompletionException(failure);
            }
            return new PreparedRelayRoom(entry, access);
        });
    }

    private enum Publication { PREPARED, PUBLISHED, CLOSED }

    private final class PreparedRelayRoom implements PreparedRoom {
        private final SessionRegistry.RoomEntry entry;
        private final RoomAccess access;
        private Publication state = Publication.PREPARED;

        PreparedRelayRoom(SessionRegistry.RoomEntry entry, RoomAccess access) {
            this.entry = entry;
            this.access = access;
        }

        @Override public synchronized void publish(RoomInviteService inviteService) {
            if (state != Publication.PREPARED) {
                throw new IllegalStateException("relay preparation no longer available");
            }
            state = Publication.PUBLISHED;
            access.room().setInviteService(inviteService);
            if (rooms.putIfAbsent(entry.roomId(), access) != null) {
                state = Publication.CLOSED;
                access.loop().execute(() -> access.room().close("duplicate relay registration"));
                throw new IllegalStateException("relay room already exists");
            }
            roomOwners.put(entry.roomId(), entry.hostFingerprint());
            lastTrackKeys.put(entry.roomId(), trackKey(entry.descriptor()));
            lastChatModes.put(entry.roomId(), access.room().descriptor().chatMode());
        }

        @Override public synchronized void close() {
            Publication previous = state;
            state = Publication.CLOSED;
            if (previous == Publication.CLOSED) return;
            if (previous == Publication.PUBLISHED && rooms.remove(entry.roomId(), access)) {
                roomOwners.remove(entry.roomId(), entry.hostFingerprint());
                lastTrackKeys.remove(entry.roomId());
                lastChatModes.remove(entry.roomId());
            }
            access.loop().execute(() -> access.room().close("relay preparation canceled"));
        }
    }

    private RoomAccess buildRoom(SessionRegistry.RoomEntry entry, RoomPassword password) {
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
                (fingerprint, clean) -> brokerLoop.execute(() -> {
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

                }, (fingerprint, displayName) -> brokerLoop.execute(() ->
                        ladder.onDisplayNameClaim(fingerprint, displayName)),
                com.openggf.net.protocol.ChatMode.parse(policy.chatModeLimit()));
        RoomHost room = new RoomHost(config, masterIdentity, clock, profiles, hooks);
        RoomRoundAccess.onPendingResultExpiry(room, result -> brokerLoop.execute(() ->
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
        return new RoomAccess(room, loop);
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
        roomOwners.remove(roomId);
        lastTrackKeys.remove(roomId);
        lastChatModes.remove(roomId);
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
            long queuedAt = RelayTimingEvents.roomEnabled() ? System.nanoTime() : 0;
            access.loop().execute(() -> {
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
                    selectSpotChecks(roomId, access.room());
                }
                ControlMessage.RoomDescriptor descriptor = access.room().descriptor();
                String previousMode = lastChatModes.put(roomId, descriptor.chatMode());
                if (!descriptor.chatMode().equals(previousMode)) {
                    String owner = roomOwners.get(roomId);
                    if (owner != null) {
                        brokerLoop.execute(() -> chatModeUpdates.accept(roomId, owner, descriptor.chatMode()));
                    }
                }
                String currentTrack = trackKey(descriptor);
                String previousTrack = lastTrackKeys.put(roomId, currentTrack);
                if (previousTrack != null && !previousTrack.equals(currentTrack)) {
                    String owner = roomOwners.get(roomId);
                    if (owner != null) {
                        brokerLoop.execute(() -> trackUpdates.accept(roomId, owner,
                                descriptor.zone(), descriptor.act()));
                    }
                }
                if (publishCounts) {
                    int count = access.room().playerCount();
                    brokerLoop.execute(() -> playerCounts.accept(roomId, count));
                }
            });
        }
    }

    private void selectSpotChecks(String roomId, RoomHost room) {
        if (verificationConfig == null || verificationJobs == null || verifiers == null
                || room.descriptor().verified()) {
            return;
        }
        List<RoomRoundAccess.Result> candidates = RoomRoundAccess.results(room).stream()
                .limit(verificationConfig.spotCheckTopTimes())
                .filter(result -> result.fingerprint() != null
                        && result.finish() != null)
                .toList();
        String trackKey = trackKey(room.descriptor());
        String fingerprint = room.determinismFingerprint();
        brokerLoop.execute(() -> {
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
        });
    }

    public void onVerdict(VerificationJobQueue.Job job, boolean pass) {
        RoomRef route = verificationRoutes.remove(job.jobId());
        if (route == null || route.spotCheck()) {
            return;
        }
        RoomAccess access = rooms.get(route.roomId());
        if (access != null) {
            access.loop().execute(() -> RoomRoundAccess.onVerdictForParticipant(
                    access.room(), route.participantId(), route.attemptId(),
                    route.recordingHash(), pass));
        }
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
        RoomAccess access = rooms.get(roomId);
        String participantId = access == null ? null
                : RoomRoundAccess.participantIdForSlot(access.room(), slot,
                        identityFingerprint);
        if (!spotCheck && participantId == null) {
            return;
        }
        brokerLoop.execute(() -> submitVerification(roomId, slot, participantId,
                identityFingerprint, finish, trackKey, character,
                determinismFingerprint, spotCheck));
    }

    private void submitVerification(String roomId, int slot, String participantId,
                                    String identityFingerprint,
                                    ControlMessage.AttemptFinish finish,
                                    String trackKey, String character,
                                    String determinismFingerprint,
                                    boolean spotCheck) {
        if (verificationConfig == null || verificationJobs == null) {
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
        String jobId;
        try {
            jobId = verificationJobs.submit(candidate,
                    clock.getAsLong() + deadlineSeconds * 1000L);
        } catch (IllegalStateException full) {
            if (verdictConsequences != null) {
                recordUnavailable(candidate);
            }
            RoomAccess rejected = rooms.get(roomId);
            if (rejected != null && !spotCheck && participantId != null) {
                rejected.loop().execute(() -> RoomRoundAccess.onVerdictForParticipant(
                        rejected.room(), participantId, finish.attemptId(),
                        finish.inputRecordingHashHex(), false));
            }
            return;
        }
        verificationRoutes.put(jobId,
                new RoomRef(roomId, participantId, finish.attemptId(),
                        finish.inputRecordingHashHex(), spotCheck));
        RoomAccess access = rooms.get(roomId);
        if (access != null && participantId != null) {
            String base = verificationConfig.publicBaseUrl();
            String path = "/recordings/" + finish.inputRecordingHashHex();
            String uploadUrl = base == null || base.isBlank() ? path
                    : base.replaceAll("/+$", "") + path;
            access.loop().execute(() -> RoomRoundAccess.sendToParticipantInSlot(
                    access.room(), slot, participantId,
                    new ControlMessage.RecordingRequest(finish.attemptId(),
                            finish.inputRecordingHashHex(), uploadUrl)));
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
                    verificationRoutes.remove(entry.getKey());
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
