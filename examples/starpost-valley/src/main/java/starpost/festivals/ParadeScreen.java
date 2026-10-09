package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.ui.CompactFont;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.core.Item;
import starpost.people.Bodies;
import starpost.ui.Text;
import starpost.valley.ValleyView;

/**
 * The Sunflower Parade in the town: Green Hill's sunflowers bloom along the street (the ROM's big
 * flower, its petals palette-cycling in a wave), the valley marches through the plaza each with a
 * flower held high to Mushroom Hill's music, petals drifting, and then Dandel judges the best
 * flower: the neighbours' entries and the farmer's, scored one by one on his card.
 */
final class ParadeScreen extends FestivalScreen {
    private static final int MHZ1 = 0x0F;          // Sonic3kMusic MHZ1
    private static final int INTRO = 150;
    private static final float MARCH_SPEED = 1.3f;
    private static final int SPACING = 44;
    private static final int REVEAL = 55;

    private String host;
    private final List<String> marchers = new ArrayList<>();
    private float marchX;
    private int phase;
    private int phaseAt;
    private String entry;
    private boolean asked;
    private List<Parade.Entry> rivals = new ArrayList<>();
    private int farmerScore;
    private final List<float[]> petals = new ArrayList<>();

    ParadeScreen(FestivalSystem sys, Festival festival) {
        super(sys, festival);
    }

    @Override
    void begin() {
        int ax = sys.anchorX(festival);
        holdTown(ax - 40);
        play.valley().runner.facingLeft = false;
        offstage(true);
        host = Festivals.host(festival, shell.game);
        for (String id : crowd()) {
            if (!id.equals(host)) {
                marchers.add(id);
            }
        }
        shell.music.want("s3k", MHZ1);
    }

    private float camX() {
        return play.valley().cameraX();
    }

    @Override
    void step() {
        freezeInput();
        boolean skip = (shell.in.confirm || shell.in.act);
        play.update(shell);
        switch (phase) {
            case 0 -> {
                if (t == 1) {
                    caption(host, "THE SUNFLOWER PARADE! THE SUNFLOWERS ARE OUT! EVERYBODY, MARCH!",
                            "item:sunflower", "sun", "note", "arrow", "!");
                }
                if (t > INTRO) {
                    next(1);
                    marchX = camX() - 30;
                }
            }
            case 1 -> {
                marchX += MARCH_SPEED;
                if (t % 9 == 0) {
                    petals.add(new float[] {t * 37 % shell.width(), 20, (t % 5) - 2, t % 4});
                }
                float last = marchX - (marchers.size() - 1) * SPACING;
                if (last > camX() + shell.width() + 40 || t - phaseAt > 120 && skip) {
                    next(2);
                }
            }
            case 2 -> {
                if (!asked) {
                    asked = true;
                    chooseEntry();
                }
            }
            case 3 -> {
                int shown = (t - phaseAt) / REVEAL;
                if ((t - phaseAt) % REVEAL == 0 && shown <= rivals.size()) {
                    shell.sfx(shown == rivals.size() ? starpost.scene.Sfx.PERFECT : starpost.scene.Sfx.SWITCH);
                    if (shown > 0 && rivals.get(shown - 1).who().equals("robotnik")) {
                        caption(host, "ROBOTNIK'S... IT'S PLASTIC. IT SAYS ROBOMART ON THE POT. DISQUALIFIED!",
                                "who:robotnik", "item:sunflower", "no", "anger");
                    }
                }
                if (shown == rivals.size() + 1 && (t - phaseAt) % REVEAL == 0) {
                    announce();
                }
                if (shown > rivals.size() + 2) {
                    end();
                }
            }
            default -> {
            }
        }
        for (float[] p : petals) {
            p[1] += 0.8f;
            p[0] += (float) Math.sin((t + p[3] * 40) / 14.0) * 0.6f;
        }
        petals.removeIf(p -> p[1] > 230);
    }

