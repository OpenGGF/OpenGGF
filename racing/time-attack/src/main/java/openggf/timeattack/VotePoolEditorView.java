package openggf.timeattack;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.TextLayout;
import openggf.timeattack.mp.VoteTrackPools;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The room owner's vote-pool editor for one game (the menu's game, so only games whose ROM is
 * available). The list page shows the built-in default pool and the saved custom pools; NEW POOL
 * and the default row start a pool from the default pool's acts. The edit page names the pool and
 * adds or removes any act of {@link TimeAttackTrackCatalog#eligibleFor} &mdash; the acts of this
 * game that end in a results tally &mdash; then saves it in {@link TimeAttackSettings}. A pool
 * holds {@value VoteTrackPools#MIN_KEYS} to {@value VotePool#MAX_ACTS} acts: rooms carry at most
 * 32 vote tracks, and a vote offers three acts other than the one just raced.
 */
public final class VotePoolEditorView {
    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int OFF = 0xFF7080A0;
    private static final int FOCUS = 0x803060C0;
    private static final int HINT = 0xFF8090B0;
    private static final int WARNING = 0xFFFF8060;
    private static final int FIRST_ROW_Y = 44;
    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 11;

    private final TimeAttackSettings settings;
    private final String gameId;
    private final Runnable changed;
    private final Consumer<MenuCue> cues;
    private final List<TimeAttackTrackCatalog.Track> eligible;
    private final List<String> gameCharacters;
    private boolean closeRequested;
    private int focus;
    private int scroll;
    private String status = "";

    // Edit page state; null editing means the list page is shown.
    private String editing;
    private boolean editingNew;
    private String draftName;
    private final Set<String> draftActs = new LinkedHashSet<>();
    private TextEntry nameEditor;

    /**
     * @param gameId  the room's game; the editor lists only its acts
     * @param changed runs after a pool is saved or deleted (the owner saves the settings)
     */
    public VotePoolEditorView(TimeAttackSettings settings, String gameId, Runnable changed, Consumer<MenuCue> cues) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.changed = Objects.requireNonNull(changed, "changed");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.eligible = TimeAttackTrackCatalog.eligibleFor(gameId);
        Set<String> characters = new LinkedHashSet<>();
        eligible.forEach(track -> characters.addAll(track.characters()));
        this.gameCharacters = List.copyOf(characters);
    }

    public void update(ViewInput input) {
        if (nameEditor != null) {
            switch (nameEditor.update(input)) {
                case ACCEPTED -> {
                    draftName = nameEditor.acceptedName();
                    nameEditor = null;
                    status = "";
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
        if (editing == null) {
            updateList(input);
        } else {
            updateEdit(input);
        }
    }

    // ── List page: DEFAULT, saved pools, NEW POOL, BACK ──────────────────

    private List<String> listRows() {
        List<String> rows = new ArrayList<>();
        rows.add("DEFAULT");
        settings.votePools(gameId).forEach(pool -> rows.add("pool:" + pool.name()));
        if (settings.votePools(gameId).size() < TimeAttackSettings.MAX_VOTE_POOLS_PER_GAME) rows.add("NEW");
        rows.add("BACK");
        return rows;
    }

    private void updateList(ViewInput input) {
        if (input.back()) {
            closeRequested = true;
            cues.accept(MenuCue.CANCEL);
            return;
        }
        List<String> rows = listRows();
        moveFocus(input, rows.size());
        if (!input.accept()) return;
        String row = rows.get(focus);
        switch (row) {
            case "BACK" -> {
                closeRequested = true;
                cues.accept(MenuCue.CANCEL);
            }
            case "DEFAULT", "NEW" -> openNew();
            default -> openExisting(row.substring("pool:".length()));
        }
    }

    private void openNew() {
        editing = "";
        editingNew = true;
        draftName = freshName();
        draftActs.clear();
        for (String key : VoteTrackPools.forGame(gameId)) draftActs.add(key.substring(gameId.length() + 1));
        enterEditPage();
    }

    private void openExisting(String name) {
        VotePool pool = settings.votePool(gameId, name).orElse(null);
        if (pool == null) return;
        editing = name;
        editingNew = false;
        draftName = name;
        draftActs.clear();
        draftActs.addAll(pool.acts());
        enterEditPage();
    }

    private void enterEditPage() {
        focus = 0;
        scroll = 0;
        status = "";
        cues.accept(MenuCue.CONFIRM);
    }

    private String freshName() {
        for (int n = 1; ; n++) {
            String name = "POOL " + n;
            if (settings.votePool(gameId, name).isEmpty()) return name;
        }
    }

    // ── Edit page: NAME, one row per eligible act, SAVE, DELETE, CANCEL ──

    private List<String> editRows() {
        List<String> rows = new ArrayList<>();
        rows.add("NAME");
        eligible.forEach(track -> rows.add("act:" + track.zone() + ":" + track.act()));
        rows.add("SAVE");
        if (!editingNew) rows.add("DELETE");
        rows.add("CANCEL");
        return rows;
    }

    private void updateEdit(ViewInput input) {
        if (input.back()) {
            leaveEdit();
            cues.accept(MenuCue.CANCEL);
            return;
        }
        List<String> rows = editRows();
        moveFocus(input, rows.size());
        String row = rows.get(focus);
        if (row.startsWith("act:")) {
            if (input.accept() || input.left() || input.right()) toggle(row.substring("act:".length()));
            return;
        }
        if (!input.accept()) return;
        switch (row) {
            case "NAME" -> nameEditor = TextEntry.name("POOL NAME", draftName)
                    .withPrompt("Shown when you choose a pool for a room");
            case "SAVE" -> save();
            case "DELETE" -> {
                settings.removeVotePool(gameId, editing);
                changed.run();
                leaveEdit();
                status = "Pool deleted";
                cues.accept(MenuCue.CONFIRM);
            }
            case "CANCEL" -> {
                leaveEdit();
                cues.accept(MenuCue.CANCEL);
            }
            default -> { }
        }
    }

    private void toggle(String act) {
        if (draftActs.remove(act)) {
            status = "";
            cues.accept(MenuCue.NAVIGATE);
            return;
        }
        if (draftActs.size() >= VotePool.MAX_ACTS) {
            status = "A pool holds at most " + VotePool.MAX_ACTS + " acts (the room limit)";
            cues.accept(MenuCue.ERROR);
            return;
        }
        draftActs.add(act);
        status = "";
        cues.accept(MenuCue.NAVIGATE);
    }

    private void save() {
        // Saved in the catalog's game order, the order votes rotate through.
        List<String> acts = new ArrayList<>();
        eligible.forEach(track -> {
            String act = track.zone() + ":" + track.act();
            if (draftActs.contains(act)) acts.add(act);
        });
        if (acts.size() < VoteTrackPools.MIN_KEYS) {
            status = "Choose at least " + VoteTrackPools.MIN_KEYS + " acts so a vote has a choice";
            cues.accept(MenuCue.ERROR);
            return;
        }
        try {
            settings.putVotePool(gameId, editingNew ? null : editing, new VotePool(draftName, acts));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            status = refused.getMessage();
            cues.accept(MenuCue.ERROR);
            return;
        }
        changed.run();
        String saved = draftName;
        leaveEdit();
        status = "Saved " + saved;
        cues.accept(MenuCue.CONFIRM);
    }

    private void leaveEdit() {
        editing = null;
        nameEditor = null;
        focus = 0;
        scroll = 0;
        status = "";
    }

    private void moveFocus(ViewInput input, int count) {
        int before = focus;
        if (input.up()) focus = Math.floorMod(focus - 1, count);
        if (input.down()) focus = (focus + 1) % count;
        focus = Math.min(focus, count - 1);
        if (focus != before) {
            cues.accept(MenuCue.NAVIGATE);
            if (editing == null) status = "";
        }
        if (focus < scroll) scroll = focus;
        if (focus >= scroll + VISIBLE_ROWS) scroll = focus - VISIBLE_ROWS + 1;
    }

    /** True once after the player leaves the editor. */
    public boolean consumeCloseRequested() {
        boolean result = closeRequested;
        closeRequested = false;
        return result;
    }

    /** True while the pool name field takes keyboard text. */
    public boolean isEditingText() {
        return nameEditor != null;
    }

    boolean isEditingPool() {
        return editing != null;
    }

    String status() {
        return status;
    }

    int focusIndex() {
        return focus;
    }

    // ── Drawing ──────────────────────────────────────────────────────────

    public void draw(SceneCanvas canvas) {
        if (nameEditor != null) {
            nameEditor.draw(canvas);
            return;
        }
        canvas.clear(BACKGROUND);
        if (editing == null) {
            drawList(canvas);
        } else {
            drawEdit(canvas);
        }
    }

    private void drawList(SceneCanvas canvas) {
        int width = canvas.width();
        canvas.text("VOTE POOLS - " + gameId.toUpperCase(), 10, 10, TITLE);
        canvas.text(fit(canvas, "A vote offers 3 pool acts", width - 20), 10, 24, HINT);
        List<String> rows = listRows();
        String chosen = settings.votePoolChoice(gameId);
        for (int i = scroll; i < rows.size() && i < scroll + VISIBLE_ROWS; i++) {
            int y = FIRST_ROW_Y + (i - scroll) * (ROW_HEIGHT + 1);
            if (i == focus) canvas.fill(8, y - 2, width - 16, ROW_HEIGHT + 1, FOCUS);
            String row = rows.get(i);
            switch (row) {
                case "DEFAULT" -> {
                    canvas.text("DEFAULT", 14, y, VALUE);
                    canvas.text(fit(canvas, VoteTrackPools.forGame(gameId).size() + " acts"
                            + (chosen.isEmpty() ? " HOSTING" : ""), width - 132), 120, y, LABEL);
                }
                case "NEW" -> canvas.text("NEW POOL", 14, y, VALUE);
                case "BACK" -> canvas.text("BACK", 14, y, VALUE);
                default -> {
                    String name = row.substring("pool:".length());
                    NameFont.draw(canvas, NameFont.fit(name, 100), 14, y - 2, VALUE);
                    int count = settings.votePool(gameId, name).map(pool -> pool.acts().size()).orElse(0);
                    canvas.text(fit(canvas, count + " acts" + (name.equals(chosen) ? " HOSTING" : ""),
                            width - 132), 120, y, LABEL);
                }
            }
        }
        if (!status.isEmpty()) canvas.text(fit(canvas, status, width - 20), 10, 194, TITLE);
        canvas.text(fit(canvas, "NEW copies the default pool", width - 20), 10, 180, HINT);
        canvas.text(fit(canvas, "Enter open  B back", width - 20), 10, 208, HINT);
    }

    private void drawEdit(SceneCanvas canvas) {
        int width = canvas.width();
        canvas.text((editingNew ? "NEW POOL - " : "EDIT POOL - ") + gameId.toUpperCase(), 10, 10, TITLE);
        boolean tooFew = draftActs.size() < VoteTrackPools.MIN_KEYS;
        canvas.text(fit(canvas, draftActs.size() + "/" + VotePool.MAX_ACTS + " acts"
                + (tooFew ? " - choose at least " + VoteTrackPools.MIN_KEYS : ""), width - 20),
                10, 24, tooFew ? WARNING : HINT);
        canvas.text(fit(canvas, "Rooms carry at most " + VotePool.MAX_ACTS + " acts", width - 20), 10, 180, HINT);
        List<String> rows = editRows();
        for (int i = scroll; i < rows.size() && i < scroll + VISIBLE_ROWS; i++) {
            int y = FIRST_ROW_Y + (i - scroll) * ROW_HEIGHT;
            if (i == focus) canvas.fill(8, y - 2, width - 16, ROW_HEIGHT, FOCUS);
            String row = rows.get(i);
            if (row.equals("NAME")) {
                canvas.text("Name", 14, y, LABEL);
                NameFont.draw(canvas, NameFont.fit(draftName, width - 132), 120, y - 2, VALUE);
            } else if (row.startsWith("act:")) {
                TimeAttackTrackCatalog.Track track = eligible.get(i - 1);
                boolean on = draftActs.contains(track.zone() + ":" + track.act());
                canvas.text(on ? "[X]" : "[ ]", 14, y, on ? VALUE : OFF);
                canvas.text(fit(canvas, track.label(), width - 150), 50, y, on ? VALUE : OFF);
                String only = characterNote(track);
                if (!only.isEmpty()) canvas.text(only, width - 24 - canvas.textWidth(only), y, HINT);
            } else {
                canvas.text(row, 14, y, VALUE);
            }
        }
        if (scroll > 0) canvas.text("^", width - 10, FIRST_ROW_Y, HINT);
        if (scroll + VISIBLE_ROWS < rows.size()) {
            canvas.text("v", width - 10, FIRST_ROW_Y + (VISIBLE_ROWS - 1) * ROW_HEIGHT, HINT);
        }
        if (!status.isEmpty()) canvas.text(fit(canvas, status, width - 20), 10, 194, TITLE);
        canvas.text(fit(canvas, "Enter toggle  B cancel", width - 20), 10, 208, HINT);
    }

    /** The racers' initials ("S+T") when not every character of the game races the act (S3K routes). */
    private String characterNote(TimeAttackTrackCatalog.Track track) {
        if (track.characters().containsAll(gameCharacters)) return "";
        StringBuilder out = new StringBuilder();
        for (String character : track.characters()) {
            if (!out.isEmpty()) out.append('+');
            out.append(character.substring(0, 1).toUpperCase());
        }
        return out.toString();
    }

    private static String fit(SceneCanvas canvas, String text, int maxWidth) {
        return TextLayout.ellipsis(text == null ? "" : text, Math.max(0, maxWidth), canvas::textWidth);
    }
}
