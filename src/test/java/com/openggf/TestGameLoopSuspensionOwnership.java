package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.*;
import com.openggf.game.launch.SuspendedSettings;
import com.openggf.game.session.*;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.SuspensionTestAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Original-production temporal controls. Failures intentionally leave partial visit state. */
@Isolated
class TestGameLoopSuspensionOwnership {
    @TempDir Path directory;

    @AfterEach void releaseGlobals() {
        Engine.clearGlobalInstance();
        SessionManager.clear();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    private EngineContext context(SonicConfigurationService config, GraphicsManager graphics, AudioManager audio) {
        return new EngineContext(config, graphics, audio, mock(RomManager.class), mock(PerformanceProfiler.class),
                mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class), mock(RomDetectionService.class),
                mock(CrossGameFeatureProvider.class));
    }

    private Fixture engine() {
        var config = spy(SonicConfigurationService.createStandalone(directory));
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
        var graphics = mock(GraphicsManager.class);
        when(graphics.getFadeManager()).thenReturn(new FadeManager());
        var audio = mock(AudioManager.class);
        var engine = new Engine(context(config, graphics, audio));
        clearInvocations(audio);
        return new Fixture(engine, config, graphics, audio);
    }

    private GameLoop loop() {
        var fixture = engine();
        // Constructor execution on the mock keeps suspension's backreference on this same loop.
        return mock(GameLoop.class, withSettings().useConstructor(
                context(fixture.config, fixture.graphics, mock(AudioManager.class))).defaultAnswer(CALLS_REAL_METHODS));
    }

    private record Fixture(Engine engine, SonicConfigurationService config, GraphicsManager graphics,
                           AudioManager audio) { }

    private WorldSession world() {
        var world = mock(WorldSession.class, RETURNS_DEEP_STUBS);
        when(world.getGameModule().getGameId()).thenReturn(com.openggf.game.GameId.S2);
        when(world.getGameModule().getZoneRegistry().getZoneName(anyInt())).thenReturn("EMERALD HILL");
        return world;
    }

    private boolean prepare(Engine engine) {
        return call(engine, "prepareSuspend", new Class<?>[]{GameMode.class}, GameMode.LEVEL);
    }

    private SuspendedGame saved(Fixture fixture, AudioLogicalSnapshot audio) {
        return new SuspendedGame(GameMode.LEVEL, new SuspendedGame.Pending(audio,
                SuspendedGame.card("s2", GameMode.LEVEL, "Emerald Hill, Act 1")), null,
                SuspendedSettings.suspend(fixture.config), null);
    }

    @Test void coldConstructionAndHostBindingReadNoCollaborators() {
        var loop = mock(GameLoop.class);
        var owner = new GameLoopSuspension(loop);
        var first = mock(GameLoopSuspension.Host.class);
        var replacement = mock(GameLoopSuspension.Host.class);
        owner.setHost(first);
        owner.setHost(replacement);
        assertFalse(owner.parked());
        assertFalse(owner.preparing());
        assertNull(owner.parkedOwner());
        verifyNoInteractions(loop, first, replacement);
    }

    @ParameterizedTest @ValueSource(strings = {"no-host", "no-runtime", "not-ready", "preparing", "parked", "host-declines", "test-mode", "mod-scene"})
    void admissionFallsBackAtTheFirstExcludedBoundary(String boundary) {
        var loop = loop();
        var gameplay = mock(GameplayModeContext.class);
        when(gameplay.isGameplayRuntimeReady()).thenReturn(!boundary.equals("not-ready"));
        doReturn(boundary.equals("no-runtime") ? null : gameplay).when(loop).resolveGameplayModeContext();
        loop.changeGameModeWithoutRewindBoundary(boundary.equals("mod-scene") ? GameMode.MOD_SCENE : GameMode.LEVEL);
        var host = mock(GameLoopSuspension.Host.class);
        when(host.prepareSuspend(any())).thenReturn(!boundary.equals("host-declines"));
        loop.suspension.setHost(boundary.equals("no-host") ? null : host);
        if (boundary.equals("preparing")) set(loop.suspension, "preparing", true);
        if (boundary.equals("parked")) set(loop.suspension, "parked", true);
        if (boundary.equals("test-mode")) loop.configService.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        doNothing().when(loop).fadeOutTo(any());
        clearInvocations(loop, gameplay, host);

        loop.suspension.leave();

        verify(loop).fadeOutTo(any());
        if (boundary.equals("host-declines")) verify(host).prepareSuspend(GameMode.LEVEL);
        else verifyNoInteractions(host);
        if (boundary.equals("no-host")) verify(loop, never()).resolveGameplayModeContext();
        else verify(loop).resolveGameplayModeContext(); // even parked/preparing resolve first
        if (List.of("preparing", "parked", "no-runtime", "no-host").contains(boundary))
            verify(gameplay, never()).isGameplayRuntimeReady();
    }

