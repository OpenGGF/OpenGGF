package com.openggf.game.soniccd.fmv;

/** Disc-backed video playback owned by a single menu or ending visit. */
public interface CdVideoProgram {
    void frame(int pad);
    boolean done();
    CdProgramScreenState screen();
}
