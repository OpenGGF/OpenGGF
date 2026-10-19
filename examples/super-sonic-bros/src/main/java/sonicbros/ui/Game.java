package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.HashMap;
import java.util.Map;
import sonicbros.content.Roster;
import sonicbros.content.Stages;
import sonicbros.input.Controls;
import sonicbros.sim.Rules;
import sonicbros.view.Art;
import sonicbros.view.BigFont;
import sonicbros.view.FighterArt;
import sonicbros.view.StageBuilder;

/**
 * Everything the screens share for one visit to the scene: input, art, rules, the four player
 * slots, records, built stages, music and the fade between screens.
 */
public final class Game {
    public static final int MUS_TITLE = 0x25;        // mus_TitleScreen
    public static final int MUS_MENU = 0x2D;         // mus_CompetitionMenu
    public static final int MUS_SELECT = 0x2F;       // mus_DataSelect
    public static final int MUS_RESULTS = 0x29;      // mus_GotThroughAct
    public static final int MUS_INVINCIBLE = 0x2C;   // mus_Invincibility
    public static final int MUS_SUDDEN = 0x30;       // mus_FinalBoss
    public static final int SFX_MOVE = 0x5B;         // sfx_Switch
    public static final int SFX_ACCEPT = 0x63;       // sfx_Starpost
    public static final int SFX_BACK = 0xB2;         // sfx_Error
    public static final int SFX_JOIN = 0x33;         // sfx_RingRight

    public final SceneContext ctx;
    public final Art art;
    public final FighterArt fighterArt;
    public final BigFont font = new BigFont();
    public final Controls controls = new Controls();
    public final Rules rules = new Rules();
    public final Roster roster = new Roster();
    public final Stages stages = new Stages();
    public final Records records = new Records();
    public final Slot[] slots = new Slot[4];
    private final Map<Integer, StageBuilder.Built> built = new HashMap<>();
    public Screen screen;
    private Screen next;
    private int fade;
    private int fadeDir;
    public long ticks;
    private int music = -1;
    public int lastStage = -1;
    public long seed;
    /** Debug: fighters start in Super form (captures of the Super sprites). */
    public boolean debugSuper;

    public Game(SceneContext ctx) {
        this.ctx = ctx;
        this.art = new Art(ctx.art().rom());
        this.fighterArt = new FighterArt(art);
        for (int i = 0; i < 4; i++) slots[i] = new Slot();
        rules.load(ctx.storage().read("rules.txt").orElse(null));
        records.load(ctx.storage());
        seed = System.nanoTime();
    }

    public int width() { return ctx.width(); }

    public int height() { return ctx.height(); }

    public void saveRules() { ctx.storage().write("rules.txt", rules.save()); }

    // ------------------------------------------------------------------ screens

    /** Fades to {@code s}. */
    public void go(Screen s) {
        if (next != null) return;
        next = s;
        fadeDir = 1;
    }

    /** Switches immediately (debug jumps). */
    public void jump(Screen s) {
        next = null;
        fade = 0;
        fadeDir = 0;
        screen = s;
        s.enter();
    }

    public boolean fading() { return next != null; }

    public void update() {
        ticks++;
        controls.update(ctx);
        if (fadeDir > 0) {
            fade += 2;
            if (fade >= 12) {
                screen = next;
                next = null;
                fadeDir = -1;
                screen.enter();
            }
            return;
        }
        if (fadeDir < 0) {
            fade -= 2;
            if (fade <= 0) { fade = 0; fadeDir = 0; }
        }
        if (screen != null) screen.update();
    }

    public void draw(SceneCanvas c) {
        if (screen != null) screen.draw(c);
        if (fade > 0) c.fill(0, 0, width(), height(), Math.min(255, fade * 22) << 24);
    }

    // ------------------------------------------------------------------ audio

    public void music(int id) {
        if (id == music) return;
        music = id;
        ctx.audio().playMusic(id);
    }

    public void forceMusic(int id) {
        music = id;
        ctx.audio().playMusic(id);
    }

    public void sfx(int id) { ctx.audio().playSfx(id); }

    // ------------------------------------------------------------------ stages

    /** The built stage, building it now if needed (may take a few hundred milliseconds). */
    public StageBuilder.Built stage(int index) {
        int i = Math.floorMod(index, stages.size());
        StageBuilder.Built b = built.get(i);
        if (b == null) {
            b = StageBuilder.build(art.rom, stages.get(i));
            if (b != null) built.put(i, b);
        }
        return b;
    }

