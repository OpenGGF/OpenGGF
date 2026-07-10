package com.openggf.net.hub;

import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Single-threaded room protocol driver and owner of its hub and round state. */
public final class RoomHost {
    public static final long ADMISSION_TIMEOUT_MILLIS = 5000;
    public static final long STANDINGS_PAGE_INTERVAL_MILLIS = 2000;
    public static final int STANDINGS_PAGE_SIZE = 10;
    private static final int TOKEN_STRIKE_LIMIT = 3;

    private static final class Member {
        final HubConnection connection;
        final HostHandshake handshake;
        final long connectedAt;
        boolean admitted;
        int slot = -1;
        String token;
        String fingerprint;
        String displayName;
        String character;
        long memberSinceMillis;
        long lastChatMillis = Long.MIN_VALUE;
        long lastStandingsPageMillis = Long.MIN_VALUE;
        int tokenStrikes;
        int violationsThisRound;
        boolean finishedThisRound;

        Member(HubConnection connection, HostHandshake handshake, long connectedAt) {
            this.connection = connection;
            this.handshake = handshake;
            this.connectedAt = connectedAt;
        }
    }

    private final RoomHostConfig config;
    private final PlayerIdentity hostIdentity;
    private final LongSupplier wallClockMillis;
    private final SessionTokenIssuer tokens = new SessionTokenIssuer();
    private final GhostHub hub;
    private final HostRoundEngine round;
    private final RoomHostHooks hooks;
    private final Map<HubConnection, Member> members = new LinkedHashMap<>();
    private final Map<HubConnection, String> expectedFingerprints = new LinkedHashMap<>();
    private String roomGameId;
    private int roomZone;
    private int roomAct;

    public RoomHost(RoomHostConfig config, PlayerIdentity hostIdentity,
                    LongSupplier wallClockMillis,
                    TrackValidationProfileSource profiles) {
        this(config, hostIdentity, wallClockMillis, profiles, RoomHostHooks.none());
    }

    public RoomHost(RoomHostConfig config, PlayerIdentity hostIdentity,
                    LongSupplier wallClockMillis,
                    TrackValidationProfileSource profiles,
                    RoomHostHooks hooks) {
        this.config = config;
        this.hostIdentity = hostIdentity;
        this.wallClockMillis = wallClockMillis;
        this.hooks = hooks == null ? RoomHostHooks.none() : hooks;
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
        this.round = new HostRoundEngine(wallClockMillis, this::broadcast);
        round.setVerifiedRoom(config.verified());
        round.setPendingExpiryListener((slot, attemptId) -> {
            if (this.hooks.verificationHooks() != null) {
                this.hooks.verificationHooks().onPendingExpired(
                        this.hooks.roomId(), slot, attemptId);
            }
        });
        round.setVoteTrackPool(config.voteTrackKeys(), this.hooks.knownVoteTrack());
        hub.setTrack(config.gameId(), config.zone(), config.act());
    }

    public void onConnected(HubConnection connection) {
        members.put(connection, new Member(connection,
                new HostHandshake(hostIdentity.fingerprint(),
                        config.requiredDeterminismFingerprint()),
                wallClockMillis.getAsLong()));
    }

    public void expectFingerprint(HubConnection connection, String fingerprint) {
        expectedFingerprints.put(connection, fingerprint);
    }

    public void onText(HubConnection connection, String text) {
        Member member = members.get(connection);
        if (member == null) {
            connection.close("not connected");
            return;
        }
        final ControlCodec.DecodedControl decoded;
        try {
            decoded = ControlCodec.decode(text);
        } catch (ProtocolViolationException e) {
            drop(member, "protocol violation: " + e.getMessage());
            return;
        }
        if (!member.admitted) {
            handleHandshake(member, decoded.message());
            return;
        }
        if (!member.token.equals(decoded.token())) {
            if (++member.tokenStrikes >= TOKEN_STRIKE_LIMIT) {
                drop(member, "session token violations");
            }
            return;
        }
        dispatch(member, decoded.message());
    }

    public void onBinary(HubConnection connection, byte[] data) {
        Member member = members.get(connection);
        if (member == null || !member.admitted) {
            connection.close("binary before admission");
            members.remove(connection);
            return;
        }
        hub.onBinary(member.slot, data);
    }

    public void onDisconnected(HubConnection connection) {
        expectedFingerprints.remove(connection);
        Member member = members.remove(connection);
        if (member != null && member.admitted) {
            removeAdmittedMember(member);
            broadcast(new ControlMessage.RoomState(players()));
        }
    }

