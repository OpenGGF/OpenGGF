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

    @Test
    public void burrobotRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_BURROBOT_ADDR);

        assertEquals(List.of(2, 2, 2, 2, 2, 2, 2),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x10, -0x14, 3, 3, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-0x18, -0x0C, 2, 3, 0x4B, false, false, 0, false),
                romFrames.get(5).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-0x0C, 0x04, 3, 2, 9, false, false, 0, false),
                romFrames.get(6).pieces().get(1));
    }

    @Test
    public void flappingDoorRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_LZ_FLAPPING_DOOR_ADDR);

        assertEquals(List.of(2, 2, 2),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-8, -0x20, 2, 4, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-5, 6, 4, 4, 8, false, true, 0, false),
                romFrames.get(1).pieces().get(1));
        assertEquals(new SpriteMappingPiece(0, 0x18, 4, 2, 0x18, false, true, 0, false),
                romFrames.get(2).pieces().get(1));
    }

    @Test
    public void waterfallRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_LZ_WATERFALL_ADDR);

        assertEquals(List.of(1, 2, 2, 1, 2, 1, 1, 1, 2, 2, 2, 2),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-8, -0x10, 2, 4, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-0x18, 0, 4, 1, 0x21, false, false, 0, false),
                romFrames.get(8).pieces().get(1));
        assertEquals(new SpriteMappingPiece(0, -0x10, 3, 4, 0x61, false, false, 0, false),
                romFrames.get(11).pieces().get(1));
    }
}
