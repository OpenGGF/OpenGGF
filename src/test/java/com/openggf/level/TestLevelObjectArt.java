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
    @Test void nativeEditorEntryRetainsCollisionSemanticsAndNativeBoundsWithoutFlatProjectConversion() throws Exception {
        var source = new ArtLevel();
        var library = new ArtLevel() {
            @Override public int resolveCollisionBlockIndex(int block, int x, int y) { return block + x + y; }
            @Override public int getMaxX() { return 0x3000; }
        };
        library.objects = List.of();
        var provider = mock(ObjectArtProvider.class, withSettings().extraInterfaces(com.openggf.level.metadata.ObjectArtPreviewSource.class));
        when(provider.getRendererKeys()).thenReturn(List.of());
        var extra = (com.openggf.level.metadata.ObjectArtPreviewSource) provider;
        when(extra.editorPreviewSheets(any())).thenReturn(java.util.Map.of());
        when(extra.editorPreviewPalettes(any())).thenReturn(java.util.Map.of());
        when(extra.editorAssetLibrary(source)).thenReturn(library);
        LevelObjectArt.publish(source, provider);
        var level = com.openggf.editor.EditorNativeLevelView.prepare(source, provider);
        assertEquals(library.getObjects(), level.getObjects(), "Initial authoring uses complete native ROM data");
        assertEquals(1, source.getObjects().size(), "Preparing an editor view does not mutate the gameplay level");
        assertEquals(12, level.resolveCollisionBlockIndex(3, 4, 5));
        assertEquals(0x3000, level.getMaxX(), "Native bounds may extend beyond the portable-project map domain");
        level.getPalette(0).setColor(1, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
        assertSame(level, com.openggf.editor.EditorNativeLevelView.prepare(level, provider), "Returning from native playtest keeps edits");
        assertEquals(0, Byte.toUnsignedInt(source.getPalette(0).getColor(1).g));
    }
    static class ArtLevel extends AbstractLevel {
        ArtLevel() {
            super(0);
            objects = List.of(new ObjectSpawn(1, 2, 1, 0, 0, false, 2));
            rings = List.of(); map = new Map(2, 1, 1);
            patterns = new Pattern[]{new Pattern()}; patternCount = 1;
            chunks = new Chunk[]{new Chunk()}; chunkCount = 1;
            blocks = new Block[]{new Block(8)}; blockCount = 1;
            solidTiles = new SolidTile[]{new SolidTile(0, new byte[16], new byte[16], (byte) 0)}; solidTileCount = 1;
            palettes = new Palette[] {new Palette(), new Palette(), new Palette(), new Palette()};
            palettes[0].setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
        }
    }
    static class Registry implements ObjectRegistry, ObjectPreviewProvider {
        public ObjectInstance create(ObjectSpawn spawn) { throw new AssertionError("Preview may not create gameplay objects"); }
        public void reportCoverage(List<ObjectSpawn> spawns) { }
        public String getPrimaryName(int id) { return "Test"; }
        public ObjectPreview objectPreview(ObjectSpawn spawn) {
            return spawn.subtype() == 1 ? new ObjectPreview(List.of(new ObjectPreview.Part("test", 0, 0, 0, false, false, 1)))
                    : ObjectPreview.frame("test", 0);
        }
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
        var runtime = MutableLevel.snapshot(source);
        assertEquals(0, Byte.toUnsignedInt(runtime.getPalette(1).getColor(2).g), "Runtime snapshots preserve the current palette phase");
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
        assertSame(detached, LevelObjectArt.authoringLibrary(source, provider));
        verify(extra).editorAssetLibrary(source);
        verify(extra).editorPreviewSheets(detached);
        assertEquals(1, source.getObjects().size());
    }
    @Test void nativeS3kLibraryPreparationNeverReplacesAuthoredAssetsOrColors() throws Exception {
        var authored = mock(ModLevel.class);
        when(authored.getZoneIndex()).thenReturn(0);
        var provider = new com.openggf.game.sonic3k.Sonic3kObjectArtProvider();
        assertSame(authored, provider.editorAssetLibrary(authored), "Prepared creator assets are already complete");
        assertTrue(provider.editorPreviewSheets(authored).isEmpty());
        assertTrue(provider.editorPreviewPalettes(authored).isEmpty(), "Authored colors must not become AIZ's native phase");
    }
    @Test void previewsReuseTexturesReflectPaletteEditsAndMirrorAroundNativeOrigin() throws Exception {
        try (var context = PresentationOffscreenTestContext.open(64, 64); var cache = new EditorObjectPreviewCache()) {
            var tile = new Pattern(); tile.setPixel(0, 0, (byte) 1);
            var level = new ArtLevel(); LevelObjectArt.publish(level, art(tile));
            level.getPalette(1).setColor(1, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
            var palette = new EditorStockObjectPalette(new Registry());
            cache.refresh(level, palette);
            var spawn = new ObjectSpawn(0, 0, 1, 0, 0, false, 0);
            var first = cache.sprite(spawn); assertNotNull(first);
            assertEquals(3, first.originX()); assertEquals(5, first.originY());
            assertSame(first, cache.sprite(spawn), "Unchanged pictures reuse their GL texture");
            var alternate = cache.sprite(new ObjectSpawn(0, 0, 1, 1, 0, false, 0));
            assertNotNull(alternate); assertNotEquals(first.texture(), alternate.texture());
            int bound = glGetInteger(GL_TEXTURE_BINDING_2D);
            var pixels = org.lwjgl.system.MemoryUtil.memAlloc(alternate.width() * alternate.height() * 4);
            try {
                glBindTexture(GL_TEXTURE_2D, alternate.texture());
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                assertEquals(0, Byte.toUnsignedInt(pixels.get(0)));
                assertEquals(255, Byte.toUnsignedInt(pixels.get(1)), "Subtype palette override reaches the real texture");
            } finally {
                glBindTexture(GL_TEXTURE_2D, bound); org.lwjgl.system.MemoryUtil.memFree(pixels);
            }
            var flipped = cache.sprite(new ObjectSpawn(0, 0, 1, 0, 3, false, 0));
            assertEquals(5, flipped.originX()); assertEquals(3, flipped.originY());
            int old = first.texture();
            level.getPalette(0).setColor(1, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
            cache.refresh(level, palette); assertFalse(glIsTexture(old));
            assertNotNull(cache.sprite(spawn));
        }
    }
}
