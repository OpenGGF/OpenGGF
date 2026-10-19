package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.List;
import sonicbros.sim.Arena;
import sonicbros.sim.FighterDef;
import sonicbros.sim.FighterDef.Slot;
import sonicbros.sim.Match;
import sonicbros.sim.Pad;
import sonicbros.sim.Rules;
import sonicbros.view.MatchView;
import sonicbros.view.StageBuilder;

/**
 * The move list: every fighter's moves with their inputs, each demonstrated live on Angel
 * Island against a training dummy. Left/right changes fighter, up/down the move.
 */
public final class MovesScreen implements Screen {
    /** One entry: a slot, how to input it, and the pad sequence that performs it facing right. */
    private record Entry(Slot slot, String input, int[] script) { }

    private final Game g;
    private final List<Entry> entries = new ArrayList<>();
    private int fighter;
    private int cursor;
    private Match demo;
    private MatchView view;
    private int frame;
    private long t;

    public MovesScreen(Game g, int fighter, int cursor) {
        this.g = g;
        this.fighter = fighter;
        this.cursor = cursor;
        int r = Pad.RIGHT, l = Pad.LEFT, u = Pad.UP, d = Pad.DOWN, a = Pad.ATTACK, b = Pad.SPECIAL, j = Pad.JUMP, s = Pad.SHIELD;
        entries.add(new Entry(Slot.JAB, "ATTACK", seq(a)));
        entries.add(new Entry(Slot.FTILT, "HOLD SIDE + ATTACK", seq(r, r, r, r, r, r, r | a)));
        entries.add(new Entry(Slot.UTILT, "HOLD UP + ATTACK", seq(u, u, u, u, u, u, u | a)));
        entries.add(new Entry(Slot.DTILT, "HOLD DOWN + ATTACK", seq(d, d, d, d, d, d, d | a)));
        entries.add(new Entry(Slot.DASH, "ATTACK WHILE RUNNING", seq(r, r, r, r, r, r, r, r, r, r, r, r, r | a)));
        entries.add(new Entry(Slot.FSMASH, "TAP SIDE + ATTACK", seq(0, r | a, a, a, a, a, a, a, a, a, a, a, a)));
        entries.add(new Entry(Slot.USMASH, "TAP UP + ATTACK", seq(0, u | a, a, a, a, a, a, a, a, a, a)));
        entries.add(new Entry(Slot.DSMASH, "TAP DOWN + ATTACK", seq(0, d | a, a, a, a, a, a, a, a, a)));
        entries.add(new Entry(Slot.NAIR, "AIR: ATTACK", seq(j, j, j, j, j, j, j, j, j, j, 0, 0, a)));
        entries.add(new Entry(Slot.FAIR, "AIR: FORWARD + ATTACK", seq(j, j, j, j, j, j, j, j, j, j, 0, 0, r | a)));
        entries.add(new Entry(Slot.BAIR, "AIR: BACK + ATTACK", seq(j, j, j, j, j, j, j, j, j, j, 0, 0, l | a)));
        entries.add(new Entry(Slot.UAIR, "AIR: UP + ATTACK", seq(j, j, j, j, j, j, j, j, j, j, 0, 0, u | a)));
        entries.add(new Entry(Slot.DAIR, "AIR: DOWN + ATTACK", seq(j, j, j, j, j, j, j, j, j, j, 0, 0, d | a)));
        entries.add(new Entry(Slot.NSPEC, "SPECIAL", hold(b, 40)));
        entries.add(new Entry(Slot.SSPEC, "SIDE + SPECIAL", prefixed(r | b, b, 40)));
        entries.add(new Entry(Slot.USPEC, "UP + SPECIAL", prefixed(u | b, u | j, 60)));
        entries.add(new Entry(Slot.DSPEC, "DOWN + SPECIAL", seq(d | b)));
        entries.add(new Entry(Slot.GRAB, "SHIELD + ATTACK, THEN A DIRECTION", seq(s | a, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, r)));
    }

    private static int[] seq(int... bits) { return bits; }

    private static int[] hold(int bits, int frames) {
        int[] out = new int[frames];
        java.util.Arrays.fill(out, bits);
        return out;
    }

    private static int[] prefixed(int first, int then, int frames) {
        int[] out = hold(then, frames);
        out[0] = first;
        // Flap or re-press now and then so flights and charges show off.
        for (int i = 12; i < frames; i += 12) out[i] = 0;
        return out;
    }

    @Override public void enter() {
        g.music(Game.MUS_MENU);
        restart();
    }

