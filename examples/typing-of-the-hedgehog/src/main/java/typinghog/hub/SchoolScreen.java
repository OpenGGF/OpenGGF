package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneImage;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import typinghog.core.TypingField;
import typinghog.hub.model.Drill;
import typinghog.hub.model.DrillSession;
import typinghog.hub.model.Grades;
import typinghog.hub.model.Handoff;
import typinghog.hub.model.School;
import typinghog.hub.model.WordMenu;

/**
 * Tails' Typing School, played entirely in the hub. Lessons on the left, graded drills on the
 * right; the Final Exam opens once every drill has a C. Tails (from a Sonic 2 or 3 &amp;
 * Knuckles ROM; a Flicky stands in for an S1-only session) instructs from the corner, and
 * a Sonic-blue keyboard shows the next key and, on the menu, your lifetime accuracy per key.
 *
 * <p>Drills run through {@link DrillSession}, which types with the course's own
 * {@code TypingField} and scores with its {@code Scorecard}. Escape leaves a drill ungraded.
 */
final class SchoolScreen implements Screen {
    private enum Mode { MENU, PLAY, RESULT }

    private static final int GRID_Y = 30;
    /** The drill arena's left edge; Tails and his speech bubble stand left of it. */
    private static final int ARENA_LEFT = 114;
    private static final int CELL_W = 95;
    private static final int CELL_H = 15;
    private static final int ROWS = 7;

    private final List<Drill> left = List.of(Drill.HOME_ROW, Drill.TOP_ROW, Drill.BOTTOM_ROW, Drill.NUMBERS,
            Drill.CAPITALS, Drill.PUNCTUATION, Drill.FINAL_EXAM);
    private final List<Drill> right = List.of(Drill.RING_RUSH, Drill.SUDDEN_DEATH, Drill.REFLEX, Drill.LONG_WORDS,
            Drill.PHRASES, Drill.BOSS_TRAINING, Drill.WEAK_KEYS);
    private final Keyboard keyboard = new Keyboard();

    private Mode mode = Mode.MENU;
    private int col;
    private int row;
    private WordMenu words;
    private DrillSession session;
    private String speech = "";
    private long speechTick;
    private long modeTick;
    private long shakeTick = -100;
    private boolean improved;
    private int sessions;
    private String typed = "";

    @Override public String name() {
        return switch (mode) {
            case MENU -> "SCHOOL";
            case PLAY -> "DRILL";
            case RESULT -> "GRADE";
        };
    }

    /** The drill under play (tests and tools). */
    DrillSession session() { return session; }

    @Override public void open(HubScene hub) {
        mode = Mode.MENU;
        modeTick = hub.ticks;
        List<WordMenu.Item> items = new ArrayList<>();
        for (Drill d : all()) items.add(WordMenu.Item.of(d.label, d.word, d.label));
        items.add(WordMenu.Item.of("BACK", "back", "menu"));
        words = new WordMenu(items);
        say(hub, hub.material.tails("welcome", (int) (hub.ticks / 7)));
        hub.say(typinghog.audio.VoiceLine.TAILS_WELCOME);
        hub.audio.theme(HubAudio.Theme.SCHOOL);
    }

    private List<Drill> all() {
        List<Drill> out = new ArrayList<>(left);
        out.addAll(right);
        return out;
    }

    private Drill selected() { return (col == 0 ? left : right).get(row); }

    private void say(HubScene hub, String line) {
        if (line == null || line.isEmpty() || line.equals(speech)) return;
        speech = line;
        speechTick = hub.ticks;
    }

    @Override public void update(HubScene hub) {
        switch (mode) {
            case MENU -> menu(hub);
            case PLAY -> play(hub);
            case RESULT -> result(hub);
        }
    }

    // ---- menu -------------------------------------------------------------------------------

