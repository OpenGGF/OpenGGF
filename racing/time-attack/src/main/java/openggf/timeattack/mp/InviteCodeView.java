package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.mods.ui.NameFont;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.FullTextReader;
import openggf.timeattack.ui.NameText;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The room's invite code, masked until the player asks to see it. A reveal lasts ten seconds and
 * ends early when the code expires, is replaced (a new invite epoch), the player's permission
 * changes or the owner calls {@link #hide()}. Copy never reveals. The view only asks: fetching,
 * copying, replacing the code and the sharing switch belong to the owner's {@link Action} handler,
 * whose results reach the view through the next {@link Snapshot}.
 */
public final class InviteCodeView {
    static final long REVEAL_MILLIS = 10_000;
    static final long CONFIRM_MILLIS = 3_000;

    private static final int PUPIL = 0xFF101830;
    private static final int CARD_Y = MenuPage.BODY_TOP - 2;
    private static final int CARD_H = 48;
    private static final int MANUAL_CARD_H = 86;
    private static final int ROW_H = MenuPage.ROW_H + 1;
    private static final int EYE_W = 68;

    /** What the view asks its owner to do. */
    public enum Action { FETCH, COPY, ROTATE, TOGGLE_SHARING, CHANGE_PASSWORD, HIDE }

    /** The owner's current view of the room's invite; {@code code} is null until fetched. */
    public record Snapshot(String code, long expiresAtMillis, long inviteEpoch, boolean canInvite, boolean host,
                           boolean membersCanInvite, String status, boolean manual, boolean privateRoom) {
        public Snapshot(String code, long expiresAtMillis, long inviteEpoch, boolean canInvite, boolean host,
                        boolean membersCanInvite, String status) {
            this(code, expiresAtMillis, inviteEpoch, canInvite, host, membersCanInvite, status, false, false);
        }
        @Override
        public String toString() {
            return "Snapshot[code=" + (code == null ? "none" : "redacted") + ", epoch=" + inviteEpoch
                    + ", canInvite=" + canInvite + ", host=" + host + "]";
        }
    }

    private enum Focus { REVEAL, COPY, ROTATE, SHARING, PASSWORD, BACK }

    private final Supplier<Snapshot> snapshots;
    private final Consumer<Action> actions;
    private final Consumer<MenuCue> cues;
    private final LongSupplier clockMillis;
    private Snapshot shown;
    private final FullTextReader reader = new FullTextReader();
    private Focus focus = Focus.REVEAL;
    private boolean revealed;
    private long revealedEpoch;
    private long revealedAt;
    /** When the player asked to see a code nobody had fetched yet; the next live code is shown. */
    private long revealRequestedAt = -1;
    private long rotateArmedAt = -1;
    private String notice = "";
    private boolean closed;
    private int lastWidth = 320;
    private boolean pad;

    public InviteCodeView(Supplier<Snapshot> snapshots, Consumer<Action> actions, Consumer<MenuCue> cues,
                          LongSupplier clockMillis) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        shown = Objects.requireNonNull(snapshots.get(), "snapshot");
        if (!shown.canInvite()) focus = Focus.BACK;
    }

    public void update(ViewInput input, SceneMouse mouse) {
        pad = MenuPage.controller(input, pad);
        if (closed) return;
        long now = clockMillis.getAsLong();
        observe(Objects.requireNonNull(snapshots.get(), "snapshot"), now);
        if (reader.handle(input, this::detailsText)) return;
        List<Focus> choices = choices();
        if (!choices.contains(focus)) focus = choices.getFirst();
        if (input.back()) {
            close();
            return;
        }
        Focus before = focus;
        Focus clicked = null;
        for (Focus item : choices) {
            int[] r = bounds(item, lastWidth);
            if (mouse.over(r[0], r[1], r[2], r[3])) {
                if (mouse.moved()) focus = item;
                if (mouse.leftPressed()) clicked = item;
            }
        }
        int vertical = input.up() ? -1 : input.down() ? 1 : 0;
        if (vertical != 0) focus = choices.get(Math.floorMod(choices.indexOf(focus) + vertical, choices.size()));
        if (focus != before) {
            withdraw();
            notice = "";
            cues.accept(MenuCue.NAVIGATE);
        }
        if (focus == Focus.SHARING && (input.left() || input.right())) activate(Focus.SHARING, now);
        if (clicked != null) {
            focus = clicked;
            activate(clicked, now);
        } else if (input.accept()) {
            activate(focus, now);
        }
    }

    /** Takes the owner's latest snapshot and withdraws a reveal it no longer supports. */
    private void observe(Snapshot next, long now) {
        boolean permissionChanged = next.canInvite() != shown.canInvite() || next.host() != shown.host()
                || next.membersCanInvite() != shown.membersCanInvite() || next.manual() != shown.manual();
        shown = next;
        if (permissionChanged || !next.canInvite()) hide();
        if (revealed && (next.inviteEpoch() != revealedEpoch || !live(next, now)
                || now - revealedAt >= REVEAL_MILLIS)) {
            withdraw();
        }
        if (revealRequestedAt >= 0) {
            if (now - revealRequestedAt >= REVEAL_MILLIS) {
                revealRequestedAt = -1;
                notice = "The invite code did not arrive";
            } else if (live(next, now)) {
                reveal(next, now);
            }
        }
        if (rotateArmedAt >= 0 && now - rotateArmedAt >= CONFIRM_MILLIS) {
            rotateArmedAt = -1;
            notice = "";
        }
    }

    private void activate(Focus item, long now) {
        if (item != Focus.ROTATE) rotateArmedAt = -1;
        switch (item) {
            case REVEAL -> {
                if (revealed || revealRequestedAt >= 0) {
                    withdraw();
                    cues.accept(MenuCue.CANCEL);
                } else if (live(shown, now)) {
                    reveal(shown, now);
                    cues.accept(MenuCue.CONFIRM);
                } else {
                    revealRequestedAt = now;
                    notice = "Fetching the invite code...";
                    actions.accept(Action.FETCH);
                    cues.accept(MenuCue.CONFIRM);
                }
            }
            case COPY -> {
                notice = "";
                actions.accept(Action.COPY);
                cues.accept(MenuCue.CONFIRM);
            }
            case ROTATE -> {
                if (rotateArmedAt < 0) {
                    rotateArmedAt = now;
                    notice = "Press again: the current code stops working";
                    cues.accept(MenuCue.NAVIGATE);
                } else {
                    withdraw();
                    actions.accept(Action.ROTATE);
                    cues.accept(MenuCue.CONFIRM);
                }
            }
            case SHARING -> {
                withdraw();
                actions.accept(Action.TOGGLE_SHARING);
                cues.accept(MenuCue.CONFIRM);
            }
            case PASSWORD -> { withdraw(); actions.accept(Action.CHANGE_PASSWORD); }
            case BACK -> close();
        }
    }

    private void reveal(Snapshot snapshot, long now) {
        revealed = true;
        revealedEpoch = snapshot.inviteEpoch();
        revealedAt = now;
        revealRequestedAt = -1;
        notice = "";
    }

    private static boolean live(Snapshot snapshot, long now) {
        return snapshot.canInvite() && snapshot.code() != null && !snapshot.code().isEmpty()
                && now < snapshot.expiresAtMillis();
    }

    /** Masks the code and forgets a pending reveal; the owner calls this when the window loses focus. */
    public void hide() {
        notice = "";
        revealed = false;
        revealRequestedAt = -1;
        rotateArmedAt = -1;
        shown = new Snapshot(null, 0, shown.inviteEpoch(), shown.canInvite(), shown.host(),
                shown.membersCanInvite(), shown.status(), shown.manual(), shown.privateRoom());
    }

    public boolean closed() {
        return closed;
    }

    private void withdraw() {
        hide();
        actions.accept(Action.HIDE);
    }

    private void close() {
        withdraw();
        closed = true;
        cues.accept(MenuCue.CANCEL);
    }

    private List<Focus> choices() {
        if (!shown.canInvite()) return List.of(Focus.BACK);
        List<Focus> choices = new ArrayList<>(List.of(Focus.REVEAL, Focus.COPY));
        if (shown.host()) {
            if (!shown.manual()) {
                choices.add(Focus.ROTATE);
                choices.add(Focus.SHARING);
            }
            if (shown.privateRoom()) choices.add(Focus.PASSWORD);
        }
        choices.add(Focus.BACK);
        return choices;
    }

    /** {x, y, w, h} of an item's hit box: the eye button in the card, the rest as rows below it. */
    private int[] bounds(Focus item, int width) {
        if (item == Focus.REVEAL) return new int[] {width - 20 - EYE_W,
                shown.manual() ? CARD_Y + MANUAL_CARD_H - 30 : CARD_Y + 12, EYE_W, 24};
        int row = choices().indexOf(item) - (shown.canInvite() ? 1 : 0);
        return new int[] {MenuPage.MARGIN, rowsY() + row * ROW_H, width - 2 * MenuPage.MARGIN, ROW_H};
    }

    private int rowsY() {
        return CARD_Y + (shown.manual() ? MANUAL_CARD_H : CARD_H) + 6;
    }

    public void draw(SceneCanvas canvas) {
        if (reader.draw(canvas)) return;
        int width = canvas.width();
        lastWidth = width;
        long now = clockMillis.getAsLong();
        MenuPage.page(canvas, shown.manual() ? "LAN INVITE" : "ROOM INVITE", shown.host() ? "Host" : "Member",
                shown.canInvite() ? "Friends join this room with the code below"
                        : "Invites are switched off for members");
        int cardWidth = width - 2 * MenuPage.MARGIN;
        int cardHeight = shown.manual() ? MANUAL_CARD_H : CARD_H;
        canvas.fill(MenuPage.MARGIN, CARD_Y, cardWidth, cardHeight, MenuPage.INK);
        MenuPage.outline(canvas, MenuPage.MARGIN, CARD_Y, cardWidth, cardHeight, MenuPage.EDGE);
        int textX = MenuPage.MARGIN + 8;
        if (shown.canInvite() && shown.manual()) {
            boolean visible = revealed && shown.inviteEpoch() == revealedEpoch && live(shown, now);
            int y = CARD_Y + 5;
            if (visible) {
                // The whole pinned invite, wrapped by measured width so no character is cut.
                for (String line : MenuPage.wrap(shown.code(), cardWidth - 16)) {
                    NameFont.draw(canvas, line, textX, y, MenuPage.TEXT);
                    y += NameFont.LINE_HEIGHT;
                }
                int helpWidth = width - 20 - EYE_W - 6 - textX;
                MenuPage.paragraph(canvas, "Replace HOST_IP with your LAN address and share the whole invite.",
                        textX, CARD_Y + MANUAL_CARD_H - 30, helpWidth, 2, MenuPage.DIM);
            } else {
                masked(canvas, textX, y + 4);
                NameFont.draw(canvas, "Hidden until you choose Show", textX, y + 18, MenuPage.DIM);
            }
            drawEye(canvas, width, visible);
        } else if (shown.canInvite()) {
            boolean visible = revealed && shown.inviteEpoch() == revealedEpoch && live(shown, now);
            if (visible) {
                String code = shown.code();
                int scale = NameFont.width(code) * 2 <= width - 52 - EYE_W ? 2 : 1;
                NameText.draw(canvas, code, textX, CARD_Y + (scale == 2 ? 4 : 10), scale, MenuPage.TEXT);
            } else {
                masked(canvas, textX, CARD_Y + 12);
            }
            NameFont.draw(canvas, NameFont.fit(expiry(now, visible), width - 48 - EYE_W), textX,
                    CARD_Y + CARD_H - 15, MenuPage.DIM);
            drawEye(canvas, width, visible);
        } else {
            String only = "Only the host can invite";
            MenuPage.lock(canvas, (width - NameFont.width(only)) / 2 - 12, CARD_Y + CARD_H / 2 - 4, MenuPage.DIM);
            NameFont.draw(canvas, only, (width - NameFont.width(only)) / 2, CARD_Y + CARD_H / 2 - 7, MenuPage.MUTED);
        }

        int lastRow = rowsY();
        for (Focus item : choices()) {
            if (item == Focus.REVEAL) continue;
            int[] r = bounds(item, width);
            if (item == Focus.SHARING) {
                MenuPage.field(canvas, r[1], MenuPage.MARGIN + 6 + MenuPage.labelColumn(List.of("Members can invite")),
                        "Members can invite", shown.membersCanInvite() ? "On" : "Off", MenuPage.TEXT,
                        focus == item, true);
            } else {
                MenuPage.action(canvas, r[1], label(item), focus == item, true);
            }
            lastRow = r[1] + ROW_H;
        }

        String status = !notice.isEmpty() ? notice : safeStatus();
        int lines = Math.max(1, (MenuPage.FOOTER_Y - 2 - lastRow - 3) / NameFont.LINE_HEIGHT);
        if (!status.isEmpty()) {
            MenuPage.Tone tone = rotateArmedAt >= 0 || notice.startsWith("The invite code did not") ? MenuPage.Tone.WARNING
                    : MenuPage.Tone.INFO;
            MenuPage.icon(canvas, tone, MenuPage.MARGIN + 6, lastRow + 6);
            MenuPage.paragraph(canvas, status, MenuPage.MARGIN + 16, lastRow + 3, width - 2 * MenuPage.MARGIN - 16,
                    lines, tone == MenuPage.Tone.INFO ? MenuPage.MUTED : MenuPage.AMBER);
        }
        List<MenuPage.Hint> hints = new ArrayList<>();
        hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"));
        if (focus == Focus.SHARING) hints.add(MenuPage.hint(MenuPage.ARROWS_LR, "Change"));
        else hints.add(MenuPage.hint(MenuPage.confirm(pad), switch (focus) {
            case REVEAL -> revealed ? "Hide" : "Show";
            case COPY -> "Copy";
            case ROTATE -> "New code";
            case PASSWORD -> "Change";
            case SHARING, BACK -> "Back";
        }));
        hints.add(MenuPage.hint(MenuPage.back(pad), "Back"));
        MenuPage.footerWithDetails(canvas, pad, "", MenuPage.TEXT, hints);
    }

    /** Six masked characters as dots in two groups: the code's shape, never its contents. */
    private static void masked(SceneCanvas canvas, int x, int y) {
        for (int i = 0; i < 6; i++) canvas.fill(x + i * 9 + (i >= 3 ? 8 : 0), y, 5, 5, MenuPage.DIM);
    }

    /** Reads guidance/status only: entering this page never reveals, copies or rotates a credential. */
    private FullTextReader.Text detailsText() {
        FullTextReader.Text text = FullTextReader.Text.of(shown.manual() ? "LAN INVITE" : "ROOM INVITE",
                shown.canInvite() ? "Friends join this room with the code below" : "Invites are switched off for members");
        if (!shown.canInvite()) {
            text.line("Only the host can invite.");
        } else {
            text.field(shown.manual() ? "Invite" : "Code", "Hidden on this page; choose Show on the invite page to see it");
            if (shown.manual()) text.line("Replace HOST_IP with your LAN address and share the whole invite.");
            else text.field("Expiry", expiry(clockMillis.getAsLong(), false));
        }
        if (choices().contains(Focus.SHARING)) text.field("Members can invite", shown.membersCanInvite() ? "On" : "Off");
        text.field("Status", notice.isEmpty() ? safeStatus() : notice);
        // Keep the warning readable even if its three-second activation window expires while reading.
        if (shown.host() && !shown.manual()) {
            text.line("New code replaces the room's code: choose it twice, and the current code stops working.");
        }
        return text;
    }

    List<String> detailsVisibleLines() { return reader.visibleLines(); }

    int detailsFirstLine() { return reader.firstVisibleLine(); }

    private String label(Focus item) {
        return switch (item) {
            case COPY -> shown.manual() ? "Copy LAN invite" : "Copy code";
            case ROTATE -> rotateArmedAt >= 0 ? "New code: press again to replace it" : "New code";
            case SHARING -> "Members can invite";
            case PASSWORD -> "Change room password";
            case BACK -> "Back";
            case REVEAL -> "";
        };
    }

    private String expiry(long now, boolean visible) {
        if (revealRequestedAt >= 0) return "Fetching...";
        if (shown.code() == null || shown.code().isEmpty()) return "Not fetched yet";
        long left = shown.expiresAtMillis() - now;
        if (left <= 0) return "Expired; SHOW gets a new one";
        long seconds = (left + 999) / 1000;
        String time = seconds >= 3600 ? "%d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60)
                : "%d:%02d".formatted(seconds / 60, seconds % 60);
        String hides = visible ? "  Hides " + Math.max(1, (REVEAL_MILLIS - (now - revealedAt) + 999) / 1000) + "s" : "";
        return "Expires " + time + hides;
    }

    /** The owner's status, unless it would print the code. */
    private String safeStatus() {
        String status = Objects.requireNonNullElse(shown.status(), "");
        String code = shown.code();
        return code != null && !code.isBlank() && status.contains(code.strip()) ? "" : status;
    }

    /** The eye button: an open eye with SHOW, or a struck-through one with HIDE while the code shows. */
    private void drawEye(SceneCanvas canvas, int width, boolean visible) {
        int[] r = bounds(Focus.REVEAL, width);
        boolean lit = focus == Focus.REVEAL;
        if (lit) MenuPage.focus(canvas, r[0], r[1], r[2], r[3]);
        else {
            canvas.fill(r[0], r[1], r[2], r[3], MenuPage.BUTTON);
            MenuPage.outline(canvas, r[0], r[1], r[2], r[3], MenuPage.EDGE);
        }
        int cx = r[0] + 12;
        int cy = r[1] + 12;
        int[] halfWidths = {3, 6, 8, 8, 6, 3};
        for (int i = 0; i < halfWidths.length; i++) {
            canvas.fill(cx - halfWidths[i], cy - 3 + i, halfWidths[i] * 2, 1, MenuPage.TEXT);
        }
        canvas.fill(cx - 2, cy - 2, 4, 4, PUPIL);
        canvas.fill(cx - 1, cy - 2, 1, 1, MenuPage.TEXT);
        if (visible) {
            int back = lit ? MenuPage.FOCUS_FILL : MenuPage.BUTTON;
            for (int i = -6; i < 6; i++) canvas.fill(cx + i, cy - i - 1, 2, 2, back);
            for (int i = -6; i < 6; i++) canvas.fill(cx + i, cy - i, 1, 1, MenuPage.AMBER);
        }
        String text = visible ? "HIDE" : revealRequestedAt >= 0 ? "WAIT" : "SHOW";
        canvas.text(text, r[0] + 24, cy - 4, lit ? MenuPage.GOLD : MenuPage.TEXT);
    }

}
