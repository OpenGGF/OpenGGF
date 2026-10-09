package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import typinghog.hub.model.Profile;
import typinghog.hub.model.WordMenu;
import typinghog.save.LaunchIntent;

/**
 * Options: difficulty, dictionaries (the standard list is always on; Sonic lore, tricky words
 * and the player's own {@code words.txt} mix in, as in Overkill), Capitals and Punctuation,
 * the No-Fail and Slow-Motion assists, and the voice switch. Left and Right (or Enter) change
 * a row; typing a row's name toggles it, and typing EASY, NORMAL, HARD or EXPERT sets the
 * difficulty. Every change is saved at once.
 */
final class OptionsScreen implements Screen {
    private static final int ROWS = 10;
    private static final int ROW_H = 14;
    private static final int TOP = 33;

    private final WordMenu words = new WordMenu(List.of(
            WordMenu.Item.of("DIFFICULTY", "difficulty"),
            WordMenu.Item.of("SONIC LORE WORDS", "lore", "sonic lore"),
            WordMenu.Item.of("TRICKY WORDS", "tricky"),
            WordMenu.Item.of("YOUR WORD LIST", "your words", "user", "word list"),
            WordMenu.Item.of("CAPITALS", "capitals"),
            WordMenu.Item.of("PUNCTUATION", "punctuation"),
            WordMenu.Item.of("NO-FAIL", "no fail", "nofail"),
            WordMenu.Item.of("SLOW-MOTION", "slow motion", "slowmo", "slow"),
            WordMenu.Item.of("VOICE", "voice"),
            WordMenu.Item.of("BACK", "back", "menu"),
            WordMenu.Item.of("EASY", "easy"),
            WordMenu.Item.of("NORMAL", "normal"),
            WordMenu.Item.of("HARD", "hard"),
            WordMenu.Item.of("EXPERT", "expert")));
    private int cursor;
    private long shakeTick = -100;
    private long changeTick = -100;

    @Override public String name() { return "OPTIONS"; }

    @Override public void open(HubScene hub) {
        words.clear();
        hub.audio.theme(HubAudio.Theme.MENU);
    }

    @Override public void update(HubScene hub) {
        Controls in = hub.controls;
        if (in.up()) move(hub, -1);
        if (in.down()) move(hub, 1);
        if (in.left()) change(hub, cursor, -1);
        if (in.right()) change(hub, cursor, 1);
        if (in.backspace()) words.backspace();
        switch (words.type(in.typed())) {
            case REJECTED -> {
                shakeTick = hub.ticks;
                hub.audio.cue(HubAudio.Cue.ERROR);
            }
            case MOVED -> {
                hub.audio.cue(HubAudio.Cue.KEY);
                if (words.cursor() < ROWS) cursor = words.cursor();
            }
            case SELECTED -> {
                int item = words.selected();
                if (item >= ROWS) {
                    cursor = 0;
                    set(hub, LaunchIntent.Difficulty.values()[item - ROWS]);
                } else {
                    cursor = item;
                    if (item == ROWS - 1) {
                        hub.go(hub.menu);
                        return;
                    }
                    change(hub, item, 1);
                }
            }
            default -> { }
        }
        if (in.accept()) {
            if (cursor == ROWS - 1) {
                hub.audio.cue(HubAudio.Cue.BACK);
                hub.go(hub.menu);
                return;
            }
            change(hub, cursor, 1);
        } else if (in.back()) {
            hub.audio.cue(HubAudio.Cue.BACK);
            hub.go(hub.menu);
        }
    }

    private void move(HubScene hub, int delta) {
        cursor = Math.floorMod(cursor + delta, ROWS);
        words.setCursor(cursor);
        words.clear();
        hub.audio.cue(HubAudio.Cue.MOVE);
    }

    private void set(HubScene hub, LaunchIntent.Difficulty d) {
        hub.profile.difficulty(d);
        changed(hub);
    }

    private void change(HubScene hub, int row, int delta) {
        Profile p = hub.profile;
        switch (row) {
            case 0 -> {
                LaunchIntent.Difficulty[] all = LaunchIntent.Difficulty.values();
                p.difficulty(all[Math.floorMod(p.difficulty().ordinal() + delta, all.length)]);
            }
            case 1 -> p.lore(!p.lore());
            case 2 -> p.tricky(!p.tricky());
            case 3 -> {
                if (hub.userWords() == 0 && !p.userWords()) {
                    hub.refuse("ADD WORDS TO " + HubScene.MOD_FOLDER.toUpperCase() + "WORDS.TXT");
                    return;
                }
                p.userWords(!p.userWords());
            }
            case 4 -> p.capitals(!p.capitals());
            case 5 -> p.punctuation(!p.punctuation());
            case 6 -> p.noFail(!p.noFail());
            case 7 -> p.slowMotion(!p.slowMotion());
            case 8 -> p.voice(!p.voice());
            default -> {
                return;
            }
        }
        changed(hub);
    }

