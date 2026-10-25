package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.TextLayout;
import openggf.timeattack.ui.MenuCue;
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

    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int HINT = 0xFF8090B0;
    private static final int WARNING = 0xFFFF8060;
    private static final int FOCUS = 0x803060C0;
    private static final int CARD = 0xFF1C2848;
    private static final int BUTTON = 0xFF2A3A64;
    private static final int BUTTON_ON = 0xFF3060C0;
    private static final int PUPIL = 0xFF101830;
    private static final String MASK = "*** ***";
    private static final int CARD_Y = 38;
    private static final int CARD_H = 48;
    private static final int ROWS_Y = 98;
    private static final int ROW_H = 16;
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
        if (closed) return;
        long now = clockMillis.getAsLong();
        observe(Objects.requireNonNull(snapshots.get(), "snapshot"), now);
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
                shown.manual() ? 116 : CARD_Y + 12, EYE_W, 24};
        int row = choices().indexOf(item) - (shown.canInvite() ? 1 : 0);
        return new int[] {8, (shown.manual() ? 152 : ROWS_Y) + row * ROW_H - 4, width - 16, ROW_H};
    }

    public void draw(SceneCanvas canvas) {
        int width = canvas.width();
        lastWidth = width;
        long now = clockMillis.getAsLong();
        canvas.clear(BACKGROUND);
        canvas.text(shown.manual() ? "MANUAL LAN INVITE" : "ROOM INVITE", 10, 10, TITLE);
        String role = shown.host() ? "HOST" : "MEMBER";
        canvas.text(role, width - 10 - canvas.textWidth(role), 10, HINT);
        canvas.text(fit(canvas, shown.canInvite() ? "Friends join this room with the code below"
                : "Invites are switched off for members", width - 20), 10, 24, HINT);

        canvas.fill(12, CARD_Y, width - 24, shown.manual() ? 106 : CARD_H, CARD);
        if (shown.canInvite() && shown.manual()) {
            boolean visible = revealed && shown.inviteEpoch() == revealedEpoch && live(shown, now);
            String value = visible ? shown.code() : MASK;
            int y = 44;
            for (String line : TextEntry.wrap(value, Math.max(1, (width - 48) / canvas.textWidth("W")))) {
                canvas.text(line, 24, y, visible ? VALUE : LABEL);
                y += 11;
            }
            if (visible) {
                canvas.text(fit(canvas, "Replace HOST_IP with your LAN address", width - 48), 24, 101, HINT);
                canvas.text(fit(canvas, "Share the complete pinned invite", width - 48), 24, 112, HINT);
            }
            drawEye(canvas, width, visible);
        } else if (shown.canInvite()) {
            boolean visible = revealed && shown.inviteEpoch() == revealedEpoch && live(shown, now);
            String code = visible ? shown.code() : MASK;
            int scale = NameFont.width(code) * 2 <= width - 52 - EYE_W ? 2 : 1;
            NameText.draw(canvas, code, 24, CARD_Y + (scale == 2 ? 5 : 12), scale, visible ? VALUE : LABEL);
            canvas.text(fit(canvas, expiry(now, visible), width - 48 - EYE_W), 24, CARD_Y + CARD_H - 12, HINT);
            drawEye(canvas, width, visible);
        } else {
            String only = "Only the host can invite";
            canvas.text(only, (width - canvas.textWidth(only)) / 2, CARD_Y + CARD_H / 2 - 4, LABEL);
        }

        for (Focus item : choices()) {
            if (item == Focus.REVEAL) continue;
            int[] r = bounds(item, width);
            if (focus == item) canvas.fill(r[0], r[1], r[2], r[3], FOCUS);
            canvas.text(fit(canvas, label(item), width - 24), 12, r[1] + 4, VALUE);
        }

        String status = !notice.isEmpty() ? notice : safeStatus();
        if (!status.isEmpty()) canvas.text(fit(canvas, status, width - 20), 10, 194,
                rotateArmedAt >= 0 ? WARNING : LABEL);
        canvas.text(fit(canvas, "Up/Down choose  A select  B back", width - 20), 10, 208, HINT);
    }

    private String label(Focus item) {
        return switch (item) {
            case COPY -> shown.manual() ? "COPY LAN INVITE" : "COPY CODE";
            case ROTATE -> rotateArmedAt >= 0 ? "NEW CODE - PRESS AGAIN" : "NEW CODE";
            case SHARING -> "MEMBERS CAN INVITE  < " + (shown.membersCanInvite() ? "ON" : "OFF") + " >";
            case PASSWORD -> "CHANGE ROOM PASSWORD";
            case BACK -> "BACK";
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
        canvas.fill(r[0], r[1], r[2], r[3], lit ? BUTTON_ON : BUTTON);
        int cx = r[0] + 12;
        int cy = r[1] + 12;
        int[] halfWidths = {3, 6, 8, 8, 6, 3};
        for (int i = 0; i < halfWidths.length; i++) {
            canvas.fill(cx - halfWidths[i], cy - 3 + i, halfWidths[i] * 2, 1, VALUE);
        }
        canvas.fill(cx - 2, cy - 2, 4, 4, PUPIL);
        canvas.fill(cx - 1, cy - 2, 1, 1, VALUE);
        if (visible) {
            for (int i = -6; i < 6; i++) canvas.fill(cx + i, cy - i - 1, 2, 2, lit ? BUTTON_ON : BUTTON);
            for (int i = -6; i < 6; i++) canvas.fill(cx + i, cy - i, 1, 1, WARNING);
        }
        String text = visible ? "HIDE" : revealRequestedAt >= 0 ? "WAIT" : "SHOW";
        canvas.text(text, r[0] + 24, cy - 4, VALUE);
    }

    private static String fit(SceneCanvas canvas, String text, int maxWidth) {
        return TextLayout.ellipsis(text, Math.max(0, maxWidth), canvas::textWidth);
    }
}
