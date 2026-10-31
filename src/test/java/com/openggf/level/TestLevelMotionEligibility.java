package com.openggf.level;

import com.openggf.game.*;
import com.openggf.game.internal.LevelMotionPresentation;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelMotionEligibility {
    private static final class Audited implements LevelInitProfile, LevelMotionPresentation {
        public List<InitStep> levelLoadSteps(LevelLoadContext context) { return List.of(); }
        public List<InitStep> levelTeardownSteps() { return List.of(); }
        public List<InitStep> perTestResetSteps() { return List.of(); }
        public List<StaticFixup> postTeardownFixups() { return List.of(); }
    }
    @Test void relocationBeforeRendererAttachmentHasNoMotionHistoryToCut() {
        var level = mock(LevelManager.class);
        assertDoesNotThrow(() -> LevelMotionAccess.cut(level, MotionCut.CAMERA));
    }
    @Test void bonusStagesCannotInheritTheOrdinaryProfileCapability() {
        var level = mock(LevelManager.class); level.level = mock(Level.class);
        level.camera = mock(com.openggf.camera.Camera.class); level.graphicsManager = mock(GraphicsManager.class);
        level.parallaxManager = mock(ParallaxManager.class); level.gameModule = mock(GameModule.class);
        var bonus = mock(BonusStageProvider.class); var special = mock(SpecialStageProvider.class);
        when(level.gameModule.getLevelInitProfile()).thenReturn(new Audited());
        when(level.gameModule.getBonusStageProvider()).thenReturn(bonus);
        when(level.gameModule.getSpecialStageProvider()).thenReturn(special);
        when(bonus.getActiveType()).thenReturn(BonusStageType.NONE);
        var renderer = new LevelRenderer(level); assertTrue(renderer.supportsMotion());
        for (var type : BonusStageType.values()) if (type != BonusStageType.NONE) {
            when(bonus.getActiveType()).thenReturn(type); assertFalse(renderer.supportsMotion(), type.toString());
        }
        when(bonus.getActiveType()).thenReturn(BonusStageType.NONE);
        when(special.isInitialized()).thenReturn(true); assertFalse(renderer.supportsMotion());
        when(special.isInitialized()).thenReturn(false); level.authoringView = true; assertFalse(renderer.supportsMotion());
        level.authoringView = false; when(level.hasContributedZoneRuntime()).thenReturn(true); assertFalse(renderer.supportsMotion());
        when(level.hasContributedZoneRuntime()).thenReturn(false);
        when(level.gameModule.getLevelInitProfile()).thenReturn(mock(LevelInitProfile.class)); assertFalse(renderer.supportsMotion());
    }
}
