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
