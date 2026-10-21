package com.openggf.stageplay.runtime;

import com.openggf.game.ModApi;
import com.openggf.game.mode.CourseControl;
import com.openggf.stageplay.StageplayTimeline.ActorPose;
import com.openggf.stageplay.StageplayTimeline.CameraPose;

import java.util.Objects;

/**
 * Optional main-player binding for a held Stageplay cutscene. Native state is changed only
 * through the current frame's CourseControl. Registration owns this helper and captures its
 * state in the playback adapter; it retains no frame capability or live player reference.
 */
@ModApi
public final class StageplayNativeBinding {
    private final String actorId;
    private CourseControl.DirectedPlayerState entry;

    @ModApi
    public enum FinishMode { RESTORE_ENTRY, KEEP_FINAL_POSITION }

    /** Entry is null when no player is currently borrowed. */
    @ModApi
    public record State(String actorId, CourseControl.DirectedPlayerState entry) {
        public State {
            Objects.requireNonNull(actorId, "actorId");
        }
    }

    public StageplayNativeBinding(String actorId) {
        if (actorId == null || actorId.length() > 64 || !actorId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Invalid bound actor id");
        }
        this.actorId = actorId;
    }

    public void captureEntry(CourseControl course) {
        if (entry != null) throw new IllegalStateException("Native player is already bound");
        entry = Objects.requireNonNull(course, "course").captureDirectedPlayer();
    }

    /** Camera coordinates must be the unshaken authored position; zoom is presentation-only. */
    public void apply(CourseControl course, ActorPose actor, CameraPose camera, int mappingFrame) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(camera, "camera");
        if (entry == null) throw new IllegalStateException("Native player is not bound");
        if (!actorId.equals(actor.actorId()) || !entry.gameId().equals(actor.gameId())
                || !entry.character().equals(actor.character()) || actor.scale() != 1
                || !Double.isFinite(camera.zoom()) || camera.zoom() <= 0) {
            throw new IllegalArgumentException("Native binding requires its matching game/character at unit actor scale");
        }
        course.directPlayer(entry, pixel(actor.x()), pixel(actor.y()), actor.animation(), mappingFrame,
                actor.flipX(), actor.flipY(), actor.visible(), pixel(camera.x()), pixel(camera.y()));
    }

    public void finish(CourseControl course, FinishMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (entry == null) return;
        course.releaseDirectedPlayer(entry, mode == FinishMode.KEEP_FINAL_POSITION);
        entry = null;
    }

    public void cancel(CourseControl course) {
        finish(course, FinishMode.RESTORE_ENTRY);
    }

    public boolean current(CourseControl course) {
        return entry != null && course.isDirectedPlayerCurrent(entry);
    }

    /** Discards a stale visit/session reference; never applies an older state to a new world. */
    public void abandon() { entry = null; }

    public State capture() { return new State(actorId, entry); }

    /** Rewind restores ownership values only; the normal native adapters restore gameplay. */
    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        if (!actorId.equals(state.actorId())) throw new IllegalArgumentException("Binding snapshot belongs to another actor");
        entry = state.entry();
    }

    private static int pixel(double value) {
        if (!Double.isFinite(value) || value < Short.MIN_VALUE || value > 0xffff) {
            throw new IllegalArgumentException("Bound actor/camera coordinate is outside native 16-bit space");
        }
        return (int) Math.round(value);
    }
}
