package secondstrike.boss;

import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;

/** The first Fire Breath gains a second, delayed wave once its armour is damaged. */
public final class SecondStrikeBosses implements AizMinibossAttackPolicy {
    /**
     * A semantic breath-cycle boundary starts this delay; it never reads a run frame,
     * route, player position, or trace row. The native owner cancels the wave on defeat.
     */
    // Native muzzle flare sequence lasts 16 updates; release aligns with that cue.
    @Override
    public int followupDelayTicks(int remainingHits) {
        return remainingHits > 0 && remainingHits <= 3 ? 16 : -1;
    }
}
