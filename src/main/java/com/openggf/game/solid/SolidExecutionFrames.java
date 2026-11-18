package com.openggf.game.solid;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectInstance;

import java.util.List;

/** Internal lifetime checks for the engine's solid registry, outside the creator API. */
public final class SolidExecutionFrames {
    private SolidExecutionFrames() { }

    public static Frame capture(SolidExecutionRegistry registry) {
        return new Frame(registry);
    }

    /** A native routine can call the re-evaluating helper with a different live object in a0. */
    public interface NativeReceiverResolver extends ObjectSolidExecutionContext.Resolver {
        SolidCheckpointBatch resolveNativeReceiver(ObjectInstance receiver);
    }

    /** Publish the receiver's contact while the enclosing controller retains its execution context. */
    public static SolidCheckpointBatch resolveNativeReceiver(SolidExecutionRegistry registry, ObjectInstance receiver) {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(SolidExecutionFrames.class);
        if (caller.getClassLoader() != SolidExecutionFrames.class.getClassLoader()) {
            throw new SecurityException("Native solid calls belong to the engine object owner");
        }
        if (!(registry instanceof DefaultSolidExecutionRegistry actual)) {
            return new SolidCheckpointBatch(receiver, java.util.Map.of());
        }
        var frame = capture(actual);
        var context = frame.currentObject();
        var batch = context.resolveNativeReceiver(receiver);
        if (!batch.perPlayer().isEmpty() && frame.isCurrent() && actual.currentObject() == context) {
            actual.publishNativeReceiver(batch);
        }
        return batch;
    }

    public static final class Frame {
        private final SolidExecutionRegistry registry;
        private final Object identity;

        private Frame(SolidExecutionRegistry registry) {
            this.registry = registry;
            identity = registry instanceof DefaultSolidExecutionRegistry actual ? actual.frameIdentity() : null;
        }

        public boolean isCurrent() {
            return !(registry instanceof DefaultSolidExecutionRegistry actual) || actual.frameIdentity() == identity;
        }

        public Frame begin(int counter, List<? extends PlayableEntity> players) {
            if (!isCurrent()) throw new IllegalStateException("Solid frame was superseded before acquisition");
            registry.beginFrame(counter, players);
            return capture(registry);
        }

        public ObjectSolidExecutionContext currentObject() { return registry.currentObject(); }

        public void endObject(ObjectInstance object, ObjectSolidExecutionContext context) {
            if (isCurrent() && registry.currentObject() == context) registry.endObject(object);
        }

        public void finish() {
            if (isCurrent()) registry.finishFrame();
        }
    }
}
