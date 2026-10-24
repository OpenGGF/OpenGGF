package com.openggf.game.rules;

import java.util.Objects;

/**
 * Engine-owned rule construction for a presentation whose native gameplay owners
 * bypass the shared algorithms. Unowned capabilities remain absent; no cartridge
 * profile is borrowed. This partial value is not a complete game's rules or a Mod API profile.
 */
public final class PresentationOnlyRules {
    private PresentationOnlyRules() { }

    public static GameRules create(CameraRules camera, ObjectInteractionRules objectInteraction,
                                   DynamicArtDmaServiceModel dynamicArtDmaService,
                                   PaletteWhiteFadeModel paletteWhiteFade) {
        return new GameRules(null, null, null, null, Objects.requireNonNull(camera, "camera"),
                null, objectInteraction, null, null, null,
                Objects.requireNonNull(dynamicArtDmaService, "dynamicArtDmaService"),
                Objects.requireNonNull(paletteWhiteFade, "paletteWhiteFade"));
    }
}
