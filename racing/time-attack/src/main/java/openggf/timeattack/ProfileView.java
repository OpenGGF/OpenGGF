package openggf.timeattack;

import com.openggf.mods.run.CharacterLooks;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.TextLayout;
import com.openggf.net.identity.PlayerAppearance;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.NameText;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The player profile page: display name and look. The look is a base colour (the colour of the
 * player's name and ghost) chosen from {@link LookPresets} or set channel by channel over the Mega
 * Drive's 512 colours (three bits each of red, green and blue), plus an optional custom palette
 * for the character being raced: any of the 16 entries of its palette line can be given its own
 * colour. Without one the character's body ramp follows the base colour.
 *
 * <p>The page edits a copy; SAVE writes the name, base colour and this character's palette to
 * the settings and runs the owner's callback (which saves them and, in a race lobby, announces
 * the new profile). Back or CANCEL discards the copy.
 */
public final class ProfileView {
    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int DIM = 0xFF8C8C8C;
    private static final int FOCUS = 0x803060C0;
    private static final int HINT = 0xFF8090B0;
    private static final int PLATE = 0xE0060A14;
    private static final int STOCK_SWATCH = 0xFF283040;
    private static final int RAMP_MARK = 0xFFFFDD67;
    private static final int FIRST_ROW_Y = 60;
    private static final int ROW_HEIGHT = 14;
    private static final int SWATCH_Y = 174;
    /** Azure: the starting base colour when a custom edit begins from the stock look. */
    private static final int DEFAULT_BASE = 0x0E62;

    enum Row { NAME, PRESET, EDIT, RED, GREEN, BLUE, SAVE, CANCEL }

    private final TimeAttackSettings settings;
    private final String gameId;
    private final String character;
    private final Runnable saved;
    private final Consumer<MenuCue> cues;
    private final int[] ramp;
    /** The character's ROM palette line as Mega Drive colour words, or null when the ROM cannot supply it. */
    private final int[] stockLine;
    private String name;
    private int base;
    /** Entry index to colour; null while the palette follows the base colour. */
    private Map<Integer, Integer> custom;
    /** -1 edits the base colour, 1..15 a palette entry. */
    private int target = -1;
    private Row focus = Row.PRESET;
    private TextEntry nameEditor;
    private boolean closeRequested;
    private boolean savedOnce;

    /**
     * @param gameId    the game whose character the palette belongs to, or null
     * @param character the character being raced, or null when none is chosen (base colour only)
     * @param saved     runs after SAVE has written the settings
     */
    public ProfileView(TimeAttackSettings settings, String gameId, String character,
                       Runnable saved, Consumer<MenuCue> cues) {
        this(settings, gameId, character, null, saved, cues);
    }

    /**
     * @param stockLine the character's 16-entry ROM palette line as Mega Drive colour words, shown
     *                  for entries without an override and used as the starting colour when one is
     *                  edited; null when the ROM cannot supply it (stock entries then show as dots)
     */
    public ProfileView(TimeAttackSettings settings, String gameId, String character, int[] stockLine,
                       Runnable saved, Consumer<MenuCue> cues) {
        this.stockLine = stockLine == null || stockLine.length != 16 || character == null ? null : stockLine.clone();
        this.settings = Objects.requireNonNull(settings, "settings");
        this.gameId = gameId;
        this.character = gameId == null ? null : character;
        this.saved = Objects.requireNonNull(saved, "saved");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.ramp = this.character == null ? new int[0] : CharacterLooks.bodyRamp(gameId, this.character);
        name = settings.displayName();
        base = settings.lookBase();
        List<PlayerAppearance.Entry> stored = this.character == null ? null
                : settings.customPalette(gameId, this.character);
        if (stored != null) {
            custom = new TreeMap<>();
            stored.forEach(entry -> custom.put(entry.index(), entry.colour()));
        }
    }

