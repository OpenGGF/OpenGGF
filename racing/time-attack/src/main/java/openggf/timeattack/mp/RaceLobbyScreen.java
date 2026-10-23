package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.PixelCanvas;

import java.util.ArrayList;
import java.util.List;

/**
 * The race lobby's main page as cards over the lobby level, in the in-race HUD's panel style
 * ({@link HudTextLayout}): a header with the room and what happens next, the players with their
 * ready state, the chat (or the vote, or the last round's results) and the menu. Cards size to
 * their content, so the level stays in view wherever they leave room. {@link #layout} is pure and
 * measured; {@link #draw} paints it in the compact and name fonts.
 */
public final class RaceLobbyScreen {
    static final int MARGIN = 4;
    static final int GAP = 4;
    static final int INSET = 6;
    static final int TITLE_HEIGHT = 11;
    static final int HEADER_HEIGHT = 4 + NameFont.LINE_HEIGHT + 9 + 2;
    static final int PLAYER_HEIGHT = NameFont.LINE_HEIGHT + 1;
    static final int ACTION_HEIGHT = 12;
    static final int HINT_HEIGHT = 11;
    static final int DISABLED = 0xFF66778A;
    static final int READY = 0xFF80FF90;
    static final int FOCUS_FILL = 0x5067DDF5;
    static final int YOU_FILL = 0x26FFFFFF;
    static final int BAR = 0x70FFDD67;
    /**
     * Denser than the in-race HUD panel ({@link HudTextLayout#FILL}): the lobby's cards hold more
     * text, and ghost nameplates in the level behind must not show through it.
     */
    static final int CARD_FILL = 0xE6122032;

    private RaceLobbyScreen() {
    }

    /** One player row: the name in its owner's colour, a ready box, a HOST/NEW badge and the character. */
    public record Player(String name, int colour, boolean ready, boolean you, String badge, String tag)
            implements Row {
        public int height() { return PLAYER_HEIGHT; }
        public String plain() { return (ready ? "[x] " : "[ ] ") + name + " " + suffix(); }
        String suffix() { return badge.isEmpty() ? tag : badge + " " + tag; }
    }

    /** A chat message; the sender's name takes their colour when it names exactly one player. */
    public record Chat(String sender, int senderColour, String text) implements Row {
        public int height() { return NameFont.LINE_HEIGHT; }
        public String plain() { return sender.isEmpty() ? text : sender + ": " + text; }
    }

    /** A menu entry. A stepper shows its value between arrows. */
    public record Action(String label, boolean focused, boolean enabled) implements Row {
        public int height() { return ACTION_HEIGHT; }
        public String plain() { return (focused ? "> " : "  ") + label; }
    }

    /** A numbered vote option with its share of the votes. */
    public record Choice(int number, String label, int votes, int total) implements Row {
        public int height() { return ACTION_HEIGHT; }
        public String plain() { return number + " " + label + " " + votes; }
    }

    /** A line of compact text, or of name-font text for anything a player typed. */
    public record Note(String text, int colour, boolean names) implements Row {
        public int height() { return names ? NameFont.LINE_HEIGHT : 9; }
        public String plain() { return text; }
    }

    public sealed interface Row permits Player, Chat, Action, Choice, Note {
        int height();
        String plain();
    }

    /** A translucent card with an accent bar, a compact title (and right-hand title) and its rows. */
    public record Card(int x, int y, int width, int height, String title, String titleRight,
                       boolean focused, List<Row> rows) {
        public Card { rows = List.copyOf(rows); }
    }

    /** Everything the lobby shows, as cards plus the key hint along the bottom edge. */
    public record Screen(Card header, String headerName, String phase, int phaseColour,
                         List<Card> cards, String hint) {
        public Screen { cards = List.copyOf(cards); }

        /** The page's text in drawing order (tests and logs). */
        public String plain() {
            StringBuilder out = new StringBuilder(headerName).append(" | ").append(phase);
            for (Row row : header.rows()) out.append(" | ").append(row.plain());
            for (Card card : cards) {
                out.append(" || ").append(card.title());
                if (!card.titleRight().isEmpty()) out.append(" ").append(card.titleRight());
                for (Row row : card.rows()) out.append(" | ").append(row.plain());
            }
            return out.append(" || ").append(hint).toString();
        }
    }

