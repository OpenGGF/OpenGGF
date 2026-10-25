package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.ModSubsystem;
import com.openggf.game.GameServices;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.game.sonic3k.S3kFormTier;
import com.openggf.game.sonic2.objects.TornadoObjectInstance;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingRasterizer;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneImage;
import com.openggf.stageplay.runtime.StageplayWorldPresentation;
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

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void hydrocityBossRushRevealsHeldUnderwaterTerrainAndActualSonic() throws Exception {
        check("s3k", 1, 1, RomTestUtils.ensureSonic3kRomAvailable().toPath(), "BOSS_RUSH");
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void skyChaseFullViewOmitsOnlyItsDirectedBoundsMask() throws Exception {
        flight("s2", 8, RomTestUtils.ensureSonic2RomAvailable().toPath(), true);
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void doomsdayRetainsFullWidthNativeShipAndSuperSonic() throws Exception {
        flight("s3k", 12, RomTestUtils.ensureSonic3kRomAvailable().toPath(), false);
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
        check(game, zone, act, rom, "STORY");
    }
    private void check(String game, int zone, int act, Path rom, String mode) throws Exception {
        Path packaged = jar();
        String previous = System.getProperty(SavePaths.ROOT_PROPERTY);
        Path saves = work.resolve(game + "-" + zone + "-" + act + "-" + mode + "-saves");
        Path storage = Files.createDirectories(saves.resolve("mods/typing-of-the-hedgehog"));
        Files.writeString(storage.resolve("intent.txt"), "mode=" + mode + "\ngame=" + game + "\nzone=" + zone + "\nact=" + act
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
                if (phase.equals("STAND") || mode.equals("BOSS_RUSH") && phase.equals("BOSS")) {
                    if (mode.equals("BOSS_RUSH")) {
                        int water = GameServices.water().getWaterLevelY(zone, act);
                        assertTrue(water < GameServices.camera().getY(), "HCZ must actually exercise a fully underwater view");
                        assertFalse(GameServices.zoneRuntimeRegistry().currentAs(
                                com.openggf.game.sonic3k.runtime.HczZoneRuntimeState.class).orElseThrow()
                                .wallChaseBgOverlayActive(), "native entry must finish the wall transition before HOLD");
                        assertTrue(com.openggf.game.internal.DirectedEntryPresentation.readyForHold());
                    }
                    assertFalse(session.player().getAir(), "this obligation needs a grounded native arrival");
                    assertFalse(session.player().getRolling(), "standing presentation must remain upright");
                    pixels(session, course, sprites.frame(session.player().getMappingFrame()), game, mode.equals("BOSS_RUSH") ? "hcz-boss-rush" : "stand");
                    standing = true;
                }
            }
            assertTrue((running || mode.equals("BOSS_RUSH")) && standing, "the selected native movement/fight must actually render");
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
                + " floor=" + positive + " old=" + negative + " body=" + body
                + " hidden=" + player.isHidden() + " nativeVisible=" + player.shouldRefreshRenderFlagThisFrame()
                + " high=" + player.isHighPriority() + " invulnerable=" + player.getInvulnerableFrames());
        assertTrue(positive.tested() > 40, "contact oracle must contain actual nontransparent ROM terrain");
        assertTrue(positive.coloured() > 20, "the floor gate must contain revealed ROM colours, not black equality");
        assertTrue(positive.matched() >= positive.tested() * .8,
                "rendered terrain must align with the native feet: " + positive);
        assertTrue(negative.matched() < negative.tested() * .8,
                "disabling projection must fail the same native terrain alignment/reveal gate: " + negative);
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

    private record Ground(int tested, int matched, int coloured) { }
    private static Ground ground(LevelManager manager, AbstractPlayableSprite player, RgbaImage image) {
        int tested = 0, matched = 0, coloured = 0;
        int floor = player.getCentreY() + player.getYRadius();
        var camera = GameServices.camera();
        for (int y = floor - 1; y < floor + 7; y++) for (int x = player.getCentreX() - 12; x <= player.getCentreX() + 12; x++) {
            Integer rgb = foregroundPixel(manager, x, y);
            int sx = x - camera.getX(), sy = y - camera.getY();
            if (rgb == null || sx < 0 || sx >= image.width() || sy < 0 || sy >= image.height()) continue;
            tested++;
            if ((image.argb(sx, sy) & 0xffffff) == rgb) {
                matched++;
                if (rgb != 0) coloured++;
            }
        }
        return new Ground(tested, matched, coloured);
    }

    private record Body(int mask, int inside, int outside) { }
    private static Body body(AbstractPlayableSprite player, SceneSprite source, RgbaImage full, RgbaImage absent) {
        return body(player, source, full, absent, 125);
    }
    private static Body body(AbstractPlayableSprite player, SceneSprite source, RgbaImage full, RgbaImage absent, int top) {
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
        for (int y = top; y < 202; y++) for (int x = 0; x < 400; x++) {
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
        var palette = level.getPalette(pattern.getPaletteIndex());
        var water = GameServices.water();
        int zone = manager.getFeatureZoneId(), act = manager.getFeatureActId();
        if (water.hasWater(zone, act) && y >= water.getWaterLevelY(zone, act) - 8) {
            var underwater = water.getUnderwaterPalette(zone, act);
            if (underwater != null) palette = underwater[pattern.getPaletteIndex()];
        }
        var color = palette.getColor(index);
        var graphics = GameServices.graphics();
        return DisplayColorConverter.toPackedRgb(color.r & 255, color.g & 255, color.b & 255,
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
    }

    /** HUD draw counters can advance between readbacks; compare only the actual world picture. */
    private static void assertWorldPictureEquals(RgbaImage a, RgbaImage b, String message) {
        for (int y = 125; y < 202; y++) for (int x = 0; x < 400; x++)
            assertEquals(a.argb(x, y), b.argb(x, y), message + " at " + x + "," + y);
    }
    private void flight(String game, int zone, Path rom, boolean nativeWing) throws Exception {
        Path packaged = jar();
        String previous = System.getProperty(SavePaths.ROOT_PROPERTY);
        Path saves = work.resolve(game + "-flight-saves");
        Path storage = Files.createDirectories(saves.resolve("mods/typing-of-the-hedgehog"));
        Files.writeString(storage.resolve("intent.txt"), "mode=STORY\ngame=" + game + "\nzone=" + zone + "\nact=0\n"
                + "difficulty=EASY\nseed=413\nnoFail=true\nvoice=OFF\n");
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var settings = new GameplayCaptureSession.Settings(400, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings); var library = SceneRomLibrary.open(Map.of(game, rom))) {
            session.applyMod(packaged);
            session.boot(rom, zone, 0, settings);
            Object course = course();
            int clock = -1;
            boolean checked = false;
            for (int row = 0; row < 1600 && !checked; row++) {
                session.step(null);
                session.renderFrame();
                Object view = invoke(course, "view");
                assertNull(get(view, "fault"), view.toString());
                String phase = (String) get(view, "phase");
                if (!phase.equals("ENTRY") && !phase.equals("IDLE")) {
                    int actual = GameServices.level().getObjectManager().getVblaCounter();
                    if (clock < 0) clock = actual;
                    assertEquals(clock, actual, "flight presentation must retain native HOLD");
                }
                if (!(phase.equals("STAND") || phase.equals("BOSS"))
                        || optionalField(course, "phaseRows").getInt(course) < 100) continue;
                var player = session.player();
                if (nativeWing) assertInstanceOf(TornadoObjectInstance.class,
                        GameServices.level().getObjectManager().getRidingObject(player));
                else assertTrue(player.isSuperSonic(), "DDZ must retain actual native Super Sonic");
                SpriteArtSet art = player.isSuperSonic()
                        ? new Sonic3kPlayerArt(RomByteReader.fromRom(GameServices.rom().getRom()))
                                .loadFormArtSet("sonic", S3kFormTier.SUPER)
                        : library.characterArt(game, "sonic");
                flightPixels(session, course, cpuMask(art, player.getMappingFrame()), game, nativeWing);
                checked = true;
            }
            assertTrue(checked, "the real flight entry and held fight must execute");
        } finally {
            if (previous == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previous);
        }
    }

    private static void flightPixels(GameplayCaptureSession session, Object course, SceneSprite mask,
            String game, boolean nativeWing) throws Exception {
        var level = GameServices.level();
        var camera = GameServices.camera();
        var player = session.player();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var before = registry.captureCourse();
        var published = LevelSpritePresentation.published(level);
        var nativeCamera = camera.capture();
        RgbaImage visible = session.render();
        var renderer = player.getSpriteRenderer();
        var noBody = new PlayerSpriteRenderer(SpriteArtSet.EMPTY, GameServices.graphics()) {
            @Override public void drawFrame(int frame, int x, int y, boolean flipX, boolean flipY) { }
        };
        RgbaImage absent;
        try { player.setSpriteRenderer(noBody); absent = session.render(); }
        finally { player.setSpriteRenderer(renderer); }
        RgbaImage repeated = session.render();
        Body body = body(player, mask, visible, absent, 30);
        assertTrue(body.mask() > 100 && body.inside() > 100 && body.inside() > body.mask() * .7,
                "actual native flight body must follow ROM art: " + body);
        assertTrue(body.outside() <= 8, "the no-body control must remove only the real flight body: " + body);
        // Default Stageplay/cinematic draws retain native masks. Exercise failed/nested
        // full-view scopes before drawing that default to prove restoration, too.
        assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withoutCinematicBoundsMask(level,
                () -> LevelSpritePresentation.withoutCinematicBoundsMask(level,
                        () -> { throw new IllegalStateException("aborted directed draw"); })));
        RgbaImage nativeMask;
        try (var defaultView = new StageplayWorldPresentation()) {
            defaultView.draw(GameServices.graphics(), level, camera.getX(), camera.getY(), 1, 400, 224, () -> {
                level.drawCinematicWorld(GameServices.sprites());
                GameServices.graphics().flush();
            });
            nativeMask = ScreenshotCapture.captureFramebuffer(400, 224);
        }
        Field projection = optionalField(course, "sectionPresentation");
        Object saved = projection.get(course);
        RgbaImage ordinary;
        try { projection.set(course, null); ordinary = session.render(); }
        finally { projection.set(course, saved); }
        RgbaImage restored = session.render();
        Path evidence = Path.of(System.getProperty("typinghog.renderEvidence", "target/typing-section-rendering"));
        Files.createDirectories(evidence);
        ScreenshotCapture.savePNG(visible, evidence.resolve(game + "-flight-full-width.png"));
        ScreenshotCapture.savePNG(absent, evidence.resolve(game + "-flight-no-body.png"));
        ScreenshotCapture.savePNG(nativeMask, evidence.resolve(game + "-flight-default-mask.png"));
        ScreenshotCapture.savePNG(ordinary, evidence.resolve(game + "-flight-ordinary.png"));
        System.out.println("SECTION-EDGE|" + game + " body=" + body + " fullNativeColours=" + nativeColourCount(visible)
                + " defaultNativeColours=" + nativeColourCount(nativeMask) + " ordinaryNativeColours=" + nativeColourCount(ordinary));
        assertTrue(nativeColourCount(visible) > 4800 * .95, "the entire right edge must contain native world colours: " + nativeColourCount(visible));
        if (nativeWing) {
            assertTrue(nativeColourCount(nativeMask) < 300, "default cinematic masking must retain its native wing");
            assertTrue(nativeColourCount(ordinary) < 300, "ordinary native masking must retain its native wing");
        } else {
            assertTrue(nativeColourCount(nativeMask) > 4800 * .95, "DDZ's already widened boss view remains complete by default");
        }
        for (int y = 30; y < 202; y++) for (int x = 0; x < 400; x++) {
            assertEquals(visible.argb(x, y), repeated.argb(x, y));
            assertEquals(visible.argb(x, y), restored.argb(x, y), "native replay must not poison the full-view picture");
        }
        assertEquals(nativeCamera, camera.capture());
        assertSame(published, LevelSpritePresentation.published(level));
        var after = registry.captureCourse();
        List<String> diffs = new ArrayList<>();
        for (String key : before.entries().keySet()) diffs.addAll(RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)));
        assertEquals(List.of(), diffs, "full-width presentation must not change native captured state");

    }

    /** MD palette levels distinguish actual ROM pixels from the procedural gray mask grain. */
    private static int nativeColourCount(RgbaImage image) {
        int count = 0;
        for (int y = 110; y < 170; y++) for (int x = 320; x < 400; x++) {
            int rgb = image.argb(x, y);
            if (nativeChannel(rgb >>> 16 & 255) && nativeChannel(rgb >>> 8 & 255) && nativeChannel(rgb & 255)) count++;
        }
        return count;
    }
    private static boolean nativeChannel(int value) {
        return value == 0 || value == 36 || value == 73 || value == 109
                || value == 146 || value == 182 || value == 219 || value == 255;
    }

    private static SceneSprite cpuMask(SpriteArtSet art, int frame) {
        Pattern[] bank = art.artTiles();
        if (frame < art.dplcFrames().size()) {
            var pieces = new ArrayList<Pattern>();
            for (var request : art.dplcFrames().get(frame).requests()) {
                int first = request.destinationOffset() >= 0 ? request.destinationOffset() : pieces.size();
                for (int i = 0; i < request.count(); i++) {
                    while (pieces.size() <= first + i) pieces.add(null);
                    pieces.set(first + i, bank[request.startTile() + i]);
                }
            }
            assertFalse(pieces.isEmpty(), "flight mask requires a ROM-backed nonempty DPLC frame");
            bank = pieces.toArray(new Pattern[0]);
        }
        int[] opaque = new int[64];
        for (int i = 0; i < opaque.length; i++) opaque[i] = i % 16 == 0 ? 0 : 0xffffffff;
        var pixels = SpriteMappingRasterizer.rasterize(art.mappingFrames().get(frame), bank, opaque, art.paletteIndex(), 0);
        return new SceneSprite(new SceneImage(pixels.width(), pixels.height(), pixels.argb()), pixels.originX(), pixels.originY());
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
