package com.openggf.level.rings;

import com.openggf.camera.Camera;

/** Test access to the NATIVE widescreen dormant ring candidates of a ring manager. */
public final class DormantRingProbe {
    private DormantRingProbe() { }

    public static int count(RingManager rings, Camera camera) {
        return rings == null ? 0 : rings.dormantMarginRings(camera).size();
    }
}
