package com.openggf.level;

import com.openggf.editor.EditorStockObjectPalette;
import com.openggf.editor.render.EditorObjectPreviewCache;
import com.openggf.game.ObjectArtProvider;
import com.openggf.level.metadata.ObjectPreview;
import com.openggf.level.metadata.ObjectPreviewProvider;
import com.openggf.level.objects.*;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.opengl.GL11.*;

/** Captures the source-art ownership and GL cache contracts without any gameplay factory. */
class TestLevelObjectArt {
    static class ArtLevel extends AbstractLevel {
        ArtLevel() {
            super(0);
            objects = List.of(new ObjectSpawn(1, 2, 1, 0, 0, false, 2));
            palettes = new Palette[] {new Palette(), new Palette(), new Palette(), new Palette()};
            palettes[0].setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
        }
    }
    static class Registry implements ObjectRegistry, ObjectPreviewProvider {
        public ObjectInstance create(ObjectSpawn spawn) { throw new AssertionError("Preview may not create gameplay objects"); }
        public void reportCoverage(List<ObjectSpawn> spawns) { }
        public String getPrimaryName(int id) { return "Test"; }
        public ObjectPreview objectPreview(ObjectSpawn spawn) { return ObjectPreview.frame("test", 0); }
    }
    static ObjectArtProvider art(Pattern tile) {
        var provider = mock(ObjectArtProvider.class);
        when(provider.getRendererKeys()).thenReturn(List.of("test"));
        when(provider.getSheet("test")).thenReturn(new ObjectSpriteSheet(new Pattern[] {tile}, List.of(new SpriteMappingFrame(
                List.of(new SpriteMappingPiece(-3, -5, 1, 1, 0, false, false, 0)))), 0, 0));
        return provider;
    }
    @Test void catalogueDetachesPixelsAndSurvivesEditorCopyOwnership() throws Exception {
        var tile = new Pattern(); tile.setPixel(0, 0, (byte) 1);
        var source = new ArtLevel(); LevelObjectArt.publish(source, art(tile));
        var copy = LevelObjectArt.inherit(new ArtLevel(), source);
        tile.setPixel(0, 0, (byte) 2);
        assertEquals(java.util.Set.of(1), LevelObjectArt.sourceIds(copy));
        assertEquals(1, LevelObjectArt.sheets(copy).get("test").getPatterns()[0].getPixel(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> LevelObjectArt.sheets(copy).clear());
    }
    @Test void staticPalettePhaseAppliesOnlyAtNativeCopyAndPreservesAuthoredReopen() throws Exception {
        var source = new ArtLevel();
        var provider = mock(ObjectArtProvider.class, withSettings().extraInterfaces(com.openggf.level.metadata.ObjectArtPreviewSource.class));
        when(provider.getRendererKeys()).thenReturn(List.of());
        var phase = new Palette(); phase.setColor(2, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
        var extra = (com.openggf.level.metadata.ObjectArtPreviewSource) provider;
        when(extra.editorPreviewSheets(source)).thenReturn(java.util.Map.of());
        when(extra.editorPreviewPalettes(source)).thenReturn(java.util.Map.of(1, phase));
        LevelObjectArt.publish(source, provider);
        var authored = LevelObjectArt.initialize(new ArtLevel(), source);
        assertEquals(255, Byte.toUnsignedInt(authored.getPalette(1).getColor(2).g));
        assertEquals(0, Byte.toUnsignedInt(source.getPalette(1).getColor(2).g), "Native world is untouched");
        authored.getPalette(1).setColor(2, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
        LevelObjectArt.inherit(authored, source);
        assertEquals(255, Byte.toUnsignedInt(authored.getPalette(1).getColor(2).r), "Reopen attaches art without replacing saved colors");
    }
    @Test void explicitAuthoringLibraryBuildsDetachedArtWithoutReplacingNativeLevel() throws Exception {
        var source = new ArtLevel(); var detached = new ArtLevel();
        var provider = mock(ObjectArtProvider.class, withSettings().extraInterfaces(com.openggf.level.metadata.ObjectArtPreviewSource.class));
        when(provider.getRendererKeys()).thenReturn(List.of());
        var extra = (com.openggf.level.metadata.ObjectArtPreviewSource) provider;
        when(extra.editorAssetLibrary(source)).thenReturn(detached);
        LevelObjectArt.publish(source, provider);
        assertSame(detached, LevelObjectArt.authoringLibrary(source));
        verify(extra).editorAssetLibrary(source);
        verify(extra).editorPreviewSheets(detached);
        assertEquals(1, source.getObjects().size());
    }
    @Test void previewsReuseTexturesReflectPaletteEditsAndMirrorAroundNativeOrigin() throws Exception {
        try (var context = PresentationOffscreenTestContext.open(64, 64); var cache = new EditorObjectPreviewCache()) {
            var tile = new Pattern(); tile.setPixel(0, 0, (byte) 1);
            var level = new ArtLevel(); LevelObjectArt.publish(level, art(tile));
            var palette = new EditorStockObjectPalette(new Registry());
            cache.refresh(level, palette);
            var spawn = new ObjectSpawn(0, 0, 1, 0, 0, false, 0);
            var first = cache.sprite(spawn); assertNotNull(first);
            assertEquals(3, first.originX()); assertEquals(5, first.originY());
            assertSame(first, cache.sprite(spawn), "Unchanged pictures reuse their GL texture");
            var flipped = cache.sprite(new ObjectSpawn(0, 0, 1, 0, 3, false, 0));
            assertEquals(5, flipped.originX()); assertEquals(3, flipped.originY());
            int old = first.texture();
            level.getPalette(0).setColor(1, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
            cache.refresh(level, palette); assertFalse(glIsTexture(old));
            assertNotNull(cache.sprite(spawn));
        }
    }
}
