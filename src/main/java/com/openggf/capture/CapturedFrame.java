package com.openggf.capture;

/**
 * One captured game frame: RGBA pixels (top-left origin, row-major, 4 bytes/px)
 * plus the stereo PCM produced by that same frame's audio step.
 *
 * <p><b>Ownership:</b> the constructor defensively copies {@code rgba} and
 * {@code pcm}, so a producer may immediately reuse its grab/drain buffers after
 * constructing a frame. Frames are handed to an async encoder thread, so this
 * copy is what makes per-frame buffer reuse safe. (Accessors return the internal
 * copies; the encoder only reads them.)
 *
 * @param rgba        width*height*4 bytes, RGBA8888 (copied)
 * @param pcm         interleaved stereo shorts; length >= sampleCount*2 (copied)
 * @param sampleCount stereo frames of audio for this video frame
 * @param frameIndex  monotonic 0-based capture index
 */
public record CapturedFrame(byte[] rgba, int width, int height,
                            short[] pcm, int sampleCount, long frameIndex) {
    public CapturedFrame {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("negative dimensions");
        }
        if (rgba.length != width * height * 4) {
            throw new IllegalArgumentException(
                    "rgba length " + rgba.length + " != width*height*4 ("
                    + (width * height * 4) + ")");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("negative sampleCount");
        }
        if (pcm.length < sampleCount * 2) {
            throw new IllegalArgumentException(
                    "pcm holds " + (pcm.length / 2) + " stereo frames, need "
                    + sampleCount);
        }
        // Defensive copy: the producer reuses its grab/drain buffers each frame,
        // but frames live on an async encoder queue. Copy so they can't be
        // mutated out from under the encoder.
        rgba = rgba.clone();
        pcm = pcm.clone();
    }
}
