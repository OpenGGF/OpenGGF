package com.openggf.game.presentation;

import com.openggf.graphics.*;
import com.openggf.level.Palette;

/** Renderer-owned colours, isolated from native level/frontend lines and rebuilt after context retirement. */
public final class PresentationPaletteBank implements AutoCloseable {
    private final Palette[] colours;
    private ScopedPaletteGraphicsAccess.Bank bank;
    public PresentationPaletteBank(Palette palette) {
        colours = new Palette[]{palette.deepCopy(),palette.deepCopy(),palette.deepCopy(),palette.deepCopy()};
    }
    public int base(GraphicsManager graphics) {
        if (!ScopedPaletteGraphicsAccess.valid(bank,graphics,this)) {
            close();
            bank = ScopedPaletteGraphicsAccess.acquire(graphics,this,colours);
        }
        return ScopedPaletteGraphicsAccess.base(bank,graphics,this);
    }
    @Override public void close() {
        ScopedPaletteGraphicsAccess.release(bank,this);
        bank=null;
    }
}
