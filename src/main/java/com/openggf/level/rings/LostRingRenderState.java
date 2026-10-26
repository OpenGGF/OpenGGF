package com.openggf.level.rings;

/** Render words retained by the native attracted-to-bouncing code-pointer change. */
record LostRingRenderState(int priorityBucket, int mappingFrame, boolean highPriority) {
    LostRingRenderState {
        if (priorityBucket < 0 || priorityBucket > 7 || mappingFrame < 0) {
            throw new IllegalArgumentException("Invalid retained native ring render words");
        }
    }
}
