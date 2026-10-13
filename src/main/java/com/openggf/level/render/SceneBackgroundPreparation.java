package com.openggf.level.render;

import com.openggf.game.session.WorldSession;

/** Private ROM scenery and scroll state. Never publishes native palette, art, layout or PLC changes. */
public final class SceneBackgroundPreparation {
    private WorldSession world;
    private int index = -1;
    private DetachedBackground background;
    private ZonePictureSource.Picture picture;

    /** Rewind retains scanline/handler state, never a viewport-sized RGB image. */
    public record State(int index, int width, int height, DetachedBackground.State background) { }
    private static ZonePictureSource.Picture copy(ZonePictureSource.Picture picture) {
        return picture == null ? null : new ZonePictureSource.Picture(picture.width(), picture.height(), picture.argb().clone());
    }
    public ZonePictureSource.Picture picture() { return copy(picture); }
    public State capture() {
        return background == null || picture == null ? new State(-1, 0, 0, null)
                : new State(index, picture.width(), picture.height(), background.capture());
    }
    public void restore(WorldSession world, State state) {
        if (state.background() == null) { reset(); return; }
        if (this.world != world || index != state.index() || background == null) prepareSource(world, state.index());
        background.restore(state.background()); picture = sample(background, state.width(), state.height());
    }

    private void prepareSource(WorldSession world, int index) {
        reset(); this.world = world; this.index = index;
        var factory = world == null ? null : world.getGameModule().getGameService(ZonePictureSource.Factory.class);
        var rom = world == null ? null : world.getDataSource().rom().orElse(null);
        if (factory == null || rom == null) throw new IllegalStateException("Native ROM scenery source is unavailable");
        background = factory.create(rom).background(sourceZone(index), sourceAct(index));
        if (background == null) throw new IllegalStateException("Native ROM scenery " + index + " is not prepared");
    }

    /** Public S3K zone order: AIZ..DDZ, followed by HPZ's sanctuary act. */
    public static int sourceZone(int index) {
        if (index < 0 || index > 13) throw new IllegalArgumentException("Scenery source must be 0..13");
        return index == 13 ? 0x16 : index;
    }
    public static int sourceAct(int index) { sourceZone(index); return index == 13 ? 1 : 0; }

    /** Called once by the world's ACTIVE logical presentation observer, never by a GPU draw. */
    public ZonePictureSource.Picture advance(WorldSession world, int index, int cameraX, int cameraY,
                                             long tick, int width, int height) {
        if (width < 1 || height < 1 || height > 224) throw new IllegalArgumentException("Native scenery requires at most 224 scanlines");
        if (this.world != world || this.index != index || background == null) prepareSource(world, index);
        background.update(cameraX, cameraY, tick);
        picture = sample(background, width, height);
        return picture();
    }

    /** CPU composition of native scanline offsets, with an independently decoded RGB palette. */
    public static ZonePictureSource.Picture sample(DetachedBackground background, int width, int height) {
        var source = background.picture(width);
        int period = background.periodWidth(width), rows = background.periodHeight();
        int[] pixels = new int[Math.multiplyExact(width, height)];
        for (int y = 0; y < height; y++) {
            int sx = background.sourceX(y), sy = Math.floorMod(background.sourceY(y), rows);
            for (int x = 0; x < width; x++) pixels[y * width + x] = source.argb()[sy * source.width() + Math.floorMod(sx + x, period)];
        }
        return new ZonePictureSource.Picture(width, height, pixels);
    }

    public void reset() { world = null; index = -1; background = null; picture = null; }
}
