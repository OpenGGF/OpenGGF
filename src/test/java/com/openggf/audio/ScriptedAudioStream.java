package com.openggf.audio;

/**
 * Test double that emits a deterministic ramp through AudioStream.read.
 * Not an SmpsDriver — that would force test fixtures to construct sequencer
 * dependencies they don't need. The runtime accepts any AudioStream via
 * setMusicStream/setSfxStream.
 */
public final class ScriptedAudioStream implements AudioStream {
    private short next;
    private final short step;
    private boolean complete;

    public ScriptedAudioStream(short initial, short step) {
        this.next = initial;
        this.step = step;
    }

    @Override
    public int read(short[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = next;
            next = (short) (next + step);
        }
        return buffer.length;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    public void markComplete() {
        complete = true;
    }
}
