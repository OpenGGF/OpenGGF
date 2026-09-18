package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.slotmachine.CNZSlotMachineRenderer;
import java.util.Objects;

/** Chip patch selected by SlotMachine_GetPixelRow for picture zero only. */
public final class Kis2SlotArt implements CNZSlotMachineRenderer.ArtPresentation {
    // loc_32598C: lea (ArtUnc_CNZSlotPicsKnucklesPatch).l,a2.
    // Verified pointer operand and unique 512-byte source asset match in shipped KiS2.
    public static final int KNUCKLES_PICTURE = 0x33B1F0;
    private static final int PICTURE_BYTES = 16 * 32;
    private final RomByteReader image;

    public Kis2SlotArt(RomByteReader image) { this.image = Objects.requireNonNull(image); }

    @Override public byte[] apply(byte[] stockPictures) {
        if (stockPictures.length != 6 * PICTURE_BYTES) {
            throw new IllegalArgumentException("Expected six CNZ slot pictures");
        }
        byte[] result = stockPictures.clone();
        System.arraycopy(image.slice(KNUCKLES_PICTURE, PICTURE_BYTES), 0, result, 0, PICTURE_BYTES);
        return result;
    }

    @Override public String faceName(int face) {
        return face == 0 ? "Knuckles" : CNZSlotMachineRenderer.getFaceName(face);
    }
}
