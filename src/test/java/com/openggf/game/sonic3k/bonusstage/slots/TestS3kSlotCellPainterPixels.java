package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** ROM wall pieces through Slot's direct painter and native collection, in an owned offscreen context. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSlotCellPainterPixels {
    @Test
    void earlierOpaqueBlackCellOccludesLaterBlueAndRenderingIsPassive() throws Exception {
        assertCellOrderAndPassivity(false);
    }

    @Test
    void earlierOpaqueBlackCellWinsDuringNativeCollectionAndCapacityKeepsSourceOrder() throws Exception {
        assertCellOrderAndPassivity(true);
    }

    private static void assertCellOrderAndPassivity(boolean collect) throws Exception {
        var saved = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try (var session = enterSlot();
             var limits = com.openggf.graphics.NativeSpriteLimits.enable()) {
            var level = GameServices.level();
            var camera = GameServices.camera();
            var graphics = GameServices.graphics();
            var services = level.getObjectManager().getObjectServices();
            assertInstanceOf(com.openggf.level.objects.DefaultObjectServices.class, services);
            assertSame(graphics, services.graphicsManager(), "loaded Slot uses the actual captured graphics owner");
            var wallRenderer = level.getObjectRenderManager().getRenderer(
                    com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SLOT_COLORED_WALL);
            var graphicsField = com.openggf.level.render.PatternSpriteRenderer.class.getDeclaredField("graphicsManager");
            graphicsField.setAccessible(true);
            assertSame(services.graphicsManager(), graphicsField.get(wallRenderer),
                    "native collection flag and cached ROM wall renderer share their graphics owner");
            assertInstanceOf(com.openggf.audio.NullAudioBackend.class, GameServices.audio().getBackend());
            assertFalse(level.getZoneFeatureProvider().useSpriteSatMasking(level.getCurrentZone()),
                    "this regression must exercise the actual Slot non-SAT painter branch");
            assertFalse(graphics.isSpriteSatCollectionActive());
            // Isolated opacity witness: retain the actual ROM mapping/patterns, but make
            // native CRAM indices $32/$33 distinguishable independent of the entry fade.
            // Retained row1017 has $32 blue and $33 black. Only index zero is transparent.
            graphics.clearPaletteFadePresentation();
            com.openggf.graphics.PaletteUploadPresentation.publishAndLatch(graphics);
            assertFalse(com.openggf.graphics.PaletteUploadPresentation.displayedFade(graphics).isActive());
            var wallPalette = new com.openggf.level.Palette();
            for (int index = 1; index < 16; index++) wallPalette.setColor(index,
                    new com.openggf.level.Palette.Color((byte) 0, (byte) 255, (byte) 0));
            wallPalette.setColor(2, new com.openggf.level.Palette.Color((byte) 0, (byte) 0, (byte) 102));
            wallPalette.setColor(3, new com.openggf.level.Palette.Color((byte) 0, (byte) 0, (byte) 0));
            graphics.cachePaletteTexture(wallPalette, 3);
            var renderer = new S3kSlotLayoutRenderer();
            renderer.updateAnimations(0x2C); // ROM frame11: art $33B + mapping $A9 = retained SAT tile $3E4.
            var before = CompactFieldCapturer.capture(renderer);
            int x = camera.getX() + 112, y = camera.getY() + 96;
            // Relative displacement of retained native SAT13/14: (166,-23) -> (177,-2).
            var earlier = cells(x, y);
            var later = cells(x + 11, y + 21);
            byte[] earlierPixels = draw(renderer, earlier, graphics, collect, 0);
            byte[] laterPixels = draw(renderer, later, graphics, collect, 0);
            var witnesses = new ArrayList<Integer>();
            for (int i = 0; i < earlierPixels.length; i += 4) {
                if (rgb(earlierPixels, i) == 0 && (rgb(laterPixels, i) & 0xFFFF00) == 0
                        && (laterPixels[i + 2] & 255) > 0) witnesses.add(i);
            }
            assertFalse(witnesses.isEmpty(), "actual ROM cells must overlap opaque black and blue pixels; colors=" + colors(earlierPixels));
            // Magenta clear survives index zero. A black pixel in earlierPixels is therefore
            // a drawn NONZERO pattern index, not a transparent RGB-black special case.
            var ordered = cells(x, y, x + 11, y + 21);
            byte[] combined = draw(renderer, ordered, graphics, collect, 0);
            for (int at : witnesses) assertEquals(0, rgb(combined, at),
                    "earlier native SAT cell must occlude later blue at buffer pixel " + at / 4);
            assertArrayEquals(combined, draw(renderer, ordered, graphics, collect, 0), "repeat render is identical");
            var reversed = cells(x + 11, y + 21, x, y);
            byte[] reversePixels = draw(renderer, reversed, graphics, collect, 0);
            for (int at : witnesses) assertEquals(rgb(laterPixels, at), rgb(reversePixels, at),
                    "swapping native order changes the overlap winner");
            var separated = cells(x, y, x + 64, y);
            byte[] separatePixels = draw(renderer, separated, graphics, collect, 0);
            assertTrue(coloredPixels(separatePixels) > coloredPixels(earlierPixels),
                    "both non-overlapping ROM cells remain visible");
            if (collect) {
                // sub_4B490 (sonic3k.asm:98324-98348) stops at d5 == $50.
                // Off-raster native entries consume table capacity without touching
                // the layout's scanlines, pixel budget, or mask state.
                var wall = level.getObjectRenderManager().getSheet(
                        com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SLOT_COLORED_WALL);
                assertEquals(1, wall.getFrame(11).pieces().size(), "one native piece per ROM wall cell");
                byte[] secondOnly = draw(renderer, cells(x + 64, y), graphics, true, 0);
                assertArrayEquals(separatePixels, draw(renderer, separated, graphics, true, 78),
                        "78 preceding entries leave both native layout slots");
                assertArrayEquals(earlierPixels, draw(renderer, separated, graphics, true, 79),
                        "entry 80 belongs to the earlier source cell; only the later cell is rejected");
                assertArrayEquals(secondOnly, draw(renderer, cells(x + 64, y, x, y), graphics, true, 79),
                        "reversing source order changes which cell receives entry 80");
                assertEquals(0, coloredPixels(draw(renderer, separated, graphics, true, 80)),
                        "80 preceding native entries reject all layout pieces");
            }
            assertEquals(before, CompactFieldCapturer.capture(renderer), "render does not advance animation state");
            assertEquals(2, ordered.size());assertEquals(x, ordered.worldXAt(0));
            assertEquals(x + 11, ordered.worldXAt(1));
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            TestEnvironment.resetAll();
            TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
        }
    }

    @Test
    void nativeLowForegroundKeepsItsPriorityAndDoesNotOccludeTheRomPeppermint() throws Exception {
        var saved = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try (var session = enterSlot();
             var limits = com.openggf.graphics.NativeSpriteLimits.enable()) {
            var manager = GameServices.level();
            var level = manager.getCurrentLevel();
            var graphics = GameServices.graphics();
            var camera = GameServices.camera();
            var coordinator = assertInstanceOf(com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator.class,
                    GameServices.module().getBonusStageProvider());
            var runtime = coordinator.activeSlotRuntime();
            assertNotNull(runtime);assertTrue(runtime.isInitialized());
            assertInstanceOf(S3kSlotBonusPlayer.class, GameServices.sprites().getMainPlayable());
            assertFalse(manager.getZoneFeatureProvider().useSpriteSatMasking(manager.getCurrentZone()));
            assertFalse(graphics.isSpriteSatCollectionActive());
            // Declared component geometry, not a replay row or camera/clock fit:
            // native Slots Plane A $4AC5 at ($430,$400), and Map_SB_R_and_Peppermint
            // frame3's opaque local(7,5), index4. LoadLevelLoadBlock2 loads the ROM
            // 16x16 words unchanged; Slots_ScreenEvent copies them to Plane A.
            int nativeDescriptor = manager.getForegroundTileDescriptorAtWorld(0x430, 0x400);
            assertEquals(0x4AC5, nativeDescriptor, "independently declared original ROM descriptor");
            int foregroundIndex = level.getPattern(nativeDescriptor & 0x7FF).getPixel(0, 0);
            assertTrue(foregroundIndex != 0 && foregroundIndex != 4,
                    "the native foreground sample must be opaque and distinct from peppermint index4");
            int liveDescriptor = manager.getForegroundTileDescriptorFromTilemapAtWorld(0x430, 0x400);
            int outside = manager.getForegroundTileDescriptorFromTilemapAtWorld(0x428, 0x400);
            manager.getZoneFeatureProvider().renderAfterBackground(camera, manager.getFrameCounter());
            assertEquals(outside, manager.getForegroundTileDescriptorFromTilemapAtWorld(0x428, 0x400),
                    "the ordinary provider does not change the neighbouring tile outside its square");
            graphics.clearPaletteFadePresentation();
            com.openggf.graphics.PaletteUploadPresentation.publishAndLatch(graphics);
            var palette = new com.openggf.level.Palette();
            for (int index = 1; index < 16; index++) palette.setColor(index,
                    new com.openggf.level.Palette.Color((byte) 238, (byte) 0, (byte) 0));
            palette.setColor(4, new com.openggf.level.Palette.Color((byte) 0, (byte) 238, (byte) 0));
            graphics.cachePaletteTexture(palette, 2);
            var renderer = new S3kSlotLayoutRenderer();
            // Ordinary animation entry: first publication, then two four-pass holds.
            for (int pass = 0; pass < 9; pass++) renderer.updateAnimations(0);
            assertEquals(3, renderer.peppermintFrame());
            var cells = new S3kSlotRenderBuffers.VisibleCells(1);
            cells.add(7, camera.getX() + 101, camera.getY() + 103);
            int at = ((224 - 1 - 96) * 320 + 96) * 4;
            byte[] unmasked = draw(renderer, cells, graphics);
            assertEquals(0x00EE00, rgb(unmasked, at),
                    "actual ROM frame3 local(7,5) is nonzero index4 through the Slot direct painter");
            var state = CompactFieldCapturer.capture(renderer);
            var registry = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var before = registry.capture();
            byte[] plane = manager.getTilemapManager().getForegroundTilemapData().clone();
            byte[] combined = drawNativeForegroundAndCell(renderer, cells, graphics, false);
            byte[] repeated = drawNativeForegroundAndCell(renderer, cells, graphics, false);
            assertArrayEquals(combined, repeated, "identical render inputs give identical pixels");
            byte[] aboveAllTiles = drawNativeForegroundAndCell(renderer, cells, graphics, true);
            assertEquals(0x00EE00, rgb(aboveAllTiles, at), "the same actual sprite remains opaque when priority bypasses tiles");
            assertArrayEquals(plane, manager.getTilemapManager().getForegroundTilemapData(), "rendering does not mutate Plane A");
            assertEquals(state, CompactFieldCapturer.capture(renderer), "rendering does not advance animation");
            var after = registry.capture();
            assertEquals(before.entries().keySet(), after.entries().keySet());
            for (String key : before.entries().keySet()) assertTrue(
                    com.openggf.game.rewind.RewindSnapshotDiff.keyEquals(key, before.get(key), after.get(key)),
                    () -> "render passivity: " + key);
            assertEquals(GL_NO_ERROR, glGetError());
            assertAll("native descriptor and actual FBO/painter consumer",
                    () -> assertEquals(nativeDescriptor, liveDescriptor,
                            "production Slot bootstrap must retain the ROM's low Plane A priority"),
                    () -> assertEquals(0x00EE00, rgb(combined, at),
                            "opaque low-priority peppermint must win over native-low Plane A"));
        } finally {
            TestEnvironment.resetAll();
            TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
        }
    }

    private static byte[] drawNativeForegroundAndCell(S3kSlotLayoutRenderer renderer,
            S3kSlotRenderBuffers.VisibleCells cells, GraphicsManager graphics, boolean highSprite) {
        graphics.flush();
        glDisable(GL_DITHER);glClearColor(1, 0, 1, 1);glClear(GL_COLOR_BUFFER_BIT);
        // Use the same production terrain consumer, priority FBO and sprite shader
        // as LevelRenderer. Project the declared ROM world point to screen(96,96),
        // without touching camera/gameplay state or replacing any renderer/provider.
        drawForegroundComponent(graphics, 0, false);
        drawForegroundComponent(graphics, 1, false);
        int drawFbo = glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFbo = glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        var mask = graphics.getTilePriorityFBO(320, 224);
        mask.begin();
        try { drawForegroundComponent(graphics, 1, true); }
        finally {
            mask.end();
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readFbo);
        }
        graphics.setUseSpritePriorityShader(true);
        graphics.setCurrentSpriteHighPriority(highSprite);
        try {
            graphics.beginPatternBatch();
            renderer.renderVisibleCells(cells, GameServices.camera(), GameServices.level().getObjectRenderManager());
            graphics.flushPatternBatch();graphics.flush();glFinish();
        } finally {
            graphics.setCurrentSpriteHighPriority(false);graphics.setUseSpritePriorityShader(false);
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(320 * 224 * 4);
        try {
            glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            byte[] bytes = new byte[buffer.remaining()];buffer.get(bytes);return bytes;
        } finally { MemoryUtil.memFree(buffer); }
    }

    private static void drawForegroundComponent(GraphicsManager graphics, int priority, boolean mask) {
        graphics.getTilemapGpuRenderer().render(com.openggf.graphics.TilemapGpuRenderer.Layer.FOREGROUND,
                320, 224, 0, 0, 320, 224, 0x430 - 96, 0x400 - 96,
                graphics.getPatternAtlasWidth(), graphics.getPatternAtlasHeight(),
                graphics.getPatternAtlasTextureId(), graphics.getCombinedPaletteTextureId(),
                0, priority, true, mask, false, 0);
    }

    private static S3kSlotRenderBuffers.VisibleCells cells(int... xy) {
        var cells = new S3kSlotRenderBuffers.VisibleCells(xy.length / 2);
        for (int i = 0; i < xy.length; i += 2) cells.add(1, xy[i], xy[i + 1]);
        return cells;
    }

    private static byte[] draw(S3kSlotLayoutRenderer renderer, S3kSlotRenderBuffers.VisibleCells cells,
                               GraphicsManager graphics) {
        return draw(renderer, cells, graphics, false, 0);
    }

    private static byte[] draw(S3kSlotLayoutRenderer renderer, S3kSlotRenderBuffers.VisibleCells cells,
                               GraphicsManager graphics, boolean collect, int precedingEntries) {
        graphics.flush();
        glDisable(GL_DITHER);glClearColor(1, 0, 1, 1);glClear(GL_COLOR_BUFFER_BIT);
        graphics.beginPatternBatch();
        if (collect) {
            graphics.beginSpriteSatCollection();
            var camera = GameServices.camera();
            for (int i = 0; i < precedingEntries; i++) {
                graphics.submitSpriteSatControlEntry(camera.getX() + 8, camera.getY() - 32, 1, 1, 0);
            }
        }
        assertEquals(collect, graphics.isSpriteSatCollectionActive());
        if (collect) {
            renderer.renderVisibleCells(cells, GameServices.camera(), GameServices.level().getObjectRenderManager(),
                    graphics.isSpriteSatCollectionActive());
        } else {
            renderer.renderVisibleCells(cells, GameServices.camera(), GameServices.level().getObjectRenderManager());
        }
        if (collect) graphics.endSpriteSatCollectionAndReplay();
        graphics.flushPatternBatch();graphics.flush();glFinish();
        ByteBuffer buffer = MemoryUtil.memAlloc(320 * 224 * 4);
        try {
            glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            byte[] bytes = new byte[buffer.remaining()];buffer.get(bytes);return bytes;
        } finally { MemoryUtil.memFree(buffer); }
    }
    private static int rgb(byte[] bytes, int at) {
        return ((bytes[at] & 255) << 16) | ((bytes[at + 1] & 255) << 8) | (bytes[at + 2] & 255);
    }
    private static java.util.Map<Integer,Integer> colors(byte[] bytes) {
        var colors = new java.util.TreeMap<Integer,Integer>();
        for (int i = 0; i < bytes.length; i += 4) colors.merge(rgb(bytes, i), 1, Integer::sum);
        return colors;
    }
    private static int coloredPixels(byte[] bytes) {
        int count = 0;
        for (int i = 0; i < bytes.length; i += 4) if (rgb(bytes, i) != 0xFF00FF) count++;
        return count;
    }
    private static GameplayCaptureSession enterSlot() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
        GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        var session = new GameplayCaptureSession(settings);
        try {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "Slot cell painter fixture");
            for (int frame = 0; frame < 60; frame++) { session.step(neutral);session.renderFrame(); }
            GameServices.level().requestBonusStageEntry(com.openggf.game.BonusStageType.SLOT_MACHINE);
            for (int frame = 0; frame < 600; frame++) {
                session.step(neutral);session.renderFrame();
                if (session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE
                        && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion()) break;
            }
            assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
            assertFalse(GameServices.fade().isActive());assertFalse(GameServices.fade().hasPendingCompletion());
            return session;
        } catch (Exception | Error e) { session.close();throw e; }
    }
}
