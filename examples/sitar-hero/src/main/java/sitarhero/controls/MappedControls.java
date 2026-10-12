package sitarhero.controls;

import com.openggf.control.ActionMap;
import com.openggf.control.ActionReducer;
import com.openggf.control.PhysicalInput;
import java.util.ArrayList;
import java.util.List;

/** Rhythm verbs over the framework's reusable timestamped reducer; scoring remains in RhythmSession. */
public final class MappedControls {
    public record Frame(long timestamp, int frets, int directPressed, boolean strum,
                        boolean power, boolean pause, float whammy) { }
    private final ControlSettings settings;
    private final ActionMap actions = new ActionMap();
    private final ActionReducer reducer = new ActionReducer(actions);
    private boolean drums;
    public MappedControls(ControlSettings settings) { this.settings = settings; }
    public void reset(PhysicalInput input, boolean drums) {
        this.drums = drums; bindings(); reducer.reset(input);
    }
    private void bindings() {
        for (int action = 0; action < 10; action++) actions.bind(Integer.toString(action),
                settings.binding(drums, false, action).framework(), settings.binding(drums, true, action).framework());
    }
    public List<Frame> accept(PhysicalInput input) {
        bindings();
        List<Frame> frames = new ArrayList<>();
        for (var frame : reducer.accept(input)) {
            int frets = 0, presses = 0;
            for (int action = 0; action < 5; action++) {
                if (frame.held(Integer.toString(action))) frets |= 1 << action;
                if (frame.pressed(Integer.toString(action))) presses |= 1 << action;
            }
            frames.add(new Frame(frame.timestampNanos(), frets, presses,
                    frame.pressed("5") || frame.pressed("6"), frame.pressed("8"), frame.pressed("9"), frame.amount("7")));
        }
        return frames;
    }
}
