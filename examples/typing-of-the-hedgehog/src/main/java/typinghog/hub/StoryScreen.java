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
 * The story's chapter map: one chapter per game, one stop per zone, laid out as a winding
 * path that Sonic runs along. Each stop shows whether it is cleared (with its emerald), open,
 * locked or still under construction (no rail yet); the panel below gives the act's best run.
 * The selected stop's own zone background fills the screen.
 *
 * <p>Typing a zone's name walks Sonic there; GO (or Enter) starts it. SONIC 1, SONIC 2 and
 * SONIC 3 switch chapters (as do Up and Down). A stop in another supplied game relaunches
 * into that game and carries straight on into the act.
 */
final class StoryScreen implements Screen {
    private static final int ROW_A = 86;
    private static final int ROW_B = 128;

    private final List<String> chapters = List.of("s1", "s2", "s3k");
    private static final int RUN_TICKS = 14;

    private String chapter;
    private List<StoryBook.Stop> stops = List.of();
    private int cursor;
    private int from;
    private long moveTick = -100;
    private long restTick;
    private WordMenu words;
    private long shakeTick = -100;
    private SceneBackdrop shown;
    private String shownKey = "";
    private long backdropTick;

    @Override public String name() { return "STORY"; }

    /** Opens on {@code game}'s chapter (debug and hand-back). */
    void chapter(HubScene hub, String game) {
        if (chapters.contains(game)) chapter = game;
    }

    @Override public void open(HubScene hub) {
        if (chapter == null || !hub.available(chapter)) {
            chapter = hub.available(hub.game) ? hub.game : hub.games.isEmpty() ? hub.game : hub.games.getFirst();
        }
        load(hub, chapter, -1);
        hub.audio.theme(HubAudio.Theme.MENU);
    }

    private void load(HubScene hub, String game, int at) {
        chapter = game;
        stops = hub.story.chapter(game);
        cursor = at >= 0 ? at : StoryBook.resumeIndex(stops, hub.profile.cleared(game), hub::railed);
        cursor = Math.max(0, Math.min(cursor, Math.max(0, stops.size() - 1)));
        from = cursor;
        restTick = hub.ticks;
        List<WordMenu.Item> items = new ArrayList<>();
        for (StoryBook.Stop stop : stops) items.add(WordMenu.Item.of(stop.title(), stop.title(), stop.code()));
        items.add(WordMenu.Item.of("GO", "go", "play"));
        items.add(WordMenu.Item.of("SONIC 1", "sonic 1", "sonic one"));
        items.add(WordMenu.Item.of("SONIC 2", "sonic 2", "sonic two"));
        items.add(WordMenu.Item.of("SONIC 3", "sonic 3", "sonic three", "knuckles"));
        items.add(WordMenu.Item.of("BACK", "back", "menu"));
        words = new WordMenu(items);
        words.setCursor(cursor);
    }

    @Override public void update(HubScene hub) {
        Controls in = hub.controls;
        if (stops.isEmpty()) {
            if (in.back() || in.accept()) hub.go(hub.menu);
            return;
        }
        if (in.left()) select(hub, cursor - 1);
        if (in.right()) select(hub, cursor + 1);
        if (in.up()) switchChapter(hub, -1);
        if (in.down()) switchChapter(hub, 1);
        if (in.backspace()) words.backspace();
        int before = words.cursor();
        WordMenu.Outcome outcome = words.type(in.typed());
        if (outcome == WordMenu.Outcome.REJECTED) {
            shakeTick = hub.ticks;
            hub.audio.cue(HubAudio.Cue.ERROR);
        } else if (outcome == WordMenu.Outcome.MOVED) {
            hub.audio.cue(HubAudio.Cue.KEY);
            if (words.cursor() < stops.size() && words.cursor() != before) select(hub, words.cursor());
        } else if (outcome == WordMenu.Outcome.SELECTED) {
            int item = words.selected();
            if (item < stops.size()) {
                select(hub, item);
                words.setCursor(item);
            } else {
                switch (item - stops.size()) {
                    case 0 -> launch(hub);
                    case 1 -> jump(hub, "s1");
                    case 2 -> jump(hub, "s2");
                    case 3 -> jump(hub, "s3k");
                    default -> hub.go(hub.menu);
                }
                return;
            }
        }
        if (in.accept()) launch(hub);
        else if (in.back()) {
            hub.audio.cue(HubAudio.Cue.BACK);
            hub.go(hub.menu);
        }
        // Load the selected zone's background once the cursor has rested, so scrolling past
        // stops never stalls on a level kit.
        StoryBook.Stop stop = stops.get(cursor);
        String key = stop.key();
        if (!key.equals(shownKey) && (hub.ticks - restTick > 10 || hub.art.backdropReady(stop.game(), stop.zone(), stop.act()))) {
            SceneBackdrop bg = hub.art.zoneBackdrop(stop.game(), stop.zone(), stop.act());
            shownKey = key;
            if (bg != null) {
                shown = bg;
                backdropTick = hub.ticks;
            }
        }
    }

