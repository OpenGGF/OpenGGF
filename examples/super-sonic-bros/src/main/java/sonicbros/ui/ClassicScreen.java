package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneImage;
import java.util.ArrayList;
import java.util.List;
import sonicbros.sim.Fighter;
import sonicbros.sim.Match;
import sonicbros.sim.Pad;
import sonicbros.sim.Rng;
import sonicbros.sim.Rules;
import sonicbros.view.Costumes;
import sonicbros.view.MatchView;

/**
 * Classic: one player climbs a six-round ladder at a chosen difficulty. A rival, a badnik, a
 * tag-team round with a CPU partner, a two-on-one handicap, Mecha Sonic in the sky, and a
 * three-on-one finale on the Death Egg. Losing offers a continue (at a score penalty); clearing
 * records the difficulty beaten and the high score.
 */
public final class ClassicScreen implements Screen {
    private enum Phase { SETUP, INTRO, CONTINUE, CLEAR, GAME_OVER }

    private static final int ROUNDS = 6;
    private final Game g;
    private final String device;
    private final Slot[] saved = new Slot[4];
    private final String[] difficulties = {"EASY", "NORMAL", "HARD", "VERY HARD", "INTENSE"};
    private final int[] baseLevel = {2, 4, 6, 7, 9};
    private Phase phase = Phase.SETUP;
    private int fighter;
    private int difficulty = 1;
    private int row;
    private int round;
    private int continues;
    private long score;
    private int timer;
    private long t;
    private final List<Integer> opponents = new ArrayList<>();
    private int ally = -1;
    private Rng rng;
    private int continueCursor;

    public ClassicScreen(Game g, String device) {
        this.g = g;
        this.device = device == null ? (g.controls.devices().isEmpty() ? "LOG1" : g.controls.devices().get(0)) : device;
        for (int i = 0; i < 4; i++) {
            Slot s = new Slot();
            Slot o = g.slots[i];
            s.kind = o.kind;
            s.device = o.device;
            s.fighter = o.fighter;
            s.costume = o.costume;
            s.cpuLevel = o.cpuLevel;
            s.team = o.team;
            saved[i] = s;
        }
    }

    @Override public void enter() {
        timer = 0;
        if (phase == Phase.SETUP) { g.music(Game.MUS_SELECT); g.voice("ann-choose-fighter"); }
        if (phase == Phase.INTRO) {
            g.forceMusic(Game.MUS_SELECT);
            g.voice(round >= ROUNDS - 1 ? "ann-final-round" : "ann-round-" + (round + 1));
            if (roundTitle().startsWith("TAG")) g.voice("ann-tag-team");
            if (roundTitle().startsWith("HANDICAP")) g.voice("ann-handicap");
            g.voice("ann-vs");
        }
        if (phase == Phase.CONTINUE) { g.forceMusic(0x28); g.voice("ann-continue"); }   // mus_Continue
        if (phase == Phase.CLEAR) { g.forceMusic(0x32); g.voice("ann-congratulations"); }      // mus_Ending
        if (phase == Phase.GAME_OVER) { g.forceMusic(0x27); g.voice("ann-game-over"); }  // mus_GameOver
    }

    private void restoreSlots() {
        for (int i = 0; i < 4; i++) {
            Slot o = g.slots[i];
            Slot s = saved[i];
            o.kind = s.kind;
            o.device = s.device;
            o.fighter = s.fighter;
            o.costume = s.costume;
            o.cpuLevel = s.cpuLevel;
            o.team = s.team;
            o.stocks = 0;
        }
    }

    private void leave() {
        restoreSlots();
        g.go(new MainMenu(g));
    }

    @Override public void update() {
        t++;
        timer++;
        switch (phase) {
            case SETUP -> setup();
            case INTRO -> {
                if (timer > 150 || timer > 30 && g.controls.pressed(device, Pad.ATTACK | Pad.JUMP | Pad.START)) startRound();
            }
            case CONTINUE -> {
                if (g.controls.repeated(device, Pad.UP) || g.controls.repeated(device, Pad.DOWN)) { continueCursor ^= 1; g.sfx(Game.SFX_MOVE); }
                boolean decide = g.controls.pressed(device, Pad.ATTACK | Pad.JUMP | Pad.START);
                if (timer > 600 || decide && continueCursor == 1) { phase = Phase.GAME_OVER; enter(); }
                else if (decide) {
                    continues++;
                    score = Math.max(0, score - 2000);
                    g.sfx(Sfx_CONTINUE);
                    phase = Phase.INTRO;
                    enter();
                }
            }
            case CLEAR, GAME_OVER -> {
                if (timer > 90 && g.controls.pressed(device, Pad.ATTACK | Pad.JUMP | Pad.START | Pad.SPECIAL)) leave();
            }
        }
    }

