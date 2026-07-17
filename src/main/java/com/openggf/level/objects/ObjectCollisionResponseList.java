package com.openggf.level.objects;

import java.util.ArrayList;
import java.util.List;

final class ObjectCollisionResponseList {
    private static final int S3K_MAX_ENTRIES = 0x7E / 2;

    private final List<ObjectInstance> previousObjects = new ArrayList<>();
    private boolean usePrevious;

    void setUsePrevious(boolean usePrevious) {
        this.usePrevious = usePrevious;
    }

    boolean usesPrevious() {
        return usePrevious;
    }

    boolean shouldRefreshFrameStartSnapshot() {
        return !usePrevious;
    }

    List<ObjectInstance> touchResponseObjects(List<ObjectInstance> currentObjects) {
        return usePrevious ? previousObjects : currentObjects;
    }

    void captureForNextFrame(List<ObjectInstance> currentObjects,
            ObjectCallbackRouter callbacks) {
        previousObjects.clear();
        for (ObjectInstance instance : currentObjects) {
            if (instance == null || callbacks.call(instance, instance::isDestroyed)
                    || !callbacks.call(instance, instance::publishesTouchResponseListEntryThisFrame)) {
                continue;
            }
            // Add_SpriteToCollisionResponseList has no camera-range gate; the
            // object routine decides whether to publish, and this helper only
            // enforces the native $7E-byte list capacity
            // (docs/skdisasm/sonic3k.asm:21200-21210).
            previousObjects.add(instance);
            if (previousObjects.size() >= S3K_MAX_ENTRIES) {
                break;
            }
        }
    }
}
