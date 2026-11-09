package com.openggf.mods.ui;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSceneMenuChrome {
    @Test void creatorAndHostShareColourRolesAndPageDimensions() {
        assertEquals(MenuTheme.HEADER_H, com.openggf.game.MenuChrome.HEADER_H);
        assertEquals(MenuTheme.FOOTER_Y, com.openggf.game.MenuChrome.FOOTER_Y);
        assertRgb(MenuTheme.GOLD, com.openggf.game.MenuChrome.GOLD);
        assertRgb(MenuTheme.HEAD, com.openggf.game.MenuChrome.HEAD);
        assertRgb(MenuTheme.TEXT, com.openggf.game.MenuChrome.TEXT);
    }

    @Test void pageAndFooterUseTheActualWidescreenCanvas() {
        for (int width : new int[] {320, 400}) {
            SceneCanvas canvas = mock(SceneCanvas.class);
            when(canvas.width()).thenReturn(width);
            when(canvas.height()).thenReturn(224);
            when(canvas.textWidth(anyString())).thenAnswer(call -> NameFont.width(call.getArgument(0)));
            SceneMenuChrome.page(canvas, "Act practice");
            verify(canvas).fill(0, 0, width, MenuTheme.HEADER_H, MenuTheme.HEAD);
            var hints = List.of(SceneMenuChrome.hint("ENTER", "Play"), SceneMenuChrome.hint("ESC", "Back"));
            SceneMenuChrome.footer(canvas, hints);
            verify(canvas).fill(0, MenuTheme.FOOTER_Y, width, 19, MenuTheme.INK);
            int capX = width - MenuTheme.MARGIN - SceneMenuChrome.hintsWidth(hints);
            assertTrue(capX >= MenuTheme.MARGIN);
            verify(canvas).fill(capX, MenuTheme.FOOTER_Y + 4,
                    SceneMenuChrome.capWidth("ENTER"), 10, MenuTheme.CAP_FACE);
        }
    }

    private static void assertRgb(int colour, float[] host) {
        assertEquals(colour >>> 16 & 255, Math.round(host[0] * 255));
        assertEquals(colour >>> 8 & 255, Math.round(host[1] * 255));
        assertEquals(colour & 255, Math.round(host[2] * 255));
    }
}
