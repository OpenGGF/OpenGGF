package com.openggf.game.sonic3k.objects;

import com.openggf.Engine;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.resources.PlcParser;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.openggf.game.sonic3k.objects.TestHczMinibossVisualParity.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Native pixel oracle: ROM art/mappings plus the ROM's independent priority/slot tables. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczMinibossNativeRendering {
    private record Part(int slot, int priority, int frame, int x, int y, boolean flip, int palette) {}

    @Test
    void allOrbitPhasesMatchRomSpriteCompositionInNativePixels() throws Exception {
        long window = 0;
        String output = System.getProperty("openggf.hcz.capture.dir");
        if (output != null) java.nio.file.Files.createDirectories(Path.of(output));
        GraphicsManager graphics = null;
        try (Rom rom = new Rom()) {
            TestEnvironment.activeGameplayMode();
            assertTrue(glfwInit(), "GLFW must initialize for pixel verification");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            window = glfwCreateWindow(320, 224, "HCZ1 sprite oracle", 0, 0);
            assertNotEquals(0, window);
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            graphics = new GraphicsManager();
            graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0, 0, 320, 224);
            graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0, 320, 0, 224).get(new float[16]));
            glViewport(0, 0, 320, 224);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glClearColor(0.125f, 0.125f, 0.125f, 1);
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            Pattern[] art = PlcParser.decompressAll(rom,
                    Sonic3kPlcLoader.parsePlc(rom, Sonic3kConstants.PLC_HCZ_MINIBOSS)).getFirst();
            Method sheetBuilder = Sonic3kObjectArtProvider.class.getDeclaredMethod("buildSheetFromPatterns",
                    Pattern[].class, RomByteReader.class, int.class, int.class);
            sheetBuilder.setAccessible(true);
            ObjectSpriteSheet sheet = (ObjectSpriteSheet) sheetBuilder.invoke(null, art, RomByteReader.fromRom(rom),
                    Sonic3kConstants.MAP_HCZ_MINIBOSS_ADDR, 1);
            PatternSpriteRenderer renderer = new PatternSpriteRenderer(sheet, graphics);
            renderer.ensurePatternsCached(graphics, 0x10000);
            Palette body = new Palette();
            body.fromSegaFormat(rom.readBytes(Sonic3kConstants.PAL_HCZ_MINIBOSS_ADDR, 32));
            Palette sonic = new Palette();
            sonic.fromSegaFormat(rom.readBytes(rom.read32BitAddr(Sonic3kConstants.PAL_POINTERS_ADDR + 3 * 8), 32));
            graphics.cachePaletteTexture(sonic, 0);
            graphics.cachePaletteTexture(body, 1);
            TestObjectServices services = new TestObjectServices().withCamera(GameServices.camera()).withGraphicsManager(graphics);
            HczMinibossInstance boss = ObjectConstructionContext.with(services, -1, () -> new HczMinibossInstance(
                    new ObjectSpawn(160, 256, 0x99, 0, 0, false, 0)) {
                @Override protected PatternSpriteRenderer getRenderer(String key) { return renderer; }
            });
            boss.setServices(services);
            Object state = field(boss, "state");
            set(state, "routine", 6);
            set(state, "x", 160);
            set(state, "y", 568);
            Object[] rockets = (Object[]) field(boss, "rockets");
            int[] initialX = {0, 0x80, 0x80, 0};
            int[] initialY = {0, 0x80, 0, 0x80};
            for (int phase = 0; phase < 256; phase += 16) {
                for (int i = 0; i < 4; i++) {
                    set(rockets[i], "phaseX", (initialX[i] + phase) & 255);
                    set(rockets[i], "phaseY", (initialY[i] + phase) & 255);
                    set(rockets[i], "exhaustActive", true);
                    invokeRocket(boss, "refreshRocketPosition", rockets[i]);
                }
                for (int parity = 0; parity < 2; parity++) {
                    set(boss, "lastVIntRunCount", parity);
                    glClear(GL_COLOR_BUFFER_BIT);
                    boss.appendRenderCommands(new ArrayList<>());
                    RgbaImage actual = finish(graphics);
                    List<Part> parts = new ArrayList<>();
                    parts.add(new Part(0, 0x280, 0, 160, 568, false, -1));
                    parts.add(new Part(1, 0x280, 0x16, 160, 584, false, -1));
                    if (parity == 0) parts.add(new Part(6, 0x280, 0x15, 160, 604, false, 0));
                    for (int i = 0; i < 4; i++) {
                        int py = (initialY[i] + phase) & 255;
                        int index = py >>> 4;
                        int x = (int) field(rockets[i], "x"), y = (int) field(rockets[i], "y");
                        boolean flip = i >= 2;
                        parts.add(new Part(2 + i, py < 128 ? 0x200 : 0x280,
                                rom.readByte(0x6AB50 + index) & 255, x, y, flip, -1));
                        if (parity == 0) {
                            int dx = rom.readByte(0x6AC18 + index * 2);
                            int dy = rom.readByte(0x6AC19 + index * 2);
                            parts.add(new Part(7 + i, rom.read16BitAddr(0x6ABF8 + index * 2),
                                    rom.readByte(0x6AC38 + index) & 255, x + (flip ? -dx : dx), y + dy, flip, 0));
                        }
                    }
                    parts.sort(Comparator.comparingInt(Part::priority).thenComparingInt(Part::slot).reversed());
                    glClear(GL_COLOR_BUFFER_BIT);
                    for (Part part : parts) renderer.drawFrameIndex(part.frame, part.x, part.y, part.flip, false, part.palette);
                    RgbaImage expected = finish(graphics);
                    if (output != null && !java.util.Arrays.equals(expected.pixels(), actual.pixels())) {
                        ScreenshotCapture.savePNG(actual, Path.of(output, "actual-mismatch.png"));
                        ScreenshotCapture.savePNG(expected, Path.of(output, "expected-mismatch.png"));
                    }
                    assertArrayEquals(expected.pixels(), actual.pixels(), "ROM composition phase " + phase + " parity " + parity);
                    long distinct = java.util.Arrays.stream(actual.pixels()).distinct().count();
                    assertTrue(distinct > 8, "oracle must render real ROM pixels, not a blank framebuffer");
                    if (output != null && parity == 0) ScreenshotCapture.savePNG(actual,
                            Path.of(output, String.format("phase-%02x.png", phase)));
                }
            }
        } finally {
            if (graphics != null) graphics.cleanup();
            if (window != 0) glfwDestroyWindow(window);
            glfwTerminate();
            TestEnvironment.resetAll();
        }
    }

    private static RgbaImage finish(GraphicsManager graphics) {
        graphics.flushWithCamera((short) 0, (short) 456, (short) 320, (short) 224);
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
}
