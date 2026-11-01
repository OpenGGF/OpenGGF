package com.openggf.level;

import com.openggf.game.rewind.identity.ObjectRefId;
import java.util.Objects;

/** Explicit relocation intent, frozen with the next native picture rather than the live draw. */
public sealed interface MotionCut {
    record Camera() implements MotionCut { }
    record Player(Object incarnation) implements MotionCut {
        public Player { Objects.requireNonNull(incarnation); }
        @Override public boolean equals(Object other) { return other instanceof Player player && incarnation == player.incarnation; }
        @Override public int hashCode() { return System.identityHashCode(incarnation); }
    }
    record ObjectOwner(ObjectRefId id) implements MotionCut {
        public ObjectOwner { Objects.requireNonNull(id); }
    }
    MotionCut CAMERA = new Camera();
    static MotionCut player(Object incarnation) { return new Player(incarnation); }
    static MotionCut object(ObjectRefId id) { return new ObjectOwner(id); }
    default boolean matches(Object key) {
        return switch (this) {
            case Camera ignored -> false;
            case Player player -> key instanceof LevelMotionOwners.PlayerKey owner && owner.incarnation() == player.incarnation();
            case ObjectOwner object -> key instanceof LevelMotionOwners.ObjectKey owner && owner.id().equals(object.id());
        };
    }
}
