package com.openggf.level.objects;

import java.util.List;
import java.util.Optional;

/** Picture-only cycles already present in native object timer logic, independent of movement. */
public interface ObjectVisualAnimationSource {
    record Cycle(int delay, List<Integer> mappingFrames) {
        public Cycle {
            mappingFrames = List.copyOf(mappingFrames);
            if (delay < 0 || mappingFrames.isEmpty()) throw new IllegalArgumentException("Invalid native picture cycle");
        }
    }

    Optional<Cycle> visualAnimationCycle(int mappingFrame);
}
