package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.run.Run;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/** The mod's title: logo, the three heroes, and the main menu. */
final class TitleScreen implements Screen {
    private final Hotspots spots = new Hotspots();
    private final List<String[]> items = new ArrayList<>();
    private boolean hasSave;

    @Override
    public void enter(Shell shell) {
        shell.music(Sounds.MUSIC_TITLE);
        hasSave = shell.hasSavedRun();
        items.clear();
        if (hasSave) {
            items.add(new String[] {"continue", "CONTINUE RUN"});
        }
        items.add(new String[] {"new", "NEW RUN"});
        items.add(new String[] {"stats", "RECORDS"});
        items.add(new String[] {"s3k", "PLAY SONIC 3 & KNUCKLES"});
        items.add(new String[] {"quit", "MASTER TITLE"});
    }

    private void layout(Shell shell) {
        spots.clear();
        int w = 150;
        int x = (shell.width() - w) / 2;
        int y = 128;
        for (String[] item : items) {
            spots.add(item[0], x, y, w, 13);
            y += 15;
        }
    }

    @Override
    public void update(Shell shell) {
        layout(shell);
        String before = spots.focused();
        String picked = spots.update(shell.in);
        if (before != null && !before.equals(spots.focused())) {
            shell.sfx(Sounds.SFX_CURSOR);
        }
        if (picked == null) {
            return;
        }
        shell.sfx(Sounds.SFX_STARPOST);
        switch (picked) {
            case "continue" -> {
                Run run = shell.loadRun();
                if (run != null) {
                    shell.attach(run);
                    shell.go(new RunScreen());
                }
            }
            case "new" -> shell.go(new CharacterSelectScreen());
            case "stats" -> shell.go(new RecordsScreen());
            case "s3k" -> shell.ctx.exitToGameTitle();
            default -> shell.ctx.exitToMasterTitle();
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas c) {
        int w = shell.width();
        int h = shell.height();
        long t = shell.ticks;
        Backdrops.menu(c, w, h, t);
        drawLogo(shell, c, w / 2, 18, t);
        drawHeroes(shell, c, w, h, t);
        layout(shell);
        for (int i = 0; i < items.size(); i++) {
            Hotspots.Spot s = spots.spots().get(i);
            Gfx.button(c, shell.font, items.get(i)[1], s.x(), s.y(), s.w(), s.h(), spots.isFocused(s.id()), true, t);
        }
        String foot = "FAN-MADE • USES YOUR SONIC 3 & KNUCKLES ROM • V0.1";
        shell.font.drawShadowed(c, foot.replace("•", "-"), (w - shell.font.width(foot.replace("•", "-"))) / 2,
                h - 8, Colors.TEXT_DIM);
    }

    static void drawLogo(Shell shell, SceneCanvas c, int cx, int y, long t) {
        SmallFont f = shell.font;
        String top = "SLAY THE";
        f.drawOutlined(c, top, cx - f.width(top) * 2 / 2, y, Colors.WHITE, 2);
        String big = "ROBOTNIK";
        int scale = 5;
        int bw = f.width(big) * scale;
        int bx = cx - bw / 2;
        int by = y + 16;
        int bob = (int) Math.round(Math.sin(t * 0.05) * 1.5);
        // Thick outline and a red-to-yellow body, like the Sonic 3 logo.
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                f.draw(c, big, bx + dx, by + dy + bob, 0xFF000024, scale);
            }
        }
        f.draw(c, big, bx, by + bob + 2, 0xFF900000, scale);
        f.draw(c, big, bx, by + bob, 0xFFDA2424, scale);
        c.clip(bx - 2, by + bob, bw + 4, 12);
        f.draw(c, big, bx, by + bob, 0xFFFFDA24, scale);
        c.unclip();
        int shine = (int) ((t * 3) % (bw + 120)) - 60;
        c.clip(bx, by + bob, bw, 25);
        c.fill(bx + shine, by + bob, 6, 25, 0x60FFFFFF);
        c.unclip();
    }

    private static void drawHeroes(Shell shell, SceneCanvas c, int w, int h, long t) {
        int floor = h - 48;
        String[] heroes = {"tails", "sonic", "knuckles"};
        int[] xs = {w / 2 - 150, w / 2 - 118, w / 2 + 130};
        for (int i = 0; i < heroes.length; i++) {
            Poses.hero(shell, c, heroes[i], Poses.WAIT, t + i * 37L, xs[i], floor + 1,
                    SceneDraw.plain().withFlipX(i == 2));
        }
    }
}
