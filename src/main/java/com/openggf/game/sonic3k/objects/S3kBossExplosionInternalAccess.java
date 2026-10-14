package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.game.rewind.RewindStateful;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Internal encounter bridge; helper RAM restores with the owner's shared native RNG. */
public final class S3kBossExplosionInternalAccess {
    private S3kBossExplosionInternalAccess() { }
    public static RewindStateful<?> rewindState(Supplier<S3kBossExplosionController> current,
                                               Consumer<S3kBossExplosionController> restored,
                                               Supplier<GameRng> sharedRng) {
        return new RewindStateful<S3kBossExplosionController.Snapshot>() {
            @Override public S3kBossExplosionController.Snapshot captureRewindStateValue() {
                var controller = current.get();
                return controller == null ? null : controller.captureSnapshot();
            }
            @Override public void restoreRewindStateValue(S3kBossExplosionController.Snapshot state) {
                restored.accept(state == null ? null : S3kBossExplosionController.fromSnapshot(state, sharedRng.get()));
            }
        };
    }
}
