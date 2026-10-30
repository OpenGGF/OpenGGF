package openggf.timeattack.mp;

import com.openggf.mods.ui.NameFont;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lobby's chat card: senders take at most a third of the row, the newest message wraps onto
 * one indented continuation line when it does not fit, older messages stay one line, and the card
 * grows by one line only when the newest message wraps.
 */
class TestRaceLobbyChat {
    private static final String LONG_NAME = "Captain Extraordinarily Long Racer Name";

    @Test
    void sendersTakeAtMostAThirdOfTheRow() {
        for (int width : new int[] {320, 400}) {
            var screen = RaceLobbyScreen.layout(model(2, List.of(chat(LONG_NAME, "hi")), 0), width, 224);
            var card = chatCard(screen);
            int row = card.width() - 2 * RaceLobbyScreen.INSET;
            String shown = RaceLobbyScreen.chatSender(LONG_NAME, row);
            assertTrue(NameFont.width(shown) <= row / 3, shown + " at " + width);
            assertTrue(shown.endsWith("\u2026"), "a cut name says so");
        }
    }

    @Test
    void theNewestMessageReadsWholeOnTwoLinesAndOlderOnesStayOneLine() {
        String older = "an older message that is also much too long to fit on one line of the card";
        String newest = "good luck everyone, see you at the finish line";
        for (int width : new int[] {320, 400}) {
            var screen = RaceLobbyScreen.layout(model(2, List.of(chat("Sid", older), chat("Tails", newest)), 0), width, 224);
            var card = chatCard(screen);
            int row = card.width() - 2 * RaceLobbyScreen.INSET;
            var rows = card.rows();
            var last = (RaceLobbyScreen.Chat) rows.getLast();
            assertEquals(newest, last.text());
            assertTrue(last.split() > 0, "the newest message wraps at " + width);
            String first = newest.substring(0, last.split()).stripTrailing();
            String second = newest.substring(last.split());
            assertEquals(newest, first + " " + second, "the break is at a space, keeping every character");
            assertTrue(NameFont.width(first) <= RaceLobbyScreen.chatFirstRoom(last, row), first);
            assertTrue(NameFont.width(second) <= row - RaceLobbyScreen.CHAT_CONTINUATION_INDENT,
                    "a message of at most two lines is whole: " + second);
            for (var older2 : rows.subList(0, rows.size() - 1))
                assertEquals(-1, ((RaceLobbyScreen.Chat) older2).split(), "older messages stay one line");
            // One line more than the same card without the wrap.
            var oneLine = RaceLobbyScreen.layout(model(2, List.of(chat("Sid", older), chat("Tails", "hi")), 0), width, 224);
            assertEquals(chatCard(oneLine).height() + NameFont.LINE_HEIGHT, card.height(),
                    "the card grows by exactly one line when the newest message wraps");
        }
    }

    @Test
    void aNewestMessageLongerThanTwoLinesEndsWithAnEllipsis() {
        String text = "this message is far too long for the chat card even on two whole lines, so it says so at the end";
        var screen = RaceLobbyScreen.layout(model(2, List.of(chat("Sid", text)), 0), 320, 224);
        var card = chatCard(screen);
        var last = (RaceLobbyScreen.Chat) card.rows().getLast();
        int row = card.width() - 2 * RaceLobbyScreen.INSET;
        String rest = text.substring(last.split());
        assertTrue(NameFont.width(rest) > row - RaceLobbyScreen.CHAT_CONTINUATION_INDENT, "fixture needs a third line");
        assertTrue(NameFont.fit(rest, row - RaceLobbyScreen.CHAT_CONTINUATION_INDENT).endsWith("\u2026"),
                "the drawn continuation says it was cut; Room details has the whole message");
    }

    @Test
    void aShortNewestMessageStaysOnOneLine() {
        var screen = RaceLobbyScreen.layout(model(2, List.of(chat("Sid", "ready?")), 0), 320, 224);
        assertEquals(-1, ((RaceLobbyScreen.Chat) chatCard(screen).rows().getLast()).split());
    }

    @Test
    void unverifiedRoomsSaySharedStandingsAreUnavailable() {
        var base = model(2, List.of(chat("Sid", "ready?")), 0);
        var unverified = new RaceLobbyScreen.Model(base.roomName(), base.track(), base.phase(),
                base.phaseColour(), false, base.players(), base.maxPlayers(), base.playerPage(),
                base.playersFocused(), base.secondary(), base.menuTitle(), base.menuTitleRight(),
                base.status(), base.actions(), base.hints());

        var screen = RaceLobbyScreen.layout(unverified, 320, 224);

        assertTrue(screen.plain().contains("NO SHARED TIMES"), screen.plain());
    }

    @Test
    void aCrowdedRoomKeepsTheWrappedNewestAndTheMessageBeforeIt() {
        List<RaceLobbyScreen.Row> history = new ArrayList<>();
        for (int i = 0; i < 6; i++) history.add(chat("Player " + i, "message " + i));
        history.add(chat("Knuckles", "this newest message is long enough that it has to take two lines here"));
        for (int width : new int[] {320, 400}) {
            var screen = RaceLobbyScreen.layout(model(8, history, 0), width, 224);
            var card = chatCard(screen);
            var rows = card.rows();
            assertTrue(((RaceLobbyScreen.Chat) rows.getLast()).split() > 0, "newest wraps at " + width);
            assertTrue(rows.size() >= 2, "the message before the newest is still shown at " + width + ": " + rows.size());
            assertTrue(card.y() + card.height() <= RaceLobbyScreen.columnBottom(224), "the card fits at " + width);
        }
    }

    @Test
    void pagingBackShowsOlderMessagesWithTheNewestShownWrapped() {
        List<RaceLobbyScreen.Row> history = List.of(chat("Sid", "first"),
                chat("Tails", "the second message is long enough to need its own second line here at all"),
                chat("Knuckles", "third"));
        var screen = RaceLobbyScreen.layout(model(2, history, 1), 320, 224);
        var rows = chatCard(screen).rows();
        var newestShown = (RaceLobbyScreen.Chat) rows.getLast();
        assertEquals("Tails", newestShown.sender(), "one page back, Tails's message is the newest shown");
        assertTrue(newestShown.split() > 0);
    }

    private static RaceLobbyScreen.Chat chat(String sender, String text) {
        return new RaceLobbyScreen.Chat(sender, 0xFFFFFFFF, text);
    }

    private static RaceLobbyScreen.Card chatCard(RaceLobbyScreen.Screen screen) {
        return screen.cards().stream().filter(card -> card.title().equals("CHAT")).findFirst().orElseThrow();
    }

    private static RaceLobbyScreen.Model model(int players, List<RaceLobbyScreen.Row> chat, int offset) {
        List<RaceLobbyScreen.Player> list = new ArrayList<>();
        for (int i = 0; i < players; i++)
            list.add(new RaceLobbyScreen.Player("Player " + i, 0xFFFFFFFF, false, i == 0, i == 0 ? "HOST" : "", "SONIC"));
        return new RaceLobbyScreen.Model("Room", "S2 EMERALD HILL 1", "LOBBY", 0xFFFFFFFF, true, list, 8, 0, false,
                new RaceLobbyScreen.Secondary("CHAT", "", chat, false, offset), "ROOM", "",
                List.of(), List.of(new RaceLobbyScreen.Action("Ready up", true, true)), List.of());
    }
}
