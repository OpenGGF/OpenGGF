package com.openggf.mods.code;

import com.openggf.game.ObjectArtOverlayProvider;
import com.openggf.game.ObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TestObjectArtPatternCountContracts {

    @Test
    void emptyProviderReportsNoRegularPatterns() {
        assertEquals(0, new EmptyObjectArtProvider().getRegularPatternCount());
    }

    @Test
    void overlayCountsBaseAndRegisteredSheetsWithoutLoadingOrCaching() throws Exception {
        ObjectArtProvider base = mock(ObjectArtProvider.class);
        when(base.getRegularPatternCount()).thenReturn(4);
        Map<String, ObjectSpriteSheet> sheets = new LinkedHashMap<>();
        sheets.put("first", sheetWithPatterns(2));
        sheets.put("second", sheetWithPatterns(3));
        ObjectArtOverlayProvider overlay = new ObjectArtOverlayProvider(base, sheets);

        assertEquals(9, overlay.getRegularPatternCount());

        verify(base).getRegularPatternCount();
        verify(base, never()).loadArtForZone(0);
        verify(base, never()).ensurePatternsCached(null, 0);
        verifyNoMoreInteractions(base);
    }

    @Test
    void overlayPreservesFailClosedCountForLegacyBaseProvider() {
        ObjectArtProvider legacy = mock(ObjectArtProvider.class, CALLS_REAL_METHODS);
        ObjectArtOverlayProvider overlay = new ObjectArtOverlayProvider(legacy, Map.of());

        assertThrows(UnsupportedOperationException.class, overlay::getRegularPatternCount);
    }

    @Test
    void overlayRejectsNegativeBaseCountBeforeAddingRegisteredSheets() {
        ObjectArtProvider base = mock(ObjectArtProvider.class);
        when(base.getRegularPatternCount()).thenReturn(-1);
        ObjectArtOverlayProvider overlay = new ObjectArtOverlayProvider(
                base, Map.of("masking-sheet", sheetWithPatterns(1)));

        assertThrows(IllegalStateException.class, overlay::getRegularPatternCount);
    }

    @Test
    void overlayCountMatchesBaseAndSheetCacheEnd() {
        ObjectArtProvider base = mock(ObjectArtProvider.class);
        when(base.getRegularPatternCount()).thenReturn(4);
        when(base.ensurePatternsCached(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation ->
                ((Integer) invocation.getArgument(1)) + 4);
        Map<String, ObjectSpriteSheet> sheets = new LinkedHashMap<>();
        sheets.put("first", sheetWithPatterns(2));
        sheets.put("second", sheetWithPatterns(3));
        Map<String, PatternSpriteRenderer> renderers = Map.of(
                "first", mock(PatternSpriteRenderer.class),
                "second", mock(PatternSpriteRenderer.class));
        ObjectArtOverlayProvider overlay = new ObjectArtOverlayProvider(base, sheets) {
            @Override public PatternSpriteRenderer getRenderer(String key) {
                return renderers.get(key);
            }
        };
        GraphicsManager graphics = mock(GraphicsManager.class);
        int patternBase = 100;

        int cacheEnd = overlay.ensurePatternsCached(graphics, patternBase);

        assertEquals(patternBase + overlay.getRegularPatternCount(), cacheEnd);
        verify(renderers.get("first")).ensurePatternsCached(graphics, patternBase + 4);
        verify(renderers.get("second")).ensurePatternsCached(graphics, patternBase + 6);
    }

    @Test
    void overlayPreservesFailClosedCountOverflow() {
        ObjectArtProvider base = mock(ObjectArtProvider.class);
        when(base.getRegularPatternCount()).thenReturn(Integer.MAX_VALUE);
        ObjectArtOverlayProvider overlay = new ObjectArtOverlayProvider(
                base, Map.of("overflow", sheetWithPatterns(1)));

        assertThrows(ArithmeticException.class, overlay::getRegularPatternCount);
    }

    private static ObjectSpriteSheet sheetWithPatterns(int count) {
        return new ObjectSpriteSheet(new Pattern[count], List.of(), 0, 1);
    }
}
