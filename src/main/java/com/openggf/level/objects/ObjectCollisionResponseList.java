package com.openggf.level.objects;

import java.util.ArrayList;
import java.util.List;

final class ObjectCollisionResponseList {
    private static final int S3K_CAMERA_BACK_OFFSET = 0x80;
    private static final int S3K_WINDOW = 0x280;
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

    void captureForNextFrame(int cameraX, List<ObjectInstance> currentObjects) {
        previousObjects.clear();
        int cameraCoarseBack = ((cameraX & 0xFFFF) - S3K_CAMERA_BACK_OFFSET) & 0xFF80;
        for (ObjectInstance instance : currentObjects) {
            if (instance == null || instance.isDestroyed()
                    || !instance.publishesTouchResponseListEntryThisFrame()) {
                continue;
            }
            int objectCoarse = instance.getOutOfRangeReferenceX() & 0xFF80;
            int delta = (objectCoarse - cameraCoarseBack) & 0xFFFF;
            if (delta > S3K_WINDOW) {
                continue;
            }
            previousObjects.add(instance);
            if (previousObjects.size() >= S3K_MAX_ENTRIES) {
                break;
            }
        }
    }
}
