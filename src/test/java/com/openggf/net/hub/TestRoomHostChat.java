package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ChatMode;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestRoomHostChat {
    private static final String FP = "test:fingerprint";
    private static final ControlJsonCodec CODEC = new ControlJsonCodec();
    private static final ControlMessage.RoundConfig ROUND =
            new ControlMessage.RoundConfig("s2", 1, 0, 60, "OPEN", null);
    @TempDir Path dir;
    private long now = 10_000;
    private boolean trusted = true;
    private PlayerIdentity owner;
    private RoomHost host;

    private record Member(FakeHubConnection connection, ControlMessage.JoinAccepted join) { }

    @BeforeEach
    void setUp() throws Exception {
        owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        host = host(ChatMode.FULL_CHAT, ChatMode.FULL_CHAT, null);
    }

    private RoomHost host(ChatMode requested, ChatMode limit, String delegatedOwner) {
        return new RoomHost(new RoomHostConfig("Room", "s2", 1, 0, "OPEN", null, 8, FP,
                List.of(), false, requested.name()), owner, () -> now, TrackValidationProfileSource.none(),
                new RoomHostHooks(false, (fingerprint, joinedAt) -> trusted, null, delegatedOwner,
                        null, null, null, null, null, limit));
    }

    @Test
    void joinPublishesEffectiveModeAndOnlyOwnerChangesItBetweenRounds() throws Exception {
        Member a = join("Owner", "owner");
        Member b = join("Guest", "guest");
        assertEquals("FULL_CHAT", a.join().room().chatMode());
        assertEquals(List.of(new ControlMessage.ChatModeState("FULL_CHAT")), all(b, ControlMessage.ChatModeState.class));
        send(b, new ControlMessage.SetChatMode("OFF"));
        assertEquals("FULL_CHAT", host.descriptor().chatMode());
        send(a, new ControlMessage.SetChatMode("QUICK_CHAT"));
        assertEquals(new ControlMessage.ChatModeState("QUICK_CHAT"), all(b, ControlMessage.ChatModeState.class).getLast());
        assertEquals("QUICK_CHAT", host.descriptor().chatMode());
        Member late = join("Late", "late");
        assertEquals("QUICK_CHAT", late.join().room().chatMode());
        assertEquals(List.of(new ControlMessage.ChatModeState("QUICK_CHAT")), all(late, ControlMessage.ChatModeState.class));
        assertTrue(host.requestStartRound(ROUND));
        send(a, new ControlMessage.SetChatMode("OFF"));
        assertEquals("QUICK_CHAT", host.descriptor().chatMode(), "countdown locks the mode");
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        host.tick();
        send(a, new ControlMessage.SetChatMode("OFF"));
        assertEquals("QUICK_CHAT", host.descriptor().chatMode(), "running locks the mode");
        now += 60_000 + HostRoundEngine.FINISH_GRACE_MILLIS + 1;
        host.tick();
        send(a, new ControlMessage.SetChatMode("OFF"));
        assertEquals("OFF", host.descriptor().chatMode(), "results permit an owner change");
        assertNull(b.connection().closedReason);
    }

    @Test
    void configuredOwnerRatherThanServerIdentityControlsRelayRoomMode() throws Exception {
        PlayerIdentity guestOwner = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        host = host(ChatMode.FULL_CHAT, ChatMode.FULL_CHAT, guestOwner.fingerprint());
        Member server = join("Server", "owner");
        Member roomOwner = join("Owner", "guest");
        send(server, new ControlMessage.SetChatMode("OFF"));
        assertEquals("FULL_CHAT", host.descriptor().chatMode());
        send(roomOwner, new ControlMessage.SetChatMode("QUICK_CHAT"));
        assertEquals("QUICK_CHAT", host.descriptor().chatMode());
    }

    @Test
    void publicCeilingRestrictsInitialAndLaterOwnerRequests() throws Exception {
        host = host(ChatMode.FULL_CHAT, ChatMode.QUICK_CHAT, null);
        Member a = join("Owner", "owner");
        assertEquals("QUICK_CHAT", a.join().room().chatMode());
        send(a, new ControlMessage.SetChatMode("FULL_CHAT"));
        assertEquals("QUICK_CHAT", host.descriptor().chatMode());
        send(a, new ControlMessage.Chat("blocked by ceiling"));
        send(a, new ControlMessage.QuickChat("GOOD_LUCK"));
        assertTrue(all(a, ControlMessage.ChatBroadcast.class).isEmpty());
        assertEquals(1, all(a, ControlMessage.QuickChatBroadcast.class).size());
        send(a, new ControlMessage.SetChatMode("OFF"));
        assertEquals("OFF", host.descriptor().chatMode());
        send(a, new ControlMessage.SetChatMode("FULL_CHAT"));
        assertEquals("QUICK_CHAT", host.descriptor().chatMode());
        host.close("done");
        host = host(ChatMode.FULL_CHAT, ChatMode.OFF, null);
        a = join("Owner", "owner");
        send(a, new ControlMessage.SetChatMode("FULL_CHAT"));
        assertEquals("OFF", host.descriptor().chatMode());
    }

    @Test
    void typedAndQuickChatShareCooldownAndTrustGateAndKeepHostIdentityAndContext() throws Exception {
        Member a = join("Owner", "owner");
        Member b = join("Witness", "guest");
        trusted = false;
        send(a, new ControlMessage.Chat("denied"));
        send(a, new ControlMessage.QuickChat("BRUTAL"));
        assertTrue(all(b, ControlMessage.ChatBroadcast.class).isEmpty());
        assertTrue(all(b, ControlMessage.QuickChatBroadcast.class).isEmpty());
        trusted = true;
        send(a, new ControlMessage.Chat("hello"));
        send(a, new ControlMessage.QuickChat("BRUTAL"));
        assertEquals(List.of(new ControlMessage.ChatBroadcast(a.join().playerSlot(), "Owner", "hello")),
                all(b, ControlMessage.ChatBroadcast.class));
        assertTrue(all(b, ControlMessage.QuickChatBroadcast.class).isEmpty());
        now += Protocol.CHAT_MIN_INTERVAL_MILLIS - 1;
        send(a, new ControlMessage.QuickChat("BRUTAL"));
        assertTrue(all(b, ControlMessage.QuickChatBroadcast.class).isEmpty());
        now++;
        send(a, new ControlMessage.QuickChat("THIS_IS_ZONE"));
        assertEquals(List.of(new ControlMessage.QuickChatBroadcast(
                a.join().playerSlot(), "Owner", "THIS_IS_ZONE", "s2", 1, 0)), all(b, ControlMessage.QuickChatBroadcast.class));
        send(a, new ControlMessage.Chat("still cooling down"));
        assertEquals(1, all(b, ControlMessage.ChatBroadcast.class).size());
        assertTrue(host.requestStartRound(ROUND));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        host.tick();
        send(a, new ControlMessage.QuickChat("THIS_IS_ZONE"));
        assertEquals(new ControlMessage.QuickChatBroadcast(a.join().playerSlot(), "Owner", "THIS_IS_ZONE", "s2", 1, 0),
                all(b, ControlMessage.QuickChatBroadcast.class).getLast(), "quick chat is available during a race");
        now += Protocol.CHAT_MIN_INTERVAL_MILLIS;
        send(a, new ControlMessage.Chat("during race"));
        assertEquals("during race", all(b, ControlMessage.ChatBroadcast.class).getLast().text());
    }

    @Test
    void quickChatContextFollowsTheAuthoritativeTrackAfterAVote() throws Exception {
        Member a = join("Owner", "owner");
        Member b = join("Witness", "guest");
        host.round().setVoteTrackPool(List.of("s2:2:1", "s2:3:0"));
        send(a, new ControlMessage.QuickChat("THIS_IS_ZONE"));
        assertEquals(new ControlMessage.QuickChatBroadcast(a.join().playerSlot(), "Owner", "THIS_IS_ZONE", "s2", 1, 0),
                all(b, ControlMessage.QuickChatBroadcast.class).getLast());
        assertTrue(host.requestStartRound(ROUND));
        now += HostRoundEngine.COUNTDOWN_MILLIS + 60_000 + HostRoundEngine.FINISH_GRACE_MILLIS + 1;
        host.tick();
        now += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        host.tick();
        assertEquals(HostRoundEngine.Phase.VOTE, host.round().phase());
        send(a, new ControlMessage.SetChatMode("QUICK_CHAT"));
        assertEquals("QUICK_CHAT", host.descriptor().chatMode(), "the owner may restrict chat during voting");
        send(a, new ControlMessage.TrackVote("s2:2:1"));
        now += HostRoundEngine.VOTE_WINDOW_MILLIS;
        host.tick();
        assertTrue(host.requestStartRound(host.round().votedNextConfig()));
        now += HostRoundEngine.COUNTDOWN_MILLIS;
        host.tick();
        send(a, new ControlMessage.QuickChat("THIS_IS_ZONE"));
        assertEquals(new ControlMessage.QuickChatBroadcast(a.join().playerSlot(), "Owner", "THIS_IS_ZONE", "s2", 2, 1),
                all(b, ControlMessage.QuickChatBroadcast.class).getLast());
        assertEquals("QUICK_CHAT", host.descriptor().chatMode(), "a track update preserves the effective mode");
    }

    @Test
    void quickAndOffModesCannotBeBypassedAndIgnoredMessagesDoNotBurnCooldown() throws Exception {
        Member a = join("Owner", "owner");
        send(a, new ControlMessage.SetChatMode("QUICK_CHAT"));
        send(a, new ControlMessage.Chat("blocked"));
        send(a, new ControlMessage.QuickChat("GOOD_LUCK"));
        assertTrue(all(a, ControlMessage.ChatBroadcast.class).isEmpty());
        assertEquals(1, all(a, ControlMessage.QuickChatBroadcast.class).size());
        now += Protocol.CHAT_MIN_INTERVAL_MILLIS;
        send(a, new ControlMessage.SetChatMode("OFF"));
        send(a, new ControlMessage.Chat("blocked"));
        send(a, new ControlMessage.QuickChat("BRUTAL"));
        send(a, new ControlMessage.SetChatMode("FULL_CHAT"));
        send(a, new ControlMessage.Chat("permitted"));
        assertEquals(List.of(new ControlMessage.ChatBroadcast(a.join().playerSlot(), "Owner", "permitted")),
                all(a, ControlMessage.ChatBroadcast.class));
        assertEquals(1, all(a, ControlMessage.QuickChatBroadcast.class).size());
    }

    @Test
    void forgedBroadcastsAndClientTemplateArgumentsAreRejected() throws Exception {
        Member a = join("Attacker", "guest");
        Member b = join("Witness", "witness");
        send(a, new ControlMessage.QuickChatBroadcast(b.join().playerSlot(), "Witness", "BRUTAL", "s3k", 9, 9));
        assertNotNull(a.connection().closedReason);
        assertTrue(all(b, ControlMessage.QuickChatBroadcast.class).isEmpty());
        a = join("Other", "other");
        String forged = CODEC.encode(a.join().sessionToken(), new ControlMessage.QuickChat("THIS_IS_ZONE"))
                .replace("\"phraseId\":\"THIS_IS_ZONE\"", "\"phraseId\":\"THIS_IS_ZONE\",\"zone\":9");
        host.onText(a.connection(), forged);
        assertNotNull(a.connection().closedReason);
        assertTrue(all(b, ControlMessage.QuickChatBroadcast.class).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mode", "phrase"})
    void malformedChatControlsRetireTheSenderBeforeBroadcast(String field) throws Exception {
        Member sender = join("Owner", "owner");
        Member witness = join("Witness", "witness");
        send(sender, field.equals("mode") ? new ControlMessage.SetChatMode("unknown")
                : new ControlMessage.QuickChat("UNKNOWN"));
        assertNotNull(sender.connection().closedReason);
        assertEquals("FULL_CHAT", host.descriptor().chatMode());
        assertTrue(all(witness, ControlMessage.ChatBroadcast.class).isEmpty());
        assertTrue(all(witness, ControlMessage.QuickChatBroadcast.class).isEmpty());
    }

    @Test
    void hostConfigRejectsUnknownModesAndLegacyConstructorsKeepDirectChatUnrestricted() {
        assertEquals("FULL_CHAT", new RoomHostConfig("R", "s1", 0, 0, "OPEN", null, 8, FP).chatMode());
        assertEquals(ChatMode.FULL_CHAT, RoomHostHooks.none().chatModeLimit());
        assertEquals(ChatMode.FULL_CHAT, new RoomHostHooks(false, null, null, null,
                null, null, null, null, null, null).chatModeLimit());
        assertThrows(IllegalArgumentException.class, () -> new RoomHostConfig("R", "s1", 0, 0,
                "OPEN", null, 8, FP, List.of(), false, null));
        assertThrows(IllegalArgumentException.class, () -> new RoomHostConfig("R", "s1", 0, 0,
                "OPEN", null, 8, FP, List.of(), false, "unknown"));
    }

    private Member join(String name, String identityDir) throws Exception {
        var identity = PlayerIdentity.loadOrCreate(dir.resolve(identityDir));
        var connection = new FakeHubConnection();
        var handshake = new ClientHandshake(identity, name, FP);
        host.onConnected(connection);
        host.onText(connection, CODEC.encode(null, handshake.hello()));
        var welcome = all(connection, ControlMessage.Welcome.class).getLast();
        host.onText(connection, CODEC.encode(null, handshake.onWelcome(welcome)));
        assertNull(connection.closedReason);
        return new Member(connection, all(connection, ControlMessage.JoinAccepted.class).getLast());
    }

    private void send(Member member, ControlMessage message) {
        host.onText(member.connection(), CODEC.encode(member.join().sessionToken(), message));
    }

    private static <T extends ControlMessage> List<T> all(Member member, Class<T> type) {
        return all(member.connection(), type);
    }

    private static <T extends ControlMessage> List<T> all(FakeHubConnection connection, Class<T> type) {
        return connection.text.stream().map(text -> CODEC.decode(text).message())
                .filter(type::isInstance).map(type::cast).toList();
    }
}
