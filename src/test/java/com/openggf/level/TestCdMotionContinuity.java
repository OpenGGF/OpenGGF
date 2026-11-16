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
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ordinary CD must not acquire interpolation through a shared renderer/profile change.
 * RebuildOutput reconciles frozen scroll RAM; it is not a camera projection API. */
class TestCdMotionContinuity {
    private static final class Audited implements LevelInitProfile, LevelMotionPresentation {
        public List<InitStep> levelLoadSteps(LevelLoadContext context) { return List.of(); }
        public List<InitStep> levelTeardownSteps() { return List.of(); }
        public List<InitStep> perTestResetSteps() { return List.of(); }
        public List<StaticFixup> postTeardownFixups() { return List.of(); }
    }

    @ParameterizedTest @EnumSource(CdZone.class)
    void ordinaryProviderHandlersRemainBehindTheActualProfileGate(CdZone zone) {
        var module = new SonicCdGameModule();
        var provider = new CdScrollHandlerProvider();
        provider.initForZone(zone.ordinal(), 0, 256, 128);
        var handler = provider.getHandler(zone.ordinal());
        assertInstanceOf(ScrollOutputRebuilder.class, handler);
        assertFalse(handler instanceof ScrollProjectionSource);
        assertFalse(module.getLevelInitProfile() instanceof LevelMotionPresentation);

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
        assertFalse(renderer.supportsMotion(), zone.name());
        // Negative control: the same ordinary scene would be eligible with an audited profile.
        when(level.gameModule.getLevelInitProfile()).thenReturn(new Audited());
        assertTrue(renderer.supportsMotion());
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
