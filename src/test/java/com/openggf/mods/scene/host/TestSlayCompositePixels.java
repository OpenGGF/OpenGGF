package com.openggf.mods.scene.host;

import com.openggf.game.GameServices;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.code.ExampleModHarness;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.art.SpriteComposition;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.opengl.GL;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Actual ROM boss parts rendered offscreen through both legacy emission and the shared helper. */
@Isolated
class TestSlayCompositePixels {
    @TempDir Path work;

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void sharedCompositionPreservesBossPixelsOriginsFlipsAndOrder() throws Exception {
        boolean initialized = false;
        long window = 0;
        SharedLevel level = null;
        SceneRenderer renderer = new SceneRenderer();
        try {
            // This check owns an offscreen EGL context, never the user's desktop.
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
            initialized = glfwInit();
            assumeTrue(initialized, "Offscreen GLFW unavailable");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
            window = glfwCreateWindow(400, 224, "mod composition qualification", 0, 0);
            assumeTrue(window != 0, "Offscreen OpenGL 4.1 unavailable");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glViewport(0, 0, 400, 224);
            glDisable(GL_DITHER);
            renderer.init();
            float[] projection = new Matrix4f().ortho(0, 400, 0, 224, -1, 1).get(new float[16]);
            level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
            try (ExampleModHarness harness = ExampleModHarness.build(
                    Path.of("examples/slay-the-robotnik"), work.resolve("build"))) {
                harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224);
                Object scene = harness.scene();
                Object shell = scene.getClass().getMethod("shell").invoke(scene);
                Class<?> visuals = harness.loader().loadClass("slaytherobotnik.scene.EnemyVisuals");
                Class<?> enemyType = harness.loader().loadClass("slaytherobotnik.core.Enemy");
                Method prepare = visuals.getDeclaredMethod("prepare", shell.getClass(), enemyType,
                        int.class, int.class, long.class, float.class, float.class);
                prepare.setAccessible(true);
                for (String encounter : List.of("aiz:flame_craft", "hcz:screw_mobile", "lbz:beam_rocket")) {
                    assertTrue(harness.debugJump("sonic:7:fight:" + encounter));
                    Object run = shell.getClass().getField("run").get(shell);
                    Object room = run.getClass().getMethod("room").invoke(run);
                    Object combat = room.getClass().getMethod("combat").invoke(room);
                    Object enemy = ((List<?>) combat.getClass().getMethod("enemies").invoke(combat)).getFirst();
                    for (long tick : new long[] {0, 29, 61}) {
                        Object pose = prepare.invoke(null, shell, enemy, 200, 185, tick, 0f, 1f);
                        List<?> parts = (List<?>) value(pose, "parts");
                        assertTrue(parts.size() > 1, "Real composite art required: " + encounter);
                        float scale = (float) value(pose, "scale");
                        float x = (float) value(pose, "originX");
                        float y = (float) value(pose, "originY");
                        RecordingCanvas legacy = new RecordingCanvas(400, 224, null);
                        for (Object part : parts) {
                            // The pre-uplift authored emitter: ordered child offset, per-part flip,
                            // shared origin and scale. It does not call the helper's geometry.
                            legacy.draw((SceneSprite) value(part, "sprite"),
                                    x + (float) value(part, "dx") * scale,
                                    y + (float) value(part, "dy") * scale,
                                    SceneDraw.plain().withScale(scale).withFlipX((boolean) value(part, "flipX")));
                        }
                        RecordingCanvas shared = new RecordingCanvas(400, 224, null);
                        ((SpriteComposition.Placement) value(pose, "prepared")).draw(shared);
                        int[] expected = pixels(renderer, legacy, projection);
                        int[] actual = pixels(renderer, shared, projection);
                        assertTrue(Arrays.stream(expected).anyMatch(pixel -> (pixel & 0xFFFFFF) != 0),
                                "Decoded boss must render visible pixels");
                        assertArrayEquals(expected, actual, encounter + " tick=" + tick);
                        assertEquals(GL_NO_ERROR, glGetError());
                    }
                }
                assertEquals(java.util.Map.of(), harness.findings());
            }
        } finally {
            if (level != null) level.dispose();
            if (window != 0) {
                renderer.cleanup();
                GL.setCapabilities(null);
                glfwDestroyWindow(window);
            }
            if (initialized) glfwTerminate();
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void romBossCpuPixelsPreserveLegacyOriginsFlipsAndOrder() throws Exception {
        SharedLevel level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(
                Path.of("examples/slay-the-robotnik"), work.resolve("cpu-build"))) {
            harness.open(harness.apply(GameServices.module()), work.resolve("cpu-saves"), 400, 224);
            Object shell = harness.scene().getClass().getMethod("shell").invoke(harness.scene());
            Class<?> visuals = harness.loader().loadClass("slaytherobotnik.scene.EnemyVisuals");
            Method prepare = visuals.getDeclaredMethod("prepare", shell.getClass(),
                    harness.loader().loadClass("slaytherobotnik.core.Enemy"),
                    int.class, int.class, long.class, float.class, float.class);
            prepare.setAccessible(true);
            for (String encounter : List.of("aiz:flame_craft", "hcz:screw_mobile", "lbz:beam_rocket")) {
                assertTrue(harness.debugJump("sonic:7:fight:" + encounter));
                Object run = shell.getClass().getField("run").get(shell);
                Object room = run.getClass().getMethod("room").invoke(run);
                Object combat = room.getClass().getMethod("combat").invoke(room);
                Object enemy = ((List<?>) combat.getClass().getMethod("enemies").invoke(combat)).getFirst();
                for (long tick : new long[] {0,29,61}) {
                    Object pose = prepare.invoke(null,shell,enemy,200,185,tick,0f,1f);
                    List<?> parts = (List<?>) value(pose,"parts");
                    assertTrue(parts.size()>1,"Decoded ROM composite required");
                    float scale=(float)value(pose,"scale"), x=(float)value(pose,"originX"), y=(float)value(pose,"originY");
                    RecordingCanvas legacy = new RecordingCanvas(400,224,null);
                    for(Object part:parts) legacy.draw((SceneSprite)value(part,"sprite"),
                            x+(float)value(part,"dx")*scale, y+(float)value(part,"dy")*scale,
                            SceneDraw.plain().withScale(scale).withFlipX((boolean)value(part,"flipX")));
                    RecordingCanvas shared = new RecordingCanvas(400,224,null);
                    ((SpriteComposition.Placement)value(pose,"prepared")).draw(shared);
                    int[] expected=cpuPixels(legacy),actual=cpuPixels(shared);
                    assertTrue(Arrays.stream(expected).anyMatch(pixel->(pixel&0xFFFFFF)!=0),"Visible ROM pixels required");
                    assertArrayEquals(expected,actual,encounter+" tick="+tick);
                }
            }
            assertEquals(java.util.Map.of(),harness.findings());
        } finally { level.dispose(); }
    }

