package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.GameMode;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.*;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

@Isolated @EnabledOnOs(OS.LINUX)
class TestLevelMotionPixels {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void ghz1BlendsOnlyPresentationAndInvalidatesAtNativeBoundaries() throws Exception {
        check(RomTestUtils.ensureSonic1RomAvailable().toPath(), false);
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void ehz1BlendsOnlyPresentationAndInvalidatesAtNativeBoundaries() throws Exception {
        check(RomTestUtils.ensureSonic2RomAvailable().toPath(), false);
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void aiz1BlendsOnlyPublishedPresentationAndHoldsLag() throws Exception {
        check(RomTestUtils.ensureSonic3kRomAvailable().toPath(), true);
    }
    private static GameplayCaptureSession.Settings settings() {
        return new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, 0x400, 128,
                null, false, false, null, null, true, null, false);
    }
    private static void check(Path rom, boolean published) throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            for (int frame = 0; frame < 3; frame++) session.step(null);
            for (int frame = 0; frame < 240 && GameServices.fade().isActive(); frame++) session.step(null);
            assertFalse(GameServices.fade().isActive());
            var level = GameServices.level();
            var renderer = level.spritePresentationRenderer();
            assertTrue(renderer.supportsMotion(), "Exercise an audited ordinary level");
            var camera = GameServices.camera(); var player = session.player();
            // AIZ's playable terrain begins at its native $13A0/$041A start, beyond the intro sky.
            int viewX = published ? 0x1300 : 0x300;
            int viewY = published ? 0x380 : 0;
            camera.setX((short) viewX); camera.setY((short) viewY);
            NativePositionOps.writeXPosPreserveSubpixel(player, viewX + 0xA0);
            NativePositionOps.writeYPosPreserveSubpixel(player, viewY + 80);
            GameServices.sprites().refreshPlayableRenderFlags(camera);
            level.parallaxManager.update(level.currentZone, level.currentAct, camera, level.frameCounter);
            prime(level);
            assertNotNull(renderer.motionHistory.offer(PictureSelection.between(.5)).sprites(), "A zero-delta pair must exist");
            var exact = session.render();
            nonblank(exact);
            samePixels(exact, between(session, .5, true), "zero-delta anchor");

            var beforeMovement = SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();
            move(level, session, 8);
            publishObservation(level);
            var offer = renderer.motionHistory.offer(PictureSelection.between(.5));
            assertNotNull(offer.sprites(), "The camera/owner pair must interpolate");
            assertEquals(new SpriteMotionProjection.Offset(-4, 0), offer.camera());
            var latest = session.render();
            var focus = focusMask(level, session);
            var blend = between(session, .5, true);
            assertEquals("lvl BLENDED", LevelMotionAccess.panelOutcome(level), "diagnostics report an adopted blend");
            sameMaskedPixels(latest, blend, focus, "followed player remains at the latest screen position");
            samePixels(blend, between(session, .5, true), "repeated Between pictures");
            var terrain = session.render(false);
            var mixedTerrain = betweenTerrain(session, .5);
            assertFalse(Arrays.equals(terrain.pixels(), mixedTerrain.pixels()), "Terrain must actually move");
            assertThrows(AssertionError.class, () -> assertFalse(Arrays.equals(terrain.pixels(), terrain.pixels())),
                    "The terrain-movement witness rejects a snapped picture");

