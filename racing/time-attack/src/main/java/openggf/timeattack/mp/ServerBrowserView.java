package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.CompactFont;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.ChatMode;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.FullTextReader;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The master-server room browser, drawn as a Time Attack scene view: room pages refresh every
 * two seconds (and on request), every listed room stays reachable, and rooms are created as
 * relay or direct. List replies arrive on a network thread and are only published to the view
 * during {@link #update}, so drawing never changes what the player is pointing at.
 */
public final class ServerBrowserView {
    private static final int VISIBLE_ROOMS = 4;
    private static final long REFRESH_INTERVAL_MILLIS = 2000;

    private enum Focus { ROOMS, CREATE, CODE, REFRESH, PAGE }

    /** What the browser asks its owner to do. */
    public interface Actions {
        void join(ControlMessage.RoomSummary room);

        void create(String routing);

        default void joinCode() { }

        void back();
    }

    private record RefreshResult(ControlMessage.RoomListResult result, Throwable error) {
    }

    private final RoomDirectory directory;
    private final String gameId;
    private final ChatMode requestedChatMode;
    private final Actions actions;
    private final Consumer<MenuCue> cues;
    private final LongSupplier clockMillis;
    private final AtomicReference<RefreshResult> pendingRefresh = new AtomicReference<>();
    private volatile boolean active = true;
    private final FullTextReader reader = new FullTextReader();
    private Focus focus = Focus.ROOMS;
    private List<ControlMessage.RoomSummary> rooms = List.of();
    private String status = "Loading rooms...";
    private boolean refreshInFlight;
    private int selected;
    private boolean selectedRoomClosed;
    private int page;
    private int totalPages;
    private String createRouting = "RELAY";
    private long lastRefreshAt = Long.MIN_VALUE;
    private boolean pad;

    public ServerBrowserView(RoomDirectory directory, String gameId, Actions actions,
                             Consumer<MenuCue> cues, LongSupplier clockMillis) {
        this(directory, gameId, actions, cues, clockMillis, ChatMode.FULL_CHAT);
    }

    public ServerBrowserView(RoomDirectory directory, String gameId, Actions actions,
                             Consumer<MenuCue> cues, LongSupplier clockMillis, ChatMode requestedChatMode) {
        this.requestedChatMode = Objects.requireNonNull(requestedChatMode, "requestedChatMode");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        refresh(clockMillis.getAsLong());
    }

    public void update(ViewInput input) {
        pad = MenuPage.controller(input, pad);
        if (!active) {
            return;
        }
        applyPendingRefresh();
        long now = clockMillis.getAsLong();
        if (!directory.isOpen()) {
            status = "Master connection lost";
        } else if (lastRefreshAt == Long.MIN_VALUE || now - lastRefreshAt >= REFRESH_INTERVAL_MILLIS) {
            refresh(now);
        }
        if (reader.handle(input, this::detailsText)) return;
        if (input.back()) {
            deactivate();
            cues.accept(MenuCue.CANCEL);
            actions.back();
            return;
        }
        Focus beforeFocus = focus;
        int beforeSelected = selected;
        int beforePage = page;
        String beforeRouting = createRouting;
        if (input.up()) move(-1);
        if (input.down()) move(1);
        int delta = input.left() ? -1 : input.right() ? 1 : 0;
        if (delta != 0) {
            if (focus == Focus.CREATE) createRouting = createRouting.equals("RELAY") ? "DIRECT" : "RELAY";
            else changePage(delta, now);
        }
        if (beforeFocus != focus || beforeSelected != selected || beforePage != page
                || !beforeRouting.equals(createRouting)) {
            if (selectedRoomClosed) {
                selectedRoomClosed = false;
                status = roomStatus();
            }
            cues.accept(MenuCue.NAVIGATE);
        }
        if (input.keyPressed(SceneKeys.R)) manualRefresh(now);
        if (input.keyPressed(SceneKeys.J)) {
            actions.joinCode();
            cues.accept(MenuCue.CONFIRM);
            return;
        }
        if (input.keyPressed(SceneKeys.C)) {
            actions.create(createRouting);
            cues.accept(MenuCue.CONFIRM);
            return;
        }
        if (input.accept()) {
            switch (focus) {
                case ROOMS -> {
                    List<ControlMessage.RoomSummary> snapshot = rooms;
                    if (selected < snapshot.size()) {
                        actions.join(snapshot.get(selected));
                        cues.accept(MenuCue.CONFIRM);
                    } else {
                        cues.accept(MenuCue.ERROR);
                    }
                }
                case CREATE -> {
                    actions.create(createRouting);
                    cues.accept(MenuCue.CONFIRM);
                }
                case CODE -> { actions.joinCode(); cues.accept(MenuCue.CONFIRM); }
                case REFRESH -> manualRefresh(now);
                case PAGE -> {
                    int previousPage = page;
                    changePage(1, now);
                    if (previousPage != page) cues.accept(MenuCue.NAVIGATE);
                }
            }
        }
    }

    /** Stops publishing replies: a late list result can no longer change a closed browser. */
    public void deactivate() {
        active = false;
        pendingRefresh.set(null);
    }

    /** A status line from the owner (a failed join or create), shown until the next refresh. */
    public void showStatus(String message) {
        if (message != null && !message.isBlank()) {
            status = message;
        }
    }

    private void manualRefresh(long now) {
        if (!directory.isOpen()) {
            cues.accept(MenuCue.ERROR);
            return;
        }
        if (refreshInFlight) return;
        refresh(now);
        cues.accept(MenuCue.CONFIRM);
    }

    private void move(int delta) {
        int count = rooms.size();
        int current = focus == Focus.ROOMS ? Math.min(selected, Math.max(0, count - 1))
                : count + focus.ordinal() - 1;
        int next = Math.floorMod(current + delta, count + 4);
        if (next < count) {
            focus = Focus.ROOMS;
            selected = next;
        } else {
            focus = Focus.values()[next - count + 1];
        }
    }

    private void changePage(int delta, long now) {
        int next = Math.clamp(page + delta, 0, Math.max(0, totalPages - 1));
        if (next != page && !refreshInFlight) {
            page = next;
            selected = 0;
            rooms = List.of();
            status = "Loading rooms...";
            refresh(now);
        }
    }

    public void draw(SceneCanvas canvas) {
        if (reader.draw(canvas)) return;
        int width = canvas.width();
        MenuPage.page(canvas, "ROOMS", MenuPage.gameName(gameId), "Rooms listed on your master server");
        List<ControlMessage.RoomSummary> snapshot = rooms;
        int first = selected / VISIBLE_ROOMS * VISIBLE_ROOMS;
        int top = MenuPage.BODY_TOP - 2;
        if (snapshot.isEmpty()) {
            boolean loading = status.startsWith("Loading");
            MenuPage.icon(canvas, MenuPage.Tone.INFO, MenuPage.MARGIN + 6, top + 6);
            NameFont.draw(canvas, loading ? "Loading rooms" : "No rooms yet", MenuPage.MARGIN + 16, top + 3,
                    MenuPage.TEXT);
            if (!loading) MenuPage.paragraph(canvas, "Create one below, or refresh in a moment.",
                    MenuPage.MARGIN + 16, top + 3 + NameFont.LINE_HEIGHT, width - 40, 2, MenuPage.DIM);
        }
        for (int i = first; i < Math.min(snapshot.size(), first + VISIBLE_ROOMS); i++) {
            int y = top + (i - first) * CARD_PITCH;
            ControlMessage.RoomSummary room = snapshot.get(i);
            boolean focused = focus == Focus.ROOMS && i == selected;
            int cardWidth = width - 2 * MenuPage.MARGIN;
            if (focused) MenuPage.focus(canvas, MenuPage.MARGIN, y, cardWidth, CARD_PITCH - 2);
            else canvas.fill(MenuPage.MARGIN, y, cardWidth, CARD_PITCH - 2, 0xC0091A4A);
            String count = room.playerCount() + "/" + room.maxPlayers();
            int countX = width - MenuPage.MARGIN - 6 - CompactFont.width(count, 1);
            // Room names carry the host's display name, in any script.
            NameFont.draw(canvas, NameFont.fit(room.name(), countX - 8 - MenuPage.MARGIN - 6), MenuPage.MARGIN + 6,
                    y, focused ? MenuPage.TEXT : MenuPage.MUTED);
            CompactFont.draw(canvas, count, countX, y + 4, 1, MenuPage.TEXT);
            int x = MenuPage.MARGIN + 6;
            x = fact(canvas, x, y + 13, room.routing(), MenuPage.CYAN);
            x = fact(canvas, x, y + 13, chatLabel(room.chatMode()).toUpperCase(java.util.Locale.ROOT), MenuPage.DIM);
            fact(canvas, x, y + 13, room.verified() ? "VERIFIED TIMES" : "UNVERIFIED TIMES",
                    room.verified() ? MenuPage.GREEN : MenuPage.AMBER);
        }
        int pages = Math.max(1, totalPages);
        if (pages > 1 || snapshot.size() > VISIBLE_ROOMS) {
            MenuPage.scrollbar(canvas, width - MenuPage.MARGIN + 3, top, VISIBLE_ROOMS * CARD_PITCH - 2, first,
                    VISIBLE_ROOMS, Math.max(snapshot.size(), VISIBLE_ROOMS));
        }
        int statusY = top + VISIBLE_ROOMS * CARD_PITCH;
        // The Page row already says which page shows; only other reports take this line.
        if (!status.isEmpty() && !snapshot.isEmpty() && !status.equals(roomStatus()) || isProblem(status)) {
            MenuPage.Tone tone = isProblem(status) ? MenuPage.Tone.WARNING : MenuPage.Tone.INFO;
            MenuPage.icon(canvas, tone, MenuPage.MARGIN + 6, statusY + 3);
            NameFont.draw(canvas, NameFont.fit(status, width - 2 * MenuPage.MARGIN - 18), MenuPage.MARGIN + 16, statusY,
                    tone == MenuPage.Tone.INFO ? MenuPage.DIM : MenuPage.AMBER);
        }
        int y = statusY + NameFont.LINE_HEIGHT + 1;
        int valueX = MenuPage.MARGIN + 6 + MenuPage.labelColumn(List.of("Create room", "Page"));
        rowField(canvas, y, valueX, Focus.CREATE, "Create room", MenuPage.words(createRouting) + ", "
                + requestedChatMode.label().toLowerCase(java.util.Locale.ROOT));
        y += ACTION_PITCH;
        rowAction(canvas, y, Focus.CODE, "Join by code");
        y += ACTION_PITCH;
        rowAction(canvas, y, Focus.REFRESH, "Refresh rooms");
        y += ACTION_PITCH;
        rowField(canvas, y, valueX, Focus.PAGE, "Page", (page + 1) + " of " + pages);
        List<MenuPage.Hint> hints = new ArrayList<>();
        hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"));
        switch (focus) {
            case ROOMS -> { if (!snapshot.isEmpty()) hints.add(MenuPage.hint(MenuPage.confirm(pad), "Join")); }
            case CREATE -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Create"));
            case CODE -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Enter code"));
            case REFRESH -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Refresh"));
            case PAGE -> hints.add(MenuPage.hint(MenuPage.ARROWS_LR, "Page"));
        }
        if (!pad) {
            hints.add(MenuPage.hint("J", "Code"));
            hints.add(MenuPage.hint("R", "Refresh"));
        }
        hints.add(MenuPage.hint(MenuPage.back(pad), "Back"));
        MenuPage.footerWithDetails(canvas, pad, "", MenuPage.TEXT, hints);
    }

    private static final int CARD_PITCH = 23;
    private static final int ACTION_PITCH = 12;

    private static boolean isProblem(String status) {
        return status.contains("unavailable") || status.contains("lost") || status.contains("closed")
                || status.contains("Cannot") || status.contains("could not") || status.contains("rejected");
    }

    /** A compact capital fact with a dot after it; returns where the next one starts. */
    private static int fact(SceneCanvas canvas, int x, int y, String text, int argb) {
        if (x > MenuPage.MARGIN + 6) {
            canvas.fill(x, y + 3, 2, 2, MenuPage.FAINT);
            x += 6;
        }
        CompactFont.draw(canvas, text, x, y, 1, argb);
        return x + CompactFont.width(text, 1) + 5;
    }

    private void rowAction(SceneCanvas canvas, int y, Focus item, String label) {
        MenuPage.action(canvas, y, label, focus == item, true);
    }

    private void rowField(SceneCanvas canvas, int y, int valueX, Focus item, String label, String value) {
        MenuPage.field(canvas, y, valueX, label, value, MenuPage.TEXT, focus == item, true);
    }

    private List<String> detailsText() {
        List<String> text = new java.util.ArrayList<>();
        text.add("TIME ATTACK ROOMS / " + gameId);
        text.add(status);
        if (selected < rooms.size()) {
            ControlMessage.RoomSummary room = rooms.get(selected);
            text.add("Selected room: " + room.name());
            text.add(room.routing() + " / " + chatLabel(room.chatMode()) + " / "
                    + (room.verified() ? "VERIFIED" : "UNVERIFIED TIMES"));
            text.add(room.playerCount() + "/" + room.maxPlayers() + " players");
        }
        text.add("CREATE < " + createRouting + " > " + requestedChatMode.label());
        text.add("JOIN BY CODE / REFRESH ROOMS / PAGE " + (page + 1) + "/" + Math.max(1, totalPages));
        text.add("Up/Down select. Left/Right page or routing. A join/create. J join code. R refresh. B back.");
        return text;
    }

    List<String> detailsVisibleLines() { return reader.visibleLines(); }

    int detailsFirstLine() { return reader.firstVisibleLine(); }

    private static String chatLabel(String value) {
        ChatMode mode = ChatMode.parse(value);
        return mode == null ? "?" : mode.label();
    }

    private void refresh(long now) {
        if (refreshInFlight || !directory.isOpen()) {
            return;
        }
        refreshInFlight = true;
        lastRefreshAt = now;
        directory.listRooms(gameId, page).whenComplete((result, error) -> {
            // Publish one immutable result; only update() may change visible state.
            if (active) pendingRefresh.set(new RefreshResult(result, error));
        });
    }

    private void applyPendingRefresh() {
        RefreshResult completion = pendingRefresh.getAndSet(null);
        if (completion == null) return;
        refreshInFlight = false;
        if (completion.error() != null) {
            status = "Room list unavailable";
            cues.accept(MenuCue.ERROR);
            return;
        }
        String selectedId = selected < rooms.size() ? rooms.get(selected).roomId() : null;
        ControlMessage.RoomListResult result = completion.result();
        rooms = List.copyOf(result.rooms());
        int matched = -1;
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).roomId().equals(selectedId)) {
                matched = i;
                break;
            }
        }
        selected = matched >= 0 ? matched : Math.min(selected, Math.max(0, rooms.size() - 1));
        if (selectedId != null && matched < 0 && focus == Focus.ROOMS) {
            focus = Focus.REFRESH;
            selectedRoomClosed = true;
        } else if (rooms.isEmpty() && focus == Focus.ROOMS) {
            focus = Focus.CREATE;
        }
        totalPages = result.totalPages();
        status = selectedRoomClosed ? "Selected room closed" : roomStatus();
    }

    private String roomStatus() {
        return rooms.isEmpty() ? "No rooms found" : "Page " + (page + 1) + "/" + Math.max(1, totalPages);
    }
}
