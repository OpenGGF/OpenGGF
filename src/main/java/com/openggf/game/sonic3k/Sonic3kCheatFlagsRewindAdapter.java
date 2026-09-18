package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;

/**
 * Rewind-registry {@link RewindSnapshottable} adapter for the per-module
 * {@link Sonic3kCheatFlags} (ROM: {@code Level_select_flag},
 * {@code Slow_motion_flag}, {@code Debug_cheat_flag}).
 *
 * <p>The MHZ pulley lift writes {@code Debug_cheat_flag} from a button
 * sequence entered during play. Without rewind coverage a backward seek to
 * before the sequence would leave the flag set while the pulley's own
 * sequence counter rolled back -- the same static-state bug family as
 * {@link Sonic3kLevelTriggerStaticAdapter} and
 * {@link com.openggf.game.sonic1.Sonic1ConveyorStateRewindAdapter}.
 *
 * <p>Registered via {@link Sonic3kLevelEventManager#extraRewindAdapters()}.
 */
public final class Sonic3kCheatFlagsRewindAdapter
        implements RewindSnapshottable<Sonic3kCheatFlags.Snapshot> {

    @Override
    public String key() {
        return "s3k-cheat-flags";
    }

    @Override
    public Sonic3kCheatFlags.Snapshot capture() {
        Sonic3kCheatFlags flags = resolve();
        return flags != null ? flags.captureRewindState() : null;
    }

    @Override
    public void restore(Sonic3kCheatFlags.Snapshot snapshot) {
        Sonic3kCheatFlags flags = resolve();
        if (flags != null) {
            flags.restoreRewindState(snapshot);
        }
    }

    @Override
    public void resetForMissingSnapshot() {
        Sonic3kCheatFlags flags = resolve();
        if (flags != null) {
            flags.reset();
        }
    }

    private static Sonic3kCheatFlags resolve() {
        return GameServices.hasRuntime()
                ? GameServices.module().getGameService(Sonic3kCheatFlags.class)
                : null;
    }
}
