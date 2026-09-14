package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import java.util.ArrayList;
import java.util.List;

/** KiS2's word-count, six-byte sprite pieces (no Sonic 2 two-player tile word). */
public final class Kis2SpriteMappings {
    private Kis2SpriteMappings() { }

    public static List<SpriteMappingFrame> load(RomByteReader reader, int address, int tileOffset) {
        int count = reader.readU16BE(address) / 2;
        if (count < 1 || count > 512) throw new IllegalArgumentException("Invalid KiS2 mapping table");
        List<SpriteMappingFrame> frames = new ArrayList<>(count);
        for (int frame = 0; frame < count; frame++) {
            int at = address + (short) reader.readU16BE(address + frame * 2);
            frames.add(readFrame(reader, at, tileOffset));
        }
        return List.copyOf(frames);
    }
    public static SpriteMappingFrame readFrame(RomByteReader reader, int address, int tileOffset) {
        int count = reader.readU16BE(address);
        List<SpriteMappingPiece> pieces = new ArrayList<>(count);
        for (int i = 0, at = address + 2; i < count; i++, at += 6) {
            int y = (byte) reader.readU8(at), size = reader.readU8(at + 1);
            int tile = reader.readU16BE(at + 2), x = (short) reader.readU16BE(at + 4);
            pieces.add(new SpriteMappingPiece(x, y, ((size >> 2) & 3) + 1, (size & 3) + 1,
                    (tile & 0x7FF) + tileOffset, (tile & 0x800) != 0, (tile & 0x1000) != 0,
                    (tile >> 13) & 3, (tile & 0x8000) != 0));
        }
        return new SpriteMappingFrame(List.copyOf(pieces));
    }

}
