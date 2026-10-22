package com.openggf.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.internal.BackgroundColumnProjectionSource;
import com.openggf.game.internal.BackgroundColumnRemap;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.tests.TestEnvironment;
import com.openggf.util.ShortIndexedView;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class TestCinematicScrollPublication {
    @Test
    void tallDirectedBackgroundKeepsNativeRowsThenExtendsItsLastRowWithoutMovingForeground() {
        TestEnvironment.resetAll();
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var level = GameServices.level();
            enableDirectedSnapshots(level);
            level.parallaxManager.installContributedHandler(37,
                    new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            level.parallaxManager.update(37, 0, level.camera, 8);
            var captured = LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            var mask = captured.registers().arenaMask();
            var nativeView = captured.forCinematicView(32, 10, 400, 224, mask);
            var tall = captured.forCinematicView(32, 10, 400, 384, mask);
            assertArrayEquals(nativeView.horizontal(), java.util.Arrays.copyOf(tall.horizontal(), 224),
                    "The complete native logical image stays untouched");
            var lower = tall.slice(0, 224, 400, 224, mask);
            assertEquals(nativeView.registers().backgroundY(), lower.registers().backgroundY(),
                    "Never advance the finite plane origin into another 256-pixel wrap");
            assertEquals(tall.registers().foregroundY() + 224, lower.registers().foregroundY(),
                    "The tall foreground remains continuous world space");
            for (int row = 0; row < 160; row++) {
                assertEquals(223, row + lower.backgroundLines()[row],
                        "Each extra background row samples the last native line");
                assertEquals((short) nativeView.horizontal()[223], (short) lower.horizontal()[row]);
            }
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void columnRemapIsCapturedWithNativeScrollAndRetainedAcrossPublicationAndRewind() {
        TestEnvironment.resetAll();
        // No level assembly occurs in this ROM-free fixture, so explicitly bind
        // its native stock module before decorating the directed capability.
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var changed = new AtomicBoolean();
            var provider =
                    mock(
                            ZoneFeatureProvider.class,
                            withSettings().extraInterfaces(BackgroundColumnProjectionSource.class));
            var source = (BackgroundColumnProjectionSource) provider;
            when(source.captureBackgroundColumns())
                    .thenAnswer(
                            ignored -> {
                                int offset = changed.get() ? 1000 : 0;
                                return (com.openggf.game.internal.BackgroundColumnProjection)
                                        view ->
                                                new BackgroundColumnRemap.Columns(
                                                        values(view.width(), offset),
                                                        values(view.width(), 7),
                                                        64);
                            });
            var level = GameServices.level();
            enableDirectedSnapshots(level);
            level.zoneFeatureProvider = provider;
            var camera = level.camera;
            camera.setX((short) 0);
            camera.setY((short) 0);
            level.parallaxManager.installContributedHandler(
                    37, new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            level.parallaxManager.update(37, 0, camera, 8);
            verify(source, times(1)).captureBackgroundColumns();
            clearInvocations(provider);
            changed.set(true);
            var nativeScroll =
                    LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            var tables = new LevelSpritePresentation.Tables();
            tables.prepare(new SpritePresentation.Frame(List.of()), nativeScroll);
            tables.publish();
            var saved = tables.capture();
            var directed =
                    tables.publishedScroll()
                            .forCinematicView(0, 0, 400, 300, nativeScroll.registers().arenaMask());
            assertEquals(17, directed.backgroundColumnsRemap().sourceX().get(17));
            var tile = directed.slice(17, 32, 63, 100, directed.registers().arenaMask());
            assertEquals(17, tile.backgroundColumnsRemap().sourceX().get(0));
            assertEquals(64, tile.backgroundColumnsRemap().sourceYEndExclusive(),
                    "Background tiles retain the native source origin, so the native cutoff stays unchanged");
            assertEquals(1, tile.columnPhaseX());
            verifyNoInteractions(provider);
            level.parallaxManager.update(37, 0, camera, 9);
            var next = LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            tables.prepare(new SpritePresentation.Frame(List.of()), next);
            tables.publish();
            assertEquals(
                    1017,
                    tables.publishedScroll()
                            .forCinematicView(0, 0, 400, 300, next.registers().arenaMask())
                            .backgroundColumnsRemap()
                            .sourceX()
                            .get(17));
            tables.restore(saved);
            assertEquals(
                    17,
                    tables.publishedScroll()
                            .forCinematicView(0, 0, 400, 300, nativeScroll.registers().arenaMask())
                            .backgroundColumnsRemap()
                            .sourceX()
                            .get(17));
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void ordinaryNativeTicksNeverCaptureDirectedOwnersAndClearRetiredSnapshots() {
        TestEnvironment.resetAll();
        // No level assembly occurs in this ROM-free fixture, so explicitly bind
        // its native stock module before decorating the directed capability.
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var level = GameServices.level();
            var nativeModule = level.gameModule;
            var provider =
                    mock(
                            ZoneFeatureProvider.class,
                            withSettings()
                                    .extraInterfaces(
                                            BackgroundColumnProjectionSource.class,
                                            com.openggf.game.internal
                                                    .BackgroundBuildProjectionSource.class));
            level.zoneFeatureProvider = provider;
            var handler = spy(new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            var parallax = level.parallaxManager;
            parallax.installContributedHandler(37, handler);
            var camera = level.camera;
            camera.setX((short) 160);
            camera.setY((short) 80);
            parallax.update(37, 0, camera, 7);
            verify(handler, never()).captureProjection(any());
            verifyNoInteractions(provider);
            assertNull(parallax.projectionPhase());
            assertNull(parallax.backgroundBuildParameters());
            assertNull(parallax.backgroundColumnProjection());
            int[] ordinaryWords = parallax.getHScrollForShader().clone();

            enableDirectedSnapshots(level);
            parallax.update(37, 0, camera, 8);
            assertNotNull(parallax.projectionPhase());
            assertNotNull(parallax.backgroundBuildParameters());
            verify(handler, times(1)).captureProjection(any());
            verify((BackgroundColumnProjectionSource) provider, times(1))
                    .captureBackgroundColumns();
            verify((com.openggf.game.internal.BackgroundBuildProjectionSource) provider, times(1))
                    .captureBackgroundBuild();
            assertArrayEquals(ordinaryWords, parallax.getHScrollForShader());

            clearInvocations(handler, provider);
            // No stale derived frame survives retiring the explicit capability.
            level.gameModule = nativeModule;
            parallax.update(37, 0, camera, 9);
            verify(handler, never()).captureProjection(any());
            verifyNoInteractions(provider);
            assertNull(parallax.projectionPhase());
            assertNull(parallax.backgroundBuildParameters());
            assertNull(parallax.backgroundColumnProjection());
            assertArrayEquals(ordinaryWords, parallax.getHScrollForShader());
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void separateEndingPublicationClearsDirectedSnapshotsAndPreservesNativeOutput() {
        TestEnvironment.resetAll();
        // No level assembly occurs in this ROM-free fixture, so explicitly bind
        // its native stock module before decorating the directed capability.
        GameServices.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        try {
            var level = GameServices.level();
            var parallax = level.parallaxManager;
            parallax.installContributedHandler(
                    37, new com.openggf.game.sonic3k.scroll.SwScrlS3kDefault());
            var camera = level.camera;
            camera.setX((short) 160);
            camera.setY((short) 80);
            parallax.update(37, 0, camera, 7);
            parallax.updateForEnding(37, 0, 8, 73);
            int[] ordinaryEndingWords = parallax.getHScrollForShader().clone();
            short ordinaryForegroundY = parallax.getVscrollFactorFG();
            short ordinaryBackgroundY = parallax.getVscrollFactorBG();

            var provider =
                    mock(
                            ZoneFeatureProvider.class,
                            withSettings().extraInterfaces(BackgroundColumnProjectionSource.class));
            when(((BackgroundColumnProjectionSource) provider).captureBackgroundColumns())
                    .thenReturn(
                            view ->
                                    new BackgroundColumnRemap.Columns(
                                            values(view.width(), 0), values(view.width(), 7)));
            level.zoneFeatureProvider = provider;
            enableDirectedSnapshots(level);
            parallax.update(37, 0, camera, 9);
            assertNotNull(parallax.projectionPhase());
            assertNotNull(parallax.backgroundBuildParameters());
            assertNotNull(parallax.backgroundColumnProjection());
            clearInvocations(provider);

            parallax.updateForEnding(37, 0, 10, 73);
            assertArrayEquals(ordinaryEndingWords, parallax.getHScrollForShader());
            assertEquals(ordinaryForegroundY, parallax.getVscrollFactorFG());
            assertEquals(ordinaryBackgroundY, parallax.getVscrollFactorBG());
            assertNull(parallax.projectionPhase());
            assertNull(parallax.backgroundBuildParameters());
            assertNull(parallax.backgroundColumnProjection());
            verifyNoInteractions(provider);
            var ending =
                    LevelScrollPresentation.capture(level, AdvancedRenderFrameState.disabled());
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            ending.forCinematicView(
                                    160, 80, 320, 224, ending.registers().arenaMask()));
        } finally {
            SessionManager.clear();
        }
    }

    private static void enableDirectedSnapshots(LevelManager level) {
        level.gameModule =
                new com.openggf.game.patch.DelegatingGameModule(level.gameModule, "directed-test") {
                    @Override
                    public <T> T getGameService(Class<T> type) {
                        if (type == com.openggf.level.scroll.DirectedScrollPresentation.class)
                            return type.cast(
                                    com.openggf.level.scroll.DirectedScrollPresentation.ENABLED);
                        return super.getGameService(type);
                    }
                };
    }

    private static ShortIndexedView values(int width, int offset) {
        return new ShortIndexedView() {
            @Override
            public int size() {
                return width;
            }

            @Override
            public short get(int index) {
                return (short) (index + offset);
            }
        };
    }
}
