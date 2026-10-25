package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.ModSubsystem;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Actual packaged-mod EGL pictures, using the same production draw dispatcher as capture/Engine.
 * Every stepped frame is rendered. ROM foreground contact pixels and a detached ROM Sonic mask
 * must agree with the current held camera/pose; omitting Sonic's renderer and disabling section projection are
 * independent controls. Drawing must leave course-captured native state and publications untouched.
 */
class TestTypingHedgehogSectionRendering {
    @TempDir static Path work;
    private static Path jar;

    @Test @RequiresRom(SonicGame.SONIC_1)
    void marbleProjectsHeldTerrainAndActualSonic() throws Exception {
        check("s1", 1, 2, RomTestUtils.ensureSonic1RomAvailable().toPath());
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void hillTopProjectsHeldTerrainAndActualSonic() throws Exception {
        check("s2", 4, 1, RomTestUtils.ensureSonic2RomAvailable().toPath());
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void carnivalProjectsHeldTerrainAndActualSonic() throws Exception {
        check("s3k", 3, 1, RomTestUtils.ensureSonic3kRomAvailable().toPath());
    }

    private static Path jar() throws Exception {
        if (jar == null) {
            Path build = work.resolve("package");
            try (var ignored = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"), build)) {
                jar = build.resolve("typing-of-the-hedgehog.jar");
            }
        }
        return jar;
    }

    private void check(String game, int zone, int act, Path rom) throws Exception {
        Path packaged = jar();
        String previous = System.getProperty(SavePaths.ROOT_PROPERTY);
        Path saves = work.resolve(game + "-saves");
        Path storage = Files.createDirectories(saves.resolve("mods/typing-of-the-hedgehog"));
        Files.writeString(storage.resolve("intent.txt"), "mode=STORY\ngame=" + game + "\nzone=" + zone + "\nact=" + act
                + "\ndifficulty=EASY\ncapitals=false\npunctuation=false\ndictionaries=standard\nseed=413\n"
                + "bossStars=1\nnoFail=true\nslowMotion=false\nvoice=OFF\n");
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var settings = new GameplayCaptureSession.Settings(400, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings); var library = SceneRomLibrary.open(Map.of(game, rom))) {
            session.applyMod(packaged);
            session.boot(rom, zone, act, settings);
            Object course = course();
            var sprites = library.rom(game).character("sonic");
            int nativeClock = -1;
            boolean running = false, standing = false;
            for (int row = 0; row < 1200 && !standing; row++) {
                session.step(null);
                session.renderFrame();
                Object view = invoke(course, "view");
                assertNull(get(view, "fault"), game + " " + view);
                String phase = (String) get(view, "phase");
                if (!phase.equals("IDLE") && !phase.equals("ENTRY")) {
                    int clock = GameServices.level().getObjectManager().getVblaCounter();
                    if (nativeClock < 0) nativeClock = clock;
                    assertEquals(nativeClock, clock, "projection must not advance the held native world");
                }
                if (!running && phase.equals("RUN")) {
                    Object section = invoke(course, "sectionView");
                    Object route = get(section, "travel");
                    if (Math.abs((int) get(route, "xSpeed")) >= 256) {
                        pixels(session, course, sprites.frame(session.player().getMappingFrame()), game, "run");
                        running = true;
                    }
                }
                if (phase.equals("STAND")) {
                    assertFalse(session.player().getAir(), "this obligation needs a grounded native arrival");
                    assertFalse(session.player().getRolling(), "standing presentation must remain upright");
                    pixels(session, course, sprites.frame(session.player().getMappingFrame()), game, "stand");
                    standing = true;
                }
            }
            assertTrue(running && standing, "both native movement and the settled fight must actually render");
            Field projection = optionalField(course, "sectionPresentation");
            assertNotNull(projection);
            Object ownedPresentation = projection.get(course);
            assertNotNull(ownedPresentation);
            invoke(course, "close");
            assertNull(projection.get(course), "leave/fault cleanup releases the section presentation");
            assertTrue((boolean) optionalField(ownedPresentation, "closed").get(ownedPresentation));
            invoke(course, "close"); // The session invokes close again during its own teardown.
        } finally {
            if (previous == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previous);
        }
    }

    private static void pixels(GameplayCaptureSession session, Object course, SceneSprite cpuBody,
            String game, String phase) throws Exception {
        var player = session.player();
        var level = GameServices.level();
        var camera = GameServices.camera();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var before = registry.captureCourse();
        var published = LevelSpritePresentation.published(level);
        var originalCamera = camera.capture();
        RgbaImage visible = session.render();
        RgbaImage noPlayer;
        var nativeRenderer = player.getSpriteRenderer();
        // Omit only the actual native body. The keyboard HUD stays present in both pictures.
        var noBody = new PlayerSpriteRenderer(SpriteArtSet.EMPTY, GameServices.graphics()) {
            @Override public void drawFrame(int frame, int x, int y, boolean flipX, boolean flipY) { }
        };
        try { player.setSpriteRenderer(noBody); noPlayer = session.render(); }
        finally { player.setSpriteRenderer(nativeRenderer); }
        RgbaImage repeated = session.render();
        assertWorldPictureEquals(visible, repeated, "omitting the real Sonic must be reversible");
        RgbaImage oldProjection;
        Field projection = optionalField(course, "sectionPresentation");
        Object savedProjection = projection == null ? null : projection.get(course);
        try {
            if (projection != null) projection.set(course, null);
            oldProjection = session.render();
        } finally { if (projection != null) projection.set(course, savedProjection); }
        RgbaImage restored = session.render();
        assertWorldPictureEquals(visible, restored, "ordinary native SAT replay must not poison the directed picture");
        Path evidence = Path.of(System.getProperty("typinghog.renderEvidence", "target/typing-section-rendering"));
        Files.createDirectories(evidence);
        String name = game + "-" + phase;
        ScreenshotCapture.savePNG(visible, evidence.resolve(name + ".png"));
        ScreenshotCapture.savePNG(noPlayer, evidence.resolve(name + "-no-player.png"));
        ScreenshotCapture.savePNG(oldProjection, evidence.resolve(name + "-no-projection.png"));
        Ground positive = ground(level, player, noPlayer);
        Ground negative = ground(level, player, oldProjection);
        Body body = body(player, cpuBody, visible, noPlayer);
        System.out.println("SECTION-PIXELS|" + name + " world=" + player.getCentreX() + "," + player.getCentreY()
                + " camera=" + camera.getX() + "," + camera.getY() + " mapping=" + player.getMappingFrame()
                + " floor=" + positive + " old=" + negative + " body=" + body);
        assertTrue(positive.tested() > 40, "contact oracle must contain actual nontransparent ROM terrain");
        assertTrue(positive.matched() >= positive.tested() * .8,
                "rendered terrain must align with the native feet: " + positive);
        assertTrue(negative.matched() < positive.matched() / 2,
                "disabling projection must reproduce the stale/blank/displaced terrain: " + negative);
        assertTrue(body.mask() > 100 && body.inside() > 100, "actual Sonic body pixels must be visible: " + body);
        assertTrue(body.inside() >= body.mask() * .7 && body.outside() <= 8,
                "visible differences must follow the independently decoded ROM Sonic mask: " + body);
        assertEquals(originalCamera, camera.capture(), "projection must restore native camera copies/bounds");
        assertSame(published, LevelSpritePresentation.published(level), "drawing must not publish another native SAT");
        var after = registry.captureCourse();
        List<String> differences = new ArrayList<>();
        for (String key : before.entries().keySet()) differences.addAll(RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)));
        assertEquals(List.of(), differences, "rendering must not restore or mutate captured native state");
    }

    private record Ground(int tested, int matched) { }
    private static Ground ground(LevelManager manager, AbstractPlayableSprite player, RgbaImage image) {
        int tested = 0, matched = 0;
        int floor = player.getCentreY() + player.getYRadius();
        var camera = GameServices.camera();
        for (int y = floor - 1; y < floor + 7; y++) for (int x = player.getCentreX() - 12; x <= player.getCentreX() + 12; x++) {
            Integer rgb = foregroundPixel(manager, x, y);
            int sx = x - camera.getX(), sy = y - camera.getY();
            if (rgb == null || sx < 0 || sx >= image.width() || sy < 0 || sy >= image.height()) continue;
            tested++;
            if ((image.argb(sx, sy) & 0xffffff) == rgb) matched++;
        }
        return new Ground(tested, matched);
    }

    private record Body(int mask, int inside, int outside) { }
    private static Body body(AbstractPlayableSprite player, SceneSprite source, RgbaImage full, RgbaImage absent) {
        boolean[] mask = new boolean[400 * 224];
        int count = 0;
        var camera = GameServices.camera();
        for (int y = 0; y < source.height(); y++) for (int x = 0; x < source.width(); x++) {
            if ((source.image().pixel(x, y) >>> 24) == 0) continue;
            int dx = x - source.originX(), dy = y - source.originY();
            int sx = player.getRenderCentreX() + (player.getRenderHFlip() ? -dx - 1 : dx) - camera.getX();
            int sy = player.getRenderCentreY() + (player.getRenderVFlip() ? -dy - 1 : dy) - camera.getY();
            if (sx < 0 || sx >= 400 || sy < 0 || sy >= 224) continue;
            mask[sy * 400 + sx] = true; count++;
        }
        int inside = 0, outside = 0;
        // The independent control must exclude the blinking START cue at rows 80..112.
        // Both tested native grounded poses lie completely in this world-only band.
        for (int y = 125; y < 202; y++) for (int x = 0; x < 400; x++) {
            if (full.argb(x, y) == absent.argb(x, y)) continue;
            if (mask[y * 400 + x]) inside++; else outside++;
        }
        return new Body(count, inside, outside);
    }

    private static Integer foregroundPixel(LevelManager manager, int x, int y) {
        var level = manager.getCurrentLevel();
        var descriptor = manager.getChunkDescAt((byte) 0, x, y);
        if (descriptor == null) return null;
        int cx = x & 15, cy = y & 15;
        if (descriptor.getHFlip()) cx = 15 - cx;
        if (descriptor.getVFlip()) cy = 15 - cy;
        var pattern = level.getChunk(descriptor.getChunkIndex()).getPatternDesc(cx / 8, cy / 8);
        int px = cx & 7, py = cy & 7;
        if (pattern.getHFlip()) px = 7 - px;
        if (pattern.getVFlip()) py = 7 - py;
        int index = level.getPattern(pattern.getPatternIndex()).getPixel(px, py) & 15;
        if (index == 0) return null;
        var color = level.getPalette(pattern.getPaletteIndex()).getColor(index);
        var graphics = GameServices.graphics();
        return DisplayColorConverter.toPackedRgb(color.r & 255, color.g & 255, color.b & 255,
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
    }

    /** HUD draw counters can advance between readbacks; compare only the actual world picture. */
    private static void assertWorldPictureEquals(RgbaImage a, RgbaImage b, String message) {
        for (int y = 125; y < 202; y++) for (int x = 0; x < 400; x++)
            assertEquals(a.argb(x, y), b.argb(x, y), message + " at " + x + "," + y);
    }
    private static Object course() throws Exception {
        Class<?> type = ModSubsystem.current().rewindClassResolver().resolve("typing-of-the-hedgehog", "typinghog.play.Course").orElseThrow();
        return GameServices.module().getGameService(type);
    }
    private static Object invoke(Object object, String name) throws Exception { return object.getClass().getMethod(name).invoke(object); }
    private static Object get(Object object, String name) throws Exception {
        Method method = object.getClass().getMethod(name); method.setAccessible(true); return method.invoke(object);
    }
    private static Field optionalField(Object object, String name) throws Exception {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field; }
        catch (NoSuchFieldException missing) { return null; }
    }
}