    private void menu(HubScene hub) {
        Controls in = hub.controls;
        Drill before = selected();
        if (in.up()) row = Math.floorMod(row - 1, ROWS);
        if (in.down()) row = Math.floorMod(row + 1, ROWS);
        if (in.left() || in.right()) col = 1 - col;
        if (in.backspace()) words.backspace();
        switch (words.type(in.typed())) {
            case REJECTED -> {
                shakeTick = hub.ticks;
                hub.audio.cue(HubAudio.Cue.ERROR);
            }
            case MOVED -> {
                hub.audio.cue(HubAudio.Cue.KEY);
                pointAt(words.cursor());
            }
            case SELECTED -> {
                if (words.selected() >= all().size()) {
                    hub.go(hub.menu);
                    return;
                }
                pointAt(words.selected());
                start(hub, selected());
                return;
            }
            default -> { }
        }
        if (selected() != before) {
            hub.audio.cue(HubAudio.Cue.MOVE);
            say(hub, hint(hub, selected()));
        }
        if (in.accept()) start(hub, selected());
        else if (in.back()) {
            hub.audio.cue(HubAudio.Cue.BACK);
            hub.go(hub.menu);
        }
    }

    private void pointAt(int index) {
        if (index < 0 || index >= all().size()) return;
        Drill d = all().get(index);
        col = left.contains(d) ? 0 : 1;
        row = (col == 0 ? left : right).indexOf(d);
    }

    private String hint(HubScene hub, Drill d) {
        if (d == Drill.FINAL_EXAM) {
            return hub.material.tails(School.examUnlocked(hub.profile) ? "exam.open" : "exam.locked", 0);
        }
        return hub.material.tails(d.name(), 0);
    }

    /** Starts a drill (also the {@code drill:NAME} debug command). */
    void start(HubScene hub, Drill drill) {
        pointAt(all().indexOf(drill));
        if (drill == Drill.FINAL_EXAM && !School.examUnlocked(hub.profile)) {
            hub.refuse("GET A C IN EVERY DRILL FIRST");
            say(hub, hub.material.tails("exam.locked", 0));
            return;
        }
        long seed = Handoff.seed(hub.profile.launches() * 31 + ++sessions, hub.ticks);
        List<DrillSession.Item> items = hub.material.items(drill, new SplittableRandom(seed), hub.profile.keyHits(),
                hub.profile.keyMisses());
        if (items.isEmpty()) {
            hub.refuse("NO WORDS FOR THIS DRILL");
            return;
        }
        session = new DrillSession(drill, items, seed);
        mode = Mode.PLAY;
        modeTick = hub.ticks;
        typed = "";
        say(hub, hub.material.tails(drill.name(), 0));
        hub.say(HubScene.line("TAILS_" + drill.name()));
        hub.audio.cue(HubAudio.Cue.CONFIRM);
    }

    // ---- play -------------------------------------------------------------------------------

    private void play(HubScene hub) {
        Controls in = hub.controls;
        if (in.back()) {
            session.abandon();
            mode = Mode.MENU;
            modeTick = hub.ticks;
            say(hub, hub.material.tails("quit", 0));
            hub.audio.cue(HubAudio.Cue.BACK);
            return;
        }
        if (in.backspace()) session.backspace();
        List<DrillSession.Event> events = new ArrayList<>(session.type(in.typed()));
        events.addAll(session.tick());
        for (DrillSession.Event e : events) react(hub, e);
        if (session.phase() == DrillSession.Phase.FINISHED) finish(hub);
    }