    public void tick() {
        long now = wallClockMillis.getAsLong();
        List<Member> loiterers = new ArrayList<>();
        for (Member member : members.values()) {
            if (!member.admitted && now - member.connectedAt > ADMISSION_TIMEOUT_MILLIS) {
                loiterers.add(member);
            }
        }
        for (Member loiterer : loiterers) {
            drop(loiterer, "handshake timeout");
        }
        hub.tick();
        HostRoundEngine.Phase previousPhase = round.phase();
        round.onTick();
        if (previousPhase == HostRoundEngine.Phase.RUNNING
                && round.phase() == HostRoundEngine.Phase.ROUND_END
                && hooks.roundOutcomeListener() != null) {
            for (Member member : List.copyOf(members.values())) {
                if (member.admitted) {
                    hooks.roundOutcomeListener().onRoundComplete(member.fingerprint,
                            member.finishedThisRound && member.violationsThisRound == 0);
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
        return (int) members.values().stream().filter(member -> member.admitted).count();
    }

    /** Closes every transport currently owned by this ephemeral room. */
    public void close(String reason) {
        for (Member member : List.copyOf(members.values())) {
            drop(member, reason);
        }
        expectedFingerprints.clear();
    }

    public List<ControlMessage.PlayerInfo> players() {
        List<ControlMessage.PlayerInfo> result = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.admitted) {
                result.add(new ControlMessage.PlayerInfo(member.slot, member.fingerprint,
                        member.displayName, member.character,
                        hooks.isNewPlayer() != null
                                && hooks.isNewPlayer().test(member.fingerprint)));
            }
        }
        return List.copyOf(result);
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
                config.lockedCharacter(), config.maxPlayers(), config.verified());
    }

    public void sendToSlot(int slot, ControlMessage message) {
        Member member = memberForSlot(slot);
        if (member != null) {
            send(member, message);
        }
    }

    public String identityFingerprintForSlot(int slot) {
        Member member = memberForSlot(slot);
        return member == null ? null : member.fingerprint;
    }

    public String determinismFingerprint() {
        return config.requiredDeterminismFingerprint();
    }

    private void handleHandshake(Member member, ControlMessage message) {
        HostHandshake.Step step = switch (message) {
            case ControlMessage.Hello hello -> member.handshake.onHello(hello);
            case ControlMessage.AuthProof proof -> member.handshake.onAuthProof(proof);
            default -> new HostHandshake.Reject("unexpected message before admission");
        };
        switch (step) {
            case HostHandshake.SendWelcome welcome -> member.connection.sendText(
                    ControlCodec.encode(null, welcome.welcome()));
            case HostHandshake.Reject reject -> {
                member.connection.sendText(ControlCodec.encode(null,
                        new ControlMessage.JoinRejected(reject.reason())));
                drop(member, reject.reason());
            }
            case HostHandshake.Admit admit -> admitMember(member, admit);
        }
    }

    private void admitMember(Member member, HostHandshake.Admit admit) {
        String expected = expectedFingerprints.remove(member.connection);
        if (expected != null && !expected.equals(admit.fingerprint())) {
            member.connection.sendText(ControlCodec.encode(null,
                    new ControlMessage.JoinRejected("identity mismatch")));
            drop(member, "identity mismatch");
            return;
        }
        if (members.values().stream().anyMatch(existing -> existing.admitted
                && existing.fingerprint.equals(admit.fingerprint()))) {
            member.connection.sendText(ControlCodec.encode(null,
                    new ControlMessage.JoinRejected("identity already connected")));
            drop(member, "duplicate identity");
            return;
        }
        int slot = lowestFreeSlot();
        if (slot < 0) {
            member.connection.sendText(ControlCodec.encode(null,
                    new ControlMessage.JoinRejected("room full")));
            drop(member, "room full");
            return;
        }
        member.admitted = true;
        member.slot = slot;
        member.fingerprint = admit.fingerprint();
        member.displayName = admit.displayName().isEmpty()
                ? admit.fingerprint().substring(0, 8) : admit.displayName();
        member.character = "LOCKED".equals(config.characterPolicy())
                ? config.lockedCharacter() : "sonic";
        member.memberSinceMillis = wallClockMillis.getAsLong();
        member.token = tokens.issue();
        hub.addPlayer(slot, member.fingerprint, member.connection);
        member.connection.sendText(ControlCodec.encode(null,
                new ControlMessage.JoinAccepted(member.token, slot, descriptor(),
                        round.snapshot())));
        broadcast(new ControlMessage.RoomState(players()));
    }

