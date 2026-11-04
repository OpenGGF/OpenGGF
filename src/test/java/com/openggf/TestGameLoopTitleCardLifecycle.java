package com.openggf;

import com.openggf.game.GameMode;
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
import static org.mockito.Mockito.doAnswer;
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
    void resumedDisplayClearArmsTitleWaitBeforeAnyOrdinaryTitleService() {
        var level = mock(LevelManager.class);
        var frame = mock(PlcLifecycleFrame.class);
        var title = mock(TitleCardProvider.class);
        var gameplay = mock(GameplayModeContext.class);
        var audio = mock(Runnable.class);
        var lifecycle = new GameLoopTitleCardLifecycle(mock(GameLoopTitleCardLifecycle.Host.class));
        try (var nativeLoad = org.mockito.Mockito.mockStatic(com.openggf.level.NativeLevelLoadAccess.class)) {
            nativeLoad.when(() -> com.openggf.level.NativeLevelLoadAccess.beginTitleScan(level))
                    .thenReturn(new com.openggf.level.NativeLevelLoadAccess.TitleScan(true, false, false));
            org.junit.jupiter.api.Assertions.assertFalse(lifecycle.update(false, title, frame, gameplay,
                    level, null, null, null, PostTitleCardDestination.LEVEL,
                    audio, audio, phase -> org.junit.jupiter.api.Assertions.fail("title service before Wait_VSync"),
                    (name, step) -> org.junit.jupiter.api.Assertions.fail("object pass before Wait_VSync")));
            nativeLoad.verify(() -> com.openggf.level.NativeLevelLoadAccess.beginTitleScan(level));
            org.mockito.Mockito.verifyNoInteractions(frame, title, gameplay, audio);
        }
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true, true", "true, false", "false, true", "false, false"})
    void releaseResetsNativeLevelClockWithoutRewindingVblank(boolean nativeLevelClock, boolean freshRelease) {
        // Level (s2.asm:4772-4773) clears the word before the title.
        // Level_MainLoop (s2.asm:5088-5095) increments it before the object pass.
        // Exercise both release edges with nonzero locked-pass debt and a
        // provider outside that policy.
        TitleCardProvider title = nativeLevelClock
                ? mock(TitleCardProvider.class, org.mockito.Mockito.withSettings().extraInterfaces(
                        com.openggf.game.internal.LockedTitleCardLevelStepGate.class,
                        com.openggf.game.titlecard.TitleCardLoopTail.class))
                : mock(TitleCardProvider.class, org.mockito.Mockito.withSettings().extraInterfaces(
                        com.openggf.game.titlecard.TitleCardLoopTail.class));
        when(title.shouldCompleteFreshLevelTransitionBoundary()).thenReturn(freshRelease);
        when(title.shouldReleaseControl()).thenReturn(true);

        LevelManager level = mock(LevelManager.class);
        SpriteManager sprites = mock(SpriteManager.class);
        org.mockito.Mockito.doCallRealMethod().when(level).setFrameCounter(org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.doCallRealMethod().when(level).getFrameCounter();
        org.mockito.Mockito.doCallRealMethod().when(level).advanceLevelFrameCounter();
        org.mockito.Mockito.doCallRealMethod().when(sprites).setFrameCounter(org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.doCallRealMethod().when(sprites).getFrameCounter();
        level.setFrameCounter(23);
        sprites.setFrameCounter(23);
        var objects = mock(com.openggf.level.objects.ObjectManager.class);
        when(level.getObjectManager()).thenReturn(objects);
        when(objects.getVblaCounter()).thenReturn(0xABCD);
        GameModule module = mock(GameModule.class);
        WorldSession world = mock(WorldSession.class);
        when(world.getGameModule()).thenReturn(module);
        GameplayModeContext gameplay = mock(GameplayModeContext.class);
        when(gameplay.getWorldSession()).thenReturn(world);
        when(gameplay.hardwareTiming()).thenReturn(mock(HardwareTimingService.class));
        var host = mock(GameLoopTitleCardLifecycle.Host.class);
        when(host.currentMode()).thenReturn(GameMode.TITLE_CARD);
        when(host.level()).thenReturn(level);
        when(host.sprites()).thenReturn(sprites);
        when(host.module()).thenReturn(module);
        when(sprites.getAllSprites()).thenReturn(List.of());
        var lifecycle = new GameLoopTitleCardLifecycle(host);
        assertTrue(lifecycle.update(false, title, mock(PlcLifecycleFrame.class), gameplay,
                level, sprites, mock(com.openggf.camera.Camera.class), null,
                PostTitleCardDestination.LEVEL, () -> {}, () -> {}, phase -> {},
                (name, step) -> step.run()));

        assertEquals(LevelFrameResult.SETUP_ONLY, lifecycle.releaseResult());
        assertEquals(0, sprites.getFrameCounter());
        assertEquals(nativeLevelClock ? 0 : 23, level.getFrameCounter());
        level.advanceLevelFrameCounter();
        assertEquals(nativeLevelClock ? 1 : 24, level.getFrameCounter(),
                "the next main-loop body must read the first increment, without title debt");
        assertEquals(0xABCD, objects.getVblaCounter());
        org.mockito.Mockito.verify(objects, org.mockito.Mockito.never())
                .initVblaCounter(org.mockito.ArgumentMatchers.anyInt());
    }

    private static DynamicArtLifecycleService releasePrelude(int tailRows) {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService(
                new com.openggf.game.sonic1.Sonic1GameModule().getGameService(com.openggf.game.resources.PlayerArtTransferPlan.class));
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
        Runnable prelude = () -> service.observePlayerDplc("sonic", 1,
                new SpriteDplcFrame(List.of(new TileLoadRequest(0, 3))));

        var host = mock(GameLoopTitleCardLifecycle.Host.class);
        when(host.currentMode()).thenReturn(GameMode.TITLE_CARD);
        when(host.level()).thenReturn(level); when(host.sprites()).thenReturn(sprites);
        when(host.module()).thenReturn(module);
        when(sprites.getAllSprites()).thenReturn(List.of());
        doAnswer(c -> { prelude.run(); return null; }).when(sprites).update(null);
        var lifecycle = new GameLoopTitleCardLifecycle(host);
        assertTrue(lifecycle.update(false, title,
                mock(PlcLifecycleFrame.class), gameplay, level, sprites, null, null,
                PostTitleCardDestination.LEVEL,
                () -> {}, () -> {}, phase -> {}, (name, step) -> step.run()));
        assertEquals(LevelFrameResult.SETUP_ONLY, lifecycle.releaseResult());
        return service;
    }
}
