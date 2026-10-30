package com.openggf.game;

/**
 * One tick of the mouse on the master title or one of its children, in local menu pixels: where it is, whether it moved,
 * and the left/right presses and wheel notches of that tick.
 */
public record MasterTitlePointer(int x, int y, boolean moved, boolean click, boolean backClick, int wheel) {
    public boolean over(int rx, int ry, int rw, int rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }
}
