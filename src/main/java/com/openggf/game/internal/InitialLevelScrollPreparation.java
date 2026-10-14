package com.openggf.game.internal;

/** Internal profile-owned scroll dispatch before a fresh initial Process_Sprites walk.
 * Only an already admitted fresh-assembly token invokes this port. Decode, preview,
 * restoration and non-level scene loops do not gain a native dispatch from it.
 */
public interface InitialLevelScrollPreparation {
    void prepareInitialLevelScroll();
}
