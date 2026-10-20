package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import sonicbros.sim.FighterDef;

/** Lifetime records: matches played and each fighter's picks, wins, KOs and falls. */
public final class RecordsScreen implements Screen {
    private final Game g;
    private long t;

    public RecordsScreen(Game g) {
        this.g = g;
    }

    @Override public void update() {
        t++;
        if (t > 10 && (g.controls.anyAccept() || g.controls.anyBack())) {
            g.sfx(Game.SFX_BACK);
            g.go(new MainMenu(g));
        }
    }

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        g.menuBackground(c, 0);
        g.bigCentered(c, "RECORDS", 3, w / 2, 6, 0xFFFFFFFF, 0xFF80B0FF);
        var r = g.records;
        g.font.draw(c, "MATCHES " + r.get("matches") + "   YOUR WINS " + r.get("human.wins") + "   BEST KOS " + r.get("best.kos")
                + "   BEST DAMAGE " + r.get("best.damage"), 14, 34, 0xFFFFE040);
        int x0 = 14;
        int y0 = 52;
        g.font.draw(c, "FIGHTER", x0 + 30, y0, 0xFFA0A0C0);
        g.font.draw(c, "PLAYED", x0 + 150, y0, 0xFFA0A0C0);
        g.font.draw(c, "WINS", x0 + 214, y0, 0xFFA0A0C0);
        g.font.draw(c, "KOS", x0 + 262, y0, 0xFFA0A0C0);
        g.font.draw(c, "FALLS", x0 + 310, y0, 0xFFA0A0C0);
        int i = 0;
        for (FighterDef d : g.roster.all()) {
            int y = y0 + 14 + i * 22;
            c.fill(x0, y - 2, w - 28, 20, i % 2 == 0 ? 0x60101830 : 0x40101830);
            c.draw(g.art.portrait(d.id, 0, 16), x0 + 6, y);
            g.font.draw(c, d.name, x0 + 30, y + 4, 0xFFFFFFFF);
            g.font.draw(c, Long.toString(r.get(d.id + ".plays")), x0 + 150, y + 4, 0xFFE0E0E0);
            g.font.draw(c, Long.toString(r.get(d.id + ".wins")), x0 + 214, y + 4, 0xFFA0FFA0);
            g.font.draw(c, Long.toString(r.get(d.id + ".kos")), x0 + 262, y + 4, 0xFFFFE080);
            g.font.draw(c, Long.toString(r.get(d.id + ".falls")), x0 + 310, y + 4, 0xFFFF9090);
            i++;
        }
    }

    @Override public String name() { return "records"; }
}
