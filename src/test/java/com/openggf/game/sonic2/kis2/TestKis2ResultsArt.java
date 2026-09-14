package com.openggf.game.sonic2.kis2;

import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestKis2ResultsArt {
    @Test void chipTextAndFontReplaceSonicWithoutMutatingTheStockSheet() {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        Pattern[] tiles = new Pattern[0x200];
        for (int i = 0; i < tiles.length; i++) tiles[i] = new Pattern();
        var frames = new ArrayList<SpriteMappingFrame>();
        for (int i = 0; i < 15; i++) frames.add(new SpriteMappingFrame(List.of()));
        var stock = new ObjectSpriteSheet(tiles, frames, 0, 1);
        var result = new Kis2ResultsArt(dump).apply(stock);
        assertEquals(11, result.getFrame(0).pieces().size());
        assertEquals(-120, result.getFrame(0).pieces().getFirst().xOffset());
        assertEquals(0xA6, result.getFrame(0).pieces().getFirst().tileIndex());
        assertTrue(result.getFrame(0).pieces().getFirst().priority());
        assertSame(stock.getFrame(3), result.getFrame(3));
        assertEquals(0, stock.getFrame(0).pieces().size());
        Pattern expected = new Pattern();
        expected.fromSegaFormat(dump.slice(Kis2ResultsArt.FONT_K, 32));
        boolean hasInk = false;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            assertEquals(expected.getPixel(x,y), result.getPatterns()[0xA6].getPixel(x,y));
            assertEquals(0, stock.getPatterns()[0xA6].getPixel(x,y));
            hasInk |= expected.getPixel(x,y) != 0;
        }
        assertTrue(hasInk);
    }
}
