package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceDecoder.*;

/** S2's eight-byte mapping format and shipped DrawSprite_2P branches, before hardware sampling. */
public final class Sonic2InterlaceSpriteMapper {
    private Sonic2InterlaceSpriteMapper() { }

    public record MappingPiece(int x, int y, int ordinarySize, int nativeTileWord) {
        public MappingPiece {
            if (x < Short.MIN_VALUE || x > Short.MAX_VALUE || y < -128 || y > 127
                    || ordinarySize < 0 || ordinarySize > 15)
                throw new IllegalArgumentException("Invalid S2 mapping piece");
            nativeTileWord &= 0xFFFF;
        }
    }

    /** Decode the separate 2P word; never derive it from the ordinary mapping tile word. */
    public static List<MappingPiece> readFrame(RomByteReader rom, int mappingAddress, int frame) {
        if (frame < 0) throw new IllegalArgumentException("Negative mapping frame");
        int address = mappingAddress + rom.readS16BE(mappingAddress + frame * 2);
        int count = rom.readU16BE(address);
        var pieces = new ArrayList<MappingPiece>(count);
        for (int i = 0; i < count; i++) {
            int piece = address + 2 + i * 8;
            pieces.add(new MappingPiece(rom.readS16BE(piece + 6), (byte) rom.readU8(piece),
                    rom.readU8(piece + 1), rom.readU16BE(piece + 4)));
        }
        return List.copyOf(pieces);
    }

    /** Adjust2PArtPointer (s2.asm:31456-31463), called once when an object selects ordinary art. */
    public static int nativeArtWord(int ordinaryArtWord) {
        return Sonic2CompetitionLevelProfile.nativePatternWord(ordinaryArtWord);
    }

    /**
     * Origins are visible native pixels (P2 adds 224 to Y). nativeArtWord has already
     * passed Adjust2PArtPointer. DrawSprite_2P (s2.asm:31511-31735) adds whole words,
     * remaps the size, and mirrors offsets using the original mapping dimensions.
     *
     * <p>FixBugs=0: ChkDrawSprite_2P (s2.asm:31490-31503) skips object flip dispatch
     * for multisprites. The fixed branch would mirror those pieces too. Mapping
     * attribute flips remain effective in either path.</p>
     */
    public static List<Sprite> map(List<MappingPiece> pieces, int originX, int originY,
            int nativeArtWord, boolean objectHFlip, boolean objectVFlip, boolean multisprite) {
        var result = new ArrayList<Sprite>(pieces.size());
        boolean h = objectHFlip && !multisprite;
        boolean v = objectVFlip && !multisprite;
        for (var piece : pieces) {
            int ordinaryWidth = ((piece.ordinarySize() >>> 2) + 1) * 8;
            int ordinaryHeight = ((piece.ordinarySize() & 3) + 1) * 8;
            int x = originX + (h ? -piece.x() - ordinaryWidth : piece.x());
            int y = originY + (v ? -piece.y() - ordinaryHeight : piece.y());
            int size = (piece.ordinarySize() & 12) | ((piece.ordinarySize() & 3) >>> 1);
            int word = ((nativeArtWord + piece.nativeTileWord()) & 0xFFFF)
                    ^ (h ? 0x800 : 0) ^ (v ? 0x1000 : 0);
            int rawX = (x + 0x80) & 0x1FF;
            // DrawSprite_2P avoids an accidental hardware X=0 masking entry.
            if (rawX == 0) rawX = 1;
            result.add(new Sprite(y + 0x100, size, 0, new NameWord(word), rawX));
        }
        return List.copyOf(result);
    }
}
