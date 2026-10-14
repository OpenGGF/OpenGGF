package com.openggf.game.mutators;

import com.openggf.control.PlayerInputState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.Optional;

/** Internal steering authority for an exact leased helper; empty retains the native CPU. */
@FunctionalInterface
public interface NativeCompanionControlSource {
    Optional<PlayerInputState> inputFor(AbstractPlayableSprite carrier);
}
