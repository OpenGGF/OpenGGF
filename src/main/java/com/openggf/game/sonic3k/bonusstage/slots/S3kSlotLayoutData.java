package com.openggf.game.sonic3k.bonusstage.slots;

import java.util.Objects;

/** The two native entry words and the original 32-by-32 ROM layout, before mutation. */
record S3kSlotLayoutData(short startX, short startY, byte[] layout) {
    S3kSlotLayoutData {
        Objects.requireNonNull(layout, "layout");
        if (layout.length != S3kSlotRomData.SLOT_LAYOUT_SIZE * S3kSlotRomData.SLOT_LAYOUT_SIZE) {
            throw new IllegalArgumentException("Slots needs exactly 1024 layout bytes");
        }
        layout = layout.clone();
    }

    @Override public byte[] layout() { return layout.clone(); }
}
