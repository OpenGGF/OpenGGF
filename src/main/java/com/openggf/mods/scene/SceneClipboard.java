package com.openggf.mods.scene;

/** Write-only system clipboard access for an explicit foreground user action. */
@com.openggf.game.ModApi
public interface SceneClipboard {
    /** Maximum UTF-16 length of a copied string. Empty, NUL and malformed Unicode text is denied. */
    int MAX_TEXT_LENGTH = 4096;

    /**
     * Requests a copy during this scene owner's update. A physical key, gamepad button or mouse
     * press grants one copy for five monotonic seconds. Further presses replace that allowance.
     * Focus loss, scene suspension and close cancel both the allowance and pending work.
     * The allowance permits asynchronous text retrieval after the initial Copy action.
     * An accepted submission consumes it even if the platform is unavailable or the write fails.
     * Native execution also checks the five-second bound. Poll the returned handle on later
     * updates. No completion callback runs creator code.
     */
    Write writeText(String text);

    /** An operation handle contains no copied text and remains readable after the scene closes. */
    @com.openggf.game.ModApi
    interface Write {
        Result result();
    }

    /** Native failure is redacted as UNAVAILABLE. DENIED covers invalid text, authority and lifetime. */
    @com.openggf.game.ModApi
    enum Result { PENDING, SUCCESS, UNAVAILABLE, DENIED }

    /** Harmless default for contexts without a platform owner. */
    static SceneClipboard unavailable() {
        return text -> () -> Result.UNAVAILABLE;
    }
}
