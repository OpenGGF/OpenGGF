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
public class TestSonic1GhzRomMappings {

    @Test
    public void smashWallRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_SMASH_ADDR);

        assertEquals(List.of(8, 8, 8),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x10, -0x20, 2, 2, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0, 0x10, 2, 2, 4, false, false, 0, false),
                romFrames.get(1).pieces().get(7));
        assertEquals(new SpriteMappingPiece(0, 0x10, 2, 2, 8, false, false, 0, false),
                romFrames.get(2).pieces().get(7));
    }

    @Test
    public void motobugRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_MOTOBUG_ADDR);

        assertEquals(List.of(4, 4, 5, 1, 1, 1, 0),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x14, -0x10, 4, 2, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-0x0C, 9, 3, 1, 0x11, false, false, 0, false),
                romFrames.get(1).pieces().get(3));
        assertEquals(new SpriteMappingPiece(-4, 8, 2, 1, 0x12, false, false, 0, false),
                romFrames.get(2).pieces().get(4));
        assertEquals(new SpriteMappingPiece(0x10, -6, 1, 1, 0x1C, false, false, 0, false),
                romFrames.get(5).pieces().get(0));
    }
}
