package survivors;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.game.rewind.RewindSnapshottable;

/** Captured menu/exit admission; Stage owns its captured phase and objects share RunState.paused. */
final class FrameControl implements GameplayFrameController, RewindSnapshottable<Boolean> {
    private boolean titleRequested;

    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
        throw new IllegalStateException("Survivors uses native-input frame dispositions");
    }
    @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
        var services = course.objectServices();
        var stage = Stage.find(services);
        if (stage == null) return GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
        course.finishInitialPresentation();
        var player = services.camera().getFocusedSprite();
        boolean held = stage.beforeFrame(player, input);
        // Active rows already service the fade in the native runtime. Held entry rows only
        // advance presentation; native destination fades retain their host owner.
        if (held && !services.levelManager().isLevelInactiveForTransition()) course.advanceEntryPresentation();
        return held ? GameplayFrameDisposition.HOLD : GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
    }
    @Override public void afterTick(CourseControl course, boolean advanced) { }
    void requestTitle() { titleRequested = true; }
    @Override public boolean consumeTitleRequest() { boolean request = titleRequested; titleRequested = false; return request; }
    @Override public String key() { return "sonic-survivors:frames"; }
    @Override public Boolean capture() { return titleRequested; }
    @Override public void restore(Boolean state) { titleRequested = state; }
}
