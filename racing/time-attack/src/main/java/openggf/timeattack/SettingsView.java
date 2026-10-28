package openggf.timeattack;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.net.protocol.ChatMode;
import com.openggf.mods.ui.NameFont;
import java.util.List;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The Time Attack settings page: multiplayer display name and look ({@link ProfileView}), host
 * port, master server URL, the development-only master TLS switch, the multiplayer minimap and
 * the retry key. Every change attempts immediate checked publication; failed saves remain active
 * and retry.
 */
public final class SettingsView {

    enum Row { DISPLAY_NAME, LOOK, HOST_PORT, MASTER_URL, MASTER_TLS, MINIMAP, RETRY_KEY, CHAT_MODE, BACK }

    private final TimeAttackSettings settings;
    private final Runnable changed;
    private final Consumer<MenuCue> cues;
    private final String gameId;
    private final String character;
    private final int[] stockLine;
    private ProfileView profile;
    private Row focus = Row.DISPLAY_NAME;
    private TextEntry editor;
    private Row editing;
    private boolean capturingKey;
    private String status = "";
    private boolean closeRequested;
    private boolean pad;

    /**
     * @param changed runs after every change (the owner saves and applies the settings)
     */
    public SettingsView(TimeAttackSettings settings, Runnable changed, Consumer<MenuCue> cues) {
        this(settings, changed, cues, null, null);
    }

    /**
     * @param gameId    the game chosen in the menu, whose character the look's palette is for (may be null)
     * @param character the character chosen in the menu (may be null)
     */
    public SettingsView(TimeAttackSettings settings, Runnable changed, Consumer<MenuCue> cues,
                        String gameId, String character) {
        this(settings, changed, cues, gameId, character, null);
    }

    /** @param stockLine the character's ROM palette line for the look page ({@link ProfileView}), or null */
    public SettingsView(TimeAttackSettings settings, Runnable changed, Consumer<MenuCue> cues,
                        String gameId, String character, int[] stockLine) {
        this.stockLine = stockLine;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.changed = Objects.requireNonNull(changed, "changed");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.gameId = gameId;
        this.character = character;
    }

    public void update(ViewInput input) {
        pad = MenuPage.controller(input, pad);
        if (profile != null) {
            profile.update(input);
            if (profile.consumeCloseRequested()) {
                profile = null;
            }
            return;
        }
        if (capturingKey) {
            if (input.keyPressed(com.openggf.mods.scene.SceneKeys.ESCAPE)) {
                capturingKey = false;
                cues.accept(MenuCue.CANCEL);
                return;
            }
            for (int key = 32; key <= 348; key++) {
                if (RetryKey.supported(key) && input.keyPressed(key)) {
                    settings.setRetryKey(key);
                    capturingKey = false;
                    changed();
                    return;
                }
            }
            return;
        }
        if (editor != null) {
            switch (editor.update(input)) {
                case ACCEPTED -> acceptEdit();
                case CANCELLED -> {
                    editor = null;
                    cues.accept(MenuCue.CANCEL);
                }
                case NONE -> { }
            }
            return;
        }
        if (input.back()) {
            closeRequested = true;
            cues.accept(MenuCue.CANCEL);
            return;
        }
        Row before = focus;
        Row[] rows = Row.values();
        if (input.up()) focus = rows[Math.floorMod(focus.ordinal() - 1, rows.length)];
        if (input.down()) focus = rows[(focus.ordinal() + 1) % rows.length];
        if (focus != before) {
            status = "";
            cues.accept(MenuCue.NAVIGATE);
        }
        boolean toggle = input.left() || input.right() || input.accept();
        switch (focus) {
            case MASTER_TLS -> {
                if (toggle) {
                    settings.setMasterTrustInsecure(!settings.masterTrustInsecure());
                    changed();
                }
            }
            case MINIMAP -> {
                if (toggle) {
                    settings.setMinimap(!settings.minimap());
                    changed();
                }
            }
            case CHAT_MODE -> {
                if (toggle) {
                    ChatMode[] modes = ChatMode.values();
                    int step = input.left() ? -1 : 1;
                    settings.setChatMode(modes[Math.floorMod(settings.chatMode().ordinal() + step, modes.length)]);
                    changed();
                }
            }
            case LOOK -> {
                if (input.accept()) {
                    profile = new ProfileView(settings, gameId, character, stockLine, this::changed, cues);
                    cues.accept(MenuCue.CONFIRM);
                }
            }
            case RETRY_KEY -> {
                if (input.accept()) {
                    capturingKey = true;
                    cues.accept(MenuCue.CONFIRM);
                }
            }
            case BACK -> {
                if (input.accept()) {
                    closeRequested = true;
                    cues.accept(MenuCue.CANCEL);
                }
            }
            default -> {
                if (input.accept()) {
                    openEditor(focus);
                    cues.accept(MenuCue.CONFIRM);
                }
            }
        }
    }

