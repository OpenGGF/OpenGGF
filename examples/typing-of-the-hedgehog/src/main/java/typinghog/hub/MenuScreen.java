package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneImage;
import java.util.List;
import typinghog.hub.model.WordMenu;

/**
 * The main menu. Type an item's name (STORY, ARCADE, BOSS RUSH, ENDLESS, SCHOOL, RECORDS,
 * OPTIONS, QUIT) or move with the arrows and press Enter. Arcade, Boss Rush and Endless open
 * once a story act has been cleared.
 */
final class MenuScreen implements Screen {
    private static final int ROW_H = 17;
    private static final int TOP = 28;

    private final WordMenu menu = new WordMenu(List.of(
            WordMenu.Item.of("STORY", "story"),
            WordMenu.Item.of("ARCADE", "arcade"),
            WordMenu.Item.of("BOSS RUSH", "boss rush", "rush"),
            WordMenu.Item.of("ENDLESS", "endless"),
            WordMenu.Item.of("TYPING SCHOOL", "school", "typing school", "tails"),
            WordMenu.Item.of("RECORDS", "records"),
            WordMenu.Item.of("OPTIONS", "options"),
            WordMenu.Item.of("QUIT", "quit", "exit")));
    private long moveTick;
    private long shakeTick = -100;
    private int previous;

    @Override public String name() { return "MENU"; }

    @Override public void open(HubScene hub) {
        menu.clear();
        hub.audio.theme(HubAudio.Theme.MENU);
    }

    @Override public void update(HubScene hub) {
        Controls in = hub.controls;
        int before = menu.cursor();
        if (in.up()) move(hub, -1);
        if (in.down()) move(hub, 1);
        if (in.backspace()) menu.backspace();
        int x = panelX(hub), w = hub.width() - x - 10;
        for (int i = 0; i < menu.items().size(); i++) {
            int y = TOP + 8 + i * ROW_H;
            if (in.hover(x, y, w, ROW_H) && menu.cursor() != i) {
                menu.setCursor(i);
                moved(hub);
            }
            if (in.click(x, y, w, ROW_H)) {
                menu.setCursor(i);
                choose(hub, i);
                return;
            }
        }
        WordMenu.Outcome outcome = menu.type(in.typed());
        switch (outcome) {
            case SELECTED -> {
                choose(hub, menu.selected());
                return;
            }
            case MOVED -> hub.audio.cue(HubAudio.Cue.KEY);
            case REJECTED -> {
                shakeTick = hub.ticks;
                hub.audio.cue(HubAudio.Cue.ERROR);
            }
            default -> { }
        }
        if (menu.cursor() != before) {
            previous = before;
            moveTick = hub.ticks;
        }
        if (in.accept()) choose(hub, menu.cursor());
        else if (in.back()) hub.go(hub.title);
    }

    private void move(HubScene hub, int delta) {
        menu.move(delta);
        moved(hub);
    }

    private void moved(HubScene hub) {
        hub.audio.cue(HubAudio.Cue.MOVE);
    }

    private boolean locked(HubScene hub, int item) {
        return (item == 1 || item == 2 || item == 3) && !hub.anyCleared();
    }

    private void choose(HubScene hub, int item) {
        if (locked(hub, item)) {
            hub.refuse("CLEAR A STORY ACT FIRST");
            return;
        }
        hub.audio.cue(HubAudio.Cue.CONFIRM);
        switch (item) {
            case 0 -> hub.go(hub.storyMap);
            case 1 -> hub.go(hub.arcade);
            case 2 -> hub.go(hub.rush);
            case 3 -> hub.go(hub.endless);
            case 4 -> hub.go(hub.school);
            case 5 -> hub.go(hub.records);
            case 6 -> hub.go(hub.options);
            default -> hub.quit();
        }
    }

    private static int panelX(HubScene hub) { return Math.max(150, hub.width() - 250); }

