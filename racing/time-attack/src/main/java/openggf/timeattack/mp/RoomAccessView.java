package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.NameFont;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.FullTextReader;
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
    private final FullTextReader reader = new FullTextReader();
    private Focus focus = Focus.ACCESS;
    private boolean privateRoom;
    private boolean membersCanInvite = true;
    private boolean editing;
    /** Created or cancelled: later presses must not create a second room. */
    private boolean finished;
    private String notice = "";
    private boolean pad;

    public RoomAccessView(Actions actions, Consumer<MenuCue> cues) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.cues = Objects.requireNonNull(cues, "cues");
    }

    public void update(ViewInput input) {
        pad = MenuPage.controller(input, pad);
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
        if (reader.handle(input, this::detailsText)) return;
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
        if (reader.draw(canvas)) return;
        int width = canvas.width();
        MenuPage.page(canvas, "CREATE ROOM", null, "Choose who can join before the room opens");
        int y = MenuPage.BODY_TOP - 2;
        option(canvas, Focus.ACCESS, "Who can join", y, privateRoom ? "Hidden from the room browser; needs a password"
                : "Listed in the room browser", MenuPage.DIM);
        MenuPage.segments(canvas, width - MenuPage.MARGIN - 6, y + 1, "PUBLIC", "PRIVATE", privateRoom,
                focus == Focus.ACCESS);
        y += OPTION_H;
        if (privateRoom) {
            String problem = passwordProblem();
            option(canvas, Focus.PASSWORD, "Password", y, problem == null ? "Set. Choose to change it" : problem,
                    problem == null ? MenuPage.GREEN : MenuPage.AMBER);
            boolean set = !password.text().isEmpty();
            int right = width - MenuPage.MARGIN - 6;
            if (set) {
                // A fixed row of dots: the length is never shown.
                for (int i = 0; i < 8; i++) canvas.fill(right - 8 * 7 + i * 7, y + 5, 3, 3, MenuPage.TEXT);
                MenuPage.lock(canvas, right - 8 * 7 - 12, y + 2, MenuPage.CYAN);
            } else {
                NameFont.draw(canvas, "Not set", right - NameFont.width("Not set"), y, MenuPage.DIM);
            }
        } else {
            NameFont.draw(canvas, "Password", MenuPage.MARGIN + 6, y, MenuPage.FAINT);
            NameFont.draw(canvas, NameFont.fit("Not needed for a public room", width - 34), MenuPage.MARGIN + 6,
                    y + NameFont.LINE_HEIGHT, MenuPage.FAINT);
        }
        y += OPTION_H;
        option(canvas, Focus.INVITES, "Members can invite", y, membersCanInvite
                ? "Everyone in the room can share its code" : "Only you can share the room's code", MenuPage.DIM);
        MenuPage.segments(canvas, width - MenuPage.MARGIN - 6, y + 1, "ON", "OFF", !membersCanInvite,
                focus == Focus.INVITES);
        y += OPTION_H + 6;
        int half = (width - 2 * MenuPage.MARGIN - 6) / 2;
        MenuPage.button(canvas, MenuPage.MARGIN, y, half, "CREATE ROOM", focus == Focus.CREATE, true);
        MenuPage.button(canvas, MenuPage.MARGIN + half + 6, y, half, "BACK", focus == Focus.BACK, false);
        if (!notice.isEmpty()) {
            MenuPage.notice(canvas, MenuPage.Tone.WARNING, notice, MenuPage.MARGIN + 6, y + MenuPage.BUTTON_H + 6,
                    width - 28);
        }
        List<MenuPage.Hint> hints = new ArrayList<>();
        hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"));
        switch (focus) {
            case ACCESS, INVITES -> hints.add(MenuPage.hint(MenuPage.ARROWS_LR, "Change"));
            case PASSWORD -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Set password"));
            case CREATE -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Create"));
            case BACK -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Back"));
        }
        hints.add(MenuPage.hint(MenuPage.back(pad), "Back"));
        MenuPage.footerWithDetails(canvas, pad, "", MenuPage.TEXT, hints);
    }

    private static final int OPTION_H = 2 * NameFont.LINE_HEIGHT + 4;

    /** A two-line setting: its label, and under it what the current choice means. */
    private void option(SceneCanvas canvas, Focus item, String label, int y, String detail, int detailColour) {
        boolean focused = focus == item;
        if (focused) MenuPage.focus(canvas, MenuPage.MARGIN, y - 1, canvas.width() - 2 * MenuPage.MARGIN, OPTION_H - 2);
        NameFont.draw(canvas, label, MenuPage.MARGIN + 6, y, focused ? MenuPage.TEXT : MenuPage.MUTED);
        NameFont.draw(canvas, NameFont.fit(detail, canvas.width() - 2 * MenuPage.MARGIN - 12), MenuPage.MARGIN + 6,
                y + NameFont.LINE_HEIGHT, detailColour);
    }
    /** The page's complete text: each choice and what it means; the password never appears. */
    private FullTextReader.Text detailsText() {
        String problem = privateRoom ? passwordProblem() : null;
        return FullTextReader.Text.of("CREATE ROOM", "Choose who can join before the room opens")
                .field("Who can join", privateRoom ? "Private. Hidden from the room browser; needs a password"
                        : "Public. Listed in the room browser")
                .field("Password", !privateRoom ? "Not needed for a public room"
                        : problem == null ? "Set. Choose to change it" : problem)
                .field("Members can invite", membersCanInvite ? "On. Everyone in the room can share its code"
                        : "Off. Only you can share the room's code")
                .field("Status", notice);
    }

    List<String> detailsVisibleLines() { return reader.visibleLines(); }

    int detailsFirstLine() { return reader.firstVisibleLine(); }

}
