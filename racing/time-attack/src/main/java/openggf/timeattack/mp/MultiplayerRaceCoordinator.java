package openggf.timeattack.mp;

import com.openggf.net.ghost.GhostFrame;
import openggf.timeattack.AttemptInputRecording;
import openggf.timeattack.GhostStore;
import openggf.timeattack.TimeAttackRuntime;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.client.RaceClient;
import com.openggf.net.client.RemoteGhostRegistry;
import com.openggf.net.hub.HostRoundEngine;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.mods.run.GhostPose;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.LevelOverlayCanvas;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bridges a room connection to one attached time-attack runtime per round. */
public final class MultiplayerRaceCoordinator implements TimeAttackRuntime.AttemptListener,
        TimeAttackRuntime.FrameCompanion {
    private final Logger LOGGER = Logger.getLogger(
            MultiplayerRaceCoordinator.class.getName());
    private static final long PING_INTERVAL_MILLIS = 500;
    private static final long STANDINGS_PAGE_INTERVAL_MILLIS = 2000;
    private static final int STANDINGS_PAGE_SIZE = 10;
    private static final int AROUND_YOU_ROWS = 5;

    private final RaceTransport transport;
    private final ClientRaceSession session;
    private final RemoteGhostRegistry registry = new RemoteGhostRegistry();
    private final LongSupplier clockMillis;
    private final AttemptRecordingVault recordingVault;
    private final RecordingUploader recordingUploader;
    private final String configuredMasterUrl;
    private final GhostStore ghostStore;

    private TimeAttackRuntime runtime;
    private GhostStreamPublisher publisher;
    private boolean publisherActive;
    private int pendingAttemptOrdinal = -1;
    private List<GhostPose> remoteGhosts = List.of();
    private boolean connectionLost;
    private long lastPingAtMillis = Long.MIN_VALUE;
    private long lastStandingsPageRequestAt = Long.MIN_VALUE;
    private int localRank = -1;
    private List<ControlMessage.StandingsRow> topStandings = List.of();
    private List<ControlMessage.StandingsRow> aroundYouStandings = List.of();
    private int roundsStarted;
    /** {@link #roundsStarted} when the runtime was attached: the round its run belongs to. */
    private int attachedRound;
    /**
     * The host has closed the latest round ({@code RoundEnd}, a vote offer or a vote result).
     * Unlike {@link ClientRaceSession#phase()}, this never comes from the estimated hub clock.
     */
    private boolean latestRoundClosed;
    private boolean leaveRequested;
    private boolean showMinimap = true;
    private Consumer<RunLevelStart> levelReadyHook;
    /** Spectator pan speed in pixels per held frame after the local attempt has finished. */
    static final int SPECTATE_PAN_SPEED_PX = 8;

    public MultiplayerRaceCoordinator(RaceTransport transport, ClientRaceSession session) {
        this(transport, session, System::currentTimeMillis, "", false,
                new GhostStore(Path.of("ghosts")));
    }

    public MultiplayerRaceCoordinator(RaceTransport transport, ClientRaceSession session,
                                      LongSupplier clockMillis) {
        this(transport, session, clockMillis, "", false,
                new GhostStore(Path.of("ghosts")));
    }

    public MultiplayerRaceCoordinator(RaceTransport transport, ClientRaceSession session,
                                      LongSupplier clockMillis, String configuredMasterUrl,
                                      boolean trustInsecure, GhostStore ghostStore) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.session = Objects.requireNonNull(session, "session");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        this.recordingVault = new AttemptRecordingVault(clockMillis);
        this.configuredMasterUrl = configuredMasterUrl == null ? "" : configuredMasterUrl;
        this.ghostStore = Objects.requireNonNull(ghostStore, "ghostStore");
        String token = transport.sessionToken();
        this.recordingUploader = token == null || token.isBlank()
                ? null : new RecordingUploader(token, trustInsecure);
    }

    public void attachRuntime(TimeAttackRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        detachRuntime();
        this.runtime = runtime;
        this.publisher = new GhostStreamPublisher(transport::sendBinary);
        attachedRound = roundsStarted;
        leaveRequested = false;
        runtime.setAttemptListener(this);
        runtime.setExtraGhostSupplier(this::remoteActiveGhosts);
        runtime.setFrameCompanion(this);
    }

    public void detachRuntime() {
        if (runtime != null) {
            runtime.spectate(false, 0, 0);
        }
        if (runtime != null) {
            runtime.setAttemptListener(null);
            runtime.setExtraGhostSupplier(null);
            runtime.setFrameCompanion(null);
            runtime = null;
        }
        if (publisherActive && publisher != null) {
            publisher.abandonAttempt();
        }
        publisherActive = false;
        pendingAttemptOrdinal = -1;
        leaveRequested = false;
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
                    } else if (control.message() instanceof ControlMessage.PlayerUpsert upsert) {
                        registry.onPlayerUpsert(upsert.player());
                    } else if (control.message() instanceof ControlMessage.PlayerLeft left) {
                        registry.onPlayerLeft(left.slot());
                    } else if (control.message() instanceof ControlMessage.RoundStart) {
                        roundsStarted++;
                        latestRoundClosed = false;
                        registry.reset();
                        remoteGhosts = List.of();
                        localRank = -1;
                        topStandings = List.of();
                        aroundYouStandings = List.of();
                    } else if (control.message() instanceof ControlMessage.StandingsDelta delta) {
                        boolean wasInTop = topStandings.stream()
                                .anyMatch(row -> row.slot() == session.localSlot());
                        topStandings = List.copyOf(delta.rows());
                        var localRow = delta.rows().stream()
                                .filter(row -> row.slot() == session.localSlot())
                                .findFirst();
                        if (localRow.isPresent()) {
                            localRank = localRow.get().rank();
                        } else if (wasInTop) {
                            // Pushed out of the top ten (the host's RankUpdate follows on
                            // this ordered connection) or the only result was removed.
                            localRank = -1;
                        }
                        maybeRequestAroundYouPage();
                    } else if (control.message() instanceof ControlMessage.RankUpdate rank) {
                        localRank = rank.rank();
                        maybeRequestAroundYouPage();
                    } else if (control.message() instanceof ControlMessage.StandingsPage page) {
                        applyAroundYouPage(page.rows());
                    } else if (control.message() instanceof ControlMessage.RoundEnd) {
                        latestRoundClosed = true;
                        recordingVault.onRoundEnd();
                    } else if (control.message() instanceof ControlMessage.TrackVoteOffer
                            || control.message() instanceof ControlMessage.TrackVoteResult) {
                        latestRoundClosed = true;
                    } else if (control.message() instanceof ControlMessage.RecordingRequest request) {
                        uploadRequestedRecording(request);
                    }
                }
                case RaceClient.GhostData ghost -> registry.onAggregate(ghost.aggregate());
                case RaceClient.Roster roster -> registry.onRoster(roster.entries());
                case RaceClient.Disconnected ignored -> connectionLost = true;
            }
        }
        maybePing();
        recordingVault.evictExpired();
    }

    public void beforeLevelFrame() {
        pump();
    }

    /**
     * Holds the attached run during its own round's countdown and once it has asked to leave. A
     * newer round's countdown never freezes an older run, such as its finished results tally.
     */
    public boolean holdGameplay() {
        return runtime != null && (leaveRequested || (roundsStarted == attachedRound
                && session.phase() == ClientRaceSession.Phase.COUNTDOWN));
    }

    @Override
    public boolean admit(com.openggf.mods.run.RunInput input) {
        pump();
        leaveIfRoundOver();
        pollLocalInput(input);
        return !holdGameplay();
    }

    /** An open window turns a completed act into the next attempt; a closed one ends the run. */
    @Override
    public boolean continueAfterActCompletion() {
        return runtime != null && isAttachedRoundCurrent() && session.isWindowOpen();
    }

    /** Retries restart the act only while the attached round can still take an attempt. */
    @Override
    public boolean mayRetry() {
        return runtime != null && isAttachedRoundCurrent()
                && (session.phase() == ClientRaceSession.Phase.COUNTDOWN || session.isWindowOpen());
    }

    private boolean isAttachedRoundCurrent() {
        return !leaveRequested && roundsStarted == attachedRound;
    }

    /**
     * An unfinished run outlives its round once the host has closed it or a newer one has
     * started: leave it so the lobby can take over. Only the host's messages count here: the
     * lobby never relaunches a round, so leaving on the estimated deadline would lock out a
     * player whose clock runs ahead of the hub, while that estimate only voids the attempt. A
     * finished attempt keeps its results tally; the run then ends when the tally exits because
     * the window is closed.
     */
    private void leaveIfRoundOver() {
        if (runtime == null || leaveRequested || runtime.isAttemptFinished()) {
            return;
        }
        if (roundsStarted != attachedRound || latestRoundClosed) {
            leaveRequested = true;
            runtime.leaveRun();
        }
    }

    @Override
    public void afterStep() {
        afterLevelFrame();
    }

    @Override
    public void levelReady(RunLevelStart start) {
        if (levelReadyHook != null) {
            levelReadyHook.accept(start);
        }
    }

    /** Draws the multiplayer HUD (countdown, window, standings, votes, minimap) over the run. */
    @Override
    public void drawOverlay(LevelOverlayCanvas canvas, int levelWidth, int localCentreX) {
        MultiplayerHudRenderer.render(canvas, hudState(), levelWidth, localCentreX, showMinimap,
                runtime != null && runtime.isAttemptFinished());
    }

    /** Called on the engine thread when an attached run's level is ready (launch and retry). */
    public void setLevelReadyHook(Consumer<RunLevelStart> hook) {
        this.levelReadyHook = hook;
    }

    /** Whether the in-run HUD draws the minimap progress strip. */
    public void setShowMinimap(boolean show) {
        this.showMinimap = show;
    }

    /**
     * Round starts seen on this connection; a lobby launches one run per value. A player who
     * joins during a running round sees zero starts, so that round still launches once.
     */
    public int roundsStarted() {
        return roundsStarted;
    }

    public void afterLevelFrame() {
        if (runtime != null && !session.isWindowOpen() && runtime.isAttemptActive()) {
            runtime.voidCurrentAttempt();
        }
        remoteGhosts = presentRemoteGhosts(
                registry.advanceAll(session.localSlot()));
    }

    public List<GhostPose> remoteActiveGhosts() {
        return remoteGhosts;
    }

    public MultiplayerHudState hudState() {
        ControlMessage.RoundConfig config = session.roundConfig();
        ControlMessage.StandingsRow local = session.localStandingsRow();
        List<RemoteGhostRegistry.FarPlayer> minimapPlayers = new ArrayList<>(
                registry.farPlayers(session.localSlot()));
        for (GhostPose ghost : remoteGhosts) {
            int slot;
            try {
                slot = Integer.parseInt(ghost.slotId().substring("net:".length()));
            } catch (RuntimeException ignored) {
                continue;
            }
            minimapPlayers.add(new RemoteGhostRegistry.FarPlayer(slot,
                    ghost.nameplate() == null ? "?" : ghost.nameplate(),
                    ghost.characterCode(), ghost.pose().centreX() >> 6,
                    ghost.pose().centreY() >> 6, -1));
        }
        return new MultiplayerHudState(transport.isOpen() || connectionLost,
                session.phase().name(), session.remainingWindowMillis(),
                session.remainingCountdownMillis(), combinedStandings(),
                session.chatLines(), connectionLost, session.kickReason(),
                session.players().size(), minimapPlayers,
                config == null ? null : config.characterPolicy(),
                session.voteOptions(), session.voteCounts(), session.voteRemainingMillis(),
                session.lastVoteResultTrackKey(), session.podiumTop(3),
                local == null ? localRank : local.rank());
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

    public void pollLocalInput(com.openggf.mods.run.RunInput input) {
        for (int option = 0; option < 3; option++) {
            if (input.keyPressed(SceneKeys.DIGIT_1 + option)) {
                castVote(option);
            }
        }
        var logical = input.input();
        int dx = (logical.menuRight() ? 1 : 0) - (logical.menuLeft() ? 1 : 0);
        int dy = (logical.menuDown() ? 1 : 0) - (logical.menuUp() ? 1 : 0);
        boolean active = runtime != null && runtime.isAttemptFinished()
                && session.phase() == ClientRaceSession.Phase.RUNNING;
        if (runtime != null) {
            runtime.spectate(active, dx * SPECTATE_PAN_SPEED_PX,
                    dy * SPECTATE_PAN_SPEED_PX);
        }
    }

    void castVote(int optionIndex) {
        List<String> options = session.voteOptions();
        if (session.phase() == ClientRaceSession.Phase.VOTE
                && optionIndex >= 0 && optionIndex < options.size()) {
            transport.sendControl(new ControlMessage.TrackVote(options.get(optionIndex)));
        }
    }

    public void shutdown() {
        detachRuntime();
        if (recordingUploader != null) {
            recordingUploader.close();
        }
        transport.close();
    }

    /**
     * Records the attempt as pending. Its {@code AttemptStart} waits for the first sampled frame,
     * so the reload fade and title card never count against the host's pacing check.
     */
    @Override
    public void onAttemptBegan(int attemptOrdinal) {
        requirePublisher();
        if (publisherActive) {
            // The runtime voids before every reload, so this is defensive: never throw here.
            int stale = publisher.currentAttemptId();
            LOGGER.warning("Attempt " + attemptOrdinal + " began while attempt " + stale
                    + " was still published; resetting it");
            publisher.abandonAttempt();
            publisherActive = false;
            transport.sendControl(new ControlMessage.AttemptReset(stale));
        }
        pendingAttemptOrdinal = attemptOrdinal;
    }

    @Override
    public void onFrameSampled(int attemptOrdinal, GhostFrame frame) {
        requirePublisher();
        if (!publisherActive && pendingAttemptOrdinal == attemptOrdinal) {
            if (session.phase() != ClientRaceSession.Phase.RUNNING) {
                // Outside the window the attempt can never count; afterLevelFrame voids it.
                return;
            }
            startPublisher(attemptOrdinal);
        }
        if (!publisherActive || publisher.currentAttemptId() != attemptOrdinal) {
            throw new IllegalStateException(
                    "frame sampled for attempt " + attemptOrdinal + " that never began");
        }
        publisher.onFrame(frame);
    }

    @Override
    public void onAttemptFinished(int attemptOrdinal, int timeFrames,
                                  int firstInputFrame, int finishFrame,
                                  byte[] inputRecordingSha256,
                                  AttemptInputRecording recording) {
        requirePublisher();
        if (!publisherActive && pendingAttemptOrdinal == attemptOrdinal) {
            // Every frame was dropped outside the window, so the host never saw this attempt.
            pendingAttemptOrdinal = -1;
            return;
        }
        publisher.finishAttempt();
        publisherActive = false;
        String recordingHash = HexFormat.of().formatHex(inputRecordingSha256);
        AttemptRecordingVault.PutResult custody = recordingVault.put(recordingHash, recording.encode());
        if (custody != AttemptRecordingVault.PutResult.STORED && custody != AttemptRecordingVault.PutResult.DUPLICATE) {
            LOGGER.warning("Attempt recording custody refused: " + custody);
        }
        transport.sendControl(new ControlMessage.AttemptFinish(
                attemptOrdinal, timeFrames, firstInputFrame, finishFrame,
                recordingHash,
                HexFormat.of().formatHex(publisher.streamHashSha256()), null));
    }

    @Override
    public void onAttemptVoided(int attemptOrdinal) {
        requirePublisher();
        if (!publisherActive && pendingAttemptOrdinal == attemptOrdinal) {
            pendingAttemptOrdinal = -1;
            return;
        }
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

    private void startPublisher(int attemptOrdinal) {
        publisher.beginAttempt(attemptOrdinal);
        publisherActive = true;
        pendingAttemptOrdinal = -1;
        transport.sendControl(new ControlMessage.AttemptStart(attemptOrdinal));
    }

    private void uploadRequestedRecording(ControlMessage.RecordingRequest request) {
        if (recordingUploader == null) {
            LOGGER.warning("Ignoring recording request without an upload session token");
            return;
        }
        byte[] recording = recordingVault.get(request.expectedHashHex()).orElse(null);
        if (recording == null) {
            try {
                recording = ghostStore.findInputRecording(request.expectedHashHex())
                        .orElse(null);
            } catch (java.io.IOException failure) {
                LOGGER.log(Level.WARNING, "Unable to search persisted recording sidecars", failure);
            }
        }
        if (recording == null) {
            LOGGER.warning("Requested attempt recording is no longer available: "
                    + request.expectedHashHex());
            return;
        }
        try {
            String url = RecordingUploader.resolveUploadUrl(
                    request.uploadUrl(), configuredMasterUrl);
            RecordingUploader.Admission admission = recordingUploader.upload(url, recording, success -> {
                if (!success) {
                    LOGGER.warning("Attempt recording upload failed: "
                            + request.expectedHashHex());
                }
            });
            if (admission != RecordingUploader.Admission.ACCEPTED && admission != RecordingUploader.Admission.DUPLICATE) {
                LOGGER.warning("Attempt recording upload refused: " + admission);
            }
        } catch (IllegalArgumentException invalidUrl) {
            LOGGER.log(Level.WARNING, "Invalid recording upload URL", invalidUrl);
        }
    }

    static List<GhostPose> presentRemoteGhosts(
            List<RemoteGhostRegistry.RemoteGhost> ghosts) {
        List<GhostPose> active = new ArrayList<>(ghosts.size());
        for (RemoteGhostRegistry.RemoteGhost ghost : ghosts) {
            float finishedScale = ghost.state().frame().finished() ? 0.55f : 1f;
            active.add(new GhostPose("net:" + ghost.slot(), ghost.character(),
                    TimeAttackRuntime.poseOf(ghost.state().frame()), ghost.displayName(),
                    ghost.state().opacityScale() * finishedScale));
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

    private void maybeRequestAroundYouPage() {
        if (localRank <= HostRoundEngine.STANDINGS_BROADCAST_CAP || !transport.isOpen()) {
            return;
        }
        long now = clockMillis.getAsLong();
        if (lastStandingsPageRequestAt != Long.MIN_VALUE
                && now - lastStandingsPageRequestAt < STANDINGS_PAGE_INTERVAL_MILLIS) {
            return;
        }
        lastStandingsPageRequestAt = now;
        transport.sendControl(new ControlMessage.StandingsPageRequest(
                (localRank - 1) / STANDINGS_PAGE_SIZE));
    }

    private void applyAroundYouPage(List<ControlMessage.StandingsRow> rows) {
        int localIndex = -1;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).slot() == session.localSlot()) {
                localIndex = index;
                localRank = rows.get(index).rank();
                break;
            }
        }
        if (localIndex < 0) {
            aroundYouStandings = List.of();
            return;
        }
        int start = Math.max(0, localIndex - AROUND_YOU_ROWS / 2);
        start = Math.min(start, Math.max(0, rows.size() - AROUND_YOU_ROWS));
        aroundYouStandings = List.copyOf(rows.subList(
                start, Math.min(rows.size(), start + AROUND_YOU_ROWS)));
    }

    private List<ControlMessage.StandingsRow> combinedStandings() {
        List<ControlMessage.StandingsRow> top = topStandings.isEmpty()
                ? session.standings() : topStandings;
        if (aroundYouStandings.isEmpty()) {
            return top;
        }
        List<ControlMessage.StandingsRow> combined = new ArrayList<>(top);
        for (ControlMessage.StandingsRow row : aroundYouStandings) {
            if (combined.stream().noneMatch(existing -> existing.slot() == row.slot())) {
                combined.add(row);
            }
        }
        return List.copyOf(combined);
    }
}
