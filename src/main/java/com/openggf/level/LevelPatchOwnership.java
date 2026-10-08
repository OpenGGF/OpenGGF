package com.openggf.level;

import java.util.Objects;
import java.util.Set;

/** Engine admission bridge. This loader check binds provenance; it is not a Java sandbox. */
public final class LevelPatchOwnership {
    private static final StackWalker CALLER=StackWalker.getInstance(Set.of(
            StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private LevelPatchOwnership() { }

    public static LevelPatch bind(String verifiedOwner,LevelPatch patch,Set<String> registeredOwnedKeys) {
        ClassLoader caller=CALLER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type->type!=LevelPatchOwnership.class && type.getClassLoader()!=null)
                .findFirst().orElseThrow().getClassLoader());
        if (caller!=LevelPatchOwnership.class.getClassLoader())
            throw new SecurityException("Only the engine may bind decoded level patch ownership");
        return Objects.requireNonNull(patch,"patch").bindOwnership(verifiedOwner,Set.copyOf(registeredOwnedKeys));
    }
}
