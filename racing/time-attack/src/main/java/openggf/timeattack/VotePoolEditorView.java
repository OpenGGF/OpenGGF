package openggf.timeattack;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.NameFont;
import openggf.timeattack.mp.VoteTrackPools;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.FullTextReader;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.TrackBadges;
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
 *
 * <p>For a Competition room of Sonic 3&amp;K the edit page adds an ARENAS group under its own
 * caption: the five Competition arenas, which race in no other mode. A Time Attack room never lists
 * them, and saving keeps any arena a pool already holds rather than dropping it unseen; the room's
 * pool check then explains why that pool cannot host Time Attack.
 */
public final class VotePoolEditorView {
    private static final int FIRST_ROW_Y = MenuPage.BODY_TOP - 2;
    private static final int VISIBLE_ROWS = 11;

    private final TimeAttackSettings settings;
    private final String gameId;
    private final Runnable changed;
    private final Consumer<MenuCue> cues;
    private final List<TimeAttackTrackCatalog.Track> eligible;
    /** The arenas the edit page lists: S3K's in a Competition room, otherwise none. */
    private final List<TimeAttackTrackCatalog.Track> arenas;
    private final List<String> gameCharacters;
    private boolean closeRequested;
    private int focus;
    private MenuPage.Keys keys = MenuPage.Keys.keyboard();
    private int scroll;
    private String status = "";
    private final FullTextReader reader = new FullTextReader();

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
        this(settings, gameId, CompetitionRoomSettings.Mode.TIME_ATTACK, changed, cues);
    }

    /** @param mode the room's race mode, which decides whether the arenas are listed */
    public VotePoolEditorView(TimeAttackSettings settings, String gameId, CompetitionRoomSettings.Mode mode,
                              Runnable changed, Consumer<MenuCue> cues) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.changed = Objects.requireNonNull(changed, "changed");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.eligible = TimeAttackTrackCatalog.eligibleFor(gameId).stream()
                .filter(track -> track.modes().contains(Objects.requireNonNull(mode, "mode"))).toList();
        this.arenas = TimeAttackTrackCatalog.eligibleFor(gameId, mode).stream()
                .filter(track -> track.group() == TimeAttackTrackCatalog.Group.ARENAS).toList();
        Set<String> characters = new LinkedHashSet<>();
        eligible.forEach(track -> characters.addAll(track.characters()));
        this.gameCharacters = List.copyOf(characters);
    }

    public void update(ViewInput input) {
        keys = MenuPage.Keys.of(input, keys);
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
        if (reader.handle(input, this::detailsText)) return;
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
        if (!arenas.isEmpty()) rows.add("cap:" + TimeAttackMenuState.groupName(TimeAttackTrackCatalog.Group.ACTS));
        eligible.forEach(track -> rows.add("act:" + poolText(track)));
        if (!arenas.isEmpty()) {
            rows.add("cap:" + TimeAttackMenuState.groupName(TimeAttackTrackCatalog.Group.ARENAS));
            arenas.forEach(track -> rows.add("act:" + poolText(track)));
        }
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

    /** A track as a pool stores it: {@code zone:act}, or {@code arena:N} for an arena. */
    private static String poolText(TimeAttackTrackCatalog.Track track) {
        return track.group() == TimeAttackTrackCatalog.Group.ARENAS ? "arena:" + track.zone()
                : track.zone() + ":" + track.act();
    }

    /** The listed track a pool entry names, or null. */
    private TimeAttackTrackCatalog.Track track(String text) {
        for (TimeAttackTrackCatalog.Track track : eligible) if (poolText(track).equals(text)) return track;
        for (TimeAttackTrackCatalog.Track track : arenas) if (poolText(track).equals(text)) return track;
        return null;
    }

    /** What the page counts: acts, or tracks once arenas are listed beside them. */
    private String unit() {
        return arenas.isEmpty() ? "acts" : "tracks";
    }

    private void toggle(String act) {
        if (draftActs.remove(act)) {
            status = "";
            cues.accept(MenuCue.NAVIGATE);
            return;
        }
        if (draftActs.size() >= VotePool.MAX_ACTS) {
            status = "A pool holds at most " + VotePool.MAX_ACTS + " " + unit() + " (the room limit)";
            cues.accept(MenuCue.ERROR);
            return;
        }
        draftActs.add(act);
        status = "";
        cues.accept(MenuCue.NAVIGATE);
    }

    private void save() {
        // Saved in the catalog's game order, the order votes rotate through.
        // Arenas a Time Attack page does not list are kept, in their catalog place.
        List<String> acts = new ArrayList<>();
        List<TimeAttackTrackCatalog.Track> order = new ArrayList<>(TimeAttackTrackCatalog.eligibleFor(gameId));
        order.addAll(TimeAttackTrackCatalog.arenas().stream().filter(arena -> arena.gameId().equals(gameId)).toList());
        order.forEach(track -> {
            String act = poolText(track);
            if (draftActs.contains(act)) acts.add(act);
        });
        if (acts.size() < VoteTrackPools.MIN_KEYS) {
            status = "Choose at least " + VoteTrackPools.MIN_KEYS + " " + unit() + " so a vote has a choice";
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
        // Captions are not focusable: the arrows step over them in the direction pressed.
        List<String> rows = editing == null ? List.of() : editRows();
        while (focus < rows.size() && rows.get(focus).startsWith("cap:")) {
            focus = Math.floorMod(focus + (input.up() ? -1 : 1), count);
        }
        if (focus != before) {
            cues.accept(MenuCue.NAVIGATE);
            if (editing == null) status = "";
        }
        // A group's first row brings its caption into view.
        int top = focus > 0 && focus - 1 < rows.size() && rows.get(focus - 1).startsWith("cap:") ? focus - 1 : focus;
        if (top < scroll) scroll = top;
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
        if (reader.draw(canvas)) return;
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
        MenuPage.footerWithDetails(canvas, keys, "", MenuPage.TEXT, List.of(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"),
                MenuPage.hint(keys.confirm(), open), MenuPage.hint(keys.back(), "Back")));
    }

    private void drawEdit(SceneCanvas canvas) {
        int width = canvas.width();
        boolean tooFew = draftActs.size() < VoteTrackPools.MIN_KEYS;
        MenuPage.page(canvas, editingNew ? "NEW POOL" : "EDIT POOL", MenuPage.gameName(gameId), null);
        String count = draftActs.size() + " of " + VotePool.MAX_ACTS + " " + unit();
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
            } else if (row.startsWith("cap:")) {
                MenuPage.section(canvas, row.substring("cap:".length()), MenuPage.MARGIN + 6, y + 4,
                        width - 2 * MenuPage.MARGIN - 12);
            } else if (row.startsWith("act:")) {
                TimeAttackTrackCatalog.Track track = track(row.substring("act:".length()));
                boolean on = draftActs.contains(poolText(track));
                if (focused) MenuPage.focus(canvas, MenuPage.MARGIN, y, width - 2 * MenuPage.MARGIN, MenuPage.ROW_H);
                MenuPage.checkbox(canvas, MenuPage.MARGIN + 6, y + 3, on, on ? MenuPage.CYAN : MenuPage.FAINT);
                String only = characterNote(track);
                // Kind and mode tags on the focused row only: most rows of a group share them.
                List<String> tags = new ArrayList<>(focused ? TrackBadges.of(track) : List.of());
                if (!only.isEmpty()) tags.add(only);
                int tagsWidth = 0;
                for (String tag : tags) tagsWidth += MenuPage.tagWidth(tag) + 3;
                String label = MenuPage.words(track.label());
                int labelRoom = right - MenuPage.MARGIN - 20 - tagsWidth - 3;
                if (NameFont.width(label) > labelRoom && focused) {
                    // A narrow page keeps the name whole and gives up the badges (Details lists them).
                    tags.removeIf(tag -> !tag.equals(only));
                    tagsWidth = only.isEmpty() ? 0 : MenuPage.tagWidth(only) + 3;
                    labelRoom = right - MenuPage.MARGIN - 20 - tagsWidth - 3;
                }
                NameFont.draw(canvas, NameFont.fit(label, labelRoom), MenuPage.MARGIN + 20, y,
                        on ? MenuPage.TEXT : MenuPage.DIM);
                int tagX = right + 3 - tagsWidth;
                for (int t = 0; t < tags.size(); t++) {
                    String tag = tags.get(t);
                    tagX += MenuPage.tag(canvas, tagX, y + 1, tag, focused && t == 0 && !tag.equals(only)
                            ? MenuPage.CYAN : MenuPage.DIM) + 3;
                }
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
        MenuPage.footerWithDetails(canvas, keys, "", MenuPage.TEXT, List.of(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"),
                MenuPage.hint(keys.confirm(), action), MenuPage.hint(keys.back(), "Cancel")));
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

    /** The page's complete text: every pool, or the draft's name and every act with its state. */
    private FullTextReader.Text detailsText() {
        String hosting = settings.votePoolChoice(gameId);
        if (editing == null) {
            FullTextReader.Text text = FullTextReader.Text.of("VOTE POOLS", MenuPage.gameName(gameId))
                    .line("Each vote offers 3 acts from the room's pool. A pool holds at most "
                            + VotePool.MAX_ACTS + " acts.")
                    .field("Status", status)
                    .section("Pools")
                    .field("Default", VoteTrackPools.forGame(gameId).size() + " acts"
                            + (hosting.isEmpty() ? ", hosting" : ""));
            for (VotePool pool : settings.votePools(gameId)) {
                text.field(pool.name(), pool.acts().size() + " acts" + (pool.name().equals(hosting) ? ", hosting" : ""));
            }
            return text.line("A new pool starts as a copy of the default pool. The menu's Vote pool row picks the one your rooms host.");
        }
        FullTextReader.Text text = FullTextReader.Text.of(editingNew ? "NEW POOL" : "EDIT POOL", MenuPage.gameName(gameId))
                .field("Name", draftName)
                .field(MenuPage.sentence(unit()), draftActs.size() + " of " + (eligible.size() + arenas.size())
                        + " chosen; a pool needs at least "
                        + VoteTrackPools.MIN_KEYS + " and holds at most " + VotePool.MAX_ACTS)
                .field("Status", status)
                .section("Acts");
        for (TimeAttackTrackCatalog.Track track : eligible) detailsRow(text, track);
        if (!arenas.isEmpty()) {
            text.section("Arenas");
            text.line("Arenas race in Competition rooms only: " + arenas.getFirst().lapCount() + " laps each.");
            for (TimeAttackTrackCatalog.Track track : arenas) detailsRow(text, track);
        }
        return text;
    }

    private void detailsRow(FullTextReader.Text text, TimeAttackTrackCatalog.Track track) {
        String racers = track.characters().containsAll(gameCharacters) ? ""
                : " (" + String.join(", ", track.characters().stream().map(MenuPage::sentence).toList()) + " only)";
        text.field(MenuPage.words(track.label()),
                (draftActs.contains(poolText(track)) ? "In the pool" : "Not in the pool") + racers);
    }

    List<String> detailsVisibleLines() { return reader.visibleLines(); }

    int detailsFirstLine() { return reader.firstVisibleLine(); }

}