    /**
     * What the lobby has to say, gathered by {@link RaceLobbyView}.
     *
     * @param players     every player, the room's owner first
     * @param secondary   the card under the players: the vote, the results or the chat
     * @param status      the right-hand card's notes above the menu (who is not ready, a notice)
     */
    public record Model(String roomName, String track, String phase, int phaseColour, boolean verified,
                        List<Player> players, int maxPlayers, int playerPage, boolean playersFocused,
                        Secondary secondary, String menuTitle, String menuTitleRight, List<Note> status,
                        List<Action> actions, String hint) {
    }

    /** The card under the players: its title, right-hand title, rows and whether it holds the focus. */
    public record Secondary(String title, String titleRight, List<Row> rows, boolean focused,
                            int newestFirstOffset) {
    }

    /** Players per page at this height: as many as fit beside a secondary card of at least four rows. */
    public static int playersPerPage(int height, boolean secondary) {
        int column = columnBottom(height) - columnTop();
        int room = secondary ? column - (TITLE_HEIGHT + 4 + 2 * NameFont.LINE_HEIGHT + GAP) : column;
        return Math.max(1, (room - TITLE_HEIGHT - 4) / PLAYER_HEIGHT);
    }

    static int columnTop() {
        return MARGIN + HEADER_HEIGHT + GAP;
    }

    static int columnBottom(int height) {
        return height - MARGIN - HINT_HEIGHT - 2;
    }

    public static Screen layout(Model model, int width, int height) {
        int inner = width - 2 * MARGIN;
        List<Row> headerRows = List.of(new Note(model.track()
                + (model.verified() ? "   VERIFIED TIMES" : "   UNVERIFIED TIMES"), HudTextLayout.MUTED, false));
        Card header = new Card(MARGIN, MARGIN, inner, HEADER_HEIGHT, "", "", false, headerRows);

        int top = columnTop();
        int bottom = columnBottom(height);
        int leftWidth = (inner - GAP) * 54 / 100;
        int rightX = MARGIN + leftWidth + GAP;
        int rightWidth = width - MARGIN - rightX;
        List<Card> cards = new ArrayList<>();

        boolean secondary = model.secondary() != null;
        int perPage = playersPerPage(height, secondary);
        int pages = Math.max(1, (model.players().size() + perPage - 1) / perPage);
        int page = Math.clamp(model.playerPage(), 0, pages - 1);
        List<Row> shown = new ArrayList<>(model.players().subList(Math.min(model.players().size(), page * perPage),
                Math.min(model.players().size(), (page + 1) * perPage)));
        String count = model.players().size() + (model.maxPlayers() > 0 ? "/" + model.maxPlayers() : "");
        String pageLabel = pages > 1 ? "< " + (page + 1) + "/" + pages + " >" : count;
        int playersHeight = TITLE_HEIGHT + 4 + Math.max(1, shown.size()) * PLAYER_HEIGHT;
        cards.add(new Card(MARGIN, top, leftWidth, playersHeight, "PLAYERS", pageLabel,
                model.playersFocused(), shown));

        if (secondary) {
            Secondary second = model.secondary();
            int y = top + playersHeight + GAP;
            int room = bottom - y - TITLE_HEIGHT - 4;
            List<Row> rows = fitRows(second.rows(), room, second.newestFirstOffset());
            int cardHeight = TITLE_HEIGHT + 4 + rows.stream().mapToInt(Row::height).sum();
            cards.add(new Card(MARGIN, y, leftWidth, cardHeight, second.title(), second.titleRight(),
                    second.focused(), rows));
        }

        List<Row> menu = new ArrayList<>(model.status());
        menu.addAll(model.actions());
        int menuHeight = Math.min(bottom - top, TITLE_HEIGHT + 4 + menu.stream().mapToInt(Row::height).sum()
                + (model.status().isEmpty() ? 0 : 3));
        cards.add(new Card(rightX, top, rightWidth, menuHeight, model.menuTitle(), model.menuTitleRight(),
                false, menu));
        return new Screen(header, model.roomName(), model.phase(), model.phaseColour(), cards, model.hint());
    }