    private void select(HubScene hub, int index) {
        int next = Math.max(0, Math.min(stops.size() - 1, index));
        if (next == cursor) return;
        from = cursor;
        cursor = next;
        moveTick = hub.ticks;
        restTick = hub.ticks;
        words.setCursor(cursor);
        hub.audio.cue(HubAudio.Cue.MOVE);
    }

    private void switchChapter(HubScene hub, int delta) {
        int i = chapters.indexOf(chapter);
        for (int step = 1; step <= chapters.size(); step++) {
            String g = chapters.get(Math.floorMod(i + delta * step, chapters.size()));
            if (hub.available(g)) {
                if (!g.equals(chapter)) {
                    load(hub, g, -1);
                    hub.audio.cue(HubAudio.Cue.MOVE);
                }
                return;
            }
        }
    }

    private void jump(HubScene hub, String g) {
        if (!hub.available(g)) {
            hub.refuse("SUPPLY THE " + HubScene.gameName(g) + " ROM FIRST");
            return;
        }
        load(hub, g, -1);
        hub.audio.cue(HubAudio.Cue.CONFIRM);
    }

    private StoryBook.State state(HubScene hub, int i) {
        return StoryBook.state(stops, i, hub.profile.cleared(chapter), hub::railed);
    }

    private void launch(HubScene hub) {
        StoryBook.Stop stop = stops.get(cursor);
        switch (state(hub, cursor)) {
            case UNDER_CONSTRUCTION -> hub.refuse(stop.title().toUpperCase() + " IS UNDER CONSTRUCTION");
            case LOCKED -> hub.refuse("CLEAR THE STOPS BEFORE " + stop.title().toUpperCase());
            default -> hub.play(LaunchIntent.Mode.STORY, stop.game(), stop.zone(), stop.act(), 1);
        }
    }

    // ---- drawing ----------------------------------------------------------------------------

    private int[] node(int i, int width) {
        int n = stops.size();
        int perRow = n > 7 ? (n + 1) / 2 : n;
        int row = i < perRow ? 0 : 1;
        int index = row == 0 ? i : i - perRow;
        int count = row == 0 ? perRow : n - perRow;
        int left = 44, right = width - 44;
        int span = count <= 1 ? 0 : (right - left) / (perRow - 1);
        int x = row == 0 ? left + index * span : right - index * span;
        int y = (row == 0 ? ROW_A : ROW_B) + (int) Math.round(Math.sin(i * 1.3) * 4);
        if (count == 1 && row == 0) x = width / 2;
        return new int[] {x, y};
    }

    @Override public void draw(HubScene hub, SceneCanvas c) {
        long t = hub.ticks;
        int w = c.width();
        if (shown != null) {
            int top = Math.max(0, Math.min(shown.image().height() - c.height(), 96));
            c.drawBackdrop(shown, top, t * 0.5, t);
            c.fill(0, 0, w, c.height(), 0x90060C24);
            if (t - backdropTick < 12) c.fill(0, 0, w, c.height(), Gfx.alpha(0xFF060C24, 1 - (t - backdropTick) / 12f));
        } else {
            hub.backdrop(c, 0.6f);
        }
        hub.header(c, "STORY", "");
        hub.emeralds(c, w - 84, 6, hub.profile.emeraldCount(chapter));
        tabs(hub, c);
        if (stops.isEmpty()) {
            hub.font.centred(c, "NO STORY FOR THIS GAME", w / 2, 100, Gfx.TEXT, 1);
            return;
        }
        int shake = t - shakeTick < 10 ? (int) Math.round(Math.sin((t - shakeTick) * 2.2) * 3) : 0;
        path(hub, c, w);
        for (int i = 0; i < stops.size(); i++) nodeAt(hub, c, i, w, t);
        // Sonic runs from the last stop to this one, then waits on it.
        int[] a = node(from, w), b = node(cursor, w);
        float k = Math.min(1, (t - moveTick) / (float) RUN_TICKS);
        int sx = Math.round(a[0] + (b[0] - a[0]) * k), sy = Math.round(a[1] + (b[1] - a[1]) * k);
        boolean running = k < 1;
        Hero.sonic(c, hub.art, chapter, running ? HubArt.ANIM_RUN : HubArt.ANIM_WAIT, t, sx + shake, sy - 6,
                running && b[0] < a[0], 1f);
        info(hub, c, w, t);
        hub.footer(c, words.buffer(), "TYPE A ZONE, THEN GO    UP/DOWN: GAME");
    }

