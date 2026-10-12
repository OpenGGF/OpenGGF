package com.openggf.graphics;

/** Current rendering projection without access to engine or session orchestration. */
@com.openggf.game.ModApi
public interface RenderProjection {
    /** Sixteen column-major floats, with the same lifetime as the current render pass. */
    float[] getProjectionMatrixBuffer();
    boolean isFBOProjectionActive();
    int getCurrentDisplayHeight();
}