    private void dispatch(Member member, ControlMessage message) {
        long now = wallClockMillis.getAsLong();
        switch (message) {
            case ControlMessage.Chat chat -> handleChat(member, chat, now);
            case ControlMessage.Ping ping -> member.connection.sendText(ControlCodec.encode(
                    null, new ControlMessage.Pong(ping.t0ClientMillis(), now)));
            case ControlMessage.SelectCharacter select -> {
                if (round.phase() == HostRoundEngine.Phase.LOBBY
                        && !"LOCKED".equals(config.characterPolicy())
                        && select.character() != null && !select.character().isBlank()) {
                    member.character = select.character();
                    broadcast(new ControlMessage.RoomState(players()));
                }
            }
            case ControlMessage.RoundConfigure configure -> {
                String owner = hooks.roundOwnerFingerprint() != null
                        ? hooks.roundOwnerFingerprint() : hostIdentity.fingerprint();
                if (member.fingerprint.equals(owner)) {
                    requestStartRound(configure.config());
                }
            }
            case ControlMessage.AttemptStart ignored -> { }
            case ControlMessage.AttemptReset ignored -> { }
            case ControlMessage.TrackVote vote ->
                    round.onTrackVote(member.slot, vote.trackKey());
            case ControlMessage.AttemptFinish finish -> {
                HostRoundEngine.FinishOutcome outcome = round.onAttemptFinish(
                        member.slot, member.displayName, member.character,
                        finish, hub.isAttemptFlagged(member.slot));
                member.finishedThisRound = round.standings().stream()
                        .anyMatch(row -> row.slot() == member.slot);
                if (outcome != null && outcome.outsideBroadcastCap()) {
                    send(member, new ControlMessage.RankUpdate(
                            outcome.rank(), finish.timeFrames()));
                }
                if (outcome != null && config.verified()
                        && hooks.verificationHooks() != null) {
                    hooks.verificationHooks().onFinishNeedingVerification(
                            hooks.roomId(), member.slot, member.fingerprint, finish,
                            roomGameId + ":" + roomZone + ":" + roomAct,
                            member.character, config.requiredDeterminismFingerprint(), false);
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
        if (hooks.chatGate() != null
                && !hooks.chatGate().mayChat(member.fingerprint, member.memberSinceMillis)) {
            return;
        }
        if (member.lastChatMillis != Long.MIN_VALUE
                && now - member.lastChatMillis < Protocol.CHAT_MIN_INTERVAL_MILLIS) {
            return;
        }
        member.lastChatMillis = now;
        String text = chat.text() == null ? "" : chat.text();
        if (text.isBlank()) {
            return;
        }
        if (text.length() > Protocol.MAX_CHAT_CHARS) {
            text = text.substring(0, Protocol.MAX_CHAT_CHARS);
        }
        broadcast(new ControlMessage.ChatBroadcast(member.slot, member.displayName, text));
    }

    private void broadcast(ControlMessage message) {
        String encoded = ControlCodec.encode(null, message);
        for (Member member : members.values()) {
            if (member.admitted) {
                member.connection.sendText(encoded);
            }
        }
    }

    private static void send(Member member, ControlMessage message) {
        member.connection.sendText(ControlCodec.encode(null, message));
    }

    private int lowestFreeSlot() {
        boolean[] used = new boolean[config.maxPlayers()];
        for (Member member : members.values()) {
            if (member.admitted && member.slot >= 0 && member.slot < used.length) {
                used[member.slot] = true;
            }
        }
        for (int slot = 0; slot < used.length; slot++) {
            if (!used[slot]) {
                return slot;
            }
        }
        return -1;
    }

    private void drop(Member member, String reason) {
        expectedFingerprints.remove(member.connection);
        members.remove(member.connection);
        if (member.admitted) {
            removeAdmittedMember(member);
        }
        member.connection.close(reason);
        if (member.admitted) {
            broadcast(new ControlMessage.RoomState(players()));
        }
    }

    private void removeAdmittedMember(Member member) {
        tokens.revoke(member.token);
        hub.removePlayer(member.slot);
        round.onPlayerLeft(member.slot);
    }

    private Member memberForSlot(int slot) {
        for (Member member : members.values()) {
            if (member.admitted && member.slot == slot) {
                return member;
            }
        }
        return null;
    }

    private boolean trackAllowed(ControlMessage.RoundConfig requested) {
        if (requested.gameId().equals(roomGameId)
                && requested.zone() == roomZone && requested.act() == roomAct) {
            return true;
        }
        ControlMessage.RoundConfig voted = round.votedNextConfig();
        return voted != null && voted.gameId().equals(requested.gameId())
                && voted.zone() == requested.zone() && voted.act() == requested.act();
    }
}