    /** The stop's badnik waits in the panel's bay with its name on a word plate: a preview of the act. */
    private void guard(HubScene hub, SceneCanvas c, StoryBook.Stop stop, StoryBook.State s, int cx, int feet, long t) {
        ZoneCast.Badnik who = ZoneCast.of(stop.game(), stop.zone());
        var set = hub.art.badnik(stop.game(), stop.zone(), stop.act());
        if (who == null || set == null) return;
        var sprite = set.frame(who.frame(t));
        int y = feet - (sprite.height() - sprite.originY());
        float bob = (float) Math.sin(t * 0.08) * 1.5f;
        c.draw(sprite, cx, y + bob, com.openggf.mods.scene.SceneDraw.plain());
        String word = who.name().toLowerCase();
        int plateW = hub.font.width(word, 1) + 6;
        int plateCx = Math.min(cx, c.width() - 12 - plateW / 2);
        int plateTop = Math.max(PANEL_Y + 3, y - sprite.originY() - 14);
        Gfx.plate(c, hub.font, word, 0, plateCx, plateTop, s == StoryBook.State.LOCKED ? 0xFF686E80 : 0xFFE0E0E0, 1, 0);
    }

    private void tabs(HubScene hub, SceneCanvas c) {
        int x = 8;
        for (String g : chapters) {
            String label = HubScene.gameName(g);
            boolean on = g.equals(chapter), have = hub.available(g);
            int lw = hub.font.width(label, 1) + 14;
            int y = 27;
            Gfx.panel(c, x, y, lw, 15, on ? 0xF02858C8 : have ? 0xD0142454 : 0x90101020,
                    on ? Gfx.GOLD : have ? 0xFF5A8CF0 : 0x60505868);
            hub.font.draw(c, label, x + 7, y + 3, on ? Gfx.WHITE : have ? Gfx.TEXT : 0x70808898, 1);
            x += lw + 4;
        }
        String more = "MORE ROMS, MORE CHAPTERS";
        if ((!hub.available("s1") || !hub.available("s2") || !hub.available("s3k"))
                && x + hub.font.width(more, 1) + 8 < c.width()) {
            hub.font.right(c, more, c.width() - 8, 31, Gfx.DIM, 1);
        }
    }

