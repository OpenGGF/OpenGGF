package com.openggf.mods.runtime;

import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.StaticFixup;
import com.openggf.mods.code.ModFaultBoundary;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/** Captures deferred initialization actions while their provider still has its verified owner. */
final class OwnerBoundInitialization {
    private OwnerBoundInitialization() { }

    static Object returned(String owner, ModFaultBoundary boundary, int limit,
            Object delegate, Method method, Object value) {
        if (!(delegate instanceof LevelInitProfile)) return value;
        boolean fixups = method.getName().equals("postTeardownFixups");
        if (!fixups && !List.of("levelLoadSteps", "levelTeardownSteps", "perTestResetSteps").contains(method.getName()))
            return value;
        if (!(value instanceof List<?> steps) || steps.size() > limit)
            throw new IllegalArgumentException("Initialization actions need a bounded list");
        return steps.stream().map(step -> {
            if (fixups) {
                if (!(step instanceof StaticFixup fixup)) throw new IllegalArgumentException("Invalid static fixup");
                return new StaticFixup(fixup.name(), fixup.reason(), action(owner,boundary,fixup.action()));
            }
            if (!(step instanceof InitStep init)) throw new IllegalArgumentException("Invalid initialization step");
            return new InitStep(init.name(), init.romRoutine(), action(owner,boundary,init.action()));
        }).toList();
    }

    private static Runnable action(String owner, ModFaultBoundary boundary, Runnable delegate) {
        Objects.requireNonNull(delegate, "Initialization action");
        return delegate instanceof OwnedAction ? delegate : new OwnedAction(owner,boundary,delegate);
    }

    private record OwnedAction(String owner, ModFaultBoundary boundary, Runnable delegate) implements Runnable {
        @Override public void run() { boundary.run(owner,delegate); }
    }
}
