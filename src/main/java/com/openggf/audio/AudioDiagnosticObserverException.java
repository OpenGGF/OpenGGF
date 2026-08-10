package com.openggf.audio;

/**
 * Marks a diagnostic observer failure so presentation mixing cannot demote it
 * to an ordinary failed voice. Complete-run capture must abort loudly.
 */
public final class AudioDiagnosticObserverException
        extends RuntimeException {
    public AudioDiagnosticObserverException(RuntimeException cause) {
        super("audio diagnostic observer failed", cause);
    }

    public static void invoke(Runnable callback) {
        try {
            callback.run();
        } catch (AudioDiagnosticObserverException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AudioDiagnosticObserverException(failure);
        }
    }
}