    public void update(ViewInput input) {
        if (nameEditor != null) {
            switch (nameEditor.update(input)) {
                case ACCEPTED -> {
                    name = nameEditor.acceptedName();
                    nameEditor = null;
                    cues.accept(MenuCue.CONFIRM);
                }
                case CANCELLED -> {
                    nameEditor = null;
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
        Row[] rows = Row.values();
        Row before = focus;
        if (input.up()) focus = rows[Math.floorMod(focus.ordinal() - 1, rows.length)];
        if (input.down()) focus = rows[(focus.ordinal() + 1) % rows.length];
        if (focus != before) cues.accept(MenuCue.NAVIGATE);
        int delta = input.left() ? -1 : input.right() ? 1 : 0;
        switch (focus) {
            case NAME -> {
                if (input.accept()) {
                    nameEditor = TextEntry.name("DISPLAY NAME", name)
                            .withPrompt("Shown to other racers, in any script");
                    cues.accept(MenuCue.CONFIRM);
                }
            }
            case PRESET -> {
                if (delta != 0) {
                    stepPreset(delta);
                    cues.accept(MenuCue.NAVIGATE);
                }
            }
            case EDIT -> {
                if (delta != 0 && character != null) {
                    target = target + delta < -1 ? 15 : target + delta > 15 ? -1 : target + delta;
                    if (target == 0) target = delta > 0 ? 1 : -1; // entry 0 is transparent
                    cues.accept(MenuCue.NAVIGATE);
                }
                if (input.accept()) {
                    if (target > 0) {
                        restoreStock(target);
                        cues.accept(MenuCue.CONFIRM);
                    } else {
                        cues.accept(MenuCue.ERROR);
                    }
                }
            }
            case RED, GREEN, BLUE -> {
                if (delta != 0) {
                    if (adjust(focus, delta)) cues.accept(MenuCue.NAVIGATE);
                }
            }
            case SAVE -> {
                if (input.accept()) save();
            }
            case CANCEL -> {
                if (input.accept()) {
                    closeRequested = true;
                    cues.accept(MenuCue.CANCEL);
                }
            }
        }
    }

    private void stepPreset(int delta) {
        List<LookPresets.Preset> presets = LookPresets.all();
        LookPresets.Preset current = custom == null ? LookPresets.forBase(base) : null;
        int index = current == null ? (delta > 0 ? -1 : presets.size()) : presets.indexOf(current);
        LookPresets.Preset next = presets.get(Math.floorMod(index + delta, presets.size()));
        base = next.baseColour();
        custom = null;
        target = -1;
    }

    /** Moves one channel of the edited colour; false at the channel's limit. */
    private boolean adjust(Row channel, int delta) {
        int shift = switch (channel) {
            case RED -> 1;
            case GREEN -> 5;
            default -> 9;
        };
        if (base == PlayerAppearance.NO_BASE_COLOUR) base = DEFAULT_BASE;
        int colour = target < 0 ? base : appearance().colourAt(target, stockLine != null ? stockLine[target] : base);
        int level = (colour >> shift) & 7;
        int moved = Math.clamp(level + delta, 0, 7);
        if (moved == level) return false;
        int updated = (colour & ~(7 << shift)) | moved << shift;
        if (target < 0) {
            base = updated;
        } else {
            materializeCustom();
            custom.put(target, updated);
        }
        return true;
    }

    private void restoreStock(int index) {
        if (base == PlayerAppearance.NO_BASE_COLOUR) return;
        materializeCustom();
        custom.remove(index);
    }

    /** Turns a preset ramp into explicit entries so single entries can change. */
    private void materializeCustom() {
        if (custom != null) return;
        List<PlayerAppearance.Entry> ramp = appearance().palette(); // read before custom stops being null
        custom = new TreeMap<>();
        for (PlayerAppearance.Entry entry : ramp) custom.put(entry.index(), entry.colour());
    }

    private void save() {
        settings.setDisplayName(name);
        settings.setLookBase(base);
        if (character != null) {
            settings.setCustomPalette(gameId, character, custom == null || base == PlayerAppearance.NO_BASE_COLOUR
                    ? null : entries());
        }
        savedOnce = true;
        closeRequested = true;
        cues.accept(MenuCue.CONFIRM);
        saved.run();
    }

    private List<PlayerAppearance.Entry> entries() {
        List<PlayerAppearance.Entry> list = new ArrayList<>();
        custom.forEach((index, colour) -> list.add(new PlayerAppearance.Entry(index, colour)));
        return list;
    }

    /** The look being edited, in canonical form. */
    public PlayerAppearance appearance() {
        if (base == PlayerAppearance.NO_BASE_COLOUR) return PlayerAppearance.STOCK;
        if (custom != null) return new PlayerAppearance(base, entries());
        if (character == null) return new PlayerAppearance(base, List.of());
        return CharacterLooks.withBaseColour(gameId, character, base);
    }

    /** The name being edited. */
    public String name() {
        return name;
    }

    /** True once after the page closes. */
    public boolean consumeCloseRequested() {
        boolean result = closeRequested;
        closeRequested = false;
        return result;
    }

    /** True when the page closed through SAVE. */
    public boolean wasSaved() {
        return savedOnce;
    }

    /** True while the name field takes keyboard text. */
    public boolean isEditingText() {
        return nameEditor != null;
    }

    Row focusedRow() {
        return focus;
    }

    public void draw(SceneCanvas canvas) {
        if (nameEditor != null) {
            nameEditor.draw(canvas);
            return;
        }
        int width = canvas.width();
        PlayerAppearance look = appearance();
        canvas.clear(BACKGROUND);
        canvas.text("PLAYER PROFILE", 10, 10, TITLE);
        canvas.text(fit(canvas, character == null ? "Name and colour"
                : gameId.toUpperCase() + " " + character.toUpperCase() + " look", width - 20), 10, 24, HINT);
        // The name as other racers will see it over the ghost.
        canvas.fill(8, 37, width - 16, 18, PLATE);
        canvas.fill(8, 37, 3, 18, swatch(base == PlayerAppearance.NO_BASE_COLOUR ? -1 : base));
        if (name.isEmpty()) {
            canvas.text("(no name yet)", 16, 41, DIM);
        } else {
            NameText.shadowed(canvas, NameFont.fit(name, width - 36), 16, 40, look.nameArgb());
        }
        row(canvas, Row.NAME, "Name", name.isEmpty() ? "Select to enter" : "Change", VALUE);
        row(canvas, Row.PRESET, "Look", "< " + lookLabel() + " >", VALUE);
        String editing = target < 0 ? "NAME COLOUR" : "ENTRY " + target + (inRamp(target) ? " BODY" : "");
        row(canvas, Row.EDIT, "Edit", character == null ? "NAME COLOUR" : "< " + editing + " >", VALUE);
        int colour = target < 0 ? base : look.colourAt(target, stock(target));
        String[] channels = {"Red", "Green", "Blue"};
        int[] shifts = {1, 5, 9};
        for (int c = 0; c < 3; c++) {
            String value = colour < 0 ? "< - >" : "< " + ((colour >> shifts[c]) & 7) + " >";
            row(canvas, Row.values()[Row.RED.ordinal() + c], channels[c], value, colour < 0 ? DIM : VALUE);
        }
        int swatchX = width - 64;
        int swatchY = FIRST_ROW_Y + Row.RED.ordinal() * ROW_HEIGHT - 3;
        canvas.fill(swatchX - 1, swatchY - 1, 50, 44, 0xFF000000);
        canvas.fill(swatchX, swatchY, 48, 42, swatch(colour));
        row(canvas, Row.SAVE, "SAVE", "", VALUE);
        row(canvas, Row.CANCEL, "CANCEL", "", VALUE);
        drawPalette(canvas, look);
        String hint = switch (focus) {
            case EDIT -> target > 0 ? "Left/Right entry  Enter use stock  B cancel" : "Left/Right pick entry  B cancel";
            case RED, GREEN, BLUE -> "Left/Right 0-7  B cancel";
            default -> "Up/Down select  Left/Right change  B cancel";
        };
        canvas.text(fit(canvas, hint, width - 20), 10, 210, HINT);
    }

    /** The character's 16-colour line: overridden entries in colour, stock ones dark, body ramp marked. */
    private void drawPalette(SceneCanvas canvas, PlayerAppearance look) {
        if (character == null) return;
        int x0 = Math.max(8, (canvas.width() - 16 * 18) / 2);
        for (int index = 0; index < 16; index++) {
            int x = x0 + index * 18;
            int override = index == 0 ? -1 : look.colourAt(index, -1);
            int colour = override >= 0 ? override : index == 0 ? -1 : stock(index);
            if (index == target) canvas.fill(x - 2, SWATCH_Y - 2, 18, 16, VALUE);
            canvas.fill(x - 1, SWATCH_Y - 1, 16, 14, 0xFF000000);
            canvas.fill(x, SWATCH_Y, 14, 12, colour < 0 ? STOCK_SWATCH : swatch(colour));
            if (override < 0 && index > 0) {
                canvas.fill(x + 5, SWATCH_Y + 4, 4, 4, 0xFF000000); // follows the ROM
                canvas.fill(x + 6, SWATCH_Y + 5, 2, 2, DIM);
            }
            if (inRamp(index)) canvas.fill(x + 3, SWATCH_Y + 14, 8, 2, RAMP_MARK);
        }
        canvas.text(fit(canvas, "dot = ROM colour  gold = body", canvas.width() - 20), 10, SWATCH_Y + 19, DIM);
    }

    private String lookLabel() {
        if (base == PlayerAppearance.NO_BASE_COLOUR) return "Classic";
        if (custom != null) return "Custom palette";
        LookPresets.Preset preset = LookPresets.forBase(base);
        return preset != null ? preset.name() : "Custom colour";
    }

    private int stock(int index) {
        return stockLine == null ? -1 : stockLine[index];
    }

    private boolean inRamp(int index) {
        for (int entry : ramp) if (entry == index) return true;
        return false;
    }

    private static int swatch(int colour) {
        return colour < 0 ? STOCK_SWATCH : 0xFF000000 | PlayerAppearance.rgb(colour);
    }

    private void row(SceneCanvas canvas, Row row, String label, String value, int valueColour) {
        int y = FIRST_ROW_Y + row.ordinal() * ROW_HEIGHT;
        boolean channel = row == Row.RED || row == Row.GREEN || row == Row.BLUE;
        if (focus == row) canvas.fill(8, y - 3, canvas.width() - (channel ? 76 : 16), ROW_HEIGHT, FOCUS);
        canvas.text(label, 14, y, LABEL);
        // Channel rows leave room for the colour swatch on the right.
        int room = canvas.width() - 96 - (channel ? 76 : 10);
        if (!value.isEmpty()) canvas.text(fit(canvas, value, room), 96, y, valueColour);
    }

    private static String fit(SceneCanvas canvas, String text, int maxWidth) {
        return TextLayout.ellipsis(text, Math.max(0, maxWidth), canvas::textWidth);
    }
}