    private void path(HubScene hub, SceneCanvas c, int w) {
        var cleared = hub.profile.cleared(chapter);
        for (int i = 0; i + 1 < stops.size(); i++) {
            int[] a = node(i, w), b = node(i + 1, w);
            boolean done = cleared.contains(Profile.act(stops.get(i).zone(), stops.get(i).act()));
            int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1])) / 4;
            for (int s = 1; s < steps; s++) {
                int x = a[0] + (b[0] - a[0]) * s / steps, y = a[1] + (b[1] - a[1]) * s / steps;
                if (done) {
                    c.fill(x - 1, y - 1, 3, 3, 0xFF000000);
                    c.fill(x, y, 2, 2, Gfx.GOLD);
                } else if (s % 2 == 0) {
                    c.fill(x, y, 2, 2, 0xA0A0A8C0);
                }
            }
        }
    }

    private void nodeAt(HubScene hub, SceneCanvas c, int i, int w, long t) {
        int[] p = node(i, w);
        StoryBook.Stop stop = stops.get(i);
        StoryBook.State s = state(hub, i);
        boolean on = i == cursor;
        int r = on ? 9 : 8;
        int rim = switch (s) {
            case CLEARED -> Gfx.GOLD;
            case OPEN -> Gfx.alpha(0xFF80D0FF, 0.6f + 0.4f * (float) Math.sin(t * 0.12));
            case LOCKED -> 0xFF686E80;
            case UNDER_CONSTRUCTION -> 0xFFE0A020;
        };
        int fill = switch (s) {
            case CLEARED -> 0xFF2858C8;
            case OPEN -> 0xFF1E3E9C;
            case LOCKED -> 0xFF303644;
            case UNDER_CONSTRUCTION -> 0xFF403018;
        };
        disc(c, p[0], p[1], r + 1, 0xFF000000);
        disc(c, p[0], p[1], r, rim);
        disc(c, p[0], p[1], r - 2, fill);
        SceneImage icon = switch (s) {
            case CLEARED -> hub.profile.hasEmerald(stop.game(), stop.zone(), stop.act()) ? null : hub.art.image("check");
            case LOCKED -> hub.art.image("lock");
            case UNDER_CONSTRUCTION -> hub.art.image("construction");
            case OPEN -> null;
        };
        if (s == StoryBook.State.CLEARED && icon == null) {
            int index = emeraldIndex(hub, i);
            SceneImage gem = hub.art.image("emerald");
            if (gem != null) c.draw(gem, p[0] - 5, p[1] - 4, com.openggf.mods.scene.SceneDraw.plain()
                    .withTint(HubArt.emeraldColour(index)));
        } else if (icon != null) {
            c.draw(icon, p[0] - icon.width() / 2f, p[1] - icon.height() / 2f);
        } else {
            hub.font.draw(c, Integer.toString(i + 1), p[0] - hub.font.width(Integer.toString(i + 1), 1) / 2, p[1] - 4,
                    Gfx.WHITE, 1);
        }
        String code = stop.code();
        int colour = on ? Gfx.GOLD : s == StoryBook.State.LOCKED ? 0xFF8890A0 : Gfx.TEXT;
        hub.font.centred(c, code, p[0], p[1] + r + 3, colour, 1);
    }

    private int emeraldIndex(HubScene hub, int stopIndex) {
        int n = 0;
        for (int i = 0; i < stopIndex; i++) {
            StoryBook.Stop s = stops.get(i);
            if (hub.profile.hasEmerald(s.game(), s.zone(), s.act())) n++;
        }
        return n;
    }

    private static void disc(SceneCanvas c, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            c.fill(cx - half, cy + dy, half * 2 + 1, 1, argb);
        }
    }

    private static final int PANEL_Y = 154;
    private static final int BAY = 64;

    /**
     * The stop panel: name, act and game, status, best run; the bay at its right holds the
     * zone's badnik (open and locked stops), its emerald (cleared) or a hazard sign (no rail).
     */
    private void info(HubScene hub, SceneCanvas c, int w, long t) {
        StoryBook.Stop stop = stops.get(cursor);
        StoryBook.State s = state(hub, cursor);
        int y = PANEL_Y, h = 52;
        Gfx.panel(c, 8, y, w - 16, h);
        hub.font.outline(c, stop.title().toUpperCase(), 16, y + 5, Gfx.WHITE, 0xFF0A1440, 2);
        String act = "ACT " + (stop.act() + 1) + "  " + HubScene.gameName(stop.game());
        if (!stop.game().equals(hub.game) && s != StoryBook.State.UNDER_CONSTRUCTION) act += "  (SWITCHES GAME)";
        hub.font.shadow(c, act, 16, y + 23, Gfx.DIM, 1);
        String status = switch (s) {
            case CLEARED -> hub.profile.hasEmerald(stop.game(), stop.zone(), stop.act()) ? "CLEARED - EMERALD WON" : "CLEARED";
            case OPEN -> "TYPE GO TO PLAY";
            case LOCKED -> "LOCKED: CLEAR THE STOPS BEFORE IT";
            case UNDER_CONSTRUCTION -> "UNDER CONSTRUCTION";
        };
        int statusColour = switch (s) {
            case CLEARED -> Gfx.GOLD;
            case OPEN -> (t / 20) % 2 == 0 ? Gfx.WHITE : 0xFF80D0FF;
            case LOCKED -> 0xFF8890A0;
            case UNDER_CONSTRUCTION -> 0xFFE0A020;
        };
        hub.font.shadow(c, status, 16, y + 33, statusColour, 1);
        Profile.Entry best = hub.profile.bestForAct(stop.game(), stop.zone(), stop.act());
        if (best != null) {
            hub.font.shadow(c, "BEST " + best.score() + "  RANK " + best.grade() + "  " + Math.round(best.wpm()) + " WPM",
                    16, y + 43, Gfx.TEXT, 1);
        } else if (s == StoryBook.State.UNDER_CONSTRUCTION) {
            hub.font.shadow(c, "THIS ACT'S RAIL IS STILL BEING BUILT", 16, y + 43, Gfx.DIM, 1);
        }
        bay(hub, c, stop, s, w - 8 - BAY / 2, y + h - 4, t);
    }

    private void bay(HubScene hub, SceneCanvas c, StoryBook.Stop stop, StoryBook.State s, int cx, int floor, long t) {
        switch (s) {
            case CLEARED -> {
                SceneImage gem = hub.art.image(hub.profile.hasEmerald(stop.game(), stop.zone(), stop.act()) ? "emerald" : "check");
                if (gem == null) return;
                float bob = (float) Math.sin(t * 0.1) * 2;
                var style = com.openggf.mods.scene.SceneDraw.plain().withScale(2);
                if (hub.profile.hasEmerald(stop.game(), stop.zone(), stop.act())) {
                    style = style.withTint(HubArt.emeraldColour(emeraldIndex(hub, cursor)));
                }
                c.draw(gem, cx - gem.width(), floor - 30 + bob, style);
            }
            case UNDER_CONSTRUCTION -> {
                SceneImage sign = hub.art.image("construction");
                if (sign != null) c.draw(sign, cx - sign.width(), floor - 26,
                        com.openggf.mods.scene.SceneDraw.plain().withScale(2));
            }
            default -> guard(hub, c, stop, s, cx, floor, t);
        }
    }
}
