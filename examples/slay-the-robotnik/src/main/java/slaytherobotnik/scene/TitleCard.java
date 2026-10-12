package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.TitleCardPresentation;

/**
 * An act's title card, moving as the game's own does (Obj_TitleCard and ObjArray_TtlCard, see
 * {@code SceneRomArt.titleCard}): the red banner drops in from above while the zone name, "ZONE"
 * and the act slide in from the right, all at 16 pixels a frame. Once everything has arrived the
 * card holds for 90 frames; then an exit counter counts up once a frame and each element leaves
 * at 32 pixels a frame (the banner upwards, the rest to the right) from its exit tick on.
 * Positions are the ROM's, on its 320-pixel screen, centred on wider ones.
 */
final class TitleCard {
    /** Exit counter value by which the last element (exit tick 7) is off even a wide screen. */
    private static final int ALL_OUT = 7 + 24;

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
        return age < TitleCardPresentation.arrivalFrames() + TitleCardPresentation.holdFrames();
    }

    /** Whether every element has left the screen. */
    boolean done() {
        return age >= TitleCardPresentation.arrivalFrames() + TitleCardPresentation.holdFrames() + ALL_OUT;
    }

    /** Cuts the hold short: the card starts leaving now (it still leaves the ROM's way). */
    void hurry() {
        if (age >= TitleCardPresentation.arrivalFrames() && age < TitleCardPresentation.arrivalFrames() + TitleCardPresentation.holdFrames()) {
            age = TitleCardPresentation.arrivalFrames() + TitleCardPresentation.holdFrames();
        }
    }

    void draw(SceneCanvas c, int width) {
        if (done()) {
            return;
        }
        int left = (width - 320) / 2;
        int exit = age - (TitleCardPresentation.arrivalFrames() + TitleCardPresentation.holdFrames());
        for (int i = 0; i < 4 && i < frames.frameCount(); i++) {
            SceneSprite sprite = frames.frame(i);
            if (sprite == null) {
                continue; // The provider has no frame for this piece.
            }
            var position = TitleCardPresentation.position(i, age, exit);
            c.draw(sprite, left + position.x(), position.y(), SceneDraw.plain());
        }
    }
}