    public boolean stageReady(int index) { return built.containsKey(Math.floorMod(index, stages.size())); }

    /** Builds one more stage in the background of a menu; returns false when all are built. */
    public boolean buildNextStage() {
        for (int i = 0; i < stages.size(); i++) {
            if (!built.containsKey(i)) { stage(i); return true; }
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing helpers

    private final SceneImage[] menuTiles = new SceneImage[2];

    /** One 64-pixel-wide repeat of the menu backdrop: a vertical gradient with a diagonal band. */
    private SceneImage menuTile(int hue) {
        if (menuTiles[hue] != null) return menuTiles[hue];
        int w = 64;
        int h = height();
        int[] px = new int[w * h];
        int top = hue == 1 ? 0x401018 : 0x0A1850;
        int bottom = hue == 1 ? 0x100408 : 0x020414;
        int band = hue == 1 ? 0xFF4040 : 0x4080FF;
        for (int y = 0; y < h; y++) {
            int base = BigFont_lerp(top, bottom, y / (double) h);
            for (int x = 0; x < w; x++) {
                int col = base;
                int d = Math.floorMod(x - y / 2, 64);
                if (d < 24) col = BigFont_lerp(base, band, 0.0625);
                px[y * w + x] = 0xFF000000 | col;
            }
        }
        menuTiles[hue] = new SceneImage(w, h, px);
        return menuTiles[hue];
    }

    /** The animated menu backdrop: a deep blue (or red) gradient with drifting diagonal bands. */
    public void menuBackground(SceneCanvas c, int hue) {
        int w = width();
        int h = height();
        SceneImage tile = menuTile(hue == 1 ? 1 : 0);
        int off = (int) (ticks % 64);
        for (int x = -off; x < w; x += 64) c.draw(tile, x, 0);
        // A row of rings drifting along the bottom edge, for a little Sonic flavour.
        for (int i = 0; i < w / 40 + 2; i++) {
            var ring = art.frame("ring", (int) ((ticks / 6 + i) % 4));
            c.draw(ring, (float) (i * 40 - (ticks % 40)), h - 8f, SceneDraw.plain().withAlpha(0.25f));
        }
    }

    private static int BigFont_lerp(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return (r << 16) | (g << 8) | bl;
    }

    /** A framed panel. */
    public void panel(SceneCanvas c, int x, int y, int w, int h, int border) {
        c.fill(x, y, w, h, 0xC0080C20);
        c.fill(x, y, w, 1, border);
        c.fill(x, y + h - 1, w, 1, border);
        c.fill(x, y, 1, h, border);
        c.fill(x + w - 1, y, 1, h, border);
    }

    public void textCentered(SceneCanvas c, String t, int cx, int y, int color) {
        font.drawCentered(c, t, cx, y, color);
    }

    /** Big gradient lettering centred at {@code cx}. */
    public void bigCentered(SceneCanvas c, String t, int scale, int cx, int y, int top, int bottom) {
        SceneImage img = font.text(t, scale, top, bottom);
        c.draw(img, cx - img.width() / 2f, y);
    }

    /** The fighter's name for menus, accounting for the random slot. */
    public String fighterName(int index) {
        if (index == Slot.RANDOM) return "RANDOM";
        if (index < 0) return "";
        return roster.get(index).name;
    }

    public String fighterId(int index) { return index < 0 ? null : roster.get(index).id; }

    /** Draws a fighter standing, feet at (x, y), for menus. */
    public void drawFighter(SceneCanvas c, int index, int costume, double x, double y, double scale, int facing, int flash) {
        if (index < 0) return;
        var def = roster.get(index);
        int pose = def.poses.idle()[0];
        if (def.id.equals("eggrobo")) pose = Roster.ROBO_IDLE;
        fighterArt.draw(c, def.id, pose, x, y, y - def.stats.height() / 2 * scale, true, facing, scale, costume, flash, 1f, ticks);
    }

    /** Draws a fighter's victory animation. */
    public void drawVictory(SceneCanvas c, int index, int costume, double x, double y, double scale, long t) {
        var def = roster.get(index);
        int[] v = def.poses.victory();
        int pose = v[(int) ((t / Math.max(6, def.poses.period() * 3)) % v.length)];
        fighterArt.draw(c, def.id, pose, x, y, y - def.stats.height() / 2 * scale, true, 1, scale, costume, 0, 1f, t);
    }
}
