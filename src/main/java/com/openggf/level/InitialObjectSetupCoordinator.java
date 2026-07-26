package com.openggf.level;

import com.openggf.game.InitialObjectSetupLifecycle;

/**
 * Owns pending initial object-setup authority for one live level runtime.
 */
final class InitialObjectSetupCoordinator {
    private InitialObjectSetupLifecycle pending = InitialObjectSetupLifecycle.NONE;

    void publish(InitialObjectSetupLifecycle lifecycle) {
        pending = lifecycle == null ? InitialObjectSetupLifecycle.NONE : lifecycle;
    }

    boolean hasPendingPass() {
        return pending == InitialObjectSetupLifecycle.S3K_LOAD_THEN_EXECUTE_ONCE;
    }

    boolean consume(Runnable dispatch) {
        InitialObjectSetupLifecycle result = pending;
        pending = InitialObjectSetupLifecycle.NONE;
        if (result != InitialObjectSetupLifecycle.S3K_LOAD_THEN_EXECUTE_ONCE) {
            return false;
        }
        dispatch.run();
        return true;
    }

    void discard() {
        pending = InitialObjectSetupLifecycle.NONE;
    }
}
