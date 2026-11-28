package com.openggf.game.internal;

import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Function;

/** An engine-owned profile edit that retains access to the host's separate native routines. */
public final class PhysicsProfileOverrides implements PhysicsProvider {
    private final PhysicsProvider source;
    private final Predicate<String> characters;
    private final Function<PhysicsProfile, PhysicsProfile> edit;

    public PhysicsProfileOverrides(PhysicsProvider source, Predicate<String> characters,
                                   Function<PhysicsProfile, PhysicsProfile> edit) {
        this.source = Objects.requireNonNull(source, "source");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.edit = Objects.requireNonNull(edit, "edit");
    }

    public static PhysicsProvider nativeSource(PhysicsProvider provider) {
        while (provider instanceof PhysicsProfileOverrides override) {
            provider = override.source;
        }
        return provider;
    }

    private PhysicsProfile map(String character, PhysicsProfile profile) {
        return profile == null || !characters.test(character) ? profile
                : Objects.requireNonNull(edit.apply(profile), "Profile transform returned null");
    }

    @Override public PhysicsProfile getProfile(String character) {
        return map(character, source.getProfile(character));
    }

    @Override public PhysicsProfile getInitProfile(String character) {
        return map(character, source.getInitProfile(character));
    }

    @Override public PhysicsModifiers getModifiers() {
        return source.getModifiers();
    }
}
