package com.openggf.tests.reel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.Engine;
import com.openggf.audio.NullAudioBackend;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageManager;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.lwjgl.opengl.GL11.*;

/** Production S1 special-stage drawing over the test's exact manager, layout and ROM art.
 * The originating task is the 2026-10-26 full TAS test reel. No level is booted.
 */
public final class S1SpecialStageReel implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private static final ThreadLocal<Integer> FRAME = ThreadLocal.withInitial(() -> 0);
    private final PresentationOffscreenTestContext context;

    private S1SpecialStageReel(PresentationOffscreenTestContext context) { this.context = context; }

    /** Must precede manager.initialize(), so its own art/background loaders see this context. */
    public static S1SpecialStageReel open() throws IOException {
        if (System.getProperty("openggf.testReel.dir") == null) return new S1SpecialStageReel(null);
        FRAME.set(0);
        GameServices.audio().setBackend(new NullAudioBackend());
        var context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
        try {
            var graphics = GameServices.graphics();
            graphics.setHeadlessMode(false);
            graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            glViewport(0, 0, 320, 224);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            graphics.setProjectionWidth(320);
            graphics.applyResolvedDisplayWidth(320);
            graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0, 320, 0, 224).get(new float[16]));
            graphics.setViewport(0, 0, 320, 224);
            return new S1SpecialStageReel(context);
        } catch (IOException | RuntimeException | Error failure) {
            try { GameServices.graphics().cleanup(); } finally { context.close(); }
            throw failure;
        }
    }

    /** Exactly one existing update, with its actual pre-update held/pressed logical input. */
    public static void update(Sonic1SpecialStageManager manager) {
        if (!TestReelCapture.isRecording()) { manager.update(); return; }
        int held = integer(manager, "heldButtons"), pressed = integer(manager, "pressedButtons");
        manager.update();
        JsonNode before = snapshot(manager);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        manager.draw();
        GameServices.graphics().flushScreenSpace();
        glFinish();
        if (!before.equals(snapshot(manager)))
            throw new IllegalStateException("S1 special-stage drawing mutated the fixture rewind snapshot");
        TestReelCapture.recordFrame(ScreenshotCapture.captureFramebuffer(320, 224), "S1_SPECIAL_STAGE",
                new Bk2FrameInput(FRAME.get(), (held & 0x0f) | ((held & 0x70) != 0 ? 16 : 0),
                        (held >> 4) & 7, false, "S1 native held=" + held + " pressed=" + pressed
                        + "; comparison=" + JSON.valueToTree(manager.captureComparisonState())));
        FRAME.set(FRAME.get() + 1);
    }

    /** Same hold and update count as the production test convenience owner, with every update observed. */
    public static void advanceToEntryPresentation(Sonic1SpecialStageManager manager) {
        if (!TestReelCapture.isRecording()) { manager.advanceToEntryPresentation(); return; }
        int maxUpdates = integer(null, "SS_STARTUP_HOLD_TICKS") + 1;
        for (int i = 0; i < maxUpdates && integer(manager, "startupHoldTicksRemaining") > 0; i++) update(manager);
        if (integer(manager, "startupHoldTicksRemaining") > 0)
            throw new IllegalStateException("Special-stage startup did not reach Obj09's first tick within " + maxUpdates + " updates");
    }

    private static int integer(Sonic1SpecialStageManager manager, String name) {
        try {
            Field field = Sonic1SpecialStageManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(manager);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static JsonNode snapshot(Sonic1SpecialStageManager manager) {
        try {
            Method method = Sonic1SpecialStageManager.class.getDeclaredMethod("captureRewindSnapshot");
            method.setAccessible(true);
            return JSON.valueToTree(method.invoke(manager));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    /** Manager.reset() and GraphicsManager.cleanup() must run first, while this context is current. */
    @Override public void close() {
        FRAME.remove();
        if (context != null) context.close();
    }
}
