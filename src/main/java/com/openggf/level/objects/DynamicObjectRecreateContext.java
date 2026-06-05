package com.openggf.level.objects;

/**
 * Narrow restore-time context exposed to dynamic rewind codecs.
 */
public record DynamicObjectRecreateContext(ObjectManager objectManager) {
    public ObjectServices objectServices() {
        return objectManager.objectServicesForRewind();
    }
}