    private void react(HubScene hub, DrillSession.Event e) {
        switch (e.signal()) {
            case KEY -> {
                keyboard.flash(e.codePoint(), true, hub.ticks);
                hub.audio.cue(HubAudio.Cue.KEY);
            }
            case ERROR, RESTART -> {
                keyboard.flash(e.codePoint(), false, hub.ticks);
                shakeTick = hub.ticks;
                hub.audio.cue(HubAudio.Cue.ERROR);
                if (e.signal() == DrillSession.Signal.RESTART || session.card().errors() % 4 == 1) {
                    say(hub, hub.material.tails("oops", session.card().errors()));
                    hub.say(session.card().errors() % 8 == 1 ? typinghog.audio.VoiceLine.TAILS_KEEP_GOING
                            : typinghog.audio.VoiceLine.TAILS_SLOW_DOWN);
                }
            }
            case COMPLETE -> {
                hub.audio.cue(HubAudio.Cue.CONFIRM);
                int chain = session.card().chain();
                if (chain > 0 && chain % 6 == 0) {
                    say(hub, hub.material.tails("cheer", chain / 6));
                    hub.say(chain % 12 == 0 ? typinghog.audio.VoiceLine.TAILS_AWESOME : typinghog.audio.VoiceLine.TAILS_GREAT);
                }
            }
            case ROUND_WON -> {
                hub.audio.cue(HubAudio.Cue.REGISTER);
                say(hub, hub.material.tails("round.won", session.successes()));
            }
            case ROUND_LOST, MISSED -> {
                hub.audio.cue(HubAudio.Cue.ERROR);
                if (e.signal() == DrillSession.Signal.ROUND_LOST) say(hub, hub.material.tails("round.lost", session.completed()));
            }
            default -> { }
        }
    }

    private void finish(HubScene hub) {
        mode = Mode.RESULT;
        modeTick = hub.ticks;
        typed = "";
        boolean examWasOpen = School.examUnlocked(hub.profile);
        improved = School.record(hub.profile, session);
        hub.save();
        DrillSession.Result r = session.result();
        say(hub, hub.material.tails("grade." + r.grade(), (int) hub.ticks));
        if (r.drill() == Drill.FINAL_EXAM && Grades.atLeast(r.grade(), "C")) {
            hub.say(typinghog.audio.VoiceLine.TAILS_EXAM_PASSED);
        } else {
            hub.say(HubScene.line("TAILS_GRADE_" + r.grade()));
        }
        if (!examWasOpen && School.examUnlocked(hub.profile)) hub.say(typinghog.audio.VoiceLine.TAILS_UNLOCKED);
    }

    // ---- result -----------------------------------------------------------------------------

