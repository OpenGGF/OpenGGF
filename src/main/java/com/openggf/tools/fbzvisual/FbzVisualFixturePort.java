package com.openggf.tools.fbzvisual;

/** Narrow validation-only write/readback seam for deterministic FBZ branches. */
public interface FbzVisualFixturePort {
    FbzVisualStateProbe.Snapshot snapshot();
    void write(String key, Object value);
}
