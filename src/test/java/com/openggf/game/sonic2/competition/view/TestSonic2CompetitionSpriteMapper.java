package com.openggf.game.sonic2.competition.view;
import static org.junit.jupiter.api.Assertions.*;

import com.openggf.data.RomByteReader;
import java.util.List;
import org.junit.jupiter.api.Test;
class TestSonic2CompetitionSpriteMapper {
    @Test
    void mappingUsesSeparateTwoPlayerWordsAndShippedMultispriteFlipDispatch() {
        byte[] bytes = new byte[32];
        word(bytes, 0, 4);
        word(bytes, 4, 1);
        bytes[6] = -9;
        bytes[7] = 10; // ordinary 3x3 -> native 3x2 cells
        word(bytes, 8, 0x7777);
        word(bytes, 10, 0x2803);
        word(bytes, 12, -5);
        var pieces = Sonic2CompetitionSpriteMapper.readFrame(RomByteReader.fromBytes(bytes), 0, 0);
        assertEquals(List.of(new Sonic2CompetitionSpriteMapper.MappingPiece(-5, -9, 10, 0x2803)), pieces);
        int art = Sonic2CompetitionSpriteMapper.nativeArtWord(0xF81B);
        assertEquals(0xF80D, art);
        var ordinary = Sonic2CompetitionSpriteMapper.map(pieces, 100, 200, art, true, true, false).getFirst();
        assertEquals(81, ordinary.x());
        assertEquals(185, ordinary.y());
        assertEquals(9, ordinary.size());
        assertEquals(((0xF80D + 0x2803) & 65535) ^ 0x1800, ordinary.name().value());
        var multi = Sonic2CompetitionSpriteMapper.map(pieces, 100, 200, art, true, true, true).getFirst();
        assertEquals(95, multi.x());
        assertEquals(191, multi.y());
        assertEquals((0xF80D + 0x2803) & 65535, multi.name().value());
        var maskAvoidance = Sonic2CompetitionSpriteMapper.map(pieces, -123, 0, 0, false, false, false).getFirst();
        assertEquals(1, maskAvoidance.rawX());
    }

    private static void word(byte[] bytes, int at, int word) {
        bytes[at] = (byte) (word >>> 8);
        bytes[at + 1] = (byte) word;
    }
}
