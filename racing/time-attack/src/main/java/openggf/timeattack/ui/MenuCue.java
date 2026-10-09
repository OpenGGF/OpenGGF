package openggf.timeattack.ui;

/**
 * Feedback a view reports for one user action: moving the selection, confirming, backing out,
 * or an action that could not be carried out. Views report a cue only when something changed,
 * so a no-op press at a list boundary stays silent.
 */
public enum MenuCue {
    NAVIGATE, CONFIRM, CANCEL, ERROR
}