    private void next(int p) {
        phase = p;
        phaseAt = t;
    }

    /** The farmer picks a flower to enter (or watches, without one). */
    private void chooseEntry() {
        Game game = shell.game;
        List<String> labels = new ArrayList<>();
        List<Item> icons = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < game.inventory.size(); i++) {
            String id = game.inventory.id(i);
            if (id != null && Parade.flower(id) && !ids.contains(id)) {
                ids.add(id);
                labels.add(game.item(id).name());
                icons.add(game.item(id));
            }
        }
        if (ids.isEmpty()) {
            caption(host, "NO FLOWER, {FARMER}? NEXT YEAR! SUNFLOWER SEEDS GROW IN SEVEN DAYS. HINT.");
            startJudging(null);
            return;
        }
        labels.add("JUST WATCH");
        icons.add(null);
        shell.push(new Choose("ENTER A FLOWER FOR JUDGING", labels, icons,
                i -> startJudging(i >= 0 && i < ids.size() ? ids.get(i) : null)));
    }

    private void startJudging(String chosen) {
        entry = chosen;
        rivals = Parade.rivals(shell.game);
        farmerScore = chosen == null ? 0 : Parade.score(chosen);
        next(3);
        if (chosen != null) {
            caption(host, "OOH. LET'S SEE EVERYONE'S FLOWERS.", "item:sunflower", "?", "sparkle");
        }
    }

    private void announce() {
        if (Parade.place(farmerScore, rivals) == 1) {
            caption(host, "THE BEST FLOWER IN THE VALLEY IS... {FARMER}'S! LOOK AT IT! IT'S SO YELLOW!",
                    "farmer", "item:sunflower", "heart", "!");
        } else {
            caption(host, "THE BEST FLOWER IS " + BoardScreen.name(best(), shell.game) + "'S! BEAUTIFULLY GROWN!");
        }
    }

    private String best() {
        Parade.Entry best = null;
        for (Parade.Entry e : rivals) {
            if (best == null || e.score() > best.score()) {
                best = e;
            }
        }
        return best == null ? host : best.who();
    }

    private void end() {
        Game game = shell.game;
        List<String> lines = new ArrayList<>();
        int place = Parade.place(farmerScore, rivals);
        lines.add(entry == null ? "YOU WATCHED THE PARADE" : game.item(entry).name() + ": " + farmerScore + " POINTS");
        if (entry != null) {
            game.inventory.remove(entry, 1);       // Dandel keeps the winners for the stall's window
        }
        lines.addAll(Parade.reward(game, festivals, entry));
        finish(place == 1 ? "BEST IN SHOW" : entry == null ? "WHAT A PARADE" : placeWord(place) + " PLACE", lines);
    }

    @Override
    void restore() {
        super.restore();
        offstage(false);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void paint(SceneCanvas canvas) {
        play.draw(shell, canvas);
        ValleyView view = play.valley();
        int cx = Math.round(view.cameraX()), cy = Math.round(view.cameraY());
        int ax = sys.anchorX(festival);
        // The host's judging stand by the plaza: a plank table, Dandel behind it.
        int standX = ax + 70;
        int floor = sys.floor(standX);
        canvas.fill(standX - 22 - cx, floor - 16 - cy, 44, 4, 0xFFB66D24);
        canvas.fill(standX - 20 - cx, floor - 12 - cy, 3, 12, 0xFF6D2400);
        canvas.fill(standX + 17 - cx, floor - 12 - cy, 3, 12, 0xFF6D2400);
        drawVillager(canvas, host, standX - cx, floor - cy, phase == 3 ? Bodies.HAPPY : Bodies.IDLE, true, 0,
                SceneDraw.plain());
        if (phase == 1) {
            for (int i = 0; i < marchers.size(); i++) {
                float x = marchX - i * SPACING;
                if (x < cx - 40 || x > cx + canvas.width() + 40) {
                    continue;
                }
                String id = marchers.get(i);
                int f = sys.floor(Math.round(x));
                boolean hopper = bodyOf(id).startsWith("animal:");
                float hop = hopper ? Math.abs((float) Math.sin((ticks + i * 9) * 0.18)) * 5 : 0;
                drawVillager(canvas, id, x - cx, f - cy, Bodies.WALK, false, hop, SceneDraw.plain());
                carried(canvas, id, x - cx, f - cy - hop);
            }
        }
        if (phase == 3) {
            // The entrants line up behind their flowers.
            int i = 0;
            for (Parade.Entry e : rivals) {
                float x = ax - 120 + i++ * 36;
                int f = sys.floor(Math.round(x));
                drawVillager(canvas, e.who(), x - cx, f - cy, Bodies.IDLE, false, 0, SceneDraw.plain());
                carried(canvas, e.who(), x - cx, f - cy);
            }
        }
        // Petals drifting down over the march (screen space): small yellow and orange flecks.
        for (float[] p : petals) {
            int px = Math.round(p[0]), py = Math.round(p[1]);
            int c = p[3] < 2 ? 0xFFFFDB00 : 0xFFFF9200;
            canvas.fill(px, py, 3, 2, c);
            canvas.fill(px + 1, py + 2, 1, 1, c);
        }
    }

    /** The flower someone carries high: Green Hill's sunflower (Robotnik's is plastic, and grey). */
    private void carried(SceneCanvas canvas, String id, float x, float feet) {
        SceneImage flower = sys.art.flower((int) (ticks / 16 % 2), (int) ((ticks / 8 + id.length()) % 4));
        if (flower == null) {
            return;
        }
        float top = feet - 44 - (bodyOf(id).startsWith("animal:") ? 0 : 14);
        canvas.fill(Math.round(x) + 6, Math.round(top) + 24, 2, Math.round(feet - top) - 34, 0xFF006D00);
        SceneDraw style = id.equals("robotnik") ? SceneDraw.plain().withFlash(0x80B6B6B6) : SceneDraw.plain();
        canvas.draw(flower, x - 9, top, style);
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        title(canvas);
        if (phase != 3 || showingResults()) {
            return;
        }
        // Dandel's judging card: each entrant, their flower and its score, revealed in turn.
        Game game = shell.game;
        int shown = (t - phaseAt) / REVEAL;
        int w = 340, h = 24 + (rivals.size() + 1) * 12, x = (canvas.width() - w) / 2, y = 34;
        solidPanel(canvas, x, y, w, h);
        Text.shadow(canvas, BoardScreen.name(host, game) + "'S CARD", x + 8, y + 6, Text.YELLOW);
        for (int i = 0; i <= rivals.size() && i < shown; i++) {
            String who, flower;
            int score;
            if (i < rivals.size()) {
                Parade.Entry e = rivals.get(i);
                who = BoardScreen.name(e.who(), game);
                flower = e.flower();
                score = e.score();
            } else {
                who = starpost.people.People.words("{FARMER}", game);
                flower = entry == null ? "NO ENTRY" : game.item(entry).name();
                score = farmerScore;
            }
            int ry = y + 22 + i * 12;
            boolean mine = i == rivals.size();
            int colour = mine ? 0xFFFFDB00 : 0xFFFFFFFF;
            CompactFont.shadowed(canvas, CompactFont.fit(who, 84, 1), x + 8, ry, 1, colour, 0xFF000000);
            CompactFont.shadowed(canvas, CompactFont.fit(flower, 170, 1), x + 96, ry, 1, mine ? colour : 0xFFB6B6B6,
                    0xFF000000);
            String value = score == 0 && !mine ? "DISQUALIFIED" : Integer.toString(score);
            CompactFont.shadowed(canvas, value, x + w - 8 - CompactFont.width(value, 1), ry, 1,
                    score == 0 ? 0xFFFF4949 : 0xFF92FF49, 0xFF000000);
        }
    }
}
