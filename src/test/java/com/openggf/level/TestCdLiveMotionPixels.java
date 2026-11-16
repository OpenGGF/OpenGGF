package com.openggf.level;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.*;
import com.openggf.graphics.*;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Disc-backed live-picture rendering contract; not a native route/parity certificate. */
@Isolated
class TestCdLiveMotionPixels {
    @Test void cdOrdinaryProfileOptsIntoLiveMotion() {
        assertInstanceOf(com.openggf.game.internal.LevelMotionPresentation.class,
                new SonicCdGameModule().getLevelInitProfile());
    }
    @ParameterizedTest @EnumSource(CdZone.class)
    void ordinaryCdLevelsBlendWithoutChangingNativeStateOrExactPixels(CdZone zone) throws Exception {
        var config = GameServices.configuration();
        boolean audio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(), zone.ordinal(), 0);
            for (int i = 0; i < 120; i++) loop.step();
            var level = GameServices.level();
            var camera = GameServices.camera();
            assertTrue(level.spritePresentationRenderer().supportsMotion(), "CD ordinary profile must opt in");
            var player = camera.getFocusedSprite();
            camera.setFrozen(true);
            assertFalse(player.getDead(), "The presentation witness needs a live native stage view");
            assertTrue(com.openggf.game.soniccd.zones.CdLevelScrollGate.runs(), "Native entry must release scroll lock");
            System.out.println("CD motion view=" + camera.getX() + "," + camera.getY()
                    + " player=" + player.getCentreX() + "," + player.getCentreY());
            LevelMotionAccess.prepareService(level, true);
            for (int i = 0; i < 3; i++) {
                camera.captureRenderCopy();
                level.parallaxManager.update(zone.ordinal(), 0, camera, level.getFrameCounter());
                GameServices.sprites().refreshPlayableRenderFlags(camera);
                LevelMotionAccess.observeService(level, GameServices.sprites(), true);
            }
            var previousScroll = level.spritePresentationRenderer().motionHistory.offer(PictureSelection.between(.5)).scroll();
            var zero = draw();
            assertArrayEquals(zero.pixels(), render(PictureSelection.between(.5)).pixels(), "zero-delta exact anchor");
            camera.setX((short) (camera.getX() + 16)); camera.setY((short) (camera.getY() + 16));
            NativePositionOps.writeXPosPreserveSubpixel(player, player.getCentreX() + 16);
            NativePositionOps.writeYPosPreserveSubpixel(player, player.getCentreY() + 16);
            camera.captureRenderCopy();
            level.setFrameCounter(level.getFrameCounter() + 1);
            level.parallaxManager.update(zone.ordinal(), 0, camera, level.getFrameCounter());
            GameServices.sprites().refreshPlayableRenderFlags(camera);
            LevelMotionAccess.observeService(level, GameServices.sprites(), true);
            var offer = level.spritePresentationRenderer().motionHistory.offer(PictureSelection.between(.5));
            assertNotNull(offer.sprites());
            assertEquals(new SpriteMotionProjection.Offset(-8, -8), offer.camera());
            var exactScroll = level.spritePresentationRenderer().motionHistory.offer(PictureSelection.LATEST).scroll();
            var bgMotion = exactScroll.backgroundMotionSince(previousScroll, camera.getFastScrollCap(), 320);
            assertNotNull(bgMotion, zone + " ordinary background pan must pair, not just foreground motion");
            assertEquals(exactScroll.registers().backgroundY() + (short) Math.rint(bgMotion.y() * .5),
                    offer.scroll().registers().backgroundY());
            for (int row = 0; row < 224; row++)
                assertEquals((short) (exactScroll.horizontal()[row] + Math.rint(bgMotion.words()[row] * .5)),
                        (short) offer.scroll().horizontal()[row], zone + " midpoint row " + row);
            var handler = level.parallaxManager.getHandler(zone.ordinal());
            var state = handler.captureRewindState();
            int frame = level.getFrameCounter(), x = player.getCentreX(), y = player.getCentreY();
            var latest = draw();
            var mixed = render(PictureSelection.between(.5));
            assertEquals("lvl BLENDED", LevelMotionAccess.panelOutcome(level));
            assertFalse(Arrays.equals(zero.pixels(), latest.pixels()), "Exact drawing must show the camera pan");
            assertArrayEquals(mixed.pixels(), render(PictureSelection.between(.5)).pixels(),
                    "Repeated render-only pictures preserve the selected phase");
            assertTrue(Arrays.stream(latest.pixels()).distinct().count() > 16, "Real stage pixels must be nonblank");
            if (Arrays.equals(latest.pixels(), mixed.pixels())) {
                java.nio.file.Path out = java.nio.file.Path.of("target/cd-motion-debug");
                java.nio.file.Files.createDirectories(out);
                ScreenshotCapture.savePNG(latest, out.resolve("latest.png"));
                ScreenshotCapture.savePNG(mixed, out.resolve("mixed.png"));
                System.out.println("CD blend register camera=" + offer.scroll().registers()
                        + " native BG=" + level.parallaxManager.getVscrollFactorBG());
            }
            assertFalse(Arrays.equals(latest.pixels(), mixed.pixels()), "Frozen-background negative control");
            assertNativeStateEquals(state, handler.captureRewindState());
            assertEquals(frame, level.getFrameCounter());
            assertEquals(x, player.getCentreX()); assertEquals(y, player.getCentreY());
            assertArrayEquals(latest.pixels(), render(PictureSelection.LATEST).pixels(), "Exact output survives blended draws");
            var captured = LevelScrollPresentation.capture(level, com.openggf.game.render.AdvancedRenderFrameState.disabled());
            var r = captured.registers();
            var nativeOutput = new com.openggf.level.scroll.ScrollProjection(captured.horizontal(),
                    captured.backgroundLines(), captured.backgroundColumns(), captured.foregroundColumns(),
                    r.foregroundY(), r.backgroundY(), r.backgroundX(), r.backgroundPeriod(),
                    r.cameraXWithShake() - r.cameraX(), r.foregroundY() - r.cameraY(), level.parallaxManager.getBgTilemapUpdateMode());
            var flags = ((com.openggf.game.soniccd.level.CdLevel) level.getCurrentLevel()).flags();
            flags.scrollLock = true;
            com.openggf.level.scroll.ScrollProjectionPhase held;
            try {
                held = ((com.openggf.level.scroll.ScrollProjectionSource) handler).captureProjection(
                        new com.openggf.level.scroll.ScrollProjectionContext(camera.getX(), camera.getY(), frame, 0, 320, 224, nativeOutput));
            } finally { flags.scrollLock = false; }
            var heldView = held.sample(new com.openggf.level.scroll.ScrollView(camera.getX() + 16, camera.getY() + 16, 320, 224));
            assertEquals(r.backgroundY(), heldView.backgroundY(), "A frozen scroll gate must retain its BG origin after release");
            for (int row = 0; row < 224; row++)
                assertEquals((short) nativeOutput.horizontal()[row], (short) heldView.horizontal()[row],
                        "Held native backgrounds cannot acquire camera-derived band motion");
            assertNativeStateEquals(state, handler.captureRewindState());
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, audio);
        }
    }
    private static void assertNativeStateEquals(Object before, Object after) throws Exception {
        assertEquals(before.getClass(), after.getClass());
        assertTrue(before.getClass().isRecord(), "Compare every captured native state component");
        for (var component : before.getClass().getRecordComponents()) {
            var accessor = component.getAccessor(); accessor.setAccessible(true);
            assertTrue(java.util.Objects.deepEquals(accessor.invoke(before), accessor.invoke(after)),
                    "Drawing changed native scroll component " + component.getName());
        }
    }
    private static RgbaImage render(PictureSelection selection) {
        var image = new AtomicReference<RgbaImage>();
        LevelMotionAccess.present(GameServices.level(), selection, () -> image.set(draw()));
        return image.get();
    }
    private static RgbaImage draw() {
        var level = GameServices.level(); var graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        level.setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.gameplay());
        graphics.flush(); glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
}
