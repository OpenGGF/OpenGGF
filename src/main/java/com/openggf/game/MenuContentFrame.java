package com.openggf.game;

import com.openggf.control.MenuInput;
import com.openggf.configuration.WidescreenAspect;

/** Local host-menu coordinates, centered inside a possibly wider logical viewport. */
public record MenuContentFrame(int surfaceWidth, int width, int left) {
    public static MenuContentFrame fit(int viewportWidth) {
        int surface = Math.max(320, viewportWidth);
        int width = Math.min(surface, WidescreenAspect.WIDE_16_9.pixelWidth());
        return new MenuContentFrame(surface, width, (surface - width) / 2);
    }

    public MenuInput.Pointer local(MenuInput.Pointer pointer) {
        int x = pointer.x() - left;
        return new MenuInput.Pointer(x, pointer.y(), pointer.inside() && x >= 0 && x < width,
                pointer.leftPressed(), pointer.rightPressed());
    }
}
