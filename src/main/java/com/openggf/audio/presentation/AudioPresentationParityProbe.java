package com.openggf.audio.presentation;

import com.openggf.audio.runtime.AudioFrameClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Read-only accounting for the transitional inaudible presentation shadow.
 */
public final class AudioPresentationParityProbe {
    public record Snapshot(long presentedFrames, long forwardFrames,
            long silentFrames, long reverseFrames, long totalStereoFrames,
            long commandCount, long historyEpoch) {
    }

    static final class FinalPcmDiagnostic {
        record Capture(List<Short> interleavedSamples, String pcmSha256,
                int frameCount, int leftOnset, int rightOnset,
                int leftTail, int rightTail) {
            Capture {
                interleavedSamples = List.copyOf(interleavedSamples);
            }
        }

        private final int maximumFrames;
        private final List<Short> samples = new ArrayList<>();

        FinalPcmDiagnostic(int maximumFrames) {
            if (maximumFrames < 0) {
                throw new IllegalArgumentException("maximum PCM frames cannot be negative");
            }
            this.maximumFrames = maximumFrames;
        }

        void accept(short[] interleaved, int frameOffset, int frames) {
            if (interleaved == null || frameOffset < 0 || frames < 0
                    || Math.addExact(frameOffset, frames) > interleaved.length / 2
                    || Math.addExact(samples.size() / 2, frames) > maximumFrames) {
                throw new IllegalArgumentException("final PCM diagnostic input exceeds its bound");
            }
            int first = Math.multiplyExact(frameOffset, 2);
            int end = Math.addExact(first, Math.multiplyExact(frames, 2));
            for (int index = first; index < end; index++) {
                samples.add(interleaved[index]);
            }
        }

        Capture finish() {
            int frames = samples.size() / 2;
            int leftOnset = -1;
            int rightOnset = -1;
            int leftTail = -1;
            int rightTail = -1;
            ByteBuffer bytes = ByteBuffer.allocate(samples.size() * 2)
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (int frame = 0; frame < frames; frame++) {
                short left = samples.get(frame * 2);
                short right = samples.get(frame * 2 + 1);
                bytes.putShort(left).putShort(right);
                if (left != 0) {
                    if (leftOnset < 0) leftOnset = frame;
                    leftTail = frame;
                }
                if (right != 0) {
                    if (rightOnset < 0) rightOnset = frame;
                    rightTail = frame;
                }
            }
            try {
                String digest = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(bytes.array()));
                return new Capture(samples, digest, frames, leftOnset,
                        rightOnset, leftTail, rightTail);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }
    }

    private final AudioFrameClock clock;
    private long presentedFrames;
    private long forwardFrames;
    private long silentFrames;
    private long reverseFrames;
    private long totalStereoFrames;
    private long commandCount;
    private long historyEpoch;

    public AudioPresentationParityProbe(int sampleRate, int frameRate) {
        clock = new AudioFrameClock(sampleRate, frameRate);
    }

    public void commandSubmitted() {
        commandCount++;
    }

    public void presented(PresentationMode mode) {
        presentedFrames++;
        totalStereoFrames += clock.samplesForNextFrame();
        switch (mode) {
            case FORWARD -> forwardFrames++;
            case SILENT -> silentFrames++;
            case REVERSE -> reverseFrames++;
        }
    }

    public void historyBoundary() {
        historyEpoch++;
    }

    public Snapshot snapshot() {
        return new Snapshot(presentedFrames, forwardFrames, silentFrames,
                reverseFrames, totalStereoFrames, commandCount, historyEpoch);
    }
}
