package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import java.util.ArrayList;

/** Chip-backed Obj3A text overlay: EOL_Sonic and loc_140AC's FontK upload. */
public final class Kis2ResultsArt {
    // c336fed EOL_Sonic's eleven 6-byte pieces match the chip byte for byte.
    static final int EOL_KNUCKLES = 0x311BF6;
    static final int FONT_K = 0x312096;
    private static final int NUMBERS_VRAM = 0x520;
    private final RomByteReader image;

    public Kis2ResultsArt(RomByteReader image) { this.image = image; }

    public ObjectSpriteSheet apply(ObjectSpriteSheet stock) {
        Pattern[] patterns = stock.getPatterns().clone();
        // loc_140AC writes four K tiles at ArtTile_ArtNem_ResultsText+$16.
        for (int i = 0; i < 4; i++) {
            Pattern tile = new Pattern();
            tile.fromSegaFormat(image.slice(FONT_K + i * 32, 32));
            patterns[0x5C6 - NUMBERS_VRAM + i] = tile;
        }
        var frames = new ArrayList<SpriteMappingFrame>();
        for (int i = 0; i < stock.getFrameCount(); i++) frames.add(stock.getFrame(i));
        frames.set(0, readKnucklesGotFrame());
        return new ObjectSpriteSheet(patterns, frames, stock.getPaletteIndex(), stock.getFrameDelay());
    }

    SpriteMappingFrame readKnucklesGotFrame() {
        return Kis2SpriteMappings.readFrame(image, EOL_KNUCKLES, -NUMBERS_VRAM);
    }
}
