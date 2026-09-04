package com.openggf.audio.presentation;

import com.openggf.audio.StreamedMusicPort;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamedPresentationSession {

    @Test
    void trackReplacementDoesNotLetOldVoiceStopNewVoice() {
        Fixture fixture = new Fixture();
        StreamedMusicVoice first = fixture.track(1, "first");
        mix(first);
        StreamedMusicVoice second = fixture.track(2, "second");
        mix(second);

        first.stop();

        assertFalse(second.isComplete());
        assertEquals("second", fixture.port.current.track().name());
        assertEquals(201, mix(second));
    }

    @Test
    void baseOverrideRestoreKeepsIndependentCursors() {
        Fixture fixture = new Fixture();
        StreamedMusicVoice base = fixture.stock(1, 0x81);
        assertEquals(100, mix(base));
        PresentationVoiceSnapshot.Streamed savedBase =
                (PresentationVoiceSnapshot.Streamed) base.snapshot();

        StreamedMusicVoice override = fixture.stock(2, 0x82);
        assertEquals(200, mix(override));
        override.stop();

        assertEquals(savedBase.playback().sourceFramePosition(),
                ((PresentationVoiceSnapshot.Streamed) base.snapshot())
                        .playback().sourceFramePosition());
        assertEquals(101, mix(base));
    }

    @Test
    void nestedOverrideAndRetriggerRetireOnlyTheirOwnCursor() {
        Fixture fixture = new Fixture();
        StreamedMusicVoice base = fixture.stock(1, 0x81);
        StreamedMusicVoice firstOverride = fixture.stock(2, 0x82);
        StreamedMusicVoice nested = fixture.track(3, "nested");
        mix(base);
        mix(firstOverride);
        mix(nested);

        StreamedMusicVoice retrigger = fixture.track(4, "nested");
        mix(retrigger);
        nested.stop();
        firstOverride.stop();

        assertFalse(retrigger.isComplete());
        assertEquals("nested", fixture.port.current.track().name());
        assertEquals(301, mix(retrigger));
    }

    @Test
    void preparedSnapshotRestoreDoesNotDisturbPublishedVoice() {
        Fixture fixture = new Fixture();
        StreamedMusicVoice published = fixture.track(1, "first");
        mix(published);
        PresentationVoiceSnapshot.Streamed snapshot =
                (PresentationVoiceSnapshot.Streamed) published.snapshot();

        StreamedMusicVoice disturbance = fixture.track(2, "second");
        mix(disturbance);
        StreamedMusicVoice prepared = fixture.restore(snapshot);

        assertEquals("second", fixture.port.current.track().name(),
                "preparation must restore the physically active cursor");
        assertEquals(201, mix(disturbance));

        published.stop();
        disturbance.stop();
        assertEquals(101, mix(prepared));
        assertEquals(snapshot.playback().sourceFramePosition() + 1,
                ((PresentationVoiceSnapshot.Streamed) prepared.snapshot())
                        .playback().sourceFramePosition());
    }

    @Test
    void detachRetiresEveryCursorBeforeManagerClosesPort() {
        Fixture fixture = new Fixture();
        StreamedMusicVoice first = fixture.track(1, "first");
        StreamedMusicVoice second = fixture.track(2, "second");
        mix(second);
        int stopsBeforeDetach = fixture.port.stopCount;

        fixture.session.detach();
        fixture.port.close();

        assertTrue(first.isComplete());
        assertTrue(second.isComplete());
        assertEquals(1, fixture.port.closeCount);
        assertEquals(stopsBeforeDetach + 1, fixture.port.stopCount,
                "session retirement stops the physical cursor once");
    }

    @Test
    void commandRollbackCanReviveAStoppedCursorWithoutDisturbingAnother() {
        Fixture fixture = new Fixture();
        StreamedMusicVoice first = fixture.track(1, "first");
        mix(first);
        PresentationVoiceSnapshot.Streamed before =
                (PresentationVoiceSnapshot.Streamed) first.snapshot();
        StreamedMusicVoice second = fixture.track(2, "second");
        mix(second);

        first.stop();
        first.restoreMutation(before);

        assertEquals("second", fixture.port.current.track().name());
        assertEquals(201, mix(second));
        second.stop();
        assertEquals(101, mix(first));
    }

    @Test
    void completionRemovalRetiresEveryFiniteCursor() {
        Fixture fixture = new Fixture();
        fixture.port.completeAfterNextMix = true;

        for (int index = 0; index < 20; index++) {
            StreamedMusicVoice voice = fixture.track(index + 1, "finite");
            mix(voice);
            assertTrue(voice.isComplete());
            voice.retireCompleted();
            assertEquals(0, fixture.session.trackedCursorCount(),
                    "registry completion removal must not accumulate cursors");
        }
    }

    @Test
    void configuredRestoreFadePreservesCadenceAndSpeedAcrossCursorSwitches() {
        Fixture fixture = new Fixture();
        StreamedMusicVoice.RestorePolicy policy =
                StreamedMusicVoice.RestorePolicy.from(config(2, 1), true);
        StreamedMusicVoice base = fixture.stock(1, 0x81, policy);
        base.setSpeedMultiplier(8);
        mix(base);
        StreamedMusicVoice override = fixture.stock(2, 0x82);
        mix(override);

        base.beginOverrideRestore();
        assertEquals(1, fixture.port.fadeInCount);
        assertEquals(2, fixture.port.current.fade().remainingSteps());
        assertEquals(1, fixture.port.current.fade().stepDelay());
        assertEquals(1.25, fixture.port.current.rate());
        mix(base);
        PresentationVoiceSnapshot.Streamed midFade =
                (PresentationVoiceSnapshot.Streamed) base.snapshot();
        mix(override);

        assertEquals(midFade.playback().fade(),
                ((PresentationVoiceSnapshot.Streamed) base.snapshot())
                        .playback().fade(),
                "switching away and back preserves fade cadence");
        mix(base);
        assertEquals(1,
                ((PresentationVoiceSnapshot.Streamed) base.snapshot())
                        .playback().fade().remainingSteps());
        assertEquals(1.25,
                ((PresentationVoiceSnapshot.Streamed) base.snapshot())
                        .playback().rate());
    }

    @Test
    void restorePolicySelectsImmediateOrAfterFadeSfxRelease() {
        Fixture fixture = new Fixture();
        StreamedMusicVoice immediate = fixture.stock(1, 0x81,
                StreamedMusicVoice.RestorePolicy.from(config(1, 0), false));
        StreamedMusicVoice afterFade = fixture.stock(2, 0x82,
                StreamedMusicVoice.RestorePolicy.from(config(1, 0), true));

        immediate.beginOverrideRestore();
        assertTrue(immediate.releasesSfxOnRestore());
        afterFade.beginOverrideRestore();
        assertFalse(afterFade.releasesSfxOnRestore());
        assertFalse(afterFade.restoreFadeComplete());
        mix(afterFade);
        assertTrue(afterFade.restoreFadeComplete());
    }

    private static SmpsSequencerConfig config(
            int steps, int delay) {
        return new SmpsSequencerConfig.Builder()
                .fadeInSteps(steps)
                .fadeInDelay(delay)
                .build();
    }

    private static int mix(StreamedMusicVoice voice) {
        long[] output = new long[2];
        voice.mixInto(output, 1);
        return (int) output[0];
    }

    private static final class Fixture {
        private final RecordingPort port = new RecordingPort();
        private final StreamedPresentationSession session =
                new StreamedPresentationSession();

        private Fixture() {
            session.attach(port);
        }

        private StreamedMusicVoice track(long voiceId, String name) {
            StreamedMusicPort.TrackRef track =
                    new StreamedMusicPort.TrackRef("owner", name);
            return new StreamedMusicVoice(voiceId,
                    session.materializeTrack(track),
                    AudioSourceDescriptor.streamedTrack("owner:" + name));
        }

        private StreamedMusicVoice stock(long voiceId, int musicId) {
            return stock(voiceId, musicId,
                    StreamedMusicVoice.RestorePolicy.immediate());
        }

        private StreamedMusicVoice stock(long voiceId, int musicId,
                StreamedMusicVoice.RestorePolicy policy) {
            return new StreamedMusicVoice(voiceId,
                    session.materializeStockOverride(musicId),
                    AudioSourceDescriptor.baseMusic(musicId), policy);
        }

        private StreamedMusicVoice restore(
                PresentationVoiceSnapshot.Streamed snapshot) {
            return StreamedMusicVoice.restore(snapshot,
                    session.restore(snapshot.playback(), snapshot.stopped()),
                    StreamedMusicVoice.RestorePolicy.immediate());
        }
    }

    private static final class RecordingPort implements StreamedMusicPort {
        private State current;
        private int closeCount;
        private int stopCount;
        private int fadeInCount;
        private boolean completeAfterNextMix;

        @Override public int outputRate() { return 48_000; }
        @Override public boolean hasStockOverride(int musicId) { return true; }
        @Override public boolean isCurrentStockOverride(int musicId) {
            return current != null && current.logicalMusicId() == musicId;
        }
        @Override public void playStockOverride(int musicId) {
            current = state(new TrackRef("stock", Integer.toString(musicId)),
                    musicId, 0);
        }
        @Override public boolean hasTrack(TrackRef track) {
            return "owner".equals(track.owner());
        }
        @Override public void playTrack(TrackRef track) {
            if (!hasTrack(track)) throw new IllegalArgumentException(track.toString());
            current = state(track, -1, 0);
        }
        @Override public boolean hasSfx(SfxRef sfx) { return false; }
        @Override public boolean hasSource() { return current != null; }
        @Override public int mixInto(short[] output, int frames) {
            if (current == null) return 0;
            int base = switch (current.track().name()) {
                case "first", "129" -> 100;
                case "second", "130" -> 200;
                default -> 300;
            };
            short sample = (short) (base + (int) current.sourceFramePosition());
            output[0] = sample;
            output[1] = sample;
            current = new State(current.track(), current.logicalMusicId(),
                    current.sourceFramePosition() + frames,
                    current.pauseMask(), current.fade(), current.rate());
            if (completeAfterNextMix) {
                current = null;
            }
            return frames;
        }
        @Override public void pause(int reason) { }
        @Override public void resume(int reason) { }
        @Override public void fadeOut(int steps, int stepDelay) {
            current = withFade(new FadeState(current.fade().gain(), steps,
                    stepDelay, stepDelay, -current.fade().gain() / steps));
        }
        @Override public void fadeIn(int steps, int stepDelay) {
            fadeInCount++;
            current = withFade(new FadeState(0, steps, stepDelay,
                    stepDelay, 1.0f / steps));
        }
        @Override public void advanceFade() {
            if (current == null || current.fade().remainingSteps() == 0) return;
            FadeState fade = current.fade();
            if (fade.delayCounter() > 0) {
                current = withFade(new FadeState(fade.gain(),
                        fade.remainingSteps(), fade.stepDelay(),
                        fade.delayCounter() - 1, fade.stepAmount()));
                return;
            }
            int remaining = fade.remainingSteps() - 1;
            if (remaining == 0) {
                current = withFade(FadeState.idle());
            } else {
                current = withFade(new FadeState(
                        fade.gain() + fade.stepAmount(), remaining,
                        fade.stepDelay(), fade.stepDelay(), fade.stepAmount()));
            }
        }
        @Override public boolean fadeActive() {
            return current != null && current.fade().remainingSteps() > 0;
        }
        @Override public boolean fadeAtFullGain() {
            return current != null && !fadeActive()
                    && current.fade().gain() == 1;
        }
        @Override public void setSpeedMultiplier(int multiplier) {
            current = new State(current.track(), current.logicalMusicId(),
                    current.sourceFramePosition(), current.pauseMask(),
                    current.fade(), multiplier == 1 ? 1.0 : 1.25);
        }
        @Override public void stop() {
            if (current != null) stopCount++;
            current = null;
        }
        @Override public void reset() { stop(); }
        @Override public Optional<State> captureState() {
            return Optional.ofNullable(current);
        }
        @Override public boolean restoreState(State state) {
            current = state;
            return true;
        }
        @Override public void close() { closeCount++; }

        private static State state(TrackRef track, int musicId, double position) {
            return new State(track, musicId, position, 0,
                    FadeState.idle(), 1.0);
        }

        private State withFade(FadeState fade) {
            return new State(current.track(), current.logicalMusicId(),
                    current.sourceFramePosition(), current.pauseMask(), fade,
                    current.rate());
        }
    }
}
