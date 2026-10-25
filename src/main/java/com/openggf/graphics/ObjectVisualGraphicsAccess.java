package com.openggf.graphics;

import java.util.ArrayList;
import java.util.List;

/** Engine-internal CPU snapshot access; no GPU handles cross the template boundary. */
public final class ObjectVisualGraphicsAccess {
    private ObjectVisualGraphicsAccess() {}

    public static final class ArtBank {
        private final GraphicsManager graphics;
        private final Object owner;
        private final ObjectVisualPatternBanks.Bank bank;
        private boolean released;

        private ArtBank(GraphicsManager graphics, Object owner, int count) {
            this.graphics = graphics;
            this.owner = owner;
            bank = graphics.objectVisualPatternBank(owner, count);
        }
    }

    public static ArtBank acquireArt(GraphicsManager graphics, Object owner, int count) {
        return new ArtBank(graphics, owner, count);
    }

    public static boolean validArt(ArtBank bank, GraphicsManager graphics, Object owner) {
        return bank != null
                && !bank.released
                && bank.graphics == graphics
                && bank.owner == owner
                && graphics.objectVisualPatternBankValid(owner, bank.bank);
    }

    public static int artBase(ArtBank bank, GraphicsManager graphics, Object owner) {
        if (!validArt(bank, graphics, owner)) {
            throw new IllegalArgumentException(
                    "Object art lease belongs to another owner, host or generation");
        }
        return bank.bank.base();
    }

    public static void releaseArt(ArtBank bank, Object owner) {
        if (bank == null) {
            return;
        }
        if (bank.owner != owner) {
            throw new IllegalArgumentException("Object art lease belongs to another owner");
        }
        if (!bank.released) {
            bank.graphics.releaseObjectVisualPatternBank(owner, bank.bank);
            bank.released = true;
        }
    }

    /**
     * Only directed visuals attach a palette mask; ordinary native SAT retains its boolean path.
     */
    public static void attachLastVisualMask(GraphicsManager graphics, int mask) {
        graphics.attachObjectVisualSatMask(mask);
    }

    public static void clipLastSatTile(GraphicsManager graphics, int rowStart, int rowEnd) {
        graphics.clipObjectVisualSatTile(rowStart, rowEnd);
    }

    public static List<Integer> palette(GraphicsManager graphics, int line) {
        PaletteView palette = graphics.paletteTextures().objectVisualPalette(line);
        if (palette == null) {
            throw new IllegalStateException("Native palette line " + line + " is not initialized");
        }
        var result = new ArrayList<Integer>(16);
        for (int i = 0; i < 16; i++) {
            result.add(
                    (i == 0 ? 0 : 0xff000000)
                            | (palette.red(i) & 255) << 16
                            | (palette.green(i) & 255) << 8
                            | (palette.blue(i) & 255));
        }
        return List.copyOf(result);
    }
}
