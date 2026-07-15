package com.openggf.mods.code;

import com.openggf.game.GameplayInputFilter;
import com.openggf.game.ModApi;
import com.openggf.game.ZoneKey;

import java.util.Objects;

/** One owner-validated, destination-scoped input-filter contribution. */
@ModApi
public record ModInputFilterContribution(ZoneKey destination, GameplayInputFilter filter) {
    public ModInputFilterContribution {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(filter, "filter");
    }
}
