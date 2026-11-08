package com.openggf.sprites.render;

import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.art.SpriteArtSet;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPlayerResidentBanksExternalPublication {
    private static final int BASE = 0x780;
    private static Pattern color(int value) {
        var pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) value);
        return pattern;
    }
    private static SpriteArtSet art() {
        return new SpriteArtSet(new Pattern[]{color(3), color(5)},
                List.of(new SpriteMappingFrame(List.of()), new SpriteMappingFrame(List.of())),
                List.of(new SpriteDplcFrame(List.of(new TileLoadRequest(0, 1))),
                        new SpriteDplcFrame(List.of(new TileLoadRequest(1, 1)))),
                0, BASE, 1, 1, null, null);
    }
    @Test void claimedBanksRejectOrdinaryInitializationRuntimeDecisionsAndImplicitDrawLoads() {
        var graphics = mock(GraphicsManager.class); when(graphics.isGlInitialized()).thenReturn(true);
        var art = art(); var renderer = new PlayerSpriteRenderer(art, graphics);
        PlayerResidentBanks.claim(renderer);
        renderer.ensureCached(graphics);
        verify(graphics, never()).cachePatternTexture(any(), anyInt());
        PlayerResidentBanks.load(renderer, art.dplcFrames().getFirst(), art.artTiles());
        assertEquals(3, PlayerResidentBanks.capture(renderer).tiles()[0].getPixel(0, 0));
        clearInvocations(graphics);
        renderer.applyRuntimeArtUpdate(1, new DynamicArtLifecycleService.ArtUpdate(true, 1,
                List.of(new TileLoadRequest(1, 1))));
        renderer.applyRuntimeArtUpdate(1, new DynamicArtLifecycleService.ArtUpdate(true, -1, List.of()));
        SpritePresentation.withSubject(graphics, SpritePresentation.Subject.WORLD,
                () -> renderer.drawFrame(1, 0, 0, false, false));
        verify(graphics, never()).cachePatternTexture(any(), anyInt());
        verify(graphics, never()).updatePatternTexture(any(), anyInt());
        assertEquals(3, PlayerResidentBanks.capture(renderer).tiles()[0].getPixel(0, 0));
        assertEquals(-1, renderer.residentLastFrame(), "ordinary notices cannot mutate the owner's frame latch");
        PlayerResidentBanks.restore(renderer, new PlayerResidentBanks.Snapshot(new Pattern[]{color(7)}, 9));
        verify(graphics).updatePatternTexture(any(), eq(BASE));
        assertEquals(7, PlayerResidentBanks.capture(renderer).tiles()[0].getPixel(0, 0));
        assertEquals(9, renderer.residentLastFrame());
    }
    @Test void unclaimedRendererStillPublishesOrdinaryRuntimeAndDrawDplcExactly() {
        var graphics = mock(GraphicsManager.class); when(graphics.isGlInitialized()).thenReturn(true);
        var renderer = new PlayerSpriteRenderer(art(), graphics);
        renderer.ensureCached(graphics);
        verify(graphics).cachePatternTexture(any(), eq(BASE));
        clearInvocations(graphics);
        renderer.applyRuntimeArtUpdate(1, new DynamicArtLifecycleService.ArtUpdate(true, 1,
                List.of(new TileLoadRequest(1, 1))));
        verify(graphics).updatePatternTexture(any(), eq(BASE));
        assertEquals(5, PlayerResidentBanks.capture(renderer).tiles()[0].getPixel(0, 0));
        assertEquals(1, renderer.residentLastFrame());
        clearInvocations(graphics);
        SpritePresentation.withSubject(graphics, SpritePresentation.Subject.WORLD,
                () -> renderer.drawFrame(0, 0, 0, false, false));
        verify(graphics).updatePatternTexture(any(), eq(BASE));
        assertEquals(3, PlayerResidentBanks.capture(renderer).tiles()[0].getPixel(0, 0));
    }
}
