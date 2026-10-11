package com.openggf.mods.scene.host;

import com.openggf.level.render.DetachedBackground;
import com.openggf.mods.scene.SceneBackground;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;

/** Stock scanline offsets composed through the scene canvas, without a live level or GL state. */
final class RomSceneBackground implements SceneBackground {
    private final DetachedBackground background;
    private SceneImage image;
    private com.openggf.level.render.ZonePictureSource.Picture picture;

    RomSceneBackground(DetachedBackground background) {
        this.background = background;

    }

    @Override
    public void draw(SceneCanvas canvas, int cameraX, int cameraY, long ticks) {
        if (canvas.height() != 224) throw new IllegalArgumentException("Stock background requires 224 scanlines");
        background.update(cameraX, cameraY, ticks);
        var current = background.picture(canvas.width());
        if (current != picture) {
            picture = current;
            image = new SceneImage(picture.width(), picture.height(), picture.argb());
        }
        int width = background.periodWidth(canvas.width()), height = background.periodHeight();
        for (int y = 0; y < canvas.height();) {
            int sx = Math.floorMod(background.sourceX(y), width);
            int sy = Math.floorMod(background.sourceY(y), height);
            int run = 1;
            while (y + run < canvas.height() && sy + run < height
                    && Math.floorMod(background.sourceX(y + run), width) == sx
                    && Math.floorMod(background.sourceY(y + run), height) == sy + run) run++;
            for (int x = 0; x < canvas.width();) {
                int source = (sx + x) % width;
                int count = Math.min(width - source, canvas.width() - x);
                canvas.drawRegion(image, source, sy, count, run, x, y, count, run, SceneDraw.plain());
                x += count;
            }
            y += run;
        }
    }
}
