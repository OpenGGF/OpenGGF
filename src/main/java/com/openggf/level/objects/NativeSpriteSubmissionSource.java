package com.openggf.level.objects;

/**
 * Engine-internal opt-in for objects whose DisplaySprite calls are transcribed.
 * While authoritative, the object receives no inferred render entry, including
 * on a zero-call init or after every attempted call was rejected.
 * This marker is deliberately outside the public Mod API.
 */
public interface NativeSpriteSubmissionSource {
    /** Conditional ports keep legacy rendering until their captured native state opts in. */
    default boolean nativeSpriteSubmissionsAuthoritative() { return true; }
}
