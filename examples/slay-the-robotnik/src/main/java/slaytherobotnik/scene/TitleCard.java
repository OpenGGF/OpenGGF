package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;

/**
 * An act's title card, moving as the game's own does (Obj_TitleCard and ObjArray_TtlCard, see
 * {@code SceneRomArt.titleCard}): the red banner drops in from above while the zone name, "ZONE"
 * and the act slide in from the right, all at 16 pixels a frame. Once everything has arrived the
 * card holds for 90 frames; then an exit counter counts up once a frame and each element leaves
 * at 32 pixels a frame (the banner upwards, the rest to the right) from its exit tick on.
 * Positions are the ROM's, on its 320-pixel screen, centred on wider ones.
 */
final class TitleCard {
    private static final int SPEED_IN = 16;
    private static final int SPEED_OUT = 32;
    private static final int HOLD = 90;
    /** Frames the last element (the act, from x 708 to 260) takes to arrive. */
    private static final int ALL_IN = (708 - 260 + SPEED_IN - 1) / SPEED_IN;
    /** Exit counter value by which the last element (exit tick 7) is off even a wide screen. */
    private static final int ALL_OUT = 7 + 24;

    // Per element: banner, zone name, "ZONE", act.
    private final int[] startX = {96, 480, 636, 708};
    private final int[] startY = {-112, 96, 128, 160};
    private final int[] endX = {96, 160, 252, 260};
    private final int[] endY = {64, 96, 128, 160};
    private final int[] exitTick = {1, 3, 5, 7};
    private final SceneSpriteSet frames;
    private int age;

    TitleCard(SceneSpriteSet frames) {
        this.frames = frames;
    }

    void update() {
        age++;
    }

    /** Whether the card is still arriving or holding (the scene behind should wait). */
    boolean showing() {
        return age < ALL_IN + HOLD;
    }

    /** Whether every element has left the screen. */
    boolean done() {
        return age >= ALL_IN + HOLD + ALL_OUT;
    }

    /** Cuts the hold short: the card starts leaving now (it still leaves the ROM's way). */
    void hurry() {
        if (age >= ALL_IN && age < ALL_IN + HOLD) {
            age = ALL_IN + HOLD;
        }
    }

    void draw(SceneCanvas c, int width) {
        if (done()) {
            return;
        }
        int left = (width - 320) / 2;
        int exit = age - (ALL_IN + HOLD);
        for (int i = 0; i < 4 && i < frames.frameCount(); i++) {
            SceneSprite sprite = frames.frame(i);
            if (sprite == null) {
                continue; // Sky Sanctuary's card has no act number
            }
            int x;
            int y;
            if (i == 0) {
                x = endX[0];
                y = Math.min(endY[0], startY[0] + SPEED_IN * age);
            } else {
                x = Math.max(endX[i], startX[i] - SPEED_IN * age);
                y = endY[i];
            }
            if (exit >= exitTick[i]) {
                int moved = SPEED_OUT * (exit - exitTick[i] + 1);
                if (i == 0) {
                    y -= moved;
                } else {
                    x += moved;
                }
            }
            c.draw(sprite, left + x, y, SceneDraw.plain());
        }
    }
}