    @Test void completionUsesTheThenCurrentHostAndConsumesPreparationBeforeCallingIt() {
        var loop = loop();
        var gameplay = mock(GameplayModeContext.class);
        when(gameplay.isGameplayRuntimeReady()).thenReturn(true);
        doReturn(gameplay).when(loop).resolveGameplayModeContext();
        var first = mock(GameLoopSuspension.Host.class);
        var replacement = mock(GameLoopSuspension.Host.class);
        when(first.prepareSuspend(GameMode.LEVEL)).thenReturn(true);
        loop.suspension.setHost(first);
        var modeReads = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (modeReads.incrementAndGet() == 2) loop.suspension.setHost(replacement);
            return GameMode.LEVEL;
        }).when(loop).getCurrentGameMode();
        loop.suspension.leave();
        assertEquals(2, modeReads.get(), "guard and argument are distinct virtual observations");
        verify(first).prepareSuspend(GameMode.LEVEL); // receiver selected BEFORE the argument replaces Host
        verify(replacement, never()).prepareSuspend(any());
        var failure = new IllegalStateException("completion");
        doAnswer(invocation -> { assertFalse(loop.suspension.preparing()); throw failure; }).when(replacement).suspend();
        assertSame(failure, assertThrows(IllegalStateException.class, loop.suspension::completePreparedSuspend));
        loop.suspension.completePreparedSuspend();
        verify(replacement).suspend();
        verify(first, never()).suspend();
    }

    @Test void parkUsesVirtualAdmissionModeButReturnsRawParkingModeAndReadsLiveListener() {
        var loop = loop();
        loop.changeGameModeWithoutRewindBoundary(GameMode.BONUS_STAGE);
        doReturn(GameMode.LEVEL).when(loop).getCurrentGameMode();
        var gameplay = mock(GameplayModeContext.class);
        when(gameplay.isGameplayRuntimeReady()).thenReturn(true);
        doReturn(gameplay).when(loop).resolveGameplayModeContext();
        var host = mock(GameLoopSuspension.Host.class);
        when(host.prepareSuspend(GameMode.LEVEL)).thenReturn(true);
        loop.suspension.setHost(host);
        loop.suspension.leave();
        var events = new ArrayList<String>();
        doReturn(true).when(loop).isUserPaused();
        doAnswer(invocation -> { events.add("unpause"); return null; }).when(loop).toggleUserPause();
        doAnswer(invocation -> {
            assertTrue(loop.suspension.parked());
            assertSame(gameplay, loop.suspension.parkedOwner());
            events.add("bindings");
            loop.gameModeChangeListener = (before, after) -> {
                assertEquals(GameMode.BONUS_STAGE, before);
                assertEquals(GameMode.MASTER_TITLE_SCREEN, after);
                events.add("replacement-listener");
            };
            return null;
        }).when(loop).refreshRuntimeBindings();
        loop.gameModeChangeListener = (before, after) -> fail("stale listener");
        assertEquals(GameMode.BONUS_STAGE, loop.suspension.park());
        assertEquals(List.of("unpause", "bindings", "replacement-listener"), events);
        verify(host).prepareSuspend(GameMode.LEVEL);
    }

    @ParameterizedTest @ValueSource(strings = {"bindings", "notification", "set-gameplay", "pause"})
    void parkAndUnparkRetainTheirExactPartialFailureState(String boundary) {
        var loop = loop();
        var gameplay = mock(GameplayModeContext.class);
        doReturn(gameplay).when(loop).resolveGameplayModeContext();
        doReturn(true).when(loop).isUserPaused();
        doNothing().when(loop).toggleUserPause();
        doNothing().when(loop).refreshRuntimeBindings();
        var failure = new IllegalStateException(boundary);
        if (boundary.equals("bindings")) doThrow(failure).when(loop).refreshRuntimeBindings();
        if (boundary.equals("notification")) loop.gameModeChangeListener = (old, next) -> { throw failure; };
        if (List.of("bindings", "notification").contains(boundary)) {
            assertSame(failure, assertThrows(IllegalStateException.class, loop.suspension::park));
            assertTrue(loop.suspension.parked());
            assertSame(gameplay, loop.suspension.parkedOwner());
            assertTrue(SuspensionTestAccess.<Boolean>get(loop.suspension, "userPausedWhenParked"));
            assertEquals(boundary.equals("notification") ? GameMode.MASTER_TITLE_SCREEN : GameMode.LEVEL,
                    loop.getCurrentGameMode());
            return;
        }
        loop.suspension.park();
        if (boundary.equals("set-gameplay")) doThrow(failure).when(loop).setGameplayMode(gameplay);
        else doThrow(failure).when(loop).toggleUserPause();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> loop.suspension.unpark(GameMode.LEVEL)));
        assertFalse(loop.suspension.parked());
        assertSame(boundary.equals("set-gameplay") ? gameplay : null, loop.suspension.parkedOwner());
        assertTrue(SuspensionTestAccess.<Boolean>get(loop.suspension, "userPausedWhenParked"));
        assertEquals(boundary.equals("set-gameplay") ? GameMode.MASTER_TITLE_SCREEN : GameMode.LEVEL,
                loop.getCurrentGameMode());
    }

    @Test void discardDropsIdentityBeforeRetirementAndDoesNotRetryAfterFailure() {
        var loop = loop();
        doNothing().when(loop).refreshRuntimeBindings();
        loop.suspension.park();
        var failure = new AssertionError("retire");
        doAnswer(invocation -> {
            assertFalse(loop.suspension.parked());
            assertNull(loop.suspension.parkedOwner());
            assertFalse(SuspensionTestAccess.<Boolean>get(loop.suspension, "userPausedWhenParked"));
            throw failure;
        }).when(loop).retireModScene();
        assertSame(failure, assertThrows(AssertionError.class, loop.suspension::discard));
        loop.suspension.discard();
        verify(loop).retireModScene();
        verify(loop, never()).tearDownGameplayForReturn();
    }

    @Test void resumeRoutingGuardsConsumptionAndUsesHostInstalledByTheRequest() {
        var loop = loop();
        var screen = mock(MasterTitleScreen.class);
        var fade = mock(FadeManager.class);
        doReturn(fade).when(loop).resolveFadeManager();
        loop.suspension.routeResume(screen); // absent Host does not even evaluate fade
        verifyNoInteractions(fade, screen);
        var first = mock(GameLoopSuspension.Host.class);
        var replacement = mock(GameLoopSuspension.Host.class);
        loop.suspension.setHost(first);
        when(fade.isActive()).thenReturn(true);
        loop.suspension.routeResume(screen);
        verifyNoInteractions(screen);
        when(fade.isActive()).thenReturn(false);
        when(screen.consumeResumeRequest()).thenReturn(false);
        loop.suspension.routeResume(screen);
        verify(first, never()).resume();
        when(screen.consumeResumeRequest()).thenAnswer(invocation -> { loop.suspension.setHost(replacement); return true; })
                .thenReturn(false);
        loop.suspension.routeResume(screen);
        loop.suspension.routeResume(screen);
        verify(replacement).resume();
        verify(first, never()).resume();
    }

    @ParameterizedTest @ValueSource(strings = {"audio-runtime", "zone-runtime", "audio-error", "fade"})
    void admissionCaptureCatchAndFadePublicationKeepSourceOrder(String boundary) {
        var fixture = engine();
        var world = world();
        var snapshot = mock(AudioLogicalSnapshot.class);
        when(fixture.audio.captureLogicalSnapshot()).thenReturn(snapshot);
        var failure = new IllegalStateException(boundary);
        var error = new AssertionError(boundary);
        if (boundary.equals("audio-runtime")) when(fixture.audio.captureLogicalSnapshot()).thenThrow(failure);
        if (boundary.equals("audio-error")) when(fixture.audio.captureLogicalSnapshot()).thenThrow(error);
        if (boundary.equals("zone-runtime")) when(world.getGameModule().getZoneRegistry().getZoneName(anyInt())).thenThrow(failure);
        if (boundary.equals("fade")) doAnswer(invocation -> {
            assertNotNull(get(fixture.engine, "pendingSuspend"));
            assertNull(get(fixture.engine, "pendingSuspendThumbnail"));
            assertTrue(SuspensionTestAccess.<Boolean>get(fixture.engine, "suspendThumbnailRequested"));
            assertFalse(fixture.engine.getGameLoop().suspension.preparing());
            throw failure;
        }).when(fixture.audio).fadeOutMusic();
        try (var sessions = mockStatic(SessionManager.class)) {
            sessions.when(SessionManager::getCurrentWorldSession).thenReturn(world);
            if (boundary.equals("audio-error")) {
                assertSame(error, assertThrows(AssertionError.class, () -> prepare(fixture.engine)));
                assertNull(get(fixture.engine, "pendingSuspend"));
                verify(fixture.audio, never()).fadeOutMusic();
            } else {
                if (boundary.equals("fade")) assertSame(failure, assertThrows(IllegalStateException.class, () -> prepare(fixture.engine)));
                else assertTrue(prepare(fixture.engine));
                SuspendedGame.Pending pending = get(fixture.engine, "pendingSuspend");
                assertSame(boundary.equals("audio-runtime") ? null : snapshot, pending.audio());
                assertEquals(boundary.equals("audio-runtime") || boundary.equals("zone-runtime") ? "" : "Emerald Hill, Act 1",
                        pending.card().location());
                var order = inOrder(fixture.audio);
                order.verify(fixture.audio).captureLogicalSnapshot();
                order.verify(fixture.audio).fadeOutMusic();
            }
            if (boundary.startsWith("audio-")) verify(world.getGameModule().getZoneRegistry(), never()).getZoneName(anyInt());
        }
    }

    @Test void engineAdmissionExclusionsAndMissingPendingHaveNoInventedRecovery() {
        var fixture = engine();
        var world = world();
        try (var sessions = mockStatic(SessionManager.class)) {
            assertFalse(prepare(fixture.engine)); // explicitly absent world in this scope
            sessions.when(SessionManager::getCurrentWorldSession).thenReturn(world);
            set(fixture.engine, "editorProjectPlaytestActive", true);
            assertFalse(prepare(fixture.engine));
            set(fixture.engine, "editorProjectPlaytestActive", false);
            set(fixture.engine, "suspendedGame", saved(fixture, null));
            assertFalse(prepare(fixture.engine));
            verifyNoInteractions(fixture.audio);
            var returns = new java.util.concurrent.atomic.AtomicInteger();
            fixture.engine.getGameLoop().setReturnToMasterTitleHandler(returns::incrementAndGet);
            call(fixture.engine, "suspendToMasterTitle");
            assertEquals(1, returns.get());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"success", "runtime", "resampling", "error"})
    void pictureRequestIsConsumedBeforeCaptureAndDimensionsAreReadAfterIt(String boundary) {
        var fixture = engine();
        set(fixture.engine, "suspendThumbnailRequested", true);
        set(fixture.engine, "viewportX", 7); set(fixture.engine, "viewportY", 11);
        set(fixture.engine, "viewportWidth", 6); set(fixture.engine, "viewportHeight", 4);
        var failure = new IllegalStateException("readback");
        var error = new AssertionError("readback");
        try (var capture = mockStatic(ScreenshotCapture.class)) {
            capture.when(() -> ScreenshotCapture.captureFramebufferRegion(7, 11, 6, 4)).thenAnswer(invocation -> {
                assertFalse(SuspensionTestAccess.<Boolean>get(fixture.engine, "suspendThumbnailRequested"));
                set(fixture.engine, "projectionWidth", 3.0); set(fixture.engine, "realHeight", 2.0);
                if (boundary.equals("runtime")) throw failure;
                if (boundary.equals("error")) throw error;
                if (boundary.equals("resampling")) {
                    var image = mock(RgbaImage.class);
                    when(image.width()).thenReturn(6); when(image.height()).thenReturn(4);
                    when(image.argb(anyInt(), anyInt())).thenThrow(failure);
                    return image;
                }
                return new RgbaImage(6, 4, new int[6 * 4]);
            });
            if (boundary.equals("error")) assertSame(error, assertThrows(AssertionError.class,
                    () -> call(fixture.engine, "captureSuspendThumbnailIfRequested")));
            else call(fixture.engine, "captureSuspendThumbnailIfRequested");
            call(fixture.engine, "captureSuspendThumbnailIfRequested");
            capture.verify(() -> ScreenshotCapture.captureFramebufferRegion(7, 11, 6, 4));
            SuspendedGame.Thumbnail thumbnail = get(fixture.engine, "pendingSuspendThumbnail");
            if (boundary.equals("success")) { assertEquals(3, thumbnail.width()); assertEquals(2, thumbnail.height()); }
            else assertNull(thumbnail);
            assertFalse(fixture.engine.getGameLoop().suspension.parked(), "readback itself never parks");
            if (!boundary.equals("error")) {
                var host = mock(GameLoopSuspension.Host.class);
                fixture.engine.getGameLoop().suspension.setHost(host);
                set(fixture.engine.getGameLoop().suspension, "preparing", true);
                var stopBeforeGl = new IllegalStateException("after handoff");
                PerformanceProfiler profiler = get(fixture.engine, "profiler");
                doThrow(stopBeforeGl).when(profiler).setEnabled(anyBoolean());
                assertSame(stopBeforeGl, assertThrows(IllegalStateException.class, () -> call(fixture.engine, "displayFrame")));
                verify(host).suspend(); // success and either caught RuntimeException release readiness
            }
        }
    }

    @Test void parkingConsumesPendingBeforeSettingsAndPublishesOnlyAfterLiveCallbacks() {
        var fixture = engine();
        var publishedWorld = world();
        var parkingOwner = mock(GameplayModeContext.class);
        var parkingWorld = mock(WorldSession.class, RETURNS_DEEP_STUBS);
        when(parkingWorld.getGameModule().getGameId()).thenReturn(GameId.S1);
        when(parkingOwner.getWorldSession()).thenReturn(parkingWorld);
        set(fixture.engine.getGameLoop(), "gameplayMode", parkingOwner);
        try (var sessions = mockStatic(SessionManager.class)) {
            sessions.when(SessionManager::getCurrentWorldSession).thenReturn(publishedWorld);
            assertTrue(prepare(fixture.engine));
        }
        var camera = mock(Camera.class);
        set(fixture.engine, "camera", camera);
        when(camera.getX()).thenAnswer(invocation -> { assertNull(get(fixture.engine, "pendingSuspend")); return (short) 12; });
        var thumbnail = new SuspendedGame.Thumbnail(1, 1, new byte[4]);
        var stop = new IllegalStateException("after publication");
        fixture.engine.getGameLoop().changeGameModeWithoutRewindBoundary(GameMode.BONUS_STAGE);
        fixture.engine.getGameLoop().gameModeChangeListener = (old, next) -> {
            assertNull(get(fixture.engine, "suspendedGame"));
            set(fixture.engine, "pendingSuspendThumbnail", thumbnail);
        };
        doThrow(stop).when(fixture.graphics).setProjectionWidth(anyInt());
        assertSame(stop, assertThrows(IllegalStateException.class, () -> call(fixture.engine, "suspendToMasterTitle")));
        SuspendedGame saved = get(fixture.engine, "suspendedGame");
        assertSame(thumbnail, saved.thumbnail(), "argument is read after park's live callback");
        assertSame(parkingOwner, fixture.engine.getGameLoop().suspension.parkedOwner());
        assertEquals(MasterTitleScreen.GameEntry.SONIC_2, saved.card().game().game(),
                "admission uses the published world, not the loop's distinct parking world");
        assertEquals(GameMode.BONUS_STAGE, saved.mode());
        assertTrue(saved.card().inLevel(), "card retains admission, independently of parking mode");
        assertEquals((short) 12, saved.camera().x());
        assertNull(get(fixture.engine, "pendingSuspendThumbnail"));
    }

    @ParameterizedTest @ValueSource(strings = {"cleanup", "settings", "unpark", "dimensions", "camera", "audio-runtime", "audio-error"})
    void resumeConsumesPayloadAndStopsAtTheExactFailingEffect(String boundary) {
        var fixture = engine();
        var audio = mock(AudioLogicalSnapshot.class);
        var settings = mock(SuspendedSettings.class);
        var camera = mock(Camera.class);
        var screen = mock(MasterTitleScreen.class);
        var fade = mock(FadeManager.class);
        when(fixture.graphics.getFadeManager()).thenReturn(fade);
        fixture.engine.getGameLoop().suspension.park();
        set(fixture.engine, "camera", camera);
        set(fixture.engine, "masterTitleScreen", screen);
        var game = new SuspendedGame(GameMode.BONUS_STAGE,
                new SuspendedGame.Pending(audio, SuspendedGame.card("s2", GameMode.LEVEL, "admitted level")),
                null, settings, new SuspendedGame.CameraPosition((short) 1, (short) 2, (short) 3, (short) 4));
        set(fixture.engine, "suspendedGame", game);
        var failure = new IllegalStateException(boundary);
        var error = new AssertionError(boundary);
        doAnswer(invocation -> {
            assertNull(get(fixture.engine, "suspendedGame"));
            assertSame(screen, get(fixture.engine, "masterTitleScreen"));
            if (boundary.equals("cleanup")) throw failure;
            return null;
        }).when(screen).cleanup();
        if (boundary.equals("settings")) doThrow(failure).when(settings).resume(fixture.config, MasterTitleScreen.GameEntry.SONIC_2, false);
        if (boundary.equals("unpark")) fixture.engine.getGameLoop().gameModeChangeListener = (old, next) -> { throw failure; };
        if (boundary.equals("dimensions")) doThrow(failure).when(fixture.graphics).setProjectionWidth(anyInt());
        if (boundary.equals("camera")) doThrow(failure).when(camera).setX((short) 1);
        if (boundary.equals("audio-runtime")) doThrow(failure).when(fixture.audio).restoreLogicalSnapshot(audio);
        if (boundary.equals("audio-error")) doThrow(error).when(fixture.audio).restoreLogicalSnapshot(audio);
        if (boundary.equals("audio-runtime")) call(fixture.engine, "resumeSuspendedGame");
        else if (boundary.equals("audio-error")) assertSame(error, assertThrows(AssertionError.class, () -> call(fixture.engine, "resumeSuspendedGame")));
        else assertSame(failure, assertThrows(IllegalStateException.class, () -> call(fixture.engine, "resumeSuspendedGame")));
        assertNull(get(fixture.engine, "suspendedGame"));
        assertSame(boundary.equals("cleanup") ? screen : null, get(fixture.engine, "masterTitleScreen"));
        if (!boundary.equals("cleanup")) verify(settings).resume(fixture.config, MasterTitleScreen.GameEntry.SONIC_2, false);
        else verifyNoInteractions(settings);
        if (List.of("cleanup", "settings", "unpark", "dimensions", "camera").contains(boundary))
            verify(fixture.audio, never()).restoreLogicalSnapshot(any());
        verify(fade, boundary.equals("audio-runtime") ? times(1) : never()).cancel();
        call(fixture.engine, "resumeSuspendedGame"); // consumed payload means no retry
        verify(screen).cleanup();
    }

    @ParameterizedTest @ValueSource(strings = {"resetGameplayRuntime", "resetForGameplayFromMasterTitle", "discardSuspendedGame", "reloadExternalContentAtTitle", "returnToMasterTitleScreen", "replacement-launch"})
    void sharedRetirementClearsPayloadBeforeDiscardAndFailureSkipsSharedTeardown(String entry) {
        var fixture = engine();
        fixture.engine.getGameLoop().suspension.park();
        set(fixture.engine, "suspendedGame", saved(fixture, null));
        var failure = new AssertionError("discard bindings");
        var world = world();
        var module = world.getGameModule();
        doAnswer(invocation -> {
            assertNull(get(fixture.engine, "suspendedGame"));
            assertFalse(fixture.engine.getGameLoop().suspension.parked());
            throw failure;
        }).when(module).resetModuleScopedState();
        try (var sessions = mockStatic(SessionManager.class)) {
            sessions.when(SessionManager::getCurrentWorldSession).thenReturn(world);
            assertSame(failure, assertThrows(AssertionError.class, () -> {
                if (entry.equals("replacement-launch")) fixture.engine.exitMasterTitleScreen("s2");
                else call(fixture.engine, entry);
            }));
        }
        verify(fixture.audio, never()).resetState();
    }

    @ParameterizedTest @ValueSource(strings = {"empty", "request-only", "ready", "awaiting-picture"})
    void betweenFrameHandoffNeedsPreparationAndAConsumedPictureRequest(String state) {
        var fixture = engine();
        var loop = fixture.engine.getGameLoop();
        var host = mock(GameLoopSuspension.Host.class);
        loop.suspension.setHost(host);
        boolean preparing = state.equals("ready") || state.equals("awaiting-picture");
        set(loop.suspension, "preparing", preparing);
        set(fixture.engine, "suspendThumbnailRequested", state.equals("request-only") || state.equals("awaiting-picture"));
        var stopBeforeGl = new IllegalStateException("boundary observed before GL");
        PerformanceProfiler profiler = get(fixture.engine, "profiler");
        doThrow(stopBeforeGl).when(profiler).setEnabled(anyBoolean());
        assertSame(stopBeforeGl, assertThrows(IllegalStateException.class, () -> call(fixture.engine, "displayFrame")));
        verify(host, state.equals("ready") ? times(1) : never()).suspend();
        assertEquals(state.equals("awaiting-picture"), loop.suspension.preparing());
    }

    @ParameterizedTest @ValueSource(strings = {"settings", "camera", "bindings", "notification", "old-screen", "factory", "initialize"})
    void parkingFailureMatrixConsumesPendingAndKeepsOnlyAlreadyPublishedState(String boundary) {
        var fixture = engine();
        var pending = new SuspendedGame.Pending(null, SuspendedGame.card("s2", GameMode.LEVEL, "admission"));
        set(fixture.engine, "pendingSuspend", pending);
        var camera = mock(Camera.class);
        set(fixture.engine, "camera", camera);
        var oldScreen = mock(MasterTitleScreen.class);
        set(fixture.engine, "masterTitleScreen", oldScreen);
        var failure = new IllegalStateException(boundary);
        if (boundary.equals("settings")) doThrow(failure).when(fixture.config).getConfigValue(any(SonicConfiguration.class));
        if (boundary.equals("camera")) when(camera.getX()).thenThrow(failure);
        if (boundary.equals("bindings")) doThrow(failure).when(fixture.graphics).clearRuntimeManagedReferences();
        if (boundary.equals("notification")) fixture.engine.getGameLoop().gameModeChangeListener = (old, next) -> { throw failure; };
        if (boundary.equals("old-screen")) doThrow(failure).when(oldScreen).cleanup();
        if (boundary.equals("factory")) {
            var factory = mock(com.openggf.game.patch.ModuleResolutionService.class);
            when(factory.prepareLaunch(any())).thenThrow(failure);
            set(fixture.engine, "moduleResolutionService", factory);
        }
        try (var construction = mockConstruction(MasterTitleScreen.class, (screen, ignored) -> {
            if (boundary.equals("initialize")) doThrow(failure).when(screen).initialize();
        })) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> call(fixture.engine, "suspendToMasterTitle")));
            assertNull(get(fixture.engine, "pendingSuspend"));
            boolean parked = !List.of("settings", "camera").contains(boundary);
            boolean published = List.of("old-screen", "factory", "initialize").contains(boundary);
            assertEquals(parked, fixture.engine.getGameLoop().suspension.parked());
            assertEquals(published, get(fixture.engine, "suspendedGame") != null);
            assertSame(boundary.equals("initialize") ? construction.constructed().getFirst() : oldScreen,
                    get(fixture.engine, "masterTitleScreen"));
            verify(oldScreen, published ? times(1) : never()).cleanup();
            if (!boundary.equals("initialize")) assertTrue(construction.constructed().isEmpty());
        }
    }

    @Test void engineAudioRemainsDistinctFromLoopPauseAudioAndNullCameraAndPictureResume() {
        var fixture = engine();
        var loopAudio = mock(AudioManager.class);
        set(fixture.engine.getGameLoop(), "audioManager", loopAudio);
        var snapshot = mock(AudioLogicalSnapshot.class);
        when(fixture.audio.captureLogicalSnapshot()).thenReturn(snapshot);
        try (var sessions = mockStatic(SessionManager.class)) {
            var world = world();
            sessions.when(SessionManager::getCurrentWorldSession).thenReturn(world);
            assertTrue(prepare(fixture.engine));
        }
        verify(fixture.audio).captureLogicalSnapshot();
        verify(fixture.audio).fadeOutMusic();
        verify(loopAudio, never()).captureLogicalSnapshot();
        fixture.engine.getGameLoop().toggleUserPause();
        fixture.engine.getGameLoop().suspension.park();
        set(fixture.engine, "suspendedGame", saved(fixture, snapshot));
        call(fixture.engine, "resumeSuspendedGame");
        verify(fixture.audio).restoreLogicalSnapshot(snapshot);
        verify(loopAudio, never()).restoreLogicalSnapshot(any());
        assertTrue(fixture.engine.getGameLoop().isUserPaused());
        assertEquals(GameMode.LEVEL, fixture.engine.getCurrentGameMode());
    }

    @Test void emptyVisitFactoryResetAndApplicationCleanupDoNotInventAVisitRetirement() {
        var fixture = engine();
        fixture.config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        // Ordinary view fixtures replace this loop with a constructor-bypassing mock. Empty visits stay safe.
        var originalLoop = fixture.engine.getGameLoop();
        set(fixture.engine, "gameLoop", mock(GameLoop.class));
        MasterTitleScreen screen = call(fixture.engine, "createMasterTitleScreen");
        assertFalse(screen.hasSuspendedGame());
        call(fixture.engine, "resetGameplayRuntime");
        set(fixture.engine, "gameLoop", originalLoop);
        var saved = saved(fixture, null);
        set(fixture.engine, "suspendedGame", saved);
        originalLoop.suspension.park();
        call(fixture.engine, "cleanup");
        assertSame(saved, get(fixture.engine, "suspendedGame"), "exit has no explicit discard step");
        assertTrue(originalLoop.suspension.parked());
    }

    @Test void failedEndHandlerConsumesPromptButRetainsCardAndOmitsReplacementLaunch() {
        var fixture = engine();
        var screen = new MasterTitleScreen(fixture.config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (var entry : MasterTitleScreen.GameEntry.values()) screen.setRomAvailableForTest(entry, true);
        screen.setSuspendedGame(SuspendedGame.card("s2", GameMode.LEVEL, "admission"), 0, 0, null);
        var failure = new IllegalStateException("end-handler");
        screen.setSuspendedGameEndHandler(() -> {
            assertFalse(SuspensionTestAccess.<Boolean>get(screen, "endPrompt"));
            assertNull(get(screen, "endPromptThen"));
            throw failure;
        });
        press(screen, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertTrue(SuspensionTestAccess.<Boolean>get(screen, "quitPrompt"));
        press(screen, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER); // default Return keeps the visit
        assertTrue(screen.hasSuspendedGame());
        assertFalse(SuspensionTestAccess.<Boolean>get(screen, "quitRequested"));
        press(screen, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT);
        press(screen, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        press(screen, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> press(screen, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER)));
        assertTrue(screen.hasSuspendedGame());
        assertFalse(SuspensionTestAccess.<Boolean>get(screen, "endPrompt"));
            assertNull(get(screen, "endPromptThen"));
        assertFalse(screen.isGameSelected());
    }

    private static void press(MasterTitleScreen screen, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        screen.update(input);
        input.handleKeyEvent(key, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        input.update(); screen.update(input);
    }


    @Test void resumeReadsReboundCameraAfterCleanupAndModeNotification() {
        var fixture = engine();
        var otherEngine = engine();
        var entryAudio = mock(AudioLogicalSnapshot.class);
        var oldScreen = mock(MasterTitleScreen.class);
        var replacementScreen = mock(MasterTitleScreen.class);
        var oldCamera = mock(Camera.class);
        var currentCamera = mock(Camera.class);
        var settings = mock(SuspendedSettings.class);
        fixture.engine.getGameLoop().suspension.park();
        set(fixture.engine, "masterTitleScreen", oldScreen);
        set(fixture.engine, "camera", oldCamera);
        set(fixture.engine, "suspendedGame", new SuspendedGame(GameMode.LEVEL,
                new SuspendedGame.Pending(entryAudio, SuspendedGame.card("s2", GameMode.BONUS_STAGE, "admission")),
                null, settings, new SuspendedGame.CameraPosition((short) 11, (short) 22, (short) 33, (short) 44)));
        doAnswer(invocation -> {
            set(fixture.engine, "masterTitleScreen", replacementScreen);
            fixture.engine.getGameLoop().suspension.setHost(get(otherEngine.engine.getGameLoop().suspension, "host"));
            return null;
        }).when(oldScreen).cleanup();
        doAnswer(invocation -> {
            assertNull(get(fixture.engine, "masterTitleScreen"), "the assignment follows old cleanup, even after rebinding");
            fixture.config.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
            return null;
        }).when(settings).resume(fixture.config, MasterTitleScreen.GameEntry.SONIC_2, true);
        fixture.engine.getGameLoop().gameModeChangeListener = (old, next) -> {
            assertTrue(SuspensionTestAccess.<Boolean>get(fixture.engine, "debugViewEnabled"), "cached configuration precedes unpark notification");
            set(fixture.engine, "camera", currentCamera);
        };
        when(oldScreen.consumeResumeRequest()).thenReturn(true);
        clearInvocations(otherEngine.audio, otherEngine.graphics);
        fixture.engine.getGameLoop().suspension.routeResume(oldScreen);
        verify(settings).resume(fixture.config, MasterTitleScreen.GameEntry.SONIC_2, true);
        var order = inOrder(currentCamera);
        order.verify(currentCamera).setX((short) 11); order.verify(currentCamera).setY((short) 22);
        order.verify(currentCamera).setXCopy((short) 33); order.verify(currentCamera).setYCopy((short) 44);
        verifyNoInteractions(oldCamera, replacementScreen, otherEngine.audio, otherEngine.graphics);
        verify(fixture.audio).restoreLogicalSnapshot(entryAudio);
        verify(fixture.graphics).setProjectionWidth(anyInt());
        assertNull(get(fixture.engine, "suspendedGame"));
    }

}