    private void openEditor(Row row) {
        editing = row;
        editor = switch (row) {
            case DISPLAY_NAME -> TextEntry.name("DISPLAY NAME", settings.displayName())
                    .withPrompt("Shown to other racers, in any script");
            case HOST_PORT -> new TextEntry("HOST PORT (1-65535)", Integer.toString(settings.hostPort()), 5, "");
            case MASTER_URL -> new TextEntry("MASTER SERVER URL", settings.masterUrl(),
                    TimeAttackSettings.MAX_TEXT_LENGTH, ".:/-_");
            default -> throw new IllegalStateException("Row " + row + " has no editor");
        };
    }

    private void acceptEdit() {
        String value = editor.text();
        Row row = editing;
        editor = null;
        editing = null;
        switch (row) {
            case DISPLAY_NAME -> settings.setDisplayName(value); // canonical: the field only accepts valid names
            case MASTER_URL -> settings.setMasterUrl(value);
            case HOST_PORT -> {
                int port;
                try {
                    port = Integer.parseInt(value);
                    settings.setHostPort(port);
                } catch (IllegalArgumentException e) {
                    status = "Port must be 1-65535";
                    cues.accept(MenuCue.ERROR);
                    return;
                }
            }
            default -> { }
        }
        changed();
    }

    private void changed() {
        changed.run();
        status = "Changed";
        cues.accept(MenuCue.CONFIRM);
    }

    /** True once after the player backs out of the page. */
    public boolean consumeCloseRequested() {
        boolean result = closeRequested;
        closeRequested = false;
        return result;
    }

    /** True while a text field (name, port, master URL) takes keyboard text. */
    public boolean isEditingText() {
        return editor != null || capturingKey || (profile != null && profile.isEditingText());
    }

    Row focusedRow() {
        return focus;
    }

