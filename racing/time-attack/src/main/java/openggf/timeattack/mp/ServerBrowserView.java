package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.TextLayout;
import openggf.racing.protocol.ControlMessage;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.ViewInput;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
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
    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int VERIFIED = 0xFF99CCFF;
    private static final int UNVERIFIED = 0xFFFFCC4C;
    private static final int FOCUS = 0x803060C0;
    private static final int HINT = 0xFF8090B0;

    private enum Focus { ROOMS, CREATE, REFRESH, PAGE }

    /** What the browser asks its owner to do. */
    public interface Actions {
        void join(ControlMessage.RoomSummary room);

        void create(String routing);

        void back();
    }

    private record RefreshResult(ControlMessage.RoomListResult result, Throwable error) {
    }

    private final RoomDirectory directory;
    private final String gameId;
    private final Actions actions;
    private final Consumer<MenuCue> cues;
    private final LongSupplier clockMillis;
    private final AtomicReference<RefreshResult> pendingRefresh = new AtomicReference<>();
    private volatile boolean active = true;
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

    public ServerBrowserView(RoomDirectory directory, String gameId, Actions actions,
                             Consumer<MenuCue> cues, LongSupplier clockMillis) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        refresh(clockMillis.getAsLong());
    }

    public void update(ViewInput input) {
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
        int next = Math.floorMod(current + delta, count + 3);
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
        canvas.clear(BACKGROUND);
        canvas.text("TIME ATTACK ROOMS", 10, 10, TITLE);
        canvas.text(gameId.toUpperCase(), 10, 24, HINT);
        int width = canvas.width();
        List<ControlMessage.RoomSummary> snapshot = rooms;
        int first = selected / VISIBLE_ROOMS * VISIBLE_ROOMS;
        if (snapshot.isEmpty()) {
            canvas.text("No rooms. Create one below.", 12, 44, LABEL);
        }
        for (int i = first; i < Math.min(snapshot.size(), first + VISIBLE_ROOMS); i++) {
            int y = 42 + (i - first) * 24;
            ControlMessage.RoomSummary room = snapshot.get(i);
            if (focus == Focus.ROOMS && i == selected) canvas.fill(8, y - 2, width - 16, 23, FOCUS);
            String count = room.playerCount() + "/" + room.maxPlayers();
            int countX = width - 12 - canvas.textWidth(count);
            canvas.text(fit(canvas, room.name(), countX - 8 - 12), 12, y, VALUE);
            canvas.text(count, countX, y, VALUE);
            canvas.text(fit(canvas, room.routing() + " / " + (room.verified() ? "VERIFIED" : "UNVERIFIED TIMES"),
                    width - 24), 12, y + 11, room.verified() ? VERIFIED : UNVERIFIED);
        }
        action(canvas, Focus.CREATE, "CREATE ROOM  < " + createRouting + " >", 144);
        action(canvas, Focus.REFRESH, "REFRESH ROOMS", 160);
        action(canvas, Focus.PAGE, "PAGE  < " + (page + 1) + " / " + Math.max(1, totalPages) + " >", 176);
        canvas.text(fit(canvas, status, width - 20), 10, 194, LABEL);
        canvas.text(fit(canvas, "Left/Right page/route  R refresh  B back", width - 20), 10, 208, HINT);
    }

    private void action(SceneCanvas canvas, Focus item, String label, int y) {
        if (focus == item) canvas.fill(8, y - 3, canvas.width() - 16, 16, FOCUS);
        canvas.text(fit(canvas, label, canvas.width() - 24), 12, y, VALUE);
    }

    private static String fit(SceneCanvas canvas, String text, int maxWidth) {
        return TextLayout.ellipsis(text, Math.max(0, maxWidth), canvas::textWidth);
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
            Throwable error = completion.error();
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            status = "Refresh failed: " + (cause.getMessage() == null
                    ? cause.getClass().getSimpleName() : cause.getMessage());
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
