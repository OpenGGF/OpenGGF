package com.openggf.tests.route;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Two identity sets reused every frame so a route observer can tell genuine
 * absent-to-active and active-to-absent object transitions apart from objects
 * that were already live. {@link #beginFrame(ObjectManager)} fills the active
 * set; {@link #endFrame()} makes it the previous set for the next frame.
 */
public final class ObjectLifetimeFrames {
    private Set<ObjectInstance> activeFrame = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<ObjectInstance> previousFrame = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Seeds the previous set with the objects already live before frame one. */
    public ObjectLifetimeFrames(ObjectManager objects) {
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (!object.isDestroyed()) previousFrame.add(object);
        }
    }

    /** Refills the active set from the live object manager. */
    public void beginFrame(ObjectManager objects) {
        activeFrame.clear();
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (!object.isDestroyed()) activeFrame.add(object);
        }
    }

    /** Swaps the sets so this frame's objects become the next frame's previous set. */
    public void endFrame() {
        Set<ObjectInstance> reusable = previousFrame;
        previousFrame = activeFrame;
        activeFrame = reusable;
    }

    public Set<ObjectInstance> active() {
        return activeFrame;
    }

    public Set<ObjectInstance> previous() {
        return previousFrame;
    }
}
