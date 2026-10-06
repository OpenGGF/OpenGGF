package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.run.Run;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Ease;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/**
 * The mod's title: a fly-through of the run's zones ({@link TitleShow}), the logo dropping in
 * and the main menu sliding in beneath it.
 */
final class TitleScreen implements Screen {
    /** Frames the logo takes to drop in, and between one menu item sliding in and the next. */
    private static final int LOGO_TICKS = 30;
    private static final int ITEM_STAGGER = 4;
    private static final int ITEM_TICKS = 16;
    /** The left column holding the logo and the menu; the chase plays across the rest. */
    private static final int COLUMN_X = 10;
    private static final int COLUMN_W = 196;
    private final Hotspots spots = new Hotspots();
    private final List<String[]> items = new ArrayList<>();
    private final TitleShow show = new TitleShow();
    private boolean hasSave;
    private long opened = -1;

    @Override
    public void enter(Shell shell) {
        shell.music(Sounds.MUSIC_TITLE);
        opened = shell.ticks;
        hasSave = shell.hasSavedRun();
        items.clear();
        if (hasSave) {
            items.add(new String[] {"continue", "CONTINUE RUN"});
        }
        items.add(new String[] {"new", "NEW RUN"});
        items.add(new String[] {"compendium", "COMPENDIUM"});
        items.add(new String[] {"stats", "RECORDS"});
        items.add(new String[] {"settings", "SETTINGS"});
        items.add(new String[] {"s3k", "PLAY SONIC 3 & KNUCKLES"});
        items.add(new String[] {"quit", "MASTER TITLE"});
    }

    private void layout(Shell shell) {
        spots.clear();
        int w = 150;
        int x = COLUMN_X + (COLUMN_W - w) / 2;
        int y = 82 - (items.size() - 6) * 7;
        for (String[] item : items) {
            spots.add(item[0], x, y, w, 13);
            y += 14;
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
            case "compendium" -> shell.go(new CompendiumScreen());
            case "stats" -> shell.go(new RecordsScreen());
            case "settings" -> shell.go(new SettingsScreen());
            case "s3k" -> shell.ctx.exitToGameTitle();
            default -> shell.ctx.exitToMasterTitle();
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas c) {
        int w = shell.width();
        int h = shell.height();
        long t = shell.ticks;
        long age = opened < 0 ? Long.MAX_VALUE / 2 : t - opened;
        show.draw(shell, c, age);
        // Shade the column so the menu reads over the level, then fade it out towards the chase
        // in 6-pixel bands that start where the solid shade ends.
        int fadeX = COLUMN_X + COLUMN_W - 40;
        c.fill(0, 0, fadeX, h, Colors.alpha(0xFF000818, 0.5f));
        for (int i = 1; i < 12; i++) {
            c.fill(fadeX + (i - 1) * 6, 0, 6, h, Colors.alpha(0xFF000818, 0.5f * (1f - i / 12f)));
        }
        // The logo drops in from above and settles with a small bounce.
        float drop = age >= LOGO_TICKS ? 1f : Ease.outBack(age / (float) LOGO_TICKS);
        drawLogo(shell, c, COLUMN_X + COLUMN_W / 2, Math.round(-50 + 60 * drop), 4, t);
        layout(shell);
        List<Hotspots.Spot> spotList = spots.spots();
        if (!spotList.isEmpty()) {
            Hotspots.Spot first = spotList.get(0);
            Hotspots.Spot last = spotList.get(spotList.size() - 1);
            float panel = Math.min(1f, Math.max(0f, (age - LOGO_TICKS / 2f) / ITEM_TICKS));
            c.fill(first.x() - 8, first.y() - 6, first.w() + 16, last.y() + last.h() - first.y() + 12,
                    Colors.alpha(0xFF000818, 0.45f * panel));
        }
        for (int i = 0; i < items.size(); i++) {
            Hotspots.Spot s = spotList.get(i);
            float in = Math.min(1f, Math.max(0f, (age - LOGO_TICKS / 2f - i * ITEM_STAGGER) / ITEM_TICKS));
            if (in <= 0f) {
                continue;
            }
            int slide = Math.round((1f - Ease.outCubic(in)) * -(s.x() + s.w()));
            Gfx.button(c, shell.font, items.get(i)[1], s.x() + slide, s.y(), s.w(), s.h(), spots.isFocused(s.id()),
                    true, t);
        }
        String foot = "FAN-MADE - USES YOUR SONIC 3 & KNUCKLES ROM - V0.1";
        shell.font.drawShadowed(c, foot, (w - shell.font.width(foot)) / 2, h - 8, Colors.TEXT_DIM);
    }

    /** The logo centred on {@code cx}: "SLAY THE" over a big two-tone "ROBOTNIK" at {@code scale}. */
    static void drawLogo(Shell shell, SceneCanvas c, int cx, int y, int scale, long t) {
        SmallFont f = shell.font;
        String top = "SLAY THE";
        int topScale = 2;
        f.drawOutlined(c, top, cx - f.width(top) * topScale / 2, y, Colors.WHITE, topScale);
        String big = "ROBOTNIK";
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
        c.clip(bx - 2, by + bob, bw + 4, scale * 5 / 2);
        f.draw(c, big, bx, by + bob, 0xFFFFDA24, scale);
        c.unclip();
        int shine = (int) ((t * 3) % (bw + 120)) - 60;
        c.clip(bx, by + bob, bw, scale * 5);
        c.fill(bx + shine, by + bob, 6, scale * 5, 0x60FFFFFF);
        c.unclip();
    }
}
