package com.openggf.game.soniccd.special;

import com.openggf.game.SpecialStageProvider;
import com.openggf.game.rewind.RewindSnapshottable;

/**
 * Rewind owns values; structural services and ROM tables stay on the live owner.
 * The session-identity check rejects restores across stage sessions; missing keyframes
 * are rejected just like the S1 adapter.
 */
final class CdSpecialStageRewindAdapter implements RewindSnapshottable<CdSpecialStageProvider.State> {
    private final CdSpecialStageProvider provider;

    CdSpecialStageRewindAdapter(CdSpecialStageProvider provider) {
        this.provider = java.util.Objects.requireNonNull(provider);
    }

    @Override public String key() { return SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY; }
    @Override public CdSpecialStageProvider.State capture() { return provider.capture(); }
    @Override public void restore(CdSpecialStageProvider.State snapshot) { provider.restore(snapshot); }
}
