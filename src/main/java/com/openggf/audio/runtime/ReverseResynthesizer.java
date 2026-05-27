package com.openggf.audio.runtime;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioLogicalSnapshot;

import java.util.Objects;

/**
 * Synthesizes historical PCM into the {@link PcmHistoryRing} when a reverse
 * cursor approaches the bottom of its readable window. Mutates the live
 * music + standalone SFX drivers across bursts; callers wrap the held-rewind
 * session in a {@link AudioManager#beginReverseAudioPresentation} /
 * {@link AudioManager#endReverseAudioPresentation} bracket so those
 * mutations are erased when the rewind session ends.
 *
 * <p>The {@link #ensureHeadroom} entry point must be called before each
 * {@link PcmHistoryRing.ReverseCursor#readPrevious}: it tops up the ring's
 * floor (lowering {@code cursor.oldestReadableFrame}) until the cursor has
 * at least the requested headroom of unread frames or until the keyframe
 * store runs out of usable history.
 *
 * <p>Spec: {@code docs/superpowers/specs/2026-05-26-reverse-resynth-design.md}.
 */
public final class ReverseResynthesizer {

    /** Drains added per burst. One game-second-equivalent of audio frames. */
    private final int burstAudioFrames;

    /** Trigger burst when unread frames in the cursor window fall below this. */
    private final int headroomThresholdFrames;

    private final PcmHistoryRing pcmHistory;
    private final AudioKeyframeStore keyframes;
    private final AudioManager audioManager;
    private final DeterministicAudioRuntime runtime;

    public ReverseResynthesizer(PcmHistoryRing pcmHistory,
                                AudioKeyframeStore keyframes,
                                AudioManager audioManager,
                                DeterministicAudioRuntime runtime,
                                int burstAudioFrames,
                                int headroomThresholdFrames) {
        this.pcmHistory = Objects.requireNonNull(pcmHistory, "pcmHistory");
        this.keyframes = Objects.requireNonNull(keyframes, "keyframes");
        this.audioManager = Objects.requireNonNull(audioManager, "audioManager");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        if (burstAudioFrames <= 0) {
            throw new IllegalArgumentException("burstAudioFrames must be positive");
        }
        if (headroomThresholdFrames < 0) {
            throw new IllegalArgumentException("headroomThresholdFrames must be non-negative");
        }
        this.burstAudioFrames = burstAudioFrames;
        this.headroomThresholdFrames = headroomThresholdFrames;
    }

    /**
     * Tops up the cursor's readable window so it can satisfy a read of
     * {@code framesNeeded} stereo frames with at least
     * {@code headroomThresholdFrames} of slack remaining afterwards (when
     * possible). Returns early if a burst attempt fails because no usable
     * keyframe is available — the caller's {@code readPrevious} will then
     * naturally drop to silence past the existing history floor.
     */
    public void ensureHeadroom(PcmHistoryRing.ReverseCursor cursor, int framesNeeded) {
        if (cursor == null) {
            return;
        }
        long target = (long) framesNeeded + headroomThresholdFrames;
        while (headroomFor(cursor) < target) {
            if (!runOneBurst(cursor)) {
                break;
            }
        }
    }

    private static long headroomFor(PcmHistoryRing.ReverseCursor cursor) {
        return cursor.nextReadableFrame() - cursor.oldestReadableFrame() + 1;
    }

    private boolean runOneBurst(PcmHistoryRing.ReverseCursor cursor) {
        // Cap the burst by the cursor's physical-slot availability. The ring
        // has a fixed capacity; once the unread window fills it, no more
        // frames can be prepended without corrupting unread slots.
        int maxPrependable = cursor.maxPrependableFrames();
        if (maxPrependable <= 0) {
            return false;
        }
        int requestedBurst = Math.min(burstAudioFrames, maxPrependable);
        // Cap by the start-of-history floor: we can't synthesize earlier than
        // audio frame 0.
        long burstFloor = Math.max(0L, cursor.oldestReadableFrame() - requestedBurst);
        int actualBurstFrames = (int) (cursor.oldestReadableFrame() - burstFloor);
        if (actualBurstFrames <= 0) {
            return false;
        }
        long targetOldestAudioFrame = burstFloor;
        AudioLogicalSnapshot keyframe = keyframes.keyframeAtOrBeforeAudioFrame(targetOldestAudioFrame);
        if (keyframe == null
                || keyframe.backend() == null
                || keyframe.backend().clockSnapshot() == null) {
            return false;
        }

        // Restore audio state to the keyframe (drivers + clock).
        audioManager.restoreLogicalSnapshot(keyframe);
        runtime.restoreClockSnapshot(keyframe.backend().clockSnapshot());

        long gameFrame = keyframe.commandTimelineFrame();
        long audioFrame = keyframe.backend().clockSnapshot().totalSamplesProduced();
        long burstEnd = cursor.oldestReadableFrame(); // exclusive

        // Build mixed[] in forward-time order first (oldest at index 0, newest at
        // the end), then invert before calling prependBackward — which expects
        // reverse-time source layout (newest at index 0). See PcmHistoryRing.java
        // lines 67-70 for the layout contract.
        int burstFrames = actualBurstFrames;
        short[] mixed = new short[burstFrames * 2];
        int mixedOffset = 0;

        while (audioFrame < burstEnd) {
            keyframes.replayCommandsAtGameFrame(audioManager, keyframe, gameFrame);
            // Re-read the runtime's music and sfx streams every iteration.
            // replayCommandsAtGameFrame can flow into backend.playSfxSmps or
            // backend.playSmps, which install fresh SmpsDriver instances via
            // runtime.setMusicStream / runtime.setSfxStream. A stream captured
            // once before the loop would go stale immediately after the first
            // such command, and a freshly-started SFX would silently drop out
            // of the mix until the next burst.
            AudioStream music = runtime.musicStreamForReverseResynth();
            AudioStream sfx = runtime.sfxStreamForReverseResynth();
            int samplesThisFrame = runtime.samplesForNextFrameForReverseResynth();
            short[] musicScratch = new short[samplesThisFrame * 2];
            short[] sfxScratch = new short[samplesThisFrame * 2];
            if (music != null) {
                music.read(musicScratch);
            }
            if (sfx != null) {
                sfx.read(sfxScratch);
                mixSfxIntoMusic(musicScratch, sfxScratch);
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

        // Invert mixed[] in place: pair-swap stereo frames so source[0..1] is
        // the newest produced frame, source[(mixedOffset-1)*2..] is the oldest.
        reverseStereoFrames(mixed, mixedOffset);

        pcmHistory.prependBackward(targetOldestAudioFrame, cursor, mixed, mixedOffset);
        return true;
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

    private static void mixSfxIntoMusic(short[] music, short[] sfx) {
        int len = Math.min(music.length, sfx.length);
        for (int i = 0; i < len; i++) {
            int mixedVal = music[i] + sfx[i];
            if (mixedVal > Short.MAX_VALUE) {
                mixedVal = Short.MAX_VALUE;
            } else if (mixedVal < Short.MIN_VALUE) {
                mixedVal = Short.MIN_VALUE;
            }
            music[i] = (short) mixedVal;
        }
    }
}
