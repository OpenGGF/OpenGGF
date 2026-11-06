package com.openggf.game.session;

import java.util.function.Consumer;

/** Runs independent session cleanup steps without losing the first failure. */
final class GameplayCleanup {
    private Throwable failure;

    GameplayCleanup() {
    }

    GameplayCleanup(Throwable primary) {
        failure = primary;
    }

    void run(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | Error next) {
            if (failure == null) failure = next;
            else if (failure != next) failure.addSuppressed(next);
        }
    }

    <T> void whenPresent(T owner, Consumer<? super T> step) {
        if (owner != null) run(() -> step.accept(owner));
    }

    void throwIfFailed() {
        if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        if (failure != null) throw (Error) failure;
    }
}
