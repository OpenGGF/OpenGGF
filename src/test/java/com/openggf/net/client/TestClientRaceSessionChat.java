package com.openggf.net.client;

import com.openggf.net.chat.QuickChatContent;
import com.openggf.net.protocol.ChatMode;
import com.openggf.net.protocol.QuickChatPhrase;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestClientRaceSessionChat {
    @Test
    void restrictingToQuickKeepsAcceptedHistoryAndBlocksOnlyNewTypedMessages() {
        var session = session("FULL_CHAT");
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "accepted before the change"));
        session.onControl(new ControlMessage.ChatModeState("QUICK_CHAT"));
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "blocked after the change"));
        session.onControl(quick("GOOD_LUCK"));
        assertEquals(List.of("A: accepted before the change", "A: Good luck!"), session.chatLines());
    }

    @Test
    void censorDefaultsOnAndPreferenceChangesRerenderOriginalNamesAndBodies() {
        var session = session("FULL_CHAT");
        assertFalse(session.chatDisabled());
        session.onControl(new ControlMessage.ChatBroadcast(0, "fuck", "shit"));
        assertEquals(List.of("***: ***"), session.chatLines());
        List<String> cached = session.chatLines();
        assertSame(cached, session.chatLines());
        session.setChatPreferences(true, false);
        assertSame(cached, session.chatLines(), "unchanged preferences do not rerender");
        session.setChatPreferences(false, false);
        assertEquals(List.of("fuck: shit"), session.chatLines());
        session.setChatPreferences(true, false);
        assertEquals(List.of("***: ***"), session.chatLines());
        assertEquals(List.of("***: ***"), cached, "published snapshots remain immutable");
        assertThrows(UnsupportedOperationException.class, () -> cached.add("forged"));
    }

    @Test
    void quickHistoryRetainsContextAndCachesRenderingUntilTheFormatterChanges() {
        var session = session("FULL_CHAT");
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<QuickChatContent> formatted = new AtomicReference<>();
        var first = new ControlMessage.QuickChatBroadcast(1, "fuck", "THIS_IS_ZONE", "s2", 1, 0);
        session.setQuickChatFormatter(line -> {
            calls.incrementAndGet();
            formatted.set(line);
            return line.gameId() + ":" + line.zone() + ":" + line.act() + " shit";
        });
        session.onControl(first);
        assertEquals(List.of("***: s2:1:0 ***"), session.chatLines());
        assertEquals(new QuickChatContent(QuickChatPhrase.THIS_IS_ZONE, "s2", 1, 0), formatted.get());
        int afterMessage = calls.get();
        List<String> cached = session.chatLines();
        session.setChatPreferences(true, false);
        assertSame(cached, session.chatLines());
        assertEquals(afterMessage, calls.get(), "unchanged preferences do not invoke the formatter");
        for (int frame = 0; frame < 20; frame++) session.chatLines();
        assertEquals(afterMessage, calls.get(), "reading presentation does no lexicon or formatter work");
        session.onControl(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s3k", 7, 1, 60, "OPEN", null), 100, 60_100));
        session.setQuickChatFormatter(line -> "Original " + line.gameId() + ":" + line.zone());
        assertEquals(List.of("***: Original s2:1"), session.chatLines(),
                "history keeps the broadcast context rather than the latest round");
    }

    @Test
    void disablingChatClearsHistoryDropsBothKindsAndKeepsNonChatStateWorking() {
        var session = session("FULL_CHAT");
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "old"));
        session.setChatPreferences(true, true);
        assertTrue(session.chatDisabled());
        assertTrue(session.chatLines().isEmpty());
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "hidden"));
        session.onControl(quick("GOOD_LUCK"));
        session.onControl(new ControlMessage.PlayerUpsert(
                new ControlMessage.PlayerInfo(2, "fp", "B", "sonic")));
        session.onControl(new ControlMessage.TrackVoteOffer(List.of("s2:1:0"), 1000));
        session.onControl(new ControlMessage.Pong(0, 250));
        session.onControl(new ControlMessage.Kick("Connection closed"));
        session.onControl(new ControlMessage.ChatModeState("QUICK_CHAT"));
        assertEquals(ChatMode.QUICK_CHAT, session.chatMode());
        assertEquals(1, session.players().size());
        assertEquals(List.of("s2:1:0"), session.voteOptions());
        assertEquals(250, session.clockOffsetMillis());
        assertEquals("Connection closed", session.kickReason());
        session.setChatPreferences(true, false);
        assertTrue(session.chatLines().isEmpty(), "re-enabling has no hidden backlog");
        session.onControl(quick("GOOD_LUCK"));
        assertEquals(List.of("A: Good luck!"), session.chatLines());
    }

    @Test
    void roomOffClearsHistoryAndModeChangesKeepTheDescriptorAuthoritative() {
        var session = session("QUICK_CHAT");
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "not allowed"));
        session.onControl(quick("GOOD_LUCK"));
        assertEquals(List.of("A: Good luck!"), session.chatLines());
        session.onControl(new ControlMessage.ChatModeState("OFF"));
        assertEquals(ChatMode.OFF, session.chatMode());
        assertEquals("OFF", session.room().chatMode());
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "hidden"));
        session.onControl(quick("BRUTAL"));
        assertTrue(session.chatLines().isEmpty());
        session.onControl(new ControlMessage.ChatModeState("FULL_CHAT"));
        assertTrue(session.chatLines().isEmpty());
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "new"));
        assertEquals(List.of("A: new"), session.chatLines());
        assertEquals(ChatMode.FULL_CHAT, session.chatMode());
        assertEquals("FULL_CHAT", session.room().chatMode());
    }

    @Test
    void mixedHistoryEvictsOriginalContentAndARejoinClearsIt() {
        var session = session("FULL_CHAT");
        session.setChatPreferences(false, false);
        for (int i = 0; i < 5; i++) {
            session.onControl(new ControlMessage.ChatBroadcast(0, "A", "message " + i));
            session.onControl(quick("BRUTAL"));
        }
        assertEquals(List.of("A: message 1", "A: Brutal!", "A: message 2", "A: Brutal!",
                "A: message 3", "A: Brutal!", "A: message 4", "A: Brutal!"), session.chatLines());
        List<String> beforeUnknown = session.chatLines();
        session.onControl(quick("UNKNOWN"));
        session.onControl(quick(null));
        assertSame(beforeUnknown, session.chatLines(), "unknown IDs neither render nor evict history");
        session.applyJoin(join("OFF"));
        assertTrue(session.chatLines().isEmpty());
        assertEquals(ChatMode.OFF, session.chatMode());
        session.onControl(new ControlMessage.ChatModeState("FULL_CHAT"));
        session.setQuickChatFormatter(line -> "replacement");
        assertTrue(session.chatLines().isEmpty(), "discarded originals cannot reappear on formatter changes");
    }

    @Test
    void chatRequiresAJoinedRoomAndPreJoinModeAnnouncementsCannotEnableIt() {
        var session = new ClientRaceSession(() -> 0);
        assertEquals(ChatMode.OFF, session.chatMode());
        session.onControl(new ControlMessage.ChatModeState("FULL_CHAT"));
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "unjoined"));
        session.onControl(quick("BRUTAL"));
        assertNull(session.room());
        assertEquals(ChatMode.OFF, session.chatMode());
        assertTrue(session.chatLines().isEmpty());
        session.applyJoin(join("FULL_CHAT"));
        session.onControl(quick("GOOD_LUCK"));
        assertEquals(ChatMode.FULL_CHAT, session.chatMode());
        assertEquals(List.of("A: Good luck!"), session.chatLines());
    }

    @Test
    void quickContentRejectsInvalidContextBeforeItCanEnterHistory() {
        assertThrows(IllegalArgumentException.class, () -> new QuickChatContent(null, "s2", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuickChatContent(QuickChatPhrase.BRUTAL, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuickChatContent(QuickChatPhrase.BRUTAL, " ", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuickChatContent(QuickChatPhrase.BRUTAL, "s2", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuickChatContent(QuickChatPhrase.BRUTAL, "s2", 0, -1));
        var session = session("FULL_CHAT");
        session.onControl(new ControlMessage.ChatBroadcast(0, "A", "retained"));
        List<String> cached = session.chatLines();
        for (var invalid : List.of(
                new ControlMessage.QuickChatBroadcast(0, "A", "BRUTAL", null, 1, 0),
                new ControlMessage.QuickChatBroadcast(0, "A", "BRUTAL", " ", 1, 0),
                new ControlMessage.QuickChatBroadcast(0, "A", "BRUTAL", "s2", -1, 0),
                new ControlMessage.QuickChatBroadcast(0, "A", "BRUTAL", "s2", 1, -1))) {
            session.onControl(invalid);
            assertSame(cached, session.chatLines(), "invalid context cannot render or evict history");
        }
        session.onControl(new ControlMessage.QuickChatBroadcast(0, "A", "BRUTAL", "s1", 0, 0));
        assertEquals(List.of("A: retained", "A: Brutal!"), session.chatLines());
    }

    @Test
    void repeatedDisableAndOffAreIdempotentAndNeverRestoreDroppedMessages() {
        var session = session("FULL_CHAT");
        AtomicInteger calls = new AtomicInteger();
        session.setQuickChatFormatter(content -> {
            calls.incrementAndGet();
            return "Brutal!";
        });
        session.onControl(quick("BRUTAL"));
        assertEquals(1, calls.get());
        session.setChatPreferences(true, true);
        List<String> cleared = session.chatLines();
        session.setChatPreferences(true, true);
        assertSame(cleared, session.chatLines());
        session.onControl(quick("BRUTAL"));
        session.onControl(new ControlMessage.ChatModeState("OFF"));
        var offRoom = session.room();
        session.onControl(new ControlMessage.ChatModeState("OFF"));
        assertSame(offRoom, session.room(), "unchanged mode preserves the descriptor snapshot");
        assertSame(cleared, session.chatLines());
        session.setChatPreferences(false, true);
        session.setChatPreferences(false, true);
        session.onControl(new ControlMessage.ChatModeState("FULL_CHAT"));
        session.onControl(quick("BRUTAL"));
        assertEquals(1, calls.get(), "disabled and Off input never reaches presentation");
        session.setChatPreferences(false, false);
        assertTrue(session.chatLines().isEmpty());
        session.onControl(quick("BRUTAL"));
        assertEquals(2, calls.get(), "only a new message after re-enabling is rendered");
        assertEquals(List.of("A: Brutal!"), session.chatLines());
    }

    private static ControlMessage.QuickChatBroadcast quick(String id) {
        return new ControlMessage.QuickChatBroadcast(0, "A", id, "s2", 1, 0);
    }

    private static ClientRaceSession session(String mode) {
        var result = new ClientRaceSession(() -> 0);
        result.applyJoin(join(mode));
        return result;
    }

    private static ControlMessage.JoinAccepted join(String mode) {
        return new ControlMessage.JoinAccepted("token", 0,
                new ControlMessage.RoomDescriptor("Room", "s2", 1, 0, "OPEN", null, 8, false, mode),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of()));
    }
}
