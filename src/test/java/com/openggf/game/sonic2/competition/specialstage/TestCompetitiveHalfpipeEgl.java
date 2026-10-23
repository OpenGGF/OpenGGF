package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter.Events;
import com.openggf.game.sonic2.specialstage.CompetitiveSpecialStagePlayerAccess;
import com.openggf.game.sonic2.specialstage.Sonic2TrackFrameDecoder;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Actual display-free production load, two-human stepping, native SAT draw and restored pixels. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeEgl {
    @Test
    void realHalfpipeBootsAndReplaysBothHumansThroughTheDefaultNativeShadowPath() throws Exception {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")), "owned EGL required");
        assertNull(System.getenv("DISPLAY"));
        assertNull(System.getenv("WAYLAND_DISPLAY"));
        GameServices.audio().setBackend(new NullAudioBackend());
        // The boot supplies the real graphics/services foundation only. No 1P
        // campaign, ordinary special-stage manager, CPU, or emerald owner boots.
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224);
             AudioOwner audioOwner = new AudioOwner()) {
            GameServices.audio().setBackend(new NullAudioBackend());
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var graphics = EngineServices.current().graphics();
            assertFalse(graphics.isHeadlessMode(), "logic-only graphics cannot prove production drawing");
            var clock = new AtomicInteger();
            try (var halfpipe = new Sonic2CompetitiveHalfpipe(
                    new CompetitiveHalfpipeRomData(TestEnvironment.currentRom()), graphics,
                    SpecialStageViewport.nativeViewport(), audioOwner.audio, clock::get)) {
                var events = new Events() {
                    @Override public void collectedRing(int slot) { }
                    @Override public void hitBomb(int slot) { }
                    @Override public void checkpoint() { fail("production must use explicit native events"); }
                    @Override public void archivedSector(int sector, int p1, int p2) { }
                    @Override public void tiedSector(int sector) { }
                    @Override public void stageComplete() { }
                };
                var input = new InputHandler();
                halfpipe.loadCompetitiveStage(4, events);
                for (int i = 0; i < 22; i++) { clock.incrementAndGet(); halfpipe.step(input); }
                var neutral = PlayerInputState.neutral();
                for (int i = 0; i < 12; i++) {
                    input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                            PlayerInputState.of(8, i == 0 ? 8 : 0, 0, 0, false, false),
                            PlayerInputState.of(4, i == 0 ? 4 : 0, 0, 0, false, false)));
                    clock.incrementAndGet(); halfpipe.step(input);
                }
                assertNotEquals(halfpipe.player(0).getAngle(), halfpipe.player(1).getAngle());
                for (int slot = 0; slot < 2; slot++) {
                    assertTrue(halfpipe.player(slot).getXPos() >= 16 && halfpipe.player(slot).getXPos() < 240);
                    assertTrue(halfpipe.player(slot).getYPos() >= 24 && halfpipe.player(slot).getYPos() < 204);
                }
                byte[] separated = draw(halfpipe);
                assertTrue(colors(separated) > 24, "ROM terrain, HUD and both actors must reach actual pixels");
                assertBlackMargins(separated);
                png(separated, "separate-human-actors.png");
                var saved = halfpipe.capture();
                int savedClock = clock.get();
                for (int i = 0; i < 8; i++) {
                    input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(neutral,
                            PlayerInputState.of(0, 0, i == 0 ? InputActionMasks.ACTION_A : 0,
                                    i == 0 ? InputActionMasks.ACTION_A : 0, false, false)));
                    clock.incrementAndGet(); halfpipe.step(input);
                }
                byte[] forward = draw(halfpipe);
                png(forward, "p2-jump-native-shadows.png");
                var recreated = halfpipe.capture();
                halfpipe.restore(saved); clock.set(savedClock);
                for (int i = 0; i < 8; i++) {
                    input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(neutral,
                            PlayerInputState.of(0, 0, i == 0 ? InputActionMasks.ACTION_A : 0,
                                    i == 0 ? InputActionMasks.ACTION_A : 0, false, false)));
                    clock.incrementAndGet(); halfpipe.step(input);
                }
                var replayed = halfpipe.capture();
                assertEquals(recreated.phase(), replayed.phase());
                assertEquals(recreated.track(), replayed.track());
                assertEquals(recreated.checkpoint(), replayed.checkpoint());
                assertEquals(recreated.objects(), replayed.objects());
                assertArrayEquals(forward, draw(halfpipe), "restored simulation must reproduce the actual framebuffer");
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

    @Test
    void nativeShadowOperatorsSelectTheSameRomPlanesAtDacIntensityBeforeProfilesAndFade() throws Exception {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")), "owned EGL required");
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        GameServices.audio().setBackend(new NullAudioBackend());
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            var graphics = EngineServices.current().graphics();
            var data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
            var renderer = new CompetitiveHalfpipeRenderer(data, graphics, SpecialStageViewport.nativeViewport());
            var palettes = new CompetitiveHalfpipePalettes(data);
            try {
                renderer.initialize(4);
                var pair = CompetitiveSpecialStagePlayerAccess.create();
                pair.step(0, 0, 0x40, 4); pair.step(1, 0, 0x40, 4);
                for (int i = 0; i < 6; i++) { pair.step(0, 0, 0, 4); pair.step(1, 0, 0, 4); }
                int[] tiles = Sonic2TrackFrameDecoder.decodeFrame(data.trackFrame(0x11), false);
                List<SpriteSatEntry> geometry = renderer.satEntries(0x11, pair.players(), List.of(), 0, 0,
                        CompetitiveHalfpipeRenderer.Overlay.none());
                int profilesChecked = 0;
                for (var profile : new DisplayColorProfile[]{DisplayColorProfile.RAW_RGB, DisplayColorProfile.MD_ANALOG}) {
                    for (var expansion : DisplayColorExpansion.values()) for (int fadePasses : new int[]{11, 22}) {
                        graphics.setDisplayColorProfile(profile); graphics.setDisplayColorExpansion(expansion);
                        palettes.load(4);
                        for (int i = 0; i < fadePasses; i++) palettes.fadeStep();
                        Palette[] source = palettes.palettes(); renderer.setPalettes(source);
                        byte[] normal = draw(() -> renderer.drawCleared(tiles, 0, 0));
                        byte[] actual = draw(() -> renderer.draw(0x11, tiles, 0, 0,
                                pair.players(), List.of(), 0, 0, CompetitiveHalfpipeRenderer.Overlay.none()));
                        Map<Integer, Integer> expected = intensityPairs(source, profile, expansion);
                        int operators = 0, holes = 0;
                        for (SpriteSatEntry shadow : geometry) {
                            if (shadow.pixelOperation() != SpriteSatEntry.PixelOperation.SHADOW_15) continue;
                            for (int y = shadow.y(); y < shadow.y() + shadow.heightTiles() * 8; y++) {
                                for (int x = shadow.x(); x < shadow.x() + shadow.widthTiles() * 8; x++) {
                                    if (x < 32 || x >= 288 || y < 0 || y >= 224) continue;
                                    final int px = x, py = y;
                                    if (geometry.stream().anyMatch(entry -> entry.pixelOperation() == SpriteSatEntry.PixelOperation.NORMAL
                                            && inside(entry, px, py))) continue;
                                    int color = shadowColor(data, shadow, x, y);
                                    if (color == 15) {
                                        Integer wanted = expected.get(rgb(normal, x, y));
                                        if (wanted == null || wanted == rgb(normal, x, y)) continue;
                                        assertEquals(wanted.intValue(), rgb(actual, x, y), profile + "/" + expansion
                                                + " fade " + fadePasses + " shadow pixel " + x + "," + y);
                                        operators++;
                                    } else if (geometry.stream().noneMatch(entry -> entry != shadow
                                            && entry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15
                                            && inside(entry, px, py) && shadowColor(data, entry, px, py) == 15)) {
                                        assertEquals(rgb(normal, x, y), rgb(actual, x, y), "transparent operator texel");
                                        holes++;
                                    }
                                }
                            }
                        }
                        assertTrue(operators >= 16, "actual ROM shadows must qualify the companion under " + profile + "/" + expansion);
                        assertTrue(holes > 0, "ROM shadow transparency must retain the source planes");
                        profilesChecked++;
                    }
                }
                assertEquals(8, profilesChecked);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                try { renderer.reset(); }
                finally { graphics.cleanup(); RenderContext.reset(); }
            }
        }
    }

    private static Map<Integer, Integer> intensityPairs(Palette[] source, DisplayColorProfile profile,
                                                       DisplayColorExpansion expansion) {
        var result = new HashMap<Integer, Integer>();
        for (Palette line : source) for (Palette.Color color : line.colors) {
            int r = color.r & 255, g = color.g & 255, b = color.b & 255;
            result.put(DisplayColorConverter.toPackedRgb(r, g, b, profile, expansion),
                    DisplayColorConverter.toNativeShadowPackedRgb((r * 7 + 127) / 255,
                            (g * 7 + 127) / 255, (b * 7 + 127) / 255, profile, expansion));
        }
        return result;
    }
    private static boolean inside(SpriteSatEntry entry, int x, int y) {
        return x >= entry.x() && x < entry.x() + entry.widthTiles() * 8
                && y >= entry.y() && y < entry.y() + entry.heightTiles() * 8;
    }
    private static int shadowColor(CompetitiveHalfpipeRomData data, SpriteSatEntry entry, int x, int y) {
        int nativeTile = entry.rawTileWordLow11();
        int base = nativeTile >= 0x29C ? 0x29C : nativeTile >= 0x262 ? 0x262 : 0x23C;
        Pattern[] art = base == 0x23C ? data.shadowHorizPatterns()
                : base == 0x262 ? data.shadowDiagPatterns() : data.shadowVertPatterns();
        int localX = x - entry.x(), localY = y - entry.y();
        if (entry.hFlip()) localX = entry.widthTiles() * 8 - 1 - localX;
        if (entry.vFlip()) localY = entry.heightTiles() * 8 - 1 - localY;
        return art[nativeTile - base + localX / 8 * entry.heightTiles() + localY / 8].getPixel(localX & 7, localY & 7);
    }
    private static int rgb(byte[] rgba, int x, int y) {
        int index = ((223 - y) * 320 + x) * 4;
        return (rgba[index] & 255) << 16 | (rgba[index + 1] & 255) << 8 | rgba[index + 2] & 255;
    }

    private static byte[] draw(Sonic2CompetitiveHalfpipe halfpipe) {
        return draw(halfpipe::draw);
    }
    private static byte[] draw(Runnable draw) {
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        draw.run();
        EngineServices.current().graphics().flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        glFinish();
        ByteBuffer buffer = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        byte[] pixels = new byte[buffer.remaining()]; buffer.get(pixels); return pixels;
    }
    private static int colors(byte[] rgba) {
        var colors = new HashSet<Integer>();
        for (int i = 0; i < rgba.length; i += 4)
            colors.add((rgba[i] & 255) << 16 | (rgba[i + 1] & 255) << 8 | rgba[i + 2] & 255);
        return colors.size();
    }
    private static void assertBlackMargins(byte[] rgba) {
        for (int y = 0; y < 224; y++) for (int x : new int[]{0, 31, 288, 319}) {
            int index = (y * 320 + x) * 4;
            assertEquals(0, (rgba[index] & 255) | (rgba[index + 1] & 255) | (rgba[index + 2] & 255),
                    "native H32 clipping at " + x + "," + y);
        }
    }
    private static void png(byte[] rgba, String name) throws Exception {
        var image = new BufferedImage(320, 224, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int index = ((223 - y) * 320 + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (rgba[index] & 255) << 16
                    | (rgba[index + 1] & 255) << 8 | rgba[index + 2] & 255);
        }
        Path path = Path.of("target", "competitive-halfpipe-egl", name);
        Files.createDirectories(path.getParent()); ImageIO.write(image, "png", path.toFile());
    }
    private static final class AudioOwner implements AutoCloseable {
        final AudioManager audio = AudioManager.createSilent();
        @Override public void close() { audio.destroy(); }
    }
}
