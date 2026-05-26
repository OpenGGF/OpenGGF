package com.openggf.audio;

import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration acceptance tests for {@code LWJGLAudioBackend} ->
 * {@link StreamBackedDeterministicAudioRuntime}. Exercises routing, ownership,
 * and the startup-deferral invariant without touching OpenAL.
 *
 * <p>Per the plan addendum (Path B), the test fixture is a sibling on
 * {@link NullAudioBackend} because {@code LWJGLAudioBackend.init()} is too
 * OpenAL-coupled to subclass cleanly. The fixture advertises
 * {@code supportsDeterministicRuntimePresentation() == true} so
 * {@code AudioManager.setBackend} installs a real
 * {@link StreamBackedDeterministicAudioRuntime}, and captures it so the tests
 * can drive runtime-side invariants directly.
 *
 * <p><b>Scope and trade-off.</b> The migration's invariants split into two
 * layers:
 * <ul>
 *   <li><b>Runtime-side invariants</b> -- bound streams produce PCM through the
 *       runtime's FIFO, the reverse cursor drains the history ring backwards,
 *       the attach-time presentation contract holds. These tests exercise that
 *       layer directly through the Path-B fixture.</li>
 *   <li><b>Backend-side routing invariants</b> -- {@code playSfxSmps} routing
 *       (mix into music driver vs standalone), {@code stopStream} runtime
 *       clearing, {@code doRestoreMusic} rebinding, {@code playSmps} non-
 *       override standalone-SFX clearing. These live inside
 *       {@code LWJGLAudioBackend} and cannot be driven without OpenAL natives
 *       (every entry point calls {@code alSourceStop(musicSource)}
 *       unconditionally before any guard). The plan addendum documents that
 *       constructing a real {@code LWJGLAudioBackend} without {@code init()}
 *       is too fragile to test against in headless CI.</li>
 * </ul>
 * Backend-side routing is covered indirectly by:
 * <ul>
 *   <li>The migration's existing unit tests for command replay, logical
 *       snapshot, and rewind-suppression flows ({@code TestAudioKeyframeReplay},
 *       {@code TestAudioLogicalSnapshot}, {@code TestAudioManagerRewindSuppression}).</li>
 *   <li>The manual smoke step documented in {@code CLAUDE.md}: boot each game,
 *       play music + SFX, hold rewind, restore. This is the final acceptance
 *       gate documented in the spec.</li>
 * </ul>
 */
class TestLwjglRuntimePresentationRoundTrip {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    /**
     * Test #1 (forward music routes through runtime). Path B simplification per
     * the task addendum: with no OpenAL prefill in the fixture there is no
     * FIFO-drain-by-prefill problem to assert against. The invariant we lock
     * here is that PCM bound to the runtime's music stream actually routes
     * through the runtime's FIFO and produces the expected samples.
     */
    @Test
    void forwardMusicProducesPcmThroughRuntime() {
        HeadlessPresentationBackend backend = new HeadlessPresentationBackend();
        AudioManager.getInstance().setBackend(backend);

        DeterministicAudioRuntime runtime = backend.runtime;
        assertNotNull(runtime, "AudioManager must have attached a runtime");
        assertInstanceOf(StreamBackedDeterministicAudioRuntime.class, runtime,
                "Presentation backend must receive StreamBackedDeterministicAudioRuntime");

        ScriptedAudioStream ramp = new ScriptedAudioStream((short) 0, (short) 1);
        runtime.setMusicStream(ramp);
        assertTrue(runtime.hasActivePresentation(),
                "Binding a music stream must mark presentation as active");

        // Drive one frame of synthesis through the runtime. With the fixture's
        // 120 Hz sample rate and the default 60 Hz frame rate, this enqueues
        // 2 stereo frames (4 samples) into the FIFO.
        runtime.advanceFrame(0L, FrameAudioMode.NORMAL);

        short[] buf = new short[4];
        int drained = runtime.drainPcm(buf, 2);
        assertEquals(2, drained, "Drain must return the frames advanceFrame produced");

        // ScriptedAudioStream emits 0,1,2,3,... starting at 0 across both
        // channels of both frames. The runtime fills its scratch buffer via
        // stream.read(scratch), and scratch is samples = framesPerFrame * 2.
        for (short i = 0; i < 4; i++) {
            assertEquals(i, buf[i], "ramp sample at index " + i);
        }
    }

