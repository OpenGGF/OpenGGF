package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import java.util.List;
import java.util.Optional;

/** Read-only lookup of the current live object set; object gameplay mutation remains supported. */
@com.openggf.game.ModApi
public interface ObjectQuery {
    ObjectQuery EMPTY = new ObjectQuery() {
        public <T extends ObjectInstance> List<T> activeObjectsOfType(Class<T> type) { return List.of(); }
        public Optional<ObjectRefId> identityOf(ObjectInstance object) { return Optional.empty(); }
        public Optional<ObjectInstance> resolve(ObjectRefId identity) { return Optional.empty(); }
    };
    /**
     * Immutable membership snapshot ordered by dynamic ordinal, placement id, then generation.
     * Recreation during rewind retains captured identities and ordering. A fresh respawn gets
     * a new identity; destroyed occupants remain visible until their normal removal boundary.
     */
    <T extends ObjectInstance> List<T> activeObjectsOfType(Class<T> type);
    /** Empty for an object that no longer belongs to this manager's live set. */
    Optional<ObjectRefId> identityOf(ObjectInstance object);
    /**
     * Resolves the current instance after recreation, or empty after removal. Ids belong to
     * this manager's current timeline: discard them on a level/timeline reset, which restarts
     * native ordinals and may reuse their numeric values.
     */
    Optional<ObjectInstance> resolve(ObjectRefId identity);
}
