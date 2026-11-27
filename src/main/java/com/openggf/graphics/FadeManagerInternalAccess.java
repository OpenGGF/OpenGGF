package com.openggf.graphics;

/** Engine composition bridge; graphics consumes host semantics without resolving a world. */
public final class FadeManagerInternalAccess {
    private FadeManagerInternalAccess() {
    }

    public static void bindWhiteFadePresentation(FadeManager fade, boolean paletteWhiteFade,
                                                 int fromWhiteVintsPerStep) {
        java.util.Objects.requireNonNull(fade, "fade")
                .bindWhiteFadePresentation(paletteWhiteFade, fromWhiteVintsPerStep);
    }

    public static boolean isNextUpdateDeferred(FadeManager fade) {
        return fade.isNextUpdateDeferred();
    }

    /** Native palette routines may retain counted waits after their colour steps. */
    public static void startFadeFromWhite(FadeManager fade, Runnable onComplete, int terminalNoOpFrames) {
        java.util.Objects.requireNonNull(fade, "fade").startFadeFromWhite(onComplete, terminalNoOpFrames);
    }
}
