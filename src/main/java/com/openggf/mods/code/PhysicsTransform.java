package com.openggf.mods.code;

import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Function;

/** One staged physics edit. The transaction supplies its owner at publication. */
record PhysicsTransform(Predicate<String> characters, Function<PhysicsProfile, PhysicsProfile> edit) {
    PhysicsTransform {
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(edit, "edit");
    }

    PhysicsProvider apply(PhysicsProvider source, String owner, ModFaultBoundary boundary) {
        return new com.openggf.game.internal.PhysicsProfileOverrides(source,
                character -> boundary.isOwnerAvailable(owner)
                        && boundary.call(owner, () -> characters.test(character)),
                profile -> boundary.call(owner, () -> Objects.requireNonNull(
                        edit.apply(profile), "Physics transform returned null")));
    }
}
