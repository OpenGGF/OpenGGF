package com.openggf.game.internal;

/**
 * Internal role for an engine decorator that owns its native rewind publications,
 * including inherited adapters, throughout cold attachment and service recreation.
 * The role alone grants no authority: admission verifies concrete engine provenance
 * and the complete structural lineage anchored to the session's actual root.
 */
public interface NativeRewindPublicationOwner {
}
