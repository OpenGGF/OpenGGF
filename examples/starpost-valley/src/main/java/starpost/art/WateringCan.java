package starpost.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;

/** Original pixel art for the farm's shield-fed can; the other chores use the farmer's abilities. */
public final class WateringCan {
    public static final int TICKS = 32;
    private final SceneImage can = ItemIcons.picture(new String[] {
        ".....kkkk.......",
        "....kYwwYk......",
        "....k....k......",
        "...kkkkkkkk..kk.",
        "..kYFFFFFFYkkwYk",
        ".kkFFFFFFFfkffk.",
        "kFkFFFFFFFfkfk..",
        "kFkFFFFFFFfkk...",
        "kFkFFFFFFFfk....",
        ".kkFFFFFFFfk....",
        "..kFFFFFFffk....",
        "..kffffffffk....",
        "...kkkkkkkk.....",
    }, 0xFF6DB6FF, 0xFF2449DB);
    private final SceneImage drop = new SceneImage(2, 3, new int[] {
        0, 0xFFB6DBFF, 0xFF6DB6FF, 0xFFFFFFFF, 0, 0xFF2449DB
    });
    private final SceneSprite[] poses = {tip(0), tip(12), tip(24)};

    public WateringCan() { }

    /** The same can on the hotbar and in the farmer's hand. */
    public SceneImage icon() { return can; }

    /** A short lift, tip, pour and recovery, anchored to the ROM standing frame's front hand. */
    public void draw(SceneCanvas canvas, String farmer, int age, float x, float feet,
            boolean left, SceneDraw tint) {
        if (age < 0 || age >= TICKS) return;
        int direction = left ? -1 : 1;
        float handForward = farmer.equals("knuckles") ? 8 : 6;
        float handUp = switch (farmer) { case "tails" -> 13; case "knuckles" -> 16; default -> 17; };
        float handX = x + direction * handForward, handY = feet - handUp;
        int pose = age < 4 || age >= 29 ? 0 : age < 8 || age >= 25 ? 1 : 2;
        SceneDraw style = tint.withFlipX(left);
        if (age >= 29) style = style.withAlpha((TICKS - age) / 3f);
        canvas.draw(poses[pose], handX, handY, style);
        // The tipped spout is twelve pixels forward and two below the grip.
        if (age >= 8 && age < 27) {
            for (int i = 0; i < 5; i++) {
                int fall = Math.floorMod(age - 8 + i * 3, 14);
                float dropX = handX + direction * (12 + fall * 0.15f + i % 3 - 1);
                float dropY = handY + 2 + fall;
                canvas.draw(drop, Math.round(dropX), Math.round(dropY), tint);
            }
        }
    }

    /** Bake nearest-neighbour poses once, keeping the rear handle on the same origin. */
    private SceneSprite tip(int degrees) {
        int w = 24, h = 24, ox = 5, oy = 9;
        int[] pixels = new int[w * h];
        double angle = Math.toRadians(degrees), cos = Math.cos(angle), sin = Math.sin(angle);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sourceX = (int) Math.round((x - ox) * cos + (y - oy) * sin + 2);
                int sourceY = (int) Math.round(-(x - ox) * sin + (y - oy) * cos + 7);
                if (sourceX >= 0 && sourceX < can.width() && sourceY >= 0 && sourceY < can.height())
                    pixels[y * w + x] = can.pixel(sourceX, sourceY);
            }
        }
        return new SceneSprite(new SceneImage(w, h, pixels), ox, oy);
    }
}
