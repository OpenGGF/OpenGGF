package com.openggf.level;

import com.openggf.game.sonic3k.Sonic3kLevelInitProfile;
import com.openggf.graphics.*;
import com.openggf.level.render.BackgroundRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelScrollDrawScope {
    @Test void queuedTerrainKeepsItsOwnModeAcrossCaptureBufferReuse() {
        retainedTerrainSurvivesCaptures(false);
    }

    @Test void queuedTerrainKeepsItsOwnModeAfterLaterResolverFailure() {
        retainedTerrainSurvivesCaptures(true);
    }

    private static void retainedTerrainSurvivesCaptures(boolean failAfterReuse) {
        try (var f = new ScrollFixture()) {
            var reversed = new java.util.concurrent.atomic.AtomicBoolean();
            var fail = new java.util.concurrent.atomic.AtomicBoolean();
            var failure = new IllegalStateException("contributor after buffer reuse");
            f.controller.register(new com.openggf.game.render.AdvancedRenderMode() {
                public String id() { return "changing-queued-mode"; }
                public void contribute(com.openggf.game.render.AdvancedRenderModeContext context,
                                       com.openggf.game.render.AdvancedRenderFrameState.Builder builder) {
                    if (reversed.get()) builder.reversePlaneAssignment();
                    if (fail.get()) throw failure;
                }
            });
            var terrain = new RetainedTerrain(f);
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly()); // controller A
            assertFalse(terrain.commands.isEmpty());
            assertTrue(terrain.passes.isEmpty(), "actual wrappers must remain unexecuted");
            clearInvocations(f.graphics);
            reversed.set(true);
            f.overrideColumns[0] = 81;
            var first = LevelSpritePresentation.captureScroll(f.level); // controller B
            f.overrideColumns[0] = 93;
            var second = LevelSpritePresentation.captureScroll(f.level); // reuses controller A
            if (failAfterReuse) {
                fail.set(true);
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> LevelSpritePresentation.captureScroll(f.level)));
            }
            verify(f.graphics, never()).flush();
            verify(f.graphics, never()).executeCapturedCommands(any(), anyInt(), anyInt(), anyInt(), anyInt());
            assertTrue(terrain.passes.isEmpty());
            terrain.execute();
            assertEquals(List.of(new PlanePass(TilemapGpuRenderer.Layer.BACKGROUND, 0, -32, -2),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 103, 211, 0),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 103, 211, 1)), terrain.passes);
            assertEquals(List.of(List.of((short) -4, (short) 7), List.of((short) -4, (short) 7)), terrain.columns,
                    "both queued foreground priorities retain the old mode's columns");
            LevelSpritePresentation.withScroll(f.level, first, () -> {
                assertTrue(f.renderer.getCurrentAdvancedRenderFrameState().reversePlaneAssignment());
                assertArrayEquals(new short[] {81, 7}, f.renderer.getCurrentAdvancedRenderFrameState().foregroundPerColumnVScrollOverride());
            });
            LevelSpritePresentation.withScroll(f.level, second, () -> {
                assertTrue(f.renderer.getCurrentAdvancedRenderFrameState().reversePlaneAssignment());
                assertArrayEquals(new short[] {93, 7}, f.renderer.getCurrentAdvancedRenderFrameState().foregroundPerColumnVScrollOverride());
            });
        }
    }

    @Test void cinematicSetupRejectsScrollReentryButItsOwnCallbackCanDraw() {
        cinematicSetupRejectsReentry(false);
    }

    @Test void cinematicResolverFailureRestoresCameraSelectionTablesAndScopeGuard() {
        cinematicSetupRejectsReentry(true);
    }

    private static void cinematicSetupRejectsReentry(boolean failResolution) {
        try (var f = new ScrollFixture()) {
            when(f.module.getLevelInitProfile()).thenReturn(new Sonic3kLevelInitProfile(null));
            var old = LevelScrollPresentation.capture(f.level, f.renderer.getCurrentAdvancedRenderFrameState());
            f.renderer.spriteTables.prepare(SpritePresentation.Frame.empty(), old);
            f.renderer.spriteTables.publish();
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            f.horizontal[0] = 99;
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var camera = f.camera.capture();
            var tables = f.renderer.spriteTables.capture();
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            int resolutionsBefore = f.resolutions.get();
            var failure = new IllegalStateException("cinematic resolver failed");
            f.controller.register(new com.openggf.game.render.AdvancedRenderMode() {
                public String id() { return "cinematic-setup-probe"; }
                public void contribute(com.openggf.game.render.AdvancedRenderModeContext context,
                                       com.openggf.game.render.AdvancedRenderFrameState.Builder builder) {
                    if (calls.incrementAndGet() > 1) return; // bounded even on the broken nested resolver
                    assertAll(
                            () -> assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.validateScroll(f.level, frame)),
                            () -> assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level)),
                            () -> assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> fail("nested draw"))),
                            () -> assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withCinematicCamera(f.level, 1, 2, () -> fail("nested cinematic"))),
                            () -> assertThrows(IllegalStateException.class, () -> f.renderer.drawCinematicWorld(null)));
                    if (failResolution) throw failure;
                }
            });
            clearInvocations(f.graphics);
            var callback = new java.util.concurrent.atomic.AtomicBoolean();
            Runnable draw = () -> {
                callback.set(true);
                f.renderer.drawCinematicWorld(null);
                assertEquals(140, f.camera.getX());
                assertEquals(168, f.camera.getY());
                assertEquals(179, f.renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
                f.graphics.flush();
            };
            if (failResolution) {
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> LevelSpritePresentation.withCinematicCamera(f.level, 140, 168, draw)));
                assertFalse(callback.get());
                verify(f.graphics, never()).flush();
            } else {
                LevelSpritePresentation.withCinematicCamera(f.level, 140, 168, draw);
                assertTrue(callback.get());
                verify(f.graphics, times(1)).flush();
            }
            verify(f.graphics, never()).executeCapturedCommands(any(), anyInt(), anyInt(), anyInt(), anyInt());
            assertEquals(1, calls.get());
            assertEquals(resolutionsBefore + 1, f.resolutions.get());
            assertEquals(camera, f.camera.capture());
            assertEquals(tables, f.renderer.spriteTables.capture());
            assertEquals(0xff9cfff9, selected(f));
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            LevelSpritePresentation.validateScroll(f.level, frame);
        }
    }

    @Test void scopeOwnsCommandsUsesCurrentGraphicsCameraAndRestoresLiveSelection() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            f.camera.setX((short) 500); f.camera.setY((short) 600);
            var camera = f.camera.capture();
            var tables = f.renderer.spriteTables.capture();
            var discarded = new ArrayList<Integer>();
            f.graphics.registerCommand(observeDiscard(f, discarded));
            LevelSpritePresentation.withScroll(f.level, frame, () -> {
                assertEquals(0xff9cfff9, selected(f));
                assertEquals(211, f.renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
                f.graphics.registerCommand(observeDiscard(f, discarded));
            });
            assertEquals(List.of(99, 0xff9cfff9), discarded);
            verify(f.graphics).executeCapturedCommands(any(), eq(503), eq(598), eq(320), eq(224));
            assertEquals(99, selected(f));
            assertFalse(f.renderer.getCurrentAdvancedRenderFrameState().hasForegroundVScrollOverride());
            assertEquals(camera, f.camera.capture());
            assertEquals(tables, f.renderer.spriteTables.capture());
            assertEquals(1, f.resolutions.get());
            f.graphics.flush();
            assertEquals(2, discarded.size(), "owned commands cannot escape to the next flush");
        }
    }

    @Test void nestedScopeCaptureCinematicAndNativePreparationRejectWithoutDamagingOuterScope() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            LevelSpritePresentation.withScroll(f.level, frame, () -> {
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> fail("nested")));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.captureScroll(f.level));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.validateScroll(f.level, frame));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withCinematicCamera(f.level, 1, 2, () -> fail("cinematic")));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.prepare(f.level, f.sprites));
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.publish(f.level,
                        com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL));
                assertThrows(IllegalStateException.class, () -> f.renderer.renderEndingBackground(3));
                assertEquals(0xff9cfff9, selected(f));
            });
            assertEquals(frame, LevelSpritePresentation.captureScroll(f.level));
        }
    }

    @Test void drawFailureDiscardsOwnedCommandsBeforeRestoringPriorPublishedSelection() {
        try (var f = new ScrollFixture()) {
            when(f.module.getLevelInitProfile()).thenReturn(new Sonic3kLevelInitProfile(null));
            var old = LevelScrollPresentation.capture(f.level, f.renderer.getCurrentAdvancedRenderFrameState());
            f.renderer.spriteTables.prepare(SpritePresentation.Frame.empty(), old);
            f.renderer.spriteTables.publish();
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            f.horizontal[0] = 77;
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var before = f.renderer.spriteTables.capture();
            var discarded = new ArrayList<Integer>();
            var failure = new IllegalArgumentException("draw failed");
            assertSame(failure, assertThrows(IllegalArgumentException.class, () ->
                    LevelSpritePresentation.withScroll(f.level, frame, () -> {
                        f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
                        f.graphics.registerCommand(observeDiscard(f, discarded));
                        throw failure;
                    })));
            assertEquals(List.of(77), discarded);
            assertEquals(0xff9cfff9, selected(f));
            assertEquals(before, f.renderer.spriteTables.capture());
            LevelSpritePresentation.withScroll(f.level, frame, () -> assertEquals(77, selected(f)));
            f.graphics.flush();
            assertEquals(1, discarded.size());
        }
    }

    @Test void scopeNestedInsideGraphicsCaptureKeepsCommandsInTheirOwnGeneration() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            var discarded = new ArrayList<Integer>();
            f.graphics.executeCapturedCommands(() -> {
                f.graphics.registerCommand(observeDiscard(f, discarded));
                LevelSpritePresentation.withScroll(f.level, frame, () -> f.graphics.registerCommand(observeDiscard(f, discarded)));
                assertEquals(List.of(0xff9cfff9), discarded);
                f.graphics.registerCommand(observeDiscard(f, discarded));
            }, 0, 0, 320, 224);
            assertEquals(List.of(0xff9cfff9, 99, 99), discarded);
        }
    }

    @Test void entryDrainFailureAndExitBatchFailureReleaseSelectionAndScopeGuard() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            f.horizontal[0] = 99;
            var discarded = new ArrayList<Integer>();
            f.graphics.registerCommand(observeDiscard(f, discarded));
            var entryFailure = new IllegalStateException("drain failure");
            doThrow(entryFailure).doCallRealMethod().when(f.graphics).flush();
            assertSame(entryFailure, assertThrows(IllegalStateException.class, () ->
                    LevelSpritePresentation.withScroll(f.level, frame, () -> fail("not entered"))));
            assertEquals(List.of(99), discarded);
            var exitFailure = new IllegalStateException("batch close failure");
            assertSame(exitFailure, assertThrows(IllegalStateException.class, () ->
                    LevelSpritePresentation.withScroll(f.level, frame, () -> {
                        f.graphics.registerCommand(observeDiscard(f, discarded));
                        doThrow(exitFailure).doCallRealMethod().when(f.graphics).flushPatternBatch();
                    })));
            assertEquals(List.of(99, 0xff9cfff9), discarded);
            assertEquals(99, selected(f));
            LevelSpritePresentation.withScroll(f.level, frame, () -> { });
            var drawFailure = new IllegalArgumentException("primary draw failure");
            var closeFailure = new IllegalStateException("secondary close failure");
            assertSame(drawFailure, assertThrows(IllegalArgumentException.class, () ->
                    LevelSpritePresentation.withScroll(f.level, frame, () -> {
                        doThrow(closeFailure).doCallRealMethod().when(f.graphics).flushPatternBatch();
                        throw drawFailure;
                    })));
            assertArrayEquals(new Throwable[] {closeFailure}, drawFailure.getSuppressed());
            LevelSpritePresentation.validateScroll(f.level, frame);
        }
    }

    @Test void lifecycleChangeInsideCallbackRejectsBeforeCommandsCanExecute() {
        try (var f = new ScrollFixture()) {
            var frame = LevelSpritePresentation.captureScroll(f.level);
            var discarded = new ArrayList<Integer>();
            assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withScroll(f.level, frame, () -> {
                f.graphics.registerCommand(observeDiscard(f, discarded));
                f.renderer.spriteTables.invalidateScrollForActReload();
            }));
            assertEquals(List.of(0xff9cfff9), discarded);
            assertNotNull(LevelSpritePresentation.captureScroll(f.level));
        }
    }

    @Test void realTerrainPassesSelectSnapshotThenRestorePriorDrawAndOrdinaryLiveDrawing() {
        try (var f = new ScrollFixture()) {
            var tilemap = mock(TilemapGpuRenderer.class);
            when(tilemap.isBackgroundContentGenerationCurrent(anyInt())).thenReturn(true);
            when(tilemap.captureBackgroundRenderState()).thenReturn(new TilemapGpuRenderer.BackgroundRenderState(0, 0, 0));
            var background = mock(BackgroundRenderer.class);
            doReturn(tilemap).when(f.graphics).getTilemapGpuRenderer();
            doReturn(background).when(f.graphics).getBackgroundRenderer();
            doReturn(1).when(f.graphics).getPatternAtlasTextureId();
            doReturn(2).when(f.graphics).getCombinedPaletteTextureId();
            doReturn(null).when(f.graphics).getTilePriorityFBO();
            f.level.useShaderBackground = true;
            var passes = new ArrayList<PlanePass>();
            doAnswer(call -> {
                passes.add(new PlanePass(call.getArgument(0), call.getArgument(7), call.getArgument(8), call.getArgument(14)));
                return null;
            }).when(tilemap).render(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                    anyFloat(), anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyFloat());
            // Exercise actual frame commands against mocked GPU endpoints. Never run GL commands.
            doAnswer(call -> {
                GLCommandable command = call.getArgument(0);
                if (command instanceof LevelRenderer.FrameCommand) command.execute(503, 598, 320, 224);
                else call.callRealMethod();
                return null;
            }).when(f.graphics).registerCommand(any());
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            var beforeMode = f.renderer.getCurrentAdvancedRenderFrameState();
            int beforeY = beforeMode.foregroundVScrollOverride();
            var snapshot = LevelSpritePresentation.captureScroll(f.level);
            f.controller.clear();
            f.camera.setX((short) 500); f.camera.setY((short) 600);
            when(f.parallax.getVscrollFactorFG()).thenReturn((short) 601);
            when(f.parallax.getVscrollFactorBG()).thenReturn((short) 67);
            passes.clear();
            LevelSpritePresentation.withScroll(f.level, snapshot,
                    () -> f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly()));
            assertEquals(List.of(new PlanePass(TilemapGpuRenderer.Layer.BACKGROUND, 0, -32, -2),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 103, 211, 0),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 103, 211, 1)), passes);
            assertEquals(beforeY, f.renderer.getCurrentAdvancedRenderFrameState().foregroundVScrollOverride());
            passes.clear();
            f.renderer.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.tilesOnly());
            assertEquals(List.of(new PlanePass(TilemapGpuRenderer.Layer.BACKGROUND, 0, 64, -2),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 503, 601, 0),
                    new PlanePass(TilemapGpuRenderer.Layer.FOREGROUND, 503, 601, 1)), passes);
        }
    }

    private record PlanePass(TilemapGpuRenderer.Layer source, float x, float y, int priority) { }

    /** Retain real terrain wrappers until after controller reuse; only GPU endpoints are replaced. */
    private static final class RetainedTerrain {
        final List<LevelRenderer.FrameCommand> commands = new ArrayList<>();
        final List<PlanePass> passes = new ArrayList<>();
        final List<List<Short>> columns = new ArrayList<>();
        RetainedTerrain(ScrollFixture f) {
            var tilemap = mock(TilemapGpuRenderer.class);
            when(tilemap.isBackgroundContentGenerationCurrent(anyInt())).thenReturn(true);
            when(tilemap.captureBackgroundRenderState()).thenReturn(new TilemapGpuRenderer.BackgroundRenderState(0, 0, 0));
            doReturn(tilemap).when(f.graphics).getTilemapGpuRenderer();
            doReturn(mock(BackgroundRenderer.class)).when(f.graphics).getBackgroundRenderer();
            doReturn(1).when(f.graphics).getPatternAtlasTextureId();
            doReturn(2).when(f.graphics).getCombinedPaletteTextureId();
            doReturn(null).when(f.graphics).getTilePriorityFBO();
            f.level.useShaderBackground = true;
            doAnswer(call -> {
                passes.add(new PlanePass(call.getArgument(0), call.getArgument(7), call.getArgument(8), call.getArgument(14)));
                return null;
            }).when(tilemap).render(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                    anyFloat(), anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyFloat());
            doAnswer(call -> {
                com.openggf.util.ShortIndexedView view = call.getArgument(0);
                var copy = new ArrayList<Short>();
                for (int i = 0; i < view.size(); i++) copy.add(view.get(i));
                columns.add(List.copyOf(copy));
                return null;
            }).when(tilemap).enablePerColumnVScroll(any(com.openggf.util.ShortIndexedView.class));
            doAnswer(call -> {
                GLCommandable command = call.getArgument(0);
                if (command instanceof LevelRenderer.FrameCommand frame) commands.add(frame);
                else command.discard();
                return null;
            }).when(f.graphics).registerCommand(any());
        }
        void execute() {
            try { for (var command : commands) command.execute(100, 200, 320, 224); }
            finally { commands.forEach(LevelRenderer.FrameCommand::discard); commands.clear(); }
        }
    }
    private static int selected(ScrollFixture f) { return LevelSpritePresentation.backgroundScrollForDraw(f.level).horizontal()[0]; }
    private static GLCommandable observeDiscard(ScrollFixture f, List<Integer> discarded) {
        return new GLCommandable() {
            public void execute(int x, int y, int w, int h) { fail("headless command must be discarded"); }
            public void discard() { discarded.add(selected(f)); }
        };
    }
}
