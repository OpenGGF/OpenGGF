package com.openggf.level;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.RomDetectionService;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.PlayableSpriteAnimation;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestLevelManagerInitialPresentationPlcLifecycle {
    @Test
    void freshRuntimeArtHandoffIsAnExplicitLoadCauseNotAHeadlessPresentationFlag() {
        LevelInitProfile profile = mock(LevelInitProfile.class);
        TitleCardProvider titleCard = mock(TitleCardProvider.class);
        GameModule module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        when(module.getTitleCardProvider()).thenReturn(titleCard);
        GraphicsManager graphics = mock(GraphicsManager.class);
        when(graphics.isHeadlessMode()).thenReturn(true);
        LevelManager manager = managerFor(module, graphics);
        LevelLoadContext liveFreshLoad = new LevelLoadContext();
        liveFreshLoad.setShowTitleCard(true);
        liveFreshLoad.setTitleCardRequiredInHeadlessMode(false);
        liveFreshLoad.setQueueFreshLevelRuntimeArt(true);
        liveFreshLoad.setLevelIndex(7);

        manager.requestTitleCardIfNeeded(liveFreshLoad);

        verify(titleCard).requestFreshLevelRuntimeArtHandoff(7);
        assertFalse(manager.consumeTitleCardRequest(),
                "headless presentation is omitted while fresh runtime art remains armed");
    }

    @Test
    void completionRunsOncePerLevelEntryTransition() {
        LevelInitProfile profile = mock(LevelInitProfile.class);
        GameModule module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        LevelManager manager = managerFor(module);
        LevelLoadContext omittedPresentation = new LevelLoadContext();
        omittedPresentation.setShowTitleCard(false);

        manager.requestTitleCardIfNeeded(omittedPresentation);
        manager.completeInitialTitleCardPresentation();
        verify(profile).completeInitialPresentationPlcs();

        manager.requestTitleCardIfNeeded(omittedPresentation);
        manager.completeInitialTitleCardPresentation();
        verify(profile, times(2)).completeInitialPresentationPlcs();
    }

    @Test
    void omittedPresentationRunsLeadingObjectsBeforeVBlankForTheWholeTeam() {
        LevelInitProfile profile = mock(LevelInitProfile.class);
        when(profile.skippedPresentationPlayableFrames()).thenReturn(3);
        when(profile.skippedPresentationPlayableFramesBeforeFirstVBlank()).thenReturn(1);
        List<String> events = new ArrayList<>();
        LevelManager manager = observedTeamManager(profile, events);
        DynamicArtLifecycleService lifecycle = mock(DynamicArtLifecycleService.class);
        when(lifecycle.isRunActive()).thenReturn(true);
        doAnswer(invocation -> { events.add("vblank"); return null; })
                .when(lifecycle).serviceProductionVBlank();

        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::dynamicArtLifecycleOrNull).thenReturn(lifecycle);
            LevelLoadContext omitted = new LevelLoadContext();
            omitted.setShowTitleCard(false);
            manager.requestTitleCardIfNeeded(omitted);
            manager.completeInitialTitleCardPresentation();
        }

        // S2's leading RunObjects pass precedes WaitForVint; each later
        // wait drains production art before the main and follower animate.
        assertEquals(List.of("plcs", "main:0", "follower:0", "vblank",
                "main:1", "follower:1", "vblank", "main:2", "follower:2"), events);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void omittedPresentationStillAnimatesTheTeamWithoutAnActiveArtRun(boolean absent) {
        LevelInitProfile profile = mock(LevelInitProfile.class);
        when(profile.skippedPresentationPlayableFrames()).thenReturn(3);
        when(profile.skippedPresentationPlayableFramesBeforeFirstVBlank()).thenReturn(1);
        List<String> events = new ArrayList<>();
        LevelManager manager = observedTeamManager(profile, events);
        DynamicArtLifecycleService lifecycle = mock(DynamicArtLifecycleService.class);
        when(lifecycle.isRunActive()).thenReturn(false);

        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::dynamicArtLifecycleOrNull).thenReturn(absent ? null : lifecycle);
            LevelLoadContext omitted = new LevelLoadContext();
            omitted.setShowTitleCard(false);
            manager.requestTitleCardIfNeeded(omitted);
        }

        assertEquals(List.of("plcs", "main:0", "follower:0", "main:1", "follower:1",
                "main:2", "follower:2"), events);
        verify(lifecycle, never()).serviceProductionVBlank();
    }

    @Test
    void omittedPresentationWithNoPlayerPassesCompletesPlcsWithoutAnimationOrVBlank() {
        LevelInitProfile profile = mock(LevelInitProfile.class);
        when(profile.skippedPresentationPlayableFrames()).thenReturn(0);
        List<String> events = new ArrayList<>();
        LevelManager manager = observedTeamManager(profile, events);
        DynamicArtLifecycleService lifecycle = mock(DynamicArtLifecycleService.class);
        when(lifecycle.isRunActive()).thenReturn(true);

        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::dynamicArtLifecycleOrNull).thenReturn(lifecycle);
            LevelLoadContext omitted = new LevelLoadContext();
            omitted.setShowTitleCard(false);
            manager.requestTitleCardIfNeeded(omitted);
        }

        assertEquals(List.of("plcs"), events);
        verifyNoInteractions(lifecycle);
    }

    private static LevelManager observedTeamManager(LevelInitProfile profile, List<String> events) {
        GameModule module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        doAnswer(invocation -> { events.add("plcs"); return null; })
                .when(profile).completeInitialPresentationPlcs();
        SpriteManager sprites = mock(SpriteManager.class);
        AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite follower = mock(AbstractPlayableSprite.class);
        PlayableSpriteAnimation mainAnimation = mock(PlayableSpriteAnimation.class);
        PlayableSpriteAnimation followerAnimation = mock(PlayableSpriteAnimation.class);
        when(main.getAnimationManager()).thenReturn(mainAnimation);
        when(follower.getAnimationManager()).thenReturn(followerAnimation);
        when(sprites.getMainPlayable()).thenReturn(main);
        when(sprites.getSidekicks()).thenReturn(List.of(follower));
        doAnswer(invocation -> { events.add("main:" + invocation.getArgument(0)); return null; })
                .when(mainAnimation).update(anyInt());
        doAnswer(invocation -> { events.add("follower:" + invocation.getArgument(0)); return null; })
                .when(followerAnimation).update(anyInt());
        return managerFor(module, mock(GraphicsManager.class), sprites);
    }

    private static LevelManager managerFor(GameModule module) {
        return managerFor(module, mock(GraphicsManager.class));
    }

    private static LevelManager managerFor(
            GameModule module, GraphicsManager graphicsManager) {
        return managerFor(module, graphicsManager, mock(SpriteManager.class));
    }

    private static LevelManager managerFor(
            GameModule module, GraphicsManager graphicsManager, SpriteManager spriteManager) {
        SonicConfigurationService configuration = mock(SonicConfigurationService.class);
        when(configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn(320);
        when(configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn(224);
        EngineContext context = new EngineContext(
                configuration,
                graphicsManager,
                mock(AudioManager.class),
                mock(RomManager.class),
                mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class),
                mock(PlaybackDebugManager.class),
                mock(RomDetectionService.class),
                mock(CrossGameFeatureProvider.class));
        LevelManager manager = new LevelManager(
                mock(Camera.class),
                spriteManager,
                mock(ParallaxManager.class),
                mock(CollisionSystem.class),
                mock(WaterSystem.class),
                new GameStateManager(),
                context,
                new WorldSession(module));
        manager.gameModule = module;
        return manager;
    }
}
