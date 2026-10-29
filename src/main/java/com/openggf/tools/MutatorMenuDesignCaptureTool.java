package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.mutators.MutatorConfigurationScreen;
import com.openggf.mods.mutators.MutatorWorldAccess;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Purpose: inspect Mutator catalogue, option widgets, availability and pending-boundary presentation.
 * Inputs: {@code <S2-ROM> <new-output-dir> <width=320|400> <scale> <example-mutators.jar>}.
 * Origin: 2026-10-27 menu design review round 4.
 *
 * <p>Requires display-free surfaceless EGL and an owned openggf.saveRoot; never uses desktop input.
 * A production capture session prepares the real example catalogue and installs NullAudioBackend.
 * Scripted menu inputs edit that temporary world's preferences, without resuming/publishing edits.
 * Title scenes include the supported plain-card variant over held gameplay and the inherited
 * native title after its own state machine reaches ACTIVE. No fabricated definitions or gameplay state.
 * Writes nearest-neighbour PNGs and the held gameplay state; refuses an existing output directory.
 */
public final class MutatorMenuDesignCaptureTool {
    private final GameplayCaptureSession session;
    private final MutatorConfigurationScreen screen;
    private final InputHandler input = new InputHandler();
    private final Path output;
    private final int width, scale;

    private MutatorMenuDesignCaptureTool(GameplayCaptureSession session, Path output, int width, int scale) {
        this.session = session; this.output = output; this.width = width; this.scale = scale;
        screen = new MutatorConfigurationScreen(SessionManager.getCurrentWorldSession(), "Mutator Lab", null, cue -> {});
        screen.initialize();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException(
                "usage: MutatorMenuDesignCaptureTool <S2-ROM> <new-output-dir> <width> <scale> <example-mutators.jar>");
        if (!SurfacelessEglContext.requested() || System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null)
            throw new IllegalStateException("Requires surfaceless EGL with DISPLAY/WAYLAND_DISPLAY unset");
        if (System.getProperty("openggf.saveRoot", "").isBlank())
            throw new IllegalStateException("Supply an owned openggf.saveRoot for temporary preferences");
        var rom = Path.of(args[0]).toAbsolutePath();
        var output = Path.of(args[1]).toAbsolutePath();
        int width = Integer.parseInt(args[2]), scale = Integer.parseInt(args[3]);
        if (!Files.isRegularFile(rom) || Files.exists(output) || (width != 320 && width != 400) || scale < 1 || scale > 8)
            throw new IllegalArgumentException("Existing ROM, new output directory, width 320/400 and scale 1..8 required");
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.applyMod(Path.of(args[4]));
            session.boot(rom, 0, 0, settings);
            if (MutatorWorldAccess.state(SessionManager.getCurrentWorldSession()) == null)
                throw new IllegalArgumentException("The supplied mod must prepare a Mutator catalogue");
            for (int frame = 0; frame < 90; frame++) session.step(null);
            if (session.player().getDead()) throw new IllegalStateException("Setup player died");
            Files.createDirectories(output);
            Files.writeString(output.resolve("state.csv"), GameplayCaptureSession.stateHeader() + "\n"
                    + session.stateLine(89, null) + "\n");
            var capture = new MutatorMenuDesignCaptureTool(session, output, width, scale);
            try { capture.scenes(); } finally { capture.screen.close(); }
            if (!session.usesNullAudioBackend())
                throw new IllegalStateException("Capture must retain NullAudioBackend");
        }
        // Title PLC/timing services belong to the production title-first driver, not the held level.
        try (var session = new GameplayCaptureSession(settings)) {
            session.applyMod(Path.of(args[4])); session.startAtTitle(); session.boot(rom, 0, 0, settings);
            var nativeTitle=SessionManager.getCurrentWorldSession().rootGameModule().getTitleScreenProvider();
            int frame=0;
            while(nativeTitle.getState()!=com.openggf.game.TitleScreenProvider.State.ACTIVE && frame<1800) {
                session.step(null); frame++;
            }
            if(nativeTitle.getState()!=com.openggf.game.TitleScreenProvider.State.ACTIVE)
                throw new IllegalStateException("Native title did not become interactive");
            for(int i=0;i<360;i++) { session.step(null); frame++; }
            if(!session.usesNullAudioBackend())
                throw new IllegalStateException("Native title replaced the no-device backend");
            writeImage(session.render(),output.resolve("13-native-title-home.png"),width,scale);
            Files.writeString(output.resolve("native-title-state.csv"),GameplayCaptureSession.stateHeaderWithHostState()
                    +"\n"+session.stateLineWithHostState(frame-1,null)+"\n");
        }
        System.out.println("Captured Mutators " + width + "x224; audio=NullAudioBackend; out=" + output);
    }

    private boolean paused;
    private void press(int key) {
        input.handleKeyEvent(key, GLFW_PRESS); update(); input.update();
        input.handleKeyEvent(key, GLFW_RELEASE); update(); input.update();
    }
    private void update() { if (paused) screen.handleInput(input); else screen.update(input); }
    private void down(int count) { for (int i = 0; i < count; i++) press(GLFW_KEY_DOWN); }
    private int index(String id) {
        var definitions = MutatorWorldAccess.state(SessionManager.getCurrentWorldSession()).definitions();
        for (int i = 0; i < definitions.size(); i++)
            if (definitions.get(i).definition().localId().equals(id)) return i;
        throw new IllegalArgumentException("Example catalogue lacks " + id);
    }
    private void scenes() throws Exception {
        save("01-home");
        down(2); press(GLFW_KEY_ENTER); save("02-help"); press(GLFW_KEY_ESCAPE);
        press(GLFW_KEY_UP); press(GLFW_KEY_ENTER); save("03-catalogue");
        press(GLFW_KEY_ENTER); down(1); save("04-slider");
        press(GLFW_KEY_RIGHT); save("05-slider-edited"); press(GLFW_KEY_ESCAPE);
        down(index("no-bonus-stages")); save("06-unavailable-scrolled");
        press(GLFW_KEY_ENTER); save("07-unavailable-options"); press(GLFW_KEY_ESCAPE);
        press(GLFW_KEY_ESCAPE); // Home, then open the real pause catalogue without advancing gameplay.
        paused = true; press(GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY));
        down(index("no-powerups")); press(GLFW_KEY_RIGHT); save("08-pending-restart");
        press(GLFW_KEY_ENTER); down(1); save("09-checkbox-options");
        down(6); save("10-unavailable-checkbox"); press(GLFW_KEY_ESCAPE);
        // Back lands on No Powerups. Walk to Big Head without resetting the draft.
        press(GLFW_KEY_UP); press(GLFW_KEY_UP); press(GLFW_KEY_ENTER);
        down(1); save("11-grouped-slider"); down(2); save("12-grouped-targets");

    }
    private void save(String name) throws Exception {
        for (int i = 0; i < 24; i++) { update(); input.update(); }
        if (!session.usesNullAudioBackend())
            throw new IllegalStateException("No-device backend required before drawing");
        session.renderFrame();
        GameServices.graphics().resetForFixedFunction();
        if (paused) screen.drawOverlay(); else screen.draw();
        GameServices.graphics().flushScreenSpace(); glFinish();
        writeImage(ScreenshotCapture.captureFramebuffer(width,224),output.resolve(name+".png"),width,scale);
        System.out.println("wrote " + name);
    }
    private static void writeImage(RgbaImage source, Path path, int width, int scale) throws Exception {
        var scaled=new RgbaImage(width*scale,224*scale,new int[width*224*scale*scale]);
        for(int y=0;y<scaled.height();y++) for(int x=0;x<scaled.width();x++)
            scaled.setArgb(x,y,source.argb(x/scale,y/scale));
        ScreenshotCapture.savePNG(scaled,path);
    }
}