    private void result(HubScene hub) {
        Controls in = hub.controls;
        long age = hub.ticks - modeTick;
        if (age == 24) hub.audio.cue(HubAudio.Cue.REGISTER);
        for (char c : in.typed().toCharArray()) if (Character.isLetter(c)) typed += Character.toLowerCase(c);
        if (typed.length() > 8) typed = typed.substring(typed.length() - 8);
        if (typed.endsWith("again") || typed.endsWith("retry")) {
            typed = "";
            start(hub, session.drill());
            return;
        }
        if ((in.accept() || in.back()) && age > 20) {
            mode = Mode.MENU;
            modeTick = hub.ticks;
            hub.audio.cue(HubAudio.Cue.CONFIRM);
            say(hub, hint(hub, selected()));
        }
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override public void draw(HubScene hub, SceneCanvas c) {
        long t = hub.ticks;
        Gfx.checker(c, 0, 0, c.width(), c.height(), 16, 0xFF14307C, 0xFF183A8C, t / 3);
        c.fill(0, 0, c.width(), c.height(), 0x40000820);
        switch (mode) {
            case MENU -> drawMenu(hub, c, t);
            case PLAY -> drawPlay(hub, c, t);
            case RESULT -> drawResult(hub, c, t);
        }
    }

    /** The drill grid's left edge: two {@link #CELL_W}-wide columns against the right edge. */
    private static int gridX(int width) { return width - 2 * CELL_W - 6; }

    /** Keycap size for the menu heatmap: smaller on narrow screens. */
    private static int heatKey(int width) { return width >= 380 ? 12 : 10; }

    private void drawMenu(HubScene hub, SceneCanvas c, long t) {
        int w = c.width();
        int gx = gridX(w);
        hub.header(c, w >= 380 ? "TAILS' TYPING SCHOOL" : "TYPING SCHOOL", School.examUnlocked(hub.profile)
                ? "FINAL EXAM OPEN" : School.drillsBelowC(hub.profile) + " DRILLS TO PASS");
        Hero.instructor(c, hub.art, hub.game, t, 28, 132, t - speechTick < 60);
        Hero.speech(c, hub.font, speech, 38, 26, Math.min(160, gx - 44), 44, t - speechTick);
        // The lifetime heatmap, and the selected drill beside it.
        int key = heatKey(w);
        hub.font.shadow(c, "YOUR KEYS", 8, 136, Gfx.GOLD, 1);
        keyboard.draw(c, hub.font, 12, 150, key, 9, -1, hub.profile, t);
        int shake = t - shakeTick < 10 ? (int) Math.round(Math.sin((t - shakeTick) * 2.2) * 3) : 0;
        hub.font.shadow(c, "LESSONS", gx + 4, GRID_Y - 4, 0xFF80D0FF, 1);
        hub.font.shadow(c, "DRILLS", gx + CELL_W + 4, GRID_Y - 4, 0xFF80D0FF, 1);
        for (int ci = 0; ci < 2; ci++) {
            List<Drill> list = ci == 0 ? left : right;
            for (int ri = 0; ri < list.size(); ri++) {
                Drill d = list.get(ri);
                int x = gx + ci * CELL_W + shake, y = GRID_Y + 6 + ri * CELL_H;
                boolean on = ci == col && ri == row;
                boolean locked = d == Drill.FINAL_EXAM && !School.examUnlocked(hub.profile);
                if (on) Gfx.highlight(c, x, y, CELL_W - 3, CELL_H - 2, t);
                else Gfx.panel(c, x, y, CELL_W - 3, CELL_H - 2, 0xC0101C48, d.exam() ? Gfx.GOLD_DARK : 0xFF3058B0);
                String label = d.label.toUpperCase();
                hub.font.shadow(c, label, x + 4, y + 2, locked ? 0xFF687088 : on ? Gfx.WHITE : Gfx.TEXT, 1);
                int index = all().indexOf(d);
                if (on && words.matched(index) > 0 && words.spelling(index).equalsIgnoreCase(d.word)) {
                    hub.font.draw(c, label.substring(0, Math.min(label.length(), words.matched(index))), x + 4, y + 2,
                            Gfx.TYPED, 1);
                }
                String grade = hub.profile.grade(d.name());
                if (locked) {
                    SceneImage lock = hub.art.image("lock");
                    if (lock != null) c.draw(lock, x + CELL_W - 15, y + 1);
                } else if (grade != null) {
                    badge(hub, c, grade, x + CELL_W - 15, y);
                }
            }
        }
        Drill d = selected();
        int ix = 12 + Keyboard.width(key) + 14, iy = GRID_Y + 6 + ROWS * CELL_H + 5, iw = w - ix - 6;
        Gfx.panel(c, ix, iy, iw, 204 - iy);
        hub.font.shadow(c, d.label.toUpperCase(), ix + 6, iy + 5, Gfx.GOLD, 1);
        String best = hub.profile.grade(d.name());
        hub.font.right(c, best == null ? "NOT TRIED" : "BEST " + best, w - 12, iy + 5, Grades.colour(best), 1);
        int ly = iy + 17;
        for (String line : hub.font.wrap(describe(d), iw - 12, 1)) {
            if (ly > 204 - 12) break;
            hub.font.shadow(c, line, ix + 6, ly, Gfx.TEXT, 1);
            ly += 10;
        }
        hub.footer(c, words.buffer(), "TYPE A LESSON OR DRILL");
    }

    private static String describe(Drill d) {
        return switch (d) {
            case HOME_ROW -> "Words from A S D F G H J K L only.";
            case TOP_ROW -> "Reach up to Q W E R T Y U I O P.";
            case BOTTOM_ROW -> "Reach down to Z X C V B N M.";
            case NUMBERS -> "The digits on the top row.";
            case CAPITALS -> "Names typed with Shift held: case counts here.";
            case PUNCTUATION -> "Commas, stops and apostrophes all count.";
            case RING_RUSH -> "Sixty seconds. As many words as you can.";
            case SUDDEN_DEATH -> "Keep going until your first mistake.";
            case REFLEX -> "Single letters flash up. Hit each before it fades.";
            case LONG_WORDS -> "Tricky long words at a steady rhythm.";
            case PHRASES -> "Whole phrases, spaces and all.";
            case BOSS_TRAINING -> "Perfect phrases, talk-backs and Robotnik's quiz.";
            case WEAK_KEYS -> "Words built on the keys you miss most.";
            case FINAL_EXAM -> "Everything at once. Needs a C in every drill.";
        };
    }

    private void badge(HubScene hub, SceneCanvas c, String grade, int x, int y) {
        int colour = Grades.colour(grade);
        c.fill(x, y, 11, 12, 0xFF000000);
        c.fill(x + 1, y + 1, 9, 10, Gfx.mix(colour, 0xFF000000, 0.55f));
        hub.font.draw(c, grade, x + 3, y + 2, colour, 1);
    }

    private void drawPlay(HubScene hub, SceneCanvas c, long t) {
        Drill d = session.drill();
        int w = c.width();
        String right;
        if (d.seconds > 0) {
            int left = Math.max(0, d.seconds * 60 - session.playRows());
            right = String.format(java.util.Locale.ROOT, "TIME %d:%02d", left / 3600, left / 60 % 60);
        } else {
            right = Math.min(session.total(), session.completed() + (session.current() != null ? 1 : 0)) + " / "
                    + session.total();
        }
        hub.header(c, d.label.toUpperCase(), right);
        var card = session.card();
        String hud = String.format(java.util.Locale.ROOT, "WPM %d  ACC %.0f%%  CHAIN %d",
                Math.round(card.wpm()), card.accuracy() * 100, card.chain());
        String score = "SCORE " + card.score();
        if (hub.font.width(hud + "  " + score, 1) <= w - ARENA_LEFT - 8) hud += "  " + score;
        hub.font.shadow(c, hud, ARENA_LEFT + 4, 28, Gfx.TEXT, 1);
        Hero.instructor(c, hub.art, hub.game, t, 24, 140, t - speechTick < 60);
        Hero.speech(c, hub.font, speech, 6, 28, 104, 20, t - speechTick);
        int shake = t - shakeTick < 10 ? (int) Math.round(Math.sin((t - shakeTick) * 2.2) * 3) : 0;
        int cx = ARENA_LEFT + (w - ARENA_LEFT - 6) / 2;
        if (session.phase() == DrillSession.Phase.READY) {
            String word = session.rows() < 50 ? "READY?" : "GO!";
            hub.font.outline(c, word, cx - hub.font.width(word, 3) / 2, 70, session.rows() < 50 ? Gfx.WHITE : Gfx.GOLD,
                    0xFF0A1440, 3);
        } else {
            arena(hub, c, cx, shake, t);
        }
        int key = w >= 380 ? 18 : 16;
        int kw = Keyboard.width(key);
        keyboard.draw(c, hub.font, (w - kw) / 2, 144, key, 13, session.expected(), null, t);
    }

    private void arena(HubScene hub, SceneCanvas c, int cx, int shake, long t) {
        DrillSession.Item item = session.current();
        List<TypingField.Target> targets = session.targets();
        int rowsLeft = session.rowsLeft();
        if (item == null || targets.isEmpty()) {
            if (session.drill() == typinghog.hub.model.Drill.REFLEX) {
                hub.font.centred(c, "WAIT FOR IT...", cx, 80, 0x90FFFFFF, 1);
            }
            return;
        }
        switch (item.kind()) {
            case QUIZ -> {
                int aw = c.width() - ARENA_LEFT - 6;
                var question = hub.font.wrap(item.display(), aw - 12, 1);
                Gfx.panel(c, ARENA_LEFT, 38, aw, 16 + question.size() * 10, 0xE0401018, 0xFFFF6040);
                hub.font.shadow(c, "ROBOTNIK'S QUIZ", ARENA_LEFT + 6, 42, 0xFFFF9080, 1);
                int qy = 53;
                for (String line : question) {
                    hub.font.shadow(c, line, ARENA_LEFT + 6, qy, Gfx.WHITE, 1);
                    qy += 10;
                }
                int n = targets.size();
                int widest = 0;
                for (TypingField.Target target : targets) widest = Math.max(widest, hub.font.width(target.prompt().display(), 1) + 10);
                boolean side = widest * n <= aw;
                for (int i = 0; i < n; i++) {
                    TypingField.Target target = targets.get(i);
                    int px = side ? ARENA_LEFT + aw * (2 * i + 1) / (2 * n) : ARENA_LEFT + aw / 2;
                    int py = side ? Math.max(qy + 8, 92) : qy + 6 + i * 15;
                    boolean locked = target.id() == session.lockedId();
                    Gfx.plate(c, hub.font, target.prompt().display(), target.progress(), px, py,
                            locked ? Gfx.GOLD : 0xFFE0E0E0, 1, locked ? shake : 0);
                }
            }
            case LETTER -> {
                int index = session.completed() * 7 + 3;
                int px = ARENA_LEFT + 26 + Math.floorMod(index * 53, c.width() - ARENA_LEFT - 58);
                int py = 48 + Math.floorMod(index * 29, 60);
                float fade = rowsLeft < 0 ? 1 : Math.min(1, rowsLeft / 30f);
                int rim = Gfx.alpha(0xFFFF6040, 0.4f + 0.6f * fade);
                Gfx.plate(c, hub.font, item.display(), 0, px, py, rim, 3, shake);
            }
            default -> {
                String label = switch (item.kind()) {
                    case PRACTICE -> "TYPE IT WITHOUT A MISTAKE";
                    case TALK_BACK -> "ROBOTNIK SAYS... TYPE IT BACK!";
                    default -> null;
                };
                if (label != null) hub.font.centred(c, label, cx, 48, item.kind() == DrillSession.Kind.TALK_BACK
                        ? 0xFFFF9080 : 0xFF80D0FF, 1);
                TypingField.Target target = targets.getFirst();
                String text = target.prompt().display();
                int scale = hub.font.width(text, 2) + 16 > c.width() - ARENA_LEFT - 6 ? 1 : 2;
                int progress = displayProgress(target, item);
                Gfx.plate(c, hub.font, text, progress, cx, 64, target.id() == session.lockedId() ? Gfx.GOLD : 0xFFE0E0E0,
                        scale, shake);
                List<String> next = session.upcoming(3);
                int ny = 64 + Gfx.plateHeight(scale) + 8;
                for (int i = 0; i < next.size(); i++) {
                    int alpha = 200 - i * 60;
                    String upcoming = next.get(i);
                    int room = (c.width() - ARENA_LEFT - 12) / HubFont.ADVANCE;
                    if (upcoming.length() > room) upcoming = upcoming.substring(0, Math.max(1, room - 1)).strip() + ".";
                    hub.font.centred(c, upcoming, cx, ny + i * 11, (alpha << 24) | 0xC8D0E8, 1);
                }
            }
        }
        if (rowsLeft >= 0 && session.drill().seconds == 0) {
            int limit = switch (item.kind()) {
                case LETTER -> DrillSession.REFLEX_WINDOW;
                case PRACTICE -> DrillSession.PRACTICE_ROWS;
                case TALK_BACK -> DrillSession.TALK_BACK_ROWS;
                default -> DrillSession.QUIZ_ROWS;
            };
            Gfx.meter(c, ARENA_LEFT + 6, 134, c.width() - ARENA_LEFT - 18, 3, rowsLeft / (double) limit, rowsLeft < limit / 4 ? Gfx.RED : Gfx.GOLD);
        }
    }

    /** Display characters already typed: walks the display, skipping characters the typed form drops. */
    private static int displayProgress(TypingField.Target target, DrillSession.Item item) {
        String display = target.prompt().display();
        int typedChars = target.progress();
        int done = 0, shown = 0;
        while (shown < display.length() && done < typedChars) {
            char ch = display.charAt(shown);
            if (!typinghog.core.Prompt.skipped(ch, item.punctuation())) done++;
            shown++;
        }
        return shown;
    }

    private void drawResult(HubScene hub, SceneCanvas c, long t) {
        DrillSession.Result r = session.result();
        long age = t - modeTick;
        int w = c.width();
        // The report card sits on the right, up to 200 wide; Tails reads it out on the left.
        int pw = Math.min(200, w - 120), px = w - pw - 6;
        hub.header(c, r.drill().label.toUpperCase(), "RESULTS");
        Hero.instructor(c, hub.art, hub.game, t, 30, 132, t - speechTick < 60);
        Hero.speech(c, hub.font, speech, 6, 30, Math.min(140, px - 12), 24, t - speechTick);
        Gfx.panel(c, px, 30, pw, 152);
        String[] labels = {"SPEED", "ACCURACY", "WORDS", "BEST CHAIN", "SCORE"};
        String[] values = {Math.round(r.wpm()) + " WPM", String.format(java.util.Locale.ROOT, "%.1f%%", r.accuracy() * 100),
                r.completed() + (r.total() > 0 ? " / " + r.total() : ""), Integer.toString(r.bestChain()),
                Long.toString(r.score())};
        if (r.drill() == Drill.REFLEX) {
            labels[0] = "REACTION";
            values[0] = Math.round(r.reactionMs()) + " MS";
            labels[2] = "LETTERS HIT";
        }
        if (r.drill() == Drill.BOSS_TRAINING) {
            labels[2] = "ROUNDS WON";
            values[2] = r.successes() + " / " + r.total();
        }
        int tx = px + 8;
        for (int i = 0; i < labels.length; i++) {
            if (age < 6 + i * 4L) break;
            hub.font.shadow(c, labels[i], tx, 40 + i * 14, Gfx.TEXT, 1);
            hub.font.right(c, values[i], w - 70, 40 + i * 14, Gfx.WHITE, 1);
        }
        if (r.failed()) hub.font.shadow(c, "ONE SLIP AND OUT!", tx, 112, Gfx.RED, 1);
        if (age >= 24) {
            long g = age - 24;
            int s = g < 8 ? (int) (5 + (8 - g)) : 5;
            String grade = r.grade();
            int gx = w - 40 - hub.font.width(grade, s) / 2, gy = 64 - (HubFont.H * s) / 3;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    if (dx != 0 || dy != 0) hub.font.draw(c, grade, gx + dx * 2, gy + dy * 2, 0xFF101028, s);
            hub.font.draw(c, grade, gx, gy, Grades.colour(grade), s);
            hub.font.centred(c, "GRADE", w - 38, 36, Gfx.DIM, 1);
            if (improved) hub.font.shadow(c, (t / 8) % 2 == 0 ? "NEW BEST GRADE!" : "", tx, 126, Gfx.GOLD, 1);
            if (r.drill() == Drill.FINAL_EXAM && Grades.atLeast(r.grade(), "C")) {
                hub.font.shadow(c, "GRADUATED! TYPING MASTER", tx, 140, 0xFF60FF70, 1);
            }
            if (School.examUnlocked(hub.profile) && r.drill() != Drill.FINAL_EXAM && improved) {
                hub.font.shadow(c, "THE FINAL EXAM IS OPEN!", tx, 140, 0xFF80D0FF, 1);
            }
        }
        // The keys this drill missed.
        hub.font.shadow(c, "MISSED: " + missed(session), tx, 166, Gfx.DIM, 1);
        if (age > 20 && (t / 20) % 3 != 0) hub.font.centred(c, "ENTER: BACK TO CLASS   AGAIN: TRY AGAIN", w / 2, 192,
                Gfx.WHITE, 1);
    }

    private static String missed(DrillSession s) {
        StringBuilder out = new StringBuilder();
        s.misses().asMap().entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(6)
                .forEach(e -> out.append(e.getKey() == ' ' ? "SPACE" : String.valueOf(Character.toUpperCase(e.getKey())))
                        .append(' '));
        return out.isEmpty() ? "NONE!" : out.toString().strip();
    }
}
