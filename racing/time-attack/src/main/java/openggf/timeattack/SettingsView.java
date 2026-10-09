package openggf.timeattack;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.TextLayout;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The Time Attack settings page: multiplayer display name, host port, master server URL, the
 * development-only master TLS switch and the multiplayer minimap. Every change is saved at once.
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
    private static final int ROW_HEIGHT = 20;

    enum Row { DISPLAY_NAME, HOST_PORT, MASTER_URL, MASTER_TLS, MINIMAP, BACK }

    private final TimeAttackSettings settings;
    private final Runnable changed;
    private final Consumer<MenuCue> cues;
    private Row focus = Row.DISPLAY_NAME;
    private TextEntry editor;
    private Row editing;
    private String status = "";
    private boolean closeRequested;

    /**
     * @param changed runs after every change (the owner saves and applies the settings)
     */
    public SettingsView(TimeAttackSettings settings, Runnable changed, Consumer<MenuCue> cues) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.changed = Objects.requireNonNull(changed, "changed");
        this.cues = Objects.requireNonNull(cues, "cues");
    }

    public void update(ViewInput input) {
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
            case DISPLAY_NAME -> new TextEntry("DISPLAY NAME", settings.displayName(),
                    TimeAttackSettings.MAX_DISPLAY_NAME_LENGTH, " -_.");
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
            case DISPLAY_NAME -> settings.setDisplayName(value);
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
        status = "Saved";
        changed.run();
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
        return editor != null;
    }

    Row focusedRow() {
        return focus;
    }

    public void draw(SceneCanvas canvas) {
        if (editor != null) {
            editor.draw(canvas);
            return;
        }
        int width = canvas.width();
        canvas.clear(BACKGROUND);
        canvas.text("TIME ATTACK SETTINGS", 10, 10, TITLE);
        canvas.text("Multiplayer racing", 10, 24, HINT);
        row(canvas, Row.DISPLAY_NAME, "Name",
                settings.displayName().isBlank() ? "(identity prefix)" : settings.displayName(), VALUE);
        row(canvas, Row.HOST_PORT, "Host port", Integer.toString(settings.hostPort()), VALUE);
        row(canvas, Row.MASTER_URL, "Master",
                settings.masterUrl().isBlank() ? "(not set)" : settings.masterUrl(), VALUE);
        row(canvas, Row.MASTER_TLS, "Master TLS",
                settings.masterTrustInsecure() ? "< TRUST ALL >" : "< VERIFY >",
                settings.masterTrustInsecure() ? WARNING : VALUE);
        row(canvas, Row.MINIMAP, "Minimap", settings.minimap() ? "< ON >" : "< OFF >", VALUE);
        int backY = FIRST_ROW_Y + Row.BACK.ordinal() * ROW_HEIGHT;
        if (focus == Row.BACK) canvas.fill(8, backY - 3, width - 16, 16, FOCUS);
        canvas.text("BACK", 14, backY, VALUE);
        if (!status.isEmpty()) {
            canvas.text(status, 10, 190, TITLE);
        }
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
