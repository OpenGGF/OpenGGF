package com.openggf.graphics;

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
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
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