    public void draw(SceneCanvas canvas) {
        int width = canvas.width();
        if (capturingKey) {
            MenuPage.page(canvas, "RETRY KEY", null, "Press the key that restarts the act");
            int y = MenuPage.BODY_TOP + 6;
            NameFont.draw(canvas, "Current key", MenuPage.MARGIN + 6, y, MenuPage.MUTED);
            MenuPage.cap(canvas, MenuPage.MARGIN + 6 + NameFont.width("Current key") + 8, y + 1,
                    RetryKey.label(settings.retryKey()));
            MenuPage.notice(canvas, MenuPage.Tone.INFO, "Letters, digits and function keys can be used.",
                    MenuPage.MARGIN + 6, y + 22, width - 28);
            MenuPage.footer(canvas, "", MenuPage.TEXT, List.of(MenuPage.hint("ESC", "Cancel")));
            return;
        }
        if (editor != null) {
            editor.draw(canvas);
            return;
        }
        if (profile != null) {
            profile.draw(canvas);
            return;
        }
        MenuPage.page(canvas, "SETTINGS", "Time Attack", "Your name, controls and multiplayer");
        List<String> labels = List.of("Name", "Look", "Host port", "Master server", "Master TLS", "Minimap",
                "Retry key", "Room chat");
        int valueX = MenuPage.MARGIN + 6 + MenuPage.labelColumn(labels);
        if (settings.displayName().isBlank()) {
            row(canvas, Row.DISPLAY_NAME, valueX, "Name", "Not set (identity prefix)", MenuPage.DIM, false);
        } else {
            row(canvas, Row.DISPLAY_NAME, valueX, "Name", "", MenuPage.TEXT, false);
            NameFont.draw(canvas, NameFont.fit(settings.displayName(), width - valueX - 14), valueX,
                    y(Row.DISPLAY_NAME), settings.appearanceFor(gameId, character).nameArgb());
        }
        row(canvas, Row.LOOK, valueX, "Look", LookPresets.label(settings, gameId, character), MenuPage.TEXT, false);
        row(canvas, Row.HOST_PORT, valueX, "Host port", Integer.toString(settings.hostPort()), MenuPage.TEXT, false);
        row(canvas, Row.MASTER_URL, valueX, "Master server",
                settings.masterUrl().isBlank() ? "Not set" : settings.masterUrl(),
                settings.masterUrl().isBlank() ? MenuPage.DIM : MenuPage.TEXT, false);
        row(canvas, Row.MASTER_TLS, valueX, "Master TLS",
                settings.masterTrustInsecure() ? "Trust any (local testing)" : "Verify certificate",
                settings.masterTrustInsecure() ? MenuPage.AMBER : MenuPage.TEXT, true);
        row(canvas, Row.MINIMAP, valueX, "Minimap", settings.minimap() ? "On" : "Off", MenuPage.TEXT, true);
        row(canvas, Row.RETRY_KEY, valueX, "Retry key", "", MenuPage.TEXT, false);
        MenuPage.cap(canvas, valueX, y(Row.RETRY_KEY) + 1, RetryKey.label(settings.retryKey()));
        row(canvas, Row.CHAT_MODE, valueX, "Room chat", MenuPage.words(settings.chatMode().label()), MenuPage.TEXT, true);
        MenuPage.action(canvas, y(Row.BACK), "Back", focus == Row.BACK, true);
        String outcome = settings.publicationNotice();
        // Validation feedback takes precedence; publication feedback survives navigation and retry.
        String message = status.startsWith("Port ") || outcome.isEmpty() ? status : outcome;
        int noteY = y(Row.BACK) + MenuPage.ROW_H + 5;
        if (message.isEmpty()) {
            MenuPage.notice(canvas, MenuPage.Tone.INFO, "Chat filters and hiding are in Engine Settings",
                    MenuPage.MARGIN + 6, noteY, width - 28);
        } else {
            MenuPage.notice(canvas, tone(message), message, MenuPage.MARGIN + 6, noteY, width - 28);
        }
        List<MenuPage.Hint> hints = new java.util.ArrayList<>();
        hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"));
        switch (focus) {
            case MASTER_TLS, MINIMAP, CHAT_MODE -> hints.add(MenuPage.hint(MenuPage.ARROWS_LR, "Change"));
            case LOOK -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Open"));
            case RETRY_KEY -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Rebind"));
            case BACK -> { }
            default -> hints.add(MenuPage.hint(MenuPage.confirm(pad), "Edit"));
        }
        hints.add(MenuPage.hint(MenuPage.back(pad), "Back"));
        MenuPage.footer(canvas, "", MenuPage.TEXT, hints);
    }

    private static MenuPage.Tone tone(String message) {
        if (message.startsWith("Port ")) return MenuPage.Tone.ERROR;
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (message.equals("Changed") || lower.contains("saved") && !lower.contains("not")) return MenuPage.Tone.GOOD;
        return MenuPage.Tone.WARNING;
    }

    private static int y(Row row) {
        return MenuPage.BODY_TOP - 2 + row.ordinal() * MenuPage.ROW_H;
    }

    private void row(SceneCanvas canvas, Row row, int valueX, String label, String value, int valueColour,
                     boolean stepper) {
        MenuPage.field(canvas, y(row), valueX, label, value, valueColour, focus == row, stepper);
    }
}
