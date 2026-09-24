package com.openggf.game.internal;

import com.openggf.graphics.ArenaMaskState;

/** Opt-in presentation contract for a level runtime, regardless of game or encounter type.
 * The level's event coordinator owns calls to activate/release/advance and rewind capture.
 * Implementing this interface alone never activates the mask.
 */
public interface ArenaMaskSource {
    ArenaMaskState arenaMask();
}
