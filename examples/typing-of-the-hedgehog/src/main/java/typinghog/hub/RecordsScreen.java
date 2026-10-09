package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.List;
import typinghog.hub.model.Drill;
import typinghog.hub.model.Grades;
import typinghog.hub.model.Profile;
import typinghog.hub.model.SchoolMaterial;
import typinghog.hub.model.StoryBook;
import typinghog.hub.model.WordMenu;

/**
 * Records: every story act's best run (score, rank, speed, emerald, Boss Rush stars) per
 * game; the Typing School's grades; and the keyboard: lifetime per-key accuracy as a heatmap,
 * lifetime words per minute and the keys to practise. Left and Right (or typing ACTS, SCHOOL,
 * KEYS) change the page.
 */
final class RecordsScreen implements Screen {
    private final List<String> tabs = List.of("ACTS", "SCHOOL", "KEYS");
    private final WordMenu words = new WordMenu(List.of(WordMenu.Item.of("ACTS", "acts"),
            WordMenu.Item.of("SCHOOL", "school", "grades"), WordMenu.Item.of("KEYS", "keys", "keyboard"),
            WordMenu.Item.of("BACK", "back", "menu")));
    private final Keyboard keyboard = new Keyboard();
    private int tab;
    private int game;
    private long tabTick;

    @Override public String name() { return "RECORDS"; }

    void tab(String name) {
        int i = tabs.indexOf(name.toUpperCase());
        if (i >= 0) tab = i;
    }

    @Override public void open(HubScene hub) {
        words.clear();
        if (!hub.available(gameAt())) game = Math.max(0, List.of("s1", "s2", "s3k").indexOf(hub.game));
        hub.audio.theme(HubAudio.Theme.MENU);
    }

    private String gameAt() { return List.of("s1", "s2", "s3k").get(game); }

    @Override public void update(HubScene hub) {
        Controls in = hub.controls;
        if (in.left()) setTab(hub, tab - 1);
        if (in.right()) setTab(hub, tab + 1);
        if (tab == 0 && (in.up() || in.down())) {
            for (int step = 1; step <= 3; step++) {
                int g = Math.floorMod(game + (in.down() ? step : -step), 3);
                if (hub.available(List.of("s1", "s2", "s3k").get(g))) {
                    game = g;
                    hub.audio.cue(HubAudio.Cue.MOVE);
                    break;
                }
            }
        }
        if (in.backspace()) words.backspace();
        if (words.type(in.typed()) == WordMenu.Outcome.SELECTED) {
            if (words.selected() == 3) {
                hub.go(hub.menu);
                return;
            }
            setTab(hub, words.selected());
        }
        if (in.back() || in.accept()) {
            hub.audio.cue(HubAudio.Cue.BACK);
            hub.go(hub.menu);
        }
    }

    private void setTab(HubScene hub, int i) {
        tab = Math.floorMod(i, tabs.size());
        tabTick = hub.ticks;
        hub.audio.cue(HubAudio.Cue.MOVE);
    }

    @Override public void draw(HubScene hub, SceneCanvas c) {
        long t = hub.ticks;
        int w = c.width();
        hub.backdrop(c, 0.6f);
        hub.header(c, "RECORDS", "");
        int x = 120;
        for (int i = 0; i < tabs.size(); i++) {
            String label = tabs.get(i);
            int lw = hub.font.width(label, 1) + 14;
            boolean on = i == tab;
            Gfx.panel(c, x, 4, lw, 15, on ? 0xF02858C8 : 0xC0142454, on ? Gfx.GOLD : 0xFF5A8CF0);
            hub.font.draw(c, label, x + 7, 7, on ? Gfx.WHITE : Gfx.TEXT, 1);
            x += lw + 4;
        }
        switch (tab) {
            case 0 -> acts(hub, c, w, t);
            case 1 -> school(hub, c, w);
            default -> keys(hub, c, w, t);
        }
        hub.footer(c, words.buffer(), "LEFT/RIGHT: PAGE" + (tab == 0 ? "   UP/DOWN: GAME" : ""));
    }

