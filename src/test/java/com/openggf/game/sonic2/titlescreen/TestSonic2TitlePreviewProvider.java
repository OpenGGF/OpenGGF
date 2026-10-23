package com.openggf.game.sonic2.titlescreen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2TitlePreviewProvider {

    @Test
    void sonic2PreviewTimelineStartsWithCharacterEntryAndSettlesToFinalFrame() {
        assertEquals(5, Sonic2TitlePreviewProvider.sonic2PreviewSonicFrameAt(0));
        assertEquals(80 + Sonic2TitlePreviewProvider.sonic2PreviewCharacterYOffset(),
                Sonic2TitlePreviewProvider.sonic2PreviewSonicYAt(0));

        assertEquals(5, Sonic2TitlePreviewProvider.sonic2PreviewSonicFrameAt(4));
        assertEquals(64 + Sonic2TitlePreviewProvider.sonic2PreviewCharacterYOffset(),
                Sonic2TitlePreviewProvider.sonic2PreviewSonicYAt(4));

        assertEquals(0x12, Sonic2TitlePreviewProvider.sonic2PreviewSonicFrameAt(160));
        assertEquals(24 + Sonic2TitlePreviewProvider.sonic2PreviewCharacterYOffset(),
                Sonic2TitlePreviewProvider.sonic2PreviewSonicYAt(160));
    }

    @Test
    void sonic2PreviewUsesCurvedLowerLogoOcclusion() {
        assertEquals(104, Sonic2TitlePreviewProvider.sonic2LogoOcclusionStartPixel(159));
        assertEquals(120, Sonic2TitlePreviewProvider.sonic2LogoOcclusionStartPixel(0));
        assertEquals(120, Sonic2TitlePreviewProvider.sonic2LogoOcclusionStartPixel(319));
    }

    @Test
    void sonic2PreviewBodyClipFollowsCurvedLogoBoundary() {
        assertTrue(Sonic2TitlePreviewProvider.sonic2PreviewBodyPixelVisible(159, 103));
        assertFalse(Sonic2TitlePreviewProvider.sonic2PreviewBodyPixelVisible(159, 104));
        assertTrue(Sonic2TitlePreviewProvider.sonic2PreviewBodyPixelVisible(0, 119));
        assertFalse(Sonic2TitlePreviewProvider.sonic2PreviewBodyPixelVisible(0, 120));
    }

    @Test
    void sonic2PreviewOffsetsCharactersDownFromTitleOrigin() {
        assertEquals(8, Sonic2TitlePreviewProvider.sonic2PreviewCharacterYOffset());
    }

    @Test
    void sonic2PreviewDrawsHandsBehindLogoOcclusion() {
        assertTrue(Sonic2TitlePreviewProvider.sonic2PreviewDrawsHandsBehindLogoOcclusion());
    }

    @Test
    void sonic2PreviewDoesNotHideHandsBehindFullLogo() {
        assertFalse(Sonic2TitlePreviewProvider.sonic2PreviewDrawsHandsBehindFullLogoText());
    }

    @Test
    void sonic2PreviewStampsCopyrightTextIntoLogoMap() {
        int[] logo = new int[40 * 28];
        int[] words = new int[11];
        for (int i = 0; i < words.length; i++) {
            words[i] = 0x680 + i;
        }
        int[] stamped = Sonic2TitlePreviewProvider.withSonic2CopyrightText(logo, words);
        assertEquals(0, logo[26 * 40 + 28], "input map is not mutated");
        assertEquals(0x680, stamped[26 * 40 + 28]);
        assertEquals(0x680 + 10, stamped[26 * 40 + 38]);
        assertEquals(0, stamped[26 * 40 + 27]);
    }
}
