package com.openggf.sprites.playable;

import com.openggf.game.ModApi;
import com.openggf.game.PhysicsProfile;
import java.util.Objects;

/** Instance-owned character tuning, independent of the active game's rules and modifiers. */
@ModApi
public record CharacterPhysicsSpec(PhysicsProfile profile, PlayableSensorSpec sensors) {
    public CharacterPhysicsSpec {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sensors, "sensors");
        if (profile.standXRadius() < 1 || profile.standXRadius() > 127
                || profile.standYRadius() < 1 || profile.standYRadius() > 127
                || profile.rollXRadius() < 1 || profile.rollXRadius() > 127
                || profile.rollYRadius() < 1 || profile.rollYRadius() > 127) {
            throw new IllegalArgumentException("Sensor radii must fit positive signed-byte offsets");
        }
    }

    public CharacterPhysicsSpec(PhysicsProfile profile) {
        this(profile, PlayableSensorSpec.standard());
    }
}
