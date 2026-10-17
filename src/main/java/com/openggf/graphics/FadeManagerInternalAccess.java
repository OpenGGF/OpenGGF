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
}
