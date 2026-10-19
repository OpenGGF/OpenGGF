package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import sonicbros.sim.Fighter;
import sonicbros.sim.Match;
import sonicbros.sim.Pad;
import sonicbros.view.Costumes;
import sonicbros.view.MatchView;
import sonicbros.view.StageBuilder;

/** The winner's pose and everyone's placing, KOs, falls, self-destructs and damage dealt. */
public final class ResultsScreen implements Screen {
    private final Game g;
    private final Match match;
    private final List<Integer> slotOfPort;
    private final StageBuilder.Built stage;
    private long t;

    public ResultsScreen(Game g, Match match, List<Integer> slotOfPort, StageBuilder.Built stage) {
        this.g = g;
        this.match = match;
        this.slotOfPort = List.copyOf(slotOfPort);
        this.stage = stage;
    }

    @Override public void enter() {
        g.forceMusic(Game.MUS_RESULTS);
    }

    @Override public void update() {
        t++;
        if (t > 60 && (g.controls.anyAccept() || g.controls.anyBack())) {
            g.sfx(Game.SFX_ACCEPT);
            g.go(new SelectScreen(g, null));
        }
    }

    private String title() {
        List<Fighter> winners = match.winners();
        if (winners.isEmpty()) return "NO CONTEST";
        if (match.rules.teams) {
            int team = winners.get(0).team;
            return (team == 0 ? "RED" : team == 1 ? "BLUE" : "GREEN") + " TEAM WINS!";
        }
        if (winners.size() > 1) return "DRAW!";
        return winners.get(0).def.name + " WINS!";
    }

    private int color(Fighter f) {
        if (match.rules.teams) return Costumes.teamColor(f.team);
        int slot = slotOfPort.get(f.port);
        return Costumes.playerColor(slot, g.slots[slot].kind == Slot.Kind.CPU);
    }

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        int h = g.height();
        c.clear(0);
        MatchView.preview(c, stage, 0, 0, w, h, t);
        c.fill(0, 0, w, h, 0x90000010);
        List<Fighter> winners = match.winners();
        Fighter hero = winners.isEmpty() ? match.fighters.get(0) : winners.get(0);
        int heroColor = color(hero);
        // Spotlight and the winner's victory animation.
        SceneImage glow = g.art.image("soft32");
        c.draw(glow, 66 - 50, 120 - 50, SceneDraw.plain().withScale(100f / glow.width()).withTint((heroColor & 0xFFFFFF) | 0x90000000));
        int index = g.roster.indexOf(hero.def.id);
        g.drawVictory(c, index, hero.costume, 66, 168, 1.8, t);
        int slide = (int) Math.max(0, 60 - t * 3);
        g.bigCentered(c, title(), title().length() > 14 ? 2 : 3, w / 2 + 64 + slide, 10, 0xFFFFFFFF, (heroColor & 0xFFFFFF) | 0xFF000000);
        // The table.
        List<Fighter> order = new ArrayList<>(match.fighters);
        order.sort(Comparator.comparingInt(f -> f.placement));
        int x0 = 136;
        int y0 = 50;
        g.font.draw(c, "KO", x0 + 150, y0 - 12, 0xFFA0A0C0);
        g.font.draw(c, "FALL", x0 + 170, y0 - 12, 0xFFA0A0C0);
        g.font.draw(c, "SD", x0 + 204, y0 - 12, 0xFFA0A0C0);
        g.font.draw(c, "DMG", x0 + 226, y0 - 12, 0xFFA0A0C0);
        for (int i = 0; i < order.size(); i++) {
            Fighter f = order.get(i);
            if (t < 20 + i * 10L) continue;
            int y = y0 + i * 34;
            int col = color(f);
            c.fill(x0, y, 258, 30, 0xC0101828);
            c.fill(x0, y, 3, 30, col);
            String place = f.placement == 1 ? "1ST" : f.placement == 2 ? "2ND" : f.placement == 3 ? "3RD" : f.placement + "TH";
            SceneImage pl = g.font.text(place, 2, f.placement == 1 ? 0xFFFFF080 : 0xFFE0E0E0, f.placement == 1 ? 0xFFF09010 : 0xFF808090);
            c.draw(pl, x0 + 6, y + 7);
            c.draw(g.art.portrait(f.def.id, f.costume, 24), x0 + 48, y + 3);
            int slot = slotOfPort.get(f.port);
            String who = g.slots[slot].kind == Slot.Kind.CPU ? "CPU" : "P" + (slot + 1);
            g.font.draw(c, f.def.name, x0 + 76, y + 4, 0xFFFFFFFF);
            g.font.draw(c, who, x0 + 76, y + 16, col);
            g.font.draw(c, Integer.toString(f.kos), x0 + 152, y + 10, 0xFFA0FFA0);
            g.font.draw(c, Integer.toString(f.falls), x0 + 176, y + 10, 0xFFFF9090);
            g.font.draw(c, Integer.toString(f.selfDestructs), x0 + 204, y + 10, 0xFFFFC080);
            g.font.draw(c, Integer.toString((int) Math.round(f.damageDealt)), x0 + 226, y + 10, 0xFFFFFFFF);
        }
        if (t > 60 && (t / 30) % 2 == 0) g.textCentered(c, "PRESS ATTACK TO CONTINUE", w / 2, h - 13, 0xFFFFFFFF);
    }

    @Override public String name() { return "results"; }
}
