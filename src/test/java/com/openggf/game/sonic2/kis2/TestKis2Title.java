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
    @Test void maskingSpriteFollowsTheEmblemDrop() {
        var state = new Kis2TitleAnimation();
        for (int i = 0; i < 128; i++) state.tick(false);
        assertEquals(112, state.maskTop());
        while (state.frame < 400) state.tick(false);
        assertEquals(136, state.maskTop());
    }
    @Test void emblemTopLowPriorityStripCoversTheFinalPoseUnderAnOpaqueLogo() {
        RomByteReader dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        var rom = LockOnAddressSpace.tierTwo(dump.window(0, 0x200000), dump.window(0x200000, 0x100000), dump);
        var data = new Kis2TitleData(rom);
        var state = new Kis2TitleAnimation();
        while (state.frame < 400) state.tick(false);
        int emblemY = 104 - state.scroll;
        var desc = new com.openggf.level.PatternDesc();
        int strips = 0;
        for (var piece : data.emblem.get(0).pieces()) {
            if (piece.priority()) continue;
            strips++;
            int left = 160 + piece.xOffset(), right = left + piece.widthTiles() * 8;
            // The filler tile would show if the logo plane were transparent or low priority beneath it.
            for (int column = left / 8; column < right / 8; column++) {
                desc.set(data.foreground[(emblemY + state.scroll) / 8 * 40 + column]);
                assertTrue(desc.getPriority(), "logo column " + column);
                var tile = data.patterns[desc.getPatternIndex()];
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) assertNotEquals(0, tile.getPixel(x, y));
            }
        }
        assertEquals(2, strips);
        // The final hand reaches that strip, so drawing the filler on top shows the tile over the logo.
        boolean reaches = false;
        for (var piece : data.knuckles.get(state.handFrame).pieces()) {
            int top = state.handY + piece.yOffset(), bottom = top + piece.heightTiles() * 8;
            reaches |= top < emblemY + 8 && bottom > emblemY;
        }
        assertTrue(reaches);
    }
}