            if (published) {
                move(level, session, 4);
                LevelSpritePresentation.prepare(level, GameServices.sprites());
                var heldFrame = LevelSpritePresentation.published(level);
                var held = session.render();
                LevelSpritePresentation.publish(level, PlcLifecyclePhase.LAG);
                LevelMotionAccess.observeService(level, GameServices.sprites(), true);
                assertSame(heldFrame, LevelSpritePresentation.published(level));
                samePixels(held, between(session, 0, true), "S3K lag retains the published picture");
            }
            prime(level);
            NativePositionOps.writeXPosPreserveSubpixel(player, camera.getX() + 0xA0 + 4);
            NativePositionOps.writeYPosPreserveSubpixel(player, camera.getY() + 0x60);
            int beforeRecentreX = camera.getX();
            camera.updatePosition(true);
            assertEquals(beforeRecentreX + 4, camera.getX(), "Use a recenter below the ordinary camera snap bound");
            publishObservation(level);
            assertNull(renderer.motionHistory.offer(PictureSelection.between(.5)).sprites(),
                    "Forced centering cuts the picture when its generation publishes");
            samePixels(session.render(), between(session, .5, true), "forced-centering latest");
            assertEquals("lvl SNAPPED", LevelMotionAccess.panelOutcome(level), "diagnostics report the snap");
            publishObservation(level);
            assertNotNull(renderer.motionHistory.offer(PictureSelection.between(.5)).sprites(),
                    "An unchanged recenter serial allows the next generation to pair");
            SessionManager.getCurrentGameplayMode().getRewindRegistry().restore(beforeMovement);
            assertNull(renderer.motionHistory.offer(PictureSelection.between(.5)), "Own restore callback drops history");
            samePixels(session.render(), between(session, .5, false), "post-restore latest");
            prime(level);
            level.executeActTransition(SeamlessLevelTransitionRequest.builder(
                    SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                    .targetZoneAct(level.getCurrentZone(), level.getCurrentAct()).preserveOffsetCameraPosition(true).build());
            assertNull(renderer.motionHistory.offer(PictureSelection.between(.5)), "In-place reload drops pairing");
            samePixels(session.render(), between(session, .5, false), "post-act-reload latest");
            level.loadZoneAndAct(0, 0);
            assertNull(renderer.motionHistory.offer(PictureSelection.between(.5)), "Fresh load drops history");
            samePixels(session.render(), between(session, .5, false), "post-load latest");
        }
        var off = exactSequence(rom, false); var active = exactSequence(rom, true);
        assertArrayEquals(off, active, "GameplayCaptureSession exact pixel bytes must not depend on live history");
        var corrupt = active.clone(); corrupt[corrupt.length / 2] ^= 1;
        assertThrows(AssertionError.class, () -> assertArrayEquals(off, corrupt));
    }
    private static void prime(LevelManager level) {
        LevelMotionAccess.observeService(level, GameServices.sprites(), true);
        publishObservation(level); publishObservation(level);
    }
    private static void publishObservation(LevelManager level) {
        LevelSpritePresentation.prepare(level, GameServices.sprites());
        LevelSpritePresentation.publish(level, PlcLifecyclePhase.ORDINARY_LEVEL);
        LevelMotionAccess.observeService(level, GameServices.sprites(), true);
    }
    private static void move(LevelManager level, GameplayCaptureSession session, int pixels) {
        var camera = GameServices.camera();
        camera.setX((short) (camera.getX() + pixels));
        NativePositionOps.writeXPosPreserveSubpixel(session.player(), session.player().getCentreX() + pixels);
        level.setFrameCounter(level.getFrameCounter() + 1);
        level.parallaxManager.update(level.currentZone, level.currentAct, camera, level.frameCounter);
        GameServices.sprites().refreshPlayableRenderFlags(camera);
        level.refreshObjectPostCameraRenderState();
    }
    private static RgbaImage between(GameplayCaptureSession session, double alpha, boolean paired) {
        var level = GameServices.level();
        var offer = level.spritePresentationRenderer().motionHistory.offer(PictureSelection.between(alpha));
        if (paired) assertNotNull(offer);
        var image = new AtomicReference<RgbaImage>();
        LevelMotionAccess.present(level, PictureSelection.between(alpha), () -> {
            image.set(session.render());
            if (paired && offer.sprites() != null) {
                var seen = new int[2];
                GameServices.graphics().registerCommand((x, y, width, height) -> { seen[0] = x; seen[1] = y; });
                GameServices.graphics().flush();
                assertEquals(GameServices.camera().getXWithShake() + offer.camera().x(), seen[0], "Scene flush adopts camera X");
                assertEquals(GameServices.camera().getYWithShake() + offer.camera().y(), seen[1], "Scene flush adopts camera Y");
            }
        });
        return image.get();
    }
    private static RgbaImage betweenTerrain(GameplayCaptureSession session, double alpha) {
        var image = new AtomicReference<RgbaImage>();
        LevelMotionAccess.present(GameServices.level(), PictureSelection.between(alpha), () -> image.set(session.render(false)));
        return image.get();
    }
    private static boolean[] focusMask(LevelManager level, GameplayCaptureSession session) {
        var graphics = GameServices.graphics(); var camera = GameServices.camera();
        var capture = SpriteMotionInternalAccess.collect(graphics, camera.getXWithShake(), camera.getYWithShake(),
                () -> level.spritePresentationRenderer().captureSceneSpriteTable(GameServices.sprites()));
        var tiles = capture.frame().tiles().stream().filter(tile -> {
            var owner = capture.motion().owner(tile);
            return owner != null && owner.key() instanceof LevelMotionOwners.PlayerKey key && key.incarnation() == session.player();
        }).toList();
        assertFalse(tiles.isEmpty(), "Native focused player owns admitted tiles");
        glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        SpritePresentationRenderer.drawNative(graphics,
                new SpritePresentation.Frame(tiles, List.of(), capture.frame().patternVersions()),
                camera.getXWithShake(), camera.getYWithShake(), layer -> true);
        graphics.flush(); glFinish();
        var mask = ScreenshotCapture.captureFramebuffer(320, 224);
        boolean[] visible = new boolean[320 * 224]; int count = 0;
        for (int i = 0; i < visible.length; i++) if ((mask.pixels()[i] >>> 24) != 0) { visible[i] = true; count++; }
        assertTrue(count > 24 && count < 1500, "Actual focused-player pixel mask count=" + count);
        return visible;
    }
    private static void sameMaskedPixels(RgbaImage expected, RgbaImage actual, boolean[] mask, String label) {
        assertMasked(expected, actual, mask, label);
        var corrupt = actual.copy();
        for (int i = 0; i < mask.length; i++) if (mask[i]) { corrupt.pixels()[i] ^= 1; break; }
        assertThrows(AssertionError.class, () -> assertMasked(expected, corrupt, mask, label));
    }
    private static void assertMasked(RgbaImage expected, RgbaImage actual, boolean[] mask, String label) {
        for (int i = 0; i < mask.length; i++) if (mask[i]) assertEquals(expected.pixels()[i], actual.pixels()[i], label + " pixel " + i);
    }
    private static void samePixels(RgbaImage expected, RgbaImage actual, String label) {
        assertArrayEquals(expected.pixels(), actual.pixels(), label);
        var corrupt = actual.pixels().clone(); corrupt[corrupt.length / 2] ^= 1;
        assertThrows(AssertionError.class, () -> assertArrayEquals(expected.pixels(), corrupt, label), "Raw RGB must detect blue-only corruption");
    }
    private static void nonblank(RgbaImage image) {
        assertTrue(Arrays.stream(image.pixels()).distinct().limit(10).count() >= 10, "Read actual rendered scene pixels");
    }
    private static byte[] exactSequence(Path rom, boolean active) throws Exception {
        var settings = settings(); var bytes = java.nio.ByteBuffer.allocate(3 * 320 * 224 * 4);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            for (int service = 0; service < 3; service++) session.step(null);
            for (int service = 0; service < 240 && GameServices.fade().isActive(); service++) session.step(null);
            assertFalse(GameServices.fade().isActive(), "Capture comparison must see the scene after its entry fade");
            for (int service = 0; service < 3; service++) {
                session.step(null);
                LevelMotionAccess.observeService(GameServices.level(), GameServices.sprites(), active);
                var image = session.render(); nonblank(image);
                for (int pixel : image.pixels()) bytes.putInt(pixel);
            }
        }
        return bytes.array();
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void specialStageRejectsLevelHistoryAndDrawsTheSameExactPicture() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            for (int frame = 0; frame < 3; frame++) session.step(null);
            for (int frame = 0; frame < 240 && GameServices.fade().isActive(); frame++) session.step(null);
            prime(GameServices.level());
            session.loop().enterSpecialStage();
            boolean ready = false;
            for (int frame = 0; frame < 900 && !ready; frame++) {
                session.step(null);
                var provider = session.loop().getActiveSpecialStageProvider();
                ready = session.loop().getCurrentGameMode() == GameMode.SPECIAL_STAGE && provider != null
                        && provider.isEntryPresentationReady() && !provider.isEntryFadeToWhiteActive() && !GameServices.fade().isActive();
            }
            assertTrue(ready);
            LevelMotionAccess.observeService(GameServices.level(), GameServices.sprites(), true);
            assertFalse(GameServices.level().spritePresentationRenderer().supportsMotion());
            assertNull(GameServices.level().spritePresentationRenderer().motionHistory.offer(PictureSelection.between(.5)));
            var exact = session.render(); nonblank(exact);
            samePixels(exact, between(session, .5, false), "unsupported special-stage latest");
        }
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sceneFlushDoesNotNarrowACameraDeltaAndFailedScopesCannotLeakIt() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic1RomAvailable().toPath(), 0, 0, settings);
            var graphics = GameServices.graphics(); var camera = GameServices.camera();
            camera.setX(Short.MIN_VALUE); camera.setY(Short.MAX_VALUE);
            LevelMotionAccess.present(GameServices.level(), PictureSelection.LATEST, () -> {
                SpriteMotionInternalAccess.installCameraOffset(graphics, new SpriteMotionProjection.Offset(-1, 1));
                assertArrayEquals(new int[]{-32769, 32768}, probeFlush(false));
                assertArrayEquals(new int[]{0, 0}, probeFlush(true));
            });
            assertArrayEquals(new int[]{-32768, 32767}, probeFlush(false));
            assertThrows(IllegalStateException.class, () -> LevelMotionAccess.present(GameServices.level(),
                    PictureSelection.between(.5), () -> {
                        SpriteMotionInternalAccess.installCameraOffset(graphics, new SpriteMotionProjection.Offset(7, 9));
                        throw new IllegalStateException("failed picture");
                    }));
            assertArrayEquals(new int[]{-32768, 32767}, probeFlush(false));
        }
    }
    private static int[] probeFlush(boolean screen) {
        int[] seen = new int[2];
        GameServices.graphics().registerCommand((x, y, width, height) -> { seen[0] = x; seen[1] = y; });
        if (screen) GameServices.graphics().flushScreenSpace(); else GameServices.graphics().flush();
        return seen;
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void nativeCompetitionRemainsExactUnderABetweenRequest() throws Exception {
        var settings = settings();
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            capture.step(null);
            var engine = com.openggf.game.session.EngineServices.current();
            var graphics = engine.graphics(); var input = new com.openggf.control.InputHandler();
            try (var views = new com.openggf.game.sonic3k.competition.S3kCompetitionViews(graphics)) {
                var host = new com.openggf.game.sonic3k.competition.S3kCompetitionNativeHost(engine,
                        new com.openggf.game.sonic3k.Sonic3kGameModule(), input, capture.loop()::setGameplayMode,
                        new com.openggf.game.sonic3k.competition.S3kCompetitionRecords(new byte[80]),
                        ignored -> { }, views, LevelArtInternalAccess::initializeObjectArt);
                try {
                    host.openArena(new com.openggf.game.sonic3k.competition.S3kCompetitionScreen.Launch(
                            new com.openggf.game.sonic3k.competition.S3kCompetitionSession.Config(
                                    com.openggf.game.sonic3k.competition.S3kCompetitionSession.Mode.MATCH_RACE,
                                    com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena.AZURE_LAKE, true, 2),
                            List.of(com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.SONIC,
                                    com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.TAILS)));
                    var context = host.world().context();
                    for (int service = 0; service < 12; service++) {
                        var logical = com.openggf.control.LogicalInputSnapshot.neutral(); input.setLogicalOverride(logical);
                        var frame = context.plcFrameLifecycle().latchBeforeFadeUpdate();
                        try { com.openggf.game.competition.NativeCompetitionIterationAccess.runAdmitted(frame,
                                () -> host.stepArena(logical, host.session())); }
                        finally { frame.finish(); }
                    }
                    var level = context.getLevelManager();
                    LevelMotionAccess.observeService(level, context.getSpriteManager(), true);
                    assertFalse(level.spritePresentationRenderer().supportsMotion());
                    assertNull(level.spritePresentationRenderer().motionHistory.offer(PictureSelection.between(.5)));
                    glViewport(0, 0, 320, 224);
                    Runnable draw = () -> views.drawArena(context, host, host.scroll());
                    draw.run(); var exact = ScreenshotCapture.captureFramebuffer(320, 224); nonblank(exact);
                    LevelMotionAccess.present(level, PictureSelection.between(.5), draw);
                    samePixels(exact, ScreenshotCapture.captureFramebuffer(320, 224), "unsupported competition latest");
                } finally { host.closeArena(); }
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

}
