package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;

/** Resolves a captured FBZ cloud identity against the restored object set. */
@FunctionalInterface
public interface FbzCloudIdentityResolver {
    boolean isLive(ObjectRefId stableId);

    default void refresh() { }
}
