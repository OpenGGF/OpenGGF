package com.openggf.tools.challenge;

/** Best-effort owner teardown: one failed release cannot strand the remaining native owners. */
final class ChallengeCleanup {
    @FunctionalInterface
    interface Action {
        void close() throws Exception;
    }
    private ChallengeCleanup() {}
    static Throwable closeAll(Throwable primary, Action... owners) {
        for (Action owner : owners) {
            try {
                owner.close();
            } catch (Throwable failure) {
                if (primary == null)
                    primary = failure;
                else if (primary != failure)
                    primary.addSuppressed(failure);
            }
        }
        return primary;
    }
    static void rethrow(Throwable failure) throws Exception {
        if (failure == null)
            return;
        if (failure instanceof Exception exception)
            throw exception;
        if (failure instanceof Error error)
            throw error;
        throw new IllegalStateException(failure);
    }
}
