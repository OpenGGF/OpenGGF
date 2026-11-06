package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameRng;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.internal.NativeLevelInputOwner;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Admission witnesses for publication order, short-circuit ownership and live mode changes. */
class TestGameLoopPauseInputOwnership {
    private GameModule module;
    private NativeLevelInputOwner nativeOwner;
    private LevelInputOverlay overlay;
    private InputHandler input;
    private PlaybackDebugManager playback;
    private HostedRunController hosted;
    private Supplier<TitleScreenProvider> title;

    @BeforeEach @SuppressWarnings("unchecked") void open() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.resetPerTest();
        module = mock(GameModule.class);
        when(module.getRules()).thenReturn(GameRules.SONIC_3K);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S3K);
        when(module.createRuntimeArtCoordinator(any())).thenReturn(RuntimeArtCoordinator.NONE);
        SessionManager.openGameplaySession(module);
        nativeOwner = mock(NativeLevelInputOwner.class);
        overlay = mock(LevelInputOverlay.class);
        when(module.getGameService(NativeLevelInputOwner.class)).thenReturn(nativeOwner);
        when(module.getGameService(LevelInputOverlay.class)).thenReturn(overlay);
        input = mock(InputHandler.class);
        playback = mock(PlaybackDebugManager.class);
        hosted = mock(HostedRunController.class);
        title = mock(Supplier.class);
    }

    @AfterEach void close() {
        SessionManager.clear();
        TestEnvironment.resetAll();
    }

    @Test void recordedNativeStartSeesPublicationBeforeItReservesPauseWithoutAdvancingTheMovie() {
        when(playback.isDriving(GameMode.LEVEL)).thenReturn(true);
        AtomicBoolean published = new AtomicBoolean();
        when(nativeOwner.handleInput(input)).thenAnswer(call -> {
            assertTrue(published.get(), "native owner must see this row's published sample");
            return true;
        });
        var ownership = GameLoopPauseInput.resolveOwnership(() -> GameMode.LEVEL, input, playback,
                () -> published.set(true), hosted, title);
        assertTrue(ownership.ownsPause());
        assertFalse(ownership.titleOwnsEscape());
        assertSame(overlay, ownership.configurationOverlay());
        verify(overlay, never()).handleInput(any());
        verifyNoInteractions(hosted, title);
        verify(playback).isDriving(GameMode.LEVEL);
        verifyNoMoreInteractions(playback);
    }

    @Test void decliningNativeConsumerLeavesOverlayThenHostedOwnerInOrder() {
        when(playback.isDriving(GameMode.LEVEL)).thenReturn(true);
        when(hosted.ownsStart(GameMode.LEVEL)).thenReturn(true);
        Runnable publish = mock(Runnable.class);
        var ownership = GameLoopPauseInput.resolveOwnership(() -> GameMode.LEVEL, input, playback,
                publish, hosted, title);
        assertTrue(ownership.ownsPause());
        var order = inOrder(publish, nativeOwner, overlay, hosted);
        order.verify(publish).run();
        order.verify(nativeOwner).handleInput(input);
        order.verify(overlay).handleInput(input);
        order.verify(hosted).ownsStart(GameMode.LEVEL);
        verifyNoInteractions(title);
    }

    @Test void ordinaryLiveOverlayOwnsPauseWithoutPublishingOrDispatchingMovieInput() {
        when(overlay.handleInput(input)).thenReturn(true);
        Runnable publish = mock(Runnable.class);
        assertTrue(GameLoopPauseInput.resolveOwnership(() -> GameMode.LEVEL, input, playback,
                publish, hosted, title).ownsPause());
        verifyNoInteractions(publish, nativeOwner, hosted, title);
    }

    @Test void nativeModeChangeIsVisibleToSubsequentTitleAndConfigurationAdmission() {
        AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.LEVEL);
        when(playback.isDriving(GameMode.LEVEL)).thenReturn(true);
        when(nativeOwner.handleInput(input)).thenAnswer(call -> {
            mode.set(GameMode.TITLE_SCREEN);
            return false;
        });
        var provider = mock(TitleScreenProvider.class);
        when(title.get()).thenReturn(provider);
        when(provider.ownsEscapeInput()).thenReturn(true);
        var ownership = GameLoopPauseInput.resolveOwnership(mode::get, input, playback, () -> { }, hosted, title);
        assertTrue(ownership.titleOwnsEscape());
        assertSame(overlay, ownership.configurationOverlay());
        verify(hosted).ownsStart(GameMode.TITLE_SCREEN);
        verify(overlay, never()).handleInput(any());
    }
}
