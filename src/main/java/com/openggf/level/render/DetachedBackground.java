package com.openggf.level.render;

import com.openggf.level.scroll.ZoneScrollHandler;
import java.util.Objects;

/** A private presentation instance of the stock scroll handler, over ROM-decoded art. */
public final class DetachedBackground {
    private final ZonePictureSource.Picture picture;
    private final ZoneScrollHandler scroll;
    private final int act, periodWidth, periodHeight;
    private final int[] words = new int[224];
    private java.util.function.IntBinaryOperator horizontalExtension;
    private ZonePictureSource.Picture expanded;
    private boolean initialized;
    private int lastX, lastY;
    private long lastTick;

    public DetachedBackground(ZonePictureSource.Picture picture, ZoneScrollHandler scroll,
            int act, int periodWidth, int periodHeight) {
        this.picture = Objects.requireNonNull(picture);
        this.scroll = Objects.requireNonNull(scroll);
        if (periodWidth < 1 || periodWidth > picture.width() || periodHeight < 1 || periodHeight > picture.height()) {
            throw new IllegalArgumentException("Background period outside decoded art");
        }
        this.act = act;
        this.periodWidth = periodWidth;
        this.periodHeight = periodHeight;
    }

    public DetachedBackground withHorizontalExtension(java.util.function.IntBinaryOperator extension) {
        horizontalExtension = Objects.requireNonNull(extension);
        return this;
    }

    public ZonePictureSource.Picture picture() { return picture; }

    public ZonePictureSource.Picture picture(int viewportWidth) {
        if (horizontalExtension == null || viewportWidth <= periodWidth) return picture;
        if (expanded == null || expanded.width() != viewportWidth) {
            int[] pixels = new int[viewportWidth * periodHeight];
            for (int x = 0; x < viewportWidth; x++) {
                int source = horizontalExtension.applyAsInt(x, viewportWidth);
                for (int y = 0; y < periodHeight; y++) pixels[y * viewportWidth + x] = picture.argb()[y * picture.width() + source];
            }
            expanded = new ZonePictureSource.Picture(viewportWidth, periodHeight, pixels);
        }
        return expanded;
    }

    public int periodWidth(int viewportWidth) {
        return horizontalExtension != null && viewportWidth > periodWidth ? viewportWidth : periodWidth;
    }
    public int periodWidth() { return periodWidth; }
    public int periodHeight() { return periodHeight; }

    /** Repeated draws at one scene tick must not advance native accumulators. */
    public void update(int cameraX, int cameraY, long tick) {
        if (initialized && cameraX == lastX && cameraY == lastY && tick == lastTick) return;
        if (!initialized) scroll.init(act, cameraX, cameraY);
        scroll.update(words, cameraX, cameraY, (int) tick, act);
        initialized = true;
        lastX = cameraX;
        lastY = cameraY;
        lastTick = tick;
    }

    public int sourceX(int line) { return -(short) words[line]; }
    public int sourceY(int line) {
        short[] offsets = scroll.getPerLineVScrollBG();
        return line + scroll.getVscrollFactorBG() + (offsets == null ? 0 : offsets[line]);
    }
}