    /**
     * Test #4 (reverse playback drains from runtime ring). Forward-steps
     * several frames so the runtime's PcmHistoryRing has known content, then
     * enters reverse presentation and asserts the cursor reads the ring
     * backwards.
     */
    @Test
    void reversePlaybackDrainsFromRuntimeRing() {
        HeadlessPresentationBackend backend = new HeadlessPresentationBackend();
        AudioManager.getInstance().setBackend(backend);

        DeterministicAudioRuntime runtime = backend.runtime;
        assertNotNull(runtime);

        ScriptedAudioStream ramp = new ScriptedAudioStream((short) 100, (short) 1);
        runtime.setMusicStream(ramp);

        // Forward-step 4 frames to populate the history ring (each frame is
        // 2 stereo frames at 120/60, so 4 frames = 8 stereo frames = 16 samples
        // total in the history).
        for (int frame = 0; frame < 4; frame++) {
            runtime.advanceFrame(frame, FrameAudioMode.NORMAL);
        }

        // Drain forward FIFO so it doesn't interfere with the reverse drain.
        short[] forwardScratch = new short[16];
        runtime.drainPcm(forwardScratch, 8);

        // Capture the last forward sample for the reverse-order assertion. The
        // ramp produces 100..115 across the 16 samples (read order); the most
        // recent stereo frame is samples 14 and 15 (114, 115).
        short lastForwardLeft = forwardScratch[14];
        short lastForwardRight = forwardScratch[15];
        assertEquals((short) 114, lastForwardLeft);
        assertEquals((short) 115, lastForwardRight);

        // Unbind the music stream so the reverse cursor reads pristine history
        // (avoids ScriptedAudioStream continuing to ramp into the FIFO mid-test
        // via any spurious advance).
        runtime.setMusicStream(null);

        AudioManager.getInstance().beginReverseAudioPresentation();
        assertTrue(runtime.hasActivePresentation(),
                "Reverse presentation must report active");

        short[] reverseBuf = new short[4];
        int read = runtime.drainPcm(reverseBuf, 2);
        assertEquals(2, read, "Reverse drain must return requested frames");

        // ReverseCursor walks the ring backwards in stereo-frame order: the
        // first frame returned is the most recent forward frame (114, 115),
        // then the prior frame (112, 113). Stereo channels within a frame are
        // NOT swapped -- they remain (left, right).
        assertEquals((short) 114, reverseBuf[0], "first reverse frame left channel");
        assertEquals((short) 115, reverseBuf[1], "first reverse frame right channel");
        assertEquals((short) 112, reverseBuf[2], "second reverse frame left channel");
        assertEquals((short) 113, reverseBuf[3], "second reverse frame right channel");

        AudioManager.getInstance().endReverseAudioPresentation();
    }

    /**
     * Locks the migration-defining attach invariant: any backend that
     * advertises {@code supportsDeterministicRuntimePresentation() == true}
     * MUST receive a runtime whose {@code providesPresentationPcm() == true}.
     *
     * <p>{@code TestAudioManagerRuntimeInstallation} also locks this in
     * isolation; the assertion is repeated here as part of the round-trip
     * coverage so this file stands alone as the migration acceptance net.
     */
    @Test
    void presentationBackendReceivesPresentationRuntime() {
        HeadlessPresentationBackend backend = new HeadlessPresentationBackend();

        AudioManager.getInstance().setBackend(backend);

        assertNotNull(backend.runtime, "Backend must have been attached a runtime");
        assertTrue(backend.runtime.providesPresentationPcm(),
                "Presentation-capable backend must receive a presentation runtime");
        assertInstanceOf(StreamBackedDeterministicAudioRuntime.class, backend.runtime,
                "Presentation runtime must be StreamBackedDeterministicAudioRuntime");
    }

    /**
     * Locks the {@code hasActivePresentation} predicate the migration added to
     * the runtime. Without a bound stream and no FIFO data, the runtime must
     * report idle so {@code LWJGLAudioBackend.updateStream}'s {@code hasStream}
     * predicate idles correctly (architecture item 11 in the spec).
     */
    @Test
    void freshRuntimeReportsNoActivePresentationUntilStreamBound() {
        HeadlessPresentationBackend backend = new HeadlessPresentationBackend();
        AudioManager.getInstance().setBackend(backend);

        DeterministicAudioRuntime runtime = backend.runtime;
        assertNotNull(runtime);
        assertFalse(runtime.hasActivePresentation(),
                "Fresh runtime must report no active presentation");

        runtime.setMusicStream(new ScriptedAudioStream((short) 0, (short) 0));
        assertTrue(runtime.hasActivePresentation(),
                "Binding a music stream must activate presentation");

        runtime.clearMusicStream();
        runtime.flushPresentationFifo();
        assertFalse(runtime.hasActivePresentation(),
                "Clearing the stream and flushing the FIFO must idle presentation");
    }

