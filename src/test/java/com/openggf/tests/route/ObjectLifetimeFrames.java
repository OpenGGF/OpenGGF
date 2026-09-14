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
 * set; {@link #endFrame()} makes it the previous set for the next frame. The
 * accessors return read-only views of sets that are cleared and swapped every
 * frame, so observers must consume them inside the frame they are handed.
 *
 * <p>Inputs: the live {@link ObjectManager} each frame. Re-read the manager
 * from the level for routes that cross an act reload, since the level
 * replaces it there.
 *
 * <p>Origin: extracted from the FBZ2 route controller
 * ({@code TestFbzAct2TraversalPreboss}) in commit 610464952.
 */
public final class ObjectLifetimeFrames {
    private Set<ObjectInstance> activeFrame = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<ObjectInstance> previousFrame = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<ObjectInstance> activeView = Collections.unmodifiableSet(activeFrame);
    private Set<ObjectInstance> previousView = Collections.unmodifiableSet(previousFrame);

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
        Set<ObjectInstance> reusableView = previousView;
        previousView = activeView;
        activeView = reusableView;
    }

    /** Read-only view of this frame's live objects; valid until {@link #endFrame()}. */
    public Set<ObjectInstance> active() {
        return activeView;
    }

    /** Read-only view of the previous frame's live objects; valid until {@link #endFrame()}. */
    public Set<ObjectInstance> previous() {
        return previousView;
    }
}
