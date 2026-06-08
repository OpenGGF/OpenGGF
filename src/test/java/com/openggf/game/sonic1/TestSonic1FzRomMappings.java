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
public class TestSonic1FzRomMappings {

    @Test
    public void fzLegsAndDamagedRomMappingsStayWithinFzEggmanPatternRange() throws Exception {
        // Nem_FzEggman ("Boss - Eggman after FZ Fight") is 0x4C patterns in REV01.
        // Map_FZLegs and Map_FZDamaged must remain within that local tile range.
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        int maxLegsTile = maxTileIndex(S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_FZ_LEGS_ADDR));
        int maxDamagedTile = maxTileIndex(S1SpriteDataLoader.loadMappingFrames(
                reader, Sonic1Constants.MAP_FZ_DAMAGED_ADDR));

        assertEquals(0x1F, maxLegsTile, "Map_FZLegs max tile should be $1F");
        assertEquals(0x4B, maxDamagedTile, "Map_FZDamaged max tile should be $4B");
    }

    private static int maxTileIndex(List<SpriteMappingFrame> frames) {
        int max = 0;
        for (SpriteMappingFrame frame : frames) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                int lastTile = piece.tileIndex() + (piece.widthTiles() * piece.heightTiles()) - 1;
                if (lastTile > max) {
                    max = lastTile;
                }
            }
        }
        return max;
    }
}
