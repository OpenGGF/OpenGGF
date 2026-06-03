package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.audio.AudioManager;
import com.openggf.capture.AudioFrameTap;
import com.openggf.capture.CaptureException;
import com.openggf.capture.CaptureRecorder;
import com.openggf.capture.CapturedFrame;
import com.openggf.capture.VideoFrameGrabber;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.trace.replay.TraceReplayDriver;

import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Ties together a booted {@link GameLoop}, a deterministic {@link TraceReplayDriver},
 * a {@link VideoFrameGrabber}, an {@link AudioFrameTap}, and a {@link CaptureRecorder}
 * into a per-frame capture loop: <em>step → render → grab → submit</em>.
 *
 * <p>Per frame, {@link #stepAndCapture()}:
 * <ol>
 *   <li>returns {@code false} when {@link TraceReplayDriver#isComplete()} (the
 *       trace has been fully replayed);</li>
 *   <li>advances the game one tick via {@link GameLoop#step()} (audio advances
 *       inside the tick via {@code advanceGameplayAudioFrameForTick});</li>
 *   <li>renders the LEVEL scene the same way {@code Engine.draw()} does — clear
 *       with the level's background colour, {@code drawWithSpritePriority}, flush,
 *       {@code glFinish};</li>
 *   <li>grabs the back buffer as RGBA, drains the frame's stereo PCM, and submits
 *       a {@link CapturedFrame} to the recorder.</li>
 * </ol>
 *
 * <p>This class assumes a current GL context on the calling thread (the headless
 * boot owns it) and that the {@link TraceReplayDriver} has already had
 * {@code start(zone, act)} run, so the comparator/observer are wired before the
 * first step.
 */
public final class TraceCaptureSession {

    private final GameLoop loop;
    private final TraceReplayDriver driver;
    private final VideoFrameGrabber grabber;
    private final AudioFrameTap audioTap;
    private final CaptureRecorder recorder;
    private final int fps;

    private final int width;
    private final int height;

    // Reusable PCM drain buffer. Sized far above any plausible per-frame stereo
    // sample count (e.g. 48000/60 = 800 stereo frames -> 1600 shorts); the
    // CapturedFrame constructor defensively copies it, so reuse is safe.
    private final short[] pcmBuffer = new short[16384];

    private long frameIndex;
    private boolean started;

    public TraceCaptureSession(GameLoop loop, TraceReplayDriver driver,
                               VideoFrameGrabber grabber, AudioFrameTap audioTap,
                               CaptureRecorder recorder, int fps) {
        this.loop = loop;
        this.driver = driver;
        this.grabber = grabber;
        this.audioTap = audioTap;
        this.recorder = recorder;
        this.fps = fps;
        this.width = grabber.width();
        this.height = grabber.height();
    }

    /**
     * Installs deterministic audio capture mode and opens the recorder. Must be
     * called once before the first {@link #stepAndCapture()}.
     */
    public void start(int width, int height, int sampleRate) throws CaptureException {
        if (width != this.width || height != this.height) {
            throw new CaptureException("capture dimensions " + width + "x" + height
                    + " do not match grabber " + this.width + "x" + this.height);
        }
        AudioManager.getInstance().beginCaptureMode(sampleRate, fps);
        recorder.start(width, height, fps, sampleRate);
        started = true;
    }

    /**
     * Advances one frame and captures it.
     *
     * @return {@code false} once the trace is complete (no frame captured);
     *         {@code true} after a frame was rendered, grabbed, and submitted.
     */
    public boolean stepAndCapture() throws CaptureException {
        if (!started) {
            throw new CaptureException("start() must be called before stepAndCapture()");
        }
        if (driver.isComplete()) {
            return false;
        }

        // 1. Advance the game one tick. Audio advances inside the tick via
        //    GameLoop.advanceGameplayAudioFrameForTick(...).
        loop.step();

        // 2. Render the LEVEL scene the same way Engine.draw() does for the
        //    default (non-debug) LEVEL path.
        renderFrame();

        // 3. Grab the rendered back buffer as RGBA (bottom-up; ffmpeg vflip
        //    corrects orientation downstream).
        byte[] rgba = grabber.grab();

        // 4. Drain this frame's stereo PCM.
        int sampleCount = audioTap.drain(pcmBuffer);

        // 5. Submit the captured frame.
        recorder.submit(new CapturedFrame(rgba, width, height,
                pcmBuffer, sampleCount, frameIndex++));
        return true;
    }

    /** Finalizes the recording and tears down audio capture mode. */
    public Path finish() throws CaptureException {
        try {
            return recorder.stop();
        } finally {
            AudioManager.getInstance().endCaptureMode();
        }
    }

    /**
     * Renders the current LEVEL scene to the back buffer. Mirrors the default
     * LEVEL branch of {@code Engine.draw()} / the headless render in
     * {@code VisualReferenceGenerator}: clear with the level background colour,
     * draw level + sprites by priority, flush the command queue, and block until
     * GL is finished so the subsequent {@code glReadPixels} sees the completed
     * frame.
     */
    private void renderFrame() {
        LevelManager levelManager = GameServices.level();
        GraphicsManager graphicsManager = GameServices.graphics();

        levelManager.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        levelManager.drawWithSpritePriority(GameServices.sprites());

        graphicsManager.flush();
        glFinish();
    }
}
