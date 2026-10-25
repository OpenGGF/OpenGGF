package com.openggf.game.competition;

import com.openggf.game.PlayableEntity;

/**
 * Engine-internal world policy for a native player's rings-before-object touch
 * boundary. The owner admits only its exact supplied human actors; eligibility
 * for any intermediate domain collision remains that callback's responsibility.
 */
public interface NativeTouchResponseOrder {
    boolean owns(PlayableEntity player);
    void afterRings(PlayableEntity player);
}
