package com.openggf.audio.runtime;

import com.openggf.audio.driver.MusicStackEntry;
import com.openggf.audio.driver.SmpsPresentationReplay;
import com.openggf.audio.driver.SmpsPresentationState;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioTimelineEntry;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Reverse-resynth worker that synthesises historical PCM into a
 * {@link PcmHistoryRing} ahead of the audio drain consumer.
 *
 * <p>The worker operates entirely on its constructor-provided
 * {@link ReverseAudioSession} and a private {@link SmpsPresentationState};
 * it never touches the live {@code AudioManager}, the live
 * {@code AudioBackend}, the live {@code AudioCommandTimeline}, or the live
 * {@code AudioKeyframeStore}. The session carries frozen copies of the
 * timeline + keyframes plus immutable replay dependencies; this isolation
 * is what allows the game thread to keep mutating the live state while the
 * worker reads from its snapshot.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>The worker is created by {@code AudioManager.beginReverseAudioPresentation}
 * (Task 6 wires this) and torn down by {@code endReverseAudioPresentation}.
 * Cooperative shutdown via {@link #requestStop} sets a volatile flag the
 * worker checks at burst boundaries; {@link #detachSession} additionally
 * nulls the volatile session reference so any in-flight burst can't reach
 * the ring or keyframes once detach has been observed.
 *
 * <h2>Burst contract</h2>
 *
 * <p>Each burst:
 * <ol>
 *   <li>Snapshots cursor state (under the ring monitor, briefly).</li>
 *   <li>Picks the latest keyframe with audio-frame ≤ burst floor.</li>
 *   <li>Restores worker state from that keyframe via
 *       {@link SmpsPresentationReplay#applyToRestoreFromSnapshot}.</li>
 *   <li>Walks audio-frames forward, applying frozen-timeline commands at
 *       each game-frame boundary onto worker state via
 *       {@link SmpsPresentationReplay}, then reading from worker drivers
 *       to fill a mixed PCM buffer.</li>
 *   <li>Inverts the buffer (forward-time → reverse-time) and prepends to
 *       the ring (under the ring monitor, briefly).</li>
 * </ol>
 *
 * <p>Chip emulation happens between the two ring-monitor windows so the
 * audio drain consumer can keep reading concurrently.
 *
 * <p>Spec: {@code docs/superpowers/specs/2026-05-26-reverse-resynth-design.md}.
 * Plan: {@code docs/superpowers/plans/2026-05-27-reverse-resynth-worker-thread.md}
 * (Task 5).
 */
public final class ReverseResynthesizer implements Runnable {

    private static final long IDLE_SLEEP_MILLIS = 2;

    private volatile ReverseAudioSession session;
    private volatile PcmHistoryRing.ReverseCursor cursor;
    private volatile boolean stopping;

    private final SmpsPresentationState workerState = new SmpsPresentationState();

    /** Scratch buffer reused across game-frames within a burst. Grown
     *  on demand to fit the largest samplesForNextFrame we see. */
    private short[] musicScratch = new short[0];
    private short[] sfxScratch = new short[0];

    public ReverseResynthesizer(ReverseAudioSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * Binds the cursor the worker drives. Called by Task 6's lifecycle
     * wiring after {@code runtime.beginReversePresentation} creates the
     * cursor on the ring. The cursor is volatile-reassignable so the
     * worker's {@link #run} loop observes it.
     */
    public void setCursor(PcmHistoryRing.ReverseCursor cursor) {
        this.cursor = cursor;
    }

    /** Cooperatively requests the worker loop to exit at the next burst
     *  boundary. Already-in-flight chip emulation will complete; the prepend
     *  step will be skipped. */
    public void requestStop() {
        this.stopping = true;
    }

    /** Stronger shutdown than {@link #requestStop}: nulls the session
     *  reference so any in-flight or future burst cannot reach the ring or
     *  keyframes. Used by {@code endReverseAudioPresentation} when a join
     *  times out (200ms in the plan) — the worker thread is left to drain
     *  itself, but it cannot corrupt subsequent state. */
    public void detachSession() {
        this.session = null;
        this.cursor = null;
        this.stopping = true;
    }

    /**
     * Synchronously runs at most one burst against the given cursor.
     * Used by the lifecycle wiring (Task 6) for the startup prefill — when
     * a held-rewind session begins, the worker pre-fills the ring before
     * the audio drain has a chance to drain past the existing history
     * window. Also used directly by unit tests.
     *
     * @return true if a burst extended the cursor's readable window
     */
    public boolean runOneIterationForPrefill(PcmHistoryRing.ReverseCursor c) {
        ReverseAudioSession s = session;
        if (s == null || stopping || c == null) {
            return false;
        }
        return runOneBurst(s, c);
    }

    @Override
    public void run() {
        while (!stopping) {
            ReverseAudioSession s = session;
            if (s == null) {
                return;
            }
            PcmHistoryRing.ReverseCursor c = cursor;
            if (c == null) {
                sleepBrief();
                continue;
            }
            long unread = c.nextReadableFrame() - c.oldestReadableFrame() + 1;
            long target = (long) s.headroomThresholdFrames() + s.burstAudioFrames();
            if (unread >= target) {
                sleepBrief();
                continue;
            }
            if (!runOneBurst(s, c)) {
                // No keyframe available, hit start-of-history floor, or
                // the ring is full. Back off so drainPcm can free slots.
                sleepBrief();
            }
        }
    }

    private static void sleepBrief() {
        try {
            Thread.sleep(IDLE_SLEEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean runOneBurst(ReverseAudioSession s, PcmHistoryRing.ReverseCursor cursor) {
        int maxPrependable = cursor.maxPrependableFrames();
        if (maxPrependable <= 0) {
            return false;
        }
        int requestedBurst = Math.min(s.burstAudioFrames(), maxPrependable);
        long burstFloor = Math.max(0L, cursor.oldestReadableFrame() - requestedBurst);
        int actualBurstFrames = (int) (cursor.oldestReadableFrame() - burstFloor);
        if (actualBurstFrames <= 0) {
            return false;
        }
        long targetOldestAudioFrame = burstFloor;
        AudioLogicalSnapshot keyframe =
                s.keyframes().keyframeAtOrBeforeAudioFrame(targetOldestAudioFrame);
        if (keyframe == null
                || keyframe.backend() == null
                || keyframe.backend().clockSnapshot() == null) {
            return false;
        }

        // Restore worker's private state from the chosen keyframe. Note
        // that this clears the override stack on workerState — the
        // snapshot's override-stack descriptors are intentionally not
        // restored (matching the live backend behaviour; rewind rebuilds
        // the override stack via command replay).
        SmpsPresentationReplay.applyToRestoreFromSnapshot(
                workerState,
                keyframe.backend(),
                s.replayDependencies(),
                s.presentationDriverFactory(),
                () -> workerState.sfxBlocked = false);

        long gameFrame = keyframe.commandTimelineFrame();
        long audioFrame = keyframe.backend().clockSnapshot().totalSamplesProduced();
        long burstEnd = cursor.oldestReadableFrame(); // exclusive

        // Private clock for this burst, restored from the keyframe's clock
        // snapshot so samplesForNextFrame() advances consistently.
        AudioFrameClock clock = new AudioFrameClock(s.sampleRate(), s.frameRate());
        clock.restoreSnapshot(keyframe.backend().clockSnapshot());

        // Pre-built fixed-size mixed buffer (forward-time order; reversed
        // before prepend).
        short[] mixed = new short[actualBurstFrames * 2];
        int mixedOffset = 0;

        // Pre-built command-replay deps reused for every command in the burst.
        SmpsPresentationReplay.SfxReplayDependencies sfxDeps = s.buildSfxDependencies();
        SmpsPresentationReplay.MusicReplayDependencies musicDeps = s.buildMusicDependencies();

        int timelineCursor = keyframe.commandEntryCount();
        List<AudioTimelineEntry> entries = s.frozenTimeline();

        while (audioFrame < burstEnd) {
            // Stop check at each game-frame boundary so a long burst can
            // surrender if endReverseAudioPresentation has fired.
            if (stopping || session == null) {
                return false;
            }

            // Replay timeline entries whose frame == gameFrame.
            timelineCursor = replayTimelineAtGameFrame(
                    s, entries, timelineCursor, gameFrame, sfxDeps, musicDeps);

            int samplesThisFrame = clock.samplesForNextFrame();
            ensureScratchCapacity(samplesThisFrame);
            int needShorts = samplesThisFrame * 2;
            Arrays.fill(musicScratch, 0, needShorts, (short) 0);

            // The worker reads from its private state's drivers — not the
            // runtime's. The base helpers wrote workerState.musicDriver
            // and (potentially) workerState.sfxStream during command replay.
            if (workerState.musicDriver != null) {
                workerState.musicDriver.read(localTrim(musicScratch, needShorts));
            }
            if (workerState.sfxStream != null) {
                Arrays.fill(sfxScratch, 0, needShorts, (short) 0);
                workerState.sfxStream.read(localTrim(sfxScratch, needShorts));
                mixSfxIntoMusic(musicScratch, sfxScratch, needShorts);
            }

            long thisFrameStart = audioFrame;
            long thisFrameEnd = audioFrame + samplesThisFrame;
            long copyStart = Math.max(thisFrameStart, targetOldestAudioFrame);
            long copyEnd = Math.min(thisFrameEnd, burstEnd);
            if (copyStart < copyEnd) {
                int srcOff = (int) (copyStart - thisFrameStart);
                int len = (int) (copyEnd - copyStart);
                System.arraycopy(musicScratch, srcOff * 2,
                        mixed, mixedOffset * 2, len * 2);
                mixedOffset += len;
            }
            audioFrame = thisFrameEnd;
            gameFrame++;
        }

        if (stopping || session == null) {
            return false;
        }

        // Invert mixed[] (forward-time → reverse-time) before prepend; the
        // ring's prependBackward consumes source[0..1] as the newest frame.
        reverseStereoFrames(mixed, mixedOffset);
        s.ring().prependBackward(targetOldestAudioFrame, cursor, mixed, mixedOffset);
        return true;
    }

    /**
     * Walks the frozen timeline forward from {@code startIndex}, applying
     * any entries at exactly {@code gameFrame} onto {@code workerState}.
     * Returns the index to resume from on the next call (just past the
     * last entry whose frame ≤ {@code gameFrame}).
     */
    private int replayTimelineAtGameFrame(
            ReverseAudioSession s,
            List<AudioTimelineEntry> entries,
            int startIndex,
            long gameFrame,
            SmpsPresentationReplay.SfxReplayDependencies sfxDeps,
            SmpsPresentationReplay.MusicReplayDependencies musicDeps) {
        int i = startIndex;
        for (; i < entries.size(); i++) {
            AudioTimelineEntry entry = entries.get(i);
            if (entry.frame() < gameFrame) {
                continue;
            }
            if (entry.frame() > gameFrame) {
                break;
            }
            applyCommandToWorkerState(s, entry.command(), sfxDeps, musicDeps);
        }
        return i;
    }

    private void applyCommandToWorkerState(
            ReverseAudioSession s,
            AudioCommand command,
            SmpsPresentationReplay.SfxReplayDependencies sfxDeps,
            SmpsPresentationReplay.MusicReplayDependencies musicDeps) {
        switch (command) {
            case AudioCommand.PlayMusic music -> {
                AudioCommandDataResolver.MusicData md = s.commandResolver().resolveMusic(music);
                if (md == null) {
                    // FALLBACK_WAV / SYSTEM_COMMAND / missing loader — skip.
                    return;
                }
                // Choose prelude based on override flag.
                boolean isOverride = music.override();
                if (isOverride) {
                    // For overrides we don't know whether it's sfx-blocking
                    // without the audio profile; the live backend looks
                    // this up via audioProfile.isSfxBlockingMusic. The
                    // command's `override` flag was recorded by the live
                    // capture path; we trust it here. For sfx-blocking we
                    // err on the side of NOT clearing sfx (the worker's
                    // priority is replaying chip state, not enforcing the
                    // gameplay-rule that 1-up kills active SFX).
                    boolean sfxBlocking = s.audioProfile() != null
                            && s.audioProfile().isSfxBlockingMusic(music.musicId());
                    boolean currentIsOverride = isCurrentMusicAnOverride(s);
                    SmpsPresentationReplay.applyToMusicPreludeOverride(
                            workerState, music.musicId(), sfxBlocking, currentIsOverride);
                } else {
                    SmpsPresentationReplay.applyToMusicPreludeNonOverride(workerState);
                }
                SmpsPresentationReplay.applyToMusicBase(
                        workerState, md.data(), md.dacData(), md.config(),
                        md.descriptor(), musicDeps);
                workerState.currentMusicId = music.musicId();
                workerState.currentMusicDescriptor = md.descriptor();
            }
            case AudioCommand.PlaySfx sfx -> {
                if (sfx.route() == AudioCommand.SfxRoute.FALLBACK_NAME
                        || sfx.route() == AudioCommand.SfxRoute.RING_RESOLVED) {
                    // Spec edge case 9: WAV-fallback SFX is silent under
                    // reverse-resynth.
                    return;
                }
                AudioCommandDataResolver.SfxData sd = s.commandResolver().resolveSfx(sfx);
                if (sd == null) {
                    return;
                }
                SmpsPresentationReplay.applyToSfx(
                        workerState, sd.data(), sd.dacData(), sfx.pitch(),
                        sd.config(), sfxDeps);
            }
            case AudioCommand.FadeOutMusic fade ->
                    SmpsPresentationReplay.applyToFadeOutMusic(
                            workerState, fade.steps(), fade.delay());
            case AudioCommand.StopMusic ignored ->
                    SmpsPresentationReplay.applyToStopMusic(workerState);
            case AudioCommand.StopAllSfx ignored ->
                    SmpsPresentationReplay.applyToStopAllSfx(workerState);
            case AudioCommand.EndMusicOverride end -> {
                // If the current music is the named override, restore the
                // previous music. Otherwise, just remove it from the
                // saved-overrides stack. Mirrors LWJGLAudioBackend.endMusicOverride.
                if (workerState.activeMusicSequencer != null
                        && workerState.currentMusicId == end.musicId()) {
                    SmpsPresentationReplay.applyToRestoreMusic(
                            workerState, workerState.speedShoesEnabled,
                            () -> workerState.sfxBlocked = false);
                } else {
                    SmpsPresentationReplay.removeSavedOverride(workerState, end.musicId());
                }
            }
            case AudioCommand.RestoreMusic ignored ->
                    SmpsPresentationReplay.applyToRestoreMusic(
                            workerState, workerState.speedShoesEnabled,
                            () -> workerState.sfxBlocked = false);
            case AudioCommand.SetSpeedShoes speed ->
                    SmpsPresentationReplay.applyToSetSpeedShoes(workerState, speed.enabled());
            case AudioCommand.SetSpeedMultiplier mult ->
                    SmpsPresentationReplay.applyToSetSpeedMultiplier(workerState, mult.multiplier());
            case AudioCommand.ChangeMusicTempo tempo ->
                    SmpsPresentationReplay.applyToChangeMusicTempo(workerState, tempo.dividingTiming());
            case AudioCommand.ResetRingAlternation ignored -> {
                // ringLeft is AudioManager-side state used to alternate
                // between left/right ring channels. The worker only mixes
                // music + sfx into mono-derived stereo; the ring alternation
                // is irrelevant to chip state and is intentionally a no-op
                // here.
            }
        }
    }

    private boolean isCurrentMusicAnOverride(ReverseAudioSession s) {
        if (s.audioProfile() == null) {
            return false;
        }
        return s.audioProfile().isMusicOverride(workerState.currentMusicId);
    }

    private void ensureScratchCapacity(int samplesPerFrame) {
        int needShorts = samplesPerFrame * 2;
        if (musicScratch.length < needShorts) {
            musicScratch = new short[needShorts];
        }
        if (sfxScratch.length < needShorts) {
            sfxScratch = new short[needShorts];
        }
    }

    /** Returns the same buffer if it's exactly {@code length} long, or a
     *  fresh buffer of that length otherwise. SmpsDriver.read consumes the
     *  entire buffer length, so we must pass exactly the requested size. */
    private static short[] localTrim(short[] buffer, int length) {
        if (buffer.length == length) {
            return buffer;
        }
        return new short[length];
    }

    private static void reverseStereoFrames(short[] buf, int frames) {
        int lo = 0;
        int hi = frames - 1;
        while (lo < hi) {
            int loIdx = lo * 2;
            int hiIdx = hi * 2;
            short l0 = buf[loIdx];
            short l1 = buf[loIdx + 1];
            buf[loIdx] = buf[hiIdx];
            buf[loIdx + 1] = buf[hiIdx + 1];
            buf[hiIdx] = l0;
            buf[hiIdx + 1] = l1;
            lo++;
            hi--;
        }
    }

    private static void mixSfxIntoMusic(short[] music, short[] sfx, int lengthShorts) {
        for (int i = 0; i < lengthShorts; i++) {
            int mixedVal = music[i] + sfx[i];
            if (mixedVal > Short.MAX_VALUE) {
                mixedVal = Short.MAX_VALUE;
            } else if (mixedVal < Short.MIN_VALUE) {
                mixedVal = Short.MIN_VALUE;
            }
            music[i] = (short) mixedVal;
        }
    }

    /** Test seam: the worker's private presentation state. Exposed
     *  package-private so unit tests can verify keyframe restore + command
     *  replay applied the expected mutations without driving a full burst
     *  through the ring. */
    SmpsPresentationState workerStateForTest() {
        return workerState;
    }

    /**
     * Suppresses unused-import warnings caused by the (currently unused)
     * import of {@link MusicStackEntry}. The override-stack restore path
     * in {@code applyToRestoreFromSnapshot} writes through
     * {@link SmpsPresentationState#musicStack}, which is typed
     * {@code Deque<MusicStackEntry>} — keep the import close at hand so
     * future edits don't need to re-find it.
     */
    @SuppressWarnings("unused")
    private static final Class<?> KEEP_IMPORT = MusicStackEntry.class;
}
