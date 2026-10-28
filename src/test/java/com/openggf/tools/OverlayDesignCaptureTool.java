package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.EscapeToMasterTitleController;
import com.openggf.capture.LiveCaptureController;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.editor.project.EditorProjectMenuRenderer;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuTextEditor;
import com.openggf.game.recording.*;
import com.openggf.graphics.*;
import com.openggf.graphics.shaderlib.*;
import com.openggf.mods.mutators.MutatorWorldAccess;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayRenderer;
import com.openggf.stageplay.runtime.StageplayResources;
import com.openggf.testmode.*;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceHudModel;
import com.openggf.trace.live.MismatchEntry;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Purpose: render in-session host overlays through their production drawing owners over S2 EHZ1.
 * Inputs: {@code <S2-ROM> <new-output-dir> [width=320] [scale=3] [mutator-mod.jar]}.
 * Origin: 2026-10-27 menu design review round 3.
 *
 * <p>Requires surfaceless EGL with DISPLAY/WAYLAND_DISPLAY unset; boot installs NullAudioBackend.
 * GameplayCaptureSession owns boot, gameplay drawing and teardown. Output is nearest-neighbour
 * scaled PNGs plus state.csv. Diagnostic counts, playback summaries and recording states are
 * declared presentation samples, not replay/parity evidence. Reflection reaches package-private
 * drawing owners/test seams without adding runtime APIs. The shader scene constructs a draw-only
 * Engine facade over the boot's services: it never initializes/runs an Engine or opens a window.
 * Its sample shader paths are display data only; no shaders are downloaded or activated.
 * Scenes 22-24 hold Escape over the pause and window video plates at once, through Engine's
 * own renderers and toast placement (pause flag and capture state set on the facade, then restored);
 * 25/26 picture every frame of the toast's slide in and out over both plates.
 * With a packaged Mutator Lab mod, capture only its real prepared-world pause/configuration UI.
 * No recordings, gameplay saves or player preferences are written; use an owned save root for mods.
 * <p>Lives in test scope: it composes the editor project menu, trace HUD and Stageplay
 * runtime, which production tools may not depend on (TestArchUnitRules core-runtime and
 * Stageplay ownership rules). Run with {@code target/test-classes} ahead of {@code target/classes}.
 */
public final class OverlayDesignCaptureTool {
    private final GameplayCaptureSession session;
    private final Path output;
    private final int width, scale;
    private final float[] projection;

