package com.openggf.mods.code;

import com.openggf.game.ModApi;
import com.openggf.game.ZoneKey;
import com.openggf.level.objects.HudProfile;

import java.util.Objects;

/** One owner-validated, destination-scoped HUD-profile contribution. */
@ModApi
public record ModHudProfileContribution(ZoneKey destination, HudProfile profile) {
    public ModHudProfileContribution {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(profile, "profile");
    }
}
