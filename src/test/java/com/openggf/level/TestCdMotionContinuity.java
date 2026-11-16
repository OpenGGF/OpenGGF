package com.openggf.level;

import com.openggf.game.*;
import com.openggf.game.internal.LevelMotionPresentation;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.scroll.CdScrollHandlerProvider;
import com.openggf.game.soniccd.zones.ppz.PpzScrollHandler;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.scroll.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CD ordinary scenes use the shared live motion path; frozen rebuild remains a distinct native operation. */
class TestCdMotionContinuity {
    @ParameterizedTest @EnumSource(CdZone.class)
    void ordinaryProviderHandlersSupplyPhasesAndTheProfileOptsIn(CdZone zone) {
        var module = new SonicCdGameModule();
        var provider = new CdScrollHandlerProvider();
        provider.initForZone(zone.ordinal(), 0, 256, 128);
        var handler = provider.getHandler(zone.ordinal());
        assertInstanceOf(ScrollOutputRebuilder.class, handler);
        assertInstanceOf(ScrollProjectionSource.class, handler);
        assertInstanceOf(LevelMotionPresentation.class, module.getLevelInitProfile());

        var level = mock(LevelManager.class);
        level.level = mock(Level.class);
        level.camera = mock(com.openggf.camera.Camera.class);
        level.graphicsManager = mock(GraphicsManager.class);
        level.parallaxManager = mock(ParallaxManager.class);
        level.gameModule = mock(GameModule.class);
        when(level.gameModule.getLevelInitProfile()).thenReturn(module.getLevelInitProfile());
        var bonus = mock(BonusStageProvider.class);
        when(bonus.getActiveType()).thenReturn(BonusStageType.NONE);
        when(level.gameModule.getBonusStageProvider()).thenReturn(bonus);
        when(level.gameModule.getSpecialStageProvider()).thenReturn(mock(SpecialStageProvider.class));
        var renderer = new LevelRenderer(level);
        assertTrue(renderer.supportsMotion(), zone.name());
        // A profile without the explicit capability must still be rejected.
        when(level.gameModule.getLevelInitProfile()).thenReturn(mock(LevelInitProfile.class));
        assertFalse(renderer.supportsMotion());
    }

    @ParameterizedTest @EnumSource(CdPeriod.class)
    void ppzVerticalPanAndFrozenRebuildHaveDistinctExactSemantics(CdPeriod period) {
        var handler = new PpzScrollHandler(period, new PpzScrollHandler.PlayerView() {
            public int centreX() { return 256; }
            public int xSpeed() { return 0; }
            public boolean on3dRamp() { return false; }
        });
        int[] nativeWords = new int[224];
        handler.init(0, 256, 128);
        handler.update(nativeWords, 256, 128, 1, 0);
        short beforeY = handler.getVscrollFactorBG();
        assertEquals(period == CdPeriod.PRESENT ? 128 : 16, beforeY);
        var frozenState = handler.captureRewindState();
        int[] frozenWords = new int[224];
        handler.rebuildOutput(frozenWords, 256, 136, 1, 0);
        assertArrayEquals(nativeWords, frozenWords);
        assertEquals(beforeY, handler.getVscrollFactorBG(), "Rebuild must not advance scroll RAM for a new camera Y");
        handler.update(nativeWords, 256, 136, 2, 0);
        assertEquals(beforeY + (period == CdPeriod.PRESENT ? 8 : 1), handler.getVscrollFactorBG());
        assertNotEquals(beforeY, handler.getVscrollFactorBG(), "Frozen output is not a vertical-pan projection");
        handler.restoreRewindState(frozenState);
        handler.rebuildOutput(nativeWords, 256, 128, 1, 0);
        assertArrayEquals(frozenWords, nativeWords, "Restored native state reproduces the complete raster");
        assertEquals(beforeY, handler.getVscrollFactorBG());
    }
}
