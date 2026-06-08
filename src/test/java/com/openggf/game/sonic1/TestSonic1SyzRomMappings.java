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
public class TestSonic1SyzRomMappings {

    @Test
    public void bumperRomMappingsKeepExpectedTableShape() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        List<SpriteMappingFrame> romFrames = S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_SYZ_BUMPER_ADDR);

        assertEquals(List.of(2, 2, 2),
                romFrames.stream().map(frame -> frame.pieces().size()).toList());
        assertEquals(new SpriteMappingPiece(-0x10, -0x10, 2, 4, 0, false, false, 0, false),
                romFrames.get(0).pieces().get(0));
        assertEquals(new SpriteMappingPiece(0, -0x10, 2, 4, 0, true, false, 0, false),
                romFrames.get(0).pieces().get(1));
        assertEquals(new SpriteMappingPiece(4, -0x0C, 1, 3, 8, true, false, 0, false),
                romFrames.get(1).pieces().get(1));
        assertEquals(new SpriteMappingPiece(0, -0x10, 2, 4, 0x0E, true, false, 0, false),
                romFrames.get(2).pieces().get(1));
    }
}
