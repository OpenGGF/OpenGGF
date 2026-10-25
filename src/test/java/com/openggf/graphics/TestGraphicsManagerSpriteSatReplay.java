package com.openggf.graphics;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineContext;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePieceRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestGraphicsManagerSpriteSatReplay {

    private GraphicsManager graphicsManager;

    @BeforeEach
    public void setUp() {
        GraphicsManager.destroyForReinit();
        TestEnvironment.resetEngineOnly();
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
        GraphicsManager.destroyForReinit();
    }

    @Test
    public void spriteSatReplay_replaysNativeBucketsFrontToBackWithFirstSatEntryFirst() throws Exception {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 1), 0);
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 2), 1);
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 3), 2);

        graphicsManager.beginSpriteSatCollection();
        graphicsManager.setCurrentSpriteSatBucket(2);
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                10, 20,
                1, 1,
                0, 0,
                1,
                false, false,
                false, true,
                SpriteMaskReplayRole.NORMAL,
                0, 1,
                0, 1,
                "first"));
        graphicsManager.setCurrentSpriteSatBucket(4);
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                20, 20,
                1, 1,
                1, 1,
                1,
                false, false,
                false, true,
                SpriteMaskReplayRole.NORMAL,
                0, 1,
                0, 1,
                "second"));
        graphicsManager.setCurrentSpriteSatBucket(2);
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                30, 20,
                1, 1,
                2, 2,
                2,
                false, false,
                false, true,
                SpriteMaskReplayRole.NORMAL,
                0, 1,
                0, 1,
                "third"));

        graphicsManager.endSpriteSatCollectionAndReplay();

        assertEquals(3, graphicsManager.commands.size());
        assertTrue(graphicsManager.commands.get(0) instanceof PatternRenderCommand);
        assertTrue(graphicsManager.commands.get(1) instanceof PatternRenderCommand);
        assertTrue(graphicsManager.commands.get(2) instanceof PatternRenderCommand);
        assertEquals(10f, getFloatField(graphicsManager.commands.get(0), "x"));
        assertEquals(30f, getFloatField(graphicsManager.commands.get(1), "x"));
        assertEquals(20f, getFloatField(graphicsManager.commands.get(2), "x"));
    }

    @Test
    public void spriteMaskReplay_cropsTilesToVisibleScanlines() throws Exception {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 1), 0);
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 2), 1);

        graphicsManager.beginSpriteSatCollection();
        graphicsManager.requestSpriteMask();
        // Map_SpriteMask-style pair covering scanlines 20..27.
        graphicsManager.submitSpriteSatControlEntry(108, 20, 1, 1, 0x7C0);
        graphicsManager.submitSpriteSatControlEntry(100, 20, 1, 1, 0);
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                100, 16,
                1, 2,
                0, 0,
                1,
                false, false,
                false, false,
                SpriteMaskReplayRole.NORMAL,
                0, 1,
                0, 2,
                "masked"));

        graphicsManager.endSpriteSatCollectionAndReplay();

        assertEquals(2, graphicsManager.commands.size());
        float[] heights = new float[2];
        for (int i = 0; i < 2; i++) {
            Object command = graphicsManager.commands.get(i);
            assertTrue(command instanceof PatternRenderCommand);
            assertTrue(getBooleanField(command, "textureCoordinatesResolved"));
            heights[i] = getFloatField(command, "height");
            float v0 = getFloatField(command, "v0");
            float v1 = getFloatField(command, "v1");
            // Four of eight texel rows: half the atlas entry's V span.
            PatternAtlas.Entry entry = graphicsManager.getPatternAtlas().getEntry(i);
            assertEquals((entry.v1() - entry.v0()) / 2f, Math.abs(v0 - v1), 1e-6f);
        }
        assertEquals(4f, heights[0]);
        assertEquals(4f, heights[1]);
    }

    @Test
    public void nativeBudgetAppliesWithoutRequestingSpriteMask() {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 1), 0);
        graphicsManager.beginSpriteSatCollection();
        for (int index = 0; index < 21; index++) {
            graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                    10 + index * 8, 20, 1, 1, 0, 0, 0,
                    false, false, false, false, SpriteMaskReplayRole.NORMAL,
                    0, 1, 0, 1, null));
        }
        graphicsManager.endSpriteSatCollectionAndReplay();
        assertEquals(20, graphicsManager.commands.size(), "H40 admits only the first twenty pieces on a line");
    }

    @Test
    void compatibilityIngressCopiesEveryFieldAndEachIndependentFlag() throws Exception {
        for (int flags = 0; flags < 16; flags++) {
            boolean h = (flags & 1) != 0, v = (flags & 2) != 0;
            boolean mapping = (flags & 4) != 0, global = (flags & 8) != 0;
            var legacy = new SpritePieceRenderer.PreparedPiece(-37, 41, 11, 13, 0x20005, 0x7C0, 14,
                    h, v, mapping, global, SpriteMaskReplayRole.PRE_MASK_FRONT, 2, 3, 5, 7, "original");
            graphicsManager.beginSpriteSatCollection();
            graphicsManager.setCurrentSpriteSatBucket(4);
            graphicsManager.submitSpriteSatPiece(legacy);
            var entries = collectedEntries();
            assertEquals(1, entries.size());
            var entry = entries.getFirst();
            assertEquals(new SpritePiece(-37, 41, 11, 13, 0x20005, 0x7C0, 14,
                    h, v, mapping, global, SpriteMaskReplayRole.PRE_MASK_FRONT, 2, 3, 5, 7, "original"), entry.piece());
            assertEquals(4, entry.priorityBucket());
            assertSame(SpritePresentation.Subject.WORLD, entry.presentationSubject());
            assertNull(entry.ghost());
            assertEquals(SpriteSatEntry.UNCLIPPED_TOP, entry.visibleTopY());
            assertEquals(SpriteSatEntry.UNCLIPPED_BOTTOM, entry.visibleBottomY());
        }
    }

    @Test
    void ingressOverridesOnlySourceAndCapturesSubjectAndGhostWithoutMutatingLegacy() throws Exception {
        var legacy = new SpritePieceRenderer.PreparedPiece(-37, 41, 11, 13, 0x20005, 0x7C0, 14,
                true, false, true, false, SpriteMaskReplayRole.PRE_MASK_FRONT, 2, 3, 5, 7, "original");
        var subject = new SpritePresentation.Subject("sonic", SpritePresentation.Part.BODY, true, 150);
        graphicsManager.beginSpriteSatCollection();
        graphicsManager.setCurrentSpriteSatDebugSource("override");
        graphicsManager.beginGhostRenderEffect(0.375f, true);
        SpritePresentation.withSubject(graphicsManager, subject, () -> graphicsManager.submitSpriteSatPiece(legacy));
        graphicsManager.endGhostRenderEffect();
        var entry = collectedEntries().getFirst();
        assertEquals(new SpritePiece(-37, 41, 11, 13, 0x20005, 0x7C0, 14,
                true, false, true, false, SpriteMaskReplayRole.PRE_MASK_FRONT, 2, 3, 5, 7, "override"), entry.piece());
        assertSame(subject, entry.presentationSubject());
        assertEquals(new SpriteSatEntry.Ghost(0.375f, true), entry.ghost());
        assertEquals("original", legacy.debugSource());
    }

    @Test
    void inactiveAndNullSubmissionsRemainNoOpsAndNullableLegacyFieldsAreAccepted() throws Exception {
        var legacy = new SpritePieceRenderer.PreparedPiece(1, 2, 0, 0, 3, 4, 5,
                false, true, false, true, null, 0, 0, 0, 0, null);
        graphicsManager.submitSpriteSatPiece(legacy);
        graphicsManager.submitSpriteSatPiece(null);
        assertTrue(collectedEntries().isEmpty());
        graphicsManager.beginSpriteSatCollection();
        graphicsManager.submitSpriteSatPiece(null);
        assertTrue(collectedEntries().isEmpty());
        graphicsManager.submitSpriteSatPiece(legacy);
        assertNull(collectedEntries().getFirst().piece().maskReplayRole());
        assertNull(collectedEntries().getFirst().piece().debugSource());
    }

    @Test
    void shadowTileRepublishKeepsColouredGhostAndAllImmutablePresentationFields() {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 15), 3);
        var subject = new SpritePresentation.Subject("shadow-copy", SpritePresentation.Part.BODY, false, 100);
        var tile = new SpritePresentation.Tile(SpritePresentation.Layer.PLAYER, 3, 3, true, false,
                false, 40, 50, 8, 8, true, 5, true, 0.375f, 2, 7, subject, true,
                SpriteSatEntry.PixelOperation.SHADOW_15);
        var frame = SpritePresentation.prepare(graphicsManager, 7, 11,
                () -> graphicsManager.renderShadowTile(tile, 13, 17, null, 1),
                descriptor -> { throw new AssertionError("Republishing must retain the immutable tile"); });
        assertEquals(List.of(new SpritePresentation.Tile(SpritePresentation.Layer.PLAYER, 3, 3, true, false,
                false, 46, 56, 8, 8, true, 5, true, 0.375f, 2, 7, subject, true,
                SpriteSatEntry.PixelOperation.SHADOW_15)), frame.tiles());
        assertEquals(SpritePresentation.patternSample(graphicsManager, 3), frame.patternVersions().get(3));
    }

    @Test
    void visualAttachmentsReplaceTheLeafWithoutLosingOrMutatingItsMetadata() throws Exception {
        var subject = new SpritePresentation.Subject("directed-leaf", SpritePresentation.Part.OBJECT_BODY, false);
        var piece = new SpritePieceRenderer.PreparedPiece(100, 80, 1, 1, 512, 512, 2,
                true, false, false, false, SpriteMaskReplayRole.PRE_MASK_FRONT, 0, 1, 0, 1, "leaf");
        graphicsManager.beginSpriteSatCollection();
        graphicsManager.setCurrentSpriteTileOcclusionPaletteMask(5);
        graphicsManager.beginGhostRenderEffect(.375f, true);
        SpritePresentation.withSubject(graphicsManager, subject, () -> graphicsManager.submitSpriteSatPiece(piece));
        graphicsManager.endGhostRenderEffect();
        var original = collectedEntries().getLast();
        ObjectVisualGraphicsAccess.attachLastVisualMask(graphicsManager, 0x17);
        ObjectVisualGraphicsAccess.clipLastSatTile(graphicsManager, 2, 6);
        var attached = collectedEntries().getLast();
        assertNotSame(original, attached);
        assertEquals(5, original.tileOcclusionPaletteMask());
        assertEquals(SpriteSatEntry.UNCLIPPED_TOP, original.visibleTopY());
        assertEquals(SpriteSatEntry.UNCLIPPED_BOTTOM, original.visibleBottomY());
        assertEquals(7, attached.tileOcclusionPaletteMask());
        assertEquals(82, attached.visibleTopY());
        assertEquals(86, attached.visibleBottomY());
        assertSame(original.piece(), attached.piece());
        assertSame(subject, attached.presentationSubject());
        assertEquals(original.ghost(), attached.ghost());
        assertEquals(original.pixelOperation(), attached.pixelOperation());
        assertNull(attached.replayFragments());

        var composite = attached.withReplayFragments(List.of(original))
                .withPixelOperation(SpriteSatEntry.PixelOperation.SHADOW_15);
        var copy = composite.withTileOcclusionPaletteMask(3).withVisibleScanlines(83, 85);
        assertEquals(composite.replayFragments(), copy.replayFragments());
        assertSame(composite.piece(), copy.piece());
        assertSame(subject, copy.presentationSubject());
        assertEquals(composite.ghost(), copy.ghost());
        assertEquals(SpriteSatEntry.PixelOperation.SHADOW_15, copy.pixelOperation());
        assertEquals(7, composite.tileOcclusionPaletteMask());
        assertEquals(82, composite.visibleTopY());
        assertEquals(86, composite.visibleBottomY());
    }

    @Test
    void noArgumentCollectionUsesExact350SpanAndReplaysTheWholeEdgeTile() throws Exception {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 1), 0);
        graphicsManager.setProjectionWidth(350);
        graphicsManager.beginSpriteSatCollection();
        Field field = GraphicsManager.class.getDeclaredField("spriteSatViewport");
        field.setAccessible(true);
        var viewport = (SpriteSatMaskPostProcessor.LogicalViewport) field.get(graphicsManager);
        assertEquals(350, viewport.width()); assertEquals(320, viewport.hardwareWidth());
        int edgeX = viewport.x() + 344, y = viewport.y() + 20;
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(edgeX, y, 1, 1, 0, 0, 0,
                false, false, false, false, SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, "edge"));
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(viewport.x() + 350, y, 1, 1, 0, 0, 0,
                false, false, false, false, SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, "outside"));
        graphicsManager.endSpriteSatCollectionAndReplay();
        assertEquals(1, graphicsManager.commands.size());
        assertEquals((float) edgeX, getFloatField(graphicsManager.commands.getFirst(), "x"));
        assertEquals(8f, getFloatField(graphicsManager.commands.getFirst(), "width"),
                "the 350px destination viewport, rather than source-tile clipping, supplies the last six pixels");
        assertFalse(graphicsManager.isSpriteSatCollectionActive()); assertTrue(collectedEntries().isEmpty());
    }

    @Test
    void engineAlignedH32EntryKeepsInnerCapacityInside350Presentation() throws Exception {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 1), 0);
        graphicsManager.setProjectionWidth(350);
        SpriteSatInternalAccess.beginCollection(graphicsManager, SpriteSatMaskPostProcessor.Raster.h32(47, 7, 100));
        for (int i = 0; i < 17; i++) graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                55, 20, 1, 1, 0, 0, 0, false, false, false, false, SpriteMaskReplayRole.NORMAL,
                0, 1, 0, 1, null));
        Field field = GraphicsManager.class.getDeclaredField("spriteSatViewport");
        field.setAccessible(true);
        var inner = (SpriteSatMaskPostProcessor.LogicalViewport) field.get(graphicsManager);
        assertEquals(47, inner.x()); assertEquals(7, inner.y()); assertEquals(256, inner.width());
        assertEquals(100, inner.height()); assertEquals(256, inner.hardwareWidth());
        graphicsManager.endSpriteSatCollectionAndReplay();
        assertEquals(16, graphicsManager.commands.size()); assertEquals(350, graphicsManager.getProjectionWidth());
        int previousCommands = graphicsManager.commands.size();
        SpriteSatInternalAccess.beginLogicalCollection(graphicsManager,
                SpriteSatMaskPostProcessor.LogicalViewport.h40(47, 7, 350, 100));
        for (int i = 0; i < 21; i++) graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                55, 20, 1, 1, 0, 0, 0, false, false, false, false, SpriteMaskReplayRole.NORMAL,
                0, 1, 0, 1, null));
        graphicsManager.endSpriteSatCollectionAndReplay();
        assertEquals(previousCommands + 20, graphicsManager.commands.size());
    }

    @Test
    void shadowRestartRejectionPreservesLogicalCollectionAndNestedFragmentLifetimes() throws Exception {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 15), 3);
        var viewport = SpriteSatMaskPostProcessor.LogicalViewport.h40(7, 11, 350, 224);
        graphicsManager.beginLogicalSpriteSatCollection(viewport);
        graphicsManager.requestSpriteMask();
        graphicsManager.setCurrentSpriteSatBucket(4);
        graphicsManager.setCurrentSpriteSatDebugSource("preserved-edge");
        graphicsManager.setCurrentSpriteTileOcclusionPaletteMask(5);
        var subject = new SpritePresentation.Subject("logical-shadow", SpritePresentation.Part.BODY, false, 100);
        var leaf = new SpritePieceRenderer.PreparedPiece(351, 31, 1, 1, 3, 3, 3,
                true, true, false, false, SpriteMaskReplayRole.PRE_MASK_FRONT, 0, 1, 0, 1, "original");
        Field fragmentsField = GraphicsManager.class.getDeclaredField("spriteSatFragments");
        fragmentsField.setAccessible(true);
        Field viewportField = GraphicsManager.class.getDeclaredField("spriteSatViewport");
        viewportField.setAccessible(true);
        graphicsManager.beginGhostRenderEffect(.375f, true);
        try {
            SpritePresentation.withSubject(graphicsManager, subject, () -> graphicsManager.submitShadowGroups(
                    List.of(SpriteSatEntry.of(351, 31, 1, 1, 3, 3, false, false, false, false)), () -> {
                        Object fragments = assertDoesNotThrow(() -> fragmentsField.get(graphicsManager));
                        assertNotNull(fragments);
                        graphicsManager.submitSpriteSatPiece(leaf);
                        assertThrows(IllegalStateException.class, graphicsManager::beginSpriteSatCollection);
                        assertThrows(IllegalStateException.class, () -> graphicsManager.beginSpriteSatCollection(null));
                        assertThrows(IllegalStateException.class, () -> graphicsManager.beginSpriteSatCollection(
                                SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224)));
                        assertThrows(IllegalStateException.class, () -> graphicsManager.beginLogicalSpriteSatCollection(null));
                        assertThrows(IllegalStateException.class, () -> SpriteSatInternalAccess.beginLogicalCollection(
                                graphicsManager, SpriteSatMaskPostProcessor.LogicalViewport.h40(0, 0, 350, 224)));
                        assertThrows(IllegalStateException.class, graphicsManager::endSpriteSatCollectionAndReplay);
                        assertThrows(IllegalStateException.class, () -> graphicsManager.submitSpriteSatGroups(List.of(), () -> fail("nested group ran")));
                        assertSame(fragments, assertDoesNotThrow(() -> fragmentsField.get(graphicsManager)));
                        assertSame(viewport, assertDoesNotThrow(() -> viewportField.get(graphicsManager)));
                        assertTrue(graphicsManager.isSpriteSatCollectionActive());
                        assertEquals(SpriteSatEntry.PixelOperation.SHADOW_15, graphicsManager.currentSpritePixelOperation);
                        graphicsManager.submitShadowPieces(() -> graphicsManager.submitSpriteSatPiece(leaf));
                        assertEquals(SpriteSatEntry.PixelOperation.SHADOW_15, graphicsManager.currentSpritePixelOperation);
                    }));
            assertEquals(SpriteSatEntry.PixelOperation.NORMAL, graphicsManager.currentSpritePixelOperation);
            assertTrue(getBooleanField(graphicsManager, "spriteMaskRequested"));
            assertNull(fragmentsField.get(graphicsManager));
            var group = collectedEntries().getFirst();
            assertEquals(SpriteSatEntry.PixelOperation.SHADOW_15, group.pixelOperation());
            assertEquals(2, group.replayFragments().size());
            for (var fragment : group.replayFragments()) {
                assertSame(subject, fragment.presentationSubject());
                assertEquals(new SpriteSatEntry.Ghost(.375f, true), fragment.ghost());
                assertEquals(4, fragment.priorityBucket()); assertEquals(5, fragment.tileOcclusionPaletteMask());
                assertEquals("preserved-edge", fragment.piece().debugSource());
                assertEquals(SpriteMaskReplayRole.PRE_MASK_FRONT, fragment.piece().maskReplayRole());
                assertEquals(SpriteSatEntry.PixelOperation.SHADOW_15, fragment.pixelOperation());
                assertTrue(fragment.piece().hFlip()); assertTrue(fragment.piece().vFlip());
            }
            assertThrows(UnsupportedOperationException.class, () -> group.replayFragments().clear());
            SpritePresentation.withSubject(graphicsManager, subject, () -> graphicsManager.submitSpriteSatPiece(leaf));
            var ordinary = collectedEntries().getLast();
            assertEquals(SpriteSatEntry.PixelOperation.NORMAL, ordinary.pixelOperation());
            assertSame(subject, ordinary.presentationSubject()); assertEquals(group.replayFragments().getFirst().ghost(), ordinary.ghost());
            assertEquals(4, ordinary.priorityBucket()); assertEquals("preserved-edge", ordinary.piece().debugSource());
        } finally { graphicsManager.endGhostRenderEffect(); }
        graphicsManager.cancelSpritePresentationCollection();
        assertFalse(graphicsManager.isSpriteSatCollectionActive()); assertFalse(getBooleanField(graphicsManager, "spriteMaskRequested"));
        assertTrue(collectedEntries().isEmpty()); assertNull(fragmentsField.get(graphicsManager));
    }

    @Test
    void wholeTileFallbackRetainsExpandedPaletteFlagsAndFloatGeometryAtIntegerExtremes() throws Exception {
        int id = PatternAtlasRange.OBJECTS.base();
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 1), id);
        for (int flags = 0; flags < 8; flags++) {
            boolean h = (flags & 1) != 0, v = (flags & 2) != 0, priority = (flags & 4) != 0;
            // Characterize the receiving command builder, not native SAT admission:
            // the viewport correctly rejects these offscreen integer-extreme coordinates.
            var replayCommands = graphicsManager.buildSpriteSatReplayCommands(List.of(SpriteSatEntry.of(
                    Integer.MAX_VALUE, Integer.MIN_VALUE, 1, 1, id, 0x123, 7,
                    h, v, priority, false)));
            try {
                assertEquals(1, replayCommands.size());
                Object command = replayCommands.getFirst();
                assertEquals(7, field(command, "paletteIndex"));
                assertEquals(h, field(command, "hFlip")); assertEquals(v, field(command, "vFlip"));
                assertEquals(priority, field(command, "piecePriority"));
                assertEquals(false, field(command, "textureCoordinatesResolved"));
                assertEquals((float) Integer.MAX_VALUE, getFloatField(command, "x"));
                assertEquals(224f - (float) Integer.MIN_VALUE - 8f, getFloatField(command, "y"),
                        "whole commands convert to float before subtracting; obtainRows has int overflow here");
            } finally {
                replayCommands.forEach(GLCommandable::discard); replayCommands.clear();
            }
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    @SuppressWarnings("unchecked")
    private List<SpriteSatEntry> collectedEntries() throws Exception {
        Field field = GraphicsManager.class.getDeclaredField("spriteSatEntries");
        field.setAccessible(true);
        return (List<SpriteSatEntry>) field.get(graphicsManager);
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static Pattern createSolidPattern(byte color) {
        Pattern pattern = new Pattern();
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                pattern.setPixel(x, y, color);
            }
        }
        return pattern;
    }

    private static float getFloatField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getFloat(target);
    }
}


