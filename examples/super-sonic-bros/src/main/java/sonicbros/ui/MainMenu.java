package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import sonicbros.sim.Pad;

/** VERSUS, RULES, RECORDS, HOW TO PLAY and EXIT. */
public final class MainMenu implements Screen {
    private final String[] items = {"VERSUS", "RULES", "RECORDS", "HOW TO PLAY", "EXIT"};
    private final Game g;
    private int cursor;
    private int t;

    public MainMenu(Game g) {
        this.g = g;
    }

    @Override public void enter() {
        g.music(Game.MUS_MENU);
        t = 0;
    }

    @Override public void update() {
        t++;
        if (t % 3 == 0) g.buildNextStage();
        if (g.controls.anyRepeated(Pad.UP)) { cursor = (cursor + items.length - 1) % items.length; g.sfx(Game.SFX_MOVE); }
        if (g.controls.anyRepeated(Pad.DOWN)) { cursor = (cursor + 1) % items.length; g.sfx(Game.SFX_MOVE); }
        if (g.controls.anyBack()) { g.sfx(Game.SFX_BACK); g.go(new TitleScreen(g)); return; }
        String who = g.controls.whoPressed(Pad.ATTACK | Pad.JUMP | Pad.START);
        if (who == null) return;
        g.sfx(Game.SFX_ACCEPT);
        switch (cursor) {
            case 0 -> g.go(new SelectScreen(g, who));
            case 1 -> g.go(new RulesScreen(g));
            case 2 -> g.go(new RecordsScreen(g));
            case 3 -> g.go(new HowToScreen(g));
            default -> g.ctx.exitToMasterTitle();
        }
    }

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        g.menuBackground(c, 0);
        g.bigCentered(c, "SUPER SONIC BROS.", 3, w / 2, 14, 0xFFFFF060, 0xFFF06010);
        for (int i = 0; i < items.length; i++) {
            boolean sel = i == cursor;
            int y = 66 + i * 26;
            int bw = 180;
            int x = w / 2 - bw / 2 + (sel ? (int) (Math.sin(t * 0.2) * 2) : 0);
            g.panel(c, x, y, bw, 22, sel ? 0xFFFFE040 : 0xFF405080);
            if (sel) c.fill(x + 1, y + 1, bw - 2, 20, 0x403060C0);
            g.bigCentered(c, items[i], 2, w / 2, y + 2, sel ? 0xFFFFFFFF : 0xFFA0A8C0, sel ? 0xFFFFE040 : 0xFF606880);
        }
        // A fighter cheering beside the menu.
        int fighter = (int) ((t / 180) % g.roster.all().size());
        g.drawVictory(c, fighter, 0, 54, 190, 1.4, t);
        g.textCentered(c, "UP/DOWN CHOOSE   Z/A CONFIRM   X/B BACK", w / 2, g.height() - 12, 0xA0FFFFFF);
    }

    @Override public String name() { return "menu"; }
}
