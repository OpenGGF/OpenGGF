package openggf.timeattack;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.NameFont;
import openggf.timeattack.mp.VoteTrackPools;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
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
    private static final int FIRST_ROW_Y = MenuPage.BODY_TOP - 2;
    private static final int VISIBLE_ROWS = 11;

    private final TimeAttackSettings settings;
    private final String gameId;
    private final Runnable changed;
    private final Consumer<MenuCue> cues;
    private final List<TimeAttackTrackCatalog.Track> eligible;
    private final List<String> gameCharacters;
    private boolean closeRequested;
    private int focus;
    private boolean pad;
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
        pad = MenuPage.controller(input, pad);
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
        if (editing == null) {
            drawList(canvas);
        } else {
            drawEdit(canvas);
        }
    }

    private void drawList(SceneCanvas canvas) {
        int width = canvas.width();
        MenuPage.page(canvas, "VOTE POOLS", MenuPage.gameName(gameId), "Each vote offers 3 acts from the room's pool");
        List<String> rows = listRows();
        String chosen = settings.votePoolChoice(gameId);
        int y = FIRST_ROW_Y;
        for (int i = scroll; i < rows.size() && i < scroll + VISIBLE_ROWS; i++) {
            String row = rows.get(i);
            boolean focused = i == focus;
            switch (row) {
                case "NEW" -> MenuPage.action(canvas, y, "New pool", focused, true);
                case "BACK" -> MenuPage.action(canvas, y, "Back", focused, true);
                default -> {
                    boolean standard = row.equals("DEFAULT");
                    // Pool names are display text in any script.
                    String name = standard ? "Default" : row.substring("pool:".length());
                    int count = standard ? VoteTrackPools.forGame(gameId).size()
                            : settings.votePool(gameId, name).map(pool -> pool.acts().size()).orElse(0);
                    boolean hosting = standard ? chosen.isEmpty() : name.equals(chosen);
                    String acts = count + " acts";
                    int tagWidth = hosting ? MenuPage.tagWidth("Hosting") + 6 : 0;
                    int right = width - MenuPage.MARGIN - 6;
                    MenuPage.action(canvas, y, "", focused, true);
                    NameFont.draw(canvas, NameFont.fit(name, right - MenuPage.MARGIN - 20 - tagWidth
                            - NameFont.width(acts) - 8), MenuPage.MARGIN + 14, y, focused ? MenuPage.TEXT : MenuPage.MUTED);
                    NameFont.draw(canvas, acts, right - tagWidth - NameFont.width(acts), y, MenuPage.DIM);
                    if (hosting) MenuPage.tag(canvas, right - tagWidth + 6, y + 1, "Hosting", MenuPage.GREEN);
                }
            }
            y += MenuPage.ROW_H;
        }
        if (status.isEmpty()) {
            MenuPage.notice(canvas, MenuPage.Tone.INFO, "A new pool starts as a copy of the default pool. "
                    + "The menu's Vote pool row picks the one your rooms host.", MenuPage.MARGIN + 6, y + 6, width - 28);
        } else {
            MenuPage.notice(canvas, statusTone(), status, MenuPage.MARGIN + 6, y + 6, width - 28);
        }
        String open = rows.get(focus).equals("BACK") ? "Back" : rows.get(focus).equals("NEW") ? "Create" : "Edit";
        MenuPage.footer(canvas, "", MenuPage.TEXT, List.of(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"),
                MenuPage.hint(MenuPage.confirm(pad), open), MenuPage.hint(MenuPage.back(pad), "Back")));
    }

    private void drawEdit(SceneCanvas canvas) {
        int width = canvas.width();
        boolean tooFew = draftActs.size() < VoteTrackPools.MIN_KEYS;
        MenuPage.page(canvas, editingNew ? "NEW POOL" : "EDIT POOL", MenuPage.gameName(gameId), null);
        String count = draftActs.size() + " of " + VotePool.MAX_ACTS + " acts";
        NameFont.draw(canvas, count, MenuPage.MARGIN + 1, MenuPage.HEADER_H + 3, tooFew ? MenuPage.AMBER : MenuPage.MUTED);
        String note = !status.isEmpty() ? status : tooFew ? "Choose at least " + VoteTrackPools.MIN_KEYS : "";
        if (!note.isEmpty()) {
            int x = MenuPage.MARGIN + 13 + NameFont.width(count);
            MenuPage.Tone tone = status.isEmpty() ? MenuPage.Tone.WARNING : statusTone();
            MenuPage.icon(canvas, tone, x, MenuPage.HEADER_H + 6);
            NameFont.draw(canvas, NameFont.fit(note, width - MenuPage.MARGIN - x - 10), x + 10, MenuPage.HEADER_H + 3,
                    MenuPage.colour(tone));
        }
        List<String> rows = editRows();
        int y = FIRST_ROW_Y;
        int right = width - MenuPage.MARGIN - 8;
        for (int i = scroll; i < rows.size() && i < scroll + VISIBLE_ROWS; i++) {
            String row = rows.get(i);
            boolean focused = i == focus;
            if (row.equals("NAME")) {
                MenuPage.field(canvas, y, MenuPage.MARGIN + 6 + MenuPage.labelColumn(List.of("Name")), "Name", "",
                        MenuPage.TEXT, focused, false);
                NameFont.draw(canvas, NameFont.fit(draftName, right - 60), MenuPage.MARGIN + 6
                        + MenuPage.labelColumn(List.of("Name")), y, MenuPage.TEXT);
            } else if (row.startsWith("act:")) {
                TimeAttackTrackCatalog.Track track = eligible.get(i - 1);
                boolean on = draftActs.contains(track.zone() + ":" + track.act());
                if (focused) MenuPage.focus(canvas, MenuPage.MARGIN, y, width - 2 * MenuPage.MARGIN, MenuPage.ROW_H);
                MenuPage.checkbox(canvas, MenuPage.MARGIN + 6, y + 3, on, on ? MenuPage.CYAN : MenuPage.FAINT);
                String only = characterNote(track);
                int onlyWidth = only.isEmpty() ? 0 : MenuPage.tagWidth(only) + 6;
                NameFont.draw(canvas, NameFont.fit(MenuPage.words(track.label()), right - MenuPage.MARGIN - 20 - onlyWidth),
                        MenuPage.MARGIN + 20, y, on ? MenuPage.TEXT : MenuPage.DIM);
                if (!only.isEmpty()) MenuPage.tag(canvas, right - onlyWidth + 6, y + 1, only, MenuPage.DIM);
            } else {
                String label = switch (row) {
                    case "SAVE" -> "Save pool";
                    case "DELETE" -> "Delete pool";
                    default -> "Cancel";
                };
                MenuPage.action(canvas, y, label, focused, true);
            }
            y += MenuPage.ROW_H;
        }
        MenuPage.scrollbar(canvas, width - MenuPage.MARGIN - 3, FIRST_ROW_Y, VISIBLE_ROWS * MenuPage.ROW_H, scroll,
                VISIBLE_ROWS, rows.size());
        String row = rows.get(focus);
        String action = row.startsWith("act:") ? "Toggle" : row.equals("NAME") ? "Rename"
                : row.equals("SAVE") ? "Save" : row.equals("DELETE") ? "Delete" : "Cancel";
        MenuPage.footer(canvas, "", MenuPage.TEXT, List.of(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"),
                MenuPage.hint(MenuPage.confirm(pad), action), MenuPage.hint(MenuPage.back(pad), "Cancel")));
    }

    private MenuPage.Tone statusTone() {
        return status.startsWith("Saved") || status.equals("Pool deleted") ? MenuPage.Tone.GOOD : MenuPage.Tone.WARNING;
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

}
