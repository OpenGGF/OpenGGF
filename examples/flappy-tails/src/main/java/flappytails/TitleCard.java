package flappytails;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;

/**
 * Plays a stock title card the way the ROM's {@code Obj_TitleCard} does, from the four sprites
 * {@code SceneRomArt.titleCard} returns: every element slides in at 16 pixels a frame to its
 * place, the card holds for 90 frames once all have arrived, then an exit counter runs and each
 * element leaves at 32 pixels a frame when the counter reaches its own tick (the red banner
 * first, upwards; the zone name, "ZONE" and the act number to the right).
 *
 * <p>The positions are the ROM's for a 320-pixel screen; {@code xOffset} centres the card on a
 * wider one. Everything is a function of the ticks since the card began, so drawing it never
 * changes state.
 */
final class TitleCard {
    // Start and rest positions of the four elements, and the exit tick of each (SceneRomArt.titleCard).
    // Mods may not keep static arrays, so the tables are switches.
    private static int startX(int i) { return switch (i) { case 0 -> 96; case 1 -> 480; case 2 -> 636; default -> 708; }; }
    private static int startY(int i) { return switch (i) { case 0 -> -112; case 1 -> 96; case 2 -> 128; default -> 160; }; }
    private static int restX(int i) { return switch (i) { case 0 -> 96; case 1 -> 160; case 2 -> 252; default -> 260; }; }
    private static int restY(int i) { return switch (i) { case 0 -> 64; case 1 -> 96; case 2 -> 128; default -> 160; }; }
    private static int exitTick(int i) { return 2 * i + 1; }
    private static final int SLIDE_IN = 16;
    private static final int SLIDE_OUT = 32;
    private static final int HOLD = 90;

    private TitleCard() { }

    /** Frames until every element has arrived: the farthest one's distance over 16. */
    static int arrival() {
        int frames = 0;
        for (int i = 0; i < 4; i++) {
            int distance = Math.abs(startX(i) - restX(i)) + Math.abs(startY(i) - restY(i));
            frames = Math.max(frames, (distance + SLIDE_IN - 1) / SLIDE_IN);
        }
        return frames;
    }

    /** Frames until the last element has left a screen {@code width} wide. */
    static int duration(int width) {
        return arrival() + HOLD + exitTick(3) + (width + 240) / SLIDE_OUT;
    }

    /**
     * Draws the card {@code elapsed} frames after it began. {@code name} replaces the zone name
     * when not null (the mod's own "FLAPPY TAILS ZONE" card); {@code showAct} hides the act number
     * when false, as the ROM does for Sky Sanctuary.
     */
    static void draw(SceneCanvas canvas, SceneSpriteSet card, long elapsed, int xOffset,
            NamePainter name, boolean showAct) {
        if (card == null || elapsed < 0) return;
        long exit = elapsed - arrival() - HOLD;
        for (int i = 0; i < 4; i++) {
            if (i == 3 && (!showAct || card.frameCount() < 4)) continue;
            float x = slide(startX(i), restX(i), elapsed);
            float y = slide(startY(i), restY(i), elapsed);
            if (exit >= exitTick(i)) {
                long out = (exit - exitTick(i) + 1) * SLIDE_OUT;
                if (i == 0) y -= out; else x += out;
            }
            if (i == 1 && name != null) {
                name.paint(canvas, x + xOffset, y);
                continue;
            }
            SceneSprite sprite = card.frame(i);
            canvas.draw(sprite, x + xOffset, y, SceneDraw.plain());
        }
    }

    private static float slide(int from, int to, long elapsed) {
        long moved = elapsed * SLIDE_IN;
        if (from < to) return Math.min(to, from + moved);
        return Math.max(to, from - moved);
    }

    /** Paints a replacement zone name with its left edge at the name element's origin. */
    interface NamePainter {
        void paint(SceneCanvas canvas, float x, float y);
    }
}
