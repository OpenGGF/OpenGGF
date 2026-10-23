package openggf.timeattack;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.TextLayout;
import openggf.timeattack.ui.MenuCue;
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
    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int WARNING = 0xFFFF8060;
    private static final int FOCUS = 0x803060C0;
    private static final int HINT = 0xFF8090B0;
    private static final int FIRST_ROW_Y = 44;
    private static final int ROW_HEIGHT = 18;

    enum Row { DISPLAY_NAME, LOOK, HOST_PORT, MASTER_URL, MASTER_TLS, MINIMAP, RETRY_KEY, BACK }

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
        if (capturingKey) {
            canvas.clear(BACKGROUND);
            canvas.text("RETRY KEY", 10, 10, TITLE);
            canvas.text("Press a key to retry an act", 10, 60, VALUE);
            canvas.text("Escape cancels", 10, 208, HINT);
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
        int width = canvas.width();
        canvas.clear(BACKGROUND);
        canvas.text("TIME ATTACK SETTINGS", 10, 10, TITLE);
        canvas.text("Run controls and multiplayer", 10, 24, HINT);
        if (settings.displayName().isBlank()) {
            row(canvas, Row.DISPLAY_NAME, "Name", "(identity prefix)", VALUE);
        } else {
            row(canvas, Row.DISPLAY_NAME, "Name", "", VALUE);
            NameFont.draw(canvas, NameFont.fit(settings.displayName(), width - 132), 120,
                    FIRST_ROW_Y + Row.DISPLAY_NAME.ordinal() * ROW_HEIGHT - 2,
                    settings.appearanceFor(gameId, character).nameArgb());
        }
        row(canvas, Row.LOOK, "Look", LookPresets.label(settings, gameId, character), VALUE);
        row(canvas, Row.HOST_PORT, "Host port", Integer.toString(settings.hostPort()), VALUE);
        row(canvas, Row.MASTER_URL, "Master",
                settings.masterUrl().isBlank() ? "(not set)" : settings.masterUrl(), VALUE);
        row(canvas, Row.MASTER_TLS, "Master TLS",
                settings.masterTrustInsecure() ? "< TRUST ALL >" : "< VERIFY >",
                settings.masterTrustInsecure() ? WARNING : VALUE);
        row(canvas, Row.MINIMAP, "Minimap", settings.minimap() ? "< ON >" : "< OFF >", VALUE);
        row(canvas, Row.RETRY_KEY, "Retry key", RetryKey.label(settings.retryKey()), VALUE);
        int backY = FIRST_ROW_Y + Row.BACK.ordinal() * ROW_HEIGHT;
        if (focus == Row.BACK) canvas.fill(8, backY - 3, width - 16, 16, FOCUS);
        canvas.text("BACK", 14, backY, VALUE);
        String outcome = settings.publicationNotice();
        // Validation feedback takes precedence; publication feedback survives navigation and retry.
        String message = status.startsWith("Port ") || outcome.isEmpty() ? status : outcome;
        if (!message.isEmpty()) canvas.text(message, 10, 194, TITLE);
        canvas.text(TextLayout.ellipsis("Up/Down select  Enter edit  B back", width - 20, canvas::textWidth),
                10, 208, HINT);
    }

    private void row(SceneCanvas canvas, Row row, String label, String value, int valueColour) {
        int y = FIRST_ROW_Y + row.ordinal() * ROW_HEIGHT;
        if (focus == row) canvas.fill(8, y - 3, canvas.width() - 16, 16, FOCUS);
        canvas.text(label, 14, y, LABEL);
        canvas.text(TextLayout.ellipsis(value, canvas.width() - 132, canvas::textWidth), 120, y, valueColour);
    }
}