    private static String describe(int item) {
        return switch (item) {
            case 0 -> "Run every zone of every Sonic you own. Badniks carry words: type them first!";
            case 1 -> "Replay any cleared act for a high score on the top-10 board.";
            case 2 -> "Robotnik's machines back to back. Every win adds a star to that boss.";
            case 3 -> "One stand, wave after wave, until the rings run out.";
            case 4 -> "Tails teaches touch typing: lessons, graded drills and the Final Exam.";
            case 5 -> "Best scores, drill grades and your keyboard heatmap.";
            case 6 -> "Difficulty, dictionaries and assists.";
            default -> "Back to the OpenGGF title screen.";
        };
    }

    @Override public void draw(HubScene hub, SceneCanvas c) {
        long t = hub.ticks;
        hub.backdrop(c, 0.35f);
        int w = c.width();
        int floor = hub.stage.floorRow(70, t);
        Hero.sonic(c, hub.art, hub.game, HubArt.ANIM_WAIT, t, 70, floor, false, 1f);
        hub.header(c, "MAIN MENU", "EMERALDS " + hub.profile.totalEmeralds() + "   LIFETIME WPM "
                + Math.round(hub.profile.lifetimeWpm()));
        int x = panelX(hub), pw = w - x - 10;
        int shake = t - shakeTick < 10 ? (int) Math.round(Math.sin((t - shakeTick) * 2.2) * 3) : 0;
        Gfx.panel(c, x - 6 + shake, TOP, pw + 12, menu.items().size() * ROW_H + 16);
        float glide = Math.min(1, (t - moveTick) / 5f);
        int fromY = TOP + 8 + previous * ROW_H, toY = TOP + 8 + menu.cursor() * ROW_H;
        int hy = Math.round(fromY + (toY - fromY) * glide);
        Gfx.highlight(c, x + shake, hy, pw, ROW_H - 2, t);
        for (int i = 0; i < menu.items().size(); i++) {
            int y = TOP + 8 + i * ROW_H;
            WordMenu.Item item = menu.items().get(i);
            boolean locked = locked(hub, i);
            boolean on = i == menu.cursor();
            SceneImage icon = hub.art.image(locked ? "lock" : "icon." + iconName(i));
            if (icon != null) c.draw(icon, x + 6 + shake, y + (ROW_H - 2 - icon.height()) / 2f);
            String label = item.label();
            int lx = x + 26 + shake, ly = y + 1;
            int colour = locked ? 0xFF687088 : on ? Gfx.WHITE : Gfx.TEXT;
            hub.font.shadow(c, label, lx, ly, colour, 2);
            if (on) {
                // Light the letters typed so far, wherever the spelled word sits in the label.
                int matched = menu.matched(i);
                String word = menu.spelling(i).toUpperCase();
                int at = label.indexOf(word);
                if (matched > 0 && at >= 0) {
                    hub.font.draw(c, label.substring(at, at + matched), lx + at * HubFont.ADVANCE * 2, ly,
                            Gfx.TYPED, 2);
                }
                hub.ring(c, x - 12 + shake, y + 8, t);
            }
        }
        String desc = locked(hub, menu.cursor()) ? "Clear a story act to open this mode." : describe(menu.cursor());
        int dy = TOP + menu.items().size() * ROW_H + 22;
        for (String line : hub.font.wrap(desc, pw, 1)) {
            hub.font.shadow(c, line, x, dy, Gfx.TEXT, 1);
            dy += 11;
        }
        hub.footer(c, menu.buffer(), "TYPE A MODE OR USE THE ARROWS");
    }

    private static String iconName(int i) {
        return switch (i) {
            case 0 -> "story";
            case 1 -> "arcade";
            case 2 -> "rush";
            case 3 -> "endless";
            case 4 -> "school";
            case 5 -> "records";
            case 6 -> "options";
            default -> "quit";
        };
    }
}