    private OverlayDesignCaptureTool(GameplayCaptureSession session, Path output, int width, int scale) {
        this.session = session; this.output = output; this.width = width; this.scale = scale;
        projection = SceneSurface.projection(width, 224);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 5)
            throw new IllegalArgumentException("usage: OverlayDesignCaptureTool <S2-ROM> <new-output-dir> [width] [scale] [mutator-mod.jar]");
        if (!SurfacelessEglContext.requested() || System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null)
            throw new IllegalStateException("Requires display-free surfaceless EGL; unset DISPLAY and WAYLAND_DISPLAY");
        Path rom = Path.of(args[0]).toAbsolutePath(), out = Path.of(args[1]).toAbsolutePath();
        int width = args.length > 2 ? Integer.parseInt(args[2]) : 320;
        int scale = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        if ((width != 320 && width != 400) || scale < 1 || scale > 8)
            throw new IllegalArgumentException("width must be 320 or 400; scale must be 1..8");
        if (!Files.isRegularFile(rom) || Files.exists(out))
            throw new IllegalArgumentException("Supply an existing ROM and a new output directory");
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            if (args.length == 5) session.applyMod(Path.of(args[4]));
            session.boot(rom, 0, 0, settings);
            if (!session.usesNullAudioBackend())
                throw new IllegalStateException("Capture must retain NullAudioBackend");
            for (int frame = 0; frame < 90; frame++) session.step(null);
            if (session.player().getDead()) throw new IllegalStateException("Capture player died during setup");
            Files.createDirectories(out);
            Files.writeString(out.resolve("state.csv"), GameplayCaptureSession.stateHeader() + "\n"
                    + session.stateLine(89, null) + "\n");
            var capture = new OverlayDesignCaptureTool(session, out, width, scale);
            capture.save("00-gameplay", () -> {});
            if (args.length == 5) capture.mutators();
            else capture.overlays();
            if (!session.usesNullAudioBackend())
                throw new IllegalStateException("Overlay drawing replaced the no-device backend");
            System.out.println("Captured " + width + "x224 at scale " + scale + "; audio=NullAudioBackend; out=" + out);
        }
    }

    @FunctionalInterface private interface Draw { void run() throws Exception; }

    private void save(String name, Draw draw) throws Exception {
        session.renderFrame();
        GameServices.graphics().resetForFixedFunction();
        draw.run();
        GameServices.graphics().flushScreenSpace();
        glFinish();
        RgbaImage source = ScreenshotCapture.captureFramebuffer(width, 224);
        RgbaImage scaled = new RgbaImage(width * scale, 224 * scale, new int[width * 224 * scale * scale]);
        for (int y = 0; y < scaled.height(); y++) for (int x = 0; x < scaled.width(); x++)
            scaled.setArgb(x, y, source.argb(x / scale, y / scale));
        ScreenshotCapture.savePNG(scaled, output.resolve(name + ".png"));
        System.out.println("wrote " + name);
    }

    private void overlays() throws Exception {
        var menuText = PixelFontTextRenderer.menu();
        // Transport, recording and trace plates draw with the same authored compact face as Engine.
        var debugText = PixelFontTextRenderer.compactMenu();
        menuText.setProjectionMatrix(projection); debugText.setProjectionMatrix(projection);
        try (var projects = new EditorProjectMenuRenderer();
             var resources = new StageplayResources(game -> null, List.of());
             var surface = new SceneSurface()) {
            var input = new InputHandler();
            var escape = new EscapeToMasterTitleController(() -> false, () -> {});
            input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
            for (int i = 0; i < 30; i++) { escape.update(GameMode.LEVEL, input); input.update(); }
            save("01-escape-holding", () -> toast(menuText, escape, false));
            for (int i = 0; i < 30; i++) { escape.update(GameMode.LEVEL, input); input.update(); }
            save("02-escape-complete", () -> toast(menuText, escape, true));
            escape.reset();
            input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_RELEASE); input.update();
            input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS); escape.update(GameMode.LEVEL, input); input.update();
            input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_RELEASE);
            for (int i = 0; i < 12; i++) { escape.update(GameMode.LEVEL, input); input.update(); }
            save("03-escape-tap", () -> toast(menuText, escape, false));

            Class<?> liveType = Class.forName("com.openggf.game.rewind.LiveRewindHudOverlay");
            var constructor = liveType.getDeclaredConstructor(Supplier.class); constructor.setAccessible(true);
            Object rewind = constructor.newInstance((Supplier<String>) () -> "REWIND 1248");
            var render = liveType.getDeclaredMethod("render", PixelFontTextRenderer.class, com.openggf.graphics.GraphicsManager.class, int.class); render.setAccessible(true);
            save("04-live-rewind", () -> render.invoke(rewind, debugText, GameServices.graphics(), width));

            // Trace states without a rewind: REW outranks ERR, DESYNC and COMPLETE on the plate, so a
            // rewind supplied here (as before 2026-10-28) drew one identical picture for all three.
            var trace = trace(false, false, null);
            save("05-trace-mismatches", () -> trace.render(debugText));
            var paused = trace(true, false, null);
            save("06-trace-paused-desync", () -> paused.render(debugText));
            var complete = trace(false, true, null);
            save("07-trace-complete", () -> complete.render(debugText));
            requireDistinct("05-trace-mismatches", "06-trace-paused-desync", "07-trace-complete");
            // The deliberate control: the same mismatching trace while rewinding shows REW.
            var rewinding = trace(false, false, "REWIND 1248");
            save("07c-trace-rewind", () -> rewinding.render(debugText));
            requireDistinct("05-trace-mismatches", "07c-trace-rewind");
            // The manual details page (debug-exempt dense panel), opened by a physical Tab press on
            // each replay type and on the error state; it never opens by itself.
            tab(paused);
            save("07b-trace-details", () -> paused.render(debugText));
            tab(trace);
            save("07d-trace-details-errors", () -> trace.render(debugText));
            var special = new SpecialStageTraceHudOverlay(sampleModel(false, true), () -> "S2 special stage", () -> null, () -> null);
            save("08-special-stage-trace-layout", () -> special.render(debugText));
            if (special.detailsVisible()) throw new IllegalStateException("details opened without Tab");
            tab(special);
            save("08b-special-stage-trace-details", () -> special.render(debugText));

            var project = new EditorProjectMenu();
            // The production page with no project open (EditorProjectWorkspace.openMenu): its labels and notes.
            project.showActions("Editor projects", List.of(
                    new EditorProjectMenu.Action("New blank level", "Choose a game, zone and act to start from", () -> {}),
                    new EditorProjectMenu.Action("Open project", "Load a saved .ogfproject file", () -> {}),
                    new EditorProjectMenu.Action("Import exported level", "Bring back a level exported from the editor", () -> {}),
                    new EditorProjectMenu.Action("Clone native layout as project", "Copy the level on screen into a new project", () -> {})));
            project.setSubtitle("Create or open an editable project.");
            save("09-editor-project-actions", () -> projects.render(project, projection, width));
            project.showForm("New project", List.of(
                    new EditorProjectMenu.Field("name", "Project name", "Emerald Hill remix", MenuTextEditor.Mode.TEXT),
                    new EditorProjectMenu.Field("game", "Base game", "Sonic 2", List.of("Sonic 1", "Sonic 2", "Sonic 3 & Knuckles")),
                    new EditorProjectMenu.Field("folder", "Project folder", "projects/sonic/emerald-hill-remix", MenuTextEditor.Mode.PATH)),
                    "Create project", values -> {});
            project.setSubtitle("Choose a name, source game and folder.");
            save("10-editor-project-form", () -> projects.render(project, projection, width));
            project.report(new IllegalArgumentException("Choose an empty project folder."));
            save("11-editor-project-error", () -> projects.render(project, projection, width));

            var document = StageplayDocument.empty("s2", 0, 0, 0, 0, width, 224).withDialogue(List.of(
                    new StageplayDocument.DialogueCue("review", 0, 240, "", "Tails",
                            "Ready, Sonic?\nLet's take the high road.", "", "", 12, 144, width - 24, 68, "", List.of())));
            var dialogue = new StageplayRenderer(resources);
            save("12-stageplay-dialogue", () -> {
                surface.record(width, 224, canvas -> dialogue.drawLiveOverlay(document, StageplayTimeline.sample(document, 180), canvas));
                surface.render(projection, new int[]{0, 0, width, 224});
            });
            recording(debugText);
            shader();
        } finally {
            debugText.cleanup(); menuText.cleanup();
        }
    }

    private void toast(PixelFontTextRenderer text, EscapeToMasterTitleController escape, boolean complete) {
        text.beginBatch();
        try { MenuChrome.holdToast(text.font(), width, 6, "Esc", escape.message(),
                (float) escape.progress(), complete, escape.shownFrames()); }
        finally { text.endBatch(); }
    }

    /** One physical Tab press, as the replay HUD receives it in a presentation frame. */
    private static void tab(com.openggf.testmode.TraceHudOverlay overlay) { tab((Object) overlay); }
    private static void tab(SpecialStageTraceHudOverlay overlay) { tab((Object) overlay); }
    private static void tab(Object overlay) {
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        if (overlay instanceof TraceHudOverlay hud) hud.handleInput(input);
        else ((SpecialStageTraceHudOverlay) overlay).handleInput(input);
        input.update();
        boolean open = overlay instanceof TraceHudOverlay hud ? hud.detailsVisible()
                : ((SpecialStageTraceHudOverlay) overlay).detailsVisible();
        if (!open) throw new IllegalStateException("Tab did not open the details page");
    }

    /** Fails the capture when states meant to differ drew the same picture. */
    private void requireDistinct(String... names) throws Exception {
        for (int i = 0; i < names.length; i++) for (int j = i + 1; j < names.length; j++)
            if (java.nio.file.Files.mismatch(output.resolve(names[i] + ".png"), output.resolve(names[j] + ".png")) < 0)
                throw new IllegalStateException(names[i] + " and " + names[j] + " drew the same picture");
    }

    private TraceHudOverlay trace(boolean paused, boolean complete, String rewind) throws Exception {
        var constructor = TraceHudOverlay.class.getDeclaredConstructor(TraceHudModel.class, Supplier.class,
                java.util.function.BooleanSupplier.class, Supplier.class, Supplier.class, boolean.class);
        constructor.setAccessible(true);
        var trace = constructor.newInstance(sampleModel(paused, complete), (Supplier<String>) () -> "SPACE",
                (java.util.function.BooleanSupplier) () -> paused, (Supplier<String>) () -> "Sonic",
                (Supplier<String>) () -> rewind, true);
        var status = TraceHudOverlay.class.getDeclaredMethod("setPlaybackStatusSupplier", Supplier.class); status.setAccessible(true);
        status.invoke(trace, (Supplier<TracePlaybackStatus>) () -> new TracePlaybackStatus(
                "ehz1-review.bk2", "LEVEL", 1248, 3600, "< 1x >"));
        return trace;
    }

    private static TraceHudModel sampleModel(boolean paused, boolean complete) {
        // Each state shows its own plate: errors outrank DESYNC, so the desync and complete
        // states carry no field errors (a recording desync can occur with every field matching).
        boolean clean = paused || complete;
        return new TraceHudModel() {
            public int errorCount() { return clean ? 0 : 3; }
            public int warningCount() { return clean ? 0 : 12; }
            public int laggedFrames() { return 7; }
            public int recentActionMask() { return 1; }
            public int recentInputMask() { return 8; }
            public boolean recentStartPressed() { return false; }
            public boolean hasRecordingDesync() { return paused; }
            public boolean isComplete() { return complete; }
            public List<MismatchEntry> recentMismatches() {
                if (clean) return List.of();
                return List.of(new MismatchEntry(1248, "x_pos", "04A0", "04A2", "2", Severity.ERROR, 3),
                        new MismatchEntry(1247, "y_vel", "0000", "0001", "1", Severity.WARNING, 1));
            }
        };
    }

    private void recording(PixelFontTextRenderer text) throws Exception {
        Object previous = field(session.loop(), "userRecordingControls").get(session.loop());
        try {
            var controls = new UserRecordingRuntimeControls(new SampleRecordingRuntime(null));
            field(controls, "recordHoldFrames").setInt(controls, 30);
            field(session.loop(), "userRecordingControls").set(session.loop(), controls);
            save("13-recording-hold", () -> session.loop().renderUserRecordingHud(text));
            field(session.loop(), "userRecordingControls").set(session.loop(), new UserRecordingRuntimeControls(
                    new SampleRecordingRuntime(new UserRecordingHudState(true, "REC frame 1248", "ehz1-review.bk2", 1248, false, false))));
            save("14-recording-active", () -> session.loop().renderUserRecordingHud(text));
            field(session.loop(), "userRecordingControls").set(session.loop(), new UserRecordingRuntimeControls(new SampleRecordingRuntime(null)));
            save("15-recording-playback", () -> session.loop().renderUserRecordingHud(text));
        } finally { field(session.loop(), "userRecordingControls").set(session.loop(), previous); }
    }

    private record SampleRecordingRuntime(UserRecordingHudState hud) implements UserRecordingRuntimeControls.Runtime {
        public int recordKey() { return GLFW_KEY_F9; }
        public GameMode currentGameMode() { return GameMode.LEVEL; }
        public boolean traceOrDebugSurfaceOwnsRecordingInput() { return false; }
        public boolean hasActiveRecording() { return hud != null; }
        public void beginRecordingFromCurrentLevel() { throw new UnsupportedOperationException("draw-only sample"); }
        public void stopActiveRecording(UserRecordingStopReason reason) { throw new UnsupportedOperationException("draw-only sample"); }
        public UserRecordingHudState activeRecordingHudState() { return hud; }
        public UserRecordingPlaybackOptions activePlaybackOptions() { return new UserRecordingPlaybackOptions(3599, true, false); }
        public UserRecordingPlaybackState activePlaybackState() { return UserRecordingPlaybackState.PLAYING; }
        public int currentPlaybackFrame() { return 1248; }
        public int playbackFrameCount() { return 3600; }
    }

    private void shader() throws Exception {
        // Draw-only facade. Do not call Engine.init/run/cleanup: the capture session owns services/GL.
        Field instance = Engine.class.getDeclaredField("instance"); instance.setAccessible(true);
        Object previousEngine = instance.get(null);
        var engine = new Engine();
        field(engine, "projectionWidth").setDouble(engine, width);
        field(engine, "realHeight").setDouble(engine, 224);
        System.arraycopy(projection, 0, (float[]) field(engine, "matrixBuffer").get(engine), 0, 16);
        var picker = new DisplayShaderPickerController(new DisplayShaderSelectionModel(List.of(
                new DisplayShaderPresetRef(DisplayShaderPresetRef.Kind.GLSL, "crt/scanlines.glsl", Path.of("shaders/crt/scanlines.glsl")),
                new DisplayShaderPresetRef(DisplayShaderPresetRef.Kind.GLSL, "lcd/handheld.glsl", Path.of("shaders/lcd/handheld.glsl")))), GLFW_KEY_F8);
        var input = new InputHandler(); input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS); picker.update(input, DisplayShaderPresetRef.OFF);
        field(engine, "displayShaderPickerController").set(engine, picker);
        var draw = Engine.class.getDeclaredMethod("renderDisplayShaderPickerOverlay"); draw.setAccessible(true);
        var text = (PixelFontTextRenderer) field(engine, "traceHudTextRenderer").get(engine);
        try {
            save("16-shader-picker", () -> draw.invoke(engine));
            save("16b-shader-picker-settled", () -> draw.invoke(engine));
            // The window recorder deliberately excludes these indicators from its output. Draw
            // the production indicator directly here without starting a recording or a window.
            var plate = Engine.class.getDeclaredMethod("renderLiveCapturePlate", LiveCaptureController.Interruption.class);
            plate.setAccessible(true);
            save("19-video-recording-indicator", () -> plate.invoke(engine, (Object) null));
            save("20-video-recording-interrupted", () -> plate.invoke(engine, LiveCaptureController.Interruption.WINDOW_RESIZED));
            save("20b-video-recording-interrupted-settled", () -> plate.invoke(engine, LiveCaptureController.Interruption.CAPTURE_ERROR));
            var pause = Engine.class.getDeclaredMethod("renderUserPauseIndicator"); pause.setAccessible(true);
            save("21-user-pause", () -> pause.invoke(engine));
            sessionStatus(engine, pause, plate);
        }
        finally {
            text.cleanup();
            ((AsyncScreenshotWriter) field(engine, "screenshotWriter").get(engine)).close();
            instance.set(null, previousEngine);
        }
    }

    /**
     * Every engine-session status at once: the held Escape toast with the pause plate, then with
     * an interrupted and an active window video plate, drawn in Engine's order by its own
     * renderers and toast placement. Draw-only: the pause flag and capture state are set on the
     * facade and restored; no recording starts and no gameplay advances.
     */
    private void sessionStatus(Engine engine, java.lang.reflect.Method pause, java.lang.reflect.Method plate) throws Exception {
        var loop = (com.openggf.GameLoop) field(engine, "gameLoop").get(engine);
        Field paused = com.openggf.GameLoop.class.getDeclaredField("userPaused"); paused.setAccessible(true);
        Field interruption = field(engine, "liveCaptureInterruption");
        var capture = (LiveCaptureController) field(engine, "liveCaptureController").get(engine);
        Field state = LiveCaptureController.class.getDeclaredField("state"); state.setAccessible(true);
        Object idle = state.get(capture);
        var prompt = Engine.class.getDeclaredMethod("renderEscapeToMasterTitlePrompt",
                com.openggf.graphics.pipeline.RenderOrderRecorder.class);
        prompt.setAccessible(true);
        var escape = loop.getEscapeToMasterTitleController();
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
        for (int i = 0; i < 30; i++) { escape.update(GameMode.LEVEL, input); input.update(); }
        try {
            paused.setBoolean(loop, true);
            save("22-escape-over-pause", () -> { prompt.invoke(engine, (Object) null); pause.invoke(engine); });
            interruption.set(engine, LiveCaptureController.Interruption.WINDOW_RESIZED);
            save("23-escape-over-video-stopped-and-pause", () -> {
                prompt.invoke(engine, (Object) null); pause.invoke(engine);
                plate.invoke(engine, LiveCaptureController.Interruption.WINDOW_RESIZED);
            });
            interruption.set(engine, null);
            state.set(capture, LiveCaptureController.State.ACTIVE);
            save("24-escape-over-video-and-pause", () -> {
                prompt.invoke(engine, (Object) null); pause.invoke(engine); plate.invoke(engine, (Object) null);
            });
            // Motion: the same two plates while the toast slides in (frames 1-10 of the hold) and out
            // after release, so transient positions are pictured as well as the resting one.
            escape.reset();
            input = new InputHandler();
            input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
            for (int frame = 1; frame <= EscapeToMasterTitleController.SLIDE_FRAMES; frame++) {
                escape.update(GameMode.LEVEL, input); input.update();
                save(String.format("25-escape-enter-f%02d", frame), () -> {
                    prompt.invoke(engine, (Object) null); pause.invoke(engine); plate.invoke(engine, (Object) null);
                });
            }
            input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_RELEASE);
            for (int frame = 1; escape.presence() > 0f && frame <= 240; frame++) {
                escape.update(GameMode.LEVEL, input); input.update();
                if (escape.presence() < 1f) save(String.format("26-escape-leave-f%03d", frame), () -> {
                    prompt.invoke(engine, (Object) null); pause.invoke(engine); plate.invoke(engine, (Object) null);
                });
            }
            // Pause toggled mid-hold (G4): the plate leaving lets the toast ease up along a clear path;
            // the plate returning under it moves it at once. Video stays off so the top row is free.
            escape.reset();
            state.set(capture, idle);
            input = new InputHandler();
            input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS);
            for (int frame = 1; frame <= EscapeToMasterTitleController.SLIDE_FRAMES + 4; frame++) {
                escape.update(GameMode.LEVEL, input); input.update();
                prompt.invoke(engine, (Object) null); // the toast settles under the pause plate
            }
            paused.setBoolean(loop, false);
            for (int frame = 1; frame <= 8; frame++) {
                escape.update(GameMode.LEVEL, input); input.update();
                save(String.format("27-escape-pause-off-f%02d", frame), () -> { prompt.invoke(engine, (Object) null); pause.invoke(engine); });
            }
            paused.setBoolean(loop, true);
            for (int frame = 1; frame <= 3; frame++) {
                escape.update(GameMode.LEVEL, input); input.update();
                save(String.format("28-escape-pause-on-f%02d", frame), () -> { prompt.invoke(engine, (Object) null); pause.invoke(engine); });
            }
        } finally {
            state.set(capture, idle);
            interruption.set(engine, null);
            paused.setBoolean(loop, false);
            escape.reset();
        }
    }

    private void mutators() throws Exception {
        LevelInputOverlay overlay = GameServices.module().getGameService(LevelInputOverlay.class);
        if (overlay == null || MutatorWorldAccess.state(SessionManager.getCurrentWorldSession()) == null)
            throw new IllegalArgumentException("Supplied mod must prepare a mutator world and LevelInputOverlay");
        // Creator callbacks arrive through the host's interface projection, not their concrete class.
        LevelInputOverlay screen = overlay;
        var input = new InputHandler();
        press(screen, input, GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY));
        for (int i = 0; i < 20; i++) screen.handleInput(input);
        save("17-mutator-list", () -> {});
        press(screen, input, GLFW_KEY_ENTER);
        for (int i = 0; i < 20; i++) screen.handleInput(input);
        save("18-mutator-options", () -> {});
    }

    private static void press(LevelInputOverlay screen, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS); screen.handleInput(input); input.update();
        input.handleKeyEvent(key, GLFW_RELEASE); screen.handleInput(input); input.update();
    }

    private static Field field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
}
