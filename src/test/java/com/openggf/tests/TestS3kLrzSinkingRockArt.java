package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-backed regression for Obj_LRZSinkingRock's act-specific art_tile words. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzSinkingRockArt {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void loadedSheetUsesTheRocksTerrainTiles(int act) {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, act).build();
        var manager = GameServices.level();
        var sheet = manager.getObjectRenderManager().getSheet(act == 0
                ? Sonic3kObjectArtKeys.LRZ_SINKING_ROCK
                : Sonic3kObjectArtKeys.LRZ2_SINKING_ROCK);
        assertNotNull(sheet);
        assertEquals(2, sheet.getPaletteIndex());
        assertEquals(2, sheet.getFrameCount());
        // Map_LRZSinkingRock ($42834): 4x2@0 + two mirrored 2x2@8;
        // act 2 is two mirrored 2x4@0 pieces. Both frames share the table.
        assertEquals(3, sheet.getFrame(0).pieces().size());
        assertEquals(2, sheet.getFrame(1).pieces().size());
        assertEquals(12, sheet.getPatterns().length);
        int[][][] shapes = {{{4, 2, 0}, {2, 2, 8}, {2, 2, 8}}, {{2, 4, 0}, {2, 4, 0}}};
        for (int frame = 0; frame < shapes.length; frame++) {
            for (int piece = 0; piece < shapes[frame].length; piece++) {
                var actual = sheet.getFrame(frame).pieces().get(piece);
                assertArrayEquals(shapes[frame][piece],
                        new int[]{actual.widthTiles(), actual.heightTiles(), actual.tileIndex()});
            }
        }
        // Obj_LRZSinkingRock explicitly uses $0D3, NOT ArtTile_LRZMisc ($3A1).
        // Its Current_act branch changes the base to $090 for act 2.
        int nativeBase = act == 0 ? 0x0D3 : 0x090;
        for (int tile = 0; tile < sheet.getPatterns().length; tile++) {
            assertSame(manager.getCurrentLevel().getPattern(nativeBase + tile),
                    sheet.getPatterns()[tile], "wrong level-art binding at rock tile " + tile);
        }
    }
}
