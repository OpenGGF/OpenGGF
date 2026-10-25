package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.TextLayout;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Who may join a new room: public (listed) or private (password), and whether members may share
 * the room's invite code. The password is held only in its masked editor and is cleared when the
 * room is created, the form is cancelled or the view closes; it is never drawn, not even as a length.
 */
public final class RoomAccessView {
    static final int MIN_PASSWORD_CODE_POINTS = 15;
    static final int MAX_PASSWORD_CODE_POINTS = 128;
    static final int MAX_PASSWORD_UTF8_BYTES = 512;

    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int HINT = 0xFF8090B0;
    private static final int DIM = 0xFF5C6886;
    private static final int WARNING = 0xFFFF8060;
    private static final int GOOD = 0xFF99CCFF;
    private static final int FOCUS = 0x803060C0;
    private static final int SEGMENT = 0xFF1C2848;
    private static final int SEGMENT_ON = 0xFF3060C0;
    private static final String MASK = "************";

    private enum Focus { ACCESS, PASSWORD, INVITES, CREATE, BACK }

    /** What the form asks its owner to do. */
    public interface Actions {
        /** {@code password} is empty for a public room; the owner must not retain or log it. */
        void create(boolean privateRoom, boolean membersCanInvite, String password);

        void cancelled();
    }

    private final Actions actions;
    private final Consumer<MenuCue> cues;
    private final TextEntry password = TextEntry.password("ROOM PASSWORD");
    private Focus focus = Focus.ACCESS;
    private boolean privateRoom;
    private boolean membersCanInvite = true;
    private boolean editing;
    /** Created or cancelled: later presses must not create a second room. */
    private boolean finished;
    private String notice = "";

