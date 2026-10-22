package com.openggf.level.objects;

/**
 * Engine-internal opt-in for objects whose DisplaySprite calls are transcribed.
 * Such an object receives no inferred render entry, including on a zero-call init.
 * This marker is deliberately outside the public Mod API.
 */
public interface NativeSpriteSubmissionSource {
}
