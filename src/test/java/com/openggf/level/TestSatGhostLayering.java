package com.openggf.level;

import com.openggf.SpritePriorityLayerHook;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gameplay ghosts on the sprite-table (SAT) masking path: the layer hook runs there, its
 * entries sit behind their layer's players exactly as on the painter path, keep their ghost
 * style through the replay, are clipped by a sprite mask, and never arm one.
 */
class TestSatGhostLayering {
    private GraphicsManager graphics;

    @BeforeEach
    void setUp() {
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        graphics.endGhostRenderEffect(); // the shared instance may carry another test's effect
    }

    @AfterEach
    void tearDown() {
        graphics.resetState();
    }

    @Test
    void levelRendererPassesTheGhostHookOnBothSpritePaths() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelRenderer.java"));
        int sat = source.indexOf("graphicsManager.beginSpriteSatCollection();");
        int replay = source.indexOf("graphicsManager.endSpriteSatCollectionAndReplay();", sat);
        assertTrue(sat >= 0 && replay > sat);
        String satPath = source.substring(sat, replay);
        assertTrue(satPath.contains("drawPreparedUnifiedBucketWithPriority(bucket, graphicsManager, ghostLayerHook)"),
                "the SAT path must not drop the ghost layer hook");
        assertTrue(source.indexOf("drawPreparedUnifiedBucketWithPriority(bucket, graphicsManager, ghostLayerHook)",
                replay) > replay, "the painter path keeps it too");
    }

    @Test
    void satCollectionRunsTheHookBehindEachLayersPlayers() {
        SpriteManager manager = new SpriteManager();
        manager.clearAllSprites();
        AbstractPlayableSprite low = playable("low", false, false);
        AbstractPlayableSprite high = playable("high", true, true);
        manager.addSprite(low);
        manager.addSprite(high);
        long[] order = {0};
        doAnswer(invocation -> { order[0] = order[0] * 10 + 2; return null; }).when(low).draw();
        doAnswer(invocation -> { order[0] = order[0] * 10 + 4; return null; }).when(high).draw();
        SpritePriorityLayerHook hook = (bucket, highPriority) -> order[0] = order[0] * 10 + (highPriority ? 3 : 1);

        manager.prepareRenderBucketsForPass();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            manager.drawPreparedUnifiedBucketWithPriority(bucket, graphics, hook);
        }
        assertEquals(3412, order[0], "painter: each layer's ghosts are drawn first, behind its players");

        order[0] = 0;
        graphics.beginSpriteSatCollection();
        manager.prepareRenderBucketsForPass();
        for (int bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++) {
            graphics.setCurrentSpriteSatBucket(bucket);
            manager.drawPreparedUnifiedBucketWithPriority(bucket, graphics, hook);
        }
        graphics.endSpriteSatCollectionAndReplay();
        // SAT order is front to back: the exact reverse of the painter order above.
        assertEquals(2143, order[0], "SAT: each layer's ghosts follow its players, the same layering");
        manager.clearAllSprites();
    }

    @Test
    void ghostEntriesKeepTheirStyleThroughTheSatReplay() {
        var frame = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            graphics.beginSpriteSatCollection();
            graphics.setCurrentSpriteSatBucket(2);
            submit(0x100);
            graphics.beginGhostRenderEffect(0.5f, true);
            submit(0x200);
            graphics.endGhostRenderEffect();
            graphics.beginGhostRenderEffect(0.25f);
            submit(0x300);
            graphics.endGhostRenderEffect();
            graphics.endSpriteSatCollectionAndReplay();
        });
        var tiles = frame.tiles();
        SpritePresentation.Tile real = tile(tiles, 0x100);
        SpritePresentation.Tile coloured = tile(tiles, 0x200);
        SpritePresentation.Tile grey = tile(tiles, 0x300);
        assertFalse(real.ghost());
        assertTrue(coloured.ghost());
        assertTrue(coloured.ghostColour());
        assertEquals(0.5f, coloured.ghostAlpha());
        assertTrue(grey.ghost());
        assertFalse(grey.ghostColour());
        assertEquals(0.25f, grey.ghostAlpha());
        assertFalse(graphics.isGhostRenderEffectActive(), "the replay restores the caller's ghost state");
    }

    @Test
    void aMaskClipsLaterGhostsButAGhostNeverArmsIt() {
        SpriteSatEntry.Ghost style = new SpriteSatEntry.Ghost(1f, true);
        SpriteSatEntry earlierGhost = piece(0x100, 0, 0).withGhost(style);
        // The marker (which loc_1AE34 moves to X=1) sits on other lines, so on the companion's
        // lines only the ghost precedes the pair: the VDP would see no arming sprite there.
        SpriteSatEntry marker = piece(0x7C0, 0, 100);
        SpriteSatEntry companion = piece(0, 0, 0);
        SpriteSatEntry laterReal = piece(0x300, 0, 0);
        List<SpriteSatEntry> unarmed = SpriteSatMaskPostProcessor.process(
                List.of(earlierGhost, marker, companion, laterReal), true);
        assertEquals(List.of(0x100, 0x300), unarmed.stream().map(SpriteSatEntry::firstPatternIndex).toList());
        assertTrue(unarmed.stream().allMatch(entry -> entry.visibleTopY() == SpriteSatEntry.UNCLIPPED_TOP));

        // A real sprite arms it: the ghost after the pair loses those lines, as a player would.
        SpriteSatEntry earlierReal = piece(0x100, 0, 0);
        SpriteSatEntry laterGhost = piece(0x200, 0, 0).withGhost(style);
        List<SpriteSatEntry> armed = SpriteSatMaskPostProcessor.process(
                List.of(earlierReal, marker, companion, laterGhost), true);
        assertEquals(List.of(0x100), armed.stream().map(SpriteSatEntry::firstPatternIndex).toList());
    }

    private void submit(int pattern) {
        graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                0, 0, 1, 1, pattern, pattern, 0, false, false, false, false, 0, 1));
    }

    private static SpriteSatEntry piece(int pattern, int x, int y) {
        return SpriteSatEntry.of(x, y, 1, 1, pattern, pattern & 0x7FF, 0, false, false, false, false);
    }

    private static SpritePresentation.Tile tile(List<SpritePresentation.Tile> tiles, int pattern) {
        return tiles.stream().filter(tile -> tile.patternId() == pattern).findFirst().orElseThrow();
    }

    private static AbstractPlayableSprite playable(String code, boolean highPriority, boolean cpu) {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getCode()).thenReturn(code);
        when(sprite.getPriorityBucket()).thenReturn(2);
        when(sprite.isHighPriority()).thenReturn(highPriority);
        when(sprite.isCpuControlled()).thenReturn(cpu);
        return sprite;
    }
}