    /**
     * The rows that fit in {@code room} pixels. Chat keeps its newest lines (skipping
     * {@code newestFirstOffset} of them when paging back); other cards keep their first rows.
     */
    private static List<Row> fitRows(List<Row> rows, int room, int newestFirstOffset) {
        if (newestFirstOffset < 0) {
            List<Row> kept = new ArrayList<>();
            int used = 0;
            for (Row row : rows) {
                if (used + row.height() > room) break;
                kept.add(row);
                used += row.height();
            }
            return kept;
        }
        int end = Math.max(0, rows.size() - newestFirstOffset);
        List<Row> kept = new ArrayList<>();
        int used = 0;
        for (int i = end - 1; i >= 0; i--) {
            Row row = rows.get(i);
            if (used + row.height() > room) break;
            kept.addFirst(row);
            used += row.height();
        }
        return kept;
    }

    public static void draw(PixelCanvas canvas, Screen screen) {
        Card header = screen.header();
        panel(canvas, header, false);
        int phaseWidth = CompactFont.width(screen.phase(), 1);
        int nameRoom = header.width() - 2 * INSET - phaseWidth - 8;
        NameFont.draw(canvas, NameFont.fit(screen.headerName(), nameRoom), header.x() + INSET, header.y() + 2,
                HudTextLayout.WHITE);
        CompactFont.draw(canvas, screen.phase(), header.x() + header.width() - INSET - phaseWidth,
                header.y() + 5, 1, screen.phaseColour());
        Note track = (Note) header.rows().getFirst();
        CompactFont.draw(canvas, CompactFont.fit(track.text(), header.width() - 2 * INSET, 1),
                header.x() + INSET, header.y() + 4 + NameFont.LINE_HEIGHT, 1, track.colour());

        for (Card card : screen.cards()) {
            panel(canvas, card, card.focused());
            int titleColour = card.focused() ? HudTextLayout.GOLD : HudTextLayout.ACCENT;
            CompactFont.draw(canvas, card.title(), card.x() + INSET, card.y() + 3, 1, titleColour);
            if (!card.titleRight().isEmpty()) {
                CompactFont.draw(canvas, card.titleRight(), card.x() + card.width() - INSET
                        - CompactFont.width(card.titleRight(), 1), card.y() + 3, 1,
                        card.focused() ? HudTextLayout.GOLD : HudTextLayout.MUTED);
            }
            int y = card.y() + TITLE_HEIGHT + 2;
            boolean afterNotes = false;
            for (Row row : card.rows()) {
                if (row instanceof Action && afterNotes) {
                    y += 3;
                    afterNotes = false;
                }
                if (row instanceof Note) afterNotes = true;
                drawRow(canvas, card, row, y);
                y += row.height();
            }
        }

        String hint = CompactFont.fit(screen.hint(), canvas.width() - 2 * MARGIN - 2 * INSET, 1);
        int hintWidth = CompactFont.width(hint, 1) + 2 * INSET;
        int hintX = (canvas.width() - hintWidth) / 2;
        int hintY = canvas.height() - MARGIN - HINT_HEIGHT;
        canvas.fill(hintX, hintY, hintWidth, HINT_HEIGHT, CARD_FILL);
        CompactFont.draw(canvas, hint, hintX + INSET, hintY + 2, 1, HudTextLayout.MUTED);
    }

    private static void panel(PixelCanvas canvas, Card card, boolean focused) {
        canvas.fill(card.x(), card.y(), card.width(), card.height(), CARD_FILL);
        canvas.fill(card.x(), card.y(), 2, card.height(), focused ? HudTextLayout.GOLD : HudTextLayout.ACCENT);
    }