    private void acts(HubScene hub, SceneCanvas c, int w, long t) {
        String g = gameAt();
        List<StoryBook.Stop> stops = hub.story.chapter(g);
        hub.font.shadow(c, HubScene.gameName(g), 10, 30, Gfx.GOLD, 1);
        hub.emeralds(c, w - 86, 28, hub.profile.emeraldCount(g));
        Gfx.panel(c, 6, 42, w - 12, 162);
        // Columns scale with the width: 150/214/250/304 on a 400-pixel screen.
        int best = w * 3 / 8, rank = w * 107 / 200, speed = w * 5 / 8, boss = w * 76 / 100;
        hub.font.shadow(c, "ZONE", 14, 47, Gfx.DIM, 1);
        hub.font.shadow(c, "BEST", best, 47, Gfx.DIM, 1);
        hub.font.shadow(c, "RANK", rank - 8, 47, Gfx.DIM, 1);
        hub.font.shadow(c, "SPEED", speed, 47, Gfx.DIM, 1);
        hub.font.shadow(c, "BOSS", boss, 47, Gfx.DIM, 1);
        int rowH = stops.size() > 11 ? 11 : 13;
        SceneImage gem = hub.art.image("emerald");
        SceneImage star = hub.art.image("star");
        int emerald = 0;
        for (int i = 0; i < stops.size(); i++) {
            StoryBook.Stop s = stops.get(i);
            int y = 59 + i * rowH;
            boolean cleared = hub.profile.isCleared(g, s.zone(), s.act());
            String name = s.title().toUpperCase() + " " + (s.act() + 1);
            int room = (best - 20) / HubFont.ADVANCE;
            if (name.length() > room) name = name.substring(0, Math.max(1, room - 1)).strip() + ".";
            hub.font.shadow(c, name, 14, y, cleared ? Gfx.WHITE : 0xFF8890A0, 1);
            Profile.Entry top = hub.profile.bestForAct(g, s.zone(), s.act());
            if (top != null) {
                hub.font.shadow(c, Long.toString(top.score()), best, y, Gfx.GOLD, 1);
                hub.font.shadow(c, top.grade(), rank, y, Grades.colour(top.grade()), 1);
                hub.font.shadow(c, Math.round(top.wpm()) + " WPM", speed, y, Gfx.TEXT, 1);
            } else {
                hub.font.shadow(c, "-", best, y, 0x80808898, 1);
            }
            int stars = hub.profile.stars(g, s.zone(), s.act());
            if (star != null && stars > 0) {
                c.draw(star, boss, y - 1);
                hub.font.shadow(c, "x" + stars, boss + 10, y, Gfx.GOLD, 1);
            }
            if (hub.profile.hasEmerald(g, s.zone(), s.act()) && gem != null) {
                c.draw(gem, w - 26, y - 1, SceneDraw.plain().withTint(HubArt.emeraldColour(emerald++)));
            }
        }
    }

    private void school(HubScene hub, SceneCanvas c, int w) {
        Gfx.panel(c, 6, 28, w - 12, 176);
        int i = 0;
        for (Drill d : Drill.values()) {
            int col = i < 7 ? 0 : 1;
            int y = 36 + (i % 7) * 20;
            int x = 16 + col * ((w - 12) / 2);
            String grade = hub.profile.grade(d.name());
            hub.font.shadow(c, d.label.toUpperCase(), x, y + 3, d.exam() ? Gfx.GOLD : Gfx.TEXT, 1);
            int gx = x + 120;
            c.fill(gx, y, 14, 15, 0xFF000000);
            c.fill(gx + 1, y + 1, 12, 13, Gfx.mix(Grades.colour(grade), 0xFF000000, 0.55f));
            hub.font.draw(c, grade == null ? "-" : grade, gx + 4, y + 3, Grades.colour(grade), 1);
            i++;
        }
        hub.font.shadow(c, typinghog.hub.model.School.examUnlocked(hub.profile) ? "FINAL EXAM OPEN"
                : typinghog.hub.model.School.drillsBelowC(hub.profile) + " DRILLS STILL NEED A C", 16, 182, 0xFF80D0FF, 1);
    }

    private void keys(HubScene hub, SceneCanvas c, int w, long t) {
        Profile p = hub.profile;
        Gfx.panel(c, 6, 28, w - 12, 176);
        int kw = Keyboard.width(22);
        keyboard.draw(c, hub.font, (w - kw) / 2, 40, 22, 16, -1, p, t);
        int y = 40 + Keyboard.height(16) + 10;
        String[] labels = {"LIFETIME SPEED", "ACCURACY", "WORDS TYPED", "ACTS CLEARED"};
        String[] values = {Math.round(p.lifetimeWpm()) + " WPM",
                String.format(java.util.Locale.ROOT, "%.1f%%", p.lifetimeAccuracy() * 100),
                Long.toString(p.lifeWords()), p.actsCleared() + " / " + p.actsPlayed()};
        for (int i = 0; i < labels.length; i++) {
            int x = 20 + (i % 2) * ((w - 40) / 2);
            int ly = y + (i / 2) * 12;
            hub.font.shadow(c, labels[i], x, ly, Gfx.TEXT, 1);
            hub.font.right(c, values[i], x + (w - 40) / 2 - 16, ly, Gfx.GOLD, 1);
        }
        String weak = SchoolMaterial.weakKeys(p.keyHits(), p.keyMisses(), 5).toUpperCase();
        hub.font.shadow(c, "PRACTISE: " + String.join(" ", weak.split("")) + "   (TYPING SCHOOL: WEAK KEYS)", 20, y + 28,
                0xFF80D0FF, 1);
        // Legend.
        int lx = 20, ly = y + 40;
        for (double a : new double[] {0.75, 0.85, 0.92, 0.99}) {
            c.fill(lx, ly, 10, 7, Keyboard.heatColour(a));
            lx += 12;
        }
        hub.font.shadow(c, "MISSED  ...  CLEAN", lx + 4, ly, Gfx.DIM, 1);
    }
}
