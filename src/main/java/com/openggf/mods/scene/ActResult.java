package com.openggf.mods.scene;

import com.openggf.game.ActExit;

import com.openggf.game.ZoneKey;
import java.util.Map;
import java.util.Objects;

/** Delivered once to the same suspended scene; rings are native health, not a mod wallet. */
@com.openggf.game.ModApi
public record ActResult(ZoneKey.Mod destination, ActExit reason, int rings,
        long frames, Map<String, String> state) {
    public ActResult {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(reason, "reason");
        if (rings < 0 || frames < 0) throw new IllegalArgumentException("Negative act result");
        state = Map.copyOf(state);
    }
}
