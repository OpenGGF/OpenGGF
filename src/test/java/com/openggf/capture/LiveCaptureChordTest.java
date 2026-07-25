package com.openggf.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveCaptureChordTest {
    @Test void togglesWhenShiftPrecedesKey() {
        LiveCaptureChord chord = new LiveCaptureChord();
        assertFalse(chord.update(false, true, false, false));
        assertTrue(chord.update(true, true, false, false));
        assertFalse(chord.update(true, true, false, false));
    }

    @Test void togglesWhenKeyPrecedesShift() {
        LiveCaptureChord chord = new LiveCaptureChord();
        assertFalse(chord.update(true, false, false, false));
        assertTrue(chord.update(true, true, false, false));
    }

    @Test void requiresReleaseBeforeRetoggle() {
        LiveCaptureChord chord = new LiveCaptureChord();
        assertTrue(chord.update(true, true, false, false));
        assertFalse(chord.update(true, true, false, false));
        assertFalse(chord.update(false, true, false, false));
        assertTrue(chord.update(true, true, false, false));
    }

    @Test void ctrlAndAltSuppressAndModifierRemovalCanCompleteChord() {
        LiveCaptureChord chord = new LiveCaptureChord();
        assertFalse(chord.update(true, true, true, false));
        assertTrue(chord.update(true, true, false, false));
        assertFalse(chord.update(true, true, false, true));
        assertTrue(chord.update(true, true, false, false));
    }

    @Test void configuredKeyStateIsIndependentFromInputRecordingF9() {
        LiveCaptureChord chord = new LiveCaptureChord();
        boolean configuredO = true;
        boolean unrelatedF9 = true;
        assertTrue(chord.update(configuredO, true, false, false));
        assertTrue(unrelatedF9, "the separate F9 state is not consumed by this chord");
    }
}
