package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import sonicbros.sim.Pad;
import sonicbros.view.StageBuilder;

/**
 * The title: the logo over Angel Island's sky, the whole roster lined up on a carved island,
 * and PRESS START. The menu theme starts here.
 */
public final class TitleScreen implements Screen {
    private final Game g;
    private int t;

    public TitleScreen(Game g) {
        this.g = g;
    }

    @Override public void enter() {
        t = 0;
        g.music(Game.MUS_TITLE);
        g.voice("ann-title");
    }

    @Override public void update() {
        t++;
        // Use idle title time to build the stages ahead of the stage select.
        if (t > 20 && t % 2 == 0) g.buildNextStage();
        if (t > 30 && (g.controls.anyPressed(Pad.START | Pad.ATTACK | Pad.JUMP))) {
            g.sfx(Game.SFX_ACCEPT);
            g.go(new MainMenu(g));
        }
    }

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        int h = g.height();
        StageBuilder.Built s = g.stage(0);
        if (s != null) {
            c.clear(0);
            c.drawBackdrop(s.backdrop(), 220, t * 0.6, t);
            float scale = 0.9f;
            float ix = w / 2f - s.island().width() * scale / 2;
            float iy = 150;
            c.draw(s.island(), ix, iy, SceneDraw.plain().withScale(scale));
            // The roster lined up on the island.
            int n = g.roster.all().size();
            for (int i = 0; i < n; i++) {
                double x = w / 2.0 + (i - (n - 1) / 2.0) * 34;
                double floorY = iy + (-s.islandY()) * scale;
                int bob = (int) (Math.sin((t + i * 13) * 0.07) * 1.5);
                g.drawFighter(c, i, 0, x, floorY + bob * 0, 0.9, x < w / 2.0 ? 1 : -1, 0);
            }
        } else {
            g.menuBackground(c, 0);
        }
        // Logo: drops in, then settles with a shine.
        int drop = Math.max(0, 40 - t);
        int y = 22 - drop * drop / 20;
        g.bigCentered(c, "SUPER SONIC", 4, w / 2, y, 0xFFFFF060, 0xFFF06010);
        g.bigCentered(c, "BROS.", 6, w / 2, y + 40, 0xFF80D0FF, 0xFF2050F0);
        if (t > 40 && (t / 30) % 2 == 0) {
            g.bigCentered(c, "PRESS START", 2, w / 2, h - 30, 0xFFFFFFFF, 0xFFA0C0FF);
        }
        g.textCentered(c, "A FAN-MADE FIGHTER  -  ALL ART AND MUSIC FROM YOUR SONIC 3 & KNUCKLES ROM", w / 2, h - 11, 0x80FFFFFF);
    }

    @Override public String name() { return "title"; }
}