    /** Pixel-centre nearest-neighbour oracle for these opaque ROM parts; does not call the helper. */
    private static int[] cpuPixels(RecordingCanvas canvas) {
        int[] pixels=new int[400*224]; Arrays.fill(pixels,0xFF000000);
        for(SceneDrawOp op:canvas.ops()) {
            assertNotNull(op.image()); assertEquals(0xFFFFFFFF,op.tint()); assertEquals(0,op.flash());
            for(int y=0;y<224;y++) for(int x=0;x<400;x++) {
                float px=x+0.5f,py=y+0.5f;
                if(px<op.x0()||px>=op.x1()||py<op.y0()||py>=op.y1()) continue;
                int sx=(int)Math.floor(op.u0()+(px-op.x0())/(op.x1()-op.x0())*(op.u1()-op.u0()));
                int sy=(int)Math.floor(op.v0()+(py-op.y0())/(op.y1()-op.y0())*(op.v1()-op.v0()));
                int color=op.image().pixel(sx,sy),alpha=color>>>24;
                assertTrue(alpha==0||alpha==255,"This oracle requires opaque/transparent ROM palette pixels");
                if(alpha!=0)pixels[y*400+x]=color;
            }
        }
        return pixels;
    }

    private static Object value(Object record, String name) throws Exception {
        Method method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static int[] pixels(SceneRenderer renderer, RecordingCanvas canvas, float[] projection) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        renderer.render(canvas.ops(), projection, 400, 224, new int[] {0, 0, 400, 224});
        return ScreenshotCapture.captureFramebuffer(400, 224).pixels();
    }
}
