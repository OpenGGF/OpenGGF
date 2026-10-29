package com.openggf.game.soniccd.level;

import java.util.Objects;

/**
 * Finite native object tile-body format: eighteen signed relative directory words, then word bodies.
 * Targets are relative to byte zero. Aliases and directory order do not change a body's exclusive end:
 * it is the closest greater target, or the declared include end. This value format authenticates no
 * source and knows no game, stage, selector meaning or execution owner.
 */
public final class CdObjectTileIds {
    private CdObjectTileIds() { }

    /** Validates all eighteen entries and returns the selected body's exclusive byte end. */
    public static int endOfBody(CdAssetBytes bytes, int selector) {
        Objects.requireNonNull(bytes);
        if (bytes.length() < 38 || (bytes.length() & 1) != 0) {
            throw new IllegalArgumentException("Object tile directory/data extent");
        }
        if (selector < 0 || selector >= 18) {
            throw new IllegalArgumentException("Object tile directory selector");
        }
        int target = (short) bytes.unsignedWord(selector * 2);
        int end = bytes.length();
        for (int i = 0; i < 18; i++) {
            int offset = (short) bytes.unsignedWord(i * 2);
            if (offset < 36 || (offset & 1) != 0 || offset > bytes.length() - 2) {
                throw new IllegalArgumentException("Object tile signed relative word points outside declared data bodies");
            }
            if (offset > target && offset < end) { end = offset; }
        }
        return end;
    }
}
