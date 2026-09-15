package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.RgbaImage;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/** FBZ2 room exit: compare production tile rendering with ROM layout/pattern pixels. */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzBossPlanePixels {
    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void reversedPlanesSampleTheirOwnWorldCoordinates(int width) throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                width, "sonic", "", "off", null, 0x2C20 + (width - 320) / 2, 0x68C);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 4, 1, settings);
            PlaneCoordinates published = null;
            for (int i = 0; i < 30; i++) {
                published = cpuCoordinates();
                session.step(null);
            }
            var state = S3kRuntimeStates.currentFbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            assertEquals(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED, state.planeAssignmentMode());
            PlaneSamples initial = assertTilePixels(session, published);
            byte[] checkpoint = state.captureBytes();
            var events = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
            // VInt uploads the already prepared scroll/SAT before this loop
            // calculates the injected CPU offsets. Check both sides of that boundary.
            published = cpuCoordinates();
            events.setBossBackgroundOffsets(0x137, 0x180);
            session.step(null);
            assertTilePixels(session, published);
            published = cpuCoordinates();
            session.step(null);
            PlaneSamples moving = assertTilePixels(session, published);
            assertTrue(initial.front() + moving.front() > 100, "moving Plane A must contribute visible pixels");
            if (width >= 528) {
                assertTrue(initial.rear() + moving.rear() > 100, "wide view must expose stationary Plane B");
            }

            // Exercise the production zone-state restore/reconciliation order,
            // including its retained 64x32 snapshot, then run a forward tick.
            state.restoreBytes(checkpoint);
            events.reconcileAfterRewindRestore();
            GameServices.level().getTilemapManager().resetTilemapsForRewindRestore();
            session.step(null); // regenerate CPU scroll after this partial zone-state restore
            published = cpuCoordinates();
            session.step(null); // VInt publishes the regenerated scroll/SAT pair
            assertTilePixels(session, published);
        }
    }

    private record PlaneCoordinates(int cameraX, int cameraY, int movingX, int movingY) { }

    /** Independent ROM coordinate oracle, sampled before the VBlank it describes. */
    private static PlaneCoordinates cpuCoordinates() {
        var state = S3kRuntimeStates.currentFbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        int cameraX = GameServices.camera().getX();
        // FBZ2_CloudDeform and loc_530F0: logical BG on physical A,
        // logical FG on physical B, with independent VSRAM and HScroll.
        return new PlaneCoordinates(cameraX, state.bossForegroundVScroll(),
                cameraX - 0x2600 - state.bossBackgroundOffsetX(),
                GameServices.parallaxOrNull().getVscrollFactorBG());
    }

    private static PlaneSamples assertTilePixels(GameplayCaptureSession session, PlaneCoordinates published) {
        RgbaImage image = session.render(false);
        byte[] priorityMask = readPriorityMask(image.width(), image.height());
        LevelManager manager = GameServices.level();
        Level level = manager.getCurrentLevel();
        int cameraX = published.cameraX();
        int cameraY = published.cameraY();
        int movingX = published.movingX();
        int movingY = published.movingY();
        int compared = 0;
        int front = 0;
        int rear = 0;
        int mismatched = 0;
        int maskMismatched = 0;
        String first = "";
        for (int y = 2; y < image.height(); y += 3) {
            for (int x = 2; x < image.width(); x += 3) {
                int a = manager.getBackgroundTileDescriptorAtWorld(movingX + x, movingY + y);
                int b = manager.getForegroundTileDescriptorAtWorld(cameraX + x, cameraY + y);
                int aPixel = pixel(level, a, movingX + x, movingY + y);
                int bPixel = pixel(level, b, cameraX + x, cameraY + y);
                boolean highTile = aPixel != 0 && (a & 0x8000) != 0
                        || bPixel != 0 && (b & 0x8000) != 0;
                boolean masked = priorityMask[(image.height() - 1 - y) * image.width() + x] != 0;
                if (highTile != masked) maskMismatched++;
                if (aPixel == 0 && bPixel == 0) continue;
                boolean useA = aPixel != 0 && (bPixel == 0 || (a & 0x8000) != 0 || (b & 0x8000) == 0);
                if (useA) front++; else rear++;
                int descriptor = useA ? a : b;
                int index = useA ? aPixel : bPixel;
                Palette.Color colour = level.getPalette((descriptor >>> 13) & 3).getColor(index);
                int expected = (colour.r & 255) << 16 | (colour.g & 255) << 8 | (colour.b & 255);
                int actual = image.argb(x, y) & 0xFFFFFF;
                compared++;
                if (expected != actual) {
                    mismatched++;
                    if (first.isEmpty()) first = "at " + x + "," + y + " expected="
                            + Integer.toHexString(expected) + " actual=" + Integer.toHexString(actual)
                            + " A=" + Integer.toHexString(a) + " B=" + Integer.toHexString(b)
                            + " selected=" + (useA ? "A" : "B");
                }
            }
        }
        assertTrue(compared > 1000, "must exercise opaque ROM terrain, not an empty frame");
        assertEquals(0, mismatched, "of " + compared + " ROM tile pixels; " + first);
        assertEquals(0, maskMismatched, "low-priority sprites must be masked by high pixels from either plane");
        return new PlaneSamples(front, rear);
    }

    private record PlaneSamples(int front, int rear) { }

    private static byte[] readPriorityMask(int width, int height) {
        var mask = GameServices.graphics().getTilePriorityFBO();
        assertEquals(width, mask.getWidth());
        assertEquals(height, mask.getHeight());
        byte[] result = new byte[width * height];
        ByteBuffer pixels = memAlloc(result.length);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, mask.getTextureId());
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_BYTE, pixels);
            pixels.get(result);
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            memFree(pixels);
        }
        return result;
    }

    private static int pixel(Level level, int descriptor, int x, int y) {
        int px = x & 7;
        int py = y & 7;
        if ((descriptor & 0x800) != 0) px = 7 - px;
        if ((descriptor & 0x1000) != 0) py = 7 - py;
        return level.getPattern(descriptor & 0x7FF).getPixel(px, py);
    }
}
