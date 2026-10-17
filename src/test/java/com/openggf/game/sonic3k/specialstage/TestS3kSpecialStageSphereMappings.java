package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Map_SStageSphere_/word_A48A..word_A502: repeated pointers are runtime geometry. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSpecialStageSphereMappings {
    private static final int MAP = 0xA464;
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(ints = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
    void everyAdmittedDepthUsesItsActualRomMappingPointer(int frame) throws Exception {
        var rom = GameServices.rom().getRom();
        assertEquals(0x26, rom.read16BitAddr(MAP), "19-word native frame table");
        int map = MAP + rom.read16BitAddr(MAP + frame * 2L);
        assertEquals(1, rom.read16BitAddr(map));
        int y = (byte) rom.readByte(map + 2);
        int size = rom.readByte(map + 3) & 255;
        int pattern = rom.read16BitAddr(map + 4);
        int x = (short) rom.read16BitAddr(map + 6);
        int width = ((size >>> 2) & 3) + 1, height = (size & 3) + 1;
        int[][] cells = {{CELL_BLUE,2},{CELL_TOUCHED,2},{CELL_RED,0},{CELL_BUMPER,1},{CELL_SPRING,3}};
        var draw = Sonic3kSpecialStageRenderer.class.getDeclaredMethod("renderPerspectiveSprite",
                int.class, int.class, int.class, int.class, int.class);
        draw.setAccessible(true);
        for (int viewport : new int[]{320,800}) for (int[] cell : cells) {
            var graphics = new RecordingGraphics();
            var renderer = new Sonic3kSpecialStageRenderer(graphics);
            renderer.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(viewport));
            renderer.setSpherePatternBase(0x4000);
            renderer.setSphereMappingData(rom.readBytes(MAP, 166));
            draw.invoke(renderer, cell[0], 160, 64, frame, 0);
            var expected = new ArrayList<Tile>();
            for (int col = 0; col < width; col++) for (int row = 0; row < height; row++)
                expected.add(new Tile(0x4000 + (pattern & 0x7FF) + col * height + row,
                        viewport / 2 + x + col * 8, 64 + y + row * 8, cell[1]));
            assertEquals(expected, graphics.tiles, "frame=" + frame + " cell=" + cell[0]
                    + " width=" + viewport);
        }
    }
    private record Tile(int pattern, int x, int y, int palette) { }
    private static final class RecordingGraphics extends GraphicsManager {
        final List<Tile> tiles = new ArrayList<>();
        @Override public void renderPatternWithId(int pattern, PatternDesc descriptor, int x, int y) {
            tiles.add(new Tile(pattern, x, y, descriptor.getPaletteIndex()));
        }
    }
}
