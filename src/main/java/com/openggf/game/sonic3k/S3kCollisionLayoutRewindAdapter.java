package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;

/** Per-game layout metadata; resident definitions already belong to LevelSnapshot COW arrays. */
public final class S3kCollisionLayoutRewindAdapter implements RewindSnapshottable<S3kCollisionLayoutRewindAdapter.State> {
    private static final State EMPTY = new State(null, null);
    private State last;
    private byte[] lastSource;
    public record State(byte[] encoded, Sonic3kLevel.SceneLayoutSnapshot scene) {
        public State { encoded = encoded == null ? null : encoded.clone(); }
        @Override public byte[] encoded() { return encoded == null ? null : encoded.clone(); }
    }
    @Override public String key() { return "s3k-collision-layout"; }
    @Override public State capture() {
        if (!(GameServices.level().getCurrentLevel() instanceof Sonic3kLevel level)
                || level.collisionLayout == null) return EMPTY;
        byte[] source = level.collisionLayout.encoded();
        if (last != null && lastSource == source && last.scene().mapData() == level.getMap().getData()) return last;
        lastSource = source;
        return last = new State(source, level.captureSceneLayout());
    }
    @Override public void restore(State state) {
        byte[] encoded = state.encoded();
        if (encoded == null) return;
        if (!(GameServices.level().getCurrentLevel() instanceof Sonic3kLevel level))
            throw new IllegalStateException("Encoded collision layout restored without its level");
        level.collisionLayout = new S3kCollisionLayout(encoded, state.scene().width(), state.scene().height());
        level.restoreSceneLayout(state.scene());
        GameServices.level().setLevel(level);
        last = null;lastSource = null;
    }
    @Override public void resetForMissingSnapshot() { last = null;lastSource = null; }
}