    private static final int Sfx_CONTINUE = 0xAC; // sfx_Continue

    private void setup() {
        int n = g.roster.all().size();
        if (g.controls.repeated(device, Pad.UP) || g.controls.repeated(device, Pad.DOWN)) { row ^= 1; g.sfx(Game.SFX_MOVE); }
        int d = g.controls.repeated(device, Pad.LEFT) ? -1 : g.controls.repeated(device, Pad.RIGHT) ? 1 : 0;
        if (d != 0) {
            if (row == 0) fighter = Math.floorMod(fighter + d, n);
            else difficulty = Math.max(0, Math.min(difficulties.length - 1, difficulty + d));
            g.sfx(Game.SFX_MOVE);
        }
        if (g.controls.pressed(device, Pad.SPECIAL)) { g.sfx(Game.SFX_BACK); leave(); return; }
        if (g.controls.pressed(device, Pad.ATTACK | Pad.JUMP | Pad.START)) {
            g.sfx(Game.SFX_ACCEPT);
            g.voice("ann-name-" + g.roster.get(fighter).id);
            g.voice("voice-" + g.roster.get(fighter).id + "-select");
            rng = new Rng(g.seed ^ g.ticks);
            round = 0;
            score = 0;
            continues = 0;
            planRound();
            phase = Phase.INTRO;
            enter();
        }
    }

    private int stageFor(int r) {
        return switch (r) {
            case 0 -> 0;   // Angel Island
            case 1 -> 1;   // Hydrocity
            case 2 -> 2;   // Marble Garden
            case 3 -> 3;   // Carnival Night
            case 4 -> 8;   // Sky Sanctuary
            default -> 9;  // Death Egg
        };
    }

    private int pick(String... ids) {
        List<Integer> pool = new ArrayList<>();
        for (String id : ids) {
            int i = g.roster.indexOf(id);
            if (i >= 0 && i != fighter && !opponents.contains(i) && i != ally) pool.add(i);
        }
        if (pool.isEmpty()) return g.roster.indexOf("knuckles") == fighter ? g.roster.indexOf("sonic") : g.roster.indexOf("knuckles");
        return pool.get(rng.nextInt(pool.size()));
    }

    private void planRound() {
        opponents.clear();
        ally = -1;
        switch (round) {
            case 0 -> opponents.add(pick("sonic", "tails", "knuckles"));
            case 1 -> opponents.add(pick("rhinobot", "eggrobo"));
            case 2 -> {
                ally = pick("sonic", "tails", "knuckles");
                opponents.add(pick("rhinobot", "eggrobo", "mecha", "robotnik"));
                opponents.add(pick("sonic", "tails", "knuckles", "rhinobot"));
            }
            case 3 -> {
                opponents.add(pick("tails", "sonic", "knuckles", "rhinobot"));
                opponents.add(pick("eggrobo", "rhinobot", "tails"));
            }
            case 4 -> opponents.add(pick("mecha", "sonic"));
            default -> {
                opponents.add(pick("robotnik", "knuckles"));
                opponents.add(pick("mecha", "sonic"));
                opponents.add(pick("eggrobo", "tails"));
            }
        }
    }

    private String roundTitle() {
        return switch (round) {
            case 2 -> "TAG TEAM";
            case 3 -> "HANDICAP: TWO AT ONCE";
            case 4 -> "STEEL RIVAL";
            case 5 -> "FINAL ROUND: THE DEATH EGG";
            default -> "VERSUS";
        };
    }

    private void startRound() {
        int level = Math.min(9, baseLevel[difficulty] + (round >= 4 ? 1 : 0));
        Rules rules = new Rules();
        rules.mode = Rules.Mode.STOCK;
        rules.stocks = round == 5 ? 3 : 2;
        rules.itemFrequency = 1;
        rules.tapJump = g.rules.tapJump;
        rules.teams = true;
        for (Slot s : g.slots) { s.kind = Slot.Kind.OFF; s.stocks = 0; }
        Slot me = g.slots[0];
        me.kind = Slot.Kind.HUMAN;
        me.device = device;
        me.fighter = fighter;
        me.costume = 0;
        me.team = 0;
        int next = 1;
        if (ally >= 0) {
            Slot a = g.slots[next++];
            a.kind = Slot.Kind.CPU;
            a.fighter = ally;
            a.costume = 0;
            a.cpuLevel = level;
            a.team = 0;
        }
        for (int o : opponents) {
            Slot s = g.slots[next++];
            s.kind = Slot.Kind.CPU;
            s.fighter = o;
            s.costume = o == fighter ? 1 : 0;
            s.cpuLevel = (round == 3 || round == 5) ? Math.max(1, level - 2) : level;
            s.team = 1;
        }
        // Several opponents (handicap, finale) bring one stock each; the player and partner keep theirs.
        if (opponents.size() > 1 && ally < 0) for (int i = 1; i < next; i++) g.slots[i].stocks = 1;
        g.go(new BattleScreen(g, stageFor(round), rules, "ROUND " + (round + 1) + "  " + roundTitle(), this::finished));
        g.seed++;
    }

