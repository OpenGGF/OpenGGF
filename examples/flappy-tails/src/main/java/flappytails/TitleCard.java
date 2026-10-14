package flappytails;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.TitleCardPresentation;

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
    private TitleCard() { }

    /** Frames until every element has arrived: the farthest one's distance over 16. */
    static int arrival() {
        return TitleCardPresentation.arrivalFrames();
    }

    /** Frames until the last element has left a screen {@code width} wide. */
    static int duration(int width) {
        return TitleCardPresentation.durationFrames(width);
    }

    /**
     * Draws the card {@code elapsed} frames after it began. {@code name} replaces the zone name
     * when not null (the mod's own "FLAPPY TAILS ZONE" card); {@code showAct} hides the act number
     * when false, as the ROM does for Sky Sanctuary.
     */
    static void draw(SceneCanvas canvas, SceneSpriteSet card, long elapsed, int xOffset,
            NamePainter name, boolean showAct) {
        if (card == null || elapsed < 0) return;
        long exit = TitleCardPresentation.automaticExitElapsed(elapsed);
        for (int i = 0; i < 4 && i < card.frameCount(); i++) {
            if (i == 3 && (!showAct || card.frameCount() < 4)) continue;
            var position = TitleCardPresentation.position(i, elapsed, exit);
            float x = position.x(), y = position.y();
            if (i == 1 && name != null) {
                name.paint(canvas, x + xOffset, y);
                continue;
            }
            SceneSprite sprite = card.frame(i);
            canvas.draw(sprite, x + xOffset, y, SceneDraw.plain());
        }
    }

    /** Paints a replacement zone name with its left edge at the name element's origin. */
    interface NamePainter {
        void paint(SceneCanvas canvas, float x, float y);
    }
}
