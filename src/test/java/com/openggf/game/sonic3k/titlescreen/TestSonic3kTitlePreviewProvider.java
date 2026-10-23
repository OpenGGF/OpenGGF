package com.openggf.game.sonic3k.titlescreen;

import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kTitlePreviewProvider {

    @Test
    void sonic3kPreviewStartsAtFinalSceneFingerWag() throws Exception {
        assertEquals(4, invokeIntHelper("s3kPreviewFingerFrameAt", 0));
        assertEquals(4, invokeIntHelper("s3kPreviewFingerFrameAt", 35));
        assertEquals(0, invokeIntHelper("s3kPreviewFingerFrameAt", 36));
        assertEquals(4, invokeIntHelper("s3kPreviewFingerFrameAt", 42));
        assertEquals(1, invokeIntHelper("s3kPreviewFingerFrameAt", 48));
    }

    @Test
    void sonic3kPreviewFingerOverlayIsLoweredToMatchFinalTitlePose() {
        assertEquals(76, Sonic3kTitlePreviewProvider.sonic3kPreviewFingerY());
    }

    @Test
    void sonic3kPreviewIncludesTitleWinkAnimation() throws Exception {
        assertEquals(2, invokeIntHelper("s3kPreviewWinkFrameAt", 0));
        assertEquals(3, invokeIntHelper("s3kPreviewWinkFrameAt", 2));
        assertEquals(2, invokeIntHelper("s3kPreviewWinkFrameAt", 10));
        assertEquals(4, invokeIntHelper("s3kPreviewWinkFrameAt", 16));
        assertEquals(2, invokeIntHelper("s3kPreviewWinkFrameAt", 168));
    }

    @Test
    void sonic3kPreviewWinkOverlayIsLoweredAgainstScaledFinalTitlePose() {
        assertEquals(56, Sonic3kTitlePreviewProvider.sonic3kPreviewWinkY());
    }

    @Test
    void sonic3kPreviewUsesNativeCanvasWithoutResampling() {
        assertEquals(320, Sonic3kTitlePreviewProvider.sonic3kPreviewScaledWidth());
        assertEquals(224, Sonic3kTitlePreviewProvider.sonic3kPreviewScaledHeight());
    }

    @Test
    void sonic3kPreviewUsesSettledPlaneAVerticalScroll() {
        assertEquals(16, Sonic3kTitlePreviewProvider.sonic3kPreviewVScroll());
        assertEquals(76, Sonic3kTitlePreviewProvider.sonic3kPreviewFingerY());
        assertEquals(56, Sonic3kTitlePreviewProvider.sonic3kPreviewWinkY());
    }

    @Test
    void sonic3kPreviewOmitsInteractiveMenuSelection() {
        assertFalse(Sonic3kTitlePreviewProvider.sonic3kPreviewDrawsMenuSelection());
    }

    @Test
    void sonic3kPreviewUsesSettledTitleBannerPosition() {
        assertEquals(84, Sonic3kTitlePreviewProvider.sonic3kPreviewBannerY());
    }

    @Test
    void sonic3kPreviewIncludesCopyrightInMasterComposition() {
        assertTrue(Sonic3kTitlePreviewProvider.sonic3kPreviewDrawsCopyright());
    }

    @Test
    void sonic3kPreviewComposesTrademarkAndCopyrightPixels() throws Exception {
        Pattern[] patterns = new Pattern[0x800];
        Pattern ink = new Pattern();
        ink.setPixel(0, 0, (byte) 1);
        var trademark = Sonic3kTitleScreenMappings.createBannerFrames().get(1).pieces().get(0);
        var copyright = Sonic3kTitleScreenMappings.createCopyrightFrame().get(0).pieces().get(0);
        patterns[trademark.tileIndex()] = ink;
        patterns[copyright.tileIndex()] = ink;
        Palette[] palettes = { palette(0), palette(0), palette(0), palette(0, 0xFF_FF_FF) };
        Method compose = Sonic3kTitlePreviewProvider.class.getDeclaredMethod("composeSonic3kFinalPreview",
                Image.class, Pattern[].class, Palette[].class, int.class, int.class);
        compose.setAccessible(true);
        Image blank = new Image(320, 224, new byte[320 * 224 * 4]);

        Image image = (Image) compose.invoke(
                null, blank, patterns, palettes, -1, -1);

        assertPixel(image, 252, 104, 255, 255, 255, 255);
        // Copyright fits between the game selector and navigation hints without covering the logo.
        assertPixel(image, 216, 200, 255, 255, 255, 255);
    }

    private static Palette palette(int... colors) {
        Palette palette = new Palette();
        for (int i = 0; i < colors.length; i++) {
            int rgb = colors[i];
            palette.setColor(i, new Palette.Color(
                    (byte) ((rgb >> 16) & 0xFF),
                    (byte) ((rgb >> 8) & 0xFF),
                    (byte) (rgb & 0xFF)));
        }
        return palette;
    }

    private static int invokeIntHelper(String methodName, int value) throws Exception {
        Method method = Sonic3kTitlePreviewProvider.class.getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, value);
    }

    private static void assertPixel(Image image,
                                    int x,
                                    int y,
                                    int r,
                                    int g,
                                    int b,
                                    int a) {
        int offset = ((y * image.width()) + x) * 4;
        byte[] rgba = image.rgba();
        assertEquals(r, rgba[offset] & 0xFF);
        assertEquals(g, rgba[offset + 1] & 0xFF);
        assertEquals(b, rgba[offset + 2] & 0xFF);
        assertEquals(a, rgba[offset + 3] & 0xFF);
    }
}