    private void finished(Match match) {
        if (match == null) { leave(); return; }
        Fighter me = match.fighters.get(0);
        boolean won = me.placement == 1;
        if (won) {
            int stocksLeft = 0;
            for (Fighter f : match.fighters) if (f.team == 0) stocksLeft += Math.max(0, f.stocks);
            long time = match.frame / 60;
            score += 1000L * (round + 1) + stocksLeft * 500L + Math.max(0, 3000 - time * 15) + (difficulty + 1) * 400L;
            round++;
            if (round >= ROUNDS) {
                phase = Phase.CLEAR;
                String id = g.roster.get(fighter).id;
                g.records.max("classic." + id + ".best", difficulty + 1);
                g.records.max("classic.hiscore", score);
                g.records.add("classic.clears", 1);
                g.records.save(g.ctx.storage());
            } else {
                planRound();
                phase = Phase.INTRO;
            }
        } else {
            phase = Phase.CONTINUE;
            continueCursor = 0;
        }
        g.go(this);
    }

    // ------------------------------------------------------------------ drawing

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        int h = g.height();
        switch (phase) {
            case SETUP -> drawSetup(c, w, h);
            case INTRO -> drawIntro(c, w, h);
            case CONTINUE -> drawContinue(c, w, h);
            case CLEAR -> drawClear(c, w, h);
            case GAME_OVER -> {
                c.clear(0);
                g.bigCentered(c, "GAME OVER", 5, w / 2, h / 2 - 30, 0xFFFF8080, 0xFFB02020);
                g.font.drawCentered(c, "SCORE " + score, w / 2, h / 2 + 20, 0xFFFFFFFF);
            }
        }
    }

    private void drawSetup(SceneCanvas c, int w, int h) {
        g.menuBackground(c, 0);
        g.bigCentered(c, "CLASSIC", 3, w / 2, 6, 0xFFFFFFFF, 0xFFFFC040);
        g.font.drawCentered(c, "SIX ROUNDS ACROSS THE ISLANDS. HOW FAR CAN YOU GO?", w / 2, 32, 0xFFC0D0FF);
        int n = g.roster.all().size();
        for (int i = 0; i < n; i++) {
            int x = w / 2 - n * 40 / 2 + i * 40;
            boolean sel = i == fighter;
            c.fill(x + 2, 46, 36, 36, sel ? 0xE0303A70 : 0xA0141C38);
            if (sel) { c.fill(x + 2, 46, 36, 1, 0xFFFFE040); c.fill(x + 2, 81, 36, 1, 0xFFFFE040); }
            c.draw(g.art.portrait(g.roster.get(i).id, 0, 32), x + 4, 48);
            long best = g.records.get("classic." + g.roster.get(i).id + ".best");
            if (best > 0) c.fill(x + 30, 48, 6, 6, 0xFFFFD020);
        }
        g.drawVictory(c, fighter, 0, w / 2.0, 150, 2.0, t);
        g.bigCentered(c, g.roster.get(fighter).name, 2, w / 2, 156, row == 0 ? 0xFFFFFFFF : 0xFFA0A8C0, row == 0 ? 0xFFFFE040 : 0xFF606880);
        String diff = "< " + difficulties[difficulty] + " >";
        g.panel(c, w / 2 - 80, 178, 160, 18, row == 1 ? 0xFFFFE040 : 0xFF405080);
        g.font.drawCentered(c, diff, w / 2, 183, row == 1 ? 0xFFFFE040 : 0xFFB0B8D0);
        long best = g.records.get("classic." + g.roster.get(fighter).id + ".best");
        String rec = best > 0 ? "CLEARED ON " + difficulties[(int) best - 1] : "NOT YET CLEARED";
        g.font.drawCentered(c, rec + "   HIGH SCORE " + g.records.get("classic.hiscore"), w / 2, h - 22, 0xA0FFFFFF);
        g.font.drawCentered(c, "LEFT/RIGHT CHOOSE   UP/DOWN FIGHTER/DIFFICULTY   ATTACK START", w / 2, h - 11, 0x90FFFFFF);
    }

    private void drawIntro(SceneCanvas c, int w, int h) {
        var stage = g.stage(stageFor(round));
        c.clear(0);
        if (stage != null) MatchView.preview(c, stage, 0, 0, w, h, t);
        c.fill(0, 0, w, h, 0x90000010);
        int slide = Math.max(0, 40 - timer) * 8;
        g.bigCentered(c, "ROUND " + (round + 1), 4, w / 2 - slide, 14, 0xFFFFFFFF, 0xFFFFC040);
        g.font.drawCentered(c, roundTitle(), w / 2 + slide, 48, 0xFFFFE040);
        // Your side on the left, theirs on the right.
        List<Integer> left = new ArrayList<>();
        left.add(fighter);
        if (ally >= 0) left.add(ally);
        drawSide(c, left, w / 4, 0xFFF03838);
        drawSide(c, opponents, w * 3 / 4, 0xFF3880F8);
        g.bigCentered(c, "VS", 4, w / 2, 100, 0xFFFFFFFF, 0xFFFF4040);
        g.font.drawCentered(c, g.stages.get(stageFor(round)).name() + " ZONE", w / 2, h - 30, 0xFFFFFFFF);
        g.font.drawCentered(c, "SCORE " + score + (continues > 0 ? "   CONTINUES " + continues : ""), w / 2, h - 16, 0xA0FFFFFF);
    }

    private void drawSide(SceneCanvas c, List<Integer> fighters, int cx, int color) {
        int n = fighters.size();
        for (int i = 0; i < n; i++) {
            double x = cx + (i - (n - 1) / 2.0) * 52;
            int f = fighters.get(i);
            SceneImage glow = g.art.image("soft32");
            c.draw(glow, (float) x - 30, 102, com.openggf.mods.scene.SceneDraw.plain().withScale(60f / glow.width())
                    .withTint((color & 0xFFFFFF) | 0x70000000));
            g.drawFighter(c, f, f == fighter && cx > g.width() / 2 ? 1 : 0, x, 160, 1.6, cx < g.width() / 2 ? 1 : -1, 0);
            g.font.drawCentered(c, g.roster.get(f).name, (int) x, 166, color);
        }
    }

    private void drawContinue(SceneCanvas c, int w, int h) {
        g.menuBackground(c, 1);
        int secs = Math.max(0, 10 - timer / 60);
        g.bigCentered(c, "CONTINUE?", 4, w / 2, 24, 0xFFFFFFFF, 0xFFFF8040);
        g.bigCentered(c, Integer.toString(secs), 6, w / 2, 64, 0xFFFFF080, 0xFFF08010);
        String[] items = {"YES", "NO"};
        for (int i = 0; i < 2; i++) {
            boolean sel = i == continueCursor;
            g.panel(c, w / 2 - 50, 130 + i * 24, 100, 20, sel ? 0xFFFFE040 : 0xFF405080);
            g.font.drawCentered(c, items[i], w / 2, 136 + i * 24, sel ? 0xFFFFE040 : 0xFFB0B8D0);
        }
        g.drawFighter(c, fighter, 0, w / 2.0 - 120, 190, 1.6, 1, (int) (t / 10 % 2 == 0 ? 0x40000000 : 0));
        g.font.drawCentered(c, "CONTINUING COSTS 2000 POINTS", w / 2, h - 16, 0xA0FFFFFF);
    }

    private void drawClear(SceneCanvas c, int w, int h) {
        var stage = g.stage(8);
        c.clear(0);
        if (stage != null) MatchView.preview(c, stage, 0, 0, w, h, t);
        c.fill(0, 0, w, h, 0x60000010);
        g.bigCentered(c, "CONGRATULATIONS!", 3, w / 2, 14, 0xFFFFFFFF, 0xFFFFC040);
        g.drawVictory(c, fighter, 0, w / 2.0, 150, 2.2, t);
        g.font.drawCentered(c, g.roster.get(fighter).name + " CLEARED CLASSIC ON " + difficulties[difficulty], w / 2, 160, 0xFFFFE040);
        g.font.drawCentered(c, "SCORE " + score + (continues > 0 ? "   (" + continues + " CONTINUES)" : "   NO CONTINUES!"), w / 2, 174, 0xFFFFFFFF);
        g.font.drawCentered(c, "THANKS FOR PLAYING", w / 2, h - 16, (t / 30) % 2 == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
    }

    @Override public String name() { return "classic-" + phase.name().toLowerCase(); }
}
