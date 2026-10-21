package com.openggf.stageplay;

import com.openggf.game.ModApi;

/** Owner-bound mod trigger; get it from injected object services using gameService(StageplayControl.class). */
@ModApi
public interface StageplayControl {
    @ModApi enum Status { IDLE, REQUESTED, PLAYING, COMPLETED, SKIPPED, CANCELLED }
    /** Requests a scene at the next ready native frame boundary. An active request is not replaced. */
    void play();
    /** Applies the final pose and the configured completion policy once. */
    void skip();
    /** Returns the borrowed player/camera/audio to their entry state. */
    void cancel();
    Status status();
    int frame();
}
