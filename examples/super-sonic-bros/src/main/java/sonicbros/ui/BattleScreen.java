package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import sonicbros.sim.Event;
import sonicbros.sim.Fighter;
import sonicbros.sim.FighterDef;
import sonicbros.sim.Match;
import sonicbros.sim.Pad;
import sonicbros.sim.Rng;
import sonicbros.sim.Rules;
import sonicbros.view.Costumes;
import sonicbros.view.MatchView;
import sonicbros.view.StageBuilder;

/**
 * A battle: builds the {@link Match} from the player slots, feeds it each person's controller,
 * plays its sounds and the zone's music (the invincibility theme while anyone is invincible or
 * Super), pauses on START and hands over to the results when the match is decided.
 */
public final class BattleScreen implements Screen {
    private final Game g;
    private final int stageIndex;
    private StageBuilder.Built built;
    private Match match;
    private MatchView view;
    private final List<Integer> slotOfPort = new ArrayList<>();
    private boolean paused;
    private String pausedBy;
    private int pauseCursor;
    private boolean finished;
    private int introHold;
    private int nextIntro;
    private long t;

    /** Called with the decided match instead of opening the results (Classic mode). */
    private final java.util.function.Consumer<Match> onEnd;
    private final sonicbros.sim.Rules rules;
    /** Shown over the HUD during the countdown (Classic's round name), or null. */
    private final String banner;

    public BattleScreen(Game g, int stageIndex) {
        this(g, stageIndex, null, null, null);
    }

    public BattleScreen(Game g, int stageIndex, sonicbros.sim.Rules rules, String banner,
            java.util.function.Consumer<Match> onEnd) {
        this.g = g;
        this.stageIndex = stageIndex;
        this.rules = rules;
        this.banner = banner;
        this.onEnd = onEnd;
    }

    public Match match() { return match; }

    @Override public void enter() {
        built = g.stage(stageIndex);
        Rng rng = new Rng(g.seed + g.ticks);
        List<Match.Entrant> entrants = new ArrayList<>();
        slotOfPort.clear();
        for (int i = 0; i < 4; i++) {
            Slot s = g.slots[i];
            if (!s.chosen()) continue;
            int f = s.fighter >= 0 ? s.fighter : rng.nextInt(g.roster.all().size());
            s.resolved = f;
            FighterDef def = g.roster.get(f);
            int costume = s.costume;
            if (s.fighter < 0) {
                for (Match.Entrant e : entrants) if (e.def() == def && e.costume() == costume) costume = (costume + 1) % Costumes.COUNT;
            }
            entrants.add(new Match.Entrant(def, costume, s.team, s.kind == Slot.Kind.CPU ? s.cpuLevel : 0));
            slotOfPort.add(i);
        }
        match = new Match(rules != null ? rules : g.rules, built.arena(), entrants, g.seed ^ g.ticks * 0x9E3779B97F4A7C15L);
        introHold = match.fighters.size() * 120;
        nextIntro = 0;
        for (int p = 0; p < slotOfPort.size(); p++) {
            int st = g.slots[slotOfPort.get(p)].stocks;
            if (st > 0 && match.rules.mode == sonicbros.sim.Rules.Mode.STOCK) match.fighters.get(p).stocks = st;
        }
        view = new MatchView(g.art, g.font, built, match);
        if (g.debugSuper) for (var f : match.fighters) f.superTimer = 60 * 60;
        String[] tags = new String[entrants.size()];
        int[] colors = new int[entrants.size()];
        for (int p = 0; p < entrants.size(); p++) {
            int slot = slotOfPort.get(p);
            boolean cpu = g.slots[slot].kind == Slot.Kind.CPU;
            tags[p] = cpu ? "CPU" : "P" + (slot + 1);
            colors[p] = Costumes.playerColor(slot, cpu);
        }
        view.setPlayers(tags, colors);
        g.forceMusic(built.stage().music());
        g.records.add("matches", 1);
    }

