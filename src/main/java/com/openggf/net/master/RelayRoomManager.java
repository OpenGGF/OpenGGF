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
import com.openggf.net.protocol.VerdictCodec;

/** Master-owned relay rooms, each confined to one event-loop executor. */
public final class RelayRoomManager implements RoomBroker.RelayRoomDirectory {
    public record RoomAccess(RoomHost room, Executor loop) { }
    private record RoomRef(String roomId, int slot, int attemptId,
                           boolean spotCheck) { }

    @FunctionalInterface
    public interface PlayerCountSink {
        void accept(String roomId, int playerCount);
    }

    @FunctionalInterface
    public interface TrackUpdateSink {
        void accept(String roomId, String ownerFingerprint, int zone, int act);
    }

    public static final int PLAYER_COUNT_INTERVAL_TICKS = 20;

    private final PlayerIdentity masterIdentity;
    private final TrustLadder ladder;
    private final TrackValidationProfileSource profiles;
    private final List<Executor> roomLoops;
    private final Executor brokerLoop;
    private final LongSupplier clock;
    private final PlayerCountSink playerCounts;
    private final TrackUpdateSink trackUpdates;
    private final MasterConfig verificationConfig;
    private final VerificationJobQueue verificationJobs;
    private final VerdictConsequences verdictConsequences;
    private final VerifierRegistry verifiers;
    private final Map<String, RoomAccess> rooms = new ConcurrentHashMap<>();
    private final Map<String, Boolean> newTierByFingerprint = new ConcurrentHashMap<>();
    private final Map<String, String> roomOwners = new ConcurrentHashMap<>();
    private final Map<String, String> lastTrackKeys = new ConcurrentHashMap<>();
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
        this.verificationConfig = verificationConfig;
        this.verificationJobs = verificationJobs;
        this.verdictConsequences = verdictConsequences;
        this.verifiers = verifiers;
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
                entry.determinismFingerprint(), entry.voteTrackKeys(),
                descriptor.verified());
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
                newTierByFingerprint.getOrDefault(fingerprint, false),
                this::knownVoteTrack, entry.roomId(), new RoomHostHooks.VerificationHooks() {
                    @Override
                    public void onFinishNeedingVerification(String roomId, int slot,
                            String identityFingerprint, ControlMessage.AttemptFinish finish,
                            String trackKey, String character,
                            String determinismFingerprint, boolean spotCheck) {
                        requestVerification(roomId, slot, identityFingerprint, finish,
                                trackKey, character, determinismFingerprint, spotCheck);
                    }

                    @Override
                    public void onPendingExpired(String roomId, int slot, int attemptId) {
                        brokerLoop.execute(() -> voidPendingRoute(roomId, slot, attemptId));
                    }
                });
        RoomHost room = new RoomHost(config, masterIdentity, clock, profiles, hooks);
        if (descriptor.verified() && verificationConfig != null) {
            room.round().setPendingHoldMillis(
                    verificationConfig.verifiedUploadDeadlineSeconds() * 1000L
                            + verificationConfig.verdictGraceMillis());
        }
        Executor loop = roomLoops.get(Math.floorMod(
                nextLoop.getAndIncrement(), roomLoops.size()));
        RoomAccess old = rooms.putIfAbsent(entry.roomId(), new RoomAccess(room, loop));
        if (old != null) {
            throw new IllegalStateException("relay room already exists: " + entry.roomId());
        }
        roomOwners.put(entry.roomId(), entry.hostFingerprint());
        lastTrackKeys.put(entry.roomId(), trackKey(descriptor));
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
        roomOwners.remove(roomId);
        lastTrackKeys.remove(roomId);
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
                com.openggf.net.hub.HostRoundEngine.Phase previous =
                        access.room().round().phase();
                access.room().tick();
                if (previous == com.openggf.net.hub.HostRoundEngine.Phase.RUNNING
                        && access.room().round().phase()
                        == com.openggf.net.hub.HostRoundEngine.Phase.ROUND_END) {
                    selectSpotChecks(roomId, access.room());
                }
                ControlMessage.RoomDescriptor descriptor = access.room().descriptor();
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
        record Candidate(int slot, String fingerprint, String character,
                         ControlMessage.AttemptFinish finish) { }
        List<Candidate> candidates = room.round().standings().stream()
                .limit(verificationConfig.spotCheckTopTimes())
                .map(row -> new Candidate(row.slot(),
                        room.identityFingerprintForSlot(row.slot()), row.character(),
                        room.round().bestFinish(row.slot())))
                .filter(candidate -> candidate.fingerprint() != null
                        && candidate.finish() != null)
                .toList();
        String trackKey = trackKey(room.descriptor());
        String fingerprint = room.determinismFingerprint();
        brokerLoop.execute(() -> {
            if (!verifiers.verifierAvailable(fingerprint)) {
                return;
            }
            long now = clock.getAsLong();
            for (Candidate candidate : candidates) {
                Long last = lastSpotCheckByFingerprint.get(candidate.fingerprint());
                if (last != null && now - last < 3_600_000L) {
                    continue;
                }
                lastSpotCheckByFingerprint.put(candidate.fingerprint(), now);
                submitVerification(roomId, candidate.slot(), candidate.fingerprint(),
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
            access.loop().execute(() -> access.room().round().onVerdict(
                    route.slot(), route.attemptId(), pass));
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

    public void requestVerification(String roomId, int slot,
                                    String identityFingerprint,
                                    ControlMessage.AttemptFinish finish,
                                    String trackKey, String character,
                                    String determinismFingerprint,
                                    boolean spotCheck) {
        brokerLoop.execute(() -> submitVerification(roomId, slot, identityFingerprint,
                finish, trackKey, character, determinismFingerprint, spotCheck));
    }

    private void submitVerification(String roomId, int slot,
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
                roomId + "#" + slot + "#" + finish.attemptId(),
                determinismFingerprint, trackKey, character, finish.timeFrames(),
                finish.firstInputFrame(), finish.finishFrame(),
                finish.inputRecordingHashHex(), finish.ghostStreamHashHex(),
                spotCheck, clock.getAsLong());
        String jobId = verificationJobs.submit(candidate,
                clock.getAsLong() + deadlineSeconds * 1000L);
        verificationRoutes.put(jobId,
                new RoomRef(roomId, slot, finish.attemptId(), spotCheck));
        RoomAccess access = rooms.get(roomId);
        if (access != null) {
            String base = verificationConfig.publicBaseUrl();
            String path = "/recordings/" + finish.inputRecordingHashHex();
            String uploadUrl = base == null || base.isBlank() ? path
                    : base.replaceAll("/+$", "") + path;
            access.loop().execute(() -> access.room().sendToSlot(slot,
                    new ControlMessage.RecordingRequest(finish.attemptId(),
                            finish.inputRecordingHashHex(), uploadUrl)));
        }
    }

    private void voidPendingRoute(String roomId, int slot, int attemptId) {
        verificationRoutes.entrySet().stream()
                .filter(entry -> entry.getValue().roomId().equals(roomId)
                        && entry.getValue().slot() == slot
                        && entry.getValue().attemptId() == attemptId
                        && !entry.getValue().spotCheck())
                .findFirst().ifPresent(entry -> {
                    VerificationJobQueue.Job job = verificationJobs
                            .voidJob(entry.getKey()).orElse(null);
                    verificationRoutes.remove(entry.getKey());
                    if (job != null && verdictConsequences != null) {
                        verdictConsequences.apply(new IdentityStore.VerdictRecord(
                                job.identityFingerprint(), job.attemptRef(),
                                job.inputRecordingHashHex(),
                                VerdictCodec.RESULT_VOID_NO_UPLOAD, null,
                                clock.getAsLong()), "master");
                    }
                });
    }

    private boolean knownVoteTrack(String key) {
        String[] parts = key == null ? new String[0] : key.split(":", -1);
        if (parts.length != 3) {
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