    /**
     * Locks the SFX binding leg of the routing contract. When a backend binds
     * an SFX stream into the runtime, {@code hasActivePresentation} reports
     * active until that stream is cleared. This is the runtime-side half of
     * test #3 in the spec ("standalone SFX activates when no music"); the
     * backend-side half that asserts {@code LWJGLAudioBackend.playSfxSmps}
     * actually calls {@code runtime.setSfxStream} cannot be tested in
     * headless CI -- see the class-level Javadoc.
     */
    @Test
    void sfxStreamBindingActivatesPresentation() {
        HeadlessPresentationBackend backend = new HeadlessPresentationBackend();
        AudioManager.getInstance().setBackend(backend);

        DeterministicAudioRuntime runtime = backend.runtime;
        assertNotNull(runtime);
        assertFalse(runtime.hasActivePresentation());

        ScriptedAudioStream sfx = new ScriptedAudioStream((short) 50, (short) 0);
        runtime.setSfxStream(sfx);
        assertTrue(runtime.hasActivePresentation(),
                "Binding an SFX stream must activate presentation");

        runtime.clearSfxStream();
        assertFalse(runtime.hasActivePresentation(),
                "Clearing the SFX stream must idle presentation when no other"
                        + " state is active");
    }

    /**
     * Locks the music-then-SFX mixing invariant at the runtime level. When
     * both streams are bound, {@link StreamBackedDeterministicAudioRuntime}
     * mixes SFX into music with saturation clamping (covered in
     * {@code TestStreamBackedDeterministicAudioRuntime}); here we assert the
     * round-trip wiring: AudioManager-installed runtime, music + SFX bound,
     * single advanceFrame produces mixed PCM.
     */
    @Test
    void musicAndSfxMixThroughRuntime() {
        HeadlessPresentationBackend backend = new HeadlessPresentationBackend();
        AudioManager.getInstance().setBackend(backend);

        DeterministicAudioRuntime runtime = backend.runtime;
        assertNotNull(runtime);

        ScriptedAudioStream music = new ScriptedAudioStream((short) 100, (short) 0);
        ScriptedAudioStream sfx = new ScriptedAudioStream((short) 20, (short) 0);
        runtime.setMusicStream(music);
        runtime.setSfxStream(sfx);

        runtime.advanceFrame(0L, FrameAudioMode.NORMAL);

        short[] buf = new short[4];
        runtime.drainPcm(buf, 2);
        for (int i = 0; i < 4; i++) {
            assertEquals((short) 120, buf[i],
                    "Mixed sample at index " + i + " must be music+sfx (100+20)");
        }
    }

    /**
     * Headless presentation backend. Sibling of {@link NullAudioBackend} that
     * advertises deterministic-runtime presentation support so AudioManager
     * installs a real {@link StreamBackedDeterministicAudioRuntime}. The
     * attached runtime is captured for the tests to drive directly.
     *
     * <p>This fixture intentionally does NOT extend or mimic
     * {@code LWJGLAudioBackend}'s {@code playSmps} / {@code playSfxSmps} /
     * {@code stopStream} orchestration. Per the plan addendum (Path B), those
     * code paths require OpenAL natives and cannot be exercised in headless
     * CI; routing tests that depend on them are documented in the class-level
     * Javadoc as out of scope for this file.
     */
    private static final class HeadlessPresentationBackend extends NullAudioBackend {
        DeterministicAudioRuntime runtime;

        @Override
        public boolean supportsDeterministicRuntimePresentation() {
            return true;
        }

        @Override
        public int outputSampleRate() {
            // Small but workable -- 120 Hz at 60 fps gives 2 stereo frames per
            // tick, which is enough to assert ramp ordering and reverse
            // ordering without inflating buffer sizes.
            return 120;
        }

        @Override
        public void attachDeterministicAudioRuntime(DeterministicAudioRuntime runtime) {
            this.runtime = runtime;
        }
    }
}
