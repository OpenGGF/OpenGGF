package typinghog.hub;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneImage;
import java.util.ArrayList;
import java.util.List;
import typinghog.hub.model.Profile;
import typinghog.hub.model.StoryBook;
import typinghog.hub.model.WordMenu;
import typinghog.save.LaunchIntent;

/**
 * Arcade, Endless and Boss Rush: every story act cleared in any supplied game, with its local
 * top-10 at the chosen difficulty (Left and Right change it). Arcade replays the act for score;
 * Endless holds one stand with escalating waves; Boss Rush fights a game's cleared bosses back
 * to back (or one boss alone), each win raising that boss's star level. Acts in another game
 * relaunch into it first.
 */
final class ActListScreen implements Screen {
    private static final int ROW_H = 13;
    private static final int VISIBLE = 10;
    private static final int LIST_TOP = 46;

    /** One list row: a whole game's rush (Boss Rush only) or one act. */
    private record Row(String game, StoryBook.Stop stop) {
        boolean header() { return stop == null; }
    }

    private final LaunchIntent.Mode mode;
    private final List<Row> rows = new ArrayList<>();
    private int cursor;
    private int scroll;
    private WordMenu words;
    private long shakeTick = -100;
    private SceneBackdrop shown;
    private String shownKey = "";
    private long restTick;

    ActListScreen(LaunchIntent.Mode mode) {
        this.mode = mode;
    }

    @Override public String name() { return mode.name(); }

    private String title() {
        return switch (mode) {
            case ARCADE -> "ARCADE";
            case ENDLESS -> "ENDLESS";
            case BOSS_RUSH -> "BOSS RUSH";
            default -> "ACTS";
        };
    }

    @Override public void open(HubScene hub) {
        rows.clear();
        for (String g : List.of("s1", "s2", "s3k")) {
            if (!hub.available(g)) continue;
            List<Row> acts = new ArrayList<>();
            for (StoryBook.Stop stop : hub.story.chapter(g)) {
                if (hub.profile.isCleared(g, stop.zone(), stop.act()) && hub.railed(stop)) acts.add(new Row(g, stop));
            }
            if (acts.isEmpty()) continue;
            if (mode == LaunchIntent.Mode.BOSS_RUSH) rows.add(new Row(g, null));
            rows.addAll(acts);
        }
        cursor = Math.min(cursor, Math.max(0, rows.size() - 1));
        List<WordMenu.Item> items = new ArrayList<>();
        for (Row row : rows) {
            items.add(row.header() ? WordMenu.Item.of(HubScene.gameName(row.game()) + " RUSH",
                    HubScene.gameName(row.game()).toLowerCase(), "rush " + HubScene.gameShort(row.game()).toLowerCase())
                    : WordMenu.Item.of(row.stop().title(), row.stop().title(), row.stop().code()));
        }
        items.add(WordMenu.Item.of("GO", "go", "play"));
        items.add(WordMenu.Item.of("EASY", "easy"));
        items.add(WordMenu.Item.of("NORMAL", "normal"));
        items.add(WordMenu.Item.of("HARD", "hard"));
        items.add(WordMenu.Item.of("EXPERT", "expert"));
        items.add(WordMenu.Item.of("BACK", "back", "menu"));
        words = new WordMenu(items);
        words.setCursor(cursor);
        restTick = hub.ticks;
        hub.audio.theme(HubAudio.Theme.MENU);
    }

    @Override public void update(HubScene hub) {
        Controls in = hub.controls;
        if (rows.isEmpty()) {
            if (in.back() || in.accept()) hub.go(hub.menu);
            return;
        }
        if (in.up()) select(hub, cursor - 1);
        if (in.down()) select(hub, cursor + 1);
        if (in.left()) difficulty(hub, -1);
        if (in.right()) difficulty(hub, 1);
        if (in.backspace()) words.backspace();
        int before = words.cursor();
        switch (words.type(in.typed())) {
            case REJECTED -> {
                shakeTick = hub.ticks;
                hub.audio.cue(HubAudio.Cue.ERROR);
            }
            case MOVED -> {
                hub.audio.cue(HubAudio.Cue.KEY);
                if (words.cursor() < rows.size() && words.cursor() != before) select(hub, words.cursor());
            }
            case SELECTED -> {
                int item = words.selected();
                if (item < rows.size()) {
                    select(hub, item);
                } else {
                    int command = item - rows.size();
                    if (command == 0) launch(hub);
                    else if (command <= 4) setDifficulty(hub, LaunchIntent.Difficulty.values()[command - 1]);
                    else hub.go(hub.menu);
                    return;
                }
            }
            default -> { }
        }
        if (in.accept()) launch(hub);
        else if (in.back()) {
            hub.audio.cue(HubAudio.Cue.BACK);
            hub.go(hub.menu);
        }
        Row row = rows.get(cursor);
        StoryBook.Stop stop = row.header() ? firstAct(row.game()) : row.stop();
        if (stop != null && !stop.key().equals(shownKey)
                && (hub.ticks - restTick > 10 || hub.art.backdropReady(stop.game(), stop.zone(), stop.act()))) {
            shownKey = stop.key();
            SceneBackdrop bg = hub.art.zoneBackdrop(stop.game(), stop.zone(), stop.act());
            if (bg != null) shown = bg;
        }
    }

