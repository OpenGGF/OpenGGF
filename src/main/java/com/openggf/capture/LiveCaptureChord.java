package com.openggf.capture;

/** Rising edge detector for the complete live-capture shortcut chord. */
public final class LiveCaptureChord {
    private boolean previousComplete;

    public boolean update(boolean keyDown, boolean shiftDown,
                          boolean controlDown, boolean altDown) {
        boolean complete = keyDown && shiftDown && !controlDown && !altDown;
        boolean rising = complete && !previousComplete;
        previousComplete = complete;
        return rising;
    }
}
