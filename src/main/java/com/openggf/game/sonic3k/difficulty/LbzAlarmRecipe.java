package com.openggf.game.sonic3k.difficulty;

/**
 * Bounded native LBZ alarm presets. The native owner keeps its touch latch,
 * siren timer and Flybot 767 allocation. {@link #BOUNDED} limits the stock
 * unbounded reinforcement:
 * <ul>
 *   <li>a blocked alarm (another escort is live) stays armed and silent: no
 *       cue, siren timer or animation is started;</li>
 *   <li>each allocated alarm instance makes at most one summon attempt, latched
 *       before allocation so a slot failure is not retried; ordinary native
 *       unload/reload allocates a new instance, a destructive rewind restore
 *       preserves the latch;</li>
 *   <li>at most one bounded escort is live in the world;</li>
 *   <li>the escort retires after its first committed attack or when a native
 *       LBZ scripted sequence begins (Robotnik intro, launch, Knuckles
 *       cutscene control lock); ordinary object-controlled play does not.</li>
 * </ul>
 */
@com.openggf.game.ModApi
public enum LbzAlarmRecipe {
    /** Shipped behaviour: every touch of an idle alarm summons a persistent Flybot. */
    STOCK,
    /** One bounded escort per alarm; retires before scripted control. */
    BOUNDED
}
