package com.openggf.game.rewind.snapshot;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.objects.Aiz2BossEndSequenceState;

/**
 * Static-state {@link RewindSnapshottable} adapter for the AIZ2 post-boss
 * bridge / button / Knuckles cutscene latches held in
 * {@link Aiz2BossEndSequenceState}. Wraps the static snapshot/restore methods
 * for the rewind registry, exactly like {@link OscillationStaticAdapter}.
 *
 * <p>These latches ratchet forward and are not otherwise rewound, so without
 * this adapter a backward seek desyncs them against the rewound boss/cutscene
 * instance flags (bridge re-drop, cutscene-override object deletion).
 */
public final class Aiz2BossEndSequenceStaticAdapter
        implements RewindSnapshottable<Aiz2BossEndSequenceState.Snapshot> {

    @Override
    public String key() {
        return "aiz2-boss-end-sequence";
    }

    @Override
    public Aiz2BossEndSequenceState.Snapshot capture() {
        return Aiz2BossEndSequenceState.snapshot();
    }

    @Override
    public void restore(Aiz2BossEndSequenceState.Snapshot snapshot) {
        Aiz2BossEndSequenceState.restore(snapshot);
    }
}
