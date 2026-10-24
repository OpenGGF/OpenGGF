package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPlayerVisualAdapter;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.render.SpriteMappingRasterizer;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Render selected disc-backed poses over the normal static stage. The production
 * player renderer consumes explicit residency; there is no actor or movement.
 * This checks adapter/GPU compatibility, not native SAT, occlusion or gameplay parity.
 */
class TestCdPlayerVisualRendering {
    private static Path captureRoot;

    @ParameterizedTest(name = "{0}: fixed-pose renderer, explicit service and restored bank")
    @EnumSource(CdReleaseVariant.class)
    void nativeMappingAndResidentTilesDrawOnlyAfterTheExplicitArtService(CdReleaseVariant variant)
            throws Exception {
        String media = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(media != null && !media.isBlank(), "Set soniccd.media.root for supplied discs");
        Assumptions.assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")),
                "Select display-free rendering explicitly");
        assertNull(System.getenv("DISPLAY"));
        assertNull(System.getenv("WAYLAND_DISPLAY"));
        String directory = switch (variant) {
            case USA -> "USA";
            case JAPAN -> "Japan";
            case EUROPE -> "Europe";
        };
        Path cue;
        try (var entries = Files.list(Path.of(media).resolve(directory))) {
            var cues = entries.filter(path -> path.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        var source = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.bootSonicCdStatic(source);
            capture.step(null);
            capture.step(null);
            var graphics = GameServices.graphics();
            var art = new CdPlayerArtRuntime(source.assets().sonic(), new CdPlayerArtRuntime.State(
                    new byte[0x300], new byte[0x300], 0, false));
            var renderer = new PlayerSpriteRenderer(
                    CdPlayerVisualAdapter.artSet(source.assets().sonic(), 0x10000), graphics);
            PlayerResidentBanks.claim(renderer);
            PlayerResidentBanks.restore(renderer, new PlayerResidentBanks.Snapshot(
                    CdPlayerVisualAdapter.residentPatterns(art.capture()), -1));
            assertEquals(24, renderer.patternBankCapacity());
            int originX = source.assets().loadPlan().initialCentre().centreX() - GameServices.camera().getX();
            int originY = 1898 - GameServices.camera().getY();
            var background = capture.render();
            var emptyState = art.capture();
            var blank = draw(capture, renderer, 1, originX, originY);
            assertArrayEquals(background.pixels(), blank.pixels(), "Draw must not invent an initial cue");
            assertSame(emptyState, art.capture());

            assertTrue(art.serviceVInt(0).isEmpty());
            assertTrue(art.serviceVInt(1).isPresent());
            PlayerResidentBanks.restore(renderer, new PlayerResidentBanks.Snapshot(
                    CdPlayerVisualAdapter.residentPatterns(art.capture()), -1));
            var pose = draw(capture, renderer, 1, originX, originY);
            assertVisibleMappingMask(background, pose, renderer, art.capture(), originX, originY,
                    CdPlayerVisualAdapter.mappingFrames(source.assets().sonic()).get(1));
            var savedState = art.capture();
            var savedBank = PlayerResidentBanks.capture(renderer);
            assertSame(savedState, art.capture());
            assertEquals(-1, savedBank.lastFrame(), "renderer cache is distinct from native last-frame 1");
            assertArrayEquals(pose.pixels(), draw(capture, renderer, 1, originX, originY).pixels());
            assertSame(savedState, art.capture(), "repeated Draw must not service native art");

            assertTrue(art.serviceVInt(2).isPresent());
            PlayerResidentBanks.restore(renderer, new PlayerResidentBanks.Snapshot(
                    CdPlayerVisualAdapter.residentPatterns(art.capture()), -1));
            var otherPose = draw(capture, renderer, 2, originX, originY);
            assertFalse(Arrays.equals(pose.pixels(), otherPose.pixels()), "distinct native pose must reach pixels");
            art.restore(savedState);
            PlayerResidentBanks.restore(renderer, savedBank);
            assertArrayEquals(pose.pixels(), draw(capture, renderer, 1, originX, originY).pixels(),
                    "restore logical residency and GPU bank without another cue");
            assertSame(savedState, art.capture());
            assertNull(GameServices.camera().getFocusedSprite(), "a fixed-pose probe is not a gameplay actor");
            if (Boolean.getBoolean("soniccd.playerVisual.capture")) {
                if (captureRoot == null) {
                    captureRoot = Files.createTempDirectory(Path.of("target"), "soniccd-player-visuals-");
                }
                String name = variant.name().toLowerCase(Locale.ROOT);
                ScreenshotCapture.savePNG(pose, captureRoot.resolve(name + "-fixed-frame-1.png"));
                ScreenshotCapture.savePNG(otherPose, captureRoot.resolve(name + "-fixed-frame-2.png"));
            }
        }
    }

    private static RgbaImage draw(GameplayCaptureSession capture, PlayerSpriteRenderer renderer,
                                  int frame, int originX, int originY) {
        capture.render();
        var graphics = GameServices.graphics();
        graphics.resetForFixedFunction();
        graphics.beginPatternBatch();
        try { renderer.drawFrame(frame, originX, originY, false, false); }
        finally { graphics.flushPatternBatch(); }
        graphics.flushScreenSpace();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static void assertVisibleMappingMask(RgbaImage background, RgbaImage actual,
                                                 PlayerSpriteRenderer renderer, CdPlayerArtRuntime.State state,
                                                 int originX, int originY,
                                                 com.openggf.level.render.SpriteMappingFrame mapping) {
        int[] white = new int[64];
        Arrays.fill(white, 0xFFFFFFFF);
        var mask = SpriteMappingRasterizer.rasterize(mapping,
                CdPlayerVisualAdapter.residentPatterns(state), white, renderer.paletteLine(), 0);
        int changed = 0;
        for (int y = 0; y < actual.height(); y++) {
            for (int x = 0; x < actual.width(); x++) {
                if (actual.argb(x, y) == background.argb(x, y)) { continue; }
                changed++;
                int mx = x - originX + mask.originX();
                int my = y - originY + mask.originY();
                assertTrue(mx >= 0 && mx < mask.width() && my >= 0 && my < mask.height(),
                        "only the selected sprite's footprint may change");
                assertNotEquals(0, mask.argb()[my * mask.width() + mx] >>> 24,
                        "changed pixel must correspond to actual nontransparent resident art");
            }
        }
        assertTrue(changed > 100, "require visible sprite pixels, not a successful blank draw");
    }
}
