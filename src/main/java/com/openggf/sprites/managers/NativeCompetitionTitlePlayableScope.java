package com.openggf.sprites.managers;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The two native player slots execute inside Object_RAM while the title-card
 * game-mode bit excludes LevelOnly_Object_RAM: RunObjects (s2.asm:29805-29819).
 * Only those exact actors' fixed dust slots are excluded from their ordinary
 * animation calls; unrelated actors and extension dust retain normal behavior.
 */
final class NativeCompetitionTitlePlayableScope implements AutoCloseable {
    private static final ThreadLocal<NativeCompetitionTitlePlayableScope> CURRENT = new ThreadLocal<>();

    private final Thread owner = Thread.currentThread();
    private final AbstractPlayableSprite p1;
    private final AbstractPlayableSprite p2;
    private boolean closed;

    private NativeCompetitionTitlePlayableScope(AbstractPlayableSprite p1, AbstractPlayableSprite p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    static NativeCompetitionTitlePlayableScope enter(AbstractPlayableSprite p1, AbstractPlayableSprite p2) {
        if (p1 == null || p2 == null || p1 == p2) {
            throw new IllegalArgumentException("Native title scope requires two distinct actors");
        }
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested native title playable scope");
        }
        var scope = new NativeCompetitionTitlePlayableScope(p1, p2);
        CURRENT.set(scope);
        return scope;
    }

    static boolean suppressFixedDustFor(AbstractPlayableSprite actor) {
        var scope = CURRENT.get();
        return scope != null && (scope.p1 == actor || scope.p2 == actor);
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner || closed || CURRENT.get() != this) {
            throw new IllegalStateException("Native title playable scope must close once on its owner thread");
        }
        closed = true;
        CURRENT.remove();
    }
}
