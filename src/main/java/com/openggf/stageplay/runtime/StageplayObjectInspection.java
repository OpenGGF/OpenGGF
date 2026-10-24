package com.openggf.stageplay.runtime;

import java.util.List;

/** Engine/editor inspection values; no ROM bytes or native object handles enter the document/API. */
public record StageplayObjectInspection(String actorId, String name, List<Part> parts,
                                        List<String> limitations) {
    public StageplayObjectInspection {
        parts = List.copyOf(parts);
        limitations = List.copyOf(limitations);
    }

    public record Part(String partId, int defaultPose, List<Integer> mappingFrames,
                       List<Integer> animations) {
        public Part {
            mappingFrames = List.copyOf(mappingFrames);
            animations = List.copyOf(animations);
        }
    }
}
