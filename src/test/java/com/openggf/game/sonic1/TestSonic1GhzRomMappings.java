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

    @Test
    public void newtronRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_NEWTRON_ADDR);

        assertEquals(List.of(3, 3, 3, 4, 3, 2, 3, 3, 3, 3, 0),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x14, -0x14, 4, 2, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-0x14, -0x14, 2, 3, 0x2A, false, false, 0, false),
                romFrames.get(2).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0x0C, 0x0C, 1, 1, 0x3C, false, false, 0, false),
                romFrames.get(3).pieces().get(3));
        assertEquals(new SpriteMappingPiece(0x14, -2, 1, 1, 0x52, false, false, 3, true),
                romFrames.get(8).pieces().get(2));
        assertEquals(new SpriteMappingPiece(0x14, -2, 2, 1, 0x53, false, false, 3, true),
                romFrames.get(9).pieces().get(2));
    }

    @Test
    public void crabmeatRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_CRABMEAT_ADDR);

        assertEquals(List.of(4, 4, 4, 4, 6, 1, 1),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x18, -0x10, 3, 2, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0, -0x10, 3, 2, 0, true, false, 0, false),
                romFrames.get(0).pieces().get(1));
        assertEquals(new SpriteMappingPiece(-0x10, -0x10, 2, 1, 0x32, false, false, 0, false),
                romFrames.get(4).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0, 0x08, 2, 1, 0x3A, true, false, 0, false),
                romFrames.get(4).pieces().get(5));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0x3C, false, false, 0, false),
                romFrames.get(5).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0x40, false, false, 0, false),
                romFrames.get(6).pieces().get(0));
    }

    @Test
    public void buzzBomberRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_BUZZ_BOMBER_ADDR);

        assertEquals(List.of(6, 6, 7, 7, 6, 4),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x18, -0x0C, 3, 2, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(4, -0x0F, 2, 1, 0x1D, false, false, 0, false),
                romFrames.get(0).pieces().get(5));
        assertEquals(new SpriteMappingPiece(0x0C, 4, 1, 1, 0x30, false, false, 0, false),
                romFrames.get(2).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0x0C, 4, 2, 1, 0x31, false, false, 0, false),
                romFrames.get(3).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-0x0C, 0x0C, 2, 1, 0x0D, false, false, 0, false),
                romFrames.get(5).pieces().get(3));
    }

    @Test
    public void buzzBomberMissileRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_BUZZ_MISSILE_ADDR);

        assertEquals(List.of(1, 1, 1, 1),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0x24, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0x28, false, false, 0, false),
                romFrames.get(1).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0x2C, false, false, 0, false),
                romFrames.get(2).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0x33, false, false, 0, false),
                romFrames.get(3).pieces().get(0));
    }

    @Test
    public void spikedPoleHelixRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_SPIKED_POLE_HELIX_ADDR);

        assertEquals(List.of(1, 1, 1, 1, 1, 1, 0, 1),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-4, -0x10, 1, 2, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-8, -0x0B, 2, 2, 2, false, false, 0, false),
                romFrames.get(1).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-8, -5, 2, 2, 0x0A, false, false, 0, false),
                romFrames.get(3).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-3, 4, 1, 1, 0x10, false, false, 0, false),
                romFrames.get(5).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-3, -0x0C, 1, 1, 0x11, false, false, 0, false),
                romFrames.get(7).pieces().get(0));
    }

    @Test
    public void ghzSwingingPlatformRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_SWING_GHZ_ADDR);

        assertEquals(List.of(2, 1, 1),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x18, -8, 3, 2, 4, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0, -8, 3, 2, 4, false, false, 0, false),
                romFrames.get(0).pieces().get(1));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0, false),
                romFrames.get(1).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0x0A, false, false, 0, false),
                romFrames.get(2).pieces().get(0));
    }

    @Test
    public void slzSwingingPlatformRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_SWING_SLZ_ADDR);

        assertEquals(List.of(8, 1, 1),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x20, -0x10, 4, 4, 4, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0, -0x10, 4, 4, 4, true, false, 0, false),
                romFrames.get(0).pieces().get(1));
        assertEquals(new SpriteMappingPiece(0, 0x10, 1, 2, 0x1A, true, false, 0, false),
                romFrames.get(0).pieces().get(7));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 2, false),
                romFrames.get(1).pieces().get(0));
        assertEquals(new SpriteMappingPiece(-8, -8, 2, 2, 0x1C, false, false, 0, false),
                romFrames.get(2).pieces().get(0));
    }
}