    @Override public void update() {
        t++;
        if (finished) return;
        Pad[] pads = new Pad[match.fighters.size()];
        for (int p = 0; p < pads.length; p++) {
            Slot s = g.slots[slotOfPort.get(p)];
            pads[p] = s.kind == Slot.Kind.HUMAN ? g.controls.pad(s.device) : Pad.none();
        }
        if (paused) {
            pauseMenu();
            view.update(List.of(), g.width(), g.height(), false);
            return;
        }
        if (match.countdown == 0 && !match.over) {
            for (int p = 0; p < pads.length; p++) {
                Slot s = g.slots[slotOfPort.get(p)];
                if (s.kind == Slot.Kind.HUMAN && pads[p].hit(Pad.START)) {
                    paused = true;
                    pausedBy = s.device;
                    pauseCursor = 0;
                    g.sfx(Game.SFX_ACCEPT);
                    return;
                }
            }
        }
        boolean wasOver = match.over;
        if (introHold > 0) {
            if (introHold % 120 == 0 && nextIntro < match.fighters.size()) {
                g.voice("voice-" + match.fighters.get(nextIntro++).def.id + "-intro");
            }
            introHold--;
            view.update(List.of(), g.width(), g.height(), false);
            return;
        }
        if (match.countdown == 160) g.voice("ann-three");
        else if (match.countdown == 110) g.voice("ann-two");
        else if (match.countdown == 60) g.voice("ann-one");
        else if (match.countdown == 10) g.voice("ann-go");
        boolean beforeSuddenDeath = match.suddenDeath;
        match.step(pads);
        if (!beforeSuddenDeath && match.suddenDeath) g.voice("ann-sudden-death");
        if (!wasOver && match.over) g.voice(match.rules.mode == Rules.Mode.TIME ? "ann-time" : "ann-game");
        playSounds(match.events);
        view.update(match.events, g.width(), g.height(), true);
        music();
        if (!wasOver && match.over) g.ctx.audio().fadeOutMusic();
        if (match.over && match.endTimer > 150) {
            finished = true;
            recordResults();
            if (onEnd != null) onEnd.accept(match);
            else g.go(new ResultsScreen(g, match, slotOfPort, built));
        }
    }

    private void pauseMenu() {
        if (g.controls.repeated(pausedBy, Pad.UP) || g.controls.repeated(pausedBy, Pad.DOWN)) {
            pauseCursor = 1 - pauseCursor;
            g.sfx(Game.SFX_MOVE);
        }
        if (g.controls.pressed(pausedBy, Pad.START) || g.controls.pressed(pausedBy, Pad.SPECIAL)) {
            paused = false;
            return;
        }
        if (g.controls.pressed(pausedBy, Pad.ATTACK | Pad.JUMP)) {
            if (pauseCursor == 0) { paused = false; return; }
            finished = true;
            g.sfx(Game.SFX_BACK);
            g.ctx.audio().fadeOutMusic();
            if (onEnd != null) onEnd.accept(null);
            else g.go(new SelectScreen(g, null));
        }
    }

    private void playSounds(List<Event> events) {
        Set<Integer> played = new HashSet<>();
        for (Event e : events) {
            if (e.type() == Event.SOUND && played.add(e.a())) g.sfx(e.a());
        }
    }

    private void music() {
        if (match.over) return;
        boolean power = false;
        for (Fighter f : match.fighters) if (f.alive() && (f.starTimer > 0 || f.superTimer > 0)) power = true;
        int want = match.suddenDeath ? Game.MUS_SUDDEN : power ? Game.MUS_INVINCIBLE : built.stage().music();
        g.music(want);
    }

    private void recordResults() {
        var r = g.records;
        for (Fighter f : match.fighters) {
            String id = f.def.id;
            r.add(id + ".plays", 1);
            if (f.placement == 1) r.add(id + ".wins", 1);
            r.add(id + ".kos", f.kos);
            r.add(id + ".falls", f.falls);
            r.add(id + ".damage", Math.round(f.damageDealt));
            r.max("best.kos", f.kos);
            r.max("best.damage", Math.round(f.damageDealt));
            boolean human = g.slots[slotOfPort.get(f.port)].kind == Slot.Kind.HUMAN;
            if (human && f.placement == 1) r.add("human.wins", 1);
        }
        r.save(g.ctx.storage());
    }

    @Override public void draw(SceneCanvas c) {
        view.draw(c, paused);
        if (banner != null && match.countdown > 0) {
            int w = g.width();
            c.fill(0, 16, w, 16, 0xA0000000);
            g.font.drawCentered(c, banner, w / 2, 20, 0xFFFFE040);
        }
        if (paused) {
            int w = g.width();
            int h = g.height();
            c.fill(0, 0, w, h, 0x80000000);
            g.bigCentered(c, "PAUSE", 4, w / 2, 50, 0xFFFFFFFF, 0xFF80B0FF);
            String[] items = {"CONTINUE", "QUIT MATCH"};
            for (int i = 0; i < 2; i++) {
                boolean sel = i == pauseCursor;
                g.panel(c, w / 2 - 70, 100 + i * 24, 140, 20, sel ? 0xFFFFE040 : 0xFF405080);
                g.textCentered(c, items[i], w / 2, 106 + i * 24, sel ? 0xFFFFE040 : 0xFFB0B8D0);
            }
            g.textCentered(c, "PAUSED BY " + sonicbros.input.Controls.deviceName(pausedBy), w / 2, 160, 0x90FFFFFF);
        }
    }

    @Override public String name() { return paused ? "battle-paused" : match != null && match.over ? "battle-over" : "battle"; }

    /** Seconds of match time so far, for tests. */
    public long frames() { return match.frame; }
}
