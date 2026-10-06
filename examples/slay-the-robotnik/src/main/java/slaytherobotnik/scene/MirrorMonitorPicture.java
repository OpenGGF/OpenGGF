package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.core.Card;
import slaytherobotnik.ui.Colors;

/**
 * The Mirror Monitor, standing on the act's level: a 1-Up monitor whose face is the hero's own
 * life icon (Map_Monitor frame 2 shows ArtTile_PlayerLifeIcon, which the hero's PLC fills),
 * flickering through static as Ani_Monitor_OneUp does, with a pale mirror image of the hero
 * beside it. [Copy] (after the card is picked): the card rises over the hero, the screen goes
 * to static, the reflection brightens as a Hyudoro fades in (sfx_GhostAppear) and a copy of the
 * card slides out of the screen to it. The screen stays fizzled, both cards held up.
 */
final class MirrorMonitorPicture extends TimedPicture {
    /** Mod timing for the copy. */
    private static final int RAISE = 16;
    private static final int STEP_OUT = 40;
    private static final int ARRIVE = 64;
    private static final int LENGTH = 96;
    /** Ani_Monitor_OneUp: frames 0, 2, 2, 1, 2, 2 at delay 1 (two frames each). */
    private static final String ONE_UP = "022122";
    private static final float SCALE = 2f;

    private Card card;

    MirrorMonitorPicture(Shell shell) {
    }

    @Override
    void begin(Shell shell, String verb, String argument) {
        card = card(shell, argument);
        shell.sfx(Sounds.SFX_SWITCH);
    }

    @Override
    boolean playing() {
        return clock < LENGTH;
    }

    @Override
    void step(Shell shell) {
        if (detail != null && clock == RAISE) {
            shell.sfx(Sounds.SFX_GHOST_APPEAR);
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        String hero = hero(shell);
        int cx = x + w / 2;
        int heroX = x + 22;
        int mirrorX = x + w - 22;
        Poses.hero(shell, c, hero, Poses.WAIT, t, heroX, feet(placement, x, y, heroX, ground), SceneDraw.plain());

        // The reflection: pale and see-through, brightening while the copy is made.
        float alpha = 0.7f;
        int flash = 0;
        if (detail != null && clock >= RAISE && clock < ARRIVE) {
            alpha = 0.7f + 0.25f * Math.min(1f, (clock - RAISE) / 16f);
            flash = (clock / 2) % 2 == 0 ? 0x60FFFFFF : 0;
        } else if (detail != null && clock >= ARRIVE) {
            alpha = 0.95f - 0.1f * Math.min(1f, (clock - ARRIVE) / 24f);
        }
        Poses.hero(shell, c, hero, Poses.WAIT, t, mirrorX, feet(placement, x, y, mirrorX, ground),
                SceneDraw.plain().withFlipX(true).withTint(0xFFB6DAFF).withAlpha(alpha).withFlash(flash));

        drawMonitor(shell, c, hero, cx, feet(placement, x, y, cx, ground), t);

        if (detail == null || card == null) {
            return;
        }
        int top = y + 2;
        int leftX = x + 28;
        int rightX = x + w - 28;
        int raise = Math.min(RAISE, clock);
        drawCard(shell, c, card, leftX, top + (RAISE - raise) * 3);
        if (clock >= STEP_OUT) {
            // The copy slides out of the screen and up to the reflection.
            float k = Math.min(1f, (clock - STEP_OUT) / (float) (ARRIVE - STEP_OUT));
            float ease = 1f - (1f - k) * (1f - k);
            int fromX = cx;
            int fromTop = ground - 40 - CardRenderer.SMALL_H / 2;
            int px = Math.round(fromX + (rightX - fromX) * ease);
            int py = Math.round(fromTop + (top - fromTop) * ease);
            drawCard(shell, c, card, px, py);
            if (k < 1f) {
                c.fill(px - CardRenderer.SMALL_W / 2, py, CardRenderer.SMALL_W, CardRenderer.SMALL_H,
                        Colors.alpha(0xFFB6DAFF, 0.6f * (1f - k)));
            } else {
                sparkle(shell, c, rightX + CardRenderer.SMALL_W / 2, top + 4, clock - ARRIVE);
            }
        }
    }

    /**
     * The monitor, its 1-Up face the hero's life icon. While (and after) the copy is made the
     * screen shows only static, the box's two static frames (Map_Monitor 0 and 1).
     */
    private void drawMonitor(Shell shell, SceneCanvas c, String hero, int mx, int feet, long t) {
        SceneSprite box = shell.art.romFrame("monitor", 0);
        if (box == null) {
            c.fill(mx - 14, feet - 30, 28, 30, 0xFF6C6C6C);
            return;
        }
        SceneDraw style = SceneDraw.plain().withScale(SCALE);
        float originY = feet - (box.height() - box.originY()) * SCALE;
        boolean fizzled = detail != null && clock >= RAISE;
        int frame = fizzled ? (int) ((t / 2) % 2) : ONE_UP.charAt((int) ((t / 2) % ONE_UP.length())) - '0';
        c.draw(shell.art.romFrame("monitor", frame == 2 ? 0 : frame), mx, originY, style);
        SceneSprite face = shell.art.romFrame("life_icon_" + hero, 2);
        if (frame == 2 && face != null) {
            c.draw(face, mx, originY, style);
        }
    }
}
