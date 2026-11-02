package com.openggf.route;

import java.util.Objects;

/**
 * The single authority for how a route starts: ROM by hash, inlet, roster, viewport width,
 * donor and explicit initial state. Java tests and comments do not supply a competing launch.
 */
public record LaunchContract(RomIdentity rom, Inlet inlet, Roster roster, int width, String donor,
                             InitialState initial) {
    public LaunchContract {
        Objects.requireNonNull(rom, "rom");
        Objects.requireNonNull(inlet, "inlet");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(initial, "initial");
        donor = donor == null || donor.isBlank() ? "off" : donor;
        if (width <= 0) throw new IllegalArgumentException("width must be positive: " + width);
        if (inlet instanceof Inlet.ColdLevel cold && !cold.level().game().equals(rom.game())) {
            throw new IllegalArgumentException("inlet level " + cold.level() + " is not on ROM " + rom.game());
        }
    }

    public static LaunchContract coldLevel(RomIdentity rom, LevelId level, Roster roster, int width,
                                           int vIntStart) {
        return new LaunchContract(rom, new Inlet.ColdLevel(level, false), roster, width, "off",
                new InitialState(vIntStart, Seeds.NONE));
    }

    public LaunchContract withInitial(InitialState next) {
        return new LaunchContract(rom, inlet, roster, width, donor, next);
    }
}
