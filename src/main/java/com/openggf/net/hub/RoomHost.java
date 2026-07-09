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
        long lastChatMillis = Long.MIN_VALUE;
        int tokenStrikes;

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
    private final Map<HubConnection, Member> members = new LinkedHashMap<>();

    public RoomHost(RoomHostConfig config, PlayerIdentity hostIdentity,
                    LongSupplier wallClockMillis,
                    TrackValidationProfileSource profiles) {
        this.config = config;
        this.hostIdentity = hostIdentity;
        this.wallClockMillis = wallClockMillis;
        this.hub = new GhostHub(wallClockMillis, profiles,
                (slot, fingerprint, kind, detail) -> System.getLogger(
                        RoomHost.class.getName()).log(System.Logger.Level.WARNING,
                        "ghost violation slot=" + slot + " fp=" + fingerprint + " "
                                + kind + ": " + detail));
        this.round = new HostRoundEngine(wallClockMillis, this::broadcast);
        hub.setTrack(config.gameId(), config.zone(), config.act());
    }

    public void onConnected(HubConnection connection) {
        members.put(connection, new Member(connection,
                new HostHandshake(hostIdentity.fingerprint(),
                        config.requiredDeterminismFingerprint()),
                wallClockMillis.getAsLong()));
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
        round.onTick();
    }

    public boolean requestStartRound(ControlMessage.RoundConfig roundConfig) {
        if (roundConfig == null || !config.gameId().equals(roundConfig.gameId())
                || config.zone() != roundConfig.zone() || config.act() != roundConfig.act()) {
            return false;
        }
        if (!round.startRound(roundConfig)) {
            return false;
        }
        hub.setTrack(roundConfig.gameId(), roundConfig.zone(), roundConfig.act());
        return true;
    }

    public void applyTrackValidationProfile(TrackValidationProfile profile) {
        hub.applyProfile(profile);
    }

    public int playerCount() {
        return (int) members.values().stream().filter(member -> member.admitted).count();
    }

    public List<ControlMessage.PlayerInfo> players() {
        List<ControlMessage.PlayerInfo> result = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.admitted) {
                result.add(new ControlMessage.PlayerInfo(member.slot, member.fingerprint,
                        member.displayName, member.character));
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
        return new ControlMessage.RoomDescriptor(config.roomName(), config.gameId(),
                config.zone(), config.act(), config.characterPolicy(),
                config.lockedCharacter(), config.maxPlayers(), false);
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
                if (member.fingerprint.equals(hostIdentity.fingerprint())) {
                    requestStartRound(configure.config());
                }
            }
            case ControlMessage.AttemptStart ignored -> { }
            case ControlMessage.AttemptReset ignored -> { }
            case ControlMessage.AttemptFinish finish -> round.onAttemptFinish(member.slot,
                    member.displayName, member.character, finish,
                    hub.isAttemptFlagged(member.slot));
            default -> drop(member,
                    "illegal client message " + message.getClass().getSimpleName());
        }
    }

    private void handleChat(Member member, ControlMessage.Chat chat, long now) {
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
}
