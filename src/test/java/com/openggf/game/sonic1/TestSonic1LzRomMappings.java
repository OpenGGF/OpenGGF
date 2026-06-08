package com.openggf.game.sonic1;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1LzRomMappings {

    @Test
    public void harpoonRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_LZ_HARPOON_ADDR);

        assertEquals(List.of(1, 1, 2, 1, 1, 2),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-8, -4, 2, 1, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0x10, -4, 3, 1, 3, false, false, 0, false),
                romFrames.get(2).pieces().get(1));
        assertEquals(new SpriteMappingPiece(-4, -0x10, 1, 3, 0x0F, false, false, 0, false),
                romFrames.get(5).pieces().get(1));
    }

    @Test
    public void jawsRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_JAWS_ADDR);

        assertEquals(List.of(2, 2, 2, 2),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x10, -0x0C, 4, 3, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0x10, -0x0B, 2, 2, 0x18, false, true, 0, false),
                romFrames.get(2).pieces().get(1));
        assertEquals(new SpriteMappingPiece(0x10, -0x0B, 2, 2, 0x1C, false, true, 0, false),
                romFrames.get(3).pieces().get(1));
    }
}
