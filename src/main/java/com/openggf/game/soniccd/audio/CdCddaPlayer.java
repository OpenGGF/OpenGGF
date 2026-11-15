package com.openggf.game.soniccd.audio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Red Book transport and fader for the CD audio the sub CPU starts with BIOS music calls.
 *
 * <p>Commands are the BIOS operations the system program issues ({@code LoopCDDA},
 * {@code PlayCDDA}, {@code SPCmd_FadeOutCDDA}, {@code SPCmd_StopCDDA}, {@code SPCmd_PauseCDDA},
 * {@code SPCmd_UnpauseCDDA}, {@code ResetCDDAVol}; CD System Program/SPX.asm:1652-2008):
 * {@code MSCPLAYR} repeats a track from INDEX 01, {@code MSCPLAY1} plays it once, and the
 * fader is set by {@code FDRSET} and ramped by {@code FDRCHG}. A track number is the BIOS
 * track: the disc's table of contents is the only map to audio bytes.
 *
 * <p>Start latency is immediate and unmodelled. Under BizHawk 2.11 (gpgx, BIOS US 1.10) the
 * first INDEX 01 sample is heard 41 to 83 video frames after the command, growing with the
 * track's distance from the data track (R11A track 3: 41, R81A track 21: 83), so it is the
 * drive's seek time; no seek model exists here and no constant is fitted.
 *
 * <p>The fade is the one measured element: {@link #FADE_UNITS_PER_SECOND}.
 */
public final class CdCddaPlayer {
    /** Red Book sample rate. */
    public static final int SOURCE_RATE = 44_100;
    /** Full-scale fader value; {@code FDRSET} writes 380h ({@code ResetCDDAVol}, CD System Program/SPX.asm:1697-1710). */
    public static final int FADER_FULL = 0x400;
    public static final int RESET_VOLUME = 0x380;
    /**
     * Fader units removed per second by {@code FDRCHG} at the rate the game uses (20h,
     * {@code SPCmd_FadeOutCDDA}, CD System Program/SPX.asm:1660-1664). Measured, not derived: BIOS US 1.10 under
     * BizHawk 2.11 gpgx takes the fader from 380h to silence linearly in 19 video frames after
     * SCMD_FADECDA, on two independent deaths: R31A (track 6, frames 2942-2961) and R52A
     * (track 12, frames 3193-3212); per-frame least-squares gain of the disc track in the native
     * audio, tools in ~/Downloads/Sonic-CD/native/aud1-tools. 380h / (19 / 59.92 s). Keyed to
     * that firmware; the BIOS ramp itself is not disassembled here.
     */
    public static final double FADE_UNITS_PER_SECOND = RESET_VOLUME / (19 / 59.922743);

    /** Audio bytes by BIOS track number. */
    public interface TrackSource {
        /** INDEX 01 to the next track's gap, in CD frames of 588 stereo samples. */
        long programFrames(int track);

        /** Raw little-endian stereo s16 frames from INDEX 01. */
        byte[] readFrames(int track, long firstFrame, int count) throws IOException;
    }

    /** Immutable transport state; track 0 is idle. */
    public record State(int track, boolean repeat, boolean paused, long position, long phase,
                        int volume, int master, double fadeVolume, boolean fading) {
        public static final State IDLE = new State(0, false, false, 0, 0, RESET_VOLUME, RESET_VOLUME, RESET_VOLUME, false);
    }

    private static final int CHUNK_FRAMES = 75;
    private static final int SAMPLES_PER_FRAME = 588;

    private final TrackSource source;
    private State state = State.IDLE;
    private int outputRate;
    private int chunkTrack = -1;
    private long chunkFirstSample = -1;
    private short[] chunk = new short[0];
    private int chunkSamples;

    public CdCddaPlayer(TrackSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public State state() {
        return state;
    }

    public void restore(State restored) {
        state = Objects.requireNonNull(restored, "state");
    }

    public void bindOutputRate(int sampleRate) {
        if (sampleRate <= 0) throw new IllegalArgumentException("output rate must be positive");
        outputRate = sampleRate;
    }

    /** {@code LoopCDDA} (CD System Program/SPX.asm:1877-1881) and {@code PlayCDDA} (:2001-2005): reset the volume, then play. */
    public void play(int track, boolean repeat) {
        if (track <= 0 || source.programFrames(track) <= 0) {
            throw new IllegalArgumentException("No audio track " + track);
        }
        state = new State(track, repeat, false, 0, 0, RESET_VOLUME, RESET_VOLUME, RESET_VOLUME, false);
    }

    public void stop() {
        state = new State(0, false, false, 0, 0, state.volume(), state.master(), state.fadeVolume(), false);
    }

    public void setPaused(boolean paused) {
        State s = state;
        state = new State(s.track(), s.repeat(), paused, s.position(), s.phase(), s.volume(), s.master(), s.fadeVolume(), s.fading());
    }

    /** {@code SPCmd_ResetCDDAVol} (CD System Program/SPX.asm:1652-1654). */
    public void resetVolume() {
        State s = state;
        state = new State(s.track(), s.repeat(), s.paused(), s.position(), s.phase(), RESET_VOLUME, RESET_VOLUME, RESET_VOLUME, false);
    }

    /** {@code FDRCHG} towards zero. */
    public void fadeOut() {
        State s = state;
        state = new State(s.track(), s.repeat(), s.paused(), s.position(), s.phase(), s.volume(), s.master(), s.fadeVolume(), true);
    }

    /** Adds this transport's output into interleaved stereo {@code target}. */
    public void render(short[] target, int stereoFrames) {
        State s = state;
        if (s.track() == 0 || s.paused() || outputRate <= 0) {
            return;
        }
        long position = s.position();
        long phase = s.phase();
        double fader = s.fadeVolume();
        boolean fading = s.fading();
        long total = source.programFrames(s.track()) * SAMPLES_PER_FRAME;
        double faderStep = FADE_UNITS_PER_SECOND / outputRate;
        int track = s.track();
        for (int i = 0; i < stereoFrames; i++) {
            if (position >= total) {
                if (!s.repeat()) {
                    state = new State(0, false, false, 0, 0, s.volume(), s.master(), fader, false);
                    return;
                }
                position -= total;
            }
            double gain = fader / FADER_FULL;
            // Linear interpolation from the 44.1 kHz disc clock to the output clock.
            long next = position + 1 < total ? position + 1 : s.repeat() ? 0 : position;
            double frac = (double) phase / outputRate;
            int l0 = sample(track, position, 0);
            int r0 = sample(track, position, 1);
            int l1 = sample(track, next, 0);
            int r1 = sample(track, next, 1);
            double l = l0 + (l1 - l0) * frac;
            double r = r0 + (r1 - r0) * frac;
            target[i * 2] = clamp(target[i * 2] + (int) Math.round(l * gain));
            target[i * 2 + 1] = clamp(target[i * 2 + 1] + (int) Math.round(r * gain));
            if (fading) {
                fader = Math.max(0, fader - faderStep);
                if (fader == 0) fading = false;
            }
            phase += SOURCE_RATE;
            position += phase / outputRate;
            phase %= outputRate;
        }
        state = new State(track, s.repeat(), false, position, phase, s.volume(), s.master(), fader, fading);
    }

    private int sample(int track, long position, int channel) {
        if (track != chunkTrack || position < chunkFirstSample || position >= chunkFirstSample + chunkSamples) {
            long firstFrame = position / SAMPLES_PER_FRAME;
            int frames = (int) Math.min(CHUNK_FRAMES, source.programFrames(track) - firstFrame);
            try {
                byte[] raw = source.readFrames(track, firstFrame, frames);
                chunkSamples = raw.length / 4;
                if (chunk.length < chunkSamples * 2) chunk = new short[chunkSamples * 2];
                for (int k = 0; k < chunkSamples * 2; k++) {
                    chunk[k] = (short) ((raw[k * 2] & 0xFF) | (raw[k * 2 + 1] << 8));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("CD audio track " + track + " read failed", e);
            }
            chunkTrack = track;
            chunkFirstSample = firstFrame * SAMPLES_PER_FRAME;
        }
        return chunk[(int) (position - chunkFirstSample) * 2 + channel];
    }

    private static short clamp(int v) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
    }
}
