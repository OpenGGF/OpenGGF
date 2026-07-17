package com.openggf.tools.fbzvisual;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Acceptance contract for one overlay-free AniPLC cadence series. */
final class FbzVisualCadenceVerifier {

    private FbzVisualCadenceVerifier() {
    }

    static void verify(List<FrameEvidence> frames) {
        Objects.requireNonNull(frames, "frames");
        if (frames.size() < 5) {
            throw new IllegalStateException("FBZ cadence requires at least five frames, got "
                    + frames.size());
        }
        boolean zeroStep = false;
        boolean oneStep = false;
        boolean naturalExpiry = false;
        boolean reviewedCorrelation = false;
        Set<String> cropHashes = new HashSet<>();

        for (int i = 0; i < frames.size(); i++) {
            FrameEvidence frame = frames.get(i);
            if (!frame.overlayFree()) {
                throw new IllegalStateException("FBZ cadence frame " + frame.index()
                        + " is title/fade/overlay contaminated");
            }
            requireHash(frame.vramSha256(), "VRAM", frame.index());
            requireHash(frame.cropSha256(), "crop", frame.index());
            cropHashes.add(frame.cropSha256());
            if ("zero-step".equals(frame.control())) {
                zeroStep = true;
                if (frame.timerBefore() != frame.timerAfter()
                        || frame.frameBefore() != frame.frameAfter()) {
                    throw new IllegalStateException("FBZ zero-step control mutated AniPLC state");
                }
            } else if ("one-step".equals(frame.control())) {
                oneStep = true;
                boolean advanced = frame.frameAfter() != frame.frameBefore();
                if (frame.timerBefore() == 0 && advanced) naturalExpiry = true;
                if (advanced && frame.reviewedVisibleRegionChanged() && i > 0) {
                    FrameEvidence previous = frames.get(i - 1);
                    reviewedCorrelation = !previous.cropSha256().equals(frame.cropSha256())
                            && !previous.vramSha256().equals(frame.vramSha256());
                }
            } else {
                throw new IllegalStateException("Unknown FBZ cadence control: " + frame.control());
            }
        }
        if (!zeroStep || !oneStep) {
            throw new IllegalStateException("FBZ cadence requires zero-step and one-step controls");
        }
        if (!naturalExpiry) {
            throw new IllegalStateException("FBZ cadence did not span a natural timer expiry");
        }
        if (cropHashes.size() < 2) {
            throw new IllegalStateException("FBZ cadence requires at least two distinct crop hashes");
        }
        if (!reviewedCorrelation) {
            throw new IllegalStateException("FBZ cadence lacks a reviewed visible crop/VRAM advance");
        }
    }

    private static void requireHash(String hash, String kind, int index) {
        if (hash == null || !hash.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalStateException("FBZ cadence frame " + index + " lacks a valid "
                    + kind + " SHA-256");
        }
    }

    record FrameEvidence(int index, String control,
                         int timerBefore, int frameBefore,
                         int timerAfter, int frameAfter,
                         String vramSha256, String cropSha256,
                         boolean overlayFree,
                         boolean reviewedVisibleRegionChanged) {
        FrameEvidence {
            Objects.requireNonNull(control, "control");
        }
    }
}
