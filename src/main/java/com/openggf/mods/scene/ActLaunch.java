package com.openggf.mods.scene;

import com.openggf.game.CharacterKey;
import com.openggf.game.ZoneKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/** A zero-based mod act visit. Optional spawn values are native centre coordinates. */
@com.openggf.game.ModApi
public record ActLaunch(ZoneKey.Mod destination, int act, CharacterKey main,
        List<CharacterKey> sidekicks, OptionalInt spawnX, OptionalInt spawnY,
        int rings, Map<String, String> state) {
    public ActLaunch {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(main, "main");
        sidekicks = List.copyOf(sidekicks);
        Objects.requireNonNull(spawnX, "spawnX");
        Objects.requireNonNull(spawnY, "spawnY");
        if (act < 0 || rings < 0 || rings > 999)
            throw new IllegalArgumentException("Invalid act or ring count");
        for (OptionalInt position : List.of(spawnX, spawnY)) {
            if (position.isPresent() && (position.getAsInt() < 0 || position.getAsInt() > 0x7fff))
                throw new IllegalArgumentException("Spawn must fit positive native centre coordinates");
        }
        state = Map.copyOf(state);
    }
}
