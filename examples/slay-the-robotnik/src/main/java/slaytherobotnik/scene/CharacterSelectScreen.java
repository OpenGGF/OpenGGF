package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.List;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CharacterDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.run.Run;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/** Choose Sonic, Tails or Knuckles, then embark. */
final class CharacterSelectScreen implements Screen {
    private final Hotspots spots = new Hotspots();
    private int selected;

    @Override
    public void enter(Shell shell) {
        shell.music(Sounds.MUSIC_CHARACTER);
        spots.focus("char0");
    }

    private void layout(Shell shell) {
        spots.clear();
        int pw = 118;
        int gap = 10;
        int total = 3 * pw + 2 * gap;
        int x = (shell.width() - total) / 2;
        for (int i = 0; i < 3; i++) {
            spots.add("char" + i, x + i * (pw + gap), 26, pw, 172);
        }
        spots.add("embark", shell.width() / 2 + 6, 203, 90, 16);
        spots.add("back", shell.width() / 2 - 96, 203, 90, 16);
    }

    @Override
    public void update(Shell shell) {
        if (embark >= 0) {
            playEmbark(shell);
            return;
        }
        layout(shell);
        String before = spots.focused();
        String picked = spots.update(shell.in);
        String focused = spots.focused();
        if (focused != null && focused.startsWith("char")) {
            selected = focused.charAt(4) - '0';
        }
        if (before != null && !before.equals(focused)) {
            shell.sfx(Sounds.SFX_CURSOR);
        }
        if (shell.in.back) {
            shell.sfx(Sounds.SFX_SWITCH);
            shell.go(new TitleScreen());
            return;
        }
        if (picked == null) {
            return;
        }
        if (picked.startsWith("char")) {
            spots.focus("embark");
            shell.sfx(Sounds.SFX_STARPOST);
        } else if (picked.equals("embark")) {
            // The hero revs a spin dash and blasts off before the run begins.
            embark = 0;
            shell.sfx(Sounds.SFX_SPINDASH);
        } else {
            shell.go(new TitleScreen());
        }
    }

    /** Frames into the embark: revs at 0, 12 and 24, the release at {@link #RELEASE}, the run at {@link #LEAVE}. */
    private int embark = -1;
    private static final int RELEASE = 34;
    private static final int LEAVE = 52;

    private void playEmbark(Shell shell) {
        embark++;
        if (embark == 12 || embark == 24) {
            shell.sfx(Sounds.SFX_SPINDASH);
        } else if (embark == RELEASE) {
            shell.sfx(Sounds.SFX_DASH);
        } else if (embark == LEAVE) {
            CharacterDef def = shell.catalog.characters().get(selected);
            long seed = System.nanoTime() ^ (shell.ticks * 0x9E3779B97F4A7C15L);
            shell.deleteRun();
            shell.attach(Run.start(shell.catalog, def.id(), seed));
            shell.profile.add("runs." + def.id(), 1);
            shell.profile.save(shell.ctx.storage());
            shell.go(new RunScreen());
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas c) {
        Backdrops.menu(c, shell.width(), shell.height(), shell.ticks);
        SmallFont f = shell.font;
        String title = "CHOOSE YOUR HERO";
        f.drawOutlined(c, title, (shell.width() - f.width(title) * 2) / 2, 7, Colors.GOLD, 2);
        layout(shell);
        List<CharacterDef> chars = shell.catalog.characters();
        for (int i = 0; i < 3 && i < chars.size(); i++) {
            Hotspots.Spot s = spots.spot("char" + i);
            drawPanel(shell, c, chars.get(i), s, i == selected);
        }
        if (embark >= RELEASE && selected < chars.size()) {
            Hotspots.Spot s = spots.spot("char" + selected);
            Poses.hero(shell, c, chars.get(selected).id(), Poses.ROLL, embark, s.x() + s.w() / 2f
                    + (embark - RELEASE) * 12f, s.y() + 67, SceneDraw.plain());
        }
        Hotspots.Spot go = spots.spot("embark");
        Gfx.button(c, f, "EMBARK!", go.x(), go.y(), go.w(), go.h(), spots.isFocused("embark"), true, shell.ticks);
        Hotspots.Spot back = spots.spot("back");
        Gfx.button(c, f, "BACK", back.x(), back.y(), back.w(), back.h(), spots.isFocused("back"), true, shell.ticks);
    }

    private void drawPanel(Shell shell, SceneCanvas c, CharacterDef def, Hotspots.Spot s, boolean active) {
        int color = Colors.opaque(CardColor.rgb(def.color()));
        int dark = Colors.mix(color, Colors.BLACK, 0.7f);
        Gfx.gradient(c, s.x() + 1, s.y() + 1, s.w() - 2, s.h() - 2, Colors.alpha(Colors.mix(color, Colors.BLACK, 0.4f),
                active ? 0.95f : 0.7f), Colors.alpha(dark, 0.95f));
        Gfx.outline(c, s.x(), s.y(), s.w(), s.h(), active ? Colors.FOCUS : Colors.mix(color, Colors.WHITE, 0.3f));
        if (active) {
            Gfx.focusFrame(c, s.x(), s.y(), s.w(), s.h(), shell.ticks);
        }
        SmallFont f = shell.font;
        String name = def.name().toUpperCase();
        f.drawOutlined(c, name, s.x() + (s.w() - f.width(name) * 2) / 2, s.y() + 6, Colors.WHITE, 2);
        int floor = s.y() + 66;
        c.fill(s.x() + 20, floor, s.w() - 40, 2, Colors.alpha(Colors.BLACK, 0.4f));
        if (!(active && embark >= RELEASE)) {
            // Once released, the rolling hero is drawn over every panel (see draw).
            int pose = !active ? Poses.WAIT : embark < 0 ? Poses.WALK : Poses.SPINDASH;
            Poses.hero(shell, c, def.id(), pose, active && embark >= 0 ? embark : shell.ticks, s.x() + s.w() / 2f,
                    floor + 1, SceneDraw.plain().withTint(active ? Colors.WHITE : 0xFFB0B0B0));
        }
        int y = floor + 6;
        f.drawShadowed(c, "HP " + def.maxHp(), s.x() + 8, y, Colors.TEXT);
        String stat = def.primaryStat().toUpperCase();
        f.drawShadowed(c, stat, s.x() + s.w() - 8 - f.width(stat), y, Colors.GOLD);
        y += 10;
        for (String line : f.wrap(def.blurb().toUpperCase(), s.w() - 14)) {
            f.drawShadowed(c, line, s.x() + 7, y, Colors.TEXT);
            y += SmallFont.LINE;
        }
        Relic relic = shell.catalog.newRelic(def.startingRelic());
        y = s.y() + s.h() - 34;
        c.fill(s.x() + 4, y - 3, s.w() - 8, 1, Colors.alpha(Colors.WHITE, 0.25f));
        f.drawShadowed(c, relic.name().toUpperCase(), s.x() + 7, y, Colors.GOLD);
        y += SmallFont.LINE;
        for (String line : f.wrap(relic.description().toUpperCase(), s.w() - 14)) {
            f.drawShadowed(c, line, s.x() + 7, y, Colors.TEXT_DIM);
            y += SmallFont.LINE;
        }
    }
}