    private void changed(HubScene hub) {
        changeTick = hub.ticks;
        hub.save();
        hub.audio.cue(HubAudio.Cue.CONFIRM);
    }

    private String value(HubScene hub, int row) {
        Profile p = hub.profile;
        return switch (row) {
            case 0 -> p.difficulty().name();
            case 1 -> onOff(p.lore());
            case 2 -> onOff(p.tricky());
            case 3 -> p.userWords() ? "ON (" + hub.userWords() + ")" : hub.userWords() == 0 ? "NONE" : "OFF (" + hub.userWords() + ")";
            case 4 -> onOff(p.capitals());
            case 5 -> onOff(p.punctuation());
            case 6 -> onOff(p.noFail());
            case 7 -> onOff(p.slowMotion());
            case 8 -> onOff(p.voice());
            default -> "";
        };
    }

    private static String onOff(boolean on) { return on ? "ON" : "OFF"; }

    private static String describe(int row) {
        return switch (row) {
            case 0 -> "Easy slows badniks and keeps words short; Expert sends them fast with long words and phrases.";
            case 1 -> "Mix in names from the games you own: zones, badniks, bosses and items.";
            case 2 -> "Mix in long, awkward words with double letters and far reaches.";
            case 3 -> "Your own list, one word per line, in " + HubScene.MOD_FOLDER + "words.txt.";
            case 4 -> "Capital letters must be typed with Shift.";
            case 5 -> "Commas, stops and apostrophes must be typed too.";
            case 6 -> "Running out of rings never ends the act.";
            case 7 -> "Badniks approach at 60% speed.";
            case 8 -> "The announcer, Robotnik and Tails speak their lines.";
            default -> "Back to the main menu.";
        };
    }

    @Override public void draw(HubScene hub, SceneCanvas c) {
        long t = hub.ticks;
        int w = c.width();
        hub.backdrop(c, 0.55f);
        hub.header(c, "OPTIONS", "SAVED AS YOU GO");
        int shake = t - shakeTick < 10 ? (int) Math.round(Math.sin((t - shakeTick) * 2.2) * 3) : 0;
        int x = 40, pw = w - 80;
        Gfx.panel(c, x - 8 + shake, TOP - 6, pw + 16, ROWS * ROW_H + 10);
        for (int i = 0; i < ROWS; i++) {
            int y = TOP + i * ROW_H;
            boolean on = i == cursor;
            if (on) Gfx.highlight(c, x + shake, y - 2, pw, ROW_H - 1, t);
            String label = words.items().get(i).label();
            hub.font.shadow(c, label, x + 8 + shake, y + 1, on ? Gfx.WHITE : Gfx.TEXT, 1);
            if (on && words.matched(i) > 0) {
                String spelled = words.spelling(i).toUpperCase();
                int at = label.replace('-', ' ').indexOf(spelled.substring(0, Math.min(spelled.length(), words.matched(i))));
                if (at >= 0) hub.font.draw(c, label.substring(at, at + words.matched(i)), x + 8 + shake + at * HubFont.ADVANCE, y + 1,
                        Gfx.TYPED, 1);
            }
            String value = value(hub, i);
            boolean bump = on && t - changeTick < 8;
            int colour = value.startsWith("ON") || i == 0 ? Gfx.GOLD : Gfx.DIM;
            if (!value.isEmpty()) {
                String shown = i == 0 && on ? "< " + value + " >" : value;
                hub.font.right(c, shown, x + pw - 8 + shake, y + 1 - (bump ? 1 : 0), colour, 1);
            }
        }
        int dy = TOP + ROWS * ROW_H + 8;
        Gfx.panel(c, x - 8, dy, pw + 16, 26, 0xC0101830, 0xFF3058B0);
        int ly = dy + 5;
        for (String line : hub.font.wrap(describe(cursor), pw, 1)) {
            hub.font.shadow(c, line, x, ly, Gfx.TEXT, 1);
            ly += 10;
        }
        hub.footer(c, words.buffer(), "TYPE AN OPTION TO TOGGLE IT");
    }
}
