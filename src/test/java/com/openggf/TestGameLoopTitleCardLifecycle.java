package com.openggf;

import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.OscillationManager;
import com.openggf.game.OscillationSnapshot;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.LevelManager;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated
class TestGameLoopTitleCardLifecycle {
    private OscillationSnapshot oscillationBefore;

    @BeforeEach
    void captureOscillation() {
        oscillationBefore = OscillationManager.snapshot();
    }

    @AfterEach
    void restoreOscillation() {
        OscillationManager.restore(oscillationBefore);
    }

    @Test
    void s1ReleasePreludeTransferBelongsToLoadTailAndSurvivesRewind() {
        // Level_Delay's four VBlanks plus PalFadeIn_Alt's twenty-two precede
        // Level_MainLoop (S1 sonic.asm:2956-2966), after Level_LoadObj stages art.
        DynamicArtLifecycleService service = releasePrelude(4 + 22);
        var beforeAdmission = service.capture();
        service.settlePendingPlayerPreparationBeforeLevelMainLoop(1000);
        var expectedGap = service.gapEdges();
        assertEquals(List.of("submitted", "completed"), expectedGap.stream()
                .map(edge -> edge.phase()).toList());
        assertEquals(List.of(974, 974), expectedGap.stream()
                .map(edge -> edge.movieLogicalFrame()).toList());
        assertEquals(List.of(1, 1), expectedGap.stream()
                .map(edge -> edge.mappingFrame()).toList());

        service.openComparisonSegment();
        service.serviceProductionVBlank();
        assertTrue(service.publishRow(0, false).edges().isEmpty(),
                "a load-tail transfer must not be republished on gameplay row zero");

        service.restore(beforeAdmission);
        service.settlePendingPlayerPreparationBeforeLevelMainLoop(1000);
        assertEquals(expectedGap, service.gapEdges());
        service.openComparisonSegment();
        service.serviceProductionVBlank();
        assertTrue(service.publishRow(0, false).edges().isEmpty());
    }

    @Test
    void zeroTailRetainsOrdinaryProductionVblankOwnership() {
        DynamicArtLifecycleService service = releasePrelude(0);
        service.settlePendingPlayerPreparationBeforeLevelMainLoop(1000);
        assertTrue(service.gapEdges().isEmpty());
        service.openComparisonSegment();
        service.serviceProductionVBlank();
        assertEquals(List.of("submitted", "completed"),
                service.publishRow(0, false).edges().stream()
                        .map(edge -> edge.phase()).toList());
    }

    private static DynamicArtLifecycleService releasePrelude(int tailRows) {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        service.beginRun();
        service.setMovieLogicalFrame(900);
        GameModule module = mock(GameModule.class);
        LevelInitProfile profile = mock(LevelInitProfile.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        when(profile.preLevelMainLoopDelayFrames()).thenReturn(tailRows);
        WorldSession world = mock(WorldSession.class);
        when(world.getGameModule()).thenReturn(module);
        GameplayModeContext gameplay = mock(GameplayModeContext.class);
        when(gameplay.getWorldSession()).thenReturn(world);
        when(gameplay.hardwareTiming()).thenReturn(mock(HardwareTimingService.class));
        when(gameplay.dynamicArtLifecycle()).thenReturn(service);
        TitleCardProvider title = mock(TitleCardProvider.class);
        when(title.shouldCompleteFreshLevelTransitionBoundary()).thenReturn(true);
        when(title.levelObjectPreludePassesAtRelease()).thenReturn(1);
        when(title.shouldRunPlayerPreludeAtRelease()).thenReturn(true);
        LevelManager level = mock(LevelManager.class);
        SpriteManager sprites = mock(SpriteManager.class);
        Runnable prelude = () -> service.observePlayerDplc(GameId.S1, "sonic", 1,
                new SpriteDplcFrame(List.of(new TileLoadRequest(0, 3))));

        assertTrue(GameLoopTitleCardLifecycle.update(false, title,
                mock(PlcLifecycleFrame.class), gameplay, level, sprites, null, null,
                prelude, PostTitleCardDestination.LEVEL, () -> {}, result -> {},
                () -> {}, () -> {}, phase -> {}, (name, step) -> step.run()));
        return service;
    }
}
