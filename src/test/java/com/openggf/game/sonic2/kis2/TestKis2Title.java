package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestKis2Title {
    @Test void chipTitleMappingsAddressUploadedArtAndUseKnucklesPalette() {
        RomByteReader dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        var rom = LockOnAddressSpace.tierTwo(dump.window(0, 0x200000), dump.window(0x200000, 0x100000), dump);
        var data = new Kis2TitleData(rom);
        assertEquals(40 * 28, data.foreground.length);
        assertEquals(8, data.knuckles.size());
        assertEquals(4, data.stars.size());
        assertEquals(3, data.text.size());
        assertEquals(1, data.banner.size());
        for (var frames : java.util.List.of(data.knuckles, data.stars, data.emblem, data.text, data.banner)) {
            for (var frame : frames) for (var piece : frame.pieces()) {
                assertTrue(piece.tileIndex() >= 0);
                assertTrue(piece.tileIndex() + piece.widthTiles() * piece.heightTiles() <= data.patterns.length);
            }
        }
        var expected = new com.openggf.level.Palette();
        expected.fromSegaFormat(dump.slice(0x3106A8, 32));
        for (int i = 0; i < 16; i++) assertEquals(expected.colors[i].r, data.palettes[0].colors[i].r);
    }
    @Test void naturalIntroRaisesHandLowersEmblemThenBouncesBanner() {
        var state = new Kis2TitleAnimation();
        for (int i = 0; i < 128; i++) state.tick(false);
        assertFalse(state.handVisible());
        for (int i = 128; i < 270; i++) state.tick(false);
        assertTrue(state.handVisible());
        assertEquals(-24, state.scroll);
        assertFalse(state.complete);
        while (state.frame < 288) state.tick(false);
        assertTrue(state.complete);
        assertFalse(state.bannerVisible);
        while (state.frame < 400) state.tick(false);
        assertTrue(state.bannerVisible);
        assertTrue(state.bannerSettled);
        assertEquals(36, state.bannerY);
        assertEquals(42, state.y);
        assertEquals(90, state.handY);
        assertEquals(4, state.bodyFrame);
    }
    @Test void skippingPreservesRomDifferentBannerPositionAndStopsPendingGestures() {
        var state = new Kis2TitleAnimation();
        for (int i = 0; i < 180; i++) state.tick(false);
        state.tick(true);
        assertTrue(state.complete);
        assertEquals(32, state.bannerY);
        assertEquals(116, state.x);
        assertEquals(42, state.y);
        for (int i = 0; i < 400; i++) state.tick(false);
        assertEquals(32, state.bannerY);
        assertEquals(90, state.handY);
    }
}
