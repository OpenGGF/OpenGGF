package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.List;
import slaytherobotnik.ui.Colors;

/**
 * The title screen's backdrop: a fly-through of the run's four zones, one shot each, built from
 * the ROM. Each shot is a strip of the act's real level (its foreground from
 * {@code SceneRomArt.levelForeground}, centred on a stretch of floor from {@code levelStages})
 * panning over the zone's own parallax background, and the heroes chase Robotnik's Egg Mobile
 * across it: Tails carrying Sonic as {@code Tails_Carry_Sonic} does (Sonic $1C below Tails on
 * {@code AniRaw_Tails_Carry}'s frames, Tails on his Carry pose with the Fly1 tail), Knuckles
 * gliding. Shots dip through black into the next. Without ROM level art it falls back to the
 * drawn menu backdrop.
 */
final class TitleShow {
    /** Frames a shot lasts, the pan across it, and the dip to black between shots. */
    private static final int SHOT_TICKS = 420;
    private static final int PAN = 700;
    private static final int DIP = 24;
    /** The strip's floor sits this far up from the bottom of the screen. */
    private static final int FLOOR_FROM_BOTTOM = 52;
    /** Tails_Carry_Sonic: Sonic hangs $1C below Tails, on AniRaw_Tails_Carry's frames, $B+1 frames each. */
    private static final int CARRY_DROP = 0x1C;
    private static final int CARRY_FRAME_TICKS = 12;
    /** Tails' Carry pose ($22 -> mapping frame $A2) and the tail's Fly1 script (AniTails_Tail0B: $27, $28, two frames each). */
    private static final int TAILS_CARRY_FRAME = 0xA2;
    /**
     * Knuckles' side-on glide facing right, the way the chase flies: Map_Knuckles frame $C0
     * (Knuckles_Set_Gliding_Animation). $C4 is the same glide facing left, which the ROM itself
     * never shows, drawing $C0 mirrored instead.
     */
    private static final int KNUCKLES_GLIDE_FRAME = 0xC0;

    /** Zone and act of each shot: Angel Island, Hydrocity, Launch Base, Sky Sanctuary. */
    private final int[][] shots = {{0, 0}, {1, 0}, {6, 0}, {10, 0}};
    private final String[] names = {"ANGEL ISLAND ZONE", "HYDROCITY ZONE", "LAUNCH BASE ZONE", "SKY SANCTUARY ZONE"};
    /** AniRaw_Tails_Carry: Sonic's hanging frames. */
    private final int[] carry = {0x91, 0x91, 0x90, 0x90, 0x90, 0x90, 0x90, 0x90, 0x92, 0x92, 0x92, 0x92, 0x92, 0x92,
            0x91, 0x91};
    /** The shot being shown, its strip and where the strip sits in the level. */
    private int shotIndex = -1;
    private SceneImage strip;
    private int stripLeft;
    private int stripTop;
    private boolean missing;
    private long shotStart;

    /** Draws the backdrop at time {@code t} (title ticks since the screen opened). */
    void draw(Shell shell, SceneCanvas c, long t) {
        int w = shell.width();
        int h = shell.height();
        int index = (int) ((t / SHOT_TICKS) % shots.length);
        if (index != shotIndex) {
            load(shell, index, w, h);
            shotStart = t - t % SHOT_TICKS;
        }
        if (missing) {
            Backdrops.menu(c, w, h, t);
            return;
        }
        long inShot = t - shotStart;
        float pan = PAN * Math.min(1f, inShot / (float) SHOT_TICKS);
        int zone = shots[index][0];
        int act = shots[index][1];
        Backdrops.zone(shell, c, zone, act, Math.round(stripLeft + pan));
        c.draw(strip, -pan, 0);
        drawChase(shell, c, w, h, t);
        // Keep the menu readable over a busy level: a soft vertical shade.
        c.fill(0, 0, w, h, 0x38000820);
        // The zone's name, bottom right, while the shot is in full view.
        float caption = Math.min(1f, Math.min((inShot - DIP) / 30f, (SHOT_TICKS - DIP - inShot) / 30f));
        if (caption > 0f) {
            String name = names[index];
            int nx = w - 10 - shell.font.width(name) * 2 + Math.round((1f - caption) * 24);
            shell.font.drawOutlined(c, name, nx, h - 30, Colors.alpha(Colors.GOLD, caption), 2);
        }
        // Dip through black between shots.
        long toEnd = SHOT_TICKS - inShot;
        float dark = inShot < DIP ? 1f - inShot / (float) DIP : toEnd < DIP ? 1f - toEnd / (float) DIP : 0f;
        if (dark > 0f) {
            c.fill(0, 0, w, h, Colors.alpha(Colors.BLACK, dark));
        }
    }

