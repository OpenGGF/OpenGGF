package com.openggf.tools.challenge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** A resized host window keeps the composed layout's aspect instead of stretching it. */
class TestChallengePresentationLayout {
    @Test
    void nativeSizeFillsTheWindow() {
        assertArrayEquals(new int[] {0, 0, 1024, 700}, ChallengePresentation.letterbox(1024, 700));
        assertArrayEquals(new int[] {0, 0, 2048, 1400}, ChallengePresentation.letterbox(2048, 1400));
    }

    @Test
    void widerOrTallerWindowsAreCentredWithBars() {
        assertArrayEquals(new int[] {170, 0, 1580, 1080}, ChallengePresentation.letterbox(1920, 1080));
        assertArrayEquals(new int[] {0, 126, 800, 547}, ChallengePresentation.letterbox(800, 800));
    }
}
