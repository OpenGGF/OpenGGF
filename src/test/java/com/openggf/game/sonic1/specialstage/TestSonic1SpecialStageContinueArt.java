package com.openggf.game.sonic1.specialstage;

import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1SpecialStageContinueArt {
    @Test
    void resultsPlcContainsTheContinueTextAndBothFootTapPoses() throws Exception {
        ObjectSpriteSheet sheet = Sonic1SpecialStageResultsScreen.loadContinueSheet(TestEnvironment.currentRom());
        assertEquals(3, sheet.getFrameCount());
        SpriteMappingFrame down = sheet.getFrame(0);
        SpriteMappingFrame up = sheet.getFrame(1);
        SpriteMappingFrame text = sheet.getFrame(2);
        assertEquals(List.of(0, 8, 16), text.pieces().stream().map(SpriteMappingPiece::tileIndex).toList());
        assertEquals(new SpriteMappingPiece(64, -8, 2, 3, 18, false, false, 1, false),
                down.pieces().getLast());
        assertEquals(new SpriteMappingPiece(64, -8, 2, 3, 24, false, false, 1, false),
                up.pieces().getLast());
        for (int frame = 0; frame < sheet.getFrameCount(); frame++) {
            for (SpriteMappingPiece piece : sheet.getFrame(frame).pieces()) {
                for (int tile = piece.tileIndex(); tile < piece.tileIndex() + piece.widthTiles() * piece.heightTiles(); tile++) {
                    assertTrue(tile < sheet.getPatterns().length, "every mapping tile belongs to the PLC art bank");
                    Pattern pattern = sheet.getPatterns()[tile];
                    boolean drawn = false;
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            drawn |= pattern.getPixel(x, y) != 0;
                        }
                    }
                    assertTrue(drawn, "continue tile " + tile + " must contain ROM pixels");
                }
            }
        }
    }
}