    private StoryBook.Stop firstAct(String g) {
        for (Row r : rows) if (!r.header() && r.game().equals(g)) return r.stop();
        return null;
    }

    private void select(HubScene hub, int index) {
        int next = Math.max(0, Math.min(rows.size() - 1, index));
        if (next == cursor) return;
        cursor = next;
        restTick = hub.ticks;
        words.setCursor(cursor);
        if (cursor < scroll) scroll = cursor;
        if (cursor >= scroll + VISIBLE) scroll = cursor - VISIBLE + 1;
        hub.audio.cue(HubAudio.Cue.MOVE);
    }

    private void difficulty(HubScene hub, int delta) {
        LaunchIntent.Difficulty[] all = LaunchIntent.Difficulty.values();
        setDifficulty(hub, all[Math.floorMod(hub.profile.difficulty().ordinal() + delta, all.length)]);
    }

    private void setDifficulty(HubScene hub, LaunchIntent.Difficulty d) {
        hub.profile.difficulty(d);
        hub.save();
        hub.audio.cue(HubAudio.Cue.MOVE);
    }

    private void launch(HubScene hub) {
        Row row = rows.get(cursor);
        if (mode == LaunchIntent.Mode.BOSS_RUSH) {
            List<String> bosses = new ArrayList<>();
            if (row.header()) {
                for (Row r : rows) if (!r.header() && r.game().equals(row.game())) bosses.add(Profile.act(r.stop().zone(), r.stop().act()));
            } else {
                bosses.add(Profile.act(row.stop().zone(), row.stop().act()));
            }
            hub.startRush(row.game(), bosses);
            return;
        }
        StoryBook.Stop stop = row.stop();
        hub.play(mode, stop.game(), stop.zone(), stop.act(), 1);
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override public void draw(HubScene hub, SceneCanvas c) {
        long t = hub.ticks;
        int w = c.width();
        if (shown != null) {
            int top = Math.max(0, Math.min(shown.image().height() - c.height(), 96));
            c.drawBackdrop(shown, top, t * 0.4, t);
            c.fill(0, 0, w, c.height(), 0xA0060C24);
        } else {
            hub.backdrop(c, 0.6f);
        }
        hub.header(c, title(), "DIFFICULTY  < " + hub.profile.difficulty().name() + " >");
        if (rows.isEmpty()) {
            Gfx.panel(c, 40, 80, w - 80, 50);
            hub.font.centred(c, "CLEAR STORY ACTS TO PLAY THEM HERE", w / 2, 96, Gfx.TEXT, 1);
            hub.font.centred(c, "PRESS ENTER TO GO BACK", w / 2, 110, Gfx.DIM, 1);
            return;
        }
        int shake = t - shakeTick < 10 ? (int) Math.round(Math.sin((t - shakeTick) * 2.2) * 3) : 0;
        int listW = listWidth(w);
        hub.font.shadow(c, subtitle(), 10, 31, Gfx.GOLD, 1);
        Gfx.panel(c, 6 + shake, LIST_TOP - 4, listW + 6, VISIBLE * ROW_H + 8);
        for (int i = scroll; i < Math.min(rows.size(), scroll + VISIBLE); i++) {
            Row row = rows.get(i);
            int y = LIST_TOP + (i - scroll) * ROW_H;
            boolean on = i == cursor;
            if (on) Gfx.highlight(c, 9 + shake, y - 1, listW, ROW_H, t);
            if (row.header()) {
                hub.font.shadow(c, HubScene.gameName(row.game()) + " RUSH", 14 + shake, y + 2, Gfx.GOLD, 1);
                continue;
            }
            StoryBook.Stop stop = row.stop();
            hub.font.shadow(c, HubScene.gameShort(stop.game()), 14 + shake, y + 2, Gfx.DIM, 1);
            int room = (listW - (mode == LaunchIntent.Mode.BOSS_RUSH ? 38 : 0) - 34) / HubFont.ADVANCE;
            String label = fit(stop.title().toUpperCase(), room - 2) + " " + (stop.act() + 1);
            hub.font.shadow(c, label, 40 + shake, y + 2, on ? Gfx.WHITE : Gfx.TEXT, 1);
            if (on && words.matched(i) > 0 && words.spelling(i).equalsIgnoreCase(stop.title())) {
                hub.font.draw(c, label.substring(0, Math.min(label.length(), words.matched(i))), 40 + shake, y + 2,
                        Gfx.TYPED, 1);
            }
            if (mode == LaunchIntent.Mode.BOSS_RUSH) stars(hub, c, listW - 34 + shake, y + 1,
                    hub.profile.stars(stop.game(), stop.zone(), stop.act()));
        }
        if (scroll > 0) hub.font.centred(c, "^", 6 + listW / 2, LIST_TOP - 10, Gfx.GOLD, 1);
        if (scroll + VISIBLE < rows.size()) hub.font.centred(c, "v", 6 + listW / 2, LIST_TOP + VISIBLE * ROW_H + 4, Gfx.GOLD, 1);
        table(hub, c, w, t);
        hub.footer(c, words.buffer(), mode == LaunchIntent.Mode.BOSS_RUSH ? "TYPE A BOSS OR A GAME, THEN GO" : "TYPE AN ACT, THEN GO");
    }

    /** {@code text} cut to {@code chars} characters, ending in a stop when cut. */
    private static String fit(String text, int chars) {
        return text.length() <= chars ? text : text.substring(0, Math.max(1, chars - 1)).strip() + ".";
    }

    /** The list's width: 186 on a 400-pixel screen, under half of narrower ones. */
    private static int listWidth(int width) { return Math.min(186, width * 15 / 32); }

    private String subtitle() {
        return switch (mode) {
            case ARCADE -> "PICK A CLEARED ACT. BEAT YOUR BEST.";
            case ENDLESS -> "ONE STAND. ENDLESS WAVES.";
            default -> "ROBOTNIK'S MACHINES, BACK TO BACK.";
        };
    }

    private void stars(HubScene hub, SceneCanvas c, int x, int y, int count) {
        SceneImage star = hub.art.image("star");
        int shown = Math.min(5, count);
        for (int i = 0; i < 5; i++) {
            if (star != null) {
                c.draw(star, x + i * 8, y, com.openggf.mods.scene.SceneDraw.plain()
                        .withTint(i < shown ? 0xFFFFFFFF : 0x50303040));
            }
        }
        if (count > 5) hub.font.draw(c, "+" + (count - 5), x + 42, y + 1, Gfx.GOLD, 1);
    }

    private void table(HubScene hub, SceneCanvas c, int w, long t) {
        Row row = rows.get(cursor);
        int x = listWidth(w) + 20, y = LIST_TOP - 4, pw = w - x - 6;
        Gfx.panel(c, x, y, pw, VISIBLE * ROW_H + 8 + 30);
        String table;
        String heading;
        if (row.header()) {
            table = Profile.rushTable(row.game(), hub.profile.difficulty());
            int count = 0;
            for (Row r : rows) if (!r.header() && r.game().equals(row.game())) count++;
            heading = count + " BOSS" + (count == 1 ? "" : "ES") + " BACK TO BACK";
        } else {
            StoryBook.Stop stop = row.stop();
            table = mode == LaunchIntent.Mode.ENDLESS
                    ? Profile.endlessTable(stop.game(), stop.zone(), stop.act(), hub.profile.difficulty())
                    : mode == LaunchIntent.Mode.BOSS_RUSH ? null
                    : Profile.actTable(stop.game(), stop.zone(), stop.act(), hub.profile.difficulty());
            heading = mode == LaunchIntent.Mode.BOSS_RUSH ? "STAR LEVEL " + hub.profile.stars(stop.game(), stop.zone(), stop.act())
                    : "TOP 10  " + hub.profile.difficulty().name();
        }
        hub.font.shadow(c, heading, x + 8, y + 6, Gfx.GOLD, 1);
        if (table == null) {
            StoryBook.Stop stop = row.stop();
            int stars = hub.profile.stars(stop.game(), stop.zone(), stop.act());
            stars(hub, c, x + 8, y + 22, stars);
            int ty = y + 40;
            for (String line : hub.font.wrap("Each win against " + stop.title() + "'s boss adds a star. Higher stars mean "
                    + "harder phrases and faster fights.", pw - 16, 1)) {
                hub.font.shadow(c, line, x + 8, ty, Gfx.TEXT, 1);
                ty += 11;
            }
            return;
        }
        List<Profile.Entry> list = hub.profile.records(table);
        for (int i = 0; i < Profile.RECORD_SLOTS; i++) {
            int ry = y + 20 + i * 11;
            if (i < list.size()) {
                Profile.Entry e = list.get(i);
                int colour = i == 0 ? Gfx.GOLD : Gfx.TEXT;
                hub.font.shadow(c, (i + 1) + ".", x + 8, ry, Gfx.DIM, 1);
                hub.font.shadow(c, Long.toString(e.score()), x + 24, ry, colour, 1);
                hub.font.shadow(c, e.grade(), x + 24 + Math.max(44, (pw - 24) / 3), ry, typinghog.hub.model.Grades.colour(e.grade()), 1);
                hub.font.right(c, Math.round(e.wpm()) + " WPM", x + pw - 8, ry, Gfx.DIM, 1);
            } else {
                hub.font.shadow(c, (i + 1) + ".", x + 8, ry, 0x60808898, 1);
                hub.font.shadow(c, "-----", x + 24, ry, 0x60808898, 1);
            }
        }
    }
}
