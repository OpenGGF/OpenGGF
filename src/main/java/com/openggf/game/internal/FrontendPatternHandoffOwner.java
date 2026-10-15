package com.openggf.game.internal;

/** Internal native pattern resource handoff, separate from guest VDP/register/gameplay state. */
public interface FrontendPatternHandoffOwner {
    String frontendPatternFormat();
    /** Null means this owner has no admitted frontend uploads. The result must be a copy. */
    byte[] captureFrontendPatterns();
    void acceptFrontendPatterns(byte[] packed);
    void retireFrontendPatterns();
}
