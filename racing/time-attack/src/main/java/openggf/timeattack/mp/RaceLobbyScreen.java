package openggf.timeattack.mp;

import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.PixelCanvas;
import openggf.timeattack.ui.MenuPage;

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

    /**
     * A chat message; the sender's name takes their colour when it names exactly one player.
     * {@code split} is where the text's second line starts (after a space), or -1 for one line;
     * {@link #layout} wraps only the newest shown message.
     */
    public record Chat(String sender, int senderColour, String text, int split) implements Row {
        public Chat(String sender, int senderColour, String text) { this(sender, senderColour, text, -1); }
        public int height() { return split < 0 ? NameFont.LINE_HEIGHT : 2 * NameFont.LINE_HEIGHT; }
        public String plain() { return sender.isEmpty() ? text : sender + ": " + text; }
    }

    /** A continuation line starts this far in from the sender, so it reads as the same message. */
    static final int CHAT_CONTINUATION_INDENT = 6;

    /** The sender as drawn: at most a third of the row, so the message keeps most of it. */
    static String chatSender(String sender, int rowWidth) {
        return sender.isEmpty() ? "" : NameFont.fit(sender, rowWidth / 3);
    }

    /** Pixels the first line's text may use after the sender and its colon. */
    static int chatFirstRoom(Chat chat, int rowWidth) {
        String sender = chatSender(chat.sender(), rowWidth);
        return rowWidth - (sender.isEmpty() ? 0 : NameFont.width(sender) + NameFont.width(": "));
    }

    /**
     * The newest message on two lines when it does not fit one: the first line ends at the last
     * space that fits (or, for one long word, its last fitting character), the rest continues
     * indented. A message longer than two lines ends with an ellipsis; Room details has it whole.
     */
    static Chat wrapNewest(Chat chat, int rowWidth) {
        int first = chatFirstRoom(chat, rowWidth);
        String text = chat.text();
        if (NameFont.width(text) <= first) return chat;
        int fit = 0;
        while (fit < text.length() && NameFont.width(text.substring(0, text.offsetByCodePoints(fit, 1))) <= first)
            fit = text.offsetByCodePoints(fit, 1);
        int space = text.lastIndexOf(' ', fit);
        int split = space > 0 ? space + 1 : Math.max(fit, Character.charCount(text.codePointAt(0)));
        return new Chat(chat.sender(), chat.senderColour(), text, split);
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

    /** A line of compact text, or of name-font text for sentences and anything a player typed. */
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
                         List<Card> cards, List<MenuPage.Hint> hints) {
        public Screen { cards = List.copyOf(cards); hints = List.copyOf(hints); }

        /** The page's text in drawing order (tests and logs). */
        public String plain() {
            StringBuilder out = new StringBuilder(headerName).append(" | ").append(phase);
            for (Row row : header.rows()) out.append(" | ").append(row.plain());
            for (Card card : cards) {
                out.append(" || ").append(card.title());
                if (!card.titleRight().isEmpty()) out.append(" ").append(card.titleRight());
                for (Row row : card.rows()) out.append(" | ").append(row.plain());
            }
            out.append(" ||");
            for (MenuPage.Hint hint : hints) out.append(' ').append(hint.key()).append(' ').append(hint.label());
            return out.toString();
        }
    }

    /**
     * What the lobby has to say, gathered by {@link RaceLobbyView}.
     *
     * @param players     every player, the room's owner first
     * @param secondary   the card under the players: the vote, the results or the chat
     * @param status      the right-hand card's notes above the menu (how many are not ready, a notice)
     */
    public record Model(String roomName, String track, String phase, int phaseColour, boolean verified,
                        List<Player> players, int maxPlayers, int playerPage, boolean playersFocused,
                        Secondary secondary, String menuTitle, String menuTitleRight, List<Note> status,
                        List<Action> actions, List<MenuPage.Hint> hints) {
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
                + (model.verified() ? "   VERIFIED TIMES" : "   NO SHARED TIMES"), HudTextLayout.MUTED, false));
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
            List<Row> secondRows = new ArrayList<>(second.rows());
            int newest = secondRows.size() - 1 - Math.max(0, second.newestFirstOffset());
            // The newest message may take a second line; older ones stay one line each.
            if (second.newestFirstOffset() >= 0 && newest >= 0 && secondRows.get(newest) instanceof Chat chat)
                secondRows.set(newest, wrapNewest(chat, leftWidth - 2 * INSET));
            List<Row> rows = fitRows(secondRows, room, second.newestFirstOffset());
            int cardHeight = TITLE_HEIGHT + 4 + rows.stream().mapToInt(Row::height).sum();
            cards.add(new Card(MARGIN, y, leftWidth, cardHeight, second.title(), second.titleRight(),
                    second.focused(), rows));
        }

        // Status notes wrap by measured width inside the menu card, within the room the actions
        // leave; a note that still does not fit ends its last line with an ellipsis. Sentences use
        // the name font (13 px lines), as the rows do.
        int noteWidth = rightWidth - 2 * INSET;
        int noteRoom = bottom - top - TITLE_HEIGHT - 4 - 3 - model.actions().stream().mapToInt(Row::height).sum();
        List<Row> menu = new ArrayList<>();
        for (Note note : model.status()) {
            int lineHeight = note.height();
            List<String> lines = note.names() ? wrapNames(note.text(), noteWidth) : wrapCompact(note.text(), noteWidth);
            int fit = Math.max(1, Math.min(lines.size(), noteRoom / lineHeight));
            for (int i = 0; i < fit; i++) {
                String line = lines.get(i);
                if (i == fit - 1 && fit < lines.size()) {
                    String rest = line + " " + lines.get(i + 1);
                    line = note.names() ? NameFont.fit(rest, noteWidth) : CompactFont.fit(rest, noteWidth, 1);
                }
                menu.add(new Note(line, note.colour(), note.names()));
            }
            noteRoom -= fit * lineHeight;
        }
        menu.addAll(model.actions());
        int menuHeight = Math.min(bottom - top, TITLE_HEIGHT + 4 + menu.stream().mapToInt(Row::height).sum()
                + (model.status().isEmpty() ? 0 : 3));
        cards.add(new Card(rightX, top, rightWidth, menuHeight, model.menuTitle(), model.menuTitleRight(),
                false, menu));
        return new Screen(header, model.roomName(), model.phase(), model.phaseColour(), cards, model.hints());
    }

    /** Name-font lines no wider than {@code width}, split at spaces (a single long word is fitted). */
    static List<String> wrapNames(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String line : com.openggf.mods.ui.TextLayout.wrap(text, Math.max(1, width), NameFont::width)) {
            lines.add(NameFont.fit(line, width));
        }
        return lines;
    }

    /** Compact-font lines no wider than {@code width}, split at spaces (a single long word is fitted). */
    static List<String> wrapCompact(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String line : com.openggf.mods.ui.TextLayout.wrap(text, Math.max(1, width), t -> CompactFont.width(t, 1))) {
            lines.add(CompactFont.fit(line, width, 1));
        }
        return lines;
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

        // Key caps on a plate along the bottom edge; arrow hints give way first at narrow widths.
        List<MenuPage.Hint> hints = MenuPage.fitHints(screen.hints(), canvas.width() - 2 * MARGIN - 2 * INSET);
        int hintWidth = MenuPage.hintsWidth(hints) + 2 * INSET;
        int hintX = (canvas.width() - hintWidth) / 2;
        int hintY = canvas.height() - MARGIN - HINT_HEIGHT - 2;
        canvas.fill(hintX, hintY, hintWidth, HINT_HEIGHT + 2, CARD_FILL);
        MenuPage.hints(canvas, hintX + INSET, hintY + 1, hints, HudTextLayout.WHITE);
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
                // Muted rather than disabled grey: the tag stays above 4.5:1 on the card and on the
                // lighter band behind your own row.
                CompactFont.draw(canvas, player.tag(), right - tagWidth, compactY, 1, HudTextLayout.MUTED);
                if (!player.badge().isEmpty()) {
                    CompactFont.draw(canvas, player.badge(), right - tagWidth - badgeWidth, compactY, 1,
                            "HOST".equals(player.badge()) ? HudTextLayout.GOLD : HudTextLayout.ACCENT);
                }
            }
            case Chat chat -> {
                String sender = chatSender(chat.sender(), right - left);
                int x = left;
                if (!sender.isEmpty()) {
                    NameFont.draw(canvas, sender, left, y, chat.senderColour());
                    x += NameFont.width(sender);
                    NameFont.draw(canvas, ":", x, y, HudTextLayout.WHITE);
                    x += NameFont.width(": ");
                }
                String first = chat.split() < 0 ? chat.text() : chat.text().substring(0, chat.split()).stripTrailing();
                NameFont.draw(canvas, NameFont.fit(first, right - x), x, y, HudTextLayout.WHITE);
                if (chat.split() >= 0) {
                    int indent = left + CHAT_CONTINUATION_INDENT;
                    NameFont.draw(canvas, NameFont.fit(chat.text().substring(chat.split()), right - indent), indent,
                            y + NameFont.LINE_HEIGHT, HudTextLayout.WHITE);
                }
            }
            case Action action -> {
                if (action.focused()) {
                    canvas.fill(card.x() + 3, y, card.width() - 5, ACTION_HEIGHT - 1, FOCUS_FILL);
                    CompactFont.draw(canvas, ">", left, y + 2, 1, HudTextLayout.GOLD);
                }
                // Actions are menu rows: sentence case in the light row face, as on every Time Attack
                // page; card titles and status tags keep the HUD's compact capitals.
                NameFont.draw(canvas, NameFont.fit(action.label(), right - left - 8), left + 8, y - 1,
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
