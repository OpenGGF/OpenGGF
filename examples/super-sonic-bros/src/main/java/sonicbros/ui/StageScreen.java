package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import sonicbros.sim.Pad;
import sonicbros.view.MatchView;
import sonicbros.view.StageBuilder;

/** The ten stages as live previews (and Random); confirm to fight. */
public final class StageScreen implements Screen {
    private static final int COLS = 4;
    private static final int CW = 88;
    private static final int CH = 44;
    private final Game g;
    private int cursor;
    private long t;

    public StageScreen(Game g) {
        this.g = g;
    }

    @Override public void enter() {
        cursor = g.lastStage >= 0 ? g.lastStage : 0;
    }

    private int count() { return g.stages.size() + 1; }

    @Override public void update() {
        t++;
        g.buildNextStage();
        int n = count();
        int before = cursor;
        if (g.controls.anyRepeated(Pad.LEFT)) cursor = (cursor + n - 1) % n;
        if (g.controls.anyRepeated(Pad.RIGHT)) cursor = (cursor + 1) % n;
        if (g.controls.anyRepeated(Pad.UP)) cursor = cursor - COLS >= 0 ? cursor - COLS : cursor;
        if (g.controls.anyRepeated(Pad.DOWN)) cursor = Math.min(n - 1, cursor + COLS);
        if (cursor != before) g.sfx(Game.SFX_MOVE);
        if (g.controls.anyBack()) { g.sfx(Game.SFX_BACK); g.go(new SelectScreen(g, null)); return; }
        if (g.controls.anyAccept()) {
            int stage = cursor == n - 1 ? (int) ((System.nanoTime() / 1000) % g.stages.size()) : cursor;
            if (cursor != n - 1) g.lastStage = cursor;
            g.sfx(Game.SFX_ACCEPT);
            g.go(new BattleScreen(g, stage));
        }
    }

    private int cellX(int i) { return g.width() / 2 - COLS * (CW + 4) / 2 + (i % COLS) * (CW + 4) + 2; }

    private int cellY(int i) { return 29 + (i / COLS) * (CH + 13); }

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        g.menuBackground(c, 1);
        g.bigCentered(c, "CHOOSE A STAGE", 2, w / 2, 6, 0xFFFFFFFF, 0xFFFF9040);
        int n = count();
        for (int i = 0; i < n; i++) {
            int x = cellX(i);
            int y = cellY(i);
            boolean sel = i == cursor;
            int border = sel ? ((t / 6) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFE040) : 0xFF504060;
            c.fill(x - 2, y - 2, CW + 4, CH + 4, border);
            if (i == n - 1) {
                c.fill(x, y, CW, CH, 0xFF202040);
                g.bigCentered(c, "?", 4, x + CW / 2, y + 10, 0xFFFFFFFF, 0xFFA0A0FF);
            } else if (g.stageReady(i)) {
                StageBuilder.Built b = g.stage(i);
                c.clip(x, y, CW, CH);
                MatchView.preview(c, b, x, y, CW, CH, sel ? t : 0);
                c.unclip();
            } else {
                c.fill(x, y, CW, CH, 0xFF101020);
                g.textCentered(c, "...", x + CW / 2, y + CH / 2 - 5, 0x80FFFFFF);
            }
            String name = i == n - 1 ? "RANDOM" : g.stages.get(i).name();
            int tw = g.font.width(name);
            if (tw > CW) name = name.split(" ")[0];
            g.textCentered(c, name, x + CW / 2, y + CH + 1, sel ? 0xFFFFE040 : 0xFFC0C0D0);
        }
        String sub = cursor == n - 1 ? "LET FATE DECIDE" : g.stages.get(cursor).name() + " ZONE - " + g.stages.get(cursor).subtitle().toUpperCase();
        g.textCentered(c, sub, w / 2, g.height() - 12, 0xFFFFFFFF);
    }

    @Override public String name() { return "stage"; }
}
