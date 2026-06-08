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
public class TestSonic1SbzRomMappings {

    @Test
    public void buttonRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_BUTTON_ADDR);

        assertEquals(List.of(2, 2, 2, 2),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x10, -0x0B, 2, 2, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-0x10, -0x0B, 2, 2, 0x7FC, true, true, 3, true),
                romFrames.get(2).pieces().get(0));
        assertEquals(romFrames.get(1), romFrames.get(3));
    }

    @Test
    public void sbzFalseFloorRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_SBZ_FALSE_FLOOR_ADDR);

        assertEquals(List.of(1, 2, 2, 2, 2),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0, -8, 1, 2, 0x0E, false, false, 0, false),
                romFrames.get(4).pieces().get(1));
    }

    @Test
    public void sbzFlamethrowerRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_SBZ_FLAMETHROWER_ADDR);

        assertEquals(List.of(1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
                        1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-5, 0x28, 2, 2, 0x14, false, false, 2, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-0x0C, -0x19, 3, 4, 8, true, false, 0, false),
                romFrames.get(10).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-7, 0x28, 2, 2, 0x18, false, false, 2, false),
                romFrames.get(11).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-4, 0x20, 1, 2, 0, true, false, 0, false),
                romFrames.get(21).pieces().get(5));
    }
}