    private static void drawRow(PixelCanvas canvas, Card card, Row row, int y) {
        int left = card.x() + INSET;
        int right = card.x() + card.width() - INSET;
        switch (row) {
            case Player player -> {
                if (player.you()) canvas.fill(card.x() + 3, y, card.width() - 5, PLAYER_HEIGHT - 1, YOU_FILL);
                int boxY = y + 4;
                if (player.ready()) {
                    canvas.fill(left, boxY, 5, 5, READY);
                } else {
                    canvas.fill(left, boxY, 5, 1, DISABLED);
                    canvas.fill(left, boxY + 4, 5, 1, DISABLED);
                    canvas.fill(left, boxY, 1, 5, DISABLED);
                    canvas.fill(left + 4, boxY, 1, 5, DISABLED);
                }
                int tagWidth = CompactFont.width(player.tag(), 1);
                int badgeWidth = player.badge().isEmpty() ? 0 : CompactFont.width(player.badge(), 1) + 4;
                int nameX = left + 9;
                NameFont.draw(canvas, NameFont.fit(player.name(), right - nameX - tagWidth - badgeWidth - 4),
                        nameX, y, player.colour());
                int compactY = y + NameFont.BASELINE - 7;
                CompactFont.draw(canvas, player.tag(), right - tagWidth, compactY, 1, DISABLED);
                if (!player.badge().isEmpty()) {
                    CompactFont.draw(canvas, player.badge(), right - tagWidth - badgeWidth, compactY, 1,
                            "HOST".equals(player.badge()) ? HudTextLayout.GOLD : HudTextLayout.ACCENT);
                }
            }
            case Chat chat -> {
                if (chat.sender().isEmpty()) {
                    NameFont.draw(canvas, NameFont.fit(chat.text(), right - left), left, y, HudTextLayout.WHITE);
                    return;
                }
                String sender = NameFont.fit(chat.sender(), (right - left) / 2);
                NameFont.draw(canvas, sender, left, y, chat.senderColour());
                int x = left + NameFont.width(sender);
                NameFont.draw(canvas, NameFont.fit(": " + chat.text(), right - x), x, y, HudTextLayout.WHITE);
            }
            case Action action -> {
                if (action.focused()) {
                    canvas.fill(card.x() + 3, y, card.width() - 5, ACTION_HEIGHT - 1, FOCUS_FILL);
                    CompactFont.draw(canvas, ">", left, y + 2, 1, HudTextLayout.GOLD);
                }
                CompactFont.draw(canvas, CompactFont.fit(action.label(), right - left - 8, 1), left + 8, y + 2, 1,
                        action.enabled() ? HudTextLayout.WHITE : DISABLED);
            }
            case Choice choice -> {
                canvas.fill(left, y + 1, 9, 9, HudTextLayout.GOLD);
                CompactFont.draw(canvas, Integer.toString(choice.number()), left + 2, y + 2, 1, 0xFF122032);
                String votes = Integer.toString(choice.votes());
                int votesWidth = CompactFont.width(votes, 1);
                int labelX = left + 13;
                CompactFont.draw(canvas, CompactFont.fit(choice.label(), right - labelX - votesWidth - 6, 1),
                        labelX, y + 2, 1, HudTextLayout.WHITE);
                CompactFont.draw(canvas, votes, right - votesWidth, y + 2, 1, HudTextLayout.GOLD);
                if (choice.total() > 0 && choice.votes() > 0) {
                    int span = right - labelX;
                    canvas.fill(labelX, y + 10, Math.max(1, span * choice.votes() / choice.total()), 1, BAR);
                }
            }
            case Note note -> {
                if (note.names()) {
                    NameFont.draw(canvas, NameFont.fit(note.text(), right - left), left, y, note.colour());
                } else {
                    CompactFont.draw(canvas, CompactFont.fit(note.text(), right - left, 1), left, y + 1, 1,
                            note.colour());
                }
            }
        }
    }
}