    private void restart() {
        StageBuilder.Built stage = g.stage(0);
        Rules rules = new Rules();
        rules.itemFrequency = 0;
        rules.tapJump = false;
        rules.stocks = 99;
        FighterDef me = g.roster.get(fighter);
        FighterDef dummy = g.roster.byId(me.id.equals("knuckles") ? "sonic" : "knuckles");
        demo = new Match(rules, stage == null ? Arena.flat(288, true) : stage.arena(),
                List.of(new Match.Entrant(me, 0, 0, 0), new Match.Entrant(dummy, 1, 1, 0)), 7);
        demo.countdown = 0;
        demo.fighters.get(0).x = 6;
        demo.fighters.get(1).x = 42;
        demo.fighters.get(1).facing = -1;
        if (stage != null) {
            view = new MatchView(g.art, g.font, stage, demo);
            view.setPlayers(new String[] {"P1", "DUMMY"}, new int[] {0xFFF03838, 0xFF9098A8});
            view.hud = false;
            view.lockCamera(0, -40, 1.5);
        }
        frame = 0;
    }

    @Override public void update() {
        t++;
        int n = entries.size();
        int before = cursor;
        int fBefore = fighter;
        if (g.controls.anyRepeated(Pad.UP)) cursor = (cursor + n - 1) % n;
        if (g.controls.anyRepeated(Pad.DOWN)) cursor = (cursor + 1) % n;
        if (g.controls.anyRepeated(Pad.LEFT)) fighter = (fighter + g.roster.all().size() - 1) % g.roster.all().size();
        if (g.controls.anyRepeated(Pad.RIGHT)) fighter = (fighter + 1) % g.roster.all().size();
        if (cursor != before || fighter != fBefore) { g.sfx(Game.SFX_MOVE); restart(); }
        if (g.controls.anyBack() || g.controls.anyPressed(Pad.START)) { g.sfx(Game.SFX_BACK); g.go(new MainMenu(g)); return; }
        int[] script = entries.get(cursor).script();
        Pad[] pads = new Pad[2];
        int bits = frame >= 20 && frame - 20 < script.length ? script[frame - 20] : 0;
        Pad prev = demo.fighters.get(0).pad;
        pads[0] = prev.then(bits);
        pads[1] = Pad.none();
        demo.step(pads);
        // Keep the dummy fresh so every demo hits a target at 0%.
        var dummy = demo.fighters.get(1);
        if (!dummy.alive() || dummy.state == sonicbros.sim.Fighter.State.RESPAWN) dummy.damage = 0;
        if (view != null) view.update(demo.events, g.width(), g.height(), true);
        for (var e : demo.events) if (e.type() == sonicbros.sim.Event.SOUND && t % 1 == 0) g.sfx(e.a());
        frame++;
        if (frame > 150 + script.length) restart();
    }

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        int h = g.height();
        if (view != null) view.draw(c, false); else g.menuBackground(c, 0);
        FighterDef def = g.roster.get(fighter);
        c.fill(0, 0, 150, h, 0xB0081028);
        g.bigCentered(c, "< " + def.name + " >", 1, 75, 4, 0xFFFFFFFF, 0xFFFFC040);
        int y = 18;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            boolean sel = i == cursor;
            if (sel) c.fill(2, y - 1, 146, 10, 0xC03060C0);
            String name = def.move(e.slot()).name.toUpperCase();
            g.font.draw(c, name, 6, y, sel ? 0xFFFFE040 : 0xFFD0D8E8);
            y += 11;
        }
        Entry sel = entries.get(cursor);
        var m = def.move(sel.slot());
        String info = sel.input();
        c.fill(150, h - 26, w - 150, 26, 0xB0081028);
        g.font.draw(c, info, 156, h - 22, 0xFFFFFFFF);
        double dmg = m.maxDamage();
        String stats = (dmg > 0 ? Math.round(dmg) + "%" : "-") + "   STARTUP " + Math.max(0, m.startup() + 1) + "F   LENGTH " + m.duration + "F";
        if (sel.slot() == Slot.GRAB) stats = "THROWS: " + Math.round(def.forwardThrow.damage()) + "% / " + Math.round(def.backThrow.damage())
                + "% / " + Math.round(def.upThrow.damage()) + "% / " + Math.round(def.downThrow.damage()) + "%";
        g.font.draw(c, stats, 156, h - 11, 0xFFA0C0FF);
        // The fighter's description, word-wrapped across the top.
        int ty = 4;
        StringBuilder line = new StringBuilder();
        for (String word : def.blurb.toUpperCase().split(" ")) {
            if (line.length() + word.length() + 1 > 38) {
                g.font.draw(c, line.toString(), 158, ty, 0xFFFFFFFF);
                ty += 10;
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) g.font.draw(c, line.toString(), 158, ty, 0xFFFFFFFF);
    }

    @Override public String name() { return "moves"; }
}