    /** Fetches shot {@code index}'s strip: the act's middle stretch of floor, the level around it. */
    private void load(Shell shell, int index, int w, int h) {
        shotIndex = index;
        int zone = shots[index][0];
        int act = shots[index][1];
        List<SceneLevelStage> stages = shell.art.levelStages(zone, act, w, 96, 32);
        missing = stages.isEmpty();
        if (missing) {
            return;
        }
        SceneLevelStage stage = stages.get(stages.size() / 2);
        stripLeft = stage.x() + stage.width() / 2 - (w + PAN) / 2;
        stripTop = stage.floorY() - (h - FLOOR_FROM_BOTTOM);
        strip = shell.art.levelForeground(zone, act, stripLeft, stripTop, w + PAN, h);
        missing = strip == null;
    }

    /** Robotnik flees right in the Egg Mobile; Tails, carrying Sonic, and Knuckles give chase. */
    private void drawChase(Shell shell, SceneCanvas c, int w, int h, long t) {
        float bob = (float) Math.sin(t * 0.07) * 3;
        // The Egg Mobile with Robotnik at its controls, looking back now and then.
        SceneSprite mobile = shell.art.romFrame("robotnik_ship", 5);
        SceneSprite head = shell.art.romFrame("robotnik_ship", (t / 40) % 4 == 0 ? 1 : 0);
        float rx = w * 0.88f + (float) Math.sin(t * 0.013) * 10;
        float ry = 58 + (float) Math.sin(t * 0.05 + 1) * 5;
        if (mobile != null) {
            Poses.centre(c, mobile, rx, ry + 10, SceneDraw.plain());
        }
        if (head != null) {
            Poses.centre(c, head, rx, ry - 6, SceneDraw.plain());
        }
        // Tails carrying Sonic.
        float tx = w * 0.68f + (float) Math.sin(t * 0.011) * 14;
        float ty = 74 + bob;
        SceneSpriteSet tails = shell.art.character("tails");
        SceneSpriteSet sonic = shell.art.character("sonic");
        if (tails != null && sonic != null) {
            SceneSprite hang = sonic.frame(carry[(int) ((t / CARRY_FRAME_TICKS) % carry.length)]);
            c.draw(hang, tx, ty + CARRY_DROP, SceneDraw.plain());
            SceneSpriteSet tail = shell.art.hasRom() ? shell.art.rom().characterAccessory("tails") : null;
            if (tail != null) {
                c.draw(tail.frame(0x27 + (int) ((t / 2) % 2)), tx, ty, SceneDraw.plain());
            }
            c.draw(tails.frame(TAILS_CARRY_FRAME), tx, ty, SceneDraw.plain());
        }
        // Knuckles gliding just behind and below.
        SceneSpriteSet knuckles = shell.art.character("knuckles");
        if (knuckles != null) {
            float kx = w * 0.58f + (float) Math.sin(t * 0.017 + 2) * 12;
            float ky = 124 + (float) Math.sin(t * 0.06 + 2) * 4;
            c.draw(knuckles.frame(KNUCKLES_GLIDE_FRAME), kx, ky, SceneDraw.plain());
        }
    }
}