    public RoomAccessView(Actions actions, Consumer<MenuCue> cues) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.cues = Objects.requireNonNull(cues, "cues");
    }

    public void update(ViewInput input) {
        if (finished) return;
        if (editing) {
            switch (password.update(input)) {
                case ACCEPTED -> {
                    editing = false;
                    notice = "";
                    cues.accept(MenuCue.CONFIRM);
                }
                case CANCELLED -> {
                    editing = false;
                    password.clear();
                    cues.accept(MenuCue.CANCEL);
                }
                case NONE -> { }
            }
            return;
        }
        if (input.back()) {
            cancel();
            return;
        }
        Focus before = focus;
        boolean wasPrivate = privateRoom;
        boolean couldInvite = membersCanInvite;
        int vertical = input.up() ? -1 : input.down() ? 1 : 0;
        if (vertical != 0) {
            List<Focus> rows = rows();
            int row = rows.indexOf(focus == Focus.BACK ? Focus.CREATE : focus);
            focus = rows.get(Math.floorMod(row + vertical, rows.size()));
        }
        int horizontal = input.left() ? -1 : input.right() ? 1 : 0;
        if (horizontal != 0) {
            switch (focus) {
                case ACCESS -> privateRoom = horizontal > 0;
                case INVITES -> membersCanInvite = horizontal < 0;
                case CREATE, BACK -> focus = horizontal < 0 ? Focus.CREATE : Focus.BACK;
                case PASSWORD -> { }
            }
        }
        if (!privateRoom && wasPrivate) password.clear();
        if (focus != before || privateRoom != wasPrivate || membersCanInvite != couldInvite) {
            notice = "";
            cues.accept(MenuCue.NAVIGATE);
        }
        if (input.accept()) {
            switch (focus) {
                case ACCESS -> {
                    privateRoom = !privateRoom;
                    if (!privateRoom) password.clear();
                    cues.accept(MenuCue.NAVIGATE);
                }
                case PASSWORD -> {
                    editing = true;
                    cues.accept(MenuCue.CONFIRM);
                }
                case INVITES -> {
                    membersCanInvite = !membersCanInvite;
                    cues.accept(MenuCue.NAVIGATE);
                }
                case CREATE -> create();
                case BACK -> cancel();
            }
        }
    }

    /** The rows up and down step through; CREATE stands for the last row, which BACK shares. */
    private List<Focus> rows() {
        List<Focus> rows = new ArrayList<>(List.of(Focus.ACCESS));
        if (privateRoom) rows.add(Focus.PASSWORD);
        rows.add(Focus.INVITES);
        rows.add(Focus.CREATE);
        return rows;
    }

    private void create() {
        String problem = privateRoom ? passwordProblem() : null;
        if (problem != null) {
            notice = problem;
            focus = Focus.PASSWORD;
            cues.accept(MenuCue.ERROR);
            return;
        }
        String secret = privateRoom ? password.text() : "";
        password.clear();
        finished = true;
        cues.accept(MenuCue.CONFIRM);
        actions.create(privateRoom, membersCanInvite, secret);
    }

    private void cancel() {
        password.clear();
        editing = false;
        finished = true;
        cues.accept(MenuCue.CANCEL);
        actions.cancelled();
    }

    /** Why the typed password cannot be used, or null; exact code points, no trimming. */
    private String passwordProblem() {
        String value = password.text();
        int points = value.codePointCount(0, value.length());
        if (points == 0) return "Set a password for a private room";
        if (points < MIN_PASSWORD_CODE_POINTS) return "Password needs at least " + MIN_PASSWORD_CODE_POINTS + " characters";
        if (points > MAX_PASSWORD_CODE_POINTS
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_UTF8_BYTES) return "Password is too long";
        return null;
    }

    /** True while the password editor takes keyboard text. */
    public boolean isEditingText() {
        return editing;
    }

    /** The window lost focus: leave the editor so later keys cannot type into a field out of sight. */
    public void focusLost() {
        editing = false;
        password.clear();
    }

    /** The owner discards the form: the password is erased. */
    public void close() {
        editing = false;
        finished = true;
        password.clear();
    }

    public void draw(SceneCanvas canvas) {
        if (editing) {
            password.draw(canvas);
            return;
        }
        int width = canvas.width();
        canvas.clear(BACKGROUND);
        canvas.text("CREATE ROOM", 10, 10, TITLE);
        canvas.text(fit(canvas, "Choose who can join before the room opens", width - 20), 10, 24, HINT);

        int y = 44;
        row(canvas, Focus.ACCESS, "ACCESS", y, privateRoom ? "Hidden from the room browser"
                : "Listed in the room browser", LABEL);
        segments(canvas, "PUBLIC", "PRIVATE", privateRoom, y);
        y += 30;
        if (privateRoom) {
            String problem = passwordProblem();
            row(canvas, Focus.PASSWORD, "PASSWORD", y, problem == null ? "Ready  -  A to change" : problem,
                    problem == null ? GOOD : WARNING);
            boolean set = !password.text().isEmpty();
            String value = set ? MASK : "NOT SET";
            canvas.text(value, width - 16 - canvas.textWidth(value), y, set ? VALUE : DIM);
        } else {
            canvas.text("PASSWORD", 16, y, DIM);
            canvas.text(fit(canvas, "Not needed for a public room", width - 32), 16, y + 11, DIM);
        }
        y += 30;
        row(canvas, Focus.INVITES, "MEMBERS CAN INVITE", y, membersCanInvite
                ? "Everyone in the room can share its code" : "Only you can share the room's code", LABEL);
        segments(canvas, "ON", "OFF", !membersCanInvite, y);

        int buttonY = 152;
        int half = (width - 24) / 2;
        button(canvas, Focus.CREATE, "CREATE ROOM", 8, buttonY, half);
        button(canvas, Focus.BACK, "BACK", 16 + half, buttonY, half);

        if (!notice.isEmpty()) canvas.text(fit(canvas, notice, width - 20), 10, 194, WARNING);
        canvas.text(fit(canvas, "Arrows choose/change  A OK  B back", width - 20), 10, 208, HINT);
    }

    /** A two-line setting: its label, and under it what the current choice means. */
    private void row(SceneCanvas canvas, Focus item, String label, int y, String detail, int detailColour) {
        if (focus == item) canvas.fill(8, y - 4, canvas.width() - 16, 26, FOCUS);
        canvas.text(label, 16, y, VALUE);
        canvas.text(fit(canvas, detail, canvas.width() - 32), 16, y + 11, detailColour);
    }

    /** A two-way switch drawn at the row's right edge, the chosen side lit. */
    private static void segments(SceneCanvas canvas, String first, String second, boolean secondChosen, int y) {
        int pad = 6;
        int secondWidth = canvas.textWidth(second) + pad * 2;
        int firstWidth = canvas.textWidth(first) + pad * 2;
        int right = canvas.width() - 14;
        int secondX = right - secondWidth;
        int firstX = secondX - 2 - firstWidth;
        canvas.fill(firstX, y - 2, firstWidth, 12, secondChosen ? SEGMENT : SEGMENT_ON);
        canvas.fill(secondX, y - 2, secondWidth, 12, secondChosen ? SEGMENT_ON : SEGMENT);
        canvas.text(first, firstX + pad, y, secondChosen ? HINT : VALUE);
        canvas.text(second, secondX + pad, y, secondChosen ? VALUE : HINT);
    }

    private void button(SceneCanvas canvas, Focus item, String label, int x, int y, int width) {
        canvas.fill(x, y - 4, width, 16, focus == item ? SEGMENT_ON : SEGMENT);
        String shown = fit(canvas, label, width - 8);
        canvas.text(shown, x + (width - canvas.textWidth(shown)) / 2, y, focus == item ? VALUE : LABEL);
    }

    private static String fit(SceneCanvas canvas, String text, int maxWidth) {
        return TextLayout.ellipsis(text, Math.max(0, maxWidth), canvas::textWidth);
    }
}
