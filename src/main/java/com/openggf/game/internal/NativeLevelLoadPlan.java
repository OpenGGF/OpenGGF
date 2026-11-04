package com.openggf.game.internal;

import com.openggf.game.InitStep;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Selected real operations and native queue waits, retained without any open execution scope. */
public record NativeLevelLoadPlan(List<Section> sections) {
    public NativeLevelLoadPlan { sections = List.copyOf(sections); }

    public record Section(NativeLevelLoadPhaseAdmission.Phase phase,
                          Supplier<NativeLevelLoadInputs> inputs,
                          BooleanSupplier ready, List<InitStep> prepare, List<InitStep> commit) {
        public Section {
            Objects.requireNonNull(ready, "ready");
            prepare = List.copyOf(prepare); commit = List.copyOf(commit);
            if (phase != null) Objects.requireNonNull(inputs, "inputs");
        }
        public static Section steps(List<InitStep> steps) {
            return new Section(null, null, () -> true, List.of(), steps);
        }
        public static Section waitFor(BooleanSupplier ready) {
            return new Section(null, null, ready, List.of(), List.of());
        }
    }
}
