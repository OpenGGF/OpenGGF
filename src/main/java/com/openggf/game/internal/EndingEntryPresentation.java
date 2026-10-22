package com.openggf.game.internal;

/**
 * Internal capability for native endings whose outgoing level already performs
 * the entry fade and audio request, and whose provider owns the scene reveal.
 * The shell still requests its save and initializes/binds the ending provider.
 */
public interface EndingEntryPresentation {
    boolean ownsEntryPresentation();
}
